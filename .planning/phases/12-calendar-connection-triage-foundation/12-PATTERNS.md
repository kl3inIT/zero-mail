# Phase 12: Calendar Connection + Triage Foundation - Pattern Map

**Mapped:** 2026-06-20
**Files analyzed:** ~40 (backend + frontend + Liquibase + Gradle)
**Analogs found:** 38 / 40 (2 net-new with no in-repo analog — see "No Analog Found")

## File Classification

### Backend — OAuth scope ledger (INFRA-01)

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `backend/core/src/main/java/com/zeromail/core/oauth/scope/GoogleOAuthScope.java` | domain enum | constant lookup | `core/onboarding/domain/OnboardingStep.java` (IdentifiedEnum/fromId) + existing `api/security/OAuthScopes.java` (string constants) | role-match |
| `backend/core/src/test/java/com/zeromail/core/oauth/scope/OAuthScopeAllowListTest.java` | ArchUnit test | source-text scan | `core/admin/arch/AdminTenantOAuthGuardTest.java` | exact |
| `backend/core/src/test/java/com/zeromail/core/oauth/scope/GoogleOAuthScopeEnumTest.java` | unit test | enum invariants | existing `*EnumTest` files (use IdentifiedEnum/fromId convention) | role-match |

### Backend — OAuth token store generalization

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `backend/core/src/main/java/com/zeromail/core/oauth/token/OAuthTokenStore.java` (new facade) | utility / crypto facade | encrypt/decrypt | `core/gmail/persistence/crypto/RefreshTokenCipher.java` | exact |

### Backend — Multi-Google-Calendar OAuth (CAL-CONN-01..08)

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `core/calendar/domain/CalendarConnectionStatus.java` | enum (state machine) | identity lookup | `core/gmail/domain/GmailConnectionStatus.java` | exact |
| `core/calendar/domain/CalendarRole.java` (FREEBUSY/EVENT_WRITE/BRIEF_SOURCE) | enum | identity | `core/gmail/domain/GmailCategory.java` | role-match |
| `core/calendar/domain/CalendarConnection.java` (record value object) | domain value object | — | `core/gmail/projection/GmailConnectionProjection.java` | role-match |
| `core/calendar/persistence/CalendarConnectionEntity.java` | JPA entity | CRUD | `core/gmail/persistence/GmailConnectionEntity.java` | exact |
| `core/calendar/persistence/CalendarEntity.java` | JPA entity (sub-calendar) | CRUD | `GmailConnectionEntity.java` (composite-PK uniqueness) | role-match |
| `core/calendar/persistence/MailboxCalendarPreferenceEntity.java` | join-table entity | CRUD | `GmailConnectionEntity.java` | role-match |
| `core/calendar/usecases/CalendarConnectionService.java` | service (`@Service` + `@Transactional`) | CRUD/state machine | `core/gmail/usecases/GmailConnectionService.java` | exact |
| `core/calendar/usecases/CalendarSnapshotIngestionService.java` | service | external→DB transform | `core/gmail/usecases/InboxBackfillService.java` | role-match |
| `core/calendar/gateway/CalendarApiClientFactory.java` | gateway adapter | OAuth token → Google client | `core/gmail/gateway/GmailApiClientFactory.java` | exact |
| `core/calendar/event/CalendarConnectionDisconnected.java` | Modulith event record | event-driven | `core/gmail/event/MailMessageObserved.java` | role-match |
| `api/security/CalendarOAuthSuccessHandler.java` (or extension of existing) | OAuth handler | request-response | `api/security/GoogleOAuthSuccessHandler.java` | exact |
| `api/security/GoogleAuthorizationRequestResolver.java` (MODIFY) | OAuth resolver | request-response | self (extend `customizeAuthorizationRequest` with `google-calendar` branch) | exact |
| `api/controllers/calendar/CalendarConnectionController.java` | REST controller | request-response | `api/controllers/tenant/TenantStatusController.java` (CONVENTIONS §1 example) | exact |
| `api/dto/calendar/CalendarConnectionResponse.java` (record DTO) | DTO record | response | `api/dto/account/MeResponse.java` (CONVENTIONS §3 example) | exact |

### Backend — Calendar-aware Gmail triage (CAL-TRIAGE-01..04)

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `backend/worker/.../triage/CalendarMessageClassifier.java` | service (`@TransactionalEventListener` AFTER_COMMIT) | event-driven | inferred — worker AFTER_COMMIT listener pattern; see "No Analog Found" | partial |
| `backend/worker/.../triage/CalendarPartParser.java` | utility (ical4j wrapper) | transform | see "No Analog Found" — new library boundary | partial |
| `core/inbox/persistence/GmailInboxProjectionEntity.java` (MODIFY add `message_class`+`event_dt`) | JPA entity | CRUD | self | exact |
| `core/inbox/persistence/GmailInboxProjectionRepository.java` (MODIFY ORDER BY) | repository (native query) | read | self | exact |
| `core/rules/domain/RuleEvaluator.java` (MODIFY add PRESET_CALENDAR branch) | domain service | transform | self | exact |
| `api/security/GoogleOAuthSuccessHandler.java` (MODIFY seed `system-calendar` rule) | OAuth handler | request-response | self (the existing `materializeDefaultRulesEnabled` call) | exact |

### Backend — Liquibase + Gradle

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `db/changelog/changes/131-calendar-connections.yaml` | migration | schema | `119-gmail-connections-multi-mailbox.yaml` | exact |
| `db/changelog/changes/132-calendars.yaml` | migration | schema | `119-gmail-connections-multi-mailbox.yaml` | exact |
| `db/changelog/changes/133-mailbox-calendar-preferences.yaml` | migration | schema | `119-gmail-connections-multi-mailbox.yaml` | exact |
| `db/changelog/changes/134-inbox-projection-calendar-columns.yaml` | migration (addColumn) | schema | `119-gmail-connections-multi-mailbox.yaml` | role-match |
| `db/changelog/db.changelog-master.yaml` (MODIFY include) | manifest | append | self | exact |
| `gradle/libs.versions.toml` (MODIFY add ical4j + calendar API) | catalog | append | self | exact |

### Frontend — `apps/web/features/calendar/**`

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `app/(app)/settings/mailboxes/[mailboxId]/calendar/page.tsx` | route | SSR shell | `apps/web/features/mailbox/...` route (mailbox settings page) | role-match |
| `features/calendar/api/calendar-api.ts` | API client | request-response | `features/mailbox/api/mailbox-api.ts` | exact |
| `features/calendar/query-keys.ts` | TanStack key factory | cache identity | `features/mailbox/query-keys.ts` | exact |
| `features/calendar/hooks/use-calendar-connections.ts` | hook (query) | read | `features/mailbox/hooks/*` (use* TanStack Query pattern) | role-match |
| `features/calendar/hooks/use-toggle-calendar.ts` | hook (mutation, meta-toast) | write | `features/account/hooks/useUpdateLanguage.ts` (mutation w/ meta) | role-match |
| `features/calendar/hooks/use-update-calendar-preference.ts` | hook (mutation) | write | same as above | role-match |
| `features/calendar/components/CalendarConnectionsPanel.tsx` | composed component | view | IZ `CalendarConnections.tsx` (visual only) + raw shadcn Card | partial |
| `features/calendar/components/CalendarConnectionCard.tsx` | composed component | view | IZ `CalendarConnectionCard.tsx` (visual only) | partial |
| `features/calendar/components/CalendarList.tsx` | composed component | view | IZ `CalendarList.tsx` (visual only) | partial |
| `features/calendar/components/RoleAssignmentSection.tsx` | composed component | view | Calendly-pattern overlay — no analog | none (new shape) |
| `lib/api/schema.d.ts` (REGENERATE) | generated artifact | — | per `apps/web/AGENTS.md` MANDATORY rule | exact |

## Pattern Assignments

### `core/oauth/scope/GoogleOAuthScope.java` (enum, ledger)

**Analogs:** `core/onboarding/domain/OnboardingStep.java` (IdentifiedEnum + fromId fail-loud, CONVENTIONS §4) + existing `api/security/OAuthScopes.java` (constant strings — to be deprecated).

**Pattern to copy — IdentifiedEnum + `fromId` fail-loud (`OnboardingStep.java:111–122`):**

```java
public enum OnboardingStep implements OrderedEnum {
    GMAIL_CONNECTED(10), TEMPLATE_SELECTED(20), COMPLETE(30);
    public static OnboardingStep fromId(String id) {
        return Stream.of(values()).filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown OnboardingStep id: " + id));
    }
}
```

**Adapt:** carry the scope URL via `value()` accessor; add JavaDoc per entry with `purpose` / `phase-introduced` / `sensitivity-tier`. Each entry whitelisted from the literal-scanner because the enum body itself contains the canonical URL.

---

### `core/oauth/scope/OAuthScopeAllowListTest.java` (ArchUnit test)

**Analog:** `core/admin/arch/AdminTenantOAuthGuardTest.java` (full file, 40 lines).

**Pattern to copy — ArchUnit composite rule scaffold (full analog file):**

```java
ArchRule rule =
        noClasses()
                .that().resideInAnyPackage("..controllers.admin..", "..core.admin..")
                .and().areNotAssignableTo(TenantOAuthRevocationGateway.class)
                .should().dependOnClassesThat()
                .resideInAnyPackage("..core.gmail.persistence..", "..core.gmail.gateway..")
                .because("admin tenant operations must never inject token-decrypting Gmail internals")
                .allowEmptyShould(true);
rule.check(importProductionClasses());
```

**Adapt per D-02 caveat (RESEARCH.md §A):** ArchUnit's byte-code model drops constant string values, so the rule **cannot** use `getMethodCallsFromSelf()` + argument inspection. Two viable shapes:
1. **Source-text scan** of `backend/{api,core,worker}/src/main/java` (recurse, regex `https://www\\.googleapis\\.com/auth/[a-zA-Z0-9._-]+`, exclude `..core.oauth.scope..`).
2. **Static-final-field scan** via custom `ArchCondition<JavaClass>` over `getFields()` reading `JavaField#tryGetStaticValue()` — works for `static final String` literals but misses inline arguments.

Recommended: shape 1 (source-text), because production sites read scope URLs via `application.yml` resource and `GoogleOAuthScope.X.value()`, not static-final fields.

---

### `core/oauth/token/OAuthTokenStore.java` (cipher facade)

**Analog:** `core/gmail/persistence/crypto/RefreshTokenCipher.java` (full file, 75 lines).

**Pattern to copy — AES-GCM envelope with tenantId AAD (`RefreshTokenCipher.java:33–52`):**

```java
public byte[] encrypt(byte[] plaintext, String tenantId) {
    byte[] nonce = new byte[12]; secureRandom.nextBytes(nonce);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, keysByVersion.get(currentVersion),
            new GCMParameterSpec(128, nonce));
    cipher.updateAAD(tenantId.getBytes(StandardCharsets.UTF_8));
    byte[] ciphertext = cipher.doFinal(plaintext);
    ByteBuffer envelopeBuffer = ByteBuffer.allocate(4 + 12 + ciphertext.length);
    envelopeBuffer.putInt(currentVersion); envelopeBuffer.put(nonce); envelopeBuffer.put(ciphertext);
    return envelopeBuffer.array();
}
```

**Adapt per Claude's Discretion (CONTEXT D-14 area):** keep cipher class IDENTICAL — facade wraps it. Add row-discriminator parameter only at the facade layer; AAD stays `tenantId` (rotating the AAD would invalidate existing Gmail ciphertexts).

```java
@Component
public class OAuthTokenStore {
    private final RefreshTokenCipher cipher;  // delegate
    public byte[] encrypt(byte[] plaintext, UUID tenantId, RowDiscriminator d) {
        return cipher.encrypt(plaintext, tenantId.toString());
    }
    public byte[] decrypt(byte[] envelope, UUID tenantId, RowDiscriminator d) { ... }
    public enum RowDiscriminator { GMAIL_CONNECTION, CALENDAR_CONNECTION }
}
```

---

### `core/calendar/persistence/CalendarConnectionEntity.java` (JPA entity)

**Analog:** `core/gmail/persistence/GmailConnectionEntity.java` (lines 1–80 essential).

**Pattern to copy — entity shape + crypto column + tenant-owned base (`GmailConnectionEntity.java:14–80`):**

```java
@Entity @Table(name = "gmail_connections")
public class GmailConnectionEntity extends AbstractTenantOwnedEntity {
    @Column(name = "google_email", nullable = false) private String googleEmail;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private GmailConnectionStatus status;
    /** Encrypted: [key_version | nonce:12 | ciphertext]. Field name intentionally distinct
     *  from `refreshToken` so the privacy regex doesn't false-positive. */
    @Column(name = "refresh_token_encrypted") private byte[] refreshTokenEncrypted;
    @Column(name = "scopes_granted", columnDefinition = "text") private String scopesGranted;
    @Column(name = "connected_at") private Instant connectedAt;
    @Column(name = "google_profile_name") private String googleProfileName;
    @Column(name = "google_profile_picture_url", columnDefinition = "text") private String googleProfilePictureUrl;
    @Column(name = "disconnected_at") private Instant disconnectedAt;
    protected GmailConnectionEntity() {}
    public GmailConnectionEntity(UUID id, UUID tenantId, String googleEmail, GmailConnectionStatus status) {
        super(id, tenantId); this.googleEmail = googleEmail; this.status = status;
    }
}
```

**Adapt — OMIT (CAL-CONN-06 / RESEARCH §B):** `isPrimary`, `displayPurpose`, `lastSyncedHistoryId`, `watchHistoryId`, `watchExpiresAt`, `watchRenewedAt`, `watchConsecutiveFailures`, `ingestionHealth`. Calendar Phase 12 does not poll; mailbox primary-ness has no analog.

---

### `core/calendar/usecases/CalendarConnectionService.java` (`@Service`)

**Analog:** `core/gmail/usecases/GmailConnectionService.java:38–108`.

**Pattern to copy — service-owned `@Transactional` + projection-on-read (`GmailConnectionService.java:71–82`):**

```java
@Service
public class GmailConnectionService {
    private static final Logger log = LoggerFactory.getLogger(GmailConnectionService.class);
    private final GmailConnectionRepository connectionRepository;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final RefreshTokenCipher refreshTokenCipher;
    private final TransactionTemplate disconnectTransaction;
    // ... constructor injects all collaborators ...

    @Transactional(readOnly = true)
    public GmailConnectionProjection currentStatus(UUID tenantId) {
        return connectionRepository.findByTenantId(tenantId)
            .map(c -> new GmailConnectionProjection(c.getStatus().name(),
                    c.getIngestionHealth().name(), c.getGoogleEmail()))
            .orElseGet(GmailConnectionProjection::notConnected);
    }
}
```

**Adapt:** mirror `resolveOwnedConnectionOrThrow` for `CalendarConnection` ownership guard (throws `CalendarConnectionNotOwnedException` + `CalendarDisconnectedException`). For D-14 cascade-disconnect: use `TransactionTemplate` for the multi-table cascade write, then publish `CalendarConnectionDisconnected` AFTER_COMMIT via `ApplicationEventPublisher`.

---

### `core/calendar/gateway/CalendarApiClientFactory.java`

**Analog:** `core/gmail/gateway/GmailApiClientFactory.java:42–80`.

**Pattern to copy — Spring-injected clientId + per-connection access-token cache (`GmailApiClientFactory.java:42–73`):**

```java
@Component
public class GmailApiClientFactory {
    private final String clientId; private final String clientSecret;
    private final GmailConnectionRepository gmailConnectionRepository;
    private final RefreshTokenCipher refreshTokenCipher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ConcurrentMap<UUID, TokenRefreshResult> accessTokenCache = new ConcurrentHashMap<>();

    public GmailApiClientFactory(
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret}") String clientSecret,
            ...) { ... }
}
```

**Adapt:** swap `google-api-services-gmail` → `google-api-services-calendar` v3; `Gmail` builder → `Calendar` builder; pull clientId/secret from the SAME `google` registration (single OAuth client per RESEARCH §B); evict access-token cache on `CalendarConnectionDisconnected`.

---

### `api/security/GoogleAuthorizationRequestResolver.java` (MODIFY)

**Analog:** itself, lines 70–102.

**Pattern to copy — additional-parameters customizer (`GoogleAuthorizationRequestResolver.java:70–82`):**

```java
private OAuth2AuthorizationRequest customizeAuthorizationRequest(
        OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest servletRequest) {
    if (authorizationRequest == null) return null;
    var additionalParameters = new HashMap<>(authorizationRequest.getAdditionalParameters());
    additionalParameters.put("access_type", "offline");
    additionalParameters.put("include_granted_scopes", "true");
    if ("true".equals(servletRequest.getParameter(RECONNECT_PARAMETER))) {
        additionalParameters.put("prompt", "consent");
    }
    // ...
}
```

**Adapt per RESEARCH §B Pattern 1:** add a `calendarFlow` check on `REGISTRATION_ID` attribute (`"google-calendar".equals(registrationId)`) and always set `prompt=consent` for that branch so CAL-CONN-01's "explicit action" invariant holds.

---

### `api/security/GoogleOAuthSuccessHandler.java` (MODIFY — seed system-calendar)

**Analog:** itself, lines 1–120 + the existing `materializeDefaultRulesEnabled` call site (memory `project_default_rules_seeded_first_login.md`).

**Pattern to copy — provisioning + rule materialization wiring (`GoogleOAuthSuccessHandler.java:80–98`):**

```java
private final RuleTemplateMaterializationService ruleTemplateMaterializationService;
// constructor injects it; success-flow calls:
ruleTemplateMaterializationService.materializeDefaultRulesEnabled(tenantId);
```

**Adapt per D-09:** the existing `113-default-rule-templates-seed.yaml` already contains a `system-calendar` template — Phase 12 evolves its matcher from `SEMANTIC_INTENT` to a new `PRESET_CALENDAR` marker via a new data-only changeset (RESEARCH §E, mirror `023-fix-pin-calendar-category.yaml` precedent). No new call site needed if the template key list already includes `system-calendar`.

---

### `api/controllers/calendar/CalendarConnectionController.java`

**Analog:** `api/controllers/tenant/TenantStatusController.java` (CONVENTIONS §1 excerpt).

**Pattern to copy — thin controller + `Response.from(...)` (CONVENTIONS §1):**

```java
@GetMapping("/gmail/connection/status")
public GmailConnectionStatusResponse status() {
    UUID tenantId = UUID.fromString(TenantContext.currentOrThrow());
    GmailConnectionProjection projection = connectionService.currentStatus(tenantId);
    return GmailConnectionStatusResponse.from(projection);
}
```

**Adapt:** routes `GET /api/calendar/mailboxes/{mailboxId}/connections`, `DELETE /api/calendar/connections/{id}`, `PATCH /api/calendar/calendars/{id}/enabled`, `PATCH /api/calendar/mailboxes/{mailboxId}/preferences`. Controller injects `CalendarConnectionService` + `MailboxCalendarPreferenceService` ONLY (no repository).

---

### `api/dto/calendar/CalendarConnectionResponse.java` (record DTO)

**Analog:** `api/dto/account/MeResponse.java` (CONVENTIONS §3).

**Pattern to copy — record + OpenAPI annotations (CONVENTIONS §3):**

```java
public record MeResponse(String userId, String tenantId, String email,
        String onboardingStep, String preferredLanguage) {}
```

**Adapt:** use `@Schema(requiredProperties = {...})` for always-present fields, `@Schema(allowableValues = {...})` for closed `CalendarRole` / `CalendarConnectionStatus` strings so `openapi-typescript` emits literal unions. Provide `static from(CalendarConnectionEntity)` factory; controllers MUST return `CalendarConnectionResponse.from(...)`.

---

### `core/inbox/persistence/GmailInboxProjectionEntity.java` (MODIFY add columns)

**Analog:** itself, lines 24–87.

**Pattern to copy — entity column with `IdentifiedEnum.fromId` getter (`GmailInboxProjectionEntity.java:70–71,186–188`):**

```java
@Column(name = "inbox_state", nullable = false, length = 16)
private String inboxState = InboxState.INBOX.id();
public InboxState getInboxState() { return InboxState.fromId(inboxState); }
```

**Adapt per D-11:** add `@Column(name = "message_class", length = 16) private String messageClass;` (nullable) + `@Column(name = "event_dt") private Instant eventDt;` (nullable). Getter `MessageClass getMessageClass()` calls `MessageClass.fromId(messageClass)` per CONVENTIONS §4 (where `MessageClass` is an `IdentifiedEnum` with values INVITE/CANCEL/RESCHEDULE/RSVP).

---

### `core/inbox/persistence/GmailInboxProjectionRepository.java` (MODIFY ORDER BY)

**Analog:** itself, lines 102–127.

**Pattern to copy — native page query (`GmailInboxProjectionRepository.java:102–120`):**

```java
@Query(value = """
        SELECT * FROM gmail_inbox_projection
        WHERE tenant_id = :tenantId
          AND gmail_connection_id = :gmailConnectionId
          AND inbox_state = 'INBOX'
          AND expires_at > NOW()
          AND ( CAST(:beforeReceivedAt AS timestamptz) IS NULL OR ... )
        ORDER BY received_at DESC, gmail_message_id DESC
        LIMIT :pageLimit
        """, nativeQuery = true)
List<GmailInboxProjectionEntity> findInboxPage(...);
```

**Adapt per D-12 (RESEARCH §D Pattern 3):** replace ORDER BY with:

```sql
ORDER BY
    (message_class IS NOT NULL AND event_dt IS NOT NULL
        AND now() < event_dt + INTERVAL '24 hours') DESC,
    received_at DESC,
    gmail_message_id DESC
```

Keyset cursor stays valid because the pin predicate is monotonic within a single query (single `now()` invocation).

---

### `db/changelog/changes/131..134-*.yaml`

**Analog:** `db/changelog/changes/119-gmail-connections-multi-mailbox.yaml` (full file, 67 lines).

**Pattern to copy — preConditions HALT + atomic schema change + explicit rollback (`119-gmail-connections-multi-mailbox.yaml:1–67`):**

```yaml
databaseChangeLog:
  - changeSet:
      id: 119-gmail-connections-multi-mailbox
      author: zeromail
      comment: >
        Single changeSet by design: ...
      preConditions:
        - onFail: HALT
        - sqlCheck:
            expectedResult: 0
            sql: |
              SELECT count(*) FROM ( ... duplicates ... ) duplicates;
      changes:
        - sql:
            splitStatements: false
            sql: |
              CREATE UNIQUE INDEX uq_gmail_conn_active_email
                ON gmail_connections (tenant_id, lower(google_email))
                WHERE status = 'CONNECTED';
              ...
      rollback:
        - sql:
            splitStatements: false
            sql: |
              DROP INDEX IF EXISTS uq_gmail_conn_active_email;
              ...
```

**Adapt per RESEARCH §B Pattern 2 + §D Pattern 3:** drop the legacy-duplicate `preConditions` HALT for the four net-new tables (no pre-existing data). KEEP the `splitStatements: false` + explicit `rollback` SQL for every changeset (CONVENTIONS §11). For `134-inbox-projection-calendar-columns.yaml`, use `addColumn` (NOT raw SQL) so Liquibase can dialect-translate; index goes in a follow-up `sql` change inside the same changeset. Per-role partial unique indexes for `mailbox_calendar_preferences` (RESEARCH §B) live in `133`.

---

### `core/rules/domain/RuleEvaluator.java` (MODIFY add PRESET_CALENDAR branch)

**Analog:** itself.

**Pattern to copy — match-before-AI early return (RESEARCH §E Pattern 5):**

```java
for (var rule : rules) {
    if (rule.matcherType() == MatcherType.PRESET_CALENDAR) {
        if (message.messageClass() != null) {
            results.add(RuleEvaluationResult.preset(rule.id(),
                    rule.actionIntents(), "CALENDAR"));
        }
        continue;  // Skip AI matcher entirely for PRESET rules.
    }
    // ... existing SEMANTIC_INTENT / EXAMPLES paths ...
}
```

**Adapt:** verify whether `MatcherType` already exists in `core.rules.domain` or whether the `RuleEntity` carries a `systemType` column (RESEARCH §E flags this as "investigation needed"). The seeded `system-calendar` rule's matcher AST must evolve to `{PRESET_CALENDAR}` via a data-only Liquibase changeset (precedent: `023-fix-pin-calendar-category.yaml`).

---

### `backend/worker/.../triage/CalendarMessageClassifier.java`

**Analog (partial):** none found by glob in this scan; worker AFTER_COMMIT listeners exist for `MailMessageObserved` (event defined in `core/gmail/event/MailMessageObserved.java`).

**Pattern to copy per RESEARCH §D Pattern 4 + CONVENTIONS §6:**

```java
@Component
public class CalendarMessageClassifier {
    private final CalendarPartParser parser;
    private final GmailInboxProjectionRepository projectionRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMailMessageObserved(MailMessageObserved event) {
        // 1. fetch message body (existing path)
        // 2. detect text/calendar part (boolean: .ics OR mimeType OR BEGIN:VCALENDAR)
        // 3. parser.parse(icsBody) -> Optional<ParseResult>
        // 4. native UPDATE projection SET message_class=?, event_dt=?
        // PRIVACY: never log icsBody.
    }
}
```

**Note CONVENTIONS §6:** because API and worker are separate processes, `MailMessageObserved` listener in the worker should be a plain `@TransactionalEventListener(AFTER_COMMIT)` (NOT `@ApplicationModuleListener`).

---

### `backend/worker/.../triage/CalendarPartParser.java`

**No in-repo analog** — first ical4j integration. Use the worked example in RESEARCH §D Pattern 4 verbatim:

```java
public final class CalendarPartParser {
    public record ParseResult(MessageClass messageClass, Optional<Instant> eventDt) {}
    public enum MessageClass { INVITE, CANCEL, RESCHEDULE, RSVP }

    public Optional<ParseResult> parse(String icsBody) {
        try {
            Calendar cal = new CalendarBuilder().build(new StringReader(icsBody));
            String method = cal.<Method>getProperty(Property.METHOD)
                    .map(Method::getValue).orElse(null);
            MessageClass classification = classify(method);
            if (classification == null) return Optional.empty();
            Optional<Instant> dtStart = cal.<VEvent>getComponent("VEVENT")
                    .flatMap(v -> v.<DtStart<?>>getProperty(Property.DTSTART))
                    .map(dt -> toInstant(dt.getDate()));
            return Optional.of(new ParseResult(classification, dtStart));
        } catch (Exception parseFailure) {
            return Optional.empty();   // NEVER log icsBody
        }
    }
}
```

---

### `apps/web/features/calendar/api/calendar-api.ts`

**Analog:** `apps/web/features/mailbox/api/mailbox-api.ts` (full file, 66 lines).

**Pattern to copy — typed `api.GET/PUT` + schema-derived types (`mailbox-api.ts:1–28`):**

```ts
import { adaptFetchForOpenApi, api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type MailboxSummary = components['schemas']['MailboxSummaryResponse'];

export async function listMailboxes(options: MailboxRequestOptions = {}): Promise<MailboxSummary[]> {
  const { data, error, response } = await api.GET('/api/gmail/mailboxes', {
    cache: fetcher || headers ? 'no-store' : undefined,
    fetch: adaptFetchForOpenApi(fetcher ?? (headers ? fetch : undefined)),
    headers, signal,
  });
  if (error || !response.ok || data === undefined) {
    throw error ?? new Error(`/api/gmail/mailboxes failed: ${response.status}`);
  }
  return data;
}
```

**Adapt:** Types `CalendarConnection`, `MailboxCalendarPreference` etc. come from `components['schemas']['CalendarConnectionResponse']` — only after backend DTO ships and `pnpm --filter web run generate:api` regenerates `schema.d.ts` (AGENTS.md MANDATORY).

---

### `apps/web/features/calendar/query-keys.ts`

**Analog:** `apps/web/features/mailbox/query-keys.ts` (full file, 6 lines).

**Pattern to copy verbatim:**

```ts
export const mailboxQueryKeys = {
  all: ['mailbox'] as const,
  list: () => [...mailboxQueryKeys.all, 'list'] as const,
  active: () => [...mailboxQueryKeys.all, 'active'] as const,
} as const;
```

**Adapt:** `calendarQueryKeys.all = ['calendar']`, `.connections(mailboxId) = [...all, 'connections', mailboxId]`, `.preferences(mailboxId)`. NOTE per CONVENTIONS §8: no key for mutation-only operations (toggle/update mutations invalidate `.connections(mailboxId)` rather than owning their own key).

---

### `features/calendar/hooks/use-toggle-calendar.ts` + `use-update-calendar-preference.ts`

**Analog:** `features/account/hooks/useUpdateLanguage.ts` (mutation with `meta` toast).

**Pattern to copy — TanStack mutation with `meta` toast + invalidation (CONVENTIONS §8 + `apps/web/AGENTS.md` "TanStack v5"):**

```ts
return useMutation({
  mutationFn: (input) => api.PATCH(...),
  onSuccess: () => queryClient.invalidateQueries({ queryKey: calendarQueryKeys.connections(mailboxId) }),
  meta: {
    successMessage: t('calendar.toggle.success'),
    errorMessage: t('calendar.toggle.error'),
  },
});
```

**Adapt:** do NOT call `toast.success/error` locally — `MutationCache.onError`/`onSuccess` in `lib/query-client.tsx` reads `meta` globally.

## Shared Patterns

### Privacy logging (CONVENTIONS §5)

**Source:** `GoogleOAuthSuccessHandler.java:137–139`
**Apply to:** every new backend service/handler/classifier in this phase

```java
log.info("event=oauth_provisioning_complete tenantId={}", result.tenantId());
log.warn("event=oauth_settings_basic_missing tenantId={}", result.tenantId());
```

**Phase 12 specifics:** `CalendarMessageClassifier` MUST NEVER log icsBody, attendee emails, or DTSTART raw value (only the resulting `event=calendar_parsed tenantId={} messageClass={INVITE|CANCEL|...}`).

### Thin controller + `Response.from(...)` (CONVENTIONS §1)

**Source:** `TenantStatusController.java`
**Apply to:** `CalendarConnectionController`, `MailboxCalendarPreferenceController`

Controllers inject services ONLY; never repositories. Response DTOs own `static from(domainResult)` factories.

### Service-owned `@Transactional` (CONVENTIONS §1, §6)

**Source:** `GmailConnectionService.java:71`
**Apply to:** `CalendarConnectionService`, `CalendarSnapshotIngestionService`, `MailboxCalendarPreferenceService`

Use `TransactionTemplate` for D-14's multi-table cascade-disconnect (delete preferences + null booking link + flip status atomically), then publish `CalendarConnectionDisconnected` AFTER_COMMIT.

### Records-for-DTOs + OpenAPI schema discipline (CONVENTIONS §3)

**Source:** `MeResponse.java`
**Apply to:** every file under `api/dto/calendar/`

After each DTO add/change: `./gradlew :backend:api:generateOpenApiDocs` → `pnpm --filter web run generate:api` → commit regenerated `schema.d.ts`.

### IdentifiedEnum + `fromId` fail-loud (CONVENTIONS §4)

**Source:** `OnboardingStep.java:111–122`
**Apply to:** `CalendarConnectionStatus`, `CalendarRole`, `MessageClass`

Storage uses `id()`; reads call `EnumX.fromId(string)` which throws `NoSuchElementException` on unknown ids — never silently default.

### Liquibase changelog discipline (CONVENTIONS §11 / project §10)

**Source:** `119-gmail-connections-multi-mailbox.yaml`
**Apply to:** `131-`, `132-`, `133-`, `134-` + master include

Append-only; one logical change per changeset; explicit `rollback` block; `splitStatements: false` for compound SQL. After adding, include in `db.changelog-master.yaml`. NEVER edit applied changesets.

### Frontend feature layout (CONVENTIONS §8 + `apps/web/AGENTS.md`)

**Source:** `apps/web/features/mailbox/**`
**Apply to:** `apps/web/features/calendar/**`

`api/` for HTTP, `query-keys.ts` for cache identity, `hooks/use-*.ts` one-per-use-case, `components/` for view. No barrel; import concrete files. Types from `components['schemas'][...]`, NEVER hand-written mirror DTOs. Mutations use `meta.successMessage/errorMessage`; no local `toast.*`.

### shadcn raw primitives + token classes (`apps/web/AGENTS.md`)

**Apply to:** every component under `features/calendar/components/`

Use `Card`, `Collapsible`, `Select`, `Switch`, `Badge`, `DropdownMenu` directly from `@/components/ui/*`. Token classes only (`bg-card`, `border-border`, `text-foreground`) — no `bg-[#xxx]`. No wrapper components per memory `feedback_raw_shadcn_first.md`.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `backend/worker/.../triage/CalendarPartParser.java` | utility | transform | First ical4j integration in the repo; use RESEARCH §D Pattern 4 sketch verbatim |
| `features/calendar/components/RoleAssignmentSection.tsx` | composed view | view | Calendly-pattern multi-select + 2× single-select overlay has no analog; compose from raw shadcn `Select` + a `MultiSelect` (install via `pnpm dlx shadcn@latest add multi-select` if missing) |
| `backend/worker/.../triage/CalendarMessageClassifier.java` | event listener | event-driven | No existing worker AFTER_COMMIT listener on `MailMessageObserved` found by glob — listener mechanics from CONVENTIONS §6 + Spring docs; verify during planning whether a precedent listener exists in worker |

## Metadata

**Analog search scope:**
- `backend/core/src/main/java/com/zeromail/core/gmail/**` (full enumeration — primary analog source)
- `backend/core/src/main/java/com/zeromail/core/inbox/persistence/**`
- `backend/core/src/main/java/com/zeromail/core/rules/domain/RuleEvaluator.java`
- `backend/api/src/main/java/com/zeromail/api/security/**` (full enumeration)
- `backend/core/src/test/java/com/zeromail/core/admin/arch/**`
- `backend/core/src/main/resources/db/changelog/changes/11*.yaml`
- `apps/web/features/mailbox/**` + `apps/web/features/account/**`

**Files scanned:** ~80 (glob enumerations) + 11 fully read for excerpts
**Pattern extraction date:** 2026-06-20
