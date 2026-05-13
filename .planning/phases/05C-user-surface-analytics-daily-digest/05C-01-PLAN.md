---
phase: 05C
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - backend/core/src/main/resources/db/changelog/changes/032-mail-message-observed-sender-email.yaml
  - backend/core/src/main/resources/db/changelog/changes/033-tenants-time-zone.yaml
  - backend/core/src/main/resources/db/changelog/changes/034-notification-preference.yaml
  - backend/core/src/main/resources/db/changelog/changes/035-digest-delivery.yaml
  - backend/core/src/main/resources/db/changelog/changes/036-analytics-supporting-indexes.yaml
  - backend/core/src/main/resources/db/changelog/changes/037-notification-preference-backfill.yaml
  - backend/core/src/main/resources/db/changelog/master.yaml
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java
  - backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java
  - backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java
  - backend/core/src/main/java/com/zeromail/core/tenant/usecases/TenantService.java
  - backend/core/src/main/java/com/zeromail/core/notification/package-info.java
  - backend/core/src/main/java/com/zeromail/core/notification/domain/ChannelType.java
  - backend/core/src/main/java/com/zeromail/core/notification/domain/DigestDeliveryStatus.java
  - backend/core/src/main/java/com/zeromail/core/notification/persistence/NotificationPreferenceEntity.java
  - backend/core/src/main/java/com/zeromail/core/notification/persistence/NotificationPreferenceRepository.java
  - backend/core/src/main/java/com/zeromail/core/notification/persistence/DigestDeliveryEntity.java
  - backend/core/src/main/java/com/zeromail/core/notification/persistence/DigestDeliveryRepository.java
  - backend/core/src/main/java/com/zeromail/core/notification/usecases/NotificationPreferenceService.java
  - backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java
  - backend/core/src/main/java/com/zeromail/core/account/usecases/AccountDeletionService.java
  - backend/core/src/test/java/com/zeromail/core/notification/persistence/NotificationPreferencePersistenceTest.java
  - backend/core/src/test/java/com/zeromail/core/notification/persistence/DigestDeliveryUniqueConstraintTest.java
  - backend/core/src/test/java/com/zeromail/core/account/OAuthProvisioningDefaultsTest.java
  - backend/core/src/test/java/com/zeromail/core/notification/persistence/NotificationPreferenceBackfillTest.java
  - backend/core/src/test/java/com/zeromail/core/gmail/GmailDeliveryProcessingSenderEmailTest.java
autonomous: true
requirements:
  - ANL-02
  - ANL-03
  - WEB-02
threat_refs:
  - T-05C-01
  - T-05C-02
  - T-05C-03
must_haves:
  truths:
    - "Schema migrations land idempotently — six new Liquibase changesets apply cleanly from scratch and on top of an existing 031 database"
    - "Every existing tenant ends up with `tenants.time_zone='Asia/Ho_Chi_Minh'` after migration 033 runs"
    - "Every existing tenant ends up with a `notification_preference (tenant_id, 'EMAIL', true, 20)` row after backfill migration 037 runs (so the digest scheduler sees them) — REVIEW FIX (Codex C3)"
    - "Every newly provisioned tenant (post OAuth provisioning) has exactly one `notification_preference (tenant_id, 'EMAIL', true, 20)` row"
    - "Persisted `channel` DB value equals the uppercase enum name `'EMAIL'` — Liquibase indexes, backfill, and all queries downstream of this plan use `channel = 'EMAIL'` (NOT `'email'`); ChannelType.EMAIL.id() returns `\"EMAIL\"`; @Enumerated(STRING) matches — REVIEW FIX (Codex C2)"
    - "`NotificationPreferenceEntity` PK is a single UUID `id` column with UNIQUE `(tenant_id, channel)` — entity extends `AbstractTenantOwnedEntity` (which provides `id`) without conflict — REVIEW FIX (Codex C1)"
    - "`digest_delivery` UNIQUE constraint rejects a second insert for the same `(tenant_id, digest_day_local)` with SQLState 23505"
    - "`digest_delivery` carries an `external_ref varchar(255) NULL` column so Plan 03 `markSent(..., externalId)` is persistable — REVIEW FIX (Codex MEDIUM)"
    - "`mail_message_observed` rows written by ingestion after this plan carry a non-null `sender_email` when Gmail returns a From header — extracted at the REAL writer `GmailDeliveryProcessingService` (NOT `PubSubIngestionService`) via `format=METADATA` + `metadataHeaders=From` — REVIEW FIX (Codex C4)"
    - "Account deletion cascades remove `notification_preference` and `digest_delivery` rows for that tenant"
  artifacts:
    - path: "backend/core/src/main/resources/db/changelog/changes/032-mail-message-observed-sender-email.yaml"
      provides: "Nullable sender_email column on mail_message_observed (§0 fix)"
      contains: "addColumn"
    - path: "backend/core/src/main/resources/db/changelog/changes/033-tenants-time-zone.yaml"
      provides: "NOT NULL time_zone column on tenants with default 'Asia/Ho_Chi_Minh'"
    - path: "backend/core/src/main/resources/db/changelog/changes/034-notification-preference.yaml"
      provides: "notification_preference table + partial index for scheduler fanout"
    - path: "backend/core/src/main/resources/db/changelog/changes/035-digest-delivery.yaml"
      provides: "digest_delivery table + UNIQUE(tenant_id, digest_day_local) + external_ref column"
    - path: "backend/core/src/main/resources/db/changelog/changes/036-analytics-supporting-indexes.yaml"
      provides: "Btree indexes for Q1 (mail_message_observed tenant_id, observed_at), Q3 (tenant_id, sender_email, observed_at) WHERE sender_email IS NOT NULL, Q4 (triage_audit tenant_id, rule_name_snapshot, decided_at)"
    - path: "backend/core/src/main/resources/db/changelog/changes/037-notification-preference-backfill.yaml"
      provides: "INSERT-SELECT backfill of existing tenants into notification_preference (channel='EMAIL', digest_enabled=true, hour=20) with ON CONFLICT DO NOTHING (Codex C3)"
    - path: "backend/core/src/main/java/com/zeromail/core/notification/persistence/NotificationPreferenceEntity.java"
      provides: "JPA entity for per-tenant per-channel preference"
    - path: "backend/core/src/main/java/com/zeromail/core/notification/persistence/DigestDeliveryEntity.java"
      provides: "JPA entity for idempotency-and-status rows"
  key_links:
    - from: "backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java"
      to: "core.notification.usecases.NotificationPreferenceService"
      via: "insertDefaults(tenantId, ChannelType.EMAIL, true, 20) inside bundledTransaction"
      pattern: "notificationPreferenceService.insertDefaults"
    - from: "backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java"
      to: "mail_message_observed.sender_email column"
      via: "Gmail metadata GET with format=METADATA + metadataHeaders=From, sanitized From passed through MailMessageObservedRepository.insertObservedIfAbsent (Codex C4)"
      pattern: "metadataHeaders.*From|senderEmail"
---

<objective>
Ship the Wave 1 schema + entities + defaults wiring that the analytics endpoint (Plan 02), digest dispatcher (Plan 03), and frontend (Plan 04) all build on. This plan is the foundation of Phase 5C: it resolves the §0 SHOW-STOPPER (`mail_message_observed.sender_email` missing — added per CONTEXT.md and RESEARCH.md §0, populated at the REAL ingestion writer `GmailDeliveryProcessingService` via Gmail `format=METADATA` + `metadataHeaders=From`); adds `tenants.time_zone`, `notification_preference` (with single-column UUID `id` PK + UNIQUE `(tenant_id, channel)` per review C1), `digest_delivery` (with `external_ref` for Resend external IDs per review medium-severity), and an existing-tenant backfill changeset (review C3); creates JPA entities + the `NotificationPreferenceService`; extends `OAuthProvisioningService` to seed defaults inside the existing `PROPAGATION_REQUIRED` transaction (D-17); and wires account-deletion cascades (D-16). The locked `channel` storage value is uppercase `'EMAIL'` (review C2 — JPA `@Enumerated(STRING)` stores `EMAIL`, so all Liquibase indexes, backfill SQL, and Plan 03 queries match). No HTTP endpoints, no scheduler, no UI in this plan — those land in Plans 02–04.

Purpose: every Plan 02 task assumes these columns + entities exist; every Plan 03 task assumes the `digest_delivery` UNIQUE + `external_ref` + the channel + status enums + the existing-tenant backfill exist. This is the depends_on root for the rest of the phase.

Output: 6 Liquibase changesets (32–37 inclusive — 037 is the existing-tenant backfill added by review C3), 8 Java source files (entities, repositories, service, package-info; `NotificationPreferenceId` is REMOVED because Codex C1 fix changes the PK to a single UUID column), 3 modifications to existing services (`GmailDeliveryProcessingService` for real-writer sender extraction per C4, `OAuthProvisioningService`, `AccountDeletionService` / equivalent cascade host), 5 new Testcontainers-backed test classes (added: `NotificationPreferenceBackfillTest` for C3; `GmailDeliveryProcessingSenderEmailTest` for C4).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/REQUIREMENTS.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-SPEC.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-CONTEXT.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-RESEARCH.md
@.planning/phases/05C-user-surface-analytics-daily-digest/05C-VALIDATION.md
@CLAUDE.md
@CONVENTIONS.md

<!-- Prior-phase references -->
@.planning/phases/02A-mail-ingestion/02A-CONTEXT.md
@.planning/phases/01.5-inbox-zero-alignment-bundled-oauth-ux-polish-cleanup-sweep-r/01.5-CONTEXT.md
@.planning/phases/04-triage-convergence-hero/04-CONTEXT.md
@.planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md

<!-- Interface anchors the executor must read before editing -->
@backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java
@backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java
@backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java
@backend/core/src/main/java/com/zeromail/core/shared/persistence/AbstractTenantOwnedEntity.java
@backend/core/src/main/java/com/zeromail/core/shared/lang/IdentifiedEnum.java
@backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml
@backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml
@backend/core/src/main/resources/db/changelog/master.yaml
</context>

<interfaces>
<!-- Key facts the executor needs without re-exploring -->

Existing `mail_message_observed` columns (from changeset 012): tenant_id (uuid), gmail_message_id (varchar), gmail_thread_id (varchar), history_id (bigint), label_ids (text[]), internal_date (timestamptz), observed_at (timestamptz). NO sender_email column today — this plan adds it.

Existing `triage_audit` indexes (from changeset 025): pk(audit_id), ux_triage_audit_idem (tenant_id, gmail_message_id, rule_id, action_type, args_hash) NULLS NOT DISTINCT, idx_triage_audit_tenant_message (tenant_id, gmail_message_id), idx_triage_audit_tenant_decided_at (tenant_id, decided_at), idx_triage_audit_pending_last_attempt partial.

`AbstractTenantOwnedEntity` (read this file CAREFULLY — load-bearing for review C1): provides a single-column UUID `id` PK column annotated `@Id`, plus `tenant_id` (auto-populated via @TenantId), created_at, updated_at, version (auditing). REVIEW FIX (Codex C1): `NotificationPreferenceEntity` MUST use this inherited `id` PK + a separate UNIQUE constraint on `(tenant_id, channel)` — NOT `@IdClass(NotificationPreferenceId.class)` (which would conflict with the inherited @Id). `DigestDeliveryEntity` already uses single UUID PK so no change.

`IdentifiedEnum` contract: `String id()` + static `fromId(String)` fail-loud (`NoSuchElementException`); persisted via `@Enumerated(EnumType.STRING)` with id()==name() invariant. REVIEW FIX (Codex C2): `ChannelType.EMAIL.id()` returns `"EMAIL"` (uppercase) — NOT `"email"`. JPA `@Enumerated(STRING)` persists the enum name (`EMAIL`); Liquibase index predicates, backfill SQL, and Plan 03's scheduler claim query all use `channel = 'EMAIL'`. DigestDeliveryStatus ids = `"PENDING"`, `"SENT"`, `"FAILED"` (unchanged — already uppercase-consistent).

Liquibase sequence: last existing changeset is `031-thread-reply-status-resolved-index.yaml`. This plan adds 032, 033, 034, 035, 036, **037** (existing-tenant backfill — Codex C3) in `backend/core/src/main/resources/db/changelog/changes/` and adds the includes to `master.yaml`. 037 MUST land after 034 (notification_preference table exists) and after 033 (tenants.time_zone exists, though backfill does not need it — `INSERT INTO notification_preference SELECT ... FROM tenants` uses only `tenants.id`).

REAL ingestion writer (Codex C4 — load-bearing): `mail_message_observed` rows are written by `GmailDeliveryProcessingService.processDelivery(...)` (NOT `PubSubIngestionService` which only verifies the OIDC token and routes), which calls `MailMessageObservedRepository.insertObservedIfAbsent(...)`. Executor MUST grep `INSERT INTO mail_message_observed`, locate the actual call site, and add a Gmail `users.messages.get(format=METADATA, metadataHeaders=[From])` fetch step BEFORE the insert call so the `From` header is available to sanitize and pass through. The existing Gmail metadata request currently uses `format=MINIMAL` (no headers) — this MUST change to `METADATA` with the `metadataHeaders` parameter populated, per RESEARCH §0 and Codex C4.

`OAuthProvisioningService.provisionBundledOAuth` host transaction: opens `bundledTransaction.executeWithoutResult(...)` under a `ScopedValue.where(TenantContext.TENANT, ...)`. The two new method calls (`tenantService.setTimeZoneIfAbsent` and `notificationPreferenceService.insertDefaults`) must land INSIDE that block, BEFORE `gmailConnectionService.upsert(...)`. Both methods inherit `PROPAGATION_REQUIRED` automatically.
</interfaces>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Liquibase migration wave — sender_email column + time_zone + notification_preference (single-UUID PK + UNIQUE(tenant_id,channel)) + digest_delivery (with external_ref) + analytics indexes + existing-tenant notification_preference backfill</name>
  <files>
    backend/core/src/main/resources/db/changelog/changes/032-mail-message-observed-sender-email.yaml,
    backend/core/src/main/resources/db/changelog/changes/033-tenants-time-zone.yaml,
    backend/core/src/main/resources/db/changelog/changes/034-notification-preference.yaml,
    backend/core/src/main/resources/db/changelog/changes/035-digest-delivery.yaml,
    backend/core/src/main/resources/db/changelog/changes/036-analytics-supporting-indexes.yaml,
    backend/core/src/main/resources/db/changelog/changes/037-notification-preference-backfill.yaml,
    backend/core/src/main/resources/db/changelog/master.yaml,
    backend/core/src/test/java/com/zeromail/core/notification/persistence/DigestDeliveryUniqueConstraintTest.java,
    backend/core/src/test/java/com/zeromail/core/notification/persistence/NotificationPreferenceBackfillTest.java
  </files>
  <read_first>
    backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml,
    backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml,
    backend/core/src/main/resources/db/changelog/changes/031-thread-reply-status-resolved-index.yaml,
    backend/core/src/main/resources/db/changelog/master.yaml,
    backend/core/src/main/java/com/zeromail/core/shared/persistence/AbstractTenantOwnedEntity.java,
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-RESEARCH.md (§0 schema fix and §5 index recommendations and §8 changeset numbering),
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-REVIEWS.md (Codex C1 PK shape, C2 enum DB value, C3 backfill, MEDIUM external_ref)
  </read_first>
  <behavior>
    - Changeset 032: `addColumn` `sender_email varchar(320)` NULLABLE to `mail_message_observed`; no NOT NULL promotion in this phase (per §0 — pre-fix rows must remain valid)
    - Changeset 033: `addColumn` `time_zone varchar(64)` to `tenants` with `defaultValueComputed: 'Asia/Ho_Chi_Minh'` and NOT NULL; existing rows backfilled by the default
    - Changeset 034 (REVIEW FIX C1 — PK shape changed): create `notification_preference` table — columns `id uuid PRIMARY KEY` (matches the inherited `@Id` on `AbstractTenantOwnedEntity`), `tenant_id uuid NOT NULL`, `channel varchar(16) NOT NULL`, `digest_enabled boolean NOT NULL DEFAULT true`, `digest_send_hour_local int NOT NULL DEFAULT 20`, `created_at`, `updated_at`, `version int`; **NO composite PK** — instead a separate `UNIQUE (tenant_id, channel)` constraint (named `uq_notification_preference_tenant_channel`) provides the business key; FK `tenant_id → tenants(id) ON DELETE CASCADE`; CHECK `digest_send_hour_local BETWEEN 0 AND 23`; partial btree index `idx_notification_preference_due` on `(tenant_id)` `WHERE digest_enabled = true AND channel = 'EMAIL'` (REVIEW FIX C2 — uppercase `'EMAIL'` matches the JPA-persisted enum name)
    - Changeset 035 (REVIEW FIX — add external_ref): create `digest_delivery` table — columns `id uuid PRIMARY KEY`, `tenant_id uuid NOT NULL`, `digest_day_local date NOT NULL`, `status varchar(16) NOT NULL DEFAULT 'PENDING'`, `channel varchar(16) NOT NULL`, `attempt_count int NOT NULL DEFAULT 1`, `next_attempt_at timestamptz NULL`, `dispatched_at timestamptz`, `external_ref varchar(255) NULL` (Resend `email_id` from `CreateEmailResponse.getId()` — Codex MEDIUM fix; needed by Plan 03 `markSent(..., externalId)`), `failure_reason varchar(255)`, `created_at`, `updated_at`, `version int`; UNIQUE `(tenant_id, digest_day_local)` (named `uq_digest_delivery_tenant_day`); FK `tenant_id → tenants(id) ON DELETE CASCADE`; supporting btree `idx_digest_delivery_reaper` on `(status, created_at)` for the reaper
    - Changeset 036: three new btree indexes — `idx_mail_message_observed_tenant_observed_at (tenant_id, observed_at)`, `idx_mail_message_observed_tenant_sender_observed (tenant_id, sender_email, observed_at)` declared with `WHERE sender_email IS NOT NULL` partial-index clause (matches the §0 graceful-skip filter Plan 02 Q3 uses), `idx_triage_audit_tenant_rule_decided (tenant_id, rule_name_snapshot, decided_at)`
    - Changeset 037 (REVIEW FIX C3 — existing-tenant backfill): `sql` block — `INSERT INTO notification_preference (id, tenant_id, channel, digest_enabled, digest_send_hour_local, created_at, updated_at, version) SELECT gen_random_uuid(), t.id, 'EMAIL', true, 20, now(), now(), 0 FROM tenants t ON CONFLICT (tenant_id, channel) DO NOTHING;` — uppercase `'EMAIL'` matches JPA `@Enumerated(STRING)` storage (C2). Idempotent — re-running the changeset is a no-op because of the UNIQUE constraint. MUST run AFTER changeset 034 (table exists) and 033 (tenants.time_zone exists, though not referenced).
    - `master.yaml`: register all 6 changesets in numeric order at the end of the existing `<include>` list (032 → 037)
    - Test 1 (DigestDeliveryUniqueConstraintTest): seeded fixture inserts row for tenant T, day D; second insert with same (T, D) raises `DataIntegrityViolationException` SQLState 23505
    - Test 2 (NotificationPreferenceBackfillTest — NEW for C3): seed 3 tenants via raw INSERT BEFORE running 037 (simulating pre-5C state); run liquibase update; assert each of the 3 tenants now has exactly one `notification_preference` row with `(channel='EMAIL', digest_enabled=true, digest_send_hour_local=20)`; run liquibase update a second time (idempotency); assert STILL exactly one row per tenant
    - Test 3: `./gradlew :backend:core:liquibaseUpdate` (or the equivalent test-container migrate) applies 032–037 with zero errors
  </behavior>
  <action>Create 6 new YAML changesets under `backend/core/src/main/resources/db/changelog/changes/` strictly following the existing YAML style (`databaseChangeLog` → `changeSet` with `id` + `author: zero-mail`; use `addColumn`, `createTable`, `addUniqueConstraint`, `createIndex` blocks; index names follow `idx_<table>_<columns>` convention; UNIQUE constraint names follow `uq_<table>_<columns>`). Changeset 037 uses a `sql` block (Liquibase supports raw SQL inside YAML via `- sql:` / `sql: |`) because the `INSERT ... SELECT ... ON CONFLICT` form is more compact and idempotent than per-row inserts; verify the existing repo has a precedent for raw-SQL changesets — if not, use `<sql>` element form. Append `<include file="changes/032-..."/>` (and the five others, in numeric order 032 → 037) to `backend/core/src/main/resources/db/changelog/master.yaml`. ALL changesets are forward-only — no rollback blocks unless an existing changeset in this repo has one (verify the convention from changesets 012/025/031). The `sender_email` column on `mail_message_observed` stays NULLABLE forever (per §0 — pre-fix rows must remain valid; Plan 02 Q3 and changeset 036's `idx_mail_message_observed_tenant_sender_observed` both filter `WHERE sender_email IS NOT NULL`). REVIEW FIXES:
  - **C1**: `notification_preference` uses single UUID `id` PK + UNIQUE `(tenant_id, channel)` — NOT composite PK. This matches `AbstractTenantOwnedEntity`'s inherited `@Id` column.
  - **C2**: every reference to the email channel in DB-layer SQL (the partial index predicate in 034 and the backfill SELECT in 037) MUST use uppercase `'EMAIL'`. This is because Plan 02 Task 2 step also locks `ChannelType.EMAIL.id() = "EMAIL"` so JPA `@Enumerated(STRING)` and SQL match.
  - **C3**: 037 inserts a row per existing tenant via `INSERT ... SELECT ... FROM tenants ON CONFLICT DO NOTHING` — guarantees existing tenants receive the digest after deploy.
  - **MEDIUM (external_ref)**: `digest_delivery.external_ref varchar(255) NULL` accommodates Resend's `email_id` (UUID-like, ~36 chars).
  - **MEDIUM (next_attempt_at)**: `digest_delivery.next_attempt_at timestamptz NULL` lets Plan 03 reaper/retry job sequence transient failures without re-inserting (also addresses Codex MEDIUM transient-retry concern; Plan 03 still locks v1 as no-retry-after-FAILED, but the column is available for the reaper to flip a row back to PENDING with `next_attempt_at` deferred).
  After writing, run `./gradlew :backend:core:check --tests DigestDeliveryUniqueConstraintTest --tests NotificationPreferenceBackfillTest` to ensure both new tests pass. Implements D-15 + D-20 + §0 fix + REVIEWS C1 + C2 + C3 + MEDIUM external_ref + MEDIUM next_attempt_at.</action>
  <verify>
    <automated>./gradlew :backend:core:test --tests DigestDeliveryUniqueConstraintTest --tests NotificationPreferenceBackfillTest -x checkstyleMain -x spotlessCheck</automated>
  </verify>
  <done>
    All 6 YAML changesets exist under `backend/core/src/main/resources/db/changelog/changes/`; `master.yaml` includes them in 032..037 order; `notification_preference` table has single-UUID `id` PK + UNIQUE `(tenant_id, channel)`; `digest_delivery` has `external_ref` and `next_attempt_at` columns; backfill changeset 037 idempotently inserts uppercase-`EMAIL` rows for every existing tenant; `DigestDeliveryUniqueConstraintTest` runs green; `NotificationPreferenceBackfillTest` runs green (asserts 3 tenants get 3 rows on first run, still 3 rows on second run); Postgres MCP `mcp__postgres__list_objects` (post-migration) shows the new tables + UNIQUE + indexes with the documented column shape and uppercase-`EMAIL` literal in the partial-index predicate.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Entity + repository + enum + service wiring for notification preferences and digest delivery (single-UUID-PK shape per Codex C1, uppercase EMAIL enum per Codex C2), plus mail_message_observed.sender_email field + REAL writer GmailDeliveryProcessingService metadata-header patch (Codex C4)</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/notification/package-info.java,
    backend/core/src/main/java/com/zeromail/core/notification/domain/ChannelType.java,
    backend/core/src/main/java/com/zeromail/core/notification/domain/DigestDeliveryStatus.java,
    backend/core/src/main/java/com/zeromail/core/notification/persistence/NotificationPreferenceEntity.java,
    backend/core/src/main/java/com/zeromail/core/notification/persistence/NotificationPreferenceRepository.java,
    backend/core/src/main/java/com/zeromail/core/notification/persistence/DigestDeliveryEntity.java,
    backend/core/src/main/java/com/zeromail/core/notification/persistence/DigestDeliveryRepository.java,
    backend/core/src/main/java/com/zeromail/core/notification/usecases/NotificationPreferenceService.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java,
    backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java,
    backend/core/src/test/java/com/zeromail/core/notification/persistence/NotificationPreferencePersistenceTest.java,
    backend/core/src/test/java/com/zeromail/core/gmail/GmailDeliveryProcessingSenderEmailTest.java
  </files>
  <read_first>
    backend/core/src/main/java/com/zeromail/core/shared/persistence/AbstractTenantOwnedEntity.java,
    backend/core/src/main/java/com/zeromail/core/shared/lang/IdentifiedEnum.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedId.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java,
    backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java,
    backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailDeliveryProcessingService.java,
    backend/core/src/main/java/com/zeromail/core/gmail/PubSubIngestionService.java,
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-CONTEXT.md (decisions D-04 D-14 D-25),
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-RESEARCH.md (§12 #4 ChannelType cardinality, §0 sender_email policy),
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-REVIEWS.md (Codex C1, C2, C4)
  </read_first>
  <behavior>
    - **REVIEW FIX (Codex C2)** — `ChannelType` is `enum implements IdentifiedEnum` with single member `EMAIL` whose `id()` returns `"EMAIL"` (uppercase, NOT `"email"`); `fromId(String)` fails loud on unknown id (`NoSuchElementException`); `name() == id()` invariant — JPA `@Enumerated(STRING)` therefore stores `EMAIL`, matching the Liquibase index predicate and 037 backfill `'EMAIL'` literal. No AttributeConverter needed.
    - `DigestDeliveryStatus` is `enum implements IdentifiedEnum` with three members `PENDING`, `SENT`, `FAILED` whose `id()` returns the uppercase string; `fromId` fail-loud (unchanged — already uppercase-consistent)
    - **REVIEW FIX (Codex C1)** — `NotificationPreferenceEntity` extends `AbstractTenantOwnedEntity` and inherits its single-column UUID `@Id` field; declares `@Table(name = "notification_preference", uniqueConstraints = @UniqueConstraint(name = "uq_notification_preference_tenant_channel", columnNames = {"tenant_id", "channel"}))`; fields `tenant_id UUID` (inherited via `@TenantId`), `channel ChannelType` (`@Enumerated(STRING)` + `@Column(name = "channel", nullable = false, length = 16)`), `digest_enabled boolean`, `digest_send_hour_local int`. **DO NOT** declare `@IdClass(NotificationPreferenceId.class)`; **DO NOT** create `NotificationPreferenceId`. Repository methods look up by `(tenantId, channel)` via a derived query: `Optional<NotificationPreferenceEntity> findByTenantIdAndChannel(UUID tenantId, ChannelType channel)`.
    - `DigestDeliveryEntity` extends `AbstractTenantOwnedEntity`, single-column UUID PK `id` (inherited), `@Table(name = "digest_delivery", uniqueConstraints = @UniqueConstraint(name = "uq_digest_delivery_tenant_day", columnNames = {"tenant_id", "digest_day_local"}))`, fields `digest_day_local LocalDate`, `status DigestDeliveryStatus` (@Enumerated STRING), `channel ChannelType` (@Enumerated STRING — value is `EMAIL`), `attempt_count int`, `next_attempt_at Instant` (nullable — for retry sequencing), `dispatched_at Instant`, `external_ref String` (nullable, varchar 255 — Resend `email_id` from `CreateEmailResponse.getId()`; consumed by Plan 03 `markSent`), `failure_reason String` (varchar 255)
    - `NotificationPreferenceService` (in `core.notification.usecases`) — `@Service`, owns `@Transactional`: methods `insertDefaults(UUID tenantId, ChannelType channel, boolean enabled, int sendHourLocal)` (called from D-17 host; uses `notificationPreferenceRepository.save(...)` after setting fields), `findByTenantAndChannel(UUID tenantId, ChannelType channel) → Optional<NotificationPreferenceEntity>`, `updatePreference(UUID tenantId, ChannelType channel, boolean enabled, int sendHourLocal) → NotificationPreferenceEntity` (loads existing row, applies, saves), `deleteForTenant(UUID tenantId)` (called from cascade); uses repository methods only, NOT another service's repo (Convention 2)
    - `MailMessageObservedEntity` gains `String senderEmail` field (nullable, `@Column(name = "sender_email", length = 320)`); JPA mapping for the new column added in Task 1's changeset 032
    - **REVIEW FIX (Codex C4)** — `GmailDeliveryProcessingService` (the REAL writer of `mail_message_observed`, NOT `PubSubIngestionService` which only ack's the push) is modified to:
      1. Issue the Gmail message GET with `format=METADATA` (NOT `MINIMAL`) and `metadataHeaders=Collections.singletonList("From")` — verify the Gmail Java SDK call shape via Context7 (`Gmail.Users.Messages.Get` builder method `.setFormat("metadata").setMetadataHeaders(List.of("From"))`).
      2. Extract `From` from `gmailMessage.getPayload().getHeaders()` (case-insensitive header lookup), sanitize through the existing Phase 2A sanitizer (locate via grep `SenderEmailSanitizer` or equivalent), and pass the sanitized value as a new parameter to `MailMessageObservedRepository.insertObservedIfAbsent(...)` — repository method signature changes to accept `String senderEmail` (nullable).
      3. If the `From` header is absent or unparseable, pass `null` (per §0 graceful-degrade — Plan 02 Q3 filters `WHERE sender_email IS NOT NULL`).
    - `MailMessageObservedRepository.insertObservedIfAbsent(...)` gains a `String senderEmail` parameter (nullable); existing callers updated to pass `null` (only `GmailDeliveryProcessingService` passes the real value).
    - Test (`NotificationPreferencePersistenceTest`): seeded tenant T, `insertDefaults(T, EMAIL, true, 20)` round-trips through Hibernate; the persisted row has `channel = 'EMAIL'` (verified via raw JdbcTemplate SELECT — asserts the uppercase storage); `findByTenantAndChannel(T, EMAIL)` returns the row with the documented field values; second call to `insertDefaults(T, EMAIL, ...)` for same (tenant, channel) raises `DataIntegrityViolationException` (UNIQUE constraint).
    - Test (`GmailDeliveryProcessingSenderEmailTest` — NEW for C4): Mockito-mock Gmail SDK + real Testcontainers Postgres. Case A: Gmail GET returns a payload with header `From: "Alice Example <alice@example.com>"` — call `processDelivery(...)` — assert the persisted `mail_message_observed.sender_email` equals the sanitized form (the Phase 2A sanitizer's output for that input; verify via existing sanitizer unit test). Case B: Gmail GET returns no `From` header — assert persisted `sender_email IS NULL`. Case C (boundary): mock asserts that the Gmail Get builder was called with `format=METADATA` and `metadataHeaders` containing exactly `"From"`.
    - Modulith `core.notification/package-info.java` declares `@ApplicationModule(displayName="Notification", allowedDependencies={"tenant", "shared.persistence", "shared.lang", "account"})` and `@NamedInterface("api")` on the `usecases` sub-package (Plan 03 will call `NotificationPreferenceService.deleteForTenant` from `core.account`; declaring `account` as a dependency keeps Modulith happy, OR declare an `@NamedInterface` so account can import the public API)
  </behavior>
  <action>Create the `core.notification` Modulith module with the package layout from CONTEXT §code_context and RESEARCH §"Recommended Project Structure" (`domain/`, `usecases/`, `persistence/`). **DO NOT** create `NotificationPreferenceId` — Codex C1 removed it. The entity uses the single-UUID `id` PK inherited from `AbstractTenantOwnedEntity` plus a class-level `@UniqueConstraint`. `ChannelType` and `DigestDeliveryStatus` follow `OnboardingStep` / `GmailConnectionStatus` as the IdentifiedEnum precedent. `NotificationPreferenceService` is the canonical write-side service for both `insertDefaults` (D-17 host call) and `updatePreference` (Plan 03 PATCH endpoint will call). Field naming MUST use enterprise-readable names (`notificationPreferenceRepository` not `npRepo`, `digestSendHourLocal` not `hr`). For Codex C4: locate `GmailDeliveryProcessingService` via grep `mail_message_observed` and `insertObservedIfAbsent` — confirm it owns the metadata GET call. The current code likely uses `format=MINIMAL` or omits the param; change to `format=METADATA` with `metadataHeaders=List.of("From")`. Verify Gmail Java SDK 2.x Get-builder API via Context7 (`mcp__context7__resolve-library-id` then `query-docs` "Gmail Java SDK Users.Messages.Get setFormat setMetadataHeaders"). Use the existing Phase 2A sender sanitizer (do NOT duplicate sanitization logic — grep for it; expected location is `core.gmail.*Sanitizer*` or similar). After meaningful Java edits run `mcp__jetbrains__get_file_problems` on every touched file. Implements D-04 + D-14 + §0 (entity-side) + REVIEWS C1 + C2 + C4.</action>
  <verify>
    <automated>./gradlew :backend:core:test --tests NotificationPreferencePersistenceTest --tests DigestDeliveryUniqueConstraintTest --tests GmailDeliveryProcessingSenderEmailTest -x checkstyleMain -x spotlessCheck</automated>
  </verify>
  <done>
    `NotificationPreferenceEntity` uses single-UUID PK (no `@IdClass`); persisted `channel` value is `EMAIL` (uppercase, verified by raw JdbcTemplate SELECT in test); `DigestDeliveryEntity` carries `external_ref` + `next_attempt_at`; `NotificationPreferenceService` exposes `insertDefaults`, `findByTenantAndChannel`, `updatePreference`, `deleteForTenant`; `MailMessageObservedEntity.senderEmail` field is mapped; `GmailDeliveryProcessingService` issues Gmail GET with `format=METADATA` + `metadataHeaders=["From"]` and passes the sanitized From through; `NotificationPreferencePersistenceTest` and `GmailDeliveryProcessingSenderEmailTest` are green; `mcp__jetbrains__get_file_problems` on every touched Java file reports 0 errors; `./gradlew :backend:core:check` reports BUILD SUCCESSFUL on the touched scope.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: Wire OAuth provisioning defaults (D-17) + account-deletion cascade (D-16) + tenant time_zone field</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java,
    backend/core/src/main/java/com/zeromail/core/tenant/usecases/TenantService.java,
    backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java,
    backend/core/src/main/java/com/zeromail/core/account/usecases/AccountDeletionService.java,
    backend/core/src/test/java/com/zeromail/core/account/OAuthProvisioningDefaultsTest.java
  </files>
  <read_first>
    backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java,
    backend/core/src/main/java/com/zeromail/core/tenant/usecases/TenantService.java,
    backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java,
    backend/core/src/main/java/com/zeromail/core/account/usecases/AccountDeletionService.java,
    .planning/phases/01.5-inbox-zero-alignment-bundled-oauth-ux-polish-cleanup-sweep-r/01.5-CONTEXT.md (D-17 atomicity contract),
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-RESEARCH.md (§8 D-17 host snippet)
  </read_first>
  <behavior>
    - `TenantEntity` gains `String timeZone` field, `@Column(name = "time_zone", nullable = false, length = 64)`; getter + (package-private or builder-style) setter consistent with the entity's existing convention
    - `TenantService` exposes `setTimeZoneIfAbsent(UUID tenantId, String ianaZone)` — reads the tenant, sets time_zone only if currently equal to the default (`Asia/Ho_Chi_Minh`) AND was just created in the same transaction (idempotency for re-login); method is `@Transactional` with default `PROPAGATION_REQUIRED` so it joins the host
    - `OAuthProvisioningService.provisionBundledOAuth` FIRST-LOGIN PATH adds two new calls INSIDE the existing `bundledTransaction.executeWithoutResult(...)` block, BEFORE the `gmailConnectionService.upsert(...)` line: (1) `tenantService.setTimeZoneIfAbsent(tenantId, "Asia/Ho_Chi_Minh")`, (2) `notificationPreferenceService.insertDefaults(tenantId, ChannelType.EMAIL, true, 20)`
    - The same-transaction guarantee MUST hold — both new calls inherit `PROPAGATION_REQUIRED` from Spring's default `@Transactional`; if either throws, the whole bundled transaction rolls back (verified by `OAuthProvisioningDefaultsTest`)
    - `AccountDeletionService` (or the equivalent `AccountDeletionController` bridge that Phase 01.2 P05 introduced — executor verifies path via grep) gains explicit cascade steps for `notification_preference` and `digest_delivery` BEFORE the `tenantService.deleteCurrentTenant()` call; uses `notificationPreferenceService.deleteForTenant(UUID)` + `digestDeliveryService.deleteForTenant(UUID)` (these delete methods land in the service classes here). FK `ON DELETE CASCADE` (from Task 1) is the safety net; explicit service-level cleanup is the primary path (matches Phase 01.2 P05 single-domain-delete convention)
    - Test (`OAuthProvisioningDefaultsTest`): mock or real Testcontainers; after `provisionBundledOAuth(newGoogleSubject, newEmail, ...)` returns, assert (a) one new `tenants` row with `time_zone = 'Asia/Ho_Chi_Minh'`, (b) one new `notification_preference` row `(tenant_id=newTenant, channel='email', digest_enabled=true, digest_send_hour_local=20)`, (c) when `notificationPreferenceService.insertDefaults` throws a synthetic exception, the entire transaction rolls back (no tenant row, no user row, no gmail_connection row)
  </behavior>
  <action>Read `OAuthProvisioningService.provisionBundledOAuth` carefully — the FIRST-LOGIN PATH is the only branch that needs the new wiring; the SUBSEQUENT-LOGIN PATH already has a tenant + preferences row from the original first-login transaction (idempotent re-login does not insert a second `notification_preference` row). Add the two new method calls in the documented location (D-17 host snippet in RESEARCH §8). Reuse the project's enterprise-readability naming throughout: `notificationPreferenceService` not `notificationPrefSvc`, `defaultSendHourLocal` not `defaultHour`. For the rollback-atomicity test, inject a `notificationPreferenceService` mock that throws `RuntimeException` on `insertDefaults` and assert all three other domains (user, tenant, gmail_connection) ended with zero rows for that subject — same shape as the Phase 01.5 HIGH-1 atomicity test. Implements D-16 + D-17. After Java edits run `mcp__jetbrains__get_file_problems` on touched files.</action>
  <verify>
    <automated>./gradlew :backend:core:test --tests OAuthProvisioningDefaultsTest -x checkstyleMain -x spotlessCheck</automated>
  </verify>
  <done>
    `OAuthProvisioningDefaultsTest` runs green covering the three assertions in the behavior block (defaults inserted, time_zone backfilled, atomicity rollback on synthetic failure); `AccountDeletionService` calls cascade methods on both new services BEFORE tenant delete; `mcp__jetbrains__get_file_problems` reports 0 errors on all touched files; `./gradlew :backend:core:check` BUILD SUCCESSFUL on the touched scope.
  </done>
</task>

</tasks>

<threat_model>

## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| client → API → DB | Already crossed and enforced by Phase 5A/5B; this plan adds no new HTTP surface, so no new client→API trust boundary |
| OAuth callback → bundledTransaction → DB | EXISTING (Phase 01.5); the new D-17 calls extend the atomic boundary — must preserve all-or-nothing semantics |
| Gmail API → GmailDeliveryProcessingService → mail_message_observed | EXISTING (Phase 2A) — this plan adds a `format=METADATA` + `metadataHeaders=["From"]` step at the REAL writer (Codex C4); the sender_email pass-through MUST NOT introduce a path for unsanitized From to reach the DB |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05C-01 | Information disclosure | `mail_message_observed.sender_email` writes from `GmailDeliveryProcessingService` (the REAL writer — REVIEW FIX Codex C4) | mitigate | Pass ONLY the already-sanitized From the existing Phase 2A sender-sanitizer computes; the Gmail GET is performed with `format=METADATA` + `metadataHeaders=["From"]` (no body/subject leaks into the worker JVM beyond what is needed); never log the value — privacy logging is enforced in Plan 02's `AnalyticsPrivacySweepTest`. ArchUnit no-content-ban test (in Plan 02) verifies analytics service does not read other body-bearing columns |
| T-05C-02 | Tampering | OAuth provisioning atomicity (D-17) | mitigate | New service calls land INSIDE the existing `bundledTransaction.executeWithoutResult` block; `OAuthProvisioningDefaultsTest` asserts all-or-nothing rollback when synthetic failure is injected at `notificationPreferenceService.insertDefaults` |
| T-05C-03 | Information disclosure | Account deletion residue (D-16) | mitigate | DB-level `ON DELETE CASCADE` FK + service-level explicit delete in `AccountDeletionService` before `deleteCurrentTenant` — two-layer cleanup matching the Phase 01.2 P05 cascade convention |

</threat_model>

<verification>
- `./gradlew :backend:core:test --tests "DigestDeliveryUniqueConstraintTest" --tests "NotificationPreferenceBackfillTest" --tests "NotificationPreferencePersistenceTest" --tests "GmailDeliveryProcessingSenderEmailTest" --tests "OAuthProvisioningDefaultsTest"` exits 0
- `./gradlew :backend:core:check` BUILD SUCCESSFUL
- `mcp__postgres__list_objects` shows `notification_preference` (single UUID PK + UNIQUE(tenant_id, channel)) and `digest_delivery` (single UUID PK + UNIQUE(tenant_id, digest_day_local) + `external_ref` + `next_attempt_at`) tables with the documented column types + indexes
- `mcp__postgres__list_objects` confirms `mail_message_observed.sender_email` (varchar 320, nullable) and `tenants.time_zone` (varchar 64, NOT NULL, default `Asia/Ho_Chi_Minh`)
- `mcp__postgres__execute_sql "SELECT channel FROM notification_preference LIMIT 1"` returns uppercase `EMAIL` — proves Codex C2 fix (and Plan 03 scheduler's `channel = 'EMAIL'` predicate matches)
- `mcp__postgres__execute_sql "SELECT count(*) FROM notification_preference WHERE channel = 'EMAIL'"` equals the count of existing tenants — proves Codex C3 backfill ran
- `mcp__jetbrains__get_file_problems` on every touched Java file reports 0 errors
</verification>

<success_criteria>
- All 6 Liquibase changesets apply idempotently against an existing 031 database (including the 037 backfill)
- Every entity / repository / service / enum compiles and tests green
- `NotificationPreferenceEntity` uses single-UUID PK (no `@IdClass`) — Codex C1 fix verified
- Persisted `channel` value is uppercase `EMAIL` — Codex C2 fix verified by raw SQL assertion
- Existing tenants receive a `notification_preference` row via 037 backfill — Codex C3 fix verified
- `digest_delivery.external_ref` column exists for Plan 03 `markSent(..., externalId)` — review MEDIUM fix verified
- `OAuthProvisioningService.provisionBundledOAuth` extends the atomic transaction without breaking any existing test
- Account deletion cascade tests (existing Phase 01.2 ones + the new D-16 service cascade) remain green
- `mail_message_observed.sender_email` is populated for every newly-ingested message from this plan forward — extracted at `GmailDeliveryProcessingService` (the REAL writer per Codex C4) via Gmail `format=METADATA` + `metadataHeaders=["From"]`; pre-fix rows stay NULL and Plan 02 Q3 + the changeset-036 partial index both filter `WHERE sender_email IS NOT NULL`
</success_criteria>

<output>
After completion, create `.planning/phases/05C-user-surface-analytics-daily-digest/05C-01-SUMMARY.md` capturing:
- Which subpackages of `core.notification` were created
- Any deviation in Liquibase changeset shape from the documented behavior
- The exact `PubSubIngestionService` insertion point chosen (file + line)
- Whether `setTimeZoneIfAbsent` ended up needed (or replaced by a simpler `defaultValueComputed` reliance)
- `OAuthProvisioningDefaultsTest` runtime + assertions made
</output>
