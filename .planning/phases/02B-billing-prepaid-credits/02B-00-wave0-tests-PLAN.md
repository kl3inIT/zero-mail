---
phase: 02B
plan: 00
type: execute
wave: 1
depends_on: [02]
# REVIEWS HIGH-1: Plan 00 ordering and build-state honesty.
#
# Plan 00 depends on Plan 02 so the domain-model symbols (CreditLedger, CallSite,
# ReservationId, CreditBalance, InsufficientCreditsException, IllegalLedgerStateException,
# CreditReservationStatus, BillingTopupIntentStatus) exist when Plan 00 compiles its tests.
#
# However, Wave 0 tests ALSO reference symbols that land later: CreditLedgerService
# (Plan 03), SepayApiKeyVerifier (Plan 03), TopupCodeGenerator (Plan 03),
# CreditLedgerEntryEntity / CreditLedgerEntryRepository / BillingTopupIntentEntity (Plan 03),
# SepayWebhookPayload + BillingBalanceResponse (Plan 04), CreditReserveWatchdog +
# BillingIntentExpirySweeper (Plan 05). Those are intentionally compile-RED until the
# referenced plan lands.
#
# This means `./gradlew :backend:core:compileTestJava` is RED-by-design from the moment
# Plan 00 commits until Plan 03 lands, and `./gradlew :backend:api:compileTestJava` is RED
# until Plan 04 lands. The phase-level `./gradlew clean check` BUILD SUCCESSFUL invariant
# only holds at the END of Plan 06, not after Plan 00. Plan 00's must_haves below
# explicitly state the partial-build expectation; Plan 06's success criteria own the
# final clean-check gate.
#
# This is the codex-recommended path "place future-contract tests in an excluded source
# set OR land minimal production type skeletons before tests" with a pragmatic twist —
# we use the natural plan-execution ordering (02 → 03 → 04 → 05 → 06) to land the
# referenced symbols, and Plan 00 documents the RED-during-execution window honestly.
files_modified:
  - backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerConcurrentReserveTest.java
  - backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerSettleIdempotentTest.java
  - backend/core/src/test/java/com/zeromail/core/billing/service/SepayApiKeyVerifierTest.java
  - backend/core/src/test/java/com/zeromail/core/billing/service/TopupCodeGeneratorTest.java
  - backend/core/src/test/java/com/zeromail/core/billing/persistence/CreditLedgerEntryUniqueTest.java
  - backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java
  - backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookIntegrationTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayReplayTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayBadAuthTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceControllerTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceMultiTenantLeakTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingPrivacyLogScrubTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookMismatchAuditEventTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingInsufficientCreditsTest.java
  - backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java
  - backend/worker/src/test/java/com/zeromail/worker/billing/CreditReserveWatchdogTest.java
  - backend/worker/src/test/java/com/zeromail/worker/billing/BillingIntentExpirySweeperTest.java
autonomous: true
requirements: [BILL-01, BILL-02, BILL-03, BILL-04, BILL-05, BILL-06, BILL-07]
must_haves:
  truths:
    - "All 17 Wave 0 test files exist (compile-RED by design — reference future production classes from Plans 03/04/05)."
    - "VALIDATION.md frontmatter `wave_0_complete: true` after this plan ships."
    - "Worker `PostgresContainerTest` is widened to `public abstract class` so sub-package billing tests can extend it from `com.zeromail.worker.billing`."
    - "Every generated test that extends a `PostgresContainerTest` imports it explicitly — `import com.zeromail.core.support.PostgresContainerTest;` (core analog) or `import com.zeromail.worker.PostgresContainerTest;` (worker analog) — never relying on same-package resolution."
    - "REVIEWS HIGH-1 build-state honesty: this plan does NOT claim `./gradlew clean check` is GREEN at end-of-plan. The phase-level GREEN invariant is owned by Plan 06's final acceptance gate. After Plan 00 + Plan 02 commit, `./gradlew :backend:core:compileTestJava :backend:api:compileTestJava :backend:worker:compileTestJava` is RED with predictable `cannot find symbol: CreditLedgerService / SepayApiKeyVerifier / TopupCodeGenerator / CreditLedgerEntryEntity / CreditReserveWatchdog` errors. No OTHER compile errors must leak in (e.g., domain-model imports must resolve cleanly because Plan 02 ran first per `depends_on: [02]`). The RED window closes incrementally: Plan 03 lands → core compileTestJava goes GREEN; Plan 04 lands → api compileTestJava goes GREEN; Plan 05 lands → worker compileTestJava goes GREEN; Plan 06 verifies."
  artifacts:
    - path: "backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerConcurrentReserveTest.java"
      provides: "BILL-03 RED test — 10 virtual threads × reserve(TRIAGE) on available=5 → exactly 5 OK + 5 InsufficientCreditsException"
    - path: "backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerSettleIdempotentTest.java"
      provides: "BILL-03 RED test — settle twice = no-op; release after settle = IllegalLedgerStateException"
    - path: "backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookIntegrationTest.java"
      provides: "BILL-01 RED test — valid Authorization: Apikey header, payload parsed, TOPUP entry created"
    - path: "backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayReplayTest.java"
      provides: "BILL-01 RED test — replay same id returns 200 with single ledger entry"
    - path: "backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookMismatchAuditEventTest.java"
      provides: "BILL-01 / D-I1 RED test — mismatch path emits `event=sepay_webhook_amount_mismatch intentVnd={} actualVnd={}` with the VND numbers."
    - path: "backend/worker/src/test/java/com/zeromail/worker/billing/CreditReserveWatchdogTest.java"
      provides: "BILL-04 RED test — stale (>5min) reservation released by watchdog tick"
  key_links:
    - from: "Wave 0 RED tests"
      to: "Production classes (Plans 02 / 03 / 04 / 05)"
      via: "compile-RED until target classes land"
      pattern: "import com.zeromail.core.billing.model.CreditLedger"
---

<objective>
Land 17 Wave 0 RED-by-design test scaffolds that lock the contract Plans 02–05 must satisfy, plus the validation-frontmatter flip and one in-place visibility widening on the worker `PostgresContainerTest` base. Tests reference future production classes and stay compile-RED until those classes land — that's the contract executor must NOT short-circuit by stubbing/mocking. The shape of every test asserts the SPEC + CONTEXT acceptance criteria 1:1 (concurrent reserve, settle/release idempotency, SePay replay, watchdog age-based release, multi-tenant isolation, 402 mapping, ApiError no-balance-leak, log scrub PLUS dedicated mismatch-audit positive test, ArchUnit CallSite membership + Modulith boundary).

Purpose: Nyquist Dimension 8 mandates RED scaffolds before any implementation. Phase 02A's Plan 02A-00 is the proven analog (10 backend test classes, 2 fixtures). Same pattern here.

Output: 17 test files (`.java`), 1 visibility widening on the worker test base, 1 frontmatter flip (`wave_0_complete: true` in `02B-VALIDATION.md`).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/02B-billing-prepaid-credits/02B-SPEC.md
@.planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md
@.planning/phases/02B-billing-prepaid-credits/02B-RESEARCH.md
@.planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md
@.planning/phases/02B-billing-prepaid-credits/02B-VALIDATION.md
@CLAUDE.md
@CONVENTIONS.md
@backend/api/src/test/java/com/zeromail/api/security/MultiTenantLeakIntegrationTest.java
@backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java
@backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java
@backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java

<interfaces>
<!-- Future production classes the tests reference (compile-RED until Plan 02/03/04/05 land). -->
<!-- Shapes locked by 02B-PATTERNS.md + 02B-RESEARCH.md §"Pattern 1-6". -->

From com.zeromail.core.billing.model (lands in Plan 02):
```java
public interface CreditLedger {
    ReservationId reserve(UUID tenantId, CallSite callSite);
    void settle(ReservationId reservationId);
    void release(ReservationId reservationId);
    CreditBalance balance(UUID tenantId);
}
public enum CallSite implements IdentifiedEnum { TRIAGE(1), DRAFT(2), PREVIEW(1); int cost(); }
public record ReservationId(UUID value) {}
public record CreditBalance(int availableCredits, int heldCredits) {}
public class InsufficientCreditsException extends RuntimeException { public InsufficientCreditsException() {} }
public class IllegalLedgerStateException extends RuntimeException { public IllegalLedgerStateException(String message) {} }
public enum CreditReservationStatus implements IdentifiedEnum { PENDING, SETTLED, RELEASED }
public enum BillingTopupIntentStatus implements IdentifiedEnum { PENDING, PAID, EXPIRED }
```

From com.zeromail.core.billing.service (lands in Plan 03):
```java
class SepayApiKeyVerifier {
    SepayApiKeyVerifier(String expectedApiKey);
    boolean verify(String authorizationHeader); // expects literal "Apikey <key>"
}
class TopupCodeGenerator {
    String generateUniqueCode(java.util.function.Predicate<String> isAvailable, int maxAttempts);
}
```

From com.zeromail.api.dto.billing (lands in Plan 04):
```java
public record SepayWebhookPayload(long id, String gateway, String transactionDate, String accountNumber,
    String code, String content, String transferType, long transferAmount, long accumulated,
    String subAccount, String referenceCode, String description) {}
public record BillingBalanceResponse(int availableCredits, int heldCredits, String currency) {}
```

From com.zeromail.worker.billing (lands in Plan 05):
```java
@Component class CreditReserveWatchdog { void tick(); } // exposed as package-private but @Test calls it directly
@Component class BillingIntentExpirySweeper { void sweep(); }
```

ArchUnit existing analog (DO NOT modify in this plan — Plan 06 owns):
backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java — read for rule shape.

Test-base classes (CROSS-PACKAGE — explicit import required):
- backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java — `public abstract class`, package `com.zeromail.core.support`. Already public; cross-package extension works once import is added.
- backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java — currently package-private; THIS plan widens to `public abstract class`.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 0: Widen worker PostgresContainerTest to public abstract (B2 prerequisite)</name>
  <files>
    backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java
  </files>
  <read_first>
    - backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java (line 17 declares `abstract class PostgresContainerTest` — package-private; sub-package billing tests cannot extend it)
    - backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java (analog — already `public abstract class`; mirror that visibility)
  </read_first>
  <action>
**Single-line edit on `backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java` line 17:**

Change:
```java
abstract class PostgresContainerTest {
```

To:
```java
public abstract class PostgresContainerTest {
```

NO other changes — fields, methods, and the `@Autowired private RefreshTokenCipher cipher;` (which IS package-private — but only used inside this class, so visibility doesn't matter for sub-package extenders) all remain as-is. The protected static fields and the `protected byte[] encryptedRefreshToken(UUID tenantId)` method are already protected and inherit cleanly.

This widening is required because Wave 0 worker billing tests live in `com.zeromail.worker.billing` (sub-package) and `extends PostgresContainerTest`. Symmetric with the core analog at `com.zeromail.core.support.PostgresContainerTest` which is already `public abstract`.
  </action>
  <verify>
    <automated>grep -q "^public abstract class PostgresContainerTest" backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java</automated>
  </verify>
  <done>Line 17 now reads `public abstract class PostgresContainerTest {` — sub-package extension works.</done>
</task>

<task type="auto">
  <name>Task 1: Create core/billing test scaffolds (7 files in backend/core/src/test)</name>
  <files>
    backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerConcurrentReserveTest.java,
    backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerSettleIdempotentTest.java,
    backend/core/src/test/java/com/zeromail/core/billing/service/SepayApiKeyVerifierTest.java,
    backend/core/src/test/java/com/zeromail/core/billing/service/TopupCodeGeneratorTest.java,
    backend/core/src/test/java/com/zeromail/core/billing/persistence/CreditLedgerEntryUniqueTest.java,
    backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java,
    backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java
  </files>
  <read_first>
    - .planning/phases/02B-billing-prepaid-credits/02B-SPEC.md (Acceptance Criteria checkbox 5 — concurrent reserve; checkbox 6 — settle/release idempotency)
    - .planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md (D-A3 — 10-thread concurrent test pattern; D-D1..D4 — settle/release semantics; D-G3 — ArchUnit CallSite membership + JdbcTemplate ban)
    - .planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md (§"Layer: Tests" lines 952–1000 — StructuredTaskScope shape from MultiTenantLeakIntegrationTest)
    - backend/api/src/test/java/com/zeromail/api/security/MultiTenantLeakIntegrationTest.java (verbatim StructuredTaskScope shape — copy structure, swap target call to `creditLedger.reserve(...)`)
    - backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java (test base with @DynamicPropertySource — billing tests under backend/core extend `PostgresContainerTest` instead, see backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java)
    - backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java (the actual core test base — package `com.zeromail.core.support`, class is `public abstract`, extension requires explicit `import com.zeromail.core.support.PostgresContainerTest;`)
    - backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java (ArchUnit per-domain-rule shape for BillingDomainBoundaryArchTest)
  </read_first>
  <action>
Create the seven core-test files. ALL tests are RED-by-design (they import classes that do NOT yet exist). Compile is expected to fail; runtime is unreachable. Use `@Disabled("Wave 0 RED scaffold — production class lands in Plan 0X")` on every `@Test` method so once production lands the executor flips off `@Disabled` rather than rewriting tests.

**MANDATORY for every file that extends `PostgresContainerTest`:** add the explicit import line — package `com.zeromail.core.support` is NOT the same package as the test (`com.zeromail.core.billing.*`), so without an import the symbol does not resolve:

```java
import com.zeromail.core.support.PostgresContainerTest;
```

**File 1: `CreditLedgerConcurrentReserveTest.java`** (BILL-03, D-A3, SPEC AC #5)
- Package `com.zeromail.core.billing.service`. `import com.zeromail.core.support.PostgresContainerTest;` then `extends PostgresContainerTest`. `@SpringBootTest` (note: `PostgresContainerTest` already declares `@SpringBootTest(classes = ZeroMailCoreTestApplication.class)` so an additional `@SpringBootTest` on the subclass MAY be omitted — mirror the existing core test analogs to confirm).
- Single `@Test void ten_virtual_threads_reserve_against_available_5_yields_exactly_5_successes()` (annotate `@Disabled`).
- Body sketch (use `java.util.concurrent.StructuredTaskScope` per Java 25 stable API; `CountDownLatch latch = new CountDownLatch(1);` for simultaneous release). Seed tenant with TOPUP=5 via `creditLedgerEntryRepository.save(CreditLedgerEntryEntity.topup(...))`. Fork 10 subtasks; each calls `latch.await(); return creditLedger.reserve(tenantId, CallSite.TRIAGE);`. Catch `InsufficientCreditsException` → return null. After `scope.join()`, count non-null vs null results: assert exactly 5 non-null reservations, 5 null. Assert `creditLedger.balance(tenantId).availableCredits() == 0`. Assert `creditLedgerEntryRepository.count() == 1 + 5` (1 TOPUP + 5 RESERVE rows).
- Imports: `com.zeromail.core.support.PostgresContainerTest`, `com.zeromail.core.billing.model.{CreditLedger, CallSite, ReservationId, InsufficientCreditsException}`, `com.zeromail.core.billing.persistence.{CreditLedgerEntryEntity, CreditLedgerEntryRepository}`. The `core.billing.*` symbols do NOT yet exist — that is the contract. The `core.support.PostgresContainerTest` import IS resolvable today.

**File 2: `CreditLedgerSettleIdempotentTest.java`** (BILL-03, D-D2/D-D3/D-D4)
- Package `com.zeromail.core.billing.service`. `import com.zeromail.core.support.PostgresContainerTest;` then `extends PostgresContainerTest`. `@SpringBootTest`.
- 4 `@Test`s, all `@Disabled`:
  1. `settle_twice_is_no_op()` — reserve, settle, settle again; assert second call does not throw and `credit_ledger_entry` still has only one SETTLE row for that ref_id.
  2. `release_twice_is_no_op()` — reserve, release, release again; assert no throw, single RELEASE row.
  3. `release_after_settle_throws_IllegalLedgerStateException()` — reserve, settle, then `release(rid)` → assert throws `IllegalLedgerStateException`.
  4. `settle_after_release_throws_IllegalLedgerStateException()` — reserve, release, then `settle(rid)` → assert throws `IllegalLedgerStateException`.

**File 3: `SepayApiKeyVerifierTest.java`** (BILL-01, D-E2 pure-unit)
- Package `com.zeromail.core.billing.service`. Pure JUnit (no Spring context). NO `PostgresContainerTest` extension.
- 4 `@Test`s, all `@Disabled`:
  1. `null_authorization_header_rejected()` — `verifier.verify(null)` returns `false`.
  2. `wrong_prefix_rejected()` — `verifier.verify("Bearer abc")` returns `false`.
  3. `wrong_key_rejected()` — `verifier.verify("Apikey wrong-key")` returns `false`.
  4. `correct_key_accepted()` — `new SepayApiKeyVerifier("expected-key").verify("Apikey expected-key")` returns `true`.

**File 4: `TopupCodeGeneratorTest.java`** (D-C1 — Crockford alphabet)
- Package `com.zeromail.core.billing.service`. Pure JUnit. NO base extension.
- 3 `@Test`s, all `@Disabled`:
  1. `code_is_8_chars_from_crockford_alphabet()` — generate 100 codes, assert each is exactly 8 chars and matches regex `[0-9A-HJKMNPQRSTVWXYZ]{8}` (no `I`, `L`, `O`, `U`).
  2. `collision_retry_succeeds_within_three_attempts()` — `Predicate<String>` rejects first 2 candidates, accepts 3rd; `generateUniqueCode(predicate, 3)` returns the 3rd.
  3. `collision_retry_throws_when_exhausted()` — `Predicate<String>` always returns false; `generateUniqueCode(predicate, 3)` throws `IllegalStateException`.

**File 5: `CreditLedgerEntryUniqueTest.java`** (BILL-02, SPEC AC #4)
- Package `com.zeromail.core.billing.persistence`. `import com.zeromail.core.support.PostgresContainerTest;` then `extends PostgresContainerTest`. `@SpringBootTest`.
- 1 `@Test @Disabled void unique_constraint_blocks_duplicate_ref_type_id_kind()` — insert one TOPUP entry with `ref_type='PAYMENT_SEPAY', ref_id='SEPAY-TX-1', kind='TOPUP'`. Insert second with same triple → assert `DataIntegrityViolationException`.

**File 6: `CallSiteEnumMembershipArchTest.java`** (BILL-07, D-G3)
- Package `com.zeromail.core.billing`. Pure JUnit. NO base extension.
- 3 `@Test`s, all `@Disabled`:
  1. `callsite_has_exactly_three_members()` — `assertThat(CallSite.values()).hasSize(3);`
  2. `callsite_members_locked_to_TRIAGE_DRAFT_PREVIEW()` — assert names equal `Set.of("TRIAGE","DRAFT","PREVIEW")`.
  3. `callsite_costs_match_spec()` — `TRIAGE.cost()==1`, `DRAFT.cost()==2`, `PREVIEW.cost()==1`.

**File 7: `BillingDomainBoundaryArchTest.java`** (D-G3 ArchUnit)
- Package `com.zeromail.core.billing`. Use `com.tngtech.archunit.junit.AnalyzeClasses` + `@ArchTest`.
- `@AnalyzeClasses(packages = "com.zeromail.core.billing")` (or `"com.zeromail"` if other domains are scanned).
- 3 `@ArchTest`s, all wrapped in `@Disabled` via custom annotation if ArchUnit doesn't honor `@Disabled` — alternative: use a `static final ArchRule` referenced from a `@Test @Disabled` JUnit method. Pick whichever runs RED:
  1. `jdbc_template_only_in_lowlevel` — `noClasses().that().resideInAPackage("..core.billing..").and().resideOutsideOfPackage("..core.billing.persistence.lowlevel..").should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc.core..")`
  2. `credit_ledger_service_not_instantiated_outside_billing_service` — `noClasses().that().resideOutsideOfPackage("..core.billing.service..").should().callConstructor(CreditLedgerService.class, ...)`
  3. `core_billing_only_depends_on_allowed_packages` — `classes().that().resideInAPackage("..core.billing..").should().onlyDependOnClassesThat().resideInAnyPackage("..core.billing..", "..core.tenant..", "..core.shared.persistence..", "..core.shared.lang..", "java..", "jakarta..", "org.springframework..", "org.hibernate..", "org.slf4j..", "com.fasterxml.jackson..")`. (Plan 06 widens this if false-positives surface.)

**Why @Disabled instead of compile-RED only:** ArchUnit and `@SpringBootTest` failures cascade through the build. `@Disabled("Wave 0 RED scaffold")` keeps `./gradlew check` GREEN until production lands, AND the test files exist as durable contracts. This is the same pattern Phase 02A-00 used (verified by reading 02A-00-SUMMARY.md acceptance gate — the build was GREEN with disabled scaffolds). Executors flip `@Disabled → @Test` in Plan 03/04/05 acceptance.
  </action>
  <verify>
    <automated>./gradlew :backend:core:compileTestJava 2>&1 | grep -E '(cannot find symbol|package .* does not exist).*billing' | wc -l should be > 0 (RED-by-design); BUT ./gradlew :backend:core:test --tests "com.zeromail.core.billing.*" -x compileTestJava || true should report no executed tests if @Disabled is honored. Practical gates: (a) each file exists + contains literal "@Disabled" annotation: `find backend/core/src/test/java/com/zeromail/core/billing -name "*.java" | xargs grep -l "@Disabled"` returns all 7 paths; (b) `grep -q "import com.zeromail.core.support.PostgresContainerTest;" backend/core/src/test/java/com/zeromail/core/billing/service/CreditLedgerConcurrentReserveTest.java`; (c) same import grep for `CreditLedgerSettleIdempotentTest.java`; (d) same for `CreditLedgerEntryUniqueTest.java`. Pure-unit tests (SepayApiKeyVerifierTest, TopupCodeGeneratorTest, CallSiteEnumMembershipArchTest, BillingDomainBoundaryArchTest) MUST NOT have the import (no extension).</automated>
  </verify>
  <done>7 files exist; each has at least one `@Disabled("Wave 0 RED scaffold — production class lands in Plan 0X")` annotation; the 3 files extending PostgresContainerTest have `import com.zeromail.core.support.PostgresContainerTest;`; imports reference future production classes (CreditLedger, CallSite, SepayApiKeyVerifier, TopupCodeGenerator, CreditLedgerEntryEntity, CreditLedgerEntryRepository, CreditLedgerService, InsufficientCreditsException, IllegalLedgerStateException) — confirmed via `grep -l 'com.zeromail.core.billing' backend/core/src/test/java/com/zeromail/core/billing/**`.</done>
</task>

<task type="auto">
  <name>Task 2: Create api/billing test scaffolds (8 files in backend/api/src/test — happy-path scrub + dedicated mismatch audit positive test)</name>
  <files>
    backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookIntegrationTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayReplayTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayBadAuthTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceControllerTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingBalanceMultiTenantLeakTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingPrivacyLogScrubTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookMismatchAuditEventTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingInsufficientCreditsTest.java
  </files>
  <read_first>
    - .planning/phases/02B-billing-prepaid-credits/02B-SPEC.md (Acceptance Criteria checkboxes 8, 9, 10, 11, 13)
    - .planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md (D-E1, D-E2, D-I1 — `event=sepay_webhook_amount_mismatch intentVnd={} actualVnd={}` ALLOWS the VND numbers in this event line; D-I2 — privacy log patterns)
    - .planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md (§"Layer: Tests" lines 952–1010 — RestClient + LocalServerPort pattern; replay test shape)
    - backend/api/src/test/java/com/zeromail/api/security/MultiTenantLeakIntegrationTest.java (RestClient + LocalServerPort + StructuredTaskScope shape — copy verbatim, swap endpoint to /api/billing/balance)
    - backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java (filter-chain integration test — analog for SepayBadAuthTest)
    - backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java (test base; the `@DynamicPropertySource` line for `zero-mail.billing.sepay.webhook-api-key=test-sepay-key-fixture` is added in Plan 04, NOT here — these tests stay @Disabled until then)
  </read_first>
  <action>
Create the eight api-test files. ALL tests `@Disabled("Wave 0 RED scaffold — production class lands in Plan 04")`. Mirror `MultiTenantLeakIntegrationTest` for the multi-tenant test, mirror `PubSubOidcAuthFilterTest` for auth tests. Use `RestClient.create("http://localhost:" + port)` everywhere `TenantContext` ScopedValue must bind (per Phase 1 lesson).

**File 8: `SepayWebhookIntegrationTest.java`** (BILL-01, SPEC AC #8 happy path)
- Package `com.zeromail.api.controllers.billing`. `extends ApiPostgresTestBase`. `@SpringBootTest(webEnvironment = RANDOM_PORT)`. `@LocalServerPort int port`.
- 1 `@Test @Disabled void valid_apikey_payload_credits_ledger_idempotently()`:
  - Seed: create `BillingTopupIntentEntity` with `code="ABC12345", amountVnd=100000, status=PENDING`.
  - Build payload: synthetic `SepayWebhookPayload(id=999L, gateway="VCB", transactionDate="2026-05-05 12:00:00", accountNumber="0123", code=null, content="ABC12345 nap tien zeromail", transferType="in", transferAmount=100000L, accumulated=0L, subAccount=null, referenceCode="ABC12345", description="bank sms")`.
  - POST to `http://localhost:{port}/api/billing/sepay/webhook` with header `Authorization: Apikey test-sepay-key-fixture`, JSON body.
  - Assert: status `200 OK`; body contains `"success":true`; `creditLedgerEntryRepository.findAll()` has exactly 1 TOPUP entry with `amountCredits == 100` (100k VND / 1k = 100); intent status flipped to PAID.

**File 9: `SepayReplayTest.java`** (BILL-01, SPEC AC #9, threat T2)
- Same shape as File 8.
- 1 `@Test @Disabled void replay_same_transaction_id_yields_single_topup()`:
  - POST same payload twice (same `id`). Assert both calls return 200; assert `creditLedgerEntryRepository.count() == 1`; assert intent has paidAt set once (sepay_transaction_id matches first POST).

**File 10: `SepayBadAuthTest.java`** (BILL-01, SPEC AC #8 401 path, threat T1)
- Same shape.
- 3 `@Test @Disabled`s:
  1. `missing_authorization_header_returns_401()` — POST without header → 401, no ledger row created.
  2. `wrong_prefix_returns_401()` — `Authorization: Bearer abc` → 401, no ledger row.
  3. `wrong_apikey_returns_401()` — `Authorization: Apikey wrong-secret` → 401, no ledger row.

**File 11: `BillingBalanceControllerTest.java`** (BILL-05, SPEC AC #10)
- `extends ApiPostgresTestBase`. `@Import(TestSessionSupport.class)` (mirror `MultiTenantLeakIntegrationTest` line 23).
- 1 `@Test @Disabled void authenticated_balance_returns_shape()`:
  - Seed tenant + user, mint test session via `TestSessionSupport.TestSessionMinter`.
  - Insert TOPUP entry of `amountCredits=42`.
  - GET `/api/billing/balance` with session cookie → 200 + body `{availableCredits: 42, heldCredits: 0, currency: "credits"}`.

**File 12: `BillingBalanceMultiTenantLeakTest.java`** (BILL-05, SPEC AC #10 isolation)
- Verbatim copy of `MultiTenantLeakIntegrationTest` skeleton. Seed N=10 tenants each with a different `availableCredits` (T0=10, T1=20, ..., T9=100). `StructuredTaskScope` fork 10 GET `/api/billing/balance` calls — each under that tenant's session. Assert each response's `availableCredits` matches the seeded value for that tenant; no cross-leak.

**File 13: `BillingPrivacyLogScrubTest.java`** (Privacy invariant — happy path only; mismatch path tested separately in File 13b)
- Capture Logback output (`@Import(LogbackTestCaptureConfig.class)` — if the project doesn't have one, use `LoggerFactory.getLogger(...).getLoggerContext().getStatusManager()` or attach an in-memory appender programmatically per `org.slf4j.LoggerFactory`).
- 1 `@Test @Disabled void successful_topup_happy_path_logs_no_payload_bytes()`:
  - **Scenario explicitly limited to the happy path**: seed intent with `amountVnd=100000`; payload `transferAmount=100000`. Amounts match, so the mismatch event MUST NOT fire — therefore the literal string `"100000"` MUST NOT appear in the log buffer.
  - Run File 8's happy path; capture all log output during the POST + service execution.
  - Assert: log buffer does NOT contain `"100000"` (no mismatch event was triggered, and `event=sepay_topup_credited tenantId={} credits={}` only logs the integer credits — `100`, not `100000`); does NOT contain `"Apikey "` substring; does NOT contain the literal SePay transaction ID `999`; does NOT contain `accountNumber` value `"0123"`; does NOT contain raw payload `description` value `"bank sms"`.
- **Class Javadoc MUST state explicitly:** "This test only covers the HAPPY-PATH log scrub. The mismatch path — where D-I1 explicitly permits `event=sepay_webhook_amount_mismatch intentVnd={} actualVnd={}` (numbers allowed) — is covered by `SepayWebhookMismatchAuditEventTest`. Do NOT extend this scrub test to assert against numeric amounts in the mismatch path; that contradicts the locked privacy contract."

**File 13b (NEW per W7): `SepayWebhookMismatchAuditEventTest.java`** (BILL-01, D-I1 dedicated coverage)
- Package `com.zeromail.api.controllers.billing`. `extends ApiPostgresTestBase`. Same shape as File 13 with logback capture.
- 1 `@Test @Disabled void amount_mismatch_emits_audit_event_with_vnd_numbers()`:
  - Seed: create `BillingTopupIntentEntity` with `code="MISMATCH", amountVnd=50000, status=PENDING`.
  - Build payload: same synthetic shape as File 8 BUT with `transferAmount=99000L`, `referenceCode="MISMATCH"`.
  - POST to `/api/billing/sepay/webhook` with valid Apikey header.
  - Assert: status `200 OK` (per D-C3 — ack to stop SePay retries); body `{"success":true}`; **NO new TOPUP entry was created** (`creditLedgerEntryRepository.count() == 0`); **intent status remains PENDING** (NOT flipped to PAID).
  - Assert log buffer DOES contain the substring `event=sepay_webhook_amount_mismatch`; DOES contain `intentVnd=50000` (or whatever shape `{}` interpolation produces — verify against actual SLF4J output); DOES contain `actualVnd=99000`. Numbers in this exact event are explicitly allowed by D-I1.
  - Assert log buffer STILL does NOT contain `"Apikey "` (header bytes), does NOT contain raw SePay txn id `"999"` (no opaque txn-id leak), does NOT contain `accountNumber` `"0123"`. Privacy invariants other than the mismatch numbers remain intact.

**File 14: `BillingInsufficientCreditsTest.java`** (BILL-06, SPEC AC #11, Pitfall 7)
- 1 `@Test @Disabled void insufficient_balance_returns_402_no_balance_leak()`:
  - Seed tenant with `availableCredits=0`.
  - POST to a test-only `@RestController` endpoint that wraps `creditLedger.reserve(tenantId, CallSite.TRIAGE)` — define this controller as a `@Configuration` test bean inside the test class (or in a `BillingTestController` under `src/test`).
  - Assert: status `402`; response body parses as `ApiError` with `code="error.billing.insufficient"`; body does NOT contain any digit-string that could leak balance (regex `\b\d+\b` count must be 0 EXCEPT for the HTTP status code if echoed); `params: {}` (empty Map).
  </action>
  <verify>
    <automated>find backend/api/src/test/java/com/zeromail/api/controllers/billing -name "*.java" | wc -l returns 8; grep -l "@Disabled" backend/api/src/test/java/com/zeromail/api/controllers/billing/*.java returns all 8; ./gradlew :backend:api:compileTestJava 2>&1 | grep -E "cannot find symbol.*billing" | wc -l > 0 (RED-by-design until Plan 04); grep -q "event=sepay_webhook_amount_mismatch" backend/api/src/test/java/com/zeromail/api/controllers/billing/SepayWebhookMismatchAuditEventTest.java; grep -q "happy-path log scrub" backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingPrivacyLogScrubTest.java || grep -q "happy path" backend/api/src/test/java/com/zeromail/api/controllers/billing/BillingPrivacyLogScrubTest.java</automated>
  </verify>
  <done>8 api test files exist with `@Disabled` annotations; SepayWebhookIntegrationTest references future SepayWebhookPayload + Authorization Apikey header; BillingBalanceMultiTenantLeakTest mirrors `MultiTenantLeakIntegrationTest` line-by-line on RestClient + LocalServerPort + StructuredTaskScope; BillingPrivacyLogScrubTest negative-asserts on `Apikey ` + `accountNumber` + `bank sms` substrings AND its Javadoc explicitly limits scope to happy path; SepayWebhookMismatchAuditEventTest positively asserts `event=sepay_webhook_amount_mismatch` + `intentVnd=` + `actualVnd=` strings in log output (D-I1 dedicated coverage).</done>
</task>

<task type="auto">
  <name>Task 3: Create worker/billing test scaffolds + flip VALIDATION.md frontmatter</name>
  <files>
    backend/worker/src/test/java/com/zeromail/worker/billing/CreditReserveWatchdogTest.java,
    backend/worker/src/test/java/com/zeromail/worker/billing/BillingIntentExpirySweeperTest.java,
    .planning/phases/02B-billing-prepaid-credits/02B-VALIDATION.md
  </files>
  <read_first>
    - .planning/phases/02B-billing-prepaid-credits/02B-VALIDATION.md (frontmatter currently `wave_0_complete: false` — flip to true)
    - .planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md (D-B3 — watchdog FOR UPDATE SKIP LOCKED query; D-C4 — sweeper @Scheduled fixedRate=3_600_000)
    - backend/worker/src/test/java/com/zeromail/worker/PostgresContainerTest.java (worker test base — Task 0 above widens it to `public abstract class`. Sub-package billing tests need explicit `import com.zeromail.worker.PostgresContainerTest;`)
    - backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java (analog scheduler shape — read for ScopedValue.where(TenantContext.TENANT, ...).run() idiom)
  </read_first>
  <action>
**Both worker test files MUST add explicit import** — they live in `com.zeromail.worker.billing` (sub-package) and the base class lives at the parent `com.zeromail.worker` package:

```java
import com.zeromail.worker.PostgresContainerTest;
```

(This works only AFTER Task 0 widens the base to `public abstract`. Task 0 runs first in Wave 1 — same plan, same wave, but Task 0 is listed first so the executor edits visibility before the test scaffolds are compiled by `:backend:worker:compileTestJava`.)

**File 15: `CreditReserveWatchdogTest.java`** (BILL-04, SPEC AC #7)
- Package `com.zeromail.worker.billing`. `import com.zeromail.worker.PostgresContainerTest;` then `extends PostgresContainerTest`. `@SpringBootTest`.
- Inject `CreditReserveWatchdog`, `CreditReservationRepository`, `CreditLedgerEntryRepository`, `CreditLedger` (interface).
- 3 `@Test @Disabled`s:
  1. `stale_reservation_older_than_5_minutes_is_released()` — Insert TOPUP=10 for tenant. Insert RESERVE=2 + matching `CreditReservationEntity(status=PENDING, createdAt=Instant.now().minus(Duration.ofMinutes(6)))`. Call `watchdog.tick()`. Assert: `creditReservationRepository.findById(rid).get().getStatus() == RELEASED`; new RELEASE ledger entry exists with `amountCredits == 2` and `ref_id == rid`; `creditLedger.balance(tenantId).availableCredits() == 10`.
  2. `tick_on_already_released_reservation_is_no_op()` — Run scenario 1, then call `watchdog.tick()` a second time. Assert no additional RELEASE rows (UNIQUE constraint on `(ref_type='RESERVATION', ref_id=rid, kind='RELEASE')` — second insert silently skipped). Assert `creditLedgerEntryRepository.count()` unchanged from end-of-scenario-1.
  3. `fresh_reservation_under_5_minutes_is_not_released()` — Insert RESERVE with `createdAt=Instant.now().minus(Duration.ofMinutes(2))`. Call `watchdog.tick()`. Assert reservation status still `PENDING`.

**File 16: `BillingIntentExpirySweeperTest.java`** (D-C4)
- Package `com.zeromail.worker.billing`. `import com.zeromail.worker.PostgresContainerTest;` then `extends PostgresContainerTest`. `@SpringBootTest`.
- 2 `@Test @Disabled`s:
  1. `expired_pending_intents_marked_EXPIRED()` — Insert intent with `expiresAt=Instant.now().minus(Duration.ofHours(25))`, `status=PENDING`. Call `sweeper.sweep()`. Assert intent status flipped to `EXPIRED`.
  2. `paid_intent_not_touched_by_sweeper()` — Insert intent with `status=PAID, expiresAt=Instant.now().minus(Duration.ofHours(25))`. Call sweeper. Assert status remains `PAID` (sweeper only touches PENDING).

**Frontmatter flip:**
- Edit `02B-VALIDATION.md`. Change line 5 from `nyquist_compliant: false` → `nyquist_compliant: true`. Change line 6 from `wave_0_complete: false` → `wave_0_complete: true`. Tick all the `Wave 0 Requirements` checkboxes (lines 75–90) for the 17 files now landed (lines 75–90 use `- [ ]` syntax — flip to `- [x]`). Note the count went from 16 → 17 because `SepayWebhookMismatchAuditEventTest.java` was added per W7 in Task 2.
  </action>
  <verify>
    <automated>find backend/worker/src/test/java/com/zeromail/worker/billing -name "*.java" | wc -l returns 2; grep -q "import com.zeromail.worker.PostgresContainerTest;" backend/worker/src/test/java/com/zeromail/worker/billing/CreditReserveWatchdogTest.java; grep -q "import com.zeromail.worker.PostgresContainerTest;" backend/worker/src/test/java/com/zeromail/worker/billing/BillingIntentExpirySweeperTest.java; grep -E '^(nyquist_compliant|wave_0_complete): true' .planning/phases/02B-billing-prepaid-credits/02B-VALIDATION.md returns 2 matches; grep -c '^- \[x\]' .planning/phases/02B-billing-prepaid-credits/02B-VALIDATION.md returns >= 17 (Wave 0 Requirements section flipped).</automated>
  </verify>
  <done>2 worker test files exist with `@Disabled` annotations and `import com.zeromail.worker.PostgresContainerTest;`; CreditReserveWatchdogTest covers age-based release + idempotency + fresh-skip; BillingIntentExpirySweeperTest covers PENDING→EXPIRED + PAID-untouched; VALIDATION.md frontmatter flipped to `nyquist_compliant: true` + `wave_0_complete: true`.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Test-only code → Production code | Wave 0 tests reference future production class names; compile-RED is the contract. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-02B-W0-01 | Tampering | Wave 0 test scaffolds | accept | Tests are committed to git as immutable contracts; Plan 03/04/05 executor flips `@Disabled` off, never edits test logic shape. ArchUnit (Plan 06) re-validates final shape post-implementation. |
| T-02B-W0-02 | Information disclosure | `BillingPrivacyLogScrubTest` itself | mitigate | Test must NOT log the synthetic API key / payload during failures. Use AssertJ `assertThat(...)` — does not echo the inputs to stdout on assertion failure. |
| T-02B-W0-03 | Information disclosure | `SepayWebhookMismatchAuditEventTest` log assertion vs broader privacy contract | mitigate | This test asserts that VND numbers DO appear in the dedicated mismatch audit event line — a privacy carve-out explicitly granted by D-I1. The carve-out is narrow (this one event only) and scoped (intent VND + actual VND, not bank account, not txn id). All other privacy invariants (no Apikey header, no SePay txn id, no accountNumber) remain asserted in this same test. |
</threat_model>

<verification>
- All 17 Wave 0 test files exist at the paths declared in `files_modified`, plus the worker test base widened to `public abstract class`.
- Each test file contains at least one `@Disabled("Wave 0 RED scaffold — production class lands in Plan 0X")` annotation.
- Tests extending a `PostgresContainerTest` (5 files: 3 core + 2 worker) include the explicit cross-package import line.
- `02B-VALIDATION.md` frontmatter has `nyquist_compliant: true` and `wave_0_complete: true`.
- REVIEWS HIGH-1: `./gradlew :backend:core:compileTestJava` (and `api` and `worker`) is RED at end-of-Plan-00. The errors are exclusively `cannot find symbol` for the future production classes named in this plan's `must_haves` truths. ArchUnit `@AnalyzeClasses` failures are also expected if any rule references not-yet-existing classes — they must be wrapped in `@Disabled` at the class level (or kept inside a JUnit `@Test @Disabled` method that holds the `static final ArchRule`) so the analyzer does not run. The phase-level `clean check` GREEN invariant is owned by Plan 06's final gate, NOT this plan.
</verification>

<success_criteria>
- 17 test files committed to git + 1 worker test base visibility widening (`git status` clean after task 3 commit).
- VALIDATION.md frontmatter flipped.
- REVIEWS HIGH-1: `./gradlew clean check` is NOT expected GREEN at end-of-Plan-00 — that is Plan 06's responsibility. The achievable end-of-Plan-00 state is: tests committed, only "cannot find symbol" errors for the named future classes, no other compile-error leakage, `@Disabled` annotations keep the test bodies dormant.
- Plans 03/04/05 can flip `@Disabled` off in their respective acceptance phases without rewriting test logic.
- Worker `PostgresContainerTest` is `public abstract class` (B2 closed); core analog already was.
- D-I1 mismatch audit event has explicit dedicated positive coverage (W7 closed); happy-path scrub test no longer carries the contradictory negative assertion against `100000`.
</success_criteria>

<output>
After completion, create `.planning/phases/02B-billing-prepaid-credits/02B-00-SUMMARY.md`.
</output>
