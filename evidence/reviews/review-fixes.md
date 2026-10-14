# Final review fixes

Implemented 2026-10-14. Scope: R1 and R2 from `final-review.md`. No unit tests, git commits, live demo restarts, database changes, or acceptance-service changes were performed by this agent. Browser/session-invalidation acceptance remains root-owned.

## Modified source paths

- `frontend/src/api/http.ts`
- `backend/src/main/java/com/example/hotel/database/DemoSeeder.java`

## R1 — Expired-session recovery

After a mutating request returns 403, the HTTP client uses a raw same-origin GET to `/api/auth/me` to distinguish expiry from role denial. A 401 from that probe clears the cached CSRF token, emits the existing `session-expired` event, and returns a 401 `ApiError` with the expiry message. This applies to booking and logout alike; the existing application event handler clears the displayed user and navigates to login.

If the probe confirms an authenticated session, the client refreshes the CSRF token and still rejects the original request. It does not replay a mutation. A genuine role denial retains its original 403 and does not emit an expiry event. Probe/refresh network failures preserve the original failure rather than assuming logout.

Every CSRF refresh discards the prior token before fetching, and direct 401 responses also clear the cache. The existing login flow fetches a fresh token before login and again after successful authentication. No booking payload, session-storage idempotency record, or retry-key logic was changed.

## R2 — Seed creation timestamps

Seed `created_at` now uses the earlier of arrival minus two days and hotel today minus one day. Future arrivals therefore no longer produce future booking creation timestamps. Earlier historical creation dates retain their ordering, and the creation event uses the same corrected timestamp.

## Fresh verification

- Frontend formatting: local Prettier `--write src/api/http.ts`, exit 0 (already formatted).
- Backend formatting: `java -jar /tmp/hotel-google-java-format.jar --replace src/main/java/com/example/hotel/database/DemoSeeder.java`, exit 0.
- Frontend: `npm run build`, exit 0; `vue-tsc --noEmit` and Vite production build completed. Vite reported the existing large-chunk warning for the shared application bundle.
- Backend: `./mvnw -Dmaven.test.skip=true package`, exit 0, `BUILD SUCCESS`; output explicitly states test sources were not compiled and tests were skipped. Updated artifact: `backend/target/hotel-backend-1.0.0.jar`.

Runtime expiry recovery and browser login/logout behavior are not claimed as verified here; root will exercise the refreshed frontend and invalidated sessions. Generated frontend `dist/` and backend `target/` outputs were rebuilt by the commands above.
