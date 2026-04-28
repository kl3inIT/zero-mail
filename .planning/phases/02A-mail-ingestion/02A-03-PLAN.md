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
  - backend/api/src/main/java/com/zeromail/api/dto/tenant/TriagePauseRequest.java
  - backend/api/src/main/java/com/zeromail/api/dto/tenant/TriagePauseResponse.java
  - backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java
  - backend/api/src/main/resources/application.yml
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
    - "PubSubSecurityConfig @Order(1) intercepts /internal/pubsub/** BEFORE user OAuth chain"
  artifacts:
    - path: "backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java"
      provides: "OncePerRequestFilter that verifies Google OIDC token"
      contains: "TokenVerifier"
    - path: "backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java"
      provides: "SecurityFilterChain @Order(1) for /internal/pubsub/**"
      contains: "securityMatcher"
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java"
      provides: "POST /internal/pubsub/gmail ack-fast receiver"
      contains: "/internal/pubsub/gmail"
    - path: "backend/api/src/main/java/com/zeromail/api/controllers/TriagePauseController.java"
      provides: "PUT /tenant/triage-pause"
      contains: "/tenant/triage-pause"
  key_links:
    - from: "PubSubSecurityConfig"
      to: "PubSubOidcAuthFilter"
      via: "addFilterBefore in SecurityFilterChain"
      pattern: "addFilterBefore.*oidcFilter"
    - from: "GmailPubSubController"
      to: "PubSubDeliveryRepository"
      via: "insertDelivery service call after tenant lookup"
      pattern: "deliveryRepository|insertDelivery"
    - from: "SecurityConfig"
      to: "@Order(2)"
      via: "@Order annotation on SecurityConfig class or bean method"
      pattern: "@Order\\(2\\)"
---

<objective>
Implement the API-layer components that close MAIL-03 (OIDC verification) and MAIL-01 (push receiver) and provide the triage-pause endpoint. This plan runs in Wave 2 parallel with Plan 02 (worker schedulers).

Purpose: The push receiver + OIDC filter is the Phase 01.5 D-D5 deferred ceremony — this plan delivers it. The triage-pause controller delivers MAIL-06 API surface.

Output: PubSubOidcAuthFilter, PubSubSecurityConfig, GmailPubSubController, TriagePauseController, DTOs, MeResponse extension, SecurityConfig @Order(2).
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

PubSubDeliveryRepository — available from Plan 01; has claimPendingBatch + updateStatus

TenantService.setTriagePaused(UUID, boolean) — available from Plan 02
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: OIDC filter + dual SecurityFilterChain + PubSubController + TriagePauseController</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java,
    backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java,
    backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java,
    backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java,
    backend/api/src/main/java/com/zeromail/api/controllers/TriagePauseController.java,
    backend/api/src/main/java/com/zeromail/api/dto/gmail/PubSubPushEnvelope.java,
    backend/api/src/main/java/com/zeromail/api/dto/tenant/TriagePauseRequest.java,
    backend/api/src/main/java/com/zeromail/api/dto/tenant/TriagePauseResponse.java,
    backend/api/src/main/resources/application.yml
  </files>

  <read_first>
    - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java (full file — READ BEFORE editing)
    - backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java (OncePerRequestFilter pattern)
    - backend/api/src/main/java/com/zeromail/api/controllers/DisconnectController.java (thin controller pattern)
    - backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java (record DTO pattern)
    - backend/api/src/main/resources/application.yml (full file — READ BEFORE editing to add env vars)
    - .planning/phases/02A-mail-ingestion/02A-RESEARCH.md (Pattern 2 TokenVerifier, Pattern 3 PubSub payload, Pattern 1 dual SecurityFilterChain, P-01 pitfall order)
    - .planning/phases/02A-mail-ingestion/02A-PATTERNS.md (PubSubOidcAuthFilter, PubSubSecurityConfig, GmailPubSubController, TriagePauseController adaptations)
    - CLAUDE.md (Conventions: thin controllers, privacy logging format, Lombok-free, records for DTOs)
  </read_first>

  <action>
**`PubSubOidcAuthFilter.java`** — package `com.zeromail.api.security`. Exact shape from RESEARCH.md Pattern 2:

```java
@Component
public class PubSubOidcAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PubSubOidcAuthFilter.class);

    private final TokenVerifier tokenVerifier;
    private final String expectedEmail;

    public PubSubOidcAuthFilter(
            @Value("${pubsub.push-audience-url}") String audience,
            @Value("${pubsub.sa-principal-email}") String saEmail) {
        this.expectedEmail = saEmail;
        this.tokenVerifier = TokenVerifier.newBuilder()
                .setAudience(audience)
                .setIssuer("https://accounts.google.com")
                .build();
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

**`PubSubSecurityConfig.java`** — package `com.zeromail.api.security`. New `@Configuration @Order(1) @Profile("!test")`:

```java
@Configuration
@Order(1)
@Profile("!test")
public class PubSubSecurityConfig {

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

**`SecurityConfig.java`** — READ the current file. Add `@Order(2)` to the SecurityConfig class declaration:
```java
@Configuration
@Order(2)
@Profile("!test")
public class SecurityConfig { ... }
```
No other changes. Import `org.springframework.core.annotation.Order`.

**`PubSubPushEnvelope.java`** — package `com.zeromail.api.dto.gmail`. Nested record per RESEARCH.md Pattern 3:

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

**`GmailPubSubController.java`** — package `com.zeromail.api.controllers`. Thin controller per PATTERNS.md:

```java
@RestController
@io.swagger.v3.oas.annotations.Hidden  // Exclude from OpenAPI
public class GmailPubSubController {

    private static final Logger log = LoggerFactory.getLogger(GmailPubSubController.class);

    private final GmailConnectionRepository connectionRepository;
    private final PubSubDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;

    public GmailPubSubController(GmailConnectionRepository connectionRepository,
                                  PubSubDeliveryRepository deliveryRepository,
                                  ObjectMapper objectMapper) { ... }

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

        // Tenant lookup by LOWER(google_email) — D-A4
        Optional<GmailConnectionEntity> connOpt =
            connectionRepository.findByGoogleEmailIgnoreCase(notification.emailAddress());
        if (connOpt.isEmpty()) {
            log.info("event=pubsub_unknown_email_dropped");  // No email in log — privacy safe
            return ResponseEntity.ok().build();
        }

        GmailConnectionEntity conn = connOpt.get();
        UUID tenantId = conn.getTenantId();

        // Bind TenantContext before any DB call — D-A3
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() -> {
            String payload;
            try {
                payload = objectMapper.writeValueAsString(envelope);
            } catch (Exception e) {
                payload = "{}";
            }
            PubSubDeliveryEntity delivery = new PubSubDeliveryEntity(
                UUID.randomUUID(),
                tenantId,
                envelope.message().messageId(),
                notification.historyId(),
                payload
            );
            try {
                deliveryRepository.save(delivery);
            } catch (DataIntegrityViolationException e) {
                // D-A5: ON CONFLICT — duplicate messageId, silently ignore
                log.info("event=pubsub_duplicate_delivery_dropped tenantId={}", tenantId);
            }
        });

        return ResponseEntity.ok().build();  // Always 200 — prevents infinite redeliver
    }
}
```

Add `findByGoogleEmailIgnoreCase(String email)` to `GmailConnectionRepository.java`:
```java
Optional<GmailConnectionEntity> findByGoogleEmailIgnoreCase(String googleEmail);
```

Privacy: NEVER log `notification.emailAddress()`. NEVER log `conn.getGoogleEmail()`. After tenant lookup, log only `tenantId` UUID.

**`TriagePauseRequest.java`** — package `com.zeromail.api.dto.tenant`:
```java
public record TriagePauseRequest(@NotNull Boolean paused) {}
```

**`TriagePauseResponse.java`** — package `com.zeromail.api.dto.tenant`:
```java
public record TriagePauseResponse(boolean paused) {}
```

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

**`backend/api/src/main/resources/application.yml`** — READ the current file. ADD these env vars with `:?` fail-fast:
```yaml
pubsub:
  push-audience-url: ${PUBSUB_PUSH_AUDIENCE_URL:?PUBSUB_PUSH_AUDIENCE_URL env var is required}
  sa-principal-email: ${PUBSUB_SA_PRINCIPAL_EMAIL:?PUBSUB_SA_PRINCIPAL_EMAIL env var is required}
```
  </action>

  <verify>
    <automated>./gradlew :backend:api:compileJava :backend:api:test --tests "*PubSubOidcAuthFilterTest*" --tests "*GmailPubSubControllerIntegrationTest*" 2>&1 | grep -E "BUILD|PASSED|FAILED|error:" | head -20</automated>
  </verify>

  <acceptance_criteria>
    - `PubSubOidcAuthFilter.java` contains `TokenVerifier.newBuilder()` and `event=pubsub_oidc_verification_failed`
    - `PubSubOidcAuthFilter.java` does NOT contain any log line referencing the token content or email address
    - `PubSubSecurityConfig.java` contains `@Order(1)` and `securityMatcher("/internal/pubsub/**")`
    - `SecurityConfig.java` contains `@Order(2)` — check with `grep -n '@Order' backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java`
    - `GmailPubSubController.java` contains `@PostMapping("/internal/pubsub/gmail")` and `event=pubsub_unknown_email_dropped`
    - `GmailPubSubController.java` does NOT contain `notification.emailAddress()` in any log statement
    - `TriagePauseController.java` contains `@PutMapping("/tenant/triage-pause")` and `event=triage_pause_toggled`
    - `backend/api/src/main/resources/application.yml` contains `PUBSUB_PUSH_AUDIENCE_URL:?` and `PUBSUB_SA_PRINCIPAL_EMAIL:?`
    - `./gradlew :backend:api:compileJava` exits 0
    - `PubSubOidcAuthFilterTest` passes GREEN (5 test cases: valid + 4 rejection paths)
    - `GmailPubSubControllerIntegrationTest` passes GREEN (5 test cases including duplicate dedup)
  </acceptance_criteria>

  <done>OIDC filter, dual SecurityFilterChain, PubSub controller, and triage-pause controller all compile and their Wave 0 tests pass GREEN. Phase 01.5 D-D5 deferred ceremony is now closed.</done>
</task>

<task type="auto">
  <name>Task 2: Extend MeResponse with triagePaused + gmailConnectionStatus</name>
  <files>
    backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java
  </files>

  <read_first>
    - backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java (full file — READ BEFORE editing)
    - backend/api/src/main/java/com/zeromail/api/controllers/MeController.java (or wherever /me is defined — find it)
    - backend/core/src/main/java/com/zeromail/core/account/service/AccountService.java (getCurrentUser logic — what projection it returns)
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

Privacy: `googleEmail` is in the response ONLY for UI display (the user's own email). It must NOT appear in any log statement in the controller or service. The response field is safe because the user owns it.
  </action>

  <verify>
    <automated>./gradlew :backend:api:compileJava :backend:api:test --tests "*MeControllerTest*" 2>&1 | grep -E "BUILD|PASSED|FAILED|error:" | head -10</automated>
  </verify>

  <acceptance_criteria>
    - `MeResponse.java` contains `boolean triagePaused` and `GmailConnectionStatusExtended gmailConnectionStatus` as record components
    - `MeResponse.GmailConnectionStatusExtended` record contains `status`, `ingestionHealth`, `googleEmail` fields
    - The `from(...)` factory method accepts two new arguments (triagePaused + GmailConnectionStatusExtended)
    - No existing tests break (`./gradlew :backend:api:compileJava` exits 0)
    - `GmailConnectionProjection` (or whatever projection is used) exposes `ingestionHealth` field
  </acceptance_criteria>

  <done>MeResponse extended with triagePaused + GmailConnectionStatusExtended; all callers updated; api module compiles clean</done>
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
| T-01 | Spoofing | PubSubOidcAuthFilter | mitigate | TokenVerifier.newBuilder().setAudience().setIssuer() — hard 401 on any mismatch; verify() throws VerificationException for wrong aud/email/exp/sig/iss; filter never calls chain.doFilter on failure |
| T-06 | Tampering | GmailConnectionRepository.findByGoogleEmailIgnoreCase | mitigate | Spring Data derived query — always parameterized; no string concatenation in lookup |
| T-07 | Elevation of Privilege | TriagePauseController | mitigate | Existing SecurityConfig user OAuth chain (now @Order(2)) requires authentication; TenantContext.currentOrThrow() throws if no binding; @TenantId filter bounds service writes |
| T-08 | Denial of Service | GmailPubSubController ack deadline | mitigate | D-A1 ack-fast pattern: controller does ONLY OIDC-verify (filter) + tenant-lookup + INSERT + 200; no Gmail API call; p99 target <300ms |
| T-10 | Information Disclosure | MeResponse.gmailConnectionStatus.googleEmail | accept | googleEmail shown to authenticated user only (their own email); never logged; acceptable display field |
| T-02 | Tampering | PubSubDeliveryRepository.save duplicate | mitigate | DataIntegrityViolationException catch on UNIQUE constraint violation = silent dedup; exactly-once delivery semantics |
</threat_model>

<verification>
After this plan:
- `./gradlew :backend:api:compileJava` exits 0
- `./gradlew :backend:api:test --tests "*PubSubOidcAuthFilterTest*"` exits 0 — 5 test cases GREEN
- `./gradlew :backend:api:test --tests "*GmailPubSubControllerIntegrationTest*"` exits 0 — 5 test cases GREEN
- `grep -n '@Order' backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` shows `@Order(2)`
- `grep -c 'PUBSUB_PUSH_AUDIENCE_URL' backend/api/src/main/resources/application.yml` >= 1
</verification>

<success_criteria>
OIDC filter verified by 5-case test (valid + wrong aud/email/exp/sig). Push controller handles unknown email drop and duplicate dedup. SecurityConfig correctly ordered. MeResponse extended with triagePaused + gmailConnectionStatus. Phase 01.5 D-D5 deferred ceremony is closed.
</success_criteria>

<output>
After completion, create `.planning/phases/02A-mail-ingestion/02A-03-SUMMARY.md`
</output>
