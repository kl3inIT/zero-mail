---
phase: 10
slug: telegram-messaging-assistant
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-28
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `10-RESEARCH.md` §14 "Validation Architecture" + §22 quick reference.
> Planner fills the per-task verification map during `/gsd:plan-phase` and
> may revise sampling/Wave 0 entries before approval.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ + Mockito + Spring Boot Test (backend); Vitest + Playwright (frontend) |
| **Config file** | `backend/core/build.gradle.kts`, `backend/api/build.gradle.kts`, `apps/web/vitest.config.ts`, `apps/web/playwright.config.ts` |
| **Quick run command** | `./gradlew :backend:core:test --tests "*Telegram*" --tests "*MailAction*" --tests "*OutboundActionAudit*" -x integrationTest` |
| **Full suite command** | `./gradlew :backend:core:test :backend:api:test :backend:worker:test :backend:core:archUnitTest && pnpm --filter web test && pnpm --filter web e2e` |
| **Estimated runtime** | ~180 seconds (backend unit + ArchUnit), ~600 seconds (full incl. integration + Playwright) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :backend:core:test --tests "<TestClassTouched>"` (or `pnpm --filter web test <path>` for frontend tasks)
- **After every plan wave:** Run full module test set (`:backend:core:test`, `:backend:api:test`, `:backend:worker:test`, `pnpm --filter web test`)
- **Before `/gsd:verify-work`:** Full suite incl. ArchUnit (`OnlyOneGmailSendCallSiteTest`, `ChatPersistenceContentBanTest`, new `TelegramPathBodyBanTest`, `TelegramPrivacySweepTest`, `TelegramOutboxDrainArchTest`, `OutboundActionAuditMandatoryArchTest`, `MailActionServiceArchTest`) + Playwright e2e (`telegram-pairing.spec.ts`)
- **Max feedback latency:** 180 seconds for per-wave; 600 seconds for full pre-verify run

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| _planner-fills_ | _planner-fills_ | _planner-fills_ | TG-XX | T-10-XX | _expected secure behavior_ | unit / integration / archunit / e2e / wiremock | `{command}` | ⬜ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

> **Note:** Planner is responsible for emitting one row per task with concrete REQ-ID + threat ref + automated command. RESEARCH.md §14 lists the 19 acceptance invariants — each maps to ≥1 test surface; planner translates those into task-level rows here.

---

## Wave 0 Requirements

Per RESEARCH.md §14 Validation Architecture, Wave 0 must install:

- [ ] `backend/core/src/test/java/com/zeromail/core/messaging/telegram/` — package + test base class for WireMock fixtures
- [ ] `backend/api/src/test/resources/telegram-fixtures/` — JSON fixture directory (sendMessage 200, sendMessage 429 + retry_after, editMessageText 200, editMessageText 429, sendChatAction 200, getMe 200, getMe 401)
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/TelegramPathBodyBanTest.java` — empty skeleton (red), ArchUnit rule added by planner task
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/TelegramPrivacySweepTest.java` — empty skeleton
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/TelegramOutboxDrainArchTest.java` — empty skeleton
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/OutboundActionAuditMandatoryArchTest.java` — empty skeleton
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/MailActionServiceArchTest.java` — empty skeleton
- [ ] `apps/web/e2e/telegram-pairing.spec.ts` — empty skeleton describing Connect→notify→callback-confirm-send flow
- [ ] `apps/web/features/telegram-integration/__tests__/` — Vitest test base for the pairing dialog + status query
- [ ] Verify Bucket4j 8.19.0 dependency declared in `libs.versions.toml` + wired in `backend/core/build.gradle.kts`

*Approved alternatives:* WireMock JUnit5 Jupiter extension already wired into Pub/Sub tests (Phase 02A pattern reference) — reuse via shared `WireMockExtension`.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| BotFather registration of `@ZeroMailBot` / `@ZeroMailAssistantBot` username | TG-05 | One-time external interaction with Telegram BotFather (not automatable without exposing bot token credentials) | Owner runs BotFather `/newbot` flow, captures token + final chosen username, sets `zero-mail.messaging.telegram.bot-token` env + `bot-username` config, then runs `./gradlew :backend:api:bootRun` and POSTs `/api/integrations/telegram/admin/setwebhook` (admin endpoint) to confirm Telegram acknowledges secret_token registration. |
| End-to-end happy path on real Telegram server | TG-08, TG-09, TG-12, TG-17 | Sandboxed Playwright + WireMock cannot fully simulate Telegram's actor identity + DM-only enforcement; one manual real-server pass after deploy | Owner pairs personal Telegram → triggers a real Gmail rule → confirms notification arrives → taps inline keyboard send → confirms email reaches recipient inbox. Captures screenshots in `docs/integrations/telegram-setup.md`. |
| VPS reverse-proxy X-Forwarded-For trust configuration | TG-06 | IP allowlist correctness depends on proxy header trust setup outside Java code | After deploy, owner verifies `server.forward-headers-strategy=native` + Nginx `proxy_set_header X-Real-IP $remote_addr;` and runs a curl from a non-Telegram IP to confirm webhook returns 401, then from Telegram IP range to confirm 200. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify entry or Wave 0 dependency
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (test base classes + WireMock fixtures + ArchUnit skeletons + Playwright skeleton + Bucket4j dependency)
- [ ] No watch-mode flags in any test command
- [ ] Feedback latency < 180s per wave / < 600s pre-verify
- [ ] `nyquist_compliant: true` set in frontmatter after all rows in Per-Task Verification Map are filled by planner

**Approval:** pending — planner fills per-task rows in `/gsd:plan-phase` run; reviewer flips `nyquist_compliant: true` and `wave_0_complete: true` after `/gsd:execute-phase` Wave 0 commits.
