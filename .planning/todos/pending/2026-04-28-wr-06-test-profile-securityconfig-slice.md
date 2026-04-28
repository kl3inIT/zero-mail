---
created: 2026-04-28T00:00:00Z
title: WR-06 — test-profile SecurityConfig slice for OAuth filter chain coverage
area: testing
source:
  phase: 01.5
  finding: WR-06 (REVIEW.md)
  status: deferred_in_review_fix
files:
  - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
  - backend/api/src/test/java/com/zeromail/api/ApiPostgresTestBase.java
---

## Problem

`SecurityConfig` is annotated `@Profile("!test")`, so the bundled OAuth filter chain
(login flow, redirect resolver, success/failure handlers, CSRF behavior) is NEVER exercised
under integration tests. Phase 01.5 plan 07 added a thin controller-slice test for the
new GET endpoint, but the runtime path through the OAuth filters remains untested.

This is currently a coverage gap, not a correctness defect — it was deferred during
`/gsd-code-review-fix 01.5` because the safe fix is a non-trivial refactor that exceeds
a single fixer pass. Without it, future regressions in the OAuth wiring (handler
ordering, CSRF, state parameter handling, scope-required → consent_denied transitions)
will not surface until manual UAT or production.

## Solution

Introduce a parallel `@Profile("test-security")` `SecurityConfig` test slice that wires
only what integration tests need (filter chain + handlers + mock `ClientRegistrationRepository`).
Tests that need the OAuth path activate the `test-security` profile via `@ActiveProfiles`;
tests that don't keep the existing `!test` exclusion behavior.

**Approach:**
1. Extract the filter-chain DSL out of `SecurityConfig` into a package-private helper
   that can be reused by both production and test profiles
2. Add `SecurityConfigTestSlice` annotated `@Profile("test-security")` that wires the
   helper plus a mocked `ClientRegistrationRepository` for the bundled `google` registration
3. Update `BundledGoogleOAuthIntegrationTest` (and any new reconnect-path test from WR-03
   / Plan 07) to use `@ActiveProfiles("test", "test-security")`
4. Verify GET `/tenant/connect-gmail` returns 302 with the OAuth Location header THROUGH
   the filter chain (not bypassing it)
5. Verify scope-missing throw produces the `/login?error=gmail_scope_required` redirect
   end-to-end through the failure handler

**Acceptance:** at least one integration test exercises the OAuth filter chain end-to-end;
the `@Profile("!test")` exclusion remains the default for tests that don't opt in.

## Estimated effort

Medium — likely a focused 1-task plan. Schedule for either Phase 2A (when other Pub/Sub
OIDC verification work touches the filter chain) or as a dedicated Phase 01.6 if it
should land before 2A.
