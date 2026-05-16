# Phase 04: triage-convergence-hero - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-11
**Phase:** 04-triage-convergence-hero
**Areas discussed:** Event publish + consumer, Audit JSON schema + undo state, Idempotency unique-index design, SEMANTIC_INTENT batching scope

**Mode:** Advisor (research-backed comparison tables, calibration tier = `full_maturity` from `Vendor Choices: thorough-evaluator`).

---

## Area 1 — Event publish + consumer mechanics

| Option | Description | Selected |
|--------|-------------|----------|
| A | Sync `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` — same-thread, no persistent registry | |
| B | Async `ApplicationEventPublisher` + `@Async @TransactionalEventListener(AFTER_COMMIT)` — decoupled but silent event loss on JVM crash | |
| **C** | **`@ApplicationModuleListener` + Spring Modulith JDBC Event Publication Registry — persistent registry, atomic with insert tx, ShedLock-driven retry** | **✓** |
| D | `@DomainEvents` on aggregate root + `@ApplicationModuleListener` — auto-publish on `save()` (incompatible with Phase 2A native UPSERT) | |
| E | Modulith `@Externalized` (Kafka/RabbitMQ) — cross-process; rejected by SPEC | |

**User's choice:** C — Modulith JDBC Event Publication Registry.
**Notes:** Aligns with CLAUDE.md Convention #6 verbatim. The persistent registry guarantees no silent event loss on JVM crash, which is the load-bearing safety property for the hero triage path. Sub-decisions accepted from the research recommendation: event record in `core.gmail.event` package, publish from `GmailDeliveryProcessingService.processDelivery`, consumer in `core.triage.application` annotated `@ApplicationModuleListener`, retry + cleanup via existing ShedLock infra, Liquibase changelog ships explicit (no auto-init).

---

## Area 2 — Audit JSON schema + undo state

| Option | Description | Selected |
|--------|-------------|----------|
| A | Reuse `core.rules.domain.ActionIntent` sealed type — less code but missing `draftId` for SAVE_DRAFT undo | |
| **B** | **New sealed `TriageActionResult` (Label/Archive/SaveDraft) + dedicated `TriageActionResultJsonValidator` + JSONB-as-String** | **✓** |
| C | Jackson polymorphic `@JsonTypeInfo` + `@JsonSubTypes` — fragments codebase pattern (`ActionIntentJsonValidator` is the locked precedent) | |
| D | Per-action `@Embeddable` + dedicated columns — kills schema evolution, breaks INSERT-only audit philosophy | |
| E | Free-form `Map<String,Object>` — loses sealed-interface compile-time exhaustiveness | |

**User's choice:** B — New sealed `TriageActionResult` + validator.
**Notes:** All sub-decisions in the locked-in table accepted: APPLY_LABEL stores `{labelId, labelName}`; ARCHIVE empty body with `gmail_change_token` carrying the removed INBOX token; SAVE_DRAFT stores `{instruction, draftId, threadId}` (NO drafted body — privacy); `gmail_change_token` is a top-level JSONB column (not nested); validation = Java validator + sealed `switch` in `TriageUndoService.computeInverse`, NO DB CHECK; strict-write / lenient-read forward-compat policy.

---

## Area 3 — Idempotency unique-index + write-order strategy

Two coupled sub-decisions.

### Sub-table 1: Unique-key shape

| Option | Description | Selected |
|--------|-------------|----------|
| A | `(tenant_id, gmail_message_id, rule_id, action_type)` — SPEC current; collapses 1 rule × 2 labels | |
| **B** | **`(tenant_id, gmail_message_id, rule_id, action_type, args_hash)` — SHA-256(canonicalJson(args)) BYTEA(32)** | **✓** |
| C | `(tenant_id, gmail_message_id, rule_id, action_type, action_args_json)` — full JSONB index, fragile | |
| D | UUIDv5 PK over canonical bytes — mirrors `CreditLedger.reservationId` (preserved as planner alternative) | |

### Sub-table 2: Write-order strategy

| Option | Description | Selected |
|--------|-------------|----------|
| X | SELECT-then-INSERT — race window | |
| Y | `INSERT ON CONFLICT DO NOTHING RETURNING *` (one-shot) — OK for idempotent Gmail endpoints, fails SAVE_DRAFT | |
| **Z** | **Reserve PENDING row → call Gmail → UPDATE APPLIED (two-phase) — only path safe for non-idempotent `users.drafts.create`** | **✓** |
| W | Gmail-first then INSERT — duplicate visible drafts on retry; rejected | |

**User's choice:** B + Z — args_hash column + PENDING→APPLIED two-phase.
**Notes:** Decision enum extends SPEC's list: `PENDING / APPLIED / SHADOW_LOGGED / REJECTED_BY_SAFETY_NET / REJECTED_BY_SAFETY_POLICY / FAILED / REVERTED`. `PENDING` is a transient state — the ArchUnit "no UPDATE/DELETE outside revert" rule must whitelist `PENDING → APPLIED / FAILED`. A `triagePendingReaper` worker job handles stuck `PENDING` rows from JVM crashes between Gmail call and final UPDATE. Shared `TriageActionArgsCanonicalizer` utility lives in `core.triage.domain` so writers cannot drift on canonicalization.

---

## Area 4 — SEMANTIC_INTENT batching scope

| Option | Description | Selected |
|--------|-------------|----------|
| A | Per-node sequential — 6 calls × 1-4s = 12-24s; blows p95 SLO | |
| B | Per-rule batched — 1 call per rule, parallel via virtual threads (advisor recommendation) | |
| **C** | **Per-message all-rules batched — 1 LLM call per message; cheapest + lowest latency; wider injection blast radius accepted** | **✓** |
| D | Adaptive hybrid (C if ≤ K nodes else B) — deferred to post-ship metrics | |
| E | Per-node parallel via virtual threads — 6× credit cost; OpenRouter rate-limit risk | |

**User's choice:** C — Per-message all-rules batched. (Deviation from research recommendation B; explicit user pick accepting wider injection blast radius for cleaner cost/latency story.)

**Follow-up sub-decisions:**

| Sub-question | Options | Selected |
|--------------|---------|----------|
| Token-cap fallback (when content + intents > 3896 token budget) | (i) **Fall back to per-rule B for that message** / (ii) Truncate semantic-node list / (iii) Fail-fast skip all semantic eval | **(i)** |
| Credit reserve granularity | (i) **1 reserve per message (match actual LLM spend)** / (ii) N reserves per rule-with-semantic | **(i)** |

**Notes:** User explicitly chose option C against the research recommendation, with rationale that the cost story is simpler and per-tenant rule count stays small in v1. Fallback to per-rule (option B) only when 3896-token budget exceeded preserves graceful degradation without losing semantic evaluation. 1 reserve per LLM call matches OpenRouter spend reality; pure-deterministic messages consume `CallSite.TRIAGE_DETERMINISTIC` with a near-zero configurable unit. Failure semantics: LlmGateway retries once internally; if still fails, mark all semantic nodes for that message as DEFERRED-(error), action-gating treats them as NOT_MATCHED — NO new top-level `decision=FAILED` from semantic failures alone.

---

## Claude's Discretion

The following sub-decisions were deliberately left to the planner / executor within the constraints documented in CONTEXT.md §D-E (Operational mechanics) and §Claude's Discretion:

- Exact API DTO field names for new endpoints.
- Exact i18n message-key spelling for new error codes (vi + en).
- Default cost for `CallSite.TRIAGE_DETERMINISTIC` in `BillingProperties`.
- Spring Modulith `allowedDependencies` literal list for `core.triage` package-info.
- Whether `TriageOrchestratorService` lives in `application/` (recommended) or `service/`.
- Whether `RefreshTokenCipher` needs a fresh `allowedDependencies` edge from `core.triage` (recommend NO).
- Exact `lockAtLeastFor` / `lockAtMostFor` values for each Phase 4 ShedLock job.
- Exact Liquibase changelog slot numbers (floor is `024`).
- Tightening / loosening the 7s / 6s LLM-call timeout budget within the 10s p95 envelope.

## Deferred Ideas

Captured in CONTEXT.md `<deferred>` section. Highlights:

- Cross-message batched LLM evaluation (v2 cost optimization).
- Adaptive token-budget strategy beyond simple per-rule fallback.
- Aggregate-rooted `@DomainEvents` on `MailMessageObservedEntity`.
- Single-column UUIDv5 `audit_id` PK (planner alternative).
- DB-level `jsonb_path_match` CHECK constraint on `action_args_json`.
- Production alerting for SLO breach (Phase 6).
- Sender-safety-net "remove opt-in" endpoint (v2).
- Per-tenant LLM cost cap independent of CreditLedger (out of v1).
