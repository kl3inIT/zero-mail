---
phase: 03-rules-engine
status: passed
updated: 2026-05-10
verification_artifact: .planning/phases/03-rules-engine/03-VERIFICATION.md
---

# Phase 03 UAT

Phase 03 UAT is complete through automated browser, API, service, and architecture evidence. No manual Gmail write UAT is included because Phase 03 explicitly forbids Gmail side effects; Phase 04 owns runtime triage writes, audit, undo, shadow mode, sender safety net, and semantic LLM evaluation.

## User Acceptance Scenarios

| Scenario | Expected behavior | Evidence | Result |
| --- | --- | --- | --- |
| Author a natural-language rule | User writes rule source text in the Rules workspace; compiled structure is review-only. | `RuleComposer`, `RulesWorkspace`, `RulesWorkspace.test.tsx`, Playwright desktop flow. | PASS |
| Clarify ambiguous rule | Compiler returns one focused clarification; UI renders it inline under the source text and does not save a guessed rule. | `RuleCompileResult`, `RuleClarificationQuestion`, `RuleCompilerServiceTest`, `RulesWorkspace.test.tsx`. | PASS |
| Save then preview before enable | Saved rules start disabled; enablement requires successful preview for the current entity version. | `RuleManagementServiceTest`, `RulesControllerIntegrationTest`, `RulePreviewPanel`, Playwright flow. | PASS |
| Preview recent messages safely | Preview shows sanitized summaries, evidence chips, action counts, deferred count, conflicts, and explicit no-write copy. | `RulePreviewServiceTest`, `RulePreviewPrivacyTest`, `RulePreviewWriteBoundaryTest`, Playwright flow. | PASS |
| Manage existing rules | User can edit, enable, disable, reorder, and delete rules without cross-tenant leakage. | `RuleManagementServiceTest`, `RulesControllerTenantIsolationTest`, `RuleList`, `use-rules.ts`. | PASS |
| Use starter templates | User sees starter templates and materializes selected onboarding templates exactly once, disabled by default. | `RuleTemplateCatalogTest`, `RuleTemplateMaterializationServiceTest`, `RuleTemplateGallery`, Playwright template scenario. | PASS |
| Mobile workspace usability | Rules page remains usable on a narrow mobile viewport without horizontal overflow. | `apps/web/e2e/rules.spec.ts` mobile Chromium scenario. | PASS |
| Privacy boundary | No raw Gmail body, prompt, completion, token bytes, Google subject, or tool arguments are persisted, logged, or returned. | `RulePreviewPrivacyTest`, `LlmRepositoryContentBanTest`, source greps in `03-VERIFICATION.md`. | PASS |

## Browser Evidence

`pnpm --filter web test:e2e -- apps/web/e2e/rules.spec.ts` passed with 4 Chromium tests after the final rerun. The covered paths are desktop create/preview/enable management, starter template interaction, mobile viewport behavior, and error/clarification placement.

## Accepted Boundaries

- UAT does not apply real Gmail labels, archive messages, or save drafts. Those are Phase 04 responsibilities.
- UAT does not run live production-model semantic intent evaluation. Phase 04 owns live semantic checks and shadow-mode validation.
- UAT does not test undo or audit history because no Phase 03 path writes Gmail actions.

## Handoff Acceptance

Phase 04 can start from this contract:

- Enabled rules are ordered and tenant-owned.
- Deterministic matcher ASTs and safe action intents are persisted under `rules.v1`.
- Preview semantics are side-effect-free and privacy-preserving.
- `SEMANTIC_INTENT` is visible and deferred, not evaluated in Phase 03.
- Gmail writes, audit, undo, shadow mode, sender safety net, and live semantic evaluation remain unimplemented by design.
