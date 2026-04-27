## Deferred Items — Phase 01.5

### Pre-existing Test Failure (Out of Scope)

**Test:** `CorsIntegrationTest.actual_response_for_frontend_origin_includes_cors_headers()`

**Root cause:** Test calls `/actuator/health` and expects HTTP 200. In test environments
without Redis, the reactive Redis health indicator reports DOWN → Boot returns 503. This
failure existed before Phase 01.5-01 changes (confirmed by `git stash` regression check).

**Fix approach:** Either exclude the Redis health indicator in the test profile
(`management.health.redis.enabled=false` in `application-test.yml`), or change the CORS
test to hit an endpoint that does not depend on Redis health (e.g., `/actuator/info` or
`/login`).

**File:** `backend/api/src/test/java/com/zeromail/api/security/CorsIntegrationTest.java`
**Line:** 43 (`/actuator/health` call)
**Deferred to:** Plan 01.5-02 or a dedicated cleanup task.
