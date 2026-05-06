---
phase: 02B
plan: 02
type: execute
wave: 1
depends_on: []
files_modified:
  - backend/core/src/main/java/com/zeromail/core/billing/package-info.java
  - backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/package-info.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/CreditReservationStatus.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/BillingTopupIntentStatus.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/CreditLedger.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/ReservationId.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/CreditBalance.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/InsufficientCreditsException.java
  - backend/core/src/main/java/com/zeromail/core/billing/model/IllegalLedgerStateException.java
autonomous: true
requirements: [BILL-02, BILL-03, BILL-06, BILL-07]
must_haves:
  truths:
    - "`com.zeromail.core.billing.model.CreditLedger` interface exists with `reserve`, `settle`, `release`, `balance` methods and BYOK exemption Javadoc clause."
    - "`com.zeromail.core.billing.model.CallSite` enum implements `IdentifiedEnum` with exactly `{TRIAGE, DRAFT, PREVIEW}` and costs `{1, 2, 1}`."
    - "`core.billing` Spring Modulith package-info declares `allowedDependencies = {tenant, shared.persistence, shared.lang}` (NOT shared.privacy)."
    - "`core.billing.persistence.lowlevel` sub-package marker exists for the Plan 03 advisory-lock helper."
    - "Every `fromId` Stream-filter lambda uses a domain-revealing parameter name per CLAUDE.md §Backend Code Style — `callSite ->`, `status ->`, `intentStatus ->`. Do NOT copy the existing `e ->` style from `core.onboarding.model.OnboardingStep`; that file pre-dates the enterprise-naming rule and is NOT a style precedent for this phase."
    - "`./gradlew :backend:core:compileJava` is GREEN — model package compiles cleanly with no `core.billing.service` or `core.billing.persistence` symbols required."
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/billing/package-info.java"
      provides: "Spring Modulith leaf module declaration with allowedDependencies."
    - path: "backend/core/src/main/java/com/zeromail/core/billing/model/CreditLedger.java"
      provides: "Cross-phase contract (Phase 2C imports verbatim) — reserve/settle/release/balance interface + BYOK Javadoc."
    - path: "backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java"
      provides: "IdentifiedEnum {TRIAGE(1), DRAFT(2), PREVIEW(1)} + cost() accessor."
    - path: "backend/core/src/main/java/com/zeromail/core/billing/model/ReservationId.java"
      provides: "UUID-wrapping record for type safety on reservation handles."
    - path: "backend/core/src/main/java/com/zeromail/core/billing/model/CreditBalance.java"
      provides: "Read-projection record (availableCredits, heldCredits)."
    - path: "backend/core/src/main/java/com/zeromail/core/billing/model/InsufficientCreditsException.java"
      provides: "Thrown by reserve when balance < cost; no-args constructor — privacy invariant."
    - path: "backend/core/src/main/java/com/zeromail/core/billing/model/IllegalLedgerStateException.java"
      provides: "Thrown on forbidden transitions (release-after-settle, settle-after-release)."
  key_links:
    - from: "core.billing.model.CreditLedger"
      to: "Phase 2C core.llm.LlmGateway"
      via: "import com.zeromail.core.billing.model.CreditLedger"
      pattern: "Phase 2C SPEC contract: gateway pre-call calls Phase2B.CreditLedger.reserve"
---

<objective>
Land the `core.billing` Modulith leaf module with its model package only — the cross-phase contract Phase 2C imports verbatim. No service/persistence/lowlevel implementation in this plan; that's Plan 03. Decoupling the interface from implementation keeps Plan 02 + Plan 03 independent of Plan 01 (schema) for compile purposes — Plan 02 has zero schema dependency.

Purpose: per CONTEXT D-G2, the `model/` sub-package holds public-API surface (interface + value objects + enums + exceptions). Plan 02 ships exactly these. The package-info ApplicationModule declaration is the Spring Modulith boundary that ApplicationModulesTest enforces (Plan 06 verifies after Plan 03 lands the dependent service classes).

Output: 8 Java source files + 2 package-info markers under `core.billing.model` and `core.billing` (root) and `core.billing.persistence.lowlevel` (sub-package marker).
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
@.planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md
@CLAUDE.md
@CONVENTIONS.md
@backend/core/src/main/java/com/zeromail/core/gmail/package-info.java
@backend/core/src/main/java/com/zeromail/core/gmail/persistence/lowlevel/package-info.java
@backend/core/src/main/java/com/zeromail/core/onboarding/model/OnboardingStep.java
@backend/core/src/main/java/com/zeromail/core/shared/lang/IdentifiedEnum.java
@backend/core/src/main/java/com/zeromail/core/gmail/service/InvalidGrantException.java

<interfaces>
<!-- IdentifiedEnum (already exists, do not modify) -->
```java
public interface IdentifiedEnum {
    String id();                          // MUST equal Enum#name() for enums
    default String labelKey() { return getClass().getSimpleName() + "." + id(); }
}
```

<!-- Existing analog: OnboardingStep -->
```java
public enum OnboardingStep implements OrderedEnum {
    GMAIL_CONNECTED(10), TEMPLATE_SELECTED(20), COMPLETE(30);
    private final int weight;
    OnboardingStep(int weight) { this.weight = weight; }
    @Override public String id() { return name(); }
    @Override public int weight() { return weight; }
    public static OnboardingStep fromId(String id) {
        return Stream.of(values()).filter(step -> step.id().equals(id)).findFirst()  // analog only — the new billing enums MUST use domain-revealing names like callSite/status/intentStatus per CLAUDE.md, never `e` / `s` / single-letter
            .orElseThrow(() -> new NoSuchElementException("Unknown OnboardingStep id: " + id));
    }
}
```

<!-- Existing analog: gmail/package-info.java -->
```java
@ApplicationModule(displayName = "Gmail", allowedDependencies = {"tenant", "shared.privacy", "shared.persistence", "shared.lang"})
package com.zeromail.core.gmail;
import org.springframework.modulith.ApplicationModule;
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Modulith package-info declarations + sub-package marker</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/billing/package-info.java,
    backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/package-info.java
  </files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/gmail/package-info.java (exact analog — copy + adjust displayName + drop shared.privacy from allowedDependencies)
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/lowlevel/package-info.java (sub-package marker analog — re-brand for billing's advisory-lock helper)
    - .planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md (D-G1 — exact allowedDependencies set: `{"tenant", "shared.persistence", "shared.lang"}`; D-G2 — sub-packages `{model, service, persistence, persistence.lowlevel}`)
  </read_first>
  <action>
**File 1: `backend/core/src/main/java/com/zeromail/core/billing/package-info.java`**
```java
/**
 * Billing domain — prepaid credit ledger with reserve/settle/release semantics.
 *
 * <p><b>Cross-phase contract.</b> Phase 2C ({@code core.llm.LlmGateway}) imports
 * {@link com.zeromail.core.billing.model.CreditLedger} verbatim and calls
 * {@code reserve(tenantId, callSite)} on the gateway pre-call path when no BYOK
 * credential exists for the tenant.
 *
 * <p><b>Modulith boundary (D-G1).</b> Allowed dependencies are restricted to
 * {@code tenant} (TenantContext), {@code shared.persistence} (AbstractTenantOwnedEntity),
 * and {@code shared.lang} (IdentifiedEnum). NO edge to {@code shared.privacy} —
 * billing has no Sensitive&lt;T&gt; payloads. NO edge to {@code account / gmail / onboarding}.
 *
 * <p><b>Sub-packages (D-G2):</b>
 * <ul>
 *   <li>{@code model} — public API: interface, records, enums, exceptions.</li>
 *   <li>{@code service} — implementation (lands Plan 03).</li>
 *   <li>{@code persistence} — JPA entities + repositories (lands Plan 03).</li>
 *   <li>{@code persistence.lowlevel} — raw JDBC for {@code pg_advisory_xact_lock}; ArchUnit-allowlisted (lands Plan 03).</li>
 * </ul>
 */
@ApplicationModule(
    displayName = "Billing",
    allowedDependencies = {"tenant", "shared.persistence", "shared.lang"})
package com.zeromail.core.billing;

import org.springframework.modulith.ApplicationModule;
```

**File 2: `backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/package-info.java`**
```java
/**
 * Allow-listed package for native SQL / raw JDBC inside the billing domain.
 *
 * <p>Phase 2B introduces {@code AdvisoryLockJdbcHelper} here for
 * {@code SELECT pg_advisory_xact_lock(hashtext(?))} — auto-released on commit; auto-released
 * on rollback. Wraps the SUM-balance check + RESERVE INSERT critical section per CONTEXT D-A1.
 *
 * <p><b>ArchUnit guard (Plan 06):</b> No class outside this sub-package may use
 * {@code org.springframework.jdbc.core.JdbcTemplate}. Mirror of
 * {@code core.gmail.persistence.lowlevel} (intra-domain marker — NOT a separate Modulith module).
 */
package com.zeromail.core.billing.persistence.lowlevel;
```

Note: do not add `@ApplicationModule` to the lowlevel sub-package — it is intra-domain only (per Phase 1.2 CL-3 pattern locked in 01.2-02-SUMMARY).
  </action>
  <verify>
    <automated>test -f backend/core/src/main/java/com/zeromail/core/billing/package-info.java; grep -q '"tenant", "shared.persistence", "shared.lang"' backend/core/src/main/java/com/zeromail/core/billing/package-info.java; ! grep -q "shared.privacy" backend/core/src/main/java/com/zeromail/core/billing/package-info.java; test -f backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/package-info.java; ! grep -q "@ApplicationModule" backend/core/src/main/java/com/zeromail/core/billing/persistence/lowlevel/package-info.java</automated>
  </verify>
  <done>billing/package-info.java declares Modulith allowedDependencies = {"tenant", "shared.persistence", "shared.lang"} (no shared.privacy); persistence/lowlevel/package-info.java is a marker comment with no @ApplicationModule annotation.</done>
</task>

<task type="auto">
  <name>Task 2: Domain enums + value records + exceptions in core.billing.model</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java,
    backend/core/src/main/java/com/zeromail/core/billing/model/CreditReservationStatus.java,
    backend/core/src/main/java/com/zeromail/core/billing/model/BillingTopupIntentStatus.java,
    backend/core/src/main/java/com/zeromail/core/billing/model/ReservationId.java,
    backend/core/src/main/java/com/zeromail/core/billing/model/CreditBalance.java,
    backend/core/src/main/java/com/zeromail/core/billing/model/InsufficientCreditsException.java,
    backend/core/src/main/java/com/zeromail/core/billing/model/IllegalLedgerStateException.java
  </files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/onboarding/model/OnboardingStep.java (verbatim shape — copy id()/fromId()/Stream.of(values()) idiom)
    - backend/core/src/main/java/com/zeromail/core/shared/lang/IdentifiedEnum.java (interface contract — id() must equal name() for enums per D-C2 invariant)
    - backend/core/src/main/java/com/zeromail/core/gmail/service/InvalidGrantException.java (exception analog — extends RuntimeException with no-args + message constructors)
    - .planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md (D-G2 — exception classes live in `core.billing.model` not `core.billing.service` so 2C can import through model surface)
    - .planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md (lines 100–148 — IdentifiedEnum + fromId fail-loud pattern)
  </read_first>
  <action>
**Enterprise-naming reminder (W9, do NOT skip):** Every `fromId` Stream-filter lambda below uses a domain-revealing parameter name (`callSite`, `status`, `intentStatus`). The existing `OnboardingStep.fromId` analog uses `e ->` — that file pre-dates CLAUDE.md §Backend Code Style and is NOT a style precedent. Copy the IDENTIFIER name from this plan, not from OnboardingStep.

**File 3: `CallSite.java`** (BILL-07 enum membership locked; SPEC R8)
```java
package com.zeromail.core.billing.model;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.zeromail.core.shared.lang.IdentifiedEnum;

/**
 * Billable call site cost map. Implements {@link IdentifiedEnum} (NOT {@code OrderedEnum} —
 * there is no progression order; the integer payload is {@code cost()}, not {@code weight()}).
 *
 * <p><b>Locked membership (D-G3 ArchUnit ban on additions):</b> {@code TRIAGE}, {@code DRAFT},
 * {@code PREVIEW}. There is intentionally NO {@code BYOK} member — BYOK traffic bypasses the
 * ledger entirely (gateway-side decision per Phase 2C). See {@link CreditLedger} Javadoc.
 *
 * <p><b>Costs (locked SPEC R1 + interview log Round 1):</b>
 * <ul>
 *   <li>{@code TRIAGE = 1}</li>
 *   <li>{@code DRAFT  = 2}</li>
 *   <li>{@code PREVIEW = 1}</li>
 * </ul>
 */
public enum CallSite implements IdentifiedEnum {

    TRIAGE(1),
    DRAFT(2),
    PREVIEW(1);

    private final int cost;

    CallSite(int cost) {
        this.cost = cost;
    }

    public int cost() {
        return cost;
    }

    @Override
    public String id() {
        return name();
    }

    public static CallSite fromId(String id) {
        return Stream.of(values())
                .filter(callSite -> callSite.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown CallSite id: " + id));
    }
}
```

**File 4: `CreditReservationStatus.java`** (D-B1)
```java
package com.zeromail.core.billing.model;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.zeromail.core.shared.lang.IdentifiedEnum;

public enum CreditReservationStatus implements IdentifiedEnum {

    PENDING,
    SETTLED,
    RELEASED;

    @Override
    public String id() {
        return name();
    }

    public static CreditReservationStatus fromId(String id) {
        return Stream.of(values())
                .filter(status -> status.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown CreditReservationStatus id: " + id));
    }
}
```

**File 5: `BillingTopupIntentStatus.java`** (D-C1)
Same shape as File 4. Members: `PENDING, PAID, EXPIRED`. Class JavaDoc: "Lifecycle of a SePay top-up intent. PENDING on create, PAID on webhook success, EXPIRED on TTL elapse without payment."

**File 6: `ReservationId.java`** (D-G2)
```java
package com.zeromail.core.billing.model;

import java.util.UUID;

/**
 * UUID-wrapping handle returned by {@link CreditLedger#reserve(UUID, CallSite)} and consumed
 * by {@link CreditLedger#settle(ReservationId)} / {@link CreditLedger#release(ReservationId)}.
 * Wrapper provides type safety so callers cannot accidentally pass a tenant UUID where a
 * reservation UUID is expected.
 */
public record ReservationId(UUID value) {
}
```

**File 7: `CreditBalance.java`** (D-G2; SPEC R1)
```java
package com.zeromail.core.billing.model;

/**
 * Read-projection of a tenant's current credit ledger state.
 *
 * <p>{@code availableCredits} = {@code SUM(amount_credits)} across all journal entries for
 * the tenant. {@code heldCredits} = sum of negative {@code RESERVE} amounts whose reservation
 * has not been finalized (no matching SETTLE/RELEASE entry yet).
 *
 * <p>Currency is implicit (integer credits). HTTP responses wrap this in
 * {@code BillingBalanceResponse(availableCredits, heldCredits, "credits")}.
 */
public record CreditBalance(int availableCredits, int heldCredits) {
}
```

**File 8: `InsufficientCreditsException.java`** (BILL-06; SPEC AC #11; Pitfall 7)
```java
package com.zeromail.core.billing.model;

/**
 * Thrown by {@link CreditLedger#reserve(java.util.UUID, CallSite)} when the tenant's
 * {@link CreditBalance#availableCredits()} is less than {@link CallSite#cost()}.
 *
 * <p><b>Privacy invariant (SPEC AC #11):</b> NO balance number in the exception payload.
 * The HTTP layer maps this to 402 with {@code code="error.billing.insufficient"} and
 * {@code params: Map.of()} — frontends localize via i18n and never read the actual balance
 * from the error response.
 */
public class InsufficientCreditsException extends RuntimeException {

    public InsufficientCreditsException() {
        super();
    }
}
```

**File 9: `IllegalLedgerStateException.java`** (D-D4)
```java
package com.zeromail.core.billing.model;

/**
 * Thrown by {@link CreditLedger} when a forbidden state transition is attempted —
 * e.g. {@code release} after {@code settle}, or {@code settle} after {@code release}.
 *
 * <p><b>HTTP mapping (D-D4):</b> Maps to HTTP 500 with
 * {@code code="error.billing.ledger.invalidState"}. This is a programming-error class, NOT a
 * user-recoverable condition — should not happen in normal flow.
 */
public class IllegalLedgerStateException extends RuntimeException {

    public IllegalLedgerStateException(String message) {
        super(message);
    }
}
```
  </action>
  <verify>
    <automated>./gradlew :backend:core:compileJava 2>&1 | tee /tmp/compile.log | grep -E "BUILD SUCCESSFUL|BUILD FAILED"; ! grep -E "(error|cannot find symbol).*billing/model" /tmp/compile.log; test -f backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java; grep -q "TRIAGE(1)" backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java; grep -q "DRAFT(2)" backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java; grep -q "PREVIEW(1)" backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java; grep -q "throws new NoSuchElementException" backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java || grep -q "NoSuchElementException" backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java; grep -q "implements IdentifiedEnum" backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java</automated>
  </verify>
  <done>7 model files exist; CallSite has exactly TRIAGE(1)/DRAFT(2)/PREVIEW(1) and implements IdentifiedEnum; both status enums have fromId throwing NoSuchElementException; ReservationId/CreditBalance are records; InsufficientCreditsException has no-args constructor + privacy Javadoc; IllegalLedgerStateException takes String message; `./gradlew :backend:core:compileJava` BUILD SUCCESSFUL.</done>
</task>

<task type="auto">
  <name>Task 3: CreditLedger interface + BYOK exemption Javadoc</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/billing/model/CreditLedger.java
  </files>
  <read_first>
    - .planning/phases/02B-billing-prepaid-credits/02B-SPEC.md (Requirement 1 — interface signature; Requirement 8 — BYOK exemption Javadoc clause)
    - .planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md (D-D1 — Phase 2C lifecycle pattern locked; D-D2/D3 — settle/release idempotency contract)
    - .planning/phases/02B-billing-prepaid-credits/02B-RESEARCH.md (interface signature in §"Recommended Project Structure" line 350)
  </read_first>
  <action>
**File 10: `CreditLedger.java`** — the cross-phase contract Phase 2C imports verbatim. Javadoc carries the BYOK exemption clause (SPEC R8) and the D-D1 settle/release lifecycle pattern.

```java
package com.zeromail.core.billing.model;

import java.util.UUID;

/**
 * Prepaid credit ledger — the cross-phase contract that Phase 2C ({@code core.llm.LlmGateway})
 * imports verbatim. Implementations live in {@code core.billing.service}; callers depend on
 * this interface only (D-G3 ArchUnit ban on direct instantiation of the implementation class
 * outside {@code core.billing.service}).
 *
 * <h3>Reserve / settle / release lifecycle (D-D1)</h3>
 *
 * <p>Phase 2C calls {@link #settle(ReservationId)} on success and {@link #release(ReservationId)}
 * on the exception path:
 *
 * <pre>{@code
 * ReservationId reservationId = creditLedger.reserve(tenantId, CallSite.TRIAGE);
 * try {
 *     ChatResponse response = chatClient.call(prompt);
 *     creditLedger.settle(reservationId);
 *     return response;
 * } catch (Exception failure) {
 *     creditLedger.release(reservationId);
 *     throw failure;
 * }
 * }</pre>
 *
 * <p>The watchdog ({@code backend/worker.billing.CreditReserveWatchdog}) is the safety net
 * for crashes between {@code reserve} and {@code settle}/{@code release} — NOT the
 * steady-state finalizer. Reservations older than 5 minutes with no SETTLE/RELEASE journal
 * entry are auto-released by the watchdog.
 *
 * <h3>Idempotency (D-D2 + D-D3)</h3>
 *
 * <ul>
 *   <li>{@code settle} called twice on the same reservation: second call is a no-op (UNIQUE
 *       constraint on the journal blocks the duplicate; sidecar status already SETTLED).</li>
 *   <li>{@code release} called twice on the same reservation: second call is a no-op.</li>
 *   <li>{@code release} after {@code settle} (or vice versa): throws
 *       {@link IllegalLedgerStateException} — forbidden transition (D-D4).</li>
 * </ul>
 *
 * <h3>Concurrency (D-A1 + D-A2)</h3>
 *
 * <p>{@link #reserve(UUID, CallSite)} runs in {@code Propagation.REQUIRES_NEW} so an outer
 * transaction failure cannot roll back a successful reserve. The implementation acquires a
 * per-tenant Postgres advisory lock ({@code pg_advisory_xact_lock(hashtext(tenantId))})
 * inside the same transaction as the SUM-balance check + RESERVE INSERT, making the entire
 * critical section serialized for one tenant. Two concurrent {@code reserve} calls on
 * {@code available=1} cannot both succeed.
 *
 * <p>{@link #settle(ReservationId)} and {@link #release(ReservationId)} run in
 * {@code Propagation.REQUIRED} (caller-controlled atomicity).
 *
 * <h3>BYOK exemption (BILL-07 — SPEC R8 verbatim)</h3>
 *
 * <p><b>BYOK traffic bypasses the ledger entirely.</b> Phase 2C's {@code LlmGateway} MUST
 * check the {@code tenant_byok_credentials} table before calling {@link #reserve} and skip
 * this method when a BYOK row exists for the tenant. The {@link CallSite} enum has no BYOK
 * member because BYOK traffic does not enter this interface.
 *
 * <h3>Privacy invariants</h3>
 *
 * <ul>
 *   <li>{@link InsufficientCreditsException} carries no balance number — frontends infer
 *       from HTTP 402 + i18n key only (SPEC AC #11).</li>
 *   <li>Implementation logs use the {@code event=opaque tenantId={}} format (CLAUDE.md §4).</li>
 * </ul>
 *
 * @see CallSite
 * @see ReservationId
 * @see CreditBalance
 * @see InsufficientCreditsException
 * @see IllegalLedgerStateException
 */
public interface CreditLedger {

    /**
     * Atomically reserve {@code callSite.cost()} credits against the tenant's available
     * balance.
     *
     * @return a handle for the subsequent {@link #settle(ReservationId)} or
     *         {@link #release(ReservationId)} call.
     * @throws InsufficientCreditsException if {@code availableCredits < callSite.cost()}.
     */
    ReservationId reserve(UUID tenantId, CallSite callSite);

    /**
     * Finalize a reservation as consumed (no credit return). Idempotent on repeat call;
     * throws {@link IllegalLedgerStateException} if the reservation is already RELEASED.
     */
    void settle(ReservationId reservationId);

    /**
     * Reverse a reservation back to available balance. Idempotent on repeat call; throws
     * {@link IllegalLedgerStateException} if the reservation is already SETTLED.
     */
    void release(ReservationId reservationId);

    /**
     * Read the tenant's current available + held credit balance. Read-only transaction.
     */
    CreditBalance balance(UUID tenantId);
}
```

After saving, run `./gradlew :backend:core:compileJava` — must be GREEN. Plan 03 lands the implementation.
  </action>
  <verify>
    <automated>./gradlew :backend:core:compileJava 2>&1 | grep -E "BUILD SUCCESSFUL|BUILD FAILED" | head -1 | grep -q SUCCESSFUL; test -f backend/core/src/main/java/com/zeromail/core/billing/model/CreditLedger.java; grep -q "BYOK traffic bypasses the ledger entirely" backend/core/src/main/java/com/zeromail/core/billing/model/CreditLedger.java; grep -q "ReservationId reserve(UUID tenantId, CallSite callSite)" backend/core/src/main/java/com/zeromail/core/billing/model/CreditLedger.java; grep -q "void settle(ReservationId reservationId)" backend/core/src/main/java/com/zeromail/core/billing/model/CreditLedger.java; grep -q "CreditBalance balance(UUID tenantId)" backend/core/src/main/java/com/zeromail/core/billing/model/CreditLedger.java</automated>
  </verify>
  <done>CreditLedger.java exists; all 4 methods declared with exact signatures from SPEC R1; Javadoc contains the BYOK exemption clause verbatim ("BYOK traffic bypasses the ledger entirely"); D-D1 lifecycle code block embedded; D-A1/A2 concurrency note embedded; `./gradlew :backend:core:compileJava` BUILD SUCCESSFUL.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| `core.billing` package boundary | Modulith-enforced — only `tenant`, `shared.persistence`, `shared.lang` may be referenced from inside billing; nothing leaks the other way (Plan 06 verifies via ArchUnit + ApplicationModulesTest). |
| Phase 2B → Phase 2C contract | `CreditLedger` interface is the only symbol Phase 2C imports from billing; downstream stability is required. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-02B-02-01 | Tampering | CallSite enum membership | mitigate | ArchUnit test (Plan 00 RED scaffold; Plan 06 finalize) asserts {TRIAGE, DRAFT, PREVIEW} membership locked — prevents accidental BYOK addition by Phase 2C devs. DB CHECK constraint on credit_ledger_entry.kind (Plan 01) is defense-in-depth. |
| T-02B-02-02 | Information disclosure | InsufficientCreditsException payload | mitigate | No-args constructor (no message field carrying balance number); Javadoc explicitly forbids; Plan 04 GlobalExceptionHandler sets `params: Map.of()`; Plan 00 RED test BillingInsufficientCreditsTest asserts no balance leak. |
| T-02B-02-03 | Repudiation | Cross-phase contract drift | mitigate | CreditLedger interface Javadoc includes the verbatim BYOK exemption clause from SPEC R8 — Phase 2C plan-phase will quote this clause when wiring the gateway. |
| T-02B-02-04 | Elevation of privilege | Modulith boundary | mitigate | package-info declares allowedDependencies = {tenant, shared.persistence, shared.lang} — no edge to account/gmail/onboarding/shared.privacy. ApplicationModulesTest enforces (Plan 06 verifies). |
</threat_model>

<verification>
- 10 files exist at the declared paths.
- `core.billing.package-info.java` has `allowedDependencies = {"tenant", "shared.persistence", "shared.lang"}` (no `shared.privacy`).
- `core.billing.model.CallSite` declares exactly `TRIAGE(1)`, `DRAFT(2)`, `PREVIEW(1)` and implements `IdentifiedEnum`.
- `core.billing.model.CreditLedger` interface has the 4 SPEC-locked method signatures + the BYOK exemption Javadoc clause.
- `./gradlew :backend:core:compileJava` BUILD SUCCESSFUL.
- ApplicationModulesTest will fail in Plan 06 if the package-info is malformed — but in this plan, only compile-time correctness is checked.
</verification>

<success_criteria>
- 10 source files committed.
- `./gradlew :backend:core:check` passes (compile + existing ArchUnit; new billing-specific ArchUnit added in Plan 06).
- Phase 2C plan-phase can import `com.zeromail.core.billing.model.CreditLedger` immediately (no circular dependency).
- Plan 03 (next) builds the implementation against this exact interface.
</success_criteria>

<output>
After completion, create `.planning/phases/02B-billing-prepaid-credits/02B-02-SUMMARY.md`.
</output>
