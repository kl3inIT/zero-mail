---
phase: 02A-mail-ingestion
plan: "01"
type: execute
wave: 1
depends_on:
  - "02A-00"
files_modified:
  - gradle/libs.versions.toml
  - backend/core/build.gradle.kts
  - backend/api/build.gradle.kts
  - backend/core/src/main/resources/db/changelog/changes/010-gmail-ingestion-state.yaml
  - backend/core/src/main/resources/db/changelog/changes/011-pubsub-delivery-table.yaml
  - backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml
  - backend/core/src/main/resources/db/changelog/changes/013-tenants-triage-paused.yaml
  - backend/core/src/main/java/com/zeromail/core/gmail/model/GmailIngestionHealth.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntity.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java
  - backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java
  - backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java
autonomous: true
requirements:
  - MAIL-01
  - MAIL-02
  - MAIL-03
  - MAIL-04
  - MAIL-06

must_haves:
  truths:
    - "Liquibase applies changesets 010-013 cleanly on a fresh schema without errors"
    - "GmailConnectionEntity has all 6 new fields (lastSyncedHistoryId, watchHistoryId, watchExpiresAt, watchRenewedAt, watchConsecutiveFailures, ingestionHealth)"
    - "PubSubDeliveryEntity persists to pubsub_delivery with UNIQUE(tenant_id, pubsub_message_id), non-null updated_at, and non-null version inherited from AbstractAuditableEntity"
    - "MailMessageObservedRepository exposes native INSERT ... ON CONFLICT DO NOTHING for idempotent observation writes"
    - "MailMessageObservedEntity persists with composite PK (tenant_id, gmail_message_id), applies Hibernate @TenantId on tenant_id, and maps TEXT[] label_ids"
    - "Gradle dependency wiring exists before worker/API implementation: version catalog aliases for Gmail API + google-auth, core module dependency for Gmail client factory, and api module dependency for TokenVerifier"
    - "GmailIngestionHealth implements IdentifiedEnum with fromId fail-loud"
    - "TenantEntity has triage_paused boolean field"
    - "012-mail-message-observed-table.yaml schema contains no subject, from_address, body, snippet, sender_domain, or recipient column definitions — privacy floor preserved (D-B3)"
  artifacts:
    - path: "backend/core/src/main/resources/db/changelog/changes/010-gmail-ingestion-state.yaml"
      provides: "ALTER TABLE gmail_connections — 6 columns"
      contains: "addColumn:"
    - path: "backend/core/src/main/resources/db/changelog/changes/011-pubsub-delivery-table.yaml"
      provides: "CREATE TABLE pubsub_delivery"
      contains: "createTable:"
    - path: "backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml"
      provides: "CREATE TABLE mail_message_observed"
      contains: "createTable:"
    - path: "backend/core/src/main/resources/db/changelog/changes/013-tenants-triage-paused.yaml"
      provides: "ALTER TABLE tenants ADD COLUMN triage_paused"
      contains: "addColumn:"
    - path: "backend/core/src/main/java/com/zeromail/core/gmail/model/GmailIngestionHealth.java"
      provides: "IdentifiedEnum: HEALTHY, WATCH_UNHEALTHY, HISTORY_LOST"
      exports: ["GmailIngestionHealth"]
    - path: "backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntity.java"
      provides: "JPA entity for pubsub_delivery table"
    - path: "backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java"
      provides: "JPA entity for mail_message_observed with composite PK + TEXT[]"
    - path: "gradle/libs.versions.toml"
      provides: "Library aliases for Google auth and Gmail API clients"
      contains: "google-api-services-gmail"
    - path: "backend/core/build.gradle.kts"
      provides: "Gmail API + Google auth dependencies for GmailApiClientFactory"
      contains: "google.api.services.gmail"
    - path: "backend/api/build.gradle.kts"
      provides: "Google auth dependency for PubSubOidcAuthFilter and OpenAPI dummy Pub/Sub args"
      contains: "google.auth.library.oauth2.http"
  key_links:
    - from: "GmailConnectionEntity"
      to: "GmailIngestionHealth"
      via: "@Enumerated(EnumType.STRING) ingestionHealth field"
      pattern: "GmailIngestionHealth"
    - from: "MailMessageObservedEntity"
      to: "MailMessageObservedId"
      via: "@IdClass"
      pattern: "@IdClass|MailMessageObservedId|@TenantId"
    - from: "Liquibase changeset 011"
      to: "pubsub_delivery table"
      via: "includeAll auto-pick from db.changelog-master.yaml"
      pattern: "pubsub_delivery"
---

<objective>
Create the Liquibase schema changesets 010-013 and all JPA entity/repository/enum classes for the Phase 2A data layer. This is the foundation that Waves 2a and 2b depend on.

Purpose: All DB tables + JPA types must exist before the service, controller, and scheduler layers can compile.

Output: 4 Liquibase YAML changelogs + 1 new enum + 2 new entities + 2 new repositories + 2 modified entities.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/phases/02A-mail-ingestion/02A-CONTEXT.md
@.planning/phases/02A-mail-ingestion/02A-RESEARCH.md
@.planning/phases/02A-mail-ingestion/02A-PATTERNS.md

<interfaces>
<!-- Existing entities/enums to extend/copy -->
From backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java:
```java
@Entity @Table(name = "gmail_connections")
public class GmailConnectionEntity extends AbstractTenantOwnedEntity {
    @Column(name = "google_email", nullable = false) private String googleEmail;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private GmailConnectionStatus status;
    @Column(name = "refresh_token_encrypted") private byte[] refreshTokenEncrypted;
    @Column(name = "connected_at") private Instant connectedAt;
    @Column(name = "disconnected_at") private Instant disconnectedAt;
    protected GmailConnectionEntity() {}
    public GmailConnectionEntity(UUID id, UUID tenantId, String googleEmail, GmailConnectionStatus status) { super(id, tenantId); ... }
}
```

From backend/core/src/main/java/com/zeromail/core/gmail/model/GmailConnectionStatus.java:
```java
public enum GmailConnectionStatus implements IdentifiedEnum {
    NOT_CONNECTED, PENDING, CONNECTED, DISCONNECTED;
    @Override public String id() { return name(); }
    public static GmailConnectionStatus fromId(String id) {
        return Stream.of(values()).filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown GmailConnectionStatus id: " + id));
    }
}
```

From backend/core/src/main/java/com/zeromail/core/shared/persistence/AbstractTenantOwnedEntity.java:
- supplies: id (UUID @Id), tenantId (@TenantId), createdAt, updatedAt, version fields
- constructor: super(id, tenantId)

From backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java:
```java
@Entity @Table(name = "tenants")
public class TenantEntity extends AbstractEntity {
    @Column(name = "display_name", nullable = false) private String displayName;
    protected TenantEntity() {}
    public TenantEntity(UUID id, String displayName) { super(id); this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
```

From backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java:
```java
public interface GmailConnectionRepository extends JpaRepository<GmailConnectionEntity, UUID> {
    Optional<GmailConnectionEntity> findByTenantId(UUID tenantId);
}
```

Last existing changeset: 009-drop-signed-in-onboarding-step.yaml
db.changelog-master.yaml uses: includeAll with relativeToChangelogFile: false — auto-picks 010-013 alphabetically
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Liquibase changesets 010-013</name>
  <files>
    backend/core/src/main/resources/db/changelog/changes/010-gmail-ingestion-state.yaml,
    backend/core/src/main/resources/db/changelog/changes/011-pubsub-delivery-table.yaml,
    backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml,
    backend/core/src/main/resources/db/changelog/changes/013-tenants-triage-paused.yaml
  </files>

  <read_first>
    - backend/core/src/main/resources/db/changelog/changes/007-add-audit-columns.yaml (addColumn pattern)
    - backend/core/src/main/resources/db/changelog/changes/003-create-gmail-connections.yaml (createTable pattern)
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml (confirm includeAll)
    - .planning/phases/02A-mail-ingestion/02A-RESEARCH.md (Pattern 8 Liquibase YAML shapes — exact column definitions)
    - CLAUDE.md (Conventions section)
  </read_first>

  <action>
Create 4 YAML changeset files. Copy these exact column definitions from RESEARCH.md Pattern 8:

**`010-gmail-ingestion-state.yaml`** — ALTER TABLE gmail_connections ADD 6 columns:
```yaml
databaseChangeLog:
  - changeSet:
      id: 010-gmail-ingestion-state
      author: zeromail
      changes:
        - addColumn:
            tableName: gmail_connections
            columns:
              - column:
                  name: last_synced_history_id
                  type: bigint
              - column:
                  name: watch_history_id
                  type: bigint
              - column:
                  name: watch_expires_at
                  type: timestamptz
              - column:
                  name: watch_renewed_at
                  type: timestamptz
              - column:
                  name: watch_consecutive_failures
                  type: int
                  defaultValueNumeric: 0
                  constraints:
                    nullable: false
              - column:
                  name: ingestion_health
                  type: varchar(32)
                  defaultValue: HEALTHY
                  constraints:
                    nullable: false
      rollback:
        - dropColumn:
            tableName: gmail_connections
            columnName: last_synced_history_id
        - dropColumn:
            tableName: gmail_connections
            columnName: watch_history_id
        - dropColumn:
            tableName: gmail_connections
            columnName: watch_expires_at
        - dropColumn:
            tableName: gmail_connections
            columnName: watch_renewed_at
        - dropColumn:
            tableName: gmail_connections
            columnName: watch_consecutive_failures
        - dropColumn:
            tableName: gmail_connections
            columnName: ingestion_health
```

**`011-pubsub-delivery-table.yaml`** — CREATE TABLE pubsub_delivery:
```yaml
databaseChangeLog:
  - changeSet:
      id: 011-pubsub-delivery-table
      author: zeromail
      changes:
        - createTable:
            tableName: pubsub_delivery
            columns:
              - column:
                  name: id
                  type: uuid
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: tenant_id
                  type: uuid
                  constraints:
                    nullable: false
              - column:
                  name: pubsub_message_id
                  type: text
                  constraints:
                    nullable: false
              - column:
                  name: history_id
                  type: bigint
                  constraints:
                    nullable: false
              - column:
                  name: payload
                  type: jsonb
                  constraints:
                    nullable: false
              - column:
                  name: status
                  type: varchar(16)
                  defaultValue: PENDING
                  constraints:
                    nullable: false
              - column:
                  name: attempts
                  type: int
                  defaultValueNumeric: 0
                  constraints:
                    nullable: false
              - column:
                  name: locked_until
                  type: timestamptz
              - column:
                  name: created_at
                  type: timestamptz
                  defaultValueComputed: now()
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: timestamptz
                  defaultValueComputed: now()
                  constraints:
                    nullable: false
              - column:
                  name: version
                  type: int
                  defaultValueNumeric: 0
                  constraints:
                    nullable: false
        - addUniqueConstraint:
            tableName: pubsub_delivery
            columnNames: tenant_id, pubsub_message_id
            constraintName: uq_pubsub_delivery_tenant_message
        - createIndex:
            tableName: pubsub_delivery
            indexName: idx_pubsub_delivery_status_locked
            columns:
              - column:
                  name: status
              - column:
                  name: locked_until
      rollback:
        - dropTable:
            tableName: pubsub_delivery
```

**`012-mail-message-observed-table.yaml`** — CREATE TABLE mail_message_observed:
```yaml
databaseChangeLog:
  - changeSet:
      id: 012-mail-message-observed-table
      author: zeromail
      changes:
        - createTable:
            tableName: mail_message_observed
            columns:
              - column:
                  name: tenant_id
                  type: uuid
                  constraints:
                    nullable: false
              - column:
                  name: gmail_message_id
                  type: text
                  constraints:
                    nullable: false
              - column:
                  name: gmail_thread_id
                  type: text
                  constraints:
                    nullable: false
              - column:
                  name: history_id
                  type: bigint
                  constraints:
                    nullable: false
              - column:
                  name: label_ids
                  type: text[]
                  constraints:
                    nullable: false
              - column:
                  name: internal_date
                  type: bigint
              - column:
                  name: observed_at
                  type: timestamptz
                  defaultValueComputed: now()
                  constraints:
                    nullable: false
        - addPrimaryKey:
            tableName: mail_message_observed
            columnNames: tenant_id, gmail_message_id
            constraintName: pk_mail_message_observed
        - createIndex:
            tableName: mail_message_observed
            indexName: idx_mail_message_observed_at_brin
            indexType: BRIN
            columns:
              - column:
                  name: observed_at
      rollback:
        - dropTable:
            tableName: mail_message_observed
```

**`013-tenants-triage-paused.yaml`** — ALTER TABLE tenants ADD triage_paused:
```yaml
databaseChangeLog:
  - changeSet:
      id: 013-tenants-triage-paused
      author: zeromail
      changes:
        - addColumn:
            tableName: tenants
            columns:
              - column:
                  name: triage_paused
                  type: boolean
                  defaultValueBoolean: false
                  constraints:
                    nullable: false
      rollback:
        - dropColumn:
            tableName: tenants
            columnName: triage_paused
```
  </action>

  <verify>
    <automated>./gradlew :backend:api:test --tests "*PostgresContainerTest*" 2>&1 | grep -E "BUILD|PASSED|FAILED" | head -5</automated>
  </verify>

  <acceptance_criteria>
    - All 4 YAML files exist at the exact paths
    - `010-gmail-ingestion-state.yaml` contains one `addColumn:` block with six `- column:` entries for `last_synced_history_id`, `watch_history_id`, `watch_expires_at`, `watch_renewed_at`, `watch_consecutive_failures`, and `ingestion_health`
    - `011-pubsub-delivery-table.yaml` contains `createTable:`, `addUniqueConstraint:`, `name: updated_at` with `nullable: false`, and `name: version`
    - `012-mail-message-observed-table.yaml` contains `createTable:` and `addPrimaryKey:`
    - `013-tenants-triage-paused.yaml` contains `defaultValueBoolean: false`
    - `./gradlew :backend:core:test` applies changesets without Liquibase errors (test startup succeeds)
    - Privacy floor verified: `grep -v '^#' backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml | grep -E "subject|from_address|body|snippet|sender|recipient"` returns empty (no matches) — D-B3 privacy floor enforced at DDL level
  </acceptance_criteria>

  <done>4 YAML changesets exist with correct Liquibase syntax; Liquibase applies all 4 cleanly on test container startup; 012 changeset contains no privacy-violating columns</done>
</task>

<task type="auto">
  <name>Task 2: GmailIngestionHealth enum + JPA entities/repositories + entity field extensions</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/gmail/model/GmailIngestionHealth.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntity.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java,
    backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java,
    backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java,
    gradle/libs.versions.toml,
    backend/core/build.gradle.kts,
    backend/api/build.gradle.kts
  </files>

  <read_first>
    - gradle/libs.versions.toml (existing version catalog; versions already include googleAuthLibrary + gmailApi)
    - backend/core/build.gradle.kts (add deps before GmailApiClientFactory lands in Plan 02)
    - backend/api/build.gradle.kts (add deps before PubSubOidcAuthFilter lands in Plan 03; add OpenAPI dummy Pub/Sub args)
    - backend/core/src/main/java/com/zeromail/core/gmail/model/GmailConnectionStatus.java (exact IdentifiedEnum pattern to copy)
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java (field annotation style)
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java (repository pattern)
    - backend/core/src/main/java/com/zeromail/core/shared/persistence/AbstractTenantOwnedEntity.java (fields it already provides)
    - backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java (add triage_paused field)
    - .planning/phases/02A-mail-ingestion/02A-PATTERNS.md (Pattern assignments section — PubSubDeliveryEntity, MailMessageObservedEntity composite PK, GmailIngestionHealth)
    - .planning/phases/02A-mail-ingestion/02A-RESEARCH.md (Pattern 7 Hibernate 7 TEXT[], Pattern 9 GmailIngestionHealth)
    - CLAUDE.md (Conventions section: records-for-DTOs / classes-for-entities, Lombok-free)
  </read_first>

  <action>
First wire Gradle dependencies, then create or modify 7 Java files.

**Step 0 — Gradle dependency wiring for Plans 02 and 03**

`gradle/libs.versions.toml` already contains:
```toml
googleAuthLibrary = "1.35.0"
gmailApi = "v1-rev20250331-2.0.0"
```

ADD library aliases:
```toml
google-auth-library-oauth2-http = { module = "com.google.auth:google-auth-library-oauth2-http", version.ref = "googleAuthLibrary" }
google-api-services-gmail = { module = "com.google.apis:google-api-services-gmail", version.ref = "gmailApi" }
```

`backend/core/build.gradle.kts` — ADD:
```kotlin
api(libs.google.api.services.gmail)
implementation(libs.google.auth.library.oauth2.http)
```
Use `api(...)` for Gmail because `GmailApiClientFactory.buildGmailClient(...)` returns the generated `Gmail` type and worker code calls generated Gmail APIs through that object. Use `implementation(...)` for Google auth because `GoogleCredentials` / `HttpCredentialsAdapter` are implementation details inside the factory.

`backend/api/build.gradle.kts` — ADD:
```kotlin
implementation(libs.google.auth.library.oauth2.http)
```
This is required by `PubSubOidcAuthFilter` (`TokenVerifier` + `JsonWebSignature`).

Also extend the existing `openApi.customBootRun.args` dummy-arg list with:
```kotlin
"--pubsub.push-audience-url=https://openapi.invalid/internal/pubsub/gmail",
"--pubsub.sa-principal-email=pubsub-openapi@openapi.invalid",
"--pubsub.oidc-certificates-url=https://www.googleapis.com/oauth2/v3/certs"
```
Without these dummy args, Plan 03's fail-fast Pub/Sub properties can break `:backend:api:generateOpenApiDocs`.

***

Create or modify 7 Java files.

**`GmailIngestionHealth.java`** — copy EXACTLY from `GmailConnectionStatus.java` pattern. Change enum name, change values to `HEALTHY, WATCH_UNHEALTHY, HISTORY_LOST`. Update `fromId` error message to "Unknown GmailIngestionHealth id: ". No `weight()` — this is `IdentifiedEnum`, not `OrderedEnum` (unordered per D-D1).

**`GmailConnectionEntity.java`** — READ the current file first, then ADD these 6 new fields below `disconnectedAt`:
```java
@Column(name = "last_synced_history_id")
private Long lastSyncedHistoryId;

@Column(name = "watch_history_id")
private Long watchHistoryId;

@Column(name = "watch_expires_at")
private Instant watchExpiresAt;

@Column(name = "watch_renewed_at")
private Instant watchRenewedAt;

@Column(name = "watch_consecutive_failures", nullable = false)
private int watchConsecutiveFailures = 0;

@Enumerated(EnumType.STRING)
@Column(name = "ingestion_health", nullable = false)
private GmailIngestionHealth ingestionHealth = GmailIngestionHealth.HEALTHY;
```
Add corresponding getters/setters using the existing one-liner style. DO NOT touch any existing fields, constructor, or methods.

**`PubSubDeliveryEntity.java`** — new entity extending `AbstractTenantOwnedEntity`. Package `com.zeromail.core.gmail.persistence`.
- `@Entity @Table(name = "pubsub_delivery")`
- Fields (AbstractTenantOwnedEntity already provides: id, tenantId, createdAt, updatedAt, version — DO NOT redeclare):
  - `@Column(name = "pubsub_message_id", nullable = false) private String pubsubMessageId`
  - `@Column(name = "history_id", nullable = false) private Long historyId`
  - `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "payload", columnDefinition = "jsonb", nullable = false) private String payload` (store full JSON string)
  - `@Column(name = "status", nullable = false) private String status = "PENDING"`
  - `@Column(name = "attempts", nullable = false) private int attempts = 0`
  - `@Column(name = "locked_until") private Instant lockedUntil`
- `protected PubSubDeliveryEntity() {}` — Hibernate no-args
- Constructor: `public PubSubDeliveryEntity(UUID id, UUID tenantId, String pubsubMessageId, Long historyId, String payload) { super(id, tenantId); ... }`
- Getters/setters one-liner style for all fields

**`PubSubDeliveryRepository.java`** — extends `JpaRepository<PubSubDeliveryEntity, UUID>`. Add an atomic native claim query. Do NOT implement claim as `SELECT ... FOR UPDATE SKIP LOCKED` returning entities only; repository method transactions end before worker processing, releasing locks and allowing duplicate workers to select the same rows. Claim must change row state inside the SQL statement:
```java
@Transactional
@Query(value = """
    UPDATE pubsub_delivery
    SET status = 'PROCESSING',
        locked_until = NOW() + (:lockSeconds * INTERVAL '1 second'),
        attempts = attempts + 1,
        updated_at = NOW(),
        version = version + 1
    WHERE id IN (
        SELECT id
        FROM pubsub_delivery
        WHERE (status = 'PENDING' AND (locked_until IS NULL OR locked_until < NOW()))
           OR (status = 'PROCESSING' AND locked_until < NOW())
        ORDER BY created_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
    )
    RETURNING *
    """, nativeQuery = true)
List<PubSubDeliveryEntity> claimPendingBatch(@Param("limit") int limit,
                                             @Param("lockSeconds") int lockSeconds);
```
Also add native idempotent insert + state update helpers:
```java
@Modifying
@Query(value = """
    INSERT INTO pubsub_delivery
      (id, tenant_id, pubsub_message_id, history_id, payload, status, attempts,
       locked_until, created_at, updated_at, version)
    VALUES
      (:id, :tenantId, :pubsubMessageId, :historyId, CAST(:payload AS jsonb),
       'PENDING', 0, NULL, NOW(), NOW(), 0)
    ON CONFLICT (tenant_id, pubsub_message_id) DO NOTHING
    """, nativeQuery = true)
@Transactional
int insertPendingIfAbsent(@Param("id") UUID id,
                          @Param("tenantId") UUID tenantId,
                          @Param("pubsubMessageId") String pubsubMessageId,
                          @Param("historyId") Long historyId,
                          @Param("payload") String payload);

@Modifying
@Query("UPDATE PubSubDeliveryEntity d SET d.status = :status, d.lockedUntil = null WHERE d.id = :id")
@Transactional
int updateStatus(@Param("id") UUID id, @Param("status") String status);

@Modifying
@Query("UPDATE PubSubDeliveryEntity d SET d.status = 'PENDING', d.lockedUntil = :nextAttemptAt WHERE d.id = :id")
@Transactional
int releaseForRetry(@Param("id") UUID id, @Param("nextAttemptAt") Instant nextAttemptAt);
```

**`MailMessageObservedEntity.java`** — new entity WITHOUT extending `AbstractTenantOwnedEntity`, but WITH explicit Hibernate `@TenantId` on `tenant_id`. Package `com.zeromail.core.gmail.persistence`.

Why: this table has a natural composite PK `(tenant_id, gmail_message_id)`, so it cannot inherit the project base class that supplies a surrogate `id`. It is still tenant-owned audit data. Do not leave it as an unfiltered JPA entity. Use `@IdClass` and annotate the standalone `tenantId` id field with `@TenantId` so Hibernate's tenant discriminator protects JPA reads (`findAll`, derived queries, future Phase 4 polling helpers). Native inserts still pass `tenantId` explicitly.
```java
@Entity
@Table(name = "mail_message_observed")
@IdClass(MailMessageObservedEntity.MailMessageObservedId.class)
public class MailMessageObservedEntity {

    public record MailMessageObservedId(
        UUID tenantId,
        String gmailMessageId
    ) implements java.io.Serializable {}

    @Id
    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "gmail_message_id", nullable = false)
    private String gmailMessageId;

    @Column(name = "gmail_thread_id", nullable = false)
    private String gmailThreadId;

    @Column(name = "history_id", nullable = false)
    private Long historyId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "label_ids", columnDefinition = "text[]", nullable = false)
    private String[] labelIds;

    @Column(name = "internal_date")
    private Long internalDate;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt = Instant.now();

    protected MailMessageObservedEntity() {}

    public MailMessageObservedEntity(UUID tenantId, String gmailMessageId, String gmailThreadId,
                                      Long historyId, String[] labelIds, Long internalDate) {
        this.tenantId = tenantId;
        this.gmailMessageId = gmailMessageId;
        this.gmailThreadId = gmailThreadId;
        this.historyId = historyId;
        this.labelIds = labelIds;
        this.internalDate = internalDate;
    }

    // Getters for all fields
    public MailMessageObservedId getId() { return new MailMessageObservedId(tenantId, gmailMessageId); }
    public UUID getTenantId() { return tenantId; }
    public String getGmailMessageId() { return gmailMessageId; }
    public String getGmailThreadId() { return gmailThreadId; }
    public Long getHistoryId() { return historyId; }
    public String[] getLabelIds() { return labelIds; }
    public Long getInternalDate() { return internalDate; }
    public Instant getObservedAt() { return observedAt; }
}
```
Import: `jakarta.persistence.Id`, `jakarta.persistence.IdClass`, `org.hibernate.annotations.TenantId`, `org.hibernate.annotations.JdbcTypeCode`, `org.hibernate.type.SqlTypes`.

**`MailMessageObservedRepository.java`** — extends `JpaRepository<MailMessageObservedEntity, MailMessageObservedEntity.MailMessageObservedId>`. Add a native insert method so idempotency never depends on catching `DataIntegrityViolationException` from JPA flush/commit:
```java
@Modifying
@Query(value = """
    INSERT INTO mail_message_observed
      (tenant_id, gmail_message_id, gmail_thread_id, history_id, label_ids, internal_date, observed_at)
    VALUES
      (:tenantId, :gmailMessageId, :gmailThreadId, :historyId, :labelIds, :internalDate, NOW())
    ON CONFLICT (tenant_id, gmail_message_id) DO NOTHING
    """, nativeQuery = true)
@Transactional
int insertObservedIfAbsent(@Param("tenantId") UUID tenantId,
                           @Param("gmailMessageId") String gmailMessageId,
                           @Param("gmailThreadId") String gmailThreadId,
                           @Param("historyId") Long historyId,
                           @Param("labelIds") String[] labelIds,
                           @Param("internalDate") Long internalDate);
```

**`TenantEntity.java`** — READ the current file, then ADD after the last field:
```java
@Column(name = "triage_paused", nullable = false)
private boolean triagePaused = false;

public boolean isTriagePaused() { return triagePaused; }
public void setTriagePaused(boolean triagePaused) { this.triagePaused = triagePaused; }
```
Do NOT touch any existing field, constructor, or getter.
  </action>

  <verify>
    <automated>./gradlew :backend:core:compileJava :backend:core:test --tests "*GmailIngestionHealthTest*" --tests "*PubSubDeliveryEntityTest*" --tests "*MailMessageObservedEntityTest*" 2>&1 | grep -E "BUILD|PASSED|FAILED|error:" | head -20</automated>
  </verify>

  <acceptance_criteria>
    - `GmailIngestionHealth.java` contains `implements IdentifiedEnum` and `NoSuchElementException`
    - `GmailIngestionHealth.java` does NOT contain `weight()` method (unordered enum)
    - `GmailConnectionEntity.java` contains `private GmailIngestionHealth ingestionHealth` with `@Enumerated(EnumType.STRING)`
    - `PubSubDeliveryEntity.java` extends `AbstractTenantOwnedEntity` and `@Table(name = "pubsub_delivery")`
    - `PubSubDeliveryEntity.java` maps payload with `@JdbcTypeCode(SqlTypes.JSON)`
    - `PubSubDeliveryRepository.java` contains `UPDATE pubsub_delivery` + `RETURNING *` inside `claimPendingBatch` and does NOT contain a standalone `SELECT * FROM pubsub_delivery ... FOR UPDATE SKIP LOCKED` claim method
    - `claimPendingBatch` selects both due `status = 'PENDING'` rows (`locked_until IS NULL OR locked_until < NOW()`) and stale `status = 'PROCESSING' AND locked_until < NOW()` rows, then increments `attempts`, refreshes `locked_until`, and returns the claimed/reclaimed rows in the same SQL statement
    - `PubSubDeliveryRepository.java` contains `insertPendingIfAbsent` with `ON CONFLICT (tenant_id, pubsub_message_id) DO NOTHING`
    - `MailMessageObservedEntity.java` contains `@IdClass`, `@TenantId`, and `@JdbcTypeCode(SqlTypes.ARRAY)`
    - `MailMessageObservedEntity.java` does NOT extend `AbstractTenantOwnedEntity`, but its `tenantId` field is annotated `@TenantId`
    - `MailMessageObservedRepository.java` contains `insertObservedIfAbsent` with `ON CONFLICT (tenant_id, gmail_message_id) DO NOTHING`
    - `MailMessageObservedEntityTest` contains a tenant-isolation assertion: with `TenantContext.TENANT` bound to tenant A, a JPA `findAll`/derived read cannot see tenant B's `mail_message_observed` row
    - `TenantEntity.java` contains `private boolean triagePaused = false`
    - `gradle/libs.versions.toml` contains `google-auth-library-oauth2-http` and `google-api-services-gmail` aliases
    - `backend/core/build.gradle.kts` contains `api(libs.google.api.services.gmail)` and `implementation(libs.google.auth.library.oauth2.http)`
    - `backend/api/build.gradle.kts` contains `implementation(libs.google.auth.library.oauth2.http)` and OpenAPI dummy args for `pubsub.push-audience-url`, `pubsub.sa-principal-email`, and `pubsub.oidc-certificates-url`
    - `./gradlew :backend:core:compileJava` exits 0
    - `GmailIngestionHealthTest` goes GREEN (id()==name() + fromId + NoSuchElementException)
    - `PubSubDeliveryEntityTest` and `MailMessageObservedEntityTest` pass (Liquibase applies schema, round-trips work)
  </acceptance_criteria>

  <done>Gradle Google/Gmail dependencies wired; all 7 Java files compile; GmailIngestionHealthTest GREEN; PubSubDeliveryEntityTest and MailMessageObservedEntityTest GREEN (Liquibase applied 010-013)</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| DB schema layer | Changeset rollback sections prevent orphaned schema state |
| Entity layer | @TenantId Hibernate filter applied to AbstractTenantOwnedEntity subclasses and explicitly to MailMessageObservedEntity.tenantId |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-02 | Tampering | pubsub_delivery UNIQUE constraint | mitigate | `UNIQUE(tenant_id, pubsub_message_id)` in Liquibase changeset 011 enforces atomic dedup at DB level |
| T-05 | Information Disclosure | mail_message_observed schema + JPA reads | mitigate | Schema contains NO subject/from/body/snippet columns; entity uses `@TenantId` on `tenant_id` despite custom composite PK, so future JPA reads are tenant-filtered |
| T-06 | Tampering | emailAddress lookup query | mitigate | Repository uses parameterized `findByGoogleEmailIgnoreCase(String)` — never string concatenation |
| T-04 | Information Disclosure | refresh token logging | accept | No refresh token fields exist in these entity classes; token cipher is in a separate module; ArchUnit FND-04 remains active |
</threat_model>

<verification>
After this plan:
- `./gradlew :backend:core:compileJava :backend:api:compileJava` exits 0
- `./gradlew :backend:core:test --tests "*GmailIngestionHealthTest*"` exits 0 (GREEN)
- `./gradlew :backend:core:test --tests "*PubSubDeliveryEntityTest*" --tests "*MailMessageObservedEntityTest*"` exits 0 (GREEN)
- Liquibase applies changesets 010-013 without errors during test container startup
- Privacy floor: `grep -v '^#' backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml | grep -E "subject|from_address|body|snippet|sender|recipient"` returns empty
</verification>

<success_criteria>
All 4 Liquibase changesets exist and apply cleanly. Google/Gmail Gradle dependencies are present before API/worker implementation. All 7 Java files compile. GmailIngestionHealthTest, PubSubDeliveryEntityTest, and MailMessageObservedEntityTest pass GREEN. The Wave 0 tests for these classes are now unblocked. 012 changeset has no privacy-violating columns.
</success_criteria>

<output>
After completion, create `.planning/phases/02A-mail-ingestion/02A-01-SUMMARY.md`
</output>
