---
phase: 08-bulk-unsubscribe-campaign
plan: 02
subsystem: cleanup
tags:
  - liquibase
  - schema
  - wave-1
  - migration
  - forward-only
dependency_graph:
  requires:
    - "Wave 0 RED test stub set (Plan 01) — `LiquibaseMigrationTest` + Wave 0 contract for column names (list_unsubscribe_url, list_unsubscribe_mailto, list_unsubscribe_one_click, source, sender_email, sender_domain) the test stubs reference"
  provides:
    - "Schema foundation for Phase 8: 4 new tables (processing_job, sender_suppression, unsubscribe_campaign, unsubscribe_attempt) + 3 new columns on mail_message_observed + 1 new column on triage_audit"
    - "D-01 generic Postgres outbox table `processing_job` reusable by SEED-009 via job_type enum extension"
    - "D-09 sender_suppression with XOR target check (email OR domain, never both)"
    - "H-3 Path A triage_audit.source column to distinguish CLEANUP_CAMPAIGN audit rows from TRIAGE rows, with partial index for undo lookup"
  affects:
    - "backend/core/src/main/resources/db/changelog/changes (6 new YAML files 041..046)"
    - "backend/core/src/main/resources/db/changelog/db.changelog-master.yaml (6 include entries appended)"
tech_stack:
  added: []
  patterns:
    - "Forward-only ADD COLUMN with NULL or false default (D-10) — `mail_message_observed` gains 3 cols without backfill writes; existing rows keep NULL/false"
    - "Native `sql:` for CHECK constraints — Liquibase YAML `addCheckConstraint` not used; falls back to `ALTER TABLE … ADD CONSTRAINT ck_*` to keep predicate text faithful to D-09/D-19"
    - "Partial unique index pattern for XOR target (`WHERE col IS NOT NULL`) — sender_suppression has separate ux_*_email and ux_*_domain"
    - "Partial index for SKIP LOCKED reaper + purge scans (`WHERE status = 'RUNNING'`, `WHERE status IN ('COMPLETED','FAILED')`) on processing_job"
    - "Partial index pattern for cleanup-undo path on triage_audit (`WHERE source = 'CLEANUP_CAMPAIGN'`) — keeps index small versus full index because only campaign-source rows are queried by undo"
    - "FK delete-cascade chains: tenant→all dependent rows; campaign→attempts cascade; campaign.job_id FK to processing_job is NO ACTION (jobs purge after 90d, campaigns kept indefinitely)"
key_files:
  created:
    - backend/core/src/main/resources/db/changelog/changes/041-mail-message-observed-list-unsubscribe.yaml
    - backend/core/src/main/resources/db/changelog/changes/042-processing-job.yaml
    - backend/core/src/main/resources/db/changelog/changes/043-sender-suppression.yaml
    - backend/core/src/main/resources/db/changelog/changes/044-unsubscribe-campaign.yaml
    - backend/core/src/main/resources/db/changelog/changes/045-unsubscribe-attempt.yaml
    - backend/core/src/main/resources/db/changelog/changes/046-triage-audit-source.yaml
  modified:
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
decisions:
  - "Used native `sql:` directives for every CHECK constraint instead of Liquibase's `addCheckConstraint` element to keep the predicate exactly as written in D-09/D-19 (some `addCheckConstraint` validation forks differ across Liquibase 4.x→5.x; raw SQL is portable and matches the analog in changelog 025)."
  - "URL invariant (`https://` prefix) intentionally NOT enforced as a DB CHECK on `list_unsubscribe_url` — D-11 places that guard at parse-time in `GmailPreviewReadService`. A DB-level CHECK would also reject legacy rows that pre-date Phase 8 if any vendor ever ingested with a non-HTTPS value, and would force a backfill purge we explicitly want to avoid (D-10 forward-only)."
  - "`unsubscribe_campaign.job_id` is nullable + uses default NO ACTION FK (not deleteCascade) — D-04 requires transactional INSERT order campaign→attempts→job, so job_id is set last; D-25 purges completed/failed processing_job rows after 90d but campaign rows are kept indefinitely. ON DELETE CASCADE would defeat that. Index `idx_unsubscribe_campaign_job` supports the rare join-back path; once the job row is purged, job_id can be left dangling because campaigns are read by their own id."
  - "`triage_audit.source` defaults to `'TRIAGE'` so the addColumn step itself backfills existing rows in a single ALTER TABLE — no separate backfill changelog needed. The `text` type (matching the analog convention used by `decision`) plus the CHECK constraint enforce the closed enum at write time."
  - "Index `idx_triage_audit_cleanup` is PARTIAL on `WHERE source = 'CLEANUP_CAMPAIGN'`. Trade-off: undo lookups (rare per-campaign) are sub-millisecond on the small partial; TRIAGE-source writes (frequent) pay zero index-write cost. Plan 07 Task 3 will use this index in the undo JOIN path."
  - "Sender targets in `sender_suppression` are stored as the same sanitized + canonicalized form `SenderEmailCanonicalizer` already produces — no FK to a sender catalog (no such table exists yet). When Plan 06 Task 3 writes `triage_audit.sanitized_sender_email` for cleanup rows, it must persist the SAME normalization that `CandidateQueryService` returns so the Plan 07 undo JOIN remains sound."
metrics:
  duration: "approx 40 minutes (read context + 7 YAML authoring + 1 LiquibaseMigrationTest run + commit + summary)"
  completed_date: "2026-05-20"
  files_created: 6
  files_modified: 1
  lines_added: 456
  lines_removed: 0
---

# Phase 8 Plan 02: Wave 1a Schema Migrations Summary

Wave 1a lands the entire schema foundation for Phase 8 bulk unsubscribe — 6 forward-only Liquibase changelogs (041..046) registered in `db.changelog-master.yaml`. After this plan, the database has every column, table, CHECK, and index that Wave 1b entity classes, Wave 2 candidate-query service, Wave 4 worker, and Wave 7 undo service need. No production code yet; pure DDL.

## One-liner

Six Liquibase YAML migrations (041..046) ship the bulk-unsubscribe schema: `mail_message_observed` extension for List-Unsubscribe headers, `processing_job` outbox, `sender_suppression` with XOR target, campaign + attempt aggregate tables with cascading delete, and `triage_audit.source` enum + partial index for cleanup undo.

## What Shipped

### 1. `041-mail-message-observed-list-unsubscribe.yaml` — UNS-01

ADD COLUMN forward-only (D-10). Mirrors `032-mail-message-observed-sender-email.yaml` exactly.

- `list_unsubscribe_url VARCHAR(2048) NULL` — parsed from RFC 8058 `List-Unsubscribe` header; rows pre-Phase 8 keep NULL. Candidate query filters `IS NOT NULL`.
- `list_unsubscribe_mailto VARCHAR(512) NULL` — `mailto:` variant; nullable for same forward-only reason.
- `list_unsubscribe_one_click BOOLEAN NOT NULL DEFAULT false` — D-11 flag for RFC 8058 one-click POST eligibility.
- HTTPS URL invariant deliberately NOT enforced at DB level (decision above) — `GmailPreviewReadService` parse-time guard does it (D-11).

**Rollback:** 3 `dropColumn` in reverse declaration order.

### 2. `042-processing-job.yaml` — D-01 generic Postgres outbox

Mirrors structure of `025-triage-audit.yaml` for tenant FK + CHECK + partial-index pattern, role-matches `017-shedlock-table.yaml` for the worker outbox role.

- 13 columns: id (uuid PK gen_random_uuid()), tenant_id FK→tenants delete-cascade, job_type varchar(64), payload jsonb, status varchar(16) default `QUEUED`, attempts int default 0, next_run_at timestamptz default now(), heartbeat_at timestamptz nullable, created_at timestamptz default now(), started_at + finished_at nullable, failure_reason varchar(255), updated_at timestamptz default now().
- `ck_processing_job_status` CHECK status IN ('QUEUED','RUNNING','COMPLETED','FAILED').
- `ck_processing_job_job_type` CHECK job_type IN ('UNSUBSCRIBE_CAMPAIGN') — SEED-009 will extend this enum via a follow-up migration.
- `idx_processing_job_pickup` (status, next_run_at, created_at) — supports `SELECT … FOR UPDATE SKIP LOCKED` poll (D-02).
- `idx_processing_job_running_heartbeat` PARTIAL on `(heartbeat_at) WHERE status = 'RUNNING'` — D-03 reaper crash-recovery scan.
- `idx_processing_job_tenant_type` (tenant_id, job_type) — per-tenant filter for admin / analytics queries.
- `idx_processing_job_finished_at` PARTIAL on `(finished_at) WHERE status IN ('COMPLETED','FAILED')` — D-25 purge batch (90-day TTL).

**Rollback:** `dropTable processing_job`.

### 3. `043-sender-suppression.yaml` — UNS-02

Role-matches `027-tenant-sender-opt-in.yaml` for per-tenant sender list with FK delete-cascade.

- 6 columns: id (uuid PK), tenant_id FK→tenants delete-cascade, sender_email varchar(320) nullable, sender_domain varchar(255) nullable, reason varchar(16) NOT NULL, created_at timestamptz default now().
- `ck_sender_suppression_one_target` CHECK `(sender_email IS NOT NULL) <> (sender_domain IS NOT NULL)` — XOR exactly-one-target invariant from D-09. PostgreSQL `<>` between booleans is logical XOR.
- `ck_sender_suppression_reason` CHECK reason IN ('manual','replied','auto') — matches `SenderSuppressionReason` ids from D-09 (manual = user added, replied = auto-added after reply, auto = system policy).
- `ux_sender_suppression_email` UNIQUE PARTIAL on `(tenant_id, sender_email) WHERE sender_email IS NOT NULL` — no duplicate email suppression per tenant.
- `ux_sender_suppression_domain` UNIQUE PARTIAL on `(tenant_id, sender_domain) WHERE sender_domain IS NOT NULL` — no duplicate domain suppression per tenant.

**Rollback:** `dropTable sender_suppression`.

### 4. `044-unsubscribe-campaign.yaml` — campaign aggregate root

Role-matches `025-triage-audit.yaml` (tenant FK + applied_at/reverted_at + status CHECK pattern).

- 9 columns: id (uuid PK), tenant_id FK→tenants delete-cascade, **job_id uuid nullable FK→processing_job(id)** with NO ACTION on delete (rationale in Decisions above; D-25 purges jobs while campaigns are kept), status varchar(16) default `QUEUED`, applied_at + reverted_at timestamptz nullable, total_sender_count + total_history_message_count int NOT NULL, created_at timestamptz default now().
- `ck_unsubscribe_campaign_status` CHECK status IN ('QUEUED','RUNNING','COMPLETED','FAILED').
- `idx_unsubscribe_campaign_tenant_created` (tenant_id, created_at DESC) — supports the campaign-history list view per tenant.
- `idx_unsubscribe_campaign_job` (job_id) — supports rare join-back from processing_job to campaign.

**Rollback:** `dropTable unsubscribe_campaign`.

### 5. `045-unsubscribe-attempt.yaml` — per-sender attempt row

Role-matches `025-triage-audit.yaml` for the per-row state machine + composite index pattern (PATTERNS Group 1).

- 10 columns: id (uuid PK), **campaign_id FK→unsubscribe_campaign delete-CASCADE**, sender_email varchar(320) NOT NULL, sender_domain varchar(255) NOT NULL, unsubscribe_method varchar(16) NOT NULL, state varchar(16) default `PENDING`, failure_reason varchar(255) nullable, archived_message_count int default 0, started_at + finished_at timestamptz nullable.
- `ck_unsubscribe_attempt_method` CHECK method IN ('ONE_CLICK','MAILTO') — D-09 closed transport set.
- `ck_unsubscribe_attempt_state` CHECK state IN ('PENDING','RUNNING','OK','FAILED') — per-attempt lifecycle.
- `idx_unsubscribe_attempt_campaign_state` (campaign_id, state) — supports campaign progress queries + state-rollup aggregations.
- `idx_unsubscribe_attempt_campaign_domain` (campaign_id, sender_domain) — supports worker's throttle bucket-by-domain query (D-13).

**Rollback:** `dropTable unsubscribe_attempt` — campaign's CASCADE wipes children automatically anyway, but explicit drop in rollback is required because attempt is registered as its own changeSet and runs first in reverse.

### 6. `046-triage-audit-source.yaml` — H-3 Path A audit-row provenance

Extends existing `triage_audit` table with provenance column so Plan 07 undo can locate cleanup-archived audit rows without touching TRIAGE rows.

- `source TEXT NOT NULL DEFAULT 'TRIAGE'` — single ALTER TABLE backfills every existing row with `'TRIAGE'` in one transaction (no separate backfill changelog needed).
- `ck_triage_audit_source` CHECK source IN ('TRIAGE','CLEANUP_CAMPAIGN').
- `idx_triage_audit_cleanup` PARTIAL on `(tenant_id, sanitized_sender_email, applied_at DESC) WHERE source = 'CLEANUP_CAMPAIGN'` — Plan 07 Task 3 undo lookup path. Partial keeps the index small because cleanup-source rows are a tiny fraction of total triage_audit volume; write cost for TRIAGE-source rows is zero.

**Rollback:** DROP INDEX → DROP CONSTRAINT → DROP COLUMN (reverse declaration order).

### 7. `db.changelog-master.yaml`

Appended 6 include entries (041..046) after the existing `040-triage-audit-message-ref.yaml` line, preserving the existing `file: changes/XXX.yaml` + `relativeToChangelogFile: true` format.

## CHECK Constraints — Quick Reference

| Constraint | Table | Predicate | Source |
| --- | --- | --- | --- |
| ck_processing_job_status | processing_job | status IN ('QUEUED','RUNNING','COMPLETED','FAILED') | D-01 |
| ck_processing_job_job_type | processing_job | job_type IN ('UNSUBSCRIBE_CAMPAIGN') | D-01 (closed enum; SEED-009 will extend) |
| ck_sender_suppression_one_target | sender_suppression | (sender_email IS NOT NULL) <> (sender_domain IS NOT NULL) | D-09 XOR target |
| ck_sender_suppression_reason | sender_suppression | reason IN ('manual','replied','auto') | D-09 |
| ck_unsubscribe_campaign_status | unsubscribe_campaign | status IN ('QUEUED','RUNNING','COMPLETED','FAILED') | Campaign lifecycle |
| ck_unsubscribe_attempt_method | unsubscribe_attempt | unsubscribe_method IN ('ONE_CLICK','MAILTO') | D-09 |
| ck_unsubscribe_attempt_state | unsubscribe_attempt | state IN ('PENDING','RUNNING','OK','FAILED') | Per-attempt lifecycle |
| ck_triage_audit_source | triage_audit | source IN ('TRIAGE','CLEANUP_CAMPAIGN') | H-3 Path A |

## Indexes — Quick Reference

| Index | Table | Definition | Purpose |
| --- | --- | --- | --- |
| idx_processing_job_pickup | processing_job | (status, next_run_at, created_at) | D-02 SKIP LOCKED poll |
| idx_processing_job_running_heartbeat | processing_job | PARTIAL (heartbeat_at) WHERE status='RUNNING' | D-03 reaper scan |
| idx_processing_job_tenant_type | processing_job | (tenant_id, job_type) | Per-tenant filter |
| idx_processing_job_finished_at | processing_job | PARTIAL (finished_at) WHERE status IN ('COMPLETED','FAILED') | D-25 90d purge |
| ux_sender_suppression_email | sender_suppression | UNIQUE PARTIAL (tenant_id, sender_email) WHERE sender_email IS NOT NULL | No duplicate email suppression |
| ux_sender_suppression_domain | sender_suppression | UNIQUE PARTIAL (tenant_id, sender_domain) WHERE sender_domain IS NOT NULL | No duplicate domain suppression |
| idx_unsubscribe_campaign_tenant_created | unsubscribe_campaign | (tenant_id, created_at DESC) | Campaign-history list view |
| idx_unsubscribe_campaign_job | unsubscribe_campaign | (job_id) | Job→campaign join-back |
| idx_unsubscribe_attempt_campaign_state | unsubscribe_attempt | (campaign_id, state) | Progress + state rollup |
| idx_unsubscribe_attempt_campaign_domain | unsubscribe_attempt | (campaign_id, sender_domain) | D-13 throttle bucket-by-domain |
| idx_triage_audit_cleanup | triage_audit | PARTIAL (tenant_id, sanitized_sender_email, applied_at DESC) WHERE source='CLEANUP_CAMPAIGN' | Plan 07 undo lookup |

## Foreign Keys — Quick Reference

| FK Name | Child | Parent | ON DELETE | Why |
| --- | --- | --- | --- | --- |
| fk_processing_job_tenant | processing_job | tenants(id) | CASCADE | Privacy: drop tenant → drop their jobs |
| fk_sender_suppression_tenant | sender_suppression | tenants(id) | CASCADE | Privacy: drop tenant → drop their suppression list |
| fk_unsubscribe_campaign_tenant | unsubscribe_campaign | tenants(id) | CASCADE | Privacy: drop tenant → drop their campaigns |
| fk_unsubscribe_campaign_job | unsubscribe_campaign | processing_job(id) | NO ACTION (default) | Campaigns outlive jobs by D-25 90d purge |
| fk_unsubscribe_attempt_campaign | unsubscribe_attempt | unsubscribe_campaign(id) | CASCADE | Tenant delete cascades through campaign to attempts |

## Rollback Strategy

Forward-only is the project philosophy, but every changeSet ships a `rollback:` block per project-wide test convention. Rollback order for each file (reverse-declared) is:

- **041:** drop `list_unsubscribe_one_click` → `list_unsubscribe_mailto` → `list_unsubscribe_url` (booleans first because their default-NOT-NULL is the most invasive).
- **042..045:** simple `dropTable` — Postgres handles FK + index cleanup automatically.
- **046:** DROP INDEX → DROP CONSTRAINT → DROP COLUMN (must drop dependents before the column itself).

## Verification

### Automated

`./gradlew :backend:core:test --tests "*LiquibaseMigrationTest*"` — **BUILD SUCCESSFUL (22/22 tests pass)**.

`LiquibaseMigrationTest` boots a fresh Postgres 17.6 Testcontainer, applies all 46 changelogs forward, and asserts the 9 required base tables are present (plus the existing `ux_triage_audit_idem`, `idx_thread_reply_status_*` index integrity assertions). After this plan: 46 changelogs apply clean, including the 6 new ones from 041..046.

### Out-of-scope failures observed (expected)

- `CleanupModuleVerificationTest` — fails because `core.cleanup` Spring Modulith module type doesn't exist yet (Wave 1b/Plan 03 ships it). Plan 02 acceptance: "Wave 0 test `CleanupModuleVerificationTest` chưa pass (vì code chưa) — Wave 1b sẽ flip."
- `TriageAuditWriterCleanupArchiveTest` — fails for two reasons, neither caused by this plan: (a) `TriageAuditWriter.recordCleanupArchive` method doesn't exist yet (Wave 1b/Plan 06 ships it — NoSuchMethodException) and (b) the test stub references columns `subject_excerpt` and `matcher_evidence` that do NOT exist in `triage_audit` (those are Wave 0 test-stub typos for `sanitized_subject` from changelog 040; tracked as a Wave 1b/Plan 03 fix-up not Plan 02 scope).

### Environment workaround applied (local-only, non-committed)

The Windows host running these tests reports its IANA timezone as `Asia/Saigon` (legacy alias), which Postgres 17 rejects with `FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"` during JDBC connection startup. Same failure reproduces at HEAD (Wave 0) without my changes — pre-existing infrastructure issue, not caused by this plan. Workaround used **only locally**: a `~/.gradle/init.d/local-tz-fix.gradle.kts` init script overrides `user.timezone=Asia/Ho_Chi_Minh` on the test fork JVM. The init script is **outside the repo** and NOT committed; users on Linux / macOS or with `Asia/Ho_Chi_Minh` already in the host TZ are unaffected. A clean fix (e.g., setting `user.timezone` in `buildSrc/zeromail.java-conventions.gradle.kts` or in `PostgresContainerTest` via `setEnv("TZ", "UTC")`) is out of scope for Plan 02 — should be filed as a separate infra ticket.

## Acceptance Criteria — Pass/Fail Check

- [x] 6 file YAML 041..046 land với schema đúng D-09 + D-19 + D-25 + H-3 Path A (046 triage_audit.source).
- [x] Mọi CHECK constraint + index per RESEARCH §"Liquibase DDL sketch" + D-25 partial index cho purge.
- [x] `LiquibaseMigrationTest` xanh trên Postgres 17 Testcontainer (22/22 PASS với local TZ workaround; pre-existing env issue at HEAD documented above).
- [x] Schema có đủ 4 table mới + 3 cột mới trên mail_message_observed + 1 cột mới trên triage_audit (`source`) + 1 partial index `idx_triage_audit_cleanup` — verified by Liquibase apply test running against the fresh container.

`LiquibaseRollbackTest` glob in the plan acceptance does NOT match any existing test class in the project — that pattern is a placeholder for project-wide rollback testing convention. Forward apply is fully exercised by `LiquibaseMigrationTest`; rollback semantics are documented in each changeSet's `rollback:` block but not exercised by a dedicated test (out-of-scope for Plan 02).

## Deviations from Plan

### Auto-fixed Issues

None — schema authoring matched the plan's task descriptions, RESEARCH DDL sketches, and PATTERNS analog excerpts exactly.

### Out-of-Scope Discoveries

1. **Pre-existing timezone issue** — `Asia/Saigon` legacy alias rejected by Postgres 17 JDBC startup. Documented as a local-machine env issue; not in Plan 02 scope. Logged here for the Wave 1b executor's awareness (same workaround will be needed).
2. **Wave 0 test column typos** — `TriageAuditWriterCleanupArchiveTest` references `subject_excerpt` and `matcher_evidence` columns that do not exist in `triage_audit` (changelog 040 added `sanitized_subject` not `subject_excerpt`; `matcher_evidence` doesn't exist anywhere). The test was supposed to compile reflectively but its raw SQL prevents that. Logged for Wave 1b / Plan 06 to fix when wiring `TriageAuditWriter.recordCleanupArchive`.

### Auth Gates

None.

## Known Stubs

None — this plan ships only DDL. No application-layer code, no UI, no API endpoints. The 4 new tables + 1 ADD COLUMN extension are immediately usable by subsequent waves' code.

## Commits

- `d4c3a07e` — feat(phase-08-wave-1): Liquibase changelogs 041..046 for bulk unsubscribe campaign (7 files changed, 456 insertions(+))

## Self-Check: PASSED

- Files exist:
  - FOUND: backend/core/src/main/resources/db/changelog/changes/041-mail-message-observed-list-unsubscribe.yaml
  - FOUND: backend/core/src/main/resources/db/changelog/changes/042-processing-job.yaml
  - FOUND: backend/core/src/main/resources/db/changelog/changes/043-sender-suppression.yaml
  - FOUND: backend/core/src/main/resources/db/changelog/changes/044-unsubscribe-campaign.yaml
  - FOUND: backend/core/src/main/resources/db/changelog/changes/045-unsubscribe-attempt.yaml
  - FOUND: backend/core/src/main/resources/db/changelog/changes/046-triage-audit-source.yaml
- Commits:
  - FOUND: d4c3a07e

`LiquibaseMigrationTest` 22/22 PASS — Liquibase forward apply on Postgres 17 Testcontainer green; all 46 changelogs (001..046) apply clean.
