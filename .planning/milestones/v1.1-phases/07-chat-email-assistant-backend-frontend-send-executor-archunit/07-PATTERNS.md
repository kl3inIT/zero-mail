# Phase 7 — Codebase Pattern Map

> Where to look in the existing codebase before writing new files. Each new file gets a "closest analog" and a "deltas" list. Read this BEFORE generating plans.

**Mapped:** 2026-05-17
**Files classified:** ~70 new backend Java + ~25 new frontend TS files (per 07-SPEC.md Boundaries)
**Analogs found:** Backend HIGH coverage (every capability except SSE has a direct precedent); Frontend HIGH coverage for feature-folder shape, MEDIUM for streaming.

---

## 1. Module Layout & Naming Inheritance

`core.chat` mirrors **two precedents** simultaneously:

| Inherit | Source | What `core.chat` Copies |
|---------|--------|-------------------------|
| **Modulith `package-info.java` shape** | `backend/core/.../core/llm/package-info.java` (lines 32-43) and `core/triage/package-info.java` (lines 1-22) | `@ApplicationModule(displayName, allowedDependencies = {...})` + sub-package commentary block. Triage's broad `allowedDependencies` is the precedent D-01 cites. |
| **`gateway.<vendor>.*` Spring AI confinement** | `core/llm/gateway/springai/*` (12 classes; ALL `org.springframework.ai.*` imports live here) | `core.chat.llm.springai.*` repeats the boundary. ArchUnit rule needed: `noClasses().that().resideInAPackage("..core.chat..").and().resideOutsideOfPackage("..core.chat.llm.springai..").should().dependOnClassesThat().resideInAPackage("org.springframework.ai..")`. |
| **Sub-package split** | `core/triage/{domain,usecases,projection,persistence,exception}` + `core/llm/{domain,usecases,gateway,persistence,exception}` | SPEC-locked `core.chat/{domain,usecases,projection,persistence,exception,confirm,sanitize,llm}` — `confirm` + `sanitize` + `llm` are net-new sub-packages without exact analog (carved out for SPEC ARCH-02 + ARCH-03). |
| **Entity inheritance** | `core/shared/persistence/AbstractTenantOwnedEntity` (provides `@TenantId` discriminator + audit columns) | All Phase 7 JPA entities (`ChatEntity`, `AssistantPendingActionEntity`, `AssistantSendAuditEntity`, `AssistantSettingsEntity`, `AssistantMemoryEntity`, `AssistantKnowledgeSnippetEntity`) extend this base. |
| **Privacy boundary precedent** | `core/llm/usecases/LlmGateway.java` Javadoc lines 18-22 | `core.chat.usecases.ChatLlmGateway` Javadoc must declare the same "MUST NOT log raw prompts/completions" invariant, then narrow to "MAY persist user-typed chat config + structured tool I/O" per Privacy scope carve-out. |

---

## 2. Per-Capability Analog Map

### 2.1 Modulith module declaration — `core.chat/package-info.java`
- **New file:** `backend/core/src/main/java/com/zeromail/core/chat/package-info.java`
- **Closest analog:** `backend/core/src/main/java/com/zeromail/core/triage/package-info.java` (broadest precedent for D-01).
- **Inherited:** `@ApplicationModule(displayName, allowedDependencies)` literal shape; comment block style.
- **Deltas:** `allowedDependencies = {"llm", "rules", "gmail", "triage", "tenant", "shared.persistence", "shared.lang", "shared.privacy"}` per D-01. **NOT** in deps: `billing` (chat goes through `LlmGateway`).

### 2.2 SSE controller pattern — `ChatController`
- **New file:** `backend/api/src/main/java/com/zeromail/api/controllers/chat/ChatController.java`
- **Closest analog:** **NO ANALOG IN CODEBASE FOR SSE.** `Grep("SseEmitter")` returns zero hits. Closest substitute is `backend/api/.../websocket/BillingWebSocketPublisher.java` (uses STOMP, not SSE; only relevant for the `@TransactionalEventListener` event-publishing pattern — see §2.10).
- **Inherited (controller skeleton ONLY):** `backend/api/.../controllers/rules/RulesController.java` (lines 1-95) — `@RestController @Tag @RequestMapping("/api/<domain>")`, constructor injection of services, `TenantContext.currentTenantUuid()` resolution at top of every handler.
- **Inherited (controller dir pattern):** `backend/api/.../controllers/<domain>/` per CONVENTIONS #2 — Phase 7 adds `controllers/chat/`.
- **Deltas:**
  - Returns `SseEmitter` (imperative) instead of a JSON-mapped record.
  - Sets `x-vercel-ai-ui-message-stream: v1` header on `HttpServletResponse` BEFORE returning emitter (SPEC Constraint).
  - Wires `SseEmitter.onCompletion/onTimeout/onError` to `Disposable.dispose()` on the upstream Reactor stream (D-03).
  - Heartbeat scheduling per-emitter via `TaskScheduler` bean + `ScheduledFuture` (D-04) — no current pattern in codebase for this; this is one of the highest-risk new code areas.
- **Test analog:** `backend/api/.../controllers/rules/RulesControllerIntegrationTest.java` shows `@SpringBootTest` + `MockMvc` for controllers; streaming response will need `MockMvc.asyncDispatch()` plus manual SSE parsing.

### 2.3 Streaming orchestrator + Spring AI adapter — `ChatLlmGateway` + `SpringAiStreamingChatModelClient`
- **New files:**
  - `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatLlmGateway.java` (interface)
  - `backend/core/src/main/java/com/zeromail/core/chat/usecases/ChatOrchestratorService.java` (impl)
  - `backend/core/src/main/java/com/zeromail/core/chat/llm/springai/SpringAiStreamingChatModelClient.java`
- **Closest analog (interface shape + Javadoc):** `backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGateway.java` (entire file).
- **Closest analog (impl + tenant context + observation):** `backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGatewayImpl.java` (uses `TenantContext.currentTenantUuid()`, `ObservationRegistry`, `MeterRegistry`, `tools.jackson.databind.ObjectMapper`).
- **Closest analog (Spring AI adapter shape, vendor SDK confinement):** `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SpringAiLlmModelClient.java` (lines 1-120).
- **Inherited (lines 61-74 of `SpringAiLlmModelClient.java`):**
  ```java
  OpenAiChatOptions.builder()
      .model(request.model())
      .temperature(request.temperature())
      .internalToolExecutionEnabled(false);  // <-- ARCH-07 verbatim
  ```
  That `internalToolExecutionEnabled(false)` flag is already proven in v1.0 and is exactly what ARCH-07 / SPEC Constraint requires for chat HITL.
- **Inherited (Jackson 3 import):** v1.0 uses `tools.jackson.databind.ObjectMapper` + `tools.jackson.core.JacksonException` (lines 23-24 of `LlmGatewayImpl.java`, line 22-23 of `SpringAiLlmModelClient.java`) — Boot 4 / Jackson 3 namespace shift already in use; reuse verbatim.
- **Deltas:**
  - Use `StreamingChatModel.stream(prompt)` → `Flux<ChatResponse>` instead of synchronous `.call().chatResponse()`.
  - `ChatToolCallRegistry` collects tool calls from raw SSE events (ARCH-07 workaround for spring-ai #3366/#5167) — no v1.0 analog because v1.0 is synchronous.
  - Returns `Flux<UiMessageStreamEvent>` (Spring-AI-free signature per D-02) — adapter encapsulates Spring AI types.

### 2.4 Tenant ScopedValue propagation across long-lived SSE
- **New file:** `backend/core/src/main/java/com/zeromail/core/chat/llm/TenantAwareReactorScheduler.java`
- **Closest analog:** `backend/core/src/main/java/com/zeromail/core/tenant/concurrency/TenantAwareTaskScope.java` (entire file — 37 lines).
- **Inherited (verbatim pattern from lines 18-26):**
  ```java
  public static TenantAwareTaskScope openInherit() {
      String currentTenantId = TenantContext.currentOrThrow();
      return new TenantAwareTaskScope(currentTenantId, StructuredTaskScope.open());
  }
  public <T> StructuredTaskScope.Subtask<T> fork(Callable<T> task) {
      return structuredTaskScope.fork(
          () -> ScopedValue.where(TenantContext.TENANT, tenantId).call(task::call));
  }
  ```
  The "capture tenant on construction → rebind in worker" idiom is the exact pattern the Reactor scheduler must replicate — wrap each `subscribeOn` task in `ScopedValue.where(TenantContext.TENANT, capturedTenantId).run(task)`.
- **Inherited (ArchUnit ban precedent):** `backend/core/src/test/java/com/zeromail/core/arch/TenantIsolationArchTests.java` lines 27-38 — bans `Thread.ofVirtual` / `CompletableFuture.{supplyAsync,runAsync}` outside `..core.tenant.concurrency..`. Phase 7 mirrors this for `Schedulers.{boundedElastic,parallel,single}` inside `..chat..`.
- **Deltas:**
  - Reactor `Scheduler` instead of `StructuredTaskScope` — different concurrency primitive, same propagation pattern.
  - Lives in `core.chat.llm` (SPEC sub-package, not `core.tenant.concurrency`) because chat is the only consumer; if it generalizes later, hoist to shared.

### 2.5 JSONB persistence pattern — `chat_message.parts` + JPA entities
- **New files:** all entities in `backend/core/src/main/java/com/zeromail/core/chat/persistence/*Entity.java`
- **Closest analog (JPA + JSONB):** `backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java` (lines 53-64) and `backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java` (lines 39-45).
- **Inherited (verbatim from `TriageAuditEntity.java` lines 53-64):**
  ```java
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "action_args_json", columnDefinition = "jsonb", nullable = false)
  private String actionArgsJson;
  ```
  The `@JdbcTypeCode(SqlTypes.JSON)` + `String`-typed field + `columnDefinition = "jsonb"` triple is the v1.0 pattern. Apply identically to `assistant_pending_action.payload`, `assistant_send_audit.preview_snapshot`, etc.
- **Inherited (base class):** all entities extend `AbstractTenantOwnedEntity` (which carries `@TenantId @Column("tenant_id")` discriminator).
- **Inherited (`@PrePersist`/`@PreUpdate` JSON validation):** `TriageAuditEntity.java` lines 235-253 — validate JSON shape inside the entity before write. Repeat for `assistant_send_audit.preview_snapshot` shape validation.
- **Deltas:**
  - `chat_message` uses **Spring Data JDBC** (not JPA) per D-07. **NO v1.0 analog** for Spring Data JDBC + JSONB — Phase 7 introduces a custom `AttributeConverter` / `org.springframework.data.relational.core.mapping.Embedded`-style converter for the `parts` envelope. Planner must read Spring Data JDBC reference (Context7) before implementing the converter; closest "shape" reference is the JPA `@JdbcTypeCode` pattern above, but the wiring differs.
  - `chat_message.parts` carries `schemaVersion: 1` (D-08) — converter is schema-version-aware from day one.

### 2.6 Liquibase changelog convention — 041 through 046
- **Master changelog:** `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` (current last include = `040-triage-audit-message-ref.yaml` at line 119-121).
- **Closest analog (full table create + JSONB + FK + comment block):** `backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml` (lines 1-80).
- **Closest analog (column add):** `backend/core/src/main/resources/db/changelog/changes/040-triage-audit-message-ref.yaml` (entire file).
- **Inherited shape (verbatim style from 025):**
  ```yaml
  databaseChangeLog:
    - changeSet:
        id: 0XX-<slug>
        author: zeromail
        comment: >
          <multi-line rationale: what this row stores, what it does NOT store,
          why the column shape is what it is, FK/index/lifecycle decisions.>
        changes:
          - createTable: ...
        rollback: ...
  ```
  Comment block is load-bearing — used by ops + code review to understand schema decisions. All six Phase 7 changelogs must include comparable rationale (privacy invariant for `chat_message`, ARCH-04 atomicity for `assistant_send_audit`, etc.).
- **Liquibase numbering — 041–046 are uncontested.** Master changelog ends at 040. Phase 7 appends six new `include:` blocks atomically with each changelog file.
- **Deltas:**
  - `042-chat-message.yaml` is the first changelog in the repo to ship a **Postgres trigger** (`chat_message_body_ban`). No v1.0 analog for triggers — closest is `pgcrypto` extension reuse in token tables (also no trigger usage). Planner consults Liquibase YAML `<sql>` change type docs; trigger function + trigger statement go inline in the changelog.

### 2.7 ArchUnit invariants — Gmail send count flip + content ban + Scheduler ban
- **New files:**
  - `backend/core/src/test/java/com/zeromail/core/arch/OnlyOneGmailSendCallSiteTest.java`
  - `backend/core/src/test/java/com/zeromail/core/arch/ChatPersistenceContentBanTest.java`
  - `backend/core/src/test/java/com/zeromail/core/arch/ChatNoReactorSchedulerTest.java`
- **Closest analog (count == 0 / matcher style):** `backend/core/src/test/java/com/zeromail/core/arch/NoGmailSendAllowedTest.java` (entire file, 68 lines) — Phase 7 **modifies this in same PR**.
- **Closest analog (content-string-on-repository ban):** `backend/core/src/test/java/com/zeromail/core/arch/LlmRepositoryContentBanTest.java` (entire file, 73 lines) — `BANNED_METHOD_NAME` regex + `JavaClass`/`JavaMethod` walking pattern carries over verbatim for `ChatPersistenceContentBanTest`.
- **Closest analog (carved single-caller boundary):** `backend/core/src/test/java/com/zeromail/core/arch/TriageGmailWriteBoundaryTest.java` (entire file, 71 lines) — `TRIAGE_GMAIL_WRITER` constant + early-return on the allowed class is the exact pattern for `OnlyOneGmailSendCallSiteTest`'s `ASSISTANT_SEND_EXECUTOR` constant.
- **Inherited (`NoGmailSendAllowedTest.java` lines 23-61):** the entire `ArchCondition<JavaClass>` walking `getMethodCallsFromSelf()` and filtering by `targetOwnerName.endsWith(GMAIL_MESSAGES_OWNER)`. Phase 7 update: add early-return when class is annotated `@AllowedSendCallSite`, then flip `.allowEmptyShould(true)` → `.allowEmptyShould(false)` and pair with positive test that asserts **count == 1** (not ≤1).
- **Inherited (Scheduler ban shape):** `backend/core/src/test/java/com/zeromail/core/arch/TenantIsolationArchTests.java` lines 27-38 — Phase 7 mirrors this against `reactor.core.scheduler.Schedulers.{boundedElastic,parallel,single}` inside `..core.chat..`.
- **Deltas:**
  - `OnlyOneGmailSendCallSiteTest` is the first ArchUnit test in the repo that asserts an **exact count > 0**. v1.0 ArchUnit tests are all "no class" / "only X may". Pattern: count violations inside the condition, then assert `assertThat(observedCallSiteCount).isEqualTo(1)` in a `@Test` method outside the `@ArchTest` rule (because ArchUnit `ArchRule` semantics are "violations exist or not"). See ARCHITECTURE.md §"ArchUnit 0→1 Flip" for the recommended JUnit + ArchUnit hybrid.
  - `ChatPersistenceContentBanTest` mirrors `LlmRepositoryContentBanTest` regex shape but targets `chat_message.parts` JSON envelope content — extend the regex to include `emailBody|messageContent|bodyHtml|bodyText`.

### 2.8 Existing Gmail send call site count = 0 (verification)
- **Grep verification (run from repo root):**
  - `Grep("messages\(\)\.send\(", path=backend, ...)` → **zero hits** in production code (confirmed 2026-05-17 in this analysis).
  - `Grep("@AllowedSendCallSite")` → only matches in `.planning/**` documents — annotation does not yet exist.
- **Confirmation:** the count == 0 invariant currently holds. `NoGmailSendAllowedTest.allowEmptyShould(true)` is correct for v1.0. Phase 7 lands `AssistantSendExecutor` as the exactly-one call site and flips the test in the same PR.
- **Gmail write call sites that already exist (allowed, NOT send):** `backend/core/src/main/java/com/zeromail/core/triage/usecases/TriageGmailWriter.java` calls `gmail.users().messages().modify(...)` (label/archive) and `gmail.users().drafts().create(...)` (save draft) — these are NOT send and the existing `TriageGmailWriteBoundaryTest` enforces this is the single triage write call site.

### 2.9 Spring Modulith verification test
- **No new file needed** — Phase 7 reuses existing `backend/api/src/test/java/com/zeromail/api/ZeroMailApiApplicationModulesTest.java` (12 lines, calls `ApplicationModules.of(...).verify()`).
- **Inherited:** the verification runs across the whole module graph; adding `core.chat/package-info.java` with the right `allowedDependencies` is sufficient for this test to lock the new module boundary.
- **Deltas:** if any current module needs to **import** `core.chat` (e.g., for the `AssistantSendCompleted` event consumer), update that module's `allowedDependencies` — but per D-01 / CONVENTIONS #6, the listener can live in `backend/api` (which already imports across modules) using plain `@TransactionalEventListener`, so no current module's declaration needs to change.

### 2.10 `@TransactionalEventListener(AFTER_COMMIT)` event consumer
- **New file:** wherever the analytics module subscribes to `AssistantSendCompleted` (likely `backend/api/.../analytics/AssistantSendAnalyticsPublisher.java` or `backend/core/.../analytics/...` listener).
- **Closest analog (verbatim shape):** `backend/api/src/main/java/com/zeromail/api/websocket/BillingWebSocketPublisher.java` (entire file, 28 lines).
- **Inherited (lines 22-27):**
  ```java
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onBillingTopupCredited(BillingTopupCredited event) {
      String destination = "/topic/tenants/" + event.tenantId() + "/billing";
      messagingTemplate.convertAndSend(destination, BillingTopupCreditedMessage.from(event));
      log.info("event=billing_topup_websocket_sent tenantId={}", event.tenantId());
  }
  ```
  Note: this is `@TransactionalEventListener` (NOT `@ApplicationModuleListener`) because `BillingWebSocketPublisher` lives in `backend/api`, which listens to a `core` event — matches the user memory rule `feedback_modulith_listener_scope`.
- **Deltas:**
  - Event class `AssistantSendCompleted` lives in `core.chat.event` (per "domain events shared by API/worker/future modules belong in `backend/core`" — CONVENTIONS #6).
  - Single event after `assistant_send_audit` commits — NOT per SSE turn (SPEC Constraint).

### 2.11 Audit row pattern — `assistant_send_audit`
- **New file:** `backend/core/src/main/java/com/zeromail/core/chat/persistence/AssistantSendAuditEntity.java`
- **Closest analog:** `backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java` (entire file, 283 lines).
- **Inherited:**
  - Extends `AbstractTenantOwnedEntity` (line 23).
  - `@AttributeOverride(name = "id", column = @Column(name = "audit_id"))` pattern for renaming the inherited `id` column (line 21).
  - JSONB action args field + `@JdbcTypeCode(SqlTypes.JSON)` (lines 53-55).
  - `@PrePersist`/`@PreUpdate` validation (lines 235-253).
  - Fail-loud constructor with `requireText` / `requireDecision` private helpers (lines 99-140, 262-281).
  - Same-transaction write semantics — `TriageAuditSaga` already demonstrates "audit row written inside the same `@Transactional` boundary as the state machine flip"; ARCH-04 mirrors this.
- **Deltas:**
  - `UNIQUE (chat_id, tool_call_id)` constraint (idempotent retry per D-06) — `TriageAudit` has analogous `(tenant_id, gmail_message_id, action_type, args_hash)` idempotency index; pattern carries over.
  - Optimistic concurrency via `chat_message.parts.updated_at` CAS lives on `ChatMessage` (JDBC), not on the audit row — see §2.5.

### 2.12 Redis lease for confirmation state machine
- **New file:** `backend/core/src/main/java/com/zeromail/core/chat/confirm/ChatConfirmationLeaseStore.java`
- **Closest analog:** `backend/core/src/main/java/com/zeromail/core/shared/lock/RedisDistributedLock.java` (entire file, 121 lines).
- **Inherited:**
  - `StringRedisTemplate` injection via `ObjectProvider<StringRedisTemplate>` + supplier pattern (lines 21-38).
  - `opsForValue().setIfAbsent(key, token, ttl)` for SET NX EX (line 54) — exactly the primitive the 5-min confirmation lease needs.
  - `LockBackendUnavailableException` fail-loud when Redis is down (lines 44-46, 58-62).
  - `event=redis_lock_unavailable keyPrefix={}` privacy-safe logging (CONVENTIONS #5; never log the full key because it embeds tenant + chat ID).
- **Deltas:**
  - Lease key shape `chat-confirm:<chatId>:<toolCallId>` (5-min TTL per SPEC Constraint).
  - Lease commit happens BEFORE Gmail send (D-06); release inside same `@Transactional` after audit row commits.
  - No `LockHandle` `try-with-resources` pattern here — the lease lives across the confirmation request/response, not within a single method scope.

### 2.13 `@Scheduled` reconciliation cron
- **New file:** `backend/api/src/main/java/com/zeromail/api/chat/AssistantPendingActionReconciler.java`
- **Closest analog:** `backend/worker/src/main/java/com/zeromail/worker/triage/TriagePendingReaperJob.java` (use `Grep` to inspect its `@Scheduled` shape) and `backend/worker/.../GmailWatchScheduler.java`.
- **Deltas:**
  - Lives in `backend/api` (NOT `backend/worker`) per D-05 — chat is request-scoped and worker is not exercised in v1.1.
  - Single-instance VPS, no `ShedLock` — but a 5-min `fixedRate` cron is the same shape as the worker reapers.
  - Inspect the worker reaper file to copy the `@Scheduled(fixedRate=...)` + `TenantContext.runWith(...)` rebind pattern (workers must rebind tenant per row).

### 2.14 Sanitization pipeline pattern — `ToolOutputSanitizer`
- **New file:** `backend/core/src/main/java/com/zeromail/core/chat/sanitize/ToolOutputSanitizer.java`
- **Closest analog:** `backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/SanitizationPipeline.java` (entire file).
- **Inherited:**
  - `@Service` + `List<Sanitizer>` injection + `AnnotationAwareOrderComparator.sort(...)` (lines 16-27).
  - `event=sanitization_completed tenantId={} truncated={} tokenCount={}` privacy-safe log (lines 39-43) — match the event-name + structured-field shape.
  - `SanitizationException` wrapping for step failures (lines 35-37).
- **Inherited (stage abstraction):** `backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/Sanitizer.java` (the `Sanitizer` interface) — `ToolOutputBodyStripStage` implements `Sanitizer` so it picks up the same `@Order` + composite pipeline plumbing.
- **Deltas:**
  - Targets tool-output JSON envelopes (not raw email HTML), so the Jsoup HTML-strip stage is NOT in the chat pipeline.
  - Stages needed: `EmailBodyStripStage` (strip `body`/`bodyHtml`/`bodyText` fields from `readEmail` results), `LengthCapStage` (truncate per-field), `SchemaVersionStampStage` (add `schemaVersion: 1`).

### 2.15 Personalization XML-fenced sandbox + sentinel stripping
- **New file:** `backend/core/src/main/java/com/zeromail/core/chat/sanitize/PersonalizationSandbox.java`
- **Closest analog:** `backend/core/src/main/java/com/zeromail/core/llm/usecases/SystemPrompts.java` (read to find the existing system-prompt template assembly).
- **Closest analog (sentinel-stripping mechanic):** `backend/core/src/main/java/com/zeromail/core/llm/gateway/sanitization/UnicodeTagStripSanitizer.java` (strips invisible Unicode-tag characters — closest precedent for "strip dangerous tokens").
- **Deltas:** new XML-fence wrapper + 2000-char hard cap; no prior code wraps user input in `<user_personalization>` fences. Use ARCH-06 SPEC text verbatim for the sentinel list.

### 2.16 Frontend feature folder — `apps/web/features/chat/`
- **New files:** `apps/web/features/chat/{api,hooks,components}/*`, `messages.ts`
- **Closest analog (whole feature folder shape):** `apps/web/features/rules/` (api/components/hooks/lib/messages.ts/query-keys.ts).
- **Inherited (verbatim file roles):**
  - `features/rules/api/rules-api.ts` (lines 1-60) — `openapi-fetch` client, `xsrfHeader()` from `@/lib/api/client`, `unwrap()` helper for typed errors.
  - `features/rules/query-keys.ts` (entire file, 6 lines) — flat key factory `chatKeys = { all, list, detail }`.
  - `features/rules/hooks/use-rules.ts` (lines 1-60) — one hook per use case, `useMutation`/`useQuery` + `useQueryClient().invalidateQueries({ queryKey: ... })`.
  - `features/rules/messages.ts` (lines 1-30) — flat `{ 'key.path': { vi, en } }` object, co-located per `feedback_flat_folder_structure` memory.
- **Deltas:**
  - `query-keys.ts` only for history sidebar caching — `useChat` itself doesn't go through TanStack Query (CONVENTIONS #8 — don't create query keys for mutation-only features). Per D-10, history list IS cached.
  - `useChat` hook from `@ai-sdk/react@3` — no v1.0 analog; `apps/web/lib/api/client.ts` provides cookie-auth foundation that `useChat` consumes via `credentials: 'include'`.

### 2.17 i18n bundles (Vietnamese-default)
- **New files:** `apps/web/features/chat/messages.ts` (and aggregation into `apps/web/i18n/messages/{vi,en}.json`).
- **Closest analog:** `apps/web/features/rules/messages.ts` (entire file).
- **Inherited:** `next-intl` v4 with locale cookie resolution (no `[locale]` segment) — `apps/web/i18n/request.ts` (entire file, 29 lines) is the existing wiring. Vietnamese default is locked there (`routing.defaultLocale === 'vi'`).
- **Deltas:** none — Phase 7 just adds `chat.*` keys following the rules pattern. `apps/web/scripts/check-i18n.ts` already enforces VI/EN parity in CI.

### 2.18 Page route + protected shell mount
- **New file:** `apps/web/app/(protected)/(app)/chat/page.tsx`
- **Closest analog:** `apps/web/app/(protected)/(app)/ai/page.tsx` (entire file, 14 lines) and `apps/web/app/(protected)/(app)/rules/page.tsx`.
- **Inherited:**
  ```tsx
  import { Suspense } from 'react';
  import { LoadingState } from '@/components/states/LoadingState';
  import { ChatWorkspace } from '@/features/chat/components/ChatWorkspace';

  export default function ChatPage() {
    return (
      <div className="mx-auto w-full max-w-6xl space-y-5 p-4 md:p-6">
        <Suspense fallback={<LoadingState variant="cards" count={2} />}>
          <ChatWorkspace />
        </Suspense>
      </div>
    );
  }
  ```
- **Deltas:** wrapper container will differ (chat is full-height conversation pane, not a card stack) — see 07-UI-SPEC.md for the actual chrome.

### 2.19 Playwright e2e specs
- **New files:** `apps/web/e2e/chat/*.spec.ts`
- **Closest analog (mock-mode pattern, route interception, structure):** `apps/web/e2e/rules.spec.ts` (lines 1-50).
- **Inherited:**
  - Mock-mode types (`MockMode`, `MockRule`) — define `MockMode = 'streaming-flow' | 'confirm-send-flow' | 'replay-flow'` etc.
  - `API_ROUTE_PATTERN` and `expectAppShellChrome` shared utilities from `e2e/chrome-test-utils.ts` (already exists).
  - Per user memory `reference_playwright_relogin`: bundled Google OAuth, so e2e tests use the existing login fixture; chat e2e mounts on the post-auth shell.
- **Deltas:**
  - SSE mocking — Playwright route handler for `POST /api/chat` must stream a fake `text/event-stream` body (set chunked transfer + write progressive chunks). No existing e2e tests do this; planner consults Playwright `Route.fulfill` streaming docs.

### 2.20 shadcn/ui primitives — vendoring audit
- **Currently vendored at `apps/web/components/ui/`:** alert, alert-dialog, avatar, badge, button, card, chart, checkbox, command, dialog, dropdown-menu, input, input-group, label, popover, progress, radio-group, scroll-area, select, separator, sheet, sidebar, skeleton, sonner, switch, table, tabs, textarea, toggle, toggle-group, tooltip.
- **Phase 7 needs (per 07-UI-SPEC.md):** Card, Button, Dialog, Sheet, Sidebar, Input, Textarea, Checkbox, Tooltip, ScrollArea, Skeleton, Sonner, Separator, DropdownMenu — **all already vendored**.
- **Not yet vendored, may need:** none confirmed. If preview cards use `<HoverCard>` (not currently used), install via `pnpm dlx shadcn@latest add hover-card` per `apps/web/AGENTS.md`.

### 2.21 AI Elements primitives — vendoring audit
- **Currently vendored at `apps/web/components/ai/`:** **NONE** (the `apps/web/features/ai/` folder is unrelated — it's the v1.0 BYOK settings page).
- **Phase 7 ships per D-09 + STACK.md:** `pnpm dlx ai-elements@latest add conversation message prompt-input response tool reasoning loader suggestion confirmation` into `apps/web/components/ai/*`.
- **ESLint/Prettier ignore globs:** currently exclude `components/ui/**`; D-09 adds `components/ai/**` to the same globs.

### 2.22 Privacy logging format
- **Reference:** `event=<name> tenantId={}` + structured fields, per CONVENTIONS #5.
- **Concrete examples to mirror:**
  - `core/llm/gateway/sanitization/SanitizationPipeline.java` line 39-43: `event=sanitization_completed tenantId={} truncated={} tokenCount={}`.
  - `core/shared/lock/RedisDistributedLock.java` lines 43-44: `event=redis_lock_unavailable keyPrefix={}`.
  - `api/websocket/BillingWebSocketPublisher.java` line 26: `event=billing_topup_websocket_sent tenantId={}`.
- **Phase 7 events** (planner-suggested): `event=chat_stream_started tenantId={} chatId={} model={}`, `event=chat_tool_call_intercepted tenantId={} chatId={} toolName={}`, `event=chat_confirmation_lease_acquired tenantId={} chatId={} ttlSeconds=300`, `event=chat_send_committed tenantId={} chatId={} auditId={}`, `event=chat_send_canceled tenantId={} chatId={}`. **Never** log `bodyHtml`, `bodyText`, `subject`, recipient email, prompt text, completion text, tool args containing body.

### 2.23 CI grep gate
- **Closest analog:** existing CI only runs `./gradlew check` + `pnpm lint/typecheck/test/build` + Playwright (see `.github/workflows/gates.yml` lines 38-39, 87-94). **No grep gates currently exist.**
- **Phase 7 adds (new step in gates.yml `backend` job):**
  ```yaml
  - name: Enforce exactly 1 Gmail send call site
    run: |
      COUNT=$(grep -rE 'messages\(\)\.send\(' backend/ --include='*.java' | grep -v Test | grep -v "AssistantSendExecutor" | wc -l)
      EXECUTOR_COUNT=$(grep -rE 'messages\(\)\.send\(' backend/ --include='*.java' | grep "AssistantSendExecutor" | wc -l)
      if [ "$COUNT" -ne "0" ] || [ "$EXECUTOR_COUNT" -ne "1" ]; then exit 1; fi
  ```
- **Deltas:** novel pattern in this repo — no prior grep-based invariant gate. Planner places it in `gates.yml` alongside the existing `Run backend checks` step.

### 2.24 `@MockitoBean` usage
- **Closest analog (verbatim):** `backend/core/src/test/java/com/zeromail/core/llm/usecases/LlmGatewayByokRoutingTest.java` (Grep showed this is a `@MockitoBean` consumer).
- **Inherited:** Spring Boot 4 / Spring Test 7 ships `@MockitoBean` as the replacement for the deprecated `@MockBean`. Reuse pattern verbatim for mocking `StreamingChatModel` in chat slice tests.

### 2.25 Testcontainers Postgres base
- **Closest analog:** `backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java` (entire file, 78 lines).
- **Inherited:** singleton container pattern, `@DynamicPropertySource` with all the Spring AI placeholder keys already set. Phase 7 integration tests for chat persistence (`ChatMessagePersistenceTest`, `AssistantSendAuditConcurrencyTest`) extend this base verbatim.
- **Deltas:** if testing the `chat_message_body_ban` Postgres trigger end-to-end, the test must extend `PostgresContainerTest` (real Postgres needed; H2 / in-memory wouldn't fire the trigger). Add `assertThatThrownBy(() -> entityManager.persist(...))` to verify trigger raises.

---

## 3. Existing Invariant Tests to Update in Same PR

| Test | File | Phase 7 Change |
|------|------|----------------|
| `NoGmailSendAllowedTest` | `backend/core/src/test/java/com/zeromail/core/arch/NoGmailSendAllowedTest.java` | Add early-return for classes annotated `@AllowedSendCallSite`; flip `.allowEmptyShould(true)` → `.allowEmptyShould(false)`. **Must land in same commit** as `OnlyOneGmailSendCallSiteTest`. |
| `ZeroMailApiApplicationModulesTest` | `backend/api/src/test/java/com/zeromail/api/ZeroMailApiApplicationModulesTest.java` | No code change — but adding `core.chat/package-info.java` MUST keep this test green. Run it locally before commit. |
| `TenantIsolationArchTests` | `backend/core/src/test/java/com/zeromail/core/arch/TenantIsolationArchTests.java` | No change — but the new `TenantAwareReactorScheduler` must NOT violate the `no_threadlocal` rule (use `ScopedValue`, never `ThreadLocal`). |
| `LlmGatewayBoundaryTest` | `backend/core/src/test/java/com/zeromail/core/arch/LlmGatewayBoundaryTest.java` | Read first — it likely enforces that `org.springframework.ai.*` imports are confined to `core.llm.gateway.springai`. Phase 7 either extends this rule to add `core.chat.llm.springai` to the allow-list, or ships a sibling `ChatLlmAdapterBoundaryTest`. |
| `master changelog include` | `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` | Append 6 `include:` entries for 041–046 atomically with each changelog file landing. |

---

## 4. Liquibase Numbering Audit

| Number | Status | Phase 7 Assignment |
|--------|--------|-------------------|
| 001–040 | Used (latest = `040-triage-audit-message-ref.yaml`) | n/a |
| **041** | **FREE** | `041-chat.yaml` — conversation aggregate |
| **042** | **FREE** | `042-chat-message.yaml` + `chat_message_body_ban` trigger |
| **043** | **FREE** | `043-assistant-pending-action.yaml` |
| **044** | **FREE** | `044-assistant-send-audit.yaml` (UNIQUE `(chat_id, tool_call_id)`) |
| **045** | **FREE** | `045-assistant-settings.yaml` (personalization NULL defaults) |
| **046** | **FREE** | `046-assistant-memory-knowledge.yaml` |

**No conflicts.** Master changelog last include is `040-triage-audit-message-ref.yaml` at line 119-121 of `db.changelog-master.yaml`; Phase 7 appends 6 sequential entries.

---

## 5. shadcn/ui + AI Elements Vendoring Audit

| Primitive | Status | Action |
|-----------|--------|--------|
| **shadcn/ui** | | |
| Card, Button, Dialog, Sheet, Sidebar, Input, Textarea, Checkbox, Tooltip, ScrollArea, Skeleton, Sonner, Separator, DropdownMenu, Badge, Alert, Popover | Already at `apps/web/components/ui/*` | Use directly. |
| HoverCard (if preview uses it) | Not vendored | Install only if UI-SPEC requires — `pnpm dlx shadcn@latest add hover-card` from `apps/web`. |
| **AI Elements** (all NEW) | | |
| conversation, message, prompt-input, response, tool, reasoning, loader, suggestion, confirmation | **None vendored** at `apps/web/components/ai/*` | Install via `pnpm dlx ai-elements@latest add conversation message prompt-input response tool reasoning loader suggestion confirmation` from `apps/web`. Add `components/ai/**` to ESLint + Prettier ignore globs (per D-09). |

`apps/web/features/ai/` is **unrelated** — it's the v1.0 BYOK provider settings page. Do not confuse with `apps/web/components/ai/*` AI Elements vendoring target.

---

## 6. Anti-Patterns Observed (do NOT repeat)

| Anti-Pattern | Where Seen | Why Not |
|--------------|------------|---------|
| Bare `ChatModel.call(prompt)` without `internalToolExecutionEnabled(false)` | None — v1.0 already sets this flag at line 66 of `SpringAiLlmModelClient.java`. | Without the flag, Spring AI auto-executes tool callbacks; HITL confirmation never renders. Phase 7 chat MUST set this same flag. |
| Logging raw email body / prompts | None in current code (privacy logging audits clean). | Privacy invariant; chat must continue this clean record. |
| `ThreadLocal` for tenant | Banned by `TenantIsolationArchTests.no_threadlocal`. | `ScopedValue` is the v1.0 standard; chat reuses it. |
| `EntityManager.createNativeQuery(...)` outside `..persistence.lowlevel..` | Banned by `TenantIsolationArchTests.no_native_sql`. | Discriminator tenancy doesn't auto-apply to native SQL; chat avoids native SQL except for the Liquibase trigger (which runs at DB level, not Java). |
| Reactor `Schedulers.boundedElastic()` in chat path | Will be banned by Phase 7 ArchUnit (no prior occurrence in repo, but Spring AI's reactive `.stream()` returns on `parallel` scheduler by default — must override with `TenantAwareReactorScheduler`). | Loses `TenantContext` ScopedValue binding across Reactor task boundaries. |
| Hand-rolled UI primitives | Banned by `apps/web/AGENTS.md` shadcn rule. | Phase 7 uses shadcn + AI Elements only. |

---

## 7. Files the Planner MUST Read Before Generating Tasks

1. **`backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/SpringAiLlmModelClient.java`** (120 lines) — Spring AI adapter shape + `internalToolExecutionEnabled(false)` proven pattern.
2. **`backend/core/src/main/java/com/zeromail/core/llm/usecases/LlmGateway.java`** (82 lines) — Gateway interface Javadoc + privacy invariant declaration.
3. **`backend/core/src/main/java/com/zeromail/core/tenant/concurrency/TenantAwareTaskScope.java`** (37 lines) — ScopedValue rebind idiom for fan-out.
4. **`backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java`** (283 lines) — JPA + JSONB + same-tx audit + `@PrePersist` validation; exact analog for `AssistantSendAuditEntity`.
5. **`backend/core/src/test/java/com/zeromail/core/arch/NoGmailSendAllowedTest.java`** (68 lines) — Phase 7 flips this file.
6. **`backend/core/src/test/java/com/zeromail/core/arch/TriageGmailWriteBoundaryTest.java`** (71 lines) — Single-caller boundary pattern for `OnlyOneGmailSendCallSiteTest`.
7. **`backend/core/src/test/java/com/zeromail/core/arch/LlmRepositoryContentBanTest.java`** (73 lines) — Content-ban regex shape for `ChatPersistenceContentBanTest`.
8. **`backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml`** (80+ lines) — Liquibase changelog rationale + JSONB column style.
9. **`backend/core/src/main/java/com/zeromail/core/shared/lock/RedisDistributedLock.java`** (121 lines) — Redis lease primitive shape for confirmation state machine.
10. **`apps/web/features/rules/{api/rules-api.ts,query-keys.ts,hooks/use-rules.ts,messages.ts}`** — Feature folder layout, openapi-fetch usage, TanStack Query key factory, co-located i18n.

---

## PATTERNS COMPLETE

- **Backend coverage:** every Phase 7 Java capability has a direct analog except SSE (Reactor scheduler is partial — `TenantAwareTaskScope` covers the propagation idea, not the Reactor type).
- **Frontend coverage:** feature-folder shape + i18n + shadcn primitives all inherit from `apps/web/features/rules/`; AI Elements vendoring is new but follows the shadcn vendoring precedent verbatim.
- **Invariant tests:** `NoGmailSendAllowedTest` is the single most fragile co-change file — it MUST flip in the same commit as `AssistantSendExecutor` and `OnlyOneGmailSendCallSiteTest`.
- **Liquibase 041–046 uncontested;** master changelog appends 6 entries at line 122+.
- **No anti-patterns to unwind** — v1.0 code is consistent with all Phase 7 invariants (no ThreadLocal, no native SQL outside lowlevel, no Gmail send call sites today).
