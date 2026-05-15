---
status: passed
phase: 04-triage-convergence-hero
source: [04-SPEC.md, 04-VALIDATION.md]
started: 2026-05-11
updated: 2026-05-11
---

# Phase 4 UAT - Triage Convergence Hero

Phase 4 is backend + REST only. All 13 SPEC acceptance criteria are covered by the green automated suite; UI-facing behaviors are intentionally not manually UATable until Phase 5 builds the triage audit log, undo button, shadow-mode toggle, and sender safety-net screens. REST endpoint behaviors are manually possible with curl/RestClient, but no manual UAT step is required for Phase 4 closure.

## Scenarios

| # | Scenario | Steps | Expected | Coverage | Status |
|---|----------|-------|----------|----------|--------|
| 1 | Mail observed event reaches triage | Commit a new observed Gmail message for a tenant with enabled rules. | `MailMessageObserved` is published after commit and consumed by `core.triage` in the worker. | automated: YES / manual: NO - `TriageOrchestratorIntegrationContractTest`, `MailMessageObservedContractTest`, `./gradlew clean check` | ✅ |
| 2 | Rule order and semantic intent resolution | Run triage with two enabled rules including semantic-intent matchers. | Rules are evaluated in `display_order`; semantic intent goes through `LlmGateway`; proposal output matches the control contract. | automated: YES / manual: NO - `TriageOrchestratorContractTest`, `TriageCreditAccountingContractTest`, `:backend:core:semanticIntentEval` | ✅ |
| 3 | Safety policy rejects non-allow-listed action | Present an action outside label/archive/save-draft. | Action is rejected, logged without content, and recorded as `REJECTED_BY_SAFETY_POLICY`; no Gmail write happens. | automated: YES / manual: NO - `TriageSafetyPolicyContractTest`, `TriageOrchestratorContractTest` | ✅ |
| 4 | Auto-send is impossible | Search backend Gmail call sites and triage action types. | No `users.messages.send` or `users.drafts.send` call site exists; `RuleActionType.SEND` remains absent. | automated: YES / manual: NO - `NoGmailSendAllowedTest`, `./gradlew clean check` | ✅ |
| 5 | Gmail write boundary is constrained | Inspect triage Gmail write callers. | Only `TriageGmailWriter` invokes Gmail write APIs from triage code. | automated: YES / manual: NO - `TriageGmailWriteBoundaryTest` | ✅ |
| 6 | Audit row exists for every decision | Run applied, shadow, safety-net-rejected, and policy-rejected triage paths. | `triage_audit` rows exist with valid action JSON, tenant scope, provenance, and idempotency key. | automated: YES / manual: NO - `TriageAuditPersistenceContractTest`, `TriageAuditRepositoryBoundaryArchTest`, `TriageShadowModeContractTest` | ✅ |
| 7 | Undo REST endpoint enforces state and window | Call `POST /api/triage/audit/{auditId}/undo` for valid, expired, and already-reverted rows. | Valid undo reverts Gmail state and marks `REVERTED`; expired/already-done return 409 with stable error codes. | automated: YES / manual: POSSIBLE - `TriageUndoServiceContractTest`, `TriageUndoControllerContractTest` | ✅ |
| 8 | Audit retention purge removes expired rows | Seed old eligible triage audit rows and run the purge job. | Rows past the retention window are deleted in bounded batches; current rows remain. | automated: YES / manual: NO - `TriageAuditPurgeJobContractTest` | ✅ |
| 9 | Shadow mode skips Gmail writes | Toggle tenant shadow mode on and run triage. | Audit decision is `SHADOW_LOGGED`; Gmail writer is not invoked. Toggle off and the next message applies normally. | automated: YES / manual: POSSIBLE - `TriageShadowModeContractTest`, `TriageTenantControllerContractTest` | ✅ |
| 10 | Sender safety net protects frequent correspondents | Seed sent-history count >= 3 in 90 days for a sender and run triage. | Sender is protected, decision is `REJECTED_BY_SAFETY_NET`, Redis caches the hashed key, and opt-in allows the next action. | automated: YES / manual: POSSIBLE - `SenderSafetyNetServiceContractTest`, `SenderSafetyNetControllerContractTest` | ✅ |
| 11 | Privacy sweep catches content bleed | Run synthetic triage with sentinel subject/snippet/sender-display-name/prompt/completion tokens. | No sentinel appears in triage logs, `triage_audit` content, Gmail change tokens, or Micrometer tags; sender logs are hashed/id-only. | automated: YES / manual: NO - `TriagePrivacySweepTest` | ✅ |
| 12 | Multi-tenant isolation still holds | Re-run Phase 1 tenant leak regression after Phase 4 additions. | Tenant-scoped repositories and native triage queries do not leak cross-tenant data. | automated: YES / manual: NO - `MultiTenantLeakIntegrationTest`, `./gradlew clean check` | ✅ |
| 13 | Full phase gate is green | Run the full build and verification suite. | `./gradlew clean check` succeeds across backend core/api/worker; ArchUnit, Modulith, integration, contract, privacy, and worker jobs all pass. | automated: YES / manual: NO - `./gradlew clean check --console=plain`, `:backend:core:semanticIntentEval` | ✅ |

## Summary

total: 13
passed: 13
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

None.
