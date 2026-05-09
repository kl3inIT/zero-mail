---
phase: 03
slug: rules-engine
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-05-10
---

# Phase 03 - Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend framework** | JUnit 5, Spring Boot Test, AssertJ, Mockito, ArchUnit, Testcontainers PostgreSQL |
| **Frontend framework** | Vitest, Testing Library, Playwright |
| **Config files** | `backend/*/build.gradle.kts`, `apps/web/package.json`, `apps/web/vitest.config.ts`, `apps/web/playwright.config.ts` |
| **Quick backend command** | `./gradlew :backend:core:test --tests "com.zeromail.core.rules.*"` |
| **Quick API command** | `./gradlew :backend:api:test --tests "com.zeromail.api.controllers.rules.*"` |
| **Quick web command** | `pnpm --filter web test -- --run features/rules __tests__/rules*` |
| **Full suite command** | `./gradlew :backend:core:check :backend:api:check && pnpm --filter web lint && pnpm --filter web typecheck && pnpm --filter web i18n:check && pnpm --filter web test && pnpm --filter web test:e2e -- apps/web/e2e/rules.spec.ts` |
| **Estimated runtime** | 4-8 minutes with Testcontainers and Playwright |

## Sampling Rate

- **After every task commit:** Run the plan-specific quick command in the `verify` block.
- **After every plan wave:** Run backend/API/frontend checks for files touched in that wave.
- **Before `$gsd-verify-work`:** Full suite must be green, including real browser rules flow.
- **Max feedback latency:** 10 minutes for full phase, under 2 minutes for focused unit/API/web checks.

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-00-01 | 00 | 0 | RULE-01..07 | T-03-schema-bypass | RED tests define compile/evaluator/preview/API/UI contracts before implementation | unit/integration/ui scaffold | `./gradlew :backend:core:compileTestJava :backend:api:compileTestJava && pnpm --filter web test -- --run features/rules __tests__/rules*` | Wave 0 creates | pending |
| 03-01-01 | 01 | 1 | RULE-02, RULE-06, RULE-07 | T-03-tenant-state | Tenant-owned rules/template tables with JSONB and optimistic locking | integration/migration | `./gradlew :backend:core:test --tests "com.zeromail.core.rules.persistence.*"` | Wave 1 creates | pending |
| 03-02-01 | 02 | 1 | RULE-02 | T-03-gateway-bypass | Rule compile schema stays gateway-owned; `core.rules` has no Spring AI imports | unit/arch | `./gradlew :backend:core:test --tests "LlmGatewayBoundaryTest" --tests "RulesLlmBoundaryTest"` | Wave 1 creates | pending |
| 03-03-01 | 03 | 2 | RULE-01, RULE-02, RULE-06 | T-03-guessed-rule | Ambiguity blocks persistence; unknown nodes/actions rejected | unit/service | `./gradlew :backend:core:test --tests "com.zeromail.core.rules.service.RuleCompilerServiceTest" --tests "com.zeromail.core.rules.service.RuleManagementServiceTest"` | Wave 2 creates | pending |
| 03-04-01 | 04 | 2 | RULE-03, RULE-04 | T-03-semantic-drift | Deterministic tri-state evaluator never calls LLM | unit | `./gradlew :backend:core:test --tests "com.zeromail.core.rules.service.RuleEvaluatorTest"` | Wave 2 creates | pending |
| 03-05-01 | 05 | 2 | RULE-05 | T-03-preview-write | Preview fetches transient Gmail data and emits no writes/log content | service/integration/privacy | `./gradlew :backend:core:test --tests "com.zeromail.core.rules.service.RulePreviewServiceTest" --tests "com.zeromail.core.rules.privacy.*"` | Wave 2 creates | pending |
| 03-06-01 | 06 | 3 | RULE-07 | T-03-template-dup | Template materialization is idempotent, disabled, preserves customized rules, and reads onboarding selections only through `OnboardingService` | integration/arch | `./gradlew :backend:core:test --tests "com.zeromail.core.onboarding.service.OnboardingServiceSelectedTemplatesTest" --tests "com.zeromail.core.rules.service.RuleTemplateMaterializationServiceTest" --tests "DomainBoundaryArchTests"` | Wave 3 creates | pending |
| 03-07-01 | 07 | 3 | RULE-01..07 | T-03-api-tenant | API tenant isolation and error mapping for CRUD/compile/preview/reorder | API integration | `./gradlew :backend:api:test --tests "com.zeromail.api.controllers.rules.*"` | Wave 3 creates | pending |
| 03-08-01 | 08 | 4 | RULE-01, RULE-05, RULE-06, RULE-07 | T-03-ui-trust | Rules page supports safe create-preview-enable/manage flow in VI/EN | Vitest/Playwright | `pnpm --filter web lint && pnpm --filter web typecheck && pnpm --filter web i18n:check && pnpm --filter web test -- --run features/rules && pnpm --filter web test:e2e -- apps/web/e2e/rules.spec.ts` | Wave 4 creates | pending |
| 03-09-01 | 09 | 5 | RULE-01..07 | T-03-release-regression | Closure verifies full phase, generated OpenAPI, privacy grep, and requirements traceability | full suite | `./gradlew clean check && pnpm --filter web lint && pnpm --filter web typecheck && pnpm --filter web i18n:check && pnpm --filter web test && pnpm --filter web test:e2e -- apps/web/e2e/rules.spec.ts` | Existing plus all prior | pending |

## Wave 0 Requirements

- [ ] `backend/core/src/test/java/com/zeromail/core/rules/model/*Test.java` - matcher/action schema and semantic-deferred tests.
- [ ] `backend/core/src/test/java/com/zeromail/core/rules/persistence/*Test.java` - Liquibase/entity/repository contract scaffolds.
- [ ] `backend/core/src/test/java/com/zeromail/core/rules/service/*Test.java` - compiler, management, evaluator, preview, templates.
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/RulesBoundaryArchTest.java` - no Spring AI/vendor SDK imports in `core.rules`.
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/rules/*Test.java` - rules controller contract scaffolds.
- [ ] `apps/web/features/rules/**/*.test.tsx`, `apps/web/__tests__/rules*.test.ts`, `apps/web/e2e/rules.spec.ts` - frontend contract and browser tests.

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual density and trust feel of `/rules` | RULE-05, RULE-06, RULE-07 | Automated tests catch flow and overflow, not whether the operational UI feels inspectable | Run Playwright in desktop and mobile, inspect screenshots for UI-SPEC alignment: composer first, preview impact summary visible, no card-inside-card clutter, no text overlap. |
| Human quality of bilingual clarification prompts | RULE-01, RULE-02 | Requires bilingual judgment beyond schema compliance | Review at least 6 EN/VI ambiguous fixture outputs; prompts must ask one focused question in the authored language where possible. |

## Validation Sign-Off

- [x] All tasks have automated verify commands or Wave 0 dependencies.
- [x] Sampling continuity: no 3 consecutive implementation tasks without automated verification.
- [x] Wave 0 covers all missing phase requirements before production code lands.
- [x] No watch-mode commands in plan verify blocks.
- [x] Feedback latency target is under 10 minutes.
- [x] `nyquist_compliant: true` set in frontmatter.

**Approval:** planned 2026-05-10
