---
phase: 4
phase_dir: .planning/phases/04-triage-convergence-hero
convergence_cycle: 2
reviewers: [codex, opencode]
reviewed_at: 2026-05-11T09:39:09Z
plans_reviewed:
  - 04-00-PLAN.md
  - 04-01-PLAN.md
  - 04-02-PLAN.md
  - 04-03-PLAN.md
  - 04-04-PLAN.md
  - 04-05-PLAN.md
  - 04-06-PLAN.md
  - 04-07-PLAN.md
  - 04-08-PLAN.md
current_high: 2
---

# Cross-AI Plan Review -- Phase 4: Triage Convergence (Hero) -- Convergence Cycle 2

Reviewed by 2 AI systems: **Codex** (`gpt-5.x` via `codex exec`) and **OpenCode** (`minimax-m2.5-free`). Claude CLI not available in this environment (running inside Claude Code -- skipped for independence). OpenCode's default `nemotron-3-super-free` model returned a provider error again; review obtained via `minimax-m2.5-free` fallback.

Cycle 1 raised **6 HIGH** concerns. This cycle assessed whether the revised plans address them, plus surfaced new issues.

---

## Codex Review

**Summary**

The revised plans materially improve the cycle 1 version. Most prior HIGH issues are now addressed with concrete plan changes: `NULLS NOT DISTINCT`, a `TriageAuditWriter` validation seam, `gmail_change_token`, terminal audit inserts, protected-sender observations, and all-action sender-net gating. The main remaining risk is the audit mini-saga: the plan now names the right phases, but because `@ApplicationModuleListener` itself is transactional, the Gmail write may still run inside the listener’s ambient transaction unless explicitly suspended. The plan also still admits a `SAVE_DRAFT` duplicate-draft residual after a post-Gmail/pre-finalize crash.

**Prior HIGH Concerns — Resolution Status**

| Prior concern | Status | Evidence |
|---|---:|---|
| PENDING → Gmail → APPLIED transaction boundaries unsafe | **PARTIALLY RESOLVED** | 04-05 Task 2 introduces `TriageAuditSaga` with reserve/finalize as `REQUIRES_NEW` and Gmail write as a plain non-transactional phase. However 04-05 Task 3 also keeps `@ApplicationModuleListener`, which the plan says expands to `@Transactional(REQUIRES_NEW)`. A plain method called inside that listener still runs under the ambient listener transaction unless transaction propagation is explicitly suspended. 04-05 threat model also admits duplicate `drafts.create` can still occur after a phase-2 crash that actually succeeded. |
| Existing PENDING conflict handling can lose actions | **FULLY RESOLVED** | 04-02 Task 1 adds `attempt_count` / `last_attempt_at`; 04-02 Task 3 adds `reclaimStalePending`; 04-05 Task 2 uses stale-lease reclaim instead of skip-forever; 04-07 Task 3 adds a pending reaper. |
| `SAVE_DRAFT` idempotency hash cannot include `draftId` | **FULLY RESOLVED** | 04-02 Task 2 states `args_hash` is over pre-write intent only and asserts `SaveDraft(instr,null,thr)` hashes equal to `SaveDraft(instr,"draft-123",thr)`. 04-05 Task 2 stores `draftId` as `external_ref` after Gmail success. |
| Audit repository surface incomplete: shadow/rejected states and `gmail_change_token` | **FULLY RESOLVED, with cleanup needed** | 04-02 Task 3 adds `markApplied(auditId, tenantId, externalRef, gmailChangeToken)`, `insertAuditTerminalIfAbsent`, and `markShadowLogged`; 04-05 Task 2/3 use direct terminal inserts for `SHADOW_LOGGED`, `REJECTED_BY_SAFETY_NET`, and `REJECTED_BY_SAFETY_POLICY`. Minor inconsistency: 04-02 must-haves mention `markRejectedBySafetyNet/Policy`, but the task body intentionally uses `insertTerminal` instead. |
| Native audit insert bypasses entity validation | **FULLY RESOLVED** | 04-02 Task 3 introduces `TriageAuditWriter` as the only sanctioned native-insert path, validating and canonicalizing before `insertAuditPendingIfAbsent` / `insertAuditTerminalIfAbsent`. |
| Nullable `rule_id` breaks idempotency unique index | **FULLY RESOLVED** | 04-02 Task 1 requires a PostgreSQL `NULLS NOT DISTINCT` unique index on `(tenant_id, gmail_message_id, rule_id, action_type, args_hash)` and a Testcontainers assertion. |
| Sender-safety-net endpoint has no protected-sender source | **FULLY RESOLVED** | 04-02 Task 1 adds `tenant_protected_sender_observation`; 04-04 Task 3 upserts protected observations; 04-06 Task 2 makes `GET /api/triage/sender-safety-net` read that source joined with opt-ins. |
| Sender safety net only gates archive/save-draft | **FULLY RESOLVED** | 04-04 objective/must-haves and 04-05 Task 3 explicitly gate **all** actions, including label, when `senderProtected` is true. |

**Strengths**

- Stronger audit model: terminal rejected/shadow rows, `gmail_change_token`, `decided_at`, lease columns, and purge coverage are now planned explicitly.
- Sender safety net is now product-complete for backend/REST: protected observations, hashed Redis key, escaped Gmail query token, opt-in override, and all-action suppression.
- Test-spine revisions address the compile-RED problem by using reflection/FQN strings, so targeted test runs remain usable.
- Privacy posture is reinforced by `semanticEvalContent` constraints, hashed sender logs, prompt/completion observation disabling, and a final `TriagePrivacySweepTest`.
- Closure plan is much better: full `clean check`, no orphaned Wave-0 disables, validation sign-off, and UAT coverage labels.

**New Concerns**

- **[HIGH] Gmail writes may still run inside the listener transaction.** 04-05 Task 3 says `@ApplicationModuleListener` is transactional; 04-05 Task 2 says the Gmail phase is non-transactional, but non-transactional code invoked inside a transactional listener still participates in the ambient transaction. Use `@Transactional(propagation = NOT_SUPPORTED)` around the Gmail phase, move the listener annotation to a non-transactional adapter, or use a transaction template that suspends the listener transaction.

- **[HIGH] The 2-minute reaper/lease can mark a live Gmail attempt as FAILED.** 04-05 Task 2 uses a 2-minute lease; 04-07 Task 3 flips stale PENDING rows to FAILED at the same cutoff. If Gmail or the process stalls past 2 minutes, the reaper can mark FAILED while the original write later succeeds, leaving Gmail changed but audit non-undoable.

- **[MEDIUM] Unsupported action proposals may fail before `REJECTED_BY_SAFETY_POLICY` is written.** 04-05 Task 3 builds `TriageActionResult preWriteIntent` before calling `TriageSafetyPolicy.gate`. If a future/non-allow-listed action has no `TriageActionResult` variant, conversion can throw before the safety-policy audit row is inserted.

- **[MEDIUM] RuleActionType naming is inconsistent across plans.** 04-04 uses `{LABEL, ARCHIVE, SAVE_DRAFT}` while 04-05 uses `APPLY_LABEL` / `ARCHIVE_SKIP_INBOX`. Normalize the exact enum constants before execution.

- **[MEDIUM] `SaveDraft.draftId` storage contract is inconsistent.** 04-02 says post-write `draftId` lives in `action_args_json`, but `markApplied` only updates `external_ref`. Undo can use `external_ref`, but the JSON contract should be changed or an update added.

**Suggestions**

- Make the saga’s Gmail phase explicitly `NOT_SUPPORTED` and add a test/assertion that no transaction is active during `TriageGmailWriter` calls.
- Add a lease owner token and longer lease timeout, or have the reaper only mark rows FAILED after a much longer abandoned threshold.
- Gate action type before converting to `TriageActionResult`; for rejected unsupported actions, store a minimal safe rejected payload.
- Replace the stale `markRejectedBy*` references in 04-02 must-haves with the chosen `insertTerminal` design.
- Add a plan invariant that the listener is worker-only, or document why API cannot accidentally register the core listener.

**Risk Assessment**

Overall risk: **MEDIUM-HIGH**. Most cycle 1 structural gaps are fixed, but the transaction-suspension issue is still directly on the trust-critical Gmail write path, and the pending reaper can create false audit state under slow external calls.

Remaining HIGH count: **2**  
This includes **1 partially resolved prior HIGH** and **1 new HIGH**.


---

## OpenCode Review

# Cross-AI Plan Review — Phase 4: Triage Convergence (Hero) — Cycle 2

## Summary

The current plan set (04-00 through 04-08) demonstrates substantial revision since Cycle 1, with detailed implementation guidance for each wave. The plans now include explicit transaction boundaries for the audit saga, lease/reclaim semantics for PENDING rows, the `gmail_change_token` population in `markApplied`, full repository transition surface including all terminal decision states, the `NULLS NOT DISTINCT` index for nullable `rule_id`, and the protected sender observation persistence. However, several concerns remain partially resolved or introduce new gaps that need clarification before execution.

---

## Prior HIGH Concerns — Resolution Status

### 1. PENDING → Gmail → APPLIED inside `@ApplicationModuleListener` is transactionally unsafe

**Status:** PARTIALLY RESOLVED

**Evidence:** 04-05-PLAN.md Task 2 creates `TriageAuditSaga` as a SEPARATE `@Component` with explicit `@Transactional(propagation = REQUIRES_NEW)` methods (`reservePhase`, `finalizePhase`), and the Gmail-write phase (`gmailWritePhase`) has NO `@Transactional`. The plan explicitly states: "The transaction COMMITS here — the PENDING reservation + lease are durable before any external call" and "NO DB write happens in this phase." This addresses the transaction boundary concern. However, the plan relies on a `@Lazy` self-reference to trigger the proxy, which is an anti-pattern compared to injecting a separate saga bean directly into the orchestrator. The OpenCode review suggestion to "handle direct SHADOW_LOGGED insert in the orchestrator (preferred, avoids an extra DB row + UPDATE for shadow mode)" is implemented, but the self-reference trick is fragile.

### 2. PENDING conflict handling can lose actions (lease/attempt semantics)

**Status:** FULLY RESOLVED

**Evidence:** 04-02-PLAN.md Task 3 adds `attempt_count` and `last_attempt_at` columns to `triage_audit` (Lease columns), and defines `reclaimStalePending(UUID auditId, UUID tenantId)` in `TriageAuditRepository` with the exact semantics: "UPDATE ... SET attempt_count = attempt_count + 1, last_attempt_at = NOW() WHERE ... AND decision = 'PENDING' AND (last_attempt_at IS NULL OR last_attempt_at < NOW() - INTERVAL '2 minutes')" returning `int` (1 = lease claimed, 0 = fresh lease held). 04-05-PLAN.md Task 2 implements the three-phase logic: fresh insert → claim lease via `reclaimStalePending`; existing PENDING → try to reclaim stale; if reclaimed → attempt Gmail; if not → skip. This directly addresses the Codex HIGH concern.

### 3. SAVE_DRAFT idempotency hash cannot include `draftId`

**Status:** FULLY RESOLVED

**Evidence:** 04-02-PLAN.md Task 2 creates `TriageActionArgsCanonicalizer` with explicit Javadoc: "the hash is over pre-write action intent only — never the Gmail-returned `draftId`/`labelId`", and the canonicalizer "asserts or normalizes-away a non-null `SaveDraft.draftId` so the hash is stable across the pre-write→post-write transition." The acceptance criteria includes: "unit-asserted: `canonicalHash(SaveDraft(instr, null, thr)) == canonicalHash(SaveDraft(instr, "draft-123", thr))`." This is fully resolved.

### 4. Repository surface does not support all decisions

**Status:** FULLY RESOLVED

**Evidence:** 04-02-PLAN.md Task 3 defines the FULL transition surface in `TriageAuditRepository`: `insertAuditPendingIfAbsent`, `insertAuditTerminalIfAbsent`, `reclaimStalePending`, `markApplied(auditId, tenantId, externalRef, gmailChangeToken)`, `markFailed`, `markReverted`, `markShadowLogged`, plus NEW methods for `markRejectedBySafetyNet` and `markRejectedBySafetyPolicy`. The `TriageAuditRepositoryBoundaryArchTest` whitelist is updated to include all seven methods. Additionally, 04-05-PLAN.md Task 2 implements `recordTerminal(SagaContext, TriageDecision)` which calls `TriageAuditWriter.insertTerminal` for direct terminal inserts (shadow/rejected), so the orchestrator never needs to transition PENDING → terminal.

### 5. Native audit insert bypasses entity validation

**Status:** FULLY RESOLVED

**Evidence:** 04-02-PLAN.md Task 3 creates `TriageAuditWriter` as a `@Component` that is the ONLY sanctioned way to create triage_audit rows. The writer "validates + canonicalizes + hashes a pre-write `TriageActionResult` BEFORE calling `insertAuditPendingIfAbsent`", ensuring native SQL never bypasses validation. The entity `TriageAuditEntity` also invokes the validator in its getter: "its `getActionArgsJson()` invokes `TriageActionResultJsonValidator`." This is fully resolved.

### 6. Nullable `rule_id` breaks unique idempotency index

**Status:** FULLY RESOLVED

**Evidence:** 04-02-PLAN.md Task 1 creates `025-triage-audit.yaml` with the idempotency index declared as a raw `sql`/`createIndex` changeset using `NULLS NOT DISTINCT`: "emit it as a raw `sql`/`createIndex` changeset using `NULLS NOT DISTINCT` so NULL `rule_id` rows still conflict (PG 15+)." The acceptance criteria confirms: "a unique index on `(tenant_id, gmail_message_id, rule_id, action_type, args_hash)` declared `NULLS NOT DISTINCT`."

### 7. (Divergent) Sender safety net endpoint has no protected-sender source

**Status:** FULLY RESOLVED

**Evidence:** 04-02-PLAN.md Task 1 creates `028-tenant-protected-sender-observation.yaml` with `tenant_protected_sender_observation` table storing canonicalized sender email + counters. 04-04-PLAN.md Task 3 implements `SenderSafetyNetService.isProtected` which "upserts a `tenant_protected_sender_observation` row for `(tenantId, canonical)`" on a protected verdict, and `listProtectedSenders` returns rows from this table. 04-06-PLAN.md Task 2 maps this to `GET /api/triage/sender-safety-net`.

### 8. (Divergent) Sender safety net only gates archive/save-draft

**Status:** FULLY RESOLVED

**Evidence:** 04-04-PLAN.md Task 3 Javadoc explicitly states: "a protected verdict suppresses ALL auto-actions on the message (label/archive/save-draft), not just the destructive tiers — SPEC §req 8 point b says 'skip Gmail writes' with no per-tier carve-out." 04-05-PLAN.md Task 3 implements: "if `senderProtected` → `recordTerminal(sagaContext, TriageDecision.REJECTED_BY_SAFETY_NET)` for ANY action type (label included)." This is resolved per the SPEC text.

### 9. (NEW from OpenCode) gmail_change_token not populated on PENDING→APPLIED

**Status:** FULLY RESOLVED

**Evidence:** 04-02-PLAN.md Task 3 defines `markApplied(auditId, tenantId, externalRef, gmailChangeTokenJson)` with the signature: `@Param("gmailChangeToken") String`. 04-05-PLAN.md Task 2 implements `finalizePhase` which calls `markApplied` with "the post-write change token: `{"addedLabelId":...}` / `{"removedLabelIds":["INBOX"]}` / null". This is fully resolved.

---

## Strengths

- **Explicit three-phase audit saga** with committed REQUIRES_NEW transactions for reserve and finalize, and NO transaction around the Gmail call — this is the correct design.
- **Lease/reclaim semantics** with `attempt_count`/`last_attempt_at` columns and the `reclaimStalePending` method resolve the "lost action on PENDING conflict" concern definitively.
- **Pre-write-only args_hash** with the canonicalizer assertion ensures stable idempotency keys across the pre-write→post-write transition.
- **NULLS NOT DISTINCT** index explicitly handles nullable `rule_id` in the unique constraint.
- **Protected sender observation table** provides a real data source for the GET endpoint (TRG-08 point c).
- **All-action sender-net gating** explicitly gates labels too, per SPEC text.
- **TriageAuditWriter as mandatory validation seam** ensures no native SQL bypasses validation.
- **Wave 7 closure plan** includes a privacy sweep test analogous to FND-03.

---

## New Concerns

### HIGH

1. **04-05-PLAN Task 2 uses @Lazy self-reference to trigger REQUIRES_NEW proxy** — The plan creates `TriageAuditSaga` as a SEPARATE `@Component` (correct) but then calls `self.reservePhase(...)` / `self.finalizePhase(...)` via `@Lazy TriageAuditSaga self` to ensure the proxy fires. This is an anti-pattern — the cleaner approach is for the caller (`TriageOrchestratorService`) to inject both the orchestrator and the saga, and call saga methods directly on the injected reference. The current design is fragile and could silently degrade to self-invocation (no transaction) if the `@Lazy` is removed or if the bean is ever looked up differently.

### MEDIUM

2. **04-04-PLAN Task 3 sender-net cache key uses HEX SHA-256, not a keyed hash** — The concern stated "Use a keyed hash in the Redis key" but the plan uses `redisCacheKeyComponent(canonical)` which returns "a hex SHA-256 (not the raw address)". This is acceptable but the term "keyed hash" (e.g., HMAC) would provide better protection against rainbow-table attacks. Current implementation is reasonable but not ideal.

3. **04-05-PLAN Task 1 metadata-only fetch may not expose sanitized sender email** — The plan says "CHECK whether `RuleEvaluationInput` already exposes the sanitized FULL sender email address (not just `sanitizedSenderDomain`)" and if not, returns a wrapper. This needs verification — if `RuleEvaluationInput` doesn't carry the full sender, the wrapper approach is fine, but this is a potential gap.

4. **04-07-PLAN Task 1 failed Modulith publications retry** — The plan states "CONFIRM at execute time" whether the Spring Modulith version exposes failed publications as a distinct bean. This is a research gap left to execution time, which may cause the plan to need adjustment if the version doesn't support failed publication resubmission.

### LOW

5. **04-08-PLAN Task 2 orphaned @Disabled check may miss tests in default source sets** — The grep "for orphaned `@Disabled("Wave 0 ...")` in the triage test trees" is good, but Gradle compiles all test sources before running targeted tests per the Wave-0 contract. The plan correctly acknowledges this but the verification should be explicit.

---

## Suggestions

- **Refactor TriageAuditSaga injection** — Instead of using `@Lazy` self-reference, inject `TriageAuditSaga` directly into `TriageOrchestratorService` and call `saga.reservePhase(...)` / `saga.finalizePhase(...)` on the injected reference. This is cleaner and avoids the proxy-trigger trick.

- **Verify RuleEvaluationInput sender field** — Confirm in 04-05 Task 1 whether `RuleEvaluationInput` carries the full sanitized sender email. If not, the wrapper approach is acceptable but should be documented clearly.

- **Confirm Spring Modulith failed publication API at execute time** — Run a quick test in 04-07 to verify whether `FailedEventPublications` exists as a distinct bean or if failed publications surface through `IncompleteEventPublications`. Adjust 04-07 Task 1 accordingly.

- **Add Micrometer counter for stale-PENDING reaper flips** — 04-07 Task 3 logs `triage_pending_reaped` but should also increment a counter for observability.

---

## Risk Assessment

**Overall: MEDIUM**

The plan set is substantially stronger than Cycle 1, with all 9 prior HIGH concerns either fully resolved or addressed with acceptable trade-offs. The remaining risks are:

- **1 PARTIALLY RESOLVED** (transaction proxy via @Lazy self-reference — fragile pattern)
- **0 UNRESOLVED** prior HIGHs
- **1 NEW HIGH** (the @Lazy self-reference anti-pattern)
- **3 NEW MEDIUM** concerns (cache key hashing, metadata-only sender field verification, Modulith API confirmation deferred)

The transaction boundary design is now correct, the lease/reclaim semantics are solid, and the idempotency model is complete. The main residual risk is the fragile self-reference injection pattern in the audit saga, which could silently break if the codebase evolves. The execution-blocking research gaps (Modulith failed publications API) are low-likelihood to derail but should be verified early.

Count of HIGH concerns remaining: **1** (new HIGH - @Lazy self-reference) + **0** (unresolved prior HIGHs) + **0** (partially resolved prior HIGHs counted as unresolved) = **1**


---

## Consensus Summary

Both reviewers agree the cycle-1 -> cycle-2 revision is **substantial and effective**: 7-9 of the prior HIGH concerns (depending on how the divergent-view items are counted) are now **FULLY RESOLVED** in the current plans -- `NULLS NOT DISTINCT` idempotency index, the `TriageAuditWriter` validation seam, `gmail_change_token` plumbed through `markApplied`, terminal audit inserts for shadow/rejected states, lease/reclaim (`attempt_count` / `last_attempt_at` + `reclaimStalePending`), pre-write-only `args_hash`, the `tenant_protected_sender_observation` table behind `GET /api/triage/sender-safety-net`, and all-action sender-net gating (label included). Neither reviewer found any cycle-1 HIGH still flatly UNRESOLVED.

The disagreement is on **overall risk** and **the residual count**: Codex rates **MEDIUM-HIGH** and counts **2 remaining HIGH** (1 partially-resolved prior HIGH on the audit-saga transaction boundary + 1 new HIGH on the reaper/lease window); OpenCode rates **MEDIUM** and counts **1 remaining HIGH** (the same audit-saga concern, framed as the fragile `@Lazy` self-reference proxy trick). Taking the union of reviewer-flagged HIGHs, **2 HIGH concerns remain** before this phase is execution-ready.

### Agreed Strengths

- Multi-layer SEND prevention preserved (sealed `TriageActionResult`, exhaustive switch, runtime allow-list, ArchUnit boundary tests, `RuleActionType.SEND` existence guard).
- Privacy posture reinforced: metadata-only `MailMessageObserved` / `semanticEvalContent`, hashed sender logs, prompt/completion observation disabled, closure `TriagePrivacySweepTest`.
- Lease/reclaim semantics (`attempt_count` / `last_attempt_at` + `reclaimStalePending`) definitively close the "lost action on PENDING conflict" gap.
- Pre-write-only `args_hash` with the canonicalizer equality assertion gives stable idempotency keys across the pre-write->post-write transition.
- `NULLS NOT DISTINCT` unique index correctly handles nullable `rule_id`.
- `TriageAuditWriter` as the sole sanctioned native-insert path closes the entity-validation-bypass gap.
- `tenant_protected_sender_observation` table gives `GET /api/triage/sender-safety-net` a real protected-sender source (TRG-08); all-action gating matches the SPEC text.
- Test-spine revisions (reflection / FQN strings) keep targeted `--tests` runs usable despite the Wave-0 RED scaffold.
- Closure plan: full `clean check`, no orphaned `@Disabled` Wave-0 markers, validation sign-off, UAT with coverage labels.

### Agreed Concerns (highest priority)

1. **[HIGH] Audit-saga transaction boundary is not airtight.** Both reviewers flag the same root issue from different angles. Codex: `@ApplicationModuleListener` is itself transactional (the plan expands it to `@Transactional(REQUIRES_NEW)`), so the "non-transactional" Gmail-write phase invoked inside it still participates in the ambient listener transaction unless propagation is explicitly suspended -- fix with `@Transactional(propagation = NOT_SUPPORTED)` around the Gmail phase, or move the listener annotation to a non-transactional adapter, or use a `TransactionTemplate` that suspends. OpenCode: the `@Lazy TriageAuditSaga self` self-reference proxy trick used to fire `REQUIRES_NEW` is fragile and could silently degrade to a no-transaction self-invocation -- inject the saga bean directly into `TriageOrchestratorService` instead. The plan's own threat model still admits a duplicate `drafts.create` residual after a post-Gmail / pre-finalize crash. -> counts as **1 partially-resolved prior HIGH**.
2. **[HIGH -- Codex only] 2-minute reaper/lease can mark a live Gmail attempt FAILED.** 04-05 Task 2 uses a 2-minute lease; 04-07 Task 3's pending reaper flips stale PENDING rows to FAILED at the same cutoff. A Gmail/process stall past 2 minutes lets the reaper mark FAILED while the original write later succeeds -- Gmail changed but audit non-undoable. Fix: lease owner token + longer abandoned threshold than the retry lease, or only FAILED-flip after a much longer window. -> counts as **1 new HIGH**.

### Other Concerns (MEDIUM / LOW)

- **[MEDIUM] Unsupported-action proposals may throw before `REJECTED_BY_SAFETY_POLICY` is written** -- 04-05 Task 3 builds `TriageActionResult preWriteIntent` before `TriageSafetyPolicy.gate`; a non-allow-listed action with no `TriageActionResult` variant can throw before the safety-policy audit row exists. Gate the action *type* before conversion; store a minimal safe rejected payload. (Codex)
- **[MEDIUM] `RuleActionType` constant names inconsistent across plans** -- 04-04 uses `{LABEL, ARCHIVE, SAVE_DRAFT}`; 04-05 uses `APPLY_LABEL` / `ARCHIVE_SKIP_INBOX`. Normalize before execution. (Codex)
- **[MEDIUM] `SaveDraft.draftId` storage contract inconsistent** -- 04-02 says post-write `draftId` lives in `action_args_json`; `markApplied` only updates `external_ref`. Pick one. (Codex)
- **[MEDIUM] 04-02 must-haves still reference `markRejectedBySafetyNet/Policy` while the task body uses `insertTerminal`** -- stale reference; reconcile. (Codex; OpenCode reads the resolved `insertTerminal`/`recordTerminal` design as already in place.)
- **[MEDIUM] Sender-net Redis cache key uses hex SHA-256, not a keyed hash (HMAC)** -- acceptable, but HMAC would harden against rainbow-table lookups of known addresses. (OpenCode)
- **[MEDIUM] `RuleEvaluationInput` may not expose the full sanitized sender email** -- 04-05 Task 1 leaves this as an execute-time CHECK with a wrapper fallback; verify early. (OpenCode)
- **[MEDIUM] Spring Modulith failed-publications retry API unconfirmed** -- 04-07 Task 1 defers to execute time whether `FailedEventPublications` is a distinct bean or failures surface via `IncompleteEventPublications`; could force a plan tweak. (OpenCode; echoes a cycle-1 MEDIUM not fully closed.)
- **[LOW] Wave-0 `@Disabled` orphan check** -- Gradle compiles all test sources before targeted runs; the grep-for-orphans check is good but should be explicit. (OpenCode)

### Divergent Views

- **Overall risk:** Codex says **MEDIUM-HIGH** (the saga transaction-suspension issue is still on the trust-critical Gmail write path and the reaper can create false audit state); OpenCode says **MEDIUM** (the design is correct in shape, only the `@Lazy` injection pattern is fragile). Resolved by landing the saga-boundary fix (explicit `NOT_SUPPORTED` / direct saga injection) and the reaper-lease separation, then re-rating.
- **Reaper/lease window as a HIGH:** Codex raises it as a new HIGH; OpenCode considered lease/reclaim "fully resolved" and did not flag the FAILED-flip-vs-live-attempt race. Worth a planner decision: separate the reaper's "abandoned" threshold from the retry lease, or accept the residual.
- **Audit-saga concern severity:** Codex frames it as a *partially-resolved prior HIGH* (transaction boundary still leaky); OpenCode frames the *same* code as a *new HIGH* about the `@Lazy` anti-pattern but otherwise marks the prior boundary concern resolved. Same fix closes both framings.

---

## Recommendation

Run `/gsd-plan-phase 4 --reviews` to fold these in. Two HIGH items to land before execution:
1. Make the audit saga's Gmail-write phase explicitly transaction-suspending (`@Transactional(propagation = NOT_SUPPORTED)` or a suspending `TransactionTemplate`), and inject `TriageAuditSaga` directly into the orchestrator rather than via a `@Lazy` self-reference; add a test asserting no transaction is active during `TriageGmailWriter` calls.
2. Decouple the pending reaper's "abandoned" threshold from the 2-minute retry lease (longer window + lease owner token) so a slow-but-live Gmail attempt is never flipped to FAILED.

Then re-run `/gsd-review --phase 4` for cycle 3 to confirm `current_high` reaches 0.
