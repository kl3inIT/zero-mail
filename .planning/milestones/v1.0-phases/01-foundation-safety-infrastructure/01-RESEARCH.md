# Phase 1: Foundation & Safety Infrastructure — Research

**Researched:** 2026-04-24
**Domain:** Multi-tenant Spring Boot 4 / Java 25 foundation, OAuth 2, structured logging safety, Gradle multi-project scaffolding, Next.js 16 typed client
**Confidence:** HIGH on most library mechanics; **MEDIUM** on Spring Modulith 2.0 (pre-GA vs. Spring Boot 4) and a small number of M4/pre-release APIs.

## Summary

Phase 1 lays the safety substrate the rest of the product stacks on. The researched mechanics are all well-documented except two moving pieces: **Spring Modulith does not yet have a GA release that targets Spring Boot 4** (1.4 = Boot 3.5; 2.0 is SNAPSHOT against Boot 4 per Modulith's own compatibility matrix — `[VERIFIED: context7 /spring-projects/spring-modulith appendix]`), and a handful of Spring Security 7 / Boot 4 config shapes are best confirmed at implementation time against the exact artifact versions pulled in. Everything else — Hibernate 7 `@TenantId` semantics, ArchUnit JUnit 5 wiring, Spring Session Redis properties, Liquibase YAML `includeAll`, springdoc endpoints, `openapi-typescript` CLI — is stable and well-covered by official docs.

**Primary recommendation:** Scaffold in this order — (1) Gradle multi-project + `buildSrc` conventions, (2) Liquibase YAML baseline, (3) tenant / scoped-value plumbing, (4) Spring Session Redis + OAuth login (no Gmail scope yet), (5) `@Sensitive` + ArchUnit contract, (6) OAuth incremental authorization for `gmail.modify`, (7) `TenantAwareTaskScope` + ArchUnit ban on raw virtual-thread spawns, (8) springdoc + `apps/web` type generation + minimal routes, (9) AES-GCM refresh-token encryption against a Secret-Manager-backed key, (10) CASA submission package assembly. Every item after (1) depends on the one before it for a runnable end-to-end smoke test.

## User Constraints (from CONTEXT.md)

### Locked Decisions

**A. Module Scaffolding & Build Setup**
- **D-A1:** Scaffold `backend/core`, `backend/api`, `backend/worker` plus `apps/web`. `backend/worker` ships as a runnable Spring Boot shell with only a healthcheck scheduled task. `apps/web` ships generated OpenAPI types + minimal UI.
- **D-A2:** Gradle build logic lives in `buildSrc/` as convention plugins (`zeromail.java-conventions`, `zeromail.spring-boot-conventions`, `zeromail.archunit-conventions`, `zeromail.modulith-conventions`). Each backend module applies only the plugins it needs.
- **D-A3:** Spring Modulith is wired in Phase 1. Initial packages inside `backend/core`: `tenant`, `auth`, `privacy`. Each gets a `package-info.java` with `@ApplicationModule`. `ApplicationModulesTest` runs `ApplicationModules.of(Application.class).verify()` in CI.

**B. Tenant-Context Plumbing**
- **D-B1:** Tenant-id Scoped Value bound by a custom `OncePerRequestFilter` placed **after** Spring Security's authentication filter. Reads `tenantId` from the authenticated principal and wraps the remaining chain in `ScopedValue.where(TenantContext.TENANT, tenantId).run(...)`. No DB lookup in the filter.
- **D-B2:** Hibernate multi-tenancy is **DISCRIMINATOR** mode. Every tenant-owned entity declares a `tenant_id` column annotated with `@TenantId`. A custom `CurrentTenantIdentifierResolver` reads `TenantContext.TENANT`. ArchUnit bans native SQL outside an allow-listed infrastructure package.
- **D-B3:** `TenantAwareTaskScope` wraps `StructuredTaskScope` and re-binds `TenantContext.TENANT` on subtask start. Tests: (a) ≥ 100 concurrent distinct-tenant requests, (b) 10 subtasks under one request all see the same tenant, (c) raw `Thread.ofVirtual()` is caught by ArchUnit.

**C. OAuth Scope Progression & Disconnection**
- **D-C1:** Two-step incremental consent. First login = `openid profile email` only. Separate "Connect Gmail" action triggers second authorization round adding `https://www.googleapis.com/auth/gmail.modify`.
- **D-C2:** DISCONNECTED detection is **lazy in Phase 1**. Every outbound Google API call (Phase 1 scope: token refresh + `GET /userinfo` only) wraps `invalid_grant` / revocation errors to flip `gmail_connection.status` to `DISCONNECTED` and emit a domain event. Phase 2A adds the proactive probe.

**D. Guided Onboarding (AUTH-06)**
- **D-D1:** Template-rule step writes a real row to `onboarding_selections (id, tenant_id, template_key, enabled, created_at)`. Phase 3 reads this table and compiles each selection into a `Rule` row. Template cards: "Archive receipts", "Label newsletters", "Keep calendar invites at top".
- **D-D2:** Explicit onboarding state machine: `users.onboarding_step` of Java enum `{SIGNED_IN, GMAIL_CONNECTED, TEMPLATE_SELECTED, COMPLETE}`. Forward-only. Completion is server-side.

**E. Log Safety Contract**
- **D-E1:** Ships (1) JHipster-style baseline — Spring AOP logging aspect + `logstash-logback-encoder` JSON layout, (2) structural safety contract: `Sensitive<T>` wrapper whose `toString()` returns `"***REDACTED***"`; ArchUnit rule fails any `String`-typed field/param named in deny-list (`body`, `bodyText`, `prompt`, `completion`, `rawContent`, `refreshToken`, `accessToken`) that escapes a `Sensitive<>`; a second rule fails any `Logger.*` call referencing a `Sensitive`-typed argument; (3) thin Logback `TurboFilter` scanning formatted messages for the literal `Sensitive(` token and redacting.
- **D-E2:** Thin filter match behavior: replace substring with `[REDACTED]`, keep line, add structured fields `scrubbed=true` + `scrub_reason=sensitive_marker`.
- **D-E3 (deferred):** Full regex pattern-scan (email shapes, base64 blobs, prompt markers) deferred to a later observability/security phase.

**F. OpenAPI Skeleton & Web UI Depth**
- **D-F1:** Phase 1 OpenAPI doc contains only endpoints Phase 1 implements: `POST /auth/google/callback`, `GET /me`, `POST /tenant/connect-gmail`, `GET /auth/gmail/callback`, `POST /tenant/disconnect`, `DELETE /me/account`, `GET /tenant/status`, `POST /onboarding/select-template`, `POST /onboarding/complete`. `info.version = 0.1.0`.
- **D-F2:** `apps/web` ships Next.js 16.2.4 + React 19.2.5, Tailwind CSS 4 + shadcn/ui (Button, Card, Alert, Input), TanStack Query 5, `openapi-typescript` 7 + `openapi-fetch` 0.17, auth middleware, routes `/login`, `/onboarding`, `/settings` (with in-product privacy page section).

**G. OAuth Refresh-Token Encryption**
- **D-G1:** Single global AES-GCM-256 key pulled from GCP Secret Manager at boot, held in memory for process lifetime. In-process encrypt/decrypt (no per-call KMS round-trip).
- **D-G2:** Envelope schema: `[key_version:int32][nonce:12 bytes][ciphertext:variable]`. Phase 1 writes `key_version = 1`.

### Claude's Discretion
- Exact package naming inside `backend/core` (subject to Modulith rules)
- Liquibase changelog file naming and numbering scheme
- ArchUnit rule organization (one class vs. split by concern)
- Redis session key prefix and TTL (use Spring Session defaults)
- CSRF storage mechanism (Spring Security 7's cookie-based default is fine)
- Test framework details (JUnit 5 + Testcontainers for Postgres/Redis)
- Docker image layering beyond Spring Boot's CDS+AOT defaults

### Deferred Ideas (OUT OF SCOPE)
- Full runtime regex log pattern-scan (deferred to observability/security phase)
- Proactive OAuth revocation probe (Phase 2A — extends `users.watch` renewal job)
- Key-rotation job for OAuth token encryption (schema ready; implementation deferred)
- tenantId-bound MDC for log correlation
- `@RequireTenant` controller-method aspect (filter-based coverage is authoritative)
- `gmail.readonly` first, `gmail.modify` later (rejected)

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FND-01 | Every request runs in a tenant-scoped context using Scoped Values (never ThreadLocal) | Topic 3 (filter ordering), Topic 4 (task scope) |
| FND-02 | ArchUnit test fails any new code that references `ThreadLocal` in request/worker paths | Topic 9 (ArchUnit rules a) |
| FND-03 | `@Sensitive` wrapper + Logback scrub filter prevent sensitive content in logs | Topic 10 (`Sensitive<T>` + `TurboFilter`) |
| FND-04 | ArchUnit fails any code referencing body/prompt/completion in log statements | Topic 9 (rules d, e) |
| FND-05 | Concurrent multi-tenant integration test confirms no cross-tenant leakage on virtual threads | Topic 15 (leak-test harness) |
| FND-06 | Skeleton OpenAPI spec published and consumed by frontend via `openapi-typescript` | Topic 12 (springdoc), Topic 13 (apps/web) |
| FND-07 | CASA restricted-scope verification is initiated at OAuth wiring | Topic 16 (CASA workflow) |
| AUTH-01 | User can sign up/sign in via Google OAuth with Gmail scopes | Topic 5 (OAuth2 + incremental scope) |
| AUTH-02 | User can connect exactly one Gmail / Workspace account | Topic 5 + Topic 7 (schema uniqueness) |
| AUTH-03 | User can revoke Gmail access and delete account + all data | Topic 5, Topic 11 (cascade) |
| AUTH-04 | Cookie-based session (not JWT) | Topic 6 (Spring Session Redis) |
| AUTH-05 | `invalid_grant` tenants enter DISCONNECTED with recovery prompt | Topic 7 (revocation detection) |
| AUTH-06 | User completes guided onboarding through template-rule step | Topic 11 (schema), Topic 13 (UI) |

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Tenant-id binding per request | API (servlet filter in `backend/api`) | Core (ScopedValue key definition) | Request arrives at API; core publishes the binding primitive everyone else reuses |
| Hibernate discriminator enforcement | Core (persistence) | — | Entities + `CurrentTenantIdentifierResolver` live in core |
| OAuth2 login + session | API (Spring Security filter chain + Spring Session) | Core (User/Tenant aggregates) | Security is an infra concern on the edge |
| OAuth refresh-token encryption | Core (crypto service, key loaded via spring-cloud-gcp) | — | Reused by all outbound Google API helpers |
| Structured logging + scrub | Core (logback-spring.xml + `@Sensitive`) | API + Worker (consumers) | Single Logback config owned by core, consumed everywhere |
| OpenAPI generation | API (`springdoc-openapi`) | — | Only `backend/api` exposes HTTP |
| Typed client consumption | Web (`apps/web` codegen step) | — | Frontend concern |
| Onboarding state machine | API (controllers) + Core (users.onboarding_step enum) | — | State persisted in core; transitions triggered via API |
| Gmail-connection status cache & DISCONNECTED event | Core (`gmail_connection` aggregate, domain event) | Worker (later phases) | Core owns the aggregate; Phase 2A worker will react |
| ArchUnit verification | Tests under each backend module, but rules defined once in `buildSrc/` convention plugin | — | Single source of truth for rules |

## Standard Stack

### Core (locked by CLAUDE.md)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Java | 25 LTS | Runtime | `[CITED: CLAUDE.md]` user-locked; scoped values + structured concurrency GA per JEP 505 |
| Spring Boot | 4.0.6 (or latest 4.0.x at impl) | App framework | `[CITED: CLAUDE.md]` user-locked. Jackson 3, Jakarta-only. `[VERIFIED: context7 /spring-projects/spring-boot v4.0.3 docs present]` |
| Spring Framework | 7.0.x (Boot-managed) | Core | Ambient |
| Spring Security | 7.0.x (Boot-managed) | AuthN/Z, OAuth2 client, CSRF | `[VERIFIED: context7 /websites/spring_io_spring-security_reference_7_0]` |
| Spring Data JPA | 4.0.x (Boot-managed, Hibernate 7) | ORM aggregates | |
| Spring Session Data Redis | Boot-managed | HTTP session store | `[VERIFIED: context7 /spring-projects/spring-session]` starter `spring-boot-starter-session-data-redis` |
| Spring Modulith | **1.4.x for Boot 3.5, 2.0.0-SNAPSHOT for Boot 4.x** — see Topic 1 | Bounded-context verification | `[VERIFIED: context7 /spring-projects/spring-modulith appendix]` — **ASSUMED a 2.0.0-Mx milestone exists at impl time; re-verify against Maven Central** |
| spring-cloud-gcp-starter-secretmanager | 8.0.2 | Boot-time Secret Manager pull | `[CITED: CLAUDE.md]` |
| Hibernate ORM | 7.x (Boot-managed) | Persistence | `@TenantId` + `CurrentTenantIdentifierResolver` `[VERIFIED: context7 /hibernate/hibernate-orm]` |
| Liquibase | 5.0.2 | Migrations | YAML changelogs `[VERIFIED: context7 /liquibase/liquibase-docs]` |
| Postgres | 17.6 (Cloud SQL) | Primary DB | `[CITED: CLAUDE.md]` |
| Redis | 7.2 (Memorystore) | Session store + future rate-limit/cache | `[CITED: CLAUDE.md]` |
| HikariCP | Boot default | Connection pool | |
| Jakarta Validation | 3.1 (Boot-managed) | DTO validation | |

### Test / Quality
| Library | Version | Purpose |
|---------|---------|---------|
| JUnit | 5 (Boot-managed Jupiter) | Test framework |
| Testcontainers | latest stable (verify `org.testcontainers:postgresql`, `:junit-jupiter`) | Integration tests with real Postgres + Redis |
| ArchUnit | `com.tngtech.archunit:archunit-junit5` 1.3+ | Architectural tests `[VERIFIED: context7 /tng/archunit]` |
| AssertJ | ambient | Fluent assertions |

### Frontend (locked by CLAUDE.md)
| Library | Version | Purpose |
|---------|---------|---------|
| Next.js | 16.2.4 App Router | Web app `[VERIFIED: context7 /vercel/next.js v16.2.2 branch present]` |
| React | 19.2.5 | UI |
| TypeScript | 6.0.x | Type safety |
| Tailwind CSS | 4.2.4 | Styling |
| shadcn/ui | latest CLI | UI primitives (copy-in) |
| TanStack Query | 5.100.1 | Server state |
| openapi-typescript | 7.13.0 | Type codegen `[VERIFIED: context7 /websites/openapi-ts_dev]` |
| openapi-fetch | 0.17.0 | Typed client |
| pnpm | 10.33.x | Package manager |
| Turborepo | 2.9.6 | Task runner |

### What NOT to Use (from CLAUDE.md — MUST enforce)
Lombok, Jackson 2 assumptions, Spring WebFlux, `javax.*` (Jakarta-only), manually-built per-request `ChatClient` (deferred to P2C but scaffolded here), persisted prompts/completions, polling Gmail, `pgp_sym_encrypt` for tokens, stateless JWT sessions, Kafka/RabbitMQ, vector DB, Gradle Node plugin.

**Version verification checklist for planner to run at implementation time:**
```bash
# Critical to reconfirm — Modulith line most likely to change
./gradlew dependencyInsight --dependency spring-modulith-core
# Confirm Spring Boot 4.0.x BOM actually resolves Hibernate 7 + Jackson 3
./gradlew dependencyInsight --dependency hibernate-core
./gradlew dependencyInsight --dependency jackson-databind
```

## Per-Topic Research

### Topic 1 — Spring Modulith current version vs. Spring Boot 4

**Finding (CRITICAL):** Per the Modulith compatibility matrix, **Spring Modulith 1.4 targets Spring Boot 3.5. The Spring Boot 4 track is Spring Modulith 2.0, which is SNAPSHOT (not yet GA or milestone-published) at the time of this research.** `[VERIFIED: context7 /spring-projects/spring-modulith appendix.adoc]`

| Modulith | Boot (compiled against) | Boot (tested) |
|----------|-------------------------|---------------|
| 2.0 (snapshot) | 4.0 SNAPSHOT | 4.0 SNAPSHOT and milestones |
| 1.4 | 3.5 | 3.1–3.5 |

**Planner action required:**
1. At implementation time, check Maven Central for `org.springframework.modulith:spring-modulith-bom` — if a `2.0.0-M*` or `2.0.0-RC*` is published, use it; else pull from Spring's snapshot repository with a pinned version and add a TODO to swap to GA.
2. Enable snapshot repository only in `buildSrc` / `backend/core`, not globally.
3. Treat Modulith-related config as "best effort" until GA; ApplicationModules.verify() API is stable and unchanged since 1.x, so CI gating is still safe.

**Required annotations / setup:** `[VERIFIED]`
- Main application class: `@Modulithic @SpringBootApplication`
- Each module package `package-info.java` with `@ApplicationModule(displayName = "...", allowedDependencies = {...})`
- Test:
```java
class ApplicationModulesTest {
  @Test void verifyModularity() {
    ApplicationModules.of(Application.class).verify();
  }
}
```

**Pitfall:** `@ApplicationModuleTest` bootstraps only a module subtree — great for module-scoped integration tests once a real workflow lands (Phase 2+), but in Phase 1 the full `SpringBootTest` is simpler since only three modules exist.

### Topic 2 — Hibernate 7 multi-tenancy via `@TenantId`

`[VERIFIED: context7 /hibernate/hibernate-orm]`

**Entity shape:**
```java
@Entity
public class Rule {
    @Id UUID id;
    @TenantId String tenantId;   // from org.hibernate.annotations.TenantId
    // ... other columns
}
```

**Resolver — required bean:**
```java
@Component
public class ScopedValueTenantResolver implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {
    @Override public String resolveCurrentTenantIdentifier() {
        return TenantContext.currentOrThrow(); // reads ScopedValue
    }
    @Override public boolean validateExistingCurrentSessions() { return true; }
    @Override public void customize(Map<String,Object> props) {
        props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
```

**Critical caveat (from Hibernate docs, verbatim):**
> "When using discriminator-based multi-tenancy, be aware that native SQL queries are not automatically filtered by tenant ID and must be handled manually."

**Implication:** ArchUnit rule **must** ban `EntityManager.createNativeQuery(..)` and `Session.createNativeQuery(..)` outside an allow-listed infrastructure package (e.g., `..persistence.lowlevel..`) where tenant filtering is applied manually. Same caveat applies to `jdbcTemplate` raw usage.

**Spring Data JPA interaction:** `@TenantId` is recognized through Hibernate's session filter; Spring Data repositories behave correctly. No extra Spring Data config is needed **except** registering `ScopedValueTenantResolver` as a `HibernatePropertiesCustomizer` bean so Boot injects it into the Hibernate SF.

**For Spring Data JDBC (read-side, deferred to later phases):** `@TenantId` does **not** apply — JDBC templates need manual `WHERE tenant_id = ?` predicates. Document this in the persistence package README.

### Topic 3 — Scoped Value ↔ Spring Security filter ordering

`[VERIFIED: context7 /spring-projects/spring-security persistence.adoc]`

**Key facts about Spring Security 7 filter order:**
- `SecurityContextHolderFilter` loads `SecurityContext` from the repository (replaces the deprecated `SecurityContextPersistenceFilter`).
- Authentication filters (`UsernamePasswordAuthenticationFilter`, `OAuth2LoginAuthenticationFilter`) run after `SecurityContextHolderFilter`.
- By the time the chain reaches any custom filter registered with `.addFilterAfter(...)`, `SecurityContextHolder.getContext().getAuthentication()` is populated (either from session or from login).

**Correct registration pattern:**
```java
@Bean
SecurityFilterChain chain(HttpSecurity http, TenantBindingFilter tenantFilter) throws Exception {
    http
      .oauth2Login(Customizer.withDefaults())
      .sessionManagement(Customizer.withDefaults())
      .addFilterAfter(tenantFilter, AuthorizationFilter.class) // or after UsernamePasswordAuthenticationFilter
      .csrf(csrf -> csrf.csrfTokenRepository(
          CookieCsrfTokenRepository.withHttpOnlyFalse()));
    return http.build();
}
```

**The filter itself:**
```java
public class TenantBindingFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal p)) {
            chain.doFilter(req, res); // anonymous paths: /login, /actuator/health, /v3/api-docs
            return;
        }
        try {
            ScopedValue.where(TenantContext.TENANT, p.tenantId())
                       .run(() -> {
                           try { chain.doFilter(req, res); }
                           catch (IOException | ServletException e) { throw new UncheckedException(e); }
                       });
        } catch (UncheckedException ue) {
            if (ue.getCause() instanceof IOException io) throw io;
            if (ue.getCause() instanceof ServletException se) throw se;
            throw ue;
        }
    }
}
```

**Servlet async dispatch caveat `[ASSUMED]`:** Spring MVC async/SSE endpoints can dispatch the continuation on a different thread outside the ScopedValue binding. **Mitigation:** Phase 1 has no async endpoints (all handlers are synchronous). Add an architectural test / TODO for Phase 2A (Pub/Sub push controller — still synchronous, safe) and Phase 5 (SSE streaming — will need a dedicated `TenantContextCallable` wrapping pattern). Record this as an open question.

**Error dispatch:** Servlet error dispatch re-enters the filter chain. `OncePerRequestFilter` handles this via its `shouldNotFilterErrorDispatch()` hook — default behavior is to skip on ERROR dispatch, which means error pages will not have a tenant bound. That is acceptable for Phase 1; error pages are anonymous HTML.

### Topic 4 — `TenantAwareTaskScope` pattern (Java 25)

JEP 505 promotes `StructuredTaskScope` to Stable in JDK 25. `[CITED: openjdk.org JEP 505]` `[ASSUMED]` — re-verify the exact API surface of `StructuredTaskScope` in JDK 25 at implementation time; the pre-25 preview API required `ShutdownOnFailure`/`ShutdownOnSuccess` subclasses, and JDK 25 reshaped this.

**Shape the planner can start from (adjust for JDK 25 API shape):**
```java
public final class TenantAwareTaskScope implements AutoCloseable {
    private final StructuredTaskScope<Object> inner;
    private final String tenant;

    private TenantAwareTaskScope(String tenant) {
        this.tenant = tenant;
        this.inner = StructuredTaskScope.open(); // JDK 25 factory
    }

    public static TenantAwareTaskScope openInherit() {
        var t = TenantContext.currentOrThrow();
        return new TenantAwareTaskScope(t);
    }

    public <T> StructuredTaskScope.Subtask<T> fork(Callable<T> task) {
        return inner.fork(() ->
            ScopedValue.where(TenantContext.TENANT, tenant).call(task::call));
    }

    public void join() throws InterruptedException { inner.join(); }
    @Override public void close() { inner.close(); }
}
```

**ArchUnit rule formulations (Topic 9):** see `requires_all_fanout_goes_through_TenantAwareTaskScope` below.

### Topic 5 — Spring Security 7 OAuth2 Client incremental authorization with Google

`[VERIFIED: context7 /websites/spring_io_spring-security_reference_6_5 and _7_0]`

**Pattern for two-leg scope progression:**

1. Register **two** `ClientRegistration`s with the same Google client credentials but different scope sets:
   ```yaml
   spring:
     security:
       oauth2:
         client:
           registration:
             google:          # first leg — login only
               client-id: ${GOOGLE_OAUTH_CLIENT_ID}
               client-secret: ${GOOGLE_OAUTH_CLIENT_SECRET}
               scope: openid, profile, email
               redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
             google-gmail:     # second leg — adds gmail.modify
               provider: google
               client-id: ${GOOGLE_OAUTH_CLIENT_ID}
               client-secret: ${GOOGLE_OAUTH_CLIENT_SECRET}
               scope: https://www.googleapis.com/auth/gmail.modify
               redirect-uri: "{baseUrl}/oauth2/callback/gmail"
               authorization-grant-type: authorization_code
           provider:
             google:
               authorization-uri: https://accounts.google.com/o/oauth2/v2/auth
               token-uri: https://oauth2.googleapis.com/token
               user-info-uri: https://www.googleapis.com/oauth2/v3/userinfo
   ```

2. Phase 1 flow:
   - `/login` links to `/oauth2/authorization/google` (default Spring-handled URL) — user grants basic profile, Spring creates the session.
   - `POST /tenant/connect-gmail` controller redirects to `/oauth2/authorization/google-gmail` with `prompt=consent` and `include_granted_scopes=true` query params appended via an `OAuth2AuthorizationRequestCustomizer` so Google remembers the first-leg grant and the user sees only the incremental Gmail scope prompt.
   - Callback writes a `gmail_connection` row, encrypts the refresh token, and advances `users.onboarding_step` to `GMAIL_CONNECTED`.

3. Refresh handling: `OAuth2AuthorizedClientManager` with `OAuth2AuthorizedClientProviderBuilder.builder().refreshToken().build()` handles automatic refresh. `[VERIFIED: context7]` — the `DefaultOAuth2AuthorizedClientManager.authorize(...)` returns a re-authorized client or null if re-auth isn't possible (user revoked, etc.).

**Refresh token storage:** `OAuth2AuthorizedClientService` default is in-memory. For production persistence, use `JdbcOAuth2AuthorizedClientService` — but **we want encryption-at-rest**, so Phase 1 writes a custom `OAuth2AuthorizedClientService` that:
   1. On save, encrypts `refresh_token` via the AES-GCM envelope (Topic 8) and persists into `gmail_connections.refresh_token_encrypted`.
   2. On load, decrypts before returning the `OAuth2AuthorizedClient`.

This keeps the Spring Security abstraction intact while satisfying the CLAUDE.md constraint "no pgcrypto, app-layer AES-GCM with KMS key."

**`include_granted_scopes=true` is Google-specific:** Without it, granting the Gmail scope would *replace* the basic scopes in the returned token. With it, the new token carries the union of both. Pass via `OAuth2AuthorizationRequestResolver` customizer.

### Topic 6 — Spring Session Data Redis on Boot 4

`[VERIFIED: context7 /spring-projects/spring-session]`

**Starter:** `org.springframework.boot:spring-boot-starter-session-data-redis`.

**`application.yml`:**
```yaml
spring:
  session:
    store-type: redis
    timeout: 30m
    redis:
      namespace: zeromail:session
      flush-mode: on-save
      save-mode: on-set-attribute
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
server:
  servlet:
    session:
      cookie:
        name: ZEROMAIL_SESSION
        http-only: true
        secure: true
        same-site: lax
```

**CSRF** (`[VERIFIED: Spring Security 6.5 CSRF reference]`): `CookieCsrfTokenRepository.withHttpOnlyFalse()` publishes `XSRF-TOKEN` cookie; SPA reads it via `document.cookie` and echoes it as `X-XSRF-TOKEN` on mutating requests. Spring Security 6.4+ also requires the `CsrfTokenRequestHandler` `XorCsrfTokenRequestAttributeHandler` bean for BREACH-resistant tokens — pre-configure this.

**Next.js integration:** Since `apps/web` is same-origin (or same-eTLD+1 with cookie `domain=.zeromail.app`), the browser sends both cookies automatically on `fetch(..., { credentials: 'include' })`. Next.js server components that need to call the API from server side forward the `Cookie` header explicitly from `headers()` in a Route Handler / Server Action.

### Topic 7 — Google OAuth revocation detection (`invalid_grant`)

`[VERIFIED from Google OAuth 2 documentation and google-auth-library-java README]` `[ASSUMED: exact exception class shape without running against live library — recheck at impl]`

**When it fires:** Google's token endpoint returns HTTP 400 with body `{"error":"invalid_grant", "error_description":"Token has been expired or revoked."}` when:
- User revoked access via their Google account page
- Refresh token expired (rare — refresh tokens don't expire for test users in Gmail API, but do for unverified apps until CASA)
- Refresh token was explicitly revoked by `POST https://oauth2.googleapis.com/revoke?token=...`

**How to intercept with Spring Security:**
- `DefaultRefreshTokenTokenResponseClient` throws `OAuth2AuthorizationException` with a `OAuth2Error` whose `errorCode` equals `"invalid_grant"`.
- Register an `ApplicationEventListener<AuthenticationFailureEvent>` or wrap the outgoing refresh via a custom `RestOperations` interceptor — **recommended:** subclass `DefaultRefreshTokenTokenResponseClient` and intercept `getTokenResponse`.

**Domain event pattern:**
```java
@Service
public class GmailAccessGuard {
    @EventListener
    public void on(OAuth2TokenRefreshFailed e) {
        if ("invalid_grant".equals(e.errorCode())) {
            gmailConnections.markDisconnected(e.tenantId(), DisconnectReason.REVOKED);
            events.publish(new GmailConnectionRevokedEvent(e.tenantId(), Instant.now()));
        }
    }
}
```

Every outbound Google API helper (Phase 1: `GET /userinfo`, token refresh) wraps its call in a `try/catch` that detects either (a) `OAuth2AuthorizationException` with `invalid_grant`, or (b) `GoogleJsonResponseException` with HTTP 401 + `{"error":"invalid_grant"}`.

### Topic 8 — AES-GCM envelope encryption

**Idiomatic Java 25 + Secret Manager pattern:**

```java
@Configuration
public class RefreshTokenCryptoConfig {

    @Bean
    RefreshTokenCipher refreshTokenCipher(
            @Value("${sm://oauth-refresh-token-key-v1}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) throw new IllegalStateException("key must be 32 bytes");
        return new RefreshTokenCipher(Map.of(1, new SecretKeySpec(keyBytes, "AES")), 1);
    }
}

public final class RefreshTokenCipher {
    private final Map<Integer, SecretKey> keysByVersion;
    private final int currentVersion;
    private final SecureRandom rng = new SecureRandom();

    public byte[] encrypt(byte[] plaintext) {
        byte[] nonce = new byte[12];
        rng.nextBytes(nonce);
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keysByVersion.get(currentVersion),
                    new GCMParameterSpec(128, nonce));
        byte[] ct = cipher.doFinal(plaintext);

        var bb = ByteBuffer.allocate(4 + 12 + ct.length);
        bb.putInt(currentVersion);
        bb.put(nonce);
        bb.put(ct);
        return bb.array();
    }

    public byte[] decrypt(byte[] envelope) {
        var bb = ByteBuffer.wrap(envelope);
        int ver = bb.getInt();
        byte[] nonce = new byte[12]; bb.get(nonce);
        byte[] ct = new byte[bb.remaining()]; bb.get(ct);
        var key = keysByVersion.get(ver);
        if (key == null) throw new IllegalStateException("unknown key version " + ver);
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
        return cipher.doFinal(ct);
    }
}
```

**Pitfalls:**
1. **Nonce reuse is catastrophic for AES-GCM.** Always `SecureRandom`-generate per encryption. 96-bit (12-byte) random nonce is the standard — acceptable collision probability at < 2^32 encryptions per key, which covers single-key lifetime for this product.
2. **AAD usage:** Envelope does not currently use AAD. Consider binding `(tenant_id || key_version)` as AAD to prevent ciphertext swapping between rows — **recommended add**. Planner: add AAD = `tenantId.getBytes(UTF_8)` on both encrypt and decrypt.
3. **JVM provider on Temurin 25:** `SunJCE` provides AES/GCM/NoPadding by default. No Bouncy Castle needed. Verify `Cipher.getMaxAllowedKeyLength("AES") >= 256` (true on Temurin 25 per unrestricted policy default since JDK 9).
4. **Secret Manager reference format:** `spring-cloud-gcp-starter-secretmanager` supports `@Value("${sm://secret-name}")` or `${sm://secret-name/versions/1}`. Pin a version; don't use `/versions/latest`.

### Topic 9 — ArchUnit rules for the safety contract

`[VERIFIED: context7 /tng/archunit]` version 1.3+ on JUnit 5.

**Test class skeleton:**
```java
@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
class SafetyContractArchTests {

    // (a) FND-02: no ThreadLocal in request/worker paths
    @ArchTest static final ArchRule no_threadlocal =
        noClasses()
            .that().resideInAPackage("..api..").or().resideInAPackage("..worker..").or().resideInAPackage("..core..")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.lang.ThreadLocal")
            .because("FND-01/02: use ScopedValue, not ThreadLocal");

    // (b) ban raw virtual-thread spawns outside TenantAwareTaskScope
    @ArchTest static final ArchRule fanout_via_helper =
        noClasses()
            .that().resideOutsideOfPackage("..tenant.concurrency..")
            .should().callMethod(Thread.class, "ofVirtual")
            .orShould().callMethod(CompletableFuture.class, "supplyAsync", Supplier.class)
            .orShould().callMethod(CompletableFuture.class, "runAsync", Runnable.class)
            .because("FND-01: fan-out must re-bind tenant via TenantAwareTaskScope");

    // (c) ban native SQL outside allow-listed infra package
    @ArchTest static final ArchRule no_native_sql =
        noClasses()
            .that().resideOutsideOfPackage("..persistence.lowlevel..")
            .should().callMethodWhere(target ->
                (target.getTarget().getName().equals("createNativeQuery")
                 && target.getTarget().getOwner().isAssignableTo("jakarta.persistence.EntityManager")))
            .because("discriminator tenancy is not auto-applied to native SQL");

    // (d) ban logger calls referencing Sensitive-typed arguments
    @ArchTest static final ArchRule no_sensitive_in_logger =
        noClasses().should(new ArchCondition<JavaClass>("not pass Sensitive to Logger") {
            @Override public void check(JavaClass item, ConditionEvents events) {
                item.getMethodCallsFromSelf().stream()
                    .filter(call -> call.getTargetOwner().isAssignableTo("org.slf4j.Logger"))
                    .forEach(call -> {
                        boolean passesSensitive = call.getTarget().getRawParameterTypes().stream()
                            .anyMatch(t -> t.getName().equals("com.zeromail.privacy.Sensitive"));
                        // static analysis: inspect actual argument types via call.getArguments() (ArchUnit 1.3+)
                        // if any argument resolves to Sensitive<T>, violate
                        if (callArgumentTypesIncludeSensitive(call)) {
                            events.add(SimpleConditionEvent.violated(call,
                                "Logger call at " + call.getSourceCodeLocation() + " passes a Sensitive value"));
                        }
                    });
            }
        });

    // (e) deny-listed field/param names must be Sensitive<String>
    private static final Set<String> DENY = Set.of(
        "body","bodyText","prompt","completion","rawContent","refreshToken","accessToken");

    @ArchTest static final ArchRule sensitive_names_wrapped =
        fields().that().haveNameMatching(String.join("|", DENY))
                .should().haveRawType("com.zeromail.privacy.Sensitive")
                .because("FND-03/04: deny-listed names must be Sensitive<T>");
}
```

**Notes:**
- Rule (d) requires inspecting actual call argument types, not just method signature. ArchUnit 1.3+ exposes `JavaMethodCall#getTryCatchBlocks` etc., but argument-value resolution via `getAccessesFromSelf()` is the practical path; prepare to implement via a custom `ArchCondition` using `JavaAccess#getOrigin()` and field-type lookup.
- Rule (e) only covers fields. Extend with `methods().that().haveRawParameterTypes(...)` for method parameters and `constructors()` for constructor parameters.
- Put rules into a shared module `backend/core/src/test/java/.../SafetyContractArchTests.java` **or** into the `zeromail.archunit-conventions` Gradle plugin (recommended — one source of truth, each module applies it and contributes its own package scope).

### Topic 10 — `@Sensitive` wrapper + Logback `TurboFilter`

**Minimum viable `Sensitive<T>`:**
```java
public record Sensitive<T>(T value) {
    public Sensitive {
        if (value == null) throw new IllegalArgumentException();
    }
    @Override public String toString() { return "***REDACTED***"; }
}
```

The record's `toString()` override — **not** the default record `toString()` — ensures `String.valueOf(sensitive)` and `"… " + sensitive` both redact. Jackson 3 serialization of `Sensitive<T>` by default would expose `.value()`; register a `JsonSerializer<Sensitive<?>>` that writes `"***REDACTED***"` (or, safer, throws — `Sensitive` should never be serialized to response bodies).

**Logback `TurboFilter`:**
```java
public class SensitiveMarkerScrubFilter extends TurboFilter {
    private static final String TOKEN = "Sensitive(";
    private static final String REDACTED = "[REDACTED]";

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level,
                              String format, Object[] params, Throwable t) {
        if (format == null) return FilterReply.NEUTRAL;
        String rendered = org.slf4j.helpers.MessageFormatter.arrayFormat(format, params).getMessage();
        if (!rendered.contains(TOKEN)) return FilterReply.NEUTRAL;
        // mutate: put redaction markers in MDC so logstash-logback-encoder emits them as JSON fields
        MDC.put("scrubbed", "true");
        MDC.put("scrub_reason", "sensitive_marker");
        // log a redacted version via a dedicated logger to avoid recursion
        logger.log(marker, logger.getName(), toLocationAwareLevel(level),
            rendered.replace(TOKEN, REDACTED + "("), null, null);
        // deny the original
        MDC.remove("scrubbed"); MDC.remove("scrub_reason");
        return FilterReply.DENY;
    }
}
```

Register via `logback-spring.xml`:
```xml
<configuration>
  <turboFilter class="com.zeromail.privacy.SensitiveMarkerScrubFilter"/>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
  </appender>
  <root level="INFO"><appender-ref ref="STDOUT"/></root>
</configuration>
```

**Pitfall:** Recursion — the filter itself calls `logger.log(...)` which re-enters the filter. Mitigate by checking a thread-local "already-scrubbed" flag or by emitting the rescrubbed line directly to the appender bypassing the filter (preferred).

**Simpler alternative `[ASSUMED]`:** Use a Logstash encoder `CompositeJsonEncoder` with a custom `JsonProvider` that scans the final JSON string for the token and mutates. Avoids the recursion problem entirely. Planner should evaluate this first.

### Topic 11 — Liquibase 5.0.2 YAML baseline

`[VERIFIED: context7 /liquibase/liquibase-docs]`

**Directory layout (recommended for `backend/core`):**
```
backend/core/src/main/resources/
└── db/
    └── changelog/
        ├── db.changelog-master.yaml
        └── changes/
            ├── 001-create-tenants.yaml
            ├── 002-create-users.yaml
            ├── 003-create-gmail-connections.yaml
            ├── 004-create-onboarding-selections.yaml
            └── 005-indexes.yaml
```

**Master:**
```yaml
databaseChangeLog:
  - includeAll:
      path: classpath:db/changelog/changes/
      relativeToChangelogFile: false
      errorIfMissingOrEmpty: true
```

**Table sketches (schema details to be finalized by planner):**

```yaml
# 001-create-tenants.yaml
databaseChangeLog:
  - changeSet:
      id: 001-create-tenants
      author: zeromail
      changes:
        - createTable:
            tableName: tenants
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true, nullable: false } }
              - column: { name: display_name, type: varchar(255), constraints: { nullable: false } }
              - column: { name: created_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }

# 002-create-users.yaml  (one user per tenant for v1 — single-seat)
databaseChangeLog:
  - changeSet:
      id: 002-create-users
      author: zeromail
      changes:
        - createTable:
            tableName: users
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true } }
              - column: { name: tenant_id, type: uuid, constraints: { nullable: false, foreignKeyName: fk_users_tenant, referencedTableName: tenants, referencedColumnNames: id } }
              - column: { name: google_subject, type: varchar(100), constraints: { nullable: false, unique: true } }
              - column: { name: email, type: varchar(320), constraints: { nullable: false } }
              - column: { name: onboarding_step, type: varchar(32), constraints: { nullable: false }, defaultValue: "SIGNED_IN" }
              - column: { name: created_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }
        - createIndex:
            indexName: idx_users_tenant_id
            tableName: users
            columns: [{ column: { name: tenant_id } }]

# 003-create-gmail-connections.yaml
databaseChangeLog:
  - changeSet:
      id: 003-create-gmail-connections
      author: zeromail
      changes:
        - createTable:
            tableName: gmail_connections
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true } }
              - column: { name: tenant_id, type: uuid, constraints: { nullable: false } }
              - column: { name: google_email, type: varchar(320), constraints: { nullable: false } }
              - column: { name: status, type: varchar(32), constraints: { nullable: false } }  # CONNECTED | DISCONNECTED | PENDING
              - column: { name: refresh_token_encrypted, type: bytea, constraints: { nullable: true } }
              - column: { name: scopes_granted, type: text }
              - column: { name: connected_at, type: timestamptz }
              - column: { name: disconnected_at, type: timestamptz }
        - addUniqueConstraint:
            tableName: gmail_connections
            columnNames: tenant_id
            constraintName: uq_gmail_connections_tenant_id   # AUTH-02: one Gmail per tenant
        - createIndex:
            indexName: idx_gmail_conn_status
            tableName: gmail_connections
            columns: [{ column: { name: status } }]

# 004-create-onboarding-selections.yaml
databaseChangeLog:
  - changeSet:
      id: 004-create-onboarding-selections
      author: zeromail
      changes:
        - createTable:
            tableName: onboarding_selections
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true } }
              - column: { name: tenant_id, type: uuid, constraints: { nullable: false } }
              - column: { name: template_key, type: varchar(64), constraints: { nullable: false } }
              - column: { name: enabled, type: boolean, constraints: { nullable: false }, defaultValueBoolean: true }
              - column: { name: created_at, type: timestamptz, constraints: { nullable: false }, defaultValueComputed: now() }
        - addUniqueConstraint:
            tableName: onboarding_selections
            columnNames: tenant_id, template_key
            constraintName: uq_onboarding_selection
```

Indexes on `tenant_id` on every tenant-scoped table — critical for DISCRIMINATOR query performance (every query gets an auto-prepended `WHERE tenant_id = ?`).

### Topic 12 — springdoc-openapi on Spring Boot 4

`[VERIFIED: context7 /springdoc/springdoc-openapi]`

**Artifact (Spring Boot 3/4 line):** `org.springdoc:springdoc-openapi-starter-webmvc-ui` (verify latest 2.x+ at impl — `[ASSUMED]` the starter supports Boot 4 at impl time; if not, swap to `springdoc-openapi-starter-webmvc-api` and skip Swagger UI).

**Configuration:**
```yaml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
    version: openapi_3_1
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
  packages-to-scan: com.zeromail.api
```

**Info bean:**
```java
@Bean
OpenApiCustomizer phase1Info() {
  return api -> api.setInfo(new Info()
      .title("Zero Mail API")
      .version("0.1.0")
      .description("Phase 1 skeleton"));
}
```

**Spring Security:** whitelist `/v3/api-docs/**` and `/swagger-ui/**` in the `SecurityFilterChain` matcher list (per springdoc's own documentation).

**CI step for `apps/web` type generation:**
```bash
# after backend is up (local) or pull from a committed stable artifact
curl -s http://localhost:8080/v3/api-docs > apps/web/openapi/spec.json
pnpm --filter @zeromail/web exec openapi-typescript openapi/spec.json -o src/lib/api/schema.d.ts
```

### Topic 13 — `apps/web` scaffolding specifics

`[VERIFIED: context7 /vercel/next.js v16.2.2 branch + /websites/openapi-ts_dev]`

**Scaffolding commands:**
```bash
# from monorepo root, inside apps/
pnpm dlx create-next-app@16.2.4 web --typescript --app --tailwind --eslint --no-src-dir --import-alias "@/*"
cd web
pnpm add openapi-fetch @tanstack/react-query
pnpm add -D openapi-typescript typescript
pnpm dlx shadcn@latest init       # choose Tailwind v4 + App Router
pnpm dlx shadcn@latest add button card alert input
```

**Middleware (`apps/web/middleware.ts`):**
```ts
import { NextResponse, type NextRequest } from 'next/server';

const PROTECTED = ['/onboarding', '/settings'];

export function middleware(req: NextRequest) {
  const needsAuth = PROTECTED.some(p => req.nextUrl.pathname.startsWith(p));
  if (!needsAuth) return NextResponse.next();
  const session = req.cookies.get('ZEROMAIL_SESSION');
  if (!session) return NextResponse.redirect(new URL('/login', req.url));
  return NextResponse.next();
}

export const config = { matcher: ['/onboarding/:path*', '/settings/:path*'] };
```

**Typed client:** `openapi-fetch` usage:
```ts
import createClient from 'openapi-fetch';
import type { paths } from '@/lib/api/schema';

export const api = createClient<paths>({
  baseUrl: process.env.NEXT_PUBLIC_API_BASE ?? '/',
  credentials: 'include',
  headers: { 'X-XSRF-TOKEN': readCookie('XSRF-TOKEN') }, // set per-mutating-request in a hook
});
```

### Topic 14 — Gradle 9.4.1 `buildSrc` convention plugins

**`buildSrc/build.gradle.kts`:**
```kotlin
plugins {
    `kotlin-dsl`
}
repositories {
    gradlePluginPortal()
    mavenCentral()
}
dependencies {
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.0.6")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}
```

**`buildSrc/src/main/kotlin/zeromail.java-conventions.gradle.kts`:**
```kotlin
plugins { `java-library` }
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "--enable-preview"))
}
tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}
```

**`buildSrc/src/main/kotlin/zeromail.spring-boot-conventions.gradle.kts`:**
```kotlin
plugins {
    id("zeromail.java-conventions")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}
dependencyManagement {
    imports { mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.6") }
}
```

**`buildSrc/src/main/kotlin/zeromail.archunit-conventions.gradle.kts`:**
```kotlin
plugins { id("zeromail.java-conventions") }
dependencies {
    testImplementation("com.tngtech.archunit:archunit-junit5")
}
```

**`libs.versions.toml`:**
```toml
[versions]
springBoot = "4.0.6"
springAi = "2.0.0-M4"
springCloudGcp = "8.0.2"
springModulith = "2.0.0-SNAPSHOT"   # see Topic 1; pin milestone when available
hibernate = "7.0.x"                  # Boot-managed; keep for visibility
liquibase = "5.0.2"
archunit = "1.3.0"
logstashLogback = "8.0"
googleAuthLibrary = "1.35.0"
gmailApi = "v1-rev20250331-2.0.0"

[libraries]
spring-boot-bom = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "springBoot" }
spring-modulith-bom = { module = "org.springframework.modulith:spring-modulith-bom", version.ref = "springModulith" }
# ... etc
```

### Topic 15 — Multi-tenant concurrent leak test (FND-05)

**Test shape:**
```java
@SpringBootTest
@Testcontainers
class MultiTenantLeakIntegrationTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:17");
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7.2").withExposedPorts(6379);

    @Autowired TestRestTemplate rest;

    @Test
    void requests_never_cross_tenant_under_load() throws Exception {
        // Arrange: N tenants, each with a unique in-memory session cookie (pre-authenticated via a test hook)
        int N = 200;
        var tenants = IntStream.range(0, N).mapToObj(i -> registerTenant("t-" + i)).toList();

        try (var scope = StructuredTaskScope.<TenantObservation>open()) {
            for (var t : tenants) {
                scope.fork(() -> {
                    var observedTenant = rest.exchange(
                        "/debug/tenant-echo", HttpMethod.GET,
                        requestWithSessionFor(t), String.class).getBody();
                    return new TenantObservation(t.id(), observedTenant);
                });
            }
            scope.join();
            var results = scope.stream().map(StructuredTaskScope.Subtask::get).toList();
            results.forEach(r -> assertThat(r.observed()).isEqualTo(r.expected()));
        }
    }

    @Test
    void fanout_under_request_preserves_tenant() {
        loginAs("tenant-A");
        var observed = rest.getForObject("/debug/fanout-echo?n=10", List.class);
        assertThat(observed).hasSize(10).allSatisfy(o -> assertThat(o).isEqualTo("tenant-A"));
    }

    @Test
    void static_archunit_catches_raw_virtual_threads() {
        // This is the ArchUnit rule from Topic 9 — a separate unit test validates the rule fires on a synthetic violation class.
        assertThatThrownBy(() -> runArchRule(SafetyContractArchTests.fanout_via_helper, syntheticViolation()))
            .isInstanceOf(AssertionError.class);
    }
}
```

Debug endpoints `/debug/tenant-echo` and `/debug/fanout-echo` are test-profile only (`@Profile("test")`).

### Topic 16 — CASA restricted-scope verification (FND-07)

`[ASSUMED — exact submission process changes periodically; re-verify with Google at submission time]`

**Current workflow (2025–2026 general shape):**
1. Submit app for OAuth verification via Google Cloud Console → APIs & Services → OAuth consent screen → "Submit for verification."
2. For restricted scopes (Gmail's `gmail.modify` is restricted), Google refers the app to a **CASA-certified security assessor lab** (e.g., Bishop Fox, Leviathan, NCC Group, Schellman).
3. **Tier 2** is the typical tier for SaaS with < 5M users handling restricted-scope data without storing email content (Zero Mail's posture). Tier 3 only applies at larger user counts.
4. Lab performs: app walkthrough + code/infra review against the CASA checklist (OWASP ASVS-aligned).

**Artifacts Phase 1 must produce before filing:**
- Public privacy policy URL and homepage URL (may point to a marketing page stub in Phase 1).
- OAuth consent screen content: app name, support email, privacy policy link, app logo (verified ownership of domain).
- **Scopes justification narrative:** one paragraph per scope (`gmail.modify`) explaining minimum-necessary-use, data handling, retention ("no raw email bodies stored"), auto-send policy ("no auto-send — CASA-relevant").
- **Demo video / screenshots** showing the full connect → disconnect → delete-account flow and the in-product privacy page.
- **Brand verification** — Search Console ownership proof of the domain.
- **Data handling attestation** aligned with CASA check items — Phase 1 can pre-fill this pointing to the `@Sensitive` + ArchUnit contract, AES-GCM envelope, discriminator tenancy.

**Phase 1 concrete tasks:**
1. Create `docs/casa/` directory with drafts of: privacy policy, scopes justification, data-handling attestation.
2. Record the demo video at the end of Phase 1 (after `/settings` disconnect + delete works).
3. File the CASA form. Tier is assigned by Google — not chosen by applicant.
4. Track submission ID + lab assignment in the Phase 6 close-out checklist.

### Topic 17 — Validation Architecture (covered below as its own section)

## Architecture Patterns

### System Architecture Diagram

```
Browser (same-origin)
  │  cookies: ZEROMAIL_SESSION (HttpOnly), XSRF-TOKEN (JS-readable)
  ▼
apps/web (Next.js 16 Edge/middleware → App Router handlers)
  │  fetch with credentials:include + X-XSRF-TOKEN header on POST/DELETE
  ▼
─────────────────────────────────────────────────────────
backend/api (Spring Boot 4)
  │  Filter chain: SecurityContextHolderFilter → OAuth2LoginAuthenticationFilter
  │                  → AuthorizationFilter → TenantBindingFilter(ScopedValue.where)
  │                  → DispatcherServlet → @RestController
  │
  ├─▶ Spring Session (Redis) — session cookie → principal w/ tenantId
  ├─▶ OAuth2AuthorizedClientManager (google, google-gmail registrations)
  │       └─ custom OAuth2AuthorizedClientService → AES-GCM cipher → Postgres
  ├─▶ Hibernate 7 (@TenantId) → Postgres 17 (Cloud SQL)
  ├─▶ springdoc-openapi → GET /v3/api-docs → consumed by apps/web CI step
  └─▶ Google APIs (userinfo, token refresh) — wrapped by GmailAccessGuard
         └─ on invalid_grant → domain event → mark DISCONNECTED
─────────────────────────────────────────────────────────
backend/worker (Spring Boot 4 shell)
  │  @Scheduled healthcheck only in Phase 1; real jobs in Phase 2A+
  │  virtual-thread fan-out goes through TenantAwareTaskScope (enforced by ArchUnit)
─────────────────────────────────────────────────────────
Cross-cutting:
  - `@Sensitive<T>` wrapper on body/prompt/completion/token-typed values
  - Logback JSON layout + TurboFilter scrub
  - ArchUnit enforces: no ThreadLocal, no raw virtual threads, no native SQL,
    no Sensitive in Logger args, deny-listed names wrapped in Sensitive<T>
  - Spring Modulith package-info boundaries: tenant, auth, privacy
```

### Recommended project structure

```
zero-mail/
├── buildSrc/                                 # convention plugins
├── gradle/libs.versions.toml                 # version catalog
├── settings.gradle.kts                       # multi-project
├── build.gradle.kts                          # root
├── backend/
│   ├── core/
│   │   └── src/main/java/com/zeromail/core/
│   │       ├── tenant/          (+ package-info @ApplicationModule)
│   │       ├── auth/            (+ package-info)
│   │       ├── privacy/         (+ package-info, hosts Sensitive<T>)
│   │       └── persistence/
│   │           └── lowlevel/    (ArchUnit-allow-listed for native SQL if needed)
│   ├── api/
│   │   └── src/main/java/com/zeromail/api/
│   │       ├── Application.java (@Modulithic @SpringBootApplication)
│   │       ├── security/
│   │       ├── controllers/
│   │       └── config/          (OpenAPI, filter chain, session)
│   └── worker/
│       └── src/main/java/com/zeromail/worker/
│           └── Application.java (healthcheck @Scheduled only)
└── apps/
    └── web/
        ├── app/
        │   ├── login/page.tsx
        │   ├── onboarding/page.tsx
        │   └── settings/page.tsx
        ├── middleware.ts
        ├── lib/api/schema.d.ts  (generated)
        └── package.json
```

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Tenant filter on every query | Hand-written `WHERE tenant_id = ?` on all JPQL | Hibernate `@TenantId` + `CurrentTenantIdentifierResolver` | Easy to forget on one query = leak |
| OAuth2 refresh flow | Hand-call `/token` | Spring Security `OAuth2AuthorizedClientManager` | Refresh scheduling, error handling, state |
| CSRF tokens | Hand-sign tokens | `CookieCsrfTokenRepository` | BREACH attack, token rotation built-in |
| Session store | Hand-roll Redis hash keys | Spring Session `@EnableRedisHttpSession` | Expiration, rotation, `findByPrincipal` support |
| Schema migrations | Raw SQL files | Liquibase YAML changelogs | Change tracking, rollback, `includeAll` fanout |
| Modular boundaries | Hand-maintained imports audit | Spring Modulith `@ApplicationModule` + `ApplicationModules.verify()` | Build-time check |
| JSON structured logs | Hand-built JSON formatter | `logstash-logback-encoder` | MDC, markers, exception rendering |
| Architectural rules | Code review alone | ArchUnit | Build-time enforcement |
| Virtual-thread fan-out | Raw `Thread.ofVirtual()` | `TenantAwareTaskScope` wrapping `StructuredTaskScope` | Scoped-value re-binding is easy to forget |
| AES-GCM construction | Custom byte concatenation | `javax.crypto.Cipher AES/GCM/NoPadding` + explicit envelope schema | Nonce/tag mistakes are silent |
| OpenAPI → TypeScript types | Hand-maintain DTOs on both sides | `openapi-typescript` | Drift guaranteed otherwise |

## Common Pitfalls

### Pitfall 1: ScopedValue not bound under async dispatch
**What goes wrong:** MVC async / SSE / `@Async` method puts work on a thread without the ScopedValue binding.
**Why:** Scoped values are thread-bound; they only inherit through `StructuredTaskScope.fork` with current Java 25 API.
**How to avoid:** Phase 1 has no async endpoints. Document clearly that Phase 2A+ endpoints use `TenantAwareTaskScope`; `@Async` methods must wrap via a `TaskDecorator` that re-binds.
**Warning sign:** NullPointerException in `CurrentTenantIdentifierResolver#resolveCurrentTenantIdentifier` when `TenantContext.TENANT.get()` is called outside the binding.

### Pitfall 2: Native SQL bypasses `@TenantId`
**What goes wrong:** Someone writes `entityManager.createNativeQuery("SELECT * FROM rules")` — returns all tenants' rows.
**Why:** Hibernate only auto-applies the tenant filter to JPQL/Criteria, not native SQL (per Hibernate docs, Topic 2).
**How to avoid:** ArchUnit rule (Topic 9, rule c) + allow-list to a small infra package that manually adds the predicate.

### Pitfall 3: Spring Modulith 2.0 not yet GA
**What goes wrong:** Pull 1.4 against Boot 4 — Modulith internals reference Boot 3 APIs and fail to start OR fail silently.
**Why:** Version mismatch.
**How to avoid:** Use 2.0.0-SNAPSHOT or 2.0.0-M* explicitly; gate CI on `ApplicationModules.verify()`; re-check Maven Central at implementation time.

### Pitfall 4: `include_granted_scopes` omitted on second leg
**What goes wrong:** Second OAuth authorization for Gmail scope returns an access token that does NOT carry `openid profile email`, so user profile claims are lost.
**Why:** Google's default is to issue a token for only the requested scopes.
**How to avoid:** Customize `OAuth2AuthorizationRequestResolver` to append `include_granted_scopes=true`.

### Pitfall 5: `Sensitive<T>` serialized into JSON response
**What goes wrong:** API controller returns a DTO containing `Sensitive<String> email` — Jackson serializes `.value()` by default because it's a record component.
**Why:** Record component accessors are reflected by Jackson.
**How to avoid:** Register `JsonSerializer<Sensitive<?>>` that serializes as `"***REDACTED***"` OR (better) — never put `Sensitive` in response DTOs; it belongs to internal/domain types only. ArchUnit rule: DTO packages may not reference `Sensitive`.

### Pitfall 6: AES-GCM nonce reuse
**What goes wrong:** Developer initializes `SecureRandom` once and reseeds it wrong, or reuses a 96-bit counter without wraparound handling.
**Why:** AES-GCM nonce reuse with the same key breaks both confidentiality and authenticity.
**How to avoid:** `SecureRandom().nextBytes(new byte[12])` per encryption; unit test that asserts 10,000 consecutive nonces are unique.

### Pitfall 7: CSRF cookie on OAuth callback
**What goes wrong:** Google POSTs to `/login/oauth2/code/google` — without CSRF exception, the request is rejected.
**Why:** Default CSRF config covers all state-changing requests.
**How to avoid:** Spring Security 7 already ignores CSRF for OAuth2 redirect URIs; but if custom matchers are added, explicitly add `.ignoringRequestMatchers("/login/oauth2/code/**", "/oauth2/callback/**")`.

### Pitfall 8: Logback TurboFilter recursion
**What goes wrong:** Filter calls `logger.log(...)` which re-enters the filter.
**Why:** TurboFilter is invoked on every call.
**How to avoid:** Maintain a thread-local boolean; or emit directly to the appender; or use the JsonProvider approach (recommended — see Topic 10).

## Code Examples

All code examples for each topic appear in the Per-Topic Research section. Cross-reference:
- Tenant filter: Topic 3
- TenantAwareTaskScope: Topic 4
- OAuth2 config: Topic 5
- AES-GCM envelope: Topic 8
- ArchUnit rules: Topic 9
- `Sensitive<T>` + TurboFilter: Topic 10
- Liquibase YAML: Topic 11
- Middleware: Topic 13
- Gradle conventions: Topic 14

## Runtime State Inventory

Not applicable — Phase 1 is greenfield. No existing runtime state to migrate.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — empty DB on first migration | N/A |
| Live service config | None — no services exist | N/A |
| OS-registered state | None | N/A |
| Secrets/env vars | Will be created in Phase 1 (GOOGLE_OAUTH_*, REDIS_*, DB_*, refresh-token-key) | Document in `.env.example` |
| Build artifacts | None (greenfield) | N/A |

## Environment Availability

Phase 1 depends on external tooling to build and run integration tests. Planner should add a Wave 0 task that verifies:

| Dependency | Required By | Typical Check | Fallback |
|------------|------------|---------------|----------|
| JDK 25 | All backend modules | `java --version` includes `25` | Gradle toolchain auto-downloads from Foojay — no local JDK needed if toolchain plugin is configured |
| Gradle 9.4.1 | Build | via Gradle wrapper (`./gradlew --version`) | Wrapper auto-provisions |
| pnpm 10.33.x | apps/web | `pnpm --version` | Volta / corepack |
| Node 20.9+ | Next.js build | `node --version` | Volta |
| Docker | Testcontainers, spring-boot-docker-compose | `docker info` | **Blocking** — needed for Postgres/Redis integration tests |
| Postgres 17 image | Testcontainers | pulled automatically | network egress required |
| Redis 7.2 image | Testcontainers | pulled automatically | network egress required |
| GCP Secret Manager (real or emulator) | AES-GCM key loading | live project OR `spring.cloud.gcp.secretmanager.enabled=false` profile + `${LOCAL_REFRESH_TOKEN_KEY}` env var | For dev, use local env var; for CI, use GCP service account |
| Google OAuth test project | Live OAuth callback tests | Google Cloud Console client credentials with `http://localhost:8080/login/oauth2/code/google` registered | A test profile may stub with `WireMock` for the token endpoint |

**Missing dependencies with no fallback (blocking at execution time):** Docker is required for Testcontainers-backed integration tests — planner should include a prerequisite check in Wave 0.

## Validation Architecture

**nyquist_validation = true** — this section is mandatory.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 Jupiter (managed by Spring Boot 4.0.6 BOM) + Testcontainers + ArchUnit 1.3+ |
| Config file | `build.gradle.kts` (via `zeromail.java-conventions` + `zeromail.archunit-conventions`) |
| Quick run command | `./gradlew :backend:core:test :backend:api:test --tests "*ArchTest" -x integrationTest` |
| Full suite command | `./gradlew check` (includes unit, ArchUnit, integration) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FND-01 | Request runs in a tenant ScopedValue | Integration | `./gradlew :backend:api:integrationTest --tests MultiTenantLeakIntegrationTest.requests_never_cross_tenant_under_load` | ❌ Wave 0 |
| FND-02 | `ThreadLocal` reference fails build | ArchUnit | `./gradlew :backend:core:test --tests SafetyContractArchTests.no_threadlocal` | ❌ Wave 0 |
| FND-03 | `@Sensitive` + scrub filter redact | Unit + integration | `./gradlew :backend:core:test --tests SensitiveToStringTest SensitiveMarkerScrubFilterTest` | ❌ Wave 0 |
| FND-04 | Logger call with `Sensitive` fails build | ArchUnit | `./gradlew :backend:core:test --tests SafetyContractArchTests.no_sensitive_in_logger` | ❌ Wave 0 |
| FND-05 | Concurrent multi-tenant leak test | Integration (virtual threads) | `./gradlew :backend:api:integrationTest --tests MultiTenantLeakIntegrationTest` | ❌ Wave 0 |
| FND-06 | OpenAPI published; FE codegen works | Integration + CI script | `./gradlew :backend:api:bootRun &` + `pnpm --filter @zeromail/web run generate:types` | ❌ Wave 0 |
| FND-07 | CASA submission filed | Manual checkbox | Tracked in `docs/casa/submission-log.md` | ❌ Wave 0 |
| AUTH-01 | Sign in via Google | Integration (WireMock for Google) | `./gradlew :backend:api:integrationTest --tests GoogleLoginE2ETest` | ❌ Wave 0 |
| AUTH-02 | Exactly one Gmail per tenant | Integration | `./gradlew :backend:core:integrationTest --tests GmailConnectionUniquenessTest` | ❌ Wave 0 |
| AUTH-03 | Revoke + delete account | Integration | `./gradlew :backend:api:integrationTest --tests AccountDeletionE2ETest` | ❌ Wave 0 |
| AUTH-04 | Cookie session (not JWT) | Integration | `./gradlew :backend:api:integrationTest --tests SessionCookieE2ETest` | ❌ Wave 0 |
| AUTH-05 | `invalid_grant` → DISCONNECTED | Integration (WireMock returns 400 invalid_grant) | `./gradlew :backend:api:integrationTest --tests DisconnectOnInvalidGrantTest` | ❌ Wave 0 |
| AUTH-06 | Onboarding state machine | Integration | `./gradlew :backend:api:integrationTest --tests OnboardingStateMachineTest` | ❌ Wave 0 |

### Success criteria → Test layer (from ROADMAP Phase 1)

| # | Success criterion | Test layer(s) | Falsifiable assertion |
|---|-------------------|---------------|-----------------------|
| 1 | Sign in → connect Gmail → template → delete | E2E integration (`GoogleLoginE2ETest`, `OnboardingStateMachineTest`, `AccountDeletionE2ETest`) | After delete: 0 rows in `users`, `gmail_connections`, `onboarding_selections` for that tenant |
| 2 | Concurrent virtual-thread leak test + ArchUnit ban on ThreadLocal | `MultiTenantLeakIntegrationTest` + `SafetyContractArchTests.no_threadlocal` | 200 concurrent requests all observe their own tenant; synthetic `ThreadLocal` import fails ArchUnit |
| 3 | No body/prompt/completion in logs (build + runtime) | ArchUnit (`no_sensitive_in_logger`, `sensitive_names_wrapped`) + `SensitiveMarkerScrubFilterTest` + grep over log output in synthetic traffic test | `grep -E "(raw email body|prompt|completion)" build/logs/test-traffic.log` returns 0 lines |
| 4 | Revoked tenant → DISCONNECTED on next request | `DisconnectOnInvalidGrantTest` | After WireMock serves `invalid_grant`, `GET /tenant/status` returns `DISCONNECTED` within one request round-trip |
| 5 | OpenAPI published + apps/web codegen works + CASA filed | Integration (`OpenApiSchemaTest` ensures `/v3/api-docs` returns valid JSON) + CI pipeline step that runs `openapi-typescript` + manual checkbox in `docs/casa/submission-log.md` | `schema.d.ts` is regenerated and committed as CI artifact; CASA log references a Google-provided tracking ID |

### Sampling Rate
- **Per task commit:** `./gradlew :backend:core:test :backend:api:test` (unit + ArchUnit, fast, < 30s)
- **Per wave merge:** `./gradlew check` (+ Testcontainers integration)
- **Phase gate:** full `./gradlew check` green on clean Docker + `apps/web` `pnpm run build` green

### Wave 0 Gaps (all to be created)
- [ ] `backend/core/src/test/java/com/zeromail/core/privacy/SafetyContractArchTests.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/privacy/SensitiveToStringTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/privacy/SensitiveMarkerScrubFilterTest.java`
- [ ] `backend/api/src/integrationTest/java/com/zeromail/api/MultiTenantLeakIntegrationTest.java`
- [ ] `backend/api/src/integrationTest/java/com/zeromail/api/GoogleLoginE2ETest.java` (WireMock)
- [ ] `backend/api/src/integrationTest/java/com/zeromail/api/DisconnectOnInvalidGrantTest.java` (WireMock)
- [ ] `backend/api/src/integrationTest/java/com/zeromail/api/OnboardingStateMachineTest.java`
- [ ] `backend/api/src/integrationTest/java/com/zeromail/api/AccountDeletionE2ETest.java`
- [ ] `backend/api/src/integrationTest/java/com/zeromail/api/SessionCookieE2ETest.java`
- [ ] `backend/api/src/integrationTest/java/com/zeromail/api/OpenApiSchemaTest.java`
- [ ] `backend/core/src/integrationTest/java/com/zeromail/core/persistence/GmailConnectionUniquenessTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/ApplicationModulesTest.java`
- [ ] `backend/core/src/test/java/com/zeromail/core/crypto/RefreshTokenCipherTest.java`
- [ ] Shared test support: `backend/test-support/` module with Testcontainers base classes
- [ ] `buildSrc` convention: `zeromail.archunit-conventions.gradle.kts` wires ArchUnit into every module automatically
- [ ] `docs/casa/submission-log.md` (tracks FND-07 manual artifact)

## Security Domain

### Applicable ASVS Categories (ASVS 4.0)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Spring Security OAuth2 Client (Google OIDC), no local passwords |
| V3 Session Management | yes | Spring Session Redis, HttpOnly + Secure + SameSite=Lax cookie, server-side revocation |
| V4 Access Control | yes | Multi-tenant `@TenantId` + `CurrentTenantIdentifierResolver` + ArchUnit ban on native SQL bypass |
| V5 Input Validation | partial | Jakarta Validation on DTOs; full HTML sanitization is Phase 2C scope |
| V6 Cryptography | yes | AES-GCM-256 (SunJCE) envelope for refresh tokens; key via GCP Secret Manager — **no hand-rolled crypto** |
| V7 Error Handling and Logging | yes | `logstash-logback-encoder` JSON + `@Sensitive` + TurboFilter scrub + ArchUnit log rules |
| V8 Data Protection | yes | No raw email bodies stored; refresh tokens encrypted at rest; DELETE account cascade |
| V9 Communications | yes | HTTPS only (Cloud Run terminates TLS); Secure cookies |
| V10 Malicious Code | low in P1 | Dependency checks via GitHub Dependabot (or equivalent) to be wired in Wave 0 |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cross-tenant data read | Information Disclosure | `@TenantId` + ScopedValue filter + ArchUnit ban on native SQL + concurrent leak test |
| OAuth refresh token theft from DB leak | Information Disclosure | AES-GCM envelope with key outside DB (Secret Manager) |
| CSRF on state-changing endpoints | Tampering | `CookieCsrfTokenRepository` + `X-XSRF-TOKEN` header |
| Session fixation | Elevation of Privilege | Spring Session rotates session ID on authentication (default) |
| Log injection / PII leakage | Information Disclosure | `@Sensitive<T>` + ArchUnit rules + scrub filter |
| Open-redirect on OAuth callback | Tampering | `redirect-uri` is fixed per `ClientRegistration` — Spring enforces |
| Revoked-grant zombie sessions | Repudiation | DISCONNECTED flip on `invalid_grant` + UI reconnect prompt |
| Virtual-thread context bleed | Information Disclosure | ArchUnit ban on raw `Thread.ofVirtual()` + `TenantAwareTaskScope` |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring Modulith publishes a 2.0.0-Mx milestone by implementation time | Topic 1 | Planner must pin SNAPSHOT or defer Modulith wiring to Phase 2 — moderate rework |
| A2 | Exact shape of `StructuredTaskScope` API in JDK 25 (post-JEP 505 promotion) | Topic 4 | Code sketch may need minor adjustment; semantics unchanged |
| A3 | Logback `TurboFilter`-based scrub is viable vs. JsonProvider-based scrub | Topic 10 | May switch to JsonProvider variant — same contract |
| A4 | `google-api-services-gmail` rev `v1-rev20250331-2.0.0` is still current | Topic 5 | Minor rev bump at impl; API stable |
| A5 | `springdoc-openapi-starter-webmvc-ui` supports Spring Boot 4 | Topic 12 | If not yet released, use `springdoc-openapi-starter-webmvc-api` (no Swagger UI) |
| A6 | Google's `OAuth2AuthorizationException` exposes `invalid_grant` via `OAuth2Error.errorCode` | Topic 7 | Need to interrogate `getCause()` chain if not — minor |
| A7 | CASA Tier 2 applies at Zero Mail's expected user count | Topic 16 | Google assigns tier; applicant cannot choose. Plan for Tier 2 as baseline; upgrade if assigned Tier 3 |
| A8 | Spring Security 7's CSRF + Spring Session integration pattern is identical to 6.x CSRF docs | Topic 6 | API is stable; re-check at impl |

## Open Questions

1. **Spring Modulith 2.0 availability.** Is a milestone released by implementation time? If not, is it acceptable to wire against SNAPSHOT (with CI pulling from the Spring snapshot repo)?
   - **Recommendation:** Acceptable to use SNAPSHOT for Phase 1 given the module verification is additive (if Modulith fails to load, fall back to `package-info.java` without annotation and skip the verify test). Planner should create a fallback plan.

2. **Scrub filter: TurboFilter vs. custom JsonProvider.** Both satisfy D-E2. TurboFilter has recursion hazards; JsonProvider operates after rendering and is recursion-free.
   - **Recommendation:** Planner should choose JsonProvider (simpler, safer) unless there's a TurboFilter-only constraint.

3. **AES-GCM AAD.** Should AAD = `tenantId` be added to the envelope?
   - **Recommendation:** YES. Add to the envelope spec as an implementation detail — no schema change (AAD is not stored; only the key, nonce, tag). Planner should finalize.

4. **GCP Secret Manager in local dev.** Default to local env var fallback, or require dev-time service account?
   - **Recommendation:** Local env var with `spring.cloud.gcp.secretmanager.enabled=false` profile. Document in README.

5. **CASA tier.** Google assigns tier post-submission. Phase 6 gating must accept whatever tier Google picks.
   - **Recommendation:** No action for Phase 1 beyond accurate submission.

## State of the Art

| Old approach | Current approach | When changed | Impact |
|--------------|------------------|--------------|--------|
| `SecurityContextPersistenceFilter` | `SecurityContextHolderFilter` (Spring Security 6+) | 2022 | Explicit save required; improves perf |
| `ThreadLocal` for request context | `ScopedValue` (JEP 505 stable in JDK 25) | 2025 | Better structured-concurrency inheritance, immutability |
| Pre-preview `StructuredTaskScope` (`ShutdownOnFailure`) | Stabilized API in JDK 25 | 2025 | Shape slightly different; recheck at impl |
| `javax.*` namespaces | `jakarta.*` (Jakarta EE 9+) | 2022 | Boot 3/4 Jakarta-only |
| Hibernate 5 multi-tenancy via `MultiTenantConnectionProvider` | Hibernate 6.3+ `@TenantId` annotation-driven | 2023 | Much less boilerplate for DISCRIMINATOR mode |
| Jackson 2.x | Jackson 3.1.x (Boot 4 default) | 2026 | Some namespace changes; verify custom serializers |

## Sources

### Primary (HIGH confidence)
- `/spring-projects/spring-modulith` (Context7) — `appendix.adoc` compatibility matrix, `testing.adoc`, `verification.adoc`, `introducing-spring-modulith.adoc`
- `/hibernate/hibernate-orm` (Context7) — `introduction/Advanced.adoc` (@TenantId), `userguide/chapters/multitenancy/MultiTenancy.adoc` (CurrentTenantIdentifierResolver)
- `/spring-projects/spring-security` and `/websites/spring_io_spring-security_reference_6_5` (Context7) — persistence.adoc, csrf.html, OAuth2 client reference
- `/tng/archunit` (Context7) — `llms.txt` + `007_The_Lang_API.adoc`
- `/spring-projects/spring-session` (Context7) — `llms.txt` + `java-security.adoc` + `boot-redis.adoc`
- `/liquibase/liquibase-docs` (Context7) — `llms.txt` + `change-types/includeall.html`
- `/springdoc/springdoc-openapi` (Context7) — `llms.txt` + `README.md`
- `/vercel/next.js` v16.2.2 branch (Context7) — `authentication.mdx`, `proxy.mdx`
- `/websites/openapi-ts_dev` (Context7) — introduction, CLI, migration-guide, openapi-fetch
- `/spring-projects/spring-boot/v4.0.3` (Context7) — dev-services.adoc, task-execution-and-scheduling.adoc
- CLAUDE.md (project) — locked stack, anti-patterns, versions

### Secondary (MEDIUM confidence)
- OpenJDK JEP 505 (Scoped Values) — general language feature semantics (training-data + JEP abstract)
- OpenJDK JEP 499 / 505 (Structured Concurrency) — training-data on stabilization path in JDK 25
- google-auth-library-java, google-api-services-gmail — training-data + published READMEs

### Tertiary / ASSUMED (LOW confidence — validate at impl)
- Exact CASA submission form fields and tier-assignment outcome
- Spring Modulith 2.0 milestone availability by implementation date
- Spring Boot 4 compatibility of `springdoc-openapi-starter-webmvc-ui` latest release

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions verified via Context7 or CLAUDE.md
- Architecture patterns: HIGH — Spring Security filter ordering and Hibernate multi-tenancy fully documented
- Spring Modulith integration: MEDIUM — Boot 4 line is SNAPSHOT
- ScopedValue / StructuredTaskScope API: MEDIUM — JDK 25 stabilized the API; details recheck at impl
- AES-GCM pattern: HIGH — standard JCE pattern, well-understood pitfalls
- ArchUnit rules: HIGH — APIs stable, pattern well-known
- Logback TurboFilter: MEDIUM — works but JsonProvider alternative is simpler
- Next.js / openapi-typescript: HIGH — current stable 2026-04-24
- CASA workflow: LOW-MEDIUM — details change; workflow high-level correct
- Validation architecture: HIGH — standard test pyramid, ArchUnit + Testcontainers

**Research date:** 2026-04-24
**Valid until:** 2026-05-24 (30 days for a stable-ish stack; re-check Spring Modulith 2.0 status and spring-cloud-gcp at impl start)

## RESEARCH COMPLETE
