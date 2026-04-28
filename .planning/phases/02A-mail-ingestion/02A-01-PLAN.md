---
phase: 02A-mail-ingestion
plan: "01"
type: execute
wave: 1
depends_on:
  - "02A-00"
files_modified:
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
    - "PubSubDeliveryEntity persists to pubsub_delivery with UNIQUE(tenant_id, pubsub_message_id)"
    - "MailMessageObservedEntity persists with composite PK (tenant_id, gmail_message_id) and TEXT[] label_ids"
    - "GmailIngestionHealth implements IdentifiedEnum with fromId fail-loud"
    - "TenantEntity has triage_paused boolean field"
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
  key_links:
    - from: "GmailConnectionEntity"
      to: "GmailIngestionHealth"
      via: "@Enumerated(EnumType.STRING) ingestionHealth field"
      pattern: "GmailIngestionHealth"
    - from: "MailMessageObservedEntity"
      to: "MailMessageObservedId"
      via: "@EmbeddedId"
      pattern: "@EmbeddedId|MailMessageObservedId"
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
    - `010-gmail-ingestion-state.yaml` contains `addColumn:` 6 times (one per new column)
    - `011-pubsub-delivery-table.yaml` contains `createTable:` and `addUniqueConstraint:`
    - `012-mail-message-observed-table.yaml` contains `createTable:` and `addPrimaryKey:`
    - `013-tenants-triage-paused.yaml` contains `defaultValueBoolean: false`
    - `./gradlew :backend:core:test` applies changesets without Liquibase errors (test startup succeeds)
  </acceptance_criteria>

  <done>4 YAML changesets exist with correct Liquibase syntax; Liquibase applies all 4 cleanly on test container startup</done>
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
    backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java
  </files>

  <read_first>
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
  - `@Column(name = "payload", columnDefinition = "jsonb", nullable = false) private String payload` (store full JSON string)
  - `@Column(name = "status", nullable = false) private String status = "PENDING"`
  - `@Column(name = "attempts", nullable = false) private int attempts = 0`
  - `@Column(name = "locked_until") private Instant lockedUntil`
- `protected PubSubDeliveryEntity() {}` — Hibernate no-args
- Constructor: `public PubSubDeliveryEntity(UUID id, UUID tenantId, String pubsubMessageId, Long historyId, String payload) { super(id, tenantId); ... }`
- Getters/setters one-liner style for all fields

**`PubSubDeliveryRepository.java`** — extends `JpaRepository<PubSubDeliveryEntity, UUID>`. Add native query for SKIP LOCKED batch claim:
```java
@Query(value = """
    SELECT * FROM pubsub_delivery
    WHERE status = 'PENDING'
    AND (locked_until IS NULL OR locked_until < NOW())
    ORDER BY created_at
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
@Transactional
List<PubSubDeliveryEntity> claimPendingBatch(@Param("limit") int limit);
```
Also add: `@Modifying @Query("UPDATE PubSubDeliveryEntity d SET d.status = :status WHERE d.id = :id") @Transactional int updateStatus(@Param("id") UUID id, @Param("status") String status)`

**`MailMessageObservedEntity.java`** — new entity WITHOUT extending AbstractTenantOwnedEntity (composite PK incompatibility). Package `com.zeromail.core.gmail.persistence`.
```java
@Entity
@Table(name = "mail_message_observed")
public class MailMessageObservedEntity {

    @Embeddable
    public record MailMessageObservedId(
        @Column(name = "tenant_id") UUID tenantId,
        @Column(name = "gmail_message_id") String gmailMessageId
    ) implements java.io.Serializable {}

    @EmbeddedId
    private MailMessageObservedId id;

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
        this.id = new MailMessageObservedId(tenantId, gmailMessageId);
        this.gmailThreadId = gmailThreadId;
        this.historyId = historyId;
        this.labelIds = labelIds;
        this.internalDate = internalDate;
    }

    // Getters for all fields
    public MailMessageObservedId getId() { return id; }
    public UUID getTenantId() { return id.tenantId(); }
    public String getGmailMessageId() { return id.gmailMessageId(); }
    public String getGmailThreadId() { return gmailThreadId; }
    public Long getHistoryId() { return historyId; }
    public String[] getLabelIds() { return labelIds; }
    public Long getInternalDate() { return internalDate; }
    public Instant getObservedAt() { return observedAt; }
}
```
Import: `org.hibernate.annotations.JdbcTypeCode`, `org.hibernate.type.SqlTypes`.

**`MailMessageObservedRepository.java`** — extends `JpaRepository<MailMessageObservedEntity, MailMessageObservedEntity.MailMessageObservedId>`. No custom queries needed in this wave; ON CONFLICT DO NOTHING handled at service layer via native INSERT.

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
    - `MailMessageObservedEntity.java` contains `@EmbeddedId` and `@JdbcTypeCode(SqlTypes.ARRAY)`
    - `MailMessageObservedEntity.java` does NOT extend `AbstractTenantOwnedEntity`
    - `TenantEntity.java` contains `private boolean triagePaused = false`
    - `./gradlew :backend:core:compileJava` exits 0
    - `GmailIngestionHealthTest` goes GREEN (id()==name() + fromId + NoSuchElementException)
    - `PubSubDeliveryEntityTest` and `MailMessageObservedEntityTest` pass (Liquibase applies schema, round-trips work)
  </acceptance_criteria>

  <done>All 7 Java files compile; GmailIngestionHealthTest GREEN; PubSubDeliveryEntityTest and MailMessageObservedEntityTest GREEN (Liquibase applied 010-013)</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| DB schema layer | Changeset rollback sections prevent orphaned schema state |
| Entity layer | @TenantId Hibernate filter applied to AbstractTenantOwnedEntity subclasses |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-02 | Tampering | pubsub_delivery UNIQUE constraint | mitigate | `UNIQUE(tenant_id, pubsub_message_id)` in Liquibase changeset 011 enforces atomic dedup at DB level |
| T-05 | Information Disclosure | mail_message_observed schema | mitigate | Schema contains NO subject/from/body/snippet columns — privacy floor enforced at DDL level; only IDs + label_ids + timestamps |
| T-06 | Tampering | emailAddress lookup query | mitigate | Repository uses parameterized `findByGoogleEmailIgnoreCase(String)` — never string concatenation |
| T-04 | Information Disclosure | refresh token logging | accept | No refresh token fields exist in these entity classes; token cipher is in a separate module; ArchUnit FND-04 remains active |
</threat_model>

<verification>
After this plan:
- `./gradlew :backend:core:compileJava :backend:api:compileJava` exits 0
- `./gradlew :backend:core:test --tests "*GmailIngestionHealthTest*"` exits 0 (GREEN)
- `./gradlew :backend:core:test --tests "*PubSubDeliveryEntityTest*" --tests "*MailMessageObservedEntityTest*"` exits 0 (GREEN)
- Liquibase applies changesets 010-013 without errors during test container startup
</verification>

<success_criteria>
All 4 Liquibase changesets exist and apply cleanly. All 7 Java files compile. GmailIngestionHealthTest, PubSubDeliveryEntityTest, and MailMessageObservedEntityTest pass GREEN. The Wave 0 tests for these classes are now unblocked.
</success_criteria>

<output>
After completion, create `.planning/phases/02A-mail-ingestion/02A-01-SUMMARY.md`
</output>
