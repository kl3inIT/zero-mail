---
phase: 02A-mail-ingestion
verified: 2026-04-29T08:04:45Z
status: human_needed
score: "7/7 must-haves verified"
overrides_applied: 0
human_verification:
  - test: "End-to-end Pub/Sub delivery on staging VPS"
    expected: "Real Gmail message creates one tenant-attributed mail_message_observed row, and replaying the same Pub/Sub message creates no duplicate."
    why_human: "Requires real Google Pub/Sub push subscription, Gmail account, deployed HTTPS endpoint, and Google-signed OIDC token."
  - test: "users.watch 7-day expiry renewal"
    expected: "Backdated watch_expires_at is renewed, watch_renewed_at advances, and last_synced_history_id is not corrupted."
    why_human: "Requires time manipulation or a live worker/staging environment tied to Google watch behavior."
  - test: "Reconnect prompt UX after actual history-404"
    expected: "A history-404 sets ingestion_health=HISTORY_LOST, the reconnect prompt is visible, and clicking reconnect starts the Gmail OAuth flow."
    why_human: "Actual Gmail history expiration is not practical to trigger in automated verification."
  - test: "Pause toggle visual hierarchy and persistent banner"
    expected: "Settings pause toggle, persistent banner, and inline unpause control are visually correct and clear at target viewports."
    why_human: "Visual quality and layout hierarchy require human inspection or a separate visual audit."
---

# Phase 02A: Mail Ingestion Verification Report

**Phase Goal:** Receive Gmail push notifications reliably, keep `users.watch` alive, and process every history delivery idempotently with a tenant-visible global pause.
**Verified:** 2026-04-29T08:04:45Z
**Status:** human_needed
**Re-verification:** No previous `*-VERIFICATION.md` existed on disk; this pass focused on the user-identified gap fixes plus the roadmap contract.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | New Gmail push notifications produce tenant-attributed message observations. | VERIFIED | `GmailPubSubController` decodes Pub/Sub and calls `PubSubIngestionService.ingestPushEnvelope(...)`; `PubSubIngestionService` tenant-resolves via JdbcTemplate then inserts `pubsub_delivery`; `GmailHistoryProcessor` claims deliveries and calls `GmailDeliveryProcessingService.processDelivery`; `GmailDeliveryProcessingService` fetches Gmail history and metadata, then calls `MailMessageObservedRepository.insertObservedIfAbsent`. Worker tests pass. |
| 2 | `users.watch` is kept alive and unhealthy renewal state is tenant-visible. | VERIFIED | `GmailWatchScheduler.tick()` runs every minute, selects rows with null or near-expiry `watch_expires_at`, calls Gmail `users().watch("me", ...)`, records success, and marks `WATCH_UNHEALTHY` after 3 failures. `GmailWatchSchedulerTest` covers renewal, INBOX-only watch, preserved cursors, and HISTORY_LOST preservation. |
| 3 | Duplicate deliveries do not create duplicate downstream effects. | VERIFIED | `pubsub_delivery` insert uses `ON CONFLICT (tenant_id, pubsub_message_id) DO NOTHING`; `mail_message_observed` insert uses `ON CONFLICT (tenant_id, gmail_message_id) DO NOTHING`; integration tests assert duplicate Pub/Sub rows and duplicate observed-message rows stay at count 1. |
| 4 | Missing, expired, wrong-audience, wrong-email, and bad-signature Pub/Sub OIDC tokens return 401 before business logic. | VERIFIED | `PubSubOidcAuthFilter` builds `TokenVerifier` with expected audience and issuer, checks service-account email, and sends 401 before `chain.doFilter`. `PubSubSecurityConfig` installs it on `/internal/pubsub/**`. API and filter tests passed. |
| 5 | Global pause and history-404 recovery are user-visible. | VERIFIED | `TriagePauseController` persists `TenantService.setTriagePaused`; `/me` returns `triagePaused` and `gmailConnectionStatus.ingestionHealth`; settings renders the pause toggle and `ReconnectPromptGate`; `PauseBanner` renders when paused. `markHistoryLost` sets `HISTORY_LOST`, and reconnect prompt tests cover unhealthy gates. Current Phase 2A has no write-action queue yet, so no current code path queues Gmail write actions while paused. |
| 6 | `pubsub_delivery.payload` does not persist reversible Pub/Sub `message.data` or decoded `emailAddress`; it persists `{}`. | VERIFIED | `GmailPubSubController.receivePush` passes sanitized literal `"{}"` to `ingestPushEnvelope` after extracting only routing fields; `PubSubIngestionService` persists the supplied sanitized payload. `GmailPubSubControllerIntegrationTest.validPush_knownTenant_returns200` queries `payload::text`, asserts it equals `{}`, and asserts it does not contain the email, `data`, or `emailAddress`. |
| 7 | `ReconnectPrompt.test.tsx` renders and clicks real UI gate states for unhealthy vs healthy ingestion. | VERIFIED | The test renders `ReconnectPromptGate` inside `NextIntlClientProvider`, asserts `WATCH_UNHEALTHY` and `HISTORY_LOST` render an alert, clicks the real reconnect button for `DISCONNECTED`, and asserts `CONNECTED` plus `HEALTHY` renders no alert. Targeted Vitest run passed 4/4 tests. |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| Phase plan artifacts | 34 declared artifacts across `02A-00` through `02A-05` | VERIFIED | `gsd-sdk query verify.artifacts` passed all 34/34 declared artifacts. |
| `backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java` | Thin Pub/Sub push receiver with sanitized persistence handoff | VERIFIED | Decodes notification for `emailAddress` and `historyId`, validates `messageId`, and passes `"{}"` to service. No repository injection. |
| `backend/core/src/main/java/com/zeromail/core/gmail/service/PubSubIngestionService.java` | Tenant lookup plus tenant-bound delivery insert | VERIFIED | Uses JdbcTemplate for pre-tenant lookup, then `ScopedValue.where(TenantContext.TENANT, ...)` and `TransactionTemplate` around `insertPendingIfAbsent`. |
| `backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java` | End-to-end Pub/Sub integration and privacy assertions | VERIFIED | Tests missing auth 401, known tenant insert, unknown email drop, duplicate idempotency, malformed payload drop, missing message id drop, and sanitized payload `{}`. |
| `apps/web/features/gmail/components/ReconnectPrompt.tsx` | Presentational reconnect alert plus gate helper | VERIFIED | `shouldShowReconnectPrompt` gates `DISCONNECTED` and `CONNECTED` with non-HEALTHY ingestion; `ReconnectPromptGate` returns prompt only when gate is true. |
| `apps/web/features/gmail/components/ReconnectPrompt.test.tsx` | Render-level gate tests | VERIFIED | No skip markers; tests render/click actual component states. |
| `apps/web/app/(protected)/settings/page.tsx` | Settings pause toggle and reconnect gate wiring | VERIFIED | Reads `/me` data, computes status/ingestion health, mounts `ReconnectPromptGate`, and mutates triage pause from the toggle. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| Pub/Sub security chain | `PubSubOidcAuthFilter` | `PubSubSecurityConfig.addFilterBefore` | WIRED | `/internal/pubsub/**` chain is order 1, stateless, CSRF-disabled, and includes the OIDC filter before `UsernamePasswordAuthenticationFilter`. |
| `GmailPubSubController` | `PubSubIngestionService` | `ingestPushEnvelope(...)` | WIRED | Controller injects service only and passes decoded routing fields plus sanitized `"{}"`. |
| `PubSubIngestionService` | `PubSubDeliveryRepository` | `insertPendingIfAbsent` | WIRED | Service uses tenant-bound transaction and branches on `inserted == 0` for duplicates. |
| `GmailHistoryProcessor` | `GmailDeliveryProcessingService` | injected service call inside `ScopedValue.where` | WIRED | Claimed rows are processed under tenant ScopedValue and handed to public transactional service method. |
| `GmailDeliveryProcessingService` | `MailMessageObservedRepository` | `insertObservedIfAbsent` | WIRED | Metadata-only Gmail fetch produces observed rows via native idempotent insert. |
| `settings/page.tsx` | `ReconnectPromptGate` | status plus ingestionHealth props | WIRED | Settings receives `/me` connection health and passes it into the gate before rendering the prompt. |
| Pause UI | Backend pause flag | `useToggleTriagePause` -> `PUT /tenant/triage-pause` -> `/me` -> `PauseBanner` | WIRED | Mutation invalidates `accountKeys.me()`, `/me` exposes `triagePaused`, and banner renders from the same current-user query. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `GmailPubSubController` / `PubSubIngestionService` | `pubsub_message_id`, `history_id`, sanitized `payload` | Pub/Sub envelope JSON plus service tenant lookup | Yes | FLOWING. Payload is intentionally sanitized to `{}` while IDs flow to DB. |
| `GmailDeliveryProcessingService` | observed Gmail message metadata | Gmail `history().list(...).setLabelId("INBOX")` and `messages().get(...).setFormat("metadata")` | Yes | FLOWING. Inserts id/thread/labels/internalDate only, no raw body fields. |
| `ReconnectPromptGate` in settings | `connStatus`, `ingestionHealth` | `useCurrentUser` -> `GET /me` -> `MeController` -> `GmailConnectionService.currentStatus` | Yes | FLOWING. Not hardcoded; values come from DB projection or NOT_CONNECTED sentinel. |
| `PauseBanner` and settings toggle | `triagePaused` | `useCurrentUser` -> `GET /me` -> `TenantService.isTriagePaused` | Yes | FLOWING. Toggle writes via API and invalidates the current-user query. |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full backend suite | `.\gradlew.bat clean check` | `BUILD SUCCESSFUL` in 1m 53s | PASS |
| API Pub/Sub, OIDC, `/me`, and pause tests | `.\gradlew.bat :backend:api:test --tests "*PubSubOidcAuthFilterTest*" --tests "*GmailPubSubControllerIntegrationTest*" --tests "*MeControllerTest*" --tests "*TriagePauseControllerTest*" --tests "*PubSubIdempotencyTest*"` | `BUILD SUCCESSFUL` | PASS |
| Core persistence/entity tests | `.\gradlew.bat :backend:core:test --tests "*PubSubDeliveryEntityTest*" --tests "*MailMessageObservedEntityTest*" --tests "*GmailIngestionHealthTest*"` | `BUILD SUCCESSFUL` | PASS |
| Worker watch/history tests | `.\gradlew.bat :backend:worker:test --tests "*GmailWatchSchedulerTest*" --tests "*GmailHistoryProcessorTest*"` | `BUILD SUCCESSFUL` | PASS |
| Phase 02A frontend tests | `pnpm --filter web exec vitest run features/triage/components/PauseBanner.test.tsx features/triage/hooks/useToggleTriagePause.test.tsx __tests__/architecture/phase-02a-files.test.ts features/gmail/components/ReconnectPrompt.test.tsx --reporter=verbose` | 4 files / 14 tests passed | PASS |
| Frontend typecheck | `pnpm --filter web run typecheck` | Exit 0 | PASS |
| Frontend lint | `pnpm --filter web run lint` | Exit 0 | PASS |
| Frontend i18n | `pnpm --filter web run i18n:check` | 318 leaf keys, parity OK | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| MAIL-01 | 02A-00, 02A-01, 02A-02, 02A-03 | Register `users.watch` and process Pub/Sub push notifications | SATISFIED | Watch scheduler issues `users.watch`; Pub/Sub API inserts delivery rows; worker processes delivery into `mail_message_observed`. |
| MAIL-02 | 02A-02 | Renew `users.watch` before expiry with health alerting | SATISFIED | Renewal query selects near-expiry rows; scheduler records success or marks `WATCH_UNHEALTHY`; `/me` and UI expose ingestion health. |
| MAIL-03 | 02A-01, 02A-03 | Verify Google OIDC tokens on every Pub/Sub push | SATISFIED | Filter verifies issuer, audience, signature, expiry, and service-account email; missing auth integration test returns 401. |
| MAIL-04 | 02A-01, 02A-03 | Idempotent processing per tenant/history/message | SATISFIED | Delivery and observed-message repositories use native `ON CONFLICT DO NOTHING`; API and worker duplicate tests pass. |
| MAIL-05 | 02A-02, 02A-04 | Bound history-404 recovery with visible reconnect prompt | SATISFIED | 404 marks `HISTORY_LOST` without full rescan; watch renewal preserves HISTORY_LOST; `/me` and settings gate render reconnect prompt for unhealthy ingestion. |
| MAIL-06 | 02A-01, 02A-03, 02A-04 | User can globally pause automated triage actions from UI | SATISFIED | Tenant has `triage_paused`; API persists it; `/me` exposes it; settings toggle and persistent banner are wired and tested. |

No orphaned Phase 2A requirements were found in `.planning/REQUIREMENTS.md`.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `apps/web/features/gmail/components/ReconnectPrompt.tsx` | 60-61 | `return null` | Info | Expected gate behavior for healthy/non-disconnected state, covered by render-level tests. |
| `apps/web/features/triage/components/PauseBanner.tsx` | 14 | `return null` | Info | Expected conditional render when triage is not paused, covered by render-level tests. |

No blocking TODO/FIXME/placeholder, skipped Phase 02A test, console-only handler, hardcoded empty rendered data, raw Pub/Sub payload persistence, or token logging pattern was found in the reviewed Phase 02A files.

### Human Verification Required

### 1. End-to-End Pub/Sub Delivery On Staging VPS

**Test:** Provision a real Pub/Sub push subscription to `/internal/pubsub/gmail`, connect a test Gmail account, send a message, then replay the same Pub/Sub delivery.
**Expected:** One tenant-attributed `mail_message_observed` row appears, and replay creates no duplicate row.
**Why human:** Requires real Google Pub/Sub, Gmail, deployed HTTPS, and Google-signed OIDC.

### 2. `users.watch` 7-Day Expiry Renewal

**Test:** Backdate `gmail_connections.watch_expires_at` to within 24 hours for a staging tenant and observe the worker.
**Expected:** `watch_renewed_at` advances and `last_synced_history_id` is preserved.
**Why human:** Requires live worker timing and Google watch behavior.

### 3. Reconnect Prompt After Actual History-404

**Test:** Force or reproduce a Gmail history-404 for a connected staging tenant, open settings, and click reconnect.
**Expected:** `ingestion_health = HISTORY_LOST`, reconnect prompt is visible, and the click starts the Gmail OAuth reconnect path.
**Why human:** Real Gmail history expiration is not practical to trigger in automated CI.

### 4. Pause Toggle Visual Hierarchy

**Test:** Open `/settings`, toggle pause, inspect the persistent banner, then unpause from the banner.
**Expected:** Toggle and banner are visually clear at target viewports; banner hides after unpause.
**Why human:** Visual clarity and layout hierarchy require inspection.

### Gaps Summary

No automated goal-achievement gaps remain. The two specific previous gaps are closed:

- `pubsub_delivery.payload` now persists sanitized `{}` instead of reversible Pub/Sub `message.data` or decoded `emailAddress`, and the integration test proves it.
- `ReconnectPrompt.test.tsx` now renders real `ReconnectPromptGate` UI states and clicks the reconnect CTA.

Overall status is `human_needed` only because the phase still has staging/visual checks that cannot be proven from local code and tests.

---

_Verified: 2026-04-29T08:04:45Z_
_Verifier: the agent (gsd-verifier)_
