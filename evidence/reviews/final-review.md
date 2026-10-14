# Final whole-project review

Reviewed 2026-10-14. Scope: all new backend/frontend source, schema, runtime scripts and the API/spec contracts, including untracked implementation files relative to docs-only baseline ce245ce. No implementation edits, unit tests, backend mutations or browser interactions were performed. This review writes only this report; root owns live acceptance and browser evidence.

## Verdict

Spec compliance and code quality: one independently identified P2 frontend session-recovery defect remains. Root also reported the seed timestamp defect below and is fixing it. No additional actionable backend inventory, state-transition, authorization or lifecycle defect was found within the agreed MVP scope. Approval is pending these fixes and root's remaining acceptance/browser verification.

## Finding

### R1 — P2: Recover expired sessions when stale CSRF causes 403

File: `frontend/src/api/http.ts:45-52` (cached token reuse at lines 27-29; related logout flow `frontend/src/api/auth.ts:15-18`).

When a logged-in tab remains open beyond the configured 30-minute session timeout, its in-memory user and CSRF token remain present. The next booking or logout POST sends the old CSRF token. Spring Security's CSRF filter rejects the request with 403 before its authentication entry point can return 401. The client only dispatches `session-expired` for 401, and never clears or refreshes the token on 403. Booking retries and the visible logout control therefore keep returning the generic permission/CSRF error while the UI still shows the user as signed in. A new login is unavailable in the header until navigation/reload happens to trigger a protected GET. Order-detail and room actions partially mask this through their subsequent protected GET, but booking and logout do not.

Handle the ambiguous 403 without treating every role denial as expiry: inspect `/auth/me` after a mutating 403, invalidate local auth/token and dispatch the existing expiry event when that returns 401; if still authenticated, refresh the CSRF token and provide a safe retry path. Do not blindly replay booking mutations with a new idempotency key. Verify a login followed by session invalidation/expiry and a booking POST/logout POST recovers to login rather than a persistent 403 loop. This finding is based on the actual security configuration and request control flow, not an independently executed expiry scenario.

### R2 — P2: Future seed creation dates misorder newly created bookings (root-observed)

File: `backend/src/main/java/com/example/hotel/database/DemoSeeder.java`, `order()` creation timestamp expression `in.minusDays(2)`.

Root's browser acceptance observed newly created orders appearing below future-arrival seed orders. Source confirms arrivals at D+3/D+4 produce creation timestamps at D+1/D+2. The API's descending creation ordering is correct, but these seeds represent bookings created in the future and make the demo's latest booking harder to locate. Root is changing creation to the earlier of `in.minusDays(2)` and `today.minusDays(1)`; this is an appropriate scoped fix. This review did not modify the seed or independently repeat the browser observation.

## Prior foundation finding F3

Resolved in current source: `backend/src/main/java/com/example/hotel/config/DatabaseConfig.java:32-36` creates an application-lifetime connection with a shutdown close hook. This closes the idle unpooled-datasource gap in the existing `lsof` reset guard. Read-only observation during this review showed Java PID 31653 retaining FD 8u on `data/hotel.db` while idle. No reset or shutdown was attempted. The earlier foundation report's F3 is stale and should not remain an open finding.

## Reviewed strengths and evidence limits

- Booking decisions occur inside the unified IMMEDIATE transaction, check per-night coverage, reserve with conditional updates, and create order/night/event records atomically. The `(user_id,idempotency_key)` constraint and hash comparison protect replay; a replay returns the current order state before new-date validation.
- Cancellation/no-show release only HELD details from hotel today onward. State changes require the expected original state, preventing repeated release or room assignment effects. Check-in rechecks physical occupancy, clean status, room type and interval conflicts in the transaction; checkout leaves historical inventory intact and marks the room dirty. The partial unique index protects active room occupancy.
- Session roles protect the complete guest/frontdesk route families, owner lookup hides other guests' orders, and SQL filters use bound parameters. Dates and state rules are enforced server-side; fixed dates require the acceptance profile.
- Frontend main flows and APIs match the contract. Booking retains a request key in session storage across uncertain failures, room selection uses fresh candidates, and detail views show overdue/no-show conditions. Browser layout/usability acceptance remains root-owned.
- Runtime start/stop checks owned PIDs and readiness; reset checks application/file ownership; the current lifetime connection addresses the previously reported idle-owner defect. `node --check tools/demo.mjs` passed during this review.
- Existing `.runtime/acceptance/results.json` contains root-run PASS results; those are not represented as tests executed by this reviewer. Root reported 24/24 API and 2/2 temporal acceptance and was rerunning the hardened jar. No unit tests were run.
