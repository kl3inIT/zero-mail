---
phase: 8
slug: bulk-unsubscribe-campaign
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-17
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Sourced from `08-RESEARCH.md §"Validation Architecture"`.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend framework** | JUnit 5 + Mockito + AssertJ + Spring Boot Test + Testcontainers (Postgres 17, Redis 7) + ArchUnit 1.4.x + Spring Modulith Test |
| **Frontend framework** | Vitest + Testing Library + Playwright |
| **Config files** | `backend/core/build.gradle.kts` (test task), `apps/web/vitest.config.ts`, `apps/web/playwright.config.ts` |
| **Quick run command** | `./gradlew :backend:core:test :backend:worker:test` + `pnpm --filter web test` |
| **Full suite command** | `./gradlew check` + `pnpm --filter web test && pnpm --filter web e2e` + `pnpm i18n:check` |
| **Estimated runtime** | Quick ~90s · Full ~6 minutes (Testcontainers warm-up dominant) |

---

## Sampling Rate

- **After every task commit:** `./gradlew :backend:core:test :backend:worker:test --rerun-tasks` + `pnpm --filter web typecheck && pnpm --filter web lint`
- **After every plan wave:** `./gradlew check` + `pnpm --filter web test && pnpm --filter web e2e --grep "cleanup"` + `pnpm i18n:check`
- **Before `/gsd:verify-work`:** Full suite green + Liquibase migration apply/rollback test on fresh Postgres container + Playwright full e2e + privacy sweep explicit
- **Max feedback latency:** ~90 seconds (quick) — Testcontainers reuse keeps slice tests fast

---

## Per-Task Verification Map

> Concrete task IDs are assigned by `gsd-planner` when PLAN.md files are written. The table below maps **SPEC requirement IDs (UNS-01..UNS-09)** to verification targets. Planner will distribute these across plans + waves; each task in `<verification>` block must reference one of the requirement IDs below.

| Req ID | Requirement (from SPEC.md) | Test Type | Automated Command | File Exists | Status |
|--------|----------------------------|-----------|-------------------|-------------|--------|
| UNS-01 | Candidate query — 3-sender fixture (2 valid + 1 no-header excluded + suppression filtered) | Integration (DataJpaTest + Testcontainers) | `./gradlew :backend:core:test --tests "*CandidateQueryServiceTest*"` | ❌ W0 | ⬜ pending |
| UNS-02 | Suppression CRUD + auto-add heuristic (reply ≥1/90d) | Integration (DataJpaTest + Testcontainers) | `./gradlew :backend:core:test --tests "*SuppressionService*"` | ❌ W0 | ⬜ pending |
| UNS-03a | Preview reject >25 sender → HTTP 400 `CAMPAIGN_TOO_MANY_SENDERS` | WebMvcTest | `./gradlew :backend:api:test --tests "*UnsubscribeCampaignController*"` | ❌ W0 | ⬜ pending |
| UNS-03b | Preview reject >2000 history → HTTP 400 `CAMPAIGN_TOO_MANY_MESSAGES` | WebMvcTest | `./gradlew :backend:api:test --tests "*UnsubscribeCampaignController*"` | ❌ W0 | ⬜ pending |
| UNS-04a | Execute → jobId returned; worker pickup; per-sender atomic | SpringBootTest E2E (Postgres + Redis Testcontainers) | `./gradlew :backend:worker:test --tests "*UnsubscribeCampaignE2ETest*"` | ❌ W0 | ⬜ pending |
| UNS-04b | Throttle 1/domain/60s + 10/domain/h enforced (12 sender cùng domain) | Integration with Testcontainers Redis | `./gradlew :backend:worker:test --tests "*UnsubscribeDomainThrottleTest*"` | ❌ W0 | ⬜ pending |
| UNS-04c | RFC 8058 POST status mapping (200/201/202/204=OK; 3xx/4xx/5xx/timeout=FAILED) | Unit + WireMock | `./gradlew :backend:core:test --tests "*UnsubscribeHttpClientTest*"` | ❌ W0 | ⬜ pending |
| UNS-05 | Status polling: progressPct + perSender array correct | TanStack Vitest + WebMvcTest | `pnpm --filter web test useCampaignStatus` + `./gradlew :backend:api:test --tests "*CampaignStatusController*"` | ❌ W0 | ⬜ pending |
| UNS-06 | Retry OK sender → HTTP 409 (idempotent) | WebMvcTest | `./gradlew :backend:api:test --tests "*UnsubscribeCampaignController*"` | ❌ W0 | ⬜ pending |
| UNS-07a | Undo within 30d → INBOX restored + label removed + revertedAt set | SpringBootTest with Clock injection | `./gradlew :backend:core:test --tests "*CampaignUndoService*"` | ❌ W0 | ⬜ pending |
| UNS-07b | Undo after 30d → HTTP 410 `UNDO_WINDOW_EXPIRED` | WebMvcTest with Clock injection | `./gradlew :backend:api:test --tests "*UnsubscribeCampaignController*"` | ❌ W0 | ⬜ pending |
| UNS-08a | ArchUnit: `HttpClient` / `RestClient` cấm ngoài `UnsubscribeHttpClient` | ArchUnit | `./gradlew :backend:core:test --tests "*UnsubscribeHttpClientBoundaryTest*"` | ❌ W0 | ⬜ pending |
| UNS-08b | ArchUnit: `Gmail.users().messages().send()` cấm ngoài `TriageGmailWriter` + `UnsubscribeMailtoSender` | ArchUnit (extend existing) | `./gradlew :backend:core:test --tests "*GmailWriteBoundaryTest*"` | ❌ W0 (rename/extend) | ⬜ pending |
| UNS-08c | `UnsubscribeHttpClient` reject `http://` URL + non-persisted URL | Unit | `./gradlew :backend:core:test --tests "*UnsubscribeHttpClientTest*"` | ❌ W0 | ⬜ pending |
| UNS-09 | Privacy sweep — no log/audit leak of full email/body/subject | SpringBootTest + Logback `ListAppender` | `./gradlew :backend:core:test --tests "*CleanupPrivacySweepTest*"` | ❌ W0 (mirror `TriagePrivacySweepTest`) | ⬜ pending |
| Golden path | candidate list → preview → execute → status polling → undo | Playwright | `pnpm --filter web e2e -- --grep "cleanup unsubscribe"` | ❌ W0 | ⬜ pending |
| Suppression UI | Add manual → candidates excludes; auto-add visible | Playwright | `pnpm --filter web e2e -- --grep "cleanup suppression"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

All test files referenced are **NEW** unless explicitly marked as a rename. Wave 0 (test scaffolding before Wave 1 implementation):

**Backend (12 new files + 1 rename):**
- [ ] `backend/core/src/test/java/com/zeromail/core/cleanup/usecases/CandidateQueryServiceTest.java` — stubs for UNS-01
- [ ] `backend/core/src/test/java/com/zeromail/core/cleanup/usecases/SuppressionServiceTest.java` — UNS-02
- [ ] `backend/core/src/test/java/com/zeromail/core/cleanup/usecases/CampaignUndoServiceTest.java` — UNS-07a
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/cleanup/UnsubscribeCampaignControllerTest.java` — UNS-03a/b, UNS-06, UNS-07b
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/cleanup/CampaignStatusControllerTest.java` — UNS-05 (backend half)
- [ ] `backend/core/src/test/java/com/zeromail/core/cleanup/http/UnsubscribeHttpClientTest.java` — UNS-04c, UNS-08c (WireMock)
- [ ] `backend/core/src/test/java/com/zeromail/core/arch/UnsubscribeHttpClientBoundaryTest.java` — UNS-08a
- [ ] Rename `TriageGmailWriteBoundaryTest.java` → `GmailWriteBoundaryTest.java` + extend allow-list cho `UnsubscribeMailtoSender` — UNS-08b
- [ ] `backend/core/src/test/java/com/zeromail/core/cleanup/CleanupPrivacySweepTest.java` (mirror `TriagePrivacySweepTest`) — UNS-09
- [ ] `backend/core/src/test/java/com/zeromail/core/cleanup/CleanupModuleVerificationTest.java` — Modulith allowedDependencies verify
- [ ] `backend/core/src/test/java/com/zeromail/core/cleanup/UnsubscribeMailtoSenderRecipientGuardTest.java` — D-23 mailto recipient provenance guard
- [ ] `backend/core/src/test/java/com/zeromail/core/triage/usecases/TriageGmailWriterLookupLabelIdTest.java` — H-2 `lookupLabelId` returns `Optional<String>` (empty if label deleted)
- [ ] `backend/core/src/test/java/com/zeromail/core/triage/persistence/TriageAuditWriterCleanupArchiveTest.java` — H-3 `recordCleanupArchive(...)` persists row with `source='CLEANUP_CAMPAIGN'`

**Worker (5 new files):**
- [ ] `backend/worker/src/test/java/com/zeromail/worker/cleanup/UnsubscribeCampaignE2ETest.java` — UNS-04a
- [ ] `backend/worker/src/test/java/com/zeromail/worker/cleanup/UnsubscribeDomainThrottleTest.java` — UNS-04b
- [ ] `backend/worker/src/test/java/com/zeromail/worker/scheduling/ProcessingJobReaperBatchTest.java` — Crash recovery (D-03)
- [ ] `backend/worker/src/test/java/com/zeromail/worker/scheduling/ProcessingJobPurgeBatchTest.java` — Retention purge deletes only terminal jobs older than 90d (D-25)
- [ ] `backend/worker/src/test/java/com/zeromail/worker/cleanup/ProcessingJobWorkerThrottleDeferralTest.java` — M-2: deferred path leaves `processing_job.status='QUEUED'` (NOT FAILED) after `ThrottleDeferredException` is thrown

**Frontend Vitest (2 new files):**
- [ ] `apps/web/features/cleanup/unsubscribe-campaign/hooks/__tests__/useCampaignStatus.test.ts` — polling termination logic
- [ ] `apps/web/features/cleanup/suppression/hooks/__tests__/useSuppressionList.test.ts` — CRUD optimistic update

**Playwright e2e (2 new files):**
- [ ] `apps/web/e2e/cleanup-unsubscribe-campaign.spec.ts` — Golden path UNS-05 + UNS-06 + UNS-07
- [ ] `apps/web/e2e/cleanup-suppression.spec.ts` — Suppression CRUD + auto-add visibility

**Liquibase rollback verification:** Re-use existing `LiquibaseRollbackTest` pattern (project-wide); add the 6 new changelogs `041..046` (xem CONTEXT D-09 cho chi tiết schema từng file + iteration H-3 Path A addendum for `046-triage-audit-source.yaml`) vào rollback test set.

**Test data fixtures needed:**
- 3 sender Gmail fixtures: 1 one-click, 1 mailto-only, 1 no `List-Unsubscribe` header (HTTPS fixtures via WireMock for one-click POST).
- 1 suppressed sender row in `sender_suppression` table.
- Auto-add fixture: `mail_message_observed.label_ids` chứa `SENT` label + sender_email = current user's Gmail address, JOIN với original sender's domain (≥1 trong 90d).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Provider 200 OK nhưng vẫn gửi email tiếp | UNS-04a (acceptable failure mode) | Provider-side state, không control được trong test fixture. UI disclaimer (per Risks §) là mitigation. | Sau khi ship, theo dõi support ticket → nếu pattern xuất hiện, add disclaimer copy + log analytics counter `unsubscribe.suspected_ignored`. |
| Real-world DKIM-aligned `List-Unsubscribe` URL phishing | UNS-08 (security) | Cần live Gmail account với DKIM-failed header injection — không feasible trong test container. | Bug-bash session với QA: gửi email từ external test account với spoofed header → verify Gmail Pub/Sub không deliver (Gmail upstream filters). |
| Throttle bucket Redis memory pressure (1 tenant × 1000 domain) | UNS-04b (scalability) | Synthetic load test — không thuộc phase gate. | Defer cho phase ops sau (CONTEXT.md deferred §). Add metrics counter `throttle.redis.keys.count` cho monitoring. |

---

## Invariants (must hold across all phase 8 changes)

1. **No PII in logs** — sender_email never appears unmasked; body/subject never logged. (UNS-09)
2. **HTTPS-only HTTP POST** — `UnsubscribeHttpClient` rejects non-HTTPS URLs at parse-time AND execute-time. (UNS-08c)
3. **Per-sender atomic** — Unsubscribe FAILED → archive count = 0 cho sender đó. (UNS-04a)
4. **Throttle enforced** — Domain X với 12 attempts → at most 1 per 60s, 10 per hour. (UNS-04b)
5. **Undo reversibility** — Within 30 days, restore label `INBOX` + remove `Zero Mail/Unsubscribed` per archived message. (UNS-07a)
6. **HTTP unsubscribe only from persisted header** — `UnsubscribeHttpClient.postOneClick` requires URL provenance từ `mail_message_observed.list_unsubscribe_url`. (UNS-08)
7. **Gmail send-as-self only for unsubscribe-mailto** — ArchUnit guard cover `core.cleanup`. (UNS-08b)
8. **No auto-send** — `UNSUBSCRIBE` NOT added to `RuleActionType` enum. (boundary lock từ SPEC + CONTEXT)
9. **Tenant isolation** — Per-tenant Redis throttle keys; per-tenant `sender_suppression`; per-tenant campaign + attempt rows.
10. **Crash safety** — Reaper batch reclaims stale RUNNING jobs sau 5 phút. (D-03)
11. **Job retention safety** — Purge deletes only `COMPLETED`/`FAILED` `processing_job` rows older than 90 days; fresh terminal rows and non-terminal rows stay; `unsubscribe_campaign`/`unsubscribe_attempt` stay forever. (D-25)

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (21 new test files + 1 rename — adds 3 stubs per H-2/H-3/M-2 iteration: TriageGmailWriterLookupLabelIdTest, TriageAuditWriterCleanupArchiveTest, ProcessingJobWorkerThrottleDeferralTest)
- [ ] No watch-mode flags trong test commands
- [ ] Feedback latency < 90s (quick), < 6m (full)
- [ ] `nyquist_compliant: true` set in frontmatter (sau khi planner finalize task → req mapping)
- [ ] `wave_0_complete: true` set sau khi Wave 0 ship (all test stubs in place, failing red)

**Approval:** pending
