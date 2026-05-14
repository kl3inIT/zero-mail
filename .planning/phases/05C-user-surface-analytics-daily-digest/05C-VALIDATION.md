---
phase: 5C
slug: user-surface-analytics-daily-digest
status: complete
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-13
audited: 2026-05-14
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
| **Quick run command (frontend)** | `pnpm --filter web test analytics notifications` |
| **Full suite command** | `./gradlew check && pnpm --filter web check && pnpm --filter web i18n:check` |
| **Estimated runtime (quick)** | ~60s |
| **Estimated runtime (full)** | ~6–8 min |

---

## Sampling Rate

- **After every task commit:** Run the focused test command listed in the per-task verification map.
- **After every plan wave:** Run `./gradlew :backend:core:test :backend:api:test :backend:worker:test` + `pnpm --filter web check`.
- **Before `/gsd-verify-work`:** Full suite + i18n parity gate must be green.
- **Max feedback latency:** 90 seconds per task, ~8 min per wave.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 5C-01-T1 | 01 | 1 | ANL-02, ANL-03, WEB-02 | T-05C-01..03 | Liquibase 032–037 apply idempotently; uppercase `EMAIL` persisted; UNIQUE`(tenant_id, digest_day_local)` rejects duplicates | Integration (Testcontainers Postgres) | `./gradlew :backend:core:test --tests DigestDeliveryUniqueConstraintTest --tests NotificationPreferenceBackfillTest` | ✅ | ✅ green |
| 5C-01-T2 | 01 | 1 | ANL-02, ANL-03 | T-05C-01..03 | `sender_email` captured from Gmail metadata-only From header via real writer; no raw body fetched | Integration + Mockito | `./gradlew :backend:core:test --tests NotificationPreferencePersistenceTest --tests DigestDeliveryUniqueConstraintTest --tests GmailDeliveryProcessingSenderEmailTest` | ✅ | ✅ green |
| 5C-01-T3 | 01 | 1 | ANL-03, WEB-02 | T-05C-02 | Bundled OAuth provisioning seeds tenant tz + EMAIL preference; account deletion cascades notification rows | Integration (Testcontainers) | `./gradlew :backend:core:test --tests OAuthProvisioningDefaultsTest` | ✅ | ✅ green |
| 5C-02-T1 | 02 | 2 | ANL-01, ANL-02 | T-05C-04..06 | Tenant-scoped JDBC; closed-open windows; INBOX filter on Q1/Q3; reverted excluded from Q2/Q4-applied; no content columns reach service | Integration + ArchUnit + log scrub | `./gradlew :backend:core:test --tests "TimeSavedWeightsTest" --tests "AnalyticsSummaryQueryServiceTest" --tests "AnalyticsRepositoryContentBanTest" --tests "AnalyticsPrivacySweepTest"` | ✅ | ✅ green |
| 5C-02-T2 | 02 | 2 | ANL-01, ANL-02 | T-05C-04, T-05C-13 | Closed-enum window param; unknown nonblank → 400; default/blank → 7d; OpenAPI shape stable | `@WebMvcTest` MockMvc + OpenAPI assertion | `./gradlew :backend:api:test --tests "AnalyticsControllerContractTest"` | ✅ | ✅ green |
| 5C-03-T1 | 03 | 3 | ANL-03, WEB-02 | T-05C-07..09 | Channel-free `DigestPayload` (no `htmlBody`/`mimeType`/`subject`); Resend imports banned from core; prefs controller tenant-scoped | ArchUnit + `@WebMvcTest` + unit | `./gradlew :backend:core:test --tests "ResendBoundaryArchTest" --tests "DigestPayloadShapeArchTest" :backend:worker:test --tests "DigestComposerTest" :backend:api:test --tests "NotificationPreferencesControllerTest"` | ✅ | ✅ green |
| 5C-03-T2 | 03 | 3 | ANL-03 | T-05C-07..09 | Thymeleaf snapshot HTML+TXT vi/en; missing-key fail-loud; `Idempotency-Key` = `tenantId:digestDayLocal`; log scrub | Snapshot + integration + Mockito + ArchUnit + log scrub | `./gradlew :backend:worker:test --tests "ThymeleafDigestRendererTest" --tests "DigestMessageSourceParityTest" --tests "EmailNotificationChannelTest" --tests "DigestPrivacySweepTest" :backend:core:test --tests "ResendBoundaryArchTest"` | ✅ | ✅ green |
| 5C-03-T3 | 03 | 3 | ANL-03 | T-05C-10..12 | Hourly cron + ShedLock; per-tenant claim idempotent (UNIQUE catches re-run); reaper promotes stuck PENDING → FAILED past grace | Testcontainers + injected `Clock` + AopTestUtils for proxy unwrap | `./gradlew :backend:worker:test --tests "DigestDispatchSchedulerTest" --tests "DigestIdempotencyTest" --tests "DigestPendingReaperJobTest" --tests "DigestDispatchWithNoopChannelTest"` | ✅ | ✅ green |
| 5C-04-T1 | 04 | 4 | ANL-01 | T-05C-13, T-05C-14 | Closed-enum `?window=` URL gate; empty-state + window-switch + no-NaN; 320px responsive; i18n parity | Vitest + Playwright + i18n strict | `pnpm --filter web tsc --noEmit && pnpm --filter web test features/analytics --run && pnpm --filter web i18n:check` + `pnpm --filter web playwright test e2e/analytics.spec.ts` | ✅ | ✅ green |
| 5C-04-T2 | 04 | 4 | WEB-02 | T-05C-13 | Optimistic toggle + send-hour persistence + error rollback; D-07 disclosure; vi/en parity | Vitest + Playwright + lint + i18n strict | `pnpm --filter web tsc --noEmit && pnpm --filter web test features/notifications --run && pnpm --filter web lint && pnpm --filter web i18n:check` + `pnpm --filter web playwright test e2e/settings-notifications.spec.ts` | ✅ | ✅ green |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Seeded from `05C-RESEARCH.md` §Validation Architecture › Wave 0 Gaps. Each line is a NEW test file the phase had to create before its first verified task ran. All 18 files exist and were green at task-commit time per each plan's SUMMARY.md Verification section.

**Backend (Java) — `backend/core`:**
- [x] `src/test/java/com/zeromail/core/arch/DigestPayloadShapeArchTest.java` — ArchUnit "no fields named `htmlBody`/`mimeType`/`subject`" on `DigestPayload`.
- [x] `src/test/java/com/zeromail/core/analytics/AnalyticsSummaryQueryServiceTest.java` — Testcontainers Postgres, seeded fixtures, all 4 queries (Q1–Q4).
- [x] `src/test/java/com/zeromail/core/arch/AnalyticsRepositoryContentBanTest.java` — ArchUnit ban on raw-body / prompt / completion columns reaching analytics service.
- [x] `src/test/java/com/zeromail/core/analytics/AnalyticsPrivacySweepTest.java` — mirrors `TriagePrivacySweepTest`, `SensitiveMarkerScrubFilter` enforced.

**Backend (Java) — `backend/worker`:** (ResendBoundaryArchTest moved here — Wave 0 listed `backend/core` but the ArchUnit ban applies where Resend SDK could be imported)
- [x] `src/test/java/com/zeromail/worker/arch/ResendBoundaryArchTest.java` — ArchUnit, mirrors `LlmGatewayBoundaryTest`.

**Backend (Java) — `backend/api`:**
- [x] `src/test/java/com/zeromail/api/controllers/analytics/AnalyticsControllerContractTest.java` — `@WebMvcTest` + MockMvc, typed response shape.
- [x] `src/test/java/com/zeromail/api/controllers/notifications/NotificationPreferencesControllerTest.java` — auth + tenant scoping.

**Backend (Java) — `backend/worker`:**
- [x] `src/test/java/com/zeromail/worker/notification/DigestDispatchSchedulerTest.java` — Testcontainers + injected `Clock`, hourly fanout assertion.
- [x] `src/test/java/com/zeromail/worker/notification/DigestIdempotencyTest.java` — double-run, assert ≤1 `NotificationChannel.dispatch()`.
- [x] `src/test/java/com/zeromail/worker/notification/DigestComposerTest.java` — zero-activity branch + non-zero branch.
- [x] `src/test/java/com/zeromail/worker/notification/ThymeleafDigestRendererTest.java` — snapshot HTML + TXT, vi + en.
- [x] `src/test/java/com/zeromail/worker/notification/DigestMessageSourceParityTest.java` — fail-loud on missing key.
- [x] `src/test/java/com/zeromail/worker/notification/email/EmailNotificationChannelTest.java` — mock Resend client, assert `Idempotency-Key` header = `tenantId:digestDayLocal`.
- [x] `src/test/java/com/zeromail/worker/notification/DigestPrivacySweepTest.java` — `ListAppender` + `SensitiveMarkerScrubFilter`.
- [x] `src/test/java/com/zeromail/worker/notification/DigestPendingReaperJobTest.java` — grace-period promotion `PENDING → FAILED`.

**Frontend (TypeScript) — `apps/web`:**
- [x] `features/analytics/__tests__/AnalyticsPanels.test.tsx` — empty-state + window-switch + zero-data → no NaN.
- [x] `features/notifications/__tests__/NotificationsSection.test.tsx` — optimistic toggle, send-hour persistence, error rollback.
- [x] `e2e/analytics.spec.ts` — full window-switch flow (auth, render, switch chip, fetch).
- [x] `e2e/settings-notifications.spec.ts` — opt-out + send-hour persistence + i18n.

**Framework install:** none — all test infrastructure (JUnit 5, Testcontainers, ArchUnit, Vitest, Playwright, jsdom) was already present.

**Supporting tests added beyond Wave 0** (created during execution, not part of the original Wave 0 list — all green at commit time):

- `backend/core` — `NotificationPreferencePersistenceTest`, `DigestDeliveryUniqueConstraintTest`, `NotificationPreferenceBackfillTest`, `OAuthProvisioningDefaultsTest`, `GmailDeliveryProcessingSenderEmailTest`, `EmailAddressCanonicalizerTest`, `TimeSavedWeightsTest`.
- `backend/worker` — `DigestDispatchWithNoopChannelTest`, `DigestDispatchTestData` (shared fixture).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live Resend deliverability to Gmail / Vietnamese ISP MX | ANL-03 (operational) | Requires a verified sending domain DNS record and a real Gmail inbox — not assertable in CI. | (1) Deploy to staging with `RESEND_API_KEY` + verified domain. (2) Toggle `digest_enabled=true` for a test tenant. (3) Confirm digest arrives in Gmail Primary tab within ±5 min of `digest_send_hour_local`. (4) Verify Vietnamese diacritics render correctly in both HTML and TXT bodies. |
| Real Gmail OAuth → digest opt-out preference write | ANL-03, D-17 | OAuth provisioning side-effect is integration-tested with mocks, but live Google grant flow is manual once per release. | Run the bundled-OAuth flow end-to-end; verify `notification_preference (tenant_id, 'EMAIL', true, 20)` row exists after provisioning. |
| Visual QA: 4 panels at design-token fidelity in vi + en, dark/light, 320px → 1920px | ANL-01, UI-SPEC §1–§4 | Pixel/typography fidelity is reviewer judgment, not automatable. | Run `apps/web` dev server; switch Vi/En; toggle theme; resize to 320 / 768 / 1024 / 1920; capture screenshots for design review. |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 90s per task
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** complete — all 10 tasks across 4 plans have automated verify commands referencing existing, green tests.

---

## Validation Audit 2026-05-14

| Metric | Count |
|--------|-------|
| Gaps found | 0 |
| Resolved | 0 |
| Escalated | 0 |
| Per-task map rows populated | 10 / 10 |
| Wave 0 test files present | 18 / 18 |
| Manual-only items recorded | 3 |

**Audit notes:**
- State A (existing VALIDATION.md). Per-task map was placeholder-only at audit start; populated from the four PLAN `<verify><automated>` blocks and matched against SUMMARY Verification logs.
- ArchUnit `ResendBoundaryArchTest` lives in `backend/worker` (not `backend/core` as Wave 0 originally listed). The boundary it enforces is "Resend SDK imports stay in worker.notification.email", which is only meaningful from within `backend/worker`. Treated as a Wave 0 location correction, not a gap.
- No auditor subagent spawn was needed: zero MISSING, zero PARTIAL.
- Manual-only set unchanged (deliverability, real-Gmail OAuth, design-token visual QA).
