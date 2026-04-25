---
phase: 01-foundation-safety-infrastructure
plan: 05
status: production-complete-tests-incomplete
---

# Plan 01-05 Summary — OAuth2, Spring Session, Tenant Binding, Disconnect Detection

## What was built

### Production code (10 files, all compile, all JetBrains-clean)

**Security package (`backend/api/.../security/`):**
- `SecurityConfig.java` — Spring Security 7 filter chain: OAuth2 login (Google), CSRF cookie token (HttpOnly false for double-submit), session management, `TenantBindingFilter` after `AuthorizationFilter`.
- `TenantBindingFilter.java` — `OncePerRequestFilter` that binds `TenantContext.TENANT` via `ScopedValue.where(...)` for the request lifetime, sourced from the authenticated `OidcUser`.
- `GoogleOAuthSuccessHandler.java` — first-login provisioning: creates `TenantEntity` + `UserEntity`, redirects to `/onboarding`.
- `GmailScopeRequestResolver.java` — appends `include_granted_scopes=true`, `prompt=consent`, `access_type=offline` for the `google-gmail` second-leg flow.
- `DisconnectDetectingRefreshTokenClient.java` — wraps Spring Security 7's `RestClientRefreshTokenTokenResponseClient` (the `Default*` class was removed in 7.x). On `invalid_grant` from Google, publishes `OAuth2TokenRefreshFailed` event then rethrows.
- `GmailAccessGuard.java` — `@EventListener @Transactional` listener: binds the event's tenant via `ScopedValue` (since events may fire from threads outside the request scope), looks up the connection, flips status to `DISCONNECTED`, sets `disconnected_at`, publishes `GmailConnectionRevokedEvent`.
- `events/OAuth2TokenRefreshFailed.java` — record (tenantId, errorCode, at).
- `events/GmailConnectionRevokedEvent.java` — record (tenantId, at).

**Application wiring:**
- `application.yml` — added dual OAuth2 client registrations (`google` for openid/profile/email, `google-gmail` for `gmail.modify`) plus the `google` provider URLs; redis session namespace.
- `Application.java` — added `@EntityScan(basePackages = "com.zeromail.core.persistence")` and `@EnableJpaRepositories(basePackages = "com.zeromail.core.persistence")` so the API's auto-configuration picks up the entities and repositories defined in the `core` module. Note: `@EntityScan` moved in Spring Boot 4 from `org.springframework.boot.autoconfigure.domain` to `org.springframework.boot.persistence.autoconfigure`.

### Cross-cutting fix

- `ScopedValueTenantResolver.java` — changed type parameter from `<String>` to `<UUID>` (matches the entity column type) and made it return a `BOOTSTRAP_TENANT` sentinel UUID when no tenant is bound. This unblocks Spring Data JPA's startup query validation, which preflights an EntityManager that consults the resolver. Real request paths still bind a real tenant via `TenantBindingFilter`; the sentinel only appears outside the request lifecycle and naturally isolates any stray operation against it.

### Tests written (5 files, all compile)

- `support/ApiPostgresTestBase.java` — singleton Postgres 17.6 testcontainer, dynamic datasource properties, in-memory `MapSessionRepository` (so Spring Session resolves cookies without redis), stubbed Google OAuth client credentials, AES-256 test key.
- `security/TestSessionSupport.java` — `@TestConfiguration` providing a `TestSessionMinter` bean. Builds a real `MapSession` with `OAuth2AuthenticationToken(DefaultOidcUser, …)` stored under `SPRING_SECURITY_CONTEXT`, returns `ZEROMAIL_SESSION=<id>` cookie string.
- `security/MultiTenantLeakIntegrationTest.java` — FND-05: seeds 100 tenants, fans out 100 concurrent requests via `StructuredTaskScope.<String>open()` (Java 25 final API — single type param), asserts each request observes its own tenant.
- `security/SessionCookieE2ETest.java` — single-tenant cookie roundtrip plus `Set-Cookie` HttpOnly + SameSite=Lax assertion.
- `security/DisconnectOnInvalidGrantTest.java` — publishes `OAuth2TokenRefreshFailed("…", "invalid_grant", now)`, asserts the connection row flips to `DISCONNECTED`.
- `debug/DebugController.java` — `@Profile("test")` endpoints `/debug/tenant-echo` and `/debug/fanout-echo` for the leak test.

## Tests status — NOT YET PASSING

`DisconnectOnInvalidGrantTest` ran successfully (context loads, test executes) but the assertion fails: expected `DISCONNECTED`, observed `CONNECTED`. The other two tests in the class hierarchy reuse the same context. Two root causes were resolved during execution:

1. **API module wasn't picking up core's `@Entity` / `Repository` beans.** Fixed by adding explicit `@EntityScan` + `@EnableJpaRepositories` on `Application`. Spring Boot's auto-configuration package only covers the package of the annotated class, not the `scanBasePackages`.
2. **`ScopedValueTenantResolver` threw on bootstrap because no tenant was bound during JPA query validation.** Fixed by returning the `BOOTSTRAP_TENANT` sentinel UUID. Resolver type parameter also corrected from `<String>` to `<UUID>` so Hibernate's `@TenantId` filter parameter binding does not fail with "Argument assigned to filter parameter 'tenantId' is not of type 'java.util.UUID'".

The remaining failure (`DisconnectOnInvalidGrantTest` not flipping status) appears to be an event-listener/transactional-visibility issue — either the listener is not firing on the test thread, or the listener's transaction is not committing the update before the test's reload. Diagnosis was deferred to a follow-up due to context budget.

## Outstanding work to close the plan

1. Diagnose why `GmailAccessGuard.on(OAuth2TokenRefreshFailed)` does not flip the status when invoked from a non-request context. Hypotheses: `@TransactionalEventListener` may be required (event published outside an active transaction), or the test's fresh `findByTenantId` re-uses a Hibernate session that pre-dates the listener's commit.
2. After (1) is fixed, run all three `com.zeromail.api.security.*` tests and the existing `com.zeromail.core.*` test suite to confirm the resolver type change to `<UUID>` does not regress the core tests.
3. The plan also calls for `GmailSecondLegCallbackTest`, `ConnectGmailCsrfContractTest`, and `SpringSecurity7CompatibilityTest`. These were not written in this iteration; they belong to the second OAuth leg work that overlaps with plan 01-07's controllers.

## Threats from PLAN.md

| ID | Mitigation status |
|----|-------------------|
| T-01 | Code-complete (`TenantBindingFilter` binds `ScopedValue`); test written but not yet green |
| T-04 | Code-complete (Spring Security state + PKCE; CSRF ignore narrowed to `/login/oauth2/code/**` and `/oauth2/callback/**`) |
| T-05 | Code-complete (Spring Session rotates session id on auth; HttpOnly + SameSite=Lax cookie) |
| T-07 | Code-complete (`DisconnectDetectingRefreshTokenClient` + `GmailAccessGuard`); test written but the assertion does not yet pass |
| T-06 | Code-complete (only registered redirect-uri accepted; success handler uses fixed `/onboarding`) |

## Acceptance criteria

| Criterion | Status |
|-----------|--------|
| `grep "google-gmail:" application.yml` | ✅ |
| `grep "gmail.modify" application.yml` | ✅ |
| `grep "addFilterAfter(tenantFilter, AuthorizationFilter.class)" SecurityConfig.java` | ✅ |
| `grep "ScopedValue.where(TenantContext.TENANT" TenantBindingFilter.java` | ✅ |
| `grep "include_granted_scopes" GmailScopeRequestResolver.java` | ✅ |
| `grep "CookieCsrfTokenRepository" SecurityConfig.java` | ✅ |
| `./gradlew :backend:api:compileJava` exits 0 | ✅ |
| `grep "invalid_grant" DisconnectDetectingRefreshTokenClient.java` | ✅ |
| `grep "GmailConnectionStatus.DISCONNECTED" GmailAccessGuard.java` | ✅ |
| `grep "GmailConnectionRevokedEvent" GmailAccessGuard.java` | ✅ |
| `grep -c "UUID tenant" GmailAccessGuard.java` returns 1 | ✅ |
| `./gradlew :backend:api:compileTestJava` exits 0 | ✅ |
| `:backend:api:test --tests "*MultiTenantLeakIntegrationTest"` exits 0 | ❌ blocked by listener-issue / context-cache |
| `:backend:api:test --tests "*DisconnectOnInvalidGrantTest"` exits 0 | ❌ assertion fails |
| `:backend:api:test --tests "*SessionCookieE2ETest"` exits 0 | ❌ blocked by context-cache |
