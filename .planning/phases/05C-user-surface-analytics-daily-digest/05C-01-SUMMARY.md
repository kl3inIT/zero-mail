---
phase: 05C-user-surface-analytics-daily-digest
plan: 01
subsystem: database, backend-core, gmail-ingestion, notification
tags: [postgres, liquibase, jpa, gmail-api, spring-modulith, notification-preferences]

requires:
  - phase: 02A-mail-ingestion
    provides: Gmail Pub/Sub processing and mail_message_observed writer
  - phase: 01.5-inbox-zero-alignment-bundled-oauth-ux-polish-cleanup-sweep-r
    provides: bundled OAuth provisioning transaction
provides:
  - Liquibase schema foundation for analytics and daily digest
  - core.notification module with notification preferences and digest delivery persistence
  - Gmail observed-message sender_email capture from metadata-only From header
  - OAuth provisioning defaults for tenant time zone and email digest preference
affects: [05C-02-analytics-summary, 05C-03-daily-digest, 05C-04-web-surface]

tech-stack:
  added: []
  patterns:
    - Single UUID entity PK plus tenant/channel unique business key
    - Shared email canonicalization in core.shared.privacy
    - Metadata-only Gmail message fetch with metadataHeaders=["From"]

key-files:
  created:
    - backend/core/src/main/resources/db/changelog/changes/032-mail-message-observed-sender-email.yaml
    - backend/core/src/main/resources/db/changelog/changes/033-tenants-time-zone.yaml
    - backend/core/src/main/resources/db/changelog/changes/034-notification-preference.yaml
    - backend/core/src/main/resources/db/changelog/changes/035-digest-delivery.yaml
    - backend/core/src/main/resources/db/changelog/changes/036-analytics-supporting-indexes.yaml
    - backend/core/src/main/resources/db/changelog/changes/037-notification-preference-backfill.yaml
    - backend/core/src/main/java/com/zeromail/core/notification/usecases/NotificationPreferenceService.java
    - backend/core/src/main/java/com/zeromail/core/notification/usecases/DigestDeliveryService.java
    - backend/core/src/main/java/com/zeromail/core/shared/privacy/EmailAddressCanonicalizer.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java
    - backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java
    - backend/api/src/main/java/com/zeromail/api/controllers/account/AccountDeletionController.java

key-decisions:
  - "ChannelType.EMAIL persists as uppercase EMAIL so JPA @Enumerated(STRING), partial indexes, and backfill SQL match."
  - "Reusable sender canonicalization lives in shared.privacy to avoid a Gmail -> Triage dependency cycle."
  - "GmailDeliveryProcessingService, not PubSubIngestionService, is the sender_email insertion point."

patterns-established:
  - "New tenant-owned tables extend AbstractTenantOwnedEntity and use explicit service delete methods plus FK ON DELETE CASCADE."
  - "Gmail sender analytics data is future-only: pre-5C rows keep sender_email NULL and analytics filters NULL senders."

requirements-completed: [ANL-02, ANL-03, WEB-02]

duration: 41min
completed: 2026-05-13
---

# Phase 05C Plan 01 Summary

**Analytics and digest persistence foundation with notification defaults and metadata-only sender capture**

## Performance

- **Duration:** 41 min
- **Started:** 2026-05-13T21:49:00+07:00
- **Completed:** 2026-05-13T22:30:00+07:00
- **Tasks:** 3/3 complete
- **Files modified:** 36

## Accomplishments

- Added changesets 032-037 for `sender_email`, `tenants.time_zone`, `notification_preference`, `digest_delivery`, analytics indexes, and existing-tenant notification preference backfill.
- Created `core.notification` with uppercase `ChannelType.EMAIL`, digest delivery status, entities, repositories, and service-owned `@Transactional` write/delete operations.
- Patched the real Gmail observation writer to fetch metadata only, request the `From` header, canonicalize it through shared privacy code, and persist it as nullable `mail_message_observed.sender_email`.
- Extended first-login OAuth provisioning so tenant time zone and email digest defaults are created inside the existing bundled transaction.
- Extended account deletion to delete notification preferences and digest delivery rows before tenant deletion.

## Task Commits

1. **Task 1: Liquibase migration wave** - `bf7e738` (`feat`)
2. **Task 2: Entity/repository/service wiring + Gmail sender metadata** - `36322e3` (`feat`)
3. **Task 3: OAuth defaults + account deletion cascade + tenant time zone** - `74e8886` (`feat`)

**Plan metadata:** committed after summary creation.

## Files Created/Modified

- `backend/core/src/main/resources/db/changelog/changes/032-mail-message-observed-sender-email.yaml` - nullable `sender_email varchar(320)`.
- `backend/core/src/main/resources/db/changelog/changes/033-tenants-time-zone.yaml` - `tenants.time_zone` with `Asia/Ho_Chi_Minh` default.
- `backend/core/src/main/resources/db/changelog/changes/034-notification-preference.yaml` - single UUID PK plus unique `(tenant_id, channel)`.
- `backend/core/src/main/resources/db/changelog/changes/035-digest-delivery.yaml` - digest idempotency table with `external_ref` and `next_attempt_at`.
- `backend/core/src/main/resources/db/changelog/changes/036-analytics-supporting-indexes.yaml` - analytics window/top-sender/rule-hit indexes.
- `backend/core/src/main/resources/db/changelog/changes/037-notification-preference-backfill.yaml` - existing tenant default preference backfill.
- `backend/core/src/main/java/com/zeromail/core/notification/**` - new notification Modulith module.
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java` - Gmail metadata From extraction and sender persistence.
- `backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java` - default preference/time-zone wiring inside the bundled transaction.
- `backend/api/src/main/java/com/zeromail/api/controllers/account/AccountDeletionController.java` - notification cleanup in the account deletion cascade.

## Decisions Made

- `db.changelog-master.yaml` is the actual master changelog path in this repo; the plan text's `master.yaml` reference was interpreted as that file.
- `setTimeZoneIfAbsent` remains explicit even though the DB column has a default, because Hibernate inserts bind entity fields and should not rely on DB defaults for new JPA-managed tenant rows.
- `PubSubIngestionService` was not modified; `GmailDeliveryProcessingService.observeInboxMessages(...)` is the exact insertion point for sender metadata.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Avoided Gmail -> Triage module cycle**
- **Found during:** Task 2
- **Issue:** The only existing sender canonicalizer lived under `core.triage.usecases`; importing it from `core.gmail` would create a bidirectional Gmail/Triage module dependency.
- **Fix:** Added `EmailAddressCanonicalizer` in `core.shared.privacy`, made `SenderEmailCanonicalizer` delegate to it, and used the shared canonicalizer from Gmail.
- **Files modified:** `EmailAddressCanonicalizer.java`, `SenderEmailCanonicalizer.java`, `triage/package-info.java`
- **Verification:** Focused Gmail sender tests and `:backend:core:check` passed.
- **Committed in:** `36322e3`

**2. [Rule 2 - Missing Critical] Account deletion cascade host differs from planned filename**
- **Found during:** Task 3
- **Issue:** `AccountDeletionService` does not exist; the established cascade host is `AccountDeletionController`.
- **Fix:** Added `NotificationPreferenceService.deleteForTenant` and `DigestDeliveryService.deleteForTenant` calls in the controller before user/tenant deletion.
- **Files modified:** `AccountDeletionController.java`
- **Verification:** `:backend:api:compileJava` passed.
- **Committed in:** `74e8886`

---

**Total deviations:** 2 auto-fixed (2 missing critical).
**Impact on plan:** Both preserve the intended architecture and avoid module/cascade gaps. No scope expansion beyond required wiring.

## Issues Encountered

- The planned Gradle command included `-x checkstyleMain`, but this project has no `checkstyleMain` task. Re-ran the focused tests without that exclusion.
- JetBrains entity inspections reported unresolved table/column names for newly added Liquibase columns and also a pre-existing `triage_shadow_mode` column. Gradle Liquibase + Hibernate validation is green, so this is treated as stale IDE database metadata rather than a code defect.

## User Setup Required

None - no external service configuration required in this plan.

## Verification

- `./gradlew.bat :backend:core:test --tests DigestDeliveryUniqueConstraintTest --tests NotificationPreferenceBackfillTest` - passed.
- `./gradlew.bat :backend:core:test --tests NotificationPreferencePersistenceTest --tests DigestDeliveryUniqueConstraintTest --tests GmailDeliveryProcessingSenderEmailTest` - passed.
- `./gradlew.bat :backend:core:test --tests OAuthProvisioningDefaultsTest` - passed.
- `./gradlew.bat :backend:core:test --tests DigestDeliveryUniqueConstraintTest --tests NotificationPreferenceBackfillTest --tests NotificationPreferencePersistenceTest --tests GmailDeliveryProcessingSenderEmailTest --tests OAuthProvisioningDefaultsTest` - passed.
- `./gradlew.bat :backend:core:check` - passed.
- `./gradlew.bat :backend:api:compileJava` - passed.

## Next Phase Readiness

Plan 02 can build `core.analytics` and `/api/analytics/summary` against the committed schema. The top-sender query should filter `sender_email IS NOT NULL`, matching the partial index and the future-only data policy. Plan 03 can build digest claiming/settlement against `notification_preference` and `digest_delivery`, including `external_ref` and `next_attempt_at`.

---
*Phase: 05C-user-surface-analytics-daily-digest*
*Completed: 2026-05-13*
