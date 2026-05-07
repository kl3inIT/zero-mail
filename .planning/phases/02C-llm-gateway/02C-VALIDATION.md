---
phase: 2C
slug: llm-gateway
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-05-07
updated: 2026-05-07 (H-3 — per-task map populated)
---

# Phase 2C — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Populated from RESEARCH.md "Validation Architecture" section. Per-task rows are
> filled in by the planner once PLAN.md files exist; VALIDATION.md is updated
> alongside plans before `/gsd-execute-phase`.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Spring Boot 4 / Spring Boot Test) + ArchUnit + Testcontainers (Postgres 17) |
| **Config file** | `backend/build.gradle.kts` (test sourcesets per module), root `gradle/libs.versions.toml` |
| **Quick run command** | `./gradlew :backend:core:test --tests "*Llm*"` |
| **Full suite command** | `./gradlew :backend:core:test :backend:api:test :backend:worker:test` |
| **Estimated runtime** | ~120 seconds (quick) / ~360 seconds (full, includes Testcontainers boot) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :backend:core:test --tests "*Llm*"` (or the module/class touched).
- **After every plan wave:** Run `./gradlew :backend:core:test :backend:api:test :backend:worker:test`.
- **Before `/gsd-verify-work`:** Full suite must be green AND ArchUnit `LlmGatewayBoundaryTest` must pass.
- **Max feedback latency:** 120 seconds for quick run.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-T1 | 01 | 1 | LLM-01 | T-2C-spurious-deps, T-2C-byok-schema-drift | Build wiring + Modulith package skeleton + Liquibase 018 schema land without dependency drift; rollback block present | unit + arch + migration | `./gradlew :backend:core:compileJava :backend:api:compileJava :backend:worker:compileJava` | ✅ no Wave 0 | ⬜ pending |
| 01-T2 | 01 | 1 | LLM-01 | T-2C-06, T-2C-byok-key-redeclaration | ArchUnit pins Spring AI / vendor SDK / jsoup / jtokkit imports; entity does NOT redeclare tenant_id; LlmTool record + stub interfaces compile | unit + arch + integration | `./gradlew :backend:core:test --tests "LlmGatewayBoundaryTest" --tests "DomainBoundaryArchTests" --tests "TenantByokCredentialsPersistenceWave0Test"` | ✅ Wave 0 stubs created | ⬜ pending |
| 02-T1 | 02 | 2 | LLM-05, LLM-06, LLM-08 | T-2C-truncate-multibyte-corruption, T-2C-pipeline-step-bypass, T-2C-jsoup-cve | Each sanitizer step (Jsoup/NFC/Unicode-tag/jtokkit) verified independently; HARD_CAP_TOKENS = 3896 named constant | unit | `./gradlew :backend:core:test --tests "JsoupHtmlStripSanitizerTest" --tests "NfcNormalizeSanitizerTest" --tests "UnicodeTagStripSanitizerTest" --tests "JtokkitTruncateSanitizerTest"` | ✅ no Wave 0 | ⬜ pending |
| 02-T2 | 02 | 2 | LLM-05, LLM-06, LLM-07, LLM-08 | T-2C-01, T-2C-05 | Pipeline orchestrator runs 4 steps in @Order; fail-fast on step exception; metadata-only log line; corpus exercises 5 prompt-injection fixtures | unit + integration | `./gradlew :backend:core:test --tests "SanitizationPipelineTest" --tests "PromptInjectionCorpusTest" --tests "SanitizationPipelineWave0Test"` | ✅ unblocks 01-T2 Wave 0 | ⬜ pending |
| 03-T1 | 03 | 3 | LLM-01, LLM-02, LLM-09 | T-2C-secret-leak-on-boot | Action enum + ToolCallResult record + LlmGateway interface + ZeroMailLlmProperties; ZEROMAIL_LLM_PLATFORM_API_KEY:? fail-fast in api+worker yml; observation log-prompt/log-completion: false pinned | unit + boot | `./gradlew :backend:core:test --tests "ActionEnumTest" --tests "ToolCallResultTest" --tests "ZeroMailLlmPropertiesTest" --tests "LlmGatewayBoundaryTest"` | ✅ no Wave 0 | ⬜ pending |
| 03-T2 | 03 | 3 | LLM-01, LLM-02, LLM-09 | T-2C-06, T-2C-05, T-2C-platform-key-cached, T-2C-cross-tenant-cache-leak | Singleton ChatClient + dynamic PlatformApiKey reading TenantContext per call; metadata-only privacy logs; 100-tenant leak test | unit + integration | `./gradlew :backend:core:test --tests "PlatformApiKeyTest" --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayMultiTenantLeakTest" --tests "LlmGatewayWave0Test" --tests "LlmGatewayBoundaryTest"` | ✅ unblocks 01-T2 LlmGateway Wave 0 | ⬜ pending |
| 04-T1 | 04 | 4 | LLM-07 | T-2C-future-enum-drift | SafetyViolationException no-content invariant (no String/Throwable ctor); ActionValidator EnumSet allow-list + fromFunctionName fail-loud | unit | `./gradlew :backend:core:test --tests "ActionValidatorTest"` | ✅ no Wave 0 | ⬜ pending |
| 04-T2 | 04 | 4 | LLM-07 | T-2C-02, T-2C-05, T-2C-toolchoice-ignored, T-2C-internalToolExecution-default-flips | Layer-1 toolChoice="required" + internalToolExecutionEnabled(false) on OpenAiChatOptions.builder() (H-5 lock); Layer-2 ActionValidator throws SafetyViolationException; mock send action rejected | unit + integration | `./gradlew :backend:core:test --tests "LlmGatewayActionValidatorTest" --tests "ActionValidatorTest" --tests "ActionValidatorWave0Test" --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayMultiTenantLeakTest"` | ✅ unblocks 01-T2 ActionValidator Wave 0 | ⬜ pending |
| 05a-T1 | 05a | 5 | LLM-03 | T-2C-03, T-2C-09, T-2C-cipher-aad-mismatch | BYOKChatModelFactory + 2 asymmetric impls + ByokEndpointValidator (H-4 SSRF allow-list) wired BEFORE client construction; LlmGatewayImpl BYOK branch decrypts envelope per call; Arrays.fill best-effort zero | unit + integration + arch | `./gradlew :backend:core:test --tests "LlmGatewayByokRoutingTest" --tests "ByokEndpointValidatorTest" --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayActionValidatorTest" --tests "LlmGatewayMultiTenantLeakTest" --tests "LlmGatewayBoundaryTest"` | ✅ no Wave 0 | ⬜ pending |
| 05b-T1 | 05b | 6 | LLM-03 | T-2C-03, T-2C-09, T-2C-byok-host-leak-in-current, T-2C-globalexceptionhandler-content-leak | ByokService.{validate,save} call ByokEndpointValidator FIRST; outbound probe never sees a rejected endpoint; current() returns metadata-only DTO; GlobalExceptionHandler maps SafetyViolation/Sanitization/InvalidByok with class-name-only logs | unit + integration | `./gradlew :backend:core:test --tests "ByokServiceTest" :backend:api:test --tests "ByokControllerIntegrationTest"` | ✅ no Wave 0 | ⬜ pending |
| 06-T1 | 06 | 6 | LLM-04, LLM-10 | T-2C-04, T-2C-credit-leak-on-safety-violation, T-2C-credit-leak-on-arbitrary-exception, T-2C-bill-byok-by-mistake, T-2C-drift-billed | Platform path reserves → settle on success / release on any failure (SafetyViolation, RuntimeException, Sanitization-pre-reserve does not consume credits); BYOK path skipped (LLM-04 via 05a); driftCheck skipped (D-E3); M-3 Micrometer counter on safety-violation release | unit + integration | `./gradlew :backend:core:test --tests "LlmGatewayCreditLifecycleTest" --tests "LlmGatewayPlatformPathTest" --tests "LlmGatewayActionValidatorTest" --tests "LlmGatewayByokRoutingTest" --tests "LlmGatewayMultiTenantLeakTest"` | ✅ no Wave 0 | ⬜ pending |
| 07-T1 | 07 | 7 | LLM-11 | T-2C-pii-in-fixture | DriftFixture + Loader + golden-set.json (≥20 synthetic, no consumer-mail domains) + golden-baseline.json align 1:1 | unit | `./gradlew :backend:worker:test --tests "DriftFixtureLoaderTest"` | ✅ no Wave 0 | ⬜ pending |
| 07-T2 | 07 | 7 | LLM-11 | T-2C-07, T-2C-fixture-content-in-log, T-2C-baseline-tampering, T-2C-cron-flag-flips-prematurely, T-2C-shedlock-bypass | DriftDetectionJob @Scheduled(cron='0 0 6 * * *') + ShedLock + Levenshtein comparator (M-6 configurable threshold via zeromail.llm.drift.threshold-percent default 20); enabled=false default; metadata-only log | unit + integration | `./gradlew :backend:worker:test --tests "DriftDetectionJobNoDriftTest" --tests "DriftDetectionJobDriftDetectedTest" --tests "DriftFixtureLoaderTest"` | ✅ no Wave 0 | ⬜ pending |
| 08-T1 | 08 | 7 | LLM-03, LLM-04, LLM-10 | T-2C-i18n-content-leak, T-2C-error-localization-shows-server-detail | schema.d.ts regenerated; api+hooks files land per PATTERNS.md; H-6 messages.ts source of truth + scripts/merge-feature-i18n.ts + pnpm i18n:build wired; vi+en lock-step STRICT pass | unit + lint | `cd apps/web && pnpm tsc --noEmit && pnpm i18n:check` | ✅ no Wave 0 | ⬜ pending |
| 08-T2 | 08 | 7 | LLM-03, LLM-04, LLM-10 | T-2C-03, T-2C-form-resubmit-leak, T-2C-toast-leaks-key, T-2C-mounted-without-auth | ByokForm uses uncontrolled <input type="password" name="apiKey">; useRef<HTMLFormElement>; raw key never enters React state / TanStack cache / localStorage / sessionStorage / cookies; raw shadcn primitives (no ByokFormCard / ValidationResultAlert wrappers); frontend-design skill invoked before write | unit + lint + arch (project-wide invariant) | `cd apps/web && pnpm tsc --noEmit && pnpm vitest run --include "features/llm/**" --include "__tests__/byok-key-handling.test.ts" && pnpm eslint . && pnpm i18n:check` | ✅ no Wave 0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

**Sampling continuity check (H-3):** every row above carries an `<automated>` command; no 3 consecutive task rows are missing automated verification. ✅

---

## Wave 0 Requirements

> Wave 0 lands the test scaffolding the rest of the phase depends on. Plan 01 ships
> stub interfaces (`SanitizationPipeline`, `LlmGateway`, `ActionValidator`) plus a
> placeholder `SanitizationContext` record so the @Disabled Wave 0 tests compile
> against fixed contracts (M-7). Plan 02/03/04 each delete-and-recreate the stubs
> as their concrete implementations and remove `@Disabled`.

- [ ] `backend/core/src/test/java/com/zeromail/core/llm/gateway/sanitization/SanitizationPipelineWave0Test.java` — `@Disabled` until Plan 02 — `pipeline.sanitize("<script>alert(1)</script>hi").content() == "hi"`
- [ ] `backend/core/src/test/java/com/zeromail/core/llm/service/LlmGatewayWave0Test.java` — `@Disabled` until Plan 03 — gateway returns ToolCallResult under TenantContext-bound mock
- [ ] `backend/core/src/test/java/com/zeromail/core/llm/service/ActionValidatorWave0Test.java` — `@Disabled` until Plan 04 — `validator.validate("send")` throws SafetyViolationException; `validator.validate("label")` returns Action.LABEL
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/LlmGatewayBoundaryTest.java` — ArchUnit rule (HIGH-1 Solution B narrow exemption — `areNotAssignableTo(LlmGatewayImpl.class)` on Spring AI + vendor SDK rules; jsoup/jtokkit rule stays strict): `org.springframework.ai..` outside `core.llm.gateway.springai` → fail (LlmGatewayImpl exempted); `com.openai.*` / `com.anthropic.*` outside same → fail (LlmGatewayImpl exempted); `org.jsoup..` / `com.knuddels.jtokkit..` outside `core.llm.gateway.sanitization` → fail (strict)
- [ ] `backend/core/src/test/java/com/zeromail/core/llm/persistence/TenantByokCredentialsPersistenceWave0Test.java` — Testcontainers Postgres + UNIQUE-constraint upsert
- [ ] `backend/core/src/test/resources/llm/prompt-injection/{html-script-tag,unicode-tag-injection,zero-width-rtl,ignore-previous-instructions,over-budget}.txt` — 5 corpus fixtures
- [ ] `backend/core/src/main/resources/llm/golden-set.json` + `golden-baseline.json` — 20-fixture drift sample

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| OpenRouter live BYOK call with real key | LLM-03 (verifies real provider integration) | Requires real third-party credentials; cannot be in CI | Set `ZEROMAIL_TEST_OPENROUTER_KEY` env var locally, run `./gradlew :backend:core:integrationTest --tests "ByokLiveRoutingIT" -PenableLiveByok` |
| Drift detection schedule (cron-time) | LLM-11 | Wall-clock dependent; CI run uses manual trigger | Trigger `DriftDetectionJob#run()` directly via Spring Boot test slice; verify Micrometer counter on injected regression fixture |
| Per-tenant daily spend cap rollover at midnight UTC | LLM-10 | Day-boundary behavior verified once with frozen clock; production is wall-clock dependent | Phase 2B `CreditLedgerService` already covers via fixed-clock test; LlmGatewayImpl integration with the ledger is verified by `LlmGatewayCreditLifecycleTest`; for prod, oncall watches Grafana `llm_safety_violation_cost_absorbed_total{tenantId}` (M-3) + Phase 2B `credit_balance` |
| Frontend Playwright walk on `/settings` BYOK card | LLM-03 (UX) | Real browser + dev server required; not in CI | `cd apps/web && pnpm dev` then walk: visit `/settings`, verify BYOK card, fill form, validate, save, observe success Alert + form reset |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (H-3 — per-task map populated)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (prompt-injection corpus, ArchUnit boundary, golden set)
- [x] No watch-mode flags
- [x] Feedback latency < 120s for quick run
- [x] `nyquist_compliant: true` set in frontmatter (H-3 closed)

**Approval:** ready for plan-checker re-run.
