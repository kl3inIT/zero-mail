---
phase: 5C
slug: user-surface-analytics-daily-digest
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-13
---

# Phase 5C — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Wave 0 inventory + per-requirement test map are seeded from `05C-RESEARCH.md` §Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend framework** | JUnit 5 + Mockito + AssertJ + Testcontainers Postgres (`PostgresContainerTest` base) + ArchUnit |
| **Frontend framework** | Vitest + Playwright + jsdom |
| **Config files** | per-subproject `backend/*/build.gradle.kts`, `apps/web/vitest.config.ts`, `apps/web/playwright.config.ts` |
| **Quick run command (backend)** | `./gradlew :backend:core:test :backend:api:test :backend:worker:test --tests "*Analytics*" --tests "*Digest*" --tests "*Notification*"` |
| **Quick run command (frontend)** | `pnpm --filter apps/web test analytics notifications` |
| **Full suite command** | `./gradlew check && pnpm --filter apps/web check && pnpm --filter apps/web i18n:check` |
| **Estimated runtime (quick)** | ~60s |
| **Estimated runtime (full)** | ~6–8 min |

---

## Sampling Rate

- **After every task commit:** Run the focused test command listed in the per-task verification map (planner fills below).
- **After every plan wave:** Run `./gradlew :backend:core:test :backend:api:test :backend:worker:test` + `pnpm --filter apps/web check`.
- **Before `/gsd-verify-work`:** Full suite + i18n parity gate must be green.
- **Max feedback latency:** 90 seconds per task, ~8 min per wave.

---

## Per-Task Verification Map

> Planner populates this table as PLAN.md tasks are written. Each task that is verifiable MUST cite a test type and command from `05C-RESEARCH.md` §Validation Architecture.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| _(planner fills)_ | | | | | | | | | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Seeded from `05C-RESEARCH.md` §Validation Architecture › Wave 0 Gaps. Each line is a NEW test file the phase must create before its first verified task runs.

**Backend (Java) — `backend/core`:**
- [ ] `src/test/java/com/zeromail/core/arch/ResendBoundaryArchTest.java` — ArchUnit, mirrors `LlmGatewayBoundaryTest`.
- [ ] `src/test/java/com/zeromail/core/arch/DigestPayloadShapeArchTest.java` — ArchUnit "no fields named `htmlBody`/`mimeType`/`subject`" on `DigestPayload`.
- [ ] `src/test/java/com/zeromail/core/analytics/AnalyticsSummaryQueryServiceTest.java` — Testcontainers Postgres, seeded fixtures, all 4 queries (Q1–Q4).
- [ ] `src/test/java/com/zeromail/core/analytics/AnalyticsRepositoryContentBanTest.java` — ArchUnit ban on raw-body / prompt / completion columns reaching analytics service.
- [ ] `src/test/java/com/zeromail/core/analytics/AnalyticsPrivacySweepTest.java` — mirrors `TriagePrivacySweepTest`, `SensitiveMarkerScrubFilter` enforced.

**Backend (Java) — `backend/api`:**
- [ ] `src/test/java/com/zeromail/api/controllers/analytics/AnalyticsControllerContractTest.java` — `@WebMvcTest` + MockMvc, typed response shape.
- [ ] `src/test/java/com/zeromail/api/controllers/notifications/NotificationPreferencesControllerTest.java` — auth + tenant scoping.

**Backend (Java) — `backend/worker`:**
- [ ] `src/test/java/com/zeromail/worker/notification/DigestDispatchSchedulerTest.java` — Testcontainers + injected `Clock`, hourly fanout assertion.
- [ ] `src/test/java/com/zeromail/worker/notification/DigestIdempotencyTest.java` — double-run, assert ≤1 `NotificationChannel.dispatch()`.
- [ ] `src/test/java/com/zeromail/worker/notification/DigestComposerTest.java` — zero-activity branch + non-zero branch.
- [ ] `src/test/java/com/zeromail/worker/notification/ThymeleafDigestRendererTest.java` — snapshot HTML + TXT, vi + en.
- [ ] `src/test/java/com/zeromail/worker/notification/DigestMessageSourceParityTest.java` — fail-loud on missing key.
- [ ] `src/test/java/com/zeromail/worker/notification/email/EmailNotificationChannelTest.java` — mock Resend client, assert `Idempotency-Key` header = `tenantId:digestDayLocal`.
- [ ] `src/test/java/com/zeromail/worker/notification/DigestPrivacySweepTest.java` — `ListAppender` + `SensitiveMarkerScrubFilter`.
- [ ] `src/test/java/com/zeromail/worker/notification/DigestPendingReaperJobTest.java` — grace-period promotion `PENDING → FAILED`.

**Frontend (TypeScript) — `apps/web`:**
- [ ] `features/analytics/__tests__/AnalyticsPanels.test.tsx` — empty-state + window-switch + zero-data → no NaN.
- [ ] `features/notifications/__tests__/NotificationsSection.test.tsx` — optimistic toggle, send-hour persistence, error rollback.
- [ ] `e2e/analytics.spec.ts` — full window-switch flow (auth, render, switch chip, fetch).
- [ ] `e2e/settings-notifications.spec.ts` — opt-out + send-hour persistence + i18n.

**Framework install:** none — all test infrastructure (JUnit 5, Testcontainers, ArchUnit, Vitest, Playwright, jsdom) is already present.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live Resend deliverability to Gmail / Vietnamese ISP MX | ANL-03 (operational) | Requires a verified sending domain DNS record and a real Gmail inbox — not assertable in CI. | (1) Deploy to staging with `RESEND_API_KEY` + verified domain. (2) Toggle `digest_enabled=true` for a test tenant. (3) Confirm digest arrives in Gmail Primary tab within ±5 min of `digest_send_hour_local`. (4) Verify Vietnamese diacritics render correctly in both HTML and TXT bodies. |
| Real Gmail OAuth → digest opt-out preference write | ANL-03, D-17 | OAuth provisioning side-effect is integration-tested with mocks, but live Google grant flow is manual once per release. | Run the bundled-OAuth flow end-to-end; verify `notification_preference (tenant_id, 'email', true, 20)` row exists after provisioning. |
| Visual QA: 4 panels at design-token fidelity in vi + en, dark/light, 320px → 1920px | ANL-01, UI-SPEC §1–§4 | Pixel/typography fidelity is reviewer judgment, not automatable. | Run `apps/web` dev server; switch Vi/En; toggle theme; resize to 320 / 768 / 1024 / 1920; capture screenshots for design review. |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s per task
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
