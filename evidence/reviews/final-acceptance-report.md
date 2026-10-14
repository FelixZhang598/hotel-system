# Final backend acceptance report

Executed 2026-10-14 Asia/Shanghai against the final hardened JAR. No business source changed, no unit tests executed, no Git commits. Live demo8080/5173 and root18080 were not contacted, stopped, reset, or modified. Only this task's own backend processes on18084 and temporal runner processes on18082 were launched and stopped.

## Artifact and environment

- Repository: /Users/felix/IdeaProjects/hotel-system
- Node: v26.8.1; Java:26.0.2.
- JAR: /Users/felix/IdeaProjects/hotel-system/backend/target/hotel-backend-1.0.0.jar; mtime 2026-10-14T07:47:09.982Z; SHA256 de7cbaa444d2813b6eb5684e80bb16e0e8727c0ceb2aa1d25865fd10457ecd53.
- JAR was newer than latest backend source; no rebuild needed. Existing build explicitly skipped unit tests.
- Main isolated backend env: SERVER_PORT=18084, HOTEL_DB=/Users/felix/IdeaProjects/hotel-system/.runtime/acceptance/final/hotel.db; acceptance profile; inherited HOTEL_FIXED_DATE/HOTEL_FIXED_INSTANT removed.
- Launch command (cwd backend): java --enable-native-access=ALL-UNNAMED -jar /Users/felix/IdeaProjects/hotel-system/backend/target/hotel-backend-1.0.0.jar --spring.profiles.active=acceptance.
- Baseline hotel date from API: 2026-10-14.

## Actual commands and outcomes

1. lsof -nP -iTCP:18084 -sTCP:LISTEN and equivalent18082: exit1/empty, both free before own launch.
2. HOTEL_ACCEPTANCE_DB=/Users/felix/IdeaProjects/hotel-system/.runtime/acceptance/final/hotel.db node tools/acceptance/run.mjs --base-url http://127.0.0.1:18084: exit0; 24/24 API groups PASS. Covers A01–A25 except A26's later separate execution, plus A30–A32; room lifecycle group combines A13/A15/A19/A20/A21. Includes actual concurrent booking/allocation/state races and held write-lock503/rollback. Each group reconciled inventory bounds/ledger, room occupants, and order/stay consistency through tools/acceptance/assertions.mjs. Snapshot: evidence/api-results.json.
3. node .runtime/acceptance/final/verify.mjs: corrected standalone orchestration exit0; A26 PASS. Stopped own backend with SIGTERM, snapshotted all8 business tables, restarted same DB, snapshotted again, deep-compared exact contents, relogged guest1/frontdesk, compared identity/order/room/catalog API responses. Table counts: {"check_in_records":5,"inventory_days":291,"order_events":52,"order_nights":42,"orders":35,"room_types":3,"rooms":18,"users":3}. Snapshot files are under .runtime/acceptance/final/; summary evidence/restart-results.json.
4. lsof -nP -- /Users/felix/IdeaProjects/hotel-system/.runtime/acceptance/final/hotel.db after all API/database activity ceased and800ms idle: exit0, own Java PID33612 holds the file; after own shutdown exit1/empty. FoundationF3 lifetime-ownership condition PASS. The full demo reset rejection/success workflow is intentionally left to root; this report does not claim that reset itself ran. Evidence: evidence/database-lifetime-results.json.
5. node tools/acceptance/temporal.mjs: exit0;2/2 temporal cases PASS using latest JAR, port18082, acceptance profile, independent before-midnight/after-midnight databases. HOTEL_FIXED_INSTANT=2026-10-14T15:59:59Z yields Shanghai today2026-10-14; HOTEL_FIXED_INSTANT=2026-10-14T16:00:01Z yields today2026-10-15. Each authenticates and creates a valid same-day order201. Snapshot evidence/temporal-results.json. This establishes A29 backend midnight/date behavior; browser timezone behavior belongs to root frontend acceptance.
6. Final lsof on18084/18082: both exit1/empty; all this task's instances stopped. Details .runtime/acceptance/final/final-ports.json.

## Runner and orchestration observations

- Reported to root before executing temporal runner: temporal.mjs has fixed DB paths and fixed idempotency keys; running again against existing DBs would return200 replay while script expects201. Preserved previous temporal results/databases intact at /Users/felix/IdeaProjects/hotel-system/.runtime/acceptance/temporal-before-final-1791964442412, then ran the unchanged temporal runner against new isolated files. No runner code modified.
- Initial temporary verification harness import path was one directory too high and failed before any process launched; corrected only the runtime harness.
- Full API suite then passed24/24, but the temporary persistence harness requested /room-types without contract-required dates and received400; own backend shut down cleanly. This was a harness error, not a business failure. Preserved original failed run under .runtime/acceptance/final/verification-first-attempt.json and verify-first-attempt.mjs. Corrected query to include checkIn/checkOut, reused the same populated database, and ran persistence/lifetime checks only. The mutating API suite was not repeated on dirty fixtures.
- Relevant actual logs: .runtime/acceptance/final/initial.log and restart.log; temporal logs under .runtime/acceptance/temporal/. Full command/stdout/stderr/events JSON: .runtime/acceptance/final/verification.json and temporal-command.json.

## Limits

A27/A28/A33 UI and documentation checks, A29 browser timezone, and full demo reset guard execution are outside this backend assignment and remain root-owned. Backend-only checks reported here all passed.
