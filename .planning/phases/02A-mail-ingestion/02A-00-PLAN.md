---
phase: 02A-mail-ingestion
plan: "00"
type: execute
wave: 0
depends_on: []
files_modified:
  - backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntityTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntityTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/model/GmailIngestionHealthTest.java
  - backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java
  - backend/worker/src/test/java/com/zeromail/worker/GmailWatchSchedulerTest.java
  - backend/worker/src/test/java/com/zeromail/worker/test/MockGoogleOidcServer.java
  - backend/worker/src/test/java/com/zeromail/worker/test/MockGmailHistoryServer.java
  - apps/web/features/triage/components/PauseBanner.test.tsx
  - apps/web/features/triage/hooks/useToggleTriagePause.test.tsx
  - apps/web/features/gmail/components/ReconnectPrompt.test.tsx
  - apps/web/__tests__/architecture/phase-02a-files.test.ts
autonomous: true
requirements:
  - MAIL-01
  - MAIL-02
  - MAIL-03
  - MAIL-04
  - MAIL-05
  - MAIL-06

must_haves:
  truths:
    - "All 16 Wave 0 test files exist on disk and compile (backend tests fail at RED; frontend tests fail at RED)"
    - "MockGoogleOidcServer and MockGmailHistoryServer fixtures can generate signed JWT tokens and serve fake JWKS"
    - "GmailIngestionHealthTest asserts fromId fail-loud contract"
    - "PauseBanner.test.tsx fails with import error (component doesn't exist yet)"
    - "phase-02a-files.test.ts asserts file presence and i18n key parity"
    - "TriagePauseControllerTest.java exists with @Disabled RED scaffold (MAIL-06 has automated coverage)"
    - "PubSubIdempotencyTest.java exists with @Disabled RED scaffold (MAIL-04 dedup path has automated coverage)"
    - "MeControllerTest.java exists with @Disabled RED scaffold (triagePaused + ingestionHealth fields have automated coverage)"
    - "ReconnectPrompt.test.tsx exists with it.skip RED scaffold (ingestionHealth gate has automated coverage)"
  artifacts:
    - path: "backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java"
      provides: "RED OIDC verification test — 5 test cases (valid passes, wrong aud/email/exp/sig → 401)"
    - path: "backend/worker/src/test/java/com/zeromail/worker/test/MockGoogleOidcServer.java"
      provides: "Hermetic JWKS fixture for OIDC tests"
    - path: "apps/web/__tests__/architecture/phase-02a-files.test.ts"
      provides: "File-presence + i18n parity guard"
    - path: "backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java"
      provides: "RED scaffold for MAIL-06 persistence automated coverage"
    - path: "backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java"
      provides: "RED scaffold for MAIL-04 idempotency automated coverage"
    - path: "backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java"
      provides: "RED scaffold for /me endpoint triagePaused + ingestionHealth contract"
    - path: "apps/web/features/gmail/components/ReconnectPrompt.test.tsx"
      provides: "RED scaffold for MAIL-05 ingestionHealth gate"
  key_links:
    - from: "PubSubOidcAuthFilterTest"
      to: "MockGoogleOidcServer"
      via: "setCertificatesLocation() override"
      pattern: "MockGoogleOidcServer|mock.*jwks|certLocation"
    - from: "GmailHistoryProcessorTest"
      to: "MockGmailHistoryServer"
      via: "stubbed history.list response"
      pattern: "MockGmailHistoryServer|stubHistory|historyResponse"
    - from: "MeControllerTest"
      to: "MeResponse.triagePaused + MeResponse.gmailConnectionStatus.ingestionHealth"
      via: "JSON serialization assertions"
      pattern: "triagePaused|ingestionHealth"
---

<objective>
Create all 16 Wave 0 RED-scaffold test files that define the acceptance contract for Waves 1-3. Tests must compile (or fail with clear import-not-found errors) and be RED-by-design — they reference classes that don't exist yet.

Purpose: Establish the Nyquist-compliant verification spine before any production code is written. This is the established Phase 01.3/01.4/01.5/01.6 pattern.

Output: 16 test files — 12 backend JUnit 5 + 2 hermetic fixtures + 1 frontend Vitest architecture guard + 4 frontend component/hook tests.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/02A-mail-ingestion/02A-CONTEXT.md
@.planning/phases/02A-mail-ingestion/02A-RESEARCH.md
@.planning/phases/02A-mail-ingestion/02A-VALIDATION.md
@.planning/phases/02A-mail-ingestion/02A-PATTERNS.md

<interfaces>
<!-- Existing test infrastructure to follow -->
<!-- From Phase 01.2.1 pattern: PostgresContainerTest base -->
Existing test base classes (analogs):
- `backend/core/src/test/java/com/zeromail/core/shared/PostgresContainerTest.java`
- `backend/api/src/test/java/com/zeromail/api/ApiPostgresTestBase.java`
- `backend/core/src/test/java/com/zeromail/core/gmail/persistence/OnboardingStepPersistenceTest.java`
- `backend/api/src/test/java/com/zeromail/api/MultiTenantLeakIntegrationTest.java`

Frontend test analogs:
- `apps/web/__tests__/architecture/feature-folders.test.ts`
- `apps/web/__tests__/features/account/me-cache-dedupe.test.ts`
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Backend Wave 0 RED scaffolds — filter, controller, persistence tests + hermetic fixtures + MAIL-04/06 coverage scaffolds</name>
  <files>
    backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java,
    backend/core/src/test/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntityTest.java,
    backend/core/src/test/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntityTest.java,
    backend/core/src/test/java/com/zeromail/core/gmail/model/GmailIngestionHealthTest.java,
    backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java,
    backend/worker/src/test/java/com/zeromail/worker/GmailWatchSchedulerTest.java,
    backend/worker/src/test/java/com/zeromail/worker/test/MockGoogleOidcServer.java,
    backend/worker/src/test/java/com/zeromail/worker/test/MockGmailHistoryServer.java
  </files>

  <read_first>
    - backend/core/src/test/java/com/zeromail/core/shared/PostgresContainerTest.java
    - backend/api/src/test/java/com/zeromail/api/MultiTenantLeakIntegrationTest.java
    - backend/core/src/test/java/com/zeromail/core/gmail/persistence/OnboardingStepPersistenceTest.java
    - backend/api/src/test/java/com/zeromail/api/ApiPostgresTestBase.java (if present)
    - backend/worker/src/main/java/com/zeromail/worker/HealthcheckScheduler.java
    - .planning/phases/02A-mail-ingestion/02A-RESEARCH.md (Pattern 2 TokenVerifier, Pattern 4 SKIP LOCKED, Pattern 9 GmailIngestionHealth)
    - .planning/phases/02A-mail-ingestion/02A-VALIDATION.md (Wave 0 Requirements list)
    - CLAUDE.md (Conventions section)
  </read_first>

  <action>
Create all 12 backend test files + 2 fixtures as RED scaffolds. Each test references classes that don't exist yet — this is intentional.

**`PubSubOidcAuthFilterTest.java`** — package `com.zeromail.api.security`. Extends nothing (unit test). Import `com.zeromail.api.security.PubSubOidcAuthFilter` (RED). Five `@Test` methods:
1. `validToken_passes()` — builds a valid signed JWT with correct aud + email + iss, calls `doFilterInternal`, asserts chain is called (verify mock `FilterChain`)
2. `wrongAudience_returns401()` — token with wrong `aud`, asserts `response.getStatus() == 401`
3. `wrongEmail_returns401()` — token with correct aud but wrong email claim
4. `expiredToken_returns401()` — token with `exp` in the past
5. `badSignature_returns401()` — token signed with wrong RSA key
All test cases rely on `MockGoogleOidcServer` to serve JWKS at a local URL. `PubSubOidcAuthFilter` is constructed with `audience="https://test.example/internal/pubsub/gmail"` and `saEmail="pubsub-sa@test-project.iam.gserviceaccount.com"` + `setCertificatesLocation(mockServer.jwksUrl())` override. Use `MockHttpServletRequest` / `MockHttpServletResponse` from `spring-test`.

**`GmailPubSubControllerIntegrationTest.java`** — package `com.zeromail.api.controllers`. `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@ActiveProfiles("test")`. Imports `com.zeromail.api.controllers.GmailPubSubController` (RED). Uses `RestClient` + `@LocalServerPort` (NOT MockMvc — per STATE.md decision). Five `@Test` methods:
1. `missingAuthHeader_returns401()` — POST `/internal/pubsub/gmail` no auth → assert 401
2. `validPush_knownTenant_returns200()` — valid OIDC token + valid payload for known tenant → assert 200 + `pubsub_delivery` row exists
3. `validPush_unknownEmail_returns200_dropsSilently()` — valid token but email not in `gmail_connections` → 200, no row
4. `duplicatePush_idempotent()` — same `messageId` twice → 200 both times, one row in `pubsub_delivery`
5. `invalidPayload_returns400()` — malformed base64 data → 400

**`MeControllerTest.java`** — package `com.zeromail.api.controllers`. Covers the `/me` endpoint contract for the new `triagePaused` and `gmailConnectionStatus.ingestionHealth` fields added in Plan 03 Task 2. `@Disabled("Wave 0 RED scaffold — implementation pending in Plan 03")`. Uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestClient`. Three `@Test` methods that will compile ONLY after `MeResponse.java` is extended:
```java
@Disabled("Wave 0 RED scaffold — implementation pending in Plan 03")
class MeControllerTest extends ApiPostgresTestBase {

    @LocalServerPort int port;

    @Test
    void me_response_contains_triagePaused_field() {
        // When: authenticated user calls GET /me
        // Then: response body contains "triagePaused" key with boolean value
        // Assert: MeResponse.triagePaused is present in JSON
        RestClient client = RestClient.create("http://localhost:" + port);
        // RED: MeResponse does not have triagePaused field yet
        // This test compiles once MeResponse is updated in Plan 03 Task 2
        ResponseEntity<com.zeromail.api.dto.account.MeResponse> response =
            client.get().uri("/me")
                  .retrieve().toEntity(com.zeromail.api.dto.account.MeResponse.class);
        assertThat(response.getBody().triagePaused()).isNotNull(); // RED: field missing
    }

    @Test
    void me_response_contains_gmailConnectionStatus_with_ingestionHealth() {
        // Assert: MeResponse.gmailConnectionStatus.ingestionHealth is present
        RestClient client = RestClient.create("http://localhost:" + port);
        ResponseEntity<com.zeromail.api.dto.account.MeResponse> response =
            client.get().uri("/me")
                  .retrieve().toEntity(com.zeromail.api.dto.account.MeResponse.class);
        // RED: gmailConnectionStatus field missing until Plan 03 Task 2
        assertThat(response.getBody().gmailConnectionStatus()).isNotNull();
        assertThat(response.getBody().gmailConnectionStatus().ingestionHealth())
            .isIn("HEALTHY", "WATCH_UNHEALTHY", "HISTORY_LOST");
    }

    @Test
    void me_response_json_shape_serializes_cleanly() {
        // Assert: JSON does not contain unexpected null fields
        // RED: will fail until MeResponse extended + from() factory updated
        RestClient client = RestClient.create("http://localhost:" + port);
        String raw = client.get().uri("/me")
                           .retrieve().body(String.class);
        assertThat(raw).contains("\"triagePaused\"");
        assertThat(raw).contains("\"ingestionHealth\"");
    }
}
```
Note: All three tests are `@Disabled` so the file compiles even though `MeResponse.triagePaused()` doesn't exist yet. When Plan 03 Task 2 is implemented, remove the `@Disabled` annotation and the tests become GREEN.

**`TriagePauseControllerTest.java`** — package `com.zeromail.api.controllers`. RED scaffold for MAIL-06. `@Disabled("Wave 0 RED scaffold — implementation pending in Plan 03")`. Imports `com.zeromail.api.controllers.TriagePauseController` (RED until Plan 03). Two `@Test` methods:
```java
@Disabled("Wave 0 RED scaffold — implementation pending in Plan 03")
class TriagePauseControllerTest extends ApiPostgresTestBase {

    @LocalServerPort int port;

    @Test
    void putTriagePause_true_persists_triage_paused() {
        // When: authenticated user PUT /tenant/triage-pause {paused: true}
        // Then: tenants.triage_paused = true for that tenant
        // Then: response body {paused: true}
        // RED: TriagePauseController does not exist yet
        RestClient client = RestClient.create("http://localhost:" + port);
        ResponseEntity<com.zeromail.api.dto.tenant.TriagePauseResponse> response =
            client.put().uri("/tenant/triage-pause")
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(new com.zeromail.api.dto.tenant.TriagePauseRequest(true))
                  .retrieve().toEntity(com.zeromail.api.dto.tenant.TriagePauseResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().paused()).isTrue();
    }

    @Test
    void putTriagePause_false_clears_triage_paused() {
        // When: paused=false after paused=true
        // Then: tenants.triage_paused = false
        RestClient client = RestClient.create("http://localhost:" + port);
        ResponseEntity<com.zeromail.api.dto.tenant.TriagePauseResponse> response =
            client.put().uri("/tenant/triage-pause")
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(new com.zeromail.api.dto.tenant.TriagePauseRequest(false))
                  .retrieve().toEntity(com.zeromail.api.dto.tenant.TriagePauseResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().paused()).isFalse();
    }
}
```

**`PubSubIdempotencyTest.java`** — package `com.zeromail.api.controllers`. RED scaffold for MAIL-04 dedup path at the controller integration level. `@Disabled("Wave 0 RED scaffold — implementation pending in Plan 03")`. Two `@Test` methods:
```java
@Disabled("Wave 0 RED scaffold — implementation pending in Plan 03")
class PubSubIdempotencyTest extends ApiPostgresTestBase {

    @LocalServerPort int port;

    @Test
    void duplicatePushMessage_sameMessageId_onlyOnePubSubDeliveryRow() {
        // When: same Pub/Sub messageId delivered twice (valid OIDC token both times)
        // Then: pubsub_delivery table has exactly ONE row for that messageId
        // RED: GmailPubSubController + PubSubIngestionService not yet implemented
        // This test verifies the UNIQUE(tenant_id, pubsub_message_id) dedup contract end-to-end
        String messageId = UUID.randomUUID().toString();
        // ... send push twice with same messageId ...
        // ... assert COUNT(*) FROM pubsub_delivery WHERE pubsub_message_id = messageId == 1 ...
        throw new org.opentest4j.TestAbortedException("RED scaffold — remove @Disabled when Plan 03 is complete");
    }

    @Test
    void unknownEmailAddress_returns200_noPubSubDeliveryRow() {
        // When: valid OIDC token but emailAddress not in gmail_connections
        // Then: 200 OK + no row in pubsub_delivery
        // RED: GmailPubSubController not yet implemented
        throw new org.opentest4j.TestAbortedException("RED scaffold — remove @Disabled when Plan 03 is complete");
    }
}
```

**`PubSubDeliveryEntityTest.java`** — package `com.zeromail.core.gmail.persistence`. Extends `PostgresContainerTest`. Imports `com.zeromail.core.gmail.persistence.PubSubDeliveryEntity` (RED) + `PubSubDeliveryRepository` (RED). Three `@Test` methods:
1. `insertAndRead_roundtrip()` — persist entity, find by id, assert fields
2. `uniqueConstraint_preventsduplicateMessageId()` — insert two rows with same `(tenantId, pubsubMessageId)`, assert second throws `DataIntegrityViolationException`
3. `skipLocked_claimBatch()` — insert 3 PENDING rows, call `claimPendingBatch(2)`, assert 2 returned + locked status

**`MailMessageObservedEntityTest.java`** — package `com.zeromail.core.gmail.persistence`. Extends `PostgresContainerTest`. Imports `com.zeromail.core.gmail.persistence.MailMessageObservedEntity` (RED) + `MailMessageObservedRepository` (RED). Three `@Test` methods:
1. `insertAndRead_compositePk_roundtrip()` — persist entity, find by composite PK, assert all fields
2. `labelIds_textArray_roundtrip()` — persist entity with `labelIds = ["INBOX", "UNREAD"]`, read via `JdbcTemplate`, assert raw column type `text[]` and values
3. `onConflictDoNothing_deduplication()` — insert row with same composite PK twice, assert second is silently ignored (no exception), count = 1

**`GmailIngestionHealthTest.java`** — package `com.zeromail.core.gmail.model`. Pure unit test. Imports `com.zeromail.core.gmail.model.GmailIngestionHealth` (RED). Four `@Test` methods:
1. `allValues_haveStableId()` — assert `HEALTHY.id() == "HEALTHY"`, `WATCH_UNHEALTHY.id() == "WATCH_UNHEALTHY"`, `HISTORY_LOST.id() == "HISTORY_LOST"`
2. `fromId_validValues_succeed()` — `fromId("HEALTHY")` returns `HEALTHY`, etc.
3. `fromId_unknownId_throwsNoSuchElementException()` — `fromId("BOGUS")` throws `NoSuchElementException` with message containing "Unknown GmailIngestionHealth"
4. `idEqualsName()` — for all enum values, assert `e.id().equals(e.name())`

**`MockGoogleOidcServer.java`** — package `com.zeromail.worker.test`. Uses `com.sun.net.httpserver.HttpServer` or WireMock-style (prefer `com.github.tomakehurst:wiremock` if already in test dependencies; otherwise use raw `HttpServer`). Generates a fresh RSA-2048 keypair at construction. Exposes:
- `String jwksUrl()` — local URL to JWKS endpoint
- `String sign(String audience, String email, String issuer, long expiresInSeconds)` — returns compact JWT signed with the private key
- `String signWithWrongKey(String audience, String email)` — signs with a DIFFERENT key (for bad-sig test)
- `void start()` / `void stop()`

Check if `wiremock-standalone` or similar is already in test dependencies:
```bash
grep -r 'wiremock\|WireMock\|mockwebserver\|okhttp' backend/build.gradle.kts backend/api/build.gradle.kts 2>/dev/null | head -5
```
If not available, use `com.sun.net.httpserver.HttpServer` from JDK (always available). JWKS format: `{"keys":[{"kty":"RSA","kid":"test-key-1","use":"sig","alg":"RS256","n":"...","e":"AQAB"}]}`.

**`MockGmailHistoryServer.java`** — package `com.zeromail.worker.test`. Configures a stub Gmail API server using `HttpServer`. Exposes:
- `void stubHistoryList(long startHistoryId, List<HistoryMessageResponse> messages)` — returns synthetic Gmail history.list response
- `void stubHistoryList404()` — returns 404 response to simulate expired historyId
- `void stubWatchSuccess(long historyId, long expirationMs)` — returns watch response
- `void stubWatchFailure(int statusCode)` — returns failure
- `String baseUrl()` — local base URL for Gmail client configuration
- `void start()` / `void stop()`

**`GmailHistoryProcessorTest.java`** — package `com.zeromail.worker`. Extends `PostgresContainerTest` (from `backend/core`). Imports `com.zeromail.worker.GmailHistoryProcessor` (RED). Uses `MockGmailHistoryServer` fixture. Five `@Test` methods:
1. `processDelivery_insertsMailMessageObserved()` — PENDING delivery row + stubbed history.list with 1 INBOX message → asserts `mail_message_observed` row created + delivery status=PROCESSED
2. `processDelivery_history404_setsHistoryLost()` — stubbed 404 → asserts `ingestion_health=HISTORY_LOST`, delivery status=PROCESSED, `last_synced_history_id` advanced to webhook_history_id
3. `processDelivery_idempotent_duplicateMessage()` — same delivery twice → exactly one `mail_message_observed` row
4. `processDelivery_scopedValueBound_perRow()` — two deliveries for different tenants → each observation row has correct `tenant_id` (cross-tenant isolation)
5. `processDelivery_invalidGrant_setsDisconnected()` — 401 from token refresh → asserts `gmail_connections.status=DISCONNECTED`, delivery status=DEAD

**`GmailWatchSchedulerTest.java`** — package `com.zeromail.worker`. Extends `PostgresContainerTest`. Imports `com.zeromail.worker.GmailWatchScheduler` (RED). Uses `MockGmailHistoryServer`. Four `@Test` methods:
1. `register_nullExpiry_issuersWatch()` — `gmail_connections` row with `watch_expires_at=NULL` + `status=CONNECTED` → scheduler tick → asserts `watch_history_id` + `watch_expires_at` + `watch_renewed_at` set + `ingestion_health=HEALTHY`
2. `renew_expiryWithin24h_issuersWatch()` — row with `watch_expires_at=NOW+23h` → tick → renewed
3. `threeConsecutiveFailures_setsWatchUnhealthy()` — stub watch failure three times → after 3rd, `ingestion_health=WATCH_UNHEALTHY`
4. `watchRequest_inboxOnly_labelIds()` — capture the `WatchRequest` sent to Gmail stub, assert `labelIds=["INBOX"]` and `labelFilterBehavior="include"`
  </action>

  <verify>
    <automated>./gradlew :backend:core:compileTestJava :backend:api:compileTestJava :backend:worker:compileTestJava 2>&1 | grep -E "error:|FAILED|BUILD" | head -30</automated>
  </verify>

  <acceptance_criteria>
    - All 12 test files exist at the exact paths listed in files_modified
    - `MockGoogleOidcServer.java` exists at `backend/worker/src/test/java/com/zeromail/worker/test/MockGoogleOidcServer.java` and contains `jwksUrl()` method signature
    - `MockGmailHistoryServer.java` exists at `backend/worker/src/test/java/com/zeromail/worker/test/MockGmailHistoryServer.java` and contains `stubHistoryList(` method signature
    - `MeControllerTest.java` contains `@Disabled` annotation and references `MeResponse.triagePaused()` and `MeResponse.gmailConnectionStatus().ingestionHealth()`
    - `TriagePauseControllerTest.java` contains `@Disabled` annotation and references `TriagePauseController`, `TriagePauseRequest`, `TriagePauseResponse`
    - `PubSubIdempotencyTest.java` contains `@Disabled` annotation and describes the UNIQUE dedup contract
    - Compilation fails with "cannot find symbol" errors referencing `PubSubOidcAuthFilter`, `GmailPubSubController`, `PubSubDeliveryEntity`, `MailMessageObservedEntity`, `GmailIngestionHealth`, `GmailHistoryProcessor`, `GmailWatchScheduler` — NOT with syntax/package errors
    - `GmailIngestionHealthTest.java` contains `NoSuchElementException` in the body
    - `PubSubOidcAuthFilterTest.java` contains `"pubsub_oidc"` event string reference
  </acceptance_criteria>

  <done>12 backend test files + 2 fixtures exist; compilation is RED-by-design (missing production classes); TriagePauseControllerTest, PubSubIdempotencyTest, MeControllerTest exist as @Disabled RED scaffolds</done>
</task>

<task type="auto">
  <name>Task 2: Frontend Wave 0 RED scaffolds — architecture guard + PauseBanner + hook + ReconnectPrompt tests</name>
  <files>
    apps/web/features/triage/components/PauseBanner.test.tsx,
    apps/web/features/triage/hooks/useToggleTriagePause.test.tsx,
    apps/web/features/gmail/components/ReconnectPrompt.test.tsx,
    apps/web/__tests__/architecture/phase-02a-files.test.ts
  </files>

  <read_first>
    - apps/web/__tests__/architecture/feature-folders.test.ts
    - apps/web/__tests__/features/account/me-cache-dedupe.test.ts
    - apps/web/features/gmail/components/ReconnectPrompt.tsx (existing component shape — read BEFORE writing test)
    - apps/web/features/gmail/hooks/useDisconnectGmail.ts (mutation hook analog)
    - apps/web/features/account/api/keys.ts (key factory analog for accountKeys.me())
    - .planning/phases/02A-mail-ingestion/02A-VALIDATION.md (Wave 0 Requirements)
    - CLAUDE.md (Conventions section)
  </read_first>

  <action>
Create 4 frontend RED scaffold files.

**`apps/web/__tests__/architecture/phase-02a-files.test.ts`** — file-presence + i18n parity guard. Pattern: copy shape from `feature-folders.test.ts` (uses `existsSync` static predicate at module load, NOT `beforeAll`).

Content structure:
```typescript
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const WEB_ROOT = join(process.cwd(), 'apps/web');

// Phase 02A expected files — RED until waves 1-3 complete
const EXPECTED_FILES = [
  'features/triage/components/PauseBanner.tsx',
  'features/triage/hooks/useToggleTriagePause.ts',
  'features/triage/api/triagePause.ts',
  'features/triage/api/keys.ts',
];

describe('Phase 02A: required files exist', () => {
  EXPECTED_FILES.forEach((relPath) => {
    it(`${relPath} exists`, () => {
      expect(existsSync(join(WEB_ROOT, relPath))).toBe(true);
    });
  });
});

describe('Phase 02A: i18n key parity', () => {
  it('vi.json and en.json contain settings.triage.pause keys', () => {
    const viPath = join(WEB_ROOT, 'i18n/messages/vi.json');
    const enPath = join(WEB_ROOT, 'i18n/messages/en.json');
    expect(existsSync(viPath)).toBe(true);
    expect(existsSync(enPath)).toBe(true);
    const vi = JSON.parse(require('fs').readFileSync(viPath, 'utf-8'));
    const en = JSON.parse(require('fs').readFileSync(enPath, 'utf-8'));
    const requiredKeys = [
      'settings.triage.pause.title',
      'settings.triage.pause.toggleLabel',
      'settings.triage.pause.banner.heading',
      'settings.triage.pause.banner.unpause',
    ];
    for (const key of requiredKeys) {
      const parts = key.split('.');
      // Traverse nested object
      let viNode: unknown = vi;
      let enNode: unknown = en;
      for (const p of parts) {
        viNode = (viNode as Record<string, unknown>)?.[p];
        enNode = (enNode as Record<string, unknown>)?.[p];
      }
      expect(viNode, `vi.json missing: ${key}`).toBeTruthy();
      expect(enNode, `en.json missing: ${key}`).toBeTruthy();
    }
  });
});
```

This test is RED now (files don't exist). GREEN after Wave 3 completes.

**`apps/web/features/triage/components/PauseBanner.test.tsx`** — conditional render test. Import `PauseBanner` from `@/features/triage/components/PauseBanner` (RED until Wave 3). Use vitest + @testing-library/react. Three test cases:
1. `renders_when_triagePaused_true` — mock `useCurrentUser()` returning `{ triagePaused: true }`, render `<PauseBanner>`, assert `getByRole('alert')` present + heading contains "triage"
2. `notRendered_when_triagePaused_false` — mock returns `{ triagePaused: false }`, assert alert NOT in document
3. `unpauses_on_cta_click` — `triagePaused: true`, click unpause button, assert `useToggleTriagePause().mutate` called with `false`

Use `vi.mock('@/features/account/hooks/useCurrentUser', ...)` and `vi.mock('@/features/triage/hooks/useToggleTriagePause', ...)`. The component uses `useCurrentUser()` internally (hook-based, no props) — this is the authoritative shape per Plan 04. Plain DOM `<button>` (not `<Button>`) per Phase 01.4 vitest boundary pattern.

**`apps/web/features/triage/hooks/useToggleTriagePause.test.tsx`** — mutation hook test. Import `useToggleTriagePause` from `@/features/triage/hooks/useToggleTriagePause` (RED until Wave 3). Two test cases:
1. `mutate_callsSetTriagePaused` — mock `setTriagePaused`, call mutation with `true`, assert mock called with `true`
2. `onSuccess_invalidates_me_key` — on successful mutation, assert `queryClient.invalidateQueries` called with key matching `accountKeys.me()`

**`apps/web/features/gmail/components/ReconnectPrompt.test.tsx`** — MAIL-05 ingestionHealth gate test. This is a new test file for an EXISTING component. Import `ReconnectPrompt` from `@/features/gmail/components/ReconnectPrompt`. The tests are `it.skip(...)` RED scaffolds since the ingestionHealth gate extension is not yet implemented.

```typescript
import { describe, it } from 'vitest';

// Wave 0 RED scaffold — ingestionHealth gate not yet implemented in Plan 04
// Remove it.skip() when Plan 04 extends the ReconnectPrompt gate condition

describe('ReconnectPrompt — ingestionHealth gate (MAIL-05)', () => {
  it.skip('renders when status is CONNECTED but ingestionHealth is WATCH_UNHEALTHY', () => {
    // When: user?.gmailConnectionStatus?.status === 'CONNECTED'
    //   AND user?.gmailConnectionStatus?.ingestionHealth === 'WATCH_UNHEALTHY'
    // Then: ReconnectPrompt is visible (gate must check BOTH conditions)
    // This test verifies D-D3: unified gate status!=CONNECTED || ingestionHealth!=HEALTHY
    // Production code: Plan 04 Task 1 extends ReconnectPrompt gate
  });

  it.skip('renders when status is CONNECTED but ingestionHealth is HISTORY_LOST', () => {
    // Same as above but for HISTORY_LOST case
  });

  it.skip('does NOT render when status is CONNECTED and ingestionHealth is HEALTHY', () => {
    // Healthy state: no prompt shown
  });
});
```

Create the directory `apps/web/features/triage/components/` and `apps/web/features/triage/hooks/` as needed (just empty test files; no production files yet).
  </action>

  <verify>
    <automated>cd /d/study-materials-summer-2026/EXE202/zero-mail && pnpm -F web run test:run -- --reporter=verbose 2>&1 | grep -E "FAIL|pass|fail|PauseBanner|useToggle|phase-02a|ReconnectPrompt" | head -20</automated>
  </verify>

  <acceptance_criteria>
    - `apps/web/__tests__/architecture/phase-02a-files.test.ts` exists and contains `settings.triage.pause.title`
    - `apps/web/features/triage/components/PauseBanner.test.tsx` exists and contains `triagePaused` and `vi.mock('@/features/account/hooks/useCurrentUser'`
    - `apps/web/features/triage/hooks/useToggleTriagePause.test.tsx` exists and contains `invalidateQueries`
    - `apps/web/features/gmail/components/ReconnectPrompt.test.tsx` exists, contains `it.skip`, and references `ingestionHealth` and `WATCH_UNHEALTHY`
    - Running vitest shows these 4 files as FAIL or SKIP (import errors or skipped assertions) — NOT syntax errors
    - `phase-02a-files.test.ts` fails with "false to be true" (files don't exist yet)
  </acceptance_criteria>

  <done>4 frontend test files exist; PauseBanner + hook tests fail RED; ReconnectPrompt test has it.skip scaffolds; phase-02a-files.test.ts fails RED</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Test environment | No production secrets; hermetic fixtures only |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01 | Spoofing | PubSubOidcAuthFilterTest | mitigate | Wave 0 test defines 5 rejection cases (wrong aud/email/exp/sig/iss) — these tests must go RED now and GREEN in Wave 2a |
| T-02 | Tampering | Idempotency test coverage | mitigate | MailMessageObservedEntityTest + PubSubDeliveryEntityTest + PubSubIdempotencyTest verify ON CONFLICT DO NOTHING semantics at DB level and controller integration level |
| T-03 | Tampering | MeControllerTest field contract | mitigate | MeControllerTest asserts triagePaused + ingestionHealth JSON shape — prevents silent null serialization |
</threat_model>

<verification>
After this plan:
- `./gradlew :backend:core:compileTestJava :backend:api:compileTestJava :backend:worker:compileTestJava` exits non-zero (RED expected — production classes missing)
- All 16 test files exist on disk
- `pnpm -F web run test:run` shows 4 new failing or skipped test files
</verification>

<success_criteria>
16 Wave 0 test scaffold files exist. Backend tests are RED ("cannot find symbol" for production classes). TriagePauseControllerTest, PubSubIdempotencyTest, MeControllerTest are @Disabled RED scaffolds that compile. Frontend tests are RED (import not found) or it.skip. No syntax/parse errors in test files themselves. MockGoogleOidcServer and MockGmailHistoryServer are compilable fixtures.
</success_criteria>

<output>
After completion, create `.planning/phases/02A-mail-ingestion/02A-00-SUMMARY.md`
</output>
