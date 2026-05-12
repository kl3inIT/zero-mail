---
phase: 04-triage-convergence-hero
plan: 02
subsystem: database
tags: [triage, audit, postgres, liquibase, spring-data-jpa, hibernate, tenant-isolation]

requires:
  - phase: 04-triage-convergence-hero
    provides: "04-00 Wave 0 triage tests and 04-01 Modulith message-observed event spine"
provides:
  - "Triage audit schema with lease/reclaim columns and NULLS NOT DISTINCT idempotency"
  - "TriageActionResult domain contract, manual validator, canonical hash, and sender canonicalizer"
  - "Triage audit entity/repository/writer seam plus sender opt-in and protected sender observation persistence"
  - "CallSite triage additions, tenant shadow-mode persistence, and green persistence/ArchUnit guards"
affects: [phase-04, phase-04-plan-04, phase-04-plan-05, phase-04-plan-06, phase-04-plan-07, phase-05]

tech-stack:
  added: []
  patterns:
    - "Native INSERT ... ON CONFLICT ... RETURNING exposed through Spring Data native @Query returning Optional<UUID>"
    - "TriageAuditWriter validates and hashes action args before any native audit insert"
    - "Inherited AbstractEntity.id can be remapped to audit_id with @AttributeOverride for tenant-owned audit rows"

key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditRepository.java
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditWriter.java
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TenantSenderOptInEntity.java
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TenantProtectedSenderObservationEntity.java
  modified:
    - backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml
    - backend/core/src/main/java/com/zeromail/core/billing/domain/CallSite.java
    - backend/core/src/main/java/com/zeromail/core/config/ZeroMailCoreProperties.java
    - backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java
    - backend/core/src/test/java/com/zeromail/core/triage/TriageAuditPersistenceContractTest.java

key-decisions:
  - "Map TriageAuditEntity's inherited id field to audit_id with @AttributeOverride instead of redeclaring a second @Id."
  - "Use Spring Data native @Query without @Modifying for INSERT ... RETURNING methods so Optional<UUID> result mapping works; reserve @Modifying for update row-count methods."
  - "Keep triage-deterministic cost at zero credits while still exposing a configurable zero-mail.billing.cost.triage-deterministic property."

patterns-established:
  - "Audit row creation flows through TriageAuditWriter: validate action JSON, canonicalize pre-write intent, compute raw SHA-256 args_hash, then call native insert."
  - "Stale PENDING reclaim uses attempt_count, last_attempt_at, and lease_owner with a 2-minute retry lease; the later reaper uses a longer abandoned threshold."
  - "Protected sender GET data comes from tenant_protected_sender_observation, not from scanning triage_audit."

requirements-completed: [TRG-04, TRG-05, TRG-07, TRG-08]

duration: 29 min
completed: 2026-05-11
---

# Phase 04 Plan 02: Triage Persistence and Domain Summary

**Triage audit persistence with validated action JSON, canonical idempotency hashes, sender safety tables, and tenant shadow-mode controls.**

## Performance

- **Duration:** 29 min
- **Started:** 2026-05-11T10:55:00Z
- **Completed:** 2026-05-11T11:23:37Z
- **Tasks:** 3/3
- **Files modified:** 34

## Accomplishments

- Added Liquibase 025-028 for `triage_audit`, tenant shadow mode, sender opt-in, and protected sender observations.
- Added the triage domain contract: sealed `TriageActionResult`, strict manual JSON validator, canonical SHA-256 hasher, sender canonicalizer, `TriageDecision`, and privacy-safe exceptions.
- Added `TriageAuditEntity`, full transition-surface `TriageAuditRepository`, and `TriageAuditWriter` so native inserts cannot bypass validation.
- Extended `CallSite`, `ZeroMailCoreProperties`, `TenantEntity`, and `TenantService`; converted Wave 0 audit persistence tests into real Testcontainers coverage.

## Task Commits

1. **Task 1: Liquibase audit and sender safety schema** - `689bb7c` (`feat`)
2. **Task 2: Triage action domain contracts** - `ef6a4c2` (`feat`)
3. **Task 3: Audit persistence surface and guards** - `0a94526` (`feat`)
4. **Formatting closeout** - `bc250e7` (`style`)

## Files Created/Modified

- `backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml` - `triage_audit` with `args_hash`, JSONB fields, decisions, lease columns, and NULLS NOT DISTINCT idempotency.
- `backend/core/src/main/java/com/zeromail/core/triage/domain/TriageActionResult.java` - sealed audit action result contract.
- `backend/core/src/main/java/com/zeromail/core/triage/domain/TriageActionResultJsonValidator.java` - manual discriminator validator and serializer.
- `backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditRepository.java` - insert, reclaim, terminal transition, and bounded stuck-PENDING read surface.
- `backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditWriter.java` - sanctioned validation/canonicalization seam before native inserts.
- `backend/core/src/main/java/com/zeromail/core/triage/persistence/TenantSenderOptInEntity.java` - sender opt-in persistence.
- `backend/core/src/main/java/com/zeromail/core/triage/persistence/TenantProtectedSenderObservationEntity.java` - protected sender observation persistence.
- `backend/core/src/main/java/com/zeromail/core/billing/domain/CallSite.java` - added `TRIAGE_PLATFORM_LLM` and `TRIAGE_DETERMINISTIC`.
- `backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java` - added `triageShadowMode`.
- `backend/core/src/test/java/com/zeromail/core/triage/TriageAuditPersistenceContractTest.java` - real persistence, tenant scoping, JSON validation, lease reclaim, transition, and sender repository tests.

## Decisions Made

- Used `@AttributeOverride(name = "id", column = @Column(name = "audit_id"))` for `TriageAuditEntity` because `AbstractTenantOwnedEntity` already owns the `@Id` mapping.
- Used native `@Query` without `@Modifying` for INSERT-returning repository methods; update transitions remain `@Modifying` and return affected row counts.
- Added the triage validator/canonicalizer/canonical sender normalizer as Spring components so `TriageAuditWriter` and later services can inject the same normalizers.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Used native @Query, not @Modifying, for INSERT ... RETURNING**
- **Found during:** Task 3
- **Issue:** Spring Data JPA documents `@Modifying` for update/delete row-count methods; using it for an INSERT that returns `Optional<UUID>` would block result mapping.
- **Fix:** `insertAuditPendingIfAbsent` and `insertAuditTerminalIfAbsent` use native `@Query` returning `Optional<UUID>`; all update transitions use `@Modifying`.
- **Files modified:** `TriageAuditRepository.java`
- **Verification:** `TriageAuditPersistenceContractTest` inserts pending and terminal rows and verifies duplicate conflicts return empty.
- **Committed in:** `0a94526`

**2. [Rule 3 - Blocking] Mapped audit_id through inherited id instead of redeclaring @Id**
- **Found during:** Task 3
- **Issue:** The plan text asked for `TriageAuditEntity extends AbstractTenantOwnedEntity` and also a local `@Id auditId`; the base class already declares the JPA id.
- **Fix:** Used JPA `@AttributeOverride` to map inherited `id` to `audit_id`, with `getAuditId()` as the domain-revealing accessor.
- **Files modified:** `TriageAuditEntity.java`
- **Verification:** Hibernate validation and Testcontainers round-trip in `TriageAuditPersistenceContractTest` pass.
- **Committed in:** `0a94526`

**3. [Rule 1 - Bug] Aligned new version columns with inherited Integer @Version**
- **Found during:** Task 3
- **Issue:** Task 1 changelogs used `bigint` for `version`, while `AbstractAuditableEntity.version` is `Integer`; existing tenant-owned tables use `int`.
- **Fix:** Changed `version` columns in changelogs 025, 027, and 028 to `int`.
- **Files modified:** `025-triage-audit.yaml`, `027-tenant-sender-opt-in.yaml`, `028-tenant-protected-sender-observation.yaml`
- **Verification:** `:backend:core:test --tests "*TriageAuditPersistenceContractTest"` boots with `spring.jpa.hibernate.ddl-auto=validate`.
- **Committed in:** `0a94526`

**Total deviations:** 3 auto-fixed (1 bug, 2 blocking)
**Impact on plan:** All changes preserve the planned behavior and prevent mapping/runtime failures; no feature scope was added.

## Known Stubs

None.

## Threat Flags

None - the new schema and native-query trust boundaries were already covered by the plan threat model.

## Issues Encountered

- A first persistence-test assertion matched an exact JSON byte shape. It was adjusted to assert semantic content because Jackson serialization formatting is not the contract.
- `MultiTenantLeakIntegrationTest` lives in `backend/api`, not `backend/core`; the core selector was still run with the plan filters, and the actual leak regression was verified through `:backend:api:test`.

## Verification

- `.\gradlew.bat :backend:core:compileJava` - PASS
- Task 3 grep acceptance checks - PASS
- `.\gradlew.bat :backend:core:test --tests "*CallSiteEnumMembershipArchTest" --tests "*TriageAuditRepositoryBoundaryArchTest" --tests "*TriageActionResultJsonValidatorContractTest" --tests "*TriageAuditPersistenceContractTest" --tests "*MultiTenantLeakIntegrationTest"` - PASS for matching core tests
- `.\gradlew.bat :backend:api:test --tests "*MultiTenantLeakIntegrationTest"` - PASS
- JetBrains file problem checks on key new/modified Java files - PASS
- `git diff --check` - PASS
- Formatting-only closeout commit `bc250e7` - PASS, no behavior changes

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

04-03 can build semantic intent evaluation on top of the expanded `CallSite` membership. 04-04 through 04-07 can rely on `TriageAuditWriter`, sender opt-in/protected-observation repositories, and the lease-aware audit repository transition surface.

## Self-Check: PASSED

- Verified key created files exist: SUMMARY, `TriageAuditEntity`, `TriageAuditRepository`, `TriageAuditWriter`, and `025-triage-audit.yaml`.
- Verified task and closeout commits exist in git history: `689bb7c`, `ef6a4c2`, `0a94526`, `bc250e7`.

---
*Phase: 04-triage-convergence-hero*
*Completed: 2026-05-11*
