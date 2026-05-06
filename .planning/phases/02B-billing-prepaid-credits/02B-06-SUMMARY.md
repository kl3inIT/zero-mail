---
phase: 02B-billing-prepaid-credits
plan: 06
subsystem: verification-closure
tags: [billing, prepaid-credits, archunit, modulith, verification]

requires:
  - phase: 02B-00
    provides: Wave 0 boundary and enum tests.
  - phase: 02B-04
    provides: API billing DTO package used by Modulith verification.
  - phase: 02B-05
    provides: Worker billing schedulers.
provides:
  - Billing domain ArchUnit boundary tests enabled and passing.
  - CallSite enum membership lock enabled and passing.
  - DomainBoundaryArchTests extended with billing repository boundaries.
  - Full backend and frontend closure checks passing.
affects: [02C-llm-gateway]

tech-stack:
  added: []
  patterns:
    - Spring Modulith nested DTO packages expose controller-facing records through @NamedInterface.
    - New backend domains extend DomainBoundaryArchTests with one domain-specific repository rule and update the other domains' repository exclusion lists.

key-files:
  created:
    - backend/api/src/main/java/com/zeromail/api/dto/billing/package-info.java
  modified:
    - backend/core/src/test/java/com/zeromail/core/arch/DomainBoundaryArchTests.java
    - backend/core/src/test/java/com/zeromail/core/billing/BillingDomainBoundaryArchTest.java
    - backend/core/src/test/java/com/zeromail/core/billing/CallSiteEnumMembershipArchTest.java

key-decisions:
  - "dto.billing is exposed as a Spring Modulith @NamedInterface, matching existing dto.account/dto.gmail/dto.onboarding package conventions."
  - "core.billing's ArchUnit allowlist includes core.config because billing services intentionally read nested settings from ZeroMailCoreProperties."
  - "BILL-01..BILL-07 were already marked Complete in REQUIREMENTS.md by prior execution; no additional requirements edit was needed."

requirements-completed: [BILL-01, BILL-02, BILL-03, BILL-04, BILL-05, BILL-06, BILL-07]

duration: 20min
completed: 2026-05-06
---

# Phase 02B Plan 06: Verification Closure Summary

**Phase 02B closure gates are green.**

## Accomplishments

- Extended `DomainBoundaryArchTests` so account, onboarding, Gmail, and tenant domains also ban direct dependencies on `core.billing.persistence.*` repositories.
- Added the billing-specific `billing_no_cross_domain_repos` rule.
- Enabled `BillingDomainBoundaryArchTest` and `CallSiteEnumMembershipArchTest` by removing Wave 0 `@Disabled` annotations.
- Added `@NamedInterface("billing")` to `api.dto.billing` so `ZeroMailApiApplicationModulesTest` verifies the new billing DTO package cleanly.
- Confirmed `REQUIREMENTS.md` already marks all seven BILL requirements as complete.

## Commits

| Commit | Description |
|--------|-------------|
| `f20ac6a` | `test(02B-06): close billing boundary verification` |

## Verification

- `.\gradlew.bat :backend:core:test --tests "com.zeromail.core.arch.DomainBoundaryArchTests" --tests "com.zeromail.core.billing.BillingDomainBoundaryArchTest" --tests "com.zeromail.core.billing.CallSiteEnumMembershipArchTest" :backend:api:test --tests "com.zeromail.api.ZeroMailApiApplicationModulesTest"` - PASS.
- `.\gradlew.bat clean check` - PASS.
- `pnpm --filter web i18n:check` - PASS.
- `pnpm --filter web generate:api` - PASS.
- `rg "@Disabled" backend/core/src/test/java/com/zeromail/core/billing backend/api/src/test/java/com/zeromail/api/controllers/billing backend/worker/src/test/java/com/zeromail/worker/billing -n` - PASS, no matches.

## Deviations from Plan

- `ApplicationModulesTest` lives in `backend/api/src/test/java/com/zeromail/api/ZeroMailApiApplicationModulesTest.java`, not under `backend/core`.
- `REQUIREMENTS.md` already had the BILL rows checked and marked `Complete`; this plan verified rather than changed that file.
- Added `backend/api/src/main/java/com/zeromail/api/dto/billing/package-info.java` because Spring Modulith requires nested DTO packages used by controllers to be explicitly exposed.

## Self-Check: PASSED

- Billing Wave 0 tests are enabled.
- Domain and Modulith boundary checks pass.
- Full backend `clean check` passes.
- Frontend i18n and generated API schema gates pass.
