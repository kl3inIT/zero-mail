---
phase: 05C-user-surface-analytics-daily-digest
verified: 2026-05-14T04:12:43Z
status: passed
score: 6/6 must-haves verified
decision_coverage:
  honored: 24
  total: 24
  not_honored: []
acknowledged_gaps:
  - "Live Resend deliverability requires third-party setup (RESEND_API_KEY and verified sender domain) and is tracked in 05C-03-USER-SETUP.md / 05C-UAT.md."
---

# Phase 05C: User Surface - Analytics & Daily Digest Verification Report

**Phase Goal:** An analytics screen shows volume triaged, estimated time saved, top senders, and rule hits over a user-selectable window, derived from per-message metadata only (no bodies, prompts, or completions stored or queried); and each day a connected tenant receives a digest email summarizing triage activity for the prior day.
**Verified:** 2026-05-14T04:12:43Z
**Status:** passed

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Analytics screen exposes volume triaged, estimated time saved, top senders, and rule hits. | VERIFIED | `apps/web/app/(protected)/(app)/analytics/page.tsx` renders `AnalyticsPageClient`; panels exist under `features/analytics/components/*`; Playwright e2e verified all four panels. |
| 2 | Analytics supports user-selectable 7d, 30d, and 90d windows. | VERIFIED | `WindowChips.tsx` normalizes URL params; `analytics-api.ts` calls `GET /api/analytics/summary`; e2e verified 7d/30d/90d switching and invalid-window canonicalization. |
| 3 | Analytics derives metrics from metadata only. | VERIFIED | `AnalyticsSummaryQueryService` queries `mail_message_observed` and `triage_audit` metadata columns only; `AnalyticsRepositoryContentBanTest` and `AnalyticsPrivacySweepTest` passed. |
| 4 | Gmail observed-message rows capture sanitized sender metadata without raw body reads. | VERIFIED | `GmailDeliveryProcessingService` uses `setMetadataHeaders(List.of("From"))`, canonicalizes through `EmailAddressCanonicalizer`, and persists nullable `sender_email`. |
| 5 | Daily digest composition reuses the analytics read model for a prior-day window. | VERIFIED | `DigestComposer` injects `AnalyticsSummaryQueryService` and calls `TimeWindow.between(sendMoment.minus(Duration.ofHours(24)), sendMoment)`. |
| 6 | Connected tenants have a scheduled, idempotent email digest path. | VERIFIED | `DigestDispatchScheduler` uses hourly cron plus ShedLock; `DigestDeliveryService` claims unique `(tenant, digest_day)` rows; `EmailNotificationChannel` sends Resend email with `Idempotency-Key`; worker tests passed. |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/core/src/main/resources/db/changelog/changes/032-037-*.yaml` | Analytics/digest schema foundation | EXISTS + SUBSTANTIVE | Adds `sender_email`, tenant time zone, `notification_preference`, `digest_delivery`, indexes, and backfill. |
| `backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryQueryService.java` | Metadata-only analytics aggregation | EXISTS + SUBSTANTIVE | Implements Q1-Q4 via parameterized `JdbcTemplate` queries. |
| `backend/api/src/main/java/com/zeromail/api/controllers/analytics/AnalyticsController.java` | `GET /api/analytics/summary` | EXISTS + SUBSTANTIVE | Thin controller maps tenant context and validates window IDs. |
| `backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestComposer.java` | Digest payload composition | EXISTS + SUBSTANTIVE | Builds channel-free `DigestPayload` from analytics projections. |
| `backend/worker/src/main/java/com/zeromail/worker/notification/*` | Scheduled digest dispatch and reaper | EXISTS + SUBSTANTIVE | Scheduler, tenant worker, and pending reaper are implemented with ShedLock and DB FSM writes. |
| `backend/worker/src/main/java/com/zeromail/worker/notification/email/*` | Resend-backed email channel | EXISTS + SUBSTANTIVE | Thymeleaf renderer plus Resend gateway with HTML/text body and idempotency header. |
| `backend/api/src/main/java/com/zeromail/api/controllers/notifications/NotificationPreferencesController.java` | Notification preferences API | EXISTS + SUBSTANTIVE | `GET/PATCH /api/me/notifications` with tenant-scoped service calls. |
| `apps/web/app/(protected)/(app)/analytics/page.tsx` and `apps/web/features/analytics/**` | Authenticated analytics UI | EXISTS + SUBSTANTIVE | Route, API function, query hook, window chips, and four panels are present. |
| `apps/web/features/notifications/**` and settings route wiring | Settings notifications UI | EXISTS + SUBSTANTIVE | Optimistic toggle, send-hour select, query/mutation hooks, and tests are present. |

**Artifacts:** 9/9 verified

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| Gmail observation | `mail_message_observed.sender_email` | Metadata-only Gmail API request | WIRED | `setMetadataHeaders(List.of("From"))` plus `insertObservedIfAbsent(... senderEmail)`. |
| Analytics API | Analytics query service | Controller service injection | WIRED | `AnalyticsController` calls `AnalyticsSummaryQueryService.summarize(...)`. |
| Web analytics hook | `/api/analytics/summary` | typed OpenAPI client | WIRED | `features/analytics/api/analytics-api.ts` calls `api.GET('/api/analytics/summary', ...)`. |
| Digest composer | Analytics query service | direct service call | WIRED | `DigestComposer` injects `AnalyticsSummaryQueryService`. |
| Worker scheduler | tenant digest dispatch | DB fanout + tenant worker | WIRED | `DigestDispatchScheduler` finds due tenants and delegates to `DigestDispatchTenantWorker`. |
| Digest tenant worker | Resend email channel | `NotificationChannel.dispatch` | WIRED | Tenant worker composes payload, dispatches, then marks SENT/FAILED. |
| Notification settings UI | `/api/me/notifications` | typed OpenAPI client | WIRED | `notifications-api.ts` calls GET/PATCH; `NotificationsSection` uses query and mutation hooks. |
| App sidebar | `/analytics` route | nav item | WIRED | `AppSidebar.tsx` includes `/analytics` after Triage. |

**Wiring:** 8/8 connections verified

## Requirements Coverage

| Requirement | Status | Evidence |
|-------------|--------|----------|
| ANL-01: User sees volume triaged, estimated time saved, top senders, and rule hits over a selectable window | SATISFIED | Backend analytics contract, frontend panels, window chips, Vitest, and Playwright e2e passed. |
| ANL-02: Metrics derive from minimal per-message metadata only | SATISFIED | Analytics SQL reads metadata tables/columns only; content-ban and privacy sweep tests passed. |
| ANL-03: Daily digest email summarizes triage activity for the prior day | SATISFIED | Digest composer, scheduler, delivery FSM, renderer, Resend gateway, idempotency tests, and reaper tests passed. Live third-party delivery remains setup-dependent. |
| WEB-02 analytics portion | SATISFIED | `/analytics` route, sidebar nav, settings notification controls, i18n, typed API schema, and e2e coverage are present. |

**Coverage:** 4/4 requirements satisfied

## Behavioral Verification

| Check | Result | Detail |
|-------|--------|--------|
| Backend module checks | PASS | `.\gradlew.bat :backend:core:check :backend:api:check :backend:worker:check` completed `BUILD SUCCESSFUL`. |
| Web typecheck | PASS | `pnpm --filter web typecheck`. |
| Web lint | PASS | `pnpm --filter web lint`. |
| Web i18n parity | PASS | `pnpm --filter web i18n:check` reported vi/en parity, 697 leaf keys. |
| Analytics Vitest | PASS | `pnpm --filter web test features/analytics --run`: 1 file, 3 tests passed. |
| Notifications Vitest | PASS | `pnpm --filter web test features/notifications --run`: 1 file, 3 tests passed. |
| Analytics + notification Playwright e2e | PASS | `pnpm --filter web test:e2e e2e/analytics.spec.ts e2e/settings-notifications.spec.ts`: 7 tests passed. |
| Package aggregate check | N/A | `pnpm --filter web check` has no script in the `web` package; phase-specific commands above are the active verification surface. |

## Test Quality Audit

| Area | Result | Evidence |
|------|--------|----------|
| Disabled requirement-linked tests | PASS | No `it.skip`, `describe.skip`, `test.skip`, `@Disabled`, or todo tests found in 5C-linked backend/frontend tests. |
| Circular expected-value generation | PASS | No fixture-generation writes detected. Matches for `capture` were assertion captors (`ArgumentCaptor`, Playwright request capture), not generated baselines. |
| Assertion strength | PASS | Tests include value-level and workflow assertions for analytics numbers, window URL changes, notification PATCH payloads, idempotency, Resend headers, renderer output, privacy logs, and DB constraints. |

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `apps/web/i18n/messages/{en,vi}.json` | existing legal-copy keys | `placeholder*` keys | Info | Pre-existing legal/privacy placeholder content from public legal pages, not introduced as 5C functional UI and not part of analytics/digest flow. |
| `apps/web/components/ui/select.tsx` | 44 | `data-placeholder` CSS token | Info | shadcn Select primitive state class, not placeholder product content. |
| `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java` | 220 | `return null` | Info | Intentional nullable sender metadata when Gmail payload/headers are absent; analytics filters null senders. |

**Anti-patterns:** 0 blockers, 0 warnings, 3 informational findings

## Human Verification

No blocking human verification remains for code-level phase completion.

`05C-UAT.md` is `status: partial` because live Resend deliverability could not be exercised without `RESEND_API_KEY` and a verified sender domain. The user explicitly accepted this UAT state for shipping on 2026-05-14. The missing live send is an acknowledged third-party/deployment setup item, not a code gap, because the backend dispatch path, idempotency behavior, scheduler annotations, renderer, and email gateway are covered by automated tests.

## Acknowledged Non-Code Gap

| Gap | Status | Tracking |
|-----|--------|----------|
| Live Resend email delivery to a real mailbox | Acknowledged for ship; requires third-party setup | `.planning/phases/05C-user-surface-analytics-daily-digest/05C-03-USER-SETUP.md` and `05C-UAT.md` test 13 |

## Decision Coverage

All trackable `05C-CONTEXT.md` decisions are honored by shipped artifacts.

| Honored | Total | Blocking |
|---------|-------|----------|
| 24 | 24 | false |

## Gaps Summary

**No code gaps found.** Phase goal is achieved for the implemented repository surfaces and is ready to proceed to shipping.

## Verification Metadata

**Verification approach:** Goal-backward verification from ROADMAP success criteria, summaries, code wiring, automated tests, and UAT artifact review.
**Must-haves source:** ROADMAP success criteria plus 05C plan summaries.
**Automated checks:** 7 command groups passed, 0 failed.
**Human checks required:** 0 blocking; 1 acknowledged third-party live-delivery setup item.
**Total verification time:** about 12 minutes.

---
*Verified: 2026-05-14T04:12:43Z*
*Verifier: Codex*
