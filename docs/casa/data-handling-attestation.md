# Data-Handling Attestation (CASA / OWASP ASVS alignment)

| ASVS Category | Control | Where implemented |
|---------------|---------|-------------------|
| V2 Authentication | Google OAuth2 (`openid profile email`) + incremental scope upgrade to `gmail.modify` via separate `google-gmail` client registration | Phase 1, plan 05 |
| V3 Session Management | Spring Session Redis, `HttpOnly` + `SameSite=Lax` cookie (`ZEROMAIL_SESSION`), CSRF cookie (`XSRF-TOKEN`) + `X-XSRF-TOKEN` header for unsafe methods, server-side revocation | Phase 1, plan 05 |
| V4 Access Control | Multi-tenant `@TenantId` (Hibernate 7) + `ScopedValue<String> TenantContext.TENANT` (Java 25) bound after authentication; ArchUnit ban on `ThreadLocal` in tenant-scoped paths and on native SQL bypassing the JPA filter | Phase 1, plans 02, 04 |
| V6 Cryptography | AES-GCM-256 envelope with `tenantId` Additional Authenticated Data; key held in GCP Secret Manager (never in DB) | Phase 1, plan 06 |
| V7 Error Handling and Logging | `Sensitive<T>` wrapper + Logback `SensitiveMarkerScrubFilter` (TurboFilter) stamps `scrubbed=true` MDC on stray markers; ArchUnit rules forbid raw body/prompt/completion in log calls | Phase 1, plan 03 |
| V8 Data Protection | No raw email bodies stored; refresh tokens encrypted at rest; `DELETE /me/account` cascades through all tenant-owned tables; lazy `DISCONNECTED` on external revoke (AUTH-05) | Phase 1, plans 05, 06, 07 |
| V9 Communications | HTTPS-only (Cloud Run); `Secure` cookies in prod profile; HSTS via Cloud Run defaults | Deployment |
| V13 Configuration | OAuth client secrets and AES key from GCP Secret Manager via `spring-cloud-gcp-starter-secretmanager`; never committed | Phase 1, plan 01 |

## Concurrent multi-tenant safety evidence

`backend/api/src/test/java/com/zeromail/api/security/MultiTenantLeakIntegrationTest.java` — 100 concurrent virtual-thread requests across distinct tenants seeded via real Spring Session-minted cookies; asserts each request observes its own `tenantId` (no cross-tenant observation). Run with `./gradlew :backend:api:test --tests "*MultiTenantLeakIntegrationTest"`.

## Log-safety evidence (FND-03)

`backend/api/src/test/java/com/zeromail/api/LogScrubSyntheticTrafficTest.java` — drives **real authenticated request traffic** through `/me`, `/tenant/status`, and `/onboarding/select-template` against seeded sentinel values (`leak-probe-12345`, `LEAK-REFRESH-TOKEN-ABC`); the captured ROOT-logger stream contains zero occurrences of any sentinel. Additionally emits a synthetic `Sensitive(...)` log line and asserts the `SensitiveMarkerScrubFilter` actually fires (`scrubbed=true` MDC key on at least one event).

## External-revocation evidence (AUTH-05)

`backend/api/src/test/java/com/zeromail/api/security/DisconnectOnInvalidGrantTest.java` — publishing `OAuth2TokenRefreshFailed(tenantId, "invalid_grant", now)` flips `gmail_connections.status` to `DISCONNECTED` within a single transaction, with `disconnected_at` populated and a follow-on `GmailConnectionRevokedEvent` published for downstream UI reactions.

## Account-deletion evidence (AUTH-03)

`backend/api/src/test/java/com/zeromail/api/AccountDeletionE2ETest.java` — seeds tenant + user + Gmail connection + onboarding selection; calls the `AccountDeletionController` directly; asserts zero rows remain in all four tables.

## Onboarding-state-machine evidence (AUTH-06)

`backend/api/src/test/java/com/zeromail/api/OnboardingStateMachineTest.java` — drives `SIGNED_IN → TEMPLATE_SELECTED → COMPLETE` via the real controller. Backwards transitions throw via `UserEntity.advanceTo`'s forward-only guard.

## Refresh-token cipher evidence

- `backend/core/src/test/java/com/zeromail/core/crypto/RefreshTokenCipherTest.java`
- `backend/core/src/test/java/com/zeromail/core/crypto/NonceUniquenessTest.java`

## Spec contract evidence (FND-06)

- `backend/api/src/test/java/com/zeromail/api/OpenApiSchemaTest.java` — backend asserts `/v3/api-docs` returns OpenAPI 3.1 with `info.version=0.1.0` and exactly the Phase 1 paths.
- `scripts/verify-codegen.sh` — boots the backend, runs `pnpm generate:api` against the live spec, and asserts every Phase 1 path appears in the regenerated `apps/web/lib/api/schema.d.ts` (proves the typed-client round trip works end-to-end).

## Signed by

_TBD — populated by the engineer filing this attestation with the assigned CASA lab._
