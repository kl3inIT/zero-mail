---
phase: 2C
slug: llm-gateway
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-07
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
| _populated by planner_ | — | — | LLM-01..11 | T-2C-* | — | — | — | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

> Wave 0 lands the test scaffolding the rest of the phase depends on. Final list
> finalized by the planner; baseline scaffolding derived from RESEARCH.md:

- [ ] `backend/core/src/test/java/com/zeromail/llm/PromptInjectionCorpusTest.java` — golden corpus runner stub for LLM-02
- [ ] `backend/core/src/test/java/com/zeromail/llm/SanitizationPipelineTest.java` — Jsoup + NFC + tag-strip + truncate harness for LLM-02
- [ ] `backend/core/src/test/java/com/zeromail/llm/ToolCallAllowlistTest.java` — schema + allow-list rejection harness for LLM-03
- [ ] `backend/core/src/test/java/com/zeromail/llm/ByokRoutingTest.java` — per-request `ApiKey`/header override harness for LLM-04
- [ ] `backend/core/src/test/java/com/zeromail/llm/SpendCapTest.java` — Redis-backed counter harness for LLM-06/LLM-07
- [ ] `backend/core/src/test/java/com/zeromail/llm/arch/LlmGatewayBoundaryTest.java` — ArchUnit rule: nothing outside `com.zeromail.llm.gateway` may import `org.springframework.ai.chat.client.ChatClient` or vendor SDKs (LLM-01)
- [ ] `backend/core/src/test/resources/llm/prompt-injection/*.json` — golden corpus fixtures (HTML, U+E0000..U+E007F tag chars, "ignore previous instructions", base64-wrapped, RTL/zero-width)
- [ ] `backend/core/src/test/resources/llm/golden-set/*.json` — drift-detection fixed sample (LLM-10)
- [ ] `backend/core/src/test/java/com/zeromail/testsupport/TestcontainersPostgresExtension.java` — shared Postgres fixture (if not already present from Phase 2A)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| OpenRouter live BYOK call with real key | LLM-04 | Requires real third-party credentials; cannot be in CI | Set `ZEROMAIL_TEST_OPENROUTER_KEY` env var locally, run `./gradlew :backend:core:integrationTest --tests "ByokLiveRoutingIT" -PenableLiveByok` |
| Drift detection schedule (cron-time) | LLM-10 | Wall-clock dependent; CI run uses manual trigger | Trigger `LlmDriftJob#runOnce` via Spring Boot test slice; verify Micrometer counter `llm.drift.regressions` increments on injected regression fixture |
| Per-tenant daily spend cap rollover at midnight UTC | LLM-06 | Day-boundary behavior verified once with frozen clock; production behavior is wall-clock dependent | Use `Clock.fixed` in `SpendCapTimeBoundaryTest`; for prod, oncall watches Grafana `llm_spend_total{tenantId}` reset |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (prompt-injection corpus, ArchUnit boundary, golden set)
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s for quick run
- [ ] `nyquist_compliant: true` set in frontmatter once planner fills per-task rows

**Approval:** pending
