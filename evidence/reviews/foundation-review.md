# Foundation review — scoped re-review

Reviewed 2026-10-14 after fixes. Scope remains backend config/database/common/auth/schema/pom and tools/demo.mjs; this re-review checks only F1–F5. Requirements: docs/api-contract.md and docs/superpowers/specs/2026-10-14-hotel-design.md. Java 26 is authoritative; Spring Boot 4.0.8 is accepted. No implementation files changed and no unit tests executed. Unfinished business APIs are outside this review.

## Verdicts

- Spec compliance: **requires one remaining fix, F3**. Fresh initialization and transaction-busy error mapping are resolved. Authentication, CSRF, schema and transaction wiring match the reviewed requirements, supported by actual API acceptance evidence.
- Code quality: **requires one remaining fix, F3**. Partial-start recovery and ordinary signal lock cleanup are resolved. The lsof reset guard covers open files but cannot identify an idle backend using this unpooled datasource.

## Findings status

### F1 — Resolved: Fresh initialization

File: backend/src/main/java/com/example/hotel/database/DemoSeeder.java:35.

The seed order INSERT now has 14 placeholders matching 14 schema columns and 14 arguments. Root reported fresh startup succeeded, and the checked acceptance results contain 24 PASS entries against the initialized backend. No remaining blocker from the original placeholder mismatch. Explicit column lists remain an optional maintenance improvement, not a blocking finding.

### F2 — Resolved: Busy transaction creation error mapping

File: backend/src/main/java/com/example/hotel/common/ApiExceptionHandler.java:16-17.

The handler now covers DataAccessException and TransactionException, including CannotCreateTransactionException raised when IMMEDIATE lock acquisition fails. The root-run A22 result is PASS for the actual database-lock-timeout case, confirming required 503 DATABASE_BUSY behavior. This resolves the originally reported transaction-begin failure.

### F3 — P1, still open: Reset can unlink an idle live database

File: tools/demo.mjs:45-48; related datasource: backend/src/main/java/com/example/hotel/config/DatabaseConfig.java:15.

The new lsof guard rejects reset when database/WAL/SHM files are open and refuses when lsof is unavailable or returns an unexpected status. This protects active connections on arbitrary ports, but SQLiteDataSource creates a new connection per getConnection and JdbcTemplate closes it when the operation ends. An idle independently launched backend on another port can therefore have no database file open. It is absent from demo-processes.json, leaves 8080 free, and produces lsof status 1 with empty output, so reset still unlinks its live configured database. Its next request then encounters an empty database without rerunning startup initialization.

Concrete read-only evidence in this re-review: Java PID 30701 listened on port 18080; GET http://127.0.0.1:18080/api/meta returned 200 with the expected hotel metadata; lsof -t -- .runtime/acceptance/hotel.db returned status 1 with empty output while that backend was idle. The acceptance database was not reset or modified during this check. Exact installed SQLiteDataSource bytecode confirms getConnection creates a connection directly rather than retaining a pool.

Resolve with lifetime ownership tied to the canonical database path. For example, keep a dedicated datasource connection open until backend shutdown so the existing lsof guard detects the entire running lifetime, or implement a per-database application lifecycle lock checked by reset. Verify that an idle independently started backend on an overridden port using data/hotel.db blocks reset, then stopping it allows reset. Checking files only during a request is insufficient.

### F4 — Resolved: Partial demo startup

File: tools/demo.mjs:25-27.

The existing-instance branch now requires two owned processes and successful backend/frontend readiness checks. A partial recorded instance goes through stop before rebuilding and relaunching. The original single-survivor false-success path is removed. This fix was reviewed statically; no process was deliberately terminated in this re-review.

### F5 — Resolved for ordinary interruption; forced-termination recovery documented in command output

File: tools/demo.mjs:11-14 and 51.

The lock now records controller PID, SIGINT/SIGTERM call releaseLock, and normal completion uses the same idempotent cleanup. The error message gives safe manual recovery instructions for a previous controller killed forcibly: confirm the recorded PID has exited before deleting the lock. Detached application PIDs remain recorded for a subsequent stop. SIGKILL cannot run cleanup; requiring explicit stale-lock recovery is an acceptable demo limitation. No signal was injected in this re-review.

## Evidence

- Read .runtime/acceptance/results.json: recorded run at 2026-10-14T07:44:44.115Z against http://127.0.0.1:18080, with 24/24 results PASS. Relevant checks include A01 authentication/roles, A02 ownership, A24 CSRF, A32 session invalidation/token refresh, A05 multi-night rollback, A06 concurrent inventory, A16 concurrent room allocation, A31 cancel/check-in atomicity, and A22 lock timeout. These are root-run live API acceptance results, not unit tests and not runs independently repeated by this reviewer.
- Re-ran node --check tools/demo.mjs successfully.
- Earlier exact SQLite JDBC 3.53.4.0 and Spring JDBC 7.0.9 bytecode inspection confirms setAutoCommit(false) begins IMMEDIATE during doBegin before the transaction callback. TransactionTemplate rolls back runtime business exceptions; no duplicate manual BEGIN exists in this foundation.
- Schema inventory checks, foreign keys, idempotency uniqueness and the partial unique index for unclosed room stays remain appropriate. Session/CSRF and acceptance-only fixed clock wiring had no defects in the original scoped review.
- This re-review ran only code reads, syntax checks, bytecode reads, lsof process/file observations, and one read-only metadata request. No implementation edits, service launches/stops, reset operations, database mutations, browser acceptance, or unit tests were performed.

Final approval pending F3 only. This report supersedes the initial snapshot that listed five open issues.
