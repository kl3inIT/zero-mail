# Phase 04: triage-convergence-hero - Context

**Gathered:** 2026-05-11
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 4 is the hero convergence: when a Gmail message is observed for a connected tenant with active rules, a new `core.triage` Spring Modulith package (consumed in `backend/worker`) reacts to a Modulith-published `MailMessageObserved` after-commit event, resolves any `SEMANTIC_INTENT` matchers inline via `LlmGateway`, evaluates rules in `display_order`, merges deterministic `ActionProposal`s, gates every proposal through a `TriageSafetyPolicy` allow-list (label / archive / save-draft only — `SEND` permanently forbidden architecturally), applies allow-listed actions through a single `TriageGmailWriter` (the only class permitted to call Gmail write APIs from triage code), writes one immutable `triage_audit` row per applied/shadow/rejected action with full provenance, supports user undo within 30 days via a REST endpoint, honors a tenant-wide opt-in `triage_shadow_mode` toggle and a sent-history-based `SenderSafetyNetService`, and meets p95 ≤ 10s end-to-end. Phase 4 ships backend + REST only; all UI surfaces (audit log, undo button, shadow toggle, sender-safety-net management) belong to Phase 5.

</domain>

<spec_lock>
## Requirements (locked via SPEC.md)

**8 requirements are locked.** See `04-SPEC.md` for full requirements, boundaries, constraints, and acceptance criteria.

Downstream agents MUST read `04-SPEC.md` before planning or implementing. Requirements are not duplicated here.

**In scope (from SPEC.md):**
- New Spring Modulith package `core.triage` (`application/`, `domain/`, `persistence/`, `service/`, `exception/`).
- `MailMessageObserved` after-commit event published from `core.gmail` and consumed in `backend/worker`.
- `TriageOrchestratorService` (in-worker) that loads rules, evaluates matchers (including inline `SemanticIntentMatcher` resolution via `LlmGateway`), merges action proposals, and dispatches to `TriageGmailWriter` or shadow logger.
- `TriageSafetyPolicy` allow-list gate (label / archive / save-draft only) with `SafetyViolationException`.
- `TriageGmailWriter` (Gmail API write adapter; the ONLY class allowed to call `users.messages.modify` / `users.drafts.create` from Phase 4 code).
- `triage_audit` table + `TriageAuditRepository` + JSON action-args.
- `TriageUndoService` + `POST /api/triage/audit/{auditId}/undo` controller in `backend/api`.
- 30-day audit retention worker in `backend/worker` (purges expired rows).
- `tenants.triage_shadow_mode` column + `PATCH /api/tenant/triage/shadow-mode` controller.
- `SenderSafetyNetService` (Gmail SENT metadata-only lookup, Redis-cached) + `tenant_sender_opt_in` table + 2 REST endpoints.
- `CallSite.TRIAGE_PLATFORM_LLM`, `CallSite.TRIAGE_DETERMINISTIC` enum additions for credit ledger.
- New `RuleActionType` values **only** within the allow-list (e.g., `APPLY_LABEL`, `ARCHIVE_SKIP_INBOX`, `SAVE_DRAFT`); adding `RuleActionType.SEND` is forbidden.
- `triage_audit_*` ArchUnit rules (no UPDATE/DELETE outside revert), `NoGmailSendAllowedTest`, `TriageBoundaryArchTests`.
- i18n message keys for new error codes (vi + en).
- Backend integration + unit tests for orchestrator, safety policy, undo, shadow mode, sender net.
- Spring Modulith `allowedDependencies` updates for `core.triage`.

**Out of scope (from SPEC.md):**
- Audit log UI, undo button, shadow-mode toggle UI, sender-safety-net management UI — all Phase 5.
- AI-drafted reply content generation — Phase 5 `DRFT-01..DRFT-04`.
- Daily digest email — Phase 5 `ANL-03`.
- Analytics screens — Phase 5 `ANL-01`.
- Auto-send for any rule type — permanently out of v1.
- Cold-email blocker / bulk unsubscribe / reply tracker — v2.
- Cross-message batched LLM evaluation for `SEMANTIC_INTENT` — v2 cost optimization.
- Persistent training of sender heuristic via embeddings — privacy constraint forbids embeddings.
- Re-running triage against historical messages (backfill).
- CASA verification work for triage scopes — external parallel track from Phase 1.

</spec_lock>

<decisions>
## Implementation Decisions

### A. Event publication + consumer mechanics (`MailMessageObserved`)

- **D-A1: Spring Modulith JDBC Event Publication Registry is the event spine.** Add `spring-modulith-starter-jdbc` (Spring Modulith 2.0.6 GA — bundled with Spring Boot 4 BOM; verify exact pin during research). The registry persists the event row inside the SAME database transaction as the `mail_message_observed` insert, so atomicity is guaranteed without re-inventing an outbox. Plain `ApplicationEventPublisher.publishEvent(...)` plus `@Async @TransactionalEventListener(AFTER_COMMIT)` is REJECTED — that path silently drops events on listener failure or JVM crash, which contradicts the hero-path trust posture (CLAUDE.md Convention #6 endorses Modulith here verbatim).
- **D-A2: Event record shape and package.** `record MailMessageObserved(UUID tenantId, String gmailMessageId, String gmailThreadId, Instant observedAt)` lives in `com.zeromail.core.gmail.event` (new `event/` sub-package — these are integration events, not aggregate state, so they do NOT belong under `domain/`). Privacy invariant: NO subject / snippet / body / sender display name in the payload. Only stable IDs + timestamp.
- **D-A3: Publish site.** `GmailDeliveryProcessingService.processDelivery(...)` (already `@Transactional`) injects `ApplicationEventPublisher` and calls `publishEvent(new MailMessageObserved(...))` after each NEW `MailMessageObservedEntity` row returned by `insertObservedIfAbsent`. Duplicate deliveries that the Phase 2A idempotency layer absorbs (no new row) do NOT publish — the orchestrator never sees them.
- **D-A4: Consumer.** New `TriageOrchestratorService` in `com.zeromail.core.triage.application` is annotated `@ApplicationModuleListener` (Modulith macro that wraps `@Async + @Transactional(propagation = REQUIRES_NEW) + @TransactionalEventListener(phase = AFTER_COMMIT)`). The listener runs in a virtual thread automatically because `spring.threads.virtual.enabled=true` is project-wide. Exceptions inside the listener are captured by Modulith into `FailedEventPublications`, NOT swallowed. Tenant context is rebound at the listener entry via `TenantContext.runWith(tenantId, …)` before any tenant-scoped repository or `LlmGateway` call.
- **D-A5: Retry + cleanup wired through existing ShedLock infra.**
  - Retry job: `@Scheduled(fixedDelay = "PT2M")` calls `IncompleteEventPublications.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5))`. Mirror `CreditReserveWatchdog` ShedLock pattern: `@SchedulerLock(name = "triageEventRetry", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")`.
  - Cleanup job: `@Scheduled(cron = "0 0 3 * * *")` calls `CompletedEventPublications.deletePublicationsOlderThan(Duration.ofDays(7))`. ShedLock name `triageEventCleanup`.
  - Both live in `backend/worker` under `com.zeromail.worker.triage` (sibling of `com.zeromail.worker.billing`).
- **D-A6: Liquibase changelog ships the `event_publication` schema explicitly.** Phase 4 floor is **`024-modulith-event-publication.yaml`** (changelogs up to `023-fix-pin-calendar-category.yaml` are committed; `024` is the next free slot). Auto-init via `spring.modulith.events.jdbc-schema-initialization.enabled` is REJECTED for production — schema must be tracked under Liquibase like every other table.

### B. `triage_audit` JSON schema + undo state capture

- **D-B1: New sealed `TriageActionResult` type with three records.** Lives in `com.zeromail.core.triage.domain`:
  - `sealed interface TriageActionResult permits Label, Archive, SaveDraft`
  - `record Label(String labelId, String labelName) implements TriageActionResult`
  - `record Archive() implements TriageActionResult`
  - `record SaveDraft(String instruction, String draftId, String threadId) implements TriageActionResult`
  Reuse of `core.rules.domain.ActionIntent` is REJECTED — triage_audit must round-trip Gmail response state (the returned `draftId` for `drafts.delete` on undo; the resolved `labelId` because users rename labels). Polluting `ActionIntent` with audit-only fields breaks the rules-domain boundary; Jackson polymorphic `@JsonTypeInfo` is REJECTED because it diverges from the established manual-validator pattern (`ActionIntentJsonValidator`) and would fragment the codebase mid M5→GA churn.
- **D-B2: Persistence shape.** `TriageAuditEntity.actionArgsJson` is `String` typed with `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"` — same Hibernate 7 wiring already proven by `RuleEntity.actionIntents`. Jackson 3 `tools.jackson.databind.JsonMapper` for read/write; annotations stay in `com.fasterxml.jackson.annotation.*` (the `@JsonValue` / `@JsonCreator` on type-id enum, per CLAUDE.md Boot 4 / Jackson 3 split). Yasson JSON-B (locked from Phase 02A-01) covers runtime mapping for JSONB columns at the Hibernate layer.
- **D-B3: Validation = two layers, NO DB CHECK.**
  - Layer 1: new `TriageActionResultJsonValidator` in `com.zeromail.core.triage.domain`. Mirrors `ActionIntentJsonValidator`: rejects unknown discriminators (`NoSuchElementException`), rejects unknown fields per type on write, and runs from a `@PrePersist` hook (or the entity constructor) so no path can persist an invalid row.
  - Layer 2: exhaustive `switch` on `TriageActionResult` inside `TriageUndoService.computeInverse(...)`. Compile-time exhaustiveness preserves the "SEND forbidden architecturally" guarantee.
  - DB-level `jsonb_path_match` CHECK constraint is REJECTED — application-layer validation is sufficient (audit is INSERT-only via a single service path) and matches the `RuleEntity` precedent.
- **D-B4: `gmail_change_token` placement.** Top-level JSONB column on `triage_audit` (NOT nested inside `action_args_json`). SPEC already lists it as a separate column. Stores only **what we changed**, not a full label snapshot:
  - `APPLY_LABEL` → `{"addedLabelId": "Label_123"}`
  - `ARCHIVE_SKIP_INBOX` → `{"removedLabelIds": ["INBOX"]}`
  - `SAVE_DRAFT` → `null` (the `draftId` lives in `action_args_json`)
  - `SHADOW_LOGGED` → `null`
  This semantics is "undo OUR action" — if the user added more labels in Gmail during the 30-day window, those stay; we only invert the one ID we changed.
- **D-B5: Per-action payload contract.**
  - `APPLY_LABEL`: `{"type": "label", "labelId": "...", "labelName": "..."}` — labelId is the load-bearing field for inverse; labelName is read-only display.
  - `ARCHIVE_SKIP_INBOX`: `{"type": "archive"}` — empty body; `gmail_change_token` carries the removed `INBOX` token.
  - `SAVE_DRAFT`: `{"type": "save_draft", "instruction": "...", "draftId": "...", "threadId": "..."}` — `instruction` is the rule's static draft instruction (already on the rule per SPEC §req 4; NOT LLM output); `draftId` is required for `drafts.delete` on undo. **NO drafted body text is stored** (privacy invariant).
- **D-B6: Forward-compat policy.** Strict-write (validator rejects unknown fields) / lenient-read (Jackson 3 `FAIL_ON_UNKNOWN_PROPERTIES=false` default). v2 adding e.g. `tone` to `SaveDraft` will not break reads of old audit rows. New `RuleActionType` values not present in current `TriageActionResult` cause `TriageUndoService.computeInverse` to throw `TriageAuditException.unsupportedActionType` → mapped to HTTP 409 with `code=TRIAGE_UNDO_UNSUPPORTED_ACTION` (fail-loud refusal, not silent no-op).

### C. Idempotency: unique-index design + write-order strategy

- **D-C1: Unique-key shape = `(tenant_id, gmail_message_id, rule_id, action_type, args_hash)`.** `args_hash` is a new `BYTEA NOT NULL` column carrying `SHA-256(canonicalJson(actionArgs))`. The SPEC-suggested `(tenant, message, rule, action_type)` shape is REJECTED — it silently collapses legitimate cases where a single rule produces multiple `APPLY_LABEL` actions with different label names (e.g., a rule that applies both "Finance" and "Receipts"). Forcing rule authors to split rules to work around the collapse is hostile to natural authoring.
- **D-C2: Canonical-JSON serializer is shared infrastructure.** New utility `TriageActionArgsCanonicalizer` in `com.zeromail.core.triage.domain` produces stable bytes: sort object keys lexicographically, normalize whitespace, force UTF-8. Used by orchestrator AND any future test fixtures so writers cannot drift. Hash output is 32 bytes (raw SHA-256, NOT hex-encoded — saves index space). Considered alternative D (UUIDv5 PK over the same canonical bytes) — preserved as a planner option if the planner prefers a single-column PK aligned with `CreditLedger.reservationId`; not the default because composite columns stay directly queryable for support tooling.
- **D-C3: Write-order = two-phase PENDING → APPLIED.** Orchestrator:
  1. Compute `args_hash`.
  2. Native `@Modifying @Query` `insertAuditPendingIfAbsent(...) RETURNING audit_id` (mirrors Phase 2A `MailMessageObservedRepository.insertObservedIfAbsent` pattern verbatim).
  3. If `RETURNING` is empty → row already exists → skip Gmail call entirely (idempotent retry path).
  4. If `RETURNING` returns the new `audit_id` → call Gmail through `TriageGmailWriter`.
  5. `UPDATE triage_audit SET applied_at = NOW(), decision = 'APPLIED', external_ref = :gmailDraftIdOrNull WHERE audit_id = :reservedId`.
  6. On Gmail exception: `UPDATE … SET decision = 'FAILED', failure_reason = :opaqueClassName WHERE audit_id = :reservedId`.
  
  One-shot `INSERT ON CONFLICT DO NOTHING RETURNING *` (strategy Y) is REJECTED because `users.drafts.create` is NON-idempotent on Google's side and any post-insert Gmail failure on label/archive becomes an orphan row. Gmail-first then INSERT (strategy W) is REJECTED because it creates duplicate visible drafts on every retry of `SAVE_DRAFT`.
- **D-C4: `decision` enum membership extends SPEC's list.** Add `PENDING` and `FAILED` to the SPEC-listed values. Full set: `PENDING / APPLIED / SHADOW_LOGGED / REJECTED_BY_SAFETY_NET / REJECTED_BY_SAFETY_POLICY / FAILED / REVERTED`. `PENDING` is a transient state visible only between phases 2 and 5 of the orchestrator write-loop above; the ArchUnit "no UPDATE/DELETE outside revert" rule must allow the `PENDING → APPLIED / FAILED` transition (whitelist by audit-id + status WHERE clause).
- **D-C5: Stuck-`PENDING` reaper.** New `@Scheduled(fixedDelay = "PT5M")` worker in `com.zeromail.worker.triage`: rows with `decision='PENDING'` older than 2 minutes are inspected — if the corresponding Gmail call is verifiable as "succeeded" via a metadata fetch on `gmailMessageId`, flip to `APPLIED`; otherwise flip to `FAILED`. ShedLock name `triagePendingReaper`. This guards JVM crashes between the Gmail call and the final `UPDATE`. Acceptable to defer this to a follow-up plan if the planner judges the failure window narrow enough; but `PENDING` rows must NEVER live forever.

### D. `SEMANTIC_INTENT` resolution scope (orchestrator → LlmGateway)

- **D-D1: Default batching scope = ONE LLM call per message** (per-message all-rules batched). All semantic-intent nodes across all matched rules for one message go into a SINGLE prompt; the model returns `Map<nodeId, boolean>`. The advisor recommendation was per-rule (option B); the user chose option C explicitly, accepting the wider injection blast radius in exchange for the lowest latency and cleanest credit accounting. The trade-off is acceptable for v1 because Phase 2C sanitization (Jsoup + NFC + Unicode-tag-strip + jtokkit truncate) hardens the email-content side, and the per-tenant rule count in v1 is expected to stay small.
- **D-D2: Fallback path = per-rule batching when token budget exceeded.** Orchestrator measures `(sanitized email content tokens + sum of semantic-node intent tokens + tool-schema overhead)` against the 3896-token cap BEFORE the LLM call. If over budget: degrade gracefully by fanning out one LLM call per rule (option B) using virtual-thread parallelism (`CompletableFuture.allOf` joined inside the orchestrator). Single message degrades — the tenant does not lose semantic evaluation, just pays more credits for that message. Two code paths to test; document the threshold in `TriageOrchestratorService` Javadoc.
- **D-D3: Credit accounting = 1 reserve per LLM call.** Match real OpenRouter spend exactly. For per-message batched (default path): 1 `CreditLedger.reserve(CallSite.TRIAGE_PLATFORM_LLM)` per message. For per-rule fallback path: N reserves where N = number of rules with semantic intents. For pure-deterministic messages (no semantic nodes at all): 1 `CreditLedger.reserve(CallSite.TRIAGE_DETERMINISTIC)` with a near-zero configurable unit (`zero-mail.billing.cost.triage-deterministic` defaults to 0 credits — set in Phase 2B `BillingProperties` extension; planner picks exact default). BYOK paths bypass credits per Phase 2C convention.
- **D-D4: LlmGateway extension.** Add a new method to `core.llm.service.LlmGateway`:
  
  ```java
  Map<String, Boolean> evaluateSemanticIntents(
      CallSite callSite,
      String sanitizedMessageContent,
      List<SemanticIntentRequest> intents
  );
  // SemanticIntentRequest = record(String nodeId, String intent)
  ```
  
  Impl stays inside `core.llm.gateway.springai` behind the existing ArchUnit boundary. Tool-call schema returns a structured `{"nodeMatches": [{"nodeId":"...", "matches": true|false}, ...]}` validated against the supplied node-id list (any returned id not in the input list → `SafetyViolationException`). The existing `chat(CallSite, content, tools) → ToolCallResult` method stays untouched (still used by `core.rules` for NL compile).
- **D-D5: Failure semantics on LLM error.** LlmGateway already retries once internally (Phase 2C convention). If it still fails: orchestrator catches and marks ALL semantic nodes for this message as `DEFERRED-(error)` with the opaque error class in the per-node audit field. Action-gating treats `DEFERRED-(error)` as `NOT_MATCHED` — semantic-gated actions do NOT fire. Deterministic-only actions for that message still fire. **NO new top-level `decision=FAILED` from semantic failures alone** — the safety property "no semantic match means no semantic-gated action fires" preserves Phase 4's trust posture without inventing new audit states.
- **D-D6: Timeout policy.** LlmGateway hard timeout = 7s for the semantic-eval call (leaves 3s for orchestrator overhead + Gmail metadata + Gmail writes within the 10s p95 budget). Per-rule fallback path: each parallel call gets 6s (slightly tighter to keep total fan-out under budget even with worst-case stragglers). Exact values are planner-tunable but the budget math is locked.

### E. Operational mechanics (Claude's discretion — planner picks exact values within these constraints)

- **D-E1: `CallSite` enum extension is additive.** Add `TRIAGE_PLATFORM_LLM` and `TRIAGE_DETERMINISTIC` to `core.billing.domain.CallSite`. ArchUnit rule from Phase 2B that locks `CallSite` membership must be updated to allow the new values; no other call sites are added.
- **D-E2: Audit retention purge job.** New `@Scheduled(cron = "0 0 4 * * *")` daily worker in `com.zeromail.worker.triage.TriageAuditPurgeJob`. Deletes rows where `applied_at < now() - 30 days` AND `decision IN ('APPLIED', 'REVERTED', 'SHADOW_LOGGED', 'REJECTED_BY_SAFETY_NET', 'REJECTED_BY_SAFETY_POLICY', 'FAILED')`. Bounded delete with `LIMIT 1000` per tick + repeat-until-zero, to avoid long-tx vacuum stalls. ShedLock name `triageAuditPurge`, `lockAtLeastFor = "PT1M"`, `lockAtMostFor = "PT30M"`.
- **D-E3: Sender-cache invalidation on opt-in.** Redis key `triage:sender-protect:{tenantId}:{lower(senderEmail)}` (24h TTL). On `POST /api/triage/sender-safety-net/{senderEmail}/opt-in`: `redisTemplate.delete(key)` is called in the SAME service method that inserts the `tenant_sender_opt_in` row, AFTER the DB commit succeeds (use `TransactionSynchronization.afterCommit`). Cache shape stores only the boolean `protected` flag (NOT the underlying sent count) to minimize Redis footprint and avoid leaking sent-volume metadata via cache inspection.
- **D-E4: ShedLock lock-key convention.** Reuse `CreditReserveWatchdog` naming pattern: `<domain><Purpose>` camelCase, e.g., `triageEventRetry`, `triageEventCleanup`, `triageAuditPurge`, `triagePendingReaper`. Tighter `lockAtLeastFor` than the schedule interval guards against immediate re-entry on lock release.
- **D-E5: Liquibase changelog allocation.** Phase 4 floor is **`024`** (the latest committed changelog is `023-fix-pin-calendar-category.yaml`). Recommended ordering:
  - `024-modulith-event-publication.yaml` — `event_publication` table for Modulith JDBC registry.
  - `025-triage-audit.yaml` — `triage_audit` table, `args_hash` BYTEA, unique index `(tenant_id, gmail_message_id, rule_id, action_type, args_hash)`, JSONB columns for `action_args_json` and `gmail_change_token`.
  - `026-tenants-triage-shadow-mode.yaml` — `tenants.triage_shadow_mode BOOLEAN NOT NULL DEFAULT FALSE`.
  - `027-tenant-sender-opt-in.yaml` — `tenant_sender_opt_in` table.
  Exact slot numbers are not load-bearing — planner may interleave or merge as long as the floor is `024` and additions are additive (no destructive migrations per CLAUDE.md schema policy).

### Claude's Discretion

- Exact API DTO field names for new endpoints (`POST /api/triage/audit/{auditId}/undo`, `PATCH /api/tenant/triage/shadow-mode`, `GET /api/triage/sender-safety-net`, `POST /api/triage/sender-safety-net/{senderEmail}/opt-in`) — planner/executor picks within the project record-DTO convention.
- Exact i18n message-key spelling for new error codes (`TRIAGE_UNDO_EXPIRED`, `TRIAGE_UNDO_ALREADY_DONE`, `TRIAGE_UNDO_UNSUPPORTED_ACTION`, `TRIAGE_SAFETY_VIOLATION`, etc.) — copywriter pass at plan-phase, vi + en parity must pass `pnpm i18n:check`.
- Exact `default` cost for `CallSite.TRIAGE_DETERMINISTIC` in `BillingProperties` — planner picks (recommend `0` credits in v1; configurable so ops can flip to a small unit later).
- Exact Spring Modulith `allowedDependencies` literal list for `core.triage` package-info: minimally `{rules, gmail, llm, billing, tenant, shared.persistence, shared.lang}` plus any `shared.crypto` edge if `RefreshTokenCipher` access is needed.
- `TriageOrchestratorService` package placement: `application/` per CLAUDE.md Convention #2 — but planner may judge whether the consumer belongs in `application/` (use-case service) or `service/` (worker-side cross-cutting). Recommendation: `application/` with a thin `service/`-side adapter if Spring AOT detection forces it.
- Whether `RefreshTokenCipher` needs a fresh `allowedDependencies` edge from `core.triage`, or whether it remains gateway-only (`core.llm` already depends on it for BYOK; triage uses `LlmGateway`, which encapsulates the dependency). Recommend NO direct edge from `core.triage` to `core.gmail.persistence.crypto`.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase-specific (locked)
- `.planning/phases/04-triage-convergence-hero/04-SPEC.md` — Locked requirements (8), boundaries (in/out), constraints (privacy, latency, idempotency, tenant isolation, auto-send forbidden), acceptance criteria (13), interview log. MUST read before planning.

### Project-level (in-repo, locked)
- `CLAUDE.md` §Constraints, §Backend Code Style, §Conventions, §"Hard do not use" list — Java 25, Spring Boot 4.0.6, Spring AI 2.0.0-M5, Jackson 3 split (annotations in `com.fasterxml.jackson.annotation.*`, core/databind in `tools.jackson.*`), no Lombok, enterprise-readability naming, no email body/prompt/completion in logs, allow-listed write actions only.
- `CLAUDE.md` §Conventions #6 — Spring Modulith events for in-process after-commit side effects (message-observed → triage). Direct service calls do NOT cross `backend/api` ↔ `backend/worker` boundaries; both Phase 4 producer and consumer are inside `backend/worker`.
- `CONVENTIONS.md` — Detailed examples for thin controllers, service-owned `@Transactional`, record DTOs, JPA entities as classes, `IdentifiedEnum` + `fromId` fail-loud, privacy logging format.
- `.planning/PROJECT.md` — Trust posture ("trust is the product"), no auto-send write-action allow-list, no embeddings, single-VPS deployment baseline.
- `.planning/REQUIREMENTS.md` — `TRG-01..TRG-08` (Phase 4 requirements) plus upstream `MAIL-*`, `BILL-*`, `LLM-*`, `RULE-*` that Phase 4 consumes.
- `.planning/ROADMAP.md` — Phase 4 goal ("hero triage orchestrator"), dependency on Phase 2C, success criterion #1 (triage within a few seconds).
- `.planning/research/STACK.md` — Spring Boot 4.0.6, Hibernate 7, PostgreSQL 17.6 + Liquibase 5.0.2 YAML, Redis 7.2 (rate-limit + cache + session ONLY, NOT a queue), OpenRouter via Spring AI M5.

### Prior-phase context (decisive for this phase)
- `.planning/phases/03-rules-engine/03-CONTEXT.md` — `core.rules.domain.ActionIntent` sealed type, `ActionIntentJsonValidator` pattern, `RuleEvaluator` tri-state (`MATCHED/NOT_MATCHED/DEFERRED`), `ActionProposal` + `ActionProposalMerger`, `SemanticIntentMatcher` `{nodeId, intent, deferred}` storage shape, rule `display_order` semantics.
- `.planning/phases/02C-llm-gateway/02C-CONTEXT.md` — `LlmGateway` contract, `ActionValidator` allow-list, `SanitizationPipeline` 4-step + 3896-token cap, BYOK + credit-cap behavior, ArchUnit boundary on `core.llm.gateway.springai`, M5→GA churn caveat.
- `.planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md` — `CreditLedger.reserve → settle/release` lifecycle, `CallSite` enum membership (locked by ArchUnit; Phase 4 additions follow that pattern), `BillingProperties` extension shape for new call-site costs.
- `.planning/phases/02A-mail-ingestion/02A-CONTEXT.md` — `MailMessageObservedRepository.insertObservedIfAbsent` ON CONFLICT precedent (mirror in `TriageAuditRepository.insertAuditPendingIfAbsent`), `GmailDeliveryProcessingService.processDelivery` publish site, `GmailApiClientFactory.buildGmailClient(...)` reuse for Gmail writes, ShedLock + worker `@Scheduled` patterns.
- `.planning/phases/01-foundation-safety-infrastructure/01-CONTEXT.md` — `TenantContext` ScopedValue rebind pattern, `MultiTenantLeakIntegrationTest` regression target, Logback scrub filter, `@TenantId` Hibernate filter.
- `.planning/phases/01.2-domain-owned-persistence-restructuring/01.2-CONTEXT.md` — Per-domain Modulith package shape `{application, domain, persistence, service, exception}` — apply verbatim to `core.triage`.

### In-code anchors (current state to extend)
- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java` — Inject `ApplicationEventPublisher`; publish `MailMessageObserved` after each NEW observed row inserted.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java` — `insertObservedIfAbsent` is the idempotency template for the new `TriageAuditRepository.insertAuditPendingIfAbsent`.
- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailApiClientFactory.java` — Reused by `TriageGmailWriter` to obtain an authenticated `Gmail` client for write calls.
- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailPreviewReadService.java` — Existing Gmail metadata-only fetch pattern (`format=metadata`, `fields=`) — reuse for the orchestrator's metadata fetch and for `SenderSafetyNetService` SENT-history queries.
- `backend/core/src/main/java/com/zeromail/core/rules/domain/ActionIntent.java` — Reference for the sealed-interface shape; Phase 4 builds a SEPARATE `TriageActionResult` rather than reusing this type.
- `backend/core/src/main/java/com/zeromail/core/rules/domain/ActionIntentJsonValidator.java` — Validator pattern to mirror for `TriageActionResultJsonValidator`.
- `backend/core/src/main/java/com/zeromail/core/rules/domain/RuleActionType.java` — Source of truth for allow-list enum membership; Phase 4 adds NO new values.
- `backend/core/src/main/java/com/zeromail/core/rules/persistence/RuleEntity.java` — Hibernate 7 + `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"` precedent for `triage_audit.action_args_json` and `triage_audit.gmail_change_token`.
- `backend/core/src/main/java/com/zeromail/core/rules/service/RuleEvaluator.java` — Tri-state evaluator (`MATCHED / NOT_MATCHED / DEFERRED`); Phase 4 orchestrator resolves `DEFERRED` (semantic) before calling `ActionProposalMerger`.
- `backend/core/src/main/java/com/zeromail/core/rules/service/ActionProposalMerger.java` — Phase 4 orchestrator calls `evaluateAndMerge(...)` to obtain the final `List<ActionProposal>` per message.
- `backend/core/src/main/java/com/zeromail/core/llm/service/LlmGateway.java` — Extend with `evaluateSemanticIntents(...)`; existing `chat(...)` method stays untouched.
- `backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedger.java` — Reserve/settle/release contract; `core.triage` injects this for credit gating.
- `backend/core/src/main/java/com/zeromail/core/billing/domain/CallSite.java` — Add `TRIAGE_PLATFORM_LLM` and `TRIAGE_DETERMINISTIC`; update Phase 2B ArchUnit rule that locks enum membership.
- `backend/core/src/main/java/com/zeromail/core/tenant/` — `TenantContext.runWith(tenantId, ...)` rebind pattern for ScopedValue inside the Modulith listener.
- `backend/core/src/main/resources/db/changelog/changes/` — Floor is `024` (last committed: `023-fix-pin-calendar-category.yaml`).
- `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` — Append new includes in numbered order.
- `backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java` — ShedLock template (`@SchedulerLock(name=..., lockAtLeastFor=..., lockAtMostFor=...)`); mirror for triage event retry/cleanup, audit purge, pending reaper.
- `backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java` — `EnableSchedulerLock` is already wired; `core.triage` worker jobs piggyback on the same `LockProvider`.
- `backend/worker/src/main/java/com/zeromail/worker/ZeroMailWorkerApplication.java` — Application class that needs to scan new `com.zeromail.worker.triage` package; verify component-scan base packages cover it.
- `backend/api/src/main/java/com/zeromail/api/controllers/` — New `triage/` sub-folder for `TriageAuditController` (undo endpoint), `TriageTenantController` (shadow-mode PATCH), `SenderSafetyNetController` (list + opt-in).
- `backend/api/src/main/java/com/zeromail/api/error/GlobalExceptionHandler.java` — Add mappings for `TriageUndoExpiredException → 409 TRIAGE_UNDO_EXPIRED`, `TriageUndoAlreadyDoneException → 409 TRIAGE_UNDO_ALREADY_DONE`, `TriageUndoUnsupportedActionException → 409 TRIAGE_UNDO_UNSUPPORTED_ACTION`, `SafetyViolationException → 500 TRIAGE_SAFETY_VIOLATION`.
- `apps/web/lib/api/schema.d.ts` — Regenerated after `springdoc-openapi` task picks up new triage endpoints; consumed by Phase 5 frontend.

### External specs (re-fetch via Context7 at plan-phase)
- **Spring Modulith reference — Events** — https://docs.spring.io/spring-modulith/reference/events.html — `@ApplicationModuleListener`, `EventPublicationRegistry`, `IncompleteEventPublications.resubmitIncompletePublicationsOlderThan(...)`, `CompletedEventPublications.deletePublicationsOlderThan(...)`, `FailedEventPublications`, JDBC schema initialization properties.
- **Spring Modulith 2.0.6 release notes** — https://spring.io/blog/2026/04/24/spring-modulith-2-1-rc1-2-0-6-and-1-4-11-released/ — verify exact version bundled with Boot 4.0.6 BOM.
- **Spring Framework transaction-bound events** — https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html — `@TransactionalEventListener(phase = AFTER_COMMIT)` semantics.
- **Hibernate ORM — HQL Insert with ON CONFLICT** — verify Hibernate 7 support; Phase 4 stays on native SQL for parity with Phase 2A `insertObservedIfAbsent`.
- **Method: users.drafts.create | Gmail API** — https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.drafts/create — confirms NON-idempotent semantics; load-bearing for D-C3 two-phase rationale.
- **Method: users.messages.modify | Gmail API** — https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/modify — confirms idempotent label add/remove.
- **PostgreSQL INSERT … ON CONFLICT … RETURNING** — https://www.postgresql.org/docs/17/sql-insert.html — verify RETURNING semantics in conjunction with DO NOTHING (returns empty result set on conflict).
- **Spring AI 2.0.0-M5 — Tools / Tool Calling** — https://docs.spring.io/spring-ai/reference/api/tools.html — verify the structured-output pattern for `Map<nodeId, boolean>` return shape inside `evaluateSemanticIntents(...)`.
- **OpenRouter API docs** — https://openrouter.ai/docs — confirm batched-classification pricing model and rate limits.

### Local references
- `D:/study-materials-summer-2026/inbox-zero/` — Reference repo. Inbox Zero's triage orchestrator is `apps/web/utils/ai/choose-rule/` (TypeScript). Reference for UX patterns and audit semantics only — DO NOT port code shapes.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`GmailDeliveryProcessingService.processDelivery`** (`core.gmail.service`) — already `@Transactional`; inject `ApplicationEventPublisher` and publish `MailMessageObserved` after each new `MailMessageObservedEntity` row. No restructuring needed.
- **`MailMessageObservedRepository.insertObservedIfAbsent`** (`core.gmail.persistence`) — exact template for the new `TriageAuditRepository.insertAuditPendingIfAbsent`. Same native-SQL ON CONFLICT shape, same `RETURNING` semantics, same idempotent retry story.
- **`GmailApiClientFactory.buildGmailClient(accessToken)`** (`core.gmail.service`) — reuse to obtain authenticated `Gmail` client for `TriageGmailWriter` writes and for `SenderSafetyNetService` SENT-history metadata queries.
- **`GmailPreviewReadService`** (`core.gmail.service`) — existing metadata-only fetch pattern (`format=metadata`, `fields=id,threadId,labelIds,internalDate,payload/headers`) is exactly what the orchestrator needs for building `RuleEvaluationInput`; lift the fetch helper or expose a triage-friendly facade.
- **`RuleEvaluator`** + **`ActionProposalMerger`** (`core.rules.service`) — Phase 4 orchestrator calls these directly (in-package access via `allowedDependencies` edge); no behavior change to Phase 3 code.
- **`LlmGateway`** (`core.llm.service`) — extend with `evaluateSemanticIntents(...)`; impl inside `core.llm.gateway.springai` reuses existing `SanitizationPipeline`, `ActionValidator`, credit-reserve plumbing.
- **`CreditLedger`** (`core.billing.service`) — inject into `TriageOrchestratorService`; one reserve per LLM call + one optional reserve per pure-deterministic message.
- **`RefreshTokenCipher`** (`core.gmail.persistence.crypto`) — already used by `GmailDeliveryProcessingService` to decrypt refresh tokens; `TriageGmailWriter` flows through `GmailApiClientFactory` so no direct cipher dependency from `core.triage`.
- **`CreditReserveWatchdog`** + **`ShedLockConfig`** (`backend/worker/billing`) — ShedLock template for every Phase 4 worker job (`triageEventRetry`, `triageEventCleanup`, `triageAuditPurge`, `triagePendingReaper`).
- **`MultiTenantLeakIntegrationTest`** (Phase 1 FND-05) — regression target. Phase 4 adds the orchestrator + `evaluateSemanticIntents` + Gmail writes — all under `TenantContext`; the test must still pass after the additions.

### Established Patterns

- **Per-domain Modulith package shape** `{application, domain, persistence, service, exception}` (Phase 1.2 lock). Apply verbatim to `core.triage`. Recommended sub-packages: `core.triage.application` (`TriageOrchestratorService`, `TriageUndoService`), `core.triage.domain` (`TriageActionResult` sealed, `TriageActionResultJsonValidator`, `TriageActionArgsCanonicalizer`), `core.triage.persistence` (`TriageAuditEntity`, `TriageAuditRepository`, `TenantSenderOptInEntity`, `TenantSenderOptInRepository`), `core.triage.service` (`TriageGmailWriter`, `TriageSafetyPolicy`, `SenderSafetyNetService`), `core.triage.exception` (custom exception classes).
- **Thin controllers + service-owned `@Transactional`** (CLAUDE.md Convention #1) — `TriageAuditController`, `TriageTenantController`, `SenderSafetyNetController` are thin wrappers that map HTTP DTOs to core command records.
- **Records-for-DTOs / classes-for-entities Lombok-free** (CLAUDE.md Convention #3) — `TriageActionResult` + records, `UndoAuditCommand` record, `TriageAuditEntity` class.
- **`IdentifiedEnum` + fail-loud `fromId`** (CLAUDE.md Convention #4) — `TriageDecision` enum (PENDING/APPLIED/REVERTED/...) implements `IdentifiedEnum`.
- **Privacy logging format** (CLAUDE.md Convention #5) — every triage log line: `event=triage_* tenantId={} gmailMessageId={} ruleId={} actionType={} ...` — NO subject, NO snippet, NO sender display name, NO drafted body.
- **Native-SQL idempotent upserts** (Phase 2A) — `@Modifying @Query` with `INSERT … ON CONFLICT DO NOTHING RETURNING …`; mirror for `insertAuditPendingIfAbsent`.
- **ShedLock + `@Scheduled`** (Phase 2A + Phase 2B) — every Phase 4 worker job follows the `CreditReserveWatchdog` shape.
- **`AbstractTenantOwnedEntity`** (`core.shared.persistence`) — `TriageAuditEntity` and `TenantSenderOptInEntity` extend it for automatic `tenant_id` discriminator + audit columns + `@TenantId` filter.

### Integration Points

- **Phase 4 → Phase 2A**: `GmailDeliveryProcessingService` (in `core.gmail`) gains `ApplicationEventPublisher` injection and publishes `MailMessageObserved`. Modulith `allowedDependencies` for `core.gmail` does NOT need to declare a `core.triage` edge — events are decoupled by Modulith's registry pattern.
- **Phase 4 → Phase 3**: `core.triage` Modulith package declares `allowedDependencies` edge to `rules` for `RuleEvaluator`, `ActionProposalMerger`, `ActionProposal`, `ActionIntent`, `RuleActionType`. Loads rules in `display_order` via `RuleRepository`.
- **Phase 4 → Phase 2C**: `core.triage` declares `allowedDependencies` edge to `llm` for `LlmGateway`. The new `evaluateSemanticIntents(...)` method lives behind the existing ArchUnit boundary; impl is inside `core.llm.gateway.springai`.
- **Phase 4 → Phase 2B**: `core.triage` declares `allowedDependencies` edge to `billing` for `CreditLedger` + `CallSite`. Two new `CallSite` values added (additive enum extension; ArchUnit lock updated).
- **Phase 4 → Phase 1**: `core.triage` declares `allowedDependencies` edges to `tenant` (`TenantContext`), `shared.persistence` (`AbstractTenantOwnedEntity`), `shared.lang` (`IdentifiedEnum`).
- **`GlobalExceptionHandler`** (`backend/api/src/main/java/com/zeromail/api/error/`) — adds 4 new mappings (3× `409` undo errors + 1× `500` safety violation).
- **`springdoc-openapi` regeneration** — after Phase 4 controllers land, `pnpm generate:api` in `apps/web` refreshes `lib/api/schema.d.ts` for Phase 5 consumption.

</code_context>

<specifics>
## Specific Ideas

- Treat `triage_audit` as the user-visible source of truth for "what did Zero Mail do to my inbox" — every audit-log UI in Phase 5 reads directly from this table, so column shape and JSON payload must round-trip cleanly without lossy interpretation.
- The two-phase PENDING → APPLIED write loop should feel like a tiny mini-saga: deterministic, restartable, observable. The orchestrator log line at each phase (`event=triage_audit_reserved`, `event=triage_action_applied`, `event=triage_action_failed`) is the operational signal — keep IDs in, keep content out.
- Per-message single LLM call for SEMANTIC_INTENT is the chosen default precisely because it makes the Phase 4 cost story SIMPLE for the user: "1 triage = 1 LLM credit (at most), no matter how many of my rules use semantic matchers." The complexity of the per-rule fallback is hidden inside the orchestrator.
- Modulith event registry feels like overkill for a single in-process hop until you imagine the worker restarting mid-orchestrator — at that point the registry IS the safety net that distinguishes "trust the product" from "lost an action and the user noticed."
- The `gmail_change_token` column is explicitly "what we changed", not "what state the message was in". This phrasing should appear in `TriageAuditEntity` Javadoc so future maintainers don't try to expand it into a full label snapshot.

</specifics>

<deferred>
## Deferred Ideas

- **Cross-message batched LLM evaluation** for `SEMANTIC_INTENT` (e.g., process 10 messages in one LLM call). Locked out for v1 per SPEC; v2 cost optimization.
- **Adaptive token-budget strategy** beyond the simple "fall back to per-rule when over 3896 tokens". E.g., truncate lowest-priority rule's semantic intents, or split into two batched calls. Revisit if telemetry shows the fallback path triggers often.
- **Aggregate-rooted `@DomainEvents` on `MailMessageObservedEntity`** — requires restructuring Phase 2A's UPSERT path; not worth it for the marginal DDD purity gain.
- **Single-column `audit_id` PK as UUIDv5 (D-C1 option D)** — preserved as a planner-time alternative if the planner prefers `CreditLedger.reservationId`-style PKs. Not the default.
- **DB-level `jsonb_path_match` CHECK constraint** on `triage_audit.action_args_json` — Java validator + sealed-interface exhaustiveness already gives defense-in-depth; CHECK adds Liquibase complexity for marginal benefit.
- **Production alerting for SLO breach (>30 min p95 over 10s)** — captured as a Phase 6 hardening item per SPEC §Constraints (latency target).
- **Sender-safety-net "remove opt-in" endpoint** — opt-in is one-way in v1 per SPEC. v2 can add opt-out + cache invalidation symmetry.
- **CASA verification tier resolution** — external parallel track from Phase 1 (per ROADMAP).
- **Refresh-token-style key rotation drill for any new secrets introduced** — covered by the existing umbrella in STATE.md Blockers (Phase 2C / dedicated security-ceremony phase).
- **Per-tenant LLM cost cap independent of CreditLedger** — out of v1 (Phase 2C SPEC).

</deferred>

---

*Phase: 04-triage-convergence-hero*
*Context gathered: 2026-05-11*
