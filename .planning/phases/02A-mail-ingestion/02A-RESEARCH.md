# Phase 02A: Mail Ingestion — Research

**Researched:** 2026-04-28
**Domain:** Gmail Pub/Sub push pipeline, OIDC verification, PostgreSQL SKIP LOCKED queue, watch lifecycle, global pause toggle
**Confidence:** HIGH overall (codebase fully verified; library APIs confirmed via official docs + GitHub source)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-A1:** Ack-fast + Postgres handoff. Controller does OIDC verify + tenant lookup + dedup INSERT + 200 OK only. Gmail API calls in worker.
- **D-A2:** Single shared Pub/Sub topic + subscription for all tenants.
- **D-A3:** Push endpoint `/internal/pubsub/gmail` — `permitAll` on user OAuth chain, gated by custom `PubSubOidcAuthFilter`.
- **D-A4:** Tenant lookup by `LOWER(google_email)`. Unknown email → 200 OK + drop.
- **D-A5:** Two idempotency layers: `UNIQUE (tenant_id, pubsub_message_id)` on `pubsub_delivery`; `PRIMARY KEY (tenant_id, gmail_message_id)` on `mail_message_observed`. Both `ON CONFLICT DO NOTHING`.
- **D-B1:** Extend `gmail_connections` with ingestion-state columns (no new join table in v1).
- **D-B2:** Two tables, two invariants: `pubsub_delivery` (ingress queue + Pub/Sub dedup) and `mail_message_observed` (per-Gmail-message audit log).
- **D-B3:** `mail_message_observed` is privacy-floor. NO subject/from/body/snippet. `label_ids TEXT[]` is acceptable.
- **D-B4:** `messagesAdded` only, INBOX-filtered.
- **D-B5:** Monotonic-conditional `last_synced_history_id` UPDATE.
- **D-B6:** Bounded history window — 500 items max, log gap truncations.
- **D-C1:** Watch registration is async via `GmailWatchScheduler`, NOT inside OAuth success handler.
- **D-C2:** `@Scheduled(cron = "0 * * * * *")` (every minute) for scheduler. 24h renewal margin.
- **D-C3:** `labelIds = ['INBOX']` only, `labelFilterBehavior = 'include'`.
- **D-C4:** 3 consecutive watch failures → `ingestion_health = WATCH_UNHEALTHY`.
- **D-C5:** `users.stop()` best-effort on disconnect/deletion.
- **D-D1:** `GmailIngestionHealth` — new `IdentifiedEnum` with `HEALTHY`, `WATCH_UNHEALTHY`, `HISTORY_LOST`. `GmailConnectionStatus` UNCHANGED.
- **D-D2:** History-404 → advance pointer to `webhook_history_id`, set `HISTORY_LOST`, mark delivery `PROCESSED`.
- **D-D3:** ReconnectPrompt unified gate lives at the settings-page parent mount site: render for `status == DISCONNECTED` OR `(status == CONNECTED AND ingestionHealth != HEALTHY)`. Single copy, single CTA. `NOT_CONNECTED` keeps the initial connect CTA.
- **D-D4:** Reconnect handler clears `watch_*` columns + resets `ingestion_health = HEALTHY` + `watch_consecutive_failures = 0`.
- **D-E1:** `tenants.triage_paused BOOLEAN NOT NULL DEFAULT false` (one column, not a settings table).
- **D-E2:** Pause gate semantic = Phase 2A persists + exposes; Phase 4 reads at enqueue time.
- **D-E3:** `PUT /tenant/triage-pause` body `{"paused": boolean}`.
- **D-E4:** Extend `/me` response with `triagePaused` + `gmailConnectionStatus`.
- **D-E5:** Settings toggle + non-dismissible `PauseBanner` in `(protected)/layout.tsx`. `useToggleTriagePause` invalidates `me` key.

### Claude's Discretion

- Full Pub/Sub envelope vs decoded payload in `pubsub_delivery.payload JSONB` — recommend full envelope.
- Worker module shape: single `mail-ingestion` module (not split) — recommend single.
- Spring Security filter shape: `SecurityFilterChain @Order(1)` vs `OncePerRequestFilter` only — researcher recommends `SecurityFilterChain @Order(1)` (see §Architecture Patterns).
- `GmailHistoryProcessor` retry classification — documented in §Common Pitfalls.
- Token refresh: direct POST to `https://oauth2.googleapis.com/token` (not `OAuth2AuthorizedClientService`).
- `watch_consecutive_failures` column on `gmail_connections` (durable, not in-memory).
- Pub/Sub topic + subscription provisioning: manual + RUNBOOK.md for v1.
- `PUBSUB_PUSH_AUDIENCE_URL` + `PUBSUB_SA_PRINCIPAL_EMAIL` env vars with `:?` fail-fast.
- `mail_message_observed.label_ids` as `TEXT[]`.
- `mail_message_observed` should also store `internal_date BIGINT` (Gmail-side timestamp, useful for Phase 4 ordering, no privacy violation).
- Event log granularity: per-batch (`event=gmail_history_processed`), per-state-change for health events.
- Endpoint base path: `/internal/pubsub/gmail` (not proxied through Next.js, firewalled at reverse proxy).
- No Spring application event for `mail_message_observed` — Phase 4 polls directly.

### Deferred Ideas (OUT OF SCOPE)

- Phase 4 triage_job enqueue + write-action gate.
- LLM/triage/rules engine.
- BYOK, billing.
- Sent-side / Reply-Tracker.
- Label-change observation.
- Per-tenant Pub/Sub topology.
- GCP-side DLQ.
- Pub/Sub IaC.
- Multi-account/workspace.
- `tenant_settings` table.
- Per-message event logs.
- Sentry/OTel browser SDK.
- SSE/WebSocket live stream.
- Full mailbox import / `messages.list` rescan.
- History pagination beyond 500.
- Webhook signing secret rotation.
- Retention/purge of `pubsub_delivery` rows.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MAIL-01 | System registers `users.watch` on Gmail connect and processes Pub/Sub push notifications | `GmailWatchScheduler` (async post-connect, every minute) + `GmailPubSubController` (push receiver) — fully documented in §Architecture Patterns |
| MAIL-02 | Daily scheduled job renews `users.watch` before 7-day expiry with per-tenant health alerting | Unified `GmailWatchScheduler` handles BOTH initial registration AND renewal via NULL-or-near-expiry query; 3-strike → `WATCH_UNHEALTHY` |
| MAIL-03 | Pub/Sub push receiver verifies Google OIDC tokens on every request | `PubSubOidcAuthFilter` using `TokenVerifier` from `google-auth-library-oauth2-http 1.35.0` — full API documented in §Code Examples |
| MAIL-04 | Message processing is idempotent per `(tenantId, historyId, messageId)` — duplicate deliveries are safe | Two `ON CONFLICT DO NOTHING` layers: `pubsub_delivery` UNIQUE + `mail_message_observed` PRIMARY KEY; monotonic `last_synced_history_id` update |
| MAIL-05 | History-404 recovery is bounded (no full mailbox rescan) and surfaces user-visible reconnect prompt | D-D2: advance pointer to `webhook_history_id`, set `HISTORY_LOST`; D-D3: unified `ReconnectPrompt` gate |
| MAIL-06 | User can globally pause all automated triage actions from the UI | `tenants.triage_paused`, `PUT /tenant/triage-pause`, `PauseBanner`, `useToggleTriagePause` — Phase 2A stores + exposes; Phase 4 reads at enqueue |
</phase_requirements>

---

## Summary

Phase 2A wires the complete Gmail ingress pipeline. The architecture is an ack-fast push receiver (controller is pure OIDC-verify + DB-insert + 200), a Postgres-backed SKIP LOCKED worker for history fan-out, and a minute-tick scheduler for watch lifecycle. Three privacy-correct tables capture state: `pubsub_delivery` (ingress queue), `mail_message_observed` (audit log, no email content), and `tenants.triage_paused` (one-bit pause flag).

The most technically demanding deliverable is the `PubSubOidcAuthFilter`: a separate `SecurityFilterChain @Bean @Order(1)` that runs BEFORE the user OAuth chain, verifies Google OIDC tokens using `TokenVerifier` from `google-auth-library-oauth2-http 1.35.0`, and returns 401 on any mismatch without binding `TenantContext`. This closes the deferred ceremony from Phase 01.5 D-D5.

Token refresh in the worker context (no `Authentication` principal in scheduler thread) is handled by a direct POST to `https://oauth2.googleapis.com/token` using the decrypted refresh token from `RefreshTokenCipher`. This is the correct pattern for headless worker scenarios; `OAuth2AuthorizedClientService` is designed for request-scoped OAuth flows and cannot be used in scheduled worker threads without significant workarounds.

**Primary recommendation:** Use a dedicated `SecurityFilterChain @Order(1)` with `securityMatcher("/internal/pubsub/**")` for the push endpoint. Add `PubSubOidcAuthFilter` as a `BeforeFilter` within that chain. Keep the existing user OAuth chain unchanged.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Pub/Sub OIDC token verification | API (Spring filter) | — | Machine-to-machine; must reject before any business logic |
| Push payload dedup INSERT | API controller | — | Controller owns the ack-fast response; DB owns idempotency |
| History fan-out + `mail_message_observed` | Worker (scheduled) | Core (service) | Gmail API calls belong in worker; business logic in core service |
| Watch registration + renewal | Worker (scheduled) | Core (service) | Async post-connect; no Gmail API in login critical path |
| `users.stop()` on disconnect | Core (GmailConnectionService) | API (DisconnectController) | Service owns connection state; controller delegates |
| Global pause toggle | API (`PUT /tenant/triage-pause`) | Core (TenantService) | Standard controller→service pattern; Phase 4 reads the flag |
| Pause flag read at triage enqueue | Worker (Phase 4, out of scope) | — | Phase 2A only stores; Phase 4 reads |
| `PauseBanner` conditional render | Frontend (`(protected)/layout.tsx`) | — | Per-layout server render; reads `me` query cache |
| `ReconnectPrompt` gate extension | Frontend settings page + gmail feature | — | Existing component is presentational; extend settings-page parent mount condition |
| OpenAPI schema codegen | API (`springdoc-openapi-gradle-plugin`) | Frontend (`pnpm generate:api`) | Extend `MeResponse` + add pause endpoint |

---

## Standard Stack

### Core Backend

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.google.auth:google-auth-library-oauth2-http` | **1.35.0** | OIDC `TokenVerifier` for Pub/Sub push verification | Locked in CLAUDE.md; official Google auth library for server-side OIDC |
| `com.google.apis:google-api-services-gmail` | **v1-rev20250331-2.0.0** | `users.watch`, `users.history.list`, `users.stop` | Locked in CLAUDE.md; official generated Gmail REST client |
| `com.google.api-client:google-api-client` | transitive | HTTP transport for Gmail client | Pulled by `google-api-services-gmail` |
| Spring Boot 4.0.6 BOM (existing) | managed | All Spring starters | Already declared |
| PostgreSQL 17.6 self-hosted | existing | Primary datastore | Locked |

All of the above are already declared in `gradle/libs.versions.toml` per CLAUDE.md. No new top-level dependencies needed. [VERIFIED: codebase grep on libs.versions.toml + CLAUDE.md]

### Env Vars (new, fail-fast)

| Variable | Consumed By | Pattern |
|----------|-------------|---------|
| `PUBSUB_PUSH_AUDIENCE_URL` | `PubSubOidcAuthFilter`, `backend/api` application.yml | `:?` fail-fast (Phase 01.5 P08) |
| `PUBSUB_SA_PRINCIPAL_EMAIL` | `PubSubOidcAuthFilter` | `:?` fail-fast |
| `GOOGLE_PUBSUB_TOPIC_NAME` | `GmailWatchScheduler`, `backend/worker` application.yml | `:?` fail-fast |

---

## Architecture Patterns

### System Architecture Diagram

```
Google Pub/Sub
    |
    | HTTP POST (OIDC-signed)
    v
[PubSubOidcAuthFilter @Order(1)]
    | 401 on any OIDC failure (never reaches business logic)
    v
[GmailPubSubController POST /internal/pubsub/gmail]
    | decode base64url data -> {emailAddress, historyId}
    | lookup gmail_connections WHERE LOWER(google_email)=LOWER(emailAddress)
    | unknown email -> 200 OK + drop
    | ScopedValue.where(TENANT, tenantId).run(...)
    | INSERT pubsub_delivery ON CONFLICT DO NOTHING
    | -> 200 OK immediately
    v
[pubsub_delivery table] (PENDING rows)
    |
    | every ~1s
    v
[GmailHistoryProcessor @Scheduled(fixedDelay=1000)]
    | SELECT ... FOR UPDATE SKIP LOCKED LIMIT 50
    | per row: bind TenantContext, decrypt refresh token (RefreshTokenCipher)
    | POST https://oauth2.googleapis.com/token -> access_token
    |   401 invalid_grant -> flip status=DISCONNECTED, mark DEAD
    | gmail.users().history().list(startHistoryId, historyTypes=[messageAdded], maxResults=500)
    |   404 -> HISTORY_LOST flow (advance pointer, mark PROCESSED)
    |   500/429 -> increment attempts; 3 fails -> DEAD
    | for each messagesAdded where INBOX in labelIds:
    |   INSERT mail_message_observed ON CONFLICT DO NOTHING
    | UPDATE gmail_connections last_synced_history_id (monotonic-conditional)
    | UPDATE pubsub_delivery status=PROCESSED
    v
[mail_message_observed table] (append-only, privacy-floor)

[GmailWatchScheduler @Scheduled(cron="0 * * * * *")]
    | SELECT ... FROM gmail_connections WHERE status=CONNECTED
    |   AND (watch_expires_at IS NULL OR watch_expires_at < NOW() + INTERVAL '24 hours')
    |   FOR UPDATE SKIP LOCKED LIMIT 50
    | per row: bind TenantContext, decrypt + refresh token
    | gmail.users().watch(userId='me', labelIds=['INBOX'], topicName=env)
    |   success -> persist watch_history_id, watch_expires_at, watch_renewed_at, HEALTHY
    |   failure -> increment watch_consecutive_failures
    |   3 failures -> ingestion_health=WATCH_UNHEALTHY

[PUT /tenant/triage-pause]
    | body: {paused: boolean}
    | TenantService.setTriagePaused(boolean) under TenantContext
    | audit: event=triage_pause_toggled tenantId={} paused={}
    | extends /me response: triagePaused + gmailConnectionStatus

Frontend (apps/web)
    | GET /me -> triagePaused, gmailConnectionStatus.{status, ingestionHealth, googleEmail}
    | (protected)/layout.tsx: conditional <PauseBanner> when triagePaused
    | settings/page.tsx: toggle -> useToggleTriagePause -> invalidates me key
    | Settings page: ReconnectPrompt shown when DISCONNECTED or CONNECTED+unhealthy
```

### Recommended Project Structure

New files per module:

```
backend/api/src/main/java/com/zeromail/api/
├── controllers/
│   ├── GmailPubSubController.java        # NEW: push receiver
│   └── TriagePauseController.java        # NEW: PUT /tenant/triage-pause
├── security/
│   ├── PubSubOidcAuthFilter.java         # NEW: OIDC filter
│   ├── PubSubSecurityConfig.java         # NEW: SecurityFilterChain @Order(1)
│   └── SecurityConfig.java              # MODIFIED: @Order(2) existing chain
├── dto/account/
│   └── MeResponse.java                  # MODIFIED: + triagePaused + gmailConnectionStatus

backend/core/src/main/java/com/zeromail/core/
├── gmail/
│   ├── model/
│   │   └── GmailIngestionHealth.java     # NEW: IdentifiedEnum
│   ├── persistence/
│   │   ├── GmailConnectionEntity.java    # MODIFIED: +6 ingestion-state columns
│   │   ├── PubSubDeliveryEntity.java     # NEW
│   │   ├── PubSubDeliveryRepository.java # NEW
│   │   ├── MailMessageObservedEntity.java# NEW
│   │   └── MailMessageObservedRepository.java # NEW
│   └── service/
│       └── GmailConnectionService.java   # MODIFIED: +markHistoryLost, +markWatchUnhealthy,
│                                         #           +clearForReconnect, +recordWatchSuccess
│                                         #           +disconnect extends with users.stop()
├── tenant/
│   ├── persistence/
│   │   └── TenantEntity.java            # MODIFIED: +triage_paused
│   └── service/
│       └── TenantService.java           # MODIFIED: +setTriagePaused(boolean)

backend/worker/src/main/java/com/zeromail/worker/
├── GmailWatchScheduler.java             # NEW: @Scheduled(cron="0 * * * * *")
└── GmailHistoryProcessor.java           # NEW: @Scheduled(fixedDelay=1000)

backend/core/src/main/resources/db/changelog/changes/
├── 010-gmail-ingestion-state.yaml       # NEW: ALTER TABLE gmail_connections
├── 011-pubsub-delivery-table.yaml       # NEW: CREATE TABLE pubsub_delivery
├── 012-mail-message-observed-table.yaml # NEW: CREATE TABLE mail_message_observed
└── 013-tenants-triage-paused.yaml       # NEW: ALTER TABLE tenants

apps/web/features/triage/              # NEW feature folder (recommended)
├── api/triagePause.ts
├── components/PauseBanner.tsx
└── hooks/useToggleTriagePause.ts
apps/web/features/gmail/
└── components/ReconnectPrompt.tsx      # Reused presentational alert; settings page owns mount gate
apps/web/features/account/api/me.ts    # MODIFIED: extend CurrentUser interface
apps/web/app/(protected)/
├── layout.tsx                          # MODIFIED: +PauseBanner conditional
└── settings/page.tsx                  # MODIFIED: +pause toggle Card section
```

### Pattern 1: Dual SecurityFilterChain for Pub/Sub OIDC

The idiomatic Spring Security 7 approach for a machine-to-machine endpoint alongside a user OAuth chain is two `@Bean` filter chains with `@Order`:

```java
// Source: Spring Security 7 reference + current SecurityConfig.java verified
@Configuration
public class PubSubSecurityConfig {

    @Bean
    @Order(1)                        // Evaluated BEFORE user OAuth chain
    public SecurityFilterChain pubSubFilterChain(HttpSecurity http,
                                                  PubSubOidcAuthFilter oidcFilter) throws Exception {
        http
            .securityMatcher("/internal/pubsub/**")
            .csrf(csrf -> csrf.disable())            // Machine-to-machine: no CSRF
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())  // Filter owns authN
            .addFilterBefore(oidcFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

// Existing SecurityConfig becomes @Order(2) — add annotation, no other change
@Configuration
@Order(2)                            // Drop @Profile("!test") if using @Order
@Profile("!test")
public class SecurityConfig { ... }
```

**Key insight:** `securityMatcher("/internal/pubsub/**")` ensures this chain ONLY intercepts the push endpoint. The `permitAll()` is correct — the filter itself provides authentication; Spring Security just needs to not block it at the `authorizeHttpRequests` layer. [VERIFIED: Spring Security reference docs + existing SecurityConfig.java pattern]

**Why not `OncePerRequestFilter` on the existing chain?** The existing chain uses `permitAll()` only on specific user-facing paths. Adding the push endpoint to `permitAll()` there would skip security context setup. A separate chain with `@Order(1)` is cleaner and avoids polluting the user chain. [ASSUMED — based on Spring Security pattern analysis; no Spring 7.0.5-specific doc contradiction found]

### Pattern 2: TokenVerifier Setup (google-auth-library-oauth2-http 1.35.0)

```java
// Source: https://github.com/googleapis/google-auth-library-java/blob/main/oauth2_http/java/com/google/auth/oauth2/TokenVerifier.java
// Source: https://github.com/googleapis/google-auth-library-java/blob/main/samples/snippets/src/main/java/VerifyGoogleIdToken.java

public class PubSubOidcAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PubSubOidcAuthFilter.class);

    // Build once at startup; JWKS caching is internal to TokenVerifier
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
            // Verify email claim against configured SA principal
            String email = (String) jws.getPayload().get("email");
            if (!expectedEmail.equalsIgnoreCase(email)) {
                log.warn("event=pubsub_oidc_wrong_email");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            // Stash verified SA email in request attribute for controller sanity check
            request.setAttribute("pubsub.verified.email", email);
            chain.doFilter(request, response);
        } catch (TokenVerifier.VerificationException e) {
            // Messages: "Expected audience does not match", "Token is expired",
            //           "Expected issuer does not match", "Invalid signature"
            log.warn("event=pubsub_oidc_verification_failed");  // No token content
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
```

Cycle 3 review correction: do not make `PubSubOidcAuthFilter` a `@Component`. Define it as a bean in `PubSubSecurityConfig`, disable global servlet registration with `FilterRegistrationBean#setEnabled(false)`, and keep `shouldNotFilter` as defense in depth.

**VerificationException causes:** [VERIFIED: github.com/googleapis/google-auth-library-java TokenVerifier.java source]
- `"Expected audience does not match"` — wrong `aud` claim
- `"Token is expired"` — `exp` claim in the past
- `"Expected issuer does not match"` — not `https://accounts.google.com`
- `"Invalid signature"` — tampered or wrong key
- `"Unexpected signing algorithm: expected either RS256 or ES256"` — malformed JWT

**Pub/Sub OIDC `aud` claim:** The `aud` claim equals the custom audience URL configured on the Pub/Sub subscription (`--push-auth-token-audience`). For this app, set `PUBSUB_PUSH_AUDIENCE_URL` to the public HTTPS push endpoint URL (e.g., `https://zeromail.app/internal/pubsub/gmail`). [VERIFIED: cloud.google.com/pubsub/docs/authenticate-push-subscriptions]

**Testing:** Override `.setCertificatesLocation(mockJwksUrl)` in tests. Sign synthetic ID tokens with a generated RSA keypair. Assert 401 on: wrong aud, wrong email, expired exp, bad signature, wrong iss. Use WireMock or `MockWebServer` to serve the mock JWKS.

### Pattern 3: Pub/Sub Push Payload Binding (Jackson 3)

```java
// Pub/Sub envelope: {message: {data: base64url, messageId, publishTime, attributes}, subscription}
// Source: cloud.google.com/pubsub/docs/push + Jackson 3 record deserialization

public record PubSubPushEnvelope(
    PubSubMessage message,
    String subscription
) {
    public record PubSubMessage(
        String data,         // base64url-encoded JSON {emailAddress, historyId}
        String messageId,
        String publishTime,
        Map<String, String> attributes
    ) {}

    /** Decode data field -> GmailNotification */
    public GmailNotification decodeData() {
        // data is base64url; normalize to base64 standard for Java's Base64.getDecoder
        byte[] bytes = Base64.getUrlDecoder().decode(message.data());
        // ... parse JSON -> GmailNotification
    }
}

public record GmailNotification(String emailAddress, long historyId) {}
```

**Jackson 3 records:** Records deserialize automatically without `@JsonProperty` when field names match JSON keys. Null `attributes` map: annotate `Map<String, String>` with `@JsonInclude(Include.NON_NULL)` on the record component or use `@JsonProperty(defaultValue)`. historyId may arrive as either string or long — use `@JsonDeserialize(using = ...)` or accept Object + coerce. [VERIFIED: inbox-zero `decodeHistoryId` shows this same dual-type concern]

**Recommendation for `payload` column:** Store the full raw Pub/Sub envelope JSON in `pubsub_delivery.payload JSONB` for replay/debug capability. Bounded size (~1–2 KB). The decoded `{emailAddress, historyId}` lives in explicit columns for the worker query.

### Pattern 4: SKIP LOCKED Worker with ScopedValue

```java
// Source: CLAUDE.md SKIP LOCKED pattern + Java 25 JEP-464 ScopedValue semantics

@Scheduled(fixedDelay = 1_000L)  // 1s after previous tick completes
public void processTick() {
    // NOTE: @Scheduled on virtual threads with fixedDelay runs on same thread per tick
    // Each tick should be a single transaction to bound the work unit
    List<PubSubDeliveryEntity> batch = deliveryRepository.claimPendingBatch(50);
    for (PubSubDeliveryEntity delivery : batch) {
        // Bind TenantContext PER ROW - scheduler thread does NOT auto-bind
        ScopedValue.where(TenantContext.TENANT, delivery.getTenantId().toString())
                   .run(() -> processSingleDelivery(delivery));
    }
}

// Repository: claimPendingBatch uses SKIP LOCKED
@Query(value = """
    SELECT * FROM pubsub_delivery
    WHERE status = 'PENDING'
    ORDER BY created_at
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
@Transactional
List<PubSubDeliveryEntity> claimPendingBatch(@Param("limit") int limit);
```

**ScopedValue on scheduled threads:** `@Scheduled` threads do NOT inherit the HTTP-request `TenantContext.TENANT` binding. The `ScopedValue.where(...).run(...)` must be called explicitly per-row before any DB or Gmail API call. [VERIFIED: Java 25 JEP-464 ScopedValue semantics; ScopedValues are not inherited by non-child threads]

**fixedDelay vs fixedRate with virtual threads:** Spring Framework issue #31900 documents that `fixedDelay` tasks may serialize on the same virtual thread under certain configurations. This is acceptable for the worker: one tick processes a batch of 50 and returns; the next tick starts 1s later. For the watch scheduler using `cron`, each invocation gets its own thread. [CITED: github.com/spring-projects/spring-framework/issues/31900]

### Pattern 5: Gmail API Call Shapes

**`users.watch`:**

```java
// Source: developers.google.com/gmail/api/reference/rest/v1/users/watch (verified)
WatchRequest watchRequest = new WatchRequest()
    .setLabelIds(List.of("INBOX"))
    .setLabelFilterBehavior("include")
    .setTopicName(googlePubsubTopicName);  // e.g., "projects/my-project/topics/gmail-push"

WatchResponse watchResponse = gmail.users()
    .watch("me", watchRequest)
    .execute();

long watchHistoryId = watchResponse.getHistoryId();
long expirationMs = watchResponse.getExpiration();  // epoch ms
Instant expiresAt = Instant.ofEpochMilli(expirationMs);
```

**Response:** `historyId` (baseline for sync start), `expiration` (epoch ms when watch expires, ~7 days from call). [VERIFIED: Gmail API reference]

**Idempotency on re-call:** Re-calling `users.watch` on an already-watched account replaces the previous watch with a new one and returns a fresh `historyId` and `expiration`. This is the intended renewal mechanism. [ASSUMED — no explicit doc statement, but Inbox-zero pattern + Gmail API behavior implies replace-not-fail; worker uses this for renewal]

**`users.history.list`:**

```java
// Source: developers.google.com/gmail/api/reference/rest/v1/users.history/list (verified)
ListHistoryResponse historyResponse = gmail.users()
    .history()
    .list("me")
    .setStartHistoryId(BigInteger.valueOf(startHistoryId))
    .setHistoryTypes(List.of("messageAdded"))
    .setMaxResults(500L)
    // Do NOT setLabelId here — labelId filters the returned messages by label,
    // but we filter manually to avoid missing multi-label messages
    .execute();

List<History> historyList = historyResponse.getHistory();       // may be null if empty
String nextPageToken = historyResponse.getNextPageToken();      // non-null if more pages

// Check for INBOX in each message's labelIds
for (History h : historyList != null ? historyList : List.of()) {
    for (HistoryMessageAdded added : h.getMessagesAdded() != null ? h.getMessagesAdded() : List.of()) {
        Message msg = added.getMessage();
        if (msg.getLabelIds() != null && msg.getLabelIds().contains("INBOX")) {
            // Insert into mail_message_observed
        }
    }
}
```

**404 handling:** A 404 from `history.list` means the `startHistoryId` is expired (older than Gmail's ~7-day window). The HTTP exception from the generated client is a `GoogleJsonResponseException` with `getStatusCode() == 404`. [VERIFIED: Gmail API reference + inbox-zero `isHistoryIdExpiredError` pattern]

**`users.stop`:**

```java
// Best-effort — no response body
try {
    gmail.users().stop("me").execute();
} catch (GoogleJsonResponseException e) {
    // 400 if token already revoked; ignore on disconnect path
    log.warn("event=gmail_watch_stop_failed tenantId={}", tenantId);
}
```

### Pattern 6: Token Refresh (Worker Context — No `Authentication`)

```java
// Source: developers.google.com/identity/protocols/oauth2/web-server#offline (verified)
// No OAuth2AuthorizedClientService — scheduled workers have no Authentication in context

public record TokenRefreshResult(String accessToken, Instant expiresAt) {}

public TokenRefreshResult refreshAccessToken(String decryptedRefreshToken) throws IOException {
    HttpClient httpClient = HttpClient.newHttpClient();
    String body = "grant_type=refresh_token"
        + "&client_id=" + URLEncoder.encode(clientId, UTF_8)
        + "&client_secret=" + URLEncoder.encode(clientSecret, UTF_8)
        + "&refresh_token=" + URLEncoder.encode(decryptedRefreshToken, UTF_8);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://oauth2.googleapis.com/token"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();

    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    if (response.statusCode() == 200) {
        // Parse JSON -> access_token, expires_in
        // ...
        return new TokenRefreshResult(accessToken, Instant.now().plusSeconds(expiresIn - 60));
    }

    // 400 with body {"error": "invalid_grant"} -> token revoked
    if (response.statusCode() == 400 && response.body().contains("invalid_grant")) {
        throw new InvalidGrantException("OAuth token revoked for tenant");
    }
    throw new IOException("Token refresh failed: " + response.statusCode());
}
```

**Success response:** `{access_token, expires_in (seconds), token_type: "Bearer", scope}`. [VERIFIED: Google OAuth docs]
**`invalid_grant` → flip `status=DISCONNECTED`** on the `gmail_connections` row; log `event=gmail_oauth_revoked tenantId={}`. [VERIFIED: standard pattern; confirmed by inbox-zero `"invalid_grant"` check]

### Pattern 7: Hibernate 7 TEXT[] Mapping

```java
// Source: Hibernate 7 SqlTypes.ARRAY + PostgreSQL dialect native array support
// Verified: Hibernate 6/7 natively supports String[] -> PostgreSQL text[] via @JdbcTypeCode

@JdbcTypeCode(SqlTypes.ARRAY)
@Column(name = "label_ids", columnDefinition = "text[]", nullable = false)
private String[] labelIds;
```

Hibernate 7 (via Spring Boot 4.0.6 BOM) natively supports PostgreSQL array types through the PostgreSQL dialect's `supportsStandardArrays()`. No `hypersistence-utils` or `@Type` annotation needed for `String[]`. [VERIFIED: Hibernate ORM 6.0 User Guide + SqlTypes Javadocs + search results confirming native support]

**Integration test:** assert round-trip via `JdbcTemplate` that a multi-element `label_ids` array stores and retrieves correctly. This is pitfall P-04.

### Pattern 8: Liquibase 5.0.2 YAML Changeset Shapes

**`010-gmail-ingestion-state.yaml` — `addColumn` with NOT NULL DEFAULT:**

```yaml
databaseChangeLog:
  - changeSet:
      id: 010-gmail-ingestion-state
      author: zeromail
      changes:
        - addColumn:
            tableName: gmail_connections
            columns:
              - column: { name: last_synced_history_id, type: bigint }
              - column: { name: watch_history_id, type: bigint }
              - column: { name: watch_expires_at, type: timestamptz }
              - column: { name: watch_renewed_at, type: timestamptz }
              - column: { name: watch_consecutive_failures, type: int,
                          defaultValueNumeric: 0,
                          constraints: { nullable: false } }
              - column: { name: ingestion_health, type: varchar(32),
                          defaultValue: HEALTHY,
                          constraints: { nullable: false } }
      rollback:
        - dropColumn: { tableName: gmail_connections, columnName: last_synced_history_id }
        - dropColumn: { tableName: gmail_connections, columnName: watch_history_id }
        - dropColumn: { tableName: gmail_connections, columnName: watch_expires_at }
        - dropColumn: { tableName: gmail_connections, columnName: watch_renewed_at }
        - dropColumn: { tableName: gmail_connections, columnName: watch_consecutive_failures }
        - dropColumn: { tableName: gmail_connections, columnName: ingestion_health }
```

**Note:** `defaultValue` (string) vs `defaultValueNumeric` (int/bigint/numeric) vs `defaultValueComputed` (SQL expression like `now()`). Liquibase distinguishes these. For `BOOLEAN NOT NULL DEFAULT false`, use `defaultValueBoolean: false`. [VERIFIED: existing changeset 007 pattern in codebase; Liquibase 5.0.2 YAML syntax]

**`013-tenants-triage-paused.yaml`:**

```yaml
        - addColumn:
            tableName: tenants
            columns:
              - column: { name: triage_paused, type: boolean,
                          defaultValueBoolean: false,
                          constraints: { nullable: false } }
```

**`db.changelog-master.yaml` `includeAll` auto-picks:** Confirmed — current master uses `includeAll: path: classpath:db/changelog/changes/` with `relativeToChangelogFile: false`. No master file edit needed when adding `010-013` files; Liquibase picks them alphabetically. [VERIFIED: read `.planning/.../db.changelog-master.yaml`]

**Last existing changeset:** `009-drop-signed-in-onboarding-step.yaml` — confirmed by filesystem scan. Phase 2A adds `010-013`. [VERIFIED: codebase filesystem]

### Pattern 9: GmailIngestionHealth as IdentifiedEnum

```java
// Source: Phase 01.2.1 IdentifiedEnum contract + existing GmailConnectionStatus.java pattern
package com.zeromail.core.gmail.model;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum GmailIngestionHealth implements IdentifiedEnum {
    HEALTHY,
    WATCH_UNHEALTHY,
    HISTORY_LOST;

    // D-B3 default labelKey: GmailIngestionHealth.HEALTHY etc.
    // D-C2 invariant: id() == name() == stored DB value

    public static GmailIngestionHealth fromId(String id) {
        return Stream.of(values())
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown GmailIngestionHealth id: " + id));
    }
}
```

**Storage:** `@Enumerated(EnumType.STRING)` on `GmailConnectionEntity.ingestionHealth`. DB column `varchar(32)` with `DEFAULT 'HEALTHY'`. `fromId` is used when reading from raw DB results outside JPA (e.g., in worker JPQL projections).

### Pattern 10: Frontend Feature Structure

```typescript
// apps/web/features/triage/ (new feature folder, D-E5)
// Follows Phase 01.3 feature-folder convention: deep imports, no barrel index.ts

// apps/web/features/triage/api/triagePause.ts
import { api } from '@/lib/api/client';
export async function setTriagePaused(paused: boolean) {
  const { data, error } = await api.PUT('/tenant/triage-pause', { body: { paused } });
  if (error) throw error;
  return data;
}

// apps/web/features/triage/hooks/useToggleTriagePause.ts
'use client';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { setTriagePaused } from '@/features/triage/api/triagePause';
import { accountKeys } from '@/features/account/api/keys';  // 'me' key

export function useToggleTriagePause() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (paused: boolean) => setTriagePaused(paused),
    onSuccess: () => qc.invalidateQueries({ queryKey: accountKeys.me() }),
  });
}

// apps/web/features/triage/components/PauseBanner.tsx
'use client';
// Uses <Alert variant="warning"> (Phase 01.5 D-C3) - already in components/ui/alert
// Non-dismissible. Plain DOM <button> for vitest compatibility.
// Reads triagePaused from useCurrentUser() hook
```

**Pause copy recommendation (i18n keys):**
- `settings.triage.pause.title`: "Tự động xử lý email" / "Automated triage"
- `settings.triage.pause.body`: "Khi tắt, Zero Mail sẽ không tự động xử lý email mới" / "When off, Zero Mail won't automatically process new emails"
- `settings.triage.pause.toggleLabel`: "Tạm dừng tự động xử lý" / "Pause automated triage"
- `settings.triage.pause.banner.heading`: "Tự động xử lý đang tạm dừng" / "Automated triage is paused"
- `settings.triage.pause.banner.unpause`: "Bật lại" / "Resume"

[ASSUMED — copy decisions; to be confirmed by `frontend-design` skill during plan-phase polish]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| OIDC token verification | Custom JWT parser | `TokenVerifier` from `google-auth-library-oauth2-http` | JWKS caching, signature algo check, expiry, audience — all handled; hand-rolling misses edge cases |
| Base64url decode | Manual `replace(+/-)`  | `Base64.getUrlDecoder()` (Java stdlib) | Built-in; handles padding automatically |
| JSON deserialization of Pub/Sub envelope | Manual parsing | Jackson 3 `@RequestBody` record binding | Records auto-deserialize; zero boilerplate |
| PostgreSQL TEXT[] → String[] | Custom `UserType` or `@Type(hypersistence)` | `@JdbcTypeCode(SqlTypes.ARRAY)` | Hibernate 7 native support; no extra dep |
| `SKIP LOCKED` queue pattern | Custom lock table | `SELECT ... FOR UPDATE SKIP LOCKED LIMIT N` via `@Query(nativeQuery=true)` | Postgres-native; already used in CLAUDE.md |
| Token refresh from worker thread | Hacking `OAuth2AuthorizedClientService` into scheduler | Direct POST to `https://oauth2.googleapis.com/token` | No `Authentication` context in scheduler; direct call is the correct pattern |
| Watch renewal scheduling | Cron with per-tenant timers | Single `@Scheduled(cron)` with SKIP LOCKED query per tick | Simpler; natural scatter via `watch_renewed_at` column |

---

## Runtime State Inventory

> Step 2.5 applied. This is a new-feature phase (not rename/refactor), but checking for any existing runtime state.

| Category | Items Found | Action Required |
|----------|-------------|-----------------|
| Stored data | No `pubsub_delivery` or `mail_message_observed` tables exist yet | Liquibase creates 010-013 |
| Live service config | No Pub/Sub topic/subscription provisioned yet | Manual GCP setup + RUNBOOK.md (deferred D-item) |
| OS-registered state | None | None |
| Secrets/env vars | `PUBSUB_PUSH_AUDIENCE_URL`, `PUBSUB_SA_PRINCIPAL_EMAIL`, `GOOGLE_PUBSUB_TOPIC_NAME` do not exist in any env file | Add to `.env.local` and document in RUNBOOK.md |
| Build artifacts | None | None |

**Nothing found in categories 3-4:** Verified by filesystem scan of `backend/*/src/main/resources/application*.yml`. [VERIFIED: codebase]

---

## Common Pitfalls

### P-01: Forged Push — OIDC Filter Runs AFTER Business Logic
**What goes wrong:** If `SecurityConfig` order is wrong and the existing chain runs first, the push endpoint may be treated as "not matched by any chain" and fall through to `anyRequest().authenticated()`, which redirects to OAuth login — not a 401.
**Root cause:** Spring Security applies filter chains in `@Order` sequence; without `@Order(1)` on the Pub/Sub chain, the user OAuth chain (`@Order(2)`) may intercept first.
**Prevention:** `@Order(1)` on `PubSubSecurityConfig`; confirm `securityMatcher("/internal/pubsub/**")` is correct. Add explicit `@Order(2)` to existing `SecurityConfig`.
**Verification gate:** Integration test: POST to `/internal/pubsub/gmail` without Authorization header → assert 401, not 302 redirect.

### P-02: ScopedValue Not Bound Before DB Call in Worker
**What goes wrong:** `@TenantId` Hibernate filter silently queries with NULL tenant → returns cross-tenant or empty results.
**Root cause:** Scheduled threads do not inherit HTTP-request `TenantContext.TENANT`. The value is `ScopedValue.orElse(null)` which maps to an unconstrained filter.
**Prevention:** `ScopedValue.where(TENANT, tenantId.toString()).run(...)` per row BEFORE any JPA call. Add ArchUnit rule checking for bare scheduled method calling JPA without ScopedValue wrapper.
**Verification gate:** `MultiTenantLeakIntegrationTest` pattern — assert worker for tenant A cannot see tenant B's `mail_message_observed` rows.

### P-03: Watch Registration in OAuth Handler
**What goes wrong:** If a future PR adds `gmail.users().watch()` inside `GoogleOAuthSuccessHandler`, Gmail API failure breaks the login flow.
**Root cause:** Bundled OAuth handler is currently correct (async watch per D-C1). Risk is future regression.
**Prevention:** ArchUnit rule banning Gmail API calls from `security` package. Code review checkpoint.
**Verification gate:** `ApplicationModulesTest` enforces module boundaries; Gmail API import in `security.GoogleOAuthSuccessHandler` would be a boundary violation if Gmail API is in `core.gmail` module.

### P-04: TEXT[] Round-Trip Type Mismatch
**What goes wrong:** `label_ids` stored as `text[]` in PostgreSQL but JPA maps it as `varchar[]` or VARBINARY, causing `ClassCastException` or silent truncation.
**Root cause:** Without `@JdbcTypeCode(SqlTypes.ARRAY)`, Hibernate may serialize as binary.
**Prevention:** Use `@JdbcTypeCode(SqlTypes.ARRAY)` + `@Column(columnDefinition = "text[]")`.
**Verification gate:** `MailMessageObservedPersistenceTest` — persist entity with multi-element `labelIds`, read back via `JdbcTemplate`, assert raw column type is `text[]` and values round-trip correctly.

### P-05: `historyId` Type Confusion (String vs Long)
**What goes wrong:** Gmail Pub/Sub payload sends `historyId` as either a JSON string or a JSON number depending on encoding. Java record `long historyId` fails on string representation.
**Root cause:** Inbox-zero comment: "seem to get this in different formats? so unifying as number".
**Prevention:** In `GmailNotification` record, use `@JsonDeserialize(using = FlexibleLongDeserializer.class)` or accept `Object` and coerce. Alternatively: accept `String historyId` and `Long.parseLong`.
**Verification gate:** Unit test with two JSON payloads: `{"historyId": 12345}` and `{"historyId": "12345"}`.

### P-06: `ON CONFLICT DO NOTHING` Returns Empty on Duplicate — Worker Sees Empty Insert
**What goes wrong:** Worker inserts `pubsub_delivery` row, gets 0 rows inserted (conflict), but doesn't detect that the delivery is already being processed by another worker tick (was already set to PROCESSING by previous tick).
**Root cause:** `ON CONFLICT DO NOTHING` does not distinguish "conflict because already PROCESSED" from "conflict because currently PROCESSING by another worker". SKIP LOCKED covers most cases but there's a subtle window.
**Prevention:** Worker query: `SELECT ... WHERE status IN ('PENDING', 'PROCESSING') AND locked_until < NOW()`. The `locked_until` column prevents re-acquisition of in-flight rows.
**Verification gate:** Worker idempotency test — insert a PROCESSING row with `locked_until = NOW() + 30s`, confirm worker tick does NOT re-process it.

### P-07: i18n EN_SCAN_FILES Drift on New Feature Folder
**What goes wrong:** New `apps/web/features/triage/components/PauseBanner.tsx` and `hooks/useToggleTriagePause.ts` are not in `EN_SCAN_FILES` in `apps/web/scripts/check-i18n.ts` → missing key check silently drops coverage.
**Root cause:** Phase 01.3 D-D3 pattern — `EN_SCAN_FILES` must be updated in the same plan as new files adding i18n usage.
**Prevention:** Add `features/triage/components/PauseBanner.tsx` + settings toggle to `EN_SCAN_FILES` in same commit as the i18n keys.
**Verification gate:** `pnpm i18n:check` in strict mode (husky pre-commit gate); assert it fails if `settings.triage.pause.*` keys are missing.

### P-08: `nextPageToken` Silently Dropped — History Pagination
**What goes wrong:** `history.list(maxResults=500)` returns `nextPageToken`. Worker ignores it, advances `last_synced_history_id` to the highest returned `historyId`. On next push, a gap exists.
**Root cause:** D-B6 explicitly accepts this truncation. However, if `last_synced_history_id` is advanced to the full `webhook_history_id` (not to the last returned history entry), the gap is bridged. Advance to `webhookHistoryId` unconditionally after the 500-item pass.
**Prevention:** Inbox-zero pattern: if empty history after gap truncation, still advance `last_synced_history_id` to `webhookHistoryId`. Worker must always advance even on pagination truncation.
**Verification gate:** Log `event=gmail_history_pagination_dropped` when `nextPageToken` is non-null; integration test asserts `last_synced_history_id` advances to `webhookHistoryId` even on truncated response.

### P-09: `users.stop()` Failure Propagates Disconnect
**What goes wrong:** `GmailConnectionService.disconnect()` calls `users.stop()` which throws (token revoked) → entire disconnect transaction rolls back → user cannot disconnect.
**Root cause:** Best-effort call wrapped in same transaction as status update.
**Prevention:** Call `users.stop()` OUTSIDE the transaction (or in a separate try-catch that swallows non-critical exceptions). Commit the status=DISCONNECTED update first, then attempt stop. D-C5 explicitly states "best-effort (don't fail disconnect if stop() fails)".
**Verification gate:** Unit test: mock `users.stop()` to throw; assert `disconnect()` still sets `status=DISCONNECTED` and clears `watch_*` columns.

### P-10: Push Endpoint Reachable from Next.js Proxy
**What goes wrong:** `/internal/pubsub/gmail` is accidentally forwarded by the Next.js reverse proxy config, exposing it to browser clients (CSRF vector even though the filter would block them).
**Root cause:** Missing proxy exclusion rule.
**Prevention:** Check `apps/web` proxy config. Add `!path.startsWith('/internal/')` exclusion. Document in RUNBOOK.md.
**Verification gate:** Code review check on `apps/web` proxy/rewrites config.

---

## Threats

### T-01: Forged Pub/Sub Push (Missing/Fake OIDC Token)
**Severity:** HIGH
**STRIDE:** Spoofing
**Description:** Attacker POSTs to `/internal/pubsub/gmail` without a valid OIDC token or with a token signed for a different audience/SA. Without the filter, attacker can inject arbitrary `pubsub_delivery` rows attributable to real tenants.
**Mitigation:** `PubSubOidcAuthFilter` — hard 401 on ANY verification failure; configured `aud` + `email` checked; reject paths never bind `TenantContext`. Fail-closed.
**Verification gate:** Integration test: wrong aud, wrong email, expired, bad signature → all must return 401, no DB row created.

### T-02: Pub/Sub Replay (Same `messageId`)
**Severity:** MEDIUM
**STRIDE:** Tampering / Information Disclosure
**Description:** Google Pub/Sub delivers the same message multiple times (at-least-once delivery). Without idempotency, duplicate `mail_message_observed` rows could trigger double triage actions in Phase 4.
**Mitigation:** `UNIQUE (tenant_id, pubsub_message_id)` + `ON CONFLICT DO NOTHING` on `pubsub_delivery`; `PRIMARY KEY (tenant_id, gmail_message_id)` + `ON CONFLICT DO NOTHING` on `mail_message_observed`.
**Verification gate:** Integration test: replay same push twice → assert single `pubsub_delivery` row, single `mail_message_observed` row.

### T-03: History Gap Exploit (Forced Disconnect + Reconnect)
**Severity:** MEDIUM
**STRIDE:** Elevation of Privilege
**Description:** Attacker forces tenant disconnect + reconnect loop, causing gap truncations that drop large history windows. In theory allows old messages to never be triaged.
**Mitigation:** 500-item gap cap (D-B6); history-404 → pointer advance (D-D2); no full mailbox rescan. The design explicitly accepts dropped-gap messages.
**Verification gate:** Worker test: large gap (>500) → assert truncation log emitted and `last_synced_history_id` advances correctly.

### T-04: Refresh Token Leak via Worker Logs
**Severity:** HIGH
**STRIDE:** Information Disclosure
**Description:** Worker calls `RefreshTokenCipher.decrypt()` → plaintext refresh token in memory → if logged accidentally, token exposed.
**Mitigation:** Privacy logging contract (Phase 1 D-E1); Logback scrub filter (FND-03); never log the return value of `decrypt()` or the `refreshToken` field. ArchUnit deny-list includes `refreshToken` (Phase 1 D-E1 confirmed).
**Verification gate:** ArchUnit rule from Phase 1 FND-04 — verify `GmailHistoryProcessor` and `GmailWatchScheduler` have no `refreshToken` field references in log calls.

### T-05: TenantContext Leak Across Scheduled Threads
**Severity:** HIGH
**STRIDE:** Information Disclosure
**Description:** If a previous row's `ScopedValue` binding leaks into the next row's processing, tenant A sees tenant B's Gmail data.
**Mitigation:** `ScopedValue.where(...).run(...)` scoping is inherently bounded by the lambda; when the lambda exits, the binding is gone. However: any resource (DB connection, Gmail client) cached outside the lambda that retains the tenant context could leak.
**Verification gate:** `MultiTenantLeakIntegrationTest` pattern — concurrent worker ticks for two tenants assert no cross-read.

### T-06: SQL Injection via `emailAddress` Lookup
**Severity:** MEDIUM
**STRIDE:** Tampering
**Description:** `emailAddress` from Pub/Sub payload used in `LOWER(google_email) = LOWER(:email)` query. If not parameterized, SQL injection possible.
**Mitigation:** Spring Data JPA `findByGoogleEmailIgnoreCase(String email)` or `@Query("... WHERE LOWER(gc.googleEmail) = LOWER(:email)")` with `@Param` — always parameterized.
**Verification gate:** Code review: assert no string concatenation in the email lookup query.

### T-07: Pause Toggle Authorization
**Severity:** MEDIUM
**STRIDE:** Elevation of Privilege
**Description:** Unauthenticated or cross-tenant call to `PUT /tenant/triage-pause` toggles another tenant's pause state.
**Mitigation:** Existing `SecurityConfig` user OAuth chain requires authentication + `TenantContext.TENANT` binding via `TenantBindingFilter`. Service method uses `TenantContext.currentOrThrow()` — cross-tenant impossible if `@TenantId` filter is active.
**Verification gate:** Integration test with authenticated tenant A attempting to reach `TenantService.setTriagePaused` while `TenantContext` is bound to tenant B — should not be possible via HTTP API; assert 401/403 without session.

### T-08: Pub/Sub Ack Timeout Storm (p99 > 10s)
**Severity:** MEDIUM
**STRIDE:** Denial of Service
**Description:** If push controller takes >10s (Pub/Sub default ack deadline), Pub/Sub redelivers. Under load, this creates an exponential delivery storm.
**Mitigation:** Ack-fast pattern (D-A1) — controller does ONLY OIDC verify + DB INSERT + 200. No Gmail API call inside controller. Target p99 < 300ms.
**Verification gate:** Load test / latency measurement on `GmailPubSubController` with mocked OIDC verifier and DB.

### T-09: Gmail API Quota Burn from Watch Retry Loop
**Severity:** MEDIUM
**STRIDE:** Denial of Service
**Description:** `GmailWatchScheduler` retries on failure every minute. If 1000 tenants all fail simultaneously, 1000 `users.watch` calls/minute = 1000 × 100 quota units = 100,000 units/min (~1,667/sec against 250/user/sec per-project limit).
**Mitigation:** `watch_consecutive_failures` column gates retries (after 3 fails, sets `WATCH_UNHEALTHY` and stops standard renewal until user reconnects). `LIMIT 50` per tick bounds per-tick quota. `SKIP LOCKED` ensures multiple worker instances don't double-process.
**Verification gate:** Mock Gmail API to fail; assert 3 failures → `WATCH_UNHEALTHY`; assert no further `users.watch` calls for that tenant until `clearForReconnect` is called.

### T-10: Frontend `ingestionHealth` Enum Exposure to Users
**Severity:** LOW
**STRIDE:** Information Disclosure
**Description:** Exposing raw `ingestionHealth` enum values in API response might reveal internal system state or admin-level diagnostic information.
**Mitigation:** D-D3: only `HEALTHY` vs "not HEALTHY" is used for UI. Raw enum value is included for telemetry/admin — not shown in UI copy. ReconnectPrompt uses unified copy regardless of which health state triggered it.
**Verification gate:** Frontend test: assert `ReconnectPrompt` renders the same copy for `WATCH_UNHEALTHY` and `HISTORY_LOST`; assert the raw enum value is not rendered as user-visible text.

### T-11: Race Condition — Worker Processes Row While Controller Is Inserting
**Severity:** LOW
**STRIDE:** Tampering
**Description:** Controller commits `pubsub_delivery` INSERT; worker picks it up immediately before controller returns 200. If worker crashes before committing, row is re-queued. Idempotent design handles this correctly.
**Mitigation:** `ON CONFLICT DO NOTHING` + monotonic history pointer + `SKIP LOCKED` — at-least-once worker + idempotent observation = exactly-once semantics.
**Verification gate:** Crash-recovery test: worker processes delivery, crashes mid-fan-out, restarts → `mail_message_observed ON CONFLICT` skips already-written rows.

---

## Performance and Quota Envelope

### Push Controller p99 Budget

| Operation | Typical Latency | Budget |
|-----------|----------------|--------|
| OIDC `TokenVerifier.verify()` (cached JWKS) | 1–5ms | 10ms |
| `findByGoogleEmailIgnoreCase` (indexed) | 1–3ms | 10ms |
| `pubsub_delivery` INSERT | 2–5ms | 20ms |
| Network overhead (VPS same-host Postgres) | ~1ms | — |
| **Total p99 target** | **< 50ms typical** | **< 300ms hard** |

The 300ms target is extremely conservative given same-VPS Postgres. [ASSUMED — no prod benchmark available]

### Gmail API Quota

| Operation | Quota Units | v1 Rate (1000 active tenants) |
|-----------|-------------|-------------------------------|
| `users.watch` | 100 units | 1000 calls/day renewal ≈ negligible |
| `users.history.list` | 2 units | ~100 msg/s peak → 200 units/sec |
| `users.stop` | 50 units | On disconnect only |
| **Project daily budget** | **1B units/day** | Well within range |

Per-user per-second limit: 250 units/user/sec. Worker processes 50 rows/tick at 1s interval = 50 `history.list` calls/sec × 2 = 100 units/sec. Safe. [ASSUMED — quota units from training data; verify at implementation]

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual JWT parsing for OIDC | `TokenVerifier` from google-auth-library | 2020+ | JWKS caching, algo check, expiry all handled |
| `pgp_sym_encrypt` for OAuth tokens | App-layer AES-GCM + VPS deployment secret | Phase 1 | Keys never touch DB |
| `ThreadLocal` for tenant context | `ScopedValue` (Java 25) | Phase 1 | Safe with virtual threads |
| External broker (Kafka) for internal queue | Postgres `SKIP LOCKED` | Phase 1 | No extra ops surface at v1 QPS |
| `@Enumerated(ORDINAL)` for state | `@Enumerated(STRING)` + `IdentifiedEnum` | Phase 01.2.1 | Survives enum reordering |
| Custom `UserType` for PostgreSQL arrays | `@JdbcTypeCode(SqlTypes.ARRAY)` | Hibernate 6+ | No extra dep needed |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `users.watch` re-call replaces/renews the existing watch (not fails with "already watching") | §Code Examples Pattern 5 | If it fails, need `users.stop` + `users.watch` sequence; minor code change |
| A2 | Separate `SecurityFilterChain @Order(1)` + `@Order(2)` on existing chain is idiomatic Spring Security 7.0.5 | §Architecture Patterns | If wrong, fallback is adding the filter to the existing chain via a path matcher in `addFilterBefore` |
| A3 | Gmail API quota units: `users.watch` = 100, `users.history.list` = 2, `users.stop` = 50 | §Performance | If wrong, quota budget may be tighter; add monitoring |
| A4 | Pause toggle copy (Vietnamese/English) | §Code Examples Pattern 10 | `frontend-design` skill will refine; keys are placeholders |
| A5 | `fixedDelay` tasks in Spring Boot 4 with virtual threads may serialize on same virtual thread (GH #31900) | §Architecture Patterns | If fixed in Boot 4.0.6, behavior is more concurrent; `cron` still safer for the watch scheduler |
| A6 | `PubSubOidcAuthFilter.shouldNotFilter()` is required as defense in depth, and global servlet registration must be disabled with `FilterRegistrationBean#setEnabled(false)` | Cycle 3 review correction to §Architecture Patterns | Without this, a servlet Filter bean can run outside the `/internal/pubsub/**` SecurityFilterChain and break user-session endpoints |

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL 17.6 | All persistence | ✓ (Docker Compose dev) | 17.6 | None needed |
| Redis 7.2 | Spring Session (existing) | ✓ (Docker Compose dev) | 7.2 | None needed |
| Google Cloud Pub/Sub subscription | Push receiver | ✗ (not provisioned) | — | Manual GCP setup required before first E2E test |
| `PUBSUB_PUSH_AUDIENCE_URL` env var | `PubSubOidcAuthFilter` | ✗ | — | Must be added to `.env.local` and deployment secrets |
| `GOOGLE_PUBSUB_TOPIC_NAME` env var | `GmailWatchScheduler` | ✗ | — | Must be added |

**Missing dependencies with no fallback:**
- GCP Pub/Sub topic + subscription — must be provisioned manually before Gmail push can reach the endpoint. Document in RUNBOOK.md.

[VERIFIED: `backend/worker/src/main/resources/application.yml` does not contain `GOOGLE_PUBSUB_TOPIC_NAME`; `backend/api/src/main/resources/application*.yml` does not contain `PUBSUB_PUSH_AUDIENCE_URL`]

---

## Validation Architecture

> `workflow.nyquist_validation` not explicitly `false` — section required.

### Test Framework

| Property | Value |
|----------|-------|
| Backend framework | JUnit 5 + Testcontainers (PostgresContainerTest) + RestClient (NOT MockMvc) |
| Frontend framework | Vitest + Testing Library |
| Backend quick run | `./gradlew :backend:core:test :backend:api:test --tests "*.pubsub.*" --tests "*.triage.*"` |
| Backend full suite | `./gradlew clean check` |
| Frontend quick run | `pnpm --filter web test --run -- --reporter=verbose features/triage` |
| Frontend full suite | `pnpm --filter web test --run` |

**Key constraint:** Use `RestClient + LocalServerPort` (NOT MockMvc) for controller integration tests — MockMvc skips servlet filters and `PubSubOidcAuthFilter` would never fire. Confirmed by STATE.md locked decision.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File |
|--------|----------|-----------|-------------------|------|
| MAIL-01 | Watch registered within 60s of connect (integration) | Integration | `./gradlew :backend:worker:test --tests "*GmailWatchSchedulerTest*"` | `backend/worker/src/.../GmailWatchSchedulerTest.java` — Wave 0 RED |
| MAIL-01 | Push delivery creates `pubsub_delivery` row | Integration | `./gradlew :backend:api:test --tests "*GmailPubSubControllerTest*"` | `backend/api/src/.../GmailPubSubControllerTest.java` — Wave 0 RED |
| MAIL-02 | Watch renewed before expiry, 3 failures → WATCH_UNHEALTHY | Unit | `./gradlew :backend:worker:test --tests "*GmailWatchSchedulerTest*"` | Same file above |
| MAIL-03 | OIDC filter rejects missing token, wrong aud, wrong email, expired, bad sig | Integration | `./gradlew :backend:api:test --tests "*PubSubOidcAuthFilterTest*"` | `backend/api/src/.../PubSubOidcAuthFilterTest.java` — Wave 0 RED |
| MAIL-03 | Valid OIDC token allows request through | Integration | Same | Same |
| MAIL-04 | Duplicate push → single `pubsub_delivery` row (ON CONFLICT) | Integration | `./gradlew :backend:api:test --tests "*PubSubIdempotencyTest*"` | `backend/api/src/.../PubSubIdempotencyTest.java` — Wave 0 RED |
| MAIL-04 | Duplicate history → single `mail_message_observed` row | Integration | `./gradlew :backend:core:test --tests "*MailMessageObservedPersistenceTest*"` | Wave 0 RED |
| MAIL-05 | History 404 → advance pointer + HISTORY_LOST, no rescan | Unit | `./gradlew :backend:worker:test --tests "*GmailHistoryProcessorTest*"` | `backend/worker/src/.../GmailHistoryProcessorTest.java` — Wave 0 RED |
| MAIL-05 | Settings page mounts ReconnectPrompt when connected Gmail has ingestionHealth != HEALTHY | Vitest | `pnpm --filter web test --run -- ReconnectPrompt` | `apps/web/.../ReconnectPrompt.test.tsx` — Wave 0 RED |
| MAIL-06 | Toggle persists `triage_paused` in DB | Integration | `./gradlew :backend:api:test --tests "*TriagePauseControllerTest*"` | `backend/api/src/.../TriagePauseControllerTest.java` — Wave 0 RED |
| MAIL-06 | `PauseBanner` renders conditionally on `triagePaused=true` | Vitest | `pnpm --filter web test --run -- PauseBanner` | `apps/web/.../PauseBanner.test.tsx` — Wave 0 RED |
| MAIL-06 | `useToggleTriagePause` invalidates `me` query on success | Vitest unit | `pnpm --filter web test --run -- useToggleTriagePause` | Wave 0 RED |

### Hermetic Test Infrastructure Required

**Backend OIDC fixture:** Must create a test-scoped JWKS endpoint that serves a generated RSA keypair, sign synthetic JWT tokens with configurable `aud`, `email`, `iss`, `exp` claims, and override `TokenVerifier.setCertificatesLocation(mockJwksUrl)` to point at the mock. Options:
1. `WireMockServer` (if already in test classpath) serving `/.well-known/jwks.json`
2. `MockWebServer` (OkHttp) — lightweight, no classpath dependency
3. In-memory HTTP server using `com.sun.net.httpserver.HttpServer`

Recommend a `JwksTestServer` JUnit 5 extension that starts/stops per test class, generates a fresh RSA keypair, and exposes `signToken(Map<String, Object> claims)` and `jwksUrl()`.

**Gmail API mock:** Use `Mockito` mocks for `Gmail` client interface. The generated client is based on `AbstractGoogleJsonClient` — mockable via `@MockBean` or constructor injection.

### Sampling Rate

- **Per task commit:** `./gradlew :backend:core:test :backend:api:test :backend:worker:test -x integrationTest` (unit tests only, <30s)
- **Per wave merge:** `./gradlew clean check` + `pnpm --filter web test --run`
- **Phase gate:** Full suite green + all 5 roadmap success criteria demonstrable before `/gsd-verify-work`

### Wave 0 Gaps (required before Wave 1+ implementation)

Backend:
- [ ] `GmailPubSubControllerTest.java` — MAIL-01, MAIL-03, MAIL-04 (uses `LocalServerPort` + `RestClient`, NOT MockMvc)
- [ ] `PubSubOidcAuthFilterTest.java` — MAIL-03 (hermetic JWKS fixture required)
- [ ] `JwksTestServer.java` (JUnit 5 extension) — shared fixture for OIDC tests
- [ ] `GmailWatchSchedulerTest.java` — MAIL-01, MAIL-02
- [ ] `GmailHistoryProcessorTest.java` — MAIL-04, MAIL-05 (mocked Gmail client)
- [ ] `MailMessageObservedPersistenceTest.java` — MAIL-04 (extends `PostgresContainerTest`, TEXT[] round-trip)
- [ ] `TriagePauseControllerTest.java` — MAIL-06
- [ ] `PubSubIdempotencyTest.java` — MAIL-04 (duplicate push replay)

Frontend:
- [ ] `apps/web/features/triage/components/PauseBanner.test.tsx` — MAIL-06 conditional render
- [ ] `apps/web/features/triage/hooks/useToggleTriagePause.test.ts` — MAIL-06 mutation + invalidation
- [ ] `apps/web/features/gmail/components/ReconnectPrompt.test.tsx` (extend existing) — MAIL-05 settings-page ReconnectPrompt parent gate

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | Yes (push endpoint) | Google OIDC `TokenVerifier` with JWKS — not user auth |
| V3 Session Management | No (push endpoint is stateless) | `STATELESS` session policy on Pub/Sub chain |
| V4 Access Control | Yes (triage pause endpoint) | Existing Spring Security OAuth filter chain + TenantContext |
| V5 Input Validation | Yes (Pub/Sub payload) | Jackson 3 record binding + explicit type validation |
| V6 Cryptography | Yes (refresh token decrypt) | `RefreshTokenCipher` AES-GCM — never hand-rolled |
| V9 Logging | Yes (privacy constraint) | Logback scrub filter + ArchUnit deny-list + opaque event names |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Forged Pub/Sub push | Spoofing | `TokenVerifier` + configured aud + email; hard 401 |
| Pub/Sub message replay | Tampering | `ON CONFLICT DO NOTHING` on UNIQUE constraint |
| OAuth token leak in logs | Info Disclosure | ArchUnit deny-list + Logback scrub |
| Cross-tenant data leak in worker | Info Disclosure | `ScopedValue.where()` per-row + `@TenantId` filter |
| SQL injection via emailAddress | Tampering | Parameterized JPQL only |
| Pause toggle cross-tenant | Privilege Escalation | Session cookie + `TenantContext.currentOrThrow()` |
| Pub/Sub ack timeout storm | DoS | Ack-fast controller (<300ms); no Gmail API in controller |
| Runaway watch retry quota | DoS | 3-strike `WATCH_UNHEALTHY` gate + LIMIT 50 per tick |

---

## Open Questions (RESOLVED)

1. **Spring Security 7.0.5 exact `@Order` interaction when `@Profile("!test")` is active**
   - What we know: existing `SecurityConfig` has `@Profile("!test")` annotation. The Pub/Sub chain needs `@Order(1)`.
   - What's unclear: does `@Profile("!test")` interact with `@Order` bean registration in test context? The worker tests don't use a full Spring Security context.
   - **RESOLVED:** Add `@Profile("!test")` to `PubSubSecurityConfig` as well (Plan 03 Task 1 does this). Both chains are excluded from the test context together; no ordering conflict. Test-profile security (WR-06) is a separate no-security config that is not ordered relative to these two chains.

2. **Whether `GmailConnectionRepository.findByGoogleEmailIgnoreCase` needs `LOWER()` or Spring Data derive handles it**
   - What we know: existing `findByTenantId` uses standard derive. PostgreSQL `LOWER()` in query is needed for case normalization.
   - **RESOLVED:** Use explicit `@Query` with `LOWER()` cast — rename to `findByGoogleEmailLower` and accept a pre-lowercased param: `@Query("SELECT c FROM GmailConnectionEntity c WHERE LOWER(c.googleEmail) = :emailLower")`. Spring Data derived `IgnoreCase` generates `LOWER(col) = LOWER(?)` but the double-LOWER adds unnecessary work and the derive method name is misleading. `PubSubIngestionService` calls `email.toLowerCase()` before passing to the repo. Plan 03 Task 1 implements this.

3. **`TenantService.setTriagePaused` — should it use the same `@TenantId` filter or bypass for an UPDATE by tenantId?**
   - What we know: `TenantEntity` extends `AbstractEntity` (NOT `AbstractTenantOwnedEntity`) — it IS the tenant, so no `@TenantId` discriminator applies. Update is by explicit `tenantId` column.
   - **RESOLVED:** Standard `findById(tenantId).ifPresent(t -> { t.setTriagePaused(paused); })` — no `@TenantId` filter complications because `TenantEntity` is not a tenant-owned entity; it IS the tenant. This pattern is identical to how `GmailConnectionService.currentStatus` works today.

---

## Sources

### Primary (HIGH confidence)
- `D:/study-materials-summer-2026/EXE202/zero-mail` — codebase fully read: GmailConnectionEntity, SecurityConfig, GmailConnectionService, WorkerApplication, HealthcheckScheduler, ReconnectPrompt, useDisconnectGmail, me.ts, TenantStatusController, MeController, db/changelog/003+007+009, db.changelog-master.yaml
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/app/api/google/webhook/` — reference implementation: ack-fast pattern, decodeHistoryId, fetchGmailHistoryResilient, monotonic updateLastSyncedHistoryId, 404 detection
- `D:/study-materials-summer-2026/EXE202/inbox-zero/apps/web/utils/gmail/watch.ts` — users.watch request shape verified
- https://github.com/googleapis/google-auth-library-java/blob/main/oauth2_http/java/com/google/auth/oauth2/TokenVerifier.java — `TokenVerifier.Builder` API, `VerificationException` messages
- https://github.com/googleapis/google-auth-library-java/blob/main/samples/snippets/src/main/java/VerifyGoogleIdToken.java — usage pattern
- https://developers.google.com/gmail/api/reference/rest/v1/users.history/list — `startHistoryId`, `historyTypes`, `maxResults`, 404 semantics
- https://developers.google.com/gmail/api/reference/rest/v1/users/watch — request body, response shape
- https://docs.spring.io/spring-security/reference/servlet/configuration/java.html#_multiple_httpsecurity_instances — multiple SecurityFilterChain @Order pattern
- https://developers.google.com/identity/protocols/oauth2/web-server#offline — token refresh endpoint, `invalid_grant` response
- https://docs.cloud.google.com/pubsub/docs/authenticate-push-subscriptions — Pub/Sub OIDC `aud` = configured audience URL, `email` = SA principal
- Liquibase 5.0.2 YAML verified against existing changelogs 003, 007, 009

### Secondary (MEDIUM confidence)
- https://docs.cloud.google.com/pubsub/docs/push — push message envelope format: `{message: {data, messageId, publishTime, attributes}, subscription}`
- https://github.com/spring-projects/spring-framework/issues/31900 — fixedDelay + virtual threads serialization behavior
- Hibernate 7 SqlTypes Javadocs + Baeldung Hibernate array mapping articles — `@JdbcTypeCode(SqlTypes.ARRAY)` for TEXT[]
- CLAUDE.md Stack Research — Gmail quota units (training data + CLAUDE.md)

### Tertiary (LOW confidence)
- Pause toggle copy strings — ASSUMED, to be confirmed by `frontend-design` skill
- Gmail API `users.watch` re-call idempotency behavior — ASSUMED from Inbox-zero renewal pattern

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all library versions locked in CLAUDE.md; codebase confirmed
- Architecture: HIGH — all decisions locked in CONTEXT.md; patterns verified against codebase
- `TokenVerifier` API: HIGH — verified via GitHub source code
- Liquibase changeset shapes: HIGH — verified against existing changelogs in codebase
- OIDC `aud` claim semantics: HIGH — verified via official GCP docs
- Hibernate TEXT[] mapping: MEDIUM-HIGH — Hibernate 6+ native support confirmed; Hibernate 7 specific behavior extrapolated
- ScopedValue per-row binding: HIGH — Java 25 JEP-464 semantics; confirmed by Phase 1 patterns
- `SecurityFilterChain @Order` with `@Profile`: MEDIUM — standard pattern confirmed; test-profile interaction is ASSUMED

**Research date:** 2026-04-28
**Valid until:** 2026-06-28 (stable libraries; Gmail API reference is stable)

---

## RESEARCH COMPLETE
