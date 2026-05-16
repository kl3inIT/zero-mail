# Phase 4: Triage Convergence (Hero) - Pattern Map

**Mapped:** 2026-05-11
**Files analyzed:** ~40 new/modified files (1 new `core.triage` Modulith package, 1 new `worker.triage` package, 3 new API controllers, 4 Liquibase changelogs, 2 modified existing files, ~6 new ArchUnit/integration tests, 1 LlmGateway extension)
**Analogs found:** ~38 / ~40 — Phase 4 is overwhelmingly an *assembly* phase; almost every new file has a strong in-repo template named by CONTEXT.md/RESEARCH.md.

> **Read CLAUDE.md and CONVENTIONS.md before using this map.** Naming (no `req`/`svc`/`ctx`/`e`), package layout (`domain/` / `application/` / `persistence/` / `service/` / `exception/`), records-for-DTOs / classes-for-entities, `IdentifiedEnum` + `fromId` fail-loud, privacy logging format (`event=triage_* tenantId={} gmailMessageId={} ruleId={} actionType={}`, no body/subject/snippet/sender-name/draft text), Jackson 3 split (`tools.jackson.databind.*` core, `com.fasterxml.jackson.annotation.*` annotations), no Lombok, Spring Modulith events not direct calls for `message-observed → triage` (Convention #6).

---

## File Classification

### `core.triage` — new Modulith package (`backend/core`, consumed in `backend/worker`)

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `core/triage/package-info.java` | config (Modulith module decl) | — | `core/rules/package-info.java` | exact |
| `core/triage/application/TriageOrchestratorService.java` | service (worker-side after-commit consumer) | event-driven → request-response | `worker/GmailHistoryProcessor.java` (ScopedValue rebind loop) + `rules/service/ActionProposalMerger.java` (evaluate+merge) + `gmail/service/GmailDeliveryProcessingService.java` (`@Transactional` orchestration) | role-match (no existing `@ApplicationModuleListener` yet) |
| `core/triage/application/TriageUndoService.java` | service (use-case, user-initiated) | request-response / transform (compute inverse) | `rules/service/RuleManagementService.java` (tenant-scoped `@Transactional` mutation service) + `tenant/service/TenantService.java` | role-match |
| `core/triage/application/UndoAuditCommand.java` (+ result records) | DTO (command record) | — | `rules/application/RuleUpdateCommand.java`, `rules/application/RuleCompileCommand.java` | exact |
| `core/triage/domain/TriageActionResult.java` (sealed: `Label` / `Archive` / `SaveDraft`) | model (sealed value type) | — | `rules/domain/ActionIntent.java` (sealed `Label`/`Archive`/`SaveDraft`) | exact |
| `core/triage/domain/TriageActionResultJsonValidator.java` | utility (manual JSON discriminator validator) | transform | `rules/domain/ActionIntentJsonValidator.java` | exact |
| `core/triage/domain/TriageActionArgsCanonicalizer.java` | utility (canonical-JSON + SHA-256) | transform | *(no direct analog — new; mirror Jackson 3 `JsonMapper` usage from `ActionIntentJsonValidator`)* | partial |
| `core/triage/domain/TriageDecision.java` (enum: `PENDING/APPLIED/SHADOW_LOGGED/REJECTED_BY_SAFETY_NET/REJECTED_BY_SAFETY_POLICY/FAILED/REVERTED`) | model (enum state) | — | `billing/domain/CreditReservationStatus.java`, `billing/domain/CallSite.java` (IdentifiedEnum + `fromId`) | exact |
| `core/triage/persistence/TriageAuditEntity.java` | model (JPA entity, tenant-owned) | CRUD (insert-only + 2 narrow state transitions) | `rules/persistence/RuleEntity.java` (`extends AbstractTenantOwnedEntity`, `@JdbcTypeCode(SqlTypes.JSON)` jsonb + `@PrePersist`-style validation via getter) | exact |
| `core/triage/persistence/TriageAuditRepository.java` | model (Spring Data repo + native upsert) | CRUD / idempotent upsert | `gmail/persistence/MailMessageObservedRepository.java` (`insertObservedIfAbsent` native `ON CONFLICT DO NOTHING`) + `gmail/persistence/PubSubDeliveryRepository.java` (`@Modifying @Query` `updateStatus` / `releaseForRetry`) | exact |
| `core/triage/persistence/TenantSenderOptInEntity.java` | model (JPA entity, tenant-owned) | CRUD | `rules/persistence/RuleEntity.java` (minimal `extends AbstractTenantOwnedEntity` shape) | exact |
| `core/triage/persistence/TenantSenderOptInRepository.java` | model (Spring Data repo) | CRUD | `rules/persistence/RuleRepository.java` (`findByIdAndTenantId`, derived queries) | exact |
| `core/triage/service/TriageGmailWriter.java` | service (Gmail write adapter — ONLY class allowed to call Gmail write APIs from triage) | request-response (external API) | `gmail/service/GmailDeliveryProcessingService.java` (build Gmail client via `GmailApiClientFactory.refreshAccessToken` + `buildGmailClient` + `RefreshTokenCipher.decrypt`) + `gmail/service/GmailPreviewReadService.java` (Gmail API request shape, `GoogleJsonResponseException` handling) | role-match (no existing Gmail *write* call site) |
| `core/triage/service/TriageSafetyPolicy.java` | service (allow-list gate) | transform / validation | `llm/service/ActionValidator.java` (EnumSet allow-list + fail-closed exception) | exact |
| `core/triage/service/SenderSafetyNetService.java` | service (Gmail SENT lookup + Redis cache) | request-response + caching | `gmail/service/GmailPreviewReadService.java` (Gmail `users().messages()` requests, metadata fetch, `Clock` injection) + Redis: see Notes (no existing `RedisTemplate` bean — verify/add) | role-match |
| `core/triage/exception/TriageSafetyViolationException.java` | model (business exception) | — | `llm/exception/SafetyViolationException.java` (no-arg, privacy-safe) | exact |
| `core/triage/exception/TriageUndoExpiredException.java` / `TriageUndoAlreadyDoneException.java` / `TriageUndoUnsupportedActionException.java` (+ `TriageAuditException`) | model (business exceptions, reason-coded) | — | `rules/exception/RuleValidationException.java` (private ctor + `Reason` enum + static factories) | exact |
| `core/triage/exception/package-info.java`, `core/triage/application/package-info.java`, `core/triage/domain/package-info.java`, `core/triage/persistence/package-info.java`, `core/triage/service/package-info.java` | config | — | `rules/exception/package-info.java`, `rules/persistence/package-info.java`, etc. | exact |

### `core.gmail` — modified + new event package

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|----------------|---------------|
| `core/gmail/event/MailMessageObserved.java` (NEW: `record(UUID tenantId, String gmailMessageId, String gmailThreadId, Instant observedAt)`) | model (integration event record) | event payload | `api/security/events/GmailConnectionRevokedEvent.java`, `api/security/events/OAuth2TokenRefreshFailed.java` (existing Spring event records — but place under `core.gmail.event`, not `domain/`, per D-A2) | role-match |
| `core/gmail/event/package-info.java` (NEW) | config | — | `rules/projection/package-info.java` (one-liner JavaDoc) | exact |
| `core/gmail/service/GmailDeliveryProcessingService.java` (MODIFIED — inject `ApplicationEventPublisher`, `publishEvent(new MailMessageObserved(...))` after each NEW observed row where `insertedCount == 1`) | service | + event publication | itself (lines 117-159 `observeInboxMessages`, the `if (insertedCount == 1)` block at line 153-155) | exact (edit in place) |

### `core.llm` — extension

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|----------------|---------------|
| `core/llm/service/LlmGateway.java` (MODIFIED — add `Map<String,Boolean> evaluateSemanticIntents(CallSite, String sanitizedMessageContent, List<SemanticIntentRequest>)`) | service interface | request-response (structured LLM output) | itself (existing `chat(...)`, `compileRule(...)`, `driftCheck(...)` method declarations + JavaDoc) | exact (add method) |
| `core/llm/application/SemanticIntentRequest.java` (NEW: `record(String nodeId, String intent)`) | DTO record | — | `llm/application/RawToolCall.java`, `llm/application/LlmTool.java` (existing application records) | exact |
| `core/llm/gateway/springai/SemanticIntentEvaluator.java` + `SemanticIntentResponse.java` (NEW — impl behind ArchUnit boundary) | service + DTO | request-response (Spring AI structured output) | `llm/gateway/springai/SpringAiLlmModelClient.java`, `llm/service/LlmGatewayImpl.java` (Spring AI `ChatClient` usage + sanitization + credit-reserve plumbing) — see 04-AI-SPEC §3/§4 for the locked structured-output shape | role-match |

### `core.billing` — additive enum extension

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|----------------|---------------|
| `core/billing/domain/CallSite.java` (MODIFIED — add `TRIAGE_PLATFORM_LLM`, `TRIAGE_DETERMINISTIC`) | model (enum) | — | itself (existing `TRIAGE(1)`, `DRAFT(2)`, `PREVIEW(1)` members + `cost()` + `fromId`) | exact (add members) |
| `core/config/ZeroMailCoreProperties.java` or a `BillingProperties` extension (MODIFIED — add `zero-mail.billing.cost.triage-deterministic` default ~0) | config | — | existing `ZeroMailCoreProperties` nested record properties | role-match |

### `core.tenant` — modified

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|----------------|---------------|
| `core/tenant/persistence/TenantEntity.java` (MODIFIED — add `triageShadowMode` boolean field + getter/setter, mirroring `triagePaused`) | model (entity) | — | itself (the `triagePaused` field at line 18-19 + `isTriagePaused()` / `setTriagePaused(...)` at lines 32-38) | exact |
| `core/tenant/service/TenantService.java` (MODIFIED — add `setTriageShadowMode(UUID, boolean)` + `isTriageShadowMode(UUID)`, mirroring `setTriagePaused` / `isTriagePaused`) | service | CRUD | itself (lines 49-62 `setTriagePaused` / `isTriagePaused`) | exact |

### `backend/worker` — new `worker.triage` package + app scan

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `worker/triage/TriageEventRetryJob.java` (`@Scheduled(fixedDelay="PT2M")` + `@SchedulerLock("triageEventRetry")` → `IncompleteEventPublications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5))`) | service (scheduled worker) | event-driven / batch | `worker/billing/CreditReserveWatchdog.java` (scheduler + ShedLock + Micrometer counter) | exact |
| `worker/triage/TriageEventCleanupJob.java` (`@Scheduled(cron="0 0 3 * * *")` + `@SchedulerLock("triageEventCleanup")` → `CompletedEventPublications.deletePublicationsOlderThan(Duration.ofDays(7))`) | service (scheduled worker) | batch | `worker/billing/CreditReserveWatchdog.java` + `worker/billing/BillingIntentExpirySweeper.java` | exact |
| `worker/triage/TriageAuditPurgeJob.java` (`@Scheduled(cron="0 0 4 * * *")` + `@SchedulerLock("triageAuditPurge")`, bounded `LIMIT 1000` repeat-until-zero delete) + `TriageAuditPurgeBatch.java` (the `@Transactional` collaborator) | service (scheduled worker) | batch | `worker/billing/CreditReserveWatchdog.java` + `worker/billing/CreditReserveWatchdogBatch.java` (the scheduler/transactional-batch SPLIT — copy verbatim, see Pitfall 6 in RESEARCH.md) | exact |
| `worker/triage/TriagePendingReaperJob.java` (`@Scheduled(fixedDelay="PT5M")` + `@SchedulerLock("triagePendingReaper")`; PENDING > 2 min → metadata-verify → APPLIED/FAILED) — *may be a follow-up plan* | service (scheduled worker) | batch | `worker/billing/CreditReserveWatchdog.java` + `CreditReserveWatchdogBatch.java` | exact |
| `worker/ZeroMailWorkerApplication.java` (VERIFY — `scanBasePackages = {"com.zeromail.worker", "com.zeromail.core"}` already covers `com.zeromail.worker.triage`; no edit expected, just confirm) | config | — | itself | exact (verify only) |

### `backend/api` — new triage controllers + DTOs + error mappings

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `api/controllers/triage/TriageAuditController.java` (`POST /api/triage/audit/{auditId}/undo`) | controller (thin) | request-response | `api/controllers/rules/RulesController.java` (PathVariable, `@RequestBody @Valid`, `TenantContext.currentOrThrow()`, delegates to a core service, DTO `from(...)`) + `api/controllers/tenant/TriagePauseController.java` (one-action toggle controller) | exact |
| `api/controllers/triage/TriageTenantController.java` (`PATCH /api/tenant/triage/shadow-mode {"enabled": bool}`) | controller (thin) | request-response | `api/controllers/tenant/TriagePauseController.java` (verbatim shape: inject `TenantService`, resolve tenantId, call setter, log `event=*`, return DTO `from(...)`) | exact |
| `api/controllers/triage/SenderSafetyNetController.java` (`GET /api/triage/sender-safety-net`, `POST /api/triage/sender-safety-net/{senderEmail}/opt-in`) | controller (thin) | request-response | `api/controllers/rules/RulesController.java` (GET list + POST mutation) | exact |
| `api/dto/triage/*.java` (request/response records, e.g. `UndoAuditResponse`, `TriageShadowModeRequest`/`Response`, `ProtectedSendersResponse`, `SenderOptInResponse`) | DTO records | — | `api/dto/tenant/TriagePauseRequest.java` / `TriagePauseResponse.java` (record + `static from(...)`), `api/dto/rules/RuleResponse.java` | exact |
| `api/dto/triage/package-info.java` | config | — | `api/dto/tenant/package-info.java` | exact |
| `api/error/ErrorCodes.java` (MODIFIED — add `TRIAGE_UNDO_EXPIRED`, `TRIAGE_UNDO_ALREADY_DONE`, `TRIAGE_UNDO_UNSUPPORTED_ACTION`, `TRIAGE_SAFETY_VIOLATION` as dotted keys) | config (constants) | — | itself (existing `RULES_*`, `LLM_*` constants, dotted-key style) | exact |
| `api/config/GlobalExceptionHandler.java` (MODIFIED — add `@ExceptionHandler` for the 3 undo exceptions → 409, `TriageSafetyViolationException` → 500) | service (advice) | request-response | itself (existing `onRuleApiException` reason-switch → ProblemDetail, `onSafetyViolation` → 500, the `problem(...)` helper) | exact |

### Liquibase changelogs (`backend/core/src/main/resources/db/changelog/changes/` — floor `024`)

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `024-modulith-event-publication.yaml` (`event_publication` table — mirror Spring Modulith canonical DDL byte-for-byte; see RESEARCH.md Pitfall 2 — dump from a throwaway dev DB first) | migration | — | `017-shedlock-table.yaml` (an infra table created for a library) — but the *columns* must come from Spring Modulith, not invented | partial (structure analog; content from Spring Modulith) |
| `025-triage-audit.yaml` (`triage_audit` table: `audit_id uuid pk`, `tenant_id`, `gmail_message_id`, `gmail_thread_id`, `rule_id`, `rule_name_snapshot`, `action_type`, `args_hash BYTEA NOT NULL`, `action_args_json jsonb NOT NULL`, `gmail_change_token jsonb` nullable, `reason`, `decision`, `external_ref`, `failure_reason`, `applied_at`, `reverted_at`, `created_at`/`updated_at`/`version`; unique index `(tenant_id, gmail_message_id, rule_id, action_type, args_hash)`; CHECK on `decision` values; FK to `tenants(id)` deleteCascade) | migration | — | `021-rules-engine-schema.yaml` (createTable + jsonb columns + FK deleteCascade + CHECK constraints + indexes), `015-credit-reservation.yaml` (status CHECK + partial index) | exact |
| `026-tenants-triage-shadow-mode.yaml` (`addColumn tenants.triage_shadow_mode boolean defaultValueBoolean=false nullable=false` + rollback dropColumn) | migration | — | `013-tenants-triage-paused.yaml` (verbatim shape for `triage_paused`) | exact |
| `027-tenant-sender-opt-in.yaml` (`tenant_sender_opt_in` table: `id uuid pk`, `tenant_id`, `sender_email`, `created_at`/`updated_at`/`version`; unique `(tenant_id, sender_email)`; FK to `tenants(id)` deleteCascade) | migration | — | `015-credit-reservation.yaml` / `018-tenant-byok-credentials.yaml` (small tenant-owned table with FK + unique constraint) | exact |
| `db.changelog-master.yaml` (MODIFIED — append the 4 new includes in numbered order) | config | — | itself (existing numbered include list) | exact |

### Tests (`backend/core/src/test/java/com/zeromail/core/arch/` + integration)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `arch/NoGmailSendAllowedTest.java` (no class anywhere in `com.zeromail` calls `Gmail.Users.Messages.send` / `Gmail.Users.Drafts.send`) | test (ArchUnit) | — | `arch/RulesBoundaryArchTest.java` (`noClasses().that()...should().dependOnClassesThat()...` + `allowEmptyShould(true)`), `arch/SafetyContractArchTests.java` (custom `ArchCondition` over `getMethodCallsFromSelf()`) | exact |
| `arch/TriageGmailWriteBoundaryTest.java` (only `TriageGmailWriter` calls Gmail write APIs from triage code) | test (ArchUnit) | — | `arch/RulesBoundaryArchTest.java`, `arch/LlmGatewayBoundaryTest.java` | exact |
| `arch/CallSiteEnumMembershipArchTest.java` (MODIFIED — now expects 5 members incl. `TRIAGE_PLATFORM_LLM`, `TRIAGE_DETERMINISTIC`) | test (ArchUnit) | — | existing Phase 2B CallSite-membership ArchUnit rule (look under `core/arch/` or `billing` test packages) + `arch/SafetyContractArchTests.java` pattern | role-match |
| `arch/`-style "triage_audit repo-method ban" (no `delete*`/`update*` repo methods except `markApplied`/`markFailed`/`markReverted`) | test (ArchUnit) | — | `arch/LlmRepositoryContentBanTest.java` (repo-method introspection) + `arch/RulesBoundaryArchTest.java` | role-match |
| `TriageOrchestratorIntegrationTest.java` (event wiring, 2-rule deterministic proposal control run, one-audit-row-per-action) | test (integration, Testcontainers) | — | `api/controllers/gmail/GmailPubSubControllerIntegrationTest.java`, `api/controllers/rules/RulesControllerIntegrationTest.java`, `api/security/MultiTenantLeakIntegrationTest.java` (reuse the Postgres-container base + `RestClient + @LocalServerPort` decision) | role-match |
| `TriageUndoMockedClockTest.java` (undo at 30d+1s → 409) | test (integration) | — | `gmail/service/GmailPreviewReadService.java` package-private `Clock` ctor pattern + `api/controllers/account/MeLanguageIntegrationTest.java` | role-match |
| `TriageSafetyPolicyTest.java` (`RuleActionType.SEND`-ish proposal → exception, zero Gmail calls) | test (unit) | — | existing `ActionValidator` unit tests (look under `core/llm/service/` test packages) | role-match |
| i18n message keys (vi + en) for new error codes | config (resource) | — | `apps/web` `messages/{vi,en}.json` `errors.*` namespace — must pass `pnpm i18n:check` / `I18nArchUnitTest.java` | exact |

---

## Pattern Assignments

### `core/triage/application/TriageOrchestratorService.java` (service, event-driven → request-response)

**Primary analogs:** `worker/GmailHistoryProcessor.java` (ScopedValue rebind), `core/gmail/service/GmailDeliveryProcessingService.java` (`@Transactional` orchestration + Gmail-client build), `core/rules/service/ActionProposalMerger.java` (`evaluateAndMerge`).

**Listener + tenant rebind pattern** (mirror `GmailHistoryProcessor.tick()` lines 28-35):
```java
// GmailHistoryProcessor.java:28-35 — the ScopedValue rebind across the @Async boundary
@Scheduled(fixedDelay = 1_000L)
public void tick() {
  List<PubSubDeliveryEntity> batch = deliveryRepository.claimPendingBatch(BATCH_SIZE, LOCK_SECONDS);
  for (PubSubDeliveryEntity delivery : batch) {
    ScopedValue.where(TenantContext.TENANT, delivery.getTenantId().toString())
        .run(() -> deliveryProcessingService.processDelivery(delivery));
  }
}
```
→ For the triage consumer (D-A4): `@ApplicationModuleListener void on(MailMessageObserved event) { ScopedValue.where(TenantContext.TENANT, event.tenantId().toString()).run(() -> orchestrate(event)); }`. Note: CONTEXT.md mentions `TenantContext.runWith(...)` — that helper does **not** exist today; either add it to `TenantContext` first or use `ScopedValue.where(...).run(...)` directly. (RESEARCH.md Pitfall 4.)

**Rules load + tri-state evaluate + merge** (mirror `ActionProposalMerger.evaluateAndMerge` lines 40-69 + `RuleEvaluator.evaluate` lines 18-101):
```java
// RuleRepository.java:13-21 — load enabled rules in display order
List<RuleEntity> findOrderedByTenantId(@Param("tenantId") UUID tenantId);
// ActionProposalMerger.java:40-69 — feed RuleActionCandidate list (matcherNode parsed from RuleEntity.getMatcherAst(),
// actionIntents from RuleEntity.getActionIntents()) → orchestrator resolves DEFERRED semantic nodes via LlmGateway
// BEFORE this call, then merge(...) returns the ordered List<ActionProposal>.
```
`RuleEvaluator` returns `RuleEvaluationResult.deferred(nodeId, "semantic_intent_deferred")` for `SemanticIntentMatcher` (line 97-99) — the orchestrator collects those, resolves them, and treats `DEFERRED-(error)` as `NOT_MATCHED` on LLM failure (D-D5, RESEARCH.md Pitfall 8).

**Gmail metadata fetch → `RuleEvaluationInput`:** reuse `GmailPreviewReadService` (`format=metadata`, header parsing → sanitized sender/domain/recipients/subject-excerpt/labels/categories/`hasAttachment`/`listUnsubscribePresent`/`newsletterIndicatorPresent`). Lift the helper or expose `fetchTriageInput(tenantId, gmailMessageId)`. See `GmailPreviewReadService.toPreviewMessage` lines 278-328 and `messageGetRequest` lines 244-257.

**Credit accounting** (D-D3): `creditLedger.reserve(tenantId, CallSite.TRIAGE_PLATFORM_LLM)` per LLM call; `creditLedger.reserve(tenantId, CallSite.TRIAGE_DETERMINISTIC)` for pure-deterministic messages; `settle`/`release` per `CreditLedger` JavaDoc lifecycle. BYOK bypasses (Phase 2C convention).

**Shadow vs paused branch** (RESEARCH.md Pitfall 7): `if (tenant.isTriagePaused()) return;` (early, no audit) — then per proposal `if (tenant.isTriageShadowMode()) writeShadowAudit(...); else { TriageGmailWriter.apply(...); ... }`.

**Logging:** `log.info("event=triage_audit_reserved tenantId={} gmailMessageId={} ruleId={} actionType={}", ...)` — IDs only, no content (CLAUDE.md Convention #5, mirror `GmailDeliveryProcessingService` log lines 73/97/103/111).

---

### `core/triage/persistence/TriageAuditRepository.java` (model, idempotent upsert + narrow state transitions)

**Primary analog:** `core/gmail/persistence/MailMessageObservedRepository.java` (lines 14-32).

**Idempotent PENDING-row upsert** (D-C3 — mirror verbatim, change table + unique key + add `RETURNING`):
```java
// MailMessageObservedRepository.java:14-32 — the native ON CONFLICT DO NOTHING template
@Modifying
@Query(value = """
      INSERT INTO mail_message_observed
        (tenant_id, gmail_message_id, gmail_thread_id, history_id, label_ids, internal_date, observed_at)
      VALUES
        (:tenantId, :gmailMessageId, :gmailThreadId, :historyId, :labelIds, :internalDate, NOW())
      ON CONFLICT (tenant_id, gmail_message_id) DO NOTHING
      """, nativeQuery = true)
@Transactional
int insertObservedIfAbsent(@Param("tenantId") UUID tenantId, /* ... */);
```
→ `insertAuditPendingIfAbsent(...)`: same shape, `INSERT INTO triage_audit (..., decision, created_at) VALUES (gen_random_uuid(), :tenantId, ..., 'PENDING', NOW()) ON CONFLICT (tenant_id, gmail_message_id, rule_id, action_type, args_hash) DO NOTHING RETURNING audit_id` returning `Optional<UUID>` — empty result = already applied → skip the Gmail call. (`RETURNING` on `ON CONFLICT DO NOTHING` returns an empty result set on conflict.)

**Narrow state-transition updates** (D-C4 — mirror `PubSubDeliveryRepository.updateStatus` / `releaseForRetry` lines 99-109, `@Modifying @Query` with an explicit WHERE clause so the ArchUnit "no UPDATE/DELETE outside revert" rule can whitelist exactly these):
```java
// PubSubDeliveryRepository.java pattern — @Modifying @Query with explicit-status WHERE
@Modifying @Query(value = "UPDATE triage_audit SET applied_at = NOW(), decision = 'APPLIED', external_ref = :externalRef WHERE audit_id = :auditId AND tenant_id = :tenantId AND decision = 'PENDING'", nativeQuery = true)
void markApplied(@Param("auditId") UUID auditId, @Param("tenantId") UUID tenantId, @Param("externalRef") String externalRef);
// markFailed(...) WHERE decision = 'PENDING' SET decision = 'FAILED', failure_reason = :opaqueClass
// markReverted(...) WHERE decision = 'APPLIED' SET decision = 'REVERTED', reverted_at = :revertedAt
```
**Critical (RESEARCH.md Pitfall 3):** every native query touching `triage_audit` / `tenant_sender_opt_in` MUST include `tenant_id = :tenantId` in WHERE/VALUES — `@TenantId` is a Hibernate HQL/Criteria filter, NOT applied to `nativeQuery = true`. Keep `MultiTenantLeakIntegrationTest` (FND-05) green.

---

### `core/triage/persistence/TriageAuditEntity.java` (model, JPA entity, tenant-owned)

**Primary analog:** `core/rules/persistence/RuleEntity.java`.

**JSONB column + manual validator + tenant-owned base** (mirror `RuleEntity` lines 26-50, 115-123, 217-223):
```java
// RuleEntity.java — extends AbstractTenantOwnedEntity; jsonb String column; getter runs validator
@Entity @Table(name = "triage_audit")
public class TriageAuditEntity extends AbstractTenantOwnedEntity {
  private static final TriageActionResultJsonValidator ACTION_RESULT_JSON_VALIDATOR = new TriageActionResultJsonValidator();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "action_args_json", columnDefinition = "jsonb", nullable = false)
  private String actionArgsJson;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "gmail_change_token", columnDefinition = "jsonb")   // nullable: SHADOW_LOGGED / SAVE_DRAFT
  private String gmailChangeToken;

  @Column(name = "args_hash", nullable = false)
  private byte[] argsHash;  // 32 raw SHA-256 bytes (D-C2)
  // ... action_type stored as RuleActionType.id(); decision as TriageDecision.id(); rule_name_snapshot; reason; external_ref; failure_reason; applied_at; reverted_at
  // getActionArgsJson() runs ACTION_RESULT_JSON_VALIDATOR before returning (RuleEntity.getMatcherAst() pattern)
}
```
`AbstractTenantOwnedEntity` (verified) hoists `@TenantId @Column("tenant_id") private UUID tenantId` + audit columns + version. Javadoc note required (per CONTEXT.md §specifics): `gmail_change_token` stores "what we changed", NOT a full label snapshot.

---

### `core/triage/domain/TriageActionResult.java` + `TriageActionResultJsonValidator.java` (model + utility)

**Primary analogs:** `core/rules/domain/ActionIntent.java`, `core/rules/domain/ActionIntentJsonValidator.java`.

**Sealed interface** (mirror `ActionIntent` lines 5-50): `sealed interface TriageActionResult permits Label, Archive, SaveDraft` — `record Label(String labelId, String labelName)`, `record Archive()`, `record SaveDraft(String instruction, String draftId, String threadId)`. NO Jackson `@JsonTypeInfo` (D-B1) — `"type"` discriminator field + manual validator.

**Manual validator** (mirror `ActionIntentJsonValidator` lines 12-67 — Jackson 3 `JsonMapper.builder().build()` `ObjectMapper`, `readTree`, `path("type")`, `RuleActionType.fromId` → `NoSuchElementException` on unknown, `rejectUnknownFields` per type on write):
```java
// ActionIntentJsonValidator.java:12-46 — the discriminator-switch + reject-unknown-fields shape
private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();   // tools.jackson.databind.json.JsonMapper
// ... readTree(json) → switch on the resolved type → rejectUnknownFields(node, Set.of(<type-specific fields>))
//     unknown discriminator → NoSuchElementException → domain exception (mirror RuleValidationException.unsafeAction)
```
Annotations (`@JsonValue`/`@JsonCreator` on any type-id enum) stay in `com.fasterxml.jackson.annotation.*` (see `RuleActionType` lines 6-7, 22, 32). Validator runs from `@PrePersist` or the entity getter (RuleEntity precedent). Forward-compat: strict-write (validator rejects unknown), lenient-read (Jackson 3 default `FAIL_ON_UNKNOWN_PROPERTIES=false`).

**`TriageActionArgsCanonicalizer`** (D-C2 — no direct analog; new): sort object keys lexicographically, normalize whitespace, force UTF-8, output `SHA-256` 32 raw bytes (NOT hex). Reuse the Jackson 3 `JsonMapper` import path from `ActionIntentJsonValidator`.

---

### `core/triage/service/TriageSafetyPolicy.java` (service, allow-list gate)

**Primary analog:** `core/llm/service/ActionValidator.java` (lines 11-37).
```java
// ActionValidator.java:11-37 — EnumSet allow-list + fail-closed exception, both checks must fail open to leak
@Component
public class ActionValidator {
  private static final EnumSet<Action> ALLOW_LIST = EnumSet.of(Action.LABEL, Action.ARCHIVE, Action.SAVE_DRAFT);
  public Action validate(String functionName) {
    if (functionName == null || functionName.isBlank()) throw new SafetyViolationException();
    Action resolvedAction;
    try { resolvedAction = Action.fromFunctionName(functionName); }
    catch (NoSuchElementException unknownAction) { throw new SafetyViolationException(); }
    if (!ALLOW_LIST.contains(resolvedAction)) throw new SafetyViolationException();
    return resolvedAction;
  }
}
```
→ `TriageSafetyPolicy.gate(RuleActionType actionType)`: `EnumSet.of(RuleActionType.LABEL, RuleActionType.ARCHIVE, RuleActionType.SAVE_DRAFT)` (mirrors `RuleActionType` membership — Phase 4 adds NO new values); not in set → `TriageSafetyViolationException` (new no-arg, privacy-safe — see `llm/exception/SafetyViolationException` JavaDoc, RESEARCH.md Pitfall 5); log `event=triage_safety_violation tenantId={} ruleId={} actionType={}`; orchestrator writes audit `decision=REJECTED_BY_SAFETY_POLICY`. The orchestrator never reaches `TriageGmailWriter` for a rejected proposal.

---

### `core/triage/service/TriageGmailWriter.java` (service, Gmail write adapter — the ONLY triage Gmail-write call site)

**Primary analogs:** `core/gmail/service/GmailDeliveryProcessingService.java` (Gmail client build, lines 59-67), `core/gmail/service/GmailPreviewReadService.java` (Gmail request shape + `GoogleJsonResponseException` handling, `GmailApiClientFactory` reuse), `core/gmail/service/GmailApiClientFactory.java` (`refreshAccessToken` + `buildGmailClient`).

**Build authenticated Gmail client** (mirror `GmailDeliveryProcessingService` lines 59-67):
```java
// GmailDeliveryProcessingService.java:59-67 — decrypt refresh token, refresh access token, build client
String decryptedRefreshToken = new String(
    refreshTokenCipher.decrypt(connection.getRefreshTokenEncrypted(), tenantId.toString()), StandardCharsets.UTF_8);
GmailApiClientFactory.TokenRefreshResult tokenResult = gmailApiClientFactory.refreshAccessToken(decryptedRefreshToken);
Gmail gmail = gmailApiClientFactory.buildGmailClient(tokenResult.accessToken().value());
```
**Writes** (only 3 methods — no `sendDraft`/`sendMessage` anywhere): `applyLabel` → `gmail.users().messages().modify("me", id, new ModifyMessageRequest().setAddLabelIds(List.of(labelId)))`; `archiveSkipInbox` → `...modify(...).setRemoveLabelIds(List.of("INBOX"))`; `saveDraft` → `gmail.users().drafts().create("me", new Draft().setMessage(...))` → returns `draftId`. `users.messages.modify` is idempotent; `users.drafts.create` is NOT — hence the two-phase PENDING→APPLIED loop (RESEARCH.md Pitfall 1). The undo inverse calls (`removeLabel` / `addLabel="INBOX"` / `drafts.delete(draftId)`) also go through this class. No direct `RefreshTokenCipher` edge needed from `core.triage` — flow through `GmailApiClientFactory`. Wrap with `CreditLedger.reserve → execute → settle/release` and content-free logging.

---

### `core/triage/application/TriageUndoService.java` (service, compute-inverse + flip-decision)

**Primary analogs:** `core/rules/service/RuleManagementService.java` (tenant-scoped `@Transactional` mutation), `core/rules/exception/RuleValidationException.java` (reason-coded exceptions), `core/triage/service/TriageGmailWriter.java` (reused for inverse calls).

**Exhaustive `switch` on `TriageActionResult`** (D-B3 layer 2 — compile-time exhaustiveness preserves "SEND forbidden"; mirror the sealed-switch style in `RuleEvaluator.evaluate` lines 18-101 / `ActionIntent.fromAction` lines 43-49):
```java
TriageActionResult result = parse(auditRow.getActionArgsJson());
GmailInverseCall inverse = switch (result) {
  case TriageActionResult.Label label -> messagesModifyRemoveLabel(label.labelId());
  case TriageActionResult.Archive ignored -> messagesModifyAddLabel("INBOX");
  case TriageActionResult.SaveDraft draft -> draftsDelete(draft.draftId());
};
```
New `RuleActionType` values not present in `TriageActionResult` → `TriageAuditException.unsupportedActionType()` → mapped to HTTP 409 `TRIAGE_UNDO_UNSUPPORTED_ACTION` (fail-loud, not silent no-op — D-B6). Checks (mirror `RuleManagementService` ownership/version checks): tenant ownership, `decision == APPLIED` (else `TriageUndoAlreadyDoneException` → 409), `applied_at >= now - 30d` (else `TriageUndoExpiredException` → 409 — inject `Clock` like `GmailPreviewReadService` lines 65-90 for the mocked-clock test). On success: `TriageGmailWriter` inverse call → `repository.markReverted(auditId, tenantId, revertedAt)`.

**Exception classes** (mirror `RuleValidationException` lines 1-50 — private ctor + `Reason` enum + static factories) — but the *undo* exceptions can be simpler plain `RuntimeException` subclasses (no content payload); `GlobalExceptionHandler` maps each to 409 + an `ErrorCodes` constant (mirror `onRuleApiException` reason-switch lines 204-240).

---

### `core/triage/service/SenderSafetyNetService.java` (service, Gmail SENT lookup + Redis cache)

**Primary analog:** `core/gmail/service/GmailPreviewReadService.java` (Gmail `users().messages()` requests, `Clock` injection, exception handling).

**Gmail SENT metadata-only lookup** (D-D from SPEC §req 8): `gmail.users().messages().list("me").setQ("in:sent to:" + senderEmail + " newer_than:90d").setMaxResults(3L).execute()` → `protected = response.getResultSizeEstimate() >= 3` (or count returned ids ≥ 3 with early-exit). NO `.get(...)` calls — list returns ids only; count is enough; never reads bodies/snippets. Gmail client built the same way as `TriageGmailWriter` / `GmailDeliveryProcessingService`.

**Redis cache** (D-E3): key `triage:sender-protect:{tenantId}:{lower(senderEmail)}`, 24h TTL, stores only the boolean `protected` flag. **No `RedisTemplate`/`StringRedisTemplate` bean exists in the codebase today** (Redis is wired for Spring Session / rate-limit infra only) — verify and add a `StringRedisTemplate` bean if absent (Spring Data Redis + Lettuce are already on the classpath per STACK.md). Cache invalidation on opt-in: `redisTemplate.delete(key)` registered via `TransactionSynchronization.afterCommit` in the SAME service method that inserts the `tenant_sender_opt_in` row, AFTER DB commit (D-E3) — opt-in row check overrides the heuristic.

---

### `worker/triage/Triage*Job.java` (service, scheduled workers)

**Primary analog:** `worker/billing/CreditReserveWatchdog.java` + `worker/billing/CreditReserveWatchdogBatch.java`.

**Scheduler + ShedLock** (mirror `CreditReserveWatchdog` lines 26-56):
```java
// CreditReserveWatchdog.java:43-55 — @Scheduled + @SchedulerLock + Micrometer counter
@Scheduled(fixedRate = 60_000L)
@SchedulerLock(name = "creditReserveWatchdog", lockAtLeastFor = "PT30S", lockAtMostFor = "PT2M")
public void scheduledTick() { tick(); }
public void tick() { int releasedCount = batch.releaseStaleBatch(STALE_THRESHOLD, BATCH_LIMIT); /* log event=* */ }
```
→ ShedLock names: `triageEventRetry` (`fixedDelay="PT2M"`, `lockAtLeastFor="PT30S"`, `lockAtMostFor="PT5M"`), `triageEventCleanup` (`cron="0 0 3 * * *"`), `triageAuditPurge` (`cron="0 0 4 * * *"`, `lockAtLeastFor="PT1M"`, `lockAtMostFor="PT30M"`), `triagePendingReaper` (`fixedDelay="PT5M"`).

**Scheduler/batch SPLIT for the purge + reaper jobs** (mandatory — `CreditReserveWatchdogBatch` lines 14-33 JavaDoc explains why; RESEARCH.md Pitfall 6): the `@Transactional` scan-and-mutate method MUST live on a separate `@Component` collaborator (e.g., `TriageAuditPurgeBatch`) so Spring's proxy fires and the bounded `LIMIT 1000` delete loop holds locks correctly. `ShedLockConfig` (`@EnableSchedulerLock`, `LockProvider` bean) is already wired — the new jobs just register additional lock names. The event-registry retry/cleanup jobs inject Spring Modulith's `IncompleteEventPublications` / `CompletedEventPublications` beans (transitive via `spring-modulith-starter-jdbc`).

`worker/triage` is already covered by `ZeroMailWorkerApplication` `scanBasePackages = {"com.zeromail.worker", "com.zeromail.core"}` — just verify, no edit.

---

### `api/controllers/triage/*` (controllers, thin)

**Primary analogs:** `api/controllers/tenant/TriagePauseController.java` (one-action toggle), `api/controllers/rules/RulesController.java` (list + mutation, PathVariable, `@RequestBody @Valid`).

**Thin-controller shape** (mirror `TriagePauseController` lines 19-38 verbatim):
```java
// TriagePauseController.java:19-38 — inject core service, resolve tenantId from TenantContext, log event=*, return DTO.from(...)
@RestController @Tag(name = "tenant")
public class TriagePauseController {
  private final TenantService tenantService;
  @PutMapping("/tenant/triage-pause")
  public TriagePauseResponse setTriagePause(@RequestBody @Valid TriagePauseRequest request) {
    UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
    tenantService.setTriagePaused(tenantId, request.paused());
    log.info("event=triage_pause_toggled tenantId={} paused={}", tenantId, request.paused());
    return TriagePauseResponse.from(request.paused());
  }
}
```
→ `TriageTenantController.PATCH /api/tenant/triage/shadow-mode` is the near-identical twin (calls `tenantService.setTriageShadowMode(...)`, logs `event=triage_shadow_mode_toggled`). `TriageAuditController.POST /api/triage/audit/{auditId}/undo` and `SenderSafetyNetController` follow `RulesController` (PathVariable, delegate to `TriageUndoService` / `SenderSafetyNetService`, never inject repositories — Convention #1/#2, `controllers/<domain>/` + `dto/<domain>/`). DTO records mirror `api/dto/tenant/TriagePauseRequest.java`/`TriagePauseResponse.java` (record + `static from(...)`).

---

## Shared Patterns

### Tenant context rebind (worker-side after-`@Async` boundary)
**Source:** `backend/worker/src/main/java/com/zeromail/worker/GmailHistoryProcessor.java` lines 28-35; `backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java`.
**Apply to:** `TriageOrchestratorService` (first line of the `@ApplicationModuleListener`), every worker scheduler that touches tenant-scoped repositories.
```java
ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() -> /* tenant-scoped work */);
```
`TenantContext.runWith(...)` (referenced in CONTEXT.md) does not exist — add it or use `ScopedValue.where(...).run(...)`. (RESEARCH.md Pitfall 4.)

### Idempotent native upsert + native-query tenant scoping
**Source:** `backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java` lines 14-32; `backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java` (`@Modifying @Query` `updateStatus`/`releaseForRetry`).
**Apply to:** `TriageAuditRepository` (`insertAuditPendingIfAbsent` + `markApplied`/`markFailed`/`markReverted`), `TenantSenderOptInRepository` (if native), the purge/reaper batch SQL.
**Rule:** every `nativeQuery = true` statement touching a tenant-owned table MUST carry `tenant_id = :tenantId` — `@TenantId` does NOT apply to native SQL. (RESEARCH.md Pitfall 3; keep FND-05 green.)

### JSONB `String` column + manual discriminator validator + tenant-owned base
**Source:** `backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java` lines 26-50/115-123/217-223; `backend/core/src/main/java/com/zeromail/core/rules/domain/ActionIntentJsonValidator.java`; `backend/core/src/main/java/com/zeromail/core/shared/persistence/AbstractTenantOwnedEntity.java`.
**Apply to:** `TriageAuditEntity.actionArgsJson` / `gmailChangeToken`, `TenantSenderOptInEntity`, `TriageActionResultJsonValidator`.
**Rule:** `@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String ...` (Yasson handles runtime mapping); getter runs the validator; validator also runs from `@PrePersist`/constructor; Jackson 3 `tools.jackson.databind.json.JsonMapper`; annotations stay `com.fasterxml.jackson.annotation.*`; no `@JsonTypeInfo` (D-B1).

### ShedLock-coordinated `@Scheduled` worker + scheduler/batch split
**Source:** `backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java` + `CreditReserveWatchdogBatch.java`; `backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java`.
**Apply to:** all four `worker.triage` jobs.
**Rule:** scheduler `@Component` with `@Scheduled` + `@SchedulerLock(name=..., lockAtLeastFor=..., lockAtMostFor=...)` (tighter `lockAtLeastFor` than the schedule interval); any `@Transactional` scan-and-mutate logic lives on a *separate* collaborator bean (proxy boundary). `ShedLockConfig` already provides the `LockProvider` — no new wiring.

### `CreditLedger.reserve → settle/release`
**Source:** `backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedger.java` (JavaDoc lifecycle); `backend/core/src/main/java/com/zeromail/core/billing/domain/CallSite.java`.
**Apply to:** `TriageOrchestratorService` (per-message LLM call / per-rule fallback / pure-deterministic), `LlmGateway.evaluateSemanticIntents` impl. BYOK tenants bypass the ledger entirely (Phase 2C convention). Add `TRIAGE_PLATFORM_LLM` / `TRIAGE_DETERMINISTIC` to `CallSite` (additive — update the Phase 2B `CallSite`-membership ArchUnit rule).

### Gmail authenticated client construction
**Source:** `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java` (`refreshAccessToken` + `buildGmailClient`); `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java` lines 59-67; `backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java`.
**Apply to:** `TriageGmailWriter`, `SenderSafetyNetService`, the pending-reaper metadata verify.
**Rule:** decrypt refresh token via `RefreshTokenCipher.decrypt(encrypted, tenantId.toString())` → `gmailApiClientFactory.refreshAccessToken(...)` → `gmailApiClientFactory.buildGmailClient(...)`. No direct `core.triage → core.gmail.persistence.crypto` Modulith edge needed.

### Privacy-safe business exceptions + `GlobalExceptionHandler` mapping
**Source:** `backend/core/src/main/java/com/zeromail/core/rules/exception/RuleValidationException.java` (reason-coded factory); `backend/core/src/main/java/com/zeromail/core/llm/exception/SafetyViolationException.java` (no-arg, no payload); `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java` (`onRuleApiException` switch → `problem(...)`); `backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java` (dotted keys).
**Apply to:** `TriageSafetyViolationException` (no-arg, → 500 `TRIAGE_SAFETY_VIOLATION`), `TriageUndoExpiredException`/`TriageUndoAlreadyDoneException`/`TriageUndoUnsupportedActionException` (→ 409 `TRIAGE_UNDO_*`). Do NOT add a message-carrying constructor to the existing `llm.exception.SafetyViolationException` (RESEARCH.md Pitfall 5). i18n keys (vi+en) for each `ErrorCodes` constant — must pass `pnpm i18n:check` / `I18nArchUnitTest`.

### Liquibase additive changeset
**Source:** `backend/core/src/main/resources/db/changelog/changes/021-rules-engine-schema.yaml` (createTable + jsonb + FK deleteCascade + CHECK + indexes); `015-credit-reservation.yaml` (status CHECK + partial index); `013-tenants-triage-paused.yaml` (addColumn boolean).
**Apply to:** `024`–`027`. Floor is `024` (last committed: `023-fix-pin-calendar-category.yaml`). Additive only — no destructive ops. `024` must mirror the Spring Modulith canonical `event_publication` DDL (dump from a throwaway dev DB first — RESEARCH.md Pitfall 2). Append all 4 includes to `db.changelog-master.yaml` in numbered order.

### ArchUnit boundary rule
**Source:** `backend/core/src/test/java/com/zeromail/core/arch/RulesBoundaryArchTest.java` (`noClasses().that().resideInAPackage(...).should().dependOnClassesThat()...` + `allowEmptyShould(true)`); `backend/core/src/test/java/com/zeromail/core/arch/SafetyContractArchTests.java` (custom `ArchCondition` over `getMethodCallsFromSelf()`).
**Apply to:** `NoGmailSendAllowedTest` (no `Gmail.Users.Messages.send` / `Gmail.Users.Drafts.send` call site anywhere), `TriageGmailWriteBoundaryTest` (only `TriageGmailWriter` calls Gmail write APIs from triage), the `triage_audit` repo-method ban, the updated `CallSite` membership rule. Use `ImportOption.DoNotIncludeTests` and `allowEmptyShould(true)` for forward-looking bans.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `core/triage/domain/TriageActionArgsCanonicalizer.java` | utility | transform | No existing canonical-JSON serializer / SHA-256-of-payload utility in the repo. Build new; reuse only the Jackson 3 `JsonMapper` import path from `ActionIntentJsonValidator`. The hash-output convention (32 raw SHA-256 bytes, not hex) is locked by D-C2. |
| `024-modulith-event-publication.yaml` (the *column definitions*) | migration | — | The table *structure* mirrors `017-shedlock-table.yaml` (an infra table for a library), but the actual column names/types/indexes must come from Spring Modulith's canonical `event-publication.sql` for the pinned version (`2.0.7-SNAPSHOT`) — not from any existing repo file. Dump from a throwaway dev DB with auto-init enabled, then disable auto-init and let Liquibase own it (RESEARCH.md §"Version verification" + Pitfall 2). |
| `core/triage/application/TriageOrchestratorService.java` — the `@ApplicationModuleListener` annotation + `IncompleteEventPublications` retry semantics | service | event-driven | No existing `@ApplicationModuleListener` consumer or Spring Modulith event-registry usage anywhere in the codebase yet. The *structure* (ScopedValue rebind + `@Transactional` orchestration) has strong analogs (`GmailHistoryProcessor`, `GmailDeliveryProcessingService`), but the Modulith-specific glue (`@ApplicationModuleListener`, `FailedEventPublications`, the JDBC starter) is new — re-fetch the Spring Modulith Events reference via Context7 at plan-phase (URLs in 04-CONTEXT.md §"External specs"). |
| `core/triage/service/SenderSafetyNetService.java` — the Redis caching layer | service | caching | No `RedisTemplate`/`StringRedisTemplate` bean exists today (Redis is wired for Spring Session / rate-limit infra, not app-level caching). Spring Data Redis + Lettuce are on the classpath; add a `StringRedisTemplate` bean (or a small typed cache component) — verify before assuming one exists. |
| `core/llm/gateway/springai/SemanticIntentEvaluator.java` + `SemanticIntentResponse.java` | service + DTO | request-response | The *project* has Spring AI usage (`SpringAiLlmModelClient`, `LlmGatewayImpl`) but no structured-output / strict-JSON-Schema classifier path. The exact shape (`response_format=json_schema, strict=true`, node-id set-equality validator, `.chatResponse()` not `.entity()` for token capture, no `.stream()`) is pre-locked by **04-AI-SPEC §3/§4/§4b** — read it before implementing; do not improvise. |

---

## Metadata

**Analog search scope:** `backend/core/src/main/java/com/zeromail/core/{gmail,rules,llm,billing,tenant,shared}/**`, `backend/worker/src/main/java/com/zeromail/worker/**`, `backend/api/src/main/java/com/zeromail/api/{controllers,dto,error,config}/**`, `backend/core/src/main/resources/db/changelog/**`, `backend/core/src/test/java/com/zeromail/core/arch/**`.
**Files scanned:** ~50 source files read in full or in part; ~200 enumerated.
**Pattern extraction date:** 2026-05-11
