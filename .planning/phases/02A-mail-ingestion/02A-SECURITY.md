---
phase: 02A
slug: mail-ingestion
status: verified
threats_open: 0
asvs_level: 1
created: 2026-05-05
---

# Phase 02A - Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Google Pub/Sub -> `/internal/pubsub/gmail` | Machine-to-machine push endpoint; OIDC token must verify before business logic | Pub/Sub push envelope, Gmail email address, history ID |
| Browser -> `/tenant/triage-pause` | Authenticated user action; tenant context is bound from the session | Pause boolean |
| Worker -> Google OAuth endpoint | Refresh token exchange crosses the network boundary | Refresh token and access token, never logged |
| Worker -> Gmail API | Watch renewal, history listing, and message metadata fetches cross Google API boundary | Gmail history IDs, message IDs, labels, internal dates |
| Scheduled worker -> `TenantContext` | Scheduled threads must explicitly bind tenant scope before tenant-owned persistence | Tenant ID scoped to delivery/connection row |
| DB schema/entity layer | Liquibase constraints and Hibernate tenant discriminator protect persisted ingestion state | Pub/Sub delivery rows, observed-message metadata, Gmail connection health |
| Frontend render of Gmail state | Owner-only UI renders pause and reconnect status from authenticated `/me` response | Triage pause flag, ingestion health enum, owner Gmail address |
| Test environment | Hermetic fixtures; no production secrets | Synthetic OIDC/Gmail fixtures only |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| 02A-00-T-01 | Spoofing | Pub/Sub OIDC test contract | mitigate | `PubSubOidcAuthFilterTest` covers valid token, wrong audience, wrong email, wrong issuer, expired token, bad signature, and non-Pub/Sub path guard. | closed |
| 02A-00-T-02 | Tampering | Idempotency test coverage | mitigate | `pubsub_delivery` has `UNIQUE(tenant_id, pubsub_message_id)` and repository insert uses `ON CONFLICT DO NOTHING`; persistence and controller duplicate tests pass. | closed |
| 02A-00-T-03 | Tampering | `/me` response field contract | mitigate | `MeResponse` includes `triagePaused` and nested Gmail `ingestionHealth`; `MeControllerTest` asserts both fields. | closed |
| 02A-01-T-02 | Tampering | `pubsub_delivery` uniqueness | mitigate | Liquibase changeset 011 defines `uq_pubsub_delivery_tenant_message`; repository insert is atomic with `ON CONFLICT`. | closed |
| 02A-01-T-05 | Information Disclosure | `mail_message_observed` schema and reads | mitigate | Schema/entity store only `tenant_id`, Gmail IDs, history ID, labels, internal date, and observed time; no subject/from/body/snippet/sender/recipient columns; entity uses `@TenantId`. | closed |
| 02A-01-T-06 | Tampering | Email address lookup query | mitigate | Gmail email lookup is parameterized (`WHERE LOWER(google_email) = ?`) before tenant binding. | closed |
| 02A-01-T-04 | Information Disclosure | Refresh-token logging in data-layer plan | accept | Accepted risk AR-02A-01 documents that token cipher/logging lives outside this data-layer wave; later worker code still avoids token logging and wraps refreshed access tokens in `Sensitive<String>`. | closed |
| 02A-02-T-04 | Information Disclosure | `GmailApiClientFactory.refreshAccessToken` | mitigate | Refresh token parameter is only URL-encoded into the OAuth request body; returned access token is wrapped as `Sensitive<String>`; worker/service logs use event names and tenant IDs only. | closed |
| 02A-02-T-05 | Information Disclosure | Gmail delivery processing loop | mitigate | Gmail message fetch uses `setFormat("metadata")` and `setFields("id,threadId,labelIds,internalDate")`; observed insert stores only those metadata fields. | closed |
| 02A-02-T-09 | Denial of Service | Gmail watch retry loop | mitigate | Scheduler increments failures, marks `WATCH_UNHEALTHY` after threshold, continues scheduled renewal, and `recordWatchSuccess` resets failures and clears only `WATCH_UNHEALTHY` back to `HEALTHY`. | closed |
| 02A-02-T-11 | Tampering | Delivery crash recovery | mitigate | Claim query reclaims expired `PROCESSING` rows with `locked_until < NOW()` and `FOR UPDATE SKIP LOCKED`; processing is public `@Transactional`, inserts observations idempotently, and updates history pointer monotonically. | closed |
| 02A-02-T-03 | Elevation of Privilege | History gap truncation | accept | Accepted risk AR-02A-02 documents the 500-item cap and explicit `HISTORY_LOST` reconnect path for dropped-gap recovery. | closed |
| 02A-03-T-01 | Spoofing | `PubSubOidcAuthFilter` | mitigate | Dedicated `@Order(1)` Pub/Sub chain verifies audience, issuer, certificate signature, expiry, and service-account email before the controller can run; the filter is path-guarded and disabled as a generic servlet registration. | closed |
| 02A-03-T-06 | Tampering | `PubSubIngestionService` tenant lookup | mitigate | Service performs an unscoped parameterized JDBC lookup for `tenant_id`, then binds `TenantContext` before inserting tenant-owned delivery rows. | closed |
| 02A-03-T-07 | Elevation of Privilege | `TriagePauseController` | mitigate | User-session security chain requires authentication for non-public routes; controller derives tenant ID from `TenantContext.currentOrThrow()` and delegates the tenant write to `TenantService`. | closed |
| 02A-03-T-08 | Denial of Service | Gmail Pub/Sub ack path | mitigate | Controller only parses the envelope, decodes JSON, delegates `ingestPushEnvelope`, and returns `void`; no Gmail API calls or direct repository dependencies exist in the controller. | closed |
| 02A-03-T-10 | Information Disclosure | `/me` Gmail email display | accept | Accepted risk AR-02A-03 documents that `googleEmail` is returned only to the authenticated owner through `/me` and is not logged. | closed |
| 02A-03-T-02 | Tampering | Duplicate Pub/Sub delivery insert | mitigate | Native insert returns row count and uses `ON CONFLICT (tenant_id, pubsub_message_id) DO NOTHING`, avoiding rollback-only duplicate handling. | closed |
| 02A-04-T-07 | Elevation of Privilege | Frontend pause toggle | mitigate | Frontend calls `PUT /tenant/triage-pause` through the credentialed API client and sends `X-XSRF-TOKEN`; backend remains the authorization boundary. | closed |
| 02A-04-T-10 | Information Disclosure | Ingestion-health display | accept | Accepted risk AR-02A-04 documents that raw health enum values are not rendered; `ReconnectPromptGate` maps unhealthy states to unified reconnect copy. | closed |
| 02A-05-T-01 | Spoofing | Final Pub/Sub OIDC gate | mitigate | Seven-case OIDC test passes with 0 failures; invalid audience/email/issuer/expiry/signature return 401 and non-Pub/Sub paths skip the filter. | closed |
| 02A-05-T-05 | Information Disclosure | Domain boundary and safety contracts | mitigate | Core ArchUnit boundary, safety-contract, and tenant-isolation tests pass; observed-message schema remains metadata-only. | closed |

*Status: open / closed*
*Disposition: mitigate (implementation required) / accept (documented risk) / transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-02A-01 | 02A-01-T-04 | Data-layer wave did not own token cipher implementation; no refresh-token fields were introduced in the new entities, and later worker verification confirms token logs remain event/tenant-only. | Phase PLAN disposition | 2026-05-05 |
| AR-02A-02 | 02A-02-T-03 | Gmail history fan-out caps each page at 500 results to prevent runaway processing; history loss is surfaced via `HISTORY_LOST` and requires reconnect rather than silently expanding the scan. | Phase PLAN disposition | 2026-05-05 |
| AR-02A-03 | 02A-03-T-10 | Displaying the connected Gmail address to the authenticated owner is acceptable product behavior; code evidence shows it is response-only and not logged. | Phase PLAN disposition | 2026-05-05 |
| AR-02A-04 | 02A-04-T-10 | Frontend accepts backend health enum as control input but does not expose raw enum text; unhealthy states render the same reconnect prompt. | Phase PLAN disposition | 2026-05-05 |

---

## Evidence

| Threat Ref | Evidence |
|------------|----------|
| 02A-00-T-01, 02A-03-T-01, 02A-05-T-01 | `backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java:29` builds `TokenVerifier` with audience, issuer, and certificates; `:37` path-guards non-Pub/Sub requests; `:54` verifies token and `:56` checks service-account email. `PubSubSecurityConfig.java:30` disables generic servlet registration and `:35` contributes the `@Order(1)` Pub/Sub chain. `PubSubOidcAuthFilterTest` result: 7 tests, 0 failures. |
| 02A-00-T-02, 02A-01-T-02, 02A-03-T-02 | `backend/core/src/main/resources/db/changelog/changes/011-pubsub-delivery-table.yaml:68` defines the tenant/message unique constraint. `PubSubDeliveryRepository.java:82` inserts pending rows and `:88` uses `ON CONFLICT (tenant_id, pubsub_message_id) DO NOTHING`. `PubSubDeliveryEntityTest`, `PubSubIdempotencyTest`, and `GmailPubSubControllerIntegrationTest` duplicate cases pass. |
| 02A-00-T-03 | `backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java:11` includes `triagePaused`; `:14` defines `GmailConnectionStatusExtended(status, ingestionHealth, googleEmail)`. `MeControllerTest` result: 4 tests, 0 failures. |
| 02A-01-T-05, 02A-02-T-05, 02A-05-T-05 | `backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml:10` through `:45` contains only tenant/message/thread/history/labels/internal-date/observed-at fields. `MailMessageObservedEntity.java:22` applies `@TenantId`; `MailMessageObservedRepository.java:18` inserts only metadata fields. `GmailDeliveryProcessingService.java:138` fetches messages with `metadata` format and `:139` limits fields to `id,threadId,labelIds,internalDate`. |
| 02A-01-T-06, 02A-03-T-06 | `PubSubIngestionService.java:52` uses `JdbcTemplate.query`; `:54` selects only `tenant_id`; `:56` parameterizes email comparison; `:70` binds `TenantContext` before `:72` inserts delivery rows. |
| 02A-02-T-04 | `GmailApiClientFactory.java:62` returns `TokenRefreshResult(Sensitive<String> accessToken, Instant expiresAt)` and `:89` wraps refreshed access token with `Sensitive.of`. `GmailWatchScheduler.java:80`, `:83`, `:89`, and `:91` log only event names, tenant ID, and attempt counts. `GmailDeliveryProcessingService.java:73`, `:97`, `:103`, `:111`, `:165`, and `:168` log only event names, tenant ID, history pointers, and counts. |
| 02A-02-T-09 | `GmailWatchScheduler.java:85` increments watch failure, `:87` checks threshold, and `:88` marks watch unhealthy. `GmailConnectionService.java:168` records watch success, `:176` resets consecutive failures, and `:177` to `:178` clears only `WATCH_UNHEALTHY` to `HEALTHY`. |
| 02A-02-T-11 | `PubSubDeliveryRepository.java:29` to `:33` reclaims pending/expired processing rows with `FOR UPDATE SKIP LOCKED`; `GmailDeliveryProcessingService.java:28` is transactional, `:146` inserts observations idempotently, and `:95` updates the history pointer monotonically. |
| 02A-02-T-03 | `GmailDeliveryProcessingService.java:86` caps Gmail history list pages at 500 results. `GmailConnectionService.java:151` to `:154` marks `HISTORY_LOST` when the pointer is missing/lost. |
| 02A-03-T-07, 02A-04-T-07 | `SecurityConfig.java:27` permits only public routes and `:28` authenticates all other requests. `TriagePauseController.java:31` exposes `PUT /tenant/triage-pause`, `:33` reads `TenantContext.currentOrThrow()`, and `:34` delegates the tenant write to `TenantService`. `apps/web/lib/api/client.ts:8` uses `credentials: 'include'`; `apps/web/features/triage/api/triagePause.ts:4` calls the endpoint and `:6` sends the XSRF header. |
| 02A-03-T-08 | `GmailPubSubController.java:32` receives the push, `:33` to `:55` only validates/decodes the envelope, and `:56` delegates to `PubSubIngestionService`. Static search found no `GmailConnectionRepository` or `PubSubDeliveryRepository` references in the controller. |
| 02A-03-T-10 | `MeController.java:53` derives tenant from `TenantContext`, `:74` reads pause state, and `:76` to `:79` constructs the owner-only Gmail status response. Static search found no `googleEmail` log statements in new worker/API ingestion code. |
| 02A-04-T-10 | `apps/web/features/gmail/components/ReconnectPrompt.tsx:44` to `:48` maps unhealthy states to a boolean gate; `:60` to `:64` renders the unified prompt, not the enum value. `ReconnectPrompt.test.tsx` covers `WATCH_UNHEALTHY`, `HISTORY_LOST`, `DISCONNECTED`, and `HEALTHY` behavior. |

---

## Summary Threat Flags

No unregistered threat flags were reported in the execution summaries. `02A-04-SUMMARY.md` and `02A-05-SUMMARY.md` both state `Threat Flags: None`; earlier summaries did not introduce new threat-flag sections.

---

## Verification Notes

| Check | Result |
|-------|--------|
| `.\gradlew.bat :backend:core:test --tests "*GmailIngestionHealthTest*" --tests "*PubSubDeliveryEntityTest*" --tests "*MailMessageObservedEntityTest*"` | PASS by test XML: 12 tests, 0 failures. |
| `.\gradlew.bat :backend:worker:test --tests "*GmailWatchSchedulerTest*" --tests "*GmailHistoryProcessorTest*"` | PASS by test XML: 13 tests, 0 failures. |
| Phase 2A API security/controller tests | PASS by test XML: `PubSubOidcAuthFilterTest` 7, `GmailPubSubControllerIntegrationTest` 6, `PubSubIdempotencyTest` 2, `TriagePauseControllerTest` 3, `MeControllerTest` 4, all 0 failures. |
| `.\gradlew.bat :backend:core:test --tests "*DomainBoundaryArchTests*" --tests "*SafetyContractArchTests*" --tests "*TenantIsolationArchTests*"` | PASS. |
| `pnpm -F web run test:run` | PASS: 27 files, 151 tests. |
| `pnpm -F web run typecheck` | PASS. |
| `pnpm -F web run lint` | PASS. |
| `pnpm -F web run i18n:check` | PASS: vi/en parity, 318 leaf keys. |
| `.\gradlew.bat clean check` | FAILED outside the declared Phase 2A threat register: `ControllerBoundaryArchTests.controllers_do_not_touch_entities` matches Spring `org.springframework.http.ResponseEntity` because the rule uses `.*Entity`. Source search found no controller dependency on persistence entities or repositories. |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-05-05 | 22 | 22 | 0 | Codex / gsd-secure-phase |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-05-05
