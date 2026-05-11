# Phase 4: Triage Convergence (Hero) - Research

**Researched:** 2026-05-11
**Domain:** Per-message Gmail triage orchestration on Java 25 / Spring Boot 4 — Spring Modulith event-driven worker pipeline, idempotent two-phase Gmail-write loop, immutable audit + undo, shadow mode, sender safety net.
**Confidence:** HIGH for in-repo patterns and locked decisions; MEDIUM for Spring Modulith 2.x events API specifics (snapshot pin, schema-init property name) and Spring AI M5 structured-output behavior; LOW for nothing load-bearing.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**A. Event publication + consumer mechanics (`MailMessageObserved`)**
- **D-A1:** Spring Modulith JDBC Event Publication Registry is the event spine. Add `spring-modulith-starter-jdbc` (Spring Modulith 2.x — verify exact pin during research; project currently pins `springModulith = "2.0.7-SNAPSHOT"`). Registry persists the event row in the SAME transaction as the `mail_message_observed` insert. Plain `ApplicationEventPublisher.publishEvent(...)` + `@Async @TransactionalEventListener(AFTER_COMMIT)` is REJECTED — it silently drops events on listener failure / JVM crash (CLAUDE.md Convention #6 endorses Modulith here verbatim).
- **D-A2:** `record MailMessageObserved(UUID tenantId, String gmailMessageId, String gmailThreadId, Instant observedAt)` lives in `com.zeromail.core.gmail.event` (new `event/` sub-package — integration events, not aggregate state, so NOT under `domain/`). Privacy invariant: NO subject / snippet / body / sender display name in payload. Only stable IDs + timestamp.
- **D-A3:** Publish site is `GmailDeliveryProcessingService.processDelivery(...)` (already `@Transactional`) — inject `ApplicationEventPublisher`, call `publishEvent(new MailMessageObserved(...))` after each NEW `MailMessageObservedEntity` row returned by `insertObservedIfAbsent`. Duplicate deliveries absorbed by Phase 2A idempotency (no new row) do NOT publish.
- **D-A4:** Consumer is new `TriageOrchestratorService` in `com.zeromail.core.triage.application`, annotated `@ApplicationModuleListener` (`@Async + @Transactional(propagation = REQUIRES_NEW) + @TransactionalEventListener(phase = AFTER_COMMIT)`). Runs on a virtual thread (`spring.threads.virtual.enabled=true`). Exceptions captured into `FailedEventPublications`, NOT swallowed. Tenant context rebound at listener entry via `ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(...)` before any tenant-scoped repo / `LlmGateway` call.
- **D-A5:** Retry + cleanup via existing ShedLock infra. Retry job: `@Scheduled(fixedDelay = "PT2M")` resubmits incomplete publications older than 5 min; ShedLock `triageEventRetry`, `lockAtLeastFor = "PT30S"`, `lockAtMostFor = "PT5M"`. Cleanup job: `@Scheduled(cron = "0 0 3 * * *")` deletes completed publications older than 7 days; ShedLock `triageEventCleanup`. Both live in `backend/worker` under `com.zeromail.worker.triage` (sibling of `com.zeromail.worker.billing`).
- **D-A6:** Liquibase changelog ships the `event_publication` schema explicitly (`024-modulith-event-publication.yaml`; floor is `024`, last committed is `023-fix-pin-calendar-category.yaml`). Auto-init via `spring.modulith.events.jdbc(.|-)schema-initialization.enabled` is REJECTED for production.

**B. `triage_audit` JSON schema + undo state capture**
- **D-B1:** New sealed `TriageActionResult` in `com.zeromail.core.triage.domain`: `sealed interface TriageActionResult permits Label, Archive, SaveDraft`; `record Label(String labelId, String labelName)`; `record Archive()`; `record SaveDraft(String instruction, String draftId, String threadId)`. Reuse of `core.rules.domain.ActionIntent` is REJECTED — triage_audit must round-trip Gmail response state (`draftId` for `drafts.delete` on undo; resolved `labelId` because users rename labels). Jackson polymorphic `@JsonTypeInfo` is REJECTED — diverges from the manual-validator pattern (`ActionIntentJsonValidator`).
- **D-B2:** `TriageAuditEntity.actionArgsJson` is `String` typed with `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"` (same wiring as `RuleEntity.actionIntents`). Jackson 3 `tools.jackson.databind.JsonMapper` for read/write; annotations stay in `com.fasterxml.jackson.annotation.*`. Yasson JSON-B (locked from 02A-01) covers Hibernate-layer JSONB runtime mapping.
- **D-B3:** Validation = two layers, NO DB CHECK. Layer 1: `TriageActionResultJsonValidator` in `core.triage.domain`, mirrors `ActionIntentJsonValidator` — rejects unknown discriminators (`NoSuchElementException`), rejects unknown fields per type on write, runs from `@PrePersist` (or entity constructor). Layer 2: exhaustive `switch` on `TriageActionResult` inside `TriageUndoService.computeInverse(...)`. DB-level `jsonb_path_match` CHECK is REJECTED.
- **D-B4:** `gmail_change_token` is a top-level JSONB column on `triage_audit` (NOT nested in `action_args_json`). Stores only WHAT WE CHANGED: `APPLY_LABEL` → `{"addedLabelId": "Label_123"}`; `ARCHIVE_SKIP_INBOX` → `{"removedLabelIds": ["INBOX"]}`; `SAVE_DRAFT` → `null`; `SHADOW_LOGGED` → `null`. Semantics: "undo OUR action" — labels the user added in Gmail during the 30-day window stay.
- **D-B5:** Per-action payload contract. `APPLY_LABEL`: `{"type":"label","labelId":"...","labelName":"..."}` — labelId load-bearing for inverse, labelName read-only display. `ARCHIVE_SKIP_INBOX`: `{"type":"archive"}` — empty body; `gmail_change_token` carries the removed INBOX token. `SAVE_DRAFT`: `{"type":"save_draft","instruction":"...","draftId":"...","threadId":"..."}` — `instruction` is the rule's static draft instruction (already on the rule per SPEC §req 4; NOT LLM output); `draftId` required for `drafts.delete` on undo. NO drafted body text is stored.
- **D-B6:** Forward-compat: strict-write (validator rejects unknown fields) / lenient-read (Jackson 3 `FAIL_ON_UNKNOWN_PROPERTIES=false` default). New `RuleActionType` values not present in current `TriageActionResult` cause `TriageUndoService.computeInverse` to throw `TriageAuditException.unsupportedActionType` → HTTP 409 `code=TRIAGE_UNDO_UNSUPPORTED_ACTION` (fail-loud, not silent no-op).

**C. Idempotency: unique-index design + write-order strategy**
- **D-C1:** Unique-key shape = `(tenant_id, gmail_message_id, rule_id, action_type, args_hash)`. `args_hash` is a new `BYTEA NOT NULL` column = `SHA-256(canonicalJson(actionArgs))`. The SPEC-suggested `(tenant, message, rule, action_type)` shape is REJECTED — it collapses a rule producing multiple `APPLY_LABEL` actions with different label names. (Considered alternative D — UUIDv5 PK over the same canonical bytes — preserved as a planner option.)
- **D-C2:** Canonical-JSON serializer is shared infrastructure: `TriageActionArgsCanonicalizer` in `core.triage.domain` — sort object keys lexicographically, normalize whitespace, force UTF-8. Hash output = 32 raw SHA-256 bytes (NOT hex).
- **D-C3:** Write-order = two-phase PENDING → APPLIED. Orchestrator: (1) compute `args_hash`; (2) native `@Modifying @Query` `insertAuditPendingIfAbsent(...) RETURNING audit_id` (mirrors Phase 2A `MailMessageObservedRepository.insertObservedIfAbsent`); (3) empty `RETURNING` → row exists → skip Gmail call; (4) new `audit_id` → call Gmail via `TriageGmailWriter`; (5) `UPDATE triage_audit SET applied_at = NOW(), decision = 'APPLIED', external_ref = :gmailDraftIdOrNull WHERE audit_id = :reservedId`; (6) on Gmail exception: `UPDATE … SET decision = 'FAILED', failure_reason = :opaqueClassName WHERE audit_id = :reservedId`. One-shot `INSERT ON CONFLICT DO NOTHING RETURNING *` (strategy Y) REJECTED — `users.drafts.create` is NON-idempotent and post-insert label/archive failure becomes an orphan. Gmail-first-then-INSERT (strategy W) REJECTED — duplicate visible drafts on `SAVE_DRAFT` retry.
- **D-C4:** `decision` enum membership = `PENDING / APPLIED / SHADOW_LOGGED / REJECTED_BY_SAFETY_NET / REJECTED_BY_SAFETY_POLICY / FAILED / REVERTED` (extends SPEC's list with `PENDING` and `FAILED`). The ArchUnit "no UPDATE/DELETE outside revert" rule must allow the `PENDING → APPLIED / FAILED` transition (whitelist by audit-id + status WHERE clause).
- **D-C5:** Stuck-`PENDING` reaper: `@Scheduled(fixedDelay = "PT5M")` worker in `com.zeromail.worker.triage` — rows with `decision='PENDING'` older than 2 min are inspected; if the Gmail call is verifiable as "succeeded" via a metadata fetch on `gmailMessageId`, flip to `APPLIED`, else `FAILED`. ShedLock `triagePendingReaper`. May be deferred to a follow-up plan if the planner judges the failure window narrow; but `PENDING` rows must NEVER live forever.

**D. `SEMANTIC_INTENT` resolution scope (orchestrator → LlmGateway)**
- **D-D1:** Default batching scope = ONE LLM call per message (per-message all-rules batched). All semantic-intent nodes across all matched rules go into a SINGLE prompt; model returns `Map<nodeId, boolean>`. (Advisor recommended per-rule (option B); user chose option C explicitly, accepting wider injection blast radius for lowest latency + cleanest credit accounting.)
- **D-D2:** Fallback = per-rule batching when token budget exceeded. Orchestrator measures `(sanitized email content tokens + sum of semantic-node intent tokens + tool-schema overhead)` against the 3896-token cap BEFORE the LLM call. If over budget: fan out one LLM call per rule (option B) using virtual-thread parallelism (`CompletableFuture.allOf` joined in the orchestrator). Document the threshold in `TriageOrchestratorService` Javadoc.
- **D-D3:** Credit accounting = 1 reserve per LLM call. Per-message batched (default): 1 `CreditLedger.reserve(tenantId, CallSite.TRIAGE_PLATFORM_LLM)` per message. Per-rule fallback: N reserves (N = rules with semantic intents). Pure-deterministic messages (no semantic nodes): 1 `CreditLedger.reserve(tenantId, CallSite.TRIAGE_DETERMINISTIC)` with a near-zero configurable unit (`zero-mail.billing.cost.triage-deterministic` defaults to 0 credits; set in a Phase 2B `BillingProperties` extension — exact default planner-picked). BYOK paths bypass credits (Phase 2C convention).
- **D-D4:** `LlmGateway` extension — add `Map<String, Boolean> evaluateSemanticIntents(CallSite callSite, String sanitizedMessageContent, List<SemanticIntentRequest> intents)` where `SemanticIntentRequest = record(String nodeId, String intent)`. Impl stays inside `core.llm.gateway.springai` behind the existing ArchUnit boundary. Tool-call schema returns structured `{"nodeMatches": [{"nodeId":"...","matches": true|false}, ...]}` validated against the supplied node-id list (any returned id not in the input list → `SafetyViolationException`). The existing `chat(...)` / `compileRule(...)` methods stay untouched.
- **D-D5:** Failure semantics on LLM error. `LlmGateway` already retries once internally. If it still fails: orchestrator catches and marks ALL semantic nodes for this message as `DEFERRED-(error)` with the opaque error class in the per-node audit field. Action-gating treats `DEFERRED-(error)` as `NOT_MATCHED`. Deterministic-only actions for that message still fire. NO new top-level `decision=FAILED` from semantic failures alone.
- **D-D6:** Timeout policy. `LlmGateway` hard timeout = 7s for the semantic-eval call (leaves 3s for orchestrator + Gmail within the 10s p95 budget). Per-rule fallback path: each parallel call gets 6s. Exact values planner-tunable; budget math is locked.

**E. Operational mechanics (Claude's discretion — planner picks exact values within these constraints)**
- **D-E1:** `CallSite` enum extension is additive — add `TRIAGE_PLATFORM_LLM` and `TRIAGE_DETERMINISTIC` to `core.billing.domain.CallSite`. Phase 2B ArchUnit rule that locks `CallSite` membership must be updated to allow the new values; no other call sites added.
- **D-E2:** Audit retention purge job: `@Scheduled(cron = "0 0 4 * * *")` daily worker `com.zeromail.worker.triage.TriageAuditPurgeJob` — deletes rows where `applied_at < now() - 30 days` AND `decision IN ('APPLIED','REVERTED','SHADOW_LOGGED','REJECTED_BY_SAFETY_NET','REJECTED_BY_SAFETY_POLICY','FAILED')`. Bounded delete `LIMIT 1000` per tick + repeat-until-zero. ShedLock `triageAuditPurge`, `lockAtLeastFor = "PT1M"`, `lockAtMostFor = "PT30M"`.
- **D-E3:** Sender-cache invalidation on opt-in: Redis key `triage:sender-protect:{tenantId}:{lower(senderEmail)}` (24h TTL). On `POST /api/triage/sender-safety-net/{senderEmail}/opt-in`: `redisTemplate.delete(key)` in the SAME service method that inserts the `tenant_sender_opt_in` row, AFTER DB commit succeeds (`TransactionSynchronization.afterCommit`). Cache stores only the boolean `protected` flag (NOT the sent count).
- **D-E4:** ShedLock lock-key convention: `<domain><Purpose>` camelCase — `triageEventRetry`, `triageEventCleanup`, `triageAuditPurge`, `triagePendingReaper`. Tighter `lockAtLeastFor` than the schedule interval guards immediate re-entry.
- **D-E5:** Liquibase changelog allocation (floor `024`): `024-modulith-event-publication.yaml`; `025-triage-audit.yaml`; `026-tenants-triage-shadow-mode.yaml`; `027-tenant-sender-opt-in.yaml`. Exact slot numbers not load-bearing; planner may interleave/merge; additions additive (no destructive migrations).

### Claude's Discretion
- Exact API DTO field names for new endpoints (`POST /api/triage/audit/{auditId}/undo`, `PATCH /api/tenant/triage/shadow-mode`, `GET /api/triage/sender-safety-net`, `POST /api/triage/sender-safety-net/{senderEmail}/opt-in`) — planner/executor picks within the record-DTO convention.
- Exact i18n message-key spelling for new error codes (`TRIAGE_UNDO_EXPIRED`, `TRIAGE_UNDO_ALREADY_DONE`, `TRIAGE_UNDO_UNSUPPORTED_ACTION`, `TRIAGE_SAFETY_VIOLATION`, etc.) — copywriter pass at plan-phase; vi + en parity must pass `pnpm i18n:check`.
- Exact `default` cost for `CallSite.TRIAGE_DETERMINISTIC` in `BillingProperties` — planner picks (recommend `0` credits in v1; configurable).
- Exact Spring Modulith `allowedDependencies` literal list for `core.triage` package-info: minimally `{rules, gmail, llm, billing, tenant, shared.persistence, shared.lang}` plus any `shared.crypto` edge if `RefreshTokenCipher` access is needed.
- `TriageOrchestratorService` package placement: `application/` per CLAUDE.md Convention #2 — but planner may judge `application/` vs `service/`. Recommendation: `application/` with a thin `service/`-side adapter if Spring AOT detection forces it.
- Whether `RefreshTokenCipher` needs a fresh `allowedDependencies` edge from `core.triage`. Recommend NO direct edge from `core.triage` to `core.gmail.persistence.crypto` — `TriageGmailWriter` flows through `GmailApiClientFactory`, which encapsulates the dependency.

### Deferred Ideas (OUT OF SCOPE)
- Cross-message batched LLM evaluation for `SEMANTIC_INTENT` (process N messages in one call) — v2 cost optimization.
- Adaptive token-budget strategy beyond "fall back to per-rule when over 3896 tokens".
- Aggregate-rooted `@DomainEvents` on `MailMessageObservedEntity` — requires restructuring Phase 2A's UPSERT path.
- Single-column `audit_id` PK as UUIDv5 (D-C1 option D) — planner-time alternative, not the default.
- DB-level `jsonb_path_match` CHECK constraint on `triage_audit.action_args_json`.
- Production alerting for SLO breach (>30 min p95 over 10s) — Phase 6 hardening item.
- Sender-safety-net "remove opt-in" endpoint — opt-in is one-way in v1; v2 can add opt-out + cache invalidation symmetry.
- CASA verification tier resolution — external parallel track from Phase 1.
- Refresh-token-style key rotation drill for new secrets — covered by the STATE.md Blockers umbrella.
- Per-tenant LLM cost cap independent of `CreditLedger` — out of v1 (Phase 2C SPEC).
- Audit log UI, undo button, shadow-mode toggle UI, sender-safety-net management UI — all Phase 5.
- AI-drafted reply content generation — Phase 5 `DRFT-01..DRFT-04`.
- Re-running triage against historical messages (backfill).
- Persistent training of sender heuristic via embeddings — privacy constraint forbids embeddings.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description (from REQUIREMENTS.md + 04-SPEC.md) | Research Support |
|----|-------------------------------------------------|------------------|
| TRG-01 | Triage orchestrator: runs once per observed message inside `backend/worker`; evaluates matchers in `display_order`; builds `RuleEvaluationInput` from Gmail metadata-only fetch; produces merged `ActionProposal` list. p95 ≤ 10s. | Spring Modulith JDBC event registry (D-A1..A6); reuse `RuleRepository.findOrderedByTenantId`, `RuleEvaluator`, `ActionProposalMerger.evaluateAndMerge`, `GmailPreviewReadService` metadata-fetch pattern; `RuleEntity.getMatcherAst()` + `getActionIntents()` JSON parse path. Latency budget breakdown in §Cost and Latency. |
| TRG-02 | Safety policy layer: rejects any action outside the allow-list (`{LABEL, ARCHIVE_SKIP_INBOX, SAVE_DRAFT}`) before any Gmail write; logs `event=triage_safety_violation tenantId={} rule_id={} action_type={}`; `SafetyViolationException`. ArchUnit: only `TriageGmailWriter` calls Gmail write APIs from triage code. | New `TriageSafetyPolicy` in `core.triage.service`; `RuleActionType` enum is the allow-list source of truth (3 values today). Pattern mirror: `ActionValidator` (Phase 2C). New ArchUnit `TriageGmailWriteBoundaryTest`. Note: existing `SafetyViolationException` in `core.llm.exception` has only a no-arg constructor — Phase 4 needs its own triage exception type if it must carry an opaque action-type code, or reuse the no-arg one and log separately. |
| TRG-03 | Auto-send blocked at the gateway layer: no code path in `backend/core` or `backend/worker` can send mail on the user's behalf. ArchUnit `NoGmailSendAllowedTest`; grep for `users.messages().send` / `users.drafts().send` returns zero matches; `TriageGmailWriter` only exposes `applyLabel` / `archiveSkipInbox` / `saveDraft`. | New ArchUnit `NoGmailSendAllowedTest` (no class calls `Gmail.Users.Messages.send` / `Gmail.Users.Drafts.send`). `RuleActionType` has no `SEND` value — assert it stays that way. |
| TRG-04 | Allow-listed Gmail writes execute exactly once per accepted action: `TriageGmailWriter` does `users.messages.modify` (label add/remove), `users.messages.modify` with `removeLabelIds=["INBOX"]` (archive), `users.drafts.create` (save-draft); per-message+per-action idempotency check against `triage_audit`; `CreditLedger.reserve → execute → settle/release`; structured logging no content. | Two-phase PENDING → APPLIED (D-C3); native upsert mirror of `MailMessageObservedRepository.insertObservedIfAbsent`; `GmailApiClientFactory.buildGmailClient(accessToken)` + `refreshAccessToken(...)` reuse; `RefreshTokenCipher.decrypt(...)`. Gmail API idempotency facts in §Don't Hand-Roll + §Common Pitfalls. |
| TRG-05 | Immutable audit entry per applied action: `triage_audit` table (Liquibase ≥ 020 — D-E5 says `025`), columns per SPEC §req 5 + D-B/D-C extensions; tenant-scoped via `@TenantId`; INSERT-only except `PENDING→APPLIED/FAILED` and `APPLIED→REVERTED`. ArchUnit bans `delete*`/`update*` repo methods except the explicit transitions. | `TriageAuditEntity extends AbstractTenantOwnedEntity`; `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition="jsonb"` for `action_args_json` and `gmail_change_token` (RuleEntity precedent); `TriageActionResultJsonValidator` + `TriageActionArgsCanonicalizer`; new ArchUnit `triage_audit_*` rules. |
| TRG-06 | Undo applied actions within 30 days: `POST /api/triage/audit/{auditId}/undo` → `TriageUndoService` checks tenant ownership, `APPLIED` state, `applied_at ≥ now - 30d`, computes inverse Gmail call, executes, flips `decision=REVERTED` + `reverted_at`. Outside window / already-reverted → `409` with `TRIAGE_UNDO_EXPIRED` / `TRIAGE_UNDO_ALREADY_DONE`. Reverted rows stay forever; daily worker purges `applied_at < now - 30d AND decision IN (APPLIED, REVERTED, ...)`. | `computeInverse(...)` exhaustive `switch` on `TriageActionResult`: `Label` → `messages.modify removeLabelIds=[labelId]`; `Archive` → `messages.modify addLabelIds=["INBOX"]`; `SaveDraft` → `drafts.delete(draftId)`. New exception types + `GlobalExceptionHandler` mappings + `ErrorCodes` constants. `TriageAuditPurgeJob` (D-E2). Thin controller per CONVENTIONS #1/#2 under `backend/api/controllers/triage/`. Clock injection for mocked-clock tests (pattern: `GmailPreviewReadService` package-private clock constructor). |
| TRG-07 | Shadow mode: opt-in tenant-wide preview toggle, default OFF. Add `tenants.triage_shadow_mode BOOLEAN NOT NULL DEFAULT FALSE` (Liquibase ≥ 021 — D-E5 `026`). When TRUE: `TriageGmailWriter` skipped; audit row `decision=SHADOW_LOGGED`, `gmail_change_token = NULL`. `PATCH /api/tenant/triage/shadow-mode {"enabled": boolean}`. Orthogonal to `triage_paused` (Phase 2A) — paused stops the orchestrator entirely; shadow stops only the Gmail write step. | Add column + getter/setter to `TenantEntity` (same shape as `triagePaused`); a `TriageTenantController` (or extend an existing tenant-settings controller) under `backend/api/controllers/triage/`. Orchestrator checks `tenant.isTriageShadowMode()` before dispatching to `TriageGmailWriter`. |
| TRG-08 | Sender safety net: a sender is "protected" when the tenant has sent ≥ 3 emails to that address in the trailing 90 days; check reads Gmail SENT metadata only (`format=metadata`, query `in:sent to:<senderEmail> newer_than:90d`, `maxResults=3`, early-exit); per-tenant per-sender results cached in Redis 24h (`triage:sender-protect:{tenantId}:{lower(senderEmail)}`). Protected senders → audit `decision=REJECTED_BY_SAFETY_NET`, skip Gmail writes, surfaced via `GET /api/triage/sender-safety-net`. `POST /api/triage/sender-safety-net/{senderEmail}/opt-in` persists a row in new `tenant_sender_opt_in` (Liquibase — D-E5 `027`) that overrides the heuristic. | New `SenderSafetyNetService` in `core.triage.service`; `tenant_sender_opt_in` entity `extends AbstractTenantOwnedEntity`; Gmail SENT lookup via `gmail.users().messages().list("me").setQ(...).setMaxResults(3L)` then early-exit (no `.get(...)` calls — list returns ids only, count is enough). Redis: project already runs Redis (Spring Data Redis + Lettuce) for cache/session/rate-limit — verify a `RedisTemplate`/`StringRedisTemplate` bean exists or add one. Cache invalidation via `TransactionSynchronization.afterCommit` (D-E3). |
</phase_requirements>

---

## Summary

Phase 4 is the convergence point. It introduces a new Spring Modulith package `core.triage` (consumed in `backend/worker`) that reacts to a Modulith-published `MailMessageObserved` after-commit event, evaluates the tenant's rules in `display_order` using the existing Phase 3 `RuleEvaluator` + `ActionProposalMerger`, resolves any `SEMANTIC_INTENT` matchers inline via a new `LlmGateway.evaluateSemanticIntents(...)` method, gates every proposed action through a `TriageSafetyPolicy` allow-list (label / archive / save-draft only — `SEND` permanently forbidden architecturally), applies allow-listed actions through a single `TriageGmailWriter`, writes one immutable `triage_audit` row per applied/shadow/rejected action with full provenance and a two-phase PENDING → APPLIED write loop for idempotency, supports user undo within 30 days via a REST endpoint, honors a tenant-wide opt-in `triage_shadow_mode` toggle and a sent-history-based `SenderSafetyNetService`, and meets p95 ≤ 10s end-to-end. Phase 4 ships backend + REST only; all UI surfaces (audit log, undo button, shadow toggle, sender-safety-net management) are Phase 5.

The implementation is overwhelmingly **assembly of existing patterns**, not novel infrastructure: the event-registry spine (Spring Modulith JDBC), the idempotent native upsert (`MailMessageObservedRepository.insertObservedIfAbsent`), the ShedLock `@Scheduled` worker shape (`CreditReserveWatchdog`), the `@JdbcTypeCode(SqlTypes.JSON)` JSONB column wiring (`RuleEntity`), the per-domain Modulith package layout (`{application, domain, persistence, service, exception}`), the `@TenantId` tenant-owned entity base class, the thin-controller + service-owned-`@Transactional` convention, the `ScopedValue`-rebind pattern in worker loops (`GmailHistoryProcessor`), and the manual-JSON-validator pattern (`ActionIntentJsonValidator`) all exist in the repo and are explicitly cited as templates in CONTEXT.md.

**Primary recommendation:** Build `core.triage` as a new package-based Modulith module wiring Phase 2A → Phase 3 → Phase 2C, using the Spring Modulith JDBC event-publication registry as the single in-process safety net; copy the idempotent-upsert / ShedLock-worker / JSONB-column / manual-validator patterns verbatim from the named in-repo anchors; verify the exact Spring Modulith snapshot pin and the `event_publication` DDL before authoring `024-modulith-event-publication.yaml`; treat the `LlmGateway.evaluateSemanticIntents(...)` extension as a one-method addition behind the existing ArchUnit boundary per the AI-SPEC's pre-locked structured-output design.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Event publication on new observed message | `backend/core` (`core.gmail`) — inside the existing `@Transactional` delivery path | Postgres `event_publication` table (Modulith JDBC registry) | Atomicity: the event row must commit with the `mail_message_observed` insert; nothing in `backend/api` or `backend/worker` can offer that. |
| Triage orchestration (rules + LLM + proposals) | `backend/worker` (consumer side of `core.triage.application`) | `core.rules` (evaluator/merger), `core.llm` (gateway) | CONVENTIONS #6: `message-observed → triage` is an in-process after-commit side effect; both producer and consumer are inside `backend/worker`. The orchestrator never crosses to `backend/api`. |
| Gmail write execution (label / archive / draft) | `backend/core` (`core.triage.service.TriageGmailWriter`), invoked from worker | `core.gmail.service.GmailApiClientFactory` (auth) | Single class permitted to call Gmail write APIs from triage code (ArchUnit-enforced). Auth-client construction stays in `core.gmail`. |
| Audit persistence + idempotency | `backend/core` (`core.triage.persistence`) | Postgres `triage_audit` table | Tenant-owned aggregate; native upsert for the PENDING row; `@TenantId` filter. |
| Undo (compute inverse + flip decision) | `backend/api` controller → `core.triage.application.TriageUndoService` | `core.triage.service.TriageGmailWriter` (reused for inverse calls) | User-initiated REST action; thin controller + service-owned transaction (CONVENTIONS #1). |
| Shadow-mode toggle | `backend/api` controller → tenant service; column on `core.tenant` | — | Tenant-wide setting; mirrors `triage_paused`. |
| Sender safety net check | `backend/core` (`core.triage.service.SenderSafetyNetService`), invoked from worker; opt-in via `backend/api` | Redis (cache), Gmail SENT-list (read), Postgres `tenant_sender_opt_in` | Read happens inside triage (worker); opt-in write is a user REST action (api). |
| Retry / cleanup / purge / pending-reaper schedulers | `backend/worker` (`com.zeromail.worker.triage`) | ShedLock table (Postgres) | Worker-side `@Scheduled` jobs, ShedLock-coordinated; sibling of `com.zeromail.worker.billing`. |
| Frontend (audit log, undo button, shadow toggle, sender net UI) | `apps/web` — **Phase 5, OUT OF SCOPE** | — | Explicitly deferred. Phase 4 only regenerates `apps/web/lib/api/schema.d.ts` after controllers land. |

---

## Standard Stack

> All "Core" libraries are already on the classpath. Phase 4 adds exactly **one new dependency** (the Modulith JDBC events starter) and **one new Liquibase-managed schema** (`event_publication`).

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.6 | Runtime / DI / web / data | Project-locked. Already in `libs.versions.toml`. `[VERIFIED: gradle/libs.versions.toml]` |
| Spring AI | 2.0.0-M5 | LLM orchestration (`evaluateSemanticIntents` structured output) | Project-locked; AI-SPEC pre-selects this. `[VERIFIED: gradle/libs.versions.toml]` |
| Spring Modulith | `2.0.7-SNAPSHOT` (pinned today; verify before authoring 024) | Module boundary verification + event publication registry | Already used: `zeromail.modulith-conventions` build plugin imports the BOM; all `core.*` packages carry `@ApplicationModule`. `[VERIFIED: buildSrc/src/main/kotlin/zeromail.modulith-conventions.gradle.kts]` |
| Hibernate ORM | 7 (Boot-managed) | JPA aggregates incl. JSONB columns | `RuleEntity` proves `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition="jsonb"`. `[VERIFIED: backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java]` |
| Spring Data JPA + JDBC | Boot-managed | Aggregate repos + native upsert for the PENDING-row insert | `MailMessageObservedRepository.insertObservedIfAbsent` is the template. `[VERIFIED: backend/core/.../gmail/persistence/MailMessageObservedRepository.java]` |
| Liquibase | 5.0.2 (YAML changelogs) | Schema migrations (`024`–`027`) | Project-locked; floor for Phase 4 is `024`. `[VERIFIED: backend/core/src/main/resources/db/changelog/changes/]` |
| PostgreSQL | 17.6 (self-hosted on VPS) | Primary datastore + v1 queue + Modulith event log | Project-locked. `[CITED: CLAUDE.md §Technology Stack]` |
| Yasson (JSON-B) | runtimeOnly (`org.eclipse:yasson`) | Hibernate-layer JSONB runtime mapping under Boot 4 / Jackson 3 | Locked from 02A-01; required because Jackson 2 is banned. `[VERIFIED: backend/core/build.gradle.kts]` |
| Jackson 3 | `tools.jackson.databind.*` core; `com.fasterxml.jackson.annotation.*` annotations | (de)serialize `TriageActionResult` JSON | CLAUDE.md hard rule on the Boot 4 / Jackson 3 namespace split. `[CITED: CLAUDE.md "Hard do not use" list]` |
| ShedLock | 7.7.0 (`shedlock-spring` + `shedlock-provider-jdbc-template`) | Coordinate the new `@Scheduled` worker jobs across nodes | `CreditReserveWatchdog` + `ShedLockConfig` already wired in `backend/worker`. `[VERIFIED: backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java]` |
| Google Gmail API client | `v1-rev20250331-2.0.0` | Gmail write (`messages.modify`, `drafts.create`, `drafts.delete`) + SENT-list read | Already on `backend/core` classpath; `GmailApiClientFactory` + `GmailPreviewReadService` are the existing patterns. `[VERIFIED: gradle/libs.versions.toml + backend/core/.../gmail/service/GmailApiClientFactory.java]` |
| Spring Data Redis + Lettuce | Boot-managed | 24h sender-safety-net cache (NOT a queue) | CLAUDE.md: Redis runs on the VPS for cache/session/rate-limit only. `[CITED: CLAUDE.md §Technology Stack]` |
| Micrometer + OpenTelemetry Java agent | 2.16.0 | `triage.duration` histogram + `triage.semantic_eval.*` metrics | Already deployed per STACK.md; AI-SPEC §7 specifies the metric/tag set. `[CITED: CLAUDE.md §Technology Stack + 04-AI-SPEC.md §7]` |
| jtokkit | 1.1.0 | Token-budget pre-check (3896-token cap) — reused via the Phase 2C sanitization pipeline, NOT re-imported in `core.triage` | `JtokkitTruncateSanitizer` already does the cap; the orchestrator's pre-call estimate goes through the gateway, not jtokkit directly (ArchUnit confines jtokkit to `core.llm.gateway.sanitization`). `[VERIFIED: backend/core/src/test/java/.../arch/LlmGatewayBoundaryTest.java]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-modulith-starter-jdbc` | match the project's `springModulith` pin (`2.0.7-SNAPSHOT` today) | JDBC-backed event publication registry — the durable "event row commits with the observed-message insert" guarantee | Add to `backend/core` (publisher needs the registry on the classpath) AND `backend/worker` (consumer + the `IncompleteEventPublications` / `CompletedEventPublications` beans the retry/cleanup jobs inject). `[CITED: docs.spring.io/spring-modulith/reference/events.html]` `[ASSUMED: A1 — exact artifact id and that the 2.0.7-SNAPSHOT line publishes `spring-modulith-starter-jdbc`; verify on the Spring snapshot repo before authoring 024]` |
| `spring-modulith-events-api` | (transitive via the JDBC starter) | `EventPublicationRegistry`, `IncompleteEventPublications`, `CompletedEventPublications`, `FailedEventPublications` beans | Inject into the worker retry/cleanup schedulers. `[CITED: docs.spring.io/spring-modulith/reference/events.html]` |
| Spring Boot Test + Testcontainers (postgresql) | already on test classpaths | Orchestrator integration tests, mocked-clock undo test, multi-tenant leak regression | Reuse `PostgresContainerTest` base; tests requiring `TenantContext` ScopedValue use `RestClient + @LocalServerPort` per the project decision (MockMvc skips servlet filters). `[VERIFIED: STATE.md Decisions]` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Spring Modulith JDBC event registry | Plain `ApplicationEventPublisher` + `@Async @TransactionalEventListener(AFTER_COMMIT)` | REJECTED by D-A1 — silently drops events on listener failure / JVM crash; contradicts the hero-path trust posture. |
| Spring Modulith JDBC event registry | Postgres-backed outbox table hand-rolled like `pubsub_delivery` | Redundant — the Modulith registry IS the outbox, with retry/cleanup APIs included. Hand-rolling duplicates `IncompleteEventPublications` for no gain. |
| Two-phase PENDING → APPLIED audit write | One-shot `INSERT ON CONFLICT DO NOTHING RETURNING *` (strategy Y) | REJECTED by D-C3 — `users.drafts.create` is NON-idempotent on Google's side; any post-insert Gmail failure on label/archive leaves an orphan row. |
| Two-phase PENDING → APPLIED audit write | Gmail-first then INSERT (strategy W) | REJECTED by D-C3 — creates duplicate visible drafts on every retry of `SAVE_DRAFT`. |
| New sealed `TriageActionResult` | Reuse `core.rules.domain.ActionIntent` | REJECTED by D-B1 — triage_audit must round-trip Gmail response state (`draftId`, resolved `labelId`); polluting `ActionIntent` breaks the rules-domain boundary. |
| Composite unique index `(tenant, message, rule, action_type, args_hash)` | Single-column UUIDv5 PK over the canonical bytes (D-C1 option D) | Preserved as a planner option; composite kept by default because the columns stay directly queryable for support tooling. |
| Modulith-published event | Direct `TriageOrchestratorService` call from `GmailDeliveryProcessingService` | REJECTED — a direct call inside the delivery transaction would either block the delivery loop on Gmail writes + LLM latency or lose the action on a worker crash; the registry decouples and makes the hop durable. |

**Installation (only new artifact):**
```kotlin
// backend/core/build.gradle.kts — add (the publisher needs the registry on the classpath)
implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
// backend/worker/build.gradle.kts — add (consumer + IncompleteEventPublications/CompletedEventPublications beans)
implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
// gradle/libs.versions.toml — add to [libraries] (version comes from the spring-modulith-bom already imported by zeromail.modulith-conventions):
// spring-modulith-starter-jdbc = { module = "org.springframework.modulith:spring-modulith-starter-jdbc" }
```

**Version verification (DO before authoring 024):**
1. Confirm `org.springframework.modulith:spring-modulith-starter-jdbc` resolves at the project's `springModulith` pin (`2.0.7-SNAPSHOT` from the Spring snapshot repo today) — if the line ever re-pins to a milestone/GA, update both `libs.versions.toml` and `zeromail.modulith-conventions.gradle.kts` in lockstep (the toml comment already says this).
2. Capture the exact `event_publication` DDL the starter expects (column names, types, indexes). The Spring Modulith reference ships a canonical schema per database; mirror it byte-for-byte in `024-modulith-event-publication.yaml` rather than guessing.
3. Confirm the schema-init disable property name for the pinned version — the CONTEXT.md writes `spring.modulith.events.jdbc-schema-initialization.enabled` but the current reference docs use `spring.modulith.events.jdbc.schema-initialization.enabled` (dot, not dash, after `jdbc`). Set it to `false` (or omit and rely on it defaulting off) so Liquibase owns the table; verify the actual key for the pinned snapshot.
4. Confirm whether `spring.modulith.events.republish-outstanding-events-on-restart` is desired (republishes incomplete publications on app start) — D-A5 instead uses an explicit `@Scheduled` resubmit job, so likely leave this OFF and rely on the scheduled retry; document the choice.
5. `npm view`-equivalent N/A (JVM); verify via `./gradlew :backend:core:dependencies | grep modulith` after adding the starter.

---

## Architecture Patterns

### System Architecture Diagram

```
                          ┌──────────────────────────────────────────────────────────┐
   Gmail Pub/Sub push ───►│ backend/api : GmailPubSubController (OIDC-verified)        │
                          │  → PubSubIngestionService → INSERT pubsub_delivery         │
                          └──────────────────────────────────────────────────────────┘
                                            │ (existing, Phase 2A)
                                            ▼
   ┌───────────────────────────────────────────────────────────────────────────────────────┐
   │ backend/worker : GmailHistoryProcessor (@Scheduled fixedDelay=1s)                       │
   │   claims pubsub_delivery batch  →  ScopedValue.where(TENANT, …).run( … )                │
   │     → GmailDeliveryProcessingService.processDelivery()  [@Transactional]                │
   │         · gmail.users().history().list(...)  →  for each NEW inbox message:             │
   │             insertObservedIfAbsent(...)  →  if rows==1:                                 │
   │   ┌────────────────────────────────────────────────────────────────────────────────┐   │
   │   │ NEW (Phase 4, D-A3): publisher.publishEvent(new MailMessageObserved(...))        │   │
   │   │   → Spring Modulith JDBC registry writes the event row IN THE SAME TRANSACTION   │   │
   │   └────────────────────────────────────────────────────────────────────────────────┘   │
   └───────────────────────────────────────────────────────────────────────────────────────┘
                                            │ AFTER_COMMIT (async, virtual thread)
                                            ▼
   ┌───────────────────────────────────────────────────────────────────────────────────────┐
   │ core.triage.application.TriageOrchestratorService  @ApplicationModuleListener           │
   │  (consumed in backend/worker)                                                           │
   │   1. ScopedValue.where(TENANT, tenantId).run( … )                                       │
   │   2. if tenant.triagePaused → return (audit nothing; Phase 2A kill-switch)               │
   │   3. metadata fetch  → GmailPreviewReadService-style format=metadata read  → RuleEvalInput│
   │   4. rules = RuleRepository.findOrderedByTenantId(tenantId)  (enabled, display_order)    │
   │      parse RuleEntity.getMatcherAst() / getActionIntents()                               │
   │   5. tri-state eval (RuleEvaluator) — collect DEFERRED semantic nodes                    │
   │   6. if any DEFERRED:                                                                    │
   │        budget = sanitized_email_tokens + Σ intent_tokens + schema_overhead               │
   │        ≤ 3896 → LlmGateway.evaluateSemanticIntents(TRIAGE_PLATFORM_LLM, sanitized, intents)│
   │        > 3896 → per-rule fanout (virtual threads, 6s each)  [D-D2]                        │
   │        LLM failure → all nodes DEFERRED-(error) → treated as NOT_MATCHED  [D-D5]          │
   │      else: CreditLedger.reserve(tenantId, TRIAGE_DETERMINISTIC) (~0 credits)              │
   │   7. ActionProposalMerger.merge(...) → ordered List<ActionProposal>                      │
   │   8. for each proposal:                                                                  │
   │        a. TriageSafetyPolicy.gate(actionType)  — not in {LABEL,ARCHIVE_SKIP_INBOX,        │
   │           SAVE_DRAFT} → audit decision=REJECTED_BY_SAFETY_POLICY; log; skip               │
   │        b. SenderSafetyNetService.isProtected(tenantId, senderEmail)                       │
   │           (Redis 24h cache → miss: gmail in:sent to:<x> newer_than:90d maxResults=3)      │
   │           protected & action is ARCHIVE/SAVE_DRAFT → audit REJECTED_BY_SAFETY_NET; skip   │
   │        c. args_hash = SHA-256(canonicalJson(actionArgs))                                  │
   │        d. insertAuditPendingIfAbsent(...) RETURNING audit_id                              │
   │           empty → already applied → skip Gmail call (idempotent retry)                    │
   │        e. if tenant.triageShadowMode → UPDATE audit SET decision=SHADOW_LOGGED,            │
   │              gmail_change_token=NULL; no Gmail call                                       │
   │           else → TriageGmailWriter.apply(...)  (the ONLY Gmail-write call site)            │
   │              → UPDATE audit SET applied_at=NOW(), decision=APPLIED, external_ref=draftId   │
   │              → on Gmail exception: UPDATE audit SET decision=FAILED, failure_reason=class   │
   └───────────────────────────────────────────────────────────────────────────────────────┘

   backend/api (user-initiated, separate process — NO Spring events cross the api↔worker boundary):
     POST  /api/triage/audit/{auditId}/undo                → TriageUndoService.undo() → computeInverse()
                                                              → TriageGmailWriter inverse call → decision=REVERTED
     PATCH /api/tenant/triage/shadow-mode {"enabled": bool} → flips tenants.triage_shadow_mode
     GET   /api/triage/sender-safety-net                   → list protected senders for the tenant
     POST  /api/triage/sender-safety-net/{email}/opt-in    → INSERT tenant_sender_opt_in; afterCommit → redis.delete(key)

   backend/worker schedulers (com.zeromail.worker.triage, ShedLock-coordinated):
     @Scheduled PT2M  triageEventRetry      → IncompleteEventPublications.resubmit(... older than 5 min)
     @Scheduled 0 0 3 triageEventCleanup    → CompletedEventPublications.deletePublicationsOlderThan(7d)
     @Scheduled 0 0 4 triageAuditPurge      → DELETE triage_audit WHERE applied_at < now-30d AND decision IN (...) LIMIT 1000 (repeat)
     @Scheduled PT5M  triagePendingReaper   → PENDING > 2 min → verify via gmail metadata → APPLIED or FAILED
```

### Recommended Project Structure
```
backend/core/src/main/java/com/zeromail/core/
├── gmail/
│   └── event/
│       ├── MailMessageObserved.java         # NEW: record(UUID, String, String, Instant) — integration event
│       └── package-info.java                # NEW
├── triage/                                   # NEW Modulith package — @ApplicationModule allowedDependencies = {rules, gmail, llm, billing, tenant, shared.persistence, shared.lang}
│   ├── package-info.java                    # @ApplicationModule + JavaDoc
│   ├── application/
│   │   ├── TriageOrchestratorService.java   # @ApplicationModuleListener consumer (runs in backend/worker)
│   │   ├── TriageUndoService.java
│   │   ├── UndoAuditCommand.java            # record DTO
│   │   └── package-info.java
│   ├── domain/
│   │   ├── TriageActionResult.java          # sealed: Label / Archive / SaveDraft
│   │   ├── TriageActionResultJsonValidator.java
│   │   ├── TriageActionArgsCanonicalizer.java
│   │   ├── TriageDecision.java              # enum implements IdentifiedEnum: PENDING/APPLIED/SHADOW_LOGGED/REJECTED_BY_SAFETY_NET/REJECTED_BY_SAFETY_POLICY/FAILED/REVERTED
│   │   └── package-info.java
│   ├── persistence/
│   │   ├── TriageAuditEntity.java           # extends AbstractTenantOwnedEntity; @JdbcTypeCode(SqlTypes.JSON) jsonb columns
│   │   ├── TriageAuditRepository.java       # insertAuditPendingIfAbsent native @Modifying @Query RETURNING; markApplied/markFailed/markReverted
│   │   ├── TenantSenderOptInEntity.java     # extends AbstractTenantOwnedEntity
│   │   ├── TenantSenderOptInRepository.java
│   │   └── package-info.java
│   ├── service/
│   │   ├── TriageGmailWriter.java           # the ONLY class allowed to call Gmail write APIs from triage
│   │   ├── TriageSafetyPolicy.java          # allow-list gate
│   │   ├── SenderSafetyNetService.java      # Gmail SENT metadata-only lookup + Redis cache
│   │   └── package-info.java
│   └── exception/
│       ├── TriageSafetyViolationException.java   # (if a triage-specific carrier is needed beyond core.llm's no-arg one)
│       ├── TriageUndoExpiredException.java
│       ├── TriageUndoAlreadyDoneException.java
│       ├── TriageUndoUnsupportedActionException.java
│       └── package-info.java
└── llm/service/
    ├── LlmGateway.java                       # ADD: evaluateSemanticIntents(CallSite, String, List<SemanticIntentRequest>)
    └── (+ SemanticIntentRequest record in core.llm.application; SemanticIntentEvaluator + SemanticIntentResponse in core.llm.gateway.springai per AI-SPEC §3)

backend/worker/src/main/java/com/zeromail/worker/triage/        # NEW (sibling of worker/billing)
├── TriageEventRetryJob.java        # @Scheduled PT2M, @SchedulerLock("triageEventRetry")
├── TriageEventCleanupJob.java      # @Scheduled cron 0 0 3, @SchedulerLock("triageEventCleanup")
├── TriageAuditPurgeJob.java        # @Scheduled cron 0 0 4, @SchedulerLock("triageAuditPurge")
└── TriagePendingReaperJob.java     # @Scheduled PT5M, @SchedulerLock("triagePendingReaper")  [may be a follow-up plan]

backend/api/src/main/java/com/zeromail/api/
├── controllers/triage/
│   ├── TriageAuditController.java          # POST /api/triage/audit/{auditId}/undo
│   ├── TriageTenantController.java         # PATCH /api/tenant/triage/shadow-mode
│   └── SenderSafetyNetController.java      # GET /api/triage/sender-safety-net ; POST .../{email}/opt-in
├── dto/triage/                             # record DTOs
└── error/ErrorCodes.java                   # ADD: TRIAGE_UNDO_EXPIRED, TRIAGE_UNDO_ALREADY_DONE, TRIAGE_UNDO_UNSUPPORTED_ACTION, TRIAGE_SAFETY_VIOLATION

backend/core/src/main/resources/db/changelog/changes/           # floor 024
├── 024-modulith-event-publication.yaml     # event_publication table (mirror Spring Modulith canonical DDL)
├── 025-triage-audit.yaml                   # triage_audit + args_hash BYTEA + unique index + jsonb cols
├── 026-tenants-triage-shadow-mode.yaml     # tenants.triage_shadow_mode BOOLEAN NOT NULL DEFAULT FALSE
└── 027-tenant-sender-opt-in.yaml           # tenant_sender_opt_in table
# + append all four includes to db.changelog-master.yaml in numbered order

backend/core/src/test/java/com/zeromail/core/arch/             # NEW ArchUnit rules
├── NoGmailSendAllowedTest.java             # no class calls Gmail.Users.Messages.send / Gmail.Users.Drafts.send
├── TriageGmailWriteBoundaryTest.java       # only TriageGmailWriter calls Gmail write APIs from triage
└── (extend) CallSiteEnumMembershipArchTest.java — now expects 5 members incl. TRIAGE_PLATFORM_LLM, TRIAGE_DETERMINISTIC
# + triage_audit repo-method ban (no delete*/update* except markApplied/markFailed/markReverted)
```

### Pattern 1: Spring Modulith JDBC event registry as the in-process safety net
**What:** Publisher calls `ApplicationEventPublisher.publishEvent(event)` inside an existing `@Transactional` method; the Modulith JDBC starter persists the event row in the same transaction; an `@ApplicationModuleListener` consumes it AFTER_COMMIT in its own `REQUIRES_NEW` transaction on a virtual thread; failures land in `FailedEventPublications` instead of being swallowed.
**When to use:** Any in-process after-commit side effect that must not be lost (CONVENTIONS #6 names `message-observed → triage` explicitly).
**Example:**
```java
// Publisher — core.gmail.service.GmailDeliveryProcessingService (already @Transactional)
// Source: docs.spring.io/spring-modulith/reference/events.html ; CONTEXT.md D-A3
private final ApplicationEventPublisher eventPublisher;
// ... inside observeInboxMessages, after insertObservedIfAbsent returns 1:
if (insertedCount == 1) {
  newObservations++;
  eventPublisher.publishEvent(new MailMessageObserved(
      tenantId, gmailMessage.getId(), gmailMessage.getThreadId(), Instant.now()));
}

// Consumer — core.triage.application.TriageOrchestratorService (lives in core, consumed in backend/worker)
// Source: docs.spring.io/spring-modulith/reference/events.html ; CONTEXT.md D-A4
@ApplicationModuleListener   // = @Async + @Transactional(propagation=REQUIRES_NEW) + @TransactionalEventListener
void on(MailMessageObserved event) {
  ScopedValue.where(TenantContext.TENANT, event.tenantId().toString())
      .run(() -> orchestrate(event));   // pattern mirrors GmailHistoryProcessor's worker loop
}
```

### Pattern 2: Idempotent native upsert for the PENDING audit row
**What:** A `@Modifying @Query` native `INSERT ... ON CONFLICT (unique-key) DO NOTHING RETURNING audit_id` — empty result means the action was already applied on a prior run, so the orchestrator skips the Gmail call entirely.
**When to use:** The two-phase PENDING → APPLIED loop (D-C3). Verbatim mirror of `MailMessageObservedRepository.insertObservedIfAbsent`.
**Example:**
```java
// core.triage.persistence.TriageAuditRepository — mirrors gmail/persistence/MailMessageObservedRepository
// Source: backend/core/.../gmail/persistence/MailMessageObservedRepository.java ; CONTEXT.md D-C3
@Modifying
@Query(value = """
    INSERT INTO triage_audit
      (audit_id, tenant_id, gmail_message_id, gmail_thread_id, rule_id, rule_name_snapshot,
       action_type, args_hash, action_args_json, reason, decision, created_at)
    VALUES
      (gen_random_uuid(), :tenantId, :gmailMessageId, :gmailThreadId, :ruleId, :ruleName,
       :actionType, :argsHash, cast(:actionArgsJson as jsonb), :reason, 'PENDING', NOW())
    ON CONFLICT (tenant_id, gmail_message_id, rule_id, action_type, args_hash) DO NOTHING
    RETURNING audit_id
    """, nativeQuery = true)
@Transactional
Optional<UUID> insertAuditPendingIfAbsent(/* @Param ... */);
```
Note `RETURNING` on `ON CONFLICT DO NOTHING` returns an empty result set on conflict — `[CITED: postgresql.org/docs/17/sql-insert.html]`. The repo also needs `markApplied(auditId, externalRef)`, `markFailed(auditId, failureClass)`, `markReverted(auditId, revertedAt)` native `@Modifying` updates each with an `audit_id + decision = '<prior>'` WHERE clause so the ArchUnit "no update outside revert" rule can whitelist exactly these.

### Pattern 3: JSONB column with manual validator (RuleEntity precedent)
**What:** Entity stores `String` typed `@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")`; getter runs a static validator that rejects unknown discriminators / unknown fields; the validator also runs from `@PrePersist` (or the constructor) so no path can persist an invalid row.
**When to use:** `triage_audit.action_args_json` (`TriageActionResult`) and `triage_audit.gmail_change_token`.
**Example:**
```java
// core.triage.persistence.TriageAuditEntity — mirrors rules/persistence/RuleEntity
// Source: backend/core/.../rules/persistence/RuleEntity.java ; CONTEXT.md D-B2/D-B3
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "action_args_json", columnDefinition = "jsonb", nullable = false)
private String actionArgsJson;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "gmail_change_token", columnDefinition = "jsonb")   // nullable: null for SHADOW_LOGGED / SAVE_DRAFT
private String gmailChangeToken;
// getActionArgsJson() runs TriageActionResultJsonValidator before returning; @PrePersist validates on write.
```

### Pattern 4: ShedLock-coordinated `@Scheduled` worker job (CreditReserveWatchdog precedent)
**What:** A `@Component` in `backend/worker` with `@Scheduled(...)` + `@SchedulerLock(name="...", lockAtLeastFor="...", lockAtMostFor="...")`; if the work scans-and-mutates rows, delegate the transactional batch method to a collaborator so Spring's proxy actually fires (self-invocation breaks `@Transactional`).
**When to use:** All four Phase 4 worker jobs (`triageEventRetry`, `triageEventCleanup`, `triageAuditPurge`, `triagePendingReaper`).
**Example:** Copy the `CreditReserveWatchdog` / `CreditReserveWatchdogBatch` split shape verbatim. `[VERIFIED: backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java]`

### Pattern 5: Thin controller + service-owned `@Transactional` (CONVENTIONS #1/#2)
**What:** Controllers under `backend/api/controllers/triage/` translate HTTP DTOs ↔ core command records, never inject repositories; the `@Transactional` boundary lives on the core service (`TriageUndoService`, etc.); response DTOs own `from(...)` mapping; new business exceptions map in `GlobalExceptionHandler` to `ProblemDetail` with a `code` constant from `ErrorCodes`.
**When to use:** All three new controllers. `[VERIFIED: backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java + .../error/ErrorCodes.java]`

### Anti-Patterns to Avoid
- **Putting triage orchestration logic in `backend/api`.** It is a worker-side after-commit side effect. The undo/shadow/opt-in REST endpoints live in `backend/api`; the orchestrator does not.
- **Calling Gmail write APIs from anywhere except `TriageGmailWriter`.** ArchUnit-enforced (`TriageGmailWriteBoundaryTest`). The undo inverse calls also go through `TriageGmailWriter`.
- **Adding `RuleActionType.SEND`** (or any non-allow-listed value) — even for "future use". ArchUnit + the existence assertion forbid it.
- **Storing drafted body text in `triage_audit`.** Privacy invariant — only the rule's static `instruction` string and the returned `draftId` are stored for `SAVE_DRAFT`.
- **Auto-initializing the `event_publication` table via `spring.modulith.events.jdbc(.|-)schema-initialization.enabled=true`.** Liquibase owns every table (D-A6 + CLAUDE.md schema policy).
- **`@Async`/`@TransactionalEventListener` with plain `ApplicationEventPublisher` (no registry).** Loses events on listener failure / JVM crash (D-A1).
- **`future.get(6, SECONDS)` to enforce the per-rule LLM timeout.** Abandons the in-flight HTTP call without releasing the credit reservation — enforce the timeout via the HTTP client read-timeout on the `OpenAiChatModel` options instead (AI-SPEC §4b.2).
- **`ChatClient.stream()` for `evaluateSemanticIntents`.** Partial JSON can't be schema-validated; always `.call()` (AI-SPEC §4b.2).
- **Reading `.entity(T.class)` AND `.chatResponse()` from the same `ChatClient.call()`.** One-shot pipeline — pick `.chatResponse()` and convert the text yourself so token usage is captured for the credit ledger (AI-SPEC §3 pitfall #7).
- **Logging email subject / snippet / sender display name / draft body / prompts / completions.** CLAUDE.md privacy logging format: `event=triage_* tenantId={} gmailMessageId={} ruleId={} actionType={}` + structured ids only. Logback scrub filter + the FND-03/04 ArchUnit log-bans must still pass.
- **Letting `PENDING` audit rows live forever.** The pending reaper (D-C5) is mandatory in some form even if deferred to a follow-up plan.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Durable in-process event hop (observed → triage) | A custom outbox table + poller like `pubsub_delivery` | Spring Modulith JDBC event publication registry (`spring-modulith-starter-jdbc`) | The registry already does same-transaction persistence + `IncompleteEventPublications.resubmit(...)` + `CompletedEventPublications.deletePublicationsOlderThan(...)` + `FailedEventPublications`. Hand-rolling re-implements all of it. `[CITED: docs.spring.io/spring-modulith/reference/events.html]` |
| Idempotent "insert this row once" | `try { save() } catch (DataIntegrityViolationException) { … }` | Native `INSERT ... ON CONFLICT ... DO NOTHING RETURNING` (mirror `insertObservedIfAbsent`) | Catching constraint violations is brittle (driver-/dialect-specific), and the `RETURNING`-empty signal cleanly tells you "skip the Gmail call". STATE.md decision: "Delivery and observed-message idempotency use native INSERT ON CONFLICT, not caught JPA DataIntegrityViolationException paths." `[VERIFIED: STATE.md 02A decisions]` |
| LLM structured-output parsing for `evaluateSemanticIntents` | A hand-written JSON parser + ad-hoc key validation | Spring AI `BeanOutputConverter<SemanticIntentResponse>` (strict JSON Schema) + the node-id set-equality validator | AI-SPEC §3 pre-locks this; strict schema + a manual set-equality check is more reliable than tool-call round-trip for pure classification, and matches the established `ActionValidator` precedent. `[CITED: 04-AI-SPEC.md §3, §4b.1]` |
| Token budget estimation | A custom token counter in `core.triage` | The Phase 2C `SanitizationPipeline` (jtokkit CL100K_BASE, 3896-token cap) — the gateway returns the sanitized+budget-checked content | jtokkit is ArchUnit-confined to `core.llm.gateway.sanitization`; the orchestrator's pre-call estimate is `sanitized.tokenCount() + estimateIntentsTokens(intents) + TOOL_SCHEMA_OVERHEAD` computed inside the gateway impl (AI-SPEC §4 core pattern). `[CITED: 04-AI-SPEC.md §4]` |
| Cross-node scheduler coordination | A DB advisory-lock hack per job | ShedLock (`@SchedulerLock`) — already wired (`ShedLockConfig` + the Liquibase shedlock table) | `CreditReserveWatchdog` proves the shape; the new jobs just register additional lock names. `[VERIFIED: backend/worker/.../billing/ShedLockConfig.java]` |
| Multi-tenant row isolation on the audit table | Manual `WHERE tenant_id = ?` everywhere | `extends AbstractTenantOwnedEntity` (Hibernate 7 `@TenantId` filter) — note native queries DON'T inherit the filter, so the native upsert/markX queries must pass `tenantId` explicitly | The base class hoists `@TenantId @Column("tenant_id")`; the FND-05 leak test proves it survives `@MappedSuperclass`. Native SQL paths must scope `tenant_id` themselves (STATE.md 02A decision). `[VERIFIED: backend/core/.../shared/persistence/AbstractTenantOwnedEntity.java + STATE.md]` |
| Credit reserve/settle/release | A bespoke counter | `CreditLedger.reserve(tenantId, callSite)` → `settle(reservationId)` / `release(reservationId)` | Phase 2B contract; the `CreditReserveWatchdog` is the crash safety net. `core.triage` injects the `CreditLedger` interface only. `[VERIFIED: backend/core/.../billing/service/CreditLedger.java]` |
| Gmail auth client construction | A new OAuth client in `core.triage` | `GmailApiClientFactory.refreshAccessToken(decryptedRefreshToken)` + `buildGmailClient(accessToken.value())` (decrypt via `RefreshTokenCipher`) | Exactly what `GmailDeliveryProcessingService` and `GmailPreviewReadService` already do; no new crypto/edge needed from `core.triage`. `[VERIFIED: backend/core/.../gmail/service/GmailApiClientFactory.java]` |
| Gmail metadata fetch → `RuleEvaluationInput` | A new parser for From/To/Cc/Subject/labels | `GmailPreviewReadService`'s `format=metadata` fetch + header-parsing helpers — lift the helper or expose a triage-friendly facade (e.g., `fetchTriageInput(tenantId, gmailMessageId)`) | The mapping from Gmail `Message` → sanitized sender/domain/recipients/subject-excerpt/labels/categories/`hasAttachment`/`listUnsubscribePresent`/`newsletterIndicatorPresent` already exists and is privacy-correct. `[VERIFIED: backend/core/.../gmail/service/GmailPreviewReadService.java]` |
| Tri-state rule evaluation + proposal merge | Re-implementing matcher logic | `RuleEvaluator.evaluate(matcherNode, input)` (returns `MATCHED/NOT_MATCHED/DEFERRED`) + `ActionProposalMerger.evaluateAndMerge(candidates, input)` (or `merge(orderedProposals, input)`) | Phase 3 code; the orchestrator resolves `DEFERRED` semantic nodes via the LLM, then feeds the resolved set into `ActionProposalMerger`. No behavior change to Phase 3. `[VERIFIED: backend/core/.../rules/service/RuleEvaluator.java + ActionProposalMerger.java]` |
| Polymorphic JSON for `TriageActionResult` | Jackson `@JsonTypeInfo` / `@JsonSubTypes` | A `"type"` discriminator field + a manual `TriageActionResultJsonValidator` (mirror `ActionIntentJsonValidator`) | D-B1 — `@JsonTypeInfo` diverges from the established validator pattern and would fragment the codebase mid M5→GA churn. `[CITED: CONTEXT.md D-B1]` |

**Key insight:** Phase 4 is a wiring phase. Almost every "hard" sub-problem (durable events, idempotent inserts, JSONB columns, multi-tenant isolation, ShedLock jobs, Gmail auth, metadata parsing, tri-state evaluation, credit ledger, structured LLM output) already has a battle-tested in-repo implementation that CONTEXT.md names as the template. The only genuinely new code is the orchestrator glue, the `TriageGmailWriter` write adapter, the `triage_audit` schema/entity, the undo inverse logic, the safety policy gate, the sender-net Redis caching, and the four worker schedulers — plus the one-method `LlmGateway.evaluateSemanticIntents` extension whose internal design is already locked by the AI-SPEC.

---

## Runtime State Inventory

> Phase 4 is **greenfield within the repo** (new package, new tables, new endpoints) — it does NOT rename or migrate any existing string/key/identifier. There is no string-replacement or rebrand surface. The categories below are answered for completeness.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — Phase 4 creates new tables (`event_publication`, `triage_audit`, `tenant_sender_opt_in`) and a new column (`tenants.triage_shadow_mode`); it does not rewrite any existing key/collection/id. Existing `mail_message_observed` rows are read (by the metadata-fetch path) and `tenants.triage_paused` is read (kill-switch) but neither is mutated. | None. (Note: there is no triage backfill — Phase 4 only triages messages observed *after* the orchestrator is deployed, so no migration of historical `mail_message_observed` rows.) |
| Live service config | None — no n8n/Datadog/Tailscale/Cloudflare config carries a Phase 4 string. The only external service touched at runtime is the Gmail API (read + the 3 allow-listed writes) and OpenRouter (via the existing Spring AI adapter). The Spring AI platform model id may be raised from `openai/gpt-4o-mini` → `openai/gpt-5.4-nano` in `backend/worker/src/main/resources/application.yml` per AI-SPEC §2 — that is a config edit in git, not external state. | Update `spring.ai.openai.chat.options.model` in `backend/worker/src/main/resources/application.yml` (AI-SPEC §2/§4). Verify the project's current pin first — STATE.md quick task `260508-g41` touched BYOK/OpenRouter/Spring AI sync, so the file may already differ from the Phase 2C baseline. |
| OS-registered state | None — Phase 4 adds `@Scheduled` jobs that register at app startup (no OS cron, no Task Scheduler, no systemd unit, no pm2 process). ShedLock coordinates them via the existing Postgres `shedlock` table. | None. |
| Secrets/env vars | None new — Phase 4 reuses `ZERO_MAIL_OPENROUTER_API_KEY` (Phase 2C), `REFRESH_TOKEN_KEY_BASE64` (Phase 1.5), the OAuth client id/secret (Phase 1), and the Redis connection config (Phase 2A infra). No new secret key. | None. (If the planner adds a `zero-mail.billing.cost.triage-deterministic` property, that's plain config, not a secret.) |
| Build artifacts / installed packages | One new Gradle dependency (`spring-modulith-starter-jdbc`) added to `backend/core` + `backend/worker` build files and `libs.versions.toml` — no stale egg-info / compiled-binary / global-install problem. The `apps/web/lib/api/schema.d.ts` is regenerated after the new controllers land (downstream codegen, not a build artifact rename). | Run `pnpm generate:api` (or `pnpm generate:api` equivalent) in `apps/web` after Phase 4 controllers are merged so Phase 5 consumes the new endpoints. Run `./gradlew :backend:core:dependencies` to confirm the new starter resolves. |

**The canonical question (after every file in the repo is updated, what runtime systems still have stale state?):** None. Phase 4 introduces new runtime systems (the Modulith event registry, the four schedulers) but does not leave any old string/key/registration behind, because it renames nothing.

---

## Common Pitfalls

### Pitfall 1: `users.drafts.create` is NOT idempotent — naive retry creates duplicate visible drafts
**What goes wrong:** A Pub/Sub re-delivery (or a worker crash between the Gmail call and the audit `UPDATE`) re-runs the orchestrator; if `SAVE_DRAFT` is executed before the audit row is committed, the user gets two identical draft replies in Gmail.
**Why it happens:** Gmail's `users.drafts.create` has no idempotency key; each call creates a new draft. `users.messages.modify` (label add/remove, archive) IS idempotent — adding a label that's already present, or removing INBOX twice, is a no-op.
**How to avoid:** D-C3's two-phase PENDING → APPLIED loop — write the `PENDING` audit row *before* the Gmail call (via the idempotent native upsert); if the upsert conflicts (`RETURNING` empty), the action was already applied → skip the Gmail call. The `PENDING` row plus the stuck-pending reaper (D-C5) bound the crash window.
**Warning signs:** Two `triage_audit` rows with the same `(tenant, message, rule, action_type)` but different `args_hash` is *legitimate* (a rule producing two labels) — but two rows with the same `args_hash` would mean the unique index failed; two drafts in Gmail for one message means the write happened before the audit insert.
**Source:** `[CITED: developers.google.com/workspace/gmail/api/reference/rest/v1/users.drafts/create + users.messages/modify]` `[CITED: CONTEXT.md D-C3]`

### Pitfall 2: Spring Modulith snapshot pin + `event_publication` DDL mismatch
**What goes wrong:** Authoring `024-modulith-event-publication.yaml` from memory of an older Modulith schema → the starter expects a column/index that isn't there → events silently fail to persist, or the app fails to start.
**Why it happens:** The project pins `springModulith = "2.0.7-SNAPSHOT"` (the only Boot-4-compatible line as of the comment in `libs.versions.toml`), not the `2.0.6` GA the reference docs show. The DDL can differ between versions; the schema-init disable property name differs (`...jdbc.schema-initialization.enabled` vs the dash variant CONTEXT.md wrote).
**How to avoid:** Before authoring `024`: (a) add the starter and run the app once with auto-init enabled in a throwaway dev DB, dump the created `event_publication` table DDL, and mirror it exactly in the Liquibase changelog; (b) then disable auto-init and let Liquibase own it; (c) verify the exact disable-property key for the pinned snapshot. The Spring Modulith reference ships a `event-publication.sql` per database — locate the one matching the pinned version on the snapshot artifact.
**Warning signs:** `ApplicationModulesTest` passes but no triage happens; `event_publication` rows accumulate with `completion_date IS NULL` and never get resubmitted; or a Liquibase checksum/DDL error at startup.
**Source:** `[CITED: docs.spring.io/spring-modulith/reference/events.html]` `[VERIFIED: gradle/libs.versions.toml comment]` `[ASSUMED: A2 — the 2.0.7-SNAPSHOT DDL is close enough to 2.0.6 GA; the dev-DB dump approach removes the risk]`

### Pitfall 3: Native SQL bypasses the `@TenantId` Hibernate filter
**What goes wrong:** The native `insertAuditPendingIfAbsent` / `markApplied` / `markFailed` / `markReverted` queries, and the `SenderSafetyNetService` opt-in lookup if done via native SQL, don't get the automatic `tenant_id` predicate — cross-tenant write/read is possible if `tenant_id` isn't passed explicitly.
**Why it happens:** `@TenantId` is a Hibernate-session-level filter applied to HQL/Criteria, not to `nativeQuery = true`.
**How to avoid:** Every native query that touches `triage_audit` / `tenant_sender_opt_in` must include `tenant_id = :tenantId` in its `WHERE` / `VALUES`, exactly as the Phase 2A `MailMessageObservedRepository` and the Phase 2B billing repos do. STATE.md 02A decision: "Explicitly tenant-scope one-argument repository claims because native SQL does not inherit Hibernate `@TenantId` filtering." Keep `MultiTenantLeakIntegrationTest` (FND-05) green after the additions.
**Warning signs:** A query that compiles but takes `auditId` only (no `tenantId`); FND-05 leak test failing.
**Source:** `[VERIFIED: STATE.md 02A decisions + backend/core/.../gmail/persistence/MailMessageObservedRepository.java]`

### Pitfall 4: `ScopedValue` not rebound inside the `@ApplicationModuleListener`
**What goes wrong:** The orchestrator runs on a fresh virtual thread (Modulith's `@Async`); `TenantContext.TENANT` is unbound; the first tenant-scoped repository call or `LlmGateway` call throws `IllegalStateException: No tenant bound on this thread` (or, worse, silently uses an unscoped path).
**Why it happens:** `ScopedValue` bindings are per-thread-execution; they don't propagate across `@Async` thread boundaries. The event payload carries `tenantId`, so the listener must rebind explicitly — exactly like `GmailHistoryProcessor.tick()` does for the delivery loop.
**How to avoid:** First line of the listener: `ScopedValue.where(TenantContext.TENANT, event.tenantId().toString()).run(() -> orchestrate(event));`. (CONTEXT.md D-A4 says `TenantContext.runWith(...)` — there is no such helper in the current `TenantContext`; use `ScopedValue.where(...).run(...)`, or add a `runWith` convenience method to `TenantContext` first.)
**Warning signs:** Orchestrator throws on the first repo call; FND-05 leak test or a new orchestrator integration test failing.
**Source:** `[VERIFIED: backend/core/.../tenant/TenantContext.java + backend/worker/.../GmailHistoryProcessor.java]`

### Pitfall 5: `SafetyViolationException` (core.llm) has only a no-arg constructor — can't carry a triage error code
**What goes wrong:** Phase 4 wants the safety-policy rejection to surface `code=TRIAGE_SAFETY_VIOLATION` and the LLM-side hallucinated-nodeId rejection to surface a distinct signal; but the existing `com.zeromail.core.llm.exception.SafetyViolationException` was deliberately given *only* a no-arg constructor (STATE.md 02C decision) so it can't accidentally carry rejected action names / tool args / model output.
**Why it happens:** That no-arg-only design is itself a privacy guardrail and must not be loosened.
**How to avoid:** Either (a) reuse the no-arg `SafetyViolationException` for the gateway-side hallucinated-nodeId case (it already maps to HTTP 500 in `GlobalExceptionHandler` via `LLM_SAFETY_VIOLATION`) and log the safe details separately, AND introduce a separate `core.triage.exception.TriageSafetyViolationException` for the rule-engine-path allow-list breach if the planner wants a distinct HTTP code/message; or (b) keep a single mapping. Do NOT add a message-carrying constructor to the existing class. The new triage undo exceptions (`TriageUndoExpiredException`, `TriageUndoAlreadyDoneException`, `TriageUndoUnsupportedActionException`) are plain `RuntimeException` subclasses with no content-bearing payload, mapped in `GlobalExceptionHandler` to 409 + `ErrorCodes` constants.
**Warning signs:** A new constructor on `SafetyViolationException`; an ArchUnit log-ban firing on a triage log line.
**Source:** `[VERIFIED: STATE.md 02C decision "SafetyViolationException has only a no-arg constructor" + backend/core/.../llm/exception/SafetyViolationException.java]`

### Pitfall 6: `CreditReserveWatchdog`-style self-invocation breaks `@Transactional` on the purge/reaper jobs
**What goes wrong:** A purge job that does `@Scheduled void tick() { scanAndDelete(); }` where `scanAndDelete()` is `@Transactional` on the same class — the self-call bypasses Spring's proxy, the transaction never starts, and a `FOR UPDATE SKIP LOCKED` batch releases its locks the instant the query returns instead of holding them across the loop.
**Why it happens:** Spring AOP proxies intercept external calls only; `this.method()` is not intercepted.
**How to avoid:** Split exactly like `CreditReserveWatchdog` (scheduler) / `CreditReserveWatchdogBatch` (transactional batch collaborator). The class-level JavaDoc on `CreditReserveWatchdog` explains this verbatim — copy the pattern.
**Warning signs:** Concurrent worker nodes double-processing the same audit rows; a `@Transactional` method on the same class as a `@Scheduled` method.
**Source:** `[VERIFIED: backend/worker/.../billing/CreditReserveWatchdog.java JavaDoc]`

### Pitfall 7: Shadow mode vs `triage_paused` confusion
**What goes wrong:** Treating `triage_shadow_mode=true` as "don't run the orchestrator", or treating `triage_paused=true` as "log but don't write" — both wrong.
**Why it happens:** They sound similar but are orthogonal: `triage_paused` (Phase 2A kill-switch) → the orchestrator does nothing at all, writes no audit row; `triage_shadow_mode` (Phase 4) → the orchestrator runs everything, evaluates rules + LLM + safety policy + sender net, but the Gmail write step is replaced by writing the audit row with `decision=SHADOW_LOGGED` and `gmail_change_token=NULL`.
**How to avoid:** In the orchestrator: `if (tenant.isTriagePaused()) return;` (early, no audit) — then for each proposal, `if (tenant.isTriageShadowMode()) { writeShadowAudit(...); } else { writeApply(...); }`. Both states must be distinguishable in `triage_audit.decision` without inspecting Gmail (SPEC acceptance criterion).
**Warning signs:** A shadow-mode integration test that finds zero audit rows; a paused-mode test that finds `SHADOW_LOGGED` rows.
**Source:** `[VERIFIED: 04-SPEC.md §req 7 + CONTEXT.md]`

### Pitfall 8: LLM failure silently defaulting semantic booleans to `false`
**What goes wrong:** The gateway throws (timeout / 5xx-after-retry / schema violation); a `catch` returns `Map.of()` or fills `false`; semantic-gated actions silently don't fire; the user never learns the rule didn't run.
**Why it happens:** It's the path of least resistance and the test (message stays in inbox) *looks* fine.
**How to avoid:** D-D5 — orchestrator catches `LlmEvaluationFailedException` / `SafetyViolationException`, marks every requested `nodeId` as `DEFERRED-(error)` with the opaque error class in the per-node audit field, action-gating treats `DEFERRED-(error)` as `NOT_MATCHED` (semantic-gated actions don't fire), deterministic-only actions for that message still fire, and the audit row carries the deferred state. NO new top-level `decision=FAILED` from semantic failure alone. AI-SPEC eval Dim 3 is the build-breaking check for this.
**Warning signs:** A `catch` block in the orchestrator that returns booleans; an audit row with `false` for a node the LLM actually failed on.
**Source:** `[CITED: CONTEXT.md D-D5 + 04-AI-SPEC.md §1 failure mode #5, §5 Dim 3]`

---

## Code Examples

> Verified in-repo patterns Phase 4 should mirror. (Full file paths are listed in the `In-code anchors` section of CONTEXT.md.)

### Worker loop rebinding `TenantContext` (the orchestrator listener should mirror this)
```java
// backend/worker/src/main/java/com/zeromail/worker/GmailHistoryProcessor.java — VERIFIED
@Scheduled(fixedDelay = 1_000L)
public void tick() {
  List<PubSubDeliveryEntity> batch = deliveryRepository.claimPendingBatch(BATCH_SIZE, LOCK_SECONDS);
  for (PubSubDeliveryEntity delivery : batch) {
    ScopedValue.where(TenantContext.TENANT, delivery.getTenantId().toString())
        .run(() -> deliveryProcessingService.processDelivery(delivery));
  }
}
// → TriageOrchestratorService.on(MailMessageObserved event) does the same with event.tenantId().
```

### Idempotent native upsert (mirror for `insertAuditPendingIfAbsent`)
```java
// backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java — VERIFIED
@Modifying
@Query(value = """
      INSERT INTO mail_message_observed
        (tenant_id, gmail_message_id, gmail_thread_id, history_id, label_ids, internal_date, observed_at)
      VALUES
        (:tenantId, :gmailMessageId, :gmailThreadId, :historyId, :labelIds, :internalDate, NOW())
      ON CONFLICT (tenant_id, gmail_message_id) DO NOTHING
      """, nativeQuery = true)
@Transactional
int insertObservedIfAbsent(@Param("tenantId") UUID tenantId, /* ... */ @Param("internalDate") Long internalDate);
```

### ShedLock `@Scheduled` worker (mirror for the four triage jobs)
```java
// backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java — VERIFIED
@Scheduled(fixedRate = 60_000L)
@SchedulerLock(name = "creditReserveWatchdog", lockAtLeastFor = "PT30S", lockAtMostFor = "PT2M")
public void scheduledTick() { tick(); }
public void tick() { int released = batch.releaseStaleBatch(STALE_THRESHOLD, BATCH_LIMIT); /* ... */ }
// Note the scheduler/batch split: the @Transactional scan-and-mutate method lives on a collaborator.
```

### JSONB column with manual validator (mirror for `TriageAuditEntity.actionArgsJson`)
```java
// backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java — VERIFIED
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "action_intents", columnDefinition = "jsonb", nullable = false)
private String actionIntents;
public String getActionIntents() { validateActionIntents(actionIntents); return actionIntents; }
private static void validateActionIntents(String json) { ACTION_INTENT_JSON_VALIDATOR.validateActionIntentsJson(json); }
```

### Tri-state evaluation + proposal merge (orchestrator calls these directly)
```java
// backend/core/src/main/java/com/zeromail/core/rules/service/RuleEvaluator.java — VERIFIED
// Returns MATCHED / NOT_MATCHED / DEFERRED; SemanticIntentMatcher → RuleEvaluationResult.deferred(nodeId, "semantic_intent_deferred")
RuleEvaluationResult result = ruleEvaluator.evaluate(matcherNode, ruleEvaluationInput);

// backend/core/src/main/java/com/zeromail/core/rules/service/ActionProposalMerger.java — VERIFIED
ActionProposalMerger.ActionProposalMergeResult merged =
    actionProposalMerger.evaluateAndMerge(ruleActionCandidates, ruleEvaluationInput);
// merged.proposals() = ordered, de-duplicated List<ActionProposal>; merged.warnings() = conflict warnings.
```

### `LlmGateway` extension signature (per AI-SPEC §3/§4 — internal design already locked)
```java
// ADD to backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java
Map<String, Boolean> evaluateSemanticIntents(
    com.zeromail.core.billing.domain.CallSite callSite,   // TRIAGE_PLATFORM_LLM
    String rawMessageContent,                              // gateway sanitizes + budget-checks
    java.util.List<SemanticIntentRequest> intents);       // SemanticIntentRequest = record(String nodeId, String intent)
// Impl in core.llm.gateway.springai (BeanOutputConverter<SemanticIntentResponse> strict JSON Schema +
//   node-id set-equality validator → SafetyViolationException on any mismatch). chat()/compileRule()/driftCheck() untouched.
// Over-budget pre-check throws (e.g.) TokenBudgetExceededException → orchestrator catches → per-rule fanout (D-D2).
```

### Gmail metadata fetch + auth (orchestrator's `RuleEvaluationInput` source)
```java
// backend/core/src/main/java/com/zeromail/core/gmail/service/GmailPreviewReadService.java — VERIFIED (pattern)
// format=metadata, fields=id,threadId,labelIds,internalDate,payload/headers,... ; metadataHeaders=From,To,Cc,Subject,List-Unsubscribe,List-Id,Precedence,Content-Type
// Auth: refreshTokenCipher.decrypt(connection.getRefreshTokenEncrypted(), tenantId.toString())
//       → gmailApiClientFactory.refreshAccessToken(decryptedRefreshToken)
//       → gmailApiClientFactory.buildGmailClient(tokenResult.accessToken().value())
// Recommend: expose a triage-friendly facade method on GmailPreviewReadService (or a new GmailTriageReadService in core.gmail)
//   so core.triage doesn't reach into core.gmail.persistence.crypto directly (keeps the Modulith edge minimal — D-discretion).
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Hand-rolled outbox table + poller (`pubsub_delivery` style) for in-process side effects | Spring Modulith JDBC event publication registry (`@ApplicationModuleListener` + `IncompleteEventPublications`/`CompletedEventPublications`) | Spring Modulith 1.1+ (the registry has been the recommended pattern for years; 2.0 added `FailedEventPublications`) | Phase 4 uses the registry for `observed → triage` — the Pub/Sub ingress outbox stays hand-rolled because it has different semantics (external retry, dead-lettering). `[CITED: docs.spring.io/spring-modulith/reference/events.html]` |
| Spring AI 1.x `OutputParser` / ad-hoc JSON | Spring AI 2.x `BeanOutputConverter<T>` + `ResponseFormat.JSON_SCHEMA` strict mode | Spring AI 1.0 → 2.0 (M-series) | The `evaluateSemanticIntents` impl uses strict JSON Schema, not tool-call round-trip — AI-SPEC §3 locks this. Note Spring AI is still on a milestone (M5) — `BeanOutputConverter` may relocate at GA; keep imports in `core.llm.gateway.springai` (ArchUnit-enforced) so a rename is one-file blast radius. `[CITED: 04-AI-SPEC.md §3]` |
| `gpt-4o-mini` as the platform default (Phase 2C pin) | `openai/gpt-5.4-nano` (AI-SPEC §2) — cheapest OpenAI model with reliable `response_format=json_schema, strict=true`, same OpenAI BPE tokenizer (keeps the 3896-token cap math valid) | Phase 4 (AI-SPEC decision) | Phase 4 implementation updates `spring.ai.openai.chat.options.model` in `backend/worker/src/main/resources/application.yml`. Verify the file's current value first (quick task `260508-g41` may have changed it). `[CITED: 04-AI-SPEC.md §2, §4]` |
| `ordinal()`-based enum persistence | `IdentifiedEnum` + `id()` + fail-loud `fromId` (`NoSuchElementException`) | Phase 1.2.1 | `TriageDecision` implements `IdentifiedEnum`; `RuleActionType` and `CallSite` already do. `[VERIFIED: backend/core/.../shared/lang/ + RuleActionType.java + CallSite.java]` |

**Deprecated/outdated (do not use):**
- `javax.*` packages → Jakarta only.
- Lombok → records + explicit builders.
- `pgp_sym_encrypt` (pgcrypto) for tokens → app-layer AES-GCM (`RefreshTokenCipher`).
- Polling Gmail → Pub/Sub push + `users.watch` (Phase 2A) — Phase 4 reacts to the observed-message event, never polls.
- Stateless JWT user sessions → cookie + Redis-backed Spring Session.
- Embedding store / vector DB → forbidden (privacy invariant) — the sender heuristic is a live Gmail SENT-count query, not embeddings.
- Kafka / RabbitMQ → Postgres `SKIP LOCKED` + (now) the Modulith event registry.
- `spring-cloud-gcp` starters → forbidden (no-GCP-hosting baseline) — Gmail push is a plain HTTP POST controller.

---

## Project Constraints (from CLAUDE.md)

> Treat these with the same authority as locked decisions. The planner must verify every plan against this list.

- **Language/runtime/framework/build:** Java 25; Spring Boot 4.0.6; Gradle 9.x Kotlin DSL; multi-module (`backend/core` package-based Modulith + `backend/api` + `backend/worker` + `apps/web`). Internal backend boundaries are package-based inside `backend/core`, enforced by Spring Modulith verification + ArchUnit. **Phase 4 producer and consumer are both inside `backend/worker`** (CONVENTIONS #6); the orchestrator never crosses to `backend/api`; cross-process api↔worker handoff would need a Postgres-backed table, but Phase 4 has no such handoff (the undo/shadow/opt-in endpoints are independent REST actions, not handoffs).
- **AI:** Spring AI 2.0.0-M5 only; ALL direct Spring AI / vendor SDK usage confined to `core.llm.gateway.springai` (ArchUnit `LlmGatewayBoundaryTest`). `core.triage` calls `LlmGateway` only. `SEMANTIC_INTENT` evaluation MUST go through `LlmGateway.evaluateSemanticIntents(...)`.
- **No GCP hosting baseline:** do not add `spring-cloud-gcp` starters. Gmail push is a Spring MVC controller (already exists).
- **No WebFlux:** Spring MVC + virtual threads (`spring.threads.virtual.enabled=true`) — the orchestrator listener runs on a virtual thread; per-rule fanout uses `CompletableFuture` on a virtual-thread executor, not Reactor.
- **No Lombok:** records for DTOs (`TriageActionResult` and its variants, `UndoAuditCommand`, all API DTOs); classes for entities (`TriageAuditEntity`, `TenantSenderOptInEntity`); explicit builders if needed.
- **Privacy:** no long-term storage of raw email bodies, prompts, completions, or embeddings. `triage_audit.reason` carries matcher node ids + structured evidence ids only — never raw text. No subject/snippet/body/sender-display-name in the `MailMessageObserved` payload, in `triage_audit`, in logs, or in Micrometer tags. Logback scrub filter + FND-03/04 ArchUnit log-bans must still pass. Spring AI prompt/completion observation capture stays disabled (`spring.ai.chat.client.observations.log-prompt=false`, `...log-completion=false`).
- **Write actions in v1:** label / archive (skip inbox) / save Gmail draft only. **Auto-send is FORBIDDEN architecturally** — `TriageGmailWriter` exposes no send method; `RuleActionType` has no `SEND`; ArchUnit `NoGmailSendAllowedTest` + the existence assertion enforce it.
- **Datastore:** PostgreSQL self-hosted on the VPS (primary + v1 queue + Modulith event log). Redis on the same VPS for cache/session/rate-limit only — the sender-net cache is the only Phase 4 Redis use; **Redis is NOT a queue**.
- **Schema migrations:** Liquibase YAML changesets only; additive only; no destructive operations. Phase 4 floor is `024`.
- **Boot 4 / Jackson 3 split:** `jackson-annotations` stays in `com.fasterxml.jackson.annotation.*` (`@JsonValue`, `@JsonCreator`, `@JsonProperty`, `@JsonIgnoreProperties`); core/databind moved to `tools.jackson.*` (`JsonMapper`, `ObjectMapper`). Verify any new Jackson import against current docs / Gradle dependency insight before changing it. Run deprecation diagnostics after the (one) dependency add.
- **Backend naming:** explicit, domain-revealing names — no `req`/`res`/`repo`/`svc`/`cfg`/`ctx`/`msg`/`err`/`ex`/`e`/`conn`/`tx`. Use `request`, `response`, `triageAuditRepository`, `gmailConnectionService`, `tenantContext`, `gmailMessage`, etc. Exceptions: established acronyms (`ID`, `DTO`, `JPA`, `OAuth2`, `URL`), generated API names, intentionally-ignored lambda params (`_`).
- **Conventions:** thin controllers + service-owned `@Transactional` (#1); `core.<domain>` package layout `{application, domain, persistence, service, exception}` — no ambiguous `model.*` (#2); records for DTOs, classes for entities, Lombok-free (#3); `IdentifiedEnum`/`OrderedEnum` + static `fromId` fail-loud, never `ordinal()` (#4); privacy logging format (#5); Spring Modulith events for in-process after-commit side effects, direct calls for transaction-critical commands, **Spring events do NOT cross `backend/api` ↔ `backend/worker` processes** (#6); domain events shared by api/worker/future modules belong in `backend/core` (#6); each runnable subproject owns its own runtime config (#9 — worker-only properties in `backend/worker/.../application.yml`).
- **GSD workflow:** file-changing work goes through a GSD command.
- **Tooling preference:** JetBrains MCP for symbol-aware reads/refactors/diagnostics on Java; `mcp__jetbrains__get_file_problems` after meaningful Java edits before declaring done; Postgres MCP for DB inspection; Playwright MCP for any (Phase 5) frontend verification — N/A for Phase 4's backend scope.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The artifact `org.springframework.modulith:spring-modulith-starter-jdbc` resolves at the project's pinned `springModulith` line (`2.0.7-SNAPSHOT` on the Spring snapshot repo today), brings in the `EventPublicationRegistry` / `IncompleteEventPublications` / `CompletedEventPublications` / `FailedEventPublications` beans, and the `@ApplicationModuleListener` macro behaves as documented (`@Async + @Transactional(REQUIRES_NEW) + @TransactionalEventListener AFTER_COMMIT`). | Standard Stack §Supporting, Architecture Pattern 1 | If the snapshot line doesn't publish the JDBC starter, the planner must pick the nearest milestone/GA that does and re-pin `libs.versions.toml` + `zeromail.modulith-conventions.gradle.kts` in lockstep. Verify with `./gradlew :backend:core:dependencies | grep modulith` after adding it. |
| A2 | The `event_publication` DDL for the pinned Modulith version is close enough to the published `2.0.6` canonical schema that mirroring it (or, safer, dumping the auto-created table from a throwaway dev DB) in `024-modulith-event-publication.yaml` works. | Standard Stack §Version verification, Pitfall 2 | A column/index mismatch → events silently don't persist or app fails to start. Mitigation: dump the auto-created DDL before writing the changelog. |
| A3 | The schema-init disable property is `spring.modulith.events.jdbc.schema-initialization.enabled=false` (dot after `jdbc`), not the `...jdbc-schema-initialization...` form CONTEXT.md wrote — and may also be disabled by simply not enabling it. | Standard Stack §Version verification, Anti-Patterns | Wrong key → auto-init runs anyway and conflicts with Liquibase, OR (if the wrong key is harmless) the table never gets created in environments that rely on auto-init. Verify the actual key for the pinned snapshot. |
| A4 | `RuleActionType` will be extended in Phase 4 with allow-listed values like `APPLY_LABEL` / `ARCHIVE_SKIP_INBOX` / `SAVE_DRAFT` *only if the planner decides to* — today it has `LABEL` / `ARCHIVE` / `SAVE_DRAFT`. The SPEC says "New `RuleActionType` values only within the allow-list (e.g., `APPLY_LABEL`, ...)". It is equally valid to keep the existing 3 names and just map them to the Gmail operations in `TriageGmailWriter`. The CallSite ArchUnit test currently asserts exactly 3 members `{TRIAGE, DRAFT, PREVIEW}` — Phase 4 changes that to 5 (`+ TRIAGE_PLATFORM_LLM, TRIAGE_DETERMINISTIC`); the `RuleActionType` membership is not similarly locked by an ArchUnit test today. | §Phase Requirements TRG-02, Anti-Patterns | If the planner adds new `RuleActionType` values, the rules-domain Wave-0/ArchUnit expectations and any persisted `action_intents` JSON must stay backward-compatible. Recommend: keep the existing 3 `RuleActionType` names; if a clearer name is wanted, that's a Phase 3 refactor, not a Phase 4 addition. The decision is the planner's. |
| A5 | The Spring AI platform model id in `backend/worker/src/main/resources/application.yml` is currently `openai/gpt-4o-mini` (the Phase 2C pin) and Phase 4 raises it to `openai/gpt-5.4-nano` per AI-SPEC §2. Quick task `260508-g41` ("BYOK OpenRouter preset, model selection, Spring AI 2.0.0-M5 sync") may have already changed it. | Runtime State Inventory §Live service config, State of the Art | If the file already says something else, the planner reconciles against the AI-SPEC decision. Read `backend/worker/src/main/resources/application.yml` before authoring the change. |
| A6 | A `RedisTemplate` / `StringRedisTemplate` bean is available (Spring Data Redis + Lettuce auto-config is on the worker classpath because Redis is "infra" per CLAUDE.md). If not yet wired, the sender-net cache plan must add the `spring-boot-starter-data-redis` dependency + connection config to `backend/worker` (and possibly `backend/api` for the opt-in cache invalidation). | §Phase Requirements TRG-08 | Verify with a grep for `RedisTemplate` / `LettuceConnectionFactory` / `spring.data.redis` across `backend/`. If Redis isn't actually wired yet, that's an extra plan task (CLAUDE.md says Redis runs on the VPS but the Spring side may not be configured until something needs it). |

**If a decision depends on an `[ASSUMED]` claim above, the planner should verify it (or surface it to the user via `/gsd-discuss-phase`) before locking the plan.**

---

## Open Questions (RESOLVED)

> Resolved at plan-phase (2026-05-11). Each question below carries the resolution adopted by the Phase 4 plan set (plans 04-01 / 04-04 / 04-05 / 04-07).

1. **Does `core.triage` need a Modulith `allowedDependencies` edge to `core.gmail.persistence.crypto` (`RefreshTokenCipher`)?**
   - What we know: `core.llm` declares `gmail.persistence.crypto` as an allowed dependency for BYOK. `GmailDeliveryProcessingService` and `GmailPreviewReadService` (both in `core.gmail`) already do the decrypt-then-build-client dance.
   - What's unclear: whether `TriageGmailWriter` and `SenderSafetyNetService` build their own Gmail client (needing the cipher) or call a facade on `core.gmail` (e.g., a new `GmailTriageReadService` / a `buildAuthenticatedClient(tenantId)` helper) so the cipher stays inside `core.gmail`.
   - Recommendation (also CONTEXT.md's): NO direct edge — add a thin facade method on `core.gmail.service` (`GmailApiClientFactory` could expose `buildClientForTenant(tenantId)` that does the decrypt+refresh+build internally, or `GmailPreviewReadService` exposes `fetchTriageInput(...)`). `core.triage`'s `allowedDependencies` then = `{rules, gmail, llm, billing, tenant, shared.persistence, shared.lang}`. The planner makes the call when designing `TriageGmailWriter`.
   - **RESOLVED:** No `core.triage -> core.gmail.persistence.crypto` Modulith edge. `core.gmail` exposes a `GmailApiClientFactory.buildClientForTenant(tenantId)` facade (added in plan 04-04) that does the decrypt + refresh + build internally; `GmailPreviewReadService` exposes a `fetchTriageInput(...)`-style triage facade (plan 04-05). `core.triage`'s `allowedDependencies` = `{rules, gmail, llm, billing, tenant, shared.persistence, shared.lang}` — the cipher stays inside `core.gmail`.

2. **`TriageOrchestratorService` package + AOT detection.**
   - What we know: CONVENTIONS #2 says use-case services live in `application/`. `@ApplicationModuleListener` requires the bean to be component-scanned (the worker scans `com.zeromail.core` + `com.zeromail.worker`).
   - What's unclear: whether Spring AOT / native-hint detection in the worker behaves identically for a listener bean defined in `core` vs `worker`.
   - Recommendation: put it in `core.triage.application` (the worker already scans `com.zeromail.core`); if AOT detection misbehaves in CI, add a thin `worker`-side `@Component` adapter that delegates. Verify the worker boots and processes an event in an integration test.
   - **RESOLVED:** `TriageOrchestratorService` lives in `core.triage.application` (per CONVENTIONS #2); the worker already scans `com.zeromail.core`, so the `@ApplicationModuleListener` bean is component-scanned without any extra wiring (plan 04-05). A thin `worker.triage.TriageOrchestratorAdapter` `@Component` delegate is the fallback only if AOT/native-hint detection misbehaves in CI — verified by the worker integration contract test that boots the worker and dispatches an event.

3. **Is the stuck-`PENDING` reaper (D-C5) in scope for Phase 4 or a follow-up plan?**
   - What we know: CONTEXT.md says it "may be deferred to a follow-up plan if the planner judges the failure window narrow enough; but `PENDING` rows must NEVER live forever."
   - Recommendation: include at least a minimal version (flip `PENDING` older than N minutes to `FAILED` without the Gmail re-verification, leaving the re-verification as the follow-up) so the invariant holds from day one. The planner picks the cut.
   - **RESOLVED:** In scope, minimal version. Plan 04-07 ships a `TriagePendingReaperJob` + `TriagePendingReaperBatch` (ShedLock-coordinated) that flips `triage_audit` rows stuck in `PENDING` past a TTL to `FAILED` — no Gmail re-verification (that richer reconciliation is a documented follow-up). The invariant 'PENDING rows never live forever' holds from day one.

4. **Exact `event_publication` Liquibase changeset content.**
   - What we know: floor is `024`; the Spring Modulith reference ships a canonical per-database schema.
   - What's unclear (intentionally — verify before authoring): the exact column set/types/indexes for the pinned snapshot version.
   - Recommendation: do the dev-DB auto-init dump (Pitfall 2) and mirror it; do not author from memory.
   - **RESOLVED:** Plan 04-01 dumps the auto-created Spring Modulith `event_publication` DDL from a throwaway dev DB (Pitfall 2) and mirrors it in Liquibase changelog `024-modulith-event-publication.yaml` (with `spring.modulith.events.jdbc.schema-initialization.enabled=false`), rather than authoring the schema from memory.

---

## Environment Availability

> Phase 4 depends on external services at runtime (Gmail API, OpenRouter via Spring AI, Postgres, Redis) but **all are already provisioned and used by earlier phases** — no new external dependency. This audit is therefore mostly confirmation.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL (dev: docker-compose; prod: VPS) | `event_publication`, `triage_audit`, `tenant_sender_opt_in`, ShedLock table, Modulith verification | ✓ (used since Phase 1; `docker-compose.yml` auto-launch added in quick task `260426-a5s`) | 17.x dev / 17.6 prod | — (hard dependency) |
| Redis (dev: docker-compose; prod: VPS) | `SenderSafetyNetService` 24h cache (TRG-08) | ✓ (running for cache/session/rate-limit per CLAUDE.md; docker-compose includes it) — but verify the Spring side has a `RedisTemplate`/`StringRedisTemplate` bean wired (see Assumption A6) | 7.2 | If the Spring side isn't wired yet: add `spring-boot-starter-data-redis` + connection config (extra plan task). The sender-net cache is not strictly required for correctness (it's a latency/cost optimization) — worst case the heuristic re-queries Gmail every triage, which is slower but still correct. |
| Gmail API client (`v1-rev20250331-2.0.0`) | Metadata reads, the 3 allow-listed writes, SENT-history list, undo inverse calls | ✓ (on `backend/core` classpath; `GmailApiClientFactory` + `GmailPreviewReadService` use it) | as pinned | — (hard dependency) |
| OpenRouter (via Spring AI `spring-ai-starter-model-openai`, `base-url: https://openrouter.ai/api/v1`) | `LlmGateway.evaluateSemanticIntents` for `SEMANTIC_INTENT` rules | ✓ (wired in Phase 2C; `ZERO_MAIL_OPENROUTER_API_KEY` env var) | Spring AI 2.0.0-M5 | LLM failure → per-node `DEFERRED-(error)` (D-D5) — deterministic rules still fire. BYOK tenants use their own provider. |
| Spring Modulith JDBC events starter | The `observed → triage` event spine (D-A1) | ✗ — NOT yet on the classpath | (transitive from `spring-modulith-bom` already imported) | None — this is the chosen architecture; the only "fallback" (plain `ApplicationEventPublisher`) is explicitly REJECTED. Must be added (one dependency line). |
| jtokkit / Jsoup (sanitization) | Token budget pre-check; HTML strip — reused via the Phase 2C pipeline, not re-imported in `core.triage` | ✓ (on `backend/core` classpath, ArchUnit-confined to `core.llm.gateway.sanitization`) | jtokkit 1.1.0, Jsoup 1.22.2 | — |
| ShedLock (`shedlock-spring` + `shedlock-provider-jdbc-template`) | The four new `@Scheduled` triage worker jobs | ✓ (on `backend/worker` classpath; `ShedLockConfig` wired; Liquibase shedlock table exists) | 7.7.0 | — |

**Missing dependencies with no fallback:** `spring-modulith-starter-jdbc` — must be added to `backend/core` + `backend/worker` build files + `libs.versions.toml` (Wave 0 / foundation plan).

**Missing dependencies with a fallback:** Redis Spring-side wiring (Assumption A6) — if not present, add `spring-boot-starter-data-redis` + config; the sender-net cache degrades gracefully (re-query Gmail) if Redis is unavailable at runtime, so the cache should be optional/best-effort in the service code.

---

## Validation Architecture

> `workflow.nyquist_validation` was not located as explicitly `false` in `.planning/config.json` during this research — treating it as **enabled**. (The planner should confirm `.planning/config.json` before relying on this section; if the key is absent or `true`, this section applies.)

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Spring Boot Test (`spring-boot-starter-test`) + Testcontainers (`postgresql:1.21.3`, `junit-jupiter:1.21.3`) + ArchUnit (`archunit-junit5:1.4.2`) + Spring Modulith test slice (`spring-modulith-starter-test`). Frontend (Phase 5, not Phase 4): Vitest + Playwright. |
| Config file | None standalone — Gradle JUnit Platform via `zeromail.spring-boot-conventions`; `archunit-conventions` + `modulith-conventions` build plugins. Tests live in `backend/{core,api,worker}/src/test/java/...`. |
| Quick run command | `./gradlew :backend:core:test` (or `:backend:worker:test`, `:backend:api:test`) — per-module, fast feedback. |
| Full suite command | `./gradlew clean check` — compiles + all tests + ArchUnit + `ApplicationModulesTest` across all modules. SPEC acceptance gate: this must be GREEN. |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TRG-01 | Modulith event published from `core.gmail`, consumed by `core.triage`; orchestrator evaluates 2 rules in `display_order`, produces deterministic proposals, persists 1 audit row per applied action | integration (Testcontainers + Spring Modulith scenario test) | `./gradlew :backend:worker:test --tests "*TriageOrchestratorIntegrationTest"` | ❌ Wave 0 |
| TRG-01 | `MailMessageObserved` payload carries only ids + timestamp (no subject/snippet/body/display-name) | unit | `./gradlew :backend:core:test --tests "*MailMessageObservedTest"` | ❌ Wave 0 |
| TRG-02 | `TriageSafetyPolicy` rejects a hypothetical `ActionProposal` with a non-allow-listed `RuleActionType` → `SafetyViolationException` (or `TriageSafetyViolationException`), zero Gmail calls, audit `decision=REJECTED_BY_SAFETY_POLICY` | unit + integration | `./gradlew :backend:core:test --tests "*TriageSafetyPolicyTest"` | ❌ Wave 0 |
| TRG-02 | ArchUnit: only `TriageGmailWriter` invokes Gmail write APIs from triage code | ArchUnit | `./gradlew :backend:core:test --tests "*TriageGmailWriteBoundaryTest"` | ❌ Wave 0 |
| TRG-03 | ArchUnit: no class calls `Gmail.Users.Messages.send` / `Gmail.Users.Drafts.send` in `backend/`; `RuleActionType` has no `SEND` | ArchUnit | `./gradlew :backend:core:test --tests "*NoGmailSendAllowedTest"` | ❌ Wave 0 |
| TRG-04 | Replaying the same `(tenantId, gmailMessageId)` twice → exactly 1 audit row per action, ≤ 1 Gmail write per action (idempotent two-phase loop) | integration (Testcontainers) | `./gradlew :backend:worker:test --tests "*TriageIdempotencyTest"` | ❌ Wave 0 |
| TRG-04 | `CreditLedger.reserve → settle/release` invoked per LLM call (1 batched, N per-rule fanout) and per pure-deterministic message | integration | `./gradlew :backend:worker:test --tests "*TriageCreditAccountingTest"` | ❌ Wave 0 |
| TRG-05 | `triage_audit` row presence + `@TenantId` scoping + JSON schema validity (`TriageActionResultJsonValidator`); ArchUnit bans `delete*`/`update*` repo methods except `markApplied`/`markFailed`/`markReverted` | integration + ArchUnit | `./gradlew :backend:core:test --tests "*TriageAuditPersistenceTest" --tests "*TriageAuditRepositoryBoundaryArchTest"` | ❌ Wave 0 |
| TRG-06 | Undo within 30d → Gmail inverse applied + `decision=REVERTED`; second undo → 409 `TRIAGE_UNDO_ALREADY_DONE`; mocked clock at 30d+1s → 409 `TRIAGE_UNDO_EXPIRED`; unknown action type → 409 `TRIAGE_UNDO_UNSUPPORTED_ACTION` | integration (Testcontainers + injected `Clock`) + API test (`RestClient + @LocalServerPort`) | `./gradlew :backend:api:test --tests "*TriageUndoControllerTest"` ; `./gradlew :backend:core:test --tests "*TriageUndoServiceTest"` | ❌ Wave 0 |
| TRG-06 | Daily worker purges `triage_audit` rows older than 30d (decision in the purge set) — mocked-clock Testcontainers test | integration | `./gradlew :backend:worker:test --tests "*TriageAuditPurgeJobTest"` | ❌ Wave 0 |
| TRG-07 | `triage_shadow_mode=true` → orchestrator runs, audit `decision=SHADOW_LOGGED`, zero Gmail calls; flip false → next message `decision=APPLIED` + Gmail write; `PATCH /api/tenant/triage/shadow-mode` flips the flag | integration + API test | `./gradlew :backend:worker:test --tests "*TriageShadowModeTest"` ; `./gradlew :backend:api:test --tests "*TriageTenantControllerTest"` | ❌ Wave 0 |
| TRG-08 | Seeded Gmail SENT fixture (3 messages to `boss@example.com` in 90d) → triage on incoming from `boss@example.com` → `decision=REJECTED_BY_SAFETY_NET`; after `POST .../opt-in` → next message `decision=APPLIED` + Gmail write; second call to same sender within 24h → Redis cache hit (key inspection) | integration (Testcontainers + Gmail mock + embedded/Testcontainers Redis) | `./gradlew :backend:worker:test --tests "*SenderSafetyNetServiceTest"` ; `./gradlew :backend:api:test --tests "*SenderSafetyNetControllerTest"` | ❌ Wave 0 |
| (cross-cutting) | No raw body/snippet/display-name/prompt/completion in `triage_audit`, logs, or Micrometer tags (sweep test analogous to FND-03) | integration | `./gradlew :backend:core:test --tests "*TriagePrivacySweepTest"` | ❌ Wave 0 |
| (cross-cutting) | Phase 1 FND-05 `MultiTenantLeakIntegrationTest` still passes after Phase 4 additions | integration | `./gradlew :backend:core:test --tests "*MultiTenantLeakIntegrationTest"` | ✅ (exists; must stay green) |
| (cross-cutting) | `CallSiteEnumMembershipArchTest` updated: expects 5 members incl. `TRIAGE_PLATFORM_LLM`, `TRIAGE_DETERMINISTIC` | ArchUnit | `./gradlew :backend:core:test --tests "*CallSiteEnumMembershipArchTest"` | ✅ (exists; Phase 4 modifies it — currently asserts exactly 3) |
| (cross-cutting) | `ApplicationModulesTest` passes with `core.triage` + the new `core.gmail.event` sub-package + updated `allowedDependencies` | Modulith verification | `./gradlew :backend:core:test --tests "*ApplicationModulesTest"` ; `./gradlew :backend:worker:test --tests "*ApplicationModulesTest"` | ✅ (exists; Phase 4 extends it) |
| (cross-cutting, AI) | `LlmGateway.evaluateSemanticIntents` eval harness — fixture-driven (35 fixtures, recorded cassettes, no live LLM in CI); build-breaking on Dim 1/3/4/5/9 regression | offline eval (JUnit + `BeanOutputConverter` against cassettes) | `./gradlew :backend:core:semanticIntentEval` (new Gradle task per AI-SPEC §5) | ❌ Wave 0 (AI-SPEC §5 defines composition; the auditor owns this) |

### Sampling Rate
- **Per task commit:** `./gradlew :backend:<module>:test` for the module(s) touched (fast — minutes).
- **Per wave merge:** `./gradlew :backend:core:test :backend:worker:test :backend:api:test` + `ApplicationModulesTest` + the new ArchUnit rules.
- **Phase gate (`/gsd-verify-work`):** `./gradlew clean check` GREEN (all modules, all tests, all ArchUnit, Modulith verification) + the `semanticIntentEval` task GREEN + the privacy sweep + FND-05 still green. SPEC acceptance criterion: "`./gradlew clean check` is GREEN."

### Wave 0 Gaps
- [ ] `backend/core/src/test/java/com/zeromail/core/triage/...` — orchestrator/safety-policy/audit-persistence/undo-service/sender-net unit + integration test scaffolds (RED, referencing future production classes).
- [ ] `backend/worker/src/test/java/com/zeromail/worker/triage/...` — orchestrator integration test (Modulith scenario), idempotency test, shadow-mode test, credit-accounting test, purge-job test, pending-reaper test (RED).
- [ ] `backend/api/src/test/java/com/zeromail/api/...triage/...` — undo controller test, tenant/shadow controller test, sender-net controller test (RED; `RestClient + @LocalServerPort` pattern).
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/NoGmailSendAllowedTest.java`, `TriageGmailWriteBoundaryTest.java`, `TriageAuditRepositoryBoundaryArchTest.java` (RED — assert behaviors that don't compile yet).
- [ ] Update `backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java` — change the assertion from 3 to 5 members (this will go RED until `CallSite` is extended; that's the Wave-0 contract).
- [ ] `backend/core/src/test/resources/semantic-intent-eval/fixtures/*.json` + `cassettes/*.json` (35 fixtures per AI-SPEC §5) + the `semanticIntentEval` Gradle task registration in `backend/core/build.gradle.kts` (`@Tag("semantic-intent-eval")`). The eval-auditor owns content; Wave 0 needs the harness skeleton + a `@Disabled`/empty task so the build doesn't break.
- [ ] No new test framework needed — all required dependencies are already on the test classpaths. (Confirm whether an embedded/Testcontainers Redis is preferred for the sender-net cache test, or a `RedisTemplate` mock — neither requires a new framework, but the Testcontainers Redis module (`org.testcontainers:redis` or the generic container) would be a new test dependency if a real Redis is wanted.)

---

## Security Domain

> `security_enforcement` was not located as explicitly `false` in `.planning/config.json` — treating it as **enabled**.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control (this phase) |
|---------------|---------|-------------------------------|
| V1 Architecture / Secure SDLC | yes | Modulith boundary verification + ArchUnit (`NoGmailSendAllowedTest`, `TriageGmailWriteBoundaryTest`, `LlmGatewayBoundaryTest`, `triage_audit` repo-method ban) enforce the safety architecture at build time, not just at runtime. |
| V2 Authentication | no (Phase 4 adds no auth flow) | Reuses Spring Security OAuth2 + Redis-backed Spring Session cookie (Phase 1.5). The undo/shadow/opt-in endpoints sit behind the existing protected-route security chain. |
| V3 Session Management | no (reuses) | As above. The Pub/Sub push endpoint's OIDC verification (Phase 2A `PubSubOidcAuthFilter`, `@Order(1)` chain) gates ingress; Phase 4 only consumes the post-ingress event. |
| V4 Access Control | yes | `TriageUndoService` MUST verify the `triage_audit` row belongs to the requesting tenant before computing the inverse (SPEC §req 6); `@TenantId` filter on `TriageAuditEntity` + explicit `tenant_id` predicates on native queries; FND-05 leak test as the regression gate. The sender-net opt-in and `GET .../sender-safety-net` are tenant-scoped. |
| V5 Input Validation / Sanitization | yes | Email content reaching the LLM goes through the Phase 2C `SanitizationPipeline` (Jsoup HTML strip → NFC normalize → Unicode-tag (U+E0000–U+E007F) strip → jtokkit 3896-token cap) — the orchestrator never builds an LLM request directly. `TriageActionResultJsonValidator` validates `triage_audit.action_args_json` on write/read (rejects unknown discriminators/fields). The `evaluateSemanticIntents` node-id set-equality validator rejects hallucinated/missing `nodeId`s (`SafetyViolationException`). Path params (`auditId`, `senderEmail`) validated at the controller; `senderEmail` lower-cased + used only as a Gmail query operand + a Redis key segment (no SQL interpolation — the opt-in insert is parameterized). |
| V6 Cryptography | yes (reuse) | OAuth refresh-token decryption for the Gmail client uses the existing app-layer AES-GCM `RefreshTokenCipher` (Phase 1.5) — Phase 4 never derives its own crypto. `args_hash` is SHA-256 over canonical JSON (an idempotency key, not a security primitive — collision resistance is sufficient and not load-bearing for safety). No new secret key. |
| V7 Error Handling / Logging | yes | Privacy logging format (CLAUDE.md #5) — every triage log line is `event=triage_* tenantId={} gmailMessageId={} ruleId={} actionType={}` + structured ids only; Logback scrub filter + FND-03/04 ArchUnit log-bans must still pass; the new business exceptions carry no content-bearing payload; `GlobalExceptionHandler` returns generic English `ProblemDetail` + a `code` constant (never raw exception text). Spring AI prompt/completion observation capture stays disabled. |
| V8 Data Protection / Privacy | yes | No raw email body / prompt / completion / embedding stored; `triage_audit` stores ids + matcher node ids + structured evidence ids + the rule's static draft `instruction` string + Gmail-returned ids only (D-B5); 30-day audit retention auto-purged (D-E2); `MailMessageObserved` payload is ids + timestamp only (D-A2); the sender-net Redis cache stores only a boolean `protected` flag (D-E3, avoids leaking sent-volume metadata). GDPR Art. 22 explainability satisfied by the per-rule audit trail + the 30-day undo + shadow-mode opt-in; Google Workspace API Limited-Use satisfied by "sanitized content flows transiently through the LLM call only" (AI-SPEC §1b regulatory context). |
| V13 API / Web Service | yes | Thin REST controllers under `backend/api/controllers/triage/`; record DTOs; `springdoc-openapi` regenerates the typed client schema for Phase 5; rate-limiting infra (Redis) is project-wide (not Phase-4-specific) but the new endpoints inherit it. |

### Known Threat Patterns for {Spring Boot 4 / Java 25 / Gmail-write triage worker}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Auto-send of mail on the user's behalf (the catastrophic, trust-killing capability) | Elevation of Privilege / Tampering | Architecturally forbidden: `TriageGmailWriter` exposes no send method; `RuleActionType` has no `SEND`; `NoGmailSendAllowedTest` (no `Gmail.Users.Messages.send` / `Gmail.Users.Drafts.send` anywhere in `backend/`) + the existence assertion fail the build if violated; `TriageSafetyPolicy` is the runtime backstop (`SafetyViolationException` → HTTP 500 `code=TRIAGE_SAFETY_VIOLATION`). Defense-in-depth across compile-time AND runtime (SPEC constraint). |
| Prompt injection via email content flipping an unrelated rule's `nodeId` in the batched call (the accepted blast radius of D-D1) | Tampering / Spoofing | Sanitization pipeline (Jsoup + NFC + Unicode-tag strip) closes syntactic injection; system/user prompt separation with a fixed "treat email content as untrusted DATA" frame (AI-SPEC §3); `temperature=0.0` + strict JSON Schema; the AI-SPEC eval Dim 5 (6 adversarial fixtures: plain text / HTML comment / zero-width Unicode / quoted-printable / `.ics` description / forwarded-mail tail) measures cross-rule blast radius with zero-flip tolerance; the sender safety net + thread-state are the orchestrator-layer compensating controls for the catastrophic-misclassification case. |
| Cross-tenant data leakage (one tenant's rules/audit/Gmail acted on another tenant's message) | Information Disclosure / Elevation of Privilege | `ScopedValue` `TenantContext` rebound at the orchestrator listener entry (mirror `GmailHistoryProcessor`); `@TenantId` filter on all tenant-owned entities; explicit `tenant_id` predicates on every native query (STATE.md 02A decision); `TriageUndoService` tenant-ownership check; FND-05 `MultiTenantLeakIntegrationTest` as the regression gate (must stay green). |
| Duplicate destructive Gmail writes on Pub/Sub re-delivery or worker crash (a wrong draft is "impossible to un-send" — but a duplicate draft / a re-archive is still trust-eroding) | Tampering / DoS-by-clutter | Two-phase PENDING → APPLIED with the idempotent native upsert as the gate (D-C3); unique index `(tenant, message, rule, action_type, args_hash)`; `messages.modify` is idempotent on Google's side; `drafts.create` is NOT — so the audit row is written *before* the draft call and a conflict means "skip"; stuck-`PENDING` reaper bounds the crash window. |
| Event loss between "message observed" and "triage applied" (a missed action the user notices → trust loss) | Repudiation / DoS | Spring Modulith JDBC event publication registry — the event row commits with the observed-message insert; `IncompleteEventPublications.resubmit(...)` (`@Scheduled PT2M` retry job); `FailedEventPublications` captures listener exceptions instead of swallowing them; `CompletedEventPublications.deletePublicationsOlderThan(7d)` keeps the table bounded. The plain-`ApplicationEventPublisher` path that would silently drop is explicitly REJECTED. |
| Credit double-charge / lost reservation on LLM failure or worker crash | Tampering (financial) | `CreditLedger.reserve → settle/release` with `release` in every failure catch (AI-SPEC §4 code + §6 guardrail #6); the Phase 2B `CreditReserveWatchdog` sweeps stuck reservations; 1 reserve per LLM call (D-D3) so the ledger matches real OpenRouter spend; BYOK bypasses the ledger entirely. |
| Silent LLM failure presenting as "rule didn't match" (mis-calibrates the user's trust in coverage) | Repudiation | Fail-loud: `DEFERRED-(error)` per node with the opaque error class in the audit row; never default booleans to `false`; deterministic-only actions still fire; AI-SPEC eval Dim 3 is the build-breaking check; offline flywheel pages on `DEFERRED-(error)` rate > 1% sustained. |
| Sensitive content leaking via logs / OTEL spans / Micrometer tags / exception messages / cache contents | Information Disclosure | CLAUDE.md privacy logging format; Logback scrub filter; FND-03/04 ArchUnit log-bans; `SafetyViolationException` (and all new triage exceptions) carry no content-bearing payload; Spring AI observation prompt/completion capture disabled; `MailMessageObserved` payload is ids + timestamp; Redis cache stores only a boolean; `triage_audit` privacy sweep test (analogous to FND-03). |
| Resource exhaustion / SLO breach from runaway LLM completion or per-rule fanout creep | DoS | `maxTokens=512` hard cap on the `evaluateSemanticIntents` output (AI-SPEC §4); 7s batched / 6s per-rule HTTP read timeout (D-D6); pre-call token-budget check (3896-token cap) — over-budget → bounded per-rule fanout, not unbounded growth; `triage.token_budget.exceeded` counter to watch fanout creep; bounded `LIMIT 1000` deletes in the purge job to avoid long-tx vacuum stalls. |

---

## Sources

### Primary (HIGH confidence)
- **In-repo code (read this session):**
  - `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java` — publish-site shape, `@Transactional` boundary, `insertObservedIfAbsent` call.
  - `backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java` + `MailMessageObservedEntity.java` — idempotent native upsert template; `@TenantId @IdClass` shape.
  - `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java` + `GmailPreviewReadService.java` — Gmail auth (decrypt → refresh → build) + `format=metadata` fetch + header parsing → privacy-correct sender/recipient/subject/labels mapping.
  - `backend/core/src/main/java/com/zeromail/core/rules/service/RuleEvaluator.java` + `ActionProposalMerger.java` — tri-state evaluator (`MATCHED/NOT_MATCHED/DEFERRED`), `evaluateAndMerge` / `merge` contract, `SemanticIntentMatcher` deferral.
  - `backend/core/src/main/java/com/zeromail/core/rules/domain/ActionIntent.java`, `RuleActionType.java`, `RuleEvaluationInput.java`, `SemanticIntentMatcher.java`; `backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java` + `RuleRepository.java` — sealed-interface shape, allow-list enum (3 values), JSONB-column wiring, manual-validator pattern, `findOrderedByTenantId`.
  - `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java` — the interface Phase 4 extends; `core/llm/package-info.java` — Modulith `allowedDependencies` precedent.
  - `backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedger.java` + `domain/CallSite.java` — reserve/settle/release contract; current 3-member `CallSite` enum (Phase 4 extends to 5).
  - `backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java` + `persistence/TenantEntity.java` — `ScopedValue` rebind pattern; `triage_paused` column shape (Phase 4 adds `triage_shadow_mode` the same way).
  - `backend/core/src/main/java/com/zeromail/core/shared/persistence/AbstractTenantOwnedEntity.java` — `@TenantId @Column("tenant_id")` base class.
  - `backend/worker/src/main/java/com/zeromail/worker/GmailHistoryProcessor.java` — worker loop `ScopedValue.where(TENANT, ...).run(...)` pattern (mirror for the orchestrator listener).
  - `backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java` + `ShedLockConfig.java` — ShedLock `@Scheduled` + `@SchedulerLock` shape; scheduler/transactional-batch split rationale (in the JavaDoc).
  - `backend/worker/src/main/java/com/zeromail/worker/ZeroMailWorkerApplication.java` — `scanBasePackages = {"com.zeromail.worker", "com.zeromail.core"}` (already covers `core.triage` and `worker.triage`).
  - `backend/core/src/test/java/com/zeromail/core/arch/LlmGatewayBoundaryTest.java`, `SafetyContractArchTests.java`; `backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java` — ArchUnit precedents to extend; the `SafetyViolationException` no-arg-only constraint.
  - `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java` + `error/ErrorCodes.java` — exception → `ProblemDetail` + `code` mapping pattern; where the 4 new triage codes go.
  - `gradle/libs.versions.toml`, `buildSrc/src/main/kotlin/zeromail.modulith-conventions.gradle.kts`, `backend/core/build.gradle.kts`, `backend/worker/build.gradle.kts` — version pins (`springModulith = "2.0.7-SNAPSHOT"`), the Modulith BOM import, the Spring AI / jtokkit / Jsoup / ShedLock / Gmail wiring.
  - `backend/core/src/main/resources/db/changelog/changes/` + `db.changelog-master.yaml` — changelog floor is `024` (last committed `023-fix-pin-calendar-category.yaml`).
- **Phase artifacts (read this session):** `.planning/phases/04-triage-convergence-hero/04-CONTEXT.md` (locked decisions D-A1…D-E5), `04-SPEC.md` (8 requirements, boundaries, constraints, 13 acceptance criteria), `04-AI-SPEC.md` (framework, structured-output design, eval strategy, guardrails, monitoring); `.planning/REQUIREMENTS.md` (TRG-01…TRG-08 + upstream phases); `.planning/STATE.md` (accumulated decisions from Phases 1–3, blockers, quick tasks); `.planning/ROADMAP.md` (Phase 4 goal + dependencies).
- **Project docs:** `CLAUDE.md` (constraints, backend code style, conventions, "Hard do not use" list, technology stack), `CONVENTIONS.md` (referenced, not re-read this session — read it before introducing patterns in the listed areas).
- **Official docs:** Spring Modulith reference — Application Events (`@ApplicationModuleListener`, `spring-modulith-starter-jdbc`, `IncompleteEventPublications.resubmit(...)`, `CompletedEventPublications.deletePublicationsOlderThan(...)`, `FailedEventPublications`, `spring.modulith.events.jdbc.schema-initialization.enabled`, `spring.modulith.events.republish-outstanding-events-on-restart`) — https://docs.spring.io/spring-modulith/reference/events.html (fetched 2026-05-11). PostgreSQL `INSERT … ON CONFLICT … RETURNING` semantics — https://www.postgresql.org/docs/17/sql-insert.html (cited, established behavior). Gmail API `users.drafts.create` (non-idempotent) / `users.messages.modify` (idempotent label add/remove) — https://developers.google.com/workspace/gmail/api/reference/rest/v1/ (cited from CONTEXT.md canonical refs + general knowledge). Spring AI 2.0.0-M5 — Structured Output Converter / ChatClient / OpenAI Chat `responseFormat` — https://docs.spring.io/spring-ai/reference/ (cited via AI-SPEC §3, which was generated by `gsd-ai-researcher` against those pages).

### Secondary (MEDIUM confidence)
- Spring Modulith 2.x events API specifics — the reference docs show `2.0.6` GA examples; the project pins `2.0.7-SNAPSHOT`. Method names (`resubmitIncompletePublications` / `resubmit(ResubmissionOptions)` vs the CONTEXT.md's `resubmitIncompletePublicationsOlderThan(Duration)`) and the schema-init property exact key should be confirmed against the pinned snapshot before authoring `024` (see Assumptions A1–A3). The Spring Modulith 2.0.6 release notes / 2.1-RC1 blog post (https://spring.io/blog/2026/04/24/spring-modulith-2-1-rc1-2-0-6-and-1-4-11-released/, cited from CONTEXT.md canonical refs, not fetched this session) are the place to check.
- `inbox-zero` reference repo triage orchestrator — `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/utils/ai/choose-rule/` (`ai-choose-rule.ts`, `match-rules.ts`, `run-rules.ts`, `execute.ts`, `choose-args.ts`, `NOTES.md`) and `apps/web/utils/ai/content-sanitizer.ts` — TypeScript; use for UX patterns + audit/undo semantics + injection-fixture inspiration only, not for code shapes (Zero Mail is a Java rebuild).

### Tertiary (LOW confidence)
- None load-bearing. (No claim in this research rests solely on unverified web search.)

---

## Metadata

**Confidence breakdown:**
- Standard stack: **HIGH** — every Core library is already on the classpath and used; the one new dependency (`spring-modulith-starter-jdbc`) is from a BOM the project already imports.
- Architecture / patterns: **HIGH** — Phase 4 is assembly of named in-repo patterns; CONTEXT.md's locked decisions (D-A1…D-E5) already constrain the design and were all read.
- AI surface (`evaluateSemanticIntents`): **HIGH for the design** (the AI-SPEC pre-locks it down to the entry-point code), **MEDIUM for Spring AI M5 API stability** (milestone — `BeanOutputConverter` may relocate at GA; ArchUnit confines the blast radius).
- Spring Modulith events API specifics: **MEDIUM** — the docs are for `2.0.6` GA; the project pins `2.0.7-SNAPSHOT`; the `event_publication` DDL and a couple of property/method names must be verified against the pinned snapshot before authoring `024` (Assumptions A1–A3, Pitfall 2).
- Pitfalls: **HIGH** — all eight are either documented in-repo (STATE.md decisions, `CreditReserveWatchdog` JavaDoc), in CLAUDE.md (privacy logging, Boot 4 / Jackson 3 split), or in CONTEXT.md's locked decisions (two-phase loop, fail-loud LLM, shadow vs paused).

**Research date:** 2026-05-11
**Valid until:** ~2026-06-10 for the in-repo patterns (stable); ~2026-05-25 for the Spring Modulith / Spring AI specifics (milestone/snapshot lines move) — re-verify the Modulith pin + DDL at plan-phase regardless of date.
