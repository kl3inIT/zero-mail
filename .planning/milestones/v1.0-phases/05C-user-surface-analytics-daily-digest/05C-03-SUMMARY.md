---
phase: 05C-user-surface-analytics-daily-digest
plan: 03
subsystem: notifications-digest
tags: [spring-scheduling, shedlock, resend, thymeleaf, postgres, spring-mvc]

requires:
  - phase: 05C-01
    provides: notification_preference, digest_delivery, tenants.time_zone, sender_email
  - phase: 05C-02
    provides: AnalyticsSummaryQueryService and TimeWindow for digest composition
provides:
  - channel-free DigestPayload and NotificationChannel contracts
  - Notification preferences GET/PATCH API
  - Thymeleaf email rendering and Resend-backed email channel
  - hourly digest dispatch scheduler with ShedLock and per-tenant isolation
  - stuck-PENDING digest reaper
affects: [05C-04, notifications, analytics, worker, frontend-api-codegen]

tech-stack:
  added:
    - com.resend:resend-java:4.13.0
    - org.springframework.boot:spring-boot-starter-thymeleaf
  patterns:
    - provider-specific email code isolated under worker.notification.email
    - digest FSM writes isolated in DigestDeliveryService REQUIRES_NEW methods
    - scheduler tests unwrap ShedLock proxy with AopTestUtils for deterministic manual invocation

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/notification/domain/DigestPayload.java
    - backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestComposer.java
    - backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestDeliveryService.java
    - backend/api/src/main/java/com/zeromail/api/controllers/notifications/NotificationPreferencesController.java
    - backend/worker/src/main/java/com/zeromail/worker/notification/email/EmailNotificationChannel.java
    - backend/worker/src/main/java/com/zeromail/worker/notification/DigestDispatchScheduler.java
    - backend/worker/src/main/java/com/zeromail/worker/notification/DigestPendingReaperJob.java
    - .planning/phases/05C-user-surface-analytics-daily-digest/05C-03-USER-SETUP.md
  modified:
    - gradle/libs.versions.toml
    - backend/worker/build.gradle.kts
    - backend/worker/src/main/resources/application.yml
    - .planning/phases/05C-user-surface-analytics-daily-digest/05C-CONTEXT.md

key-decisions:
  - "Daily digest email delivery uses Resend Java SDK 4.13.0 behind worker.notification.email only."
  - "One pair of Thymeleaf templates renders all locales through Spring MessageSource keys."
  - "Digest dispatch runs at cron 0 5 * * * * under ShedLock name digestDispatchScheduler."
  - "Missed-hour catch-up is intentionally off in v1; D-07 is locked to exact-hour delivery only."
  - "Worker scheduler behavior tests unwrap the ShedLock proxy so idempotency is proven by the digest_delivery unique constraint, not by a held test lock."

patterns-established:
  - "Keep channel-neutral digest content in core.notification domain records; channel body rendering belongs in worker adapters."
  - "Keep outbound provider SDK imports isolated to worker.notification.email and guarded by ArchUnit."
  - "For scheduled worker integration tests, use AopTestUtils.getTargetObject(...) when testing method behavior separately from ShedLock acquisition."

requirements-completed:
  - ANL-03
  - WEB-02

duration: 1h 46m
completed: 2026-05-13
---

# Phase 05C Plan 03: Daily Digest Backend Summary

**Channel-neutral digest composition, Resend email delivery, notification preferences API, and hourly worker dispatch with stuck-PENDING recovery**

## Performance

- **Duration:** 1h 46m
- **Started:** 2026-05-13T16:20:00Z
- **Completed:** 2026-05-13T18:06:36Z
- **Tasks:** 3
- **Files modified:** 41 code/resource/test files plus 2 planning artifacts

## Accomplishments

- Added digest domain payload records, dispatch outcomes, notification channel contract, composer, and delivery FSM service.
- Exposed `GET /api/me/notifications` and `PATCH /api/me/notifications` through thin API DTO/controller surfaces.
- Added Resend email delivery with HTML/text Thymeleaf templates, vi/en message bundles, idempotency header, provider boundary tests, and privacy sweeps.
- Added hourly digest fanout with `@Scheduled(cron = "0 5 * * * *")`, ShedLock, tenant ScopedValue binding, send-hour anchored analytics windows, idempotent claims, and a stuck-PENDING reaper.

## Task Commits

1. **Task 1: Core digest contracts and preferences API** - `480898a` (`feat`)
2. **Task 2: Resend email channel and templates** - `8988319` (`feat`)
3. **Task 3: Dispatch scheduler and pending reaper** - `42112f4` (`feat`)

**Plan metadata:** this summary commit.

## Files Created

- `backend/core/src/main/java/com/zeromail/core/notification/domain/DigestPayload.java` - channel-free digest content record.
- `backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestComposer.java` - maps analytics summary data into digest payloads.
- `backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestDeliveryService.java` - `claimPending`, `markSent`, and `markFailed` FSM write surface.
- `backend/api/src/main/java/com/zeromail/api/controllers/notifications/NotificationPreferencesController.java` - authenticated notification preferences endpoints.
- `backend/worker/src/main/java/com/zeromail/worker/notification/email/*` - Thymeleaf renderer, Resend gateway, and email channel adapter.
- `backend/worker/src/main/resources/email-templates/digest/*` - HTML and plaintext digest templates.
- `backend/worker/src/main/java/com/zeromail/worker/notification/DigestDispatchScheduler.java` - due-tenant fanout scheduler.
- `backend/worker/src/main/java/com/zeromail/worker/notification/DigestDispatchTenantWorker.java` - per-tenant dispatch unit outside any long DB transaction.
- `backend/worker/src/main/java/com/zeromail/worker/notification/DigestPendingReaperJob.java` - stuck-PENDING failure promotion job.
- `backend/worker/src/test/java/com/zeromail/worker/notification/*Digest*Test.java` - scheduler, idempotency, renderer, message parity, privacy, and reaper coverage.

## Decisions Made

- Resend SDK version is `4.13.0`. Context7 docs for `/resend/resend-java` confirmed the 4.x builder shape for `CreateEmailOptions.builder()`, `addHeader(...)`, `addTag(...)`, `CreateEmailResponse.getId()`, and `ResendException.getStatusCode()`. Local jar inspection confirmed tags are built with `Tag.builder().name(...).value(...).build()`.
- `spring.main.keep-alive: true` was added in Task 2 so the worker remains alive when only scheduled/background jobs are active.
- The digest scheduler uses cron `0 5 * * * *` and ShedLock name `digestDispatchScheduler`; the reaper uses `digestPendingReaper` with a 5 minute fixed delay.
- Digest content windows anchor to the configured local send-hour boundary (`HH:00`), not the cron's `HH:05` execution instant.
- D-07 is now explicit: v1 skips a digest if the worker is down through the tenant's exact send-hour tick; no catch-up query is shipped.
- `UserRepository.findEmailByTenantId` was added in Task 1 as a native tenant-id lookup for digest recipient resolution.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Bypassed ShedLock proxy for scheduler behavior tests**
- **Found during:** Task 3 verification
- **Issue:** Direct calls to `scheduler.scheduledDispatch()` went through the ShedLock proxy. One test's `lockAtLeastFor=PT1M` row caused later tests to skip the method body, producing false "zero dispatch calls" failures.
- **Fix:** Test helpers now call `AopTestUtils.getTargetObject(scheduler).scheduledDispatch()` for behavior checks while still asserting the scheduled method's cron and ShedLock annotations by reflection. Test reset also clears digest ShedLock rows to avoid cross-test residue.
- **Files modified:** `DigestDispatchSchedulerTest.java`, `DigestIdempotencyTest.java`, `DigestDispatchWithNoopChannelTest.java`, `DigestDispatchTestData.java`
- **Verification:** Focused scheduler/idempotency/reaper/noop suite passed; full `:backend:worker:check` passed.
- **Committed in:** `42112f4`

---

**Total deviations:** 1 auto-fixed (blocking test determinism).
**Impact:** Test-only stabilization. Production ShedLock behavior and scheduler annotations remain unchanged.

## Issues Encountered

- JetBrains SQL inspections still report unresolved table/column metadata for SQL strings in `DigestDispatchScheduler` and `DigestPendingReaperJob`; Gradle tests, Liquibase, Hibernate validation, and module checks pass against the real schema.
- The second dependency insight confirmed Jackson 2.x and `javax.*` artifacts already existed through Spring AI/Google GenAI/Liquibase paths; the Resend dependency did not introduce those findings.

## Verification

- `.\gradlew.bat :backend:core:test --tests "DigestPayloadShapeArchTest"` - PASS
- `.\gradlew.bat :backend:worker:test --tests "ResendBoundaryArchTest" --tests "*Digest*" --tests "*Notification*" --tests "EmailNotificationChannelTest" --tests "ThymeleafDigestRendererTest"` - PASS
- `.\gradlew.bat :backend:api:test --tests "NotificationPreferencesControllerTest"` - PASS
- `.\gradlew.bat :backend:core:check :backend:api:check :backend:worker:check` - PASS
- JetBrains `build_project` - PASS before final Spotless formatting; a later file-scoped retry timed out, so Gradle check is the final compile/source-of-truth validation.

## User Setup Required

External Resend setup is required. See `05C-03-USER-SETUP.md` for the API key, sender-domain verification, and runtime environment variables.

## Next Phase Readiness

Plan 04 can consume the notification preferences endpoints and the analytics endpoint from Plan 02. The backend daily digest path is ready for OpenAPI client regeneration and frontend settings/analytics wiring; the only manual blocker for real email sends is completing Resend setup.

---
*Phase: 05C-user-surface-analytics-daily-digest*
*Completed: 2026-05-13*
