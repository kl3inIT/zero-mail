---
phase: 01-foundation-safety-infrastructure
fixed_at: 2026-04-25T16:00:00Z
review_path: .planning/phases/01-foundation-safety-infrastructure/01-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase 1: Code Review Fix Report

**Fixed at:** 2026-04-25T16:00:00Z
**Source review:** `.planning/phases/01-foundation-safety-infrastructure/01-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 5
- Fixed: 5
- Skipped: 0

## Fixed Issues

### WR-04: Exception Responses Leak Raw Exception Messages And Use Generic Exceptions As API Contracts

**Files modified:**
- `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java`
- `backend/core/src/main/java/com/zeromail/core/account/CurrentUserNotFoundException.java` (new)
- `backend/core/src/main/java/com/zeromail/core/account/package-info.java` (new)

**Commit:** 171460b

**Applied fix:** Replaced `Map<String,String>` responses with RFC 7807 `ProblemDetail` bodies keyed by stable i18n message keys (`error.currentUserNotFound`, `error.unauthorized`, `error.forbidden`, `error.dataIntegrity`, `error.conflict`, `error.badRequest`). Added typed handlers for `CurrentUserNotFoundException` (401, NOT 409 — corrects the previous IllegalState-as-conflict mapping for missing-user), `AuthenticationException` (401), `AccessDeniedException` (403), and `DataIntegrityViolationException` (409). Server-side logs the underlying exception class name; client-visible bodies never echo `e.getMessage()`. The new `CurrentUserNotFoundException` lives in the new `com.zeromail.core.account` Spring Modulith module (`allowedDependencies = persistence, tenant`).

**Verification:** Tier 1 re-read; Tier 2 `gradlew :backend:core:compileJava :backend:api:compileJava` PASS; full test suite PASS.

---

### WR-01: Controllers Own Repository And Transaction Boundaries

**Files modified:**
- `backend/core/src/main/java/com/zeromail/core/account/AccountService.java` (new)
- `backend/core/src/main/java/com/zeromail/core/account/TenantConnectionService.java` (new)
- `backend/core/src/main/java/com/zeromail/core/account/CurrentUserView.java` (new)
- `backend/core/src/main/java/com/zeromail/core/account/TenantConnectionView.java` (new)
- `backend/api/src/main/java/com/zeromail/api/controllers/AccountDeletionController.java`
- `backend/api/src/main/java/com/zeromail/api/controllers/DisconnectController.java`
- `backend/api/src/main/java/com/zeromail/api/controllers/MeController.java`
- `backend/api/src/main/java/com/zeromail/api/controllers/TenantStatusController.java`
- `backend/core/src/test/java/com/zeromail/core/support/CoreTestApplication.java`
- `backend/api/src/test/java/com/zeromail/api/arch/ControllerBoundaryArchTests.java` (new)

**Commit:** 08d9eb9

**Applied fix:** Introduced `AccountService` (delete cascade, `requireCurrentUser`) and `TenantConnectionService` (status read, disconnect) inside `com.zeromail.core.account`. All four controllers (Account/Disconnect/Me/TenantStatus) became thin transport adapters: resolve tenant id from `TenantContext`, delegate to service. Transactions moved off `@Transactional` controller methods onto service methods (`@Transactional`, `@Transactional(readOnly=true)`).

To enforce the controller→repository invariant going forward, added `ControllerBoundaryArchTests` with two ArchUnit rules: controllers must not depend on `*Repository` types and must not depend on `*Entity` types. Service methods therefore return DTO-shaped record views (`CurrentUserView`, `TenantConnectionView`) so controllers never import persistence-managed entities. `CoreTestApplication` gained explicit `@EnableJpaRepositories` / `@EntityScan` because the new service beans pull in repositories during the core test context boot.

HTTP contract preserved: `/me`, `/me/account`, `/tenant/status`, `/tenant/disconnect` keep their existing status codes and response shapes (verified by `OpenApiSchemaTest` and `AccountDeletionE2ETest`).

**Verification:** Tier 1 re-read; Tier 2 compile PASS; `:backend:core:test` PASS (15 tests including new `ControllerBoundaryArchTests`); `:backend:api:test` PASS.

**Status:** fixed

---

### WR-03: Onboarding Endpoints Can Return Success Without Updating A User

**Files modified:**
- `backend/core/src/main/java/com/zeromail/core/account/OnboardingService.java` (new)
- `backend/api/src/main/java/com/zeromail/api/controllers/OnboardingController.java`

**Commit:** f6478dc

**Applied fix:** `OnboardingService.selectTemplate` and `OnboardingService.complete` now load the current user FIRST and throw `CurrentUserNotFoundException` (401, via `GlobalExceptionHandler`) when no user row exists, BEFORE writing any dependent rows. Previously `selectTemplate` could save an `OnboardingSelectionEntity` and then silently no-op the user-state advance, leaving the tenant in an inconsistent state with the client seeing 2xx. `OnboardingController` is now a thin delegator and both endpoints share the same fail-fast invariant via the service.

**Verification:** Tier 1 re-read; Tier 2 compile PASS; `OnboardingStateMachineTest` PASS (the existing test creates a user before invoking the controller, so the new precondition is satisfied).

**Status:** fixed: requires human verification (logic change — previously the missing-user case returned 2xx; now it raises 401. Confirm this is the desired client contract before user-facing rollout.)

---

### WR-02: OAuth User Provisioning Is Not Atomic

**Files modified:**
- `backend/core/src/main/java/com/zeromail/core/account/OAuthProvisioningService.java` (new)
- `backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java`

**Commit:** a563e9b

**Applied fix:** Introduced `OAuthProvisioningService` that owns the find-or-create flow inside a transaction. The create path runs in `@Transactional(propagation = REQUIRES_NEW)` so a `DataIntegrityViolationException` (concurrent first-login losing the `google_subject` unique-constraint race) rolls back the inner tx — including the freshly inserted tenant row, avoiding orphans — while the outer caller catches the exception, re-reads by Google subject, and converges on the existing user. A `@Lazy` self-reference on the bean is used so the proxy applies `@Transactional` correctly to the inner call.

`GoogleOAuthSuccessHandler` is now a thin transport adapter that pulls subject + email out of the `OidcUser` principal and delegates to the service. The service signature deliberately takes plain `String` arguments rather than `OidcUser` so `core` does not have to depend on Spring Security's OAuth2 module (which would push the modulith allowed-dependencies graph beyond `persistence, tenant`).

Privacy: `log.warn` on the race path deliberately omits subject and email per the project's no-PII-in-logs contract.

**Verification:** Tier 1 re-read; Tier 2 compile PASS; tests PASS. The race path is currently exercised only by the explicit catch in code (no concurrent-first-login integration test exists yet — recommend adding one in a later plan).

**Status:** fixed: requires human verification (transaction propagation change — confirm `REQUIRES_NEW` semantics + self-injection pattern against your team's preferred style; alternative is to split into two beans).

---

### WR-05: Log Scrub Signal Is Sticky Because It Is Written To MDC From A TurboFilter

**Files modified:**
- `backend/core/src/main/java/com/zeromail/core/privacy/SensitiveMarkerScrubFilter.java`
- `backend/core/src/main/resources/logback-spring.xml`
- `backend/core/src/test/java/com/zeromail/core/privacy/SensitiveMarkerScrubFilterTest.java`
- `backend/api/src/test/java/com/zeromail/api/LogScrubSyntheticTrafficTest.java`

**Commit:** 90dcc36

**Applied fix:** Converted `SensitiveMarkerScrubFilter` from `TurboFilter` (which wrote `scrubbed=true` to thread-local MDC) into an appender-level `Filter<ILoggingEvent>`. The filter inspects each fully-built `ILoggingEvent`'s formatted message and, on detecting a stray `Sensitive(...)` token, attaches `scrubbed=true` + `scrub_reason=sensitive_marker` to a copy-on-write of THAT event's own `mdcPropertyMap` only — never thread-local MDC. The mutation uses `Logback`'s `LoggingEvent.mdcPropertyMap` field via reflection because the public `setMDCPropertyMap` setter rejects re-assignment after capture; this is documented in the file with a guard that fails loudly during tests if Logback ever renames the field.

`logback-spring.xml` now registers the filter against the STDOUT appender (`<filter>`), not as a global `<turboFilter>`. `SensitiveMarkerScrubFilterTest` gained a `scrub_marker_does_not_leak_into_subsequent_events` regression test that emits a sensitive event followed by a clean event on the same thread and asserts only the first carries the marker. `LogScrubSyntheticTrafficTest`'s ROOT-logger `ListAppender` now installs the same filter so the runtime grep-for-bodies suite continues to observe per-event enrichment.

The privacy guarantee is unchanged: the primary redaction contract is still `Sensitive#toString() -> ***REDACTED***`; the filter remains the structured signal for SOC alerting on bypass.

**Verification:** Tier 1 re-read; Tier 2 compile PASS; `SensitiveMarkerScrubFilterTest` (4 tests including new regression) PASS; `LogScrubSyntheticTrafficTest` PASS.

**Status:** fixed

---

## Skipped Issues

None.

---

_Fixed: 2026-04-25T16:00:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
