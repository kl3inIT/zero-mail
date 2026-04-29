---
phase: 02A-mail-ingestion
plan: "01"
subsystem: database
tags: [gmail, pubsub, postgresql, liquibase, jpa, hibernate, google-auth]
requires:
  - phase: 02A-00
    provides: Wave 0 RED tests for ingestion schema, enum, repositories, and tenant-isolation contracts
  - phase: 01.2.1
    provides: AbstractTenantOwnedEntity, IdentifiedEnum, PostgresContainerTest, and tenant-bound JPA conventions
provides:
  - Liquibase changesets 010-013 for Gmail ingestion state, Pub/Sub delivery, observed mail, and triage pause
  - Gmail ingestion health enum and persistence entities/repositories for downstream API and worker plans
  - Google Gmail API and google-auth dependency aliases for later Gmail client and Pub/Sub OIDC work
affects: [02A-mail-ingestion, gmail-ingestion, backend-core, backend-api, worker]
tech-stack:
  added: [google-api-services-gmail, google-auth-library-oauth2-http, org.eclipse:yasson]
  patterns:
    - Idempotent native inserts with ON CONFLICT DO NOTHING
    - Atomic UPDATE ... RETURNING claim query for Pub/Sub delivery rows
    - Composite tenant-owned entity using @IdClass plus explicit @TenantId
key-files:
  created:
    - backend/core/src/main/resources/db/changelog/changes/010-gmail-ingestion-state.yaml
    - backend/core/src/main/resources/db/changelog/changes/011-pubsub-delivery-table.yaml
    - backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml
    - backend/core/src/main/resources/db/changelog/changes/013-tenants-triage-paused.yaml
    - backend/core/src/main/java/com/zeromail/core/gmail/model/GmailIngestionHealth.java
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntity.java
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedId.java
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java
    - backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java
    - gradle/libs.versions.toml
    - backend/core/build.gradle.kts
    - backend/api/build.gradle.kts
    - backend/core/src/test/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntityTest.java
    - backend/core/src/test/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntityTest.java
key-decisions:
  - "Use Yasson JSON-B at runtime for Hibernate JSONB mapping under Spring Boot 4/Jackson 3 instead of adding Jackson 2."
  - "Keep MailMessageObservedId as a top-level record to satisfy the committed Wave 0 test contract while still using Hibernate @IdClass."
  - "Explicitly tenant-scope the one-argument PubSubDeliveryRepository claim path because native SQL does not inherit Hibernate @TenantId filtering."
patterns-established:
  - "Native queue claims that run under a bound TenantContext must include an explicit tenant_id predicate."
  - "Hibernate JSONB mappings in backend/core need a JSON-B/Jackson runtime mapper on the module runtime classpath."
requirements-completed: [MAIL-01, MAIL-02, MAIL-03, MAIL-04, MAIL-06]
duration: 11min
completed: 2026-04-29
---

# Phase 02A Plan 01: Mail Ingestion Data Layer Summary

**Gmail ingestion schema and JPA persistence foundation with idempotent Pub/Sub delivery and tenant-filtered observed-mail audit rows**

## Performance

- **Duration:** 11 min
- **Started:** 2026-04-29T05:27:34Z
- **Completed:** 2026-04-29T05:39:01Z
- **Tasks:** 2 completed
- **Files modified:** 18

## Accomplishments

- Added Liquibase changesets 010-013 for Gmail connection ingestion state, `pubsub_delivery`, `mail_message_observed`, and `tenants.triage_paused`.
- Added `GmailIngestionHealth`, ingestion fields on `GmailConnectionEntity`, pause state on `TenantEntity`, and the repository/entity pair for Pub/Sub deliveries.
- Added `mail_message_observed` as a composite-key, tenant-filtered JPA entity with TEXT[] label storage and idempotent native inserts.
- Wired Google Gmail API and google-auth dependencies ahead of the API/worker implementation waves.

## Task Commits

1. **Task 1: Liquibase changesets 010-013** - `ecc28c1` (feat)
2. **Task 2: GmailIngestionHealth enum + JPA entities/repositories + entity field extensions** - `4b7e1c6` (feat)

## Files Created/Modified

- `backend/core/src/main/resources/db/changelog/changes/010-gmail-ingestion-state.yaml` - Adds six ingestion-state columns to `gmail_connections`.
- `backend/core/src/main/resources/db/changelog/changes/011-pubsub-delivery-table.yaml` - Adds `pubsub_delivery` with dedup unique constraint and claim index.
- `backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml` - Adds privacy-floor observed-mail audit table with composite PK and BRIN index.
- `backend/core/src/main/resources/db/changelog/changes/013-tenants-triage-paused.yaml` - Adds `tenants.triage_paused`.
- `backend/core/src/main/java/com/zeromail/core/gmail/model/GmailIngestionHealth.java` - IdentifiedEnum for HEALTHY, WATCH_UNHEALTHY, and HISTORY_LOST.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntity.java` - Tenant-owned JSONB delivery entity.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java` - Atomic native claim and idempotent insert helpers.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java` - Composite-key observed mail entity with explicit `@TenantId`.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedId.java` - IdClass record for observed mail rows.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java` - Idempotent observed-message insert helper.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java` - Adds ingestion state fields.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java` - Adds parameterized case-insensitive Gmail email lookup.
- `backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java` - Adds triage pause flag.
- `gradle/libs.versions.toml`, `backend/core/build.gradle.kts`, `backend/api/build.gradle.kts` - Wire Gmail API/google-auth dependencies and OpenAPI dummy Pub/Sub args.
- `backend/core/src/test/java/com/zeromail/core/gmail/persistence/*.java` - Adjust test helpers for Spring JDBC PgArray and Instant handling.

## Decisions Made

- Yasson was added as the JSON-B runtime mapper for Hibernate JSONB mapping. This avoids introducing Jackson 2 into a Spring Boot 4 codebase that otherwise uses Jackson 3.
- `MailMessageObservedId` is a top-level record because the existing Wave 0 test contract instantiates it directly from the package.
- The one-argument `claimPendingBatch(int limit)` now requires a bound `TenantContext` and explicitly filters `tenant_id`; the native two-argument query remains available for a future global worker path if needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added JSON-B runtime mapper for Hibernate JSONB**
- **Found during:** Task 2 targeted persistence tests
- **Issue:** `@JdbcTypeCode(SqlTypes.JSON)` failed at runtime because Hibernate could not find a JSON `FormatMapper`.
- **Fix:** Added `runtimeOnly("org.eclipse:yasson")` to `backend/core/build.gradle.kts`.
- **Verification:** Targeted core persistence tests passed.
- **Committed in:** `4b7e1c6`

**2. [Rule 1 - Bug] Fixed Wave 0 JDBC helper conversions**
- **Found during:** Task 2 targeted persistence tests
- **Issue:** Spring JDBC did not auto-convert PostgreSQL `PgArray` to `String[]`, and the PostgreSQL driver could not infer a SQL type for `Instant` in a raw test insert.
- **Fix:** Switched the TEXT[] assertion to an explicit row mapper and converted test `Instant` values with `Timestamp.from(...)`.
- **Verification:** `MailMessageObservedEntityTest` and `PubSubDeliveryEntityTest` passed.
- **Committed in:** `4b7e1c6`

**3. [Rule 2 - Missing Critical] Tenant-scoped native claim path**
- **Found during:** Task 2 targeted persistence tests
- **Issue:** The native `UPDATE ... RETURNING` claim query bypassed Hibernate `@TenantId` filtering and could claim pending rows from another tenant when called under a bound tenant context.
- **Fix:** Added `claimPendingBatchForTenant(...)` with an explicit `tenant_id` predicate and routed the one-argument default method through `TenantContext.currentOrThrow()`.
- **Verification:** Claim/reclaim tests passed and attempts/status updates applied only to the bound tenant's rows.
- **Committed in:** `4b7e1c6`

**4. [Rule 2 - Missing Critical] Added parameterized Gmail email lookup**
- **Found during:** Threat-model mitigation review for T-06
- **Issue:** The plan threat register required a parameterized `findByGoogleEmailIgnoreCase(String)` lookup for Pub/Sub email-address routing.
- **Fix:** Added the derived repository method to `GmailConnectionRepository`.
- **Verification:** `:backend:core:compileJava` passed.
- **Committed in:** `4b7e1c6`

---

**Total deviations:** 4 auto-fixed (1 bug, 2 missing critical, 1 blocking)
**Impact on plan:** All fixes were required for correctness, test executability, or explicit threat-model mitigation. No unrelated behavior was added.

## Issues Encountered

- JetBrains full project build still reports the expected Wave 0 RED API test symbols for future `PubSubOidcAuthFilter` and `GmailPubSubController`. A narrowed IDE build of this plan's modified Java files passed with no problems.

## Verification

- `.\gradlew.bat :backend:core:test --tests "*GmailIngestionHealthTest*" --tests "*PubSubDeliveryEntityTest*" --tests "*MailMessageObservedEntityTest*"` - PASS.
- `.\gradlew.bat :backend:core:compileJava :backend:api:compileJava` - PASS.
- `rg -n 'subject|from_address|body|snippet|sender|recipient' backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml` - PASS, no matches.
- JetBrains targeted build for modified Java files - PASS.

## Known Stubs

None.

## User Setup Required

None - no external service configuration required for this data-layer plan.

## Next Phase Readiness

Ready for 02A-02 worker/API implementation. The schema, enum, repositories, and dependency wiring now exist for Gmail client construction, Pub/Sub OIDC verification, ack-fast delivery inserts, worker claims, and observed-message writes.

---
*Phase: 02A-mail-ingestion*
*Completed: 2026-04-29*

## Self-Check: PASSED

- Summary and all key created files exist on disk.
- Task commits found in git history: `ecc28c1`, `4b7e1c6`.
- Stub-pattern scan found no blocking stubs; matches were only native query string assignments and null-handling in test/helper code.
