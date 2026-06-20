---
title: Admin Interface Freeze
phase: 08-admin-console-operator-tooling
plan: 8A
last_verified: 2026-05-20
---

# Admin Interface Freeze

This file freezes the admin auth and session integration surface used by Phase 8A.
It is the contract for `SecurityConfig`, `EnrollmentSessionController`, and the
`apps/admin` enrollment/login routes.

## Spring Security WebAuthn API

Verified with Context7 against `/websites/spring_io_spring-security_reference_7_0`
and against the local Spring Security 7.0.5 source jar on 2026-05-20.

`HttpSecurity.webAuthn(...)` exposes the relying-party DSL used by the admin
chain:

- `rpName("Zero Mail Admin")`
- `rpId("admin.zeromail.vn")`
- `allowedOrigins("https://admin.zeromail.vn")`
- `creationOptionsRepository(...)`
- `messageConverter(...)`

The admin chain must not configure `.oauth2Login(...)`. The user chain must not
configure `.webAuthn(...)`.

## Spring Security WebAuthn Endpoints

The stock WebAuthn endpoint surface used by Spring Security 7 is:

- `POST /webauthn/register/options`
- `POST /webauthn/register`
- `DELETE /webauthn/register/{id}` (denied by default unless explicitly authorized)
- `POST /webauthn/authenticate/options`
- `POST /login/webauthn`

The admin `securityMatcher(...)` list must include `/api/admin/**`,
`/webauthn/**`, the exact `/login/webauthn` authentication endpoint, and
`/login/webauthn/**`. The `/enroll` path is reserved for the admin SPA and must
not be consumed by a backend servlet filter.

The admin login route posts an email to `POST /webauthn/authenticate/options`.
Spring Security's stock options filter does not parse that request body; the
admin chain must bind the matching ACTIVE admin as request-scoped authentication
before the options filter runs so `allowCredentials` contains the stored passkey
credential for that email.

## WebAuthn Repository Contracts

Spring Security 7 exposes these WebAuthn management contracts:

- `PublicKeyCredentialUserEntityRepository`
  - `void delete(Bytes id)`
  - `PublicKeyCredentialUserEntity findById(Bytes id)`
  - `PublicKeyCredentialUserEntity findByUsername(String username)`
  - `void save(PublicKeyCredentialUserEntity userEntity)`
- `UserCredentialRepository`
  - `void delete(Bytes credentialId)`
  - `CredentialRecord findByCredentialId(Bytes credentialId)`
  - `List<CredentialRecord> findByUserId(Bytes userId)`
  - `void save(CredentialRecord credentialRecord)`

Implementations must persist only the WebAuthn credential bytes required for
authentication. Admin summary and grant APIs must never expose `credential_id`,
`public_key_cose`, or `user_handle`.

## Enrollment Routing

`/enroll` is SPA-only. Token validation is a REST call:

- `POST /api/admin/enrollment/session`
  - Request: `{ "token": "...", "email": "operator@example.com" }`
  - Success: `200 OK`, short-lived enrollment session cookie, and `expiresAt`
  - Expired or used token: `410 Gone`

The controller may store only the pending admin user id in server-side session
state. The token value must not be logged, stored in the database, or written to
SLF4J output.

## Spring Session API

Verified with Context7 against `/spring-projects/spring-session` on 2026-05-20.

`DefaultCookieSerializer` is the supported cookie customization API:

- `setCookieName(String)`
- `setCookiePath(String)`
- `setDomainName(String)`
- `setSameSite(String)`
- `setUseSecureCookie(boolean)`
- `setUseHttpOnlyCookie(boolean)`

`spring.session.redis.namespace` is the supported property for isolating Redis
session keys. Spring Session auto-configuration expects one effective
`CookieSerializer` for the servlet application context, so 8A uses a primary
route-aware serializer that delegates to two `DefaultCookieSerializer` instances
by request path. The cookie names are the load-bearing isolation primitive in
8A; a later hardening pass may split Redis repositories if Spring Session 4
adds a cleaner first-class multi-repository hook.

The default v1.2 target names are:

- Admin cookie: `ZEROMAIL_ADMIN`
- User cookie: `ZEROMAIL_SESSION`
- Admin Redis namespace: `spring:session:admin`
- User Redis namespace: `spring:session:user`

If local development cannot use `admin.zeromail.vn`, admin cookies must be
path-scoped to `/api/admin` and must still use `ZEROMAIL_ADMIN`.

## Springdoc GroupedOpenApi API

Verified with Context7 against `/springdoc/springdoc-openapi` on 2026-05-20.

The API split uses `GroupedOpenApi.builder()`:

- Public group: `.group("public").pathsToMatch("/api/**").pathsToExclude("/api/admin/**")`
- Admin group: `.group("admin").pathsToMatch("/api/admin/**")`

`apps/web` codegen consumes `/v3/api-docs/public`. `apps/admin` codegen consumes
`/v3/api-docs/admin`.
