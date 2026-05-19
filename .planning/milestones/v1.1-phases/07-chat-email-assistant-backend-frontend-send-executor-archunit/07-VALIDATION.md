---
phase: 7
slug: chat-email-assistant-backend-frontend-send-executor-archunit
status: ready
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-17
audited: 2026-05-19
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Source: `07-RESEARCH.md` §Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend framework** | JUnit 5 + AssertJ + Mockito (`@MockitoBean` since Boot 3.4) + ArchUnit + Testcontainers Postgres |
| **Frontend framework** | Vitest (unit/component) + Playwright (e2e) |
| **Config files** | `backend/api/build.gradle.kts`, `backend/core/build.gradle.kts`, `gradle/libs.versions.toml`, `apps/web/playwright.config.ts`, `apps/web/vitest.config.ts` |
| **Quick run command (backend, scoped)** | `./gradlew :backend:core:test --tests "*ChatPersistenceContentBanTest" --tests "*OnlyOneGmailSendCallSiteTest"` |
| **Quick run command (frontend, scoped)** | `pnpm --filter @zero-mail/web test -t chat` |
| **Full suite command** | `./gradlew test && pnpm --filter @zero-mail/web test && pnpm --filter @zero-mail/web test:e2e -- e2e/chat/` |
| **Modulith verification** | `./gradlew :backend:core:test --tests "*ApplicationModulesTest"` |
| **LLM eval (deferred to Phase 8)** | `./gradlew llmEval` — Phase 7 uses mocked `LlmModelClient` only; no `@Tag("llm-eval")` tasks in Phase 7 |
| **Estimated runtime (quick)** | ~30–60s after Testcontainers warm |
| **Estimated runtime (full backend)** | ~3–5 minutes after Testcontainers warm |
| **Estimated runtime (full + e2e)** | ~8–12 minutes |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :backend:core:test :backend:api:test` (excludes `llm-eval` by default per `TESTING.md` §4). Frontend-only commits: run `pnpm --filter @zero-mail/web test -t <changed-feature>`.
- **After every plan wave:** Run `./gradlew test` full + `pnpm --filter @zero-mail/web test` + `pnpm i18n:check`.
- **Before `/gsd:verify-work`:** Full suite green + Playwright e2e (`apps/web/e2e/chat/`) green + `ApplicationModulesTest` green + CI grep gate (`grep -r "gmail.send" backend/core/src/main | wc -l == 1`) green.
- **Max feedback latency:** 5 minutes (per-task), 12 minutes (per-wave full suite).

---

## Per-Task Verification Map

Verification anchors per phase requirement. Specific Plan/Wave/Task IDs are filled in by the planner; this matrix is the inheritance source.

| Req ID | Behavior | Wave | Test Type | Automated Command / File | File Exists | Status |
|--------|----------|------|-----------|--------------------------|-------------|--------|
| CHAT-01 | SSE streams 3-turn conversation; survives refresh | 3+5 | Integration + Playwright | `ChatControllerStreamIT.java` + `apps/web/e2e/chat/stream-happy-path.spec.ts` | ✅ | ✅ green |
| CHAT-02 | Rule CRUD via tools routes through v1.0 rules engine | 3 | Integration | `backend/core/src/test/java/com/zeromail/core/chat/usecases/RuleToolIT.java` | ✅ | ✅ green |
| CHAT-03 | Inbox tools sanitize body before persistence | 1 | Integration + ArchUnit | `ChatPersistenceContentBanTest.java` + Postgres body-ban trigger (changelog `042-chat-message-and-body-ban-trigger.yaml`) | ✅ | ✅ green |
| CHAT-04 | sendEmail preview → click Send → exactly 1 Gmail call + 1 audit row | 4 | Integration race | `ConfirmationRaceIT.java` (double-click, stale toolCallId, confirm-during-stream) | ✅ | ✅ green |
| CHAT-05 | saveMemory confirm flow + searchMemories returns saved row | 3 | `@DataJpaTest` + integration | `AssistantMemoryRepositoryIT.java` | ✅ | ✅ green |
| CHAT-06 | Preview cards render with Edit/Send/Cancel; replay-mode "Sent ✓" no re-execution | 5 | Playwright | `apps/web/e2e/chat/confirmation-replay.spec.ts` | ✅ | ✅ green |
| CHAT-07 | History sidebar list/open/soft-delete; persist across refresh; no rename/search in DOM | 5 | Playwright + integration | `apps/web/e2e/chat/history-sidebar.spec.ts` + `ChatHistoryProjectorIT.java` | ✅ | ✅ green |
| CHAT-08 | Vietnamese chrome on locale=vi; assistant replies VI; locale=en flips both | 5 | Playwright | `apps/web/e2e/chat/vietnamese-default.spec.ts` | ✅ | ✅ green |
| ARCH-01 | Exactly 1 Gmail send call site; ArchUnit + CI grep gate | 4 | ArchUnit + shell test | `OnlyOneGmailSendCallSiteTest.java` + `NoGmailSendAllowedTest.java` + `scripts/ci/count-gmail-send-call-sites.sh` | ✅ | ✅ green |
| ARCH-02 | `chat_message.parts` zero email body — 3 layers | 1 | ArchUnit + Postgres trigger + integration | `ChatPersistenceContentBanTest.java` + Liquibase trigger in `042-chat-message-and-body-ban-trigger.yaml` | ✅ | ✅ green |
| ARCH-03 | Per-race test: double-click → 1 send; stale toolCallId → 404; confirm-during-stream → blocked | 4 | Integration with concurrent confirms | `ConfirmationRaceIT.java` (3 scenarios with `CompletableFuture.allOf`) | ✅ | ✅ green |
| ARCH-04 | 100 concurrent confirms → 100 audit rows + 100 confirmed states; reconciliation cron heals | 4 | Integration + concurrency | `AuditAtomicityIT.java` + `ReconciliationCronIT.java` | ✅ | ✅ green |
| ARCH-05 | 10 tenants × 5 SSE streams = 50 streams; no cross-tenant data | 3 | Integration multi-tenant | `MultiTenantChatLeakIT.java` + `ChatNoReactorSchedulerTest.java` (ArchUnit Scheduler ban) | ✅ | ✅ green |
| ARCH-06 | 10 hostile personalization payloads → slot always fenced, sentinels stripped, ≤ 2000 chars | 2 | Unit test (no LLM) | `PersonalizationSanitizerTest.java` + `XmlFencedPersonalizationRendererTest.java` | ✅ | ✅ green |
| ARCH-07 | `ChatToolCallRegistry` populated from raw chunks when Spring AI aggregator empty | 3 | Integration (mocked `ChatModel.stream`) | `ChatToolCallRegistryIT.java` + `ZeroMailChatMemoryIT.java` | ✅ | ✅ green |
| SET-SAFE-05 | Send to safety-net recipient → VIP banner + ack checkbox + Send disabled until ack; non-VIP → no banner | 4+5 | Playwright + integration | `apps/web/e2e/chat/vip-banner.spec.ts` + `AssistantSendExecutorVipIT.java` (server-side reject without ack flag) | ✅ | ✅ green |
| Req #17 (outside-source-thread badge) | Recipient outside source thread → "Added by AI" badge | 4 | Playwright | `apps/web/e2e/chat/outside-source-thread.spec.ts` | ✅ | ✅ green |
| Schema dispatch | v1 fixture deserializes via `ChatPartsJsonConverter` | 1 | `@DataJpaTest` + JSON fixture | `backend/core/src/test/resources/chat-message-fixtures/v1/*.json` (4 fixtures) + `ChatPartsSchemaV1Test.java` | ✅ | ✅ green |
| Modulith boundary | `core.chat` Modulith boundary verified | 6 | Modulith | `backend/api/src/test/java/com/zeromail/api/ZeroMailApiApplicationModulesTest.java` (renamed from `ApplicationModulesTest` in Phase 1.2 refactor `226dcd87`) | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Backend ArchUnit:
- [x] `OnlyOneGmailSendCallSiteTest.java` — paired negative + positive ArchUnit for `@AllowedSendCallSite` (ARCH-01)
- [x] `ChatPersistenceContentBanTest.java` — body-content ban ArchUnit (ARCH-02)
- [x] Scheduler-ban ArchUnit rule (ARCH-05) → `ChatNoReactorSchedulerTest.java`
- [x] Updated `NoGmailSendAllowedTest` (flip count from 0 to allow exactly the annotated method) — landed alongside `OnlyOneGmailSendCallSiteTest`

Backend integration:
- [x] `ChatControllerStreamIT.java`, `ConfirmationRaceIT.java`, `AuditAtomicityIT.java`, `ReconciliationCronIT.java`, `MultiTenantChatLeakIT.java`, `RuleToolIT.java`, `ChatToolCallRegistryIT.java`, `ZeroMailChatMemoryIT.java`, `AssistantSendExecutorVipIT.java`, `ChatHistoryProjectorIT.java`, `ChatOrchestratorIT.java`, `ConfirmationLeaseServiceIT.java`

Backend unit:
- [x] `PersonalizationSanitizerTest.java`, `XmlFencedPersonalizationRendererTest.java`, `ToolOutputSanitizerTest.java`, `VercelProtocolEmitterTest.java` (ordering enforcement), `ChatPartsSchemaV1Test.java`

Backend repository:
- [x] `ChatMessageJdbcRepositoryIT.java`, `AssistantMemoryRepositoryIT.java`
- [x] Assistant send-audit UNIQUE-constraint coverage → covered by `AuditAtomicityIT.java` (100-concurrent-confirm → exactly 100 audit rows) and `ConfirmationRaceIT.java` (idempotent double-click → 1 audit row). Dedicated `AssistantSendAuditJpaRepositoryIT.java` not needed because send-audit access is JdbcTemplate-based inside `AssistantPendingActionReconciler`, not a Spring Data repository.

Backend Modulith:
- [x] `ZeroMailApiApplicationModulesTest.verify()` boots `ZeroMailApiApplication.class` through `ApplicationModules.of(...).verify()`, exercising `core.chat` package boundaries alongside the rest of the module graph

Frontend Playwright e2e:
- [x] `stream-happy-path.spec.ts`, `confirmation-replay.spec.ts`, `confirmation-race.spec.ts`, `history-sidebar.spec.ts`, `vietnamese-default.spec.ts`, `vip-banner.spec.ts`, `outside-source-thread.spec.ts` (plus `csrf-parity.spec.ts`)

Fixture resources:
- [x] `backend/core/src/test/resources/chat-message-fixtures/v1/*.json` — 4 fixtures: `text-only.json`, `single-tool-call.json`, `multi-tool-call-confirmed-send.json`, `send-email-with-draft-body.json`

Database:
- [x] Liquibase changelog `042-chat-message-and-body-ban-trigger.yaml` (table + trigger in same changeset — see RESEARCH §Pitfall 4)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual quality of preview cards (typography, spacing, focus rings, hover states match UI-SPEC tokens) | CHAT-06, UI-SPEC | Pixel-level visual review; Playwright snapshot is brittle | Open `/chat` in browser; trigger each of 6 preview card types via tool calls; compare against `07-PROTOTYPE.html` |
| Vietnamese copy review by VI-native reader for chat chrome + system prompt fragments | CHAT-08 | Linguistic nuance (formal/informal, "anh/chị/bạn" register) | Open `/chat` with `locale=vi`; native speaker reviews 20 strings + 1 happy-path conversation transcript |
| Streaming feels natural (token rate, cancel-within-one-frame perceived smoothness) | CHAT-01 | UX feel under real network conditions | Manual stream + cancel cycle on throttled 3G profile in DevTools |
| Multi-tab UX edge cases beyond contractual race tests (e.g., user perception of optimistic-then-rolled-back state) | ARCH-03, CHAT-06 | Subjective; integration tests cover correctness but not perceived smoothness | Open 3 tabs of same conversation, fire concurrent confirm, observe optimistic UI behavior |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references in the Per-Task map above
- [x] No watch-mode flags (`--watch`, `--watchAll`) in any `<automated>` command
- [x] Feedback latency under 5 minutes (per-task) / 12 minutes (per-wave)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved (audited 2026-05-19 against on-disk tests + `07-VERIFICATION.md` PASS verdict)

---

## Validation Audit 2026-05-19

Retroactive Nyquist audit run via `/gsd-validate-phase 7` against the executed phase. Every requirement in the Per-Task Map was cross-referenced with the on-disk test files and with `07-VERIFICATION.md` (verdict: `passed`, dated 2026-05-18). No gaps required new test generation; only doc updates.

| Metric | Count |
|--------|-------|
| Requirements audited | 19 |
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
| Path corrections | 1 (`ApplicationModulesTest` → `ZeroMailApiApplicationModulesTest`, renamed in commit `226dcd87`) |
| Send-audit repo IT note | `AssistantSendAuditJpaRepositoryIT` was Wave-0 planned but never needed; access path is `JdbcTemplate` inside `AssistantPendingActionReconciler`, and UNIQUE-constraint behavior is exercised by `AuditAtomicityIT` + `ConfirmationRaceIT` |
