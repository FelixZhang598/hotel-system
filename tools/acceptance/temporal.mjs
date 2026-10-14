import { spawn } from "node:child_process";
import { mkdirSync, openSync, closeSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { assert, status } from "./assertions.mjs";
import { Session } from "./session.mjs";
const root = process.cwd(),
  dir = resolve(".runtime/acceptance/temporal");
mkdirSync(dir, { recursive: true });
const cases = [
  ["before-midnight", "2026-10-14T15:59:59Z", "2026-10-14"],
  ["after-midnight", "2026-10-14T16:00:01Z", "2026-10-15"],
];
const results = [];
for (const [name, instant, day] of cases) {
  const fd = openSync(resolve(dir, name + ".log"), "w");
  const child = spawn(
    "java",
    [
      "--enable-native-access=ALL-UNNAMED",
      "-jar",
      resolve("backend/target/hotel-backend-1.0.0.jar"),
      "--spring.profiles.active=acceptance",
    ],
    {
      cwd: resolve("backend"),
      env: {
        ...process.env,
        SERVER_PORT: "18082",
        HOTEL_DB: resolve(dir, name + ".db"),
        HOTEL_FIXED_INSTANT: instant,
      },
      stdio: ["ignore", fd, fd],
    },
  );
  closeSync(fd);
  try {
    const client = new Session("http://127.0.0.1:18082");
    let ready = false;
    for (let n = 0; n < 100; n++) {
      if (child.exitCode !== null) throw Error("Backend exited before ready");
      try {
        const r = await client.request("/meta");
        if (r.status === 200) {
          ready = true;
          assert.equal(r.data.today, day);
          break;
        }
      } catch (e) {
        if (e.code === "ERR_ASSERTION") throw e;
      }
      await new Promise((r) => setTimeout(r, 200));
    }
    assert.ok(ready, "startup readiness");
    await client.login("guest1");
    const next = new Date(day + "T00:00:00Z");
    next.setUTCDate(next.getUTCDate() + 1);
    const out = next.toISOString().slice(0, 10);
    const o = status(
      await client.request("/orders", {
        method: "POST",
        headers: { "Idempotency-Key": name },
        body: { roomTypeId: 1, checkIn: day, checkOut: out, guestCount: 1 },
      }),
      201,
    );
    assert.equal(o.checkIn, day);
    results.push({ name, instant, today: day, status: "PASS" });
  } finally {
    child.kill("SIGTERM");
    await new Promise((r) => child.once("exit", r));
  }
}
writeFileSync(resolve(dir, "results.json"), JSON.stringify(results, null, 2));
console.log(JSON.stringify(results));
