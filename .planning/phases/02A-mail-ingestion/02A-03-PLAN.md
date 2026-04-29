---
phase: 02A-mail-ingestion
plan: "03"
type: execute
wave: 2
depends_on:
  - "02A-01"
files_modified:
  - backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java
  - backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java
  - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java
  - backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java
  - backend/api/src/main/java/com/zeromail/api/controllers/TriagePauseController.java
  - backend/api/src/main/java/com/zeromail/api/dto/gmail/PubSubPushEnvelope.java
  - backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailNotification.java
  - backend/api/src/main/java/com/zeromail/api/dto/gmail/FlexibleLongDeserializer.java
  - backend/api/src/main/java/com/zeromail/api/dto/tenant/TriagePauseRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/tenant/TriagePauseResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java
  - backend/api/src/main/resources/application.yml
  - backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java
  - backend/api/src/test/java/com/zeromail/api/security/TestSessionSupport.java
  - backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java
  - backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java
  - backend/core/src/main/java/com/zeromail/core/gmail/service/IngestResult.java
  - backend/core/src/main/java/com/zeromail/core/gmail/service/PubSubIngestionService.java
  - backend/core/src/main/java/com/zeromail/core/gmail/model/GmailConnectionProjection.java
  - backend/core/src/main/java/com/zeromail/core/tenant/service/TenantService.java
autonomous: true
requirements:
  - MAIL-01
  - MAIL-03
  - MAIL-04
  - MAIL-06

must_haves:
  truths:
    - "POST /internal/pubsub/gmail without Authorization header returns 401 (not 302 redirect)"
    - "POST with valid OIDC token inserts pubsub_delivery row and returns 200"
    - "POST with valid token but unknown emailAddress returns 200 and drops silently"
    - "Second POST with same messageId returns 200 with no duplicate DB row"
    - "PUT /tenant/triage-pause with paused=true sets tenants.triage_paused=true"
    - "GET /me returns triagePaused boolean and gmailConnectionStatus.ingestionHealth"
    - "PubSubSecurityConfig @Order(1) is active under the test profile and intercepts /internal/pubsub/** BEFORE user OAuth chain"
    - "PubSubOidcAuthFilter is not globally servlet-registered; it is added only through PubSubSecurityConfig and has a shouldNotFilter path guard for non-/internal/pubsub/** requests"
    - "Test-profile /me and /tenant/triage-pause tests import TestSessionSupport, send X-Test-Subject/X-Test-Email headers, and exercise authenticated TenantContext binding instead of bypassing auth"
    - "TestSessionSupport does not match /internal/pubsub/**, so PubSubSecurityConfig remains the only test-profile chain for Pub/Sub OIDC integration tests"
    - "GmailPubSubController does NOT inject any JPA repository directly — all persistence routed via PubSubIngestionService"
    - "PubSubIngestionService performs unscoped JdbcTemplate tenant lookup before binding TenantContext, then opens a tenant-bound TransactionTemplate for delivery INSERT"
    - "PubSubIngestionService uses PubSubDeliveryRepository.insertPendingIfAbsent; duplicate Pub/Sub messages are detected by row count, not caught DataIntegrityViolationException"
    - "TenantService.setTriagePaused is implemented in this plan so Plan 03 has no same-wave dependency on Plan 02"
  artifacts:
    - path: "backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java"
      provides: "OncePerRequestFilter that verifies Google OIDC token"
      contains: "TokenVerifier"
    - path: "backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java"
      provides: "SecurityFilterChain @Order(1) for /internal/pubsub/** active in test profile"
      contains: "securityMatcher"
    - path: "backend/core/src/main/java/com/zeromail/core/gmail/service/PubSubIngestionService.java"
      provides: "@Service — unscoped lookup + tenant-bound TransactionTemplate delivery INSERT"
      contains: "ingestPushEnvelope"
    - path: "backend/core/src/main/java/com/zeromail/core/gmail/service/IngestResult.java"
      provides: "Enum result contract returned by PubSubIngestionService and mapped by GmailPubSubController"
      contains: "UNKNOWN_EMAIL"
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java"
      provides: "POST /internal/pubsub/gmail ack-fast receiver"
      contains: "/internal/pubsub/gmail"
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/TriagePauseController.java"
      provides: "PUT /tenant/triage-pause"
      contains: "/tenant/triage-pause"
    - path: "backend/api/src/test/java/com/zeromail/api/security/TestSessionSupport.java"
      provides: "Test-only authenticated user chain for non-Pub/Sub endpoints under @ActiveProfiles(\"test\")"
      contains: "X-Test-Subject"
  key_links:
    - from: "PubSubSecurityConfig"
      to: "PubSubOidcAuthFilter"
      via: "addFilterBefore in SecurityFilterChain"
      pattern: "addFilterBefore.*oidcFilter"
    - from: "GmailPubSubController"
      to: "PubSubIngestionService"
      via: "service.ingestPushEnvelope() call — no repo injection in controller"
      pattern: "ingestionService\\.ingestPushEnvelope"
    - from: "PubSubIngestionService"
      to: "PubSubDeliveryRepository"
      via: "service-owned TransactionTemplate — CLAUDE.md §1 compliant"
      pattern: "insertPendingIfAbsent"
    - from: "SecurityConfig"
      to: "@Order(2)"
      via: "@Order annotation on SecurityConfig class or bean method"
      pattern: "@Order\\(2\\)"
---

<objective>
Implement the API-layer components that close MAIL-03 (OIDC verification) and MAIL-01 (push receiver) and provide the triage-pause endpoint. This plan runs in Wave 2 parallel with Plan 02 (worker schedulers).

Purpose: The push receiver + OIDC filter is the Phase 01.5 D-D5 deferred ceremony — this plan delivers it. The triage-pause controller delivers MAIL-06 API surface.

Output: PubSubOidcAuthFilter, PubSubSecurityConfig, PubSubIngestionService (new — owns all persistence), GmailPubSubController (thin: parse → service call → map result), TriagePauseController, DTOs, MeResponse extension, SecurityConfig @Order(2).

CLAUDE.md §1 compliance note: GmailPubSubController MUST NOT inject JPA repositories. All tenant lookup + pubsub_delivery INSERT logic lives in PubSubIngestionService in backend/core; it uses unscoped JdbcTemplate lookup plus tenant-bound TransactionTemplate, not controller-owned persistence.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/phases/02A-mail-ingestion/02A-CONTEXT.md
@.planning/phases/02A-mail-ingestion/02A-RESEARCH.md
@.planning/phases/02A-mail-ingestion/02A-PATTERNS.md

<interfaces>
<!-- Existing API security classes to extend/follow -->
From backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java:
```java
@Configuration
@Profile("!test")
public class SecurityConfig {
    @Bean
    SecurityFilterChain chain(HttpSecurity http, TenantBindingFilter tenantFilter, ...) throws Exception {
        http.cors(Customizer.withDefaults())
            .authorizeHttpRequests(a -> a
                .requestMatchers("/login", "/actuator/health", "/v3/api-docs/**",
                                 "/swagger-ui/**", "/login/oauth2/**", "/oauth2/**").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(o -> o.successHandler(...).failureHandler(...)
                              .authorizationEndpoint(a -> a.authorizationRequestResolver(...)))
            .csrf(...)
            .addFilterAfter(tenantFilter, AuthorizationFilter.class);
        return http.build();
    }
}
```

From backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java:
```java
@Component
public class TenantBindingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException { ... }
}
```

From backend/api/src/main/java/com/zeromail/api/controllers/DisconnectController.java:
```java
@RestController
public class DisconnectController {
    @PostMapping("/tenant/disconnect")
    public void disconnect() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        connectionService.disconnect(tenantId);
    }
}
```

From backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java:
```java
public record MeResponse(String userId, String tenantId, String email,
        String onboardingStep, String preferredLanguage) {
    public static MeResponse from(CurrentUserProjection user) { return new MeResponse(...); }
}
```

GmailConnectionRepository.findByTenantId(UUID) — returns Optional<GmailConnectionEntity>

PubSubDeliveryRepository — available from Plan 01; has claimPendingBatch, insertPendingIfAbsent, updateStatus, and releaseForRetry

TenantService.setTriagePaused(UUID, boolean) — implemented in this plan before TriagePauseController uses it
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: PubSubIngestionService + OIDC filter + dual SecurityFilterChain + thin controllers</name>
  <files>
    backend/core/src/main/java/com/zeromail/core/gmail/service/IngestResult.java,
    backend/core/src/main/java/com/zeromail/core/gmail/service/PubSubIngestionService.java,
    backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java,
    backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java,
    backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java,
    backend/api/src/test/java/com/zeromail/api/security/TestSessionSupport.java,
    backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java,
    backend/api/src/main/java/com/zeromail/api/controllers/TriagePauseController.java,
    backend/api/src/main/java/com/zeromail/api/dto/gmail/PubSubPushEnvelope.java,
    backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailNotification.java,
    backend/api/src/main/java/com/zeromail/api/dto/gmail/FlexibleLongDeserializer.java,
    backend/api/src/main/java/com/zeromail/api/dto/tenant/TriagePauseRequest.java,
    backend/api/src/main/java/com/zeromail/api/dto/tenant/TriagePauseResponse.java,
    backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java,
    backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java,
    backend/core/src/main/java/com/zeromail/core/tenant/service/TenantService.java,
    backend/api/src/main/resources/application.yml
  </files>

  <read_first>
    - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java (full file — READ BEFORE editing)
    - backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java (OncePerRequestFilter pattern)
    - backend/api/src/test/java/com/zeromail/api/security/TestSessionSupport.java (test-profile auth + TenantContext binding pattern; update in this task)
    - backend/api/src/test/java/com/zeromail/api/controllers/MeLanguageIntegrationTest.java (existing @Import(TestSessionSupport.class) + RestClient header pattern)
    - backend/api/src/main/java/com/zeromail/api/controllers/DisconnectController.java (thin controller pattern)
    - backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java (record DTO pattern)
    - backend/core/src/main/java/com/zeromail/core/tenant/service/TenantService.java (full file — add setTriagePaused here)
    - backend/api/src/main/resources/application.yml (full file — READ BEFORE editing to add env vars)
    - .planning/phases/02A-mail-ingestion/02A-RESEARCH.md (Pattern 2 TokenVerifier, Pattern 3 PubSub payload, Pattern 1 dual SecurityFilterChain, P-01 pitfall order)
    - .planning/phases/02A-mail-ingestion/02A-PATTERNS.md (PubSubOidcAuthFilter, PubSubSecurityConfig, GmailPubSubController, TriagePauseController adaptations)
    - CLAUDE.md (Conventions §1: thin controllers, §4: privacy logging format, Lombok-free, records for DTOs)
  </read_first>

  <action>
**CLAUDE.md §1 compliance — critical design constraint:**
GmailPubSubController must NOT inject GmailConnectionRepository or PubSubDeliveryRepository.
All persistence (tenant lookup + pubsub_delivery INSERT) lives in PubSubIngestionService.
The controller's only job: parse the envelope → call service → map IngestResult to HTTP response.

***

**Step 1 — Create `IngestResult.java`** — package `com.zeromail.core.gmail.service`:

```java
package com.zeromail.core.gmail.service;

/**
 * Return value from PubSubIngestionService.ingestPushEnvelope.
 * Controller maps these to HTTP responses — no business logic in controller.
 */
public enum IngestResult {
    /** emailAddress not found in gmail_connections — silently drop */
    UNKNOWN_EMAIL,
    /** messageId already exists in pubsub_delivery — idempotent dedup */
    DUPLICATE,
    /** Row inserted; worker will process asynchronously */
    ACCEPTED
}
```

**Step 2 — Create `PubSubIngestionService.java`** — package `com.zeromail.core.gmail.service`:

```java
package com.zeromail.core.gmail.service;

import com.zeromail.core.gmail.persistence.PubSubDeliveryRepository;
import com.zeromail.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns all persistence for the ack-fast PubSub push path.
 * CLAUDE.md §1: controllers never inject repositories — this service is the
 * boundary for unscoped tenant lookup + tenant-bound pubsub_delivery INSERT.
 */
@Service
public class PubSubIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PubSubIngestionService.class);

    private final JdbcTemplate jdbc;
    private final PubSubDeliveryRepository deliveryRepository;
    private final TransactionTemplate tx;

    public PubSubIngestionService(JdbcTemplate jdbc,
                                  PubSubDeliveryRepository deliveryRepository,
                                  PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.deliveryRepository = deliveryRepository;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Ack-fast ingestion.
     *
     * Tenant lookup is intentionally unscoped native SQL because Gmail email lookup
     * happens before a tenant is known. The tenant-bound INSERT transaction opens only
     * after TenantContext is bound, preserving the Hibernate tenant invariant.
     *
     * @param emailAddress   from the decoded Pub/Sub notification (NOT logged — privacy)
     * @param pubsubMessageId Pub/Sub message ID (dedup key)
     * @param historyId      Gmail historyId from the notification
     * @param rawPayload     full envelope payload serialized as JSON string (stored for replay)
     * @return IngestResult — caller maps to HTTP response, no business logic needed
     */
    public IngestResult ingestPushEnvelope(String emailAddress,
                                           String pubsubMessageId,
                                           long historyId,
                                           String rawPayload) {
        List<UUID> tenantIds = jdbc.query(
            """
            SELECT tenant_id
            FROM gmail_connections
            WHERE LOWER(google_email) = ?
              AND status = 'CONNECTED'
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getObject("tenant_id", UUID.class),
            emailAddress.toLowerCase()
        );

        if (tenantIds.isEmpty()) {
            log.info("event=pubsub_unknown_email_dropped");  // email NOT logged — privacy safe
            return IngestResult.UNKNOWN_EMAIL;
        }

        UUID tenantId = tenantIds.getFirst();

        AtomicReference<IngestResult> result = new AtomicReference<>();
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() ->
            result.set(tx.execute(_ -> {
                int inserted = deliveryRepository.insertPendingIfAbsent(
                    UUID.randomUUID(),
                    tenantId,
                    pubsubMessageId,
                    historyId,
                    rawPayload
                );
                if (inserted == 0) {
                    log.info("event=pubsub_duplicate_delivery_dropped tenantId={}", tenantId);
                    return IngestResult.DUPLICATE;
                }
                log.info("event=pubsub_delivery_accepted tenantId={}", tenantId);
                return IngestResult.ACCEPTED;
            }))
        );
        return result.get();
    }
}
```

Do NOT add `GmailConnectionRepository.findByGoogleEmailLower(...)` for this lookup. `GmailConnectionEntity` is tenant-owned; querying it through JPA before `TenantContext` is bound can return nothing or bind the wrong tenant state. The native `JdbcTemplate` lookup is intentionally unscoped and only returns `tenant_id`; all tenant-owned inserts happen after `ScopedValue.where(TenantContext.TENANT, ...)` and inside `TransactionTemplate`.

***

**Step 3 — `PubSubOidcAuthFilter.java`** — package `com.zeromail.api.security`. Exact shape from RESEARCH.md Pattern 2:

```java
public class PubSubOidcAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PubSubOidcAuthFilter.class);

    private final TokenVerifier tokenVerifier;
    private final String expectedEmail;

    public PubSubOidcAuthFilter(
            String audience,
            String saEmail,
            String certsUrl) {
        this.expectedEmail = saEmail;
        this.tokenVerifier = TokenVerifier.newBuilder()
                .setAudience(audience)
                .setIssuer("https://accounts.google.com")
                .setCertificatesLocation(certsUrl)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/internal/pubsub/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("event=pubsub_oidc_missing_token");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String token = authHeader.substring(7);
        try {
            JsonWebSignature jws = tokenVerifier.verify(token);
            String email = (String) jws.getPayload().get("email");
            if (!expectedEmail.equalsIgnoreCase(email)) {
                log.warn("event=pubsub_oidc_wrong_email");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            request.setAttribute("pubsub.verified.email", email);
            chain.doFilter(request, response);
        } catch (TokenVerifier.VerificationException e) {
            log.warn("event=pubsub_oidc_verification_failed");  // No token content — privacy safe
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
```

Import: `com.google.auth.oauth2.TokenVerifier`, `com.google.api.client.json.webtoken.JsonWebSignature`.

Do NOT annotate this class with `@Component`. A servlet `Filter` bean can be auto-registered globally by Spring Boot, outside the Spring Security chain. `PubSubOidcAuthFilter` must only be created by `PubSubSecurityConfig` and added to the `/internal/pubsub/**` security chain. Keep the `shouldNotFilter` guard as a defense-in-depth check so accidental global registration cannot make `/me` or `/tenant/triage-pause` return Pub/Sub 401s.

***

**Step 4 — `PubSubSecurityConfig.java`** — package `com.zeromail.api.security`. New `@Configuration @Order(1)`. Do NOT add `@Profile("!test")`: API integration tests run under `@ActiveProfiles("test")` and must still verify that missing/invalid Pub/Sub OIDC requests return 401 before business logic.

```java
@Configuration
@Order(1)
public class PubSubSecurityConfig {

    @Bean
    PubSubOidcAuthFilter pubSubOidcAuthFilter(
            @Value("${pubsub.push-audience-url}") String audience,
            @Value("${pubsub.sa-principal-email}") String saEmail,
            @Value("${pubsub.oidc-certificates-url:https://www.googleapis.com/oauth2/v3/certs}") String certsUrl) {
        return new PubSubOidcAuthFilter(audience, saEmail, certsUrl);
    }

    @Bean
    FilterRegistrationBean<PubSubOidcAuthFilter> pubSubOidcAuthFilterRegistration(PubSubOidcAuthFilter filter) {
        FilterRegistrationBean<PubSubOidcAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain pubSubFilterChain(HttpSecurity http,
                                          PubSubOidcAuthFilter oidcFilter) throws Exception {
        http
            .securityMatcher("/internal/pubsub/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .addFilterBefore(oidcFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

Import `org.springframework.beans.factory.annotation.Value` and `org.springframework.boot.web.servlet.FilterRegistrationBean`. The disabled `FilterRegistrationBean` is mandatory: the filter is a bean so it can be injected into the Spring Security chain, but it must not be registered as a container-wide servlet filter.

***

**Step 5 — `SecurityConfig.java`** — READ the current file. Add `@Order(2)` to the existing user-session SecurityConfig class declaration. Keeping its existing `@Profile("!test")` is acceptable because the Pub/Sub machine-to-machine chain is now in `PubSubSecurityConfig` and active in tests:
```java
@Configuration
@Order(2)
@Profile("!test")
public class SecurityConfig { ... }
```
No other changes. Import `org.springframework.core.annotation.Order`.

***

**Step 5.5 — `TestSessionSupport.java` test-profile user auth chain** — READ the current file and update it so `/me` and `/tenant/triage-pause` tests exercise authenticated, tenant-bound requests under `@ActiveProfiles("test")` while Pub/Sub tests still use the real Pub/Sub OIDC chain.

Required shape:
- Keep the existing header contract: `X-Test-Subject` and `X-Test-Email`.
- The test auth filter must construct an `OAuth2AuthenticationToken`, set `SecurityContextHolder`, look up the seeded `UserEntity` by Google subject, and wrap `chain.doFilter` with `ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(...)`.
- If both test headers are present but no user row exists, return `401` and do not call the controller. This proves tenant binding is not optional.
- The contributed `SecurityFilterChain` must NOT match `/internal/pubsub/**`; use a negated matcher or equivalent so `PubSubSecurityConfig @Order(1)` is the only chain responsible for Pub/Sub integration tests.
- Set the test chain order to `@Order(2)` or another value after PubSub's `@Order(1)`, not `Ordered.HIGHEST_PRECEDENCE`.
- For non-Pub/Sub protected endpoints, disable CSRF for tests, keep stateless session mode, and require authentication with `.authorizeHttpRequests(a -> a.anyRequest().authenticated())`. Missing test headers on `/me` and `/tenant/triage-pause` must return `401` (or Spring Security's configured unauthenticated response), not invoke the controller with an empty `TenantContext`.
- Do NOT remove `@Profile("!test")` from production `SecurityConfig`; the test-only user chain lives only in `TestSessionSupport` and is imported by endpoint tests that need authenticated users.
- Do NOT import `TestSessionSupport` into `PubSubOidcAuthFilterTest` or `GmailPubSubControllerIntegrationTest`; those tests must continue to prove `/internal/pubsub/**` rejects missing/invalid Google OIDC tokens through `PubSubSecurityConfig`.

This addresses review concern: "Test-profile auth/tenant binding for `/me` and `/tenant/triage-pause` remains underspecified."

***

**Step 6 — `PubSubPushEnvelope.java`** — package `com.zeromail.api.dto.gmail`. Nested record per RESEARCH.md Pattern 3:

```java
package com.zeromail.api.dto.gmail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PubSubPushEnvelope(PubSubMessage message, String subscription) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PubSubMessage(
        String data,
        String messageId,
        String publishTime,
        Map<String, String> attributes
    ) {}
}
```

**`GmailNotification.java`** — package `com.zeromail.api.dto.gmail`. Simple record for decoded data:
```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record GmailNotification(
    String emailAddress,
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = FlexibleLongDeserializer.class)
    Long historyId
) {}
```

**`FlexibleLongDeserializer.java`** — package `com.zeromail.api.dto.gmail`. Handles P-05 (historyId as string or number):
```java
public class FlexibleLongDeserializer extends JsonDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_STRING) {
            return Long.parseLong(p.getText().trim());
        }
        return p.getLongValue();
    }
}
```

***

**Step 7 — `GmailPubSubController.java`** — package `com.zeromail.api.controllers`. THIN controller: parse → service → map result.
DO NOT inject GmailConnectionRepository. DO NOT inject PubSubDeliveryRepository. CLAUDE.md §1 forbids it.

```java
@RestController
@io.swagger.v3.oas.annotations.Hidden  // Exclude from OpenAPI
public class GmailPubSubController {

    private static final Logger log = LoggerFactory.getLogger(GmailPubSubController.class);

    private final PubSubIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    public GmailPubSubController(PubSubIngestionService ingestionService,
                                  ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/internal/pubsub/gmail")
    public ResponseEntity<Void> receivePush(@RequestBody PubSubPushEnvelope envelope) {
        if (envelope.message() == null || envelope.message().data() == null) {
            return ResponseEntity.ok().build();  // Malformed: 200 to prevent redeliver
        }

        // Decode base64url data
        GmailNotification notification;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(envelope.message().data());
            notification = objectMapper.readValue(decoded, GmailNotification.class);
        } catch (Exception e) {
            log.warn("event=pubsub_payload_decode_failed");
            return ResponseEntity.ok().build();  // Corrupt payload: 200 prevents redeliver
        }

        if (notification.emailAddress() == null || notification.historyId() == null) {
            log.warn("event=pubsub_payload_missing_fields");
            return ResponseEntity.ok().build();
        }

        // Serialize full envelope for replay storage — done here before service call
        String rawPayload;
        try {
            rawPayload = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            rawPayload = "{}";
        }

        // Delegate ALL persistence to PubSubIngestionService — thin controller per CLAUDE.md §1
        IngestResult result = ingestionService.ingestPushEnvelope(
            notification.emailAddress(),
            envelope.message().messageId(),
            notification.historyId(),
            rawPayload
        );

        // All outcomes return 200 — prevents infinite Pub/Sub redeliver loop
        // IngestResult.UNKNOWN_EMAIL and DUPLICATE are drop-silently cases, not errors
        return ResponseEntity.ok().build();
    }
}
```

Privacy: NEVER log `notification.emailAddress()`. All privacy-safe logging is inside PubSubIngestionService.

***

**Step 8 — `TriagePauseRequest.java`** — package `com.zeromail.api.dto.tenant`:
```java
public record TriagePauseRequest(@NotNull Boolean paused) {}
```

**`TriagePauseResponse.java`** — package `com.zeromail.api.dto.tenant`:
```java
public record TriagePauseResponse(boolean paused) {}
```

**`TenantService.java`** — READ the current file. ADD this method before writing the controller:
```java
@Transactional
public void setTriagePaused(UUID tenantId, boolean paused) {
    tenants.findById(tenantId).ifPresent(t -> {
        t.setTriagePaused(paused);
        tenants.save(t);
    });
}
```
Privacy log goes in the controller, not the service.

**`TriagePauseController.java`** — package `com.zeromail.api.controllers`. Thin per DisconnectController pattern:

```java
@RestController
@Tag(name = "tenant")
public class TriagePauseController {

    private static final Logger log = LoggerFactory.getLogger(TriagePauseController.class);
    private final TenantService tenantService;

    public TriagePauseController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PutMapping("/tenant/triage-pause")
    public TriagePauseResponse setTriagePause(@RequestBody @Valid TriagePauseRequest req) {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        tenantService.setTriagePaused(tenantId, req.paused());
        log.info("event=triage_pause_toggled tenantId={} paused={}", tenantId, req.paused());
        return new TriagePauseResponse(req.paused());
    }
}
```

***

**Step 9 — `backend/api/src/main/resources/application.yml`** — READ the current file. ADD these env vars with `:?` fail-fast:
```yaml
pubsub:
  push-audience-url: ${PUBSUB_PUSH_AUDIENCE_URL:?PUBSUB_PUSH_AUDIENCE_URL env var is required}
  sa-principal-email: ${PUBSUB_SA_PRINCIPAL_EMAIL:?PUBSUB_SA_PRINCIPAL_EMAIL env var is required}
  oidc-certificates-url: ${PUBSUB_OIDC_CERTIFICATES_URL:https://www.googleapis.com/oauth2/v3/certs}
```

**Step 10 — Enable API Wave 0 scaffolds now covered by this task.** Remove class-level `@Disabled` from:
- `backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java`
- `backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java`

For `TriagePauseControllerTest.java`:
- Add `@ActiveProfiles("test")` and `@Import(TestSessionSupport.class)`.
- Seed a `TenantEntity` and matching `UserEntity` per test, saving the user inside `ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(...)`, matching `MeLanguageIntegrationTest`.
- Send authenticated requests with `.header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())` and `.header(TestSessionSupport.HEADER_EMAIL, seed.email())`.
- Assert the raw JSON response contains `"paused":true` / `"paused":false` and verify the database column with `JdbcTemplate` or `TenantRepository`.
- Add/keep a negative test `putTriagePause_missingTestAuth_returns401()` with no test headers. This proves the test profile does not bypass protected endpoint authorization.

For `PubSubIdempotencyTest.java`:
- Do NOT import `TestSessionSupport`.
- Continue using `MockGoogleOidcServer` and valid Pub/Sub OIDC bearer tokens; this endpoint is machine-authenticated, not user-session authenticated.
- Keep raw SQL/JdbcTemplate row-count assertions for `pubsub_delivery`.

Keep assertions raw HTTP/SQL if that is still simpler, but the tests must execute GREEN before this plan is complete.
  </action>

  <verify>
    <automated>./gradlew :backend:api:compileJava :backend:core:compileJava :backend:api:test --tests "*PubSubOidcAuthFilterTest*" --tests "*GmailPubSubControllerIntegrationTest*" --tests "*TriagePauseControllerTest*" --tests "*PubSubIdempotencyTest*" 2>&1 | grep -E "BUILD|PASSED|FAILED|SKIPPED|error:" | head -30</automated>
  </verify>

  <acceptance_criteria>
    - Plan 03 `files_modified` lists every helper/test file created or modified by this plan: `IngestResult.java`, `GmailNotification.java`, `FlexibleLongDeserializer.java`, `GmailConnectionProjection.java`, `PubSubOidcAuthFilterTest.java`, `TestSessionSupport.java`, `MeControllerTest.java`, `TriagePauseControllerTest.java`, and `PubSubIdempotencyTest.java`
    - `IngestResult.java` exists in `backend/core/src/main/java/com/zeromail/core/gmail/service/` and contains `UNKNOWN_EMAIL`, `DUPLICATE`, and `ACCEPTED`
    - `PubSubIngestionService.java` exists in `backend/core/src/main/java/com/zeromail/core/gmail/service/` and contains `JdbcTemplate`, `TransactionTemplate`, and `ScopedValue.where(TenantContext.TENANT`
    - `PubSubIngestionService.java` does NOT contain `@Transactional` on `ingestPushEnvelope`, `findByGoogleEmailLower`, `GmailConnectionRepository`, or `DataIntegrityViolationException`
    - `PubSubIngestionService.java` contains `insertPendingIfAbsent` and branches on `inserted == 0` for duplicates
    - `GmailNotification.java` exists and uses `FlexibleLongDeserializer` for `historyId`
    - `FlexibleLongDeserializer.java` exists and handles both `JsonToken.VALUE_STRING` and numeric long values
    - `GmailPubSubController.java` does NOT inject `GmailConnectionRepository` or `PubSubDeliveryRepository` — verify: `grep -n "GmailConnectionRepository\|PubSubDeliveryRepository" backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java` returns empty
    - `GmailPubSubController.java` contains `ingestionService.ingestPushEnvelope` call
    - `PubSubOidcAuthFilter.java` contains `TokenVerifier.newBuilder()`, `setCertificatesLocation`, and `event=pubsub_oidc_verification_failed`
    - `PubSubOidcAuthFilter.java` does NOT contain `@Component`
    - `PubSubOidcAuthFilter.java` contains `shouldNotFilter` and `startsWith("/internal/pubsub/")`
    - `PubSubOidcAuthFilter.java` does NOT contain any log line referencing the token content or email address
    - `PubSubSecurityConfig.java` contains `@Order(1)`, `securityMatcher("/internal/pubsub/**")`, a `PubSubOidcAuthFilter` `@Bean`, and `FilterRegistrationBean<PubSubOidcAuthFilter>` with `setEnabled(false)`
    - `PubSubSecurityConfig.java` does NOT contain `@Profile("!test")`; Pub/Sub security must be active under `@ActiveProfiles("test")`
    - `SecurityConfig.java` contains `@Order(2)` — check with `grep -n '@Order' backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java`
    - `TestSessionSupport.java` no longer contains `Ordered.HIGHEST_PRECEDENCE`, does not match `/internal/pubsub/**`, requires authenticated requests for non-Pub/Sub endpoints, and binds `TenantContext.TENANT` from seeded `UserEntity`
    - `TriagePauseController.java` contains `@PutMapping("/tenant/triage-pause")` and `event=triage_pause_toggled`
    - `TenantService.java` contains `setTriagePaused(UUID tenantId, boolean paused)`
    - `backend/api/src/main/resources/application.yml` contains `PUBSUB_PUSH_AUDIENCE_URL:?` and `PUBSUB_SA_PRINCIPAL_EMAIL:?`
    - `./gradlew :backend:api:compileJava :backend:core:compileJava` exits 0
    - `PubSubOidcAuthFilterTest` passes GREEN (6 test cases: valid + 4 rejection paths + non-Pub/Sub path guard)
    - `GmailPubSubControllerIntegrationTest` passes GREEN (5 test cases including duplicate dedup)
    - `TriagePauseControllerTest` contains `@Import(TestSessionSupport.class)`, sends `TestSessionSupport.HEADER_SUBJECT` and `HEADER_EMAIL` headers on successful requests, has a missing-auth negative test, no longer contains class-level `@Disabled`, and passes GREEN
    - `PubSubIdempotencyTest` does NOT import `TestSessionSupport`, no longer contains class-level `@Disabled`, and passes GREEN with valid Pub/Sub OIDC bearer-token requests
  </acceptance_criteria>

  <done>OIDC filter, dual SecurityFilterChain, PubSubIngestionService (CLAUDE.md §1 compliant), thin PubSub controller, and triage-pause controller all compile and their Wave 0 tests pass GREEN. Phase 01.5 D-D5 deferred ceremony is now closed.</done>
</task>

<task type="auto">
  <name>Task 2: Extend MeResponse with triagePaused + gmailConnectionStatus</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java,
    backend/core/src/main/java/com/zeromail/core/gmail/model/GmailConnectionProjection.java,
    backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java
  </files>

  <read_first>
    - backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java (full file — READ BEFORE editing)
    - backend/api/src/main/java/com/zeromail/api/controllers/MeController.java (or wherever /me is defined — find it)
    - backend/core/src/main/java/com/zeromail/core/account/service/AccountService.java (getCurrentUser logic — what projection it returns)
    - backend/core/src/main/java/com/zeromail/core/gmail/model/GmailConnectionProjection.java (full file — extend with ingestionHealth)
    - backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java (Wave 0 scaffold to enable)
    - backend/api/src/test/java/com/zeromail/api/security/TestSessionSupport.java (test-profile auth + TenantContext binding)
    - backend/api/src/test/java/com/zeromail/api/controllers/MeLanguageIntegrationTest.java (existing `/me` authenticated RestClient pattern)
    - .planning/phases/02A-mail-ingestion/02A-CONTEXT.md (D-E4: extend MeResponse with triagePaused + gmailConnectionStatus)
    - .planning/phases/02A-mail-ingestion/02A-PATTERNS.md (MeResponse adaptation section)
    - CLAUDE.md (Conventions: records for DTOs, Lombok-free)
  </read_first>

  <action>
READ the current `MeResponse.java` fully. It is a record with `userId`, `tenantId`, `email`, `onboardingStep`, `preferredLanguage`.

Extend it to add `triagePaused` and a nested `GmailConnectionStatusExtended` record:

```java
package com.zeromail.api.dto.account;

public record MeResponse(
    String userId,
    String tenantId,
    String email,
    String onboardingStep,
    String preferredLanguage,
    boolean triagePaused,
    GmailConnectionStatusExtended gmailConnectionStatus
) {

    public record GmailConnectionStatusExtended(
        String status,          // GmailConnectionStatus.id()
        String ingestionHealth, // GmailIngestionHealth.id()
        String googleEmail      // included for display — NOT for logs
    ) {}

    // Update the from(...) factory to populate the new fields
    public static MeResponse from(CurrentUserProjection user,
                                   boolean triagePaused,
                                   GmailConnectionStatusExtended gmailStatus) {
        return new MeResponse(
            user.userId().toString(),
            user.tenantId().toString(),
            user.email(),
            user.onboardingStep(),
            user.preferredLanguage(),
            triagePaused,
            gmailStatus
        );
    }
}
```

**Important:** Records are immutable — adding components means a new constructor. The old `from(CurrentUserProjection)` factory signature must change. Find ALL callers of the old `MeResponse.from(...)` in the API layer (grep: `MeResponse.from(`) and update them to pass the two new arguments.

Callers will need to:
1. Load `TenantEntity.isTriagePaused()` via `TenantService` or `AccountService`
2. Load `GmailConnectionEntity` via `GmailConnectionService.currentStatus(tenantId)` to extract status + ingestionHealth + googleEmail

The controller calling `/me` must now make these two additional reads. The `/me` controller likely already has a service call — extend it, not duplicate. Use the existing `GmailConnectionService.currentStatus(UUID)` projection (which already returns status + googleEmail; it needs to be extended to also return `ingestionHealth`).

Extend `GmailConnectionProjection.java` (read it first) to add `String ingestionHealth()` if not present. If it's a Spring Data projection interface, add the method. If it's a record, update the record.

After the `/me` endpoint and DTO are updated, remove class-level `@Disabled` from `backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java`. It must execute GREEN in this plan; skipped tests do not count as closure.

`MeControllerTest.java` must use the same authenticated test-profile contract as `MeLanguageIntegrationTest`:
- Add `@ActiveProfiles("test")` and `@Import(TestSessionSupport.class)`.
- Seed `TenantEntity` with `triagePaused=true` for at least one test and save a matching `UserEntity` inside `ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(...)`.
- If a Gmail connection row is needed for `gmailConnectionStatus`, seed it inside the same tenant scope using the existing `GmailConnectionService.upsert(...)` or repository pattern from existing tests.
- Call `GET /me` via `RestClient` with `.header(TestSessionSupport.HEADER_SUBJECT, seed.googleSubject())` and `.header(TestSessionSupport.HEADER_EMAIL, seed.email())`.
- Assert raw JSON contains `"triagePaused":true`, `"gmailConnectionStatus"`, and `"ingestionHealth"`.
- Add/keep a negative test `me_missingTestAuth_returns401()` with no test headers so missing auth does not accidentally execute `/me` with empty `TenantContext`.

Privacy: `googleEmail` is in the response ONLY for UI display (the user's own email). It must NOT appear in any log statement in the controller or service. The response field is safe because the user owns it.
  </action>

  <verify>
    <automated>./gradlew :backend:core:compileJava :backend:api:compileJava :backend:api:test --tests "com.zeromail.api.controllers.MeControllerTest" 2>&1 | grep -E "BUILD|PASSED|FAILED|SKIPPED|error:" | head -20</automated>
  </verify>

  <acceptance_criteria>
    - `MeResponse.java` contains `boolean triagePaused` and `GmailConnectionStatusExtended gmailConnectionStatus` as record components
    - `MeResponse.GmailConnectionStatusExtended` record contains `status`, `ingestionHealth`, `googleEmail` fields
    - The `from(...)` factory method accepts two new arguments (triagePaused + GmailConnectionStatusExtended)
    - No existing tests break (`./gradlew :backend:api:compileJava` exits 0)
    - `GmailConnectionProjection` (or whatever projection is used) exposes `ingestionHealth` field
    - `MeControllerTest.java` contains `@Import(TestSessionSupport.class)`, sends `TestSessionSupport.HEADER_SUBJECT` and `HEADER_EMAIL` headers on successful `/me` requests, has a missing-auth negative test, and no longer contains class-level `@Disabled`
    - `./gradlew :backend:api:test --tests "com.zeromail.api.controllers.MeControllerTest"` exits 0 with GREEN tests, not SKIPPED-only output
  </acceptance_criteria>

  <done>MeResponse extended with triagePaused + GmailConnectionStatusExtended; all callers updated; api module compiles clean; MeControllerTest Wave 0 scaffold is enabled and GREEN</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Google Pub/Sub → /internal/pubsub/gmail | Machine-to-machine; OIDC token must be verified before any business logic |
| Browser → /tenant/triage-pause | User session required; @TenantId filter prevents cross-tenant writes |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-01 | Spoofing | PubSubOidcAuthFilter | mitigate | TokenVerifier.newBuilder().setAudience().setIssuer() — hard 401 on any mismatch; verify() throws VerificationException for wrong aud/email/exp/sig/iss; filter never calls chain.doFilter on failure; disabled FilterRegistrationBean + shouldNotFilter guard prevent the Pub/Sub filter from affecting non-Pub/Sub user endpoints |
| T-06 | Tampering | PubSubIngestionService tenant lookup | mitigate | Unscoped JdbcTemplate lookup returns only tenant_id with parameterized SQL; tenant-owned JPA queries are never run before TenantContext binding |
| T-07 | Elevation of Privilege | TriagePauseController | mitigate | Existing SecurityConfig user OAuth chain (now @Order(2)) requires authentication in production; TestSessionSupport requires X-Test-Subject/X-Test-Email and binds TenantContext in test profile; missing-auth tests assert protected endpoints do not run with empty TenantContext |
| T-08 | Denial of Service | GmailPubSubController ack deadline | mitigate | D-A1 ack-fast pattern: controller does ONLY envelope parse + service.ingestPushEnvelope() + 200; no Gmail API call; all persistence in PubSubIngestionService; p99 target <300ms |
| T-10 | Information Disclosure | MeResponse.gmailConnectionStatus.googleEmail | accept | googleEmail shown to authenticated user only (their own email); never logged; acceptable display field |
| T-02 | Tampering | PubSubDeliveryRepository.insertPendingIfAbsent duplicate | mitigate | Native `INSERT ... ON CONFLICT DO NOTHING` returns row count; no rollback-only transaction from caught DataIntegrityViolationException |
</threat_model>

<verification>
After this plan:
- `./gradlew :backend:api:compileJava :backend:core:compileJava` exits 0
- `./gradlew :backend:api:test --tests "*PubSubOidcAuthFilterTest*"` exits 0 — 6 test cases GREEN
- `./gradlew :backend:api:test --tests "*GmailPubSubControllerIntegrationTest*"` exits 0 — 5 test cases GREEN
- `grep -n "GmailConnectionRepository\|PubSubDeliveryRepository" backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java` returns empty (no direct repo injection in controller)
- `grep -n '@Order' backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` shows `@Order(2)`
- `grep -c 'PUBSUB_PUSH_AUDIENCE_URL' backend/api/src/main/resources/application.yml` >= 1
- `grep -n "TestSessionSupport" backend/api/src/test/java/com/zeromail/api/controllers/MeControllerTest.java backend/api/src/test/java/com/zeromail/api/controllers/TriagePauseControllerTest.java` shows both tests import the test auth chain
- `grep -n "TestSessionSupport" backend/api/src/test/java/com/zeromail/api/controllers/PubSubIdempotencyTest.java backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java` returns empty
- `./gradlew :backend:api:test --tests "com.zeromail.api.controllers.MeControllerTest"` exits 0 with GREEN tests (SKIPPED-only output does not count)
</verification>

<success_criteria>
OIDC filter verified by 6-case test (valid + wrong aud/email/exp/sig + non-Pub/Sub path guard). Push controller is thin (parse → service → 200). PubSubIngestionService owns all persistence through unscoped JdbcTemplate lookup plus tenant-bound TransactionTemplate insert — CLAUDE.md §1 compliant. SecurityConfig correctly ordered and Pub/Sub security active in tests. Test-profile user endpoints use TestSessionSupport headers to bind authenticated TenantContext and include missing-auth negative coverage. MeResponse extended with triagePaused + gmailConnectionStatus. Phase 01.5 D-D5 deferred ceremony is closed.
</success_criteria>

<output>
After completion, create `.planning/phases/02A-mail-ingestion/02A-03-SUMMARY.md`
</output>
