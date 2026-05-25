---
phase: 08-bulk-unsubscribe-campaign
plan: 03
subsystem: backend/core
tags: [cleanup, modulith, persistence, jpa, triage-audit, H-3]
dependency_graph:
  requires:
    - 08-02 (Liquibase changelogs 041-046)
    - core.shared.persistence.AbstractTenantOwnedEntity + AbstractEntity (Phase 1.2.1)
    - core.shared.lang.OrderedEnum + IdentifiedEnum
    - core.shared.exception.BusinessException + ErrorCodes
    - core.triage.persistence.TriageAuditEntity + TriageAuditRepository + TriageAuditWriter
  provides:
    - core.cleanup Spring Modulith module declared (D-17 allowedDependencies locked)
    - 4 cleanup enums (UnsubscribeMethod, UnsubscribeAttemptState, CampaignStatus, SuppressionReason)
    - UnsubscribeCampaignPolicy constants for caps + undo window + throttle (D-20 / D-23)
    - 4 JPA entities + 4 repositories + SuppressionReasonAttributeConverter
    - 5 cleanup business exceptions (extend BusinessException) + 6 ErrorCodes entries
    - CleanupAuditSource enum (core.triage.domain) + TriageAuditEntity.source field
    - TriageAuditWriter.recordCleanupArchive(...) + TriageAuditRepository.findCleanupArchiveRowsForUndo(...)
  affects:
    - Wave 3 (Plan 04 — controllers + DTOs + ProcessingJobWorker) consumes all entities + exceptions
    - Wave 4 (Plan 05 — services) consumes CampaignStatus/UnsubscribeAttemptState transitions
    - Wave 5 (Plan 06 — worker handler) calls TriageAuditWriter.recordCleanupArchive
    - Wave 6 (Plan 07 — retry/undo) calls TriageAuditRepository.findCleanupArchiveRowsForUndo
tech-stack:
  added: []
  patterns:
    - "Enum constants implements OrderedEnum/IdentifiedEnum with fail-loud fromId(String) per CONVENTIONS §4"
    - "JPA entity classes (NOT records) extending AbstractTenantOwnedEntity or AbstractEntity per Hibernate proxy requirement"
    - "@Converter(autoApply = true) for case-mismatched enum persistence (D-C3 trigger)"
    - "BusinessException subclasses with errorCode/title/detail/params for the GlobalExceptionHandler pipeline"
    - "Native SQL INSERT via @Query(nativeQuery=true) for idempotent audit-row writes (ON CONFLICT DO NOTHING + RETURNING)"
key-files:
  created:
    - backend/core/src/main/java/com/zeromail/core/cleanup/package-info.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/domain/UnsubscribeMethod.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/domain/UnsubscribeAttemptState.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/domain/CampaignStatus.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/domain/SuppressionReason.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/domain/UnsubscribeCampaignPolicy.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/persistence/UnsubscribeCampaignEntity.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/persistence/UnsubscribeCampaignRepository.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/persistence/UnsubscribeAttemptEntity.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/persistence/UnsubscribeAttemptRepository.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/persistence/SenderSuppressionEntity.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/persistence/SenderSuppressionRepository.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/persistence/SuppressionReasonAttributeConverter.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/persistence/ProcessingJobEntity.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/persistence/ProcessingJobRepository.java
    - backend/core/src/main/java/com/zeromail/core/triage/domain/CleanupAuditSource.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/exception/CampaignCapExceededException.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/exception/UndoWindowExpiredException.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/exception/CampaignNotFoundException.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/exception/CampaignRetryConflictException.java
    - backend/core/src/main/java/com/zeromail/core/cleanup/exception/SuppressedSenderException.java
  modified:
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditRepository.java
    - backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditWriter.java
    - backend/core/src/main/java/com/zeromail/core/shared/error/ErrorCodes.java
    - backend/core/src/main/resources/db/changelog/changes/042-processing-job.yaml
    - backend/core/src/main/resources/db/changelog/changes/043-sender-suppression.yaml
    - backend/core/src/main/resources/db/changelog/changes/044-unsubscribe-campaign.yaml
decisions:
  - "Used BusinessException (not raw RuntimeException) for cleanup exception base — matches existing core.triage.exception convention and enables single GlobalExceptionHandler dispatch. Plan §3 phrasing 'extends RuntimeException' was honored transitively: BusinessException itself extends RuntimeException."
  - "SuppressionReasonAttributeConverter set @Converter(autoApply = true) — explicit D-C3 trigger documented on IdentifiedEnum. Enum constants stay uppercase (MANUAL/REPLIED/AUTO) while DB column stays lowercase ('manual'/'replied'/'auto') per changelog 043 CHECK."
  - "UnsubscribeAttemptEntity extends AbstractEntity (not AbstractTenantOwnedEntity) — changelog 045 has no tenant_id, no created_at, no updated_at columns. Tenancy is enforced indirectly via the campaign_id FK cascade."
  - "ProcessingJobEntity status persisted as String (not enum field) — payload-agnostic dispatch layer treats it as a string bucket; native SQL transitions (SKIP LOCKED pickup, heartbeat reaper) operate on the column directly without round-tripping through a Java type."
  - "TriageAuditWriter.recordCleanupArchive args_hash derived from SHA-256 of (campaignAttemptId, labelId, gmailMessageId) — deterministic key for the unique-constraint (tenant_id, gmail_message_id, rule_id, action_type, args_hash) so worker retries are idempotent at the DB layer (ON CONFLICT DO NOTHING)."
  - "CleanupAuditSource placed in core.triage.domain (not core.cleanup) — preserves Spring Modulith direction cleanup → triage per D-17. Putting it in core.cleanup would force triage → cleanup, inverting the module dependency."
metrics:
  duration: 2h 0m
  tasks_completed: 4
  files_created: 21
  files_modified: 7
  completed_date: 2026-05-20
---

# Phase 8 Plan 03: Cleanup Domain + Persistence Layer Summary

Wave 2 ships the `core.cleanup` Spring Modulith module skeleton (package-info + 6 domain types) plus the full JPA persistence layer (4 entities + 4 repositories + 1 enum converter) and the H-3 Path A extension of `core.triage` (source-discriminated audit rows + dedicated cleanup-archive writer/reader). Wave 3+ services and controllers consume every type shipped here without needing further entity work.

## Decisions Made

1. **`BusinessException` base** — chose existing convention over the plan's literal "extends RuntimeException" phrasing. `BusinessException` IS a `RuntimeException` subclass; the single uniform base lets the HTTP layer's `GlobalExceptionHandler` dispatch by `errorClass()` without case-splitting on cleanup-specific types.
2. **`@Converter(autoApply = true)` for SuppressionReason** — enum value mismatch with DB column (MANUAL ↔ 'manual') is the documented D-C3 trigger. Auto-apply scope is narrow (one entity field today) so the risk of an unrelated future entity accidentally picking it up is minimal.
3. **`UnsubscribeAttemptEntity` extends `AbstractEntity`** — not `AbstractTenantOwnedEntity`. Changelog 045 deliberately omits `tenant_id` because cascade-delete via the campaign FK is sufficient and the table is hot enough (per-sender per-campaign) that an extra column would waste storage.
4. **`ProcessingJobEntity.status` as String** — kept opaque to the entity so native SQL pickup/reaper queries operate on the column without enum round-trip. Constants `STATUS_QUEUED`/`STATUS_RUNNING`/`STATUS_COMPLETED`/`STATUS_FAILED` exposed for the Wave 3 worker.
5. **SHA-256 args_hash for cleanup audit rows** — derived deterministically from `(campaignAttemptId, labelId, gmailMessageId)` so the existing unique key `(tenant_id, gmail_message_id, rule_id, action_type, args_hash)` enforces idempotency on worker retry without a separate "did this already run" lookup.
6. **`CleanupAuditSource` in `core.triage.domain`** — putting it in `core.cleanup` would invert Spring Modulith dependency direction (`triage` would have to depend on `cleanup`). Placing it on the triage side keeps the boundary clean.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Missing `version` and `updated_at` columns in Wave 1 changelogs**

- **Found during:** Task 2 — JPA metamodel boot validation failed with `Schema validation: missing column [version] in table [processing_job]`. All three entities extending `AbstractTenantOwnedEntity` (Campaign / Suppression / Job) need the `created_at` + `updated_at` + `version` columns inherited from `AbstractAuditableEntity`.
- **Issue:** Wave 1 changelogs 042 / 043 / 044 declared `created_at` only (no `updated_at`, no `version`). Changelog 042 had both `created_at` and `updated_at` but missed `version`.
- **Fix:** Edited Wave 1 changelogs in place (acceptable — never deployed to a real DB, single Wave 1 commit, no checksum-history concern). Added `version int default 0 not null` to all three; added `updated_at timestamptz default now() not null` to 043 + 044.
- **Files modified:** `backend/core/src/main/resources/db/changelog/changes/042-processing-job.yaml`, `.../043-sender-suppression.yaml`, `.../044-unsubscribe-campaign.yaml`.
- **Commit:** `4abb6480` (same commit as Task 2 — fix is inseparable from the entity layer that requires it).
- **Verified:** `TriageAuditPersistenceContractTest` still GREEN with cleanup entities on classpath, confirming Liquibase + Hibernate metamodel boot is clean.

### Documented Stub Bugs (Deferred)

**1. Wave 0 `CleanupModuleVerificationTest` structural scan-root bug**

The Wave 0 stub calls `ApplicationModules.of(ZeroMailCoreTestApplication.class)`. `ZeroMailCoreTestApplication` lives in `com.zeromail.core.support`, which contains zero domain modules — Modulith throws `IllegalArgumentException: No classes found in packages [com.zeromail.core.support]!` before the module name lookup runs. This is independent of whether `core.cleanup` is declared.

- Tried `@Modulithic(additionalPackages = "com.zeromail.core")` on the test application — fixed the scan but uncovered a separate Modulith / ArchUnit `failOnEmptyShould=true` interaction failing the cycle-detection rule.
- **Verdict:** the test stub's chosen entrypoint is structurally incompatible with how the `backend/core` test classpath is laid out. The module declaration itself is provably correct — `ZeroMailApiApplicationModulesTest` (the canonical Modulith verify gate) **passes GREEN** with the new `core.cleanup` module on the classpath. Acceptance via the API-scope test is sufficient per `01-02-SUMMARY.md` BLOCKER-2 (Application class kept out of `backend/core` to avoid reversed module dep).
- **Reconciliation:** later wave should either (a) move `ZeroMailCoreTestApplication` to `com.zeromail.core` and add `@Modulithic`, or (b) delete the duplicate Wave 0 stub since `ZeroMailApiApplicationModulesTest` already covers it.

**2. Wave 0 `TriageAuditWriterCleanupArchiveTest.recordCleanupArchive_doesNotInterfereWithSourceTriageRows` column-mismatch**

Test does a raw JDBC `INSERT INTO triage_audit(..., subject_excerpt, ..., matcher_evidence, ...)`. The actual `triage_audit` schema uses `sanitized_subject` and `reason` (not `subject_excerpt` / `matcher_evidence`). Insert fails with `BadSqlGrammarException`.

- **Verdict:** Wave 0 stub authored against an imagined schema, not the real one. The actual cleanup-archive write path is correct: the other two sub-tests `recordCleanupArchive_persistsRowWithSourceCleanupCampaign` + `recordCleanupArchive_logsEventWithDomainOnly` **both pass GREEN**, proving:
  - `source='CLEANUP_CAMPAIGN'` written correctly
  - `action_type='ARCHIVE'`, `external_ref=attemptId.toString()` populated
  - `gmail_change_token` contains the label id
  - `sanitized_sender_email` matches the canonicalized email
  - Log line `event=triage_audit_cleanup_archive_recorded senderDomain=example.com` emitted with no full-email leak
- **Reconciliation:** Plan 06 (Wave 5) reconciles this stub when the writer is wired into `UnsubscribeCampaignHandler`. Per Plan 08-03 author note, this column-mismatch is explicitly deferred.

## Auth Gates

None — every artifact in this plan is core-layer code with no external dependency.

## Self-Check: PASSED

All 21 created files present; all 7 modified files staged in the 3 commits below.

## Commits

- `f320df7d` — `feat(phase-08-wave-2): declare core.cleanup Spring Modulith module + 4 enums + policy constants`
- `4abb6480` — `feat(phase-08-wave-2): cleanup persistence layer (4 entities + 4 repositories + converter)`
- `90ce82f5` — `feat(phase-08-wave-2): cleanup exceptions + H-3 triage audit source extension`

## Verification Results

| Check                                                                        | Result                                                                                          |
| ---------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `./gradlew :backend:core:compileJava :backend:core:compileTestJava`          | SUCCESS                                                                                         |
| `./gradlew :backend:api:test --tests "*ZeroMailApiApplicationModulesTest*"`  | PASSED — Spring Modulith verify passes with new `core.cleanup` module declared                  |
| `./gradlew :backend:core:test --tests "*TriageAuditPersistenceContractTest*"` | PASSED — no regression to triage; JPA metamodel boots clean with cleanup entities on classpath |
| `./gradlew :backend:core:test --tests "*TriageAuditWriterCleanupArchiveTest*"` | 2/3 sub-tests GREEN; 3rd sub-test fails on documented Wave 0 column-mismatch stub bug         |
| `grep -rE "core\.cleanup\.application" backend/core/src/main`                | 0 matches (CONVENTIONS §2 — no `application` package)                                           |
| Banned-abbreviation grep on new files                                        | 0 matches                                                                                       |

## Threat Flags

None — every type shipped is core-layer data + behavior; no new HTTP endpoint, no new auth path, no new file access, no new schema at a trust boundary beyond what Wave 1 already established (covered by Wave 1 SECURITY register).

## Known Stubs

None introduced by this plan. All wired data flows complete at the layer this plan ships. Service / controller / worker / handler stubs that consume these types are scheduled for Waves 3-5 per the plan dependency graph.

## TDD Gate Compliance

N/A — Plan 03 is `type: execute`, not `type: tdd`. RED stubs are part of Wave 0 (Plan 01).
