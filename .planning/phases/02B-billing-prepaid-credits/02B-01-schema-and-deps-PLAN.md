---
phase: 02B
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - backend/core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml
  - backend/core/src/main/resources/db/changelog/changes/015-credit-reservation.yaml
  - backend/core/src/main/resources/db/changelog/changes/016-billing-topup-intent.yaml
  - backend/core/src/main/resources/db/changelog/changes/017-shedlock-table.yaml
  - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
  - gradle/libs.versions.toml
  - backend/worker/build.gradle.kts
autonomous: true
requirements: [BILL-01, BILL-02, BILL-03, BILL-04]
must_haves:
  truths:
    - "`./gradlew :backend:core:test --tests '*PostgresContainerTest*'` boots Postgres + applies all 17 changesets cleanly (014/015/016/017 included)."
    - "`credit_ledger_entry` table exists with UNIQUE `(ref_type, ref_id, kind)` + BRIN(`created_at`) + B-tree(`tenant_id, created_at`)."
    - "`credit_reservation` table exists with partial index on `created_at WHERE status = 'PENDING'` + B-tree(`tenant_id, status`)."
    - "`billing_topup_intent` table exists with UNIQUE(`code`) + partial UNIQUE(`sepay_transaction_id`) WHERE NOT NULL + B-tree(`status, expires_at`)."
    - "`shedlock` table exists with PK on `name`."
    - "`gradle/libs.versions.toml` declares `shedlock = \"7.7.0\"` plus the two library entries."
    - "`backend/worker/build.gradle.kts` declares both ShedLock implementations."
  artifacts:
    - path: "backend/core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml"
      provides: "Append-only journal: credit_ledger_entry table + UNIQUE(ref_type, ref_id, kind) + BRIN(created_at) + B-tree(tenant_id, created_at) + B-tree(tenant_id, ref_type, ref_id)."
    - path: "backend/core/src/main/resources/db/changelog/changes/015-credit-reservation.yaml"
      provides: "Sidecar reservation table: credit_reservation + partial index on created_at WHERE status='PENDING' + B-tree(tenant_id, status)."
    - path: "backend/core/src/main/resources/db/changelog/changes/016-billing-topup-intent.yaml"
      provides: "Top-up intent table: billing_topup_intent + UNIQUE(code) + partial UNIQUE(sepay_transaction_id) + B-tree(status, expires_at)."
    - path: "backend/core/src/main/resources/db/changelog/changes/017-shedlock-table.yaml"
      provides: "ShedLock standard DDL: name PK + lock_until + locked_at + locked_by."
    - path: "gradle/libs.versions.toml"
      provides: "shedlock 7.7.0 version pin + shedlock-spring + shedlock-provider-jdbc-template library entries."
    - path: "backend/worker/build.gradle.kts"
      provides: "ShedLock dependencies wired into worker module classpath."
  key_links:
    - from: "db.changelog-master.yaml"
      to: "014/015/016/017 changesets"
      via: "<include file: changes/014-credit-ledger-entry.yaml>"
      pattern: "include file: changes/0(14|15|16|17)-"
---

<objective>
Land the four Liquibase changesets and the ShedLock dependency wiring required by every later wave. Schema is the foundation: Plan 03's `CreditLedgerService` cannot save entities without `credit_ledger_entry`; Plan 04's `SepayWebhookController` cannot resolve intents without `billing_topup_intent`; Plan 05's `CreditReserveWatchdog` cannot use `@SchedulerLock` without `shedlock` table + ShedLock dep.

Purpose: per CONTEXT D-H1, Phase 2B claims changesets 014–017 (BLOCKING for Phase 2C — 2C must renumber to 018+).

Output: 4 new YAML changesets, master include update, version-catalog entries, worker build.gradle.kts entries.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/phases/02B-billing-prepaid-credits/02B-SPEC.md
@.planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md
@.planning/phases/02B-billing-prepaid-credits/02B-RESEARCH.md
@.planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md
@CLAUDE.md
@backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
@backend/core/src/main/resources/db/changelog/changes/011-pubsub-delivery-table.yaml
@backend/core/src/main/resources/db/changelog/changes/013-tenants-triage-paused.yaml
@gradle/libs.versions.toml
@backend/worker/build.gradle.kts
</context>

<tasks>

<task type="auto">
  <name>Task 1: Liquibase changesets 014-016 (billing tables)</name>
  <files>
    backend/core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml,
    backend/core/src/main/resources/db/changelog/changes/015-credit-reservation.yaml,
    backend/core/src/main/resources/db/changelog/changes/016-billing-topup-intent.yaml
  </files>
  <read_first>
    - .planning/phases/02B-billing-prepaid-credits/02B-SPEC.md (Requirement 2 — exact column list for credit_ledger_entry)
    - .planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md (D-B1 — credit_reservation columns; D-C1 — billing_topup_intent columns)
    - .planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md (lines 446–525 — analog 011-pubsub-delivery-table.yaml + index syntax notes)
    - backend/core/src/main/resources/db/changelog/changes/011-pubsub-delivery-table.yaml (verbatim shape: createTable + addUniqueConstraint + createIndex + rollback dropTable)
    - backend/core/src/main/java/com/zeromail/core/shared/persistence/AbstractTenantOwnedEntity.java (audit columns inherited — `id`, `tenant_id`, `created_at`, `updated_at`, `version` MUST be declared in Liquibase too because Hibernate `ddl-auto=validate` checks them)
  </read_first>
  <action>
**File 1: `014-credit-ledger-entry.yaml`** (per SPEC R2 verbatim column set)
```yaml
databaseChangeLog:
  - changeSet:
      id: 014-credit-ledger-entry
      author: zeromail
      changes:
        - createTable:
            tableName: credit_ledger_entry
            columns:
              - column: { name: id,             type: uuid,         constraints: { primaryKey: true, nullable: false } }
              - column: { name: tenant_id,      type: uuid,         constraints: { nullable: false, foreignKeyName: fk_credit_ledger_entry_tenant, references: 'tenants(id)', deleteCascade: true } }
              - column: { name: kind,           type: varchar(16),  constraints: { nullable: false } }
              - column: { name: amount_credits, type: int,          constraints: { nullable: false } }
              - column: { name: ref_type,       type: varchar(32),  constraints: { nullable: false } }
              - column: { name: ref_id,         type: varchar(128), constraints: { nullable: false } }
              - column: { name: created_at,     type: timestamptz,  defaultValueComputed: now(), constraints: { nullable: false } }
              - column: { name: updated_at,     type: timestamptz,  defaultValueComputed: now(), constraints: { nullable: false } }
              - column: { name: version,        type: bigint,       defaultValueNumeric: 0, constraints: { nullable: false } }
        - addUniqueConstraint:
            tableName: credit_ledger_entry
            columnNames: ref_type, ref_id, kind
            constraintName: uq_credit_ledger_entry_ref_kind
        - addCheckConstraint:
            tableName: credit_ledger_entry
            constraintName: ck_credit_ledger_entry_kind
            checkCondition: "kind IN ('TOPUP','RESERVE','SETTLE','RELEASE')"
        - createIndex:
            tableName: credit_ledger_entry
            indexName: idx_credit_ledger_entry_tenant_created
            columns:
              - column: { name: tenant_id }
              - column: { name: created_at }
        - createIndex:
            tableName: credit_ledger_entry
            indexName: idx_credit_ledger_entry_tenant_ref
            columns:
              - column: { name: tenant_id }
              - column: { name: ref_type }
              - column: { name: ref_id }
        - sql:
            comment: BRIN index on created_at for append-only time-ordered scans (SPEC R2)
            sql: "CREATE INDEX idx_credit_ledger_entry_created_brin ON credit_ledger_entry USING BRIN (created_at)"
      rollback:
        - dropTable: { tableName: credit_ledger_entry }
```

Notes:
- The `addCheckConstraint` is per SPEC R2 (kind enum locked to 4 values).
- BRIN index uses raw `<sql>` change because Liquibase 5's `createIndex` does not natively support `USING BRIN` (PATTERNS.md line 523).
- `addCheckConstraint` MAY require Liquibase 5.x syntax variation — if `addCheckConstraint` fails, fall back to a `<sql>` change: `ALTER TABLE credit_ledger_entry ADD CONSTRAINT ck_credit_ledger_entry_kind CHECK (kind IN ('TOPUP','RESERVE','SETTLE','RELEASE'))`. Verify by running `./gradlew :backend:core:test --tests "*PostgresContainerTest*"` after writing.

**File 2: `015-credit-reservation.yaml`** (per CONTEXT D-B1)
```yaml
databaseChangeLog:
  - changeSet:
      id: 015-credit-reservation
      author: zeromail
      changes:
        - createTable:
            tableName: credit_reservation
            columns:
              - column: { name: id,             type: uuid,        constraints: { primaryKey: true, nullable: false } }
              - column: { name: tenant_id,      type: uuid,        constraints: { nullable: false, foreignKeyName: fk_credit_reservation_tenant, references: 'tenants(id)', deleteCascade: true } }
              - column: { name: amount_credits, type: int,         constraints: { nullable: false } }
              - column: { name: call_site,      type: varchar(16), constraints: { nullable: false } }
              - column: { name: status,         type: varchar(16), constraints: { nullable: false } }
              - column: { name: created_at,     type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
              - column: { name: updated_at,     type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
              - column: { name: finalized_at,   type: timestamptz, constraints: { nullable: true } }
              - column: { name: version,        type: bigint,      defaultValueNumeric: 0, constraints: { nullable: false } }
        - addCheckConstraint:
            tableName: credit_reservation
            constraintName: ck_credit_reservation_status
            checkCondition: "status IN ('PENDING','SETTLED','RELEASED')"
        - addCheckConstraint:
            tableName: credit_reservation
            constraintName: ck_credit_reservation_amount_positive
            checkCondition: "amount_credits > 0"
        - createIndex:
            tableName: credit_reservation
            indexName: idx_credit_reservation_tenant_status
            columns:
              - column: { name: tenant_id }
              - column: { name: status }
        - sql:
            comment: Partial index on PENDING reservations for watchdog stale-scan (CONTEXT D-B1 + D-B3)
            sql: "CREATE INDEX idx_credit_reservation_pending_created ON credit_reservation (created_at) WHERE status = 'PENDING'"
      rollback:
        - dropTable: { tableName: credit_reservation }
```

**File 3: `016-billing-topup-intent.yaml`** (per CONTEXT D-C1)
```yaml
databaseChangeLog:
  - changeSet:
      id: 016-billing-topup-intent
      author: zeromail
      changes:
        - createTable:
            tableName: billing_topup_intent
            columns:
              - column: { name: id,                    type: uuid,         constraints: { primaryKey: true, nullable: false } }
              - column: { name: tenant_id,             type: uuid,         constraints: { nullable: false, foreignKeyName: fk_billing_topup_intent_tenant, references: 'tenants(id)', deleteCascade: true } }
              - column: { name: code,                  type: varchar(16),  constraints: { nullable: false, unique: true, uniqueConstraintName: uq_billing_topup_intent_code } }
              - column: { name: amount_vnd,            type: bigint,       constraints: { nullable: false } }
              - column: { name: status,                type: varchar(16),  constraints: { nullable: false } }
              - column: { name: created_at,            type: timestamptz,  defaultValueComputed: now(), constraints: { nullable: false } }
              - column: { name: updated_at,            type: timestamptz,  defaultValueComputed: now(), constraints: { nullable: false } }
              - column: { name: expires_at,            type: timestamptz,  constraints: { nullable: false } }
              - column: { name: paid_at,               type: timestamptz,  constraints: { nullable: true } }
              - column: { name: sepay_transaction_id,  type: varchar(128), constraints: { nullable: true } }
              - column: { name: version,               type: bigint,       defaultValueNumeric: 0, constraints: { nullable: false } }
        - addCheckConstraint:
            tableName: billing_topup_intent
            constraintName: ck_billing_topup_intent_status
            checkCondition: "status IN ('PENDING','PAID','EXPIRED')"
        - addCheckConstraint:
            tableName: billing_topup_intent
            constraintName: ck_billing_topup_intent_amount_positive
            checkCondition: "amount_vnd > 0"
        - createIndex:
            tableName: billing_topup_intent
            indexName: idx_billing_topup_intent_status_expires
            columns:
              - column: { name: status }
              - column: { name: expires_at }
        - sql:
            comment: Partial UNIQUE on sepay_transaction_id (replay protection — only when set)
            sql: "CREATE UNIQUE INDEX uq_billing_topup_intent_sepay_tx ON billing_topup_intent (sepay_transaction_id) WHERE sepay_transaction_id IS NOT NULL"
      rollback:
        - dropTable: { tableName: billing_topup_intent }
```

After writing all three files, run `./gradlew :backend:core:test --tests "*PostgresContainerTest*"` to confirm the schema applies. If `addCheckConstraint` is rejected by Liquibase 5 schema, replace each instance with a sibling `<sql>` change using `ALTER TABLE … ADD CONSTRAINT … CHECK (…)`.
  </action>
  <verify>
    <automated>test -f backend/core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml; test -f backend/core/src/main/resources/db/changelog/changes/015-credit-reservation.yaml; test -f backend/core/src/main/resources/db/changelog/changes/016-billing-topup-intent.yaml; grep -q "tableName: credit_ledger_entry" backend/core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml; grep -q "ref_type, ref_id, kind" backend/core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml; grep -q "USING BRIN" backend/core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml; grep -q "WHERE status = 'PENDING'" backend/core/src/main/resources/db/changelog/changes/015-credit-reservation.yaml; grep -q "WHERE sepay_transaction_id IS NOT NULL" backend/core/src/main/resources/db/changelog/changes/016-billing-topup-intent.yaml</automated>
  </verify>
  <done>3 YAML files exist; UNIQUE(ref_type, ref_id, kind) declared on credit_ledger_entry; BRIN(created_at) declared via raw sql; partial index on credit_reservation(created_at) WHERE status='PENDING' declared; partial UNIQUE on billing_topup_intent.sepay_transaction_id declared; check constraints lock kind / status / positive amounts.</done>
</task>

<task type="auto">
  <name>Task 2: ShedLock changeset + master include + version catalog + worker build wiring</name>
  <files>
    backend/core/src/main/resources/db/changelog/changes/017-shedlock-table.yaml,
    backend/core/src/main/resources/db/changelog/db.changelog-master.yaml,
    gradle/libs.versions.toml,
    backend/worker/build.gradle.kts
  </files>
  <read_first>
    - .planning/phases/02B-billing-prepaid-credits/02B-RESEARCH.md (§"Standard Stack > Supporting" lines 192–210 — exact ShedLock 7.7.0 coordinates)
    - .planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md (lines 526–547 — ShedLock table DDL pattern from ShedLock 7.x docs)
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml (existing master file — append 4 new include lines in order)
    - gradle/libs.versions.toml (current state — add `shedlock` version + 2 library entries below the existing `[libraries]` block)
    - backend/worker/build.gradle.kts (current state — add 2 implementation lines)
  </read_first>
  <action>
**File 4: `017-shedlock-table.yaml`** (ShedLock 7.x standard DDL per net.javacrumbs.shedlock:shedlock-provider-jdbc-template README)
```yaml
databaseChangeLog:
  - changeSet:
      id: 017-shedlock-table
      author: zeromail
      changes:
        - createTable:
            tableName: shedlock
            columns:
              - column: { name: name,        type: varchar(64),  constraints: { primaryKey: true, nullable: false } }
              - column: { name: lock_until,  type: timestamptz,  constraints: { nullable: false } }
              - column: { name: locked_at,   type: timestamptz,  constraints: { nullable: false } }
              - column: { name: locked_by,   type: varchar(255), constraints: { nullable: false } }
      rollback:
        - dropTable: { tableName: shedlock }
```

**File 5: `db.changelog-master.yaml` modification** — append 4 new include lines in numeric order. The existing file uses a `<include file: changes/0NN-*.yaml>` pattern. Add (preserving any indentation matching prior entries):
```yaml
  - include: { file: changes/014-credit-ledger-entry.yaml, relativeToChangelogFile: true }
  - include: { file: changes/015-credit-reservation.yaml, relativeToChangelogFile: true }
  - include: { file: changes/016-billing-topup-intent.yaml, relativeToChangelogFile: true }
  - include: { file: changes/017-shedlock-table.yaml, relativeToChangelogFile: true }
```
(Read the master file FIRST to confirm existing include syntax — it may use `relativeToChangelogFile: true` inline; mirror exactly.)

**File 6: `gradle/libs.versions.toml` modification** — add to the `[versions]` block (after `jsoup = "1.22.2"`):
```toml
shedlock = "7.7.0"
```
And add to the `[libraries]` block (alphabetical placement near other Spring entries):
```toml
shedlock-spring = { module = "net.javacrumbs.shedlock:shedlock-spring", version.ref = "shedlock" }
shedlock-provider-jdbc-template = { module = "net.javacrumbs.shedlock:shedlock-provider-jdbc-template", version.ref = "shedlock" }
```

**File 7: `backend/worker/build.gradle.kts` modification** — add inside the existing `dependencies { ... }` block (alongside other `implementation(...)` calls):
```kotlin
implementation(libs.shedlock.spring)
implementation(libs.shedlock.provider.jdbc.template)
```
(Read the worker `build.gradle.kts` FIRST to confirm existing `dependencies { }` style and decide exact placement.)

After all 4 files are saved, run `./gradlew :backend:worker:dependencies --configuration runtimeClasspath 2>&1 | grep shedlock` to confirm Gradle resolved the new artifacts. Then run `./gradlew :backend:core:test --tests "*PostgresContainerTest*"` to confirm changesets 014–017 apply cleanly on Testcontainers Postgres.
  </action>
  <verify>
    <automated>test -f backend/core/src/main/resources/db/changelog/changes/017-shedlock-table.yaml; grep -q "tableName: shedlock" backend/core/src/main/resources/db/changelog/changes/017-shedlock-table.yaml; grep -q "014-credit-ledger-entry.yaml" backend/core/src/main/resources/db/changelog/db.changelog-master.yaml; grep -q "017-shedlock-table.yaml" backend/core/src/main/resources/db/changelog/db.changelog-master.yaml; grep -q 'shedlock = "7.7.0"' gradle/libs.versions.toml; grep -q "shedlock-spring" gradle/libs.versions.toml; grep -q "shedlock.spring" backend/worker/build.gradle.kts; ./gradlew :backend:core:test --tests "*PostgresContainerTest*" -i 2>&1 | grep -E "BUILD SUCCESSFUL|ChangeSet.*014-credit-ledger-entry"</automated>
  </verify>
  <done>017-shedlock-table.yaml exists with the 4-column ShedLock DDL; master changelog includes all 4 new entries in numeric order; libs.versions.toml has `shedlock = "7.7.0"` + 2 library entries; backend/worker/build.gradle.kts has both ShedLock implementation lines; Testcontainers Postgres applies all 17 changesets cleanly (BUILD SUCCESSFUL on `*PostgresContainerTest*`).</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Liquibase migration source → Production DB | Schema-defining migrations are append-only; rollback paths exist for every changeset. |
| Build-script dep declaration → Worker module classpath | New 3rd-party dep (ShedLock) introduced — verified version pin and source repository in libs.versions.toml. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-02B-01-01 | Tampering | Liquibase changesets | mitigate | Each changeset declares an explicit `rollback: dropTable` so a bad apply on prod can be reversed; `addCheckConstraint` locks `kind` + `status` enum membership at the DB level (defense-in-depth alongside ArchUnit `CallSiteEnumMembershipArchTest`). |
| T-02B-01-02 | Denial of service | credit_ledger_entry table size | accept | BRIN(created_at) keeps append-only scans cheap; partial-index on credit_reservation(WHERE status='PENDING') keeps watchdog scan O(stale-only). v1 traffic well below where these matter. |
| T-02B-01-03 | Tampering | Multi-tenant FK | mitigate | All three new tables declare `foreignKeyName` + `deleteCascade: true` to `tenants(id)` — tenant deletion (AUTH-03) automatically prunes ledger; no orphaned rows possible. |
| T-02B-01-04 | Information disclosure | ShedLock table content | accept | `locked_by` (host name + thread) is operator-visible only; no PII / tenant data flows through ShedLock. |
| T-02B-01-05 | Repudiation | credit_ledger_entry append-only | mitigate | UNIQUE(ref_type, ref_id, kind) blocks any duplicate insert; combined with default `version=0` + Hibernate `@Version` (added in Plan 03) gives optimistic-lock audit trail. |
</threat_model>

<verification>
- All 4 new YAML changesets present at the declared paths.
- Master changelog `db.changelog-master.yaml` includes 014, 015, 016, 017 in numeric order.
- `gradle/libs.versions.toml` has `shedlock = "7.7.0"` + `shedlock-spring` + `shedlock-provider-jdbc-template`.
- `backend/worker/build.gradle.kts` has `implementation(libs.shedlock.spring)` and `implementation(libs.shedlock.provider.jdbc.template)`.
- `./gradlew :backend:core:test --tests "*PostgresContainerTest*"` BUILD SUCCESSFUL — all 17 changesets apply cleanly on a fresh Testcontainers Postgres.
- `./gradlew :backend:worker:dependencies --configuration runtimeClasspath | grep shedlock` shows both shedlock artifacts resolved.
</verification>

<success_criteria>
- 4 YAML changesets land; 1 master include update; 1 catalog update; 1 worker build update.
- Testcontainers Postgres boots and applies all 17 changesets (no Liquibase apply errors).
- Worker module resolves both ShedLock 7.7.0 artifacts at compile time.
- ApiPostgresTestBase + PostgresContainerTest still GREEN — no regression of prior changesets.
</success_criteria>

<output>
After completion, create `.planning/phases/02B-billing-prepaid-credits/02B-01-SUMMARY.md`.
</output>
