# Phase 8: Admin Console & Operator Tooling — Pattern Map

**Mapped:** 2026-05-19
**Files analyzed:** 42 requirements → 17 file clusters
**Analogs found:** 15 / 17 clusters have an exact or role-match analog; 2 are net-new patterns (WebAuthn DSL, Vite SPA workspace).

This map turns each Phase 8 file cluster into a concrete "copy from X lines N–M" instruction for the planner. Phase 8 is large (40+ files) — clustering is by Modulith sub-package and frontend route group, not file-by-file.

---

## File Classification (cluster level)

| Cluster | Role | Data Flow | Closest Analog | Match Quality |
|---------|------|-----------|----------------|---------------|
| C1. SecurityConfig admin/user chain split | config | request-response | `backend/api/.../security/SecurityConfig.java` | exact (extend existing) |
| C2. AdminContext + AdminBindingFilter | shared scoped-value | request-response | `core/tenant/TenantContext.java` + `api/security/TenantBindingFilter.java` | exact |
| C3. admin_users entity + repository + WebAuthn ceremony wiring | entity + repo + service | CRUD | `core/llm/persistence/TenantByokCredentialsEntity.java` + `core/llm/usecases/ByokService.java` | role-match (no WebAuthn DSL precedent) |
| C4. EnrollmentTokenService + BootstrapRunner (`CommandLineRunner`) | startup runner + in-memory store | event-driven (startup) | NEW pattern — no `CommandLineRunner` in repo today | none |
| C5. admin_audit_event + admin_read_event entities + writers | append-only entity + HMAC chain writer | CRUD insert-only | `core/triage/persistence/TriageAuditEntity.java` + `TriageAuditRepository.java` | exact (insert-only invariant identical) |
| C6. Postgres append-only trigger + HMAC chain function | Liquibase YAML SQL | DDL | `db/changelog/changes/042-chat-message-and-body-ban-trigger.yaml` | exact |
| C7. llm_provider_master_key entity + AES-GCM service | entity + service with cipher | CRUD | `core/llm/persistence/TenantByokCredentialsEntity.java` + `core/llm/usecases/ByokService.java` | exact |
| C8. ProviderMasterKeyResolver + ChatModel cache eviction listener | gateway adapter + Modulith listener | event-driven | `core/llm/gateway/springai/*` + `core/thread/usecases/ClassifyThreadReplyStatusService.java#@ApplicationModuleListener` | exact |
| C9. provider_catalog + model_catalog + feature_binding (3-table) + Liquibase + seed | entity + repository + read query service | CRUD | `core/triage/projection/AuditLogRow.java` + `TriageAuditRepository` read methods | role-match |
| C10. CatalogSyncOrchestrator (Fetch → Diff → Confirm) over `processing_job` | service + SKIP LOCKED queue consumer | event-driven / batch | `core/gmail/persistence/PubSubDeliveryRepository.java#claimPendingBatch` | exact |
| C11. AdminTenantAccess.readOnly + tenant inspection projections | use-case service + projection record | request-response | `core/account/usecases/OAuthProvisioningService.java#provisionBundledOAuth` (atomic + ScopedValue.where) | role-match |
| C12. AdminResponseBodyBanFilter (Jackson 3 streaming filter) | servlet filter | request-response | `api/security/TenantBindingFilter.java` (OncePerRequestFilter shape) | role-match |
| C13. Queue + spend aggregator read services | read-side query service | request-response | `core/triage/projection/AuditLogPage.java` + `AuditLogQueryService` | exact |
| C14. Admin REST controllers (7 controllers under `controllers/admin/`) | controller | request-response | `api/controllers/llm/ByokController.java` + `api/controllers/triage/TriageAuditController.java` | exact |
| C15. OpenApiConfig GroupedOpenApi split | config | config | `api/config/OpenApiConfig.java` | exact (extend existing) |
| C16. apps/admin Vite + React 19 SPA workspace | frontend SPA workspace | request-response | `apps/web/scripts/generate-api.ts` + `apps/web/lib/api/client.ts` (Next.js — conceptual only) | partial (no Vite precedent) |
| C17. ArchUnit rules (6 new) + grep-gate (1) | test | static analysis | `core/draft/DraftPathArchUnitTest.java` + `core/arch/TriageAuditRepositoryBoundaryArchTest.java` + `core/arch/OnlyOneGmailSendCallSiteTest.java` | exact |

---

## Pattern Assignments

### C1. SecurityConfig admin/user chain split

**New/modified files:**
- `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` (modify — add `@Order(1) adminChain`, demote existing chain `@Order(3)` → `@Order(2)`)

**Analog:** `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` (current file, lines 1–67)

**Excerpt — existing user chain shape (lines 22–65) to KEEP unchanged but demote `@Order(2)`:**
```java
@Bean
@Order(3)  // change to @Order(2) — no securityMatcher → catch-all fallback for /api/**
SecurityFilterChain chain(
        HttpSecurity http,
        TenantBindingFilter tenantFilter,
        GoogleOAuthSuccessHandler successHandler,
        LoginRedirectAuthenticationFailureHandler failureHandler,
        GoogleAuthorizationRequestResolver authRequestResolver) {
    http.cors(Customizer.withDefaults())
            .authorizeHttpRequests(a -> a.requestMatchers(
                    "/login", "/actuator/health", "/actuator/health/**",
                    "/v3/api-docs/**", "/swagger-ui/**",
                    "/login/oauth2/**", "/oauth2/**").permitAll()
                    .anyRequest().authenticated())
            .oauth2Login(o -> o.successHandler(successHandler)
                    .failureHandler(failureHandler)
                    .authorizationEndpoint(a -> a.authorizationRequestResolver(authRequestResolver)))
            .csrf(csrf -> csrf.spa().ignoringRequestMatchers(
                    "/login/oauth2/code/**", "/oauth2/callback/**"))
            .exceptionHandling(eh -> eh.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), API_REQUEST_MATCHER))
            .sessionManagement(Customizer.withDefaults())
            .addFilterAfter(tenantFilter, AuthorizationFilter.class);
    return http.build();
}
```

**New code to add — `@Order(1) adminChain` (skeleton, Spring Security 7 WebAuthn DSL):**
```java
@Bean
@Order(1)
SecurityFilterChain adminChain(
        HttpSecurity http,
        AdminBindingFilter adminBindingFilter,
        AdminUserDetailsService adminUserDetailsService,
        WebAuthnRelyingPartyOperations relyingPartyOperations) {
    http.securityMatcher("/api/admin/**", "/webauthn/**", "/login/webauthn/**", "/enroll")
        .authorizeHttpRequests(a -> a.requestMatchers("/webauthn/**", "/login/webauthn/**", "/enroll").permitAll()
                .anyRequest().hasRole("ADMIN"))
        .webAuthn(w -> w.rpName("Zero Mail Admin")
                       .rpId("admin.zeromail.com")
                       .allowedOrigins("https://admin.zeromail.com"))
        .userDetailsService(adminUserDetailsService)
        .csrf(csrf -> csrf.spa())  // WebAuthn endpoints exempt per Spring docs
        .exceptionHandling(eh -> eh.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), ADMIN_REQUEST_MATCHER))
        .sessionManagement(Customizer.withDefaults())
        .addFilterAfter(adminBindingFilter, AuthorizationFilter.class);
    return http.build();
}
```

**Deviation note:** No `.oauth2Login(...)` on admin chain (enforced by ArchUnit `admin_chain_does_not_use_oauth2login`). No `.webAuthn(...)` on user chain. Existing chain uses `PathPatternRequestMatcher.withDefaults().matcher(...)` — admin chain reuses the same matcher type but anchored on `/api/admin/**`.

**Privacy/security gate:** Both chains use the same `JSESSIONID`/Spring Session cookie but on different subdomains. Pitfall 4 (08-RESEARCH.md §849): if both subdomains share the parent domain via `Domain=zeromail.com`, an admin session could be replayed against `zeromail.com`. Configure Spring Session cookie scope `domain=admin.zeromail.com` (no shared parent) for admin chain — research it via Context7 `/spring-projects/spring-session` before coding.

---

### C2. AdminContext + AdminBindingFilter (mutex with TenantContext)

**New/modified files:**
- `backend/core/src/main/java/com/zeromail/core/admin/auth/AdminContext.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/auth/AdminUser.java` (new record)
- `backend/api/src/main/java/com/zeromail/api/security/AdminBindingFilter.java` (new — admin chain's tenant-binding analog)
- `backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java` (modify — add `requireUnbound()` mutex helper)

**Analog:** `backend/core/src/main/java/com/zeromail/core/tenant/TenantContext.java` (lines 1–35) + `backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java` (lines 1–60)

**Excerpt — TenantContext shape to mirror (entire file, 35 lines):**
```java
package com.zeromail.core.tenant;

import java.util.Optional;
import java.util.UUID;

public final class TenantContext {
    public static final ScopedValue<String> TENANT = ScopedValue.newInstance();
    private TenantContext() {}

    public static String currentOrThrow() {
        if (!TENANT.isBound()) {
            throw new IllegalStateException("No tenant bound on this thread");
        }
        return TENANT.get();
    }

    public static UUID currentTenantUuid() { return UUID.fromString(currentOrThrow()); }
    public static Optional<String> currentOptional() {
        return TENANT.isBound() ? Optional.of(TENANT.get()) : Optional.empty();
    }
    public static void runWith(UUID tenantId, Runnable action) {
        ScopedValue.where(TENANT, tenantId.toString()).run(action);
    }
}
```

**New `AdminContext.java` — mirror shape with mutex assertion:**
```java
public final class AdminContext {
    public static final ScopedValue<AdminUser> ADMIN = ScopedValue.newInstance();
    private AdminContext() {}

    public static AdminUser currentOrThrow() {
        if (TenantContext.TENANT.isBound()) {                  // MUTEX guard
            throw new IllegalStateException("AdminContext cannot bind while TenantContext is bound");
        }
        if (!ADMIN.isBound()) throw new IllegalStateException("No admin bound on this thread");
        return ADMIN.get();
    }

    public static boolean isBound() { return ADMIN.isBound(); }

    public static void run(AdminUser admin, Runnable action) {
        if (TenantContext.TENANT.isBound()) {
            throw new IllegalStateException("Cannot enter admin scope while tenant is bound (ARCH-08)");
        }
        ScopedValue.where(ADMIN, admin).run(action);
    }
}
```

**Excerpt — `TenantBindingFilter.java` lines 37–58 — pattern for `AdminBindingFilter` to copy:**
```java
final String tenantId = user.getTenantId().toString();
try {
    ScopedValue.where(TenantContext.TENANT, tenantId)
            .run(() -> {
                try { chain.doFilter(request, response); }
                catch (IOException | ServletException filterException) {
                    throw new RuntimeException(filterException);
                }
            });
} catch (RuntimeException runtimeException) {
    if (runtimeException.getCause() instanceof IOException ioException) throw ioException;
    if (runtimeException.getCause() instanceof ServletException servletException)
        throw servletException;
    throw runtimeException;
}
```

**Deviation:** `AdminBindingFilter` resolves principal from `admin_users` row via `AdminUserDetailsService` (not from `OidcUser` cast), and the bound value is `AdminUser` record (not `String tenantId`). Add `TenantContext.requireUnbound()` static helper used by `AdminContext.run()` and the inverse on `TenantContext.runWith()`.

**Privacy gate:** No admin email / passkey credential bytes ever appear in this filter — bind by `AdminUser(id, email, status)` record only.

---

### C3. admin_users entity + repository + WebAuthn ceremony wiring

**New/modified files:**
- `backend/core/src/main/java/com/zeromail/core/admin/auth/persistence/AdminUserEntity.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/auth/persistence/AdminUserRepository.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/auth/domain/AdminStatus.java` (new — `IdentifiedEnum`: PENDING_ENROLLMENT, ACTIVE, REVOKED)
- `backend/core/src/main/java/com/zeromail/core/admin/auth/usecases/AdminUserDetailsService.java` (new — Spring Security `UserDetailsService`)
- `backend/core/src/main/java/com/zeromail/core/admin/auth/usecases/WebAuthnCredentialStore.java` (new — `PublicKeyCredentialUserEntityRepository` + `UserCredentialRepository`)
- `backend/core/src/main/resources/db/changelog/changes/048-admin-users.yaml` (new)

**Analog:** `backend/core/src/main/java/com/zeromail/core/llm/persistence/TenantByokCredentialsEntity.java` (lines 1–70) for entity + binary key column shape

**Excerpt — `TenantByokCredentialsEntity` lines 12–50 — entity shape with byte[] field + AbstractTenantOwnedEntity superclass:**
```java
@Entity
@Table(name = "tenant_byok_credentials")
public class TenantByokCredentialsEntity extends AbstractTenantOwnedEntity {

    @Convert(converter = BYOKProviderAttributeConverter.class)
    @Column(name = "provider", nullable = false, length = 32)
    private BYOKProvider provider;

    @Column(name = "encrypted_key", nullable = false)
    private byte[] encryptedKey;

    @Column(name = "key_version", nullable = false)
    private short keyVersion;

    protected TenantByokCredentialsEntity() { /* Hibernate */ }

    public TenantByokCredentialsEntity(UUID id, UUID tenantId, BYOKProvider provider, ...) {
        super(id, tenantId);
        this.provider = provider;
        this.encryptedKey = copyEncryptedKey(encryptedKey);
        this.keyVersion = keyVersion;
    }
    // explicit getters/setters — NO Lombok
}
```

**Deviation:** `admin_users` is NOT tenant-owned (admin authority is platform-scope). Do NOT extend `AbstractTenantOwnedEntity`; use `@MappedSuperclass AbstractEntity` (look it up in `core.shared.persistence`) or plain entity. Use `IdentifiedEnum` (per CLAUDE.md convention 4) for `status` and `attestation_format`. Multi-byte columns: `user_handle BYTEA NOT NULL UNIQUE` (64 bytes), `credential_id BYTEA UNIQUE`, `public_key_cose BYTEA`. CHECK constraint on `status` per SPEC ADMIN-09.

**Liquibase analog:** `db/changelog/changes/025-triage-audit.yaml` (lines 1–60) for the column-list YAML idiom (`createTable` / `addCheckConstraint` shape). Append to `db.changelog-master.yaml` includelist.

**Privacy/security gate:** No password column. App DB role gets INSERT + SELECT + UPDATE (for `last_used_at`, `signature_counter`, `status`). DELETE permission revoked via Liquibase `<sql>` `REVOKE DELETE ON admin_users FROM <role>` (mirror trigger pattern from C6).

---

### C4. EnrollmentTokenService + BootstrapRunner (CommandLineRunner)

**New/modified files:**
- `backend/api/src/main/java/com/zeromail/api/admin/AdminBootstrapRunner.java` (new — `CommandLineRunner`)
- `backend/core/src/main/java/com/zeromail/core/admin/auth/usecases/EnrollmentTokenService.java` (new — in-memory `ConcurrentHashMap<String, EnrollmentTokenEntry>` with 10-min TTL)
- `backend/api/src/main/java/com/zeromail/api/security/EnrollmentTokenGate.java` (new — filter intercepting `/enroll` + token verification)
- `backend/core/src/main/java/com/zeromail/core/config/ZeroMailCoreProperties.java` (modify — add `admin.bootstrapEmails: List<String>`)

**Analog:** **No `CommandLineRunner` / `ApplicationRunner` exists in the codebase today** (`grep` confirms zero hits in `backend/api` + `backend/core` for the interface). This is a net-new pattern; planner must research via Context7 `/spring-projects/spring-boot` for canonical `CommandLineRunner` semantics in Boot 4.

**Closest neighbour for "service that runs once on startup":** `backend/worker/src/main/java/com/zeromail/worker/GmailWatchScheduler.java` (uses `@Scheduled` not `CommandLineRunner`) — not a true analog. Use Spring's built-in interface directly.

**Skeleton (research via Context7 first):**
```java
@Component
@Profile("!test")
public class AdminBootstrapRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private final ZeroMailCoreProperties properties;
    private final AdminUserRepository adminUserRepository;
    private final EnrollmentTokenService enrollmentTokenService;

    @Override
    public void run(String... args) {
        for (String email : properties.admin().bootstrapEmails()) {
            adminUserRepository.findByEmail(email)
                .filter(row -> row.getStatus() != AdminStatus.PENDING_ENROLLMENT)
                .ifPresentOrElse(
                    row -> log.info("event=admin_bootstrap_skipped reason=already_active"),
                    () -> {
                        AdminUserEntity row = adminUserRepository.upsertPending(email);
                        String tokenHex = enrollmentTokenService.mintToken(row.getId(), email);
                        // PRINT TO STDOUT — never via SLF4J (log file persistence)
                        System.out.println("[ZeroMail Admin Bootstrap] " + email
                            + " enrollment URL: https://admin.zeromail.com/enroll?token=" + tokenHex
                            + " (valid 10 minutes)");
                    });
        }
    }
}
```

**Deviation:** `System.out.println(...)` direct call (SPEC ADMIN-03 specifics line 230) — bypass SLF4J. In-memory token store uses `ConcurrentHashMap<String,Instant>` with `@Scheduled` 1-min sweep for TTL expiry. Tokens never persisted to disk or DB.

**Privacy gate:** Token bytes never logged. STDOUT capture during deploy is operator's responsibility — runbook (OPS-INFRA-03) documents the capture step.

---

### C5. admin_audit_event + admin_read_event entities + writers (append-only + HMAC chain)

**New/modified files:**
- `backend/core/src/main/java/com/zeromail/core/admin/audit/persistence/AdminAuditEventEntity.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/audit/persistence/AdminAuditEventRepository.java` (new — INSERT-only via `@Query nativeQuery=true`)
- `backend/core/src/main/java/com/zeromail/core/admin/audit/persistence/AdminReadEventEntity.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/audit/persistence/AdminReadEventRepository.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/audit/usecases/AdminAuditWriter.java` (new — wraps insert in same-transaction with state mutation; computes HMAC chain)
- `backend/core/src/main/java/com/zeromail/core/admin/audit/usecases/HmacChainHasher.java` (new — SHA-256 HMAC over (prev_hmac || row_bytes))
- `backend/worker/src/main/java/com/zeromail/worker/admin/AdminReadEventPurgeJob.java` (new — 30-day retention)
- `backend/worker/src/main/java/com/zeromail/worker/admin/AdminAuditChainVerifyJob.java` (new — nightly HMAC re-derivation)

**Analog:** `backend/core/src/main/java/com/zeromail/core/triage/persistence/TriageAuditEntity.java` (lines 1–283) for the immutable-audit-row shape + same-transaction insert pattern.

**Excerpt — `TriageAuditEntity` lines 19–53 — entity declaration with bytea hash column + JSONB columns:**
```java
@Entity
@Table(name = "triage_audit")
@AttributeOverride(name = "id", column = @Column(name = "audit_id", nullable = false))
public class TriageAuditEntity extends AbstractTenantOwnedEntity {

    @Column(name = "args_hash", nullable = false)
    private byte[] argsHash;                        // → reuse for `hmac_chain_hash`

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_args_json", columnDefinition = "jsonb", nullable = false)
    private String actionArgsJson;                  // → reuse shape for `before_state_json` / `after_state_json`

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;
    // ...
}
```

**Excerpt — `TriageAuditRepository.java` lines 16–48 — INSERT-only native query (narrow whitelist):**
```java
@Query(value = """
      INSERT INTO triage_audit (audit_id, tenant_id, ..., reason, decision, ...)
      VALUES (gen_random_uuid(), :tenantId, ..., :reason, 'PENDING', ...)
      ON CONFLICT (...) DO NOTHING
      RETURNING audit_id
      """, nativeQuery = true)
@Transactional
Optional<UUID> insertAuditPendingIfAbsent(@Param("tenantId") UUID tenantId, ...);
```

**Deviation:** `admin_audit_event` is **not tenant-owned** — admin actions cross tenants. Replace `tenant_id` with `actor_user_id` (NOT NULL FK to `admin_users.id`) + `target_id` (UUID, nullable; may be tenant ID or another admin's ID). HMAC chain: every insert reads previous row's `hmac_chain_hash`, computes `HMAC-SHA256(secret_kek, prev_hash || canonicalize(this_row))`, writes computed hash. Same-transaction write (per ADMIN-04) means the writer must run inside the calling service's `@Transactional` boundary — do NOT use a new transaction.

**Privacy gate:** `before_state_json` / `after_state_json` for master-key actions must contain **only** `{masked_key, kek_version, last_rotated_at}` — never `encrypted_key`, never raw plaintext. Enforced by `MasterKeySentinelLeakTest` (C17).

---

### C6. Postgres append-only trigger + permission grant

**New/modified files:**
- `backend/core/src/main/resources/db/changelog/changes/049-admin-audit-event.yaml` (new — table + trigger + grants)
- `backend/core/src/main/resources/db/changelog/changes/050-admin-read-event.yaml` (new — table only, no append-only trigger — 30d retention via worker)

**Analog:** `backend/core/src/main/resources/db/changelog/changes/042-chat-message-and-body-ban-trigger.yaml` (entire file, 179 lines)

**Excerpt — lines 14–30 — `<sql splitStatements: false>` wrapper for atomic table+trigger DDL:**
```yaml
changes:
  - sql:
      splitStatements: false
      sql: |
        CREATE TABLE chat_message (
          id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
          chat_id uuid NOT NULL REFERENCES chat(id) ON DELETE CASCADE,
          tenant_id uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
          role varchar(16) NOT NULL,
          parts jsonb NOT NULL,
          created_at timestamptz NOT NULL DEFAULT now(),
          ...
        );
```

**Excerpt — lines 131–168 — trigger function + trigger creation pattern (adapt for UPDATE/DELETE ban):**
```yaml
        CREATE OR REPLACE FUNCTION reject_chat_message_with_body()
        RETURNS trigger
        LANGUAGE plpgsql
        AS $$
        BEGIN
          IF chat_jsonb_contains_forbidden_html(NEW.parts) THEN
            RAISE EXCEPTION 'Chat persistence violation: forbidden HTML signature in chat_message.parts; see ARCH-02'
              USING ERRCODE = '23514';
          END IF;
          ...
          RETURN NEW;
        END;
        $$;

        CREATE TRIGGER chat_message_body_ban
        BEFORE INSERT OR UPDATE ON chat_message
        FOR EACH ROW
        EXECUTE FUNCTION reject_chat_message_with_body();
```

**Adapted for `admin_audit_event` (ARCH-12):**
```yaml
        CREATE OR REPLACE FUNCTION reject_admin_audit_event_mutation()
        RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          RAISE EXCEPTION 'admin_audit_event is append-only' USING ERRCODE = '23514';
        END;
        $$;
        CREATE TRIGGER admin_audit_event_append_only
        BEFORE UPDATE OR DELETE ON admin_audit_event
        FOR EACH ROW EXECUTE FUNCTION reject_admin_audit_event_mutation();

        -- App DB role privileges (per ARCH-12 acceptance)
        REVOKE UPDATE, DELETE ON admin_audit_event FROM zeromail_app;
        GRANT INSERT, SELECT ON admin_audit_event TO zeromail_app;
```

**Deviation:** Two layers of defense — Postgres trigger fires even for `postgres` superuser; role grant blocks app DB user from even attempting. `admin_read_event` table does NOT get the append-only trigger (30-day retention requires DELETE).

**Rollback section** required (mirror lines 169–178 of `042-...yaml`): `DROP TRIGGER`, `DROP FUNCTION`, `DROP TABLE`.

---

### C7. llm_provider_master_key entity + AES-GCM service

**New/modified files:**
- `backend/core/src/main/java/com/zeromail/core/admin/mkey/persistence/LlmProviderMasterKeyEntity.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/mkey/persistence/LlmProviderMasterKeyRepository.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/mkey/domain/LlmProvider.java` (new `IdentifiedEnum` — OPENAI, ANTHROPIC, GOOGLE, DEEPSEEK, OPENROUTER, ROUTER_9R)
- `backend/core/src/main/java/com/zeromail/core/admin/mkey/domain/KeyFormat.java` (new — OPENAI_FORMAT, ANTHROPIC_FORMAT)
- `backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyAdminService.java` (new — set/test/rotate)
- `backend/core/src/main/java/com/zeromail/core/admin/mkey/usecases/MasterKeyMasker.java` (new — `sk-****abc1` formatter)
- `backend/core/src/main/java/com/zeromail/core/admin/mkey/domain/event/MasterKeyRotatedEvent.java` (new Modulith event)
- `backend/core/src/main/resources/db/changelog/changes/051-llm-provider-master-key.yaml` (new)
- `backend/core/src/main/java/com/zeromail/core/shared/crypto/PlatformSecretCipher.java` (new OR relocate `RefreshTokenCipher` here — plan-phase decides)

**Analog:** `backend/core/src/main/java/com/zeromail/core/llm/persistence/TenantByokCredentialsEntity.java` (lines 1–70) for entity shape + `backend/core/src/main/java/com/zeromail/core/llm/usecases/ByokService.java` (lines 1–100) for set-with-cipher service pattern.

**Excerpt — `ByokService` lines 33–42 — cipher dependency injection + repository pairing:**
```java
public ByokService(
        TenantByokCredentialsRepository tenantByokCredentialsRepository,
        RefreshTokenCipher refreshTokenCipher,
        ByokEndpointValidator byokEndpointValidator,
        ByokValidationGateway byokValidationGateway) {
    this.tenantByokCredentialsRepository = tenantByokCredentialsRepository;
    this.refreshTokenCipher = refreshTokenCipher;
    ...
}
```

**Excerpt — `RefreshTokenCipher.java` lines 33–52 — AES-GCM encrypt with AAD:**
```java
public byte[] encrypt(byte[] plaintext, String tenantId) {
    try {
        byte[] nonce = new byte[12];
        secureRandom.nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keysByVersion.get(currentVersion),
                new GCMParameterSpec(128, nonce));
        cipher.updateAAD(tenantId.getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = cipher.doFinal(plaintext);
        ByteBuffer envelopeBuffer = ByteBuffer.allocate(4 + 12 + ciphertext.length);
        envelopeBuffer.putInt(currentVersion);
        envelopeBuffer.put(nonce);
        envelopeBuffer.put(ciphertext);
        return envelopeBuffer.array();
    } catch (GeneralSecurityException securityException) {
        throw new IllegalStateException(securityException);
    }
}
```

**Deviation (Pitfall 3, 08-RESEARCH.md §865):** `RefreshTokenCipher.encrypt(plaintext, tenantId)` requires a tenantId as AAD. Master keys have **no tenant**. Two options for planner:
1. Relocate `RefreshTokenCipher` → `core.shared.crypto.PlatformSecretCipher` with a generic AAD parameter (`String associatedData`), and have master-key callers pass `"platform:master_key:" + provider.id()` as AAD.
2. Add an overload `encrypt(byte[] plaintext, String associatedData)` and a static constant for the platform AAD.

Either way, the AAD bound MUST include the provider name so a ciphertext encrypted for `OPENAI` cannot be decrypted as `ANTHROPIC` (row-swap defense).

**Privacy/security gate (ARCH-11):** `MasterKeyAdminService.set(...)` MUST mask the input before any logging (`event=master_key_set provider={} maskedKey={} tenantId=N/A`). The plaintext key bytes never touch SLF4J. The masked form goes in audit row's `after_state_json`. Sentinel-leak test (C17) scans all log output + audit JSON for `sk-`/`sk-ant-`/`AIza`/`sk-or-` substrings.

---

### C8. ProviderMasterKeyResolver + ChatModel cache eviction listener

**New/modified files:**
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ProviderMasterKeyResolver.java` (new — sole reader of `llm_provider_master_key`)
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ModelsProbeClient.java` (new — `GET /v1/models` HTTP client, per-provider impl)
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/ChatModelCacheEvictionListener.java` (new — `@ApplicationModuleListener` on `MasterKeyRotatedEvent` + `CatalogChangedEvent`)
- `backend/core/src/main/java/com/zeromail/core/llm/gateway/springai/admin/package-info.java` (new — package fence note)

**Analog:** `backend/core/src/main/java/com/zeromail/core/thread/usecases/ClassifyThreadReplyStatusService.java` lines 66–78 for `@ApplicationModuleListener` pattern.

**Excerpt — Modulith listener idiom (lines 66–78):**
```java
@ApplicationModuleListener
void on(MailOutboundObserved event) {
    classify(new ThreadReplyClassificationInput(
            event.tenantId(),
            event.gmailThreadId(),
            event.gmailMessageId(),
            ...));
}
```

**Adapted for cache eviction:**
```java
@Component
public class ChatModelCacheEvictionListener {
    private final SpringAiChatModelFactory chatModelFactory;

    @ApplicationModuleListener
    void on(MasterKeyRotatedEvent event) {
        // Evict ALL cached ChatModel instances for this provider, across tenants.
        // Pitfall 10 (RESEARCH §914): also evict BYOK ChatModels routed through this provider
        // if the platform-default model bound to this provider rolls forward.
        chatModelFactory.evictByProvider(event.provider());
        log.info("event=chat_model_cache_evicted reason=master_key_rotated provider={}",
                 event.provider());
    }

    @ApplicationModuleListener
    void on(CatalogChangedEvent event) {
        chatModelFactory.evictByModelIds(event.affectedModelIds());
    }
}
```

**Deviation:** `MailMessageObserved` event (analog) is tenant-scoped; `MasterKeyRotatedEvent` is platform-scoped (no tenantId field). Define event record in `core.admin.mkey.domain.event` per Modulith package convention.

**Privacy/security gate (ArchUnit `MasterKeyResolverConfinementTest`):** Only `ProviderMasterKeyResolver` and `LlmProviderMasterKeyRepository` may read `llm_provider_master_key` table. Other classes get ArchUnit failure if they `@Autowired` the repository. Resolver caches decrypted plaintext in `ConcurrentHashMap<LlmProvider, CachedKey>` with TTL synced to ChatModel cache lifetime — and clears on `MasterKeyRotatedEvent`.

---

### C9. provider_catalog + model_catalog + feature_binding (3-table) + Anthropic seed

**New/modified files:**
- `backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ProviderCatalogEntity.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/ModelCatalogEntity.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/FeatureBindingEntity.java` (new)
- 3 corresponding `*Repository.java` interfaces
- `backend/core/src/main/java/com/zeromail/core/admin/cat/domain/Feature.java` (new `IdentifiedEnum` — CHAT, TRIAGE, DRAFT)
- `backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CuratedCatalogQueryService.java` (new — public read-side for `GET /api/settings/catalog`)
- `backend/core/src/main/java/com/zeromail/core/admin/cat/projection/CatalogModelRow.java` (new record)
- `backend/core/src/main/java/com/zeromail/core/admin/cat/projection/PerFeatureCatalog.java` (new record)
- `backend/core/src/main/resources/db/changelog/changes/052-catalog-tables.yaml` (new)
- `backend/core/src/main/resources/db/changelog/changes/053-anthropic-catalog-seed.yaml` (new — Liquibase `<insert>` rows for Claude 4.7 Opus / 4.6 Sonnet / 4.5 Haiku)

**Analog:** `backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogRow.java` (full file, 29 lines) for read-side projection record shape.

**Excerpt — AuditLogRow.java entire file — projection record idiom:**
```java
public record AuditLogRow(
        UUID auditId,
        String gmailThreadId,
        String gmailMessageId,
        String sanitizedSubject,
        String sanitizedSenderEmail,
        String ruleName,
        String action,
        String reason,
        String decisionState,
        Instant createdAt,
        Instant undoableUntil,
        String draftId) {

    public AuditLogRow {
        Objects.requireNonNull(auditId, "auditId must not be null");
        Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(decisionState, "decisionState must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
```

**Deviation:** `CatalogModelRow` fields per SPEC CAT-06 line 248: `provider, model_id, display_name, is_default, is_recommended, cost_per_1k_input, cost_per_1k_output, deprecated_at`. **No tenantId** — catalog is platform-scope. Liquibase 053 uses Liquibase `<insert>` changes (not `<sql>`) so rows are visible to `liquibase rollback`.

**FK + UNIQUE partial index** per CAT-01 acceptance:
```yaml
- addForeignKeyConstraint:
    baseTableName: assistant_settings
    baseColumnNames: chat_model_id
    referencedTableName: model_catalog
    referencedColumnNames: model_id
- sql:
    sql: |
      CREATE UNIQUE INDEX one_default_per_feature_per_provider
      ON feature_binding (provider, feature)
      WHERE is_default = true;
```

**Privacy/security gate:** `GET /api/settings/catalog` is a public-group endpoint (user-facing). It excludes admin-only fields (`sync_history`, `dependents_count`). GroupedOpenApi split (C15) places it in `public` group.

---

### C10. CatalogSyncOrchestrator (Fetch → Diff → Confirm)

**New/modified files:**
- `backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncOrchestrator.java` (new — state machine: FETCH_PENDING → DIFF_READY → CONFIRMED|CANCELLED)
- `backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/CatalogSyncJobConsumer.java` (new — worker side, claims `processing_job` SKIP LOCKED)
- `backend/core/src/main/java/com/zeromail/core/admin/cat/persistence/CatalogSyncJobRepository.java` (new — extends `processing_job` claim queries)
- `backend/core/src/main/java/com/zeromail/core/admin/cat/domain/event/CatalogChangedEvent.java` (new Modulith event)
- `backend/core/src/main/java/com/zeromail/core/admin/cat/usecases/ModelSchemaValidator.java` (new — JSON Schema per provider + regex `^[a-zA-Z0-9._:/\-]{1,128}$`)
- Liquibase changeset: if `processing_job` doesn't have a `job_type='CATALOG_SYNC'` discriminator yet, add it (Pitfall 9, RESEARCH §907).

**Analog:** `backend/core/src/main/java/com/zeromail/core/gmail/persistence/PubSubDeliveryRepository.java` lines 14–41 — SKIP LOCKED claim pattern.

**Excerpt — `claimPendingBatch` lines 17–41 — to copy verbatim, swap table:**
```java
@Transactional
@Query(value = """
        WITH claimed AS (
        UPDATE pubsub_delivery
        SET status = 'PROCESSING',
            locked_until = NOW() + (:lockSeconds * INTERVAL '1 second'),
            attempts = attempts + 1,
            updated_at = NOW(),
            version = version + 1
        WHERE id IN (
            SELECT id
            FROM pubsub_delivery
            WHERE (status = 'PENDING' AND (locked_until IS NULL OR locked_until < NOW()))
               OR (status = 'PROCESSING' AND locked_until < NOW())
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        )
        RETURNING *
        )
        SELECT * FROM claimed
        """, nativeQuery = true)
List<PubSubDeliveryEntity> claimPendingBatch(@Param("limit") int limit, @Param("lockSeconds") int lockSeconds);
```

**Adapted for catalog sync:** Replace `pubsub_delivery` with `processing_job WHERE job_type = 'CATALOG_SYNC'`. Add 60s Redis debounce lease (SPEC CAT-02 line 224) — implement via `RedisTemplate.opsForValue().setIfAbsent(key, ownerId, Duration.ofSeconds(60))` before claiming the job.

**Deviation:** 3-step state machine is **not** auto-applied (SPEC line 224 explicitly forbids auto-apply). Each step is a separate `POST /api/admin/catalog/{provider}/sync/{fetch|diff|confirm}` endpoint. State held in the `processing_job.payload_json` (fetched models list + computed diff).

**Privacy/security gate:** `payload_json` MAY contain model IDs and metadata, but **never** provider error response bodies (Pitfall 7). On `/models` failure, store only an enum reason (`INVALID_KEY|RATE_LIMITED|NETWORK_ERROR|TIMEOUT|SCHEMA_MISMATCH`).

---

### C11. AdminTenantAccess.readOnly + tenant inspection projections

**New/modified files:**
- `backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/AdminTenantAccess.java` (new — only legitimate cross-tenant read path)
- `backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantListRow.java` (new — metadata only)
- `backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantDetailOverview.java` (new — 5-tab overview record)
- `backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantHealthSnapshot.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantBillingSnapshot.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantSpendSnapshot.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/tenant/projection/TenantActivitySnapshot.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantInspectionService.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantPauseService.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantDisconnectService.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/tenant/usecases/TenantDeletionService.java` (new — cascades all tenant data)

**Analog:** `backend/core/src/main/java/com/zeromail/core/account/usecases/OAuthProvisioningService.java` lines 97–134 for `ScopedValue.where(TenantContext.TENANT, ...).run(...)` + transactional wrapper.

**Excerpt — OAuthProvisioningService lines 115–133:**
```java
ScopedValue.where(TenantContext.TENANT, tenantId.toString())
        .run(() -> bundledTransaction.executeWithoutResult(_ -> {
            byte[] envelope = refreshTokenCipher.encrypt(
                    refreshTokenPlaintext.getBytes(StandardCharsets.UTF_8),
                    tenantId.toString());
            gmailConnectionService.upsert(tenantId, email, grantedGmailScopes, envelope);
            gmailConnectionService.clearForReconnect(tenantId);
        }));
```

**Adapted for AdminTenantAccess.readOnly:**
```java
public <T> T readOnly(UUID tenantId, Supplier<T> supplier) {
    AdminUser admin = AdminContext.currentOrThrow();  // require admin scope
    // Pre-write read event before binding tenant — REQ ADMIN-05 + ARCH-08
    adminAuditWriter.writeReadEvent(admin, "TENANT_INSPECTION", tenantId);
    // Exit AdminContext temporarily, enter TenantContext for the supplier
    // (mutex enforced by AdminContext.run — see C2)
    return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
            .call(supplier::get);
}
```

**Deviation:** This pattern intentionally violates the AdminContext/TenantContext mutex for the read-only window — the mutex is held *outside* the `ScopedValue.where(...)` call. ArchUnit must allow this single call site (whitelist `AdminTenantAccess.readOnly` from the mutex rule).

**Privacy/security gate (OPS-TENANT-02):** All projection records contain metadata only — never `gmail_message.body`, never `chat_message.parts[].content`, never `llm_call_audit.prompt`. Field types are scalars/enums/counts/timestamps. `TenantActivitySnapshot.chatSessionCount: int` not `recentChats: List<ChatMessage>`.

---

### C12. AdminResponseBodyBanFilter (Jackson 3 streaming-aware)

**New/modified files:**
- `backend/api/src/main/java/com/zeromail/api/security/AdminResponseBodyBanFilter.java` (new — `OncePerRequestFilter` wrapping response with content cache)

**Analog:** `backend/api/src/main/java/com/zeromail/api/security/TenantBindingFilter.java` (lines 14–60) — `OncePerRequestFilter` lifecycle.

**Excerpt — TenantBindingFilter shape (lines 14–32):**
```java
@Component
public class TenantBindingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        ...
        chain.doFilter(request, response);
    }
}
```

**Adapted for body-ban (per 08-RESEARCH.md §718 Pattern 5):**
```java
@Component
public class AdminResponseBodyBanFilter extends OncePerRequestFilter {
    private static final Pattern FORBIDDEN_KEY = Pattern.compile(
        "body|bodyHtml|snippet|payload|prompt|completion|content", Pattern.CASE_INSENSITIVE);
    private static final int MAX_LEN = 200;

    @Override
    protected void doFilterInternal(...) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/admin/")) {
            chain.doFilter(request, response);
            return;
        }
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapper);
        byte[] body = wrapper.getContentAsByteArray();
        // Jackson 3 streaming scan — see RESEARCH §718 for the JsonParser idiom
        if (scanForForbiddenStringFields(body)) {
            wrapper.resetBuffer();
            wrapper.setStatus(500);
            adminAuditWriter.write(ADMIN_RESPONSE_BODY_BAN_TRIPPED, request.getRequestURI());
            wrapper.getWriter().write("{\"code\":\"error.admin.body_ban\"}");
        }
        wrapper.copyBodyToResponse();
    }
}
```

**Deviation:** Jackson 3 `JsonParser` streaming scan (NOT `ObjectMapper.readTree(...)` — that would buffer the whole payload and re-trigger Pitfall 5 NPM Host forwarding edge cases). Use `JsonFactory.createParser(byte[])` + walk `JsonToken.FIELD_NAME` events, comparing key against the regex and the next value's length.

**Privacy/security gate (OPS-TENANT-04):** The filter is the **last line** of defense; primary defense is `AdminPathBodyBanTest` ArchUnit (C17). On trip, the response is replaced (no leakage even of a partial body via `wrapper.resetBuffer()`).

---

### C13. Queue + spend aggregator read services

**New/modified files:**
- `backend/core/src/main/java/com/zeromail/core/admin/queue/usecases/QueueHealthQueryService.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/queue/projection/QueueHealthSnapshot.java` (new record)
- `backend/core/src/main/java/com/zeromail/core/admin/queue/projection/DeadLetterRow.java` (new — no `payload_json` field)
- `backend/core/src/main/java/com/zeromail/core/admin/queue/usecases/DeadLetterRequeueService.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/spend/usecases/SpendAggregateQueryService.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/spend/projection/SpendKpis.java` (new — today/7d/30d split BYOK)
- `backend/core/src/main/java/com/zeromail/core/admin/spend/projection/ProviderStackBarRow.java` (new)
- `backend/core/src/main/java/com/zeromail/core/admin/spend/projection/TopTenantRow.java` (new — k-anonymized)

**Analog:** `backend/core/src/main/java/com/zeromail/core/triage/projection/AuditLogPage.java` + `AuditLogPageQuery.java` + `core/triage/usecases/AuditLogQueryService` (read-side trio).

**Excerpt — AuditLogPage analog already shown in C9 above.** Repeat the same record shape for the new projections.

**Deviation:** `QueueHealthSnapshot` aggregates over `processing_job` + `outbox` tables — no per-row reads. `SpendAggregateQueryService` queries `llm_call_audit` with SUM/COUNT (never SELECT the `prompt`/`completion` columns — enforced by ArchUnit OPS-SPEND-02). `DeadLetterRow` record **does not have a `payloadJson` field** — DTO contract is the gate (per SPEC OPS-QUEUE-01/02).

**Privacy/security gate:** ArchUnit rule (C17 `AdminSpendPromptAccessorBanTest`) forbids `controllers.admin.spend.*` and `core.admin.spend.*` from calling `LlmCallAuditEntity.getPrompt*` or `.getCompletion*` getters.

---

### C14. Admin REST controllers (7 controllers under `controllers/admin/`)

**New/modified files:**
- `backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminAuditController.java` (new — `/api/admin/audit/events` GET + `/csv` GET)
- `backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminRoleGrantsController.java` (new — `/api/admin/grant-admin` POST + admin list GET)
- `backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminMasterKeyController.java` (new — `/api/admin/master-keys/{provider}` GET/PUT + `/test-connection` POST + `/edit-session` POST + `/rotate` POST)
- `backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminCatalogController.java` (new — `/api/admin/catalog/{provider}/sync/{fetch|diff|confirm}` + `/models` CRUD)
- `backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminTenantController.java` (new — list + detail + pause/disconnect/delete)
- `backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminQueueController.java` (new — `/api/admin/queue` + DLQ requeue)
- `backend/api/src/main/java/com/zeromail/api/controllers/admin/AdminSpendController.java` (new)
- Matching DTO records under `backend/api/src/main/java/com/zeromail/api/dto/admin/{audit,grants,mkey,cat,tenant,queue,spend}/*.java`

**Analog:** `backend/api/src/main/java/com/zeromail/api/controllers/llm/ByokController.java` (full file, 71 lines) for the controller + DTO + service-call shape.

**Excerpt — ByokController.java entire file — controller + Tag + thin handler + DTO mapping pattern:**
```java
@RestController
@Tag(name = "llm-byok")
@RequestMapping("/api/llm/byok")
public class ByokController {

    private final ByokService byokService;

    public ByokController(ByokService byokService) { this.byokService = byokService; }

    @PostMapping("/validate")
    public ByokValidateResponse validate(@Valid @RequestBody ByokValidateRequest request) {
        UUID tenantId = TenantContext.currentTenantUuid();
        ByokValidateResult result = byokService.validate(tenantId,
                new ByokValidateCommand(request.preset(), request.endpoint(),
                                         request.model(), request.apiKey()));
        return ByokValidateResponse.from(result);
    }
    ...
}
```

**Excerpt — DTO record idiom from `ByokSaveResponse.java`:**
```java
@Schema(requiredProperties = {"ok", "savedAt"})
public record ByokSaveResponse(boolean ok, Instant savedAt) {
    public static ByokSaveResponse from(ByokSaveResult result) {
        return new ByokSaveResponse(result.ok(), result.savedAt());
    }
}
```

**Adapted for AdminMasterKeyController (per SPEC D-09 + ADMIN-02):**
```java
@RestController
@Tag(name = "admin-master-keys")
@RequestMapping("/api/admin/master-keys")
@PreAuthorize("hasRole('ADMIN')")        // explicit per-class — REQUIRED by D-09 + ArchUnit
public class AdminMasterKeyController {
    private final MasterKeyAdminService masterKeyAdminService;

    @GetMapping("/{provider}")
    public MasterKeyMaskedResponse get(@PathVariable LlmProvider provider) {
        AdminUser admin = AdminContext.currentOrThrow();     // NOT TenantContext
        return MasterKeyMaskedResponse.from(masterKeyAdminService.getMasked(provider));
    }

    @PostMapping("/{provider}/test-connection")
    public TestConnectionResponse testConnection(...) { ... }
    // edit-session, rotate, set — all explicit @PreAuthorize via class-level annotation
}
```

**Deviation:**
1. **Every** admin controller carries class-level `@PreAuthorize("hasRole('ADMIN')")` (per D-09; ArchUnit `every_admin_controller_must_have_preauthorize` enforces).
2. Replace `TenantContext.currentTenantUuid()` with `AdminContext.currentOrThrow()` (per ARCH-08 mutex).
3. Cross-tenant reads route through `AdminTenantAccess.readOnly(tenantId, () -> ...)` (per C11), never direct `TenantContext.runWith`.
4. DTO records live under `api/dto/admin/...` (not `api/dto/llm/`).
5. `@RequestMapping` paths start with `/api/admin/` — anchors admin chain `securityMatcher`.

**Privacy/security gate:** Admin DTOs use `@JsonInclude(NON_NULL)` to skip nullable fields (per Convention 3). No DTO carries a field whose name matches `body|bodyHtml|snippet|payload|prompt|completion|content` (per ArchUnit `AdminPathBodyBanTest`).

---

### C15. OpenApiConfig GroupedOpenApi split (public vs admin)

**New/modified files:**
- `backend/api/src/main/java/com/zeromail/api/config/OpenApiConfig.java` (modify — add `GroupedOpenApi publicApi` + `GroupedOpenApi adminApi` beans alongside existing customizers)

**Analog:** `backend/api/src/main/java/com/zeromail/api/config/OpenApiConfig.java` (lines 51–253) — extend the existing class.

**Excerpt — existing class anchor + customizer beans (lines 51–78):**
```java
@Configuration
public class OpenApiConfig {
    private static final String API_ERROR_SCHEMA = "ApiError";
    ...

    @Bean
    OpenApiCustomizer phase1Info() {
        return api -> api.setInfo(new Info()
                .title("Zero Mail API")
                .version("0.1.1")
                .description("Phase 1 skeleton + Phase 1.1 i18n/error contract"));
    }

    @Bean
    GlobalOpenApiCustomizer apiErrorCustomizer() { ... }
}
```

**Add (research via Context7 `/springdoc/springdoc-openapi` first):**
```java
@Bean
GroupedOpenApi publicApi() {
    return GroupedOpenApi.builder()
            .group("public")
            .pathsToMatch("/api/**")
            .pathsToExclude("/api/admin/**")     // user-facing app codegen excludes admin
            .build();
}

@Bean
GroupedOpenApi adminApi() {
    return GroupedOpenApi.builder()
            .group("admin")
            .pathsToMatch("/api/admin/**")        // apps/admin codegen consumes this
            .build();
}
```

**Deviation:** Existing `GlobalOpenApiCustomizer apiErrorCustomizer()` already applies to both groups (the comment on line 36 acknowledges grouping). `OpenApiCustomizer phase1Info()` applies to the default doc only — duplicate it per group if title differentiation is wanted ("Zero Mail Public API" vs "Zero Mail Admin API").

**Privacy/security gate (ADMIN-06 acceptance):** `apps/web/scripts/generate-api.ts` continues to consume `/v3/api-docs/public` (no change). `apps/admin/scripts/generate-api.ts` consumes `/v3/api-docs/admin`. Public bundle never sees admin types — enforce by spec URL split, not by post-hoc tree-shaking.

---

### C16. apps/admin Vite + React 19 SPA workspace

**New/modified files:**
- `apps/admin/package.json` (new — `@zeromail/admin` workspace)
- `apps/admin/vite.config.ts` (new)
- `apps/admin/tsconfig.json` (new)
- `apps/admin/index.html` (new)
- `apps/admin/src/main.tsx` (new — React 19 root)
- `apps/admin/src/App.tsx` (new — React Router 6 routes per UI-SPEC §IA)
- `apps/admin/src/lib/api/admin-client.ts` (new)
- `apps/admin/src/lib/api/admin-schema.d.ts` (new — codegenned)
- `apps/admin/scripts/generate-api.ts` (new)
- `apps/admin/src/lib/webauthn.ts` (new — wraps `@simplewebauthn/browser`)
- `apps/admin/src/components/ui/*.tsx` (copy of `apps/web/components/ui/*` — all 24 primitives per UI-SPEC inventory)
- `apps/admin/src/components/AdminModeBanner.tsx` (new — sticky amber 40px)
- `apps/admin/src/components/ConfirmTwiceDialog.tsx` (new — UI-SPEC §Component Composition)
- `apps/admin/src/components/MaskedSecretField.tsx` (new)
- `apps/admin/src/components/JsonDiffViewer.tsx` (new)
- `apps/admin/src/components/KpiCard.tsx` (new)
- `apps/admin/src/components/AutoRefreshIndicator.tsx` (new)
- `apps/admin/src/routes/{enroll,login,dashboard,audit,role-grants,master-keys,catalog,tenants,tenants-detail,queue,spend}.tsx` (11 routes per UI-SPEC table)
- `apps/admin/src/features/{audit,role-grants,master-keys,catalog,tenants,queue,spend}/*.ts` (feature folders — api/, query-keys.ts, hooks per CLAUDE.md Convention 8)
- `apps/admin/src/styles/globals.css` (new — inherit `.zm-proto` palette per UI-SPEC §Color)
- `pnpm-workspace.yaml` (modify — register `apps/admin`)
- `turbo.json` (modify — add `@zeromail/admin#build` pipeline entry)

**Analog:** No Vite app exists yet. Use `apps/web/scripts/generate-api.ts` (43 lines) + `apps/web/lib/api/client.ts` (lines 1–55) as **conceptual** reference; the actual Vite scaffold is net-new.

**Excerpt — `apps/web/scripts/generate-api.ts` lines 1–43 — codegen idiom to fork:**
```ts
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';

const SPEC_URL = process.env.API_SPEC_URL;
const SPEC_PATH = process.env.API_SPEC_PATH ?? 'openapi/openapi.json';
const OUT = 'lib/api/schema.d.ts';

async function resolveSpecInput(): Promise<string> {
  if (SPEC_URL) {
    const res = await fetch(SPEC_URL);
    if (!res.ok) throw new Error(`spec fetch ${res.status}`);
    const spec = await res.text();
    mkdirSync('openapi', { recursive: true });
    writeFileSync('openapi/spec.json', spec);
    return 'openapi/spec.json';
  }
  if (!existsSync(SPEC_PATH)) {
    throw new Error(`spec file not found: ${SPEC_PATH}. Run ./gradlew :backend:api:generateOpenApiDocs or set API_SPEC_URL.`);
  }
  return SPEC_PATH;
}

async function main(): Promise<void> {
  const specInput = await resolveSpecInput();
  ...
  execFileSync(command, args, { stdio: 'inherit' });
}
```

**Adapted for apps/admin:**
- `SPEC_PATH` default → `openapi/admin-spec.json`
- `OUT` → `src/lib/api/admin-schema.d.ts`
- `API_SPEC_URL` default to `http://localhost:8080/v3/api-docs/admin`

**Excerpt — `apps/web/lib/api/client.ts` lines 1–15 — openapi-fetch client idiom to fork:**
```ts
import createClient from 'openapi-fetch';
import { getApiBase } from './base-url';
import type { paths } from './schema';

export const api = createClient<paths>({
  baseUrl: getApiBase(),
  credentials: 'include',
});
```

**Deviation (multiple):**
1. No Next.js → no `app/` router, no RSC, no `proxy.ts`. React Router 6 for client-side routing.
2. No SSR → `index.html` + Vite-built static bundle served by NPM proxy from `apps/admin/dist/`.
3. No i18n (admin is English-only per UI-SPEC §Copywriting).
4. shadcn primitives are **copied** from `apps/web/components/ui/*` byte-identical (per UI-SPEC line 9 — same `base-nova` preset). Run `pnpm dlx shadcn@latest init` in `apps/admin` with matching `components.json` before copying.
5. `apps/web/AGENTS.md` "This is NOT the Next.js you know" caveat does NOT apply to `apps/admin` — but read it anyway to understand the project's caution about training-data drift; the same caution applies to Vite 7 + React 19.
6. Tailwind 4 CSS-variable theme — copy `globals.css` `.zm-proto` block from `apps/web/app/globals.css` and add admin-specific tokens (`--warning-soft`, `--ink-2`) for the ADMIN MODE banner.

**Privacy/security gate (ADMIN-06 acceptance):** `apps/web/next.config.ts` or its bundle analyzer must show zero import of `admin-schema.d.ts` or any file under `apps/admin/`. Enforce via:
- separate workspaces (no cross-workspace import via `@zeromail/admin/...`)
- ESLint rule `no-restricted-imports` blocking `**/apps/admin/**` from `apps/web` (planner should add it)

**WebAuthn client library:** `@simplewebauthn/browser` per RESEARCH §925 + SPEC ADMIN-10. Wraps `startRegistration({ optionsJSON })` and `startAuthentication({ optionsJSON })`. Look up current API via Context7 `/MasterKale/SimpleWebAuthn` before coding — the `optionsJSON` wrapper shape changed in v10.

---

### C17. ArchUnit rules (6 new) + grep gate (1) + sentinel-leak (1)

**New/modified files:**
- `backend/core/src/test/java/com/zeromail/core/admin/arch/AdminContextMutexTest.java` (new — ARCH-08)
- `backend/core/src/test/java/com/zeromail/core/admin/arch/AdminPathBodyBanTest.java` (new — ARCH-09)
- `backend/core/src/test/java/com/zeromail/core/admin/arch/AdminSendBanTest.java` (new — extends ARCH-10 to admin packages)
- `backend/core/src/test/java/com/zeromail/core/admin/arch/MasterKeyResolverConfinementTest.java` (new — MKEY-07)
- `backend/core/src/test/java/com/zeromail/core/admin/arch/AdminSpendPromptAccessorBanTest.java` (new — OPS-SPEND-02)
- `backend/core/src/test/java/com/zeromail/core/admin/arch/MasterKeySentinelLeakTest.java` (new — ARCH-11)
- `backend/api/src/test/java/com/zeromail/api/arch/AdminControllerPreAuthorizeTest.java` (new — D-09)
- `backend/api/src/test/java/com/zeromail/api/arch/AdminChainNoOauth2LoginTest.java` (new — D-09)
- `backend/api/src/test/java/com/zeromail/api/arch/AdminTenantOAuthGuardTest.java` (new — OPS-TENANT-05)

**Analog (body-ban / path-confinement):** `backend/core/src/test/java/com/zeromail/core/draft/DraftPathArchUnitTest.java` (full file, 135 lines).

**Excerpt — DraftPathArchUnitTest lines 31–73 — `noClasses().that().resideInAnyPackage(...).should(...)` shape with custom `ArchCondition`:**
```java
@Test
void draft_and_triage_paths_never_send_or_update_gmail_drafts() {
    noClasses()
        .that().resideInAnyPackage("..core.draft..", "..core.triage..")
        .should(new ArchCondition<JavaClass>(
                "call Gmail.Users.Messages.send, Gmail.Users.Drafts.send, or Gmail.Users.Drafts.update") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
                javaClass.getMethodCallsFromSelf().forEach(methodCall -> {
                    String ownerName = methodCall.getTargetOwner().getName().replace('$', '.');
                    String methodName = methodCall.getName();
                    boolean messagesSend = ownerName.endsWith("Gmail.Users.Messages")
                            && methodName.equals("send");
                    ...
                    if (messagesSend || draftsSendOrUpdate) {
                        conditionEvents.add(SimpleConditionEvent.violated(methodCall,
                                "Forbidden Gmail send/update call at " + methodCall.getSourceCodeLocation()));
                    }
                });
            }
        })
        .because("DRFT-04: Zero Mail may save drafts, but review/edit/send stay in Gmail.")
        .allowEmptyShould(true)
        .check(importProductionClasses());
}
```

**Excerpt — lines 77–89 — dependency-package ban (simpler shape, no custom condition):**
```java
ArchRule rule = noClasses()
        .that().resideInAnyPackage("..core.draft..", "..core.thread..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework.ai..")
        .because("Draft and thread code must use the pure-Java LlmGateway seam.")
        .allowEmptyShould(true);
rule.check(importProductionClasses());
```

**Adapted for `AdminPathBodyBanTest` (ARCH-09):**
```java
noClasses()
    .that().resideInAnyPackage("..controllers.admin..", "..core.admin..projection..", "..api.dto.admin..")
    .should(new ArchCondition<JavaClass>("reference body-like fields") {
        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            javaClass.getFields().forEach(f -> {
                if (f.getName().matches("(?i).*(body|bodyHtml|snippet|payload|prompt|completion|content).*")) {
                    events.add(SimpleConditionEvent.violated(f,
                        "Admin projection cannot expose body-like field: " + f.getFullName()));
                }
            });
            javaClass.getMethodCallsFromSelf().forEach(call -> {
                String calledName = call.getName();
                if (calledName.matches("(?i)get(Body|BodyHtml|Snippet|Payload|Prompt|Completion|Content).*")) {
                    events.add(SimpleConditionEvent.violated(call,
                        "Admin code cannot call body-exposing accessor: " + call.getSourceCodeLocation()));
                }
            });
        }
    })
    .because("ARCH-09: admin path bans email/chat/prompt content")
    .allowEmptyShould(true)
    .check(importProductionClasses());
```

**Analog (repository-whitelist):** `backend/core/src/test/java/com/zeromail/core/arch/TriageAuditRepositoryBoundaryArchTest.java` (full file, 60 lines) for `MasterKeyResolverConfinementTest` — limit reads of `LlmProviderMasterKeyRepository` to a single allow-listed class.

**Excerpt — TriageAuditRepositoryBoundaryArchTest lines 13–44:**
```java
static final Set<String> ALLOWED_TRIAGE_AUDIT_MUTATION_METHODS =
        Set.of("insertAuditPendingIfAbsent", "insertAuditTerminalIfAbsent",
               "reclaimStalePending", "markApplied", "markFailed",
               "markRevertPending", "markReverted");

private static final String TRIAGE_AUDIT_REPOSITORY = "com.zeromail.core.triage.persistence.TriageAuditRepository";
private static final Pattern AD_HOC_MUTATION_NAME = Pattern.compile("(?i).*(delete|update).*");

@Test
void triage_audit_repository_exposes_only_narrow_whitelisted_mutations() throws Exception {
    Class<?> repositoryClass = Class.forName(TRIAGE_AUDIT_REPOSITORY);
    Set<String> adHocMutationMethods = Arrays.stream(repositoryClass.getDeclaredMethods())
            .map(Method::getName)
            .filter(methodName -> AD_HOC_MUTATION_NAME.matcher(methodName).matches())
            .filter(methodName -> !ALLOWED_TRIAGE_AUDIT_MUTATION_METHODS.contains(methodName))
            .collect(java.util.stream.Collectors.toSet());
    assertThat(adHocMutationMethods)
        .as("triage_audit is insert-only except the explicit ... transitions")
        .isEmpty();
}
```

**Analog (single-call-site grep gate):** `backend/core/src/test/java/com/zeromail/core/arch/OnlyOneGmailSendCallSiteTest.java` — pattern for `MasterKeySentinelLeakTest` to scan logs + audit JSON + YAML for sentinel strings (`sk-`, `sk-ant-`, `AIza`, `sk-or-`).

**Deviation per rule:**
- `AdminContextMutexTest`: also include a unit-test-style assertion that `AdminContext.run(admin, () -> TenantContext.currentOrThrow())` throws `IllegalStateException` — not only ArchUnit.
- `AdminControllerPreAuthorizeTest`: import classes under `..controllers.admin..` and assert each `@RestController` class has a class-level `@PreAuthorize` annotation with value `hasRole('ADMIN')`. Pitfall 8 (RESEARCH §900): use `JavaClass.isAnnotatedWith(...)` not raw type names — lambda-captured types are common false-negative source.
- `AdminChainNoOauth2LoginTest`: assert `SecurityConfig` admin chain method body never calls `.oauth2Login(...)`; user chain method never calls `.webAuthn(...)`.
- `MasterKeySentinelLeakTest` is a **CI gate** not a pure ArchUnit rule — it runs after the test suite and scans `build/reports/`, `build/test-results/`, `build/logs/`, and `admin_audit_event` JSON dumps for the 4 sentinels. Fixture must include `sk-test123` insertion and assert the test fails.

**Privacy/security gates collected here:**
- `AdminPathBodyBanTest`: no body-like fields/accessors in admin code
- `AdminSendBanTest`: no Gmail send method references in admin packages (extends `OnlyOneGmailSendCallSiteTest` grep gate)
- `MasterKeyResolverConfinementTest`: only `core.llm.gateway.springai.admin.ProviderMasterKeyResolver` reads `LlmProviderMasterKeyRepository`
- `AdminSpendPromptAccessorBanTest`: spend controllers/services don't call `LlmCallAuditEntity.getPrompt*`/`getCompletion*`
- `MasterKeySentinelLeakTest`: no `sk-`/`sk-ant-`/`AIza`/`sk-or-` in logs/audit/YAML

---

## Shared Patterns

### Modulith vertical sub-package layout (applies to all `core.admin.{auth,audit,mkey,cat,tenant,queue,spend}`)

**Source:** existing `core.chat`, `core.gmail`, `core.triage`, `core.llm` modules + CLAUDE.md Convention 2 + CONVENTIONS.md.

**Layout per sub-package (vertical slice):**
```
core/admin/<sub>/
  domain/         — enums (IdentifiedEnum), value objects, events
  domain/event/   — Modulith-published events (records)
  usecases/       — service classes + commands + results
  projection/     — read-side records (no entity references)
  persistence/    — JPA @Entity + JpaRepository
  exception/      — business exceptions (extend RuntimeException)
  package-info.java — one-paragraph contract note
```

**Apply to:** All clusters C2–C13 backend new code.

### Privacy logging format (applies to ALL admin code)

**Source:** CLAUDE.md Convention 5 + every existing service in `backend/core/`.

**Excerpt — common pattern from `OAuthProvisioningService`:**
```java
log.info("event=oauth_no_refresh_token tenantId={}", tenantId);
```

**Apply to:** All admin services. Format: `event=admin_<verb_noun> actorAdminId={} targetTenantId={}` (no email, no key bytes, no body, no prompt). Even structured exceptions cannot carry email or key bytes — strip in the exception constructor.

### Spring Modulith event idiom (applies to MasterKeyRotatedEvent + CatalogChangedEvent)

**Source:** `backend/core/src/main/java/com/zeromail/core/gmail/event/MailMessageObserved.java` + listeners in `core.thread` + `core.triage`.

**Excerpt — MailMessageObserved entire file:**
```java
public record MailMessageObserved(
        UUID tenantId, String gmailMessageId, String gmailThreadId, Instant observedAt) {}
```

**Apply to:** Events in C7 (`MasterKeyRotatedEvent`) + C9 (`CatalogChangedEvent`). Records only; package = `core.admin.<sub>.domain.event`. Listeners use `@ApplicationModuleListener` per C8 example. Cross-process delivery (worker ↔ api) requires Postgres outbox — but Phase 8's `MasterKeyRotatedEvent` is intra-JVM only.

### DTO record convention (applies to all `api/dto/admin/...`)

**Source:** CLAUDE.md Convention 3 + `api/dto/llm/ByokSaveResponse.java` (entire file shown in C14).

**Apply to:** Every admin DTO record. Use `@Schema(requiredProperties = {...})` for always-present response fields, `@Schema(allowableValues = {...})` for closed string sets (e.g., `TestConnectionResponse.result` ∈ {OK,INVALID_KEY,RATE_LIMITED,NETWORK_ERROR,TIMEOUT}), `@JsonInclude(NON_NULL)` to suppress nullable absent fields. Static `from(...)` factory method maps from `core` result records.

### Enum convention (applies to all admin enums)

**Source:** CLAUDE.md Convention 4 (`IdentifiedEnum` / `OrderedEnum` with static `fromId`).

**Apply to:** `AdminStatus`, `LlmProvider`, `KeyFormat`, `Feature` (CHAT/TRIAGE/DRAFT), audit action enums. Never use `ordinal()` for storage or comparison.

---

## No Analog Found

| File / Pattern | Reason | Mitigation |
|----------------|--------|------------|
| Spring Security 7 `.webAuthn(...)` DSL configuration (C1, C3) | First WebAuthn use in repo. Project uses only OAuth2 today. | Plan-phase MUST pull `/websites/spring_io_spring-security_reference_7_0` via Context7 (per SPEC line 398) before any auth code. The DSL is a 6.4+ feature; training data may underrepresent it. |
| `CommandLineRunner` (C4) | Zero usages in repo (grep confirms). | Plan-phase research via Context7 `/spring-projects/spring-boot` for Boot 4 `CommandLineRunner` lifecycle semantics. STDOUT printing pattern (`System.out.println(...)` direct call bypassing SLF4J) has no precedent — document the choice inline. |
| Vite + React 19 SPA workspace (C16) | Only `apps/web` Next.js exists. | Conceptually fork `apps/web/scripts/generate-api.ts` (43 lines) + `apps/web/lib/api/client.ts` (55 lines). Run `pnpm dlx shadcn@latest init` in `apps/admin` with matching `base-nova` preset before copying primitives (UI-SPEC line 9). Add ESLint `no-restricted-imports` rule blocking cross-workspace imports from `apps/web`. |
| `@simplewebauthn/browser` ceremony wrapper (C16) | No WebAuthn frontend code in repo. | Plan-phase research via Context7 `/MasterKale/SimpleWebAuthn` — `optionsJSON` wrapper shape changed in v10. |
| 9Router + NPM in docker-compose.yml (out of cluster — OPS-INFRA) | Current compose has only `postgres` + `redis` services. | Plan-phase follows research §"Standard Stack" for the exact 9Router image (`decolua/9router:latest`) + NPM image (`jc21/nginx-proxy-manager`). Compose service additions are net-new; no analog. |

---

## Metadata

**Analog search scope:**
- `backend/api/src/main/java/com/zeromail/api/` (security, controllers, dto, config) — 18 files spot-checked
- `backend/core/src/main/java/com/zeromail/core/` (account, gmail, llm, triage, tenant, chat, thread) — 22 files spot-checked
- `backend/core/src/test/java/com/zeromail/core/` (arch + draft) — 4 ArchUnit files spot-checked
- `backend/core/src/main/resources/db/changelog/changes/` — 47 changesets listed; 042 (body-ban trigger) + 025 (triage-audit) deep-read
- `apps/web/lib/api/`, `apps/web/scripts/`, `apps/web/components/ui/` — 3 frontend files read
- `docker-compose.yml`, `turbo.json`, `pnpm-workspace.yaml` — top-level configs surveyed

**Files scanned:** ~60 production + test files; ~5 read in full; ~10 read in targeted ranges.

**Pattern extraction date:** 2026-05-19

**Downstream note for planner:**

- 8A foundation (C1+C2+C5+C6+C14 admin controllers skeleton+C15 OpenApi split+C17 ArchUnit rules) is a hard sequential gate before 8B/8C/8D/8E/8F per SPEC line 405-408.
- C4 (BootstrapRunner) sits inside 8A because Phase 8 cannot run any admin flow end-to-end without enrolled admin.
- C16 (apps/admin Vite scaffold) can start in parallel with 8A backend work — the OpenAPI admin spec doesn't need to be final for the workspace scaffolding + shadcn primitive copy + `apps/web` ESLint guard to land.
- C8 + C10 (catalog Sync + ChatModel cache eviction) couples 8B (master keys) ↔ 8D (catalog) — the `MasterKeyRotatedEvent` listener must exist before MKEY-04 acceptance lands, but the actual eviction can be a stub on day 1.
- C12 (`AdminResponseBodyBanFilter`) is independent of 8A — can run after 8C tenant projections land, since it's a backstop for OPS-TENANT-04.

*Phase: 08-admin-console-operator-tooling*
*Pattern mapping completed: 2026-05-19*
*Next step: `gsd-planner` consumes this file alongside SPEC + RESEARCH + UI-SPEC to produce PLAN.md (or 8A.PLAN … 8F.PLAN split per D-claude-discretion line 112).*
