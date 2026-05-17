# Phase 2B: Billing (Prepaid Credits) - Pattern Map

**Mapped:** 2026-05-05
**Files analyzed:** 38 (new + modified across `backend/core`, `backend/api`, `backend/worker`, Liquibase, tests, configs)
**Analog matches:** 36 / 38 (2 partial — see "No Analog Found")

This map groups files by domain layer and pins the closest existing analog. Plans MUST mirror the imports, constructor shape, naming, and idioms from the analog excerpt rather than invent. Where Phase 2B introduces something new (advisory-lock SQL, ShedLock, SePay), the analog is approximate and the rationale field calls out the deltas.

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match |
|---------------------|------|-----------|----------------|-------|
| `core/billing/package-info.java` | modulith package-info | n/a | `core/gmail/package-info.java` | exact |
| `core/billing/persistence/lowlevel/package-info.java` | modulith sub-package marker | n/a | `core/gmail/persistence/lowlevel/package-info.java` | exact |
| `core/billing/model/CallSite.java` | enum (model) | n/a | `core/onboarding/model/OnboardingStep.java` | role-match (uses `IdentifiedEnum`, not `OrderedEnum`) |
| `core/billing/model/CreditReservationStatus.java` | enum (model) | n/a | `core/onboarding/model/OnboardingStep.java` | role-match |
| `core/billing/model/BillingTopupIntentStatus.java` | enum (model) | n/a | `core/onboarding/model/OnboardingStep.java` | role-match |
| `core/billing/model/CreditLedger.java` | interface (cross-phase contract) | n/a | `core/gmail/service/GmailConnectionService.java` (interface-shaped service) | role-match (no existing pure-interface analog; closest is service surface) |
| `core/billing/model/ReservationId.java` | record (value object) | n/a | `core/gmail/model/GmailConnectionProjection.java` | role-match |
| `core/billing/model/CreditBalance.java` | record (value object) | n/a | `core/gmail/model/GmailConnectionProjection.java` | role-match |
| `core/billing/model/InsufficientCreditsException.java` | exception (model-package) | n/a | `core/gmail/service/InvalidGrantException.java` | exact |
| `core/billing/model/IllegalLedgerStateException.java` | exception (model-package) | n/a | `core/gmail/service/InvalidGrantException.java` | exact |
| `core/billing/persistence/CreditLedgerEntryEntity.java` | JPA entity (append-only journal) | CRUD (write-only insert) | `core/gmail/persistence/PubSubDeliveryEntity.java` | exact |
| `core/billing/persistence/CreditReservationEntity.java` | JPA entity (sidecar with mutable status) | CRUD | `core/gmail/persistence/PubSubDeliveryEntity.java` | exact |
| `core/billing/persistence/BillingTopupIntentEntity.java` | JPA entity (intent lifecycle) | CRUD | `core/gmail/persistence/PubSubDeliveryEntity.java` | exact |
| `core/billing/persistence/CreditLedgerEntryRepository.java` | JpaRepository | CRUD + native UNIQUE-on-conflict | `core/gmail/persistence/PubSubDeliveryRepository.java` | exact |
| `core/billing/persistence/CreditReservationRepository.java` | JpaRepository (FOR UPDATE SKIP LOCKED) | CRUD + native batch claim | `core/gmail/persistence/PubSubDeliveryRepository.java` | exact |
| `core/billing/persistence/BillingTopupIntentRepository.java` | JpaRepository | CRUD | `core/gmail/persistence/PubSubDeliveryRepository.java` | exact |
| `core/billing/persistence/lowlevel/AdvisoryLockJdbcHelper.java` | raw-JDBC helper | request-response (lock acquire) | none (new pattern) — closest stylistic: `core/gmail/persistence/crypto/RefreshTokenCipher.java` | partial |
| `core/billing/service/CreditLedgerService.java` | service (impl of interface) | CRUD + REQUIRES_NEW transaction | `core/gmail/service/GmailConnectionService.java` | role-match |
| `core/billing/service/BillingTopupService.java` | service (intent + webhook) | CRUD | `core/gmail/service/GmailConnectionService.java` | role-match |
| `core/billing/service/SepayApiKeyVerifier.java` | utility component (constant-time compare) | request-response | `core/gmail/persistence/crypto/RefreshTokenCipher.java` (constructor-from-properties shape) | partial |
| `core/billing/service/TopupCodeGenerator.java` | utility component (random gen + uniqueness retry) | n/a | (no analog) | partial |
| `core/billing/service/BillingProperties.java` | `@ConfigurationProperties` record | n/a | `api/config/ZeroMailApiProperties.java` (api-side); `worker/config/ZeroMailWorkerProperties.java` (worker-side) | role-match |
| `core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml` | Liquibase changeset | n/a | `core/.../changes/011-pubsub-delivery-table.yaml` | exact |
| `core/src/main/resources/db/changelog/changes/015-credit-reservation.yaml` | Liquibase changeset | n/a | `core/.../changes/011-pubsub-delivery-table.yaml` | exact |
| `core/src/main/resources/db/changelog/changes/016-billing-topup-intent.yaml` | Liquibase changeset | n/a | `core/.../changes/011-pubsub-delivery-table.yaml` | exact |
| `core/src/main/resources/db/changelog/changes/017-shedlock-table.yaml` | Liquibase changeset (vendor schema) | n/a | `core/.../changes/013-tenants-triage-paused.yaml` (minimal-changeset shape) | role-match |
| `core/src/main/resources/db/changelog/db.changelog-master.yaml` | Liquibase master include | n/a | (existing — extend with 4 new includes) | exact |
| `api/controllers/billing/BillingController.java` | REST controller (session-auth) | request-response | `api/controllers/TenantStatusController.java` | exact |
| `api/controllers/billing/SepayWebhookController.java` | REST controller (no session — `@Order(1)` chain) | webhook → filter → controller | `api/controllers/GmailPubSubController.java` | exact |
| `api/dto/billing/BillingBalanceResponse.java` | DTO (record) | n/a | `api/dto/gmail/GmailConnectionStatusResponse.java` | exact |
| `api/dto/billing/TopupIntentRequest.java` | DTO (record + Jakarta Validation) | n/a | `api/dto/gmail/GmailNotification.java` (record); add `@Min` from `core.billing.service.BillingProperties` analog | role-match |
| `api/dto/billing/TopupIntentResponse.java` | DTO (record) | n/a | `api/dto/gmail/GmailConnectionStatusResponse.java` | exact |
| `api/dto/billing/SepayWebhookPayload.java` | DTO (record) | n/a | `api/dto/gmail/PubSubPushEnvelope.java` | exact |
| `api/security/billing/BillingWebhookSecurityConfig.java` | Spring Security config (`@Order(1)`) | n/a | `api/security/PubSubSecurityConfig.java` | exact |
| `api/security/billing/SepayApiKeyAuthFilter.java` | `OncePerRequestFilter` | request-response | `api/security/PubSubOidcAuthFilter.java` | exact |
| `api/error/ErrorCodes.java` (modify) | constants class | n/a | `api/error/ErrorCodes.java` (extend in place) | exact |
| `api/config/GlobalExceptionHandler.java` (modify) | `@RestControllerAdvice` | n/a | `api/config/GlobalExceptionHandler.java` (extend in place) | exact |
| `api/src/main/resources/application.yml` (modify) | config | n/a | existing file | exact |
| `worker/src/main/resources/application.yml` (modify) | config | n/a | existing file | exact |
| `worker/billing/CreditReserveWatchdog.java` | `@Scheduled` job + ShedLock | event-driven (timer) | `worker/GmailWatchScheduler.java` | role-match (ShedLock is NEW) |
| `worker/billing/BillingIntentExpirySweeper.java` | `@Scheduled` job + ShedLock | event-driven (timer) | `worker/GmailWatchScheduler.java` | role-match |
| `worker/billing/ShedLockConfig.java` | `@Configuration` for `LockProvider` | n/a | `worker/config/ZeroMailWorkerProperties.java` (config bean shape) | partial |
| `core/src/test/java/.../arch/DomainBoundaryArchTests.java` (modify) | ArchUnit | n/a | existing file (extend with billing rule + extend exclusion arrays) | exact |
| `core/src/test/java/.../billing/CallSiteEnumMembershipArchTest.java` | ArchUnit (new) | n/a | `core/.../arch/DomainBoundaryArchTests.java` | role-match |
| `api/src/test/java/.../billing/BillingBalanceMultiTenantLeakTest.java` | integration test | n/a | `api/.../security/MultiTenantLeakIntegrationTest.java` | exact |
| `api/src/test/java/.../billing/ConcurrentReserveTest.java` | integration test (StructuredTaskScope) | n/a | `api/.../security/MultiTenantLeakIntegrationTest.java` | exact |
| `api/src/test/java/.../billing/SepayWebhookReplayTest.java` | integration test | n/a | `api/.../security/PubSubOidcAuthFilterTest.java` | role-match |
| `api/src/test/java/.../support/ApiPostgresTestBase.java` (modify) | test base | n/a | existing file (add `@DynamicPropertySource` line for `zero-mail.billing.sepay.webhook-api-key`) | exact |

---

## Pattern Assignments

### Layer: Modulith package-info

#### `core/billing/package-info.java` (modulith package-info)

**Analog:** `backend/core/src/main/java/com/zeromail/core/gmail/package-info.java`

**Excerpt (full file, lines 1-5):**
```java
@ApplicationModule(displayName = "Gmail", allowedDependencies = {"tenant", "shared.privacy", "shared.persistence", "shared.lang"})
package com.zeromail.core.gmail;

import org.springframework.modulith.ApplicationModule;
```

**Apply:** copy verbatim, change `displayName="Billing"` and drop `shared.privacy` from the array (billing has no edge to privacy markers per CONTEXT D-G1). Keep `tenant`, `shared.persistence`, `shared.lang`.

#### `core/billing/persistence/lowlevel/package-info.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/gmail/persistence/lowlevel/package-info.java`

**Excerpt (lines 1-5):**
```java
/**
 * Allow-listed package for native SQL / raw JDBC inside the gmail domain. Empty in Phase 1.2.
 */
package com.zeromail.core.gmail.persistence.lowlevel;
```

**Apply:** copy and rebrand to `core.billing.persistence.lowlevel`. Update the comment to mention the advisory-lock SQL helper that Phase 2B introduces (RESEARCH §"Pattern 1" Sub-package marker).

---

### Layer: Domain enums

#### `core/billing/model/CallSite.java`, `CreditReservationStatus.java`, `BillingTopupIntentStatus.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/onboarding/model/OnboardingStep.java`

**Excerpt (lines 22-54):**
```java
public enum OnboardingStep implements OrderedEnum {

    GMAIL_CONNECTED(10),
    TEMPLATE_SELECTED(20),
    COMPLETE(30);

    private final int weight;

    OnboardingStep(int weight) {
        this.weight = weight;
    }

    @Override
    public String id() {
        return name();
    }

    @Override
    public int weight() {
        return weight;
    }

    /**
     * Per-impl static fromId per D-B4. Throws {@link NoSuchElementException} on unknown id —
     * fail-loud (NOT {@link IllegalArgumentException}).
     */
    public static OnboardingStep fromId(String id) {
        return Stream.of(values())
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown OnboardingStep id: " + id));
    }
}
```

**Apply:**
- `CallSite` implements `IdentifiedEnum` (NOT `OrderedEnum` — there is no progression order; the integer payload is `cost()`, not `weight()`). Members `TRIAGE(1)`, `DRAFT(2)`, `PREVIEW(1)` carry an `int cost`.
- `CreditReservationStatus` implements `IdentifiedEnum`, members `PENDING`, `SETTLED`, `RELEASED`.
- `BillingTopupIntentStatus` implements `IdentifiedEnum`, members `PENDING`, `PAID`, `EXPIRED`.
- All three: copy the `id() { return name(); }` pattern, the `Stream.of(values()).filter…orElseThrow(NoSuchElementException::new)` `fromId` block, and the D-C2 invariant Javadoc reference.

---

### Layer: Records (value objects + DTOs)

#### `core/billing/model/ReservationId.java`, `CreditBalance.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/gmail/model/GmailConnectionProjection.java` (records-as-domain-projections; CLAUDE.md Conventions §2 records-for-DTOs)

**Apply:**
- `public record ReservationId(UUID value) {}` — single-field UUID wrapper.
- `public record CreditBalance(int availableCredits, int heldCredits) {}` — two-int read projection. Per D-G2, lives in `core.billing.model`.

#### `api/dto/billing/BillingBalanceResponse.java`

**Analog:** `backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailConnectionStatusResponse.java`

**Excerpt (full file, lines 1-16):**
```java
package com.zeromail.api.dto.gmail;

import com.zeromail.core.gmail.model.GmailConnectionProjection;

public record GmailConnectionStatusResponse(String connectionStatus, String googleEmail) {

    public static GmailConnectionStatusResponse from(GmailConnectionProjection projection) {
        return new GmailConnectionStatusResponse(projection.status(), projection.googleEmail());
    }
}
```

**Apply:** `public record BillingBalanceResponse(int availableCredits, int heldCredits, String currency)` with a `static from(CreditBalance)` factory. Keep `currency = "credits"` literal (constraint from SPEC R7).

#### `api/dto/billing/TopupIntentRequest.java`

**Analog:** `backend/api/src/main/java/com/zeromail/api/dto/gmail/PubSubPushEnvelope.java` (record with Jackson mapping); validation pattern mirrors `core.billing.service.BillingProperties` design.

**Apply:** `public record TopupIntentRequest(@Min(1) long amountVnd) {}` — Jakarta `@Min` triggers the existing `MethodArgumentNotValidException` handler in `GlobalExceptionHandler` lines 181-207 (returns 400 + `error.validation`).

#### `api/dto/billing/TopupIntentResponse.java` and `SepayWebhookPayload.java`

**Analog:** `backend/api/src/main/java/com/zeromail/api/dto/gmail/PubSubPushEnvelope.java`

**Apply:**
- `TopupIntentResponse(String code, long amountVnd, Instant expiresAt, String qrPayload)` — `qrPayload` nullable (Phase 5 fills it).
- `SepayWebhookPayload(long id, String gateway, String transactionDate, String accountNumber, String code, String content, String transferType, long transferAmount, long accumulated, String subAccount, String referenceCode, String description)` — mirror SePay's documented payload (RESEARCH §"Pattern 5").

---

### Layer: Exceptions (model-package)

#### `core/billing/model/InsufficientCreditsException.java`, `IllegalLedgerStateException.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/gmail/service/InvalidGrantException.java`

**Apply:** both extend `RuntimeException`, package-private or `public` per usage; no balance number in `InsufficientCreditsException` payload (privacy invariant — SPEC AC checkbox 11). `IllegalLedgerStateException` accepts a String message describing the forbidden transition (e.g., `"Cannot settle a released reservation"`).

Note: per RESEARCH §"Recommended Project Structure" these live under `model/` (not `service/`) so Phase 2C can import them through the public-API surface without crossing into `core.billing.service`.

---

### Layer: JPA Entities (extend `AbstractTenantOwnedEntity`)

#### `core/billing/persistence/CreditLedgerEntryEntity.java`, `CreditReservationEntity.java`, `BillingTopupIntentEntity.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntity.java`

**Excerpt (full file, lines 1-60):**
```java
package com.zeromail.core.gmail.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "pubsub_delivery")
public class PubSubDeliveryEntity extends AbstractTenantOwnedEntity {

    @Column(name = "pubsub_message_id", nullable = false)
    private String pubsubMessageId;

    @Column(name = "history_id", nullable = false)
    private Long historyId;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    protected PubSubDeliveryEntity() {}

    public PubSubDeliveryEntity(UUID id, UUID tenantId, String pubsubMessageId, Long historyId, String payload) {
        super(id, tenantId);
        this.pubsubMessageId = pubsubMessageId;
        ...
    }

    // getters + setters (CLAUDE.md Conventions §2: no Lombok)
}
```

**Apply (per entity):**
- All three extend `AbstractTenantOwnedEntity` (gives `id`, `tenantId`, `createdAt`, `updatedAt`, `version` for free per Phase 1.2.1 — no need to redeclare `created_at`/`version` columns in the entity OR Liquibase column block).
- `protected NoArgsConstructor()` for Hibernate.
- Public constructor takes domain-specific fields after `super(id, tenantId)`.
- Use `@Enumerated(EnumType.STRING)` on the `status`/`call_site` enum columns (per `IdentifiedEnum` D-C2 invariant).
- Provide getters; setters only for fields that legitimately mutate (`CreditLedgerEntryEntity` is append-only — getters only; `CreditReservationEntity` mutates `status` + `finalizedAt`; `BillingTopupIntentEntity` mutates `status` + `paidAt` + `sepayTransactionId`).
- Add a static factory on `CreditLedgerEntryEntity` per RESEARCH Pattern 2 — `static reserve(...)`, `static settle(...)`, `static release(...)`, `static topup(...)` so service code never sets `kind` directly.

**Excerpt to copy from (audit columns are FREE):** the analog never declares `createdAt` or `version` in its body — those flow from `AbstractAuditableEntity` per Phase 1.2.1. Do NOT redeclare them.

---

### Layer: Repositories

#### `core/billing/persistence/CreditLedgerEntryRepository.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java`

**Excerpt for ON CONFLICT-style replay protection (lines 78-97):**
```java
@Modifying
@Query(
    value =
        """
          INSERT INTO pubsub_delivery
            (id, tenant_id, pubsub_message_id, history_id, payload, status, attempts,
             locked_until, created_at, updated_at, version)
          VALUES
            (:id, :tenantId, :pubsubMessageId, :historyId, CAST(:payload AS jsonb),
             'PENDING', 0, NULL, NOW(), NOW(), 0)
          ON CONFLICT (tenant_id, pubsub_message_id) DO NOTHING
          """,
    nativeQuery = true)
@Transactional
int insertPendingIfAbsent(...);
```

**Apply:** `CreditLedgerEntryRepository extends JpaRepository<CreditLedgerEntryEntity, UUID>`. Most operations are vanilla `save()`. Add a `sumAvailableCreditsForTenant(UUID)` projection (`@Query("SELECT COALESCE(SUM(e.amountCredits), 0) FROM CreditLedgerEntryEntity e WHERE e.tenantId = :tenantId")`) — read-only, no native SQL needed. The UNIQUE-on-conflict pattern is owned by the DB constraint (idempotency surfaces as `DataIntegrityViolationException` caught at service layer).

#### `core/billing/persistence/CreditReservationRepository.java`

**Analog:** `PubSubDeliveryRepository` lines 13-42 (claim-batch with `FOR UPDATE SKIP LOCKED`):
```java
@Transactional
@Query(
    value =
        """
          ... SELECT id
          FROM pubsub_delivery
          WHERE (status = 'PENDING' AND (locked_until IS NULL OR locked_until < NOW()))
             ...
          ORDER BY created_at
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          ...
          """,
    nativeQuery = true)
List<PubSubDeliveryEntity> claimPendingBatch(@Param("limit") int limit, @Param("lockSeconds") int lockSeconds);
```

**Apply:** `findStalePendingIds(Instant olderThan, int limit)` — native SQL identical shape, just `WHERE status='PENDING' AND created_at < :olderThan` plus `FOR UPDATE SKIP LOCKED LIMIT :limit`. Returns `List<UUID>`. RESEARCH §"Pattern 4" pins this exactly.

#### `core/billing/persistence/BillingTopupIntentRepository.java`

**Analog:** `PubSubDeliveryRepository` (CRUD method derivation only).

**Apply:** simple Spring Data method-name derivation — `findByCode(String)`, `countByTenantIdAndStatus(UUID, BillingTopupIntentStatus)`, plus a `@Modifying` UPDATE for the expiry sweeper (`UPDATE BillingTopupIntentEntity i SET i.status = 'EXPIRED' WHERE i.status = 'PENDING' AND i.expiresAt < :now`).

---

### Layer: Low-level (raw JDBC, allow-listed by ArchUnit)

#### `core/billing/persistence/lowlevel/AdvisoryLockJdbcHelper.java`

**Analog (stylistic):** `backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java` (constructor-injection helper component holding raw cryptography state)

**Concrete shape lifted from RESEARCH §"Pattern 2":**
```java
@Component
class AdvisoryLockJdbcHelper {
    private final JdbcTemplate jdbcTemplate;

    AdvisoryLockJdbcHelper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void acquireTenantLock(UUID tenantId) {
        jdbcTemplate.execute((Connection connection) -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtext(?))")) {
                statement.setString(1, tenantId.toString());
                statement.execute();
            }
            return null;
        });
    }
}
```

**Rationale:** there is no current `JdbcTemplate` user in `core.billing` yet, so the analog match is shape-only. This file MUST live in `persistence.lowlevel` — Phase 1.2 ArchUnit guards that `JdbcTemplate` cannot be touched outside `*.persistence.lowlevel` (parity with the empty `gmail.persistence.lowlevel` allow-list package). Package-private visibility (CONTEXT D-G3 — only `core.billing.persistence` callers).

---

### Layer: Services

#### `core/billing/service/CreditLedgerService.java`

**Analog:** `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java`

**Excerpt (lines 25-58) — service shape, constructor injection, `@Transactional(readOnly=true)` for reads:**
```java
@Service
public class GmailConnectionService {

    private static final Logger log = LoggerFactory.getLogger(GmailConnectionService.class);

    private final GmailConnectionRepository connectionRepository;
    private final GmailApiClientFactory gmailApiClientFactory;
    ...

    public GmailConnectionService(GmailConnectionRepository connectionRepository, ...) {
        this.connectionRepository = connectionRepository;
        ...
    }

    @Transactional(readOnly = true)
    public GmailConnectionProjection currentStatus(UUID tenantId) {
        return connectionRepository.findByTenantId(tenantId)
                .map(connection -> new GmailConnectionProjection(...))
                .orElseGet(GmailConnectionProjection::notConnected);
    }
}
```

**Apply:**
- `class CreditLedgerService implements CreditLedger` — package-private class, public interface (D-G3 ArchUnit ban on direct instantiation outside `core.billing.service`).
- Constructor injects `CreditLedgerEntryRepository`, `CreditReservationRepository`, `AdvisoryLockJdbcHelper`.
- `reserve(...)` annotated `@Transactional(propagation = Propagation.REQUIRES_NEW)` (CONTEXT D-A2).
- `settle(...)` and `release(...)` annotated `@Transactional(propagation = Propagation.REQUIRED)` — caller-controlled atomicity.
- `balance(...)` annotated `@Transactional(readOnly = true)` (mirror `currentStatus` pattern above).
- Privacy logging: `log.info("event=credit_reserved tenantId={} reservationId={}", tenantId, reservationId)` (CLAUDE.md Conventions §4 — `event=opaque tenantId={}`).
- Full method skeletons live in RESEARCH §"Pattern 2" — copy verbatim, adjust imports.

#### `core/billing/service/BillingTopupService.java`

**Analog:** `GmailConnectionService` (same constructor + `@Transactional` shape).

**Apply:** two public methods.
- `createIntent(UUID tenantId, long amountVnd) -> BillingTopupIntent`: enforces "max 5 PENDING per tenant" (CONTEXT D-C2) — count first, then auto-expire the oldest if at limit. Calls `TopupCodeGenerator.generateUniqueCode(...)` with up to 3 retries.
- `applyWebhook(SepayWebhookPayload payload)`: lookup intent by code, validate amount + status + expiry, in one TX UPDATE intent + INSERT TOPUP entry. Catches `DataIntegrityViolationException` and treats as replay no-op (RESEARCH §"Pattern 5" trace step 6).

#### `core/billing/service/SepayApiKeyVerifier.java`

**Analog (stylistic):** `backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java` (helper component with secret material in constructor).

**Concrete shape from RESEARCH §"Pattern 3":** uses `MessageDigest.isEqual(byte[], byte[])` after `getBytes(StandardCharsets.UTF_8)`. Cache `expectedKeyBytes` once in constructor. NEVER use `Arrays.equals` or `String.equals` (timing-attack — RESEARCH §"Don't Hand-Roll").

**Rationale:** referenced from both the filter (in `backend/api`) and any future direct-test (one shared pure-unit class). Living in `core.billing.service` keeps the secret-handling near the domain rather than in the controller layer.

#### `core/billing/service/TopupCodeGenerator.java`

**Analog:** none. New utility.

**Concrete shape from RESEARCH §"Pattern 6"** — 32-char Crockford alphabet `0123456789ABCDEFGHJKMNPQRSTVWXYZ`, 8-char length, `SecureRandom`, `Predicate<String> isAvailable` for collision check, max-3-attempt retry. ~30 LOC.

#### `core/billing/service/BillingProperties.java`

**Analog:** `backend/api/src/main/java/com/zeromail/api/config/ZeroMailApiProperties.java` (existing `@ConfigurationProperties` + `@Validated` record nested for sub-blocks).

**Concrete shape from RESEARCH §"Pattern 5"**:
```java
@ConfigurationProperties(prefix = "zero-mail.billing")
@Validated
public record BillingProperties(
        @Valid @NotNull SepayProperties sepay,
        @Min(1) @DefaultValue("1000") long vndPerCredit,
        @Min(1) @DefaultValue("5") int maxPendingIntentsPerTenant,
        @Valid @DefaultValue Duration intentExpiry) {
    public record SepayProperties(@NotBlank String webhookApiKey) {}
}
```

**Note:** lives in `core.billing.service` per RESEARCH §"Recommended Project Structure" (the package-info `allowedDependencies` does NOT include `api.config`; placing properties inside the domain keeps the dependency arrow pointing inward). `@EnableConfigurationProperties(BillingProperties.class)` registration goes in a config bean inside `backend/api` (since both the controller and the worker import the same type — CLAUDE.md §"Recommended Project Structure" allows shared `@ConfigurationProperties` records across modules).

---

### Layer: Liquibase changesets

#### `014-credit-ledger-entry.yaml`, `015-credit-reservation.yaml`, `016-billing-topup-intent.yaml`

**Analog:** `backend/core/src/main/resources/db/changelog/changes/011-pubsub-delivery-table.yaml`

**Excerpt (lines 1-83) — full skeleton to copy:**
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
                  name: status
                  type: varchar(16)
                  defaultValue: PENDING
                  constraints:
                    nullable: false
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

**Apply per file:**

| File | Table | UNIQUE | Indexes | Source |
|------|-------|--------|---------|--------|
| `014-credit-ledger-entry.yaml` | `credit_ledger_entry` | `(ref_type, ref_id, kind)` | BRIN(`created_at`); B-tree `(tenant_id, created_at)`; B-tree `(tenant_id, ref_type, ref_id)` | SPEC R2; CONTEXT D-H1 |
| `015-credit-reservation.yaml` | `credit_reservation` | none (PK only) | partial `WHERE status='PENDING'` on `created_at`; B-tree `(tenant_id, status)` | CONTEXT D-B1; SPEC R3 |
| `016-billing-topup-intent.yaml` | `billing_topup_intent` | `code`; partial UNIQUE on `sepay_transaction_id WHERE NOT NULL` | B-tree `(status, expires_at)` | CONTEXT D-C1 |

All three include `tenant_id uuid NOT NULL` with FK-on-delete-cascade to `tenants(id)`. All three rely on `AbstractTenantOwnedEntity` providing `id`, `created_at`, `updated_at`, `version` — but Liquibase changesets MUST still declare those columns explicitly (Hibernate `ddl-auto=validate` checks them). Mirror the analog above column-for-column.

**BRIN index syntax in Liquibase:** Liquibase's `createIndex` does not natively support BRIN. Use `<sql>` rawSQL block — pattern is novel for this codebase; planner should pin via Liquibase 5.x docs. Falling-back form: `CREATE INDEX … USING BRIN (created_at)` inside a `sql` change.

**Partial-index syntax:** also use `<sql>` rawSQL — `CREATE INDEX … ON credit_reservation (created_at) WHERE status = 'PENDING'`. Not natively supported by `createIndex`.

#### `017-shedlock-table.yaml`

**Analog:** `backend/core/src/main/resources/db/changelog/changes/013-tenants-triage-paused.yaml` (minimal-shape changeset)

**Excerpt (full file, lines 1-19):**
```yaml
databaseChangeLog:
  - changeSet:
      id: 013-tenants-triage-paused
      author: zeromail
      changes:
        - addColumn:
            tableName: tenants
            ...
      rollback:
        - dropColumn:
            tableName: tenants
            columnName: triage_paused
```

**Apply:** ShedLock 7.x ships a documented `shedlock` table DDL — `name VARCHAR(64) PK, lock_until TIMESTAMPTZ NOT NULL, locked_at TIMESTAMPTZ NOT NULL, locked_by VARCHAR(255) NOT NULL`. Encode as a Liquibase `createTable` change.

#### `core/src/main/resources/db/changelog/db.changelog-master.yaml` (modify)

**Apply:** append four new `<include file: changes/014-credit-ledger-entry.yaml>` lines in numeric order. Existing pattern is `includeAll` style (per CONTEXT 1.2.1 references) — verify before committing.

---

### Layer: API controllers

#### `api/controllers/billing/BillingController.java`

**Analog:** `backend/api/src/main/java/com/zeromail/api/controllers/TenantStatusController.java`

**Excerpt (full file, lines 34-50):**
```java
@RestController
@Tag(name = "gmail")
public class TenantStatusController {

    private final GmailConnectionService connectionService;

    public TenantStatusController(GmailConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/gmail/connection/status")
    public GmailConnectionStatusResponse status() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        GmailConnectionProjection projection = connectionService.currentStatus(tenantId);
        return GmailConnectionStatusResponse.from(projection);
    }
}
```

**Apply:**
- `@RestController @Tag(name="billing") @RequestMapping("/api/billing")` on the class.
- Inject `CreditLedger` (interface, NOT `CreditLedgerService` — D-G3 ArchUnit ban).
- `@GetMapping("/balance") BillingBalanceResponse balance()` — call `creditLedger.balance(UUID.fromString(TenantContext.currentOrThrow()))`, map via `BillingBalanceResponse.from(...)`.
- `@PostMapping("/topup/intent") TopupIntentResponse createIntent(@Valid @RequestBody TopupIntentRequest request)` — delegates to `BillingTopupService.createIntent(...)`.
- NO transaction annotation on the controller — service owns it (CLAUDE.md Conventions §1).
- NO repository injection — `ControllerBoundaryArchTests` enforces (lines 36-50 of analog rules file).

#### `api/controllers/billing/SepayWebhookController.java`

**Analog:** `backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java`

**Excerpt (full file, lines 18-45):**
```java
@Hidden
@RestController
public class GmailPubSubController {

    private static final Logger log = LoggerFactory.getLogger(GmailPubSubController.class);

    private final PubSubIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    public GmailPubSubController(PubSubIngestionService ingestionService, ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/internal/pubsub/gmail")
    public void receivePush(@RequestBody PubSubPushEnvelope envelope) {
        if (envelope.message() == null || envelope.message().data() == null) {
            return;
        }
        ...
        ingestionService.ingestPushEnvelope(...);
    }
}
```

**Apply:**
- `@RestController` (consider `@Hidden` from springdoc — webhook is not a user-facing API surface; planner can decide based on whether SePay's `OpenAPI` discovery matters).
- `@PostMapping("/api/billing/sepay/webhook")` — note path matches the `@Order(1)` filter chain matcher.
- Body: `@RequestBody SepayWebhookPayload`.
- Returns `Map.of("success", true)` per RESEARCH "Critical Override" (SePay docs require `{"success": true}`).
- Logs `event=sepay_webhook_received` (no payload bytes — CONTEXT D-I1).
- Delegates to `BillingTopupService.applyWebhook(payload)`. Service decides credit-vs-skip; controller stays transport-thin.

---

### Layer: API security (`@Order(1)` chain for webhook)

#### `api/security/billing/BillingWebhookSecurityConfig.java`

**Analog:** `backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java`

**Excerpt (full file, lines 1-45):**
```java
@Configuration
public class PubSubSecurityConfig {

  @Bean
  PubSubOidcAuthFilter pubSubOidcAuthFilter(ZeroMailApiProperties properties) {
    var pubsub = properties.gmail().pubsub();
    return new PubSubOidcAuthFilter(...);
  }

  @Bean
  FilterRegistrationBean<PubSubOidcAuthFilter> pubSubOidcAuthFilterRegistration(PubSubOidcAuthFilter filter) {
    FilterRegistrationBean<PubSubOidcAuthFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  @Order(1)
  SecurityFilterChain pubSubFilterChain(HttpSecurity http, PubSubOidcAuthFilter oidcFilter) {
    return http.securityMatcher("/internal/pubsub/**")
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .addFilterBefore(oidcFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
```

**Apply:**
- Three-bean shape: filter bean, `FilterRegistrationBean` (disabled — prevents the filter from also being applied to the global servlet chain), `@Order(1) SecurityFilterChain`.
- `securityMatcher("/api/billing/sepay/**")` — note the matcher MUST be `@Order(1)` (lower than the existing session-auth chain) so the API key check runs before any session resolution attempts to mint a 401-redirect.
- `csrf.disable()` + `STATELESS` — webhook is not session-bearing.
- `addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)` — same insertion point as PubSub.
- Inject `BillingProperties` (NOT `ZeroMailApiProperties`) to fetch the API key.

#### `api/security/billing/SepayApiKeyAuthFilter.java`

**Analog:** `backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java`

**Excerpt (full file, lines 19-68):**
```java
public class PubSubOidcAuthFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(PubSubOidcAuthFilter.class);

  private final TokenVerifier tokenVerifier;
  private final String expectedEmail;

  public PubSubOidcAuthFilter(String audience, String serviceAccountEmail, String certificatesUrl) {
    ...
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    return !request.getServletPath().startsWith("/internal/pubsub/");
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws IOException, ServletException {
    String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
      log.warn("event=pubsub_oidc_missing_token");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    try {
      ...
      if (!expectedEmail.equalsIgnoreCase(verifiedEmail)) {
        log.warn("event=pubsub_oidc_wrong_email");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }
      ...
      chain.doFilter(request, response);
    } catch (TokenVerifier.VerificationException verificationException) {
      log.warn("event=pubsub_oidc_verification_failed type={}", verificationException.getClass().getSimpleName());
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }
}
```

**Apply (mirror exactly, swap the verification mechanism):**
- `extends OncePerRequestFilter`.
- `shouldNotFilter` returns `!getServletPath().startsWith("/api/billing/sepay/")`.
- Header read: `request.getHeader(HttpHeaders.AUTHORIZATION)`.
- Prefix check: `startsWith("Apikey ")` (literal — RESEARCH "Critical Override").
- Constant-time compare via `MessageDigest.isEqual(expectedKeyBytes, providedKeyBytes)` (RESEARCH §"Don't Hand-Roll" + Pattern 3). NEVER `String.equals` or `Arrays.equals`.
- Privacy logging: `event=sepay_webhook_auth_missing` and `event=sepay_webhook_auth_invalid` — no header bytes (strip `Apikey ` and discard before logging anything; CONTEXT D-I1).
- Constructor takes `String expectedApiKey`, immediately encodes to `byte[]` and stores — never store the plain `String` field (avoids accidental `toString()` leak).

---

### Layer: API errors (modify in place)

#### `api/error/ErrorCodes.java` (modify)

**Analog:** `backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java` (extend the existing constants block)

**Excerpt (lines 15-29):**
```java
public final class ErrorCodes {

    public static final String AUTH_UNAUTHORIZED            = "error.auth.unauthorized";
    public static final String AUTH_FORBIDDEN               = "error.auth.forbidden";
    ...
    public static final String GMAIL_DISCONNECTED            = "error.gmail.disconnected";
    public static final String AUTH_CONSENT_DENIED           = "error.auth.consent_denied";
    public static final String AUTH_GMAIL_SCOPE_REQUIRED     = "error.auth.gmail_scope_required";

    private ErrorCodes() {}
}
```

**Apply — add four constants:**
```java
public static final String BILLING_INSUFFICIENT_CREDITS    = "error.billing.insufficient";
public static final String BILLING_LEDGER_INVALID_STATE    = "error.billing.ledger.invalidState";
public static final String BILLING_SEPAY_REFERENCE_INVALID = "error.billing.sepay.reference_invalid";
public static final String BILLING_SEPAY_AUTH_INVALID      = "error.billing.sepay.auth_invalid";
```

(Match the dotted-key naming convention exactly — i18n keys must mirror these one-to-one in `apps/web/i18n/messages/{vi,en}.json`.)

#### `api/config/GlobalExceptionHandler.java` (modify)

**Analog:** existing `GlobalExceptionHandler` (extend in place — add two new `@ExceptionHandler` methods).

**Excerpt of the analog handler shape (lines 71-90):**
```java
@ExceptionHandler(CurrentUserNotFoundException.class)
public ResponseEntity<ProblemDetail> onCurrentUserMissing(CurrentUserNotFoundException exception) {
    log.warn("Current user not found for tenant; rejecting with 401: {}", exception.getClass().getSimpleName());
    return problem(
        HttpStatus.UNAUTHORIZED,
        "Current user is not available",
        "The authenticated session points at a user that no longer exists.",
        ErrorCodes.AUTH_CURRENT_USER_NOT_FOUND);
}
```

**Apply — add two handlers using the same `problem(...)` helper:**
```java
@ExceptionHandler(InsufficientCreditsException.class)
public ResponseEntity<ProblemDetail> onInsufficientCredits(InsufficientCreditsException exception) {
    log.warn("Insufficient credits translated to 402: {}", exception.getClass().getSimpleName());
    return problem(
        HttpStatus.valueOf(402),  // PAYMENT_REQUIRED
        "Insufficient credits",
        "The current tenant balance is too low for this action.",
        ErrorCodes.BILLING_INSUFFICIENT_CREDITS);
}

@ExceptionHandler(IllegalLedgerStateException.class)
public ResponseEntity<ProblemDetail> onIllegalLedgerState(IllegalLedgerStateException exception) {
    log.error("Illegal ledger state transition translated to 500: {}", exception.getClass().getSimpleName());
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Ledger state invariant violated",
        "An internal billing-state transition was attempted in an invalid order.",
        ErrorCodes.BILLING_LEDGER_INVALID_STATE);
}
```

**Critical privacy invariant (already in analog):** `params: Map.of()` — NEVER pass the actual balance number. SPEC AC checkbox 11 enforces this.

---

### Layer: application.yml (modify)

#### `backend/api/src/main/resources/application.yml` (modify)

**Analog:** existing file — line 73 already has the `:?` fail-fast pattern for `REFRESH_TOKEN_KEY_BASE64`.

**Excerpt (lines 67-78):**
```yaml
zeromail:
  ...
  crypto:
    refresh-token-key-base64: ${REFRESH_TOKEN_KEY_BASE64:?REFRESH_TOKEN_KEY_BASE64 must be supplied via deployment secret source (Docker secret, systemd credential, or locked-down env file)}
  gmail:
    pubsub:
      push-audience-url: ${PUBSUB_PUSH_AUDIENCE_URL:?PUBSUB_PUSH_AUDIENCE_URL env var is required}
```

**Apply — add a sibling `zero-mail.billing` block** (NOTE: existing namespace is `zeromail` (no hyphen) for crypto/gmail, but RESEARCH `BillingProperties` uses `zero-mail.billing`. Plan-phase MUST verify and pick ONE convention — recommend `zero-mail` per RESEARCH §"Pattern 5" and harmonize at planning):
```yaml
zero-mail:
  billing:
    sepay:
      webhook-api-key: ${SEPAY_WEBHOOK_API_KEY:?SEPAY_WEBHOOK_API_KEY must be supplied via deployment secret source (Docker secret, systemd credential, or locked-down env file)}
    vnd-per-credit: 1000
    max-pending-intents-per-tenant: 5
    intent-expiry: PT24H
```

#### `backend/worker/src/main/resources/application.yml` (modify)

**Analog:** same file, current state (lines 27-32).

**Apply — two parity changes** (CONTEXT D-F1 + Folded Todos CR-04):
1. Already has `refresh-token-key-base64: ${REFRESH_TOKEN_KEY_BASE64:?...}` on line 29 — verify the wording matches `backend/api` exactly.
2. ADD `zero-mail.billing.sepay.webhook-api-key: ${SEPAY_WEBHOOK_API_KEY:?...}` block (worker doesn't process webhook today, but property loading is module-wide and worker context boots BillingProperties; missing env breaks tests + future code-move).

---

### Layer: Worker schedulers

#### `worker/billing/CreditReserveWatchdog.java`

**Analog:** `backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java`

**Excerpt (lines 25-58, scheduler shape + `ScopedValue.where(TenantContext.TENANT, ...)` per-tenant binding):**
```java
@Component
public class GmailWatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(GmailWatchScheduler.class);
    private static final int BATCH_SIZE = 50;

    private final GmailConnectionRepository connectionRepository;
    ...

    public GmailWatchScheduler(...) { ... }

    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        List<GmailConnectionEntity> connectionsNeedingRenewal =
                connectionRepository.findConnectionsNeedingWatchRenewal(BATCH_SIZE);
        for (GmailConnectionEntity connection : connectionsNeedingRenewal) {
            ScopedValue.where(TenantContext.TENANT, connection.getTenantId().toString())
                    .run(() -> processWatchRenewal(connection));
        }
    }
    ...
    log.info("event=gmail_watch_renewed tenantId={}", tenantId);
```

**Apply (full skeleton in RESEARCH §"Pattern 4"):**
- `@Component` package-private class.
- `@Scheduled(fixedRate = 60_000L)` (NOT `cron` — SPEC R4 locks 60s `fixedRate`).
- `@SchedulerLock(name = "creditReserveWatchdog", lockAtLeastFor = "PT30S", lockAtMostFor = "PT2M")` — ShedLock annotation (NEW — analog has no equivalent).
- Inside the loop: same `ScopedValue.where(TenantContext.TENANT, …).run(...)` idiom from line 55-57 of the analog — required because `creditLedger.release()` calls Hibernate which reads `@TenantId`.
- Log on success: `log.info("event=credit_reserve_released_stale tenantId={} reservationId={} ageSeconds={}", ...)` (CONTEXT D-I2 — privacy-safe).
- Catch `IllegalLedgerStateException` and silently no-op (race between watchdog and 2C settle — RESEARCH §"Pattern 4" comment).

#### `worker/billing/BillingIntentExpirySweeper.java`

**Analog:** same `GmailWatchScheduler` shape.

**Apply:**
- `@Scheduled(fixedRate = 3_600_000L)` (1 hour — CONTEXT D-C4).
- `@SchedulerLock(name = "billingIntentExpirySweeper", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")`.
- Calls `BillingTopupIntentRepository.expireStale(now)` `@Modifying` UPDATE (idempotent, no per-row tenant binding needed since the UPDATE is purely status-flip with no side effects).

#### `worker/billing/ShedLockConfig.java`

**Analog (stylistic):** `backend/worker/src/main/java/com/zeromail/worker/config/ZeroMailWorkerProperties.java` (a `@Configuration`-style class in `worker.config`).

**Concrete shape from RESEARCH §"Pattern 4":**
```java
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
class ShedLockConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
```

**Rationale:** ShedLock is a NEW dependency (not on classpath today). Plan-phase must add to `gradle/libs.versions.toml` and `backend/worker/build.gradle.kts` per RESEARCH §"Standard Stack > Supporting".

---

### Layer: Tests

#### `core/src/test/java/.../arch/DomainBoundaryArchTests.java` (modify)

**Analog:** existing `backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java`

**Excerpt (lines 40-78) — per-domain rule shape:**
```java
@ArchTest
static final ArchRule account_no_cross_domain_repos = noClasses()
        .that().resideInAPackage("..core.account..")
        .should().dependOnClassesThat(
                nameEndsWithRepository.and(resideInAnyPackage(
                        "..core.onboarding.persistence..",
                        "..core.gmail.persistence..",
                        "..core.tenant.persistence..")))
        .because("D-D1: cross-domain reads must go through the other domain's Service");
```

**Apply two changes:**
1. Extend each existing rule's exclusion array to include `..core.billing.persistence..` (4 places).
2. Add a new `billing_no_cross_domain_repos` rule that bans `core.billing` from depending on any of `account/onboarding/gmail/tenant.persistence` repositories.
3. Add a new `archtest` (or sibling file `BillingDomainArchTests.java`) for D-G3 invariants: (a) `JdbcTemplate` only inside `core.billing.persistence.lowlevel`; (b) `CreditLedgerService` not directly instantiated outside `core.billing.service`; (c) `CallSite` enum has exactly `{TRIAGE, DRAFT, PREVIEW}` members.

#### `core/src/test/java/.../billing/CallSiteEnumMembershipArchTest.java`

**Analog:** ArchUnit pattern from above; concrete enum-membership assertion is plain JUnit (ArchUnit cannot assert enum members, but a `Stream.of(CallSite.values())` assert is sufficient — test it from a regular `@Test` not `@ArchTest`).

#### `api/src/test/java/.../billing/BillingBalanceMultiTenantLeakTest.java`

**Analog:** `backend/api/src/test/java/com/zeromail/api/security/MultiTenantLeakIntegrationTest.java`

**Excerpt (lines 22-74) — `RestClient + LocalServerPort` + `StructuredTaskScope` (Java 25) per-tenant pattern:**
```java
@ActiveProfiles("test")
@Import(TestSessionSupport.class)
class MultiTenantLeakIntegrationTest extends ApiPostgresTestBase {

    @LocalServerPort int port;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired TestSessionSupport.TestSessionMinter minter;

    @Test
    void concurrent_virtual_thread_requests_never_cross_tenant() throws Exception {
        int N = 100;
        List<Seed> seeds = IntStream.range(0, N).mapToObj(i -> seedTenant("t-" + i)).toList();
        RestClient client = RestClient.create("http://localhost:" + port);

        try (var scope = StructuredTaskScope.<String>open()) {
            var subtasks = seeds.stream().map(s -> scope.fork(() -> fetchTenantEcho(client, s))).toList();
            scope.join();
            for (int i = 0; i < N; i++) {
                String observed = subtasks.get(i).get();
                assertThat(observed).isEqualTo(seeds.get(i).tenantId().toString());
            }
        }
    }
    ...
}
```

**Apply:**
- Same skeleton: `extends ApiPostgresTestBase`, `@Import(TestSessionSupport.class)`, `@LocalServerPort int port`, `RestClient.create("http://localhost:" + port)`.
- Seed two tenants with different starting balances (TOPUP entries inserted via repository).
- Two concurrent `GET /api/billing/balance` calls under different `TestSessionSupport` headers; assert each gets its own balance, no cross-leak.

#### `api/src/test/java/.../billing/ConcurrentReserveTest.java`

**Analog:** `MultiTenantLeakIntegrationTest` (same `StructuredTaskScope` shape).

**Apply (per CONTEXT D-A3 + SPEC AC checkbox 5):**
- Single tenant, seed `available=5`.
- `StructuredTaskScope` fork 10 threads each calling `creditLedger.reserve(tenantId, CallSite.TRIAGE)`.
- Assert: exactly 5 threads return a `ReservationId`, 5 throw `InsufficientCreditsException`.
- Assert post-state: `balance.availableCredits == 0`, `credit_ledger_entry` row count = 1 TOPUP + 5 RESERVE = 6, sum signed = 0.

#### `api/src/test/java/.../billing/SepayWebhookReplayTest.java`

**Analog:** `backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java` (filter-chain integration test pattern — webhook arrives, filter runs, controller persists).

**Apply (per SPEC AC checkbox 9 + CONTEXT D-E2):**
- Two POSTs of the same `SepayWebhookPayload` JSON with the same `id` (transaction ID).
- Assert both return HTTP 200; ledger has exactly one TOPUP row.
- Bad API key variant: assert HTTP 401, no ledger entry.
- Test base injects `SEPAY_WEBHOOK_API_KEY=test-api-key` via `@DynamicPropertySource` (modify `ApiPostgresTestBase` — see next item).

#### `api/src/test/java/.../support/ApiPostgresTestBase.java` (modify)

**Analog:** existing file, lines 26-52.

**Excerpt (lines 26-52):**
```java
@DynamicPropertySource
static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    ...
    r.add("zeromail.crypto.refresh-token-key-base64",
            () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    r.add("zeromail.gmail.pubsub.push-audience-url", () -> "https://test.example/internal/pubsub/gmail");
    r.add("zeromail.gmail.pubsub.sa-principal-email", () -> "pubsub-sa@test-project.iam.gserviceaccount.com");
}
```

**Apply — append three lines:**
```java
r.add("zero-mail.billing.sepay.webhook-api-key", () -> "test-sepay-api-key");
r.add("zero-mail.billing.vnd-per-credit", () -> "1000");
r.add("zero-mail.billing.max-pending-intents-per-tenant", () -> "5");
```

(Mirror change in `backend/core/src/test/java/.../support/PostgresContainerTest.java` if billing tests run from the core test base.)

---

## Shared Patterns

These cross-cutting patterns apply to multiple new files. Plans MUST reference these from per-plan action sections.

### Cross-cutting Pattern 1: Service-owned `@Transactional`, thin controller (CLAUDE.md Conventions §1)

**Source:** `backend/api/src/main/java/com/zeromail/api/controllers/TenantStatusController.java` lines 34-50.

**Apply to:** `BillingController`, `SepayWebhookController`. Controllers MUST NOT inject repositories; ONLY services. Transaction annotation lives on `CreditLedgerService.reserve` / `BillingTopupService.applyWebhook`. ArchUnit `ControllerBoundaryArchTests` enforces (lines 36-50 of analog).

### Cross-cutting Pattern 2: `TenantContext.currentOrThrow()` for tenant resolution (Phase 1 FND-02)

**Source:** `backend/api/src/main/java/com/zeromail/api/controllers/TenantStatusController.java` line 46.

```java
UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
```

**Apply to:** every `BillingController` method body. Watchdog/sweeper additionally use `ScopedValue.where(TenantContext.TENANT, …).run(...)` from `GmailWatchScheduler` line 55-57 because background threads have no inherited scoped value.

### Cross-cutting Pattern 3: Privacy-safe `event=opaque tenantId={}` logging (CLAUDE.md Conventions §4)

**Source:** `backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java` lines 80-91.

```java
log.info("event=gmail_watch_renewed tenantId={}", tenantId);
log.warn("event=gmail_watch_invalid_grant tenantId={}", tenantId);
log.warn("event=gmail_watch_unhealthy_threshold tenantId={}", tenantId);
```

**Apply to:** every billing log line. Locked event names from CONTEXT D-I1/I2/I3:
- `event=credit_reserved tenantId={} reservationId={}`
- `event=credit_settled tenantId={} reservationId={}`
- `event=credit_released tenantId={} reservationId={}`
- `event=credit_reserve_released_stale tenantId={} reservationId={} ageSeconds={}`
- `event=sepay_webhook_received` (no tenantId — not yet resolved)
- `event=sepay_webhook_auth_missing`
- `event=sepay_webhook_auth_invalid`
- `event=sepay_webhook_unknown_code` (NO code value)
- `event=sepay_webhook_amount_mismatch intentVnd={} actualVnd={}` (numbers OK; no payload)
- `event=sepay_webhook_intent_expired`
- `event=sepay_topup_credited tenantId={} credits={}` (NO transactionId — DB only)
- `event=sepay_topup_rounding_loss vndLost={}`

### Cross-cutting Pattern 4: `:?` fail-fast deployment secret + `@DynamicPropertySource` test inject (Phase 1.5 CR-04)

**Source:** `backend/api/src/main/resources/application.yml` line 73; `backend/api/src/test/java/.../support/ApiPostgresTestBase.java` line 48.

**Apply to:** both `application.yml` files (api + worker), both test base files. Pattern is identical in shape to existing `REFRESH_TOKEN_KEY_BASE64` — copy verbatim, swap env var name.

### Cross-cutting Pattern 5: Records-for-DTOs / classes-for-entities, Lombok-free (CLAUDE.md Conventions §2)

**Source:** Conventions block in `CLAUDE.md`; concrete examples in `api/dto/gmail/GmailConnectionStatusResponse.java` (record) + `core/gmail/persistence/PubSubDeliveryEntity.java` (mutable class with `protected NoArgs()`).

**Apply to:** all 4 billing DTOs (`BillingBalanceResponse`, `TopupIntentRequest`, `TopupIntentResponse`, `SepayWebhookPayload`) + 2 value records (`ReservationId`, `CreditBalance`) → records. All 3 billing entities → mutable classes with `protected NoArgs()`. NEVER `@Data`, `@Builder`, `@AllArgsConstructor` (Lombok forbidden project-wide).

### Cross-cutting Pattern 6: `IdentifiedEnum` + `id() == name()` invariant + `fromId` fail-loud (Phase 1.2.1 D-B)

**Source:** `backend/core/src/main/java/com/zeromail/core/onboarding/model/OnboardingStep.java` lines 22-54.

**Apply to:** `CallSite`, `CreditReservationStatus`, `BillingTopupIntentStatus`. All three implement `IdentifiedEnum`. `id()` returns `name()`. `fromId(String)` throws `NoSuchElementException` (NOT `IllegalArgumentException`).

### Cross-cutting Pattern 7: ApiError + `code:"error.x.y"` dotted i18n key (Phase 1.1 D-C3)

**Source:** `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java` lines 71-90 (handler shape) + lines 217-226 (`problem(...)` helper).

**Apply to:** every new `@ExceptionHandler` for billing exceptions. NEVER pass a numeric balance in `params`; ALWAYS `Map.of()` (privacy invariant). Frontend localizes via i18n, never reads `title`/`detail`.

### Cross-cutting Pattern 8: ArchUnit per-domain repository ban (Phase 1.2 D-D1)

**Source:** `backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java` lines 40-78.

**Apply to:** modify all 4 existing rules to add `..core.billing.persistence..` to the exclusion array; add a new 5th rule for billing.

---

## No Analog Found

| File | Role | Reason |
|------|------|--------|
| `core/billing/persistence/lowlevel/AdvisoryLockJdbcHelper.java` | raw JDBC helper for `pg_advisory_xact_lock` | The existing `gmail/persistence/lowlevel/` package is empty (marker only — see lines 1-5 of its `package-info.java`). No prior `JdbcTemplate.execute(Connection -> ...)` user exists in the codebase. Pattern shape is from RESEARCH §"Pattern 2"; planner should treat the analog as "stylistic fit with the lowlevel package convention" and the implementation as new. |
| `worker/billing/ShedLockConfig.java` | ShedLock `LockProvider` bean configuration | ShedLock is a NEW dependency (not on classpath today; Phase 2A's `GmailWatchScheduler` does NOT use `@SchedulerLock`). RESEARCH §"Standard Stack > Supporting" pins versions; CONTEXT D-H1 reserves changeset `017` for the ShedLock table. Analog match is purely shape (a `@Configuration` + `@Bean` style class — see `backend/worker/src/main/java/com/zeromail/worker/config/`). |

For these two files, the planner should follow RESEARCH excerpts verbatim rather than mirror an in-repo analog.

---

## Metadata

**Analog search scope:**
- `backend/core/src/main/java/com/zeromail/core/{gmail,onboarding,account,tenant,shared}/`
- `backend/core/src/main/resources/db/changelog/changes/`
- `backend/api/src/main/java/com/zeromail/api/{controllers,dto,security,error,config}/`
- `backend/api/src/main/resources/`
- `backend/api/src/test/java/com/zeromail/api/{security,arch,support}/`
- `backend/worker/src/main/java/com/zeromail/worker/`
- `backend/worker/src/main/resources/`
- `backend/core/src/test/java/com/zeromail/core/{arch,support}/`

**Files scanned:** ~32 distinct in-repo files read directly; ~50 enumerated via `ls`.
**Pattern extraction date:** 2026-05-05.
