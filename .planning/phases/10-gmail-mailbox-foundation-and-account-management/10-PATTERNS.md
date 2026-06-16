# Phase 10: Gmail Mailbox Foundation and Account Management - Pattern Map

**Mapped:** 2026-06-09
**Files analyzed:** 14 new/modified
**Analogs found:** 14 / 14

> Backend-only phase (Java 25 / Spring Boot 4 / Spring Security 7). `backend/core` + `backend/api`. No frontend.
> Naming rule (CLAUDE.md): NO abbreviations — `request`/`response`/`connection`/`gmailConnectionRepository`, never `req`/`res`/`conn`/`repo`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `core/.../db/changelog/changes/119-gmail-connections-multi-mailbox.yaml` (NEW) | migration | batch DDL | `042-chat-message-and-body-ban-trigger.yaml` | exact (raw `sql:` + rollback + preConditions) |
| `core/.../gmail/gateway/MailboxRef.java` (NEW) | model (value object) | transform | `OAuthProvisioningService.BundledProvisioningResult` record | role-match (record value object) |
| `core/.../gmail/gateway/GmailApiClientFactory.java` (MODIFIED) | gateway/factory | request-response + cache | itself (re-key) | exact (in-place evolution) |
| `core/.../gmail/persistence/GmailConnectionRepository.java` (MODIFIED) | repository | CRUD | itself (`findByGoogleEmailIgnoreCase`) | exact |
| `core/.../gmail/persistence/GmailConnectionEntity.java` (MODIFIED) | model (entity) | CRUD | itself (add `isPrimary`, `label`) | exact |
| `core/.../gmail/usecases/GmailConnectionService.java` (MODIFIED) | service | event-driven + CRUD | itself (mailbox-scoped overloads) | exact |
| `api/.../security/IntentCarryingAuthorizationRequestRepository.java` (NEW) | middleware (security) | request-response | `TenantBindingFilter` (filter-tier session/scoped pattern) | role-match (decorator over framework type) |
| `api/.../security/OAuthIntentSnapshot.java` (NEW) | model (DTO) | transform | `BundledProvisioningResult` record | exact (small immutable record) |
| `api/.../security/GoogleAuthorizationRequestResolver.java` (MODIFIED) | middleware (security) | request-response | itself (`customizeAuthorizationRequest`) | exact |
| `api/.../security/GoogleOAuthSuccessHandler.java` (MODIFIED) | middleware (security) | request-response | itself (intent branch) | exact |
| `api/.../security/SecurityConfig.java` (MODIFIED) | config | request-response | itself (`@Order(4)` chain `.oauth2Login(...)`) | exact |
| `api/.../controllers/gmail/ConnectMailboxController.java` (NEW) | controller | request-response | `DisconnectController` | exact (thin controller, TenantContext) |
| `api/.../controllers/gmail/ConnectedMailboxesController.java` (NEW) | controller | CRUD | `DisconnectController` + `GmailConnectionStatusResponse` | exact |
| `api/.../dto/gmail/MailboxSummaryResponse.java` (NEW) | model (DTO) | transform | `GmailConnectionStatusResponse` | exact (record + `from(...)` + `@Schema`) |
| `core/src/test/.../arch/GmailClientLookupBoundaryTest.java` (NEW) | test (arch) | — | `GmailWriteBoundaryTest` | exact (ArchCondition allow-list) |

Plus Wave-0 tests (Migration119Test, DuplicateActiveEmailTest, SetPrimaryTransactionalTest, MailboxOwnershipSeamTest, GmailApiClientFactoryMailboxCacheTest, RefreshTokenCipherContinuityTest, OAuthIntentRoutingTest, IntentCarryingRepositoryTest) — analogs in Shared Patterns § Testing.

---

## Pattern Assignments

### `119-gmail-connections-multi-mailbox.yaml` (migration, batch DDL)

**Analog:** `backend/core/src/main/resources/db/changelog/changes/042-chat-message-and-body-ban-trigger.yaml`

Single changeSet, `sql:` change with `splitStatements: false`, paired `rollback:` `sql:` block, top-level `comment:` documenting the "single changeSet by design" rationale (042 lines 1-13, 169-178):
```yaml
databaseChangeLog:
  - changeSet:
      id: 042-chat-message-body-ban
      author: zeromail
      comment: >
        Single changeSet by design (MEDIUM-3): ... applied atomically.
      changes:
        - sql:
            splitStatements: false
            sql: |
              CREATE TABLE chat_message ( ... );
      rollback:
        - sql:
            splitStatements: false
            sql: |
              DROP TRIGGER IF EXISTS chat_message_body_ban ON chat_message;
```
- **Author** = `zeromail` (literal), id prefixed with the changeset number.
- **preConditions** NOT in 042 — use the dedup `sqlCheck onFail: MARK_RAN` shape from RESEARCH.md § "Changeset 119" (lines 228-240). RESEARCH already supplies the exact ordered SQL for drop-constraint → partial unique on `(tenant_id, lower(google_email)) WHERE status='CONNECTED'` → add `is_primary` + backfill → `uq_gmail_conn_primary` partial unique.
- **Critical (RESEARCH Pitfall 3):** the drop target is a *constraint* (`003-create-gmail-connections.yaml` `addUniqueConstraint`), so use `ALTER TABLE ... DROP CONSTRAINT uq_gmail_connections_tenant_id;` NOT `DROP INDEX`. Rollback re-adds via `ADD CONSTRAINT`.
- **Master include:** append the new file to `db.changelog-master.yaml` (CONVENTIONS §10, append-only). Next free number verified = **119**.

---

### `MailboxRef.java` (model — value object, NEW)

**Analog:** `OAuthProvisioningService.BundledProvisioningResult` (`backend/core/.../account/usecases/OAuthProvisioningService.java` line 70)
```java
public record BundledProvisioningResult(UUID tenantId, UUID userId, boolean firstLogin) {}
```
Mirror as a standalone top-level record in `core.gmail.gateway`:
```java
public record MailboxRef(UUID tenantId, UUID gmailConnectionId) {}
```
Purpose (D-10): makes a tenant-only Gmail call un-typable — the arg-swap cross-mailbox bleed becomes a compile error. Carries both ids so the AES-GCM AAD (`tenantId.toString()`) survives the cache re-key (D-11).

---

### `GmailApiClientFactory.java` (gateway/factory, MODIFIED)

**Analog:** itself — `backend/core/.../gmail/gateway/GmailApiClientFactory.java`

**Cache field to re-key** (lines 45-48) — today keyed by `tenantId`, re-key to `gmailConnectionId`:
```java
// Caches the access token result for back-to-back deliveries so we skip the AES-GCM
// refresh-token decrypt + Google HTTPS refresh round trip when a still-valid token exists.
private final ConcurrentMap<UUID, TokenRefreshResult> accessTokenCache =
        new ConcurrentHashMap<>();
```

**Core pattern — cache lookup + decrypt + refresh + put** (lines 108-133). NOTE the two re-key points and the AAD that MUST stay `tenantId.toString()` (D-11):
```java
public Gmail buildClientForConnection(
        GmailConnectionEntity gmailConnection, UUID tenantId, Duration requestTimeout)
        throws IOException {
    TokenRefreshResult cachedToken = accessTokenCache.get(tenantId);   // ← re-key to entity.getId()
    if (cachedToken != null && cachedToken.expiresAt().isAfter(Instant.now())) {
        return buildGmailClient(cachedToken.accessToken().value(), requestTimeout);
    }
    byte[] decryptedRefreshTokenBytes =
            refreshTokenCipher.decrypt(
                    gmailConnection.getRefreshTokenEncrypted(), tenantId.toString()); // ← AAD UNCHANGED
    try {
        ...
        } catch (InvalidGrantException invalidGrant) {
            accessTokenCache.remove(tenantId);    // ← re-key eviction to gmailConnectionId too
            throw invalidGrant;
        }
        accessTokenCache.put(tenantId, tokenResult);   // ← re-key to gmailConnectionId
        ...
    } finally {
        Arrays.fill(decryptedRefreshTokenBytes, (byte) 0);   // keep: zeroes plaintext
    }
}
```

**`@Deprecated(forRemoval = true)` legacy adapter (D-12)** — mirror the existing `buildClientForTenant` (lines 92-101) but add the fail-loud >1-CONNECTED guard:
```java
@Deprecated(forRemoval = true)
public Gmail buildClientForTenant(UUID tenantId, Duration requestTimeout) throws IOException {
    // resolve single CONNECTED mailbox; throw IllegalStateException if >1 (surfaces un-migrated callers)
}
```
Add `buildClientForMailbox(MailboxRef mailboxRef)` that resolves via `findByIdAndTenantId` and keys the cache on `mailboxRef.gmailConnectionId()`.

---

### `GmailConnectionRepository.java` (repository, MODIFIED)

**Analog:** itself — derived-query style (line 16):
```java
Optional<GmailConnectionEntity> findByGoogleEmailIgnoreCase(String googleEmail);
```
Add (D-04) the ownership lookup, same derived-query convention:
```java
Optional<GmailConnectionEntity> findByIdAndTenantId(UUID id, UUID tenantId);
```
For tenant-scoped multi-row reads (list endpoint) add `List<GmailConnectionEntity> findByTenantIdOrderByIsPrimaryDesc(UUID tenantId)` or similar. Note native-SQL methods (`findConnectionsNeedingWatchRenewal`, lines 18-30) do NOT inherit `@TenantId` filtering — switch-primary/dedupe native SQL must include `tenant_id` explicitly (D-09).

---

### `GmailConnectionEntity.java` (model — entity, MODIFIED)

**Analog:** itself — `@Enumerated(STRING)` + `@Column` field + getter/setter pattern (lines 21-23, 118-120). Add:
```java
@Column(name = "is_primary", nullable = false)
private boolean isPrimary = false;

@Column(name = "label")
private String label;   // or display_purpose per D-07 fold
```
with matching `isPrimary()`/`setPrimary(...)` and `getLabel()`/`setLabel(...)`. Entity stays a `class` (Hibernate proxy), Lombok-free, extends `AbstractTenantOwnedEntity`.

---

### `GmailConnectionService.java` (service, MODIFIED)

**Analog:** itself — `backend/core/.../gmail/usecases/GmailConnectionService.java`

**Disconnect state-machine ordering to preserve** (lines 77-95) — make each step mailbox-scoped (resolve via `findByIdAndTenantId`) but keep the ordering (stop watch → revoke → mark disconnected):
```java
public void disconnect(UUID tenantId) {
    revokeGrantForCurrentTenant(tenantId);   // tryStopWatch THEN revokeStoredRefreshToken
    markDisconnected(tenantId);
}
```
**`markDisconnected` transactional flip** (lines 97-116) — idempotent via `ifPresent`; mirror for the mailbox-scoped overload. The `TransactionTemplate disconnectTransaction` field (lines 34, 46) is the pattern for switch-primary's transactional clear-old-set-new (D-09).

**Ownership seam to ADD (D-04)** — new method with fixed fail-closed contract (404 not-owned/missing, 409 disconnected):
```java
public GmailConnectionEntity resolveOwnedConnectionOrThrow(UUID tenantId, UUID gmailConnectionId) {
    // findByIdAndTenantId → empty ⇒ 404; status==DISCONNECTED ⇒ 409
}
```
Throw domain exceptions in `core.gmail.exception` (the `InvalidGrantException` package); `backend/api` error layer maps to 404/409. Privacy logging (lines 135, 159): `event=... tenantId={}` only — add `gmailConnectionId={}`, NEVER `google_email`.

---

### `IntentCarryingAuthorizationRequestRepository.java` (security middleware, NEW)

**Analog (mechanism):** RESEARCH.md lines 134-177 supplies the full decorator skeleton. **Analog (filter-tier session/ScopedValue convention):** `TenantBindingFilter` (`backend/api/.../security/TenantBindingFilter.java`) — same `@Component`, same `backend/api/.../security` package, same Spring-Security-aware servlet-tier component shape.

Decorator over `HttpSessionOAuth2AuthorizationRequestRepository`; on `removeAuthorizationRequest`, copy intent into the session with a FRESH value object to dirty Redis (RESEARCH Pitfall 2, `#7327`):
```java
session.setAttribute(INTENT_SESSION_ATTRIBUTE, new OAuthIntentSnapshot(...)); // fresh object = dirty write
```

---

### `OAuthIntentSnapshot.java` (DTO, NEW)

**Analog:** `BundledProvisioningResult` record. Small immutable carrier (RESEARCH line 192):
```java
public record OAuthIntentSnapshot(String intent, UUID targetMailboxId, UUID initiatingTenantId) {}
```

---

### `GoogleAuthorizationRequestResolver.java` (security middleware, MODIFIED)

**Analog:** itself — `customizeAuthorizationRequest` (lines 63-78). Keep `additionalParameters` for `access_type`/`prompt` (on-the-wire); ADD intent via `.attributes(...)` (server-side only, NEVER on the wire). RESEARCH lines 500-507:
```java
return OAuth2AuthorizationRequest.from(authorizationRequest)
        .additionalParameters(Map.copyOf(additionalParameters)) // access_type, prompt — to Google
        .attributes(existing -> {                                // intent — stays server-side
            existing.put("intent", intent);                      // "add_mailbox" | "reconnect_mailbox"
            existing.put("targetMailboxId", targetMailboxId);
            existing.put("initiatingTenantId", initiatingTenantId);
        })
        .build();
```
`initiatingTenantId` read from the authenticated session at flow start (D-02 session-presence discriminator). Keep `?reconnect=true` as the `prompt=consent` trigger only — orthogonal to intent (RESEARCH line 206).

---

### `GoogleOAuthSuccessHandler.java` (security middleware, MODIFIED)

**Analog:** itself — `onAuthenticationSuccess` (lines 101-207). The existing handler IS the `first_login` branch (`provisionBundledOAuth`, line 185). ADD a session-attribute read at the top, branch, then remove the attribute (one-shot, RESEARCH line 179):
- `first_login` / absent → existing `provisioningService.provisionBundledOAuth(...)` path (line 185).
- `add_mailbox` → INSERT new `gmail_connections` row, **branch BEFORE** `OAuthProvisioningService` (D-03) so it never re-provisions user/tenant.
- `reconnect_mailbox` → `resolveOwnedConnectionOrThrow(tenantId, targetMailboxId)` then update that row only.
Reuse the existing scope-check + null-refresh-token + cleanup pattern (lines 126-169) verbatim per branch. Privacy log contract (line 158): `event=...` opaque names only.

---

### `SecurityConfig.java` (config, MODIFIED)

**Analog:** itself — the `@Order(4)` user chain `.oauth2Login(...)` (RESEARCH cites lines 210-219). Wire the new repository (RESEARCH lines 182-188):
```java
.oauth2Login(oauth2Login -> oauth2Login
    .successHandler(successHandler)
    .failureHandler(failureHandler)
    .authorizationEndpoint(endpoint -> endpoint
        .authorizationRequestResolver(authRequestResolver)
        .authorizationRequestRepository(intentCarryingRepository)))  // NEW
```

---

### `ConnectMailboxController.java` + `ConnectedMailboxesController.java` (controllers, NEW)

**Analog:** `DisconnectController` (`backend/api/.../controllers/gmail/DisconnectController.java`) — thin `@RestController`, injects ONLY the service, reads `TenantContext.currentTenantUuid()`, delegates:
```java
@RestController
public class DisconnectController {
    private final GmailConnectionService connectionService;
    public DisconnectController(GmailConnectionService connectionService) { ... }
    @PostMapping("/api/tenant/disconnect")
    public void disconnect() {
        UUID tenantId = TenantContext.currentTenantUuid();
        connectionService.disconnect(tenantId);
    }
}
```
- Controllers NEVER inject repositories (CONVENTIONS §1).
- Mailbox-scoped routes use path segment `/api/gmail/mailboxes/{gmailConnectionId}/...` (D-05) — typed UUID path param `@PathVariable UUID gmailConnectionId`.
- `ConnectMailboxController` issues the 302 → `/oauth2/authorization/google?reconnect=true` for add/reconnect (mirror the redirect that `ConnectGmailController` already does — see RESEARCH line 18 note).
- List/set-primary/disconnect endpoints map responses via DTO `from(...)`.

---

### `MailboxSummaryResponse.java` (DTO, NEW)

**Analog:** `GmailConnectionStatusResponse` (`backend/api/.../dto/gmail/GmailConnectionStatusResponse.java`) — record + static `from(projection)` + `@Schema(requiredProperties=...)` + `@Schema(nullable=true)` per nullable field:
```java
@Schema(requiredProperties = {"connectionStatus", "googleEmail"})
public record GmailConnectionStatusResponse(
        String connectionStatus, @Schema(nullable = true) String googleEmail) {
    public static GmailConnectionStatusResponse from(GmailConnectionProjection projection) {
        return new GmailConnectionStatusResponse(projection.status(), projection.googleEmail());
    }
}
```
The mailbox summary adds id, label/purpose, `isPrimary`, watch expiry, ingestion health, last sync (GMA-02). Map from an extended `GmailConnectionProjection` shape (read-side excludes ciphertext — AUD-04). NO hand-written mirror DTOs; OpenAPI→frontend regen is Phase 11 (out of scope).

---

### `GmailClientLookupBoundaryTest.java` (test — arch, NEW)

**Analog:** `GmailWriteBoundaryTest` (`backend/core/src/test/.../arch/GmailWriteBoundaryTest.java`)

Mirror the `@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)` + custom `ArchCondition<JavaClass>` over `getMethodCallsFromSelf()` (lines 28, 38-77):
```java
@ArchTest
static final ArchRule only_allowed_writers_call_gmail_write_apis =
    classes().that().resideInAPackage("..core..")
        .should(new ArchCondition<JavaClass>("...") {
            @Override public void check(JavaClass javaClass, ConditionEvents conditionEvents) {
                if (ALLOWED_GMAIL_WRITERS.contains(javaClass.getName())) return;
                javaClass.getMethodCallsFromSelf().forEach(methodCall -> {
                    if (!isGmailWriteCall(methodCall.getTargetOwner().getName(), methodCall.getName())) return;
                    conditionEvents.add(SimpleConditionEvent.violated(methodCall, "..." + methodCall.getSourceCodeLocation()));
                });
            }
        })
        .because("...")
        .allowEmptyShould(true);   // ← Phase 10 uses allowEmptyShould(FALSE) — list is non-empty by construction (D-13)
```
Differences for D-13:
- Target = `GmailApiClientFactory.buildClientForTenant` (method call), not Gmail write APIs.
- `ALLOWED_TENANT_LOOKUP_CALLERS` = the 9-class non-empty allow-list (RESEARCH lines 513-523, full FQNs already enumerated).
- `.allowEmptyShould(false)` (D-13) — the rule bites immediately; Phase 11 deletes one entry per migrated consumer.

---

## Shared Patterns

### AES-GCM token cipher — AAD MUST stay tenant-based (D-11)
**Source:** `RefreshTokenCipher` (`backend/core/.../gmail/persistence/crypto/RefreshTokenCipher.java`)
**Apply to:** `GmailApiClientFactory` re-key, all disconnect/revoke paths.
```java
cipher.updateAAD(tenantId.getBytes(StandardCharsets.UTF_8));   // encrypt line 42 / decrypt line 68
```
Never hand-roll; never change the AAD. `MailboxRef` carries `tenantId` precisely so the AAD survives the cache-key change to `gmailConnectionId`. Continuity test (`RefreshTokenCipherContinuityTest`): old ciphertext decrypts unchanged post-migration.

### Atomic provisioning seam — branch BEFORE it for add-mailbox (D-03)
**Source:** `OAuthProvisioningService.provisionBundledOAuth` (`backend/core/.../account/usecases/OAuthProvisioningService.java` lines 101-213)
**Apply to:** `GoogleOAuthSuccessHandler` add/reconnect branches.
- `PROPAGATION_REQUIRED` `TransactionTemplate` (lines 65-66); `ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(...)` BEFORE the transaction opens (lines 119, 157 — Hibernate captures tenant on session open).
- Constraint-violation catch pattern (lines 192-212): `catch (DataIntegrityViolationException ...)` then re-read winner — mirror for the duplicate-active-email 23505 → 409 mapping (D-08, constraint name `uq_gmail_conn_active_email`).

### Filter-tier ScopedValue / session binding (the Phase 11 end-state contract)
**Source:** `TenantContext` + `TenantBindingFilter` (`backend/api/.../security/TenantBindingFilter.java`)
**Apply to:** documentation/comments only in Phase 10. The full `MailboxContext` filter is Phase 11 (D-06). Phase 10 pins the ownership contract (`resolveOwnedConnectionOrThrow`) that the future filter wraps. `IntentCarryingAuthorizationRequestRepository` follows the same `@Component` security-tier shape but does NOT bind a ScopedValue.

### Privacy logging posture
**Source:** every service/handler above — `log.warn("event=<name> tenantId={}", tenantId)` (e.g. `GmailConnectionService` lines 135, 159; `GoogleOAuthSuccessHandler` line 158).
**Apply to:** all new add/reconnect/disconnect code. Add `gmailConnectionId={}`. NEVER log `google_email`, subject, token bytes (DB/UI storage of `google_email` is allowed product state; logging is not).

### Testing
**Source A — HTTP (RestClient + @LocalServerPort, NOT MockMvc, so filters/ScopedValue bind):** `backend/api/src/test/.../controllers/rules/RulesControllerIntegrationTest.java`, `billing/BillingBalanceControllerTest.java`. Use for `MailboxOwnershipSeamTest` (404/409), `OAuthIntentRoutingTest` (+ test-profile SecurityConfig slice, folded WR-06).
**Source B — Testcontainers Postgres + Liquibase migration/repo:** `PostgresContainerTest` base (used by `TriagePrivacySweepTest` line 51). Use for `Migration119Test`, `DuplicateActiveEmailTest`, `SetPrimaryTransactionalTest`, `RefreshTokenCipherContinuityTest`.
**Source C — privacy log-sweep (sentinel tokens + ListAppender):** `TriagePrivacySweepTest` (`backend/core/src/test/.../triage/TriagePrivacySweepTest.java` lines 1-70) — `ch.qos.logback ListAppender` + `FORBIDDEN_CONTENT_TOKENS` sentinels asserted absent from captured logs. Use for the no-raw-email-in-logs assertion on add/reconnect/disconnect.

## No Analog Found

None. Every new file maps to an existing in-repo pattern; the OAuth decorator shim's mechanism is fully specified in RESEARCH.md (lines 134-188) and its component shape matches `TenantBindingFilter`.

## Metadata

**Analog search scope:** `backend/core/.../gmail`, `backend/core/.../account/usecases`, `backend/api/.../security`, `backend/api/.../controllers/gmail`, `backend/api/.../dto/gmail`, `backend/core/src/test/.../arch`, `db/changelog/changes`, test dirs.
**Files scanned:** ~20 (11 read in full for excerpts).
**Pattern extraction date:** 2026-06-09
