import { randomUUID } from "node:crypto";
import { writeFileSync, mkdirSync } from "node:fs";
import { resolve } from "node:path";
import { Session } from "./session.mjs";
import { assert, status, invariants } from "./assertions.mjs";
import { openAcceptanceDatabase, datePlus } from "./fixtures.mjs";
const args = process.argv.slice(2);
const opt = (key, fallback) =>
  args.includes(key) ? args[args.indexOf(key) + 1] : fallback;
const base = opt("--base-url", "http://127.0.0.1:18080");
const group = opt("--group", "all");
if (!["auth", "booking", "frontdesk", "all"].includes(group))
  throw Error("Invalid group");
const db = openAcceptanceDatabase(process.env.HOTEL_ACCEPTANCE_DB);
const a = new Session(base),
  b = new Session(base),
  desk = new Session(base),
  anonymous = new Session(base);
const results = [];
async function check(id, name, fn) {
  try {
    await fn();
    invariants(db);
    results.push({ id, name, status: "PASS" });
    console.log(`PASS ${id} ${name}`);
  } catch (e) {
    results.push({ id, name, status: "FAIL", detail: e.stack });
    console.error(`FAIL ${id} ${name}: ${e.message}`);
  }
}
await a.login("guest1");
await b.login("guest2");
await desk.login("frontdesk");
const meta = status(await anonymous.request("/meta"), 200),
  D = meta.today;
const types = status(
  await anonymous.request(
    `/room-types?checkIn=${D}&checkOut=${datePlus(D, 1)}`,
  ),
  200,
);
const type = types[0];
const post = (session, path, body, extra = {}) =>
  session.request(path, { method: "POST", body, ...extra });
const book = (
  session,
  offset = 10,
  nights = 1,
  key = randomUUID(),
  roomTypeId = type.id,
) =>
  post(
    session,
    "/orders",
    {
      roomTypeId,
      checkIn: datePlus(D, offset),
      checkOut: datePlus(D, offset + nights),
      guestCount: 1,
    },
    { headers: { "Idempotency-Key": key } },
  );
const inv = (offset) =>
  db
    .prepare(
      "SELECT reserved FROM inventory_days WHERE room_type_id=? AND stay_date=?",
    )
    .get(type.id, datePlus(D, offset)).reserved;
if (group === "all" || group === "auth") {
  await check("A01", "authentication and role restrictions", async () => {
    status(await anonymous.request("/orders"), 401);
    status(await a.request("/frontdesk/orders"), 403);
    status(await desk.request("/frontdesk/orders"), 200);
  });
  await check("A24", "CSRF rejects mutation", async () => {
    status(await post(a, "/orders", {}, { csrf: false }), 403);
  });
  await check("A32", "session invalidation and refreshed CSRF", async () => {
    const c = new Session(base);
    await c.login("guest1");
    status(await c.request("/auth/me"), 200);
    const stale = new Session(base);
    stale.cookies = new Map(c.cookies);
    const r = await post(c, "/auth/logout");
    assert.ok([200, 204].includes(r.status));
    status(await stale.request("/auth/me"), 401);
  });
}
if (group === "all" || group === "booking") {
  await check("A02", "order ownership", async () => {
    const o = status(await book(a), 201);
    status(await b.request(`/orders/${o.id}`), 404);
    status(await post(b, `/orders/${o.id}/cancel`), 404);
  });
  await check("A03", "half-open stay and price", async () => {
    const before = [inv(11), inv(12), inv(13)];
    const o = status(await book(a, 11, 2), 201);
    assert.equal(o.totalCents, o.nightlyPriceCents * 2);
    assert.deepEqual(
      [inv(11), inv(12), inv(13)],
      [before[0] + 1, before[1] + 1, before[2]],
    );
  });
  await check("A04", "invalid dates and capacity", async () => {
    for (const body of [
      {
        roomTypeId: type.id,
        checkIn: datePlus(D, -1),
        checkOut: D,
        guestCount: 1,
      },
      { roomTypeId: type.id, checkIn: D, checkOut: D, guestCount: 1 },
      {
        roomTypeId: type.id,
        checkIn: datePlus(D, 1),
        checkOut: D,
        guestCount: 1,
      },
      {
        roomTypeId: type.id,
        checkIn: D,
        checkOut: datePlus(D, 1),
        guestCount: 999,
      },
    ])
      status(
        await post(a, "/orders", body, {
          headers: { "Idempotency-Key": randomUUID() },
        }),
        400,
      );
  });
  await check("A05", "multi-night failure rolls back all nights", async () => {
    const total = db
      .prepare(
        "SELECT total FROM inventory_days WHERE room_type_id=? AND stay_date=?",
      )
      .get(type.id, datePlus(D, 21)).total;
    for (let i = 0; i < total; i++) status(await book(a, 21), 201);
    const before = inv(20);
    status(await book(a, 20, 2), 409);
    assert.equal(inv(20), before);
  });
  await check("A06", "concurrent last room", async () => {
    const total = db
      .prepare(
        "SELECT total FROM inventory_days WHERE room_type_id=? AND stay_date=?",
      )
      .get(type.id, datePlus(D, 23)).total;
    for (let i = 0; i < total - 1; i++) status(await book(a, 23), 201);
    const r = await Promise.all([book(a, 23), book(b, 23)]);
    assert.deepEqual(r.map((x) => x.status).sort(), [201, 409]);
  });
  await check(
    "A07",
    "concurrent identical request creates one order",
    async () => {
      const key = randomUUID();
      const r = await Promise.all([book(a, 24, 1, key), book(a, 24, 1, key)]);
      assert.deepEqual(r.map((x) => x.status).sort(), [200, 201]);
      assert.equal(r[0].data.id, r[1].data.id);
    },
  );
  await check("A08", "key payload mismatch", async () => {
    const key = randomUUID();
    status(await book(a, 25, 1, key), 201);
    const r = await book(a, 26, 1, key);
    status(r, 409);
    assert.equal(r.data.code, "IDEMPOTENCY_CONFLICT");
  });
  await check("A09", "cancel releases only once", async () => {
    const before = inv(27),
      o = status(await book(a, 27), 201);
    status(await post(a, `/orders/${o.id}/cancel`), 200);
    assert.equal(inv(27), before);
    status(await post(a, `/orders/${o.id}/cancel`), 409);
    assert.equal(inv(27), before);
  });
  await check(
    "A10",
    "same-day guest cancel forbidden frontdesk allowed",
    async () => {
      const o = status(await book(a, 0), 201);
      status(await post(a, `/orders/${o.id}/cancel`), 409);
      status(await post(desk, `/frontdesk/orders/${o.id}/cancel`), 200);
    },
  );
  await check("A23", "missing/out-of-window inventory rejected", async () => {
    status(await book(a, 100), 400);
  });
  await check("A25", "filters pagination validation", async () => {
    const o = status(await book(a, 28), 201);
    const page = status(
      await a.request(
        `/orders?orderNo=${o.orderNo}&status=CONFIRMED&checkIn=${datePlus(D, 28)}&page=1&size=1`,
      ),
      200,
    );
    assert.equal(page.total, 1);
    assert.equal(page.items[0].id, o.id);
    for (const q of ["page=0", "size=101", "status=INVALID", "checkIn=bad"])
      status(await a.request("/orders?" + q), 400);
  });
  await check("A30", "lost response replay", async () => {
    const key = randomUUID();
    await book(a, 29, 1, key);
    const before = inv(29);
    status(await book(a, 29, 1, key), 200);
    assert.equal(inv(29), before);
  });
}
if (group === "all" || group === "frontdesk") {
  await check("A11", "no-show releases future only", async () => {
    const o = db
      .prepare(
        "SELECT * FROM orders WHERE status='CONFIRMED' AND check_in_date < ? AND check_out_date > ? LIMIT 1",
      )
      .get(D, D);
    assert.ok(o, "seed spanning no-show");
    status(await post(desk, `/frontdesk/orders/${o.id}/no-show`), 200);
    const n = db
      .prepare(
        "SELECT stay_date,allocation_status FROM order_nights WHERE order_id=?",
      )
      .all(o.id);
    for (const x of n)
      assert.equal(x.allocation_status, x.stay_date < D ? "HELD" : "RELEASED");
    status(await post(desk, `/frontdesk/orders/${o.id}/no-show`), 409);
  });
  await check("A12", "fully past no-show", async () => {
    const o = db
      .prepare(
        "SELECT * FROM orders WHERE status='CONFIRMED' AND check_out_date <= ? LIMIT 1",
      )
      .get(D);
    assert.ok(o, "seed past no-show");
    status(await post(desk, `/frontdesk/orders/${o.id}/no-show`), 200);
    assert.equal(
      db
        .prepare(
          "SELECT COUNT(*) n FROM order_nights WHERE order_id=? AND allocation_status='RELEASED'",
        )
        .get(o.id).n,
      0,
    );
  });
  await check("A14", "non-today and cancelled check-in rejected", async () => {
    const o = status(await book(a, 31), 201);
    status(
      await post(desk, `/frontdesk/orders/${o.id}/check-in`, {
        roomId: 1,
        guestName: "测试住客",
        guestPhone: "13800000000",
      }),
      409,
    );
    status(await post(a, `/orders/${o.id}/cancel`), 200);
    status(
      await post(desk, `/frontdesk/orders/${o.id}/check-in`, {
        roomId: 1,
        guestName: "测试住客",
        guestPhone: "13800000000",
      }),
      409,
    );
  });
  await check("A18", "checkout preserves historical inventory", async () => {
    const o = db
      .prepare(
        "SELECT o.*,c.room_id FROM orders o JOIN check_in_records c ON c.order_id=o.id WHERE o.status='CHECKED_IN' AND o.check_out_date=? LIMIT 1",
      )
      .get(D);
    assert.ok(o, "seed checkout today");
    const before = JSON.stringify(
      db
        .prepare("SELECT * FROM inventory_days ORDER BY room_type_id,stay_date")
        .all(),
    );
    status(await post(desk, `/frontdesk/orders/${o.id}/check-out`), 200);
    assert.equal(
      JSON.stringify(
        db
          .prepare(
            "SELECT * FROM inventory_days ORDER BY room_type_id,stay_date",
          )
          .all(),
      ),
      before,
    );
    assert.equal(
      db.prepare("SELECT cleaning_status FROM rooms WHERE id=?").get(o.room_id)
        .cleaning_status,
      "DIRTY",
    );
    status(await post(desk, `/frontdesk/orders/${o.id}/check-out`), 409);
    status(await post(desk, `/frontdesk/rooms/${o.room_id}/clean`), 200);
  });
  await check("A13/A15/A19/A20/A21", "room lifecycle checks", async () => {
    const rooms = status(await desk.request("/frontdesk/rooms"), 200);
    const available = rooms.find(
      (x) => !x.occupied && x.cleaningStatus === "CLEAN",
    );
    assert.ok(available);
    const o = status(
      await book(a, 0, 1, randomUUID(), available.roomTypeId),
      201,
    );
    const wrong = rooms.find((x) => x.roomTypeId !== available.roomTypeId);
    if (wrong)
      status(
        await post(desk, `/frontdesk/orders/${o.id}/check-in`, {
          roomId: wrong.id,
          guestName: "测试住客",
          guestPhone: "13800000000",
        }),
        409,
      );
    db.prepare("UPDATE rooms SET cleaning_status='DIRTY' WHERE id=?").run(
      available.id,
    );
    status(
      await post(desk, `/frontdesk/orders/${o.id}/check-in`, {
        roomId: available.id,
        guestName: "测试住客",
        guestPhone: "13800000000",
      }),
      409,
    );
    status(await post(desk, `/frontdesk/rooms/${available.id}/clean`), 200);
    const before = inv(0);
    const checked = status(
      await post(desk, `/frontdesk/orders/${o.id}/check-in`, {
        roomId: available.id,
        guestName: "测试住客",
        guestPhone: "13800000000",
      }),
      200,
    );
    assert.equal(checked.status, "CHECKED_IN");
    assert.equal(inv(0), before);
    status(
      await post(desk, `/frontdesk/orders/${o.id}/check-in`, {
        roomId: available.id,
        guestName: "测试住客",
        guestPhone: "13800000000",
      }),
      409,
    );
    status(await post(desk, `/frontdesk/orders/${o.id}/check-out`), 409);
    status(await post(desk, `/frontdesk/rooms/${available.id}/clean`), 409);
  });
  await check("A17", "overdue room remains occupied", async () => {
    const room = status(await desk.request("/frontdesk/rooms"), 200).find(
      (r) => r.overdue,
    );
    assert.ok(room, "seed overdue room");
    assert.equal(room.occupied, true);
    const o = status(await book(b, 0, 1, randomUUID(), room.roomTypeId), 201);
    status(
      await post(desk, `/frontdesk/orders/${o.id}/check-in`, {
        roomId: room.id,
        guestName: "测试住客",
        guestPhone: "13800000000",
      }),
      409,
    );
    status(await post(desk, `/frontdesk/orders/${o.id}/cancel`), 200);
  });
  await check("A16", "concurrent room allocation", async () => {
    const rooms = status(await desk.request("/frontdesk/rooms"), 200);
    let room;
    for (const r of rooms.filter(
      (r) => !r.occupied && r.cleaningStatus === "CLEAN",
    )) {
      const invRow = db
        .prepare(
          "SELECT total-reserved n FROM inventory_days WHERE room_type_id=? AND stay_date=?",
        )
        .get(r.roomTypeId, D);
      if (invRow.n >= 2) {
        room = r;
        break;
      }
    }
    assert.ok(room, "two available slots");
    const x = status(await book(a, 0, 1, randomUUID(), room.roomTypeId), 201),
      y = status(await book(b, 0, 1, randomUUID(), room.roomTypeId), 201);
    const payload = {
      roomId: room.id,
      guestName: "并发住客",
      guestPhone: "13800000000",
    };
    const res = await Promise.all([
      post(desk, `/frontdesk/orders/${x.id}/check-in`, payload),
      post(desk, `/frontdesk/orders/${y.id}/check-in`, payload),
    ]);
    assert.deepEqual(res.map((x) => x.status).sort(), [200, 409]);
    const loser = res[0].status === 409 ? x : y;
    status(await post(desk, `/frontdesk/orders/${loser.id}/cancel`), 200);
  });
  await check("A31", "cancel vs check-in atomicity", async () => {
    const room = status(await desk.request("/frontdesk/rooms"), 200).find(
      (r) => !r.occupied && r.cleaningStatus === "CLEAN",
    );
    assert.ok(room);
    const o = status(await book(a, 0, 1, randomUUID(), room.roomTypeId), 201);
    const res = await Promise.all([
      post(desk, `/frontdesk/orders/${o.id}/check-in`, {
        roomId: room.id,
        guestName: "竞争住客",
        guestPhone: "13800000000",
      }),
      post(desk, `/frontdesk/orders/${o.id}/cancel`),
    ]);
    assert.deepEqual(res.map((r) => r.status).sort(), [200, 409]);
  });
}
await check("A22", "database lock timeout rolls back", async () => {
  db.exec("BEGIN IMMEDIATE");
  try {
    const r = await book(a, 40);
    status(r, 503);
    assert.equal(r.data.code, "DATABASE_BUSY");
  } finally {
    db.exec("ROLLBACK");
  }
});
const out = resolve(".runtime/acceptance/results.json");
mkdirSync(resolve(".runtime/acceptance"), { recursive: true });
writeFileSync(
  out,
  JSON.stringify(
    { at: new Date().toISOString(), base, group, hotelDate: D, results },
    null,
    2,
  ),
);
db.close();
console.log(
  `Results: ${results.filter((x) => x.status === "PASS").length}/${results.length} passed; ${out}`,
);
if (results.some((x) => x.status === "FAIL")) process.exitCode = 1;
