---
phase: 09
slug: user-settings-ui-on-curated-catalog
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-26
revised: 2026-05-26 (planner-checker iteration 1 — added AssistantKnowledgeAppendCallSiteTest per BLOCKER #1; corrected stub count per INFO #8)
---

# Phase 09 — Validation Strategy

> Per-phase validation contract derived from `09-RESEARCH.md` § Validation Architecture. Source of truth for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend framework** | JUnit 5 (Jupiter) + AssertJ + Mockito + Testcontainers Postgres (TESTING.md §3). Base classes: `PostgresContainerTest`, `ApiPostgresTestBase`, `TestSessionSupport.TestSessionMinter` |
| **Frontend unit framework** | Vitest 4 (`apps/web/__tests__/**` + per-feature `*.test.tsx`) |
| **Frontend e2e framework** | Playwright 1.60 (`apps/web/e2e/**`) |
| **Backend quick run** | `./gradlew :backend:core:test :backend:api:test --tests "*Settings*" --tests "*Knowledge*" --tests "*VoiceGeneration*" --tests "*Byok*"` |
| **Backend full suite** | `./gradlew test` |
| **Frontend quick run** | `pnpm --filter web test --run features/ai features/knowledge` |
| **E2E run** | `pnpm --filter web e2e -- ai-settings.spec.ts` |
| **Estimated runtime (quick)** | ~90 seconds |
| **Estimated runtime (full)** | ~6–8 minutes |

---

## Sampling Rate

- **After every task commit:** Run the smallest matching slice command from the Per-Task table.
- **After every plan wave:** Run the backend wave-merge bundle + `pnpm --filter web test --run features/ai features/knowledge`.
- **Before `/gsd:verify-work`:** Full backend suite + Playwright e2e must be green.
- **Max feedback latency:** ~90 seconds (quick) / ~8 minutes (wave merge).

---

## Per-Task Verification Map

> Sourced verbatim from `09-RESEARCH.md` § Validation Architecture. The planner MUST attach the matching automated command(s) to each task via `<automated>` blocks.

| Req ID | Behavior | Test Type | Automated Command | File Exists | Status |
|--------|----------|-----------|-------------------|-------------|--------|
| SET-VOICE-01 | writing_style 200–500 word bounds enforced | unit (validator) | `./gradlew :backend:core:test --tests SettingsVoiceServiceWordBoundsTest` | ❌ Wave 0 | ⬜ pending |
| SET-VOICE-01 | PUT /api/settings/voice returns 200 + persists | mvc slice | `./gradlew :backend:api:test --tests SettingsVoiceControllerTest` | ❌ Wave 0 | ⬜ pending |
| SET-VOICE-02 | sanitizer single-call invariant | ArchUnit | `./gradlew :backend:core:test --tests PersonalizationSanitizerSingleCallSiteTest` | ❌ Wave 0 | ⬜ pending |
| SET-VOICE-02 | sentinel `[SYSTEM]` removed from persisted value | unit | `./gradlew :backend:core:test --tests PersonalizationSanitizerCorpusTest` | ✅ existing | ⬜ pending |
| SET-VOICE-03 | signature appears verbatim in next draft | integration | `./gradlew :backend:core:test --tests DraftSignatureIntegrationTest` | ❌ Wave 0 | ⬜ pending |
| SET-VOICE-04 | UNIQUE(tenant_id,title) returns 409 | `@DataJpaTest` | `./gradlew :backend:core:test --tests AssistantKnowledgeMemoryUniqueTitleTest` | ❌ Wave 0 | ⬜ pending |
| SET-VOICE-04 | cross-tenant delete returns 404 | mvc slice | `./gradlew :backend:api:test --tests KnowledgeSnippetControllerTenantIsolationTest` | ❌ Wave 0 | ⬜ pending |
| SET-VOICE-04 | only AssistantKnowledgeService writes to AssistantKnowledgeMemoryRepository.save (repo write-site rule) | ArchUnit | `./gradlew :backend:core:test --tests KnowledgeSnippetSingleWriteSiteTest` | ❌ Wave 0 | ⬜ pending |
| SET-VOICE-04 | chat-tool + REST share `AssistantKnowledgeService.append` (append-callers rule, distinct test file per planner-checker BLOCKER #1) | ArchUnit | `./gradlew :backend:core:test --tests AssistantKnowledgeAppendCallSiteTest` | ❌ Wave 0 | ⬜ pending |
| SET-VOICE-05 | tone_preset enum CHECK rejects bad value | `@DataJpaTest` | `./gradlew :backend:core:test --tests AssistantSettingsTonePresetCheckTest` | ❌ Wave 0 | ⬜ pending |
| SET-VOICE-06 | non-`vi`/`en` ai_output_language returns 400 | mvc slice | `./gradlew :backend:api:test --tests SettingsVoiceLanguageValidationTest` | ❌ Wave 0 | ⬜ pending |
| SET-VOICE-07 | sentinel content never reaches DB/log/audit | integration | `./gradlew :backend:core:test --tests VoiceGenerationFromSentLeakTest` | ❌ Wave 0 (critical privacy) | ⬜ pending |
| SET-VOICE-07 | 4th call/hour returns 429 | unit | `./gradlew :backend:core:test --tests VoiceGenerationRateLimitTest` | ❌ Wave 0 | ⬜ pending |
| SET-VOICE-07 | Spring AI observation properties disabled (Mechanisms A/B/C per WARNING #6) | mvc slice + integration | `./gradlew :backend:api:test --tests SpringAiObservationDisabledTest` | ❌ Wave 0 | ⬜ pending |
| SET-BEHV-01 | toggle OFF → draft worker writes no rows | integration | `./gradlew :backend:worker:test --tests DraftAutoToggleIntegrationTest` | ❌ Wave 0 | ⬜ pending |
| SET-BEHV-02 | draft worker resolves enum → threshold and skips below | integration | `./gradlew :backend:worker:test --tests DraftConfidenceThresholdTest` | ❌ Wave 0 | ⬜ pending |
| SET-BEHV-03 | reuses existing notification-preferences endpoint (no new column) | smoke | Playwright e2e | ❌ Wave 0 | ⬜ pending |
| SET-BEHV-04 | LLM-05 redactor toggle-aware | unit | `./gradlew :backend:core:test --tests SensitiveDataRedactionToggleTest` | ❌ Wave 0 | ⬜ pending |
| SET-BEHV-05 | reuses triage-pause endpoint (UI labeled "Pause triage") | smoke | Playwright e2e | ❌ Wave 0 | ⬜ pending |
| SET-SAFE-01 | DELETE observation-created entry → 403 | mvc slice | `./gradlew :backend:api:test --tests SenderSafetyNetDeleteAuthorityTest` | ❌ Wave 0 | ⬜ pending |
| SET-SAFE-01 | `@acme.com` POST persists as DOMAIN | mvc slice | `./gradlew :backend:api:test --tests SenderSafetyNetDomainPatternTest` | ❌ Wave 0 | ⬜ pending |
| SET-SAFE-01 | DOMAIN entry blocks matching sender in triage worker | integration | `./gradlew :backend:worker:test --tests TriageSafetyNetDomainMatchTest` | ❌ Wave 0 | ⬜ pending |
| SET-SAFE-04 | `blocked_by_safety_net_pattern` populated when REJECTED_BY_SAFETY_NET | integration | `./gradlew :backend:worker:test --tests TriageAuditSafetyNetBadgeTest` | ❌ Wave 0 | ⬜ pending |
| SET-AI-01 | Resolution end-to-end: active+tested+model → calls `{base_url}` with `model_id`; inactive → catalog default | integration | `./gradlew :backend:core:test --tests ByokResolutionIntegrationTest` | ❌ Wave 0 | ⬜ pending |
| SET-AI-01 | Activate gate rejects when `model_id IS NULL` | mvc slice | `./gradlew :backend:api:test --tests ByokActivateGateModelMissingTest` | ❌ Wave 0 | ⬜ pending |
| SET-AI-01 | Activate gate rejects when `last_test_result <> 'OK'` | mvc slice | `./gradlew :backend:api:test --tests ByokActivateGateNotTestedTest` | ❌ Wave 0 | ⬜ pending |
| SET-AI-02 | save BYOK for `OPENROUTER` → 400 `code=ai.byok.provider_not_allowed` | mvc slice | `./gradlew :backend:api:test --tests ByokSaveProviderAllowListTest` | ❌ Wave 0 | ⬜ pending |
| SET-AI-02 | base URL: `http://attacker.com` → 400 `code=ai.byok.base_url_not_https` | mvc slice | `./gradlew :backend:api:test --tests ByokSaveBaseUrlValidationTest` | ❌ Wave 0 | ⬜ pending |
| SET-AI-02 | saving a row resets `active=false`, `last_test_result=NULL`, `last_tested_at=NULL` | `@DataJpaTest` | `./gradlew :backend:core:test --tests ByokSaveResetsStateTest` | ❌ Wave 0 | ⬜ pending |
| SET-AI-02 | plaintext key never echoed in response (regex assertion) | mvc snapshot | `./gradlew :backend:api:test --tests ByokResponseNeverEchoesPlaintextTest` | ❌ Wave 0 | ⬜ pending |
| SET-AI-02 | replace-row-on-new-provider (exactly one row per tenant) | `@DataJpaTest` | `./gradlew :backend:core:test --tests UserByokKeySingleRowPerTenantTest` | ❌ Wave 0 | ⬜ pending |
| SET-AI-03 | tenant-wide cost SUM returns exactly `{usd}` (no per-feature keys) | `@DataJpaTest` | `./gradlew :backend:core:test --tests AiCostQueryService7DayTest` | ❌ Wave 0 | ⬜ pending |
| SET-AI-04 | enum-only response; 401 provider body never leaks; `OK` carries `models[]` capped 100; ALL 4 providers (OPENAI/ANTHROPIC/GOOGLE/DEEPSEEK) covered per BLOCKER #2 | mvc slice | `./gradlew :backend:api:test --tests ByokTestConnectionEnumOnlyTest` | ❌ Wave 0 | ⬜ pending |
| SET-AI-04 | 11th test/hour returns 429 | unit | `./gradlew :backend:core:test --tests ByokTestConnectionRateLimitTest` | ❌ Wave 0 | ⬜ pending |
| SET-AI-04 | admin MKEY-03 + user `POST /api/byok/test-connection` both reach `ProviderConnectionTester.probeConnection` | ArchUnit | `./gradlew :backend:core:test --tests ProviderConnectionTesterSingleBindingTest` | ❌ Wave 0 | ⬜ pending |
| SET-AI-04 | sentinel-leak scrub stays green (no provider error body) | unit | `./gradlew :backend:core:test --tests UserByokTestConnectionSentinelLeakTest` | ❌ Wave 0 | ⬜ pending |
| Whole page | flat-section golden path + DOMAIN safety-net encoding round-trip (per INFO #10) | Playwright e2e | `pnpm --filter web e2e -- ai-settings.spec.ts` | ❌ Wave 0 | ⬜ pending |
| Whole page | no hardcoded color hex | repo grep gate | apps/web existing lint task | ✅ existing | ⬜ pending |
| Whole phase | aggregate ArchUnit (5+ rules: sanitizer-single-call, knowledge-append-callers, knowledge-repo-write-site, ProviderConnectionTester-single-binding, user_byok_key-package-confinement) | ArchUnit aggregate | `./gradlew :backend:core:test --tests Phase9ArchitectureTest` | ❌ Wave 3 (plan 09-07) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

The first plan wave MUST create stub test files for every ❌ Wave 0 row above. Suggested layout (planner refines):

- **Backend stubs (32 files — was 31 before planner-checker BLOCKER #1 added `AssistantKnowledgeAppendCallSiteTest`; total Wave-0 file count below is now 32 backend + 1 Playwright = 33):**
  - `backend/core/src/test/java/com/zeromail/core/chat/settings/SettingsVoiceServiceWordBoundsTest.java`
  - `backend/core/src/test/java/com/zeromail/core/chat/settings/AssistantSettingsTonePresetCheckTest.java`
  - `backend/core/src/test/java/com/zeromail/core/chat/sanitize/PersonalizationSanitizerSingleCallSiteTest.java`
  - `backend/core/src/test/java/com/zeromail/core/chat/knowledge/AssistantKnowledgeMemoryUniqueTitleTest.java`
  - `backend/core/src/test/java/com/zeromail/core/chat/knowledge/KnowledgeSnippetSingleWriteSiteTest.java` (repo write-site rule)
  - `backend/core/src/test/java/com/zeromail/core/chat/knowledge/AssistantKnowledgeAppendCallSiteTest.java` (append-callers rule — NEW per planner-checker BLOCKER #1)
  - `backend/core/src/test/java/com/zeromail/core/voice/VoiceGenerationFromSentLeakTest.java`
  - `backend/core/src/test/java/com/zeromail/core/voice/VoiceGenerationRateLimitTest.java`
  - `backend/core/src/test/java/com/zeromail/core/llm/byok/ByokResolutionIntegrationTest.java`
  - `backend/core/src/test/java/com/zeromail/core/llm/byok/ByokSaveResetsStateTest.java`
  - `backend/core/src/test/java/com/zeromail/core/llm/byok/UserByokKeySingleRowPerTenantTest.java`
  - `backend/core/src/test/java/com/zeromail/core/llm/byok/ByokTestConnectionRateLimitTest.java`
  - `backend/core/src/test/java/com/zeromail/core/llm/byok/ProviderConnectionTesterSingleBindingTest.java`
  - `backend/core/src/test/java/com/zeromail/core/llm/byok/UserByokTestConnectionSentinelLeakTest.java`
  - `backend/core/src/test/java/com/zeromail/core/llm/cost/AiCostQueryService7DayTest.java`
  - `backend/core/src/test/java/com/zeromail/core/llm/redaction/SensitiveDataRedactionToggleTest.java`
  - `backend/api/src/test/java/com/zeromail/api/controllers/settings/SettingsVoiceControllerTest.java`
  - `backend/api/src/test/java/com/zeromail/api/controllers/settings/SettingsVoiceLanguageValidationTest.java`
  - `backend/api/src/test/java/com/zeromail/api/controllers/settings/KnowledgeSnippetControllerTenantIsolationTest.java`
  - `backend/api/src/test/java/com/zeromail/api/controllers/triage/SenderSafetyNetDeleteAuthorityTest.java`
  - `backend/api/src/test/java/com/zeromail/api/controllers/triage/SenderSafetyNetDomainPatternTest.java`
  - `backend/api/src/test/java/com/zeromail/api/controllers/byok/ByokActivateGateModelMissingTest.java`
  - `backend/api/src/test/java/com/zeromail/api/controllers/byok/ByokActivateGateNotTestedTest.java`
  - `backend/api/src/test/java/com/zeromail/api/controllers/byok/ByokSaveProviderAllowListTest.java`
  - `backend/api/src/test/java/com/zeromail/api/controllers/byok/ByokSaveBaseUrlValidationTest.java`
  - `backend/api/src/test/java/com/zeromail/api/controllers/byok/ByokResponseNeverEchoesPlaintextTest.java`
  - `backend/api/src/test/java/com/zeromail/api/controllers/byok/ByokTestConnectionEnumOnlyTest.java`
  - `backend/worker/src/test/java/com/zeromail/worker/draft/DraftAutoToggleIntegrationTest.java`
  - `backend/worker/src/test/java/com/zeromail/worker/draft/DraftConfidenceThresholdTest.java`
  - `backend/worker/src/test/java/com/zeromail/worker/draft/DraftSignatureIntegrationTest.java`
  - `backend/worker/src/test/java/com/zeromail/worker/triage/TriageSafetyNetDomainMatchTest.java`
  - `backend/worker/src/test/java/com/zeromail/worker/triage/TriageAuditSafetyNetBadgeTest.java`
- **Frontend e2e stubs:**
  - `apps/web/e2e/ai-settings.spec.ts` (flat-section golden path covering Voice + Behavior + Updates + Safety net + AI Provider Dialogs, plus Knowledge add/edit/delete and BYOK Save → Test → Activate flow, plus DOMAIN safety-net encoding round-trip per INFO #10)

**Wave 0 stub file count: 32 backend tests + 1 Playwright spec = 33 files total. (BLOCKER #1 added one new backend test; the previous "33" Validation Sign-Off line was based on 32 backend + 1 Playwright but the count was miscalculated as 33 in error per INFO #8 — actually 31 backend + 1 = 32. The append-callers test brings it back to 32 backend + 1 = 33 total Wave-0 files.)**

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual brand-token compliance + IZ-aligned spacing/typography on the live `/ai` page | UI design-token compliance (`apps/web/AGENTS.md`) | Playwright doesn't catch token regressions vs. raw hex; design-token grep gate catches code only | Run dev server + open `/ai`, compare each section against `09-PROTOTYPE.html` side-by-side. Confirm no hardcoded `bg-[#...]` slipped in. |
| BYOK round-trip against a real provider (OpenAI + Anthropic) | SET-AI-04 happy path; BLOCKER #2 Anthropic-specific probe verification | Live API calls aren't run in CI (privacy, cost, flake). Mocked test asserts contract; one real-provider smoke confirms the wire format for both Authorization-Bearer and x-api-key transports | After enabling auto-send rules, set a test OpenAI key against `https://api.openai.com/v1`, click Test (expect `OK` + non-empty `models[]`), click Activate, send a chat message, confirm completion went through BYOK URL via app log. Then switch Provider to Anthropic, paste a throw-away Anthropic key, click Test → expect OK + claude-* model IDs (validates BLOCKER #2 fix). |
| "Generate from recent sent emails" with a real Gmail account | SET-VOICE-07 happy path | Gmail-API + LLM integration; CI uses a stub model, so a live run validates the actual prompt + style output | After Wave 0 completes, sign in with the test Google account, open the writing-style Dialog, click "Generate from recent sent emails", confirm the textarea fills with a non-empty style guide ≤ 500 words and that no DB row or log line contains email body content. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all ❌ MISSING references (32 backend test stubs + 1 Playwright spec = 33 stub files listed — count corrected per planner-checker INFO #8; previous "33 stub files listed" line was actually 31 backend + 1 = 32 in error, then BLOCKER #1 added AssistantKnowledgeAppendCallSiteTest bringing total to 32 backend + 1 = 33)
- [ ] No watch-mode flags (all commands `--run` / `--tests`, no `--watch`)
- [ ] Feedback latency < 90 seconds for quick run
- [ ] `nyquist_compliant: true` flipped after planner attaches every test command to a task or Wave-0 stub

**Approval:** pending
