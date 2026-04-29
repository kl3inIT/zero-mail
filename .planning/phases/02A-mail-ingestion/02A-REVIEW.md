---
phase: 02A-mail-ingestion
reviewed: 2026-04-29T07:36:24Z
depth: standard
files_reviewed: 8
files_reviewed_list:
  - backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java
  - backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java
  - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java
  - backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java
  - backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java
  - backend/worker/src/test/java/com/zeromail/worker/test/MockGmailHistoryServer.java
  - apps/web/features/gmail/components/ReconnectPrompt.test.tsx
  - backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailNotification.java
findings:
  critical: 0
  warning: 1
  info: 0
  total: 1
status: issues_found
---

# Phase 02A: Code Review Report

**Reviewed:** 2026-04-29T07:36:24Z
**Depth:** standard
**Files Reviewed:** 8
**Status:** issues_found

## Summary

Re-reviewed the Phase 02A files requested after commit `3e89289`, focusing on the prior CR-01, CR-02, CR-03, WR-03, and the changed `longValueExact()` conversions from WR-01. The previous critical ingestion blockers are resolved in the current source: Pub/Sub data now uses standard Base64 decoding, Gmail history pagination is processed before advancing the sync pointer, and the invalid history-id gap truncation heuristic has been removed. The prior WR-03 tenant-scope test assertion is also resolved.

One warning remains in the scoped frontend test file: `ReconnectPrompt.test.tsx` still does not render or assert the behavior its test names describe. I did not keep resolved critical findings open.

Focused verification run:

- `.\gradlew.bat :backend:worker:test --tests com.zeromail.worker.GmailHistoryProcessorTest --tests com.zeromail.worker.GmailWatchSchedulerTest :backend:api:test --tests com.zeromail.api.controllers.GmailPubSubControllerIntegrationTest` passed.
- `pnpm --filter web test:run -- features/gmail/components/ReconnectPrompt.test.tsx` passed, but the test content remains non-assertive for UI behavior as noted below.

## Resolved Prior Findings

### CR-01: BLOCKER - Pub/Sub payloads were decoded with the wrong Base64 alphabet

**Status:** Resolved.

`GmailPubSubController` now decodes `message.data` with `Base64.getDecoder()` at `backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java:40`, and `GmailPubSubControllerIntegrationTest` now builds push payloads with `Base64.getEncoder()` at `backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java:180`.

### CR-02: BLOCKER - Gmail history pagination was dropped while the sync pointer advanced

**Status:** Resolved.

`GmailDeliveryProcessingService` now loops through `nextPageToken` before calling `updateLastSyncedHistoryIdMonotonic()` at `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java:77-96`. `GmailHistoryProcessorTest` covers a two-page history response and asserts both messages are observed before the delivery is marked processed at `backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java:79-100`.

### CR-03: BLOCKER - Large history gaps were silently truncated with invalid history-id arithmetic

**Status:** Resolved.

The `HISTORY_GAP_CAP`/`webhookHistoryId - 500` logic is gone. Processing now starts from the persisted `lastSyncedHistoryId` at `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java:69-86`, and expired or missing history pointers route through `markHistoryLost()` at lines 69-74 and 99-104.

### WR-03: WARNING - Multi-tenant worker test had a tautological assertion

**Status:** Resolved.

`GmailHistoryProcessorTest` now seeds distinct tenant A/B history responses and asserts exact tenant/message ownership at `backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java:102-120`.

### WR-01: WARNING - Gmail API history ids were silently narrowed with `longValue()`

**Status:** Resolved for the changed conversions reviewed here.

The changed Gmail API conversions now use `longValueExact()` at `backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java:75` and `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java:150`, so an out-of-range `BigInteger` no longer silently wraps into a corrupt pointer. I did not require a full schema migration in this re-review per the requested scope.

## Warnings

### WR-02: WARNING - Reconnect prompt tests still do not exercise the UI behavior they name

**File:** `apps/web/features/gmail/components/ReconnectPrompt.test.tsx:6`

**Issue:** The tests still only assert that `ReconnectPrompt` is defined and that local constants equal themselves. They do not render the prompt, click the reconnect CTA, or verify the settings-page ingestion-health gate. The file would pass even if `WATCH_UNHEALTHY` and `HISTORY_LOST` never showed the reconnect UI.

**Fix:** Render the relevant component or settings-page gate with test messages/providers, then assert behavior for healthy and unhealthy states.

```tsx
render(
  <NextIntlClientProvider locale="en" messages={messages}>
    <ReconnectPrompt onReconnect={onReconnect} />
  </NextIntlClientProvider>,
);

expect(screen.getByRole('button', { name: /reconnect/i })).toBeInTheDocument();
```

---

_Reviewed: 2026-04-29T07:36:24Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
