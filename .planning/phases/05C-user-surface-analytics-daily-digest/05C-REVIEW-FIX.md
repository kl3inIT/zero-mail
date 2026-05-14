---
phase: 05C-user-surface-analytics-daily-digest
fixed_at: 2026-05-14T02:53:01Z
review_path: .planning/phases/05C-user-surface-analytics-daily-digest/05C-REVIEW.md
iteration: 1
findings_in_scope: 13
fixed: 12
skipped: 1
status: partial
---

# Phase 05C: Code Review Fix Report

**Fixed at:** 2026-05-14T02:53:01Z
**Source review:** `.planning/phases/05C-user-surface-analytics-daily-digest/05C-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 13
- Fixed: 12
- Skipped: 1
- Follow-up verification commits: 1df64a0, fb528c6

## Fixed Issues

### CR-01: `DigestDispatchScheduler` JOIN produces duplicate dispatches per multi-user tenant

**Status:** fixed: requires human verification
**Files modified:** `backend/worker/src/main/java/com/zeromail/worker/notification/DigestDispatchScheduler.java`, `backend/worker/src/test/java/com/zeromail/worker/notification/DigestDispatchSchedulerTest.java`, `backend/worker/src/test/java/com/zeromail/worker/notification/DigestDispatchTestData.java`
**Commit:** 489a361
**Applied fix:** Changed the due-tenant SQL to `SELECT DISTINCT ON (np.tenant_id)` ordered by user creation time and id, and added a regression test for multiple users on one tenant.

### CR-02: `AccountDeletionController` Javadoc claims atomicity that the implementation does not deliver

**Status:** fixed: requires human verification
**Files modified:** `backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestDeliveryService.java`
**Commit:** 7d5c3cb
**Applied fix:** Changed `DigestDeliveryService.deleteForTenant` from `REQUIRES_NEW` to default `REQUIRED` propagation so it joins account deletion transactions.

### WR-01: `AnalyticsController.invalidWindow` returns 400 with no body

**Status:** fixed
**Files modified:** `backend/api/src/main/java/com/zeromail/api/controllers/analytics/AnalyticsController.java`, `backend/api/src/test/java/com/zeromail/api/controllers/analytics/AnalyticsControllerContractTest.java`
**Commit:** 3ab35f2
**Applied fix:** Replaced the empty 400 handler with a narrow invalid-window exception handler returning a ProblemDetail body and updated the controller contract test.

### WR-02: `DigestPendingReaperJob.scheduledReap` is missing `LockAssert.assertLocked()`

**Status:** fixed
**Files modified:** `backend/worker/src/main/java/com/zeromail/worker/notification/DigestPendingReaperJob.java`, `backend/worker/src/test/java/com/zeromail/worker/notification/DigestPendingReaperJobTest.java`
**Commit:** a7a9c5e, follow-up 1df64a0
**Applied fix:** Added `LockAssert.assertLocked()` as the first scheduled reaper statement and covered the fail-fast behavior in tests. The follow-up commit calls the target object directly so the test validates `LockAssert` without the ShedLock proxy satisfying the assertion.

### WR-03: `gradle/libs.versions.toml` pins `springModulith = "2.0.7-SNAPSHOT"`

**Status:** fixed
**Files modified:** `gradle/libs.versions.toml`, `buildSrc/src/main/kotlin/zeromail.modulith-conventions.gradle.kts`
**Commit:** 5560597
**Applied fix:** Replaced the snapshot pin with the documented stable Spring Modulith `2.0.6` BOM and kept the buildSrc hard-coded BOM in sync.

### WR-04: `AnalyticsSummaryQueryService.summarize` recomputed inside `DigestComposer`

**Status:** fixed
**Files modified:** `backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestComposer.java`
**Commit:** 4ec772e
**Applied fix:** Added `event=digest_compose_latency_ms tenantId={} durationMs={}` logging around digest composition.

### WR-05: `DigestDispatchScheduler` exception handling silently swallows stack trace

**Status:** fixed
**Files modified:** `backend/worker/src/main/java/com/zeromail/worker/notification/DigestDispatchScheduler.java`
**Commit:** 3ef6c04
**Applied fix:** Promoted unexpected tenant dispatch failures to `WARN` with tenant id, exception class, and stack trace.

### WR-06: `EmailAddressCanonicalizer.canonicalize` has redundant trim and needs malformed-angle coverage

**Status:** fixed
**Files modified:** `backend/core/src/main/java/com/zeromail/core/shared/privacy/EmailAddressCanonicalizer.java`, `backend/core/src/test/java/com/zeromail/core/shared/privacy/EmailAddressCanonicalizerTest.java`
**Commit:** 38ba754
**Applied fix:** Removed the redundant post-lowercase trim and added focused canonicalizer tests for normal display-name extraction and malformed angle brackets.

### WR-07: `AnalyticsSummaryQueryService.queryAppliedByActionType` silently overrides duplicate keys

**Status:** fixed
**Files modified:** `backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryQueryService.java`
**Commit:** f30c551
**Applied fix:** Skips null action types defensively and fails loud on duplicate action-type rows instead of silently overwriting counts.

### IN-01: `DigestComposer.compose` duplicates the `.limit(3)` from the SQL layer

**Status:** fixed
**Files modified:** `backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestComposer.java`
**Commit:** 6192dd9
**Applied fix:** Removed the redundant top-sender Java limit and documented that the rule-hit Java limit intentionally caps digest email content.

### IN-02: `AccountDeletionController` order is redundant given FK cascade

**Status:** fixed
**Files modified:** `backend/api/src/main/java/com/zeromail/api/controllers/account/AccountDeletionController.java`
**Commit:** cfb1320
**Applied fix:** Added a short comment explaining the explicit notification and digest deletes are defensive because tenant FKs also cascade.

### IN-03: `OAuthProvisioningService.provisionBundledOAuth` race-loser path swallows cause context

**Status:** fixed
**Files modified:** `backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java`
**Commit:** 6e04ac8
**Applied fix:** Enriched the race log with tenant id, a non-raw Google subject hash, and the integrity exception class.

## Skipped Issues

### IN-04: `apps/web/components/ui/select.tsx` is shadcn primitive source

**File:** `apps/web/components/ui/select.tsx:1`
**Reason:** No source change required. Verified from `apps/web` with `pnpm dlx shadcn@latest add select --diff`; the CLI reported `components\ui\select.tsx (skip)` and `No changes.`
**Original issue:** Confirm whether the select primitive was copied from shadcn or manually customized, and avoid modifying copied primitive source for product-specific customizations.

---

_Fixed: 2026-05-14T02:53:01Z_
_Fixer: the agent (gsd-code-fixer)_
_Iteration: 1_
