# Backend implementation report

Completed tasks 1–4 under frozen `docs/api-contract.md`. No API contract edits and no unit tests created or executed. All implementation changes are in `backend/`, plus `docs/backend-seeds.md` and this requested report. No Git commits performed by implementer.

## Delivered

- Java 26 / Spring Boot 4.0.8 / SQLite JDBC 3.53.4.0; official only-script Maven Wrapper 3.3.4 fixes Maven 3.9.16. Official compatibility checked at https://docs.spring.io/spring-boot/4.0/system-requirements.html.
- 29 Java files in auth/common/config/database/catalog/order/frontdesk layers, 8 constrained business tables, foreign keys, indexed order filters, unique active stay per room.
- Every business write uses `WriteTransactions`/Spring TransactionTemplate with SQLite driver's IMMEDIATE transaction mode. Each connection has WAL, foreign_keys ON, busy_timeout 3000. SQLException BUSY/LOCKED is mapped through both JDBC and transaction-start exception chains to 503 DATABASE_BUSY.
- Stable inventory and seeds: 3 types, 6 rooms each, user IDs 1/2/3, order IDs 1–9; detailed seed matrix in docs/backend-seeds.md. Past no-show allocation bookkeeping and completed historic reservations preserved.
- Session login with BCrypt, session ID rotation, HttpOnly/SameSite=Lax cookie, CSRF before login and after authentication changes, full JSON security errors.
- Full catalog/order/frontdesk contract APIs, exact own-order protection, integer cents snapshots, 201 create/200 idempotency replay/409 conflicting parameters, conditional status transitions and future-only held-night release.
- Room eligibility requires matching type, CLEAN, no open stay and no overlapping allocation. Actual open stays keep overdue rooms occupied. Checkout is date-gated and makes the room DIRTY; clean is empty-room-only/idempotent.
- Default system Asia/Shanghai clock; `HOTEL_FIXED_DATE` / `HOTEL_FIXED_INSTANT` accepted only in `acceptance` profile. Precisely one business date snapshot per mutation.
- Fractional JSON numeric IDs/counts rejected. Unknown routes/methods use shared error shape. Source formatted with Google Java Format 1.37.0.
- Dedicated idle lifetime JDBC connection allows lsof detection for safe reset of running backend DB even on nondefault ports; no transaction uses it.

## Actual verification

Final build: `cd backend && ./mvnw -B -Dmaven.test.skip=true package`, exit 0, BUILD SUCCESS, 2026-10-14 15:47:09 Asia/Shanghai. Log `/tmp/hotel-backend-build.log`. Executable artifact `backend/target/hotel-backend-1.0.0.jar`. Tests skipped explicitly.

Final runtime: Java 26.0.2 `--enable-native-access=ALL-UNNAMED`, port 18081, isolated `.runtime/backend-smoke/hotel.db`. curl requests executed through Node child_process with persistent cookie jars; final exit 0:

- CSRF/login/me identity; pre-login session ID changed on login (initial smoke); guest frontdesk 403; anonymous me 401, all JSON.
- New guest order 201 with correct DTO/price; replay 200, changed parameters 409 IDEMPOTENCY_CONFLICT.
- Missing CSRF 403; fractional ID and guestCount 400; invalid status 400; unknown route 404 shared error; other guest order 404.
- Future order cancellation 200, duplicate 409.
- Same-day candidates excluded occupied 101 and dirty 103; seed1 checked in102 and trimmed guest name; premature checkout409.
- Seed2 checkout200/COMPLETED; room101 clean200 and repeat200.
- Seed3 spanning no-show200; seed9 fully past no-show200; occupied overdue201 clean409.
- Logout204 and subsequent me401.
- lsof confirmed idle lifetime file owner Java PID31379 for isolated smoke DB after requests.

Root independently reported full live API acceptance 24/24 passing, including last-room booking races, same-room checkin competition, checkin/cancel race, rollback and 3000ms BUSY→503. Root also reported Shanghai midnight acceptance before/after cases2/2. These root results preceded final numeric/error-format/lifetime-connection hardening; root should rerun full suite on final artifact.

## Handoff and limits

No known missing business endpoint. Default port8080; env HOTEL_DB defaults ../data/hotel.db relative to backend cwd. Current deployment is a local single backend instance with SQLite single-writer semantics; no distributed capacity claims. Default HTTP demo should use same-host frontend proxy for cookies; production HTTPS is outside task scope.

Fixed clocks are acceptance-only, as required. Normal restart does not alter existing users/seeds. Schema uses IF NOT EXISTS for initial creation; this MVP does not provide database migrations for arbitrary externally modified databases.

The implementer's smoke process is stopped after verification. Root owns integration/startup/acceptance and Git history.
