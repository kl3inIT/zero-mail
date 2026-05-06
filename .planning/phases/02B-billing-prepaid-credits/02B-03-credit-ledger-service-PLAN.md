---
phase: 02B
plan: 03
type: execute
wave: 2
depends_on: [01, 02]
files_modified:
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryEntity.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryRepository.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationEntity.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationRepository.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/StaleReservation.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationStaleScanFragment.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/CreditReservationRepositoryImpl.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentEntity.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentRepository.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentTenantLookup.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentTenantLookupFragment.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/BillingTopupIntentRepositoryImpl.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/AdvisoryLockJdbcHelper.java
  - backend/core/src/main/java/com/zeromail/core/billing/service/BillingProperties.java
  - backend/core/src/main/java/com/zeromail/core/billing/service/BillingConfiguration.java
  - backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java
  - backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java
  - backend/core/src/main/java/com/zeromail/core/billing/service/SepayApiKeyVerifier.java
  - backend/core/src/main/java/com/zeromail/core/billing/service/TopupCodeGenerator.java
  - backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java
autonomous: true
requirements: [BILL-02, BILL-03, BILL-04, BILL-05, BILL-06]
files_modified_overlap_note: "No overlap with Plan 01 (which only touched changelogs + build files) or Plan 02 (which only touched core.billing.model + package-info). All new files."
must_haves:
  truths:
    - "`creditLedger.reserve(tenantId, CallSite.TRIAGE)` on tenant with availableCredits=5 inserts 1 RESERVE journal row + 1 PENDING reservation row inside a single REQUIRES_NEW transaction guarded by `pg_advisory_xact_lock(hashtext(tenantId))`."
    - "Concurrent 10-thread reserve on available=5: exactly 5 succeed, 5 throw `InsufficientCreditsException` (verified by Wave 0 `CreditLedgerConcurrentReserveTest`)."
    - "`creditLedger.settle` on already-RELEASED reservation throws `IllegalLedgerStateException`. `creditLedger.release` on already-SETTLED reservation throws `IllegalLedgerStateException`."
    - "`creditLedger.balance(tenantId)` returns `CreditBalance(SUM(amount_credits), -SUM(RESERVE-without-finalization))` via JPQL aggregate query."
    - "`SepayApiKeyVerifier.verify(authorizationHeader)` uses `MessageDigest.isEqual` over UTF-8 bytes — never `String.equals` / `Arrays.equals`."
    - "`TopupCodeGenerator.generateUniqueCode(predicate, 3)` retries up to 3x using Crockford alphabet `0123456789ABCDEFGHJKMNPQRSTVWXYZ` (no I/L/O/U)."
    - "`BillingProperties` is a `@ConfigurationProperties(prefix=\"zero-mail.billing\")` record with `vndPerCredit=1000` default + `sepay.webhookApiKey @NotBlank`."
    - "`BillingTopupService.createIntent(tenantId, amountVnd)` throws `IllegalArgumentException` when `amountVnd < vndPerCredit` (REVIEWS HIGH-7 — prevents 0-credit topups at intent creation)."
    - "`BillingTopupService.applyWebhook` short-circuits with `event=sepay_topup_below_min_credits` log + return when `credits <= 0` (REVIEWS HIGH-7 defense-in-depth for vnd-per-credit reconfiguration race)."
    - "`BillingTopupService.applyWebhook` resolves the intent via `intentRepository.findTenantLookupByCode(code)` (raw JDBC, bypasses @TenantId filter), then `ScopedValue.where(TenantContext.TENANT, lookup.tenantId().toString()).run(...)` BEFORE any JPA write — REVIEWS HIGH-2 closed; webhook never calls `intentRepository.findByCode` directly because the request thread has no bound TenantContext."
    - "`BillingTopupIntentRepository extends JpaRepository<BillingTopupIntentEntity, UUID>, BillingTopupIntentTenantLookupFragment` — fragment lives in `core.billing.persistence`, implementation lives in `core.billing.persistence.lowlevel.BillingTopupIntentRepositoryImpl` (ArchUnit-allowlisted for raw JDBC)."
    - "`./gradlew :backend:core:check` passes — Wave 0 RED tests in core/billing flip to GREEN once `@Disabled` annotations are removed by this plan."
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryEntity.java"
      provides: "Append-only journal entity extending AbstractTenantOwnedEntity with static factories reserve()/settle()/release()/topup()."
    - path: "backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationEntity.java"
      provides: "Sidecar reservation entity (mutable status PENDING→SETTLED|RELEASED + finalizedAt) extending AbstractTenantOwnedEntity."
    - path: "backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentEntity.java"
      provides: "Top-up intent entity (mutable status + paidAt + sepayTransactionId) extending AbstractTenantOwnedEntity."
    - path: "backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/AdvisoryLockJdbcHelper.java"
      provides: "Raw-JDBC component issuing `SELECT pg_advisory_xact_lock(hashtext(?))` — `public` class with `public acquireTenantLock(UUID)` so `core.billing.service` can call it across packages; ArchUnit (Plan 06) `jdbc_template_only_in_lowlevel` enforces the `JdbcTemplate` boundary by package, not by Java visibility (REVIEWS HIGH-5)."
    - path: "backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java"
      provides: "Implements CreditLedger; reserve REQUIRES_NEW + advisory lock; settle/release REQUIRED + UNIQUE-on-conflict idempotent; balance read-only."
    - path: "backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java"
      provides: "createIntent (max-5-PENDING) + applyWebhook (idempotent TOPUP via UNIQUE on sepay_transaction_id)."
    - path: "backend/core/src/main/java/com/zeromail/core/billing/service/BillingProperties.java"
      provides: "@ConfigurationProperties record with sepay.webhookApiKey + vndPerCredit + maxPendingIntentsPerTenant + intentExpiry."
    - path: "backend/core/src/main/java/com/zeromail/core/billing/service/SepayApiKeyVerifier.java"
      provides: "Constant-time API-key compare via MessageDigest.isEqual; cached expectedKeyBytes to prevent String leakage."
    - path: "backend/core/src/main/java/com/zeromail/core/billing/service/TopupCodeGenerator.java"
      provides: "Crockford-base32 8-char code generator with collision retry via Predicate<String>."
  key_links:
    - from: "CreditReservationRepository.findStalePendingProjections"
      to: "Watchdog (Plan 05) ScopedValue.where(TenantContext.TENANT, ...).run(...)"
      via: "StaleReservation projection record carries tenantId so worker can bind tenant before any JPA call"
      pattern: "StaleReservation"
    - from: "CreditLedgerService.reserve"
      to: "AdvisoryLockJdbcHelper.acquireTenantLock"
      via: "method call inside @Transactional(REQUIRES_NEW)"
      pattern: "advisoryLockHelper.acquireTenantLock"
    - from: "BillingTopupService.applyWebhook"
      to: "billing_topup_intent UNIQUE(sepay_transaction_id) constraint"
      via: "DataIntegrityViolationException catch → 200 ack (replay no-op)"
      pattern: "catch.*DataIntegrityViolationException"
---

<objective>
Land the entire `core.billing.service` + `core.billing.persistence` + `core.billing.persistence.lowlevel` domain implementation. This plan is the heart of the phase: the atomic reserve/settle/release semantics under concurrency, the SePay TOPUP idempotency, and all 8 RED Wave 0 core-tests turn GREEN here. After this plan, Plan 04 wires the API surface and Plan 05 wires the worker schedulers.

Purpose: per CONTEXT D-A1 + D-A2, atomic reserve = `pg_advisory_xact_lock` inside REQUIRES_NEW; per D-B1 + D-B2, sidecar table mutates while journal stays append-only; per D-C1 + D-C3, intent table backs SePay webhook resolution; per D-G3, ArchUnit boundary requires `JdbcTemplate` only inside `persistence.lowlevel` and `CreditLedgerService` not directly instantiated outside `service`.

Output: 12 new Java source files under `core.billing.{persistence, persistence.lowlevel, service}`. Service is package-private (interface is public).
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
@CONVENTIONS.md
@backend/core/src/main/java/com/zeromail/core/shared/persistence/AbstractTenantOwnedEntity.java
@backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntity.java
@backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java
@backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java
@backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java
@backend/core/src/main/java/com/zeromail/core/billing/model/CreditLedger.java
@backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java
@backend/core/src/main/java/com/zeromail/core/billing/model/CreditReservationStatus.java
@backend/core/src/main/java/com/zeromail/core/billing/model/BillingTopupIntentStatus.java
@backend/core/src/main/java/com/zeromail/core/billing/model/ReservationId.java
@backend/core/src/main/java/com/zeromail/core/billing/model/CreditBalance.java
@backend/core/src/main/java/com/zeromail/core/billing/model/InsufficientCreditsException.java
@backend/core/src/main/java/com/zeromail/core/billing/model/IllegalLedgerStateException.java
@backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: JPA entities + repositories (3 entities, 3 repositories) + AdvisoryLockJdbcHelper</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryEntity.java,
    backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryRepository.java,
    backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationEntity.java,
    backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationRepository.java,
    backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentEntity.java,
    backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentRepository.java,
    backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/AdvisoryLockJdbcHelper.java
  </files>
  <behavior>
    - CreditLedgerEntryEntity persists tenant-owned with kind/amountCredits/refType/refId; static factories enforce kind invariants (TOPUP +N, RESERVE -N, SETTLE 0, RELEASE +N).
    - Wave 0 `CreditLedgerEntryUniqueTest` (Plan 00 file 5) GREEN: second insert with same (refType, refId, kind) throws DataIntegrityViolationException.
    - CreditReservationRepository.findStalePendingIds returns reservations older than `:olderThan` with status='PENDING' under `FOR UPDATE SKIP LOCKED` LIMIT cap.
    - BillingTopupIntentRepository.findByCode(String) + countByTenantIdAndStatus + @Modifying expireStale.
    - AdvisoryLockJdbcHelper.acquireTenantLock(UUID) issues `SELECT pg_advisory_xact_lock(hashtext(?))` once per call; auto-released on commit.
  </behavior>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/shared/persistence/AbstractTenantOwnedEntity.java (parent class — gives id, tenantId, createdAt, updatedAt, version columns; entities MUST extend this)
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntity.java (entity shape — protected NoArgs() + public constructor + @Column annotations + getters)
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java (repository shape — JpaRepository + @Modifying @Query + native SKIP LOCKED pattern at lines 13-42)
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java (helper component shape with constructor-injected secret material — for AdvisoryLockJdbcHelper visibility/style)
    - .planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md (lines 211–325 — entity field excerpts; lines 273–325 — repository excerpts; lines 332–355 — AdvisoryLockJdbcHelper concrete shape)
    - backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java (verify the public ScopedValue handle name `TENANT` so the Plan 05 watchdog can `ScopedValue.where(TenantContext.TENANT, stale.tenantId().toString()).run(...)` over the StaleReservation projection)
    - backend/core/src/main/resources/db/changelog/changes/014-credit-ledger-entry.yaml (column names from Plan 01 — entity @Column names MUST match)
    - backend/core/src/main/resources/db/changelog/changes/015-credit-reservation.yaml
    - backend/core/src/main/resources/db/changelog/changes/016-billing-topup-intent.yaml
  </read_first>
  <action>
**File 1: `CreditLedgerEntryEntity.java`** (`backend/core/src/main/java/com/zeromail/core/billing/persistence/`)

```java
package com.zeromail.core.billing.persistence;

import java.util.UUID;

import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Append-only journal row. Created via static factory methods only — service code never sets
 * {@code kind} directly so the kind/amount sign invariant cannot drift.
 *
 * <p><b>Sign invariant:</b>
 * <ul>
 *   <li>{@code TOPUP} : positive {@code amountCredits} (credits added)</li>
 *   <li>{@code RESERVE} : negative {@code amountCredits} (credits held)</li>
 *   <li>{@code SETTLE} : {@code amountCredits = 0} (no balance change; finalizes hold)</li>
 *   <li>{@code RELEASE} : positive {@code amountCredits} (mirror of original RESERVE)</li>
 * </ul>
 *
 * <p>Available balance is {@code SUM(amount_credits) WHERE tenant_id = ?}.
 */
@Entity
@Table(name = "credit_ledger_entry")
public class CreditLedgerEntryEntity extends AbstractTenantOwnedEntity {

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "amount_credits", nullable = false)
    private int amountCredits;

    @Column(name = "ref_type", nullable = false, length = 32)
    private String refType;

    @Column(name = "ref_id", nullable = false, length = 128)
    private String refId;

    protected CreditLedgerEntryEntity() {
        // Hibernate
    }

    private CreditLedgerEntryEntity(UUID id, UUID tenantId, String kind, int amountCredits, String refType, String refId) {
        super(id, tenantId);
        this.kind = kind;
        this.amountCredits = amountCredits;
        this.refType = refType;
        this.refId = refId;
    }

    public static CreditLedgerEntryEntity topup(UUID id, UUID tenantId, int amountCredits, String sepayTransactionId) {
        if (amountCredits <= 0) throw new IllegalArgumentException("TOPUP amountCredits must be positive");
        return new CreditLedgerEntryEntity(id, tenantId, "TOPUP", amountCredits, "PAYMENT_SEPAY", sepayTransactionId);
    }

    public static CreditLedgerEntryEntity reserve(UUID id, UUID tenantId, int costCredits, UUID reservationId) {
        if (costCredits <= 0) throw new IllegalArgumentException("RESERVE costCredits must be positive");
        return new CreditLedgerEntryEntity(id, tenantId, "RESERVE", -costCredits, "RESERVATION", reservationId.toString());
    }

    public static CreditLedgerEntryEntity settle(UUID id, UUID tenantId, UUID reservationId) {
        return new CreditLedgerEntryEntity(id, tenantId, "SETTLE", 0, "RESERVATION", reservationId.toString());
    }

    public static CreditLedgerEntryEntity release(UUID id, UUID tenantId, int amountCredits, UUID reservationId) {
        if (amountCredits <= 0) throw new IllegalArgumentException("RELEASE amountCredits must be positive");
        return new CreditLedgerEntryEntity(id, tenantId, "RELEASE", amountCredits, "RESERVATION", reservationId.toString());
    }

    public String getKind() { return kind; }
    public int getAmountCredits() { return amountCredits; }
    public String getRefType() { return refType; }
    public String getRefId() { return refId; }
}
```

**File 2: `CreditLedgerEntryRepository.java`**
```java
package com.zeromail.core.billing.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditLedgerEntryRepository extends JpaRepository<CreditLedgerEntryEntity, UUID> {

    @Query("SELECT COALESCE(SUM(e.amountCredits), 0) FROM CreditLedgerEntryEntity e WHERE e.tenantId = :tenantId")
    int sumAvailableCreditsForTenant(@Param("tenantId") UUID tenantId);

    /**
     * Held credits = sum of negative RESERVE amounts whose reservation has no SETTLE/RELEASE
     * journal counterpart yet. Returned as positive integer.
     */
    @Query("""
            SELECT COALESCE(-SUM(e.amountCredits), 0)
            FROM CreditLedgerEntryEntity e
            WHERE e.tenantId = :tenantId
              AND e.kind = 'RESERVE'
              AND NOT EXISTS (
                SELECT 1 FROM CreditLedgerEntryEntity finalizing
                WHERE finalizing.refType = 'RESERVATION'
                  AND finalizing.refId = e.refId
                  AND finalizing.kind IN ('SETTLE', 'RELEASE')
              )
            """)
    int sumHeldCreditsForTenant(@Param("tenantId") UUID tenantId);
}
```

**File 3: `CreditReservationEntity.java`**
```java
package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.UUID;

import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.billing.model.CreditReservationStatus;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "credit_reservation")
public class CreditReservationEntity extends AbstractTenantOwnedEntity {

    @Column(name = "amount_credits", nullable = false)
    private int amountCredits;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_site", nullable = false, length = 16)
    private CallSite callSite;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CreditReservationStatus status;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    protected CreditReservationEntity() {
        // Hibernate
    }

    public CreditReservationEntity(UUID id, UUID tenantId, int amountCredits, CallSite callSite, CreditReservationStatus status) {
        super(id, tenantId);
        this.amountCredits = amountCredits;
        this.callSite = callSite;
        this.status = status;
    }

    public int getAmountCredits() { return amountCredits; }
    public CallSite getCallSite() { return callSite; }
    public CreditReservationStatus getStatus() { return status; }
    public Instant getFinalizedAt() { return finalizedAt; }

    public void markSettled() {
        this.status = CreditReservationStatus.SETTLED;
        this.finalizedAt = Instant.now();
    }

    public void markReleased() {
        this.status = CreditReservationStatus.RELEASED;
        this.finalizedAt = Instant.now();
    }
}
```

**File 4a: `StaleReservation.java`** (NEW per B3 — watchdog-only projection record)

Lives in `backend/core/src/main/java/com/zeromail/core/billing/persistence/StaleReservation.java`. Carries `tenantId` so the watchdog can open `ScopedValue.where(TenantContext.TENANT, ...)` BEFORE any `@TenantId`-filtered JPA call.

```java
package com.zeromail.core.billing.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.zeromail.core.billing.model.CallSite;

/**
 * Watchdog-only projection. Returned by {@code findStalePendingProjections} via raw
 * {@code JdbcTemplate} so the read deliberately bypasses Hibernate's {@code @TenantId}
 * filter — the watchdog runs without a bound {@code TenantContext} on its scheduler thread,
 * so a Hibernate-filtered read would otherwise reject every row.
 *
 * <p><b>Caller MUST bind {@link com.zeromail.core.tenant.TenantContext#TENANT} to
 * {@link #tenantId()} before any subsequent JPA operation on {@link #id()}</b> — otherwise
 * `CreditLedger.release` will fail the same filter check.
 */
public record StaleReservation(
        UUID id,
        UUID tenantId,
        OffsetDateTime createdAt,
        int amountCredits,
        CallSite callSite) {
}
```

**File 4b: `CreditReservationRepository.java`** (B3 — native SKIP LOCKED query returns full projection)

The old `findStalePendingIds` is REMOVED. The new method `findStalePendingProjections` returns `List<StaleReservation>`. Implementation lives in `core.billing.persistence.lowlevel` (Plan 02 already declared the marker package + ArchUnit allow-list). Add a custom-repository-fragment pattern: declare the method on the JPA repository interface, implement via `JdbcTemplate` in a sibling class.

Step 1 — Spring Data interface declaration:

```java
package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditReservationRepository
        extends JpaRepository<CreditReservationEntity, UUID>, CreditReservationStaleScanFragment {
}
```

Step 2 — fragment interface (in `core.billing.persistence`):

```java
package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.List;

/** Custom-fragment for the watchdog-only stale scan. JPA cannot express it cleanly because
 *  the result must bypass Hibernate's @TenantId filter (no bound tenant on scheduler thread). */
public interface CreditReservationStaleScanFragment {

    /**
     * Selects up to {@code limitRows} PENDING reservations older than {@code olderThan}.
     * Read deliberately bypasses Hibernate's @TenantId filter — the returned record carries
     * {@code tenantId} so callers can bind {@link com.zeromail.core.tenant.TenantContext#TENANT}
     * before any subsequent JPA operation. Uses {@code FOR UPDATE SKIP LOCKED} so two worker
     * pods can pick disjoint sets without contention.
     */
    List<StaleReservation> findStalePendingProjections(Instant olderThan, int limitRows);
}
```

Step 3 — implementation in `core.billing.persistence.lowlevel` (ArchUnit-allowlisted for raw JDBC):

```java
package com.zeromail.core.billing.persistence.lowlevel;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.billing.persistence.CreditReservationStaleScanFragment;
import com.zeromail.core.billing.persistence.StaleReservation;

/**
 * Native-JDBC implementation of {@link CreditReservationStaleScanFragment}. Lives in
 * {@code persistence.lowlevel} so the {@code JdbcTemplate} import passes the Plan 06
 * ArchUnit guard (`jdbc_template_only_in_lowlevel`).
 *
 * <p><b>Spring Data Repository fragment naming convention:</b> the class name MUST be
 * `<RepositoryName>Impl` for Spring Data to auto-wire it. Spring Data scans for the bean
 * named `creditReservationRepositoryImpl` (lower-camel, lives anywhere on the component
 * scan path). Hence this class is `CreditReservationRepositoryImpl` even though it lives
 * in a different package than the repository interface.
 */
@Repository
public class CreditReservationRepositoryImpl implements CreditReservationStaleScanFragment {

    private final JdbcTemplate jdbcTemplate;

    public CreditReservationRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StaleReservation> findStalePendingProjections(Instant olderThan, int limitRows) {
        return jdbcTemplate.query(
                """
                SELECT id, tenant_id, created_at, amount_credits, call_site
                  FROM credit_reservation
                 WHERE status = 'PENDING' AND created_at < ?
                 ORDER BY created_at
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """,
                (resultSet, rowIndex) -> new StaleReservation(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("tenant_id", UUID.class),
                        resultSet.getObject("created_at", OffsetDateTime.class),
                        resultSet.getInt("amount_credits"),
                        CallSite.fromId(resultSet.getString("call_site"))),
                java.sql.Timestamp.from(olderThan),
                limitRows);
    }
}
```

Verify before saving: if the project's `CreditReservationRepositoryImpl` location convention prefers the fragment to live in `core.billing.persistence` directly, move it there — but in that case `JdbcTemplate` import will trip the ArchUnit guard. Keeping it in `persistence.lowlevel` is the documented allow-listed path (D-G3 + Plan 02 `persistence/lowlevel/package-info.java`).

Add a sibling `files_modified` entry in this plan's frontmatter for `backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/CreditReservationRepositoryImpl.java` and `backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationStaleScanFragment.java` if not already present.

**File 5: `BillingTopupIntentEntity.java`** (per CONTEXT D-C1, full inline field list — W4)

```java
package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.UUID;

import com.zeromail.core.billing.model.BillingTopupIntentStatus;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "billing_topup_intent")
public class BillingTopupIntentEntity extends AbstractTenantOwnedEntity {

    @Column(name = "code", nullable = false, length = 16, unique = true)
    private String code;

    @Column(name = "amount_vnd", nullable = false)
    private long amountVnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BillingTopupIntentStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "sepay_transaction_id", length = 128)
    private String sepayTransactionId;

    protected BillingTopupIntentEntity() {
        // Hibernate
    }

    public BillingTopupIntentEntity(UUID id, UUID tenantId, String code, long amountVnd,
                                    BillingTopupIntentStatus status, Instant expiresAt) {
        super(id, tenantId);
        this.code = code;
        this.amountVnd = amountVnd;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public String getCode() { return code; }
    public long getAmountVnd() { return amountVnd; }
    public BillingTopupIntentStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public String getSepayTransactionId() { return sepayTransactionId; }

    public void markPaid(String sepayTransactionId) {
        this.status = BillingTopupIntentStatus.PAID;
        this.paidAt = Instant.now();
        this.sepayTransactionId = sepayTransactionId;
    }

    public void markExpired() {
        this.status = BillingTopupIntentStatus.EXPIRED;
    }
}
```

**File 6: `BillingTopupIntentRepository.java`** (W4 — full method signatures inline; copy verbatim)

```java
package com.zeromail.core.billing.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.billing.model.BillingTopupIntentStatus;

public interface BillingTopupIntentRepository extends JpaRepository<BillingTopupIntentEntity, UUID> {

    Optional<BillingTopupIntentEntity> findByCode(String code);

    int countByTenantIdAndStatus(UUID tenantId, BillingTopupIntentStatus status);

    Optional<BillingTopupIntentEntity> findFirstByTenantIdAndStatusOrderByCreatedAtAsc(
            UUID tenantId, BillingTopupIntentStatus status);

    /**
     * Bulk-flips PENDING intents past expiresAt to EXPIRED. {@code @Modifying} requires an
     * enclosing transaction — see {@code BillingIntentExpirySweeper.sweep()} (Plan 05) which
     * declares {@code @Transactional} per REVIEWS HIGH-4. Tests must wrap calls in
     * {@code @Transactional} or call from a {@code @Transactional} service method.
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE BillingTopupIntentEntity intent
               SET intent.status = com.zeromail.core.billing.model.BillingTopupIntentStatus.EXPIRED,
                   intent.updatedAt = CURRENT_TIMESTAMP
             WHERE intent.status = com.zeromail.core.billing.model.BillingTopupIntentStatus.PENDING
               AND intent.expiresAt < :now
            """)
    int expireStale(@Param("now") Instant now);
}
```

**Files 6a/6b/6c: BillingTopupIntent tenant-filter-bypassing projection** (REVIEWS HIGH-2 — RESOLVED)

The webhook handler runs on a request thread with NO bound `TenantContext` (`Authorization: Apikey` auth has no session/tenant). A standard `intentRepository.findByCode(...)` is a Hibernate read subject to `@TenantId` filtering — it will return empty (or fail) for every webhook. We MUST resolve `{intentId, tenantId, status, amountVnd, expiresAt, code}` via a JDBC projection that bypasses the tenant filter, then bind `ScopedValue.where(TenantContext.TENANT, lookup.tenantId().toString())` BEFORE any subsequent JPA write.

This mirrors the `StaleReservation` projection pattern from the watchdog (Task 1 file 4a/4b/4c above).

**File 6a: `BillingTopupIntentTenantLookup.java`** (`backend/core/src/main/java/com/zeromail/core/billing/persistence/`)

```java
package com.zeromail.core.billing.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.zeromail.core.billing.model.BillingTopupIntentStatus;

/**
 * Webhook-only projection. Returned by {@link BillingTopupIntentTenantLookupFragment}
 * via raw {@code JdbcTemplate} so the read deliberately bypasses Hibernate's
 * {@code @TenantId} filter — the SePay webhook handler runs without a bound
 * {@link com.zeromail.core.tenant.TenantContext} on its request thread.
 *
 * <p><b>Caller MUST bind {@link com.zeromail.core.tenant.TenantContext#TENANT} to
 * {@link #tenantId()} before any subsequent JPA operation on {@link #id()}.</b>
 */
public record BillingTopupIntentTenantLookup(
        UUID id,
        UUID tenantId,
        String code,
        long amountVnd,
        BillingTopupIntentStatus status,
        OffsetDateTime expiresAt) {
}
```

**File 6b: `BillingTopupIntentTenantLookupFragment.java`** (same package)

```java
package com.zeromail.core.billing.persistence;

import java.util.Optional;

/**
 * Custom-fragment for the SePay webhook tenant-resolution scan. JPA cannot express it
 * cleanly because the result must bypass Hibernate's @TenantId filter (no bound tenant
 * on the webhook request thread).
 */
public interface BillingTopupIntentTenantLookupFragment {

    /**
     * Looks up an intent by code, returning a projection that includes {@code tenantId}.
     * Bypasses Hibernate's @TenantId filter so callers can resolve the tenant before
     * binding {@link com.zeromail.core.tenant.TenantContext#TENANT} for subsequent JPA
     * writes.
     */
    Optional<BillingTopupIntentTenantLookup> findTenantLookupByCode(String code);
}
```

**File 6c: `BillingTopupIntentRepositoryImpl.java`** (`persistence/lowlevel/`)

```java
package com.zeromail.core.billing.persistence.lowlevel;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.zeromail.core.billing.model.BillingTopupIntentStatus;
import com.zeromail.core.billing.persistence.BillingTopupIntentTenantLookup;
import com.zeromail.core.billing.persistence.BillingTopupIntentTenantLookupFragment;

/**
 * Native-JDBC implementation of {@link BillingTopupIntentTenantLookupFragment}. Lives in
 * {@code persistence.lowlevel} so the {@code JdbcTemplate} import passes the Plan 06
 * ArchUnit guard (`jdbc_template_only_in_lowlevel`).
 *
 * <p>Spring Data convention: the implementation class name matches the repository
 * interface name + {@code Impl}. The repository interface is
 * {@code BillingTopupIntentRepository}, so this class lives at
 * {@code BillingTopupIntentRepositoryImpl} regardless of package — Spring Data
 * scans the bean by name (lower-camel: {@code billingTopupIntentRepositoryImpl}).
 */
@Repository
public class BillingTopupIntentRepositoryImpl implements BillingTopupIntentTenantLookupFragment {

    private final JdbcTemplate jdbcTemplate;

    public BillingTopupIntentRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<BillingTopupIntentTenantLookup> findTenantLookupByCode(String code) {
        return jdbcTemplate.query(
                """
                SELECT id, tenant_id, code, amount_vnd, status, expires_at
                  FROM billing_topup_intent
                 WHERE code = ?
                 LIMIT 1
                """,
                (resultSet, rowIndex) -> new BillingTopupIntentTenantLookup(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("tenant_id", UUID.class),
                        resultSet.getString("code"),
                        resultSet.getLong("amount_vnd"),
                        BillingTopupIntentStatus.fromId(resultSet.getString("status")),
                        resultSet.getObject("expires_at", OffsetDateTime.class)),
                code).stream().findFirst();
    }
}
```

**File 6d: extend `BillingTopupIntentRepository.java`** to compose the fragment:

Replace the original interface declaration:

```java
public interface BillingTopupIntentRepository
        extends JpaRepository<BillingTopupIntentEntity, UUID>, BillingTopupIntentTenantLookupFragment {
    // existing method declarations unchanged: findByCode, countByTenantIdAndStatus,
    // findFirstByTenantIdAndStatusOrderByCreatedAtAsc, expireStale.
}
```

(Spring Data auto-wires the lowlevel `BillingTopupIntentRepositoryImpl` into the repository proxy. The `@Repository` annotation is required on the impl class for component scanning to find it.)

**File 7: `AdvisoryLockJdbcHelper.java`** (`persistence/lowlevel/`)
```java
package com.zeromail.core.billing.persistence.lowlevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Acquires a transaction-scoped Postgres advisory lock keyed by {@code hashtext(tenantId)}.
 * Auto-released when the surrounding {@code @Transactional} commits or rolls back.
 *
 * <p><b>Pitfall 3 (CONTEXT):</b> {@code hashtext} returns int4. At ~65k tenants the
 * birthday-bound collision probability becomes non-trivial. Two tenants colliding briefly
 * serialize each other's reserves — correctness no-op (both flows are tenant-isolated by
 * {@code @TenantId}), at worst a brief latency hiccup. v1 stays single-key.
 *
 * <p><b>ArchUnit (Plan 06):</b> No class outside {@code core.billing.persistence.lowlevel}
 * may use {@link JdbcTemplate}. Package-private visibility ensures only
 * {@code core.billing.persistence} callers reach this helper.
 */
@Component
public class AdvisoryLockJdbcHelper {

    private final JdbcTemplate jdbcTemplate;

    public AdvisoryLockJdbcHelper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquireTenantLock(UUID tenantId) {
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

**Visibility decision (REVIEWS HIGH-5 — RESOLVED):** The class is `public` and the constructor + `acquireTenantLock(UUID)` method are `public`. This is required because `CreditLedgerService` (in `core.billing.service`) injects and calls this helper across packages — Java's package-private visibility would make the import non-compilable. The `JdbcTemplate` boundary is enforced separately by Plan 06's ArchUnit rule `jdbc_template_only_in_lowlevel`, which gates by *package* (`..core.billing.persistence.lowlevel..`), not by Java visibility. This matches the Phase 1 `RefreshTokenCipher` precedent (public class, ArchUnit-package-bounded). Earlier drafts that suggested package-private visibility were inconsistent with the cross-package import in Task 3 and are explicitly REJECTED here.

  </action>
  <verify>
    <automated>./gradlew :backend:core:compileJava 2>&1 | tee /tmp/compile3-1.log | grep -E "BUILD SUCCESSFUL|BUILD FAILED" | head -1 | grep -q SUCCESSFUL; test -f backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryEntity.java; test -f backend/core/src/main/java/com/zeromail/core/billing/persistence/StaleReservation.java; test -f backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationStaleScanFragment.java; test -f backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/CreditReservationRepositoryImpl.java; grep -q "extends AbstractTenantOwnedEntity" backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryEntity.java; grep -q "static CreditLedgerEntryEntity reserve" backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryEntity.java; grep -q "FOR UPDATE SKIP LOCKED" backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/CreditReservationRepositoryImpl.java; grep -q "findStalePendingProjections" backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationStaleScanFragment.java; grep -q "pg_advisory_xact_lock" backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/AdvisoryLockJdbcHelper.java; grep -q "JdbcTemplate" backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/AdvisoryLockJdbcHelper.java</automated>
  </verify>
  <done>3 entity classes (each extending AbstractTenantOwnedEntity); 3 repositories with the queries listed; CreditReservationStaleScanFragment + lowlevel CreditReservationRepositoryImpl + StaleReservation projection record landed (B3 watchdog-safe scan); AdvisoryLockJdbcHelper has the literal `pg_advisory_xact_lock(hashtext(?))` SQL; ./gradlew :backend:core:compileJava BUILD SUCCESSFUL.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: BillingProperties + SepayApiKeyVerifier + TopupCodeGenerator (config + utility)</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/billing/service/BillingProperties.java,
    backend/core/src/main/java/com/zeromail/core/billing/service/SepayApiKeyVerifier.java,
    backend/core/src/main/java/com/zeromail/core/billing/service/TopupCodeGenerator.java
  </files>
  <behavior>
    - BillingProperties is a @ConfigurationProperties record at prefix `zero-mail.billing` with sepay.webhookApiKey @NotBlank, vndPerCredit @Min(1) default 1000, maxPendingIntentsPerTenant default 5, intentExpiry default PT24H.
    - Wave 0 `SepayApiKeyVerifierTest` (Plan 00 file 3) GREEN: null returns false, "Bearer abc" returns false, "Apikey wrong-key" returns false, "Apikey expected-key" returns true.
    - Wave 0 `TopupCodeGeneratorTest` (Plan 00 file 4) GREEN: 100 codes match `[0-9A-HJKMNPQRSTVWXYZ]{8}` (no I/L/O/U); collision retry succeeds within 3 attempts; collision retry throws after 3 exhausted attempts.
  </behavior>
  <read_first>
    - .planning/phases/02B-billing-prepaid-credits/02B-RESEARCH.md (lines 711–722 — BillingProperties shape; lines 545–586 — SepayApiKeyFilter shape uses MessageDigest.isEqual)
    - .planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md (lines 408–438 — BillingProperties record + nested SepayProperties; lines 410–417 — SepayApiKeyVerifier rationale; lines 745–774 — TopupCodeGenerator concrete shape from RESEARCH §"Pattern 6")
    - backend/api/src/main/java/com/zeromail/api/config/ZeroMailApiProperties.java (existing @ConfigurationProperties record analog — copy nested-record + @Validated + @DefaultValue style)
  </read_first>
  <action>
**File 8: `BillingProperties.java`** (`core/billing/service/`)
```java
package com.zeromail.core.billing.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "zero-mail.billing")
@Validated
public record BillingProperties(
        @Valid @NotNull SepayProperties sepay,
        @Min(1) @DefaultValue("1000") long vndPerCredit,
        @Min(1) @DefaultValue("5") int maxPendingIntentsPerTenant,
        @DefaultValue("PT24H") Duration intentExpiry) {

    public record SepayProperties(@NotBlank String webhookApiKey) {
    }
}
```

Registration: add `@EnableConfigurationProperties(BillingProperties.class)` on a config class — Plan 04 owns the API-side registration; create a `core.billing.service.BillingConfiguration` class here for core-test boot:
```java
package com.zeromail.core.billing.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BillingProperties.class)
class BillingConfiguration {
}
```

(Add `BillingConfiguration.java` as a sibling file in this same task — it's tiny, single concern, makes `BillingProperties` visible inside `@SpringBootTest` boots that scan `core.billing.service`.)

**Core test base extension (REVIEWS HIGH-6 — RESOLVED):** `BillingProperties.SepayProperties.webhookApiKey` is `@NotBlank`, so any core `@SpringBootTest` that activates `BillingConfiguration` (or that boots the full app context including `core.billing.service`) will fail validation at startup unless the property is present. Plan 04 covers the api test base; Plan 05 covers the worker test base; this plan owns the core test base.

Append the following lines inside `static void props(DynamicPropertyRegistry r)` in `backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java` (existing analog at lines 31–47 — match indentation and identifier-name style):

```java
// REVIEWS HIGH-6: BillingProperties has @NotBlank sepay.webhookApiKey, so any core
// @SpringBootTest that includes BillingConfiguration must inject these values or fail
// validation at startup.
r.add("zero-mail.billing.sepay.webhook-api-key", () -> "test-sepay-key-fixture");
r.add("zero-mail.billing.vnd-per-credit", () -> "1000");
r.add("zero-mail.billing.max-pending-intents-per-tenant", () -> "5");
r.add("zero-mail.billing.intent-expiry", () -> "PT24H");
```

After the edit, run `./gradlew :backend:core:test --tests "*BillingDomainBoundaryArchTest*"` (Plan 06 flips this test to GREEN) plus any core billing `@SpringBootTest` from Wave 0 (Plan 00 outputs that extend `PostgresContainerTest`) to confirm the full app context boots without `ConstraintViolationException` on `webhookApiKey`.

**File 9: `SepayApiKeyVerifier.java`** (Constant-time API-key compare)
```java
package com.zeromail.core.billing.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Component;

/**
 * Constant-time API-key comparator for the SePay webhook auth header.
 *
 * <p><b>Why constant-time:</b> the API key is the only secret on the wire; any timing leak
 * on the comparison lets an attacker iterate byte-by-byte. {@link MessageDigest#isEqual} is
 * documented constant-time since Java 6u17 [CITED: codahale.com/a-lesson-in-timing-attacks].
 * NEVER use {@code String.equals} or {@code Arrays.equals} — both short-circuit on first
 * inequality.
 *
 * <p><b>Header format:</b> {@code Authorization: Apikey YOUR_API_KEY} (literal word
 * {@code Apikey}, single space, then the secret) per SePay developer docs.
 */
@Component
public class SepayApiKeyVerifier {

    private static final String AUTH_PREFIX = "Apikey ";

    private final byte[] expectedKeyBytes;

    public SepayApiKeyVerifier(BillingProperties billingProperties) {
        // Cache as bytes once so MessageDigest.isEqual gets pre-encoded inputs and the
        // plain String never lingers as a field that could leak via toString().
        this.expectedKeyBytes = billingProperties.sepay().webhookApiKey().getBytes(StandardCharsets.UTF_8);
    }

    public boolean verify(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(AUTH_PREFIX)) {
            return false;
        }
        byte[] providedKeyBytes = authorizationHeader.substring(AUTH_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedKeyBytes, providedKeyBytes);
    }
}
```

(Note: This class is `public` because the API filter in Plan 04 will inject it. Constructor takes `BillingProperties`; if Wave 0 `SepayApiKeyVerifierTest` constructs it with a raw String, add a package-private convenience constructor `SepayApiKeyVerifier(String expectedApiKey)` for testability — explicit over hiding via reflection. Plan 04 only uses the public `BillingProperties` constructor.)

Add the package-private testability constructor:
```java
SepayApiKeyVerifier(String expectedApiKey) {
    this.expectedKeyBytes = expectedApiKey.getBytes(StandardCharsets.UTF_8);
}
```

**File 10: `TopupCodeGenerator.java`** (Crockford base32, hand-rolled per RESEARCH §"Don't Hand-Roll")
```java
package com.zeromail.core.billing.service;

import java.security.SecureRandom;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

/**
 * Generates 8-char Crockford-base32 top-up codes that users paste into bank-transfer memos.
 *
 * <p>Alphabet (32 chars, excludes I/L/O/U to avoid confusion with 1/0):
 * {@code 0123456789ABCDEFGHJKMNPQRSTVWXYZ}.
 *
 * <p>Hand-rolled because {@code commons-codec} is not on the project classpath (RESEARCH
 * §"Don't Hand-Roll" — pulling commons-codec in for one alphabet adds ~280KB to the worker
 * container for ~30 LOC saved). {@link SecureRandom} draws bytes uniformly from the
 * alphabet via modulo-32 (cheap and unbiased because 32 evenly divides 256).
 */
@Component
public class TopupCodeGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 8;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @param isAvailable predicate returning true if the candidate code is unused (e.g.,
     *                    `repository.findByCode(c).isEmpty()`).
     * @param maxAttempts max number of generation attempts before giving up.
     * @throws IllegalStateException if all attempts collide.
     */
    public String generateUniqueCode(Predicate<String> isAvailable, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String candidate = newCode();
            if (isAvailable.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique top-up code after " + maxAttempts + " attempts");
    }

    private String newCode() {
        char[] characters = new char[CODE_LENGTH];
        for (int index = 0; index < CODE_LENGTH; index++) {
            characters[index] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
        }
        return new String(characters);
    }
}
```

After all 3 files saved, flip Wave 0 `@Disabled` off in `SepayApiKeyVerifierTest.java` and `TopupCodeGeneratorTest.java` (remove `@Disabled` annotations on each `@Test`) so those two pure-unit tests run GREEN. Run `./gradlew :backend:core:test --tests "*SepayApiKeyVerifierTest*" --tests "*TopupCodeGeneratorTest*"` to confirm.
  </action>
  <verify>
    <automated>./gradlew :backend:core:compileJava 2>&1 | grep -q SUCCESSFUL; grep -q "MessageDigest.isEqual" backend/core/src/main/java/com/zeromail/core/billing/service/SepayApiKeyVerifier.java; ! grep -q 'Arrays.equals\|"\.equals\b' backend/core/src/main/java/com/zeromail/core/billing/service/SepayApiKeyVerifier.java; grep -q '0123456789ABCDEFGHJKMNPQRSTVWXYZ' backend/core/src/main/java/com/zeromail/core/billing/service/TopupCodeGenerator.java; grep -q 'zero-mail.billing.sepay.webhook-api-key' backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java; grep -q 'test-sepay-key-fixture' backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java; ./gradlew :backend:core:test --tests "*SepayApiKeyVerifierTest*" --tests "*TopupCodeGeneratorTest*" 2>&1 | grep -E "BUILD SUCCESSFUL|tests completed.*0 failed"</automated>
  </verify>
  <done>BillingProperties record has 4 fields with @DefaultValue + nested SepayProperties; SepayApiKeyVerifier uses MessageDigest.isEqual (no String.equals / Arrays.equals); TopupCodeGenerator uses Crockford alphabet without I/L/O/U; Wave 0 SepayApiKeyVerifierTest + TopupCodeGeneratorTest flipped to GREEN (4+3 = 7 unit tests pass).</done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: CreditLedgerService + BillingTopupService (the two services)</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java,
    backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java
  </files>
  <behavior>
    - CreditLedgerService.reserve runs in REQUIRES_NEW: acquires advisory lock → reads sumAvailableCredits → throws InsufficientCreditsException if < callSite.cost() → INSERT credit_reservation(PENDING) + INSERT credit_ledger_entry(RESERVE, -cost) → returns new ReservationId.
    - settle runs in REQUIRED: load reservation, throw IllegalLedgerStateException if RELEASED, no-op if SETTLED, else mark SETTLED + INSERT SETTLE journal entry (catch DataIntegrityViolationException = idempotent).
    - release symmetric: throw if SETTLED, no-op if RELEASED, else mark RELEASED + INSERT RELEASE journal entry.
    - balance read-only: returns CreditBalance(sumAvailableCreditsForTenant, sumHeldCreditsForTenant).
    - BillingTopupService.createIntent: count PENDING intents for tenant; if >= maxPendingIntentsPerTenant, expire oldest; generate unique 8-char code with up to 3 retries; INSERT intent with status=PENDING + expiresAt=now+intentExpiry; return DTO.
    - BillingTopupService.applyWebhook: validate transferType=="in"; lookup intent by referenceCode (uppercase-normalized); if not found → log unknown_code event + return; if status != PENDING or expiresAt < now → log + return; if amount mismatch → log + return; else: in single TX, mark intent PAID + insert TOPUP entry; catch DataIntegrityViolationException = replay no-op.
    - All Wave 0 `CreditLedgerConcurrentReserveTest` + `CreditLedgerSettleIdempotentTest` flip GREEN.
  </behavior>
  <read_first>
    - .planning/phases/02B-billing-prepaid-credits/02B-RESEARCH.md (§"Pattern 2" — full reserve/settle skeleton with @Transactional + advisory lock + DataIntegrityViolationException catch)
    - .planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md (lines 365–408 — service shape; lines 401–409 — BillingTopupService two methods; lines 1059–1083 — privacy log format)
    - .planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md (D-A1, D-A2, D-D1..D-D4, D-C2 max-5-PENDING, D-C3 webhook resolution flow, D-I1 SePay log events list)
    - backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java (service shape — @Service + constructor injection + @Transactional readOnly = true on reads + privacy log line `event=...`)
    - backend/core/src/main/java/com/zeromail/core/billing/model/CreditLedger.java (interface contract — implement these 4 methods exactly)
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditLedgerEntryEntity.java (Task 1 output — static factories signatures)
  </read_first>
  <action>
**File 11: `CreditLedgerService.java`** (`core/billing/service/`) — package-private class implementing public interface (D-G3 ban on direct instantiation outside service package)

```java
package com.zeromail.core.billing.service;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.billing.model.CallSite;
import com.zeromail.core.billing.model.CreditBalance;
import com.zeromail.core.billing.model.CreditLedger;
import com.zeromail.core.billing.model.CreditReservationStatus;
import com.zeromail.core.billing.model.IllegalLedgerStateException;
import com.zeromail.core.billing.model.InsufficientCreditsException;
import com.zeromail.core.billing.model.ReservationId;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.billing.persistence.CreditReservationEntity;
import com.zeromail.core.billing.persistence.CreditReservationRepository;
import com.zeromail.core.billing.persistence.lowlevel.AdvisoryLockJdbcHelper;

@Service
class CreditLedgerService implements CreditLedger {

    private static final Logger log = LoggerFactory.getLogger(CreditLedgerService.class);

    private final CreditLedgerEntryRepository entryRepository;
    private final CreditReservationRepository reservationRepository;
    private final AdvisoryLockJdbcHelper advisoryLockHelper;

    CreditLedgerService(
            CreditLedgerEntryRepository entryRepository,
            CreditReservationRepository reservationRepository,
            AdvisoryLockJdbcHelper advisoryLockHelper) {
        this.entryRepository = entryRepository;
        this.reservationRepository = reservationRepository;
        this.advisoryLockHelper = advisoryLockHelper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationId reserve(UUID tenantId, CallSite callSite) {
        advisoryLockHelper.acquireTenantLock(tenantId);

        int availableCredits = entryRepository.sumAvailableCreditsForTenant(tenantId);
        if (availableCredits < callSite.cost()) {
            throw new InsufficientCreditsException();  // no balance number — privacy invariant
        }

        UUID reservationUuid = UUID.randomUUID();
        CreditReservationEntity reservation = new CreditReservationEntity(
                reservationUuid, tenantId, callSite.cost(), callSite, CreditReservationStatus.PENDING);
        reservationRepository.save(reservation);

        CreditLedgerEntryEntity reserveEntry = CreditLedgerEntryEntity.reserve(
                UUID.randomUUID(), tenantId, callSite.cost(), reservationUuid);
        entryRepository.save(reserveEntry);

        log.info("event=credit_reserved tenantId={} reservationId={}", tenantId, reservationUuid);
        return new ReservationId(reservationUuid);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void settle(ReservationId reservationId) {
        UUID reservationUuid = reservationId.value();
        Optional<CreditReservationEntity> maybeReservation = reservationRepository.findById(reservationUuid);
        if (maybeReservation.isEmpty()) {
            throw new IllegalLedgerStateException("Reservation not found: " + reservationUuid);
        }
        CreditReservationEntity reservation = maybeReservation.get();

        if (reservation.getStatus() == CreditReservationStatus.RELEASED) {
            throw new IllegalLedgerStateException("Cannot settle a RELEASED reservation: " + reservationUuid);
        }
        if (reservation.getStatus() == CreditReservationStatus.SETTLED) {
            return;  // idempotent no-op
        }

        reservation.markSettled();
        reservationRepository.save(reservation);

        CreditLedgerEntryEntity settleEntry = CreditLedgerEntryEntity.settle(
                UUID.randomUUID(), reservation.getTenantId(), reservationUuid);
        try {
            entryRepository.saveAndFlush(settleEntry);
        } catch (DataIntegrityViolationException duplicate) {
            // UNIQUE(ref_type, ref_id, kind) — second SETTLE is a no-op at journal layer
        }

        log.info("event=credit_settled tenantId={} reservationId={}",
                reservation.getTenantId(), reservationUuid);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void release(ReservationId reservationId) {
        UUID reservationUuid = reservationId.value();
        Optional<CreditReservationEntity> maybeReservation = reservationRepository.findById(reservationUuid);
        if (maybeReservation.isEmpty()) {
            throw new IllegalLedgerStateException("Reservation not found: " + reservationUuid);
        }
        CreditReservationEntity reservation = maybeReservation.get();

        if (reservation.getStatus() == CreditReservationStatus.SETTLED) {
            throw new IllegalLedgerStateException("Cannot release a SETTLED reservation: " + reservationUuid);
        }
        if (reservation.getStatus() == CreditReservationStatus.RELEASED) {
            return;  // idempotent no-op
        }

        reservation.markReleased();
        reservationRepository.save(reservation);

        CreditLedgerEntryEntity releaseEntry = CreditLedgerEntryEntity.release(
                UUID.randomUUID(), reservation.getTenantId(), reservation.getAmountCredits(), reservationUuid);
        try {
            entryRepository.saveAndFlush(releaseEntry);
        } catch (DataIntegrityViolationException duplicate) {
            // UNIQUE(ref_type, ref_id, kind) — second RELEASE is a no-op at journal layer
        }

        log.info("event=credit_released tenantId={} reservationId={}",
                reservation.getTenantId(), reservationUuid);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditBalance balance(UUID tenantId) {
        int availableCredits = entryRepository.sumAvailableCreditsForTenant(tenantId);
        int heldCredits = entryRepository.sumHeldCreditsForTenant(tenantId);
        return new CreditBalance(availableCredits, heldCredits);
    }
}
```

**File 12: `BillingTopupService.java`**

```java
package com.zeromail.core.billing.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.billing.model.BillingTopupIntentStatus;
import com.zeromail.core.billing.persistence.BillingTopupIntentEntity;
import com.zeromail.core.billing.persistence.BillingTopupIntentRepository;
import com.zeromail.core.billing.persistence.BillingTopupIntentTenantLookup;
import com.zeromail.core.billing.persistence.CreditLedgerEntryEntity;
import com.zeromail.core.billing.persistence.CreditLedgerEntryRepository;
import com.zeromail.core.tenant.TenantContext;

/**
 * Top-up intent lifecycle + SePay webhook handling.
 */
@Service
public class BillingTopupService {

    private static final Logger log = LoggerFactory.getLogger(BillingTopupService.class);

    private final BillingTopupIntentRepository intentRepository;
    private final CreditLedgerEntryRepository entryRepository;
    private final TopupCodeGenerator topupCodeGenerator;
    private final BillingProperties billingProperties;

    public BillingTopupService(
            BillingTopupIntentRepository intentRepository,
            CreditLedgerEntryRepository entryRepository,
            TopupCodeGenerator topupCodeGenerator,
            BillingProperties billingProperties) {
        this.intentRepository = intentRepository;
        this.entryRepository = entryRepository;
        this.topupCodeGenerator = topupCodeGenerator;
        this.billingProperties = billingProperties;
    }

    /**
     * Per CONTEXT D-C2: max 5 PENDING intents per tenant; 6th create expires the oldest.
     *
     * <p><b>Minimum amount (REVIEWS HIGH-7):</b> rejects {@code amountVnd < vndPerCredit} with
     * {@link IllegalArgumentException} so a created intent always credits at least 1 unit.
     * This is the FIRST line of defense against the 0-credit topup bug — a downstream
     * webhook with mismatching amount is already blocked by the mismatch check, but
     * rejecting at intent creation gives the user immediate UX feedback (Phase 5 maps to a
     * 400 with {@code error.billing.topup.amount_too_small}).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public BillingTopupIntentEntity createIntent(UUID tenantId, long amountVnd) {
        long vndPerCredit = billingProperties.vndPerCredit();
        if (amountVnd < vndPerCredit) {
            throw new IllegalArgumentException(
                    "Top-up amount must be at least " + vndPerCredit + " VND (one credit)");
        }
        int pendingCount = intentRepository.countByTenantIdAndStatus(tenantId, BillingTopupIntentStatus.PENDING);
        if (pendingCount >= billingProperties.maxPendingIntentsPerTenant()) {
            intentRepository.findFirstByTenantIdAndStatusOrderByCreatedAtAsc(tenantId, BillingTopupIntentStatus.PENDING)
                    .ifPresent(oldest -> {
                        oldest.markExpired();
                        intentRepository.save(oldest);
                    });
        }

        String code = topupCodeGenerator.generateUniqueCode(
                candidate -> intentRepository.findByCode(candidate).isEmpty(),
                3);

        Instant expiresAt = Instant.now().plus(billingProperties.intentExpiry());
        BillingTopupIntentEntity intent = new BillingTopupIntentEntity(
                UUID.randomUUID(), tenantId, code, amountVnd, BillingTopupIntentStatus.PENDING, expiresAt);
        intentRepository.save(intent);
        log.info("event=billing_topup_intent_created tenantId={} amountVnd={}", tenantId, amountVnd);
        return intent;
    }

    /**
     * Per CONTEXT D-C3: parse referenceCode (or content fallback per Pitfall 1), uppercase,
     * lookup intent. Unknown / mismatch / expired → ack 200 with opaque event log; happy path
     * → in single TX: mark intent PAID + insert TOPUP entry; replay protected by UNIQUE on
     * (ref_type='PAYMENT_SEPAY', ref_id=transactionId, kind='TOPUP').
     *
     * <p><b>Tenant binding (REVIEWS HIGH-2 — RESOLVED):</b> the SePay webhook runs on a
     * request thread WITHOUT a bound {@link com.zeromail.core.tenant.TenantContext}
     * (Authorization is API-key, not session). Calling {@code intentRepository.findByCode}
     * directly here would trip Hibernate's {@code @TenantId} filter and return empty. We
     * use {@link BillingTopupIntentRepository#findTenantLookupByCode} (raw JDBC, bypasses
     * the filter) to resolve {@code tenantId}, bind it via {@code ScopedValue.where}, and
     * THEN perform JPA writes inside that scope so {@code @TenantId} accepts them.
     *
     * <p>Method itself is NOT {@code @Transactional} — the transaction is opened inside
     * {@code applyWebhookForTenant} which runs under the bound ScopedValue.
     */
    public void applyWebhook(long sepayTransactionId, String referenceCode, String content, String transferType, long transferAmountVnd) {
        if (!"in".equalsIgnoreCase(transferType)) {
            log.warn("event=sepay_webhook_non_inbound_ignored");
            return;
        }
        Optional<String> codeOpt = extractIntentCode(referenceCode, content);
        if (codeOpt.isEmpty()) {
            log.warn("event=sepay_webhook_unknown_code");
            return;
        }
        String code = codeOpt.get();
        Optional<BillingTopupIntentTenantLookup> maybeLookup = intentRepository.findTenantLookupByCode(code);
        if (maybeLookup.isEmpty()) {
            log.warn("event=sepay_webhook_unknown_code");
            return;
        }
        BillingTopupIntentTenantLookup lookup = maybeLookup.get();
        if (lookup.status() != BillingTopupIntentStatus.PENDING) {
            log.warn("event=sepay_webhook_intent_not_pending");
            return;
        }
        if (lookup.expiresAt().toInstant().isBefore(Instant.now())) {
            log.warn("event=sepay_webhook_intent_expired");
            return;
        }
        if (lookup.amountVnd() != transferAmountVnd) {
            log.warn("event=sepay_webhook_amount_mismatch intentVnd={} actualVnd={}",
                    lookup.amountVnd(), transferAmountVnd);
            return;
        }

        // Bind TenantContext from the JDBC-resolved lookup BEFORE any JPA write so
        // Hibernate @TenantId filter accepts the subsequent reads/writes.
        ScopedValue.where(TenantContext.TENANT, lookup.tenantId().toString()).run(() ->
                applyWebhookForTenant(lookup.id(), sepayTransactionId, transferAmountVnd));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    void applyWebhookForTenant(UUID intentId, long sepayTransactionId, long transferAmountVnd) {
        // Re-fetch under bound TenantContext so JPA dirty checking + @TenantId filter both
        // operate correctly. The lookup projection above already validated status/amount/
        // expiry; here we only re-load to mutate.
        Optional<BillingTopupIntentEntity> maybeIntent = intentRepository.findById(intentId);
        if (maybeIntent.isEmpty()) {
            // Intent was deleted between lookup and re-fetch — treat as race no-op.
            log.warn("event=sepay_webhook_intent_vanished_post_lookup");
            return;
        }
        BillingTopupIntentEntity intent = maybeIntent.get();
        if (intent.getStatus() != BillingTopupIntentStatus.PENDING) {
            // Concurrent webhook already processed this intent — replay no-op.
            log.info("event=sepay_topup_replay_ignored");
            return;
        }

        long credits = transferAmountVnd / billingProperties.vndPerCredit();
        long roundingLossVnd = transferAmountVnd - (credits * billingProperties.vndPerCredit());
        if (roundingLossVnd > 0) {
            log.info("event=sepay_topup_rounding_loss vndLost={}", roundingLossVnd);
        }
        if (credits <= 0) {
            // REVIEWS HIGH-7 defense-in-depth: createIntent already enforces amountVnd >=
            // vndPerCredit, and the mismatch check above blocks unequal amounts. This
            // handles the residual edge case where vndPerCredit is reconfigured between
            // intent creation and webhook arrival. ACK 200 to stop SePay retries; do NOT
            // call CreditLedgerEntryEntity.topup() (which throws on credits <= 0); intent
            // stays PENDING for manual reconciliation.
            log.warn("event=sepay_topup_below_min_credits transferAmountVnd={} vndPerCredit={}",
                    transferAmountVnd, billingProperties.vndPerCredit());
            return;
        }

        try {
            intent.markPaid(String.valueOf(sepayTransactionId));
            intentRepository.save(intent);
            CreditLedgerEntryEntity topupEntry = CreditLedgerEntryEntity.topup(
                    UUID.randomUUID(), intent.getTenantId(), Math.toIntExact(credits), String.valueOf(sepayTransactionId));
            entryRepository.saveAndFlush(topupEntry);
            log.info("event=sepay_topup_credited tenantId={} credits={}", intent.getTenantId(), credits);
        } catch (DataIntegrityViolationException replayDuplicate) {
            // UNIQUE(ref_type, ref_id, kind) on credit_ledger_entry OR partial UNIQUE on
            // billing_topup_intent.sepay_transaction_id caught the replay — return 200 ack.
            log.info("event=sepay_topup_replay_ignored");
        }
    }

    /** Pitfall 1: try referenceCode first, fall back to extracting an 8-char Crockford-shape token from content. */
    private Optional<String> extractIntentCode(String referenceCode, String content) {
        java.util.regex.Pattern crockfordEight = java.util.regex.Pattern.compile("[0-9A-HJKMNPQRSTVWXYZ]{8}");
        if (referenceCode != null) {
            String normalized = referenceCode.trim().toUpperCase(java.util.Locale.ROOT);
            if (crockfordEight.matcher(normalized).matches()) return Optional.of(normalized);
        }
        if (content != null) {
            java.util.regex.Matcher matcher = crockfordEight.matcher(content.toUpperCase(java.util.Locale.ROOT));
            if (matcher.find()) return Optional.of(matcher.group());
        }
        return Optional.empty();
    }
}
```

After saving both files, flip Wave 0 `@Disabled` off in `CreditLedgerConcurrentReserveTest.java` + `CreditLedgerSettleIdempotentTest.java` + `CreditLedgerEntryUniqueTest.java`. Run `./gradlew :backend:core:test --tests "com.zeromail.core.billing.*"` → all unit + integration tests should pass (the integration tests need Testcontainers Postgres which `PostgresContainerTest` boots automatically). If `CreditLedgerConcurrentReserveTest` is flaky, double-check that the test uses `StructuredTaskScope` + `CountDownLatch` simultaneous-release pattern (per CONTEXT D-A3) — Spring's default test transaction CAN swallow the REQUIRES_NEW propagation if the test method itself is `@Transactional`; the Wave 0 test must NOT be `@Transactional`.
  </action>
  <verify>
    <automated>./gradlew :backend:core:compileJava 2>&1 | grep -q SUCCESSFUL; grep -q "Propagation.REQUIRES_NEW" backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java; grep -q "advisoryLockHelper.acquireTenantLock" backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java; grep -q "DataIntegrityViolationException" backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java; grep -q "DataIntegrityViolationException" backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java; grep -q "event=sepay_topup_credited" backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java; grep -q "findTenantLookupByCode" backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java; grep -q "ScopedValue.where(TenantContext.TENANT" backend/core/src/main/java/com/zeromail/core/billing/service/BillingTopupService.java; test -f backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentTenantLookup.java; test -f backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentTenantLookupFragment.java; test -f backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/BillingTopupIntentRepositoryImpl.java; ./gradlew :backend:core:test --tests "com.zeromail.core.billing.service.*" 2>&1 | grep -E "BUILD SUCCESSFUL"</automated>
  </verify>
  <done>CreditLedgerService implements all 4 CreditLedger methods with documented propagation; BillingTopupService.createIntent enforces max-5-PENDING and 3-attempt code retry; BillingTopupService.applyWebhook implements full D-C3 flow including referenceCode + content fallback (Pitfall 1) + UNIQUE-replay catch; Wave 0 core tests CreditLedgerConcurrentReserveTest + CreditLedgerSettleIdempotentTest + CreditLedgerEntryUniqueTest flipped GREEN.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| `core.billing.service` ↔ `core.billing.persistence.lowlevel` | Service may invoke AdvisoryLockJdbcHelper but cannot use raw JdbcTemplate directly — ArchUnit guard (Plan 06). |
| `core.billing.service` ↔ caller (Phase 2C / `backend/api`) | Callers depend on `CreditLedger` interface only; direct `CreditLedgerService` instantiation banned by ArchUnit (Plan 06). |
| Webhook payload data → ledger | `BillingTopupService.applyWebhook` validates transferType/code/amount/expiry/PENDING-status before crediting; replay protected by UNIQUE constraints. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-02B-03-01 | Tampering | Reserve race / double-spend (T3 from phase threat model) | mitigate | `pg_advisory_xact_lock(hashtext(tenantId))` inside REQUIRES_NEW serializes the SUM-balance + RESERVE INSERT critical section per tenant. Wave 0 ConcurrentReserveTest verifies (10 threads on available=5 → exactly 5 OK). |
| T-02B-03-02 | Tampering | SePay replay (T2) | mitigate | UNIQUE `(ref_type='PAYMENT_SEPAY', ref_id=sepayTransactionId, kind='TOPUP')` on `credit_ledger_entry` + partial UNIQUE on `billing_topup_intent.sepay_transaction_id` — second insert silently caught at `DataIntegrityViolationException` and logged as `event=sepay_topup_replay_ignored`. |
| T-02B-03-03 | Tampering | Watchdog double-release (T6) | mitigate | UNIQUE on journal blocks duplicate RELEASE entries; CreditLedgerService.release returns no-op on already-RELEASED status (D-D3); IllegalLedgerStateException only on settle-after-release / release-after-settle. |
| T-02B-03-04 | Information disclosure | InsufficientCreditsException balance leak (T7 Pitfall) | mitigate | No-args constructor only; service throws `new InsufficientCreditsException()` with no message; Plan 04 GlobalExceptionHandler maps to 402 + `params: Map.of()`. |
| T-02B-03-05 | Information disclosure | Privacy log scrub | mitigate | All log lines follow `event=opaque tenantId={}` format; no payload bytes / amounts / SePay txn id leaked beyond the schema-explicit `event=sepay_topup_credited tenantId={} credits={}` (credits, not VND). Wave 0 BillingPrivacyLogScrubTest verifies (Plan 04 makes test runnable). |
| T-02B-03-06 | Spoofing | API key timing attack (T1) | mitigate | SepayApiKeyVerifier uses MessageDigest.isEqual over UTF-8 bytes; expectedKeyBytes cached at construction; never falls back to String.equals. |
| T-02B-03-07 | Tampering | Negative-amount top-up (T8) | mitigate | DB CHECK constraint `amount_vnd > 0` on `billing_topup_intent` (Plan 01); BillingTopupService.applyWebhook does not credit if intent and webhook amounts disagree. |
| T-02B-03-08 | Tampering | Credit conversion truncation race (T9) | accept | v1 uses single fixed `vnd-per-credit` from `BillingProperties` — rate change in flight would be operator-driven config reload; rounding loss logged at `event=sepay_topup_rounding_loss`. Future hardening (snapshot rate on intent row) tracked in STATE.md. |
</threat_model>

<verification>
- 12 source files exist at the declared paths.
- `./gradlew :backend:core:compileJava` BUILD SUCCESSFUL.
- `./gradlew :backend:core:test --tests "com.zeromail.core.billing.service.*"` BUILD SUCCESSFUL — at minimum SepayApiKeyVerifierTest + TopupCodeGeneratorTest GREEN; CreditLedgerConcurrentReserveTest + CreditLedgerSettleIdempotentTest + CreditLedgerEntryUniqueTest GREEN under Testcontainers.
- ArchUnit boundary tests are NOT enforced in this plan (Plan 06 lands them); compile-time correctness only.
- No ApiPostgresTestBase changes here (Plan 04 owns); core-test `PostgresContainerTest` already boots fine because Liquibase changesets 014–017 are present.
</verification>

<success_criteria>
- 12 files committed.
- Wave 0 RED tests in `core/billing/**` flipped to GREEN (5 of the 7 core test files; remaining 2 — CallSiteEnumMembershipArchTest, BillingDomainBoundaryArchTest — flip GREEN in Plan 06 once ArchUnit rules land).
- Phase 2C plan-phase can mock-or-real-inject `CreditLedger` for its gateway tests.
- No ApplicationModulesTest verification required here (Plan 06 owns final boundary verification).
</success_criteria>

<output>
After completion, create `.planning/phases/02B-billing-prepaid-credits/02B-03-SUMMARY.md`.
</output>
