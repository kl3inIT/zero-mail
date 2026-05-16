---
phase: 04-triage-convergence-hero
plan: 01
subsystem: backend
tags: [spring-modulith, jdbc-events, liquibase, gmail-ingestion, triage, tenant-context]

requires:
  - phase: 04-00
    provides: Spring Modulith JDBC starter dependency and Phase 4 contract tests
provides:
  - Privacy-safe MailMessageObserved integration event published from Gmail ingestion
  - Liquibase-owned Spring Modulith event_publication registry table
  - Empty core.triage Modulith module skeleton with allowed dependency fence
  - TenantContext.runWith helper for async tenant rebinds
affects: [phase-04, triage, gmail, worker, liquibase]

tech-stack:
  added: []
  patterns:
    - Publish Modulith application events only after idempotent new-row inserts
    - Mirror library-owned infrastructure DDL through Liquibase and Testcontainers
    - Empty Modulith package skeletons establish allowedDependencies before implementation

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/gmail/event/MailMessageObserved.java
    - backend/core/src/main/java/com/zeromail/core/gmail/event/package-info.java
    - backend/core/src/main/resources/db/changelog/changes/024-modulith-event-publication.yaml
    - backend/core/src/main/java/com/zeromail/core/triage/package-info.java
    - backend/core/src/main/java/com/zeromail/core/triage/application/package-info.java
    - backend/core/src/main/java/com/zeromail/core/triage/domain/package-info.java
    - backend/core/src/main/java/com/zeromail/core/triage/exception/package-info.java
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/package-info.java
    - backend/core/src/main/java/com/zeromail/core/triage/service/package-info.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java
    - backend/core/src/test/java/com/zeromail/core/gmail/event/MailMessageObservedContractTest.java
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
    - backend/core/src/test/java/com/zeromail/core/support/LiquibaseMigrationTest.java
    - backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java

key-decisions:
  - "Use the Spring Modulith JDBC v2 PostgreSQL schema from the pinned 2.0.7-SNAPSHOT jar: event_publication includes status, completion_attempts, and last_resubmission_date."
  - "The actual pinned schema-init property is spring.modulith.events.jdbc.schema-initialization.enabled; it remains unset in committed application YAML so Liquibase owns the table."
  - "MailMessageObservedContractTest now focuses on the Gmail event record only; future TriageOrchestratorService coverage remains in the dedicated triage and worker contract tests."

patterns-established:
  - "GmailDeliveryProcessingService publishes MailMessageObserved only when insertObservedIfAbsent returns 1."
  - "TenantContext.runWith(UUID, Runnable) is the canonical helper for async tenant rebinds."

requirements-completed: [TRG-01]

duration: 12min
completed: 2026-05-11
---

# Phase 04 Plan 01: Modulith Event Spine Summary

**MailMessageObserved event publication, Liquibase-managed Modulith JDBC registry, and the empty triage module boundary**

## Performance

- **Duration:** 12 min
- **Started:** 2026-05-11T10:40:02Z
- **Completed:** 2026-05-11T10:51:45Z
- **Tasks:** 3
- **Files modified:** 14

## Accomplishments

- Added `MailMessageObserved(UUID tenantId, String gmailMessageId, String gmailThreadId, Instant observedAt)` under `core.gmail.event`, with Javadoc locking the no-content privacy invariant.
- Wired `GmailDeliveryProcessingService` to inject `ApplicationEventPublisher` and publish only inside the `insertedCount == 1` path, so Pub/Sub duplicates absorbed by the existing idempotency layer emit no triage event.
- Added Liquibase changelog `024-modulith-event-publication.yaml` for Spring Modulith's JDBC v2 `event_publication` table and proved it applies in the existing Postgres Testcontainers migration test.
- Declared empty `core.triage` Modulith packages and `allowedDependencies = {"rules", "gmail", "llm", "billing", "tenant", "shared.persistence", "shared.lang"}`.
- Added `TenantContext.runWith(UUID, Runnable)` as the future async listener/scheduler tenant rebind helper.

## Task Commits

1. **Task 1: MailMessageObserved event record + publish site** - `f101427` (feat)
2. **Task 2: 024-modulith-event-publication.yaml** - `a0ebfdb` (feat)
3. **Task 3: core.triage skeleton + TenantContext.runWith** - `3f36061` (feat)

## Files Created/Modified

- `backend/core/src/main/java/com/zeromail/core/gmail/event/MailMessageObserved.java` - Privacy-safe integration event record for the observed-message spine.
- `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailDeliveryProcessingService.java` - Publishes the event only after a new observed-message row is inserted.
- `backend/core/src/main/resources/db/changelog/changes/024-modulith-event-publication.yaml` - Liquibase-owned Spring Modulith JDBC event registry table.
- `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` - Includes changelog `024` immediately after `023`.
- `backend/core/src/test/java/com/zeromail/core/support/LiquibaseMigrationTest.java` - Asserts `event_publication` exists after schema push.
- `backend/core/src/main/java/com/zeromail/core/triage/**/package-info.java` - Empty triage module and subpackage skeleton.
- `backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java` - Adds `runWith(UUID, Runnable)`.

## DDL Trace

Source: `spring-modulith-events-jdbc-2.0.7-SNAPSHOT.jar`, resource `org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql`. Dependency insight resolved `spring-modulith-events-jdbc:2.0.7-SNAPSHOT:20260424.100642-1`.

```sql
CREATE TABLE IF NOT EXISTS event_publication
(
  id                     UUID NOT NULL,
  listener_id            TEXT NOT NULL,
  event_type             TEXT NOT NULL,
  serialized_event       TEXT NOT NULL,
  publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
  completion_date        TIMESTAMP WITH TIME ZONE,
  status                 TEXT,
  completion_attempts    INT,
  last_resubmission_date TIMESTAMP WITH TIME ZONE,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx ON event_publication USING hash(serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx ON event_publication (completion_date);
```

## Decisions Made

- Used the default v2 Modulith JDBC schema because `JdbcConfigurationProperties.isUseLegacyStructure()` defaults to `false` in the pinned source.
- Left `spring.modulith.events.jdbc.schema-initialization.enabled` and `spring.modulith.events.republish-outstanding-events-on-restart` unset in committed configuration; retry/cleanup remains owned by later scheduled jobs.
- Kept `core.triage` as package-info-only in this plan, preserving the explicit plan boundary that production triage services land later.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Narrowed the Gmail event contract test to the current plan boundary**
- **Found during:** Task 1
- **Issue:** `MailMessageObservedContractTest` also required future `TriageOrchestratorService`, while Task 3 explicitly forbids adding production classes under `core.triage` in this plan.
- **Fix:** Removed the future orchestrator presence assertion from the Gmail event test; the dedicated triage and worker contract tests still preserve future orchestrator RED coverage.
- **Files modified:** `backend/core/src/test/java/com/zeromail/core/gmail/event/MailMessageObservedContractTest.java`
- **Verification:** `./gradlew.bat :backend:core:test --tests "*MailMessageObservedContractTest" --console=plain` passed.
- **Committed in:** `f101427`

### Workflow Deviations

**1. Captured canonical DDL from the pinned jar resource instead of booting a throwaway worker**
- **Found during:** Task 2
- **Reason:** The resolved dependency contains the exact PostgreSQL schema resource and source code proving the property key/default schema version. This avoided temporary application configuration and deployment-secret boot requirements while still using the pinned starter as source of truth.
- **Verification:** `./gradlew.bat :backend:core:dependencyInsight --dependency spring-modulith-events-jdbc --configuration runtimeClasspath --console=plain` resolved the pinned artifact; `./gradlew.bat :backend:core:test --tests "*LiquibaseMigrationTest" --console=plain` applied the Liquibase changelog against Postgres and asserted `event_publication` exists.

---

**Total deviations:** 1 auto-fixed blocking issue; 1 workflow substitution.
**Impact on plan:** The shipped code still satisfies the locked event, schema, and module-boundary outcomes. No product scope was added.

## Issues Encountered

- JetBrains reports `TenantContext.runWith(UUID, Runnable)` as unused. This is expected until the later `@ApplicationModuleListener` and worker scheduler plans consume it.

## Verification

- `./gradlew.bat :backend:core:compileJava --console=plain` - passed.
- `./gradlew.bat :backend:core:test --tests "*MailMessageObservedContractTest" --console=plain` - passed.
- `./gradlew.bat :backend:core:test --tests "*LiquibaseMigrationTest" --console=plain` - passed.
- `./gradlew.bat :backend:core:test --tests "*ApplicationModulesTest" --console=plain` - passed.
- `./gradlew.bat :backend:core:test --tests "*ApplicationModulesTest" --tests "*MailMessageObservedContractTest" --tests "*LiquibaseMigrationTest" --console=plain` - passed.
- Static checks confirmed the publish call is inside the `insertedCount == 1` block, changelog `024` is included after `023`, and no committed `application*.yml` enables Modulith JDBC schema auto-init or restart republish.

## Known Stubs

None - no stubs were added. Stub-pattern scan only matched pre-existing null checks and structured log placeholders in `GmailDeliveryProcessingService`.

## Threat Flags

None - the new event payload and registry table are planned threat-model surfaces for this plan; no unplanned endpoint, auth path, file access, or trust-boundary surface was introduced.

## Auth Gates

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Ready for 04-02. The event spine and `core.triage` boundary exist, and the next plan can add triage persistence/domain tables and classes against a green targeted verification set.

## Self-Check: PASSED

- Summary file exists: `.planning/phases/04-triage-convergence-hero/04-01-SUMMARY.md`
- Key created files exist: `MailMessageObserved.java`, `024-modulith-event-publication.yaml`, `core.triage/package-info.java`
- Task commits found: `f101427`, `a0ebfdb`, `3f36061`
- No accidental tracked file deletions were present in task commits.

---
*Phase: 04-triage-convergence-hero*
*Completed: 2026-05-11*
