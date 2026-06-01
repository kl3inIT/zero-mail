---
status: resolved
trigger: "After Google login, backend callback /login/oauth2/code/google shows Spring Boot Whitelabel 500."
created: 2026-04-26
updated: 2026-04-26
---

# Debug Session: OAuth Callback 500

## Symptoms

- Expected behavior: Google OAuth callback provisions the first-login tenant/user, then redirects to `/onboarding`.
- Actual behavior: Browser shows Spring Boot Whitelabel Error Page at `/login/oauth2/code/google`.
- Error messages: `assigned tenant id differs from current tenant id [... != 00000000-0000-0000-0000-000000000000] for entity com.zeromail.core.account.persistence.UserEntity.tenantId`.
- Timeline: Reported after frontend OAuth link was corrected to backend origin.
- Reproduction: Complete Google OAuth login and let Google redirect back to backend callback.

## Current Focus

- hypothesis: `OAuthProvisioningService` opens its `REQUIRES_NEW` transaction before binding `TenantContext`, so Hibernate captures the bootstrap tenant for the JPA session.
- test: Add an integration test that calls `findOrCreateGoogleUser(...)` for a first-login subject.
- expecting: Test fails before the fix with the same bootstrap tenant mismatch, and passes after binding `TenantContext` before opening the transaction.
- next_action: complete; fix verified.

## Evidence

- timestamp: 2026-04-26
  observation: User backend log shows `DataIntegrityViolationException: assigned tenant id differs from current tenant id [... != 00000000-0000-0000-0000-000000000000]`.
- timestamp: 2026-04-26
  observation: `OAuthProvisioningIntegrationTest.first_google_login_creates_tenant_and_user` reproduced the same exception.
- timestamp: 2026-04-26
  observation: `GmailAccessGuard` already documents the project invariant that `TenantContext` must be bound before opening a transaction/JPA session.
- timestamp: 2026-04-26
  observation: After switching provisioning to bind `TenantContext` first and run a `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW` inside the bound scope, the regression test passed.

## Eliminated

- hypothesis: Frontend route `/oauth2/authorization/google` is still 404.
  reason: OAuth entry URL redirects to Google; the new failure happens later on backend callback.
- hypothesis: Google callback path is blocked by Spring Security.
  reason: Callback reaches `GoogleOAuthSuccessHandler` and fails during tenant/user provisioning.

## Resolution

- root_cause: `@Transactional(REQUIRES_NEW)` on `createTenantAndUser(...)` opened the JPA transaction before `ScopedValue.where(TenantContext.TENANT, ...)` executed. Hibernate captured `ScopedValueTenantResolver.BOOTSTRAP_TENANT` for the session, then rejected the new `UserEntity` because its assigned tenant id differed from the current tenant id.
- fix: Replaced the proxied transactional method with a `TransactionTemplate` configured as `PROPAGATION_REQUIRES_NEW`, executed inside the bound `TenantContext` scope.
- verification: `:backend:api:test --tests com.zeromail.api.security.OAuthProvisioningIntegrationTest`, full `:backend:api:test`, `:backend:core:test`, and IDE build all passed.
- files_changed: backend/core/src/main/java/com/zeromail/core/account/service/OAuthProvisioningService.java, backend/api/src/test/java/com/zeromail/api/security/OAuthProvisioningIntegrationTest.java
