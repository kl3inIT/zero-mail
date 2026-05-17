---
phase: 7
slug: chat-email-assistant-backend-frontend-send-executor-archunit
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-17
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
| CHAT-01 | SSE streams 3-turn conversation; survives refresh | 3+5 | Integration + Playwright | `ChatControllerStreamIT.java` + `apps/web/e2e/chat/stream-happy-path.spec.ts` | ❌ Wave 0 | ⬜ pending |
| CHAT-02 | Rule CRUD via tools routes through v1.0 rules engine | 3 | Integration | `backend/core/src/test/java/.../chat/RuleToolIT.java` | ❌ Wave 0 | ⬜ pending |
| CHAT-03 | Inbox tools sanitize body before persistence | 1 | Integration + ArchUnit | `ChatPersistenceContentBanTest` + `pg_dump` grep test | ❌ Wave 0 | ⬜ pending |
| CHAT-04 | sendEmail preview → click Send → exactly 1 Gmail call + 1 audit row | 4 | Integration race | `ConfirmationRaceIT.java` (double-click, stale toolCallId, confirm-during-stream) | ❌ Wave 0 | ⬜ pending |
| CHAT-05 | saveMemory confirm flow + searchMemories returns saved row | 3 | `@DataJpaTest` + integration | `AssistantMemoryRepositoryIT.java` | ❌ Wave 0 | ⬜ pending |
| CHAT-06 | Preview cards render with Edit/Send/Cancel; replay-mode "Sent ✓" no re-execution | 5 | Playwright | `apps/web/e2e/chat/confirmation-replay.spec.ts` | ❌ Wave 0 | ⬜ pending |
| CHAT-07 | History sidebar list/open/soft-delete; persist across refresh; no rename/search in DOM | 5 | Playwright + integration | `apps/web/e2e/chat/history-sidebar.spec.ts` + `ChatHistoryProjectorIT.java` | ❌ Wave 0 | ⬜ pending |
| CHAT-08 | Vietnamese chrome on locale=vi; assistant replies VI; locale=en flips both | 5 | Playwright | `apps/web/e2e/chat/vietnamese-default.spec.ts` | ❌ Wave 0 | ⬜ pending |
| ARCH-01 | Exactly 1 Gmail send call site; ArchUnit + CI grep gate | 4 | ArchUnit + shell test | `OnlyOneGmailSendCallSiteTest` + updated `NoGmailSendAllowedTest` + `.github/workflows/ci.yml` grep gate (all in ONE PR) | ❌ Wave 0 | ⬜ pending |
| ARCH-02 | `chat_message.parts` zero email body — 3 layers | 1 | ArchUnit + Postgres trigger + integration | `ChatPersistenceContentBanTest.java` + trigger rejection test + `pg_dump \| grep` sweep | ❌ Wave 0 | ⬜ pending |
| ARCH-03 | Per-race test: double-click → 1 send; stale toolCallId → 404; confirm-during-stream → blocked | 4 | Integration with concurrent confirms | `ConfirmationRaceIT.java` (3 scenarios with `CompletableFuture.allOf`) | ❌ Wave 0 | ⬜ pending |
| ARCH-04 | 100 concurrent confirms → 100 audit rows + 100 confirmed states; reconciliation cron heals | 4 | Integration + concurrency | `AuditAtomicityIT.java` + `ReconciliationCronIT.java` | ❌ Wave 0 | ⬜ pending |
| ARCH-05 | 10 tenants × 5 SSE streams = 50 streams; no cross-tenant data | 3 | Integration multi-tenant | `MultiTenantChatLeakIT.java` (port FND-05 pattern) + ArchUnit Scheduler ban | ❌ Wave 0 | ⬜ pending |
| ARCH-06 | 10 hostile personalization payloads → slot always fenced, sentinels stripped, ≤ 2000 chars | 2 | Unit test (no LLM) | `PersonalizationSanitizerTest.java` + `XmlFencedPersonalizationRendererTest.java` | ❌ Wave 0 | ⬜ pending |
| ARCH-07 | `ChatToolCallRegistry` populated from raw chunks when Spring AI aggregator empty | 3 | Integration (mocked `ChatModel.stream`) | `ChatToolCallRegistryIT.java` + `ZeroMailChatMemoryIT.java` | ❌ Wave 0 | ⬜ pending |
| SET-SAFE-05 | Send to safety-net recipient → VIP banner + ack checkbox + Send disabled until ack; non-VIP → no banner | 4+5 | Playwright + integration | `apps/web/e2e/chat/vip-banner.spec.ts` + `AssistantSendExecutorVipIT.java` (server-side reject without ack flag) | ❌ Wave 0 | ⬜ pending |
| Req #17 (outside-source-thread badge) | Recipient outside source thread → "Added by AI" badge | 4 | Playwright | `apps/web/e2e/chat/outside-source-thread.spec.ts` | ❌ Wave 0 | ⬜ pending |
| Schema dispatch | v1 fixture deserializes via `ChatPartsJsonConverter` | 1 | `@DataJpaTest` + JSON fixture | `src/test/resources/chat-message-fixtures/v1/*.json` + `ChatPartsSchemaV1Test.java` | ❌ Wave 0 | ⬜ pending |
| Modulith boundary | `core.chat` Modulith boundary verified | 6 | Modulith | Existing `ApplicationModulesTest` extended | ✅ (extend only) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Backend ArchUnit:
- [ ] `OnlyOneGmailSendCallSiteTest.java` — paired negative + positive ArchUnit for `@AllowedSendCallSite` (ARCH-01)
- [ ] `ChatPersistenceContentBanTest.java` — body-content ban ArchUnit (ARCH-02)
- [ ] Scheduler-ban ArchUnit rule (ARCH-05) in `archunit-conventions` plugin config or dedicated test
- [ ] Updated `NoGmailSendAllowedTest` (flip count from 0 to allow exactly the annotated method) — lands in SAME PR as `OnlyOneGmailSendCallSiteTest`

Backend integration:
- [ ] `ChatControllerStreamIT.java`, `ConfirmationRaceIT.java`, `AuditAtomicityIT.java`, `ReconciliationCronIT.java`, `MultiTenantChatLeakIT.java`, `RuleToolIT.java`, `ChatToolCallRegistryIT.java`, `ZeroMailChatMemoryIT.java`, `AssistantSendExecutorVipIT.java`, `ChatHistoryProjectorIT.java`

Backend unit:
- [ ] `PersonalizationSanitizerTest.java`, `XmlFencedPersonalizationRendererTest.java`, `ToolOutputSanitizerTest.java`, `VercelProtocolEmitterTest.java` (ordering enforcement), `ChatPartsSchemaV1Test.java`

Backend repository:
- [ ] `ChatMessageJdbcRepositoryIT.java`, `AssistantSendAuditJpaRepositoryIT.java` (UNIQUE constraint), `AssistantMemoryRepositoryIT.java`

Backend Modulith:
- [ ] Extend existing `ApplicationModulesTest` to verify `core.chat` package boundaries

Frontend Playwright e2e:
- [ ] `stream-happy-path.spec.ts`, `confirmation-replay.spec.ts`, `confirmation-race.spec.ts`, `history-sidebar.spec.ts`, `vietnamese-default.spec.ts`, `vip-banner.spec.ts`, `outside-source-thread.spec.ts`

Fixture resources:
- [ ] `src/test/resources/chat-message-fixtures/v1/*.json` — ≥ 3 fixtures (text-only, single-tool-call, multi-tool-call with confirmed send)

Database:
- [ ] Liquibase changelog `042-chat-message-and-body-ban-trigger.yaml` (table + trigger in same changeset for atomicity — see RESEARCH §Pitfall 4)

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

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references in the Per-Task map above
- [ ] No watch-mode flags (`--watch`, `--watchAll`) in any `<automated>` command
- [ ] Feedback latency under 5 minutes (per-task) / 12 minutes (per-wave)
- [ ] `nyquist_compliant: true` set in frontmatter once planner emits Wave 0 plan and per-task `<automated>` blocks are mapped

**Approval:** pending
