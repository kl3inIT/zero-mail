---
phase: 3
phase_dir: .planning/phases/03-rules-engine
convergence_cycle: 2
after_replanning_commit: 1694218
previous_review_commit: 294eb72
reviewers: [opencode, claude]
reviewed_at: 2026-05-10T01:54:51.4149718+07:00
plans_reviewed:
  - 03-00-PLAN.md
  - 03-01-PLAN.md
  - 03-02-PLAN.md
  - 03-03-PLAN.md
  - 03-04-PLAN.md
  - 03-05-PLAN.md
  - 03-06-PLAN.md
  - 03-07-PLAN.md
  - 03-08-PLAN.md
  - 03-09-PLAN.md
current_high: 0
---

# Cross-AI Plan Review - Phase 3, Cycle 2

Cycle 2 reviewed the current Phase 3 plans after replanning commit `1694218`, using the prior Phase 3 review history as the baseline. The requested reviewers were OpenCode and Claude.

OpenCode first attempted the configured default model (`opencode/nemotron-3-super-free`) and failed with a provider error. The review was retried through OpenCode using the configured `openai/gpt-5.4-mini` model and completed successfully.

## OpenCode Review

**Summary**

Cycle 2 closes the cycle 1 HIGHs. The updated plans now pin schema-version rejection, isolate the LLM compile contract, define edit/preview version semantics, formalize clarification handling, make body-evidence fetching explicit, and add a mechanical preview write guard.

**Strengths**

- Clear separation between compile, evaluate, preview, templates, API, and UI.
- `core.rules` stays behind `LlmGateway`; no Spring AI leakage.
- Preview privacy is now explicit and testable.
- `lastPreviewedEntityVersion` / enable gating is no longer ambiguous.
- Clarification is modeled as a distinct typed state, not an error overload.
- Template materialization race handling is now DB-backed and retry-safe.

**Concern Resolution Table**

| Prior HIGH concern (cycle 1) | Status | Current plan support |
|---|---|---|
| Unknown `schema_version` policy on rules rows | FULLY RESOLVED | `03-01-PLAN.md` Task 1/2: `RuleSchemaVersion`, fail-loud unknown-version parsing, DB check constraint, repository/service read validation |
| `ToolCallResult` refactor blast radius | FULLY RESOLVED | `03-02-PLAN.md` Task 1: adds `RuleCompileGatewayResult` and `compileRule(...)` without refactoring existing safe-action callers |
| Unspecified `lastPreviewedVersion` reset on edit | FULLY RESOLVED | `03-03-PLAN.md` Task 2: update clears `lastPreviewedEntityVersion` and disables edited rules until re-preview |
| Clarification contract was ambiguous | FULLY RESOLVED | `03-03-PLAN.md` Task 1 + `03-07-PLAN.md` Task 1 + `03-08-PLAN.md` Task 2: distinct `compiled` / `clarificationRequired` / `invalid` states, sanitized question, inline UI flow |
| Body-derived evidence boundary was underspecified | FULLY RESOLVED | `03-01-PLAN.md` Task 1, `03-04-PLAN.md` Task 1, `03-05-PLAN.md` Task 1: `requiresBodyEvidence()`, evaluator contract, preview fetch only when needed |
| Preview write-client isolation lacked mechanical enforcement | FULLY RESOLVED | `03-00-PLAN.md` Task 1 and `03-05-PLAN.md` Task 2: structural/ArchUnit/reflection guards against Gmail write dependencies |

**New Concerns**

- MEDIUM: Preview Gmail fan-out may still be latency-sensitive if batch fetch is unavailable and the sequential fallback is used on 25/50-message previews.
- LOW: Closure now depends on a deterministic AI-SPEC fixture runner if no existing runner is available, which adds one more verification artifact.

**Current Unresolved HIGH Concerns**

None.

**Current HIGH Count**

0

**Risk Assessment**

Low-Medium. No HIGH-severity blockers remain, but preview latency and closure/test-harness complexity still deserve attention during implementation.

---

## Claude Review

# Cross-AI Plan Review - Phase 3 Cycle 2

## 1. Summary

The cycle-2 replan addresses every HIGH concern from cycle 1 with concrete, locked decisions rather than hedged language. The most significant architectural change - pivoting Plan 03-02 from a `ToolCallResult` refactor to a dedicated `LlmGateway.compileRule(...)` method returning a new `RuleCompileGatewayResult` - eliminates the largest blast-radius risk and is reflected consistently across Plans 03-03/07. The clarification contract is now a tagged response (`compiled` / `clarificationRequired` / `invalid`) with bounded length (240 chars), language matching, and locked UI placement. Body-evidence handling is mechanized via `MatcherNode.requiresBodyEvidence()`. Preview write-isolation now has both a structural ArchUnit guard (Plan 03-00) and a dedicated `RulePreviewWriteBoundaryTest` (Plan 03-05). Concurrency stories (reorder, template-materialization race) have explicit locked strategies. Regex DoS risk is closed via RE2J. Gmail batch fetch + disconnected-tenant path are now in scope. The AI-SPEC >=30 reference dataset is wired into closure (Plan 03-09).

## 2. Strengths

- **Naming clash resolved**: `entityVersion` vs `lastPreviewedEntityVersion` vs `schema_version` are now distinct, both at column and DTO level (Plan 03-01, 03-07).
- **Idempotency at DB level**: partial unique index `(tenant_id, template_key) where template_key is not null` (Plan 03-01) makes template-materialization race a constraint-violation retry, not an application-level lock.
- **Mechanical privacy guards**: structural-dependency ArchUnit rule on `*PreviewService*` (Plan 03-00) plus `RulePreviewWriteBoundaryTest` (Plan 03-05) make write-isolation a compile/arch failure, not a documentation aspiration.
- **Conflict taxonomy concretized**: `RuleConflictType` enum with named cases (Plan 03-04) replaces prose.
- **Closure runs the AI-SPEC dataset**: Plan 03-09 produces `03-AI-EVAL-RESULTS.md` with >=30 EN/VI examples before flipping requirements.
- **Cross-domain boundary protected upfront**: Plan 03-01 extends `DomainBoundaryArchTests` with `rules_no_cross_domain_repos` before any rules service plan can violate it.
- **DDL/seed split**: `021-rules-engine-schema.yaml` + `022-rule-template-catalog-seed.yaml` allow seed amendments without DDL rollback.

## 3. Concern Resolution Table

| Cycle 1 HIGH Concern | Status | Evidence |
|---|---|---|
| `schema_version` unknown-version rejection / migration policy | **FULLY RESOLVED** | Plan 03-01 Task 2: `check (schema_version = 'rules.v1')` constraint + "Repository/service reads must validate `schema_version` into `RuleSchemaVersion` before returning a domain view"; Task 1: "Unknown schema versions must fail before an entity is exposed... do not silently coerce or downgrade." |
| Plan 03-02 `ToolCallResult` refactor blast radius | **FULLY RESOLVED** | Plan 03-02 Task 1: explicitly adds `LlmGateway.compileRule(...)` returning new `RuleCompileGatewayResult`, "Do not refactor `ToolCallResult` beyond compatibility tests proving its existing constructor and `action()` behavior still work." `Action` enum membership unchanged. `ToolCallResultCompatibilityTest` added. |
| Plan 03-03 `lastPreviewedVersion` reset rule on edit | **FULLY RESOLVED** | Plan 03-03 Task 2: "update source/compiled matcher/action state, increment Hibernate `version`, set `lastPreviewedEntityVersion=null`, set `lastPreviewedAt=null`, and set `enabled=false`". Customization rule locked: only source-text/matcher/action edits set `customized=true`. |
| Clarification-required contract (response shape, error distinction, payload safety, language matching) | **FULLY RESOLVED** | Plan 03-02 Task 2: `clarificationRequired` field in schema; Plan 03-03 Task 1: `RuleClarificationQuestion` <=240 chars, sanitized, language-matched, distinct from `invalid`; Plan 03-07 Task 1: `RuleCompileResponse` tagged record with `status` in {`compiled`,`clarificationRequired`,`invalid`}; Plan 03-08 Task 2: clarification UI placement locked (inline under textarea, single answer field, original source preserved). |
| Preview body-derived evidence boundary | **FULLY RESOLVED** | Plan 03-01 Task 1: `MatcherNode.requiresBodyEvidence()` on sealed interface, header matchers return false, semantic remains deferred; Plan 03-04 Task 1: evaluator uses the flag; Plan 03-05 Task 1: data service computes `requiresBodyEvidence` from compiled AST upfront and conditionally fetches. |
| Preview write-client isolation mechanically enforced | **FULLY RESOLVED** | Plan 03-00 Task 1 item 9: structural ArchUnit rule on `*PreviewService*` field/constructor types matches `*Gmail*Write*`/`*Action*Executor*`; Plan 03-05 Task 2: dedicated `RulePreviewWriteBoundaryTest` with source/ArchUnit/reflection checks; behavior fail-if-called fake retained. |

## 4. New Concerns

- **MEDIUM - AI-SPEC dataset uses synthetic/captured gateway outputs, not live model**: Plan 03-09 Task 1 explicitly says "create a small deterministic JUnit/fixture runner that invokes the rule compile validator against captured/synthetic gateway outputs... does not call a live LLM." This is reasonable for closure cost/repeatability, but the resulting `03-AI-EVAL-RESULTS.md` measures validator behavior on labeled fixtures, not real model intent fidelity. Phase 4 should not treat the 30-example pass rate as evidence of compile quality against the production model. Recommend documenting this scope distinction explicitly in `03-AI-EVAL-RESULTS.md` so reviewers do not conflate "validator passes 30 fixtures" with "model compiles user intent correctly."
- **LOW - Plan 03-00 disabled scaffolds avoid direct imports of future symbols**: workable, but means the disabled tests are essentially comment-block placeholders until Plan 01 lands. The compile-time signal cycle 1 wanted is now weaker. Acceptable trade-off given the active ArchUnit/source-scan guards remain.
- **LOW - Plan 03-08 textarea primitive guidance**: "Add official `textarea` via `pnpm dlx shadcn@latest add textarea` only if no local textarea primitive exists." A quick repo grep before installing is implied but not stated; executor should confirm before adding the primitive to avoid duplicate installs.
- **LOW - Plan 03-09 verification uses `gsd-sdk query check.decision-coverage-plan`**: assumes this CLI subcommand exists in the project's GSD SDK version. If it does not resolve, closure verification could silently no-op. Worth verifying before Plan 03-09 runs.

## 5. Current Unresolved HIGH Concerns

None. All six prior HIGH concerns are FULLY RESOLVED, and no newly raised HIGH concerns emerged in cycle 2.

## 6. Current HIGH Count

**0**

## 7. Risk Assessment

**LOW-MEDIUM.** The cycle-2 replan converted every cycle-1 HIGH into a locked decision with mechanical enforcement (ArchUnit, partial unique index, RE2J, bounded clarification payload, full-list optimistic reorder, disconnected-tenant exception path). The remaining risks are execution-quality concerns rather than architecture or contract gaps: (a) the AI-SPEC eval measures validator behavior, not model behavior, so Phase 4 must independently validate compile fidelity against the live gateway; (b) Wave 0 scaffolds are weaker as compile-time gates than the prior plan implied. Neither is a blocker. The privacy posture, gateway boundary, tri-state evaluator, preview-before-enable invariant, and template-materialization idempotency are all enforced at multiple layers (DB constraint, ArchUnit, Java validation, integration tests). Phase 3 is ready to execute.

---

## Consensus Summary

Both reviewers agree that the cycle 2 replan resolves the six HIGH concerns from cycle 1. The prior blockers were not merely acknowledged; the updated plans now include concrete plan-level decisions, tests, or closure artifacts for each concern.

### Agreed Strengths

- Plan 03-02 now isolates rule compilation behind `LlmGateway.compileRule(...)` and `RuleCompileGatewayResult`, avoiding a risky public `ToolCallResult` refactor.
- Plans 03-01, 03-04, and 03-05 make body-derived preview evidence explicit through `MatcherNode.requiresBodyEvidence()`.
- Plans 03-00 and 03-05 add mechanical guards that fail if preview services depend on Gmail write clients or action executors.
- Plan 03-03 and Plan 03-07 distinguish `compiled`, `clarificationRequired`, and `invalid` results instead of overloading validation errors.
- Plan 03-03 locks the preview-before-enable state transition: edited rules clear `lastPreviewedEntityVersion`, clear `lastPreviewedAt`, and disable the rule until a fresh preview succeeds.
- Plan 03-01 adds fail-loud schema-version handling and a database check constraint for `rules.v1`.

### Agreed Concerns

- Preview latency remains worth watching when Gmail batch fetch is unavailable and bounded sequential fallback is used.
- Closure now includes an AI-SPEC fixture/evaluation artifact; reviewers noted that this measures validator behavior unless live model evaluation is explicitly added later.

### Divergent Views

- OpenCode framed the remaining closure fixture runner as a LOW concern, while Claude rated the synthetic/captured gateway-output scope distinction as MEDIUM.
- Claude called out a few implementation hygiene details not raised by OpenCode: avoiding duplicate shadcn textarea installation and verifying the `gsd-sdk query check.decision-coverage-plan` command before Plan 03-09 relies on it.

### Previous HIGH Resolution

| Previous HIGH | Cycle 2 status | Basis |
|---|---|---|
| `schema_version` exists on rows but no unknown-version rejection or migration policy | FULLY RESOLVED | `03-01-PLAN.md` defines `RuleSchemaVersion`, fail-loud unknown parsing, Java read validation, and a `rules.v1` database check constraint. |
| Plan 03-02 `ToolCallResult` refactor blast radius | FULLY RESOLVED | `03-02-PLAN.md` avoids the refactor by adding `LlmGateway.compileRule(...)` and `RuleCompileGatewayResult`; compatibility tests keep existing safe-action behavior intact. |
| Plan 03-03 ambiguous `lastPreviewedVersion` update/reset semantics | FULLY RESOLVED | `03-03-PLAN.md` sets `lastPreviewedEntityVersion=null`, `lastPreviewedAt=null`, and `enabled=false` on source/matcher/action edits, then marks the current version previewed only after successful preview. |
| Clarification-required response shape, UI handling, payload safety, and language matching incomplete | FULLY RESOLVED | `03-03-PLAN.md`, `03-07-PLAN.md`, and `03-08-PLAN.md` define a distinct clarification state, sanitized single-question payload, frontend status handling, inline placement, and preserved original source. |
| Body-derived evidence boundary incomplete | FULLY RESOLVED | `03-01-PLAN.md`, `03-04-PLAN.md`, and `03-05-PLAN.md` add `MatcherNode.requiresBodyEvidence()` and conditionally fetch transient sanitized body-derived evidence only when the AST requires it. |
| Preview write-client isolation asserted but not mechanically guarded | FULLY RESOLVED | `03-00-PLAN.md` adds a structural preview guard; `03-05-PLAN.md` adds `RulePreviewWriteBoundaryTest` with source/ArchUnit/reflection checks and fail-if-called behavior tests. |

### Current Unresolved HIGH Concerns

None.

### Current HIGH Count

0

## Review Prompt Inputs

- Current plans: `03-00-PLAN.md` through `03-09-PLAN.md`
- Context: `03-CONTEXT.md`
- Research: `03-RESEARCH.md`
- Prior review history: previous `03-REVIEWS.md` from before replanning commit `1694218`
- Roadmap and requirements: `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md`
