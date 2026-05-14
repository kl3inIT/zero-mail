---
phase: 05C-user-surface-analytics-daily-digest
reviewed: 2026-05-14T00:00:00Z
depth: deep
files_reviewed: 35
files_reviewed_list:
  - apps/web/app/(protected)/(app)/analytics/page.tsx
  - apps/web/app/(protected)/(app)/settings/page.tsx
  - apps/web/components/shell/AppSidebar.tsx
  - apps/web/components/ui/select.tsx
  - apps/web/e2e/analytics.spec.ts
  - apps/web/e2e/settings-notifications.spec.ts
  - apps/web/i18n/messages/en.json
  - apps/web/i18n/messages/vi.json
  - apps/web/lib/api/schema.d.ts
  - apps/web/openapi/openapi.json
  - apps/web/scripts/check-i18n.ts
  - backend/api/src/main/java/com/zeromail/api/controllers/account/AccountDeletionController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/analytics/AnalyticsController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/notifications/NotificationPreferencesController.java
  - backend/api/src/main/java/com/zeromail/api/dto/analytics/AnalyticsSummaryResponse.java
  - backend/api/src/test/java/com/zeromail/api/controllers/analytics/AnalyticsControllerContractTest.java
  - backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java
  - backend/core/src/main/java/com/zeromail/core/analytics/domain/TimeWindow.java
  - backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryQueryService.java
  - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java
  - backend/core/src/main/java/com/zeromail/core/notification/domain/DigestPayload.java
  - backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestComposer.java
  - backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestDeliveryService.java
  - backend/core/src/main/java/com/zeromail/core/notification/usecases/NotificationPreferenceService.java
  - backend/core/src/main/java/com/zeromail/core/shared/privacy/EmailAddressCanonicalizer.java
  - backend/core/src/main/resources/db/changelog/changes/032-mail-message-observed-sender-email.yaml
  - backend/core/src/main/resources/db/changelog/changes/033-tenants-time-zone.yaml
  - backend/core/src/main/resources/db/changelog/changes/034-notification-preference.yaml
  - backend/core/src/main/resources/db/changelog/changes/035-digest-delivery.yaml
  - backend/core/src/main/resources/db/changelog/changes/036-analytics-supporting-indexes.yaml
  - backend/core/src/main/resources/db/changelog/changes/037-notification-preference-backfill.yaml
  - backend/worker/build.gradle.kts
  - backend/worker/src/main/java/com/zeromail/worker/notification/DigestDispatchScheduler.java
  - backend/worker/src/main/java/com/zeromail/worker/notification/DigestPendingReaperJob.java
  - backend/worker/src/main/java/com/zeromail/worker/notification/email/EmailNotificationChannel.java
  - backend/worker/src/main/resources/application.yml
  - gradle/libs.versions.toml
findings:
  critical: 2
  warning: 7
  info: 4
  total: 13
status: issues_found
---

# Phase 05C: Code Review Report

**Reviewed:** 2026-05-14
**Depth:** deep
**Files Reviewed:** 35 (plus traversed: AnalyticsWindow, DigestDispatchTenantWorker, ResendEmailGateway for cross-file analysis)
**Status:** issues_found

## Summary

Phase 05C delivers the user-surface analytics screen, daily digest worker, notification preferences, and email channel via Resend. Overall, the implementation honours the locked privacy invariants (no email bodies / prompts / completions logged; tenant-scoped queries; AES-GCM at application layer for refresh tokens — unchanged in this phase), and the Liquibase changelogs are conservative and reversible. Cross-file wiring controller→service→repository chains is consistent, and the OpenAPI schema regenerated correctly (NotificationPreferencesResponse/Request, AnalyticsSummaryResponse match the Java DTOs).

However, the review surfaces two correctness bugs that should block ship:

1. **Cross-tenant duplicate digest claims** caused by `JOIN users` in the dispatch SQL when a tenant has more than one user row (defensive design point — the schema does not forbid multiple users per tenant).
2. **Atomicity contract is broken** in `AccountDeletionController` — `DigestDeliveryService.deleteForTenant` uses `Propagation.REQUIRES_NEW`, so it commits independently of the controller's outer transaction. The class Javadoc explicitly promises "Controller-level @Transactional provides atomicity across the four calls" — implementation contradicts the doc.

Additional warnings cover: empty 400 body in analytics window error handler (breaks the `application/problem+json` envelope), missing `LockAssert` in the reaper, snapshot dependency in `libs.versions.toml`, and minor code-quality issues. No privacy violations were found (logs adhere to `event=<name> tenantId={}` format), no auto-send pathways through Gmail API in `EmailNotificationChannel` (digests dispatch via Resend with idempotency header), and no hardcoded secrets.

## Critical Issues

### CR-01: `DigestDispatchScheduler` JOIN produces duplicate dispatches per multi-user tenant

**File:** `backend/worker/src/main/java/com/zeromail/worker/notification/DigestDispatchScheduler.java:25-35`

**Issue:** `DUE_TENANT_SQL` joins `notification_preference np` to `users u` on `u.tenant_id = t.id`. The `users` table has no unique constraint on `tenant_id` (see `db/changelog/changes/002-create-users.yaml` — only `tenant_id` index, no unique). When a tenant has N user rows (legitimate per the schema even if v1 product policy is single-user-per-tenant), the query returns N duplicate `DigestDueTenant` rows and `scheduledDispatch` calls `digestDispatchTenantWorker.dispatchOne` N times. The first call wins (`uq_digest_delivery_tenant_day` unique key), subsequent calls throw `DigestAlreadyClaimedException`, log `digest_already_claimed`, and return — wasteful work and noisy logs, but more importantly:
- Both `preferredLanguage` selection and `dueTenant.tenantId()` come from the **last** joined user row, which is non-deterministic without an `ORDER BY` or `LIMIT 1` against `users`.
- Future analytics on digest claims will be polluted with retry noise.

The query is also susceptible to silent breakage if Phase 06+ ever adds shared / team users.

**Fix:** Restrict to one user per tenant deterministically (the "owner" — by `created_at` if no owner flag exists), e.g. use `SELECT DISTINCT ON (np.tenant_id) ... ORDER BY np.tenant_id, u.created_at`:
```sql
SELECT DISTINCT ON (np.tenant_id)
       np.tenant_id, t.time_zone, np.digest_send_hour_local, u.preferred_language
FROM notification_preference np
JOIN tenants t ON t.id = np.tenant_id
JOIN users u ON u.tenant_id = t.id
WHERE np.digest_enabled = true
  AND np.channel = 'EMAIL'
  AND EXTRACT(HOUR FROM (?::timestamptz AT TIME ZONE t.time_zone))::int = np.digest_send_hour_local
ORDER BY np.tenant_id, u.created_at ASC
```
Alternatively, lift the user-language resolution into `DigestDispatchTenantWorker` and select preferred_language explicitly per dispatched tenant.

---

### CR-02: `AccountDeletionController` Javadoc claims atomicity that the implementation does not deliver

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/account/AccountDeletionController.java:18-25, 50-59`

**Issue:** The Javadoc states: "Controller-level `@Transactional` provides atomicity across the four calls; each delegated service method is itself `@Transactional` so propagation joins the controller's transaction (Spring default REQUIRED)." This is FALSE for `DigestDeliveryService.deleteForTenant` and likely for `NotificationPreferenceService.deleteForTenant` (which itself uses `@Transactional` default — joins). Verified:
- `DigestDeliveryService.deleteForTenant` (line 85) is annotated `@Transactional(propagation = Propagation.REQUIRES_NEW)`. This propagation **suspends the controller transaction**, opens a NEW transaction, commits it, then resumes the outer transaction. If `accountService.deleteCurrentUser` or `tenantService.deleteCurrentTenant` later fails, the `digest_delivery` rows are gone but the tenant survives.

The bug surface is small in practice (digest_delivery has `deleteCascade: true` on its FK to tenants, so a successful tenant delete would handle it anyway), but the implementation diverges from the explicit doc claim and from the cross-domain delete invariant. Any future developer reading the doc and adding logic in step 6 or 7 that depends on rollback of step 5 will be surprised.

**Fix:** Either (a) change `DigestDeliveryService.deleteForTenant` propagation to default REQUIRED so it joins the controller transaction:
```java
@Transactional
public void deleteForTenant(UUID tenantId) {
    digestDeliveryRepository.deleteByTenantId(tenantId);
}
```
or (b) update the controller Javadoc to acknowledge that digest_delivery is deleted in its own transaction by design (e.g., to keep the lock window short on a large table) and that the cascade FK is the final correctness guard. Option (a) is the safer default given the doc claim.

## Warnings

### WR-01: `AnalyticsController.invalidWindow` returns 400 with no body — breaks `application/problem+json` envelope

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/analytics/AnalyticsController.java:52-54`

**Issue:** The handler is:
```java
@ExceptionHandler(NoSuchElementException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
void invalidWindow() {}
```
This returns `400 Bad Request` with an EMPTY body. The OpenAPI schema (`apps/web/openapi/openapi.json:3373-3376`) declares `"400": { "description": "Bad Request" }` with no content type. Compared to the project-wide error contract (every other 4xx in the same OpenAPI doc uses `application/problem+json` referencing `ApiError`), this is inconsistent and gives the frontend nothing to render. The catch is also wide: any `NoSuchElementException` from anywhere in the controller's call chain becomes a 400 (no logging, no event for observability).

**Fix:** Catch the exception and throw the project's standard validation `ApiException` so the global handler produces an `ApiError` body:
```java
@ExceptionHandler(NoSuchElementException.class)
ResponseEntity<ApiError> invalidWindow(NoSuchElementException exception) {
    log.info("event=analytics_summary_bad_window message_class={}", exception.getClass().getSimpleName());
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(ApiError.of("error.validation.field.window.invalid"));
}
```
Or have `AnalyticsWindow.fromId` throw a controller-level validation exception with a specific code, and let the global error handler map it.

---

### WR-02: `DigestPendingReaperJob.scheduledReap` is missing `LockAssert.assertLocked()` consistency

**File:** `backend/worker/src/main/java/com/zeromail/worker/notification/DigestPendingReaperJob.java:49-51`

**Issue:** `DigestDispatchScheduler.scheduledDispatch` correctly calls `LockAssert.assertLocked()` first (line 56) to fail fast if the ShedLock annotation is misconfigured. `DigestPendingReaperJob.scheduledReap` does not, despite using `@SchedulerLock` on the same job class. If the lock provider misconfigures (e.g. table renamed) the reaper will silently run on every node every 5 minutes, double-marking-failed and producing the wrong totals in `event=digest_pending_reaped`.

**Fix:** Add `LockAssert.assertLocked()` as the first line of `scheduledReap()`:
```java
@Scheduled(fixedDelay = 300_000L)
@SchedulerLock(name = LOCK_NAME, lockAtLeastFor = "PT1M", lockAtMostFor = "PT5M")
public void scheduledReap() {
    LockAssert.assertLocked();
    reap();
}
```

---

### WR-03: `gradle/libs.versions.toml` pins `springModulith = "2.0.7-SNAPSHOT"` for a release-candidate phase

**File:** `gradle/libs.versions.toml:8`

**Issue:** The version catalog uses a `-SNAPSHOT` dependency for `springModulith`. The pin comment correctly documents why (no GA/milestone available), but a SNAPSHOT artifact is mutable — a future rebuild may resolve a different bytecode for the same version string. For deterministic CI/production builds this is undesirable. The note also says to revisit when M1 ships; that has likely shifted since the comment was written.

**Fix:** Re-check Spring repository for a non-SNAPSHOT Boot-4-compatible Modulith release (Context7 should be consulted for the latest pre-release line). If no GA/M-release is available, document a "rebuild artifact pin" strategy (e.g. mirror the resolved JAR's SHA-256 in CI to detect drift). At minimum, schedule a follow-up to drop SNAPSHOT before any production cut.

---

### WR-04: `analyticsSummaryQueryService.summarize` recomputed inside `DigestComposer` — no caching between API and worker

**File:** `backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestComposer.java:40-43`

**Issue:** The digest composer runs the FULL `AnalyticsSummaryQueryService.summarize` (5 SQL queries) for every tenant on every digest send, with a 24-hour window. This is correct, but at 09:00 local for a popular cohort all those queries hit `mail_message_observed` and `triage_audit` simultaneously (Cron is `0 5 * * * *` — one batch per UTC hour). Indices added in changelog 036 mitigate this, but consider:
- Two of the queries (`OBSERVED_VOLUME_SQL`, `TOP_SENDERS_SQL`) use the partial index `idx_mail_message_observed_tenant_sender_observed` only when filtering by `sender_email IS NOT NULL`. `OBSERVED_VOLUME_SQL` doesn't apply that filter, so it relies on the new full index `idx_mail_message_observed_tenant_observed_at`. Good.
- `RULE_HITS_SQL` uses `decided_at` but the new index is `idx_triage_audit_tenant_rule_decided` — used. Good.

The N+1 risk is low because each tenant is dispatched serially per `DigestDispatchScheduler.scheduledDispatch` iteration, but if the cohort grows the 20-minute lock window (`lockAtMostFor = "PT20M"`) is a soft cap. No correctness bug — flagging for observability.

**Fix:** Add metrics: emit per-tenant `event=digest_compose_latency_ms tenantId={} durationMs={}` so growth can be monitored before it bites. No structural change needed in v1.

---

### WR-05: `DigestDispatchScheduler` exception handling silently swallows stack trace

**File:** `backend/worker/src/main/java/com/zeromail/worker/notification/DigestDispatchScheduler.java:62-68`

**Issue:**
```java
} catch (RuntimeException runtimeException) {
    log.info("event=digest_tenant_failed tenantId={}", dueTenant.tenantId());
    log.debug("event=digest_tenant_failed_debug tenantId={}", dueTenant.tenantId(), runtimeException);
}
```
The stack trace is only logged at DEBUG level. In production (typical INFO level), an unexpected `NullPointerException` or `IllegalStateException` in `dispatchOne` is invisible — the only signal is the `digest_tenant_failed` count. The Resend gateway catches its own exceptions and returns `TransientFailure`, so a runtime exception here is most likely a code bug we'd want to investigate.

**Fix:** Promote the exception class name to INFO and keep the full stack at WARN:
```java
} catch (RuntimeException runtimeException) {
    log.warn(
        "event=digest_tenant_failed tenantId={} failureType={}",
        dueTenant.tenantId(),
        runtimeException.getClass().getSimpleName(),
        runtimeException);
}
```
This preserves the privacy contract (no payload bytes — only class name + tenantId) while making real bugs visible.

---

### WR-06: `EmailAddressCanonicalizer.canonicalize` re-trims after `toLowerCase` for no reason; angle-bracket parser accepts `>` before `<`

**File:** `backend/core/src/main/java/com/zeromail/core/shared/privacy/EmailAddressCanonicalizer.java:13-35`

**Issue:** Two minor defects:
1. Line 18: `extractedAddress.toLowerCase(Locale.ROOT).trim()` — `extractedAddress` is already trimmed by `extractAddress` (line 32) and `toLowerCase` cannot introduce whitespace. The redundant `.trim()` is harmless but signals lack of care; reviewers will wonder if there's a case we missed.
2. `extractAddress` uses `lastIndexOf('<')` and `lastIndexOf('>')`. For input `"foo> <bar@example.com"`, leftAngle = idx of `<`, rightAngle = LAST `>` which is BEFORE `<` (idx 3 vs idx 5). The check `rightAngle <= leftAngle` correctly throws — good. But for input `"<bar@example.com>"` mixed with display name `"<x><a@b>"`: leftAngle = idx of LAST `<` (position 3), rightAngle = idx of LAST `>` (position 7). Substring is `"a@b"` — fine. So the function is robust to weird inputs by virtue of using the LAST occurrence. The risk is that a malicious mailbox label like `"<evil <a@b>"` would yield `"a@b"`, not `"evil <a@b"`. Acceptable — we're aiming to extract the rightmost address.

**Fix:** Drop the redundant `.trim()` on line 18 and add a brief unit-test case for `"foo> <bar@example.com"` to lock in the rejection behavior.

---

### WR-07: `AnalyticsSummaryQueryService.queryAppliedByActionType` silently overrides duplicate keys

**File:** `backend/core/src/main/java/com/zeromail/core/analytics/projection/AnalyticsSummaryQueryService.java:128-138`

**Issue:** The SQL groups by `action_type` so duplicates shouldn't occur, but the `appliedByActionType.put(...)` overwrites silently if they did (e.g., enum case drift, NULL action_type collapse). A safer collector either rejects duplicate keys or asserts:
```java
String actionType = resultSet.getString("action_type");
long count = resultSet.getLong(2);
Long previous = appliedByActionType.put(actionType, count);
if (previous != null) {
    throw new IllegalStateException("Duplicate action_type in time-saved query: " + actionType);
}
```
Also note: `resultSet.getString("action_type")` could return `null` if a row exists without an action_type — `TimeSavedWeights.computeSeconds(...)` may NPE depending on its implementation. Defensive: skip null keys with a one-liner.

**Fix:** Add the duplicate-key guard and a null-key skip:
```java
String actionType = resultSet.getString("action_type");
if (actionType == null) {
    return;
}
appliedByActionType.merge(actionType, resultSet.getLong(2), (oldValue, newValue) -> {
    throw new IllegalStateException("Duplicate action_type in time-saved query: " + actionType);
});
```

## Info

### IN-01: `DigestComposer.compose` duplicates the `.limit(3)` from the SQL layer

**File:** `backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestComposer.java:60, 69`

**Issue:** `AnalyticsSummaryQueryService.TOP_SENDERS_SQL` already has `LIMIT 3`. `DigestComposer.compose` also calls `.limit(3)` on top senders and top rules. The SQL limit applies to top senders; the Java limit is a redundant safety net. Rule hits SQL has NO `LIMIT`, so the `.limit(3)` there is meaningful (it caps the digest content). Mark the intent explicitly:

**Fix:** Add a comment on the rule-hits `.limit(3)` line clarifying that it intentionally caps the digest section while the underlying query returns full rule hits for the analytics screen, and remove the redundant `.limit(3)` on top-senders OR lift the SQL limit and rely on Java capping consistently.

---

### IN-02: `AccountDeletionController` order is redundant given FK cascade on `notification_preference` and `digest_delivery`

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/account/AccountDeletionController.java:51-58`

**Issue:** `notification_preference` and `digest_delivery` both declare `deleteCascade: true` to `tenants(id)` in changelogs 034 and 035. The explicit `notificationPreferenceService.deleteForTenant(tenantId)` and `digestDeliveryService.deleteForTenant(tenantId)` calls are defensive but redundant when `tenantService.deleteCurrentTenant(tenantId)` runs successfully. This adds two extra SQL DELETEs per account deletion. Acceptable as a defensive pattern; consider adding a comment explaining why the calls are kept (e.g., to keep deletion idempotent if tenant delete is skipped or to enforce ordering for FK-aware logical replication).

**Fix:** Add a one-line comment:
```java
// Explicit deletes for notification_preference and digest_delivery are belt-and-suspenders;
// both tables also cascade via FK to tenants(id), which is the final guard.
notificationPreferenceService.deleteForTenant(tenantId);
digestDeliveryService.deleteForTenant(tenantId);
```

---

### IN-03: `OAuthProvisioningService.provisionBundledOAuth` race-loser path swallows DataIntegrityViolation cause

**File:** `backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java:185-201`

**Issue:** In the catch block, `userRepository.findByGoogleSubject(googleSubject).orElseThrow(() -> dataIntegrityViolation)` re-throws the original integrity violation only if the lookup also fails. If the lookup succeeds, we silently treat the race as resolved. This is correct behaviour but the WARN log `event=oauth_provisioning_race` does not include the constraint name or any tenant correlation — operationally hard to triage. Consider including `failureType={class}` or the constraint name (carefully — Postgres constraint names are not PII).

**Fix:** Enrich the log:
```java
log.warn(
    "event=oauth_provisioning_race googleSubjectHash={} failureType={}",
    googleSubject.hashCode(),
    dataIntegrityViolation.getClass().getSimpleName());
```
(Hashing googleSubject avoids logging the raw OIDC sub. The hash gives operators a correlator without the value.)

---

### IN-04: `apps/web/components/ui/select.tsx` is shadcn primitive source — already covered by ESLint/Prettier ignore

**File:** `apps/web/components/ui/select.tsx:1-201`

**Issue:** This file was modified during the phase. Per `apps/web/AGENTS.md`: "`components/ui/**` is copied shadcn primitive source and is ignored by ESLint and Prettier. Avoid hand-rolling primitives that shadcn already provides." If the file was modified by the team rather than copied from shadcn CLI (`pnpm dlx shadcn@latest add select`), future shadcn upgrades will overwrite the changes. Verify the diff against the shadcn registry, and if customizations are required, wrap them in `features/<feature>` components instead of modifying the primitive.

**Fix:** Confirm whether the change came from `pnpm dlx shadcn@latest add select` (acceptable) or a manual edit (lift the customization into a wrapper component). If manual, document the divergence so the next shadcn upgrade does not silently regress it.

---

_Reviewed: 2026-05-14_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
