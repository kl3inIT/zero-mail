# Phase 10: Telegram Messaging Assistant — Pattern Map

**Mapped:** 2026-05-28
**Phase:** 10 - Telegram Messaging Assistant
**Files analyzed:** 47 (new/modified)
**Analogs found:** 41 / 47 (87% coverage; 6 NEW patterns flagged)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| **Foundation (Wave 0)** |
| `backend/core/src/main/java/com/zeromail/core/triage/domain/TriageDecisionRecorded.java` | domain event record | event-driven publish | `core/gmail/event/MailMessageObserved.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/outbound/domain/OutboundActionSource.java` | enum (IdentifiedEnum) | value object | `core/queue/domain/JobFailureReason.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/chat/domain/ResponseSurface.java` | enum | value object | `core/queue/domain/JobFailureReason.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/outbound/usecases/OutboundSendCommand.java` *(modified — add source)* | command record | request-response | self | n/a (modify) |
| **Audit table + dedup (Wave 0 / 1)** |
| `backend/core/src/main/resources/db/changelog/changes/099-telegram-account.yaml` | liquibase changeset | schema migration | `changes/043-assistant-pending-action.yaml` | role-match |
| `backend/core/src/main/resources/db/changelog/changes/100-outbound-action-audit.yaml` | liquibase changeset | schema migration | `changes/086-triage-audit-source.yaml` | exact |
| `backend/core/src/main/resources/db/changelog/changes/101-telegram-notification-dedup.yaml` | liquibase changeset | schema migration | `changes/043-assistant-pending-action.yaml` | role-match |
| `backend/core/src/main/resources/db/changelog/changes/102-telegram-notification-log.yaml` | liquibase changeset | schema migration | `changes/043-assistant-pending-action.yaml` | role-match |
| `backend/core/src/main/resources/db/changelog/changes/103-app-db-grants-telegram.yaml` | liquibase changeset | grant-only DDL | `changes/078-processing-job-extend.yaml` | partial |
| `backend/core/src/main/java/com/zeromail/core/outbound/persistence/OutboundActionAuditEntity.java` | JPA entity (class) | CRUD | `core/triage/persistence/TriageAuditEntity.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/outbound/persistence/OutboundActionAuditRepository.java` | Spring Data repo | CRUD | `core/gmail/persistence/GmailConnectionRepository.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/outbound/persistence/OutboundActionAuditWriter.java` | writer service | CRUD (insert) | `core/triage/persistence/TriageAuditWriter.java` | exact |
| **MailAction module (Wave 0/1)** |
| `backend/core/src/main/java/com/zeromail/core/mailaction/package-info.java` | Modulith config | n/a | `core/outbound/package-info.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/mailaction/usecases/MailActionService.java` | interface | request-response | `core/outbound/usecases/OutboundSendGateway.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/mailaction/usecases/DefaultMailActionService.java` | service impl | request-response + CRUD | `core/triage/usecases/TriageGmailWriter.java` + `outbound/usecases/GmailOutboundSendGateway.java` | exact |
| **Telegram module (Wave 1/2)** |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/package-info.java` | Modulith config | n/a | `core/chat/package-info.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/domain/TelegramUpdate.java` | DTO record | request DTO | `apps/api/dto/gmail/PubSubPushEnvelope` analog (records) | role-match |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/domain/TelegramMessage.java` | DTO record | request DTO | record convention | role-match |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/domain/TelegramCallbackQuery.java` | DTO record | request DTO | record convention | role-match |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/domain/TelegramAccount.java` | domain record | value object | `core/triage/domain/TriageActionResult.java` | role-match |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/persistence/TelegramAccountEntity.java` | JPA entity (class) | CRUD | `core/gmail/persistence/GmailConnectionEntity.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/persistence/TelegramAccountRepository.java` | Spring Data repo | CRUD | `core/gmail/persistence/GmailConnectionRepository.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/persistence/TelegramNotificationLogEntity.java` | JPA entity (class) | CRUD | `core/chat/persistence/AssistantActionAuditEntity.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/persistence/TelegramNotificationDedupRepository.java` | Spring Data repo | INSERT-on-conflict | `core/triage/persistence/TriageAuditRepository.java` | role-match |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/gateway/TelegramApiClient.java` | RestClient gateway | request-response (HTTP) | `core/admin/mkey/usecases/ModelsProbeClient.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/gateway/TelegramSendRateLimiter.java` | Bucket4j throttle | flow control | **NO ANALOG** (first Bucket4j adoption) | NEW |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/gateway/TelegramProperties.java` | @ConfigurationProperties record | config | `api/config/ZeroMailApiProperties.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/usecases/PairingCodeService.java` | service | crypto + compact code | RESEARCH §19 Java snippet + `gmail/persistence/crypto/RefreshTokenCipher.java` | NEW (research-driven) |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/usecases/PairingConsumeService.java` | use-case service | CRUD upsert | `core/admin/cat/usecases/CatalogSyncJobConsumer.java` | role-match |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/notification/TelegramNotificationListener.java` | @ApplicationModuleListener | event-driven async | `core/chat/llm/springai/ChatModelCacheEvictionListener.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/notification/TelegramButtonLabels.java` | i18n constants | static map | shared pattern | NEW |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/webhook/TelegramUpdateRouter.java` | router service | request-response | `core/triage/usecases/TriageOrchestratorService.java` | role-match |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/webhook/TelegramCallbackRouter.java` | router service | request-response | `chat/confirm/send/AssistantSendExecutor.java` | role-match |
| `backend/core/src/main/java/com/zeromail/core/messaging/telegram/chat/TelegramChatStreamSink.java` | ChatStreamSink impl | streaming (Flux) | `chat/usecases/ChatStreamSink.java` interface + `chat/llm/VercelProtocolEmitter.java` | role-match |
| **Worker drain (Wave 2)** |
| `backend/worker/src/main/java/com/zeromail/worker/messaging/MessagingNotificationProcessor.java` | worker handler | batch drain | `worker/cleanup/ProcessingJobWorker.java` + `worker/cleanup/UnsubscribeCampaignHandler.java` | exact |
| `backend/worker/src/main/java/com/zeromail/worker/messaging/TelegramOutboxDrainer.java` *(or extend ProcessingJobWorker dispatch switch)* | worker scheduler | batch drain | `worker/cleanup/ProcessingJobWorker.java` | exact |
| **API surface (Wave 2)** |
| `backend/api/src/main/java/com/zeromail/api/controllers/integrations/TelegramWebhookController.java` | REST controller | request-response (webhook) | `api/controllers/gmail/GmailPubSubController.java` | exact |
| `backend/api/src/main/java/com/zeromail/api/controllers/integrations/TelegramPairingController.java` | REST controller | request-response | `api/controllers/gmail/ConnectGmailController.java` | role-match |
| `backend/api/src/main/java/com/zeromail/api/controllers/integrations/TelegramStatusController.java` | REST controller | request-response (poll) | `api/controllers/gmail/GmailInboxController.java` style | role-match |
| `backend/api/src/main/java/com/zeromail/api/security/TelegramWebhookSecurityConfig.java` | @Order security filter chain | request-response (filter) | `api/security/PubSubSecurityConfig.java` | exact |
| `backend/api/src/main/java/com/zeromail/api/security/TelegramWebhookSecretFilter.java` | OncePerRequestFilter | request-response (filter) | `api/security/PubSubOidcAuthFilter.java` | exact |
| `backend/api/src/main/java/com/zeromail/api/security/TelegramWebhookIpAllowlistFilter.java` | OncePerRequestFilter | request-response (filter) | `api/security/PubSubOidcAuthFilter.java` | partial |
| `backend/api/src/main/java/com/zeromail/api/dto/integrations/PairingResponse.java` | DTO record | response DTO | record convention | role-match |
| **ArchUnit (Wave 0)** |
| `backend/core/src/test/java/com/zeromail/core/arch/TelegramPathBodyBanTest.java` | ArchUnit test | invariant gate | `arch/ChatPersistenceContentBanTest.java` | exact |
| `backend/core/src/test/java/com/zeromail/core/arch/MailActionServiceArchTest.java` | ArchUnit test | invariant gate | `arch/OnlyOneGmailSendCallSiteTest.java` | exact |
| `backend/core/src/test/java/com/zeromail/core/arch/OutboundActionAuditMandatoryArchTest.java` | ArchUnit test | invariant gate | `arch/OnlyOneGmailSendCallSiteTest.java` | role-match |
| `backend/core/src/test/java/com/zeromail/core/arch/TelegramOutboxDrainArchTest.java` | ArchUnit test | invariant gate | `arch/OnlyOneGmailSendCallSiteTest.java` | role-match |
| `backend/core/src/test/java/com/zeromail/core/arch/TelegramChatStreamingOnlyArchTest.java` | ArchUnit test | invariant gate | `arch/ChatLlmAdapterBoundaryTest.java` style | role-match |
| **Frontend (Wave 2/3)** |
| `apps/web/features/telegram-integration/api/telegram-api.ts` | feature API | request-response | `apps/web/features/gmail/api/gmail-api.ts` | exact |
| `apps/web/features/telegram-integration/query-keys.ts` | TanStack key factory | n/a | `apps/web/features/gmail/query-keys.ts` | exact |
| `apps/web/features/telegram-integration/hooks/useTelegramStatus.ts` | useQuery + refetchInterval | streaming poll | `apps/web/features/gmail/hooks/useTenantStatus.ts` | exact |
| `apps/web/features/telegram-integration/hooks/useStartPairing.ts` | useMutation | request-response | `apps/web/features/gmail/hooks/useDisconnectGmail.ts` | exact |
| `apps/web/features/telegram-integration/hooks/useDisconnect.ts` | useMutation | request-response | `apps/web/features/gmail/hooks/useDisconnectGmail.ts` | exact |
| `apps/web/features/telegram-integration/components/TelegramCard.tsx` | feature component | UI render | `apps/web/app/(protected)/(app)/settings/SettingsClient.tsx` card sections | role-match |
| `apps/web/features/telegram-integration/components/ConnectDialog.tsx` | feature component | UI render | shadcn Dialog usage in `SettingsClient.tsx` | role-match |
| `apps/web/app/(protected)/(app)/settings/connected-apps/page.tsx` | Next.js page | UI render | `apps/web/app/(protected)/(app)/settings/page.tsx` | exact |
| `apps/web/e2e/telegram-happy-path.spec.ts` | Playwright e2e | test | `apps/web/e2e/settings-notifications.spec.ts` | role-match |

---

## Pattern Assignments

### `backend/core/src/main/java/com/zeromail/core/triage/domain/TriageDecisionRecorded.java` (domain event, event-driven publish)

**Analog:** `D:\Semester-8\zero-mail\backend\core\src\main\java\com\zeromail\core\gmail\event\MailMessageObserved.java`

**Excerpt (full file — 12 lines):**
```java
package com.zeromail.core.gmail.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Integration event consumed by core.triage when Gmail ingestion observes a new inbox message.
 * Carries only stable Gmail ids and a timestamp; never subject, snippet, body, or sender display
 * name.
 */
public record MailMessageObserved(
        UUID tenantId, String gmailMessageId, String gmailThreadId, Instant observedAt) {}
```

**Divergence notes:**
- Phase 10 event lives in `core/triage/domain/` (not `core/triage/event/`) per SPEC TG-01 + Convention 6 (events shared across modules belong in `<module>/domain/`).
- Record carries **header-only** fields: `tenantId, gmailMessageId, gmailThreadId, classification, actionTaken, senderDomain, senderDisplayName, subjectTruncated, decidedAt`.
- MUST NOT include `body|bodyHtml|snippet|messageHtml|content|prompt|completion|token` (ArchUnit `TelegramPathBodyBanTest` enforces).
- Expose via `core/triage/domain/package-info.java` which already has `@NamedInterface("domain")` — no change needed.

---

### `backend/core/src/main/java/com/zeromail/core/outbound/domain/OutboundActionSource.java` (enum, value object)

**Analog:** `D:\Semester-8\zero-mail\backend\core\src\main\java\com\zeromail\core\queue\domain\JobFailureReason.java`

**Excerpt (lines 19-86, key pattern):**
```java
public enum JobFailureReason implements IdentifiedEnum {

    DOWNSTREAM_TIMEOUT,
    GMAIL_API_RATE_LIMIT,
    // ... 9 values total
    UNKNOWN;

    @Override
    public String id() {
        return name();
    }

    public static JobFailureReason fromId(String id) {
        return Stream.of(values())
                .filter(jobFailureReason -> jobFailureReason.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () -> new NoSuchElementException("Unknown JobFailureReason id: " + id));
    }
}
```

**Divergence notes:**
- Phase 10 enum values per SPEC TG-03 / D-01: `RULE_AUTO, WEB_CHAT_CONFIRMED, TELEGRAM_INLINE_BUTTON, TELEGRAM_INLINE_BUTTON_DESTRUCTIVE_CONFIRMED, TELEGRAM_CHAT_CONFIRMED, TELEGRAM_DEEPLINK_FROM_NOTIFICATION, WEB_LEGACY` (7 values).
- Same fail-loud `NoSuchElementException` pattern. Convention 4 — never use `ordinal()` for storage; CHECK constraint on `outbound_action_audit.source` mirrors the enum's `id()` values.
- New package `core/outbound/domain/` (currently outbound only has `usecases/`) — must add a `domain/package-info.java` and update `outbound/package-info.java` `@ApplicationModule` to expose the new sub-package (no `@NamedInterface` change because outbound module already publishes its API surface as `outbound :: api`).

---

### `backend/core/src/main/java/com/zeromail/core/outbound/persistence/OutboundActionAuditEntity.java` (JPA entity, CRUD)

**Analog:** `D:\Semester-8\zero-mail\backend\core\src\main\java\com\zeromail\core\triage\persistence\TriageAuditEntity.java`

**Excerpt (lines 22-90, entity shape):**
```java
@Entity
@Table(name = "triage_audit")
@AttributeOverride(name = "id", column = @Column(name = "audit_id", nullable = false))
@SuppressWarnings("JpaDataSourceORMInspection")
public class TriageAuditEntity extends AbstractTenantOwnedEntity {

    @Column(name = "gmail_message_id", nullable = false, length = 255)
    private String gmailMessageId;

    @Column(name = "gmail_thread_id", length = 255)
    private String gmailThreadId;

    @Column(name = "action_type", nullable = false, length = 32)
    private String actionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_args_json", columnDefinition = "jsonb", nullable = false)
    private String actionArgsJson;

    @Column(name = "decision", nullable = false, length = 32)
    private String decision;
    // ...
}
```

**Divergence notes:**
- Phase 10 entity extends `AbstractTenantOwnedEntity` (multi-tenant pattern). Column set per D-01 schema sketch.
- `source VARCHAR(64)` (longer than `triage_audit.source` which is plain `text`) — Hibernate maps as `String` with `@Column(length = 64)`.
- `metadata_json JSONB` uses `@JdbcTypeCode(SqlTypes.JSON)` exactly like `TriageAuditEntity.actionArgsJson`.
- No `@AttributeOverride` needed (PK column name = `id`, not `audit_id`).

---

### `backend/core/src/main/java/com/zeromail/core/outbound/persistence/OutboundActionAuditWriter.java` (writer service, CRUD insert)

**Analog:** `D:\Semester-8\zero-mail\backend\core\src\main\java\com\zeromail\core\triage\persistence\TriageAuditWriter.java`

**Excerpt (lines 30-79, mandatory-writer shape):**
```java
@Component
public class TriageAuditWriter {

    private final TriageAuditRepository triageAuditRepository;
    private final TriageActionResultJsonValidator actionResultJsonValidator;
    private final TriageActionArgsCanonicalizer actionArgsCanonicalizer;

    public TriageAuditWriter(/* injected deps */) { ... }

    public Optional<UUID> insertPending(
            UUID tenantId,
            String gmailMessageId,
            String gmailThreadId,
            String sanitizedSubject,
            String sanitizedSenderEmail,
            UUID ruleId,
            String ruleNameSnapshot,
            RuleActionType actionType,
            TriageActionResult preWriteIntent,
            String reasonEvidence) {
        return triageAuditRepository.insertAuditPendingIfAbsent(...);
    }
}
```

**Divergence notes:**
- Phase 10 writer exposes ONE method `record(...)` (no insertPending/insertTerminal split — outbound is fire-and-record, not pending-to-terminal).
- Signature: `record(UUID tenantId, String gmailMessageId, String gmailThreadId, OutboundAction action, OutboundActionSource source, UUID initiatedByUserId, boolean succeeded, String failureReason, String metadataJson)`.
- MUST be invoked in same `@Transactional` boundary as the underlying Gmail mutation — pair with ArchUnit `OutboundActionAuditMandatoryArchTest`.

---

### `backend/core/src/main/java/com/zeromail/core/mailaction/usecases/MailActionService.java` (interface, request-response)

**Analog:** `D:\Semester-8\zero-mail\backend\core\src\main\java\com\zeromail\core\outbound\usecases\OutboundSendGateway.java`

**Excerpt (full file — 8 lines):**
```java
package com.zeromail.core.outbound.usecases;

import java.io.IOException;

public interface OutboundSendGateway {

    OutboundSendResult send(OutboundSendCommand command) throws IOException;
}
```

**Divergence notes:**
- Phase 10 interface has 5 methods (NOT 1): `archive`, `markRead`, `markSpam`, `trash`, `snooze` each taking `(UUID tenantId, String gmailMessageId, OutboundActionSource source)` + extra `Instant snoozeUntil` for `snooze`.
- Each method declares `throws IOException` per the underlying Gmail API signature (same as `OutboundSendGateway.send`).
- Per D-03 also emits `MailActionPerformed` event (in-process, AFTER_COMMIT) — but Phase 10 does NOT register a listener; event exists for forward-compat.

---

### `backend/core/src/main/java/com/zeromail/core/mailaction/usecases/DefaultMailActionService.java` (service impl, CRUD + audit)

**Analog (Gmail call shape):** `D:\Semester-8\zero-mail\backend\core\src\main\java\com\zeromail\core\triage\usecases\TriageGmailWriter.java`

**Excerpt (lines 72-89, `archiveSkipInbox` shape):**
```java
public void archiveSkipInbox(UUID tenantId, String gmailMessageId) throws IOException {
    executeGmailWrite(
            tenantId,
            "archiveSkipInbox",
            gmail -> {
                gmail.users()
                        .messages()
                        .modify(
                                USER_ID,
                                gmailMessageId,
                                new ModifyMessageRequest()
                                        .setRemoveLabelIds(List.of(INBOX_LABEL_ID)))
                        .execute();
                logMessageWrite(tenantId, gmailMessageId, "archiveSkipInbox");
                return null;
            });
}
```

**Analog (audit-in-same-tx shape):** `D:\Semester-8\zero-mail\backend\core\src\main\java\com\zeromail\core\outbound\usecases\GmailOutboundSendGateway.java`

**Excerpt (lines 11-44, full impl):**
```java
@Component
@AllowedSendCallSite
public class GmailOutboundSendGateway implements OutboundSendGateway {

    private static final String USER_ID = "me";
    private final GmailApiClientFactory gmailApiClientFactory;

    public GmailOutboundSendGateway(GmailApiClientFactory gmailApiClientFactory) {
        this.gmailApiClientFactory = Objects.requireNonNull(gmailApiClientFactory, "gmailApiClientFactory");
    }

    @Override
    public OutboundSendResult send(OutboundSendCommand command) throws IOException {
        Objects.requireNonNull(command, "command must not be null");
        try {
            Gmail gmail = gmailApiClientFactory.buildClientForTenant(command.tenantId());
            Message sendResult =
                    gmail.users().messages().send(USER_ID, command.gmailMessage()).execute();
            return new OutboundSendResult(messageId(sendResult), threadId(sendResult));
        } catch (InvalidGrantException | IllegalStateException sendFailure) {
            throw new OutboundSendException(sendFailure);
        }
    }
}
```

**Divergence notes:**
- Each `MailActionService` method follows the GmailOutboundSendGateway shape: get `Gmail` client → call `.modify(...).execute()` → write `OutboundActionAuditWriter.record(...)` row in SAME `@Transactional` block.
- TriageGmailWriter's `executeGmailWrite(...)` lambda style is the template — but the new methods are public on `MailActionService` (interface seam), not internal helpers.
- After refactor, `TriageGmailWriter.archive/markRead/markSpam/star/markUnread` delegate to `MailActionService.<method>(..., source = OutboundActionSource.RULE_AUTO)`. Keep TriageGmailWriter as façade for triage-specific helpers (label resolution, draft create) which are NOT in MailActionService.
- ArchUnit `MailActionServiceArchTest` enforces: no `GmailApiClient.users().messages().modify(...)` / `.trash(...)` outside `core.mailaction.usecases` package (mirrors `OnlyOneGmailSendCallSiteTest` shape).

---

### `backend/core/src/main/java/com/zeromail/core/messaging/telegram/package-info.java` (Modulith config)

**Analog:** `D:\Semester-8\zero-mail\backend\core\src\main\java\com\zeromail\core\chat\package-info.java`

**Excerpt (lines 20-51, full `@ApplicationModule`):**
```java
@ApplicationModule(
        displayName = "Chat (assistant streaming + confirmation)",
        allowedDependencies = {
            "llm",
            "llm :: domain",
            "llm :: gateway.springai",
            "llm :: routing",
            "llm :: usecases",
            "admin",
            "rules",
            "rules :: domain",
            "rules :: projection",
            "gmail",
            "gmail :: gateway",
            "outbound :: api",
            "triage",
            "triage :: domain",
            "triage :: usecases",
            "tenant",
            "config",
            "shared :: lock",
            "shared :: persistence",
            "shared :: lang",
            "shared :: privacy"
        })
package com.zeromail.core.chat;
```

**Divergence notes:**
- Phase 10 `core/messaging/telegram/package-info.java` carries (per RESEARCH §6 + D-03):
  - `chat`, `chat :: usecases`, `chat :: domain`, `chat :: llm` (TelegramChatStreamSink consumes ChatStreamSink + ChatOrchestrator + ResponseSurface)
  - `outbound`, `outbound :: api`, `outbound :: usecases`, `outbound :: domain` (OutboundSendGateway + OutboundActionSource)
  - `mailaction`, `mailaction :: usecases` (MailActionService — NEW module)
  - `triage :: domain` (TriageDecisionRecorded event subscription)
  - `gmail :: gateway` (optional — only if direct Gmail factory usage required; prefer routing through outbound/mailaction)
  - `tenant`, `tenant :: usecases`, `rules :: domain`, `rules :: projection`, `config`, `shared :: persistence`, `shared :: lang`, `shared :: privacy`, `shared :: exception`, `llm :: domain`, `llm :: gateway.springai`
- Need `core/mailaction/package-info.java` analogous to `core/outbound/package-info.java` (lines 1-21):
  ```java
  @ApplicationModule(
          displayName = "Mail Action",
          allowedDependencies = {
              "gmail",
              "gmail :: gateway",
              "tenant",
              "tenant :: usecases",
              "outbound :: domain",
              "outbound :: usecases",   // for OutboundActionSource enum + audit writer
              "shared :: exception",
              "shared :: lang",
              "shared :: persistence"
          })
  package com.zeromail.core.mailaction;
  ```
- After adding `mailaction`, update `chat/package-info.java` + `triage/package-info.java` allowedDependencies to include `mailaction :: usecases` (Triage refactor in D-03 needs it).

---

### `backend/core/src/main/java/com/zeromail/core/messaging/telegram/persistence/TelegramAccountEntity.java` (JPA entity, multi-tenant + lifecycle)

**Analog:** `D:\Semester-8\zero-mail\backend\core\src\main\java\com\zeromail\core\gmail\persistence\GmailConnectionEntity.java`

**Excerpt (lines 14-69, key shape):**
```java
@Entity
@Table(name = "gmail_connections")
public class GmailConnectionEntity extends AbstractTenantOwnedEntity {

    @Column(name = "google_email", nullable = false)
    private String googleEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GmailConnectionStatus status;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    protected GmailConnectionEntity() {}

    public GmailConnectionEntity(
            UUID id, UUID tenantId, String googleEmail, GmailConnectionStatus status) {
        super(id, tenantId);
        this.googleEmail = googleEmail;
        this.status = status;
    }
```

**Divergence notes:**
- Phase 10 `TelegramAccountEntity` extends `AbstractTenantOwnedEntity` (same multi-tenant parent — `tenant_id UUID NOT NULL UNIQUE`).
- Columns per TG-09: `telegramChatId BIGINT NOT NULL UNIQUE`, `telegramUserId BIGINT NOT NULL`, `telegramUsername VARCHAR(64)`, `languageCode VARCHAR(8)`, `status VARCHAR(20)` enum (`CONNECTED/BLOCKED/DISCONNECTED`), `notificationsEnabled BOOLEAN`, `notificationFilter String + @JdbcTypeCode(SqlTypes.JSON)`, `linkedAt/lastActiveAt/blockedAt/disconnectedAt Instant`.
- Status enum follows `@Enumerated(EnumType.STRING)` per Convention 4 + GmailConnectionEntity line 21-23.
- Status enum class `TelegramAccountStatus` MUST implement `IdentifiedEnum` (per Convention 4 + JobFailureReason precedent).
- Lifecycle setter pattern (lines 118-160) — explicit setters (NO Lombok).
- DB grants in `103-app-db-grants-telegram.yaml`: SELECT + INSERT + UPDATE only; DELETE FORBIDDEN (preserve audit history).

---

### `backend/core/src/main/java/com/zeromail/core/messaging/telegram/persistence/TelegramAccountRepository.java` (Spring Data repo)

**Analog:** `D:\Semester-8\zero-mail\backend\core\src\main\java\com\zeromail\core\gmail\persistence\GmailConnectionRepository.java`

**Excerpt (full file — 41 lines):**
```java
public interface GmailConnectionRepository extends JpaRepository<GmailConnectionEntity, UUID> {

    Optional<GmailConnectionEntity> findByTenantId(UUID tenantId);

    Optional<GmailConnectionEntity> findByGoogleEmailIgnoreCase(String googleEmail);

    @Query(
            value = """
            SELECT * FROM gmail_connections
            WHERE status = 'CONNECTED'
              AND (watch_expires_at IS NULL OR watch_expires_at < NOW() + INTERVAL '24 hours')
            ORDER BY watch_renewed_at NULLS FIRST
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true)
    @Transactional
    List<GmailConnectionEntity> findConnectionsNeedingWatchRenewal(@Param("limit") int limit);
}
```

**Divergence notes:**
- Phase 10 repo methods: `findActiveByTenantId(UUID tenantId)`, `findByTelegramChatId(long telegramChatId)`, `findByTenantIdAndStatus(UUID tenantId, String status)`.
- For pairing consume (D-08): use native SQL `INSERT INTO telegram_account ... ON CONFLICT (tenant_id) DO UPDATE SET status='CONNECTED', relinked_at=NOW() RETURNING id` — pattern matches `findConnectionsNeedingWatchRenewal` (native SQL + @Transactional). Place in a low-level repo (`persistence/lowlevel/TelegramAccountConsumeRepository.java`) per Convention 2 / project layout.

---

### `backend/core/src/main/java/com/zeromail/core/messaging/telegram/gateway/TelegramApiClient.java` (RestClient gateway, blocking)

**Analog:** `D:\Semester-8\zero-mail\backend\core\src\main\java\com\zeromail\core\admin\mkey\usecases\ModelsProbeClient.java`

**Excerpt (lines 50-78, request-response shape):**
```java
public MasterKeyTestResult probeConnection(
        LlmProvider provider, KeyFormat keyFormat, String baseUrl, byte[] plaintextKey) {
    String resolvedBaseUrl = baseUrlFor(provider, baseUrl);
    try {
        RestClient.RequestHeadersSpec<?> requestHeadersSpecification =
                builderFor(resolvedBaseUrl)
                        .build()
                        .get()
                        .uri(joinPath(resolvedBaseUrl, "models"));
        String apiKey = new String(plaintextKey, StandardCharsets.UTF_8);
        applyHeaders(requestHeadersSpecification, provider, keyFormat, apiKey);
        requestHeadersSpecification.retrieve().toBodilessEntity();
        return withConstantJitter(MasterKeyTestResult.OK);
    } catch (RestClientResponseException providerRejection) {
        return withConstantJitter(mapStatus(providerRejection.getStatusCode().value()));
    } catch (ResourceAccessException resourceAccessException) {
        return withConstantJitter(
                isTimeout(resourceAccessException)
                        ? MasterKeyTestResult.TIMEOUT
                        : MasterKeyTestResult.NETWORK_ERROR);
    } catch (RestClientException restClientException) {
        return withConstantJitter(MasterKeyTestResult.NETWORK_ERROR);
    }
}
```

**Divergence notes:**
- Phase 10 `TelegramApiClient` methods: `sendMessage`, `editMessageText`, `editMessageReplyMarkup`, `answerCallbackQuery`, `setMyCommands`, `getMyCommands`, `setWebhook`, `setMyProfilePhoto`. All POST to `https://api.telegram.org/bot{token}/{method}`.
- Bot token in `Authorization` URL path (Telegram convention) — NOT in header. Build URL `String botPath = "/bot" + token + "/" + method`. Apply via `.uri(botPath)`.
- Use **default** `restClientBuilder` (HTTPS to api.telegram.org → HTTP/2 via ALPN OK). DON'T use `cleartextRestClientBuilder`.
- 429 handling: catch `RestClientResponseException` → parse body `parameters.retry_after` (NOT `Retry-After` header) → call `telegramSendRateLimiter.notify429(chatId, retryAfter)`.
- Exception mapping mirrors `mapStatus` (lines 173-184) but with Telegram-specific results: `TelegramErrorKind.RATE_LIMIT_429`, `TelegramErrorKind.FORBIDDEN_USER_BLOCKED`, `TelegramErrorKind.UNKNOWN_CHAT`, `TelegramErrorKind.UNKNOWN_5XX`.
- Constructor takes `RestClient.Builder restClientBuilder` + `TelegramProperties telegramProperties` + `ObjectMapper objectMapper` + `TelegramSendRateLimiter telegramSendRateLimiter`. Acquire rate-limit token BEFORE issuing the HTTP request.

---

### `backend/core/src/main/java/com/zeromail/core/messaging/telegram/gateway/TelegramSendRateLimiter.java` (Bucket4j throttle) — **NEW PATTERN**

**Analog:** No existing rate-limiter in codebase. RESEARCH §19 provides verified code template.

**Excerpt (RESEARCH §19, lines 1035-1061):**
```java
public final class TelegramSendRateLimiter {
    private final Bucket globalBucket;
    private final ConcurrentMap<Long, Bucket> perChatBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Instant> chatPausedUntil = new ConcurrentHashMap<>();

    public TelegramSendRateLimiter() {
        this.globalBucket = Bucket.builder()
            .addLimit(Bandwidth.builder().capacity(30).refillIntervally(30, Duration.ofSeconds(1)).build())
            .build();
    }

    public boolean tryAcquire(long telegramChatId) {
        Instant pausedUntil = chatPausedUntil.get(telegramChatId);
        if (pausedUntil != null && Instant.now().isBefore(pausedUntil)) return false;

        Bucket perChat = perChatBuckets.computeIfAbsent(telegramChatId, id ->
            Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(1).refillIntervally(1, Duration.ofSeconds(1)).build())
                .build());
        return perChat.tryConsume(1) && globalBucket.tryConsume(1);
    }

    public void notify429(long telegramChatId, int retryAfterSeconds) {
        chatPausedUntil.put(telegramChatId, Instant.now().plusSeconds(retryAfterSeconds));
    }
}
```

**Divergence notes:**
- **First Bucket4j adoption in codebase** — must add `com.bucket4j:bucket4j_jdk17-core:8.19.0` to `libs.versions.toml`.
- D-07 verbatim API `replenishAt` does NOT exist in Bucket4j 8.x — use `ConcurrentMap<Long, Instant> chatPausedUntil` per RESEARCH Q4/Pitfall 3.
- Declare as `@Component` (singleton process-local) per `core.messaging.telegram.gateway` package.
- Add `@PreDestroy` cleanup of `perChatBuckets` map entries with stale `last_active_at` (memory leak prevention; planner decides eviction cadence).
- No Redis backend — single-process worker only per Constraint locks. SEED-016 closes here.

---

### `backend/core/src/main/java/com/zeromail/core/messaging/telegram/gateway/TelegramProperties.java` (@ConfigurationProperties)

**Analog:** `D:\Semester-8\zero-mail\backend\api\src\main\java\com\zeromail\api\config\ZeroMailApiProperties.java`

**Excerpt (lines 13-25, record-style @ConfigurationProperties):**
```java
@ConfigurationProperties(prefix = "zero-mail.api")
@Validated
public record ZeroMailApiProperties(
        @Valid WebProperties web, @Valid CorsProperties cors, @Valid GmailProperties gmail) {

    public ZeroMailApiProperties {
        web = web == null ? WebProperties.defaults() : web;
        cors = cors == null ? CorsProperties.defaults() : cors;
        gmail = gmail == null ? GmailProperties.defaults() : gmail;
    }

    public record WebProperties(@DefaultValue("http://localhost:3000") @NotNull URI baseUrl) {
        static WebProperties defaults() {
            return new WebProperties(URI.create("http://localhost:3000"));
        }
    }
```

**Divergence notes:**
- Phase 10 prefix: `zero-mail.messaging.telegram` per CONTEXT Established Patterns (`zero-mail.*` kebab-case canonical form from Phase 02C P05b).
- Record fields per TG-05 + Claude's Discretion: `String botToken, String botUsername, String webhookSecret, String urlSecret, String messagingLinkSecret, URI apiBaseUrl (default https://api.telegram.org), boolean enabled (default true)`.
- Lives in `core/messaging/telegram/gateway/` package (NOT api.config — backend Convention 9 means each subproject owns its config classes; but shared TypedProperties can live in core).
- Bind both in `backend/api/application.yml` (webhook controller + pairing controller needs it) and `backend/worker/application.yml` (notification drain needs `botToken` for `TelegramApiClient.sendMessage`).
- `@Validated` + Bean Validation `@NotBlank` for `botToken/webhookSecret/urlSecret/messagingLinkSecret`. When `enabled=true` and `botToken` is blank → fail-fast at startup OR return 503 from endpoints (per TG-05 acceptance).

---

### `backend/core/src/main/java/com/zeromail/core/messaging/telegram/notification/TelegramNotificationListener.java` (@ApplicationModuleListener)

**Analog:** `D:\Semester-8\zero-mail\backend\core\src\main\java\com\zeromail\core\chat\llm\springai\ChatModelCacheEvictionListener.java`

**Excerpt (full file — 49 lines):**
```java
@Component
public class ChatModelCacheEvictionListener {

    private static final Logger log = LoggerFactory.getLogger(ChatModelCacheEvictionListener.class);

    private final SpringAiChatModelFactory chatModelFactory;
    private final ProviderMasterKeyResolver providerMasterKeyResolver;
    private final CuratedCatalogQueryService curatedCatalogQueryService;

    public ChatModelCacheEvictionListener(/* injected deps */) { ... }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MasterKeyRotatedEvent event) {
        chatModelFactory.evictByProvider(event.provider());
        providerMasterKeyResolver.invalidate(event.provider());
        log.info(
                "event=chat_model_cache_evicted reason=master_key_rotated provider={}",
                event.provider());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CatalogChangedEvent event) {
        chatModelFactory.evictByModelIds(event.affectedModelIds());
        // ...
    }
}
```

**Divergence notes:**
- Phase 10 uses `@ApplicationModuleListener` (RESEARCH Q4 — shortcut for `@Async + @Transactional(REQUIRES_NEW) + @TransactionalEventListener(AFTER_COMMIT)`) because the listener must INSERT into `telegram_notification_dedup` + `processing_job` (needs a transaction).
- Plain `@TransactionalEventListener(AFTER_COMMIT)` (analog's style) would lack a transaction — DB writes would silently fail.
- Method signature: `void on(TriageDecisionRecorded event)`. Look up `telegram_account` by `event.tenantId()`, check `notifications_enabled` + `notification_filter` predicate, then `INSERT INTO telegram_notification_dedup ... ON CONFLICT DO NOTHING RETURNING ...` (0 rows = suppress), then `processingJobRepository.enqueue(MESSAGING_NOTIFICATION, payload)`.
- Lives in `core/messaging/telegram/notification/` package. Reuse `core.modulith.event_publication` table (Liquibase 024) for at-least-once delivery resilience.
- Log line MUST follow Convention 5 format: `event=telegram_notification_enqueued tenantId={} kind={}` — never includes body/subject/email/prompt.

---

### `backend/core/src/main/java/com/zeromail/core/messaging/telegram/chat/TelegramChatStreamSink.java` (ChatStreamSink impl, streaming)

**Analog (interface contract):** `D:\Semester-8\zero-mail\backend\core\src\main\java\com\zeromail\core\chat\usecases\ChatStreamSink.java`

**Excerpt (full file — 26 lines):**
```java
public interface ChatStreamSink {

    void emitTextStart(String partId);
    void emitTextDelta(String partId, String tokenText);
    void emitTextEnd(String partId);
    void emitToolInputStart(String toolCallId, String toolName);
    void emitToolInputAvailable(String toolCallId, String toolName, String inputJson);
    void emitToolOutputAvailable(String toolCallId, String outputJson);
    void emitDataPersistence(UUID chatMessageId, String state);
    void emitFinish(String reason);
    void emitError(String code, String userFacingMessage);
    void emitHeartbeat();
}
```

**Divergence notes:**
- Phase 10 sink implements this interface, but renders to Telegram `editMessageText` instead of SSE.
- Internal state: `String accumulatedText` (per-stream buffer), `Integer placeholderMessageId` (returned by initial `sendMessage("✍️ Đang viết...")`).
- `emitTextDelta` appends to accumulator + triggers Reactor `Flux.sample(Duration.ofMillis(800))` (per RESEARCH §7 + D-06, NOT bufferTimeout per Pitfall 6).
- `emitToolInputAvailable` for send/reply/forward/saveDraft → render preview card via `TelegramButtonLabels` + `inline_keyboard` `[✅ Gửi][📝 Sửa][💾 Lưu nháp][❌ Huỷ]`. Tool args are user-authored draft (carve-out per CLAUDE.md privacy) and may be rendered.
- `emitToolOutputAvailable` for email-read tools (getMessage/searchInbox/getThread) → output is sanitized by `ToolOutputSanitizer` BEFORE arriving here; sink renders short summary only, never the body.
- `emitFinish` → final `editMessageText` removing typing indicator.
- `emitError` → terminal Telegram message "Lỗi kết nối tạm thời..." per D-08.
- ArchUnit `TelegramChatStreamingOnlyArchTest`: package `core.messaging.telegram.chat` MUST only depend on `StreamingChatModel` (not `ChatModel.call` non-streaming).

---

### `backend/worker/src/main/java/com/zeromail/worker/messaging/MessagingNotificationProcessor.java` (worker handler)

**Analog (worker scheduling/dispatch shell):** `D:\Semester-8\zero-mail\backend\worker\src\main\java\com\zeromail\worker\cleanup\ProcessingJobWorker.java`

**Excerpt (lines 51-100, worker shell):**
```java
@Component
@SuppressWarnings("SqlResolve")
public class ProcessingJobWorker {

    private static final String CLAIM_SELECT_SQL =
            """
            SELECT id, tenant_id, job_type, payload_json::text AS payload
              FROM processing_job
             WHERE status = 'PENDING'
               AND next_run_at <= NOW()
             ORDER BY created_at
             LIMIT 1
               FOR UPDATE SKIP LOCKED
            """;

    @PostConstruct
    void startPolling() {
        this.pollLoopThread =
                Thread.ofVirtual().name("processing-job-worker").start(this::pollLoop);
    }
```

**Excerpt (lines 205-214, dispatch switch):**
```java
private void invokeHandler(ClaimedJob claimedJob) {
    switch (claimedJob.jobType()) {
        case "UNSUBSCRIBE_CAMPAIGN" ->
                unsubscribeCampaignHandler.handle(
                        claimedJob.jobId(), claimedJob.tenantId(), claimedJob.payload());
        default ->
                throw new IllegalStateException(
                        "Unknown processing_job.job_type: " + claimedJob.jobType());
    }
}
```

**Divergence notes:**
- Phase 10 adds new switch case `case "MESSAGING_NOTIFICATION" -> messagingNotificationProcessor.handle(...)` in `ProcessingJobWorker.invokeHandler` (modify file).
- `MessagingNotificationProcessor.handle(jobId, tenantId, payloadJson)` lives in `backend/worker/src/main/java/com/zeromail/worker/messaging/` — analog of `UnsubscribeCampaignHandler`.
- Parses payload → resolves `TelegramAccount` by `telegramChatId` → builds button labels per `language_code` → calls `TelegramApiClient.sendMessage(...)`.
- On 429: catch `TelegramRateLimit429Exception`, re-enqueue with `available_at = NOW() + retry_after` (analog: `ThrottleDeferredException` catch in `ProcessingJobWorker.dispatchJob`).
- Worker logs Convention 5 format: `event=telegram_notification_sent tenantId={} chatId={}`.
- ArchUnit `TelegramOutboxDrainArchTest` enforces (D-04): no `@Scheduled` annotation in `backend/api` references `MESSAGING_NOTIFICATION`; drain code lives only in worker.

---

### `backend/api/src/main/java/com/zeromail/api/controllers/integrations/TelegramWebhookController.java` (webhook controller)

**Analog:** `D:\Semester-8\zero-mail\backend\api\src\main\java\com\zeromail\api\controllers\gmail\GmailPubSubController.java`

**Excerpt (full file — 60 lines):**
```java
@Hidden
@RestController
public class GmailPubSubController {

    private static final Logger log = LoggerFactory.getLogger(GmailPubSubController.class);

    private final PubSubIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    public GmailPubSubController(
            PubSubIngestionService ingestionService, ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/internal/pubsub/gmail")
    public void receivePush(@RequestBody PubSubPushEnvelope envelope) {
        if (envelope.message() == null || envelope.message().data() == null) {
            return;
        }
        // ... decode, validate, route
    }
}
```

**Divergence notes:**
- Phase 10 controller path: `POST /webhooks/telegram/{urlSecret}` (per TG-06). URL secret as `@PathVariable` validated by `TelegramWebhookSecretFilter` BEFORE controller dispatches.
- Body: `@RequestBody TelegramUpdate update`. Delegate to `TelegramUpdateRouter.route(update)` immediately and return 200 within 500ms (per TG-06).
- `@Hidden` (Springdoc) — webhook should not appear in public OpenAPI doc, matches Pub/Sub controller.
- Use enterprise naming: `telegramUpdateRouter`, `objectMapper`, `request`, `response` — no `req`/`res`/`router`/`svc`.
- Log on entry only (Convention 5): `event=telegram_webhook_received update_type={message|callback_query|my_chat_member}` — never payload contents.

---

### `backend/api/src/main/java/com/zeromail/api/security/TelegramWebhookSecurityConfig.java` (security filter chain)

**Analog:** `D:\Semester-8\zero-mail\backend\api\src\main\java\com\zeromail\api\security\PubSubSecurityConfig.java`

**Excerpt (lines 16-66, full config):**
```java
@Configuration
public class PubSubSecurityConfig {

    @Bean
    public TokenVerifier pubsubOidcTokenVerifier(ZeroMailApiProperties properties) { ... }

    @Bean
    PubSubOidcAuthFilter pubSubOidcAuthFilter(
            ZeroMailApiProperties properties, PubSubTokenVerifier tokenVerifier) {
        ZeroMailApiProperties.PubSubProperties pubsubProperties = properties.gmail().pubsub();
        return new PubSubOidcAuthFilter(pubsubProperties.saPrincipalEmail(), tokenVerifier);
    }

    @Bean
    FilterRegistrationBean<PubSubOidcAuthFilter> pubSubOidcAuthFilterRegistration(
            PubSubOidcAuthFilter filter) {
        FilterRegistrationBean<PubSubOidcAuthFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);  // CRITICAL: prevent Spring from auto-registering as servlet filter
        return registration;
    }

    @Bean
    @Order(1)
    SecurityFilterChain pubSubFilterChain(HttpSecurity http, PubSubOidcAuthFilter oidcFilter) {
        return http.securityMatcher("/internal/pubsub/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(oidcFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
```

**Divergence notes:**
- Phase 10 uses `@Order(2)` (per RESEARCH Q8 — `@Order(1)` already taken by PubSubSecurityConfig; collision risk if both use 1). `@Order(2)` still runs before default user-session chain (LOWEST_PRECEDENCE_HALF_BACK).
- `securityMatcher("/webhooks/telegram/**")` instead of `/internal/pubsub/**`.
- Two filters added via `.addFilterBefore`: (a) `TelegramWebhookIpAllowlistFilter` (149.154.160.0/20 + 91.108.4.0/22 via Spring `IpAddressMatcher`); (b) `TelegramWebhookSecretFilter` (constant-time compare URL `{urlSecret}` + header `X-Telegram-Bot-Api-Secret-Token`).
- Same `FilterRegistrationBean` pattern: `setEnabled(false)` to prevent double-registration as global servlet filter (critical — otherwise the filter runs on every request including session-protected endpoints).
- `csrf().disable()` + `STATELESS` session policy — identical to Pub/Sub config.
- Plan-phase action: verify `ForwardedHeaderFilter` enabled on VPS (RESEARCH Pitfall 5) — without it, `getRemoteAddr()` returns proxy IP, blocking all Telegram traffic.

---

### `backend/api/src/main/java/com/zeromail/api/security/TelegramWebhookSecretFilter.java` (filter)

**Analog:** `D:\Semester-8\zero-mail\backend\api\src\main\java\com\zeromail\api\security\PubSubOidcAuthFilter.java`

**Note:** Did not load full PubSubOidcAuthFilter source (the filter analog shape is conventional `OncePerRequestFilter` from Servlet API). Use the SecurityConfig wiring (above) + RESEARCH §10 IP-allowlist filter snippet:

**Excerpt (RESEARCH §10, lines 536-552):**
```java
public class TelegramWebhookIpAllowlistFilter extends OncePerRequestFilter {
    private static final List<IpAddressMatcher> ALLOWED = List.of(
        new IpAddressMatcher("149.154.160.0/20"),
        new IpAddressMatcher("91.108.4.0/22"));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        String remoteAddress = request.getRemoteAddr();
        boolean allowed = ALLOWED.stream().anyMatch(matcher -> matcher.matches(remoteAddress));
        if (!allowed) {
            response.setStatus(401);
            return;
        }
        chain.doFilter(request, response);
    }
}
```

**Divergence notes:**
- `TelegramWebhookSecretFilter` extracts `{urlSecret}` from URL path (use `org.springframework.util.AntPathMatcher` or regex against `request.getServletPath()`) AND validates `X-Telegram-Bot-Api-Secret-Token` header.
- Both compares MUST use `MessageDigest.isEqual(byte[], byte[])` (constant-time) — never `String.equals()`.
- On mismatch: 401 + structured log `event=telegram_webhook_unauthorized` + Bucket4j 10/min throttle per source IP (RESEARCH §10 + TG-06 acceptance "≥11 bad requests in 60s from one source IP triggers throttle").
- Enterprise naming: `request`, `response`, `chain`, `webhookAuthenticationException`. No `req`/`res`/`ex`.

---

### `apps/web/features/telegram-integration/api/telegram-api.ts` (feature API, typed OpenAPI)

**Analog:** `D:\Semester-8\zero-mail\apps\web\features\gmail\api\gmail-api.ts`

**Excerpt (full file — 32 lines):**
```typescript
import { adaptFetchForOpenApi, api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type TenantStatus = components['schemas']['GmailConnectionStatusResponse'];

export interface GetTenantStatusOptions {
  fetcher?: typeof fetch;
  signal?: AbortSignal;
  headers?: HeadersInit;
}

export async function getTenantStatus(opts: GetTenantStatusOptions = {}): Promise<TenantStatus> {
  const { fetcher, signal, headers } = opts;
  const { data, error, response } = await api.GET('/api/gmail/connection/status', {
    cache: fetcher || headers ? 'no-store' : undefined,
    fetch: adaptFetchForOpenApi(fetcher ?? (headers ? fetch : undefined)),
    headers,
    signal,
  });
  if (error || !response.ok || data === undefined) {
    throw error ?? new Error(`/api/gmail/connection/status failed: ${response.status}`);
  }
  return data;
}

export async function disconnectGmail(): Promise<void> {
  const { error, response } = await api.POST('/api/tenant/disconnect', {});
  if (error || !response.ok) {
    throw error ?? new Error(`/api/tenant/disconnect failed: ${response.status}`);
  }
}
```

**Divergence notes:**
- Phase 10 feature API exposes: `getTelegramStatus()`, `startPairing()`, `disconnectTelegram()`, `updateNotificationFilter(filter)`.
- Types derive from generated OpenAPI: `type TelegramStatus = components['schemas']['TelegramStatusResponse']`, `type PairingResponse = components['schemas']['PairingResponse']`. NEVER hand-roll mirror DTOs (apps/web/AGENTS.md `lib/api/schema.d.ts` rule).
- Uses typed `api.GET/POST/PATCH` from `@/lib/api/client` — NO raw `fetch`.
- After backend DTO mint, MUST run `pnpm --filter web run generate:api` and commit regenerated schema.d.ts (per Convention 10 MANDATORY).
- All errors propagate as `Error` (no toast at this layer — global QueryClient mutation cache handles toasts via `meta.successMessage/errorMessage` per Convention 11).

---

### `apps/web/features/telegram-integration/query-keys.ts` (TanStack key factory)

**Analog:** `D:\Semester-8\zero-mail\apps\web\features\gmail\query-keys.ts`

**Excerpt (full file — 4 lines):**
```typescript
export const gmailQueryKeys = {
  all: ['gmail'] as const,
  status: () => [...gmailQueryKeys.all, 'status'] as const,
} as const;
```

**Divergence notes:**
- Phase 10:
  ```typescript
  export const telegramQueryKeys = {
    all: ['telegram'] as const,
    status: () => [...telegramQueryKeys.all, 'status'] as const,
  } as const;
  ```
- Per Convention 8: do NOT create query keys for mutation-only operations (pairing mint, disconnect) — only `status()` owns cached data.

---

### `apps/web/features/telegram-integration/hooks/useTelegramStatus.ts` (useQuery + refetchInterval)

**Analog (base):** `D:\Semester-8\zero-mail\apps\web\features\gmail\hooks\useTenantStatus.ts`

**Excerpt (full file — 13 lines):**
```typescript
'use client';

import { useQuery } from '@tanstack/react-query';

import { getTenantStatus } from '@/features/gmail/api/gmail-api';
import { gmailQueryKeys } from '@/features/gmail/query-keys';

export function useTenantStatus() {
  return useQuery({
    queryKey: gmailQueryKeys.status(),
    queryFn: ({ signal }) => getTenantStatus({ signal }),
  });
}
```

**Divergence notes:**
- Phase 10 adds polling-while-pairing pattern (RESEARCH §11):
  ```typescript
  export function useTelegramStatus(options: { dialogOpen?: boolean } = {}) {
    return useQuery({
      queryKey: telegramQueryKeys.status(),
      queryFn: ({ signal }) => getTelegramStatus({ signal }),
      refetchInterval: options.dialogOpen ? 2000 : false,
      enabled: options.dialogOpen !== false,
    });
  }
  ```
- Set `meta: { silent: true }` per Convention 11 to opt out of global toast on poll errors (background polling owns its own UX).

---

### `apps/web/features/telegram-integration/hooks/useDisconnect.ts` (useMutation with meta)

**Analog (base):** `D:\Semester-8\zero-mail\apps\web\features\gmail\hooks\useDisconnectGmail.ts`

**Excerpt (full file — 21 lines):**
```typescript
'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { accountQueryKeys } from '@/features/account/query-keys';
import { disconnectGmail } from '@/features/gmail/api/gmail-api';
import { gmailQueryKeys } from '@/features/gmail/query-keys';

export function useDisconnectGmail() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: disconnectGmail,
    onSuccess: async () => {
      await Promise.all([
        qc.invalidateQueries({ queryKey: gmailQueryKeys.all }),
        qc.invalidateQueries({ queryKey: accountQueryKeys.me() }),
      ]);
    },
  });
}
```

**Divergence notes:**
- Phase 10 mutation MUST opt in to global toast via `meta` (per Convention 11 — NEW mutations don't call `toast.success/error` locally):
  ```typescript
  return useMutation({
    mutationFn: disconnectTelegram,
    meta: {
      successMessage: 'connectedApps.telegram.toasts.disconnected',
      errorMessage: 'connectedApps.telegram.errors.disconnectFailed',
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: telegramQueryKeys.all }),
  });
  ```
- All string keys are next-intl bundle keys under `connectedApps.telegram.*` per TG-18.

---

### `apps/web/app/(protected)/(app)/settings/connected-apps/page.tsx` (Next.js page)

**Analog:** `D:\Semester-8\zero-mail\apps\web\app\(protected)\(app)\settings\page.tsx`

**Note:** Did not load full file. Convention 9 + apps/web/AGENTS.md: server-component page wrapper that imports the client `SettingsClient.tsx` analog. Phase 10 page imports a new `ConnectedAppsClient.tsx` rendering `<TelegramCard />` from the feature module.

**Divergence notes:**
- Per TG-18: do NOT modify `SettingsClient.tsx` beyond adding ONE navigation link "Connected Apps" pointing to `/settings/connected-apps`. Main Settings card grid stays.
- Tokens only (no hardcoded hex) — `bg-card`, `border-border`, `text-foreground` per apps/web/AGENTS.md "No hardcoded color hex".
- shadcn primitives: `Card`, `Dialog`, `AlertDialog`, `Switch`, `Button` — install via `pnpm dlx shadcn@latest add <component>` if absent (apps/web/AGENTS.md "shadcn/ui Primitive Rule").

---

### `apps/web/e2e/telegram-happy-path.spec.ts` (Playwright)

**Analog:** `D:\Semester-8\zero-mail\apps\web\e2e\settings-notifications.spec.ts`

**Excerpt (lines 17-53, viewport + state seed + interaction):**
```typescript
for (const viewport of [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 320, height: 740 },
]) {
  test(`notification preferences persist at ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const state = createChromeMockState();

    await openAuthenticatedRoute(page, '/settings', state);

    await expect(page.getByTestId('notifications-section')).toBeVisible();
    await page.getByTestId('daily-digest-switch').click();
    await expect
      .poll(() => state.notificationPreferenceUpdates)
      .toContainEqual({ digestEnabled: false, digestSendHourLocal: 20 });
  });
}
```

**Divergence notes:**
- Phase 10 spec covers (per TG-18 acceptance): start `/settings/connected-apps` disconnected → click Connect → dialog opens with QR + countdown → simulate webhook `/start <code>` via test helper POSTing to `/webhooks/telegram/<urlSecret>` → dialog closes within 4s → card shows `@username` connected state → click Disconnect → confirm → returns to disconnected.
- Uses existing helpers `createChromeMockState` + `openAuthenticatedRoute` + `installChromeApiMock` from `apps/web/e2e/chrome-test-utils.ts` (per other specs).
- Test helper for webhook simulation: `request.post('/webhooks/telegram/<urlSecret>', { headers: { 'X-Telegram-Bot-Api-Secret-Token': <secret> }, data: { message: { chat: { id: 12345, type: 'private' }, from: { id: 67890 }, text: '/start <code>' } } })` (RESEARCH §13 e2e snippet).
- For full E2E with golden-path latency assertion `<30s` per acceptance criteria.

---

## Shared Patterns

### ArchUnit invariant test (4 new tests, same shape)

**Source pattern:** `D:\Semester-8\zero-mail\backend\core\src\test\java\com\zeromail\core\arch\OnlyOneGmailSendCallSiteTest.java`

**Apply to:** `TelegramPathBodyBanTest`, `MailActionServiceArchTest`, `OutboundActionAuditMandatoryArchTest`, `TelegramOutboxDrainArchTest`, `TelegramChatStreamingOnlyArchTest`

**Excerpt (lines 12-78, full test pattern):**
```java
class OnlyOneGmailSendCallSiteTest {

    @Test
    void exactly_one_gmail_send_call_site_exists() {
        JavaClasses importedClasses = importProductionClasses();
        long callSiteCount =
                importedClasses.stream()
                        .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                        .filter(OnlyOneGmailSendCallSiteTest::isGmailSendCall)
                        .count();
        assertThat(callSiteCount).isEqualTo(1L);
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }

    private static boolean isGmailSendCall(JavaMethodCall methodCall) {
        String targetOwnerName = methodCall.getTargetOwner().getName().replace('$', '.');
        return methodCall.getName().equals("send")
                && (targetOwnerName.endsWith(GMAIL_MESSAGES_OWNER)
                        || targetOwnerName.endsWith(GMAIL_DRAFTS_OWNER));
    }
}
```

**Body-ban pattern source:** `D:\Semester-8\zero-mail\backend\core\src\test\java\com\zeromail\core\arch\ChatPersistenceContentBanTest.java`

**Excerpt (lines 23-48, regex sweep over source files):**
```java
private static final Pattern BODY_FIELD_PATTERN =
        Pattern.compile(
                "(?i)\\b(emailBody|messageBody|bodyHtml|bodyText|htmlBody|textBody|body)\\b");

@Test
void chat_persistence_sources_do_not_declare_body_shaped_fields() throws IOException {
    if (!Files.exists(CHAT_PERSISTENCE_ROOT)) {
        return;
    }
    for (Path persistenceSource : javaSourcesUnder(CHAT_PERSISTENCE_ROOT)) {
        String sourceText = Files.readString(persistenceSource);
        assertThat(sourceText)
                .as("chat_message.parts must route through ToolOutputSanitizer ...")
                .doesNotContainPattern(BODY_FIELD_PATTERN);
    }
}
```

---

### Privacy logging format (Convention 5)

**Source pattern:** Convention 5 in `CLAUDE.md` + sample lines across triage/gmail packages.

**Apply to:** All `core.messaging.telegram.*` log statements, all `core.mailaction.usecases.*` log statements, all `core.outbound.persistence.OutboundActionAuditWriter` log statements.

**Excerpt (TriageAuditWriter.java line 179-184):**
```java
logger.info(
        "event=triage_audit_cleanup_archive_recorded tenantId={} attemptId={}"
                + " senderDomain={}",
        tenantId,
        campaignAttemptId,
        extractDomain(senderEmail));
```

**Forbidden:** `body`, `bodyHtml`, `snippet`, `messageHtml`, `content`, `prompt`, `completion`, `token`, full email regex `[a-z0-9._+-]+@[a-z0-9.-]+\.[a-z]{2,}`. Use `extractDomain(senderEmail)` helper or pre-hashed identifiers only.

---

### Liquibase YAML changeset shape

**Source patterns:**
- Header style: `backend/core/src/main/resources/db/changelog/changes/086-triage-audit-source.yaml`
- CHECK + table create: `backend/core/src/main/resources/db/changelog/changes/043-assistant-pending-action.yaml`
- ALTER + grant: `backend/core/src/main/resources/db/changelog/changes/078-processing-job-extend.yaml`

**Apply to:** All Phase 10 changesets (099-103 per RESEARCH §15).

**Excerpt (043-assistant-pending-action.yaml lines 11-35, table create style):**
```yaml
- sql:
    splitStatements: false
    sql: |
      CREATE TABLE assistant_pending_action (
        id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
        chat_id uuid NOT NULL REFERENCES chat(id) ON DELETE CASCADE,
        tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
        chat_message_id uuid NOT NULL REFERENCES chat_message(id) ON DELETE CASCADE,
        tool_call_id varchar(64) NOT NULL,
        state varchar(16) NOT NULL,
        CONSTRAINT ck_assistant_pending_action_state CHECK (state IN ('PENDING', 'PROCESSING', 'CONFIRMED', 'CANCELED', 'FAILED')),
        CONSTRAINT ux_assistant_pending_action_chat_tool UNIQUE (chat_id, tool_call_id)
      );
      CREATE INDEX idx_assistant_pending_action_state_expires ON assistant_pending_action (state, expires_at);
```

**Excerpt (086-triage-audit-source.yaml lines 1-42, addColumn + CHECK shape):**
```yaml
databaseChangeLog:
  - changeSet:
      id: 086-triage-audit-source
      author: zeromail
      comment: >
        H-3 Path A — distinguish triage-driven vs cleanup-driven audit rows. ...
      changes:
        - addColumn:
            tableName: triage_audit
            columns:
              - column:
                  name: source
                  type: text
                  defaultValue: TRIAGE
                  constraints:
                    nullable: false
        - sql:
            sql: ALTER TABLE triage_audit ADD CONSTRAINT ck_triage_audit_source CHECK (source IN ('TRIAGE', 'CLEANUP_CAMPAIGN'))
      rollback:
        - sql:
            sql: ALTER TABLE triage_audit DROP CONSTRAINT IF EXISTS ck_triage_audit_source
        - dropColumn:
            tableName: triage_audit
            columnName: source
```

**Divergence notes for Phase 10:**
- ID numbering continues from `098-chat-message-composite-fk.yaml`: `099-telegram-account`, `100-outbound-action-audit`, `101-telegram-notification-dedup`, `102-telegram-notification-log`, `103-app-db-grants-telegram`.
- `100-outbound-action-audit.yaml` uses RAW SQL `createTable + ALTER ... ADD CONSTRAINT CHECK + CREATE INDEX` per D-01 schema sketch (RESEARCH §9 verbatim YAML available).
- `101-telegram-notification-dedup.yaml` uses `createTable` + PRIMARY KEY `(tenant_id, gmail_message_id)` + `idx_telegram_notification_dedup_sent_at` per RESEARCH §9 Alt A. Plus a ShedLock-protected vacuum `@Scheduled` in worker (NEW pattern; analog cron pattern in `worker/triage/TriageAuditPurgeJob.java`).
- Rollback section MANDATORY (every analog has it).

---

### TanStack Query meta-based toast (Convention 11)

**Source pattern:** apps/web/AGENTS.md "Error handling, toasts, retries"

**Apply to:** All `useMutation` in `apps/web/features/telegram-integration/hooks/*`

**Pattern (verbatim from AGENTS.md):**
```typescript
return useMutation({
  mutationFn: ...,
  meta: {
    successMessage: 'connectedApps.telegram.toasts.<key>',
    errorMessage: 'connectedApps.telegram.errors.<key>',
  },
  onSuccess: () => qc.invalidateQueries({ queryKey: telegramQueryKeys.all }),
});
```

For background polling, use `meta: { silent: true }`.

---

## No Analog Found (NEW patterns or research-derived)

| File | Role | Data Flow | Reason / Source |
|------|------|-----------|------------------|
| `TelegramSendRateLimiter.java` | rate limiter | flow control | First Bucket4j adoption in codebase; RESEARCH §19 provides verified Java 8.19 template; plan must add `com.bucket4j:bucket4j_jdk17-core:8.19.0` to libs.versions.toml |
| `PairingCodeService.java` | compact signed code | crypto request-response | RESEARCH §3 + §19 found JWT >64 chars > Telegram deep-link limit; CONTEXT D-08 must switch to manual HMAC-SHA256 truncated. NO codebase analog — use RESEARCH §19 snippet verbatim |
| `TelegramButtonLabels.java` | i18n Vietnamese/English static map | constants | No existing i18n-in-Java-Map analog; CONTEXT specifics list labels verbatim; plan creates a `Map<String, String> VI = Map.of(...)` and `Map<String, String> EN = Map.of(...)` keyed by `language_code` |
| `telegram_notification_dedup` Liquibase changeset | dedup table | INSERT ON CONFLICT | RESEARCH §9 verified `NOW()` in partial UNIQUE index PREDICATE fails. Plan-phase decision: separate dedup table + ShedLock vacuum cron — no existing equivalent shape |
| `TelegramOutboxDrainArchTest` | ArchUnit | invariant gate | Uses same OnlyOneGmailSendCallSiteTest pattern but for `@Scheduled` annotation introspection over `backend/api` packages — slightly different from existing tests, but the ArchUnit shell is identical |
| Bucket4j-aware 429 retry-after parsing in `TelegramApiClient` | HTTP error handling | 429 fallback | Telegram returns `retry_after` in body `parameters.retry_after` NOT header `Retry-After`. `ModelsProbeClient.mapStatus()` only checks status code int. Plan must parse body JSON via `objectMapper.readTree(...).path("parameters").path("retry_after").asInt()` |

---

## Metadata

**Analog search scope:**
- `backend/core/src/main/java/com/zeromail/core/{outbound,chat,triage,gmail,messaging,mailaction,queue}/**`
- `backend/core/src/main/resources/db/changelog/changes/`
- `backend/core/src/test/java/com/zeromail/core/arch/`
- `backend/api/src/main/java/com/zeromail/api/{controllers/gmail,security,config}/`
- `backend/worker/src/main/java/com/zeromail/worker/cleanup/`
- `apps/web/features/{gmail,account,notifications}/`
- `apps/web/e2e/`

**Files scanned (representative):** ~50 Java source files, 5 Liquibase changesets, 8 frontend TS files, 1 e2e spec.

**Pattern extraction date:** 2026-05-28

**Key invariants for planner to encode:**

1. **Single Gmail send call site** — `OnlyOneGmailSendCallSiteTest` MUST stay GREEN. `GmailOutboundSendGateway` is the only `@AllowedSendCallSite`.
2. **Single non-send Gmail mutation surface** — `MailActionServiceArchTest` (NEW) — only `core.mailaction.usecases` calls `gmail.users().messages().modify(...)` or `.trash(...)`.
3. **Mandatory audit row per Gmail mutation** — `OutboundActionAuditMandatoryArchTest` (NEW) + integration test row-count via Mockito spy.
4. **Worker-only outbox drain for messaging** — `TelegramOutboxDrainArchTest` (NEW) — no `@Scheduled` in `backend/api` references `MESSAGING_NOTIFICATION`.
5. **Streaming-only chat** — `TelegramChatStreamingOnlyArchTest` (NEW) — `core.messaging.telegram.chat` only imports `StreamingChatModel`.
6. **Body-ban extended to Telegram** — `TelegramPathBodyBanTest` (NEW) mirrors `ChatPersistenceContentBanTest` regex sweep but scopes to `core.messaging.telegram.*` package.
7. **Backend enterprise naming** — `request`, `response`, `telegramAccountRepository`, `mailActionService`, `telegramProperties`, `tenantContext`, `telegramUpdate`. No abbreviations.
8. **No Lombok** — DTOs are records; entities are classes with explicit getters/setters.

---

## PATTERN MAPPING COMPLETE

**Phase:** 10 — Telegram Messaging Assistant
**Files classified:** 47
**Analogs found:** 41 / 47 (87% coverage)

### Coverage
- Files with exact analog: 24
- Files with role-match analog: 17
- Files with no analog (NEW patterns): 6 — all derived from RESEARCH.md (Bucket4j first adoption, compact signed pairing code, dedup table, i18n button labels, Telegram 429 body parsing)

### Key Patterns Identified
- All Modulith modules use `@ApplicationModule(displayName, allowedDependencies)` in `package-info.java`; new `core.mailaction` + `core.messaging.telegram` follow the same shape as `core.outbound` and `core.chat`.
- Domain events are plain records in `<module>/domain/` (privacy-bounded, no body fields), published via `ApplicationEventPublisher`, consumed via `@TransactionalEventListener(AFTER_COMMIT)` (sync) or `@ApplicationModuleListener` (async + REQUIRES_NEW). Phase 10 uses the async variant per RESEARCH Q4.
- JPA entities extend `AbstractTenantOwnedEntity` (multi-tenant base), use `@Enumerated(EnumType.STRING)` for status, `@JdbcTypeCode(SqlTypes.JSON)` for JSONB columns. NO Lombok — explicit getters/setters.
- Enums implement `IdentifiedEnum` with `id() == name()`, static `fromId()` throwing `NoSuchElementException`. CHECK constraints in Liquibase mirror the enum's `id()` values exactly.
- Worker uses `Thread.ofVirtual()` + `SELECT ... FOR UPDATE SKIP LOCKED` poll loop over `processing_job`. Phase 10 extends the dispatch switch in `ProcessingJobWorker.invokeHandler` with new case `MESSAGING_NOTIFICATION`.
- REST controllers in `backend/api/controllers/<domain>/` are thin — they delegate to `core.<domain>.usecases.*` services. Webhook controllers also carry `@Hidden` (Springdoc).
- `@Order(1)` SecurityFilterChain pattern (PubSubSecurityConfig) — Phase 10 uses `@Order(2)` to avoid collision. Both filters MUST register with `FilterRegistrationBean.setEnabled(false)` to prevent global servlet registration.
- Frontend feature modules follow `apps/web/features/<feature>/{api,query-keys.ts,hooks,components}` per Convention 8. All HTTP via typed `api.GET/POST` from generated OpenAPI schema — NEVER hand-roll DTOs.
- Toast UX wires through `MutationCache.onSuccess/onError` via `meta.successMessage / meta.errorMessage` (Convention 11). New mutations MUST use meta, not local `toast.success/error`.

### File Created
`D:\Semester-8\zero-mail\.planning\phases\10-telegram-messaging-assistant\10-PATTERNS.md`

### Ready for Planning
Pattern mapping hoàn tất. Planner có thể tham chiếu trực tiếp các excerpt analog (kèm số dòng + đường dẫn tuyệt đối) khi viết PLAN.md cho từng wave. Chú ý 6 NEW patterns (Bucket4j, compact pairing code, dedup table, button labels, 429 body parsing, drain arch test) — planner cần dùng RESEARCH.md snippets thay vì codebase analog cho những file đó.
