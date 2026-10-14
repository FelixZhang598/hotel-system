# Final scoped re-review

Reviewed 2026-10-14. This is a follow-up on R1 and R2 in `.superpowers/sdd/2026-10-14-hotel-implementation/final-review.md`, with the implementation summary in `review-fixes.md`. The original final reviewer was unavailable for follow-up. This reviewer inspected the current `frontend/src/api/http.ts` and `backend/src/main/java/com/example/hotel/database/DemoSeeder.java`. This is not a new whole-project review. No implementation edits, unit tests, live service mutations, or browser interactions were performed by this reviewer; only this report was written.

## Verdict

R1 and R2 are resolved in the inspected source. No actionable remaining blocker was found within this follow-up scope. Root's reported browser recovery and recorded reset results support the runtime portions described below; they are not represented as checks executed by this reviewer.

## R1 — P2: Recover expired sessions when stale CSRF causes 403

Original finding: a stale authenticated tab could retain a CSRF token after session expiry. A booking or logout POST could return 403 before authentication produced 401, leaving the UI apparently signed in and repeatedly failing.

Current assessment: resolved. The common HTTP request path now probes `/api/auth/me` with a raw same-origin GET after a mutating 403. A 401 probe clears the cached token, dispatches the existing `session-expired` event, and returns an expiry `ApiError` with status 401. Direct 401 responses also clear cached CSRF. An authenticated probe refreshes CSRF while preserving the original failed request; the mutation is never automatically replayed. Failed probes or refreshes preserve the original failure instead of falsely assuming expiry. This distinguishes an expired session from a genuine role denial and avoids changing booking idempotency semantics.

Root-run runtime evidence, supplied directly to this reviewer: root logged out in another browser tab, then invoked logout from the stale main tab. The main tab displayed the login-expired toast, redirected to login, and successfully logged in again. This exercises the reported stale-CSRF logout recovery. Booking recovery is supported by inspection of the shared mutating-request branch; this reviewer did not independently execute a stale-session booking scenario or a role-denial scenario.

## R2 — P2: Future seed creation dates misorder newly created bookings

Original finding: `in.minusDays(2)` gave future-arrival demo orders creation dates in the future, so correctly sorted newly created bookings appeared below them.

Current assessment: resolved. `DemoSeeder.order()` now clamps the creation date to the earlier of arrival minus two days and hotel today minus one day. Future-arrival seeds therefore precede bookings created today, while older historical creation dates retain their chronology. Both the order's `created_at` and its initial creation event use the same corrected timestamp. This reviewer verified the expression and both uses in source, without reseeding or independently repeating the original browser ordering observation.

## Prior foundation finding F3 — evidence update only

The original final review already marked the application-lifetime database connection fix resolved. This scoped review did not reopen its implementation. Root reports that the idle manually started instance on port 18083 now blocks reset, and that reset succeeds after stopping it. The inspected `evidence/reset-results.json` records PASS for both cases, with the expected database-open refusal and successful reset messages. `evidence/database-lifetime-results.json` separately records idle file ownership and release evidence from prior verification. These are existing runtime artifacts inspected by this reviewer, not commands or scenarios executed here.

## Verification provenance

- Independent in this follow-up: read-only inspection of the original findings, repair report, two changed source files, and reset/lifetime result artifacts.
- Repair-agent report: frontend production build and backend packaging passed, with Maven test execution and test compilation skipped. These commands were not rerun by this reviewer.
- Root-owned: browser stale-tab logout recovery and successful subsequent login; idle manual-instance reset refusal followed by successful stopped-instance reset.
- No unit tests were executed by this reviewer. Broader implementation review and acceptance remain documented in the original reports and root-owned evidence.
