---
phase: 4
slug: triage-convergence-hero
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-11
updated: 2026-05-11
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Populated at plan-phase from RESEARCH §"Validation Architecture" + the per-task `<verify><automated>` blocks in plans 04-00..04-08. `nyquist_compliant` / `wave_0_complete` flip during/after execution (closure plan 04-08 Task 2 verifies the full suite is green and sets them).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Spring Boot Test (`spring-boot-starter-test`) + Testcontainers (`postgresql:1.21.3`, `junit-jupiter:1.21.3`) + ArchUnit (`archunit-junit5:1.4.2`) + Spring Modulith test slice (`spring-modulith-starter-test`). No standalone test config file — Gradle JUnit Platform via `zeromail.spring-boot-conventions` + `archunit-conventions` + `modulith-conventions` build plugins. |
| **Config file** | none — build plugins configure JUnit Platform; tests live in `backend/{core,api,worker}/src/test/java/...` |
| **Quick run command** | `./gradlew :backend:core:test` (or `:backend:worker:test`, `:backend:api:test`) — per-module, fast feedback |
| **Full suite command** | `./gradlew clean check` — compiles + all tests + ArchUnit + `ApplicationModulesTest` across all modules; SPEC acceptance gate |
| **Eval task** | `./gradlew :backend:core:semanticIntentEval` — offline LLM semantic-intent eval harness (recorded cassettes, `@Tag("semantic-intent-eval")`, excluded from `test`; content owned by the eval-auditor) |
| **Estimated runtime** | per-module `:test` ~minutes; `clean check` ~5–10 min (Testcontainers Postgres spin-up dominates) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :backend:<module>:test` for the module(s) the task touched (the per-task `<verify><automated>` block already does the targeted `--tests` filter).
- **After every plan wave:** Run `./gradlew :backend:core:test :backend:worker:test :backend:api:test` + `ApplicationModulesTest` + the new ArchUnit rules.
- **Before `/gsd-verify-work` (phase gate):** `./gradlew clean check` GREEN across all modules + `./gradlew :backend:core:semanticIntentEval` GREEN + the privacy sweep + FND-05 still green.
- **Max feedback latency:** ~10 min (full `clean check`); ~minutes for per-module quick runs.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 04-00-01 | 00 | 0 | TRG-01 | T-04-01-* | Spring Modulith JDBC event-registry beans on the classpath (event durability spine) | build/dependency | `./gradlew :backend:core:dependencies --configuration runtimeClasspath \| grep -q "spring-modulith-starter-jdbc" && ./gradlew :backend:worker:dependencies --configuration runtimeClasspath \| grep -q "spring-modulith-starter-jdbc"` | ✅ | ✅ green |
| 04-00-02 | 00 | 0 | TRG-01..TRG-08 | — | RED-by-design contract scaffolds referencing future Phase 4 classes (Nyquist Wave-0) | scaffold (RED) | `test -f .../gmail/event/MailMessageObservedContractTest.java && test -f .../worker/triage/TriageOrchestratorIntegrationContractTest.java && test -f .../api/.../triage/TriageUndoControllerContractTest.java && test -f .../semantic-intent-eval/README.md` | ✅ | ✅ green |
| 04-00-03 | 00 | 0 | TRG-02, TRG-03, TRG-05 | T-04-05-03 | ArchUnit guards: no Gmail `send`; only `TriageGmailWriter` calls Gmail write APIs; no UPDATE/DELETE on `triage_audit` except `markApplied`/`markFailed`/`markReverted`; `CallSite` = 5 members | ArchUnit (RED until impl) | `test -f .../arch/NoGmailSendAllowedTest.java && test -f .../arch/TriageGmailWriteBoundaryTest.java && test -f .../arch/TriageAuditRepositoryBoundaryArchTest.java && grep -q "TRIAGE_PLATFORM_LLM" .../billing/CallSiteEnumMembershipArchTest.java` | ✅ | ✅ green |
| 04-01-01 | 01 | 1 | TRG-01 | T-04-05-* | `MailMessageObserved` event published after-commit from `core.gmail`; payload = ids + timestamp only (no body/snippet/display-name) | unit + compile | `./gradlew :backend:core:compileJava && grep -q "publishEvent" .../GmailDeliveryProcessingService.java && grep -q "record MailMessageObserved" .../gmail/event/MailMessageObserved.java` | ✅ | ✅ green |
| 04-01-02 | 01 | 1 | TRG-01 | — | `event_publication` table owned by Liquibase `024` (DDL dumped from dev DB, auto-init disabled) — events persist, no Liquibase/auto-init conflict | integration (Liquibase validation) | `test -f .../changes/024-modulith-event-publication.yaml && grep -q "event_publication" .../024-modulith-event-publication.yaml && grep -q "024-modulith-event-publication" .../db.changelog-master.yaml && ./gradlew :backend:core:test --tests "*LiquibaseChangelogValidationTest"` | ✅ | ✅ green |
| 04-01-03 | 01 | 1 | TRG-01 | T-04-05-02 | `core.triage` Modulith package + `allowedDependencies` = `{rules, gmail, llm, billing, tenant, shared.*}`; `TenantContext.runWith` rebind helper | Modulith verification | `./gradlew :backend:core:compileJava && grep -q "allowedDependencies" .../triage/package-info.java && grep -q "runWith" .../tenant/TenantContext.java && ./gradlew :backend:core:test --tests "*ApplicationModulesTest"` | ✅ | ✅ green |
| 04-02-01 | 02 | 2 | TRG-04, TRG-05, TRG-07, TRG-08 | T-04-05-01 | Liquibase `025` (`triage_audit` + unique idempotency index `(tenant, message, rule, action_type, args_hash)`), `026` (`tenants.triage_shadow_mode`), `027` (`tenant_sender_opt_in`) — additive YAML, no destructive ops | integration (Liquibase) | `test -f .../changes/025-triage-audit.yaml && grep -q "ux_triage_audit_idem\|args_hash" .../025-triage-audit.yaml && grep -q "triage_shadow_mode" .../026-tenants-triage-shadow-mode.yaml && grep -q "tenant_sender_opt_in" .../027-tenant-sender-opt-in.yaml` | ✅ | ✅ green |
| 04-02-02 | 02 | 2 | TRG-05, TRG-06 | T-04-05-05 | `TriageActionResult` sealed type + JSON validator (rejects unknown discriminators/fields) + SHA-256 canonicalizer (idempotency key) + `TriageDecision` enum + triage exceptions | unit | `./gradlew :backend:core:compileJava && grep -q "sealed interface TriageActionResult" .../TriageActionResult.java && grep -q "SHA-256" .../TriageActionArgsCanonicalizer.java && ./gradlew :backend:core:test --tests "*TriageActionResultJsonValidatorContractTest"` | ✅ | ✅ green |
| 04-02-03 | 02 | 2 | TRG-04, TRG-05, TRG-07, TRG-08 | T-04-05-01 / T-04-05-02 | `TriageAuditEntity` (@TenantId) + `TriageAuditRepository` (`insertAuditPendingIfAbsent`, `markApplied`/`markFailed`, native queries with explicit tenant predicate) + `TenantSenderOptIn*` + `CallSite` → 5 members + cost property + `TenantEntity.triageShadowMode` + `TenantService` accessors | integration + ArchUnit | `./gradlew :backend:core:compileJava && grep -q "insertAuditPendingIfAbsent" .../TriageAuditRepository.java && grep -q "TRIAGE_PLATFORM_LLM" .../billing/domain/CallSite.java && ./gradlew :backend:core:test --tests "*CallSiteEnumMembershipArchTest" --tests "*TriageAuditRepositoryBoundaryArchTest" --tests "*TriageAuditPersistenceContractTest"` | ✅ | ✅ green |
| 04-03-01 | 03 | 3 | TRG-01, TRG-04 | T-04-03-03 / T-04-03-06 | `LlmGateway.evaluateSemanticIntents(CallSite, String, List<SemanticIntentRequest>)` — sanitize → pre-call token-budget check (`TokenBudgetExceededException` before any HTTP) → reserve → delegate → settle/release; `LlmGatewayImpl` has zero `org.springframework.ai.*` imports; JavaDoc names the triage caller's input (sanitized subject excerpt + content-free flag summary, no body) | unit + ArchUnit | `./gradlew :backend:core:compileJava && grep -q "evaluateSemanticIntents" .../llm/service/LlmGateway.java && grep -q "record SemanticIntentRequest" .../llm/application/SemanticIntentRequest.java && grep -Lq "org.springframework.ai" .../llm/service/LlmGatewayImpl.java && ./gradlew :backend:core:test --tests "*LlmGatewayBoundaryTest"` | ✅ | ✅ green |
| 04-03-02 | 03 | 3 | TRG-01, TRG-04 | T-04-03-01 / T-04-03-05 | `SemanticIntentEvaluator` + `SemanticIntentResponse` — strict JSON Schema (`@JsonProperty(required=true)` leaves), `temperature(0.0)`, `maxTokens(512)`, `.call().chatResponse()` (token capture), node-id set-equality validator → `SafetyViolationException` on hallucinated/missing id; all Spring AI imports confined to `core.llm.gateway.springai` | unit + ArchUnit | `./gradlew :backend:core:compileJava && grep -q "JSON_SCHEMA" .../springai/SemanticIntentEvaluator.java && grep -q "required = true" .../springai/SemanticIntentResponse.java && grep -q "SafetyViolationException" .../springai/SemanticIntentEvaluator.java && ./gradlew :backend:core:test --tests "*LlmGatewayBoundaryTest"` | ✅ | ✅ green |
| 04-03-03 | 03 | 3 | TRG-01 | T-04-03-04 | Worker model pin `openai/gpt-5.4-nano`, `spring.ai.retry.max-attempts=2`, observation prompt/completion logging stays disabled; `semanticIntentEval` Gradle task registered (offline, `@Tag("semantic-intent-eval")` excluded from `test`) | config + build | `grep -q "gpt-5.4-nano" backend/worker/src/main/resources/application.yml && grep -q "log-prompt" backend/worker/src/main/resources/application.yml && grep -q "semanticIntentEval" backend/core/build.gradle.kts && ./gradlew :backend:core:semanticIntentEval` | ✅ | ✅ green |
| 04-04-01 | 04 | 4 | TRG-02, TRG-03 | T-04-05-03 | `TriageSafetyPolicy.gate(proposal)` rejects anything outside `{APPLY_LABEL, ARCHIVE_SKIP_INBOX, SAVE_DRAFT}` → `TriageSafetyViolationException`; `GmailApiClientFactory.buildClientForTenant(...)` facade keeps the cipher inside `core.gmail` | unit | `./gradlew :backend:core:compileJava && grep -q "EnumSet" .../triage/service/TriageSafetyPolicy.java && grep -q "TriageSafetyViolationException" .../triage/service/TriageSafetyPolicy.java && grep -q "buildClientForTenant" .../gmail/service/GmailApiClientFactory.java && ./gradlew :backend:core:test --tests "*TriageSafetyPolicyContractTest"` | ✅ | ✅ green |
| 04-04-02 | 04 | 4 | TRG-03, TRG-04 | T-04-05-03 | `TriageGmailWriter` — the ONLY Gmail-write call site (forward `users.messages.modify` / `users.drafts.create` + inverse `removeLabel` / `addLabel=INBOX` / `deleteDraft`); no `send` method anywhere | unit + ArchUnit | `./gradlew :backend:core:compileJava && grep -q "modify" .../triage/service/TriageGmailWriter.java && ! grep -rq "messages().send\|drafts().send" backend/ && ./gradlew :backend:core:test --tests "*TriageGmailWriteBoundaryTest" --tests "*NoGmailSendAllowedTest"` | ✅ | ✅ green |
| 04-04-03 | 04 | 4 | TRG-08 | T-04-05-06 | `SenderSafetyNetService` — Gmail SENT metadata-only heuristic (`in:sent to:<sender> newer_than:90d`, max 3, early-exit), Redis 24h cache (`triage:sender-protect:{tenant}:{lower(sender)}`, after-commit invalidation on opt-in), opt-in override; fails SAFE on Gmail outage | unit | `./gradlew :backend:core:compileJava :backend:worker:compileJava :backend:api:compileJava && grep -q "newer_than:90d" .../triage/service/SenderSafetyNetService.java && grep -q "triage:sender-protect:" .../triage/service/SenderSafetyNetService.java && grep -q "afterCommit" .../triage/service/SenderSafetyNetService.java && ./gradlew :backend:core:test --tests "*SenderSafetyNetServiceContractTest"` | ✅ | ✅ green |
| 04-05-01 | 05 | 5 | TRG-01 | T-04-05-05 | Metadata-only triage input facade (`format=metadata, fields=id,threadId,labelIds,internalDate,payload/headers` only — no body/snippet); `GoogleJsonResponseException`/404 → log + skip (no audit row); Gmail-client construction stays in `core.gmail` | unit + compile | `./gradlew :backend:core:compileJava && (grep -q "fetchTriageInput" .../gmail/service/GmailPreviewReadService.java \|\| test -f .../triage/application/TriageRuleEvaluationInputFactory.java)` | ✅ | ✅ green |
| 04-05-02 | 05 | 5 | TRG-01, TRG-02, TRG-03, TRG-04, TRG-05, TRG-08 | T-04-05-01..07 | `TriageOrchestratorService` `@ApplicationModuleListener` — `TenantContext.runWith` rebind; `triage_paused` early-return; tri-state eval; semantic-intent resolution via `LlmGateway.evaluateSemanticIntents` passing the named `semanticEvalContent` (sanitized subject excerpt + content-free flag summary, no body) + per-rule fanout on `TokenBudgetExceededException` + `DEFERRED-(error)` → `NOT_MATCHED`; 1 reserve per LLM call / 1 per pure-deterministic msg; per-proposal `TriageSafetyPolicy` + sender-net gating; two-phase PENDING→APPLIED loop (skip Gmail when `insertAuditPendingIfAbsent` empty); SHADOW_LOGGED branch; content-free logging; v1-limitation Javadoc on the LLM input | integration (Testcontainers + Modulith scenario) + ArchUnit | `./gradlew :backend:core:compileJava :backend:worker:compileJava && grep -q "ApplicationModuleListener" .../triage/application/TriageOrchestratorService.java && grep -q "insertAuditPendingIfAbsent" .../TriageOrchestratorService.java && grep -q "evaluateSemanticIntents" .../TriageOrchestratorService.java && grep -q "semanticEvalContent" .../TriageOrchestratorService.java && ./gradlew :backend:core:test --tests "*ApplicationModulesTest" --tests "*NoGmailSendAllowedTest" && ./gradlew :backend:worker:test --tests "*TriageOrchestratorIntegrationContractTest" --tests "*TriageIdempotencyContractTest" --tests "*TriageShadowModeContractTest" --tests "*TriageCreditAccountingContractTest"` | ✅ | ✅ green |
| 04-06-01 | 06 | 5 | TRG-06 | T-04-05-03 | `TriageUndoService` — tenant-ownership check + `APPLIED` state + 30d window + exhaustive `switch` over action types → inverse Gmail call → flip `decision=REVERTED` + `reverted_at`; `409 TRIAGE_UNDO_EXPIRED` / `TRIAGE_UNDO_ALREADY_DONE` / `TRIAGE_UNDO_UNSUPPORTED_ACTION` | unit (injected `Clock`) | `./gradlew :backend:core:compileJava && grep -q "switch" .../triage/application/TriageUndoService.java && grep -q "markReverted" .../TriageUndoService.java && grep -q "Duration.ofDays(30)\|ofDays( *30 *)" .../TriageUndoService.java && ./gradlew :backend:core:test --tests "*TriageUndoServiceContractTest"` | ✅ | ✅ green |
| 04-06-02 | 06 | 5 | TRG-06, TRG-07, TRG-08 | V4 (access control) | Three thin triage controllers (`POST /api/triage/audit/{auditId}/undo`, `PATCH /api/tenant/triage/shadow-mode`, `GET /api/triage/sender-safety-net` + `POST .../{senderEmail}/opt-in`) + record DTOs + `ErrorCodes` (4 new) + `GlobalExceptionHandler` mappings + vi/en i18n keys | API test (`RestClient + @LocalServerPort`) + i18n check | `./gradlew :backend:api:compileJava && grep -q "/triage/audit/" .../controllers/triage/TriageAuditController.java && grep -q "/tenant/triage/shadow-mode\|triage/shadow-mode" .../controllers/triage/TriageTenantController.java && grep -q "TRIAGE_SAFETY_VIOLATION\|TRIAGE_UNDO_EXPIRED" .../api/error/ErrorCodes.java && grep -q "TRIAGE_UNDO" .../api/config/GlobalExceptionHandler.java && (cd apps/web && pnpm i18n:check)` | ✅ | ✅ green |
| 04-07-01 | 07 | 6 | TRG-04 | "event loss" (Repudiation/DoS) | `TriageEventRetryJob` (`resubmitIncompletePublicationsOlderThan`) + `TriageEventCleanupJob` (`deletePublicationsOlderThan(7d)`), ShedLock-coordinated — incomplete event publications resubmitted, registry table bounded | compile + grep | `./gradlew :backend:worker:compileJava && grep -q "resubmitIncompletePublicationsOlderThan" .../worker/triage/TriageEventRetryJob.java && grep -q "deletePublicationsOlderThan" .../worker/triage/TriageEventCleanupJob.java && grep -q "triageEventRetry" .../TriageEventRetryJob.java && grep -q "triageEventCleanup" .../TriageEventCleanupJob.java` | ✅ | ✅ green |
| 04-07-02 | 07 | 6 | TRG-06 | V8 (retention) | `TriageAuditPurgeJob` + `TriageAuditPurgeBatch` — bounded `LIMIT 1000` repeat-until-zero purge of `triage_audit` rows older than 30d (`decision IN (APPLIED, REVERTED)`); ShedLock; `@Transactional` on the batch (proxy boundary) | integration (Testcontainers, mocked clock) + ArchUnit | `./gradlew :backend:worker:compileJava :backend:core:compileJava && grep -q "triageAuditPurge" .../worker/triage/TriageAuditPurgeJob.java && grep -q "Transactional" .../worker/triage/TriageAuditPurgeBatch.java && grep -q "1000" .../TriageAuditPurgeJob.java && ./gradlew :backend:core:test --tests "*TriageAuditRepositoryBoundaryArchTest" && ./gradlew :backend:worker:test --tests "*TriageAuditPurgeJobContractTest"` | ✅ | ✅ green |
| 04-07-03 | 07 | 6 | TRG-04 | T-04-05-01 (residual: stuck PENDING) | `TriagePendingReaperJob` + `TriagePendingReaperBatch` — flips `triage_audit` rows stuck in `PENDING` past a TTL to `FAILED` (no Gmail re-verification); ShedLock; `@Transactional` batch — "PENDING never lives forever" | integration + Modulith verification | `./gradlew :backend:worker:compileJava :backend:core:compileJava && grep -q "triagePendingReaper" .../worker/triage/TriagePendingReaperJob.java && grep -q "Transactional" .../worker/triage/TriagePendingReaperBatch.java && grep -q "PENDING\|pending" .../TriagePendingReaperBatch.java && ./gradlew :backend:core:test --tests "*TriageAuditRepositoryBoundaryArchTest" --tests "*ApplicationModulesTest"` | ✅ | ✅ green |
| 04-08-01 | 08 | 7 | (cross-cutting) | T-04-08-01 | `TriagePrivacySweepTest` — FND-03-analogous synthetic-traffic sweep: no email body/snippet/sender-display-name/draft-body sentinel in any triage log line, `triage_audit` `reason`/`action_args_json`/`gmail_change_token`, or `triage.*` Micrometer tag; sender-net log line hashed/id-only | integration (Testcontainers + Logback `ListAppender`) | `test -f .../core/triage/TriagePrivacySweepTest.java && ./gradlew :backend:core:test --tests "*TriagePrivacySweepTest"` | ✅ | ✅ green |
| 04-08-02 | 08 | 7 | TRG-01..TRG-08 | T-04-08-02..04 | Full `./gradlew clean check` GREEN (all new ArchUnit rules + triage integration/unit/contract tests + `ApplicationModulesTest` + FND-05); no orphaned `@Disabled("Wave 0 ...")`; `REQUIREMENTS.md` TRG-01..08 → Complete; this `04-VALIDATION.md` sign-off + `nyquist_compliant: true` + `wave_0_complete: true`; `04-UAT.md` 13 scenarios | full suite + traceability | `./gradlew clean check && grep -q "TRG-01.*Complete\|TRG-01.*\[x\]" .planning/REQUIREMENTS.md && grep -q "nyquist_compliant: true" .planning/phases/04-triage-convergence-hero/04-VALIDATION.md && test -f .planning/phases/04-triage-convergence-hero/04-UAT.md` | ✅ | ✅ green |

*Closure status: every mapped task row is ✅ green and every Wave-0 scaffold file now exists; no orphaned Wave-0 `@Disabled` tests remain.*

---

## Wave 0 Requirements

The Wave-0 RED contract spine (plan 04-00) — must exist before Waves 1–7 implementation, and end GREEN by closure (04-08):

- [x] `backend/core/src/test/java/com/zeromail/core/triage/*ContractTest.java` — orchestrator / safety-policy / action-args-JSON-validator / audit-persistence / undo-service / sender-net unit + integration scaffolds (RED, referencing future production classes).
- [x] `backend/core/src/test/java/com/zeromail/core/gmail/event/MailMessageObservedContractTest.java` — payload-shape (ids + timestamp only) scaffold.
- [x] `backend/worker/src/test/java/com/zeromail/worker/triage/*ContractTest.java` — orchestrator integration (Modulith scenario), idempotency, shadow-mode, credit-accounting, audit-purge-job scaffolds (RED).
- [x] `backend/api/src/test/java/com/zeromail/api/controllers/triage/*ContractTest.java` — undo-controller, tenant/shadow-controller, sender-net-controller scaffolds (RED; `RestClient + @LocalServerPort`).
- [x] `backend/core/src/test/java/com/zeromail/core/arch/NoGmailSendAllowedTest.java` — no `users.messages.send` / `users.drafts.send` anywhere in `backend/`; `RuleActionType.SEND` absent (RED until the orchestrator/writer land their boundary).
- [x] `backend/core/src/test/java/com/zeromail/core/arch/TriageGmailWriteBoundaryTest.java` — only `TriageGmailWriter` invokes Gmail write APIs from triage code (RED).
- [x] `backend/core/src/test/java/com/zeromail/core/arch/TriageAuditRepositoryBoundaryArchTest.java` — no `delete*`/`update*` on `triage_audit` except `markApplied`/`markFailed`/`markReverted` (RED).
- [x] `backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java` — assertion bumped from 3 → 5 members (incl. `TRIAGE_PLATFORM_LLM`, `TRIAGE_DETERMINISTIC`) — RED until `CallSite` is extended (04-02).
- [x] `backend/core/build.gradle.kts` — `semanticIntentEval` Gradle test task registered (`@Tag("semantic-intent-eval")`, excluded from `test`); `backend/core/src/test/resources/semantic-intent-eval/README.md` marker (fixtures + cassettes owned by the eval-auditor per AI-SPEC §5).
- [x] `gradle/libs.versions.toml` + `backend/{core,worker}/build.gradle.kts` — `spring-modulith-starter-jdbc` dependency added (Wave-0 foundation).

*No new test framework needed — all required dependencies are already on the test classpaths (the only candidate new test dependency is a Testcontainers/embedded Redis module for the sender-net cache test; a `RedisTemplate` mock is the fallback and needs nothing new).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| (none expected) | — | Phase 4 is backend + REST only; the contract tests cover the REST surface end-to-end | — |
| (none) | — | 04-06 landed OpenAPI generation and `apps/web/lib/api/schema.d.ts` inline | Verified by `:backend:api:generateOpenApiDocs`, `pnpm generate:api`, and `pnpm i18n:check` in `04-06-SUMMARY.md` |

*Plan 04-06 landed the typed-client regeneration inline; no manual replay is required for Phase 4 closure.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies — **YES** (every task in plans 04-00..04-08 has a `<verify><automated>` block; the per-task map above mirrors them)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify — **YES**
- [x] Wave 0 covers all MISSING references — **YES** (plan 04-00 creates the RED scaffolds + ArchUnit guards + the modulith-jdbc dependency)
- [x] No watch-mode flags — **YES** (all commands are one-shot `./gradlew ...`)
- [x] Feedback latency < ~10 min (full `clean check`) — **YES**
- [x] `nyquist_compliant: true` set in frontmatter — **YES** (`./gradlew clean check` green on 2026-05-11)
- [x] `wave_0_complete: true` set in frontmatter — **YES** (no remaining Wave-0 `@Disabled` in triage/arch test trees)

**Approval:** approved 2026-05-11 (`./gradlew clean check` and `:backend:core:semanticIntentEval` both green; no orphaned Wave-0 `@Disabled` annotations remain).
