---
phase: 1
reviewers: [claude, codex, opencode]
reviewed_at: 2026-04-25T00:18:54+07:00
plans_reviewed:
  - 01-01-PLAN.md
  - 01-02-PLAN.md
  - 01-03-PLAN.md
  - 01-04-PLAN.md
  - 01-05-PLAN.md
  - 01-06-PLAN.md
  - 01-07-PLAN.md
  - 01-08-PLAN.md
  - 01-09-PLAN.md
---

# Cross-AI Plan Review — Phase 1

## Consensus Summary

Three available external reviewers were invoked: Claude, Codex, and OpenCode. The reviews are not unanimous on severity, but they converge on several execution risks that should be addressed before running all Phase 1 plans.

### Agreed Strengths

- The Phase 1 plan set is detailed, structured, and maps to the safety-first roadmap intent.
- The schema, tenant isolation, crypto envelope design, and minimal UI scope are directionally sound.
- The reviewers agree Phase 1 is worth executing after targeted corrections rather than discarding the plan set.

### Agreed Concerns

- The Gmail second OAuth leg is not clearly wired end-to-end: callback handling, gmail_connections persistence, refresh-token encryption, onboarding state transition, and DISCONNECTED recovery need explicit tasks and tests.
- Frontend/backend auth semantics are fragile: /tenant/connect-gmail, CSRF, redirect methods, cookie/CORS/same-origin behavior, and onboarding flow can break the core success criterion.
- Sensitive logging controls may overclaim: current ArchUnit/log scrub tests risk proving synthetic plumbing rather than realistic prevention of body/prompt/completion leaks.
- Spring Security and framework API compatibility in the OAuth/security plan needs verification before implementation, especially token refresh/client APIs and callback handling.
- Runtime validation needs a stronger end-to-end path for sign-in → connect Gmail → onboarding → revoke/disconnect → delete data.

### Divergent Views

- Claude reports APPROVE WITH CHANGES; Codex and OpenCode report BLOCK because the Gmail connect, CSRF/auth, and log-safety gaps threaten Phase 1 acceptance criteria.
- Some concerns are implementation-level risks rather than plan-fatal blockers, but all reviewers agree they should be incorporated before broad execution.

### Recommended Next Action

Run $gsd-plan-phase 1 --reviews before $gsd-execute-phase 1, and ensure the replanning explicitly addresses the agreed concerns above.

---

## Claude Review

# Phase 1 Plan Review — Zero Mail

## 1. Overall verdict

**APPROVE WITH CHANGES** — major revisions needed before execution.

The plans are thoroughly researched, well-structured, and the decision tree is coherent. But there are several **correctness-threatening defects** in the OAuth/session/Hibernate integration path (plan 05 + 07), a **latent chicken-and-egg** in the Hibernate tenant resolver, and the real Gmail-connection persistence path is **silently missing** from Phase 1 — which means ROADMAP success criterion #1 (“connect one Gmail account”) is not actually deliverable as planned.

## 2. Top risks before execution

1. **Gmail connection is never persisted.** Plan 05 wires a second OAuth registration (`google-gmail`) but nothing writes a `gmail_connections` row on callback. There is no custom `OAuth2AuthorizedClientService`, no callback handler for the second leg, and no cipher integration. `GET /tenant/status` will always return `NOT_CONNECTED` and `AUTH-02/AUTH-03/AUTH-05` cannot be exercised end-to-end.
2. **Hibernate tenant resolver chicken-and-egg.** `ScopedValueTenantResolver.resolveCurrentTenantIdentifier()` throws `IllegalStateException` when unbound. `GoogleOAuthSuccessHandler` calls `tenants.save(new TenantEntity(...))` *before* any ScopedValue is bound → Hibernate opens a session → resolver throws → first login crashes. The tenant-root save must be specially allowed (null tenant, bootstrap resolver, or bind a sentinel).
3. **`DefaultRefreshTokenTokenResponseClient` is removed/deprecated in Spring Security 7.** The plan acknowledges this in a comment but still ships the class by name. The substitute (`RestClientRefreshTokenTokenResponseClient`) has a different extension shape; this needs to be resolved before code is written, not discovered mid-execution.
4. **Cross-origin cookie + CSRF break.** `apps/web` on `localhost:3000` + `backend/api` on `localhost:8080` with `credentials: "include"` and `SameSite=Lax` will not send the session cookie, and `XSRF-TOKEN` cannot be read across origins. No CORS config, no reverse-proxy, no same-origin strategy is specified.
5. **Snapshot dependency in build.** Spring Modulith `2.0.7-SNAPSHOT` pins a *moving* artifact. Rebuilds will become non-deterministic the moment the snapshot is republished. Recommend switching to the latest milestone if any exists, or caching the snapshot hash in a dependency lock.
6. **MDC leak in scrub filter.** Plan 03’s `SensitiveMarkerScrubFilter` calls `MDC.put("scrubbed","true")` with no matching `MDC.remove`. Subsequent log events on the same (virtual) thread will be stamped with stale scrub markers — false positives in the plan 09 assertion.
7. **Plan 09 log-scrub test has tautological coverage.** Seed data writes sentinel bytes into `refresh_token_encrypted` but no Phase 1 code path logs that field. The test can pass even if the scrub filter is removed. The “≥1 `scrubbed=true` event” assertion only fires if *some* code logs a `Sensitive(...)` render — which doesn’t happen on the 3 real endpoints either. FND-03 runtime proof is weak.

## 3. Missing tasks / unclear dependencies

- **No `OAuth2AuthorizedClientService`** that writes `gmail_connections` and invokes `RefreshTokenCipher.encrypt`. Plan 06 produces the cipher; nothing uses it. Add a task in plan 05 or 06.
- **No callback/handler for the `google-gmail` second leg.** `google-gmail` issues a code at `/oauth2/callback/gmail`; there is no controller or success handler that transitions `users.onboarding_step` → `GMAIL_CONNECTED` and upserts the `gmail_connections` row.
- **No CORS configuration** in `SecurityConfig`. Required once the frontend runs separately.
- **`GmailConnectionEntity` setters are declared in prose only** (“add getters and setters”). Plan 05 and 09 tests call `setRefreshTokenEncrypted`, `setStatus`, `setDisconnectedAt`, `setConnectedAt`, `setTenantId`, `setGoogleEmail`. Plan 04 must enumerate them explicitly.
- **Plan 05 Task 3’s `OAuth2AuthenticationToken` registration id** uses the literal `"google-login"`. The correct value is the `ClientRegistration#getRegistrationId()` (`"google"`). This will cause Spring Session hydration to fail silently.
- **Plan 08 `ConnectGmailController` is POST**, redirecting to `/oauth2/authorization/google-gmail` (GET). Works via 302 but the frontend uses `<form method="post">` — CSRF interceptor will reject without an `X-XSRF-TOKEN`; the Phase 1 UI does not set one on the raw form submit. Use a regular anchor or add CSRF header via JS.
- **No dependency declared between plan 08 Task 3 codegen and plan 07’s test-profile boot.** `verify-codegen.sh` launches `bootRun` which requires the full `application.yml` (Redis, OAuth client id/secret). No env guidance for running this locally or in CI.

## 4. Security / privacy / compliance concerns

- **Refresh tokens may end up unencrypted.** Because there is no `AuthorizedClientService` wiring the cipher, if any Phase 1 path triggers a Google refresh, Spring Security’s default in-memory store holds the plaintext. Either (a) disallow Gmail connection in Phase 1, or (b) actually wire the cipher.
- **`TenantEntity` has no `@TenantId`.** That is correct (tenant root), but means the resolver must not be invoked during tenant inserts. Without a bootstrap path, the resolver throws and the whole onboarding flow fails — see risk #2.
- **`LogScrubSyntheticTrafficTest` adds a ROOT-logger appender but the Logback config is from `logback-spring.xml` in core**; test-side appender capture works for events passing through the logger hierarchy, but the `SensitiveMarkerScrubFilter` is registered via XML → only active if the test also loads `logback-spring.xml`. Spring Boot tests do, but verify it’s attached before asserting.
- **CSRF bypass for OAuth2 callbacks is correct**, but `/tenant/connect-gmail` is a state-changing POST that the UI submits as a form; without CSRF on that path, the SPA will fail.
- **No rate limiting on `DELETE /me/account` or `/oauth2/authorization/*`.** Not a Phase-1 blocker but worth noting — an anonymous attacker can trigger unlimited Google redirects.
- **CASA scopes justification references “AES-GCM encrypts refresh tokens”** even though the wiring isn’t actually live in Phase 1. Do not submit the attestation until the cipher is wired, or the attestation is materially false.
- **Session cookie `secure: false` in dev profile** — ensure the prod profile actually overrides (no prod profile yaml is shipped in Phase 1 plans).

## 5. Testing and validation gaps

- **FND-05 leak test uses N=100.** ROADMAP says “≥100”; plan 05 Task 3 sets `int N = 100`. Edge-of-spec — bump to 200 to match research Topic 15.
- **No test for `TenantAwareTaskScope.fork` *without* a prior bind** — should throw immediately. Current test only proves the happy path.
- **No test for the `GmailScopeRequestResolver`** — the `include_granted_scopes` logic is critical for the two-leg flow and entirely untested.
- **No integration test actually exercises the `google-gmail` registration callback** (because the callback handler doesn’t exist — see section 3).
- **ArchUnit rule (d) `no_sensitive_in_logger` is admitted to be incomplete** (Logger signatures use `Object...`). The rule will pass on a violating codebase. Either escalate to a bytecode scan or strike from FND-04 claims.
- **Plan 09 scrub test does not negate-control**: it doesn’t confirm that disabling the filter would cause the test to fail. Add a negative-control variant.
- **Plan 03 TurboFilter test asserts `anySatisfy(... MDC entry scrubbed=true)`** — but the same MDC will stamp *all subsequent events in the list* due to the missing `MDC.remove`. Test passes for the wrong reason.

## 6. Plan-by-plan notes

**Plan 01 (scaffold).** Solid. Gradle toolchain + BOM layout is idiomatic. Risk: Modulith SNAPSHOT pin. Minor: `settings.gradle.kts` preview feature flag for type-safe project accessors is fine but `enableFeaturePreview` name differs by Gradle version — verify on 9.4.1.

**Plan 02 (tenant primitives).** Cleanest plan. One concern: `TenantAwareTaskScope` depends on the JDK 25 stabilized `StructuredTaskScope.open()` signature, which is acknowledged as assumed. Verify before coding. ArchUnit rule `fanout_via_helper` needs allow-listing for `ForkJoinPool.commonPool()` / reactive schedulers if any library uses them internally — watch for false positives from Spring Session or Redis Lettuce.

**Plan 03 (log safety).** Correct direction but the MDC cleanup bug and the admitted ArchUnit-(d) limitation undermine FND-03/FND-04. Either switch to the JsonProvider alternative mentioned in research Topic 10 (recursion-free + can strip sentinel bytes), or add explicit `MDC.remove` in a `finally` hook. Also: mutating `params[]` in place is undefined behavior per the SLF4J contract — some logger wrappers pass immutable arrays.

**Plan 04 (Liquibase + entities).** Generally good. Blockers: missing explicit setters on `GmailConnectionEntity`; `TenantEntity` bootstrap problem (see #2); no changeset for the `refresh_token_key_version` column if rotation is envelope-based — actually the version lives inside the bytea, so schema is fine. Add a comment in the changelog noting that.

**Plan 05 (security + leak test).** Most at-risk plan. Three defects: (a) `DefaultRefreshTokenTokenResponseClient` API mismatch (Spring Security 7), (b) tenant save before ScopedValue bind, (c) test session `registrationId` literal mismatch. Also, `TenantBindingFilter` resolves via `UserRepository.findByGoogleSubject` on *every* request — a DB round-trip per request pre-tenant-bind. That’s two queries per request minimum; acceptable but cache in Spring Session principal instead.

**Plan 06 (crypto).** Cleanest of the auth plans. AAD binding is correct. Two notes: the AES-256 key requirement assumes unlimited strength policy (true on Temurin 25) — worth an assert at startup. Also, integration with OAuth client service is entirely missing (see risk #1).

**Plan 07 (OpenAPI + controllers).** Good. Three concerns: (a) `ConnectGmailController` POST→302 interaction with CSRF (see §3); (b) `AccountDeletionE2ETest` seeds via setters not defined in plan 04; (c) onboarding state-machine test doesn’t cover the backwards-transition rejection explicitly — `advanceTo` guards it, but no test asserts the `IllegalStateException`.

**Plan 08 (Next.js).** The scaffold is fine; the codegen verification script is well-designed. Blockers: no CORS/same-origin strategy, CSRF header not set on `ConnectGmail` form, `createClient` baseUrl hardcoded to `http://localhost:8080`. Add a dev-proxy (e.g., Next rewrites to `/api/*` → Spring) so session cookies + CSRF work without cross-origin gymnastics.

**Plan 09 (log scrub + CASA).** The log-scrub test is a weak tautology (see risk #7). Fix by (a) adding an endpoint whose handler deliberately logs a `Sensitive` value to prove positive coverage, or (b) asserting on known Spring MVC access log fields for paths containing sentinel URL params. CASA docs are good; human checkpoint is correctly modeled.

## 7. Must-fix items before executing

1. Resolve Spring Security 7 refresh-token client class name and `OAuth2AuthorizedClientService` integration path. Cipher must be wired or Gmail connection descoped from Phase 1.
2. Fix tenant-root save: either make `ScopedValueTenantResolver` return a sentinel when unbound for tables without `@TenantId`, or bind a `"bootstrap"` scoped value around tenant inserts.
3. Define cross-origin strategy (Next rewrite proxy or same-origin deployment) and add CORS if needed.
4. Write explicit setters/constructor for `GmailConnectionEntity` in plan 04.
5. Correct `OAuth2AuthenticationToken` registration id to `"google"` in `TestSessionSupport`.
6. Add `MDC.remove("scrubbed"); MDC.remove("scrub_reason")` after message emission in the TurboFilter (or switch to JsonProvider).
7. Add a real callback handler for `google-gmail` that writes `gmail_connections` through the cipher, and advances `onboarding_step`.
8. Add CSRF header on the `ConnectGmail` form submission (or make it a GET link).
9. Either strengthen FND-03 runtime test with a log-positive-control path, or relabel it as “filter is wired” rather than “bodies are scrubbed from real traffic.”
10. Downgrade Modulith to a milestone or pin the snapshot artifact hash.

## 8. Nice-to-have improvements

- Add a `prod` profile yaml turning `secure: true`, hardening `SameSite=Strict` where possible, and disabling Swagger UI.
- Add an ArchUnit rule forbidding `@Component`/`@Service` classes in `..api.controllers..` beyond declared controllers (keeps OpenAPI surface tight).
- Cache `tenantId` in the `OidcUser` attributes at login so `TenantBindingFilter` doesn’t DB-lookup each request.
- Add a no-op test fixture that asserts `ApplicationModules.verify()` also passes *without* the api module entrypoint — catches accidental cyclic imports from core→api.
- Bump FND-05 leak test to N=200 and add a p99 latency assertion to catch thread-pinning regressions.
- Replace the `Sensitive<T>` record with a sealed interface to prevent consumers from reading `.value()` reflectively through records’ canonical accessor — that’s the Jackson pitfall the plan already worries about.
- Add an explicit `apps/web` CI job that fails if `schema.d.ts` is out of sync with `/v3/api-docs` (drift detector for future phases).
- Ship a dev-only `docker-compose.override.yml` with Wiremock stubbing Google token endpoint — unlocks full local E2E without a real OAuth client.


---

## Codex Review

## 1. Overall Verdict: BLOCK

Phase 1 is directionally strong and shows serious attention to tenant isolation, OAuth, log safety, OpenAPI, and validation. However, I would block execution until several plan-level contradictions and infeasible/fragile tasks are fixed.

The biggest issue: several plans claim to satisfy Phase 1 success criteria but leave critical pieces stubbed, deferred, or technically unlikely to work as written. In particular, OAuth refresh-token encryption is not actually wired into token persistence, log-scrub runtime verification is internally inconsistent, Spring Session/OIDC test setup is fragile, OpenAPI/auth endpoints are partially mismatched, and frontend forms call CSRF-protected backend endpoints in ways that likely fail.

---

## 2. Top Risks Before Execution

- **Refresh tokens may be stored unencrypted despite Phase 1 requiring token safety.** Plan 06 explicitly defers wiring the cipher into `OAuth2AuthorizedClientService`, while Phase 5/7 imply Gmail connection/token flows exist.
- **Log-scrub filter design likely does not satisfy FND-03/FND-04.** The TurboFilter cannot reliably mutate formatted output; MDC cleanup semantics are wrong; ArchUnit logger detection is admitted as incomplete.
- **Tenant binding depends on database lookup inside every request filter.** This contradicts `01-CONTEXT.md` decision D-B1, which says no DB lookup in the filter and tenant ID should come from the session principal.
- **OAuth2 flow is under-specified and internally inconsistent.** Spring Security callback paths, `POST /tenant/connect-gmail`, CSRF, OAuth authorization resolver APIs, token persistence, and Gmail callback handling do not line up cleanly.
- **Tests rely on impossible or incomplete code snippets.** Multiple tests instantiate entities with missing required fields or comments saying “populate via setters”; this is not execution-ready.
- **Spring Modulith snapshot dependency is risky.** Plans pin `2.0.7-SNAPSHOT`; reproducibility and CI stability are weak for a foundation phase.
- **CASA “initiated” is not fully executable.** Plan 09 has a blocking human checkpoint, but the roadmap success criterion requires a filed submission, not just draft docs.

---

## 3. Missing Tasks or Unclear Dependencies

- **Custom `OAuth2AuthorizedClientService` is missing.** Required to encrypt/decrypt refresh tokens and persist Gmail authorized clients safely.
- **Gmail second-leg callback persistence is missing.** No clear task creates/updates `gmail_connections` after `google-gmail` consent, stores granted scopes, captures refresh token, or advances onboarding to `GMAIL_CONNECTED`.
- **Session principal design is missing.** Context says tenant ID is carried by the authenticated principal in Redis-backed Spring Session; plans instead re-query users by OIDC subject.
- **CSRF flow is incomplete.** Frontend posts forms directly to backend endpoints without reliably including `X-XSRF-TOKEN`, especially `/tenant/connect-gmail`.
- **CORS/same-origin deployment assumptions are missing.** `apps/web` defaults to `localhost:3000`, backend to `localhost:8080`; cookie/session behavior across origins needs explicit config.
- **Redis Testcontainer support is missing.** Spring Session Redis tests require Redis, but shared test base only provisions Postgres.
- **Entity constructors/setters are underspecified.** Plans repeatedly depend on setters that are not fully defined.
- **Integration test application context is unclear.** `backend/core` tests use `@SpringBootTest` but core is a library module; the test app class is optional in text but required in practice.
- **OpenAPI endpoint list differs from context.** Context includes `POST /auth/google/callback` and `GET /auth/gmail/callback`; plan 07 omits or delegates them, while tests expect only controller paths.
- **Onboarding transition to `GMAIL_CONNECTED` is not implemented.** The UI expects it, but no backend controller or OAuth success handler clearly sets it after Gmail consent.

---

## 4. Security / Privacy / Compliance Concerns

- **Token encryption gap is a blocker.** A cipher primitive without token persistence integration does not protect refresh tokens. For a Gmail restricted-scope app, this is not acceptable.
- **Single global AES-GCM key is accepted by decision, but operational controls are thin.** There is no rotation task, no key validation at startup beyond length, no secret provenance logging without secret value, and no production profile enforcement.
- **`Sensitive<T>` wrapper is not enough.** FND-03 says prevent bodies/prompts/completions from reaching logs; current plans rely heavily on naming conventions and an incomplete ArchUnit rule.
- **Log scrub runtime test uses sensitive identifiers in response bodies.** `/me` legitimately returns email and subject-derived state may appear in normal access logs depending configuration; the test’s expectations may conflict with actual useful audit/log behavior.
- **DISCONNECTED state is only lazy and tied to refresh failure.** That matches context, but Phase 1 must ensure the next relevant request actually performs a refresh or wrapped Google call. Current controllers may not.
- **Account deletion does not clearly revoke Google token.** AUTH-03 says revoke Gmail access and delete account/data. Plan 07 deletes local rows but does not call Google token revocation.
- **CASA claims overstate future controls.** Docs mention Phase 2C/Phase 4 controls not yet implemented; submission materials should clearly distinguish implemented vs planned controls.
- **`gmail.modify` in Phase 1 may be hard to justify if no Gmail write/read APIs are used yet.** The rationale exists, but actual app behavior is still onboarding-only.

---

## 5. Testing and Validation Gaps

- **Too many tests are not executable as written.** Placeholder comments inside test bodies make plans non-actionable.
- **No real OAuth integration or WireMock token flow is planned.** Phase 1 success needs sign-in/connect/revoke behavior; current tests mostly mint sessions manually.
- **No Redis-backed Spring Session integration test.** Test session minting depends on Spring Session but likely won’t work without Redis Testcontainer or test profile fallback.
- **No test proves refresh tokens are encrypted in DB.** Cipher tests are insufficient.
- **No test proves Gmail connection row is created after second-leg consent.**
- **No test proves `invalid_grant` occurs “on next request.”** Publishing an event manually does not prove the request path detects it.
- **No negative CSRF tests.** If CSRF is enabled, verify mutating endpoints reject missing tokens and accept valid tokens.
- **OpenAPI schema test may fail due authentication.** `/v3/api-docs` is permitted, but `PostgresContainerTest` and security context setup may still drag in missing OAuth/Redis config.
- **Frontend build against placeholder schema is misleading.** Task 1/2 build can pass without real API types; type correctness only arrives in Task 3.
- **No accessibility or responsive validation despite detailed UI spec.** At least static lint/build is included, but no Playwright or keyboard checks are planned.

---

## 6. Plan-by-Plan Notes for All 9 Plans

### Plan 01 — Gradle Multi-Project Scaffold

- Good: Establishes module layout, Java 25, Boot 4, version catalog, API/worker shells.
- Risk: Uses `buildSrc` and snapshot repositories early; acceptable, but snapshot Modulith pin weakens reproducibility.
- Risk: Applies Spring Boot conventions to `backend/core`; if not carefully configured, a library module may get unwanted Boot behavior.
- Missing: Gradle wrapper creation is conditional; for a greenfield repo it should be explicit.
- Missing: No dependency locking or verification metadata despite snapshot usage.
- Must fix: Clarify whether `backend/core` applies the Boot plugin or only dependency management.

### Plan 02 — Tenant Isolation Primitives

- Good: Uses `ScopedValue`, `CurrentTenantIdentifierResolver`, ArchUnit bans, and `TenantAwareTaskScope`.
- Blocker: `CurrentTenantIdentifierResolver<String>` returns `String`, while entities use `UUID tenantId`; Hibernate tenant ID type alignment must be verified.
- Blocker: `StructuredTaskScope.open()` and `scope.stream()` API assumptions may be wrong for Java 25; plan acknowledges this but still gives exact tests.
- Risk: `@ApplicationModule(allowedDependencies = {})` on `tenant` and `persistence` may not reflect real package dependencies once entities import Hibernate/Spring/JPA types.
- Risk: ArchUnit `CompletableFuture.supplyAsync` signature only catches one overload.
- Missing: No synthetic violation tests despite plan claiming they exist.
- Must fix: Make Java 25 structured concurrency API concrete and compilable before execution.

### Plan 03 — Log Safety Contract

- Good: Adds `Sensitive<T>`, Jackson serializer, Logback config, and ArchUnit naming rule.
- Blocker: The TurboFilter approach is flawed. It computes `rendered`, then only mutates params whose `toString()` contains `Sensitive(`; but `Sensitive.toString()` returns `***REDACTED***`, so normal sensitive values never trigger `scrubbed=true`.
- Blocker: MDC is not “cleaned up by logback after the event.” MDC is thread-bound and must be cleared explicitly or scoped.
- Blocker: The ArchUnit logger rule admits it cannot reliably detect `Logger.info("{}", sensitive)` because SLF4J uses `Object...`. This does not satisfy FND-04 as stated.
- Risk: `@JsonComponent` extending `SimpleModule` may not be discovered the way intended in Jackson 3 / Boot 4.
- Must fix: Replace with a reliable logging strategy: custom Logstash provider/converter, appender-level redaction, or bytecode/static analysis that actually detects logger arguments.

### Plan 04 — Liquibase + JPA Entities

- Good: Establishes baseline schema, unique Gmail connection per tenant, onboarding state.
- Blocker: `gmail_connections.tenant_id` lacks explicit foreign key to `tenants`. Same for `onboarding_selections.tenant_id`.
- Risk: `includeAll` ordering can be fragile unless filenames are strictly ordered; here they are, but execution should verify.
- Risk: `users.google_subject` unique globally may be okay for Google accounts, but multi-provider future would need provider ID too.
- Risk: Hibernate `@TenantId` may auto-populate tenant ID, but constructors also set it; interaction with UUID resolver must be verified.
- Missing: No cascade FK strategy; account deletion is manual. That is acceptable but must be tested thoroughly.
- Must fix: Fully specify entity constructors/setters used by later tests.

### Plan 05 — Spring Security OAuth / Session / Tenant Binding

- Good: Covers OAuth registrations, session cookie, CSRF, tenant filter, invalid_grant event, leak test.
- Blocker: TenantBindingFilter performs a DB lookup by OIDC subject on every request, contradicting D-B1 (“No DB lookup occurs in the filter”).
- Blocker: `GoogleOAuthSuccessHandler` writes tenant/user inside `ScopedValue.where(...).call(...)`, but `ScopedValue.call` throws checked exceptions; snippet may not compile as written.
- Blocker: `GmailScopeRequestResolver` uses `r.getClientRegistration()` on `OAuth2AuthorizationRequest`; that API likely does not exist.
- Blocker: No actual Gmail OAuth callback handler persists `gmail_connections`, refresh token, scopes, or onboarding state.
- Risk: `DisconnectDetectingRefreshTokenClient` is created but not wired into `OAuth2AuthorizedClientManager`.
- Risk: Test session minting with Redis-backed Spring Session lacks Redis test infrastructure.
- Must fix: Redesign around a real custom principal/session attribute and encrypted authorized-client persistence.

### Plan 06 — Refresh Token Cipher

- Good: AES-GCM envelope, 12-byte nonce, version prefix, tenant AAD, nonce uniqueness tests.
- Blocker: It explicitly defers OAuth token save/load integration. That leaves Phase 1’s Gmail OAuth unsafe/incomplete.
- Risk: Config expression `${REFRESH_TOKEN_KEY_BASE64:${sm://...:}}` may not resolve as intended with Secret Manager property source.
- Risk: Tests only check first/last version bytes weakly; should parse `ByteBuffer.getInt()`.
- Missing: No startup test verifies Spring config creates the bean with env fallback.
- Must fix: Wire cipher into refresh-token persistence before Phase 1 execution is considered sufficient.

### Plan 07 — API Controllers + OpenAPI

- Good: Provides concrete Phase 1 API surface, typed DTOs, delete cascade, onboarding state.
- Blocker: `/tenant/connect-gmail` is POST + redirect, but frontend uses plain HTML form without CSRF token. Likely 403.
- Blocker: Account deletion deletes local data but does not revoke Google OAuth grant.
- Risk: `POST /tenant/disconnect` marks `DISCONNECTED` but does not revoke token or clear encrypted refresh token.
- Risk: `OnboardingController.selectTemplate` does not handle duplicate template selection despite unique constraint.
- Risk: OpenAPI “exactly Phase 1 operations” conflicts with Spring Security OAuth endpoints not controller-owned.
- Missing: No `GMAIL_CONNECTED` transition in these controllers.
- Must fix: Define proper revoke/disconnect semantics and align endpoint methods with CSRF/OAuth redirect reality.

### Plan 08 — Next.js Frontend + Typed Client

- Good: UI scope is disciplined and aligned with Phase 1; includes login/onboarding/settings and privacy copy.
- Blocker: Uses Context7 note but execution plan itself relies on current exact versions; before implementation, docs should be fetched for Next.js/openapi tooling per repo instruction.
- Blocker: `generate-api.ts` paths are wrong when run from `apps/web`: it writes `apps/web/openapi/...` relative to `apps/web`, producing `apps/web/apps/web/openapi/...`.
- Blocker: Form POSTs to backend endpoints do not include CSRF token.
- Risk: `ConnectionHealthBadge` maps `DISCONNECTED` to destructive red, but UI spec says amber warning.
- Risk: `window.location.href = ${apiBase}/tenant/connect-gmail` performs GET, but backend only defines POST.
- Risk: `middleware.ts` only checks cookie presence, not validity; acceptable for Phase 1 UX, but not security.
- Risk: `next lint` may no longer exist in latest Next.js workflows; verify with docs.
- Must fix: Correct codegen paths and align connect/reconnect calls with backend method/CSRF strategy.

### Plan 09 — Log Scrub Runtime Test + CASA

- Good: Recognizes runtime log verification and external CASA as phase gates.
- Blocker: Synthetic log test expects `scrubbed=true` to appear “on a real request path,” but none of the real request handlers intentionally logs a `Sensitive(` marker. This will likely fail or incentivize fake logging.
- Blocker: Test seeds raw refresh token bytes directly into DB, contradicting encryption posture; okay as adversarial sentinel only if clearly isolated, but it conflicts with intended entity semantics.
- Blocker: GmailConnectionEntity setup is incomplete again.
- Risk: CASA docs claim future controls as if implemented.
- Risk: Plan is `autonomous: false` with blocking human checkpoint. That is honest, but execution cannot complete Phase 1 autonomously.
- Must fix: Separate “draft CASA package” from “filed submission” and define what counts as Phase 1 done if human filing is pending.

---

## 7. Must-Fix Items Before Executing

- Wire AES-GCM cipher into OAuth authorized-client persistence; do not defer refresh-token encryption.
- Implement the Gmail second-leg callback persistence: create/update `gmail_connections`, store encrypted refresh token, granted scopes, status, and advance onboarding to `GMAIL_CONNECTED`.
- Replace TenantBindingFilter DB lookup with session/principal-carried tenant ID, or explicitly update the Phase 1 context decision.
- Fix log-safety design so FND-03/FND-04 are actually enforceable; current TurboFilter and ArchUnit logger rule are insufficient.
- Add Redis Testcontainer or test-profile session repository so Spring Session tests are real and repeatable.
- Remove all placeholder comments from tests and plans; every test must instantiate valid entities.
- Align `/tenant/connect-gmail` method, CSRF handling, frontend calls, and OAuth redirect mechanics.
- Ensure disconnect/delete revoke Google tokens, not only local DB rows.
- Correct `apps/web/scripts/generate-api.ts` relative paths.
- Reconcile CASA completion criteria: draft package vs actual submission ID.

---

## 8. Nice-to-Have Improvements

- Add dependency locking or Gradle verification metadata, especially because Spring Modulith uses a snapshot.
- Add a small `backend/test-support` module instead of duplicating Testcontainers/session helpers across modules.
- Use database FK constraints with `onDelete` behavior where appropriate, even if application-level delete remains.
- Add a production profile enforcing secure cookies and disabling Swagger UI.
- Add a minimal Playwright smoke test for `/login`, `/onboarding`, `/settings` once the UI scaffold exists.
- Add OpenAPI diff/check task so generated frontend types fail CI when stale.
- Add a security decision record for the global token encryption key trade-off and future rotation trigger.
- Consider using `GET /tenant/connect-gmail` for OAuth initiation to avoid CSRF/form awkwardness, while keeping state-changing operations POST/DELETE.


---

## OpenCode Review

**1) Overall Verdict: BLOCK**
- The plan set is directionally strong and well-scoped, but it is not execution-safe yet: there are multiple implementation contradictions and likely compile/runtime failures that would prevent meeting Phase 1 success criteria as written.
- Biggest blocker: the OAuth second-leg Gmail connect flow is not fully wired end-to-end (state transition + connection persistence + callback handling), so AUTH-02/AUTH-06 and Roadmap Success Criterion #1 are at high risk.
- Second blocker: several safety controls are specified in a way that likely won’t enforce what they claim (notably ArchUnit log-argument checks and log scrub path realism).

**2) Top Risks Before Execution**
- **OAuth connect flow incompleteness:** no reliable path from `connect-gmail` callback to persisted `gmail_connections` + onboarding step update to `GMAIL_CONNECTED`.
- **False sense of safety from ArchUnit/log rules:** current rule formulations may pass while sensitive values still leak in real code paths.
- **Compile/API mismatch risk:** several snippets appear incompatible with current Spring Security/OpenAPI objects and Java checked-exception boundaries.
- **Tenant typing mismatch risk:** `TenantContext` is `String`, but entity `@TenantId` fields are `UUID`; resolver/type behavior is unclear and may break discriminator tenancy.
- **Frontend-backend contract fragility:** forms and redirects likely conflict with CSRF + method expectations; onboarding can deadlock in `SIGNED_IN`.
- **Validation overconfidence:** tests exist, but some are synthetic or optimistic and won’t prove the exact roadmap criterion they claim.

**3) Missing Tasks or Unclear Dependencies**
- Missing explicit task to persist/update `gmail_connections` on successful second OAuth leg (including `CONNECTED`, `google_email`, scopes, timestamps).
- Missing explicit callback ownership: `/oauth2/callback/gmail` appears delegated to Spring, but no task ensures post-callback domain state mutation.
- Missing dependency clarity between Plan 05 (OAuth/security) and Plan 07 controllers for onboarding progression; progression depends on OAuth side effects not yet guaranteed.
- No concrete task for one-account enforcement behavior at API boundary (only DB uniqueness). Need clear user-facing conflict handling path.
- Insufficiently specified migration path for modulith snapshot risk (Plan 01 pins snapshot; no rollback/fallback task if snapshot resolution fails).
- Missing explicit test that `/tenant/connect-gmail` invocation from UI path passes CSRF and method semantics correctly.

**4) Security/Privacy/Compliance Concerns**
- `no_sensitive_in_logger` ArchUnit rule is acknowledged as limited; that undermines FND-04 claims.
- TurboFilter strategy is brittle; mutation of params/MDC behavior may not reliably produce sanitized structured output across appenders.
- Plan 09 log-scrub test still risks proving logger plumbing more than end-to-end sensitive-data absence across realistic payloads.
- OAuth callback and CSRF exemptions are broad enough to warrant tighter matcher review.
- Secret loading config (`sm://` fallback chaining) is risky/unclear for production hardening; failure modes not clearly tested.
- CASA docs are decent, but technical claims depend on controls above actually being enforceable, which is currently shaky.

**5) Testing and Validation Gaps**
- No hard E2E test proving: sign-in -> connect Gmail -> onboarding step transitions exactly as required -> disconnect -> delete cascade.
- No explicit test proving second OAuth leg includes `include_granted_scopes=true` behavior against realistic auth request construction.
- Multi-tenant leak test is good, but it should include negative controls (intentional violation class) in CI to prove rules fail when expected.
- OpenAPI test checks presence, not strict surface control (unexpected endpoints could still leak into spec).
- Frontend verification lacks Playwright/browser-level flow for CSRF, cookie, and redirect behavior.
- No test that revocation (`invalid_grant`) becomes visible in `/tenant/status` immediately from user perspective with real request sequencing.

**6) Plan-by-Plan Notes (All 9)**
- **01-01 (Scaffold):** Good structure and version pinning. Risk: heavy reliance on modulith snapshot without a clear contingency execution path.
- **01-02 (Tenant isolation):** Strong intent. Concern: `TenantContext`/`@TenantId` type consistency and overbroad assumptions about `StructuredTaskScope` API shape.
- **01-03 (Log safety):** Conceptually right, practically weak. ArchUnit rule efficacy and TurboFilter behavior likely won’t enforce FND-03/FND-04 as claimed.
- **01-04 (Liquibase/entities):** Good baseline schema. Concern: tenant discriminator typing and Liquibase FK/constraint expression details may fail or drift.
- **01-05 (Security/OAuth):** Highest risk plan. Multiple likely API mismatches; DB lookup in tenant filter contradicts prior design; second-leg connect lifecycle incomplete.
- **01-06 (Crypto):** Strong envelope/AAD design. Concern: config wiring/fallback semantics need integration-level proof, not only unit tests.
- **01-07 (API/OpenAPI/controllers):** Good endpoint coverage. Concern: depends on unresolved OAuth state side effects; some flows assume entities are already in expected states.
- **01-08 (Web app):** Useful minimal UX scope. Concern: method/CSRF mismatches, redirect semantics, and onboarding deadlock risk due to backend gaps.
- **01-09 (CASA + runtime checks):** Correct to include human checkpoint. Concern: runtime log test may still not prove “zero sensitive leakage” under realistic traffic patterns.

**7) Must-Fix Items Before Executing**
- Add a concrete, testable task for second OAuth leg callback processing that persists `gmail_connections` and advances onboarding to `GMAIL_CONNECTED`.
- Resolve and validate Spring Security API compatibility in Plan 05 (authorization request resolver, callback URLs, success handler flow).
- Tighten FND-04 enforcement: replace/augment current ArchUnit logger rule with a mechanism that actually inspects sensitive argument flow or enforce no-logging wrappers at API boundaries.
- Rework log scrub verification to assert against captured structured logs from realistic endpoint traffic and known sensitive fixtures.
- Ensure CSRF + HTTP method alignment across `/tenant/connect-gmail`, `/tenant/disconnect`, `/onboarding/*`, and frontend calls/forms.
- Add strict E2E test for Roadmap Success Criterion #1 across backend + UI (or backend integration plus frontend browser test).

**8) Nice-to-Have Improvements**
- Add a formal “unexpected OpenAPI path” assertion (allow-list rather than presence-only checks).
- Add contract tests for cookie attributes in prod profile separately from test profile.
- Add fallback plan for modulith snapshot instability (feature-flagged verification or temporary reduced enforcement).
- Add observability assertions for scrub event counts and revocation event metrics.
- Introduce a small threat-model trace per plan showing requirement-to-control-to-test mapping consistency.




