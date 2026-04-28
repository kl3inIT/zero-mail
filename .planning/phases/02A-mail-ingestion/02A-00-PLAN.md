---
phase: 02A-mail-ingestion
plan: "00"
type: execute
wave: 0
depends_on: []
files_modified:
  - backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntityTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntityTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/model/GmailIngestionHealthTest.java
  - backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java
  - backend/worker/src/test/java/com/zeromail/worker/GmailWatchSchedulerTest.java
  - backend/worker/src/test/java/com/zeromail/worker/test/MockGoogleOidcServer.java
  - backend/worker/src/test/java/com/zeromail/worker/test/MockGmailHistoryServer.java
  - apps/web/features/triage/components/PauseBanner.test.tsx
  - apps/web/features/triage/hooks/useToggleTriagePause.test.tsx
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
    - "All 12 Wave 0 test files exist on disk and compile (backend tests fail at RED; frontend tests fail at RED)"
    - "MockGoogleOidcServer and MockGmailHistoryServer fixtures can generate signed JWT tokens and serve fake JWKS"
    - "GmailIngestionHealthTest asserts fromId fail-loud contract"
    - "PauseBanner.test.tsx fails with import error (component doesn't exist yet)"
    - "phase-02a-files.test.ts asserts file presence and i18n key parity"
  artifacts:
    - path: "backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java"
      provides: "RED OIDC verification test — 5 test cases (valid passes, wrong aud/email/exp/sig → 401)"
    - path: "backend/worker/src/test/java/com/zeromail/worker/test/MockGoogleOidcServer.java"
      provides: "Hermetic JWKS fixture for OIDC tests"
    - path: "apps/web/__tests__/architecture/phase-02a-files.test.ts"
      provides: "File-presence + i18n parity guard"
  key_links:
    - from: "PubSubOidcAuthFilterTest"
      to: "MockGoogleOidcServer"
      via: "setCertificatesLocation() override"
      pattern: "MockGoogleOidcServer|mock.*jwks|certLocation"
    - from: "GmailHistoryProcessorTest"
      to: "MockGmailHistoryServer"
      via: "stubbed history.list response"
      pattern: "MockGmailHistoryServer|stubHistory|historyResponse"
---

<objective>
Create all 12 Wave 0 RED-scaffold test files that define the acceptance contract for Waves 1-3. Tests must compile (or fail with clear import-not-found errors) and be RED-by-design — they reference classes that don't exist yet.

Purpose: Establish the Nyquist-compliant verification spine before any production code is written. This is the established Phase 01.3/01.4/01.5/01.6 pattern.

Output: 12 test files — 9 backend JUnit 5 + 2 hermetic fixtures + 1 frontend Vitest architecture guard + 2 frontend component/hook tests.
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
  <name>Task 1: Backend Wave 0 RED scaffolds — filter, controller, persistence tests + hermetic fixtures</name>
  <files>
    backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java,
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
Create all 9 backend test files + 2 fixtures as RED scaffolds. Each test references classes that don't exist yet — this is intentional.

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
    - All 9 test files exist at the exact paths listed in files_modified
    - `MockGoogleOidcServer.java` exists at `backend/worker/src/test/java/com/zeromail/worker/test/MockGoogleOidcServer.java` and contains `jwksUrl()` method signature
    - `MockGmailHistoryServer.java` exists at `backend/worker/src/test/java/com/zeromail/worker/test/MockGmailHistoryServer.java` and contains `stubHistoryList(` method signature
    - Compilation fails with "cannot find symbol" errors referencing `PubSubOidcAuthFilter`, `GmailPubSubController`, `PubSubDeliveryEntity`, `MailMessageObservedEntity`, `GmailIngestionHealth`, `GmailHistoryProcessor`, `GmailWatchScheduler` — NOT with syntax/package errors
    - `GmailIngestionHealthTest.java` contains `NoSuchElementException` in the body
    - `PubSubOidcAuthFilterTest.java` contains `"pubsub_oidc"` event string reference
  </acceptance_criteria>

  <done>9 backend test files + 2 fixtures exist; compilation is RED-by-design (missing production classes); no syntax errors in test files themselves</done>
</task>

<task type="auto">
  <name>Task 2: Frontend Wave 0 RED scaffolds — architecture guard + PauseBanner + hook tests</name>
  <files>
    apps/web/features/triage/components/PauseBanner.test.tsx,
    apps/web/features/triage/hooks/useToggleTriagePause.test.tsx,
    apps/web/__tests__/architecture/phase-02a-files.test.ts
  </files>

  <read_first>
    - apps/web/__tests__/architecture/feature-folders.test.ts
    - apps/web/__tests__/features/account/me-cache-dedupe.test.ts
    - apps/web/features/gmail/components/ReconnectPrompt.tsx (existing component shape)
    - apps/web/features/gmail/hooks/useDisconnectGmail.ts (mutation hook analog)
    - apps/web/features/account/api/keys.ts (key factory analog for accountKeys.me())
    - .planning/phases/02A-mail-ingestion/02A-VALIDATION.md (Wave 0 Requirements)
    - CLAUDE.md (Conventions section)
  </read_first>

  <action>
Create 3 frontend RED scaffold files.

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

**`apps/web/features/triage/components/PauseBanner.test.tsx`** — conditional render test. Import `PauseBanner` from `@/features/triage/components/PauseBanner` (RED until Wave 3). Use vitest + @testing-library/react. Three `@test` cases:
1. `renders_when_triagePaused_true()` — mock `useCurrentUser()` returning `triagePaused: true`, render `<PauseBanner>`, assert `getByRole('alert')` present + heading contains "triage" (i18n key placeholder)
2. `notRendered_when_triagePaused_false()` — mock returns `triagePaused: false`, assert alert NOT in document
3. `unpauses_on_cta_click()` — `triagePaused: true`, click unpause button, assert `useToggleTriagePause().mutate` called with `false`

Use `vi.mock('@/features/account/hooks/useCurrentUser', ...)` and `vi.mock('@/features/triage/hooks/useToggleTriagePause', ...)`. Plain DOM `<button>` (not `<Button>`) per Phase 01.4 vitest boundary pattern.

**`apps/web/features/triage/hooks/useToggleTriagePause.test.tsx`** — mutation hook test. Import `useToggleTriagePause` from `@/features/triage/hooks/useToggleTriagePause` (RED until Wave 3). Two test cases:
1. `mutate_callsSetTriagePaused()` — mock `setTriagePaused`, call mutation with `true`, assert mock called with `true`
2. `onSuccess_invalidates_me_key()` — on successful mutation, assert `queryClient.invalidateQueries` called with key matching `accountKeys.me()`

Create the directory `apps/web/features/triage/components/` and `apps/web/features/triage/hooks/` as needed (just empty test files; no production files yet).
  </action>

  <verify>
    <automated>cd /d/study-materials-summer-2026/EXE202/zero-mail && pnpm -F web run test:run -- --reporter=verbose 2>&1 | grep -E "FAIL|pass|fail|PauseBanner|useToggle|phase-02a" | head -20</automated>
  </verify>

  <acceptance_criteria>
    - `apps/web/__tests__/architecture/phase-02a-files.test.ts` exists and contains `settings.triage.pause.title`
    - `apps/web/features/triage/components/PauseBanner.test.tsx` exists and contains `triagePaused`
    - `apps/web/features/triage/hooks/useToggleTriagePause.test.tsx` exists and contains `invalidateQueries`
    - Running vitest shows these 3 files as FAIL (import errors or assertion failures) — NOT syntax errors
    - `phase-02a-files.test.ts` fails with "false to be true" (files don't exist yet)
  </acceptance_criteria>

  <done>3 frontend test files exist; all fail RED; no TypeScript parse errors in test files themselves</done>
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
| T-02 | Tampering | Idempotency test coverage | mitigate | MailMessageObservedEntityTest + PubSubDeliveryEntityTest verify ON CONFLICT DO NOTHING semantics at DB level |
</threat_model>

<verification>
After this plan:
- `./gradlew :backend:core:compileTestJava :backend:api:compileTestJava :backend:worker:compileTestJava` exits non-zero (RED expected — production classes missing)
- All 12 test files exist on disk
- `pnpm -F web run test:run` shows 3 new failing test files
</verification>

<success_criteria>
12 Wave 0 test scaffold files exist. Backend tests are RED ("cannot find symbol" for production classes). Frontend tests are RED (import not found). No syntax/parse errors in test files themselves. MockGoogleOidcServer and MockGmailHistoryServer are compilable fixtures.
</success_criteria>

<output>
After completion, create `.planning/phases/02A-mail-ingestion/02A-00-SUMMARY.md`
</output>
