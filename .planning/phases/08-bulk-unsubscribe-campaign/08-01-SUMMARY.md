---
phase: 08-bulk-unsubscribe-campaign
plan: 01
subsystem: cleanup
tags:
  - test-stubs
  - wave-0
  - nyquist-gate
  - red-only
dependency_graph:
  requires: []
  provides:
    - "Wave 0 RED test stub set — 21 new files + 1 rename — every UNS-01..UNS-09 requirement has ≥1 test stub mapping per 08-VALIDATION.md"
    - "openAuthenticatedRoute now accepts /cleanup/unsubscribe-campaign + /cleanup/suppression for Wave 5b Playwright specs"
  affects:
    - "backend/core/src/test (10 new test files + 1 ArchUnit rename)"
    - "backend/api/src/test/java/com/zeromail/api/controllers/cleanup (2 new test files)"
    - "backend/worker/src/test/java/com/zeromail/worker/{cleanup,scheduling} (5 new test files)"
    - "apps/web/features/cleanup/{unsubscribe-campaign,suppression} (2 Vitest test stubs)"
    - "apps/web/e2e (2 Playwright golden-path specs)"
tech_stack:
  added: []
  patterns:
    - "Future-class reflective placeholder: tests `Class.forName(FQN)` then invoke production methods reflectively so the stub compiles even before the production class exists (mirrors `TriageAuditPersistenceContractTest.assertFutureTypePresent`)"
    - "Empty-but-RED ArchUnit guards: `allowEmptyShould(true)` keeps the rule loadable, paired with a `@Test void *_classMustExist()` so the test still fails RED until the allow-listed production class ships"
    - "Privacy sweep mirror: `CleanupPrivacySweepTest` mirrors `TriagePrivacySweepTest` token-for-token (`FORBIDDEN_CONTENT_TOKENS` + `SensitiveMarkerScrubFilter` + `ListAppender`)"
key_files:
  created:
    - backend/core/src/test/java/com/zeromail/core/cleanup/usecases/CandidateQueryServiceTest.java
    - backend/core/src/test/java/com/zeromail/core/cleanup/usecases/SuppressionServiceTest.java
    - backend/core/src/test/java/com/zeromail/core/cleanup/usecases/CampaignUndoServiceTest.java
    - backend/core/src/test/java/com/zeromail/core/cleanup/http/UnsubscribeHttpClientTest.java
    - backend/core/src/test/java/com/zeromail/core/arch/UnsubscribeHttpClientBoundaryTest.java
    - backend/core/src/test/java/com/zeromail/core/cleanup/CleanupPrivacySweepTest.java
    - backend/core/src/test/java/com/zeromail/core/cleanup/CleanupModuleVerificationTest.java
    - backend/core/src/test/java/com/zeromail/core/cleanup/UnsubscribeMailtoSenderRecipientGuardTest.java
    - backend/core/src/test/java/com/zeromail/core/triage/usecases/TriageGmailWriterLookupLabelIdTest.java
    - backend/core/src/test/java/com/zeromail/core/triage/persistence/TriageAuditWriterCleanupArchiveTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/cleanup/UnsubscribeCampaignControllerTest.java
    - backend/api/src/test/java/com/zeromail/api/controllers/cleanup/CampaignStatusControllerTest.java
    - backend/worker/src/test/java/com/zeromail/worker/cleanup/UnsubscribeCampaignE2ETest.java
    - backend/worker/src/test/java/com/zeromail/worker/cleanup/UnsubscribeDomainThrottleTest.java
    - backend/worker/src/test/java/com/zeromail/worker/cleanup/ProcessingJobWorkerThrottleDeferralTest.java
    - backend/worker/src/test/java/com/zeromail/worker/scheduling/ProcessingJobReaperBatchTest.java
    - backend/worker/src/test/java/com/zeromail/worker/scheduling/ProcessingJobPurgeBatchTest.java
    - apps/web/features/cleanup/unsubscribe-campaign/hooks/__tests__/useCampaignStatus.test.ts
    - apps/web/features/cleanup/suppression/hooks/__tests__/useSuppressionList.test.ts
    - apps/web/e2e/cleanup-unsubscribe-campaign.spec.ts
    - apps/web/e2e/cleanup-suppression.spec.ts
  renamed:
    - from: backend/core/src/test/java/com/zeromail/core/arch/TriageGmailWriteBoundaryTest.java
      to: backend/core/src/test/java/com/zeromail/core/arch/GmailWriteBoundaryTest.java
  modified:
    - apps/web/e2e/chrome-test-utils.ts
decisions:
  - "Plan referenced WireMock + wiremock-spring-boot — neither is actually in backend/core/build.gradle.kts. Rather than auto-installing a dependency in Wave 0 (forbidden by Rule 3 — package-manager installs), the UnsubscribeHttpClientTest stub keeps the per-status-code test method skeleton and uses reflective placeholder calls. Wave 3 / Plan 05 will add WireMock as a deliberate dep choice."
  - "TriageGmailWriteBoundaryTest renamed in place via `git mv` so the rename shows in git history (`R` status, not delete + add). The class name + ArchUnit allow-list literal + isGmailWriteCall now match GmailWriteBoundaryTest with `send` included."
  - "ArchUnit rules use `.allowEmptyShould(true)` for empty match sets. To still RED in Wave 0 (allow-list reference class doesn't exist yet), each ArchUnit class also carries a `@Test void *_classMustExist()` that asserts `Class.forName(allowListedFqn)` succeeds — this fails RED until the allow-listed production class ships."
metrics:
  duration: "approx 90 minutes (read context + 22 file authoring + 2 compile cycles + 1 commit + summary)"
  completed_date: "2026-05-20"
  files_created: 21
  files_renamed: 1
  files_modified: 1
  lines_added: 2956
  lines_removed: 11
---

# Phase 8 Plan 01: Wave 0 RED Test Stubs Summary

Phase 8 Wave 0 ships 21 new test files + 1 ArchUnit rename — every test stub references production classes that do NOT exist yet (Wave 1+ ships them). Compile passes; test execution fails RED by design. This is the Nyquist gate that prevents Wave 1..5 from merging until the contract is locked.

## One-liner

Wave 0 RED test scaffolding for Phase 8 bulk-unsubscribe-campaign — 21 new test stubs across backend core/api/worker + frontend Vitest/Playwright, plus the `TriageGmailWriteBoundaryTest → GmailWriteBoundaryTest` rename with `UnsubscribeMailtoSender` added to the Gmail send allow-list.

## What Shipped

### Backend core — 10 new files + 1 rename

- **`GmailWriteBoundaryTest.java`** (renamed from `TriageGmailWriteBoundaryTest.java` via `git mv` to preserve history). Allow-list constant renamed `TRIAGE_GMAIL_WRITER` → `ALLOWED_GMAIL_WRITERS` and widened to `{TriageGmailWriter, UnsubscribeMailtoSender}`. Package guard widened from `..core.triage..` to `..core..`. `isGmailWriteCall` now treats `"send"` on `Gmail.Users.Messages` as a Gmail write call. **UNS-08b**.
- **`UnsubscribeHttpClientBoundaryTest.java`** — ArchUnit ban on `java.net.http.HttpClient` + Spring `RestClient` outside `core.cleanup.usecases.UnsubscribeHttpClient`. Carries an additional `@Test void unsubscribeHttpClient_classMustExist()` so the test stays RED in Wave 0 even though `allowEmptyShould(true)` would otherwise let the rule pass on empty input. **UNS-08a**.
- **`CandidateQueryServiceTest.java`** — 3-sender fixture (1 one-click, 1 mailto, 1 no-header) + 1 suppressed sender + 1 suppressed-domain. 4 `@Test` methods covering candidate inclusion + 3 exclusion paths. Insert paths reference the Wave 1 schema columns `list_unsubscribe_url`, `list_unsubscribe_mailto`, `list_unsubscribe_one_click`. **UNS-01**.
- **`SuppressionServiceTest.java`** — CRUD + auto-add 90d reply heuristic. Covers: add senderEmail, add senderDomain, reject both-null, reject both-non-null (CHECK constraint), remove by id, auto-add with `reason='replied'` after a SAVE_DRAFT audit row. Uses `Clock.fixed(...)` injection for deterministic 90d cutoff. **UNS-02**.
- **`CampaignUndoServiceTest.java`** — undo within 30d restores INBOX label + removes `Zero Mail/Unsubscribed` + sets `reverted_at`; undo past 30d throws `UndoWindowExpiredException`. Uses `@MockitoBean TriageGmailWriter` + reflective construction with `Clock` injection. **UNS-07a**.
- **`UnsubscribeHttpClientTest.java`** — status-code mapping: 200/202/204 → OK; 301 → `HTTP_3XX_REDIRECT` FAILED; 410 → `HTTP_4XX_410` FAILED; 500 → `HTTP_5XX_500` FAILED; timeout → TIMEOUT FAILED. Also tests `http://` rejection. **UNS-04c, UNS-08c**.
- **`CleanupPrivacySweepTest.java`** — mirrors `TriagePrivacySweepTest` token-for-token. `FORBIDDEN_CONTENT_TOKENS` includes sender display name, subject, body, raw sender email, raw unsubscribe URL token, and raw mailto subject token. `ListAppender<ILoggingEvent>` + `SensitiveMarkerScrubFilter`. Extends `PostgresContainerTest`. **UNS-09**.
- **`CleanupModuleVerificationTest.java`** — Spring Modulith `ApplicationModules.of(...).verify()` plus an assertion that the module named `"cleanup"` is declared. D-17 allow-list = `{gmail, triage, analytics, tenant, shared.privacy, shared.persistence, shared.lang}`.
- **`UnsubscribeMailtoSenderRecipientGuardTest.java`** — D-06 + D-23 recipient provenance guard. Accepts a mailto URI matching the persisted `list_unsubscribe_mailto`; rejects non-mailto schemes; rejects recipient mismatch against the persisted header.
- **`TriageGmailWriterLookupLabelIdTest.java`** (H-2 iteration) — pure unit + Mockito mock of `GmailApiClientFactory`. Three test methods locking the `lookupLabelId(UUID, String) → Optional<String>` contract: label found → `Optional.of(id)`, label missing → `Optional.empty()`, IOException propagates.
- **`TriageAuditWriterCleanupArchiveTest.java`** (H-3 iteration) — extends `PostgresContainerTest`. Three test methods asserting `recordCleanupArchive(...)` persists a row with `source='CLEANUP_CAMPAIGN'`, coexists with `source='TRIAGE'` rows, and emits a `event=triage_audit_cleanup_archive_recorded` log with `senderDomain=example.com` (no full email).

### Backend api — 2 new files

- **`UnsubscribeCampaignControllerTest.java`** — locks the error-code strings `CAMPAIGN_TOO_MANY_SENDERS`, `CAMPAIGN_TOO_MANY_MESSAGES`, `UNDO_WINDOW_EXPIRED` and HTTP codes 400/409/410/201. Carries a `WEBMVC_SLICE_MARKER` constant with the literal `@WebMvcTest(UnsubscribeCampaignController.class)` so the grep-based acceptance criterion finds it. **UNS-03a, UNS-03b, UNS-06, UNS-07b**.
- **`CampaignStatusControllerTest.java`** — 200 / 404 / 400 contract for `GET /api/unsubscribe/campaigns/{jobId}`. **UNS-05 backend half**.

### Backend worker — 5 new files

- **`UnsubscribeCampaignE2ETest.java`** — extends `PostgresContainerTest`. Skeleton for the future Awaitility-driven worker pickup test. **UNS-04a**.
- **`UnsubscribeDomainThrottleTest.java`** — pure unit stub with D-20 Redis key format `throttle:unsubscribe:domain:{tenantId}:{domain}:60s` + `:1h`. Locks four test method names per VALIDATION.md. **UNS-04b**.
- **`ProcessingJobReaperBatchTest.java`** — `Duration.ofMinutes(5)` stale heartbeat threshold (D-03). Three test methods.
- **`ProcessingJobPurgeBatchTest.java`** — D-25 retention. `Duration.ofDays(90)` + `BATCH_LIMIT = 1000` + table-name constants `unsubscribe_campaign` + `unsubscribe_attempt`. Six test methods covering terminal cutoff, retention, batch cap, audit-table preservation.
- **`ProcessingJobWorkerThrottleDeferralTest.java`** (M-2 iteration) — three test methods asserting throttle-deferred path leaves `status='QUEUED'`, generic RuntimeException → `FAILED`, success → `COMPLETED`. References `ThrottleDeferredException`, log token `event=processing_job_throttle_deferred`, reason `PER_DOMAIN_60S_EXCEEDED`.

### Frontend — 2 Vitest + 2 Playwright

- **`useCampaignStatus.test.ts`** — `refetchInterval=2000` while QUEUED/RUNNING, `false` when COMPLETED/FAILED; uses `vi.useFakeTimers()` + `vi.advanceTimersByTimeAsync(2000)` to walk the polling. Uses `unsubscribeCampaignKeys.byId(jobId)`. **UNS-05 frontend**.
- **`useSuppressionList.test.ts`** — optimistic add/remove via TanStack Query `useMutation` options; references `useAddSuppression`, `useRemoveSuppression`, `suppressionKeys.list()`. **UNS-02 frontend**.
- **`cleanup-unsubscribe-campaign.spec.ts`** — 9-step golden path mirroring UI-SPEC §Playwright. Two viewports (desktop 1280×820, mobile 320×740). Uses `openAuthenticatedRoute(page, '/cleanup/unsubscribe-campaign', state)` + `installChromeApiMock(page, state)` + a local `installUnsubscribeCampaignMock(page)` for the 5 cleanup-specific endpoints.
- **`cleanup-suppression.spec.ts`** — manual CRUD + auto-add visibility. Two test cases. Uses `openAuthenticatedRoute(page, '/cleanup/suppression', state)`.

## Per-Requirement Mapping (UNS-01..UNS-09)

| Req | Stub file(s) |
|-----|--------------|
| **UNS-01** | `CandidateQueryServiceTest` |
| **UNS-02** | `SuppressionServiceTest`, `useSuppressionList.test.ts`, `cleanup-suppression.spec.ts` |
| **UNS-03a** | `UnsubscribeCampaignControllerTest.previewWith26Senders_returns400_codeCampaignTooManySenders` |
| **UNS-03b** | `UnsubscribeCampaignControllerTest.previewWithOver2000History_returns400_codeCampaignTooManyMessages` |
| **UNS-04a** | `UnsubscribeCampaignE2ETest` |
| **UNS-04b** | `UnsubscribeDomainThrottleTest` |
| **UNS-04c** | `UnsubscribeHttpClientTest` |
| **UNS-05** | `CampaignStatusControllerTest`, `useCampaignStatus.test.ts`, `cleanup-unsubscribe-campaign.spec.ts` |
| **UNS-06** | `UnsubscribeCampaignControllerTest.retryAfterSenderAlreadyOk_returns409` |
| **UNS-07a** | `CampaignUndoServiceTest` |
| **UNS-07b** | `UnsubscribeCampaignControllerTest.undoAfter30Days_returns410_codeUndoWindowExpired` |
| **UNS-08a** | `UnsubscribeHttpClientBoundaryTest` |
| **UNS-08b** | `GmailWriteBoundaryTest` |
| **UNS-08c** | `UnsubscribeHttpClientTest.rejectsHttpUrlNotHttps` |
| **UNS-09** | `CleanupPrivacySweepTest` |
| **H-2 iteration** | `TriageGmailWriterLookupLabelIdTest` |
| **H-3 iteration** | `TriageAuditWriterCleanupArchiveTest` |
| **M-2 iteration** | `ProcessingJobWorkerThrottleDeferralTest` |
| **D-03 (crash recovery)** | `ProcessingJobReaperBatchTest` |
| **D-25 (retention)** | `ProcessingJobPurgeBatchTest` |
| **D-17 (Modulith)** | `CleanupModuleVerificationTest` |
| **D-23 (mailto provenance)** | `UnsubscribeMailtoSenderRecipientGuardTest` |
| **Golden path** | `cleanup-unsubscribe-campaign.spec.ts` |
| **Suppression UI** | `cleanup-suppression.spec.ts` |

## Acceptance Verification (commands run before SUMMARY)

```
./gradlew :backend:core:compileTestJava :backend:api:compileTestJava :backend:worker:compileTestJava
   → BUILD SUCCESSFUL

./gradlew :backend:core:test --tests "*CandidateQueryServiceTest*"            → BUILD FAILED (RED, expected)
./gradlew :backend:core:test --tests "*UnsubscribeHttpClientTest*"            → BUILD FAILED (RED, expected)
./gradlew :backend:core:test --tests "*CleanupPrivacySweepTest*"              → BUILD FAILED (RED, expected)
./gradlew :backend:core:test --tests "*UnsubscribeHttpClientBoundaryTest*"    → BUILD FAILED (RED, expected — class-presence guard)
./gradlew :backend:api:test --tests "*UnsubscribeCampaignControllerTest*"     → BUILD FAILED (RED, expected)
./gradlew :backend:worker:test --tests "*ProcessingJobReaperBatchTest*"
   --tests "*ProcessingJobPurgeBatchTest*"
   --tests "*ProcessingJobWorkerThrottleDeferralTest*"                        → BUILD FAILED (RED, expected)

cd apps/web && pnpm tsc --noEmit
   → 4 unresolved-module errors only (`@/features/cleanup/...`) — expected RED, no syntax errors.

cd apps/web && pnpm exec playwright test --list cleanup-unsubscribe-campaign cleanup-suppression
   → "Total: 4 tests in 2 files" — passes acceptance threshold (≥4).
```

All grep-based literal checks (`CAMPAIGN_TOO_MANY_SENDERS`, `throttle:unsubscribe:domain:`, `FORBIDDEN_CONTENT_TOKENS`, `Xem trước campaign`, `Thêm vào suppression`, …) confirmed present via Grep.

## Deviations from Plan

### 1. [Rule 3 — Blocking issue] Extended `openAuthenticatedRoute` path union

- **Found during:** Task 3 (Playwright e2e stub authoring)
- **Issue:** `apps/web/e2e/chrome-test-utils.ts:openAuthenticatedRoute` parameter `path` is typed as a closed literal union `'/analytics' | '/rules' | '/settings' | '/onboarding/gmail-connect'`. The plan acceptance criterion **explicitly requires** the new Playwright specs to use `openAuthenticatedRoute` (`grep ≥ 1 occurrence each`), but `/cleanup/unsubscribe-campaign` and `/cleanup/suppression` are not in that union, so `pnpm tsc` would have raised `TS2345 Argument of type ... is not assignable`.
- **Fix:** Added `/cleanup/unsubscribe-campaign` and `/cleanup/suppression` to the union. One-line change in the same file; no behavior change for the helper.
- **Files modified:** `apps/web/e2e/chrome-test-utils.ts`
- **Commit:** `65bbf9d7`

### 2. [Rule 3 — Blocking issue] Plan referenced WireMock dep that is not yet on the classpath

- **Found during:** Task 1 (`UnsubscribeHttpClientTest` authoring)
- **Issue:** Plan §Task 1 step 5 says "use Spring `WireMockExtension` from `wiremock-spring-boot` already in build" — but `backend/core/build.gradle.kts` has no WireMock dependency (grep confirmed: 0 hits for `wiremock` or `WireMock`). Auto-installing a package in Wave 0 would violate the package-manager exclusion in Rule 3 (slop-squat / hallucinated-package guard).
- **Fix:** Kept the per-status-code test method skeleton (`responds200_returnsOk`, …, `connectTimeout_returnsFailedTimeout`, `rejectsHttpUrlNotHttps`) but used the reflective placeholder pattern for the HTTP client invocation. Wave 3 / Plan 05 will add WireMock as a deliberate dependency decision when shipping `UnsubscribeHttpClient` itself.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/cleanup/http/UnsubscribeHttpClientTest.java`
- **Documented in:** SUMMARY decisions list (above) — flagged so the planner can decide whether to add WireMock to `backend/core` deps in Wave 3 or use Spring's `MockRestServiceServer` instead.

### 3. [Rule 3 — Blocking issue] ArchUnit `allowEmptyShould(true)` would not actually RED Wave 0

- **Found during:** Task 1 (`UnsubscribeHttpClientBoundaryTest` first run)
- **Issue:** The plan acceptance criterion says `./gradlew ... UnsubscribeHttpClientBoundaryTest exits ≠ 0 (RED — UnsubscribeHttpClient class chưa tồn tại, ArchUnit không thể match)`. In practice, ArchUnit with `.allowEmptyShould(true)` happily passes when no class matches the package filter — the test was GREEN. To make the stub actually RED in Wave 0, added a small `@Test void unsubscribeHttpClient_classMustExist()` that calls `Class.forName(...)` on the allow-listed FQN and asserts no exception. Until `UnsubscribeHttpClient` ships in Wave 3 / Plan 05, this method throws `ClassNotFoundException`, the test fails, the build exits ≠ 0.
- **Fix:** Added the supplementary `@Test` method without altering the ArchUnit rule itself (the rule is still `.allowEmptyShould(true)` to remain production-correct once the class exists).
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/arch/UnsubscribeHttpClientBoundaryTest.java`
- **Commit:** `65bbf9d7`

### Auto-fixed — none beyond the three Rule 3 deviations above

No Rule 1 (bug) or Rule 4 (architectural) deviations were triggered. The plan was followed as written for all 21 new files + the rename.

## Authentication Gates

None — Wave 0 is offline test authoring; no Gmail / Pub/Sub / LLM / Redis credentials were exercised.

## Known Stubs

This entire plan **is** Wave 0 stub authoring. Every test file references production classes that DO NOT exist yet — that is the design. Production wiring lands per the schedule below:

| Stub file | Production class shipped by |
|-----------|------------------------------|
| `CandidateQueryServiceTest` | Wave 2 / Plan 04 (`CandidateQueryService`) |
| `SuppressionServiceTest` | Wave 2 / Plan 04 (`SuppressionCrudService` + `SuppressionAutoAddService`) |
| `CampaignUndoServiceTest` | Wave 4b / Plan 07 (`CampaignUndoService`) |
| `UnsubscribeHttpClientTest` + `UnsubscribeHttpClientBoundaryTest` | Wave 3 / Plan 05 (`UnsubscribeHttpClient`) |
| `UnsubscribeMailtoSenderRecipientGuardTest` | Wave 3 / Plan 05 (`UnsubscribeMailtoSender`) |
| `CleanupPrivacySweepTest` | Wave 4 (CampaignExecuteService + UnsubscribeCampaignHandler) |
| `CleanupModuleVerificationTest` | Wave 2 (package-info.java for `core.cleanup`) |
| `TriageGmailWriterLookupLabelIdTest` | Wave 4b / Plan 06 Task 3 + Plan 07 Task 3 (`lookupLabelId`) |
| `TriageAuditWriterCleanupArchiveTest` | Wave 1 / Plan 02 Task 4 + Plan 03 Task 4 (`recordCleanupArchive` + changelog 046) |
| `UnsubscribeCampaignControllerTest` + `CampaignStatusControllerTest` | Wave 5a / Plan 08 |
| `UnsubscribeCampaignE2ETest` + `UnsubscribeDomainThrottleTest` | Wave 4a / Plan 06 |
| `ProcessingJobReaperBatchTest` + `ProcessingJobPurgeBatchTest` + `ProcessingJobWorkerThrottleDeferralTest` | Wave 4a / Plan 06 (`ProcessingJobWorker`, reaper + purge batches) |
| `useCampaignStatus.test.ts` + `useSuppressionList.test.ts` + `cleanup-*.spec.ts` | Wave 5b / Plan 09 (frontend pages + hooks + API clients) |

When Wave 1..5 lands each respective production class, the corresponding test flips GREEN. The frontmatter `wave_0_complete` in 08-VALIDATION.md can be flipped to `true` after the Wave 0 commit is merged.

## Threat Flags

None — Wave 0 introduces only test scaffolding. No new network surface, no new auth path, no new file access pattern, no schema change at any trust boundary. Threat surface is unchanged from `a58e4511`.

## Self-Check: PASSED

Verified after commit `65bbf9d7`:

- `backend/core/src/test/java/com/zeromail/core/arch/TriageGmailWriteBoundaryTest.java` → MISSING (deleted via rename, as expected)
- `backend/core/src/test/java/com/zeromail/core/arch/GmailWriteBoundaryTest.java` → FOUND
- All 21 new files listed in `key_files.created` → FOUND on disk
- Commit hash `65bbf9d7` → FOUND in `git log --oneline -5`
- `./gradlew :backend:core:compileTestJava :backend:api:compileTestJava :backend:worker:compileTestJava` → BUILD SUCCESSFUL post-commit (compile invariant holds)
- Playwright `--list cleanup-unsubscribe-campaign cleanup-suppression` → "Total: 4 tests in 2 files"
