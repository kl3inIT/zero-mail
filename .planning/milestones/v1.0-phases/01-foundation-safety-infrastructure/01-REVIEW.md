---
phase: 01-foundation-safety-infrastructure
reviewed: 2026-04-25T15:29:25Z
depth: standard
files_reviewed: 12
files_reviewed_list:
  - backend/api/src/main/java/com/zeromail/api/Application.java
  - backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java
  - backend/api/src/main/java/com/zeromail/api/controllers/AccountDeletionController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/ConnectGmailController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/DisconnectController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/MeController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/OnboardingController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/TenantStatusController.java
  - backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java
  - backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java
  - backend/api/src/main/java/com/zeromail/api/security/GmailAccessGuard.java
  - backend/core/src/main/resources/logback-spring.xml
findings:
  critical: 0
  warning: 5
  info: 0
  total: 5
status: issues_found
---

# Phase 1: Code Review Report

**Reviewed:** 2026-04-25T15:29:25Z
**Depth:** standard
**Files Reviewed:** 12
**Status:** issues_found

## Summary

Reviewed the configured Zero Mail backend files at standard depth, using the local JHipster repo as a Spring Boot reference for account flows, exception translation, logging, and architecture tests. The main gap is not a syntax or framework issue: too much business and persistence behavior currently lives in controllers and authentication infrastructure. That weakens transaction boundaries, makes tenant invariants harder to centralize, and leaves error responses inconsistent.

## Warnings

### WR-01: Controllers Own Repository And Transaction Boundaries

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/AccountDeletionController.java:18`

**Issue:** Multiple controllers inject repositories directly and perform business transactions in the web layer. `AccountDeletionController` deletes onboarding rows, Gmail connections, users, and tenants directly at lines 18-40. The same pattern appears in `DisconnectController.java:17-30`, `OnboardingController.java:22-44`, `MeController.java:15-25`, and `TenantStatusController.java:16-27`. This makes account deletion, onboarding state transitions, and Gmail connection state hard to validate consistently, and it increases the chance that future endpoints bypass tenant or privacy invariants.

**Fix:** Introduce narrow application services and keep controllers as transport adapters. For example:

```java
@Service
public class AccountService {
    private final OnboardingSelectionRepository onboarding;
    private final GmailConnectionRepository connections;
    private final UserRepository users;
    private final TenantRepository tenants;

    @Transactional
    public void deleteCurrentTenantAccount(UUID tenantId) {
        onboarding.deleteAll(onboarding.findByTenantId(tenantId));
        connections.findByTenantId(tenantId).ifPresent(connections::delete);
        users.findFirstByTenantId(tenantId).ifPresent(users::delete);
        tenants.findById(tenantId).ifPresent(tenants::delete);
    }
}
```

Then have `AccountDeletionController` resolve the current tenant and delegate. Add an ArchUnit rule that prevents `..api.controllers..` from depending on `..core.persistence..`; allow repository access from `..service..` and carefully reviewed security bootstrap code only.

### WR-02: OAuth User Provisioning Is Not Atomic

**File:** `backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java:37`

**Issue:** The authentication success handler creates a tenant and then a user through separate repository calls at lines 37-42, with no enclosing service transaction. If `users.save(...)` fails after `tenants.save(...)`, the tenant row can be orphaned. Concurrent first-login requests for the same Google subject can also race between the `findByGoogleSubject` check and the unique user insert. JHipster's account flow keeps this kind of identity synchronization inside `UserService`, not inside the controller/success-handler adapter.

**Fix:** Move provisioning into a transactional service that handles duplicate insert races explicitly.

```java
@Service
public class OAuthProvisioningService {
    @Transactional
    public UserEntity findOrCreateGoogleUser(OidcUser oidc) {
        return users.findByGoogleSubject(oidc.getSubject())
                .orElseGet(() -> createTenantAndUser(oidc));
    }

    private UserEntity createTenantAndUser(OidcUser oidc) {
        UUID tenantId = UUID.randomUUID();
        tenants.save(new TenantEntity(tenantId, oidc.getEmail()));
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(() -> users.save(new UserEntity(
                        UUID.randomUUID(), tenantId, oidc.getSubject(), oidc.getEmail())));
    }
}
```

Catch `DataIntegrityViolationException` around the create path and re-read by Google subject so duplicate first-login attempts converge on the existing user.

### WR-03: Onboarding Endpoints Can Return Success Without Updating A User

**File:** `backend/api/src/main/java/com/zeromail/api/controllers/OnboardingController.java:34`

**Issue:** `selectTemplate` saves an onboarding selection before verifying that the current tenant has a user, then silently skips the state transition if `findFirstByTenantId` is empty at lines 34-36. `complete` also returns success without changing anything when the user is missing at lines 43-44. That can leave a tenant with selection data but no advanced onboarding state, while the client sees a 2xx response.

**Fix:** Load the current user first and fail with a typed not-found/current-user exception before writing dependent rows.

```java
@Transactional
public void selectTemplate(UUID tenantId, String templateKey) {
    var user = users.findFirstByTenantId(tenantId)
            .orElseThrow(() -> new CurrentUserNotFoundException(tenantId));
    onboarding.save(new OnboardingSelectionEntity(UUID.randomUUID(), tenantId, templateKey));
    user.advanceTo(OnboardingStep.TEMPLATE_SELECTED);
}
```

Expose this through an onboarding service so both endpoints share the same invariant.

### WR-04: Exception Responses Leak Raw Exception Messages And Use Generic Exceptions As API Contracts

**File:** `backend/api/src/main/java/com/zeromail/api/config/GlobalExceptionHandler.java:13`

**Issue:** `GlobalExceptionHandler` maps every `IllegalStateException` to `409 Conflict` and every `IllegalArgumentException` to `400 Bad Request`, then returns `e.getMessage()` directly at lines 13-22. `MeController` relies on this by throwing `IllegalStateException("user not found")` at `MeController.java:25`, which turns an authentication/current-user consistency problem into a conflict response. As the service layer grows, raw exception messages can expose internal state, persistence messages, provider errors, or sensitive operational details to clients.

**Fix:** Use typed application exceptions and return RFC 7807 `ProblemDetail` responses with stable error codes. Add handlers for validation and persistence/security failures, and sanitize details in production.

```java
@ExceptionHandler(CurrentUserNotFoundException.class)
ResponseEntity<ProblemDetail> onCurrentUserMissing(CurrentUserNotFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
    problem.setTitle("Current user is not available");
    problem.setProperty("message", "error.currentUserNotFound");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
}
```

Mirror the JHipster reference pattern conceptually: central exception translation, stable message keys, validation field errors, and integration tests for representative failures.

### WR-05: Log Scrub Signal Is Sticky Because It Is Written To MDC From A TurboFilter

**File:** `backend/core/src/main/resources/logback-spring.xml:3`

**Issue:** Logback wires `SensitiveMarkerScrubFilter` as a global `turboFilter` at line 3. The filter stamps `scrubbed` and `scrub_reason` into MDC when it sees `Sensitive(`, and `logback-spring.xml` includes those MDC keys in JSON output at lines 5-8. The filter implementation writes those MDC values in `SensitiveMarkerScrubFilter.java:42-43` but never clears them. Because MDC is thread-local/request-local state rather than per-event state, later log events on the same execution path can inherit `scrubbed=true` even when those events were not scrubbed. That creates false audit signals and makes security alerting noisy.

**Fix:** Do not use MDC as the durable per-event signal from a TurboFilter. Replace this with a per-event Logback filter/provider that inspects the `ILoggingEvent` and emits `scrubbed` only for that event, or add a dedicated structured logging wrapper for sensitive-event detection. If MDC remains temporarily, clear it in a finally block at request boundaries and document that it is request-scoped, not event-scoped.

## JHipster Practices To Adopt

- Put account, identity sync, onboarding, Gmail connection state, and account deletion behavior behind service classes. The local JHipster `AccountResource` delegates current-user work to `UserService`; Zero Mail should do the same for tenant-sensitive flows, with stricter repository boundaries than JHipster's generated entity endpoints.
- Add a real exception translator. The useful lesson is not JHipster's exact classes, but the pattern: stable problem types/message keys, validation field errors, explicit mappings for access denied/authentication/data access/concurrency failures, and tests like `ExceptionTranslatorIT`.
- Add architectural tests for layer boundaries. Zero Mail already has Modulith and tenant-isolation tests; add a rule that controllers cannot depend on repositories/entities except DTOs and explicitly approved adapters.
- Keep structured JSON logging, but add app/service metadata and profile-gated diagnostic logging. JHipster's logging aspect is dev-profile only; Zero Mail should avoid argument/result tracing by default because Gmail/OAuth data is sensitive.

## Do Not Copy Blindly

- Do not copy JHipster's WebFlux/Reactor shapes into this MVC + virtual-thread backend.
- Do not copy broad method argument/result logging. Even in development, Zero Mail handles email addresses, OAuth state, and eventually message metadata.
- Do not copy JHipster's permissive generated layering where web resources may access repositories. Multi-tenant privacy boundaries make a stricter service layer worth it here.
- Do not copy JHipster's alert-header conventions or client-app naming assumptions unless the frontend explicitly depends on them. Standard Problem Details plus OpenAPI-documented error codes are enough.

---

_Reviewed: 2026-04-25T15:29:25Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
