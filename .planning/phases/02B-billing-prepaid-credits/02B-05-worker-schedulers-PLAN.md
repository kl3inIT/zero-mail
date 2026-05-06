---
phase: 02B
plan: 05
type: execute
wave: 3
depends_on: [01, 02, 03]
files_modified:
  - backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java
  - backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java
  - backend/worker/src/main/java/com/zeromail/worker/billing/BillingIntentExpirySweeper.java
  - backend/worker/src/main/java/com/zeromail/worker/billing/BillingWorkerConfiguration.java
  - backend/worker/src/main/resources/application.yml
  - backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java
autonomous: true
requirements: [BILL-04]
must_haves:
  truths:
    - "`CreditReserveWatchdog` runs at @Scheduled(fixedRate=60_000L) under @SchedulerLock(name=\"creditReserveWatchdog\", lockAtMostFor=PT2M); each tick consumes `List<StaleReservation>` projections from `findStalePendingProjections` (Plan 03 B3) and, per row, opens `ScopedValue.where(TenantContext.TENANT, stale.tenantId().toString()).run(...)` BEFORE calling `creditLedger.release(...)` so Hibernate's @TenantId filter does not reject the read."
    - "`BillingIntentExpirySweeper` runs at @Scheduled(fixedRate=3_600_000L) under @SchedulerLock(name=\"billingIntentExpirySweeper\"); each tick flips PENDING intents past expiresAt to EXPIRED via @Modifying UPDATE."
    - "`backend/worker/src/main/resources/application.yml` declares bare `${SEPAY_WEBHOOK_API_KEY}` (REVIEWS CYCLE-3 HIGH-2: NO `:?` default — Spring placeholder resolution raises IllegalArgumentException at boot when env is missing; parity with api per D-F1) and `REFRESH_TOKEN_KEY_BASE64:?` fail-fast (CR-04 carryover from Folded Todos — kept in `:?` form because the downstream Base64 decoder semantically catches a sentinel default; the SePay key has no such semantic catch, hence the bare-placeholder form)."
    - "Worker test base injects `SEPAY_WEBHOOK_API_KEY=test-sepay-key-fixture` + zero-mail.billing.* defaults via @DynamicPropertySource so the bare-placeholder fail-fast doesn't crash @SpringBootTest."
    - "Wave 0 `CreditReserveWatchdogTest` + `BillingIntentExpirySweeperTest` flip GREEN."
  artifacts:
    - path: "backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java"
      provides: "@EnableSchedulerLock + JdbcTemplateLockProvider over the shedlock table; usingDbTime() avoids client-clock drift."
    - path: "backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java"
      provides: "@Scheduled(fixedRate=60_000L) + @SchedulerLock job; releases stale reservations idempotently; logs event=credit_reserve_released_stale tenantId={} reservationId={} ageSeconds={}; catches IllegalLedgerStateException race + Micrometer counter."
    - path: "backend/worker/src/main/java/com/zeromail/worker/billing/BillingIntentExpirySweeper.java"
      provides: "@Scheduled(fixedRate=3_600_000L) + @SchedulerLock job; bulk @Modifying UPDATE marks PENDING intents past expiresAt as EXPIRED."
    - path: "backend/worker/src/main/java/com/zeromail/worker/billing/BillingWorkerConfiguration.java"
      provides: "@EnableConfigurationProperties(BillingProperties.class) + @ComponentScan to wire core.billing into the worker module."
  key_links:
    - from: "CreditReserveWatchdog.tick"
      to: "creditLedger.release(rid) under ScopedValue.where(TenantContext.TENANT, ...)"
      via: "Hibernate @TenantId filter requires bound tenant"
      pattern: "ScopedValue.where"
    - from: "@SchedulerLock"
      to: "shedlock table (changeset 017)"
      via: "JdbcTemplateLockProvider"
      pattern: "lockProvider"
---

<objective>
Land the worker-side scheduled jobs and worker-module billing wiring. The watchdog is the safety net for crashes between reserve and settle/release in Phase 2C. The intent expiry sweeper keeps the intent table small. Both jobs use ShedLock 7.7.0 (added in Plan 01) so two worker pods cannot double-release / double-expire.

Purpose: per CONTEXT D-B3 + SPEC R4, watchdog query is `SELECT id FROM credit_reservation WHERE status='PENDING' AND created_at < now()-INTERVAL '5 minutes' LIMIT 100 FOR UPDATE SKIP LOCKED` (already declared as `findStalePendingIds` in Plan 03). Per D-C4, sweeper runs hourly. Per D-F1 + Folded Todos, this plan also closes the worker `:?` fail-fast parity gap for `REFRESH_TOKEN_KEY_BASE64` carried over from Phase 1.5 CR-04.

Output: 4 new Java files + 1 application.yml update + 1 test base update.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/phases/02B-billing-prepaid-credits/02B-SPEC.md
@.planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md
@.planning/phases/02B-billing-prepaid-credits/02B-RESEARCH.md
@.planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md
@CLAUDE.md
@backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java
@backend/worker/src/main/java/com/zeromail/worker/GmailHistoryProcessor.java
@backend/worker/src/main/java/com/zeromail/worker/ZeroMailWorkerApplication.java
@backend/worker/src/main/java/com/zeromail/worker/config/ZeroMailWorkerProperties.java
@backend/worker/src/main/resources/application.yml
@backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java

<interfaces>
<!-- Plan 03 outputs (already landed) -->
```java
package com.zeromail.core.billing.persistence;
public interface CreditReservationRepository extends JpaRepository<CreditReservationEntity, UUID> {
    List<UUID> findStalePendingIds(Instant olderThan, int limitRows);
}
public interface BillingTopupIntentRepository extends JpaRepository<BillingTopupIntentEntity, UUID> {
    int expireStale(Instant now);  // @Modifying @Query
}
```

<!-- Plan 02/03 outputs -->
```java
package com.zeromail.core.billing.model;
public interface CreditLedger { void release(ReservationId reservationId); ... }
public class IllegalLedgerStateException extends RuntimeException {}
public record ReservationId(UUID value) {}
```

<!-- ShedLock 7.x API (added in Plan 01) -->
```java
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: BillingWorkerConfiguration + ShedLockConfig + worker application.yml + worker test base @DynamicPropertySource</name>
  <files>
    backend/worker/src/main/java/com/zeromail/worker/billing/BillingWorkerConfiguration.java,
    backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java,
    backend/worker/src/main/resources/application.yml,
    backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java
  </files>
  <read_first>
    - backend/worker/src/main/java/com/zeromail/worker/ZeroMailWorkerApplication.java (verify @SpringBootApplication / @EntityScan / @ComponentScan roots — `com.zeromail.core.billing` MUST be reachable from the worker boot scan; if `scanBasePackages = "com.zeromail"` then auto-covered, else add explicit @ComponentScan in BillingWorkerConfiguration)
    - backend/worker/src/main/java/com/zeromail/worker/config/ZeroMailWorkerProperties.java (analog @Configuration shape inside backend/worker for ShedLockConfig style + visibility)
    - backend/worker/src/main/resources/application.yml (existing structure — line 10 is the legacy `${REFRESH_TOKEN_KEY_BASE64:${sm://...}}` pattern that CR-04 must flip to :? fail-fast in this plan)
    - backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java (existing @DynamicPropertySource block — append zero-mail.billing.* lines + REFRESH_TOKEN_KEY_BASE64 if not already injected)
    - .planning/phases/02B-billing-prepaid-credits/02B-RESEARCH.md (§"Pattern 4" lines 626–642 — exact ShedLockConfig shape: @EnableSchedulerLock(defaultLockAtMostFor="PT5M") + JdbcTemplateLockProvider.Configuration.builder().usingDbTime().build())
    - .planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md (D-F1 — :? fail-fast in worker too; "Folded Todos" — CR-04 carryover from 2026-04-28-worker-application-yml-fail-fast-parity.md)
  </read_first>
  <action>
**File 1: `BillingWorkerConfiguration.java`** (`backend/worker/src/main/java/com/zeromail/worker/billing/`)

W8: Strip redundant `@ComponentScan` and `@EntityScan` / `@EnableJpaRepositories`. The worker boot class `ZeroMailWorkerApplication.java` already scans `com.zeromail.core` (verified — see `ZeroMailWorkerApplication.java` line 11). Adding them again here causes double-registration warnings on context start and offers zero benefit. The ONLY thing this config needs to add is the `BillingProperties` binding, because Spring does not auto-bind unannotated record types (`BillingProperties` is a `@ConfigurationProperties` record from Plan 03 living in `core.billing.service`).

```java
package com.zeromail.worker.billing;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.zeromail.core.billing.service.BillingProperties;

/**
 * Worker-side activation of {@link BillingProperties}. The worker boot class
 * ({@code ZeroMailWorkerApplication}) already component-scans {@code com.zeromail.core},
 * so no `@ComponentScan` / `@EntityScan` / `@EnableJpaRepositories` is needed here —
 * adding them double-registers and produces context-start warnings.
 */
@Configuration
@EnableConfigurationProperties(BillingProperties.class)
public class BillingWorkerConfiguration {
}
```

**File 2: `ShedLockConfig.java`** (`backend/worker/src/main/java/com/zeromail/worker/billing/`)
```java
package com.zeromail.worker.billing;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * Wires ShedLock 7.7.0 backed by the shedlock table (Liquibase changeset 017).
 *
 * <p>{@code defaultLockAtMostFor=PT5M}: safety net for stuck workers — if a node dies mid-tick,
 * other nodes can pick up the lock after 5 minutes. Individual jobs may override via
 * {@link net.javacrumbs.shedlock.spring.annotation.SchedulerLock#lockAtMostFor()}.
 *
 * <p>{@code usingDbTime()}: ShedLock uses the DB clock for lock_until/locked_at — avoids
 * client-clock-drift across worker pods (RESEARCH §"Pattern 4").
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
```

(If `@EnableScheduling` is already on `ZeroMailWorkerApplication` per Phase 2A's GmailWatchScheduler, leave it here too — Spring handles duplicate `@EnableScheduling` annotations idempotently.)

**File 3: `backend/worker/src/main/resources/application.yml`** — modify in place. Two changes:

1. Flip the existing `REFRESH_TOKEN_KEY_BASE64` line (line 10 per CR-04 carryover Folded Todos) from `${REFRESH_TOKEN_KEY_BASE64:${sm://...}}` to:
```yaml
refresh-token-key-base64: ${REFRESH_TOKEN_KEY_BASE64:?REFRESH_TOKEN_KEY_BASE64 must be supplied via deployment secret source (Docker secret, systemd credential, or locked-down env file)}
```

2. Append a new `zero-mail.billing` block (mirror api):
```yaml
zero-mail:
  billing:
    sepay:
      # REVIEWS CYCLE-3 HIGH-2: bare ${SEPAY_WEBHOOK_API_KEY} (NO `:?` default) — Spring placeholder
      # resolution raises IllegalArgumentException at boot when env is absent. Mirrors the api yml
      # exactly. Defense-in-depth lives in BillingProperties.SepayProperties' @PostConstruct
      # sentinel check (Plan 03).
      webhook-api-key: ${SEPAY_WEBHOOK_API_KEY}
    vnd-per-credit: 1000
    max-pending-intents-per-tenant: 5
    intent-expiry: PT24H
```

(Worker doesn't process the webhook today, but loads BillingProperties for the watchdog/sweeper context. Adding the env var keeps boot semantics consistent and prevents future code-move surprises per D-F1.)

**File 4: `backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java`** — append @DynamicPropertySource lines:
Inside the existing `static void props(DynamicPropertyRegistry r)`, append:
```java
r.add("zero-mail.billing.sepay.webhook-api-key", () -> "test-sepay-key-fixture");
r.add("zero-mail.billing.vnd-per-credit", () -> "1000");
r.add("zero-mail.billing.max-pending-intents-per-tenant", () -> "5");
r.add("zero-mail.billing.intent-expiry", () -> "PT24H");
// CR-04 parity: api side already uses :? fail-fast for refresh-token; worker mirror.
r.add("zeromail.crypto.refresh-token-key-base64", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
```

(Confirm the existing test base file does NOT already inject the refresh-token key — if it does, do not duplicate; the api side is the analog at line 48 of `ApiPostgresTestBase.java`.)

After saving all 4 files, run `./gradlew :backend:worker:compileJava :backend:worker:compileTestJava` to confirm compile cleanliness. Then run `./gradlew :backend:worker:test` to confirm all worker tests still GREEN (before adding the watchdog/sweeper logic in Task 2).
  </action>
  <verify>
    <automated>./gradlew :backend:worker:compileJava 2>&1 | grep -q SUCCESSFUL; grep -q "JdbcTemplateLockProvider" backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java; grep -q "@EnableSchedulerLock" backend/worker/src/main/java/com/zeromail/worker/billing/ShedLockConfig.java; grep -q "REFRESH_TOKEN_KEY_BASE64:?" backend/worker/src/main/resources/application.yml; grep -qE 'webhook-api-key:\s*\$\{SEPAY_WEBHOOK_API_KEY\}\s*$' backend/worker/src/main/resources/application.yml; ! grep -E 'SEPAY_WEBHOOK_API_KEY:\?' backend/worker/src/main/resources/application.yml  # REVIEWS CYCLE-3 HIGH-2: NO default-value `:?` form on SePay key; grep -q "test-sepay-key-fixture" backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java</automated>
  </verify>
  <done>BillingWorkerConfiguration enables BillingProperties + (if needed) entity/repository scan; ShedLockConfig declares @EnableScheduling + @EnableSchedulerLock + JdbcTemplateLockProvider with usingDbTime; worker application.yml has REFRESH_TOKEN_KEY_BASE64:? fail-fast (closes CR-04 carryover) AND bare ${SEPAY_WEBHOOK_API_KEY} (REVIEWS CYCLE-3 HIGH-2: NO `:?` default — Spring placeholder resolution itself fails at boot when env is missing; defense-in-depth via BillingProperties @PostConstruct sentinel check from Plan 03); PostgresContainerTest test base injects all 4 zero-mail.billing.* values + crypto refresh-token key.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: CreditReserveWatchdog + BillingIntentExpirySweeper + flip Wave 0 worker tests</name>
  <files>
    backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java,
    backend/worker/src/main/java/com/zeromail/worker/billing/BillingIntentExpirySweeper.java,
    backend/worker/src/test/java/com/zeromail/worker/billing/CreditReserveWatchdogTest.java,
    backend/worker/src/test/java/com/zeromail/worker/billing/BillingIntentExpirySweeperTest.java
  </files>
  <behavior>
    - CreditReserveWatchdog.tick() runs at fixedRate=60_000ms; SELECTs up to 100 stale (>5min PENDING) reservations via FOR UPDATE SKIP LOCKED; for each, binds TenantContext via ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(...) and calls creditLedger.release(rid); catches IllegalLedgerStateException as race no-op; logs event=credit_reserve_released_stale tenantId={} reservationId={} ageSeconds={}.
    - BillingIntentExpirySweeper.sweep() runs at fixedRate=3_600_000ms; calls intentRepository.expireStale(Instant.now()) (bulk @Modifying UPDATE).
    - Both annotated with @SchedulerLock(name=..., lockAtMostFor=...) so 2 worker pods cannot double-fire.
    - Wave 0 CreditReserveWatchdogTest + BillingIntentExpirySweeperTest GREEN.
  </behavior>
  <read_first>
    - backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java (analog scheduler shape — copy ScopedValue.where(TenantContext.TENANT, ...).run(...) idiom from Phase 2A; copy privacy log line shape `event=...`)
    - .planning/phases/02B-billing-prepaid-credits/02B-RESEARCH.md (§"Pattern 4" lines 643–677 — exact CreditReserveWatchdog skeleton with @SchedulerLock + STALE_THRESHOLD = Duration.ofMinutes(5) + BATCH_LIMIT = 100 + try/catch IllegalLedgerStateException)
    - .planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md (lines 855–897 — concrete watchdog + sweeper shape)
    - .planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md (D-I2 — privacy log format `event=credit_reserve_released_stale tenantId={} reservationId={} ageSeconds={}` MUST NOT include amounts)
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/CreditReservationRepository.java (Plan 03 output — `findStalePendingProjections` method signature returning `List<StaleReservation>` with tenantId per B3)
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/StaleReservation.java (Plan 03 output — projection record carrying id + tenantId + createdAt + amountCredits + callSite; watchdog binds ScopedValue from `stale.tenantId()`)
    - backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java (verify the public ScopedValue handle — `public static final ScopedValue<String> TENANT`; bind via `ScopedValue.where(TenantContext.TENANT, stale.tenantId().toString()).run(...)`)
    - backend/core/src/main/java/com/zeromail/core/billing/persistence/BillingTopupIntentRepository.java (Plan 03 output — expireStale @Modifying UPDATE)
  </read_first>
  <action>
**File 5: `CreditReserveWatchdog.java`** (`backend/worker/src/main/java/com/zeromail/worker/billing/`)

B3 — the watchdog scheduler thread runs WITHOUT a bound `TenantContext`. Calling `reservationRepository.findById(uuid)` here would trip Hibernate's `@TenantId` filter and reject every row. The fix is to consume the `StaleReservation` projection record (Plan 03, B3) which carries `tenantId` directly from a tenant-filter-bypassing JDBC scan, then open `ScopedValue.where(TenantContext.TENANT, stale.tenantId().toString()).run(...)` BEFORE calling `creditLedger.release(...)`. No second `findById` happens.

```java
package com.zeromail.worker.billing;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.zeromail.core.billing.model.CreditLedger;
import com.zeromail.core.billing.model.IllegalLedgerStateException;
import com.zeromail.core.billing.model.ReservationId;
import com.zeromail.core.billing.persistence.CreditReservationRepository;
import com.zeromail.core.billing.persistence.StaleReservation;
import com.zeromail.core.tenant.TenantContext;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Releases stale (>5min) PENDING reservations back to available balance. Safety net for
 * crashes between {@code reserve} and {@code settle}/{@code release} in Phase 2C.
 *
 * <p><b>NOT the steady-state finalizer</b> — Phase 2C must call {@code settle}/{@code release}
 * explicitly (D-D1). This job exists for orphaned holds only.
 *
 * <p>Runs at {@code fixedRate=60_000ms} (1 minute) per SPEC R4. Per-tick batch of 100 stale
 * projections claimed via {@code FOR UPDATE SKIP LOCKED} so two worker pods can pick disjoint
 * sets without blocking. {@code @SchedulerLock} adds a second layer at the cluster level.
 *
 * <p><b>Tenant binding (B3):</b> the scheduler thread starts with no bound TenantContext, so
 * the stale-scan returns a {@link StaleReservation} projection that carries {@code tenantId}.
 * For each row we open {@code ScopedValue.where(TenantContext.TENANT, ...)} and only THEN
 * call {@link CreditLedger#release}; otherwise Hibernate's {@code @TenantId} filter would
 * reject the read inside the ledger service.
 */
@Component
class CreditReserveWatchdog {

    private static final Logger log = LoggerFactory.getLogger(CreditReserveWatchdog.class);
    private static final int BATCH_LIMIT = 100;
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);

    private final CreditReservationRepository reservationRepository;
    private final CreditLedger creditLedger;
    private final Counter releasedTotal;

    CreditReserveWatchdog(
            CreditReservationRepository reservationRepository,
            CreditLedger creditLedger,
            MeterRegistry meterRegistry) {
        this.reservationRepository = reservationRepository;
        this.creditLedger = creditLedger;
        this.releasedTotal = Counter.builder("zero_mail.billing.watchdog.released_total")
                .description("Stale credit reservations released by the watchdog")
                .register(meterRegistry);
    }

    @Scheduled(fixedRate = 60_000L)
    @SchedulerLock(name = "creditReserveWatchdog", lockAtLeastFor = "PT30S", lockAtMostFor = "PT2M")
    public void tick() {
        Instant olderThan = Instant.now().minus(STALE_THRESHOLD);
        List<StaleReservation> staleProjections = reservationRepository
                .findStalePendingProjections(olderThan, BATCH_LIMIT);
        for (StaleReservation stale : staleProjections) {
            releaseOne(stale);
        }
    }

    private void releaseOne(StaleReservation stale) {
        ScopedValue.where(TenantContext.TENANT, stale.tenantId().toString()).run(() -> {
            try {
                creditLedger.release(new ReservationId(stale.id()));
                long ageSeconds = Duration.between(stale.createdAt().toInstant(), Instant.now()).toSeconds();
                log.info("event=credit_reserve_released_stale tenantId={} reservationId={} ageSeconds={}",
                        stale.tenantId(), stale.id(), ageSeconds);
                releasedTotal.increment();
            } catch (IllegalLedgerStateException raceWithSettle) {
                // Race: 2C settled in the gap between SELECT and release. Safe no-op.
            }
        });
    }
}
```

Verify before saving: `TenantContext.TENANT` is confirmed at `backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java` line 7 — `public static final ScopedValue<String> TENANT = ScopedValue.newInstance();`. The bind value is `stale.tenantId().toString()` because the scoped value type is `<String>` (matches Phase 2A `GmailWatchScheduler` precedent).

**File 6: `BillingIntentExpirySweeper.java`**

REVIEWS HIGH-4 — RESOLVED: `BillingTopupIntentRepository.expireStale` is `@Modifying`, so calls MUST execute inside an open transaction. The sweeper method declares `@Transactional` (default `Propagation.REQUIRED`); the scheduler thread has no enclosing transaction, so without this annotation Spring throws `TransactionRequiredException` at runtime. The repository method also carries `@Transactional` defensively (Plan 03), but the sweeper method's annotation is the canonical owner of the transaction boundary.

```java
package com.zeromail.worker.billing;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.billing.persistence.BillingTopupIntentRepository;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Hourly sweep that flips PENDING top-up intents past expiresAt to EXPIRED. No financial
 * impact — intents that never produced ledger entries simply die. Pure cleanup.
 *
 * <p><b>Transaction scope (REVIEWS HIGH-4):</b> {@code sweep()} is {@code @Transactional} so
 * the {@code @Modifying} JPQL UPDATE in {@link BillingTopupIntentRepository#expireStale}
 * runs inside an open transaction; without it Spring throws
 * {@code TransactionRequiredException}. ShedLock wraps {@code @Transactional} just fine —
 * the lock is acquired before method entry, the transaction begins inside the method body.
 */
@Component
class BillingIntentExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(BillingIntentExpirySweeper.class);

    private final BillingTopupIntentRepository intentRepository;

    BillingIntentExpirySweeper(BillingTopupIntentRepository intentRepository) {
        this.intentRepository = intentRepository;
    }

    @Scheduled(fixedRate = 3_600_000L)
    @SchedulerLock(name = "billingIntentExpirySweeper", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
    @Transactional(propagation = Propagation.REQUIRED)
    public void sweep() {
        int rowsExpired = intentRepository.expireStale(Instant.now());
        if (rowsExpired > 0) {
            log.info("event=billing_intent_expiry_sweep rowsExpired={}", rowsExpired);
        }
    }
}
```

After saving both files, flip Wave 0 `@Disabled` annotations off in:
- `backend/worker/src/test/java/com/zeromail/worker/billing/CreditReserveWatchdogTest.java`
- `backend/worker/src/test/java/com/zeromail/worker/billing/BillingIntentExpirySweeperTest.java`

Tests now run live. Run `./gradlew :backend:worker:test --tests "com.zeromail.worker.billing.*"` — all 5 watchdog/sweeper tests must pass (3 watchdog + 2 sweeper).

**Common gotchas to anticipate:**
- The B3 fix (StaleReservation projection in Plan 03) eliminated the original `findById` tenant-filter risk: the watchdog never calls JPA before binding TenantContext now. RESEARCH §"Pattern 4" lines 656–675 used plain `findById` — that approach is REJECTED by this plan in favor of the projection record.
- Spring's `@SchedulerLock` requires the `@Component`-annotated bean to NOT be `final` (CGLIB proxy). Class is non-final by default in Java; just don't add `final`.
- The `ScopedValue.where(...).run(...)` is JDK 25 stable API — no `--enable-preview` flag required.
  </action>
  <verify>
    <automated>./gradlew :backend:worker:compileJava 2>&1 | grep -q SUCCESSFUL; grep -q "@SchedulerLock" backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java; grep -q "fixedRate = 60_000L" backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java; grep -q "STALE_THRESHOLD" backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java; grep -q "ScopedValue.where(TenantContext.TENANT" backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java; grep -q "findStalePendingProjections" backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java; grep -q "StaleReservation" backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java; ! grep -q "reservationRepository.findById" backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java; grep -q "event=credit_reserve_released_stale" backend/worker/src/main/java/com/zeromail/worker/billing/CreditReserveWatchdog.java; grep -q "fixedRate = 3_600_000L" backend/worker/src/main/java/com/zeromail/worker/billing/BillingIntentExpirySweeper.java; grep -q "@Transactional" backend/worker/src/main/java/com/zeromail/worker/billing/BillingIntentExpirySweeper.java; grep -q "import org.springframework.transaction.annotation.Transactional" backend/worker/src/main/java/com/zeromail/worker/billing/BillingIntentExpirySweeper.java; ! grep -E '@Disabled.*Wave 0' backend/worker/src/test/java/com/zeromail/worker/billing/*.java; ./gradlew :backend:worker:test 2>&1 | grep -E "BUILD SUCCESSFUL"</automated>
  </verify>
  <done>CreditReserveWatchdog has @Scheduled(fixedRate=60_000L) + @SchedulerLock + STALE_THRESHOLD=Duration.ofMinutes(5) + consumes List<StaleReservation> projections + ScopedValue.where(TenantContext.TENANT, stale.tenantId().toString()).run(...) BEFORE creditLedger.release(...) + IllegalLedgerStateException catch + Micrometer counter; NO secondary findById call (B3 closed); BillingIntentExpirySweeper has @Scheduled(fixedRate=3_600_000L) + @SchedulerLock + intentRepository.expireStale(now); both Wave 0 worker tests flipped to GREEN; ./gradlew :backend:worker:test BUILD SUCCESSFUL.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Worker pod cluster ↔ shedlock table | ShedLock provides cluster-wide mutex for scheduled jobs; one pod claims, others skip until lock_until expires. |
| Worker scheduler ↔ tenant data | TenantContext is bound per-iteration via ScopedValue; release() runs under the bound tenant so Hibernate @TenantId filter applies. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-02B-05-01 | Tampering | Watchdog double-release race (T6 phase threat model) | mitigate | Two layers: (a) FOR UPDATE SKIP LOCKED in repository query gives same-pod intra-job concurrency safety; (b) @SchedulerLock(name="creditReserveWatchdog", lockAtMostFor=PT2M) prevents N>1 worker pods from running tick() simultaneously. UNIQUE (ref_type, ref_id, kind) on credit_ledger_entry catches any residual race + IllegalLedgerStateException race-with-settle is silently handled. |
| T-02B-05-02 | Information disclosure | Watchdog log payload | mitigate | event=credit_reserve_released_stale tenantId={} reservationId={} ageSeconds={} per CONTEXT D-I2 — NO amount_credits, NO call_site, NO PII. |
| T-02B-05-03 | Tampering | Worker boot env-var missing | mitigate | application.yml uses :? fail-fast for REFRESH_TOKEN_KEY_BASE64 (CR-04 carryover close) and bare `${SEPAY_WEBHOOK_API_KEY}` for SEPAY_WEBHOOK_API_KEY (REVIEWS CYCLE-3 HIGH-2: NO `:?` default — Spring placeholder resolution itself raises IllegalArgumentException at boot when env missing; D-F1 parity with api). Boot fails clearly if either env is absent; no silent fallback. |
| T-02B-05-04 | Denial of service | Watchdog stuck job blocking other ticks | mitigate | @SchedulerLock(lockAtMostFor=PT2M) — if a worker dies mid-tick, lock auto-releases after 2 minutes; another pod picks up. defaultLockAtMostFor=PT5M on EnableSchedulerLock is the global safety net. |
| T-02B-05-05 | Privilege escalation | Cross-tenant release via watchdog | mitigate | reservation.getTenantId() drives ScopedValue.where(TenantContext.TENANT, ...) per iteration; CreditLedger.release() then runs under that tenant's filter — cannot accidentally release tenant B's reservation while iterating tenant A's batch. |
</threat_model>

<verification>
- 6 files modified at the declared paths.
- ./gradlew :backend:worker:check BUILD SUCCESSFUL.
- ./gradlew :backend:worker:test --tests "com.zeromail.worker.billing.*" — all 5 worker tests GREEN.
- Worker boot does NOT crash on :? fail-fast (test profile injects via @DynamicPropertySource).
- ShedLock table is reachable (created by Liquibase changeset 017 in Plan 01).
</verification>

<success_criteria>
- 6 files committed; tests + build all GREEN.
- Wave 0 worker tests durable GREEN gates.
- CR-04 worker parity carryover from Phase 1.5 closed in this plan (Folded Todo).
- Phase 5+ ops can rely on ShedLock-coordinated scheduler behavior across multiple worker pods.
</success_criteria>

<output>
After completion, create `.planning/phases/02B-billing-prepaid-credits/02B-05-SUMMARY.md`.
</output>
