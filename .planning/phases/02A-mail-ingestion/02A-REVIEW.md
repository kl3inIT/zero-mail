---
phase: 02A-mail-ingestion
reviewed: 2026-04-29T07:25:48Z
depth: standard
files_reviewed: 80
files_reviewed_list:
  - apps/web/__tests__/architecture/phase-02a-files.test.ts
  - apps/web/app/(protected)/layout.tsx
  - apps/web/app/(protected)/settings/page.tsx
  - apps/web/features/account/api/me.ts
  - apps/web/features/gmail/components/ReconnectPrompt.test.tsx
  - apps/web/features/triage/api/keys.ts
  - apps/web/features/triage/api/triagePause.ts
  - apps/web/features/triage/components/PauseBanner.test.tsx
  - apps/web/features/triage/components/PauseBanner.tsx
  - apps/web/features/triage/hooks/useToggleTriagePause.test.tsx
  - apps/web/features/triage/hooks/useToggleTriagePause.ts
  - apps/web/i18n/messages/en.json
  - apps/web/i18n/messages/vi.json
  - apps/web/lib/api/schema.d.ts
  - apps/web/openapi/openapi.json
  - apps/web/package.json
  - apps/web/scripts/check-i18n.ts
  - apps/web/scripts/generate-api.ts
  - apps/web/vitest.config.ts
  - backend/api/build.gradle.kts
  - backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/MeController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/TriagePauseController.java
  - backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/gmail/FlexibleLongDeserializer.java
  - backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailNotification.java
  - backend/api/src/main/java/com/zeromail/api/dto/gmail/PubSubPushEnvelope.java
  - backend/api/src/main/java/com/zeromail/api/dto/tenant/package-info.java
  - backend/api/src/main/java/com/zeromail/api/dto/tenant/TriagePauseRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/tenant/TriagePauseResponse.java
  - backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java
  - backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java
  - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
  - backend/api/src/main/resources/application.yml
  - backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java
  - backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java
  - backend/api/src/test/java/com/zeromail/api/security/TestSessionSupport.java
  - backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java
  - backend/api/src/test/java/com/zeromail/api/support/MockGoogleOidcServer.java
  - backend/core/build.gradle.kts
  - backend/core/src/main/java/com/zeromail/core/account/service/OAuthProvisioningService.java
  - backend/core/src/main/java/com/zeromail/core/gmail/model/GmailConnectionProjection.java
  - backend/core/src/main/java/com/zeromail/core/gmail/model/GmailIngestionHealth.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedId.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntity.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java
  - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java
  - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java
  - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java
  - backend/core/src/main/java/com/zeromail/core/gmail/service/IngestResult.java
  - backend/core/src/main/java/com/zeromail/core/gmail/service/InvalidGrantException.java
  - backend/core/src/main/java/com/zeromail/core/gmail/service/PubSubIngestionService.java
  - backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java
  - backend/core/src/main/java/com/zeromail/core/tenant/service/TenantService.java
  - backend/core/src/main/resources/db/changelog/changes/010-gmail-ingestion-state.yaml
  - backend/core/src/main/resources/db/changelog/changes/011-pubsub-delivery-table.yaml
  - backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml
  - backend/core/src/main/resources/db/changelog/changes/013-tenants-triage-paused.yaml
  - backend/core/src/test/java/com/zeromail/core/gmail/model/GmailIngestionHealthTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntityTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntityTest.java
  - backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java
  - backend/worker/build.gradle.kts
  - backend/worker/src/main/java/com/zeromail/worker/GmailHistoryProcessor.java
  - backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java
  - backend/worker/src/main/java/com/zeromail/worker/WorkerApplication.java
  - backend/worker/src/main/resources/application.yml
  - backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java
  - backend/worker/src/test/java/com/zeromail/worker/GmailWatchSchedulerTest.java
  - backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java
  - backend/worker/src/test/java/com/zeromail/worker/test/MockGmailHistoryServer.java
  - gradle/libs.versions.toml
  - package.json
findings:
  critical: 3
  warning: 3
  info: 0
  total: 6
status: issues_found
---

# Phase 02A: Code Review Report

**Reviewed:** 2026-04-29T07:25:48Z
**Depth:** standard
**Files Reviewed:** 80
**Status:** issues_found

## Summary

Reviewed the Phase 02A Gmail ingestion, Pub/Sub push auth, watch renewal, triage pause UI/API, migrations, generated API contract, and tests. The main risk is data loss in the Gmail ingestion path: malformed-but-valid Pub/Sub data can be acknowledged without persistence, paginated Gmail history is dropped, and large history windows are truncated with an invalid history-id arithmetic heuristic.

Focused verification run:

- `.\gradlew.bat :backend:worker:test --tests com.zeromail.worker.GmailHistoryProcessorTest` passed.
- `.\gradlew.bat :backend:api:test --tests com.zeromail.api.controllers.GmailPubSubControllerIntegrationTest --tests com.zeromail.api.controllers.PubSubIdempotencyTest --tests com.zeromail.api.security.PubSubOidcAuthFilterTest` passed.
- `pnpm --filter web test:run -- features/gmail/components/ReconnectPrompt.test.tsx features/triage/components/PauseBanner.test.tsx features/triage/hooks/useToggleTriagePause.test.tsx` passed.

## Critical Issues

### CR-01: BLOCKER - Pub/Sub payloads are decoded with the wrong Base64 alphabet

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java:40`

**Issue:** `message.data` from Pub/Sub JSON pushes is decoded with `Base64.getUrlDecoder()`. Pub/Sub emits byte fields as standard Base64; the URL-safe decoder rejects standard `+` and `/` characters. The controller catches that decode failure and returns normally, so Pub/Sub treats the message as acknowledged and the notification is permanently dropped. The tests hide this because their helper also uses `Base64.getUrlEncoder()` at `backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java:180`.

**Fix:**

```java
byte[] decoded = Base64.getDecoder().decode(envelope.message().data());
notification = objectMapper.readValue(decoded, GmailNotification.class);
```

Update Pub/Sub integration helpers to use `Base64.getEncoder()` so tests match production payloads. If backwards compatibility with already-generated URL-safe fixtures is needed, accept URL-safe as a fallback after standard decode fails, but keep the primary path standard.

### CR-02: BLOCKER - Gmail history pagination is dropped while the sync pointer advances

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java:88`

**Issue:** When `users.history.list` returns `nextPageToken`, the code only logs `gmail_history_pagination_dropped`, processes the first page, then advances `last_synced_history_id` to the webhook history id at line 94 and marks the delivery `PROCESSED` at line 95. Any message additions on later pages are skipped forever. This is direct inbox-ingestion data loss for high-volume accounts.

**Fix:**

```java
int newObservations = 0;
String pageToken = null;
do {
    var request = gmail.users()
            .history()
            .list("me")
            .setStartHistoryId(BigInteger.valueOf(startHistoryId))
            .setHistoryTypes(List.of("messageAdded"))
            .setLabelId("INBOX")
            .setMaxResults(500L);
    if (pageToken != null) {
        request.setPageToken(pageToken);
    }
    ListHistoryResponse page = request.execute();
    newObservations += observeInboxMessages(gmail, tenantId, page);
    pageToken = page.getNextPageToken();
} while (pageToken != null);

connectionRepository.updateLastSyncedHistoryIdMonotonic(tenantId, webhookHistoryId);
```

Add a worker test where the mock Gmail history endpoint returns at least two pages and assert messages from both pages are inserted before the delivery is marked processed.

### CR-03: BLOCKER - Large history gaps are silently truncated with invalid history-id arithmetic

**File:** `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java:73`

**Issue:** When `webhookHistoryId - startHistoryId > 500`, the code sets `startHistoryId = webhookHistoryId - 500` and continues. Gmail history ids are opaque monotonically increasing ids, not dense event counters, so subtracting `500` does not mean "last 500 changes" and can start from an invalid or arbitrary point. The service then advances `last_synced_history_id` at line 94, permanently skipping the unprocessed window.

**Fix:** Remove `HISTORY_GAP_CAP`. Process from the persisted `lastSyncedHistoryId` and page until complete. If Gmail rejects the stored start id with 404, use the existing `markHistoryLost` path and require reconnect or a full resync; do not invent a later start id and advance the pointer.

```java
Long savedPointer = conn.getLastSyncedHistoryId();
if (savedPointer == null) {
    connectionService.markHistoryLost(tenantId, webhookHistoryId);
    deliveryRepository.updateStatus(delivery.getId(), "PROCESSED");
    return;
}
long startHistoryId = savedPointer;
```

## Warnings

### WR-01: WARNING - Gmail history ids are narrowed to signed `Long`

**File:** `backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailNotification.java:11`

**Issue:** Gmail history ids are represented as `Long` in the Pub/Sub DTO, persisted as `bigint`, and converted from the Gmail Java client's `BigInteger` with `longValue()` in `backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java:75` and `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java:149`. If Gmail returns an id outside signed 64-bit range, `longValue()` silently wraps and corrupts watch/history pointers.

**Fix:** Store and compare history ids as `BigInteger`/decimal strings end-to-end, or use `longValueExact()` with explicit failure handling that marks ingestion unhealthy instead of writing a wrapped value. If Postgres numeric comparison is needed, migrate the columns from `bigint` to `numeric(20,0)` or store canonical decimal text with careful comparison logic.

### WR-02: WARNING - Reconnect prompt tests do not exercise the UI behavior they name

**File:** `apps/web/features/gmail/components/ReconnectPrompt.test.tsx:6`

**Issue:** The three tests only assert that `ReconnectPrompt` is defined and that local constants equal themselves. They do not render the prompt, click the reconnect CTA, or verify the settings-page ingestion-health gate. These tests would pass if the reconnect prompt were never shown for `WATCH_UNHEALTHY` or `HISTORY_LOST`.

**Fix:** Render the relevant UI with `NextIntlClientProvider`, mock `/me` states, and assert the prompt is present for unhealthy connected states and absent for healthy connected states.

```tsx
render(
  <NextIntlClientProvider locale="en" messages={enMessages}>
    <ReconnectPrompt onReconnect={onReconnect} />
  </NextIntlClientProvider>,
);
expect(screen.getByRole('button', { name: /reconnect/i })).toBeInTheDocument();
```

For the gate itself, test `SettingsPage` with mocked `useCurrentUser()` returning `status: 'CONNECTED'` and each `ingestionHealth` value.

### WR-03: WARNING - Multi-tenant worker test has a tautological assertion

**File:** `backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java:91`

**Issue:** `assertThat(count("mail_message_observed", tenantB)).isGreaterThanOrEqualTo(0L)` can never fail for a SQL `COUNT(*)`. The test name claims it verifies per-row `TenantContext` binding, but it would pass if tenant B were skipped, cross-written, or processed with tenant A's data.

**Fix:** Seed distinct Gmail history responses per tenant and assert exact tenant/message ownership.

```java
assertThat(count("mail_message_observed", tenantA)).isEqualTo(1L);
assertThat(count("mail_message_observed", tenantB)).isEqualTo(1L);
assertThat(messageExists(tenantA, "gmail-a")).isTrue();
assertThat(messageExists(tenantB, "gmail-b")).isTrue();
assertThat(messageExists(tenantB, "gmail-a")).isFalse();
```

---

_Reviewed: 2026-04-29T07:25:48Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
