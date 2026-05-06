---
phase: 02B
plan: 06
type: execute
wave: 4
depends_on: [00, 01, 02, 03, 04, 05]
files_modified:
  - backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java
  - backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java
  - backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java
  - .planning/REQUIREMENTS.md
autonomous: true
requirements: [BILL-01, BILL-02, BILL-03, BILL-04, BILL-05, BILL-06, BILL-07]
must_haves:
  truths:
    - "ArchUnit DomainBoundaryArchTests has a billing-specific rule banning core.billing from depending on account/onboarding/gmail/tenant.persistence repositories — and the existing 4 per-domain rules add `..core.billing.persistence..` to their exclusion arrays."
    - "BillingDomainBoundaryArchTest (Wave 0 file) flipped to GREEN: JdbcTemplate banned outside core.billing.persistence.lowlevel; CreditLedgerService not directly instantiated outside core.billing.service; core.billing only depends on allowed packages."
    - "CallSiteEnumMembershipArchTest (Wave 0 file) flipped to GREEN: CallSite enum membership exactly {TRIAGE, DRAFT, PREVIEW} with costs {1, 2, 1}."
    - "ApplicationModulesTest passes — core.billing module declares displayName=Billing + allowedDependencies={tenant, shared.persistence, shared.lang}, no boundary violations."
    - "REQUIREMENTS.md rows BILL-01..BILL-07 status flipped from `Pending` to `Phase 2B` ✓ in the traceability table."
    - "./gradlew clean check BUILD SUCCESSFUL across all three backend modules + pnpm i18n:check STRICT GREEN."
  artifacts:
    - path: "backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java"
      provides: "Updated cross-domain repo bans + new billing-specific rule (per Phase 1.2 D-D1 pattern)."
    - path: "backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java"
      provides: "@AnalyzeClasses-driven D-G3 ArchUnit invariants flipped GREEN."
    - path: "backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java"
      provides: "Plain JUnit assertions on CallSite membership flipped GREEN."
    - path: ".planning/REQUIREMENTS.md"
      provides: "Traceability table flipped: BILL-01..BILL-07 = `Phase 2B` ✓."
  key_links:
    - from: "DomainBoundaryArchTests"
      to: "core.billing.persistence package boundary"
      via: "ArchUnit per-domain repo ban rule"
      pattern: "billing_no_cross_domain_repos"
    - from: "ApplicationModulesTest"
      to: "core.billing.package-info @ApplicationModule"
      via: "Spring Modulith verifies()"
      pattern: "modules.verify"
---

<objective>
Close Phase 2B by landing the ArchUnit boundary tests Plan 00 scaffolded RED, extending the existing DomainBoundaryArchTests for cross-domain repo bans, verifying ApplicationModulesTest passes, flipping REQUIREMENTS.md status for all 7 BILL-* IDs, and running the full check suite. After this plan, the phase ships.

Purpose: per CONTEXT D-G3, ArchUnit invariants are: (a) JdbcTemplate ban outside core.billing.persistence.lowlevel; (b) CreditLedgerService not instantiated outside core.billing.service; (c) CallSite enum membership locked. Per Phase 1.2 D-D1 pattern, the existing DomainBoundaryArchTests gets a new billing-specific cross-domain repo ban rule + the existing 4 rules' exclusion arrays widen.

Output: 1 modified ArchUnit file (existing DomainBoundaryArchTests) + 2 new ArchUnit files flipped from RED to GREEN + 1 REQUIREMENTS.md update.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/REQUIREMENTS.md
@.planning/phases/02B-billing-prepaid-credits/02B-SPEC.md
@.planning/phases/02B-billing-prepaid-credits/02B-CONTEXT.md
@.planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md
@CLAUDE.md
@backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java
@backend/core/src/test/java/com/zeromail/core/arch/ApplicationModulesTest.java
@backend/core/src/main/java/com/zeromail/core/billing/package-info.java
@backend/core/src/main/java/com/zeromail/core/billing/model/CallSite.java
@backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java
</context>

<tasks>

<task type="auto">
  <name>Task 1: Extend DomainBoundaryArchTests + flip Wave 0 ArchUnit + enum-membership tests GREEN</name>
  <files>
    backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java,
    backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java,
    backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java
  </files>
  <read_first>
    - backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java (existing per-domain rule shape — Phase 1.2 D-Plan 06 created 4 rules: account_no_cross_domain_repos / onboarding_no_cross_domain_repos / gmail_no_cross_domain_repos / tenant_no_cross_domain_repos. EXTEND each rule's exclusion array to include `..core.billing.persistence..` AND add a 5th rule billing_no_cross_domain_repos.)
    - backend/core/src/test/java/com/zeromail/core/arch/ApplicationModulesTest.java (existing — verify modules() reports core.billing once and verifies the package-info declaration; add a new test method asserting `Modules.of("com.zeromail.core").getModuleByName("billing")` has the locked allowedDependencies set if not auto-covered)
    - backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java (Plan 00 RED scaffold — file exists but @Disabled; remove @Disabled and ensure imports + ArchUnit predicates compile against actual landed classes)
    - backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java (Plan 00 RED scaffold — same flip-off-Disabled)
    - .planning/phases/02B-billing-prepaid-credits/02B-PATTERNS.md (lines 924–950 — DomainBoundaryArchTests extension specifics; lines 942–950 — billing-specific D-G3 rules)
    - backend/core/src/main/java/com/zeromail/core/billing/service/CreditLedgerService.java (Plan 03 output — confirm package-private visibility so the ArchUnit "not instantiated outside service package" rule has something to test)
  </read_first>
  <action>
**File 1: `DomainBoundaryArchTests.java`** (modify in place)

Two changes:

1. **Widen each existing rule's exclusion array** to add `..core.billing.persistence..`. The existing 4 rules (account/onboarding/gmail/tenant) each have a `noClasses().that().resideInAPackage("..core.<domain>..").should().dependOnClassesThat(nameEndsWithRepository.and(resideInAnyPackage("..core.<other1>.persistence..", "..core.<other2>.persistence..", ...)))` shape. Add `"..core.billing.persistence.."` to each rule's `resideInAnyPackage(...)` list.

2. **Add a 5th rule:**
```java
@ArchTest
static final ArchRule billing_no_cross_domain_repos = noClasses()
        .that().resideInAPackage("..core.billing..")
        .should().dependOnClassesThat(
                nameEndsWithRepository.and(resideInAnyPackage(
                        "..core.account.persistence..",
                        "..core.onboarding.persistence..",
                        "..core.gmail.persistence..",
                        "..core.tenant.persistence..")))
        .because("D-D1 (Phase 1.2): cross-domain reads must go through the other domain's Service");
```

The exact predicate names (`nameEndsWithRepository`, `resideInAnyPackage`) come from imports already present in the file (per Phase 1.2 D-Plan 06 SUMMARY). Verify before saving.

**File 2: `BillingDomainBoundaryArchTest.java`** (flip Wave 0 RED → GREEN)

Open Plan 00's RED scaffold. Remove all `@Disabled("Wave 0 RED scaffold...")` annotations. Confirm the three @ArchTest rules from Plan 00 Task 1 File 7 still match the implementation:

1. `jdbc_template_only_in_lowlevel` — `noClasses().that().resideInAPackage("..core.billing..").and().resideOutsideOfPackage("..core.billing.persistence.lowlevel..").should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc.core..")`. Should pass because Plan 03 only uses `JdbcTemplate` inside `AdvisoryLockJdbcHelper` which is in `lowlevel/`.

2. `credit_ledger_service_not_instantiated_outside_billing_service` — **REVIEWS HIGH-3 NEW (RESOLVED):** `CreditLedgerService` is package-private in `com.zeromail.core.billing.service` (Plan 03), so a `.class` literal from `BillingDomainBoundaryArchTest` (which lives in `com.zeromail.core.billing`, the parent package) WILL NOT COMPILE. Use ArchUnit's string-FQN form, which takes the fully-qualified name as a `String` and does NOT require a compile-time reference to the type:

   ```java
   @ArchTest
   static final ArchRule credit_ledger_service_not_instantiated_outside_billing_service =
       noClasses()
           .that().resideOutsideOfPackage("..core.billing.service..")
           .should().dependOnClassesThat()
           .haveFullyQualifiedName("com.zeromail.core.billing.service.CreditLedgerService")
           .because("D-G3: CreditLedgerService is package-private; callers must use the public CreditLedger interface");
   ```

   This works because `haveFullyQualifiedName(String)` is part of ArchUnit's `JavaClass.Predicates` family and does string matching at analysis time — the test class never needs the `CreditLedgerService.class` literal. The package-private visibility on `CreditLedgerService` itself is the primary defense; this ArchUnit rule is the secondary boundary that also catches reflective/`Class.forName` access drift. As a tertiary belt: keep the existing JUnit assertion `assertThat(CreditLedgerService.class.getModifiers() & Modifier.PUBLIC).isZero()` ONLY inside a test class that lives in the SAME package (`com.zeromail.core.billing.service`) — DO NOT put a `.class` literal in `BillingDomainBoundaryArchTest`. If the modifier-check is desired, place it in a separate `CreditLedgerServiceVisibilityTest.java` under `backend/core/src/test/java/com/zeromail/core/billing/service/`.

   Targeting the public `CreditLedger` interface is also acceptable and arguably cleaner: `noClasses().that().resideOutsideOfPackage("..core.billing.service..").should().dependOnClassesThat().haveFullyQualifiedName("com.zeromail.core.billing.service.CreditLedgerService")` — keep using the FQN-string form for the implementation class because that is the boundary we are guarding. The interface is intentionally open for callers; the rule is about the impl.

3. `core_billing_only_depends_on_allowed_packages` — assert `core.billing` only depends on `core.billing.*`, `core.tenant.*`, `core.shared.persistence.*`, `core.shared.lang.*`, plus standard JDK + Spring + Hibernate + SLF4J + Jackson + Jakarta packages. Mirror the rule from `02B-CONTEXT.md` D-G1.

If any of these rules generate false-positives against unanticipated dependencies (e.g., `org.springframework.dao.DataIntegrityViolationException` in `CreditLedgerService` requires `org.springframework.dao..` in the allowed list), widen the allowlist explicitly — do NOT loosen the rule's spirit. The boundary is `core.billing` cannot import other domain modules.

**File 3: `CallSiteEnumMembershipArchTest.java`** (flip Wave 0 RED → GREEN)

Open Plan 00's RED scaffold. Remove `@Disabled` annotations. The 3 `@Test` methods asserting `CallSite.values().length == 3`, member-name set, and costs should pass directly because Plan 02 declared `TRIAGE(1), DRAFT(2), PREVIEW(1)`.

After saving all 3 files, run:
```
./gradlew :backend:core:test --tests "com.zeromail.core.arch.DomainBoundaryArchTests" --tests "com.zeromail.core.billing.BillingDomainBoundaryArchTest" --tests "com.zeromail.core.billing.CallSiteEnumMembershipArchTest" --tests "*ApplicationModulesTest*"
```

All four test classes must report BUILD SUCCESSFUL. ApplicationModulesTest auto-discovers the new core.billing module from package-info — no test code change needed unless the existing test enumerates modules (in which case add `billing` to the assertion list).
  </action>
  <verify>
    <automated>grep -q "billing_no_cross_domain_repos" backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java; grep -q '..core.billing.persistence..' backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java; ! grep -E '@Disabled.*Wave 0' backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java; ! grep -E '@Disabled.*Wave 0' backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java; ./gradlew :backend:core:test --tests "*DomainBoundaryArchTests*" --tests "*BillingDomainBoundaryArchTest*" --tests "*CallSiteEnumMembershipArchTest*" --tests "*ApplicationModulesTest*" 2>&1 | grep -E "BUILD SUCCESSFUL"</automated>
  </verify>
  <done>DomainBoundaryArchTests has 5 per-domain rules (added billing_no_cross_domain_repos); existing 4 rules' exclusion arrays widened with ..core.billing.persistence..; BillingDomainBoundaryArchTest + CallSiteEnumMembershipArchTest no longer @Disabled and pass GREEN; ApplicationModulesTest passes (Modulith boundary verified).</done>
</task>

<task type="auto">
  <name>Task 2: REQUIREMENTS.md status flip (BILL-01..BILL-07) + full ./gradlew clean check sweep</name>
  <files>
    .planning/REQUIREMENTS.md
  </files>
  <read_first>
    - .planning/REQUIREMENTS.md (lines 164–170 — BILL-01..BILL-07 currently `| Phase 2B | Pending |`; flip Pending → ✓; lines 53–60 — billing requirement checkboxes `- [ ] **BILL-XX**: ...` flip to `- [x]`)
    - .planning/phases/02A-mail-ingestion/02A-05-SUMMARY.md or similar (Phase 2A's closing-plan REQUIREMENTS.md flip pattern — copy the marker convention)
    - .planning/phases/02B-billing-prepaid-credits/02B-SPEC.md (Acceptance Criteria checkboxes — assert all 16 are met before flipping)
  </read_first>
  <action>
**File 1: `.planning/REQUIREMENTS.md`** — two edits:

1. **Section "Billing (Prepaid Credits)"** (lines 53–60): Flip each `- [ ] **BILL-XX**: ...` to `- [x] **BILL-XX**: ...` for IDs 01 through 07. The Markdown file uses the `- [ ]` / `- [x]` checkbox convention — Phase 2A's MAIL-01..MAIL-06 (lines 32–37) flipped to `- [x]` is the exact precedent. Mirror that.

2. **Traceability table** (lines 164–170): Change the `Status` column for each BILL-XX row from `Pending` to a marker matching the existing Phase 2A convention (`Complete`). Read the file first to confirm — Phase 2A rows for MAIL-01..MAIL-06 currently say `Complete` (lines 158–163). Use the same `Complete` marker.

After saving, run the full sweep:

```
./gradlew clean check
pnpm --filter web i18n:check
pnpm --filter web generate:api
```

All three must BUILD SUCCESSFUL. If any test fails, fix in this plan (do not defer; this is the closing plan for the phase). Common late-stage failures:

- **ApplicationModulesTest** flagging the new `core.billing` module if package-info typo or unmatched `allowedDependencies` set. Fix in `core.billing.package-info.java` (Plan 02 output).
- **schema.d.ts diff** — if `pnpm generate:api` produces a non-empty diff vs the file Plan 04 committed, that's a leak in the openapi-emit pipeline; commit the regenerated file.
- **i18n:check** parity violation if vi.json and en.json have different leaf-key sets for `error.billing.*`. Fix by aligning the JSON structures.
- **DomainBoundaryArchTests** failing if the new billing rule's exclusion array misses a real cross-domain dependency. Decide case-by-case whether to widen the rule (legitimate dep) or fix the production code (boundary violation).

After all three commands BUILD SUCCESSFUL, the phase is complete.
  </action>
  <verify>
    <automated>grep -E '^\- \[x\] \*\*BILL-0[1-7]\*\*' .planning/REQUIREMENTS.md | wc -l | grep -q '^7$'; grep -E '\| BILL-0[1-7] \| Phase 2B \| Complete \|' .planning/REQUIREMENTS.md | wc -l | grep -q '^7$'; ./gradlew clean check 2>&1 | grep -E "BUILD SUCCESSFUL"; pnpm --filter web i18n:check 2>&1 | grep -E "PASS|0 issues|✓"</automated>
  </verify>
  <done>REQUIREMENTS.md has all 7 BILL-XX requirement checkboxes ticked + 7 traceability rows flipped to `Complete`; ./gradlew clean check BUILD SUCCESSFUL across all 3 backend modules; pnpm i18n:check STRICT PASSES; pnpm generate:api regenerates schema.d.ts cleanly.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| ArchUnit boundary tests | Compile-time + test-time enforcement of Modulith package boundaries; failures are CI gates. |
| REQUIREMENTS.md traceability | Source of truth for v1 status; closing-plan flips are durable record. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-02B-06-01 | Tampering | ArchUnit boundary drift | mitigate | DomainBoundaryArchTests + BillingDomainBoundaryArchTest run on every ./gradlew check; CI failure on any boundary violation. CallSiteEnumMembershipArchTest locks the BILL-07 enum membership invariant — Phase 2C cannot accidentally add a BYOK enum member. |
| T-02B-06-02 | Repudiation | REQUIREMENTS.md status drift | mitigate | Closing plan commits the BILL-XX ✓ flip atomically with the final ./gradlew check pass; Phase 2C plan-phase reads REQUIREMENTS.md to confirm 2B completion before consuming the CreditLedger interface. |
| T-02B-06-03 | Tampering | Modulith allowedDependencies drift | mitigate | ApplicationModulesTest (Phase 1 output, run on every check) verifies the package-info declaration matches the actual import graph. New rule for billing module auto-included via package scanning. |
</threat_model>

<verification>
- DomainBoundaryArchTests has 5 cross-domain repo ban rules; ArchUnit suite GREEN.
- BillingDomainBoundaryArchTest has 3 D-G3 rules GREEN.
- CallSiteEnumMembershipArchTest has 3 enum-membership assertions GREEN.
- ApplicationModulesTest GREEN — core.billing module declared per package-info.
- REQUIREMENTS.md flipped: 7 BILL-XX requirement checkboxes ticked + 7 traceability rows = `Complete`.
- ./gradlew clean check BUILD SUCCESSFUL across backend/core, backend/api, backend/worker.
- pnpm --filter web i18n:check STRICT GREEN.
- pnpm --filter web generate:api regenerates schema.d.ts cleanly (no diff or committed).
</verification>

<success_criteria>
- 4 files modified.
- All 16 SPEC Acceptance Criteria checkboxes met.
- All Wave 0 RED tests (16 files from Plan 00) flipped GREEN — verifiable by `grep -L '@Disabled' backend/{core,api,worker}/src/test/java/**/billing/*.java` returning every file (i.e., NO file has @Disabled annotation).
- REQUIREMENTS.md durable record of completion.
- ./gradlew clean check BUILD SUCCESSFUL.
- Phase 2C plan-phase unblocked: it can renumber Liquibase changesets to 018+ and import `com.zeromail.core.billing.model.CreditLedger` directly.
</success_criteria>

<output>
After completion, create `.planning/phases/02B-billing-prepaid-credits/02B-06-SUMMARY.md`. Phase 2B is COMPLETE.
</output>
