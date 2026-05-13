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
  - backend/core/src/main/resources/db/changelog/master.yaml
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java
  - backend/core/src/main/java/com/zeromail/core/gmail/PubSubIngestionService.java
  - backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java
  - backend/core/src/main/java/com/zeromail/core/tenant/usecases/TenantService.java
  - backend/core/src/main/java/com/zeromail/core/notification/package-info.java
  - backend/core/src/main/java/com/zeromail/core/notification/domain/ChannelType.java
  - backend/core/src/main/java/com/zeromail/core/notification/domain/DigestDeliveryStatus.java
  - backend/core/src/main/java/com/zeromail/core/notification/persistence/NotificationPreferenceEntity.java
  - backend/core/src/main/java/com/zeromail/core/notification/persistence/NotificationPreferenceId.java
  - backend/core/src/main/java/com/zeromail/core/notification/persistence/NotificationPreferenceRepository.java
  - backend/core/src/main/java/com/zeromail/core/notification/persistence/DigestDeliveryEntity.java
  - backend/core/src/main/java/com/zeromail/core/notification/persistence/DigestDeliveryRepository.java
  - backend/core/src/main/java/com/zeromail/core/notification/usecases/NotificationPreferenceService.java
  - backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java
  - backend/core/src/main/java/com/zeromail/core/account/usecases/AccountDeletionService.java
  - backend/core/src/test/java/com/zeromail/core/notification/persistence/NotificationPreferencePersistenceTest.java
  - backend/core/src/test/java/com/zeromail/core/notification/persistence/DigestDeliveryUniqueConstraintTest.java
  - backend/core/src/test/java/com/zeromail/core/account/OAuthProvisioningDefaultsTest.java
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
    - "Schema migrations land idempotently — five new Liquibase changesets apply cleanly from scratch and on top of an existing 031 database"
    - "Every existing tenant ends up with `tenants.time_zone='Asia/Ho_Chi_Minh'` after migration 033 runs"
    - "Every newly provisioned tenant (post OAuth provisioning) has exactly one `notification_preference (tenant_id, 'email', true, 20)` row"
    - "`digest_delivery` UNIQUE constraint rejects a second insert for the same `(tenant_id, digest_day_local)` with SQLState 23505"
    - "`mail_message_observed` rows written by ingestion after this plan carry a non-null `sender_email` when Gmail returns a From header"
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
      provides: "digest_delivery table + UNIQUE(tenant_id, digest_day_local)"
    - path: "backend/core/src/main/resources/db/changelog/changes/036-analytics-supporting-indexes.yaml"
      provides: "Btree indexes for Q1 (mail_message_observed tenant_id, observed_at), Q3 (tenant_id, sender_email, observed_at), Q4 (triage_audit tenant_id, rule_name_snapshot, decided_at)"
    - path: "backend/core/src/main/java/com/zeromail/core/notification/persistence/NotificationPreferenceEntity.java"
      provides: "JPA entity for per-tenant per-channel preference"
    - path: "backend/core/src/main/java/com/zeromail/core/notification/persistence/DigestDeliveryEntity.java"
      provides: "JPA entity for idempotency-and-status rows"
  key_links:
    - from: "backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java"
      to: "core.notification.usecases.NotificationPreferenceService"
      via: "insertDefaults(tenantId, ChannelType.EMAIL, true, 20) inside bundledTransaction"
      pattern: "notificationPreferenceService.insertDefaults"
    - from: "backend/core/src/main/java/com/zeromail/core/gmail/PubSubIngestionService.java"
      to: "mail_message_observed.sender_email column"
      via: "sanitized From header passed through observed-row insert"
      pattern: "senderEmail"
---

<objective>
Ship the Wave 1 schema + entities + defaults wiring that the analytics endpoint (Plan 02), digest dispatcher (Plan 03), and frontend (Plan 04) all build on. This plan is the foundation of Phase 5C: it resolves the §0 SHOW-STOPPER (`mail_message_observed.sender_email` missing — added per CONTEXT.md and RESEARCH.md §0); adds `tenants.time_zone`, `notification_preference`, and `digest_delivery` (D-14, D-15); creates JPA entities + the `NotificationPreferenceService`; extends `OAuthProvisioningService` to seed defaults inside the existing `PROPAGATION_REQUIRED` transaction (D-17); and wires account-deletion cascades (D-16). No HTTP endpoints, no scheduler, no UI in this plan — those land in Plans 02–04.

Purpose: every Plan 02 task assumes these columns + entities exist; every Plan 03 task assumes the `digest_delivery` UNIQUE + the channel + status enums exist. This is the depends_on root for the rest of the phase.

Output: 5 Liquibase changesets, 9 Java source files (entities, repositories, service, package-info), 3 modifications to existing services (`PubSubIngestionService`, `OAuthProvisioningService`, `AccountDeletionService` / equivalent cascade host), 3 new Testcontainers-backed test classes.
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

`AbstractTenantOwnedEntity` (read this file): provides tenant_id (auto-populated via @TenantId), created_at, updated_at, version (auditing). NotificationPreferenceEntity + DigestDeliveryEntity MUST extend this.

`IdentifiedEnum` contract: `String id()` + static `fromId(String)` fail-loud (`NoSuchElementException`); persisted via `@Enumerated(EnumType.STRING)` with id()==name() invariant. ChannelType id = "email" (v1 single value, per D-04 and RESEARCH §12 #4). DigestDeliveryStatus ids = "PENDING", "SENT", "FAILED".

Liquibase sequence: last existing changeset is `031-thread-reply-status-resolved-index.yaml`. This plan adds 032, 033, 034, 035, 036 in `backend/core/src/main/resources/db/changelog/changes/` and adds the includes to `master.yaml`.

`OAuthProvisioningService.provisionBundledOAuth` host transaction: opens `bundledTransaction.executeWithoutResult(...)` under a `ScopedValue.where(TenantContext.TENANT, ...)`. The two new method calls (`tenantService.setTimeZoneIfAbsent` and `notificationPreferenceService.insertDefaults`) must land INSIDE that block, BEFORE `gmailConnectionService.upsert(...)`. Both methods inherit `PROPAGATION_REQUIRED` automatically.
</interfaces>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Liquibase migration wave — sender_email column + time_zone + notification_preference + digest_delivery + analytics indexes</name>
  <files>
    backend/core/src/main/resources/db/changelog/changes/032-mail-message-observed-sender-email.yaml,
    backend/core/src/main/resources/db/changelog/changes/033-tenants-time-zone.yaml,
    backend/core/src/main/resources/db/changelog/changes/034-notification-preference.yaml,
    backend/core/src/main/resources/db/changelog/changes/035-digest-delivery.yaml,
    backend/core/src/main/resources/db/changelog/changes/036-analytics-supporting-indexes.yaml,
    backend/core/src/main/resources/db/changelog/master.yaml
  </files>
  <read_first>
    backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml,
    backend/core/src/main/resources/db/changelog/changes/025-triage-audit.yaml,
    backend/core/src/main/resources/db/changelog/changes/031-thread-reply-status-resolved-index.yaml,
    backend/core/src/main/resources/db/changelog/master.yaml,
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-RESEARCH.md (§0 schema fix and §5 index recommendations and §8 changeset numbering)
  </read_first>
  <behavior>
    - Changeset 032: `addColumn` `sender_email varchar(320)` NULLABLE to `mail_message_observed`; no NOT NULL promotion in this phase (per §0 — pre-fix rows must remain valid)
    - Changeset 033: `addColumn` `time_zone varchar(64)` to `tenants` with `defaultValueComputed: 'Asia/Ho_Chi_Minh'` and NOT NULL; existing rows backfilled by the default
    - Changeset 034: create `notification_preference` table — columns `tenant_id uuid NOT NULL`, `channel varchar(16) NOT NULL`, `digest_enabled boolean NOT NULL DEFAULT true`, `digest_send_hour_local int NOT NULL DEFAULT 20`, `created_at`, `updated_at`, `version int`; composite PK `(tenant_id, channel)`; FK `tenant_id → tenants(id) ON DELETE CASCADE`; CHECK `digest_send_hour_local BETWEEN 0 AND 23`; partial btree index `idx_notification_preference_due` on `(tenant_id)` `WHERE digest_enabled = true AND channel = 'email'`
    - Changeset 035: create `digest_delivery` table — columns `id uuid PRIMARY KEY`, `tenant_id uuid NOT NULL`, `digest_day_local date NOT NULL`, `status varchar(16) NOT NULL DEFAULT 'PENDING'`, `channel varchar(16) NOT NULL`, `attempt_count int NOT NULL DEFAULT 1`, `dispatched_at timestamptz`, `failure_reason varchar(255)`, `created_at`, `updated_at`, `version int`; UNIQUE `(tenant_id, digest_day_local)`; FK `tenant_id → tenants(id) ON DELETE CASCADE`; supporting btree on `(status, created_at)` for the reaper
    - Changeset 036: three new btree indexes — `idx_mail_message_observed_tenant_observed_at (tenant_id, observed_at)`, `idx_mail_message_observed_tenant_sender_observed (tenant_id, sender_email, observed_at)`, `idx_triage_audit_tenant_rule_decided (tenant_id, rule_name_snapshot, decided_at)`
    - `master.yaml`: register all 5 changesets in numeric order at the end of the existing `<include>` list
    - Test 1 (DigestDeliveryUniqueConstraintTest): seeded fixture inserts row for tenant T, day D; second insert with same (T, D) raises `DataIntegrityViolationException` SQLState 23505
    - Test 2: `./gradlew :backend:core:liquibaseUpdate` (or the equivalent test-container migrate) applies 032–036 with zero errors
  </behavior>
  <action>Create 5 new YAML changesets under `backend/core/src/main/resources/db/changelog/changes/` strictly following the existing YAML style (`databaseChangeLog` → `changeSet` with `id` + `author: zero-mail`; use `addColumn`, `createTable`, `addUniqueConstraint`, `createIndex` blocks; index names follow `idx_<table>_<columns>` convention). Append `<include file="changes/032-mail-message-observed-sender-email.yaml"/>` (and the four others, in numeric order) to `backend/core/src/main/resources/db/changelog/master.yaml`. ALL changesets are forward-only — no rollback blocks unless an existing changeset in this repo has one (verify the convention from changesets 012/025/031). The `sender_email` column on `mail_message_observed` stays NULLABLE forever (per §0 — pre-fix rows must remain valid and Q3 will `WHERE sender_email IS NOT NULL`). Use the existing index-naming and column-naming conventions read from changesets 012/025/031. After writing, run `./gradlew :backend:core:check --tests DigestDeliveryUniqueConstraintTest` to ensure the seeded UNIQUE-constraint test passes — that test is created in this same task as `backend/core/src/test/java/com/zeromail/core/notification/persistence/DigestDeliveryUniqueConstraintTest.java` extending `PostgresContainerTest`, inserting via raw JdbcTemplate, asserting `DataIntegrityViolationException` on the second insert. Implements D-15 + D-20 + §0 fix.</action>
  <verify>
    <automated>./gradlew :backend:core:test --tests DigestDeliveryUniqueConstraintTest -x checkstyleMain -x spotlessCheck</automated>
  </verify>
  <done>
    All 5 YAML changesets exist under `backend/core/src/main/resources/db/changelog/changes/`; `master.yaml` includes them in 032..036 order; the new `DigestDeliveryUniqueConstraintTest` runs green (proves UNIQUE constraint fires on duplicate (tenant, day)); Postgres MCP `mcp__postgres__list_objects` (post-migration) shows the new tables + indexes with the documented column shape.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Entity + repository + enum + service wiring for notification preferences and digest delivery, plus mail_message_observed.sender_email field + ingestion-adapter patch</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/notification/package-info.java,
    backend/core/src/main/java/com/zeromail/core/notification/domain/ChannelType.java,
    backend/core/src/main/java/com/zeromail/core/notification/domain/DigestDeliveryStatus.java,
    backend/core/src/main/java/com/zeromail/core/notification/persistence/NotificationPreferenceEntity.java,
    backend/core/src/main/java/com/zeromail/core/notification/persistence/NotificationPreferenceId.java,
    backend/core/src/main/java/com/zeromail/core/notification/persistence/NotificationPreferenceRepository.java,
    backend/core/src/main/java/com/zeromail/core/notification/persistence/DigestDeliveryEntity.java,
    backend/core/src/main/java/com/zeromail/core/notification/persistence/DigestDeliveryRepository.java,
    backend/core/src/main/java/com/zeromail/core/notification/usecases/NotificationPreferenceService.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java,
    backend/core/src/main/java/com/zeromail/core/gmail/PubSubIngestionService.java,
    backend/core/src/test/java/com/zeromail/core/notification/persistence/NotificationPreferencePersistenceTest.java
  </files>
  <read_first>
    backend/core/src/main/java/com/zeromail/core/shared/persistence/AbstractTenantOwnedEntity.java,
    backend/core/src/main/java/com/zeromail/core/shared/lang/IdentifiedEnum.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedId.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java,
    backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java,
    backend/core/src/main/java/com/zeromail/core/gmail/PubSubIngestionService.java,
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-CONTEXT.md (decisions D-04 D-14 D-25),
    .planning/phases/05C-user-surface-analytics-daily-digest/05C-RESEARCH.md (§12 #4 ChannelType cardinality, §0 sender_email policy)
  </read_first>
  <behavior>
    - `ChannelType` is `enum implements IdentifiedEnum` with single member `EMAIL` whose `id()` returns `"email"`; `fromId(String)` fails loud on unknown id (`NoSuchElementException`); name()==id().toUpperCase() invariant
    - `DigestDeliveryStatus` is `enum implements IdentifiedEnum` with three members `PENDING`, `SENT`, `FAILED` whose `id()` returns the uppercase string; `fromId` fail-loud
    - `NotificationPreferenceEntity` extends `AbstractTenantOwnedEntity`, mapped via `@IdClass(NotificationPreferenceId.class)` (mirrors `MailMessageObservedId` precedent); fields `tenant_id UUID` (inherited), `channel ChannelType` (@Enumerated STRING), `digest_enabled boolean`, `digest_send_hour_local int`
    - `DigestDeliveryEntity` extends `AbstractTenantOwnedEntity`, single-column UUID PK `id`, fields `digest_day_local LocalDate`, `status DigestDeliveryStatus` (@Enumerated STRING), `channel ChannelType` (@Enumerated STRING), `attempt_count int`, `dispatched_at Instant`, `failure_reason String` (varchar 255)
    - `NotificationPreferenceService` (in `core.notification.usecases`) — `@Service`, owns `@Transactional`: methods `insertDefaults(UUID tenantId, ChannelType channel, boolean enabled, int sendHourLocal)` (called from D-17 host), `findByTenantAndChannel(UUID tenantId, ChannelType channel) → Optional<NotificationPreferenceEntity>`, `updatePreference(UUID tenantId, ChannelType channel, boolean enabled, int sendHourLocal)` — uses repository methods, NOT another service's repo (Convention 2)
    - `MailMessageObservedEntity` gains `String senderEmail` field (nullable, @Column name "sender_email", length 320); JPA mapping for the new column added in Task 1
    - `PubSubIngestionService` (or the actual Gmail ingestion writer that inserts `mail_message_observed` rows — executor verifies via grep `INSERT INTO mail_message_observed` or via the entity save call site) passes the sanitized `From` header through to the new column when present; null when not parseable
    - Test (`NotificationPreferencePersistenceTest`): seeded tenant T, `insertDefaults(T, EMAIL, true, 20)` round-trips through Hibernate; second call for same (T, EMAIL) — service-level upsert OR documented exception; `findByTenantAndChannel(T, EMAIL)` returns the row with the documented field values
    - Modulith `core.notification/package-info.java` declares `@ApplicationModule(displayName="Notification", allowedDependencies={"tenant", "shared.persistence", "shared.lang"})` and `@NamedInterface("api")` on the `usecases` sub-package only if cross-module callers need it (defer NamedInterface until Plan 02/03 requests it)
  </behavior>
  <action>Create the `core.notification` Modulith module with the package layout from CONTEXT §code_context and RESEARCH §"Recommended Project Structure" (`domain/`, `usecases/`, `persistence/`). Follow `MailMessageObservedEntity`/`MailMessageObservedId` as the @IdClass + composite PK precedent. `ChannelType` and `DigestDeliveryStatus` follow `OnboardingStep` / `GmailConnectionStatus` as the IdentifiedEnum precedent (D-B5 unordered; pure interface implementation, no weight). `NotificationPreferenceService` is the canonical write-side service for both `insertDefaults` (D-17 host call) and `updatePreference` (Plan 02 PATCH endpoint will call). Field naming MUST use enterprise-readable names (`notificationPreferenceRepository` not `npRepo`, `digestSendHourLocal` not `hr`). Add `String senderEmail` to `MailMessageObservedEntity` and locate the ingestion writer — grep for `MailMessageObservedRepository.save` or `INSERT INTO mail_message_observed` and pass through the sanitized From header; null if Gmail returned no From (per §0 graceful-degrade). After meaningful Java edits run `mcp__jetbrains__get_file_problems` on every touched file. Implements D-04 + D-14 + §0 (entity-side).</action>
  <verify>
    <automated>./gradlew :backend:core:test --tests NotificationPreferencePersistenceTest --tests DigestDeliveryUniqueConstraintTest -x checkstyleMain -x spotlessCheck</automated>
  </verify>
  <done>
    `NotificationPreferenceEntity` + `DigestDeliveryEntity` + `ChannelType` + `DigestDeliveryStatus` + `NotificationPreferenceService` + repositories all compile; `MailMessageObservedEntity.senderEmail` field is mapped to the new column; `PubSubIngestionService` writes the sanitized From; `NotificationPreferencePersistenceTest` is green (round-trip insert + read); `mcp__jetbrains__get_file_problems` on every touched Java file reports 0 errors; `./gradlew :backend:core:check` reports BUILD SUCCESSFUL on the touched scope.
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
| ingestion adapter → mail_message_observed | EXISTING (Phase 2A); the sender_email pass-through MUST NOT introduce a path for unsanitized From to reach the DB |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-05C-01 | Information disclosure | `mail_message_observed.sender_email` writes from `PubSubIngestionService` | mitigate | Pass ONLY the already-sanitized From the existing Gmail ingestion adapter computes (Phase 2A already sanitizes for display elsewhere); never log the value — privacy logging is enforced in Plan 02's `AnalyticsPrivacySweepTest`. ArchUnit no-content-ban test (in Plan 02) verifies analytics service does not read other body-bearing columns |
| T-05C-02 | Tampering | OAuth provisioning atomicity (D-17) | mitigate | New service calls land INSIDE the existing `bundledTransaction.executeWithoutResult` block; `OAuthProvisioningDefaultsTest` asserts all-or-nothing rollback when synthetic failure is injected at `notificationPreferenceService.insertDefaults` |
| T-05C-03 | Information disclosure | Account deletion residue (D-16) | mitigate | DB-level `ON DELETE CASCADE` FK + service-level explicit delete in `AccountDeletionService` before `deleteCurrentTenant` — two-layer cleanup matching the Phase 01.2 P05 cascade convention |

</threat_model>

<verification>
- `./gradlew :backend:core:test --tests "DigestDeliveryUniqueConstraintTest" --tests "NotificationPreferencePersistenceTest" --tests "OAuthProvisioningDefaultsTest"` exits 0
- `./gradlew :backend:core:check` BUILD SUCCESSFUL
- `mcp__postgres__list_objects` shows `notification_preference`, `digest_delivery` tables with the documented column types + indexes
- `mcp__postgres__list_objects` confirms `mail_message_observed.sender_email` (varchar 320, nullable) and `tenants.time_zone` (varchar 64, NOT NULL, default `Asia/Ho_Chi_Minh`)
- `mcp__jetbrains__get_file_problems` on every touched Java file reports 0 errors
</verification>

<success_criteria>
- All 5 Liquibase changesets apply idempotently against an existing 031 database
- Every entity / repository / service / enum compiles and tests green
- `OAuthProvisioningService.provisionBundledOAuth` extends the atomic transaction without breaking any existing test
- Account deletion cascade tests (existing Phase 01.2 ones + the new D-16 service cascade) remain green
- `mail_message_observed.sender_email` is populated for every newly-ingested message from this plan forward (verified by post-deploy spot-check; pre-fix rows stay NULL and Plan 02 Q3 will `WHERE sender_email IS NOT NULL`)
</success_criteria>

<output>
After completion, create `.planning/phases/05C-user-surface-analytics-daily-digest/05C-01-SUMMARY.md` capturing:
- Which subpackages of `core.notification` were created
- Any deviation in Liquibase changeset shape from the documented behavior
- The exact `PubSubIngestionService` insertion point chosen (file + line)
- Whether `setTimeZoneIfAbsent` ended up needed (or replaced by a simpler `defaultValueComputed` reliance)
- `OAuthProvisioningDefaultsTest` runtime + assertions made
</output>
