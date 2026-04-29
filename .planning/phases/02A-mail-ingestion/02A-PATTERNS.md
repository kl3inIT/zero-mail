# Phase 02A: Mail Ingestion — Pattern Map

**Mapped:** 2026-04-28
**Files analyzed:** 38 new/modified files
**Analogs found:** 35 / 38

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java` | entity (modify) | CRUD | itself | exact |
| `backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntity.java` | entity | CRUD | `GmailConnectionEntity.java` | role-match |
| `backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java` | repository | CRUD | `GmailConnectionRepository.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntity.java` | entity | CRUD | `GmailConnectionEntity.java` | role-match |
| `backend/core/src/main/java/com/zeromail/core/gmail/persistence/MailMessageObservedRepository.java` | repository | CRUD | `GmailConnectionRepository.java` | role-match |
| `backend/core/src/main/java/com/zeromail/core/gmail/model/GmailIngestionHealth.java` | enum | — | `GmailConnectionStatus.java` | exact |
| `backend/api/src/main/java/com/zeromail/api/controllers/GmailPubSubController.java` | controller | request-response | `TenantStatusController.java` + `DisconnectController.java` | role-match |
| `backend/api/src/main/java/com/zeromail/api/security/PubSubOidcAuthFilter.java` | middleware | request-response | `TenantBindingFilter.java` | role-match |
| `backend/api/src/main/java/com/zeromail/api/security/PubSubSecurityConfig.java` | config | — | `SecurityConfig.java` | role-match |
| `backend/api/src/main/java/com/zeromail/api/controllers/TriagePauseController.java` | controller | request-response | `DisconnectController.java` | exact |
| `backend/api/src/main/java/com/zeromail/api/dto/gmail/PubSubPushEnvelope.java` | dto | — | `GmailConnectionStatusResponse.java` (record) | role-match |
| `backend/api/src/main/java/com/zeromail/api/dto/tenant/TriagePauseRequest.java` | dto | — | `MeResponse.java` (record) | role-match |
| `backend/api/src/main/java/com/zeromail/api/dto/tenant/TriagePauseResponse.java` | dto | — | `MeResponse.java` (record) | role-match |
| `backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java` | dto (modify) | — | itself | exact |
| `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java` | service (modify) | CRUD | itself | exact |
| `backend/core/src/main/java/com/zeromail/core/tenant/persistence/TenantEntity.java` | entity (modify) | CRUD | itself + `GmailConnectionEntity.java` | exact |
| `backend/core/src/main/java/com/zeromail/core/tenant/service/TenantService.java` | service (modify) | CRUD | itself | exact |
| `backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java` | scheduler | batch | `HealthcheckScheduler.java` | role-match |
| `backend/worker/src/main/java/com/zeromail/worker/GmailHistoryProcessor.java` | scheduler | batch | `HealthcheckScheduler.java` | role-match |
| `backend/core/src/main/resources/db/changelog/changes/010-gmail-ingestion-state.yaml` | migration | — | `007-add-audit-columns.yaml` | exact |
| `backend/core/src/main/resources/db/changelog/changes/011-pubsub-delivery-table.yaml` | migration | — | `003-create-gmail-connections.yaml` | exact |
| `backend/core/src/main/resources/db/changelog/changes/012-mail-message-observed-table.yaml` | migration | — | `003-create-gmail-connections.yaml` | exact |
| `backend/core/src/main/resources/db/changelog/changes/013-tenants-triage-paused.yaml` | migration | — | `007-add-audit-columns.yaml` | exact |
| `apps/web/features/triage/components/PauseBanner.tsx` | frontend-component | event-driven | `ReconnectPrompt.tsx` | exact |
| `apps/web/features/triage/hooks/useToggleTriagePause.ts` | frontend-hook | request-response | `useDisconnectGmail.ts` | exact |
| `apps/web/features/triage/api/triagePause.ts` | frontend-api | request-response | `disconnect.ts` | exact |
| `apps/web/features/triage/api/keys.ts` | frontend-utility | — | `apps/web/features/gmail/api/keys.ts` | exact |
| `apps/web/app/(protected)/settings/page.tsx` | frontend-component (modify) | — | itself | exact |
| `apps/web/app/(protected)/layout.tsx` | frontend-component (modify) | — | itself | role-match |
| `apps/web/features/gmail/components/ReconnectPrompt.tsx` | frontend-component (modify) | — | itself | exact |
| `apps/web/features/account/api/me.ts` | frontend-api (modify) | — | itself | exact |
| `apps/web/i18n/messages/vi.json` | frontend-i18n (modify) | — | itself | exact |
| `apps/web/i18n/messages/en.json` | frontend-i18n (modify) | — | itself | exact |
| `backend/api/src/test/java/com/zeromail/api/security/PubSubOidcAuthFilterTest.java` | test | — | `MultiTenantLeakIntegrationTest.java` | role-match |
| `backend/api/src/test/java/com/zeromail/api/controllers/GmailPubSubControllerIntegrationTest.java` | test | — | `ApiPostgresTestBase.java` + `MultiTenantLeakIntegrationTest.java` | role-match |
| `backend/core/src/test/java/com/zeromail/core/gmail/persistence/PubSubDeliveryEntityTest.java` | test | — | `OnboardingStepPersistenceTest.java` | exact |
| `backend/core/src/test/java/com/zeromail/core/gmail/persistence/MailMessageObservedEntityTest.java` | test | — | `OnboardingStepPersistenceTest.java` | exact |
| `backend/core/src/test/java/com/zeromail/core/gmail/model/GmailIngestionHealthTest.java` | test | — | `OnboardingStepPersistenceTest.java` (enum contract) | exact |
| `backend/worker/src/test/java/com/zeromail/worker/GmailHistoryProcessorTest.java` | test | — | `HealthcheckScheduler.java` (no existing worker test) | partial |
| `backend/worker/src/test/java/com/zeromail/worker/GmailWatchSchedulerTest.java` | test | — | no existing worker test | no-analog |
| `apps/web/features/triage/components/PauseBanner.test.tsx` | test | — | `apps/web/__tests__/features/account/me-cache-dedupe.test.ts` | role-match |
| `apps/web/features/triage/hooks/useToggleTriagePause.test.tsx` | test | — | `me-cache-dedupe.test.ts` | role-match |
| `apps/web/__tests__/architecture/phase-02a-files.test.ts` | test | — | `apps/web/__tests__/architecture/feature-folders.test.ts` | exact |

---

## Pattern Assignments

### `GmailConnectionEntity.java` — extend with 6 new columns

**Analog:** `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java` (itself)

**Existing field annotation pattern** (lines 19–43):
```java
@Column(name = "google_email", nullable = false)
private String googleEmail;

@Enumerated(EnumType.STRING)
@Column(nullable = false)
private GmailConnectionStatus status;

@Column(name = "refresh_token_encrypted")
private byte[] refreshTokenEncrypted;

@Column(name = "connected_at")
private Instant connectedAt;
```

**New fields to add** — copy the `@Column` field style, add below `disconnectedAt`:
```java
@Column(name = "last_synced_history_id")
private Long lastSyncedHistoryId;

@Column(name = "watch_history_id")
private Long watchHistoryId;

@Column(name = "watch_expires_at")
private Instant watchExpiresAt;

@Column(name = "watch_renewed_at")
private Instant watchRenewedAt;

@Column(name = "watch_consecutive_failures", nullable = false)
private int watchConsecutiveFailures = 0;

@Enumerated(EnumType.STRING)
@Column(name = "ingestion_health", nullable = false)
private GmailIngestionHealth ingestionHealth = GmailIngestionHealth.HEALTHY;
```

**Getter/setter pattern** (lines 53–63) — add corresponding getters/setters using the same one-liner style:
```java
public Long getLastSyncedHistoryId() { return lastSyncedHistoryId; }
public void setLastSyncedHistoryId(Long id) { this.lastSyncedHistoryId = id; }
```

**Pitfall:** `GmailIngestionHealth` must be on the classpath before `GmailConnectionEntity` compiles. `@Enumerated(EnumType.STRING)` is mandatory — never `EnumType.ORDINAL`.

---

### `PubSubDeliveryEntity.java` — new entity (ingress queue)

**Analog:** `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java`

**Constructor + class pattern** (lines 15–50):
```java
@Entity
@Table(name = "gmail_connections")
public class GmailConnectionEntity extends AbstractTenantOwnedEntity {

    @Column(name = "google_email", nullable = false)
    private String googleEmail;

    protected GmailConnectionEntity() {}   // Hibernate no-args constructor

    public GmailConnectionEntity(UUID id, UUID tenantId, String googleEmail, GmailConnectionStatus status) {
        super(id, tenantId);               // super(id, tenantId) pattern from AbstractTenantOwnedEntity
        this.googleEmail = googleEmail;
        this.status = status;
    }
```

**Adaptation for `PubSubDeliveryEntity`:**
- `@Table(name = "pubsub_delivery")`
- Fields: `pubsubMessageId TEXT`, `historyId BIGINT`, `payload JSONB` (store as `String` column with `columnDefinition = "jsonb"`), `status VARCHAR` (String field — use `PubSubDeliveryStatus` enum later or bare String for v1), `attempts INT NOT NULL DEFAULT 0`, `lockedUntil TIMESTAMPTZ`
- The `UNIQUE(tenant_id, pubsub_message_id)` constraint is Liquibase-side, not JPA-side — no `@UniqueConstraint` needed on the entity
- `AbstractTenantOwnedEntity` supplies `id`, `tenantId`, `createdAt`, `updatedAt`, `version` — do NOT re-declare them
- Pitfall: `payload JSONB` maps as `@Column(columnDefinition = "jsonb")` with `String` type in Java; do not use `@JdbcTypeCode(SqlTypes.JSON)` unless Hibernate 7 dialect registration is verified

---

### `PubSubDeliveryRepository.java` — new repository

**Analog:** `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java` (lines 1–11)

```java
package com.zeromail.core.gmail.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailConnectionRepository extends JpaRepository<GmailConnectionEntity, UUID> {

    Optional<GmailConnectionEntity> findByTenantId(UUID tenantId);
}
```

**Adaptation:** `GmailConnectionRepository` → `PubSubDeliveryRepository extends JpaRepository<PubSubDeliveryEntity, UUID>`. Add a native `@Query` for the SKIP LOCKED batch claim (see RESEARCH.md Pattern 4). The claim must be an atomic `UPDATE ... RETURNING *` that selects due PENDING rows (`locked_until IS NULL OR locked_until < NOW()`) and expired PROCESSING rows (`locked_until < NOW()`) inside the subquery, so a worker crash after claim cannot strand a row forever while retry-delayed PENDING rows stay parked. Mark the native query method with `@Transactional`. No `@Lock` annotation — the FOR UPDATE SKIP LOCKED syntax is inside the native SQL string.

---

### `MailMessageObservedEntity.java` — new entity (composite PK, TEXT[])

**Analog:** `GmailConnectionEntity.java` for class shape; no existing entity with composite PK or TEXT[] column.

**Class pattern** (same `AbstractTenantOwnedEntity` extension):
```java
@Entity
@Table(name = "gmail_connections")
public class GmailConnectionEntity extends AbstractTenantOwnedEntity {
    protected GmailConnectionEntity() {}
    public GmailConnectionEntity(UUID id, UUID tenantId, ...) {
        super(id, tenantId);
        ...
    }
```

**Composite PK adaptation:** Use `@IdClass` for `(tenant_id, gmail_message_id)` and put Hibernate `@TenantId` directly on the standalone `tenantId` id field:
```java
public record MailMessageObservedId(UUID tenantId, String gmailMessageId) implements Serializable {}
```

Then on the entity:
```java
@Entity
@Table(name = "mail_message_observed")
@IdClass(MailMessageObservedId.class)
public class MailMessageObservedEntity {  // Does NOT extend AbstractTenantOwnedEntity (custom PK)

    @Id
    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "gmail_message_id", nullable = false)
    private String gmailMessageId;
    ...
}
```

**TEXT[] column pattern** (from RESEARCH.md Pattern 7):
```java
@JdbcTypeCode(SqlTypes.ARRAY)
@Column(name = "label_ids", columnDefinition = "text[]", nullable = false)
private String[] labelIds;
```

**Pitfall — composite PK vs AbstractTenantOwnedEntity:** `AbstractTenantOwnedEntity` injects its own `@TenantId @Column("tenant_id")` field and carries `AbstractEntity`'s `@Id UUID id`. A composite PK entity cannot inherit that base. This entity is the ONLY one in Phase 2A that does NOT extend `AbstractTenantOwnedEntity`, but it still MUST be tenant-filtered. Use `@IdClass` with a standalone `@Id @TenantId @Column(name = "tenant_id") UUID tenantId` field, not `@EmbeddedId`; then prove with an integration test that tenant A's bound context cannot see tenant B's `mail_message_observed` rows through JPA.

**Pitfall — TEXT[] round-trip:** Must run an integration test that inserts and reads back a multi-element `label_ids` array via `JdbcTemplate` (see `OnboardingStepPersistenceTest` for the raw-column assertion pattern).

---

### `MailMessageObservedRepository.java` — new repository

**Analog:** `GmailConnectionRepository.java`

Same `JpaRepository<MailMessageObservedEntity, MailMessageObservedId>` extension pattern. No SKIP LOCKED needed here. Worker writes use the native `insertObservedIfAbsent(...)` method, not `save()`, so duplicate-message idempotency never depends on catching a JPA flush exception. Avoid unscoped repository read helpers; any future reads must rely on bound `TenantContext` + `@TenantId`, or use explicit tenant predicates.

---

### `GmailIngestionHealth.java` — new IdentifiedEnum

**Analog:** `backend/core/src/main/java/com/zeromail/core/gmail/model/GmailConnectionStatus.java` (full file)

```java
package com.zeromail.core.gmail.model;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.zeromail.core.shared.lang.IdentifiedEnum;

public enum GmailConnectionStatus implements IdentifiedEnum {

    NOT_CONNECTED,
    PENDING,
    CONNECTED,
    DISCONNECTED;

    @Override
    public String id() {
        return name();
    }

    public static GmailConnectionStatus fromId(String id) {
        return Stream.of(values())
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown GmailConnectionStatus id: " + id));
    }
}
```

**Adaptation:** Replace enum name, values (`HEALTHY`, `WATCH_UNHEALTHY`, `HISTORY_LOST`), and class Javadoc. Pattern is identical — `implements IdentifiedEnum`, `id() { return name(); }`, `fromId` fail-loud.

---

### `GmailPubSubController.java` — new controller (push receiver)

**Analog:** `backend/api/src/main/java/com/zeromail/api/controllers/TenantStatusController.java` (full file) + `DisconnectController.java`

**Thin controller pattern** (TenantStatusController lines 34–50):
```java
@RestController
@Tag(name = "gmail")
public class TenantStatusController {

    private final GmailConnectionService connectionService;

    public TenantStatusController(GmailConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/gmail/connection/status")
    public GmailConnectionStatusResponse status() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        GmailConnectionProjection projection = connectionService.currentStatus(tenantId);
        return GmailConnectionStatusResponse.from(projection);
    }
}
```

**Adaptation for `GmailPubSubController`:**
- `@PostMapping("/internal/pubsub/gmail")` — no `@Tag` needed (internal endpoint, excluded from OpenAPI)
- No `TenantContext.currentOrThrow()` at the top — tenant lookup is by `LOWER(emailAddress)` from decoded payload
- Method does NOT call any service with `@Transactional`; it calls the `PubSubIngestionService` (or inline logic)
- Returns `ResponseEntity<Void>` with HTTP 200 on ALL successful paths (including unknown email drop)
- Privacy: NEVER log `emailAddress` from payload — only `tenantId` UUID after lookup
- The method MUST be `@Tag(name = "internal")` + mark with `@Hidden` for OpenAPI if using springdoc

**DisconnectController pattern** (simpler thin controller, lines 1–25):
```java
@RestController
public class DisconnectController {

    private final GmailConnectionService connectionService;

    public DisconnectController(GmailConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping("/tenant/disconnect")
    public void disconnect() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        connectionService.disconnect(tenantId);
    }
}
```

**ScopedValue binding pattern** (TenantBindingFilter lines 42–50) — the filter, not the controller, owns the bind:
```java
ScopedValue.where(TenantContext.TENANT, tenantId).run(() -> {
    try {
        chain.doFilter(req, res);
    } catch (IOException | ServletException e) {
        throw new RuntimeException(e);
    }
});
```
In the controller's case, if tenant lookup happens inside the controller (not a filter), bind before any repository call:
```java
ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() -> {
    pubSubIngestionService.insertDelivery(tenantId, envelope);
});
```

---

### `PubSubOidcAuthFilter.java` — new middleware (OIDC filter)

**Analog:** `backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java` (full file)

**OncePerRequestFilter pattern** (lines 18–55):
```java
@Component
public class TenantBindingFilter extends OncePerRequestFilter {

    private final UserRepository users;

    public TenantBindingFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof OidcUser oidc)) {
            chain.doFilter(req, res);
            return;
        }
        ...
        ScopedValue.where(TenantContext.TENANT, tenantId).run(() -> {
            try {
                chain.doFilter(req, res);
            } catch (IOException | ServletException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
```

**Adaptation for `PubSubOidcAuthFilter`:**
- Do NOT annotate the filter class with `@Component`; create it as a `@Bean` in `PubSubSecurityConfig`, add it to the `/internal/pubsub/**` Spring Security chain, and disable container-wide servlet registration with `FilterRegistrationBean#setEnabled(false)`
- Constructor receives `pubsub.push-audience-url`, `pubsub.sa-principal-email`, and `pubsub.oidc-certificates-url` values from the config bean method (`:?` fail-fast pattern from Phase 01.5 P08)
- Build `TokenVerifier` once at construction time (not per-request)
- `doFilterInternal`: extract `Authorization: Bearer <token>`, call `tokenVerifier.verify(token)`, check `email` claim, call `response.sendError(401)` on any failure — never `chain.doFilter` on failure
- Override `shouldNotFilter(HttpServletRequest)` so non-`/internal/pubsub/**` paths skip the filter even if it is accidentally registered globally later
- Privacy: log events use `log.warn("event=pubsub_oidc_verification_failed")` — no token content, no email in log
- Does NOT bind `TenantContext.TENANT` — that is the controller's responsibility after tenant lookup

**Pitfall — `response.sendError` vs throwing:** `sendError` + `return` is the correct pattern for filters (does not invoke the error page chain in Spring Boot 4 default config). Do not throw an exception from the filter.

---

### `PubSubSecurityConfig.java` — new config (@Order(1) SecurityFilterChain)

**Analog:** `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` (full file)

```java
@Configuration
@Profile("!test")
public class SecurityConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http,
                              TenantBindingFilter tenantFilter,
                              GoogleOAuthSuccessHandler successHandler, ...) {
        http
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(a -> a
                .requestMatchers("/login", "/actuator/health", ...).permitAll()
                .anyRequest().authenticated())
            .oauth2Login(...)
            .csrf(...)
            .addFilterAfter(tenantFilter, AuthorizationFilter.class);
        return http.build();
    }
}
```

**Adaptation for `PubSubSecurityConfig`:**
```java
@Configuration
@Order(1)           // Runs BEFORE the user OAuth chain (which gets @Order(2))
public class PubSubSecurityConfig {

    @Bean
    PubSubOidcAuthFilter pubSubOidcAuthFilter(...) { ... }

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
            .securityMatcher("/internal/pubsub/**")   // ONLY intercepts this path prefix
            .csrf(csrf -> csrf.disable())              // Machine-to-machine: no session/CSRF
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())  // Filter owns authN
            .addFilterBefore(oidcFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

**Existing `SecurityConfig` modification:** Add `@Order(2)` to the existing chain bean method (or class). The `@Profile("!test")` stays on the user-session `SecurityConfig`. `PubSubSecurityConfig` is active in `test` profile so integration tests prove missing/invalid Pub/Sub OIDC requests return 401 before business logic.

**Pitfall:** `securityMatcher` is Spring Security 6+ API (also valid in 7.0.5). Do not use `antMatcher` (removed in Spring 6). Without `securityMatcher`, the `@Order(1)` chain would intercept ALL requests.

**Pitfall:** A servlet `Filter` bean can be auto-registered outside Spring Security. The disabled `FilterRegistrationBean<PubSubOidcAuthFilter>` is mandatory; otherwise the OIDC filter may run globally before `/me`, `/tenant/triage-pause`, or `TestSessionSupport`.

---

### `TriagePauseController.java` — new controller (PUT /tenant/triage-pause)

**Analog:** `backend/api/src/main/java/com/zeromail/api/controllers/DisconnectController.java` (full file)

```java
@RestController
public class DisconnectController {

    private final GmailConnectionService connectionService;

    public DisconnectController(GmailConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping("/tenant/disconnect")
    public void disconnect() {
        UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
        connectionService.disconnect(tenantId);
    }
}
```

**Adaptation for `TriagePauseController`:**
- `@PutMapping("/tenant/triage-pause")` — returns `TriagePauseResponse` record (not void)
- Constructor injects `TenantService`
- `@RequestBody @Valid TriagePauseRequest req` — validated body with `paused: boolean`
- `UUID tenantId = UUID.fromString(TenantContext.currentOrThrow())` — same pattern
- Delegates to `tenantService.setTriagePaused(tenantId, req.paused())`
- Returns `TriagePauseResponse` via a static `from(...)` factory on the DTO

---

### `PubSubPushEnvelope.java` — new DTO (nested record)

**Analog:** `backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailConnectionStatusResponse.java` (full file)

```java
public record GmailConnectionStatusResponse(String connectionStatus, String googleEmail) {

    public static GmailConnectionStatusResponse from(GmailConnectionProjection projection) {
        return new GmailConnectionStatusResponse(projection.status(), projection.googleEmail());
    }
}
```

**Adaptation:** Nested record shape per RESEARCH.md Pattern 3. Records in Java 25 support nested record components directly:
```java
public record PubSubPushEnvelope(PubSubMessage message, String subscription) {
    public record PubSubMessage(String data, String messageId, String publishTime,
                                 Map<String, String> attributes) {}
}
```
No `@JsonProperty` needed for field names that match JSON keys in Jackson 3. `attributes` may be null — annotate `@JsonInclude(JsonInclude.Include.NON_NULL)` on the record component if required.

---

### `TriagePauseRequest.java` + `TriagePauseResponse.java` — new DTOs

**Analog:** `backend/api/src/main/java/com/zeromail/api/dto/account/MeResponse.java` (full file)

```java
public record MeResponse(String userId, String tenantId, String email,
        String onboardingStep, String preferredLanguage) {

    public static MeResponse from(CurrentUserProjection user) {
        return new MeResponse(...);
    }
}
```

**Adaptation:**
- `TriagePauseRequest` is a one-field record: `public record TriagePauseRequest(boolean paused) {}`
- `TriagePauseResponse` mirrors the current triage-pause state: `public record TriagePauseResponse(boolean paused) {}`
- No `from(...)` factory needed on Request; Response factory takes a boolean

---

### `MeResponse.java` — extend existing DTO

**Analog:** itself (full file, lines 1–15)

```java
public record MeResponse(String userId, String tenantId, String email,
        String onboardingStep, String preferredLanguage) {

    public static MeResponse from(CurrentUserProjection user) {
        return new MeResponse(
                user.userId().toString(),
                user.tenantId().toString(),
                user.email(),
                user.onboardingStep(),
                user.preferredLanguage());
    }
}
```

**Adaptation:** Add `boolean triagePaused` and `GmailConnectionStatusExtended gmailConnectionStatus` (new nested record or reuse existing `GmailConnectionStatusResponse` shape extended with `ingestionHealth`). Records are immutable — adding a component means a new record signature; update `from(...)` factory accordingly. The extended record cannot inherit from the old one — a full replacement is required (pre-launch project, no backward compat needed).

---

### `GmailConnectionService.java` — extend existing service

**Analog:** itself (full file, lines 1–99)

**Existing service method pattern** (lines 43–50):
```java
@Transactional
public void disconnect(UUID tenantId) {
    connections.findByTenantId(tenantId).ifPresent(c -> {
        c.setStatus(GmailConnectionStatus.DISCONNECTED);
        c.setDisconnectedAt(Instant.now());
        connections.save(c);
    });
}
```

**New methods follow same `@Transactional` + `findByTenantId` + `setXxx` + `save` pattern:**
```java
@Transactional
public void markHistoryLost(UUID tenantId, Long newPointer) {
    connections.findByTenantId(tenantId).ifPresent(c -> {
        c.setLastSyncedHistoryId(newPointer);
        c.setIngestionHealth(GmailIngestionHealth.HISTORY_LOST);
        connections.save(c);
    });
}

@Transactional
public void markWatchUnhealthy(UUID tenantId) {
    connections.findByTenantId(tenantId).ifPresent(c -> {
        c.setIngestionHealth(GmailIngestionHealth.WATCH_UNHEALTHY);
        connections.save(c);
    });
}

@Transactional
public void recordWatchSuccess(UUID tenantId, Long watchHistoryId, Instant watchExpiresAt) {
    connections.findByTenantId(tenantId).ifPresent(c -> {
        c.setWatchHistoryId(watchHistoryId);
        if (c.getLastSyncedHistoryId() == null) {
            c.setLastSyncedHistoryId(watchHistoryId);
        }
        c.setWatchExpiresAt(watchExpiresAt);
        c.setWatchRenewedAt(Instant.now());
        c.setWatchConsecutiveFailures(0);
        c.setIngestionHealth(GmailIngestionHealth.HEALTHY);
        connections.save(c);
    });
}

@Transactional
public void clearForReconnect(UUID tenantId) {
    connections.findByTenantId(tenantId).ifPresent(c -> {
        c.setWatchExpiresAt(null);
        c.setWatchHistoryId(null);
        c.setLastSyncedHistoryId(null);
        c.setWatchConsecutiveFailures(0);
        c.setIngestionHealth(GmailIngestionHealth.HEALTHY);
        connections.save(c);
    });
}
```

**Extended `disconnect` method:** Extend to call `gmail.users().stop()` best-effort before the existing `setStatus(DISCONNECTED)` block. Inject `GmailApiClientFactory` (new helper) for the stop call. Wrap in try-catch that logs `event=gmail_watch_stop_failed tenantId={}` and continues — failure MUST NOT fail disconnect.

---

### `TenantEntity.java` — add triage_paused column

**Analog:** itself (lines 1–28) + `GmailConnectionEntity.java` for field annotation pattern

```java
@Entity
@Table(name = "tenants")
public class TenantEntity extends AbstractEntity {

    @Column(name = "display_name", nullable = false)
    private String displayName;

    protected TenantEntity() {}

    public TenantEntity(UUID id, String displayName) {
        super(id);
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}
```

**New field:**
```java
@Column(name = "triage_paused", nullable = false)
private boolean triagePaused = false;

public boolean isTriagePaused() { return triagePaused; }
public void setTriagePaused(boolean triagePaused) { this.triagePaused = triagePaused; }
```

---

### `TenantService.java` — add setTriagePaused method

**Analog:** itself (lines 1–48)

**Existing method pattern** (lines 44–47):
```java
@Transactional
public void deleteCurrentTenant(UUID tenantId) {
    tenants.findById(tenantId).ifPresent(tenants::delete);
}
```

**New method:**
```java
@Transactional
public void setTriagePaused(UUID tenantId, boolean paused) {
    tenants.findById(tenantId).ifPresent(t -> {
        t.setTriagePaused(paused);
        tenants.save(t);
    });
}
```

Privacy log added in the controller, not the service — same split as `disconnect` → `DisconnectController` logs the event.

---

### `GmailWatchScheduler.java` — new scheduler

**Analog:** `backend/worker/src/main/java/com/zeromail/worker/HealthcheckScheduler.java` (full file)

```java
@Component
public class HealthcheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(HealthcheckScheduler.class);

    @Scheduled(fixedRate = 60_000L)
    public void tick() {
        log.info("worker healthcheck tick");
    }
}
```

**Adaptation for `GmailWatchScheduler`:**
- `@Scheduled(cron = "0 * * * * *")` — every minute
- Inject `GmailConnectionRepository`, `RefreshTokenCipher`, `GmailApiClientFactory`, `GmailConnectionService`
- `@Component` — same bean lifecycle
- `Logger` using `LoggerFactory.getLogger(GmailWatchScheduler.class)` — same static final pattern
- ScopedValue binding PER ROW (not at scheduler level):
```java
for (GmailConnectionEntity conn : batch) {
    ScopedValue.where(TenantContext.TENANT, conn.getTenantId().toString())
               .run(() -> processWatchRenewal(conn));
}
```
- Privacy: `log.info("event=gmail_watch_renewed tenantId={}", conn.getTenantId())` — never log Google email

---

### `GmailHistoryProcessor.java` — new scheduler

**Analog:** `HealthcheckScheduler.java`

**Adaptation:**
- `@Scheduled(fixedDelay = 1_000L)` — 1s after previous tick completes (not fixedRate)
- Same ScopedValue-per-row pattern as `GmailWatchScheduler`
- After processing, advance `pubsub_delivery.status` PROCESSED inside the same transaction as `mail_message_observed` inserts
- Monotonic-conditional UPDATE for `last_synced_history_id` — use a native `@Query` or `@Modifying @Query` on the repository:
```java
@Modifying
@Query("UPDATE GmailConnectionEntity c SET c.lastSyncedHistoryId = :newId " +
       "WHERE c.tenantId = :tenantId AND " +
       "(c.lastSyncedHistoryId IS NULL OR c.lastSyncedHistoryId < :newId)")
int advanceLastSyncedHistoryId(@Param("tenantId") UUID tenantId, @Param("newId") Long newId);
```

---

### Liquibase changesets 010–013

**Analog 1: `007-add-audit-columns.yaml`** — for `addColumn` on existing tables (010, 013)

```yaml
databaseChangeLog:
  - changeSet:
      id: 007-add-audit-columns
      author: zeromail
      comment: >-
        Phase 1.2.1 D-A3 — ...
      changes:
        - addColumn:
            tableName: gmail_connections
            columns:
              - column:
                  name: created_at
                  type: timestamptz
                  defaultValueComputed: now()
                  constraints:
                    nullable: true
      rollback:
        - dropColumn:
            tableName: gmail_connections
            columnName: created_at
```

**Analog 2: `003-create-gmail-connections.yaml`** — for `createTable` (011, 012)

```yaml
databaseChangeLog:
  - changeSet:
      id: 003-create-gmail-connections
      author: zeromail
      changes:
        - createTable:
            tableName: gmail_connections
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true, nullable: false } }
              - column: { name: tenant_id, type: uuid, constraints: { nullable: false } }
              ...
        - addUniqueConstraint:
            tableName: gmail_connections
            columnNames: tenant_id
            constraintName: uq_gmail_connections_tenant_id
        - createIndex:
            indexName: idx_gmail_conn_status
            tableName: gmail_connections
            columns: [{ column: { name: status } }]
```

**Specific patterns for each changeset:**

**010-gmail-ingestion-state.yaml** — `addColumn` on `gmail_connections`:
- Use `defaultValueNumeric: 0` for `watch_consecutive_failures`
- Use `defaultValue: HEALTHY` (string) for `ingestion_health VARCHAR(32)`
- Nullable columns (`last_synced_history_id`, `watch_history_id`, etc.) omit constraints block

**011-pubsub-delivery-table.yaml** — `createTable` + `addUniqueConstraint` + `createIndex`:
- Composite UNIQUE: `addUniqueConstraint` with `columnNames: tenant_id, pubsub_message_id`
- Index on `(status, locked_until)` for SKIP LOCKED scan: `createIndex` with two column entries
- `payload` column type: `jsonb` (not `text`) — Liquibase supports `jsonb` as a type string for PostgreSQL

**012-mail-message-observed-table.yaml** — `createTable` with composite PK + BRIN index:
- Composite PK: `constraints: { primaryKey: true, primaryKeyName: pk_mail_message_observed }` on BOTH `tenant_id` and `gmail_message_id` columns — or use `addPrimaryKey` after the table creation
- `label_ids TEXT[]`: use `type: "text[]"` or `type: text_array` — verify Liquibase YAML accepts `type: "text[]"` for PostgreSQL (it does: maps to a raw SQL type)
- BRIN index: `createIndex` with `indexType: brin`

**013-tenants-triage-paused.yaml** — `addColumn` on `tenants`:
- `defaultValueBoolean: false` for `BOOLEAN NOT NULL DEFAULT false`

---

### Frontend: `apps/web/features/triage/components/PauseBanner.tsx`

**Analog:** `apps/web/features/gmail/components/ReconnectPrompt.tsx` (full file)

```tsx
'use client';

import { useTranslations } from 'next-intl';

import { Alert, AlertAction, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';

export function ReconnectPrompt({ onReconnect }: { onReconnect: () => void }) {
  const t = useTranslations();
  return (
    <Alert variant="warning">
      <AlertTitle>{t('connectionHealth.disconnected')}</AlertTitle>
      <AlertDescription>{t('connectionHealth.reconnectPrompt')}</AlertDescription>
      <AlertAction>
        <button
          type="button"
          onClick={onReconnect}
          className={cn(buttonVariants({ variant: 'outline', size: 'sm' }))}
        >
          {t('settings.gmailConnection.reconnectCta')}
        </button>
      </AlertAction>
    </Alert>
  );
}
```

**Adaptation for `PauseBanner`:**
- Same `'use client'` + `<Alert variant="warning">` + `AlertTitle` + `AlertDescription` + `AlertAction` structure
- **No props** — reads `useCurrentUser()` internally to check `triagePaused` state; calls `useToggleTriagePause` hook on Unpause click (not a prop callback)
- Shape: `function PauseBanner() { ... }` — zero-prop component; parent does not pass `onUnpause`
- i18n keys: `settings.triage.pause.banner.heading`, `settings.triage.pause.banner.unpause`
- Non-dismissible by design — no close/dismiss button
- Plain DOM `<button>` pattern preserved (STATE.md vitest @base-ui/react boundary — avoids useRef null-dispatch in test)
- `'use client'` directive REQUIRED — it calls `useTranslations()`, `useCurrentUser()`, and `useToggleTriagePause()`
- **Test note:** In `PauseBanner.test.tsx` — mock `useCurrentUser` to return `{ triagePaused: true }` to trigger render; no `onUnpause` prop to pass

---

### Frontend: `apps/web/features/triage/hooks/useToggleTriagePause.ts`

**Analog:** `apps/web/features/gmail/hooks/useDisconnectGmail.ts` (full file)

```ts
'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';

import { disconnectGmail } from '@/features/gmail/api/disconnect';
import { gmailKeys } from '@/features/gmail/api/keys';

export function useDisconnectGmail() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: disconnectGmail,
    onSuccess: () => qc.invalidateQueries({ queryKey: gmailKeys.all }),
  });
}
```

**Adaptation for `useToggleTriagePause`:**
- Import from `@/features/triage/api/triagePause` (not disconnect)
- Invalidate `accountKeys.me()` (not `gmailKeys.all`) — the toggle affects the `me` response
- `mutationFn: (paused: boolean) => toggleTriagePause(paused)` — takes a boolean arg
- No barrel import — deep import only (no-barrel invariant from D-A5)

---

### Frontend: `apps/web/features/triage/api/triagePause.ts`

**Analog:** `apps/web/features/gmail/api/disconnect.ts` (full file)

```ts
import { api, xsrfHeader } from '@/lib/api/client';

export async function disconnectGmail(): Promise<void> {
  const { error, response } = await api.POST('/tenant/disconnect', {
    headers: { ...xsrfHeader() },
  });
  if (error || !response.ok)
    throw error ?? new Error(`/tenant/disconnect failed: ${response.status}`);
}
```

**Adaptation for `triagePause.ts`:**
- `api.PUT('/tenant/triage-pause', { body: { paused }, headers: { 'Content-Type': 'application/json', ...xsrfHeader() } })`
- Returns `TriagePauseResponse` data (not void) — the response carries the updated `paused` boolean
- Error check pattern: same `if (error || !response.ok) throw ...`

---

### Frontend: `apps/web/features/triage/api/keys.ts`

**Analog:** `apps/web/features/gmail/api/keys.ts` (full file)

```ts
export const gmailKeys = {
  all: ['gmail'] as const,
  status: () => [...gmailKeys.all, 'status'] as const,
} as const;
```

**Adaptation:**
```ts
export const triageKeys = {
  all: ['triage'] as const,
  pause: () => [...triageKeys.all, 'pause'] as const,
} as const;
```
Note: `useToggleTriagePause` invalidates `accountKeys.me()` (not `triageKeys`) since the toggle's effect surfaces via `/me`. The `triageKeys` factory is still useful for any future triage-specific queries.

---

### Frontend: `apps/web/features/account/api/me.ts` — extend CurrentUser

**Analog:** itself (lines 1–12)

**Existing `CurrentUser` interface:**
```ts
export interface CurrentUser {
  id: string;
  email: string;
  preferredLanguage: 'vi' | 'en';
  onboardingStep?: string;
}
```

**Adaptation — add two fields:**
```ts
export interface CurrentUser {
  id: string;
  email: string;
  preferredLanguage: 'vi' | 'en';
  onboardingStep?: string;
  triagePaused: boolean;
  gmailConnectionStatus: {
    connectionStatus: 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING';
    ingestionHealth: 'HEALTHY' | 'WATCH_UNHEALTHY' | 'HISTORY_LOST';
    googleEmail: string | null;
  };
}
```

The `fetchCurrentUser` function body and caching wrapper are UNCHANGED — the backend JSON adds the fields to the `/me` response automatically once `MeResponse.java` is extended.

---

### Frontend: settings-page `ReconnectPrompt` mount gate

**Analog:** itself (full file, lines 25–42)

The component itself does not own the gate condition — its parent decides whether to render it. The current parent is `apps/web/app/(protected)/settings/page.tsx`. Per D-D3, extend the settings-page condition from `connStatus === 'DISCONNECTED'` to `connStatus === 'DISCONNECTED' || (connStatus === 'CONNECTED' && ingestionHealth !== 'HEALTHY')`. Keep `NOT_CONNECTED` on the initial connect CTA. The `ReconnectPrompt` component JSX itself may be unchanged.

---

### Frontend: `apps/web/i18n/messages/vi.json` + `en.json` — add triage keys

**Analog:** themselves (existing nested key pattern from `messages.contract.test.ts`)

**Key structure to add** (nested under `settings`):
```json
{
  "settings": {
    "triage": {
      "pause": {
        "title": "...",
        "body": "...",
        "toggleLabel": "...",
        "banner": {
          "heading": "...",
          "unpause": "..."
        }
      }
    }
  }
}
```

**Parity contract:** vi.json and en.json MUST have identical leaf keys — enforced by `messages.contract.test.ts` (`vi/en key parity` test). Adding keys to one file without the other will fail the test. ICU `{var}` placeholders only (no `{{ }}`).

---

## Test Pattern Assignments

### Backend entity/enum unit tests — `PubSubDeliveryEntityTest`, `MailMessageObservedEntityTest`, `GmailIngestionHealthTest`

**Analog:** `backend/core/src/test/java/com/zeromail/core/onboarding/OnboardingStepPersistenceTest.java` (full file)

```java
class OnboardingStepPersistenceTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;

    @ParameterizedTest
    @EnumSource(OnboardingStep.class)
    void each_onboarding_step_persists_as_id_string(OnboardingStep step) {
        UUID tenantId = UUID.randomUUID();
        // 1. Seed FK parent
        jdbc.update("INSERT INTO tenants(id, display_name) VALUES (?, ?)", tenantId, "test-" + tenantId);
        // 2. Persist under ScopedValue
        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() -> {
            users.saveAndFlush(...);
        });
        // 3. Raw column assertion via JdbcTemplate (not EntityManager)
        String rawValue = jdbc.queryForObject("SELECT ... FROM users WHERE id = ?", String.class, userId);
        assertThat(rawValue).isEqualTo(step.id());
    }
}
```

**Adaptation:**
- `PubSubDeliveryEntityTest` extends `PostgresContainerTest` — injects `JdbcTemplate` + `PubSubDeliveryRepository`; asserts UNIQUE constraint via double-insert ON CONFLICT (catch `DataIntegrityViolationException` on second insert)
- `MailMessageObservedEntityTest` — asserts TEXT[] round-trip: insert a row with `label_ids = ARRAY['INBOX','SENT']`, read back via JdbcTemplate, assert the array
- `GmailIngestionHealthTest` — same `@EnumSource` + raw column check pattern; also tests `fromId` fail-loud: `assertThrows(NoSuchElementException.class, () -> GmailIngestionHealth.fromId("INVALID"))`

---

### Backend API integration test — `GmailPubSubControllerIntegrationTest`

**Analog:** `backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java` + `MultiTenantLeakIntegrationTest.java`

**`ApiPostgresTestBase` key pattern** (lines 16–51):
```java
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiPostgresTestBase {

    protected static final PostgreSQLContainer<?> POSTGRES;
    static { POSTGRES = new PostgreSQLContainer<>("postgres:17.6").withDatabaseName(...); POSTGRES.start(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        ...
    }
}
```

**`MultiTenantLeakIntegrationTest` RestClient usage** (lines 41–42):
```java
RestClient client = RestClient.create("http://localhost:" + port);
```

**Adaptation for `GmailPubSubControllerIntegrationTest`:**
- Extend `ApiPostgresTestBase` (inherits `RANDOM_PORT` + Postgres container)
- `@LocalServerPort int port` field + `RestClient.create("http://localhost:" + port)`
- Must use `RestClient` against the FULL filter chain (NOT MockMvc) — OIDC verification filter is only active in full Spring context
- Test fixture requires `MockGoogleOidcServer` (new test helper) to serve mock JWKS + sign synthetic tokens
- Add `PUBSUB_PUSH_AUDIENCE_URL` + `PUBSUB_SA_PRINCIPAL_EMAIL` to `@DynamicPropertySource` for the test context
- Cases: valid token → 200; wrong audience → 401; wrong email → 401; expired → 401; no Authorization header → 401

---

### Backend unit test — `PubSubOidcAuthFilterTest`

**Analog:** `MultiTenantLeakIntegrationTest` (for wiring style) + inline filter test pattern

Standard `OncePerRequestFilter` unit test pattern using `MockHttpServletRequest` + `MockHttpServletResponse` + `MockFilterChain`:
```java
MockHttpServletRequest req = new MockHttpServletRequest();
MockHttpServletResponse res = new MockHttpServletResponse();
MockFilterChain chain = new MockFilterChain();
req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + syntheticToken);
filter.doFilterInternal(req, res, chain);
assertThat(res.getStatus()).isEqualTo(200);  // or 401
```
The `MockGoogleOidcServer` test fixture must override `TokenVerifier.setCertificatesLocation(...)` to point at the mock JWKS URL.

---

### Frontend vitest tests — `PauseBanner.test.tsx`, `useToggleTriagePause.test.tsx`

**Analog:** `apps/web/__tests__/features/account/me-cache-dedupe.test.ts` (structure) + `ReconnectPrompt.tsx` (component shape)

**Test structure pattern** (me-cache-dedupe.test.ts lines 27–53):
```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';

beforeEach(() => {
  vi.resetAllMocks();
  mockFetch.mockResolvedValue({ ok: true, json: async () => MOCK_USER });
  vi.stubGlobal('fetch', mockFetch);
});

describe('...', () => {
  it('...', async () => {
    ...
    expect(result).toEqual(MOCK_USER);
  });
});
```

**`PauseBanner.test.tsx` pattern:**
- PauseBanner has no props — mock `useCurrentUser` to return `{ triagePaused: true }` to trigger render
- Render `<PauseBanner />` (no props) and assert `<Alert variant="warning">` renders
- Assert "Unpause" button exists and clicking it invokes `useToggleTriagePause` mutation
- Plain DOM `<button>` — use `screen.getByRole('button', { name: /unpause/i })`
- Pitfall: `'use client'` components need next-intl `NextIntlClientProvider` wrapper in test render
- Pitfall: mock both `useCurrentUser` and `useToggleTriagePause` — component reads user state and fires mutation internally

**`useToggleTriagePause.test.tsx` pattern:**
- `vi.mock('@/features/triage/api/triagePause')` — mock the mutation fn
- Assert that `onSuccess` calls `qc.invalidateQueries({ queryKey: accountKeys.me() })`
- Follow `me-cache-dedupe.test.ts` shape (vi.resetAllMocks in beforeEach, vi.stubGlobal for fetch)

---

### Frontend architecture test — `apps/web/__tests__/architecture/phase-02a-files.test.ts`

**Analog:** `apps/web/__tests__/architecture/feature-folders.test.ts` (full file)

```ts
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it, expect } from 'vitest';

const APP_WEB = resolve(__dirname, '../..');
const FEATURES_DIR = resolve(APP_WEB, 'features');

describe('Phase 1.3 — Feature folder architecture', () => {
  it.each(FEATURE_ROOTS)('features/%s/ exists', (feature) => {
    expect(existsSync(resolve(FEATURES_DIR, feature))).toBe(true);
  });

  it('features/gmail/api/{status,disconnect,keys}.ts exist with correct exports', () => {
    const status = resolve(FEATURES_DIR, 'gmail/api/status.ts');
    expect(readFileSync(status, 'utf8')).toMatch(/export\s+async\s+function\s+getTenantStatus/);
  });
});
```

**Adaptation for `phase-02a-files.test.ts`:**
- Assert `features/triage/` directory exists with `api/`, `components/`, `hooks/` subdirs
- Assert no `features/triage/index.ts` barrel (D-A5)
- Assert `features/triage/api/triagePause.ts` exports `toggleTriagePause`
- Assert `features/triage/components/PauseBanner.tsx` exists
- Assert `features/triage/hooks/useToggleTriagePause.ts` exports using `useMutation`
- Assert `i18n/messages/vi.json` and `en.json` contain `settings.triage.pause.banner.heading` key (using `getDeep` pattern from `messages.contract.test.ts`)

---

## Shared Patterns

### ScopedValue binding before any DB call

**Source:** `backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java` (lines 42–50)

**Apply to:** `GmailPubSubController` (after tenant lookup, before service call), `GmailWatchScheduler` and `GmailHistoryProcessor` (per-row, NOT at scheduler level)

```java
ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() -> {
    try {
        chain.doFilter(req, res);
    } catch (IOException | ServletException e) {
        throw new RuntimeException(e);
    }
});
```

In worker context (no `FilterChain`):
```java
ScopedValue.where(TenantContext.TENANT, delivery.getTenantId().toString())
           .run(() -> processDelivery(delivery));
```

### Privacy logging format

**Source:** `backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java` (pattern from CLAUDE.md Conventions §4)

**Apply to:** ALL new backend files

```java
log.info("event=gmail_watch_renewed tenantId={}", tenantId);
log.warn("event=pubsub_oidc_verification_failed");   // No token, no email
log.info("event=triage_pause_toggled tenantId={} paused={}", tenantId, paused);
log.warn("event=pubsub_unknown_email_dropped");       // Never log emailAddress
```

Anti-pattern: `log.info("Processing email: " + emailAddress)` — triggers Logback scrub + ArchUnit failure.

### `@Transactional` on service, not controller

**Source:** `backend/core/src/main/java/com/zeromail/core/gmail/service/GmailConnectionService.java` (lines 32, 43, 57, 87)

**Apply to:** All new `@Service` methods — `GmailConnectionService` extensions, `TenantService.setTriagePaused`

Controllers NEVER carry `@Transactional`. Never inject `JpaRepository` directly into a controller.

### Record DTOs with static `from(...)` factory

**Source:** `backend/api/src/main/java/com/zeromail/api/dto/gmail/GmailConnectionStatusResponse.java` (full file)

**Apply to:** `TriagePauseResponse`, extended `MeResponse`

```java
public record GmailConnectionStatusResponse(String connectionStatus, String googleEmail) {
    public static GmailConnectionStatusResponse from(GmailConnectionProjection projection) {
        return new GmailConnectionStatusResponse(projection.status(), projection.googleEmail());
    }
}
```

### Constructor injection, no field injection

**Source:** All existing controllers and services (e.g., `TenantStatusController` lines 39–41)

**Apply to:** All new Spring beans

```java
public TenantStatusController(GmailConnectionService connectionService) {
    this.connectionService = connectionService;
}
```

No `@Autowired`, no field injection, no Lombok `@RequiredArgsConstructor`.

### Protected no-args constructor for Hibernate entities

**Source:** `GmailConnectionEntity.java` line 44

**Apply to:** `PubSubDeliveryEntity`, `MailMessageObservedEntity` (if using standard JPA, not embeddable-only)

```java
protected GmailConnectionEntity() {}   // Hibernate proxy requirement
```

### TanStack Query mutation with key invalidation

**Source:** `apps/web/features/gmail/hooks/useDisconnectGmail.ts` (full file)

**Apply to:** `useToggleTriagePause.ts`

```ts
return useMutation({
    mutationFn: disconnectGmail,
    onSuccess: () => qc.invalidateQueries({ queryKey: gmailKeys.all }),
});
```

### API function with XSRF header on mutating calls

**Source:** `apps/web/features/gmail/api/disconnect.ts` (full file) + `apps/web/features/account/hooks/useUpdateLanguage.ts` (lines 12–16)

**Apply to:** `triagePause.ts` (PUT is mutating — requires XSRF header)

```ts
const { error, response } = await api.POST('/tenant/disconnect', {
    headers: { ...xsrfHeader() },
});
if (error || !response.ok)
    throw error ?? new Error(`/tenant/disconnect failed: ${response.status}`);
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `backend/worker/src/test/java/com/zeromail/worker/GmailWatchSchedulerTest.java` | test | batch | No existing worker tests; worker module has only `HealthcheckScheduler` (no test file exists for it) |
| `backend/worker/src/test/java/com/zeromail/worker/test/MockGoogleOidcServer.java` | test-fixture | — | No existing mock-JWKS server in the project; must be built from scratch using WireMock or `MockWebServer` + RSA keypair |
| `backend/worker/src/test/java/com/zeromail/worker/test/MockGmailHistoryServer.java` | test-fixture | — | No existing Gmail API mock server; must be built from scratch |

For these files, the RESEARCH.md §Common Pitfalls and §Code Examples sections are the primary reference. Pattern from RESEARCH.md Pattern 2 (`TokenVerifier.setCertificatesLocation`) for the OIDC mock server.

---

## Metadata

**Analog search scope:** `backend/core/src/main/java/`, `backend/api/src/main/java/`, `backend/worker/src/main/java/`, `backend/core/src/test/`, `backend/api/src/test/`, `apps/web/features/`, `apps/web/__tests__/`, `backend/core/src/main/resources/db/changelog/changes/`
**Files scanned:** 55
**Pattern extraction date:** 2026-04-28

---

## PATTERN MAPPING COMPLETE

**Phase:** 02A — Mail Ingestion
**Files classified:** 43
**Analogs found:** 40 / 43

### Coverage
- Files with exact analog: 18
- Files with role-match analog: 22
- Files with no analog: 3 (worker test fixtures)

### Key Patterns Identified
- All new backend entities extend `AbstractTenantOwnedEntity` via `super(id, tenantId)` constructor, except `MailMessageObservedEntity` which uses `@IdClass` composite PK with explicit `@TenantId` on the standalone `tenantId` field — this entity does NOT extend AbstractTenantOwnedEntity but is still Hibernate tenant-filtered
- All new scheduled workers follow `HealthcheckScheduler` shape (`@Component`, `@Scheduled`, static Logger) and bind `ScopedValue.where(TenantContext.TENANT, ...).run(...)` PER ROW, not at the scheduler level
- `PubSubSecurityConfig @Order(1)` + `securityMatcher("/internal/pubsub/**")` is the idiomatic Spring Security 7 pattern for isolating machine-to-machine endpoints; the existing `SecurityConfig` adds `@Order(2)`
- Frontend hook pattern: `useMutation` + `qc.invalidateQueries` + `xsrfHeader()` on the API function — identical to `useDisconnectGmail` and `useUpdateLanguage`
- Privacy rule: log `event=<opaque_name> tenantId={}` — NEVER `emailAddress`, token bytes, or Gmail message content in any log line in any new file
- Liquibase changesets use `defaultValueBoolean` for boolean, `defaultValue` (string) for VARCHAR, `defaultValueNumeric` for INT/BIGINT — mix matters and `007-add-audit-columns.yaml` is the authoritative in-repo example
- `TEXT[]` Hibernate mapping uses `@JdbcTypeCode(SqlTypes.ARRAY)` + `@Column(columnDefinition = "text[]")` — no third-party library needed in Hibernate 7

### File Created
`D:\study-materials-summer-2026\EXE202\zero-mail\.planning\phases\02A-mail-ingestion\02A-PATTERNS.md`

### Ready for Planning
Pattern mapping complete. Planner can now reference analog patterns in PLAN.md files.
