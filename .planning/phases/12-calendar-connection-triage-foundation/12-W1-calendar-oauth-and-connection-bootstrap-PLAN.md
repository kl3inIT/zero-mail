---
phase: 12-calendar-connection-triage-foundation
plan: 02
type: execute
wave: 1
depends_on:
  - 12-01
files_modified:
  - backend/api/src/main/resources/application.yml
  - backend/api/src/main/java/com/zeromail/api/security/CalendarClientRegistrationConfig.java
  - backend/api/src/main/java/com/zeromail/api/security/GoogleAuthorizationRequestResolver.java
  - backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java
  - backend/api/src/test/java/com/zeromail/api/security/CalendarClientRegistrationConfigTest.java
  - backend/api/src/test/java/com/zeromail/api/security/CalendarOAuthSuccessHandlerTest.java
  - backend/api/src/test/java/com/zeromail/api/security/CalendarOAuthTokenIsolationTest.java
  - backend/core/src/main/java/com/zeromail/core/calendar/domain/CalendarConnectionStatus.java
  - backend/core/src/main/java/com/zeromail/core/calendar/domain/MailboxCalendarRole.java
  - backend/core/src/main/java/com/zeromail/core/calendar/domain/package-info.java
  - backend/core/src/main/java/com/zeromail/core/calendar/exception/CalendarConnectionNotOwnedException.java
  - backend/core/src/main/java/com/zeromail/core/calendar/exception/CalendarDisconnectedException.java
  - backend/core/src/main/java/com/zeromail/core/calendar/exception/package-info.java
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarConnectionEntity.java
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarConnectionRepository.java
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarEntity.java
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarRepository.java
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/MailboxCalendarPreferenceEntity.java
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/MailboxCalendarPreferenceRepository.java
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/package-info.java
  - backend/core/src/main/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactory.java
  - backend/core/src/main/java/com/zeromail/core/calendar/gateway/package-info.java
  - backend/core/src/main/java/com/zeromail/core/calendar/package-info.java
  - backend/core/src/test/java/com/zeromail/core/calendar/persistence/CalendarConnectionCipherTest.java
  - backend/core/src/test/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactoryDisconnectTest.java
autonomous: true
requirements:
  - CAL-CONN-01
  - CAL-CONN-02
  - CAL-CONN-03
  - CAL-CONN-08
must_haves:
  truths:
    - "GET /oauth2/authorization/google-calendar redirects to Google with the three calendar scopes ONLY (no openid/profile/email/gmail.modify in the redirect URL)"
    - "Calendar OAuth round-trip writes refresh_token_encrypted to calendar_connections; gmail_connections row for the same tenant is byte-identical pre- and post-flow"
    - "CalendarApiClientFactory.buildClientForCalendarConnection(tenantId, X) throws CalendarConnectionNotOwnedException when tenantId does not own X, and CalendarDisconnectedException when X is DISCONNECTED or REVOKED"
    - "GoogleOAuthScope.CALENDAR_FREEBUSY.value() is the source of the scope URL passed to ClientRegistration.scope(...); no string literal exists at the call site"
  artifacts:
    - path: "backend/api/src/main/java/com/zeromail/api/security/CalendarClientRegistrationConfig.java"
      provides: "Second Spring Security ClientRegistration bean named google-calendar sharing the Google client-id"
      contains: "google-calendar"
    - path: "backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java"
      provides: "OAuth success handler that resolves the calendar registration response and writes to calendar_connections only — never touches gmail_connections"
    - path: "backend/core/src/main/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactory.java"
      provides: "Per-connection Google Calendar client builder; refresh-token-then-AccessToken cache keyed by calendarConnectionId; fail-fast on DISCONNECTED/REVOKED"
    - path: "backend/core/src/main/java/com/zeromail/core/calendar/domain/CalendarConnectionStatus.java"
      provides: "IdentifiedEnum with values CONNECTED, DISCONNECTED, REVOKED (CAL-CONN-08 three-state machine)"
      contains: "CONNECTED"
    - path: "backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarConnectionEntity.java"
      provides: "JPA entity for calendar_connections row; refresh_token_encrypted stored via OAuthTokenStore"
  key_links:
    - from: "CalendarClientRegistrationConfig"
      to: "GoogleOAuthScope.CALENDAR_FREEBUSY.value()"
      via: "scope() builder reads from the enum so OAuthScopeAllowListTest never fires here"
      pattern: "GoogleOAuthScope.CALENDAR"
    - from: "CalendarOAuthSuccessHandler"
      to: "OAuthTokenStore.encrypt(plaintext, tenantId, CALENDAR_CONNECTION)"
      via: "refresh-token encryption path that NEVER writes to gmail_connections.refresh_token_encrypted"
      pattern: "RowDiscriminator.CALENDAR_CONNECTION"
    - from: "CalendarApiClientFactory"
      to: "CalendarConnectionStatus"
      via: "status guard throws CalendarDisconnectedException for any non-CONNECTED status"
      pattern: "CalendarConnectionStatus.CONNECTED"
---

<objective>
Land the Calendar OAuth registration + connection bootstrap WITHOUT any user-visible UI yet (W3 owns that) and WITHOUT cascade-disconnect/list-connections service (W2 owns those):

1. A second Spring Security `ClientRegistration` bean (`google-calendar`) sharing the existing Google client-id, requesting only `CALENDAR_FREEBUSY`/`CALENDAR_EVENTS`/`CALENDAR_READONLY` URLs read from the `GoogleOAuthScope` enum.
2. An extension to the existing `GoogleAuthorizationRequestResolver` so the `google-calendar` registrationId always carries `prompt=consent` + `include_granted_scopes=true` + `access_type=offline` (CAL-CONN-02 explicit-action).
3. A new `CalendarOAuthSuccessHandler` that writes ONLY to `calendar_connections.refresh_token_encrypted` via the `OAuthTokenStore` facade — never touches `gmail_connections` (Pitfall 2 mitigation).
4. JPA entities + repositories + domain enums + exceptions under `core.calendar.{domain,exception,persistence,gateway}`.
5. `CalendarApiClientFactory` mirroring `GmailApiClientFactory` shape: per-connection access-token cache, refresh-token decrypt via `OAuthTokenStore`, fail-fast on non-CONNECTED status with `CalendarDisconnectedException`.

The actual `CalendarConnectionService` (with `disconnect()` cascade + `CalendarConnectionDisconnected` event + `CalendarSnapshotIngestionService`) is W2 work. This plan ends when a successful OAuth round-trip persists one row in `calendar_connections` and the new factory can mint a Google Calendar client for that row.

Purpose: Provide the OAuth seam + token store call path so W2's service can focus on cascade/snapshot/event semantics without re-litigating crypto + registration shape, and so W3's frontend has a Connect endpoint to call.
Output: 1 application.yml edit + 3 new API/security classes + 1 modified resolver + 11 new core.calendar files + 3 tests.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/STATE.md
@.planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md
@.planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md
@.planning/phases/12-calendar-connection-triage-foundation/12-PATTERNS.md
@.planning/phases/12-calendar-connection-triage-foundation/12-01-SUMMARY.md
</context>

<artifacts_this_phase_produces>
This plan creates the following Phase 12 symbols that W2..W5 will consume:

- `CalendarConnectionStatus` IdentifiedEnum (CONNECTED / DISCONNECTED / REVOKED) per D-08 three-state.
- `MailboxCalendarRole` IdentifiedEnum (FREEBUSY / EVENT_WRITE / BRIEF_SOURCE) per CAL-CONN-07.
- `CalendarConnectionEntity`, `CalendarConnectionRepository` (findByIdAndTenantId, findAllByTenantId, save).
- `CalendarEntity`, `CalendarRepository` (findAllByCalendarConnectionId, save).
- `MailboxCalendarPreferenceEntity`, `MailboxCalendarPreferenceRepository` (findAllByMailboxId, save, deleteByCalendarConnectionId, deleteByCalendarId).
- `CalendarConnectionNotOwnedException`, `CalendarDisconnectedException`.
- `CalendarApiClientFactory.buildClientForCalendarConnection(UUID tenantId, UUID calendarConnectionId)` returns `com.google.api.services.calendar.Calendar`.
- `CalendarApiClientFactory.evictAccessToken(UUID calendarConnectionId)` — called by W2 disconnect path.
- `google-calendar` `ClientRegistration` bean.
- `CalendarOAuthSuccessHandler` (Spring `AuthenticationSuccessHandler`) wired into `SecurityConfig` via a registrationId-aware dispatch (mirror existing Gmail success/failure dispatch from `GoogleOAuthSuccessHandler`).

NOT in this plan (deferred):
- `CalendarConnectionService.list/disconnect/cascade` — W2.
- `CalendarSnapshotIngestionService` (calendarList.list ingest) — W2.
- `CalendarConnectionDisconnected` Modulith event + listener — W2.
- REST controllers + DTOs — W2.
- Frontend route + hooks — W3.
- ical4j classifier and inbox-projection ORDER BY change — W4.
- PRESET_CALENDAR matcher and rule evaluator branch — W5.

This plan inserts EXACTLY ONE row into `calendar_connections` per successful OAuth round-trip — sub-calendar enumeration via `calendarList.list()` is W2 work. The status starts as `CONNECTED` and remains CONNECTED for the entire W1 surface; W2 introduces the disconnect transitions.
</artifacts_this_phase_produces>

<tasks>

<task type="auto">
  <name>Task 1: Domain enums + exceptions + JPA entities + repositories under core.calendar</name>
  <files>backend/core/src/main/java/com/zeromail/core/calendar/package-info.java, backend/core/src/main/java/com/zeromail/core/calendar/domain/package-info.java, backend/core/src/main/java/com/zeromail/core/calendar/domain/CalendarConnectionStatus.java, backend/core/src/main/java/com/zeromail/core/calendar/domain/MailboxCalendarRole.java, backend/core/src/main/java/com/zeromail/core/calendar/exception/package-info.java, backend/core/src/main/java/com/zeromail/core/calendar/exception/CalendarConnectionNotOwnedException.java, backend/core/src/main/java/com/zeromail/core/calendar/exception/CalendarDisconnectedException.java, backend/core/src/main/java/com/zeromail/core/calendar/persistence/package-info.java, backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarConnectionEntity.java, backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarConnectionRepository.java, backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarEntity.java, backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarRepository.java, backend/core/src/main/java/com/zeromail/core/calendar/persistence/MailboxCalendarPreferenceEntity.java, backend/core/src/main/java/com/zeromail/core/calendar/persistence/MailboxCalendarPreferenceRepository.java, backend/core/src/test/java/com/zeromail/core/calendar/persistence/CalendarConnectionCipherTest.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/gmail/domain/GmailConnectionStatus.java (existing IdentifiedEnum state-machine pattern — the exact shape CalendarConnectionStatus mirrors)
    - backend/core/src/main/java/com/zeromail/core/onboarding/domain/OnboardingStep.java (fail-loud fromId pattern CONVENTIONS.md §4)
    - backend/core/src/main/java/com/zeromail/core/shared/lang/IdentifiedEnum.java (interface to implement)
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java (full entity — the columns + AbstractTenantOwnedEntity base + @TenantId binding + crypto column naming convention)
    - backend/core/src/main/java/com/zeromail/core/shared/persistence/AbstractTenantOwnedEntity.java (the entity base class — confirm constructor signature and tenant_id field convention)
    - backend/core/src/main/java/com/zeromail/core/gmail/exception/* (find via Glob — the existing exception package shape under a core domain)
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionRepository.java (Spring Data JPA repository shape + findByIdAndTenantId convention)
    - backend/core/src/main/java/com/zeromail/core/gmail/package-info.java (Modulith allowedDependencies declaration shape — calendar/package-info.java mirrors this; calendar must declare allowedDependencies = {tenant, shared.lang, shared.persistence, oauth.token})
    - backend/core/src/test/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipherTest.java (cipher round-trip test pattern — CalendarConnectionCipherTest mirrors it but uses OAuthTokenStore via RowDiscriminator.CALENDAR_CONNECTION and a real Postgres via PostgresContainerTest if entity-DB integration is asserted)
    - backend/core/src/test/java/com/zeromail/core/support/PostgresContainerTest.java (Testcontainers base for any DB-touching test)
    - .planning/phases/12-calendar-connection-triage-foundation/12-PATTERNS.md (§§ "CalendarConnectionEntity" + "MailboxCalendarPreferenceEntity" — the adapt rules including OMIT list at line 190)
    - .planning/phases/12-calendar-connection-triage-foundation/12-01-SUMMARY.md (the W0 committed OAuthTokenStore API surface)
    - CLAUDE.md §"Backend Code Style" (explicit naming — no req/res/repo/svc/cfg abbreviations)
    - CONVENTIONS.md §4 IdentifiedEnum fromId fail-loud
  </read_first>
  <action>
    Create `backend/core/src/main/java/com/zeromail/core/calendar/package-info.java` annotated with `@org.springframework.modulith.ApplicationModule(displayName="Calendar", allowedDependencies={"tenant", "shared.lang", "shared.persistence", "oauth.token", "gmail", "inbox"})`. Confirm against `core.gmail/package-info.java` for exact import + annotation shape — if Modulith requires the `oauth.scope` edge too, add it. Calendar reads `gmail_connections.id` (via `MailboxCalendarPreferenceEntity.mailbox_id` FK), so `gmail` is a needed allowedDependency; the `inbox` edge becomes hot in W4 — declare it now to avoid amending Modulith metadata mid-phase per phase-1.2 lesson `Forward-decl deferral protocol`. The `oauth.token` edge is the OAuthTokenStore facade dependency from W0.

    Create the four leaf `package-info.java` files under `domain/`, `exception/`, `persistence/`, `gateway/` (gateway is empty for Task 1 — declare it anyway so Task 3 doesn't need to add the package). Each declares only the package; no Modulith annotations needed because the parent `core.calendar` module owns the boundary.

    Create `CalendarConnectionStatus.java` implementing `IdentifiedEnum` per CONVENTIONS §4. Values: `CONNECTED("CONNECTED")`, `DISCONNECTED("DISCONNECTED")`, `REVOKED("REVOKED")`. `id()` returns the constant string. Static `fromId(String id)` throws `NoSuchElementException("Unknown CalendarConnectionStatus id: " + id)`. Per project memory `feedback_explain_before_options.md`, JavaDoc each constant naming the transition that lands it: CONNECTED on successful first-OAuth, DISCONNECTED on user-initiated disconnect (W2), REVOKED on Google-side revoke (W2 listener handles this distinction). NO `labelKey()` (CONVENTIONS §4 D-B5 — status is unordered identity, NOT OrderedEnum).

    Create `MailboxCalendarRole.java` implementing `IdentifiedEnum`. Values: `FREEBUSY("FREEBUSY")`, `EVENT_WRITE("EVENT_WRITE")`, `BRIEF_SOURCE("BRIEF_SOURCE")`. Static `fromId` per the same convention. JavaDoc each role:
    - FREEBUSY: "Calendar contributes free/busy time blocks for this mailbox's AI drafts (Phase 13). Multi-select per mailbox."
    - EVENT_WRITE: "Calendar receives event writes (Phase 14 booking confirmations). Single-select per mailbox — enforced by partial unique index uq_mailbox_event_write."
    - BRIEF_SOURCE: "Calendar contributes events for meeting briefs (Phase 16). Single-select per mailbox — enforced by partial unique index uq_mailbox_brief_source."

    Create `CalendarConnectionNotOwnedException` extending `RuntimeException`. Constructor `(UUID tenantId, UUID calendarConnectionId)` formats message `"calendar_connection_not_owned tenantId=%s calendarConnectionId=%s"` using `%s` placeholders. Exposes accessors `tenantId()` and `calendarConnectionId()`. Privacy: never include googleEmail in the message.

    Create `CalendarDisconnectedException` similarly. Constructor `(UUID tenantId, UUID calendarConnectionId, CalendarConnectionStatus status)`. Accessor `status()` returns the offending non-CONNECTED status. Message format `"calendar_connection_disconnected tenantId=%s calendarConnectionId=%s status=%s"`.

    Create `CalendarConnectionEntity` extending `AbstractTenantOwnedEntity` per PATTERNS.md lines 165-188. Columns (using `@Column` JPA names matching the Liquibase 131 changeset): `google_email varchar(320)` (`@Column(nullable=false)`), `status varchar(32)` (`@Enumerated(EnumType.STRING)` stores `CalendarConnectionStatus.name()` — confirm `name() == id()` for this enum), `refresh_token_encrypted byte[]` (the column name distinct from `refreshToken` for the privacy regex per PATTERNS.md line 175), `scopes_granted text`, `connected_at timestamptz`, `disconnected_at timestamptz`, `google_profile_name varchar(255)`, `google_profile_picture_url text`, `version int`. OMIT (per PATTERNS.md line 190): isPrimary, displayPurpose, lastSyncedHistoryId, watchHistoryId, watchExpiresAt, watchRenewedAt, watchConsecutiveFailures, ingestionHealth — Calendar Phase 12 does not poll. Provide protected no-arg ctor (Hibernate) + public ctor `(UUID id, UUID tenantId, String googleEmail, CalendarConnectionStatus status)` matching Gmail's shape. No Lombok (CLAUDE.md hard ban). Explicit getters; no chained setters.

    Create `CalendarConnectionRepository extends org.springframework.data.jpa.repository.JpaRepository<CalendarConnectionEntity, UUID>` with methods: `Optional<CalendarConnectionEntity> findByIdAndTenantId(UUID id, UUID tenantId)`, `java.util.List<CalendarConnectionEntity> findAllByTenantId(UUID tenantId)`. Per CLAUDE.md naming, use `calendarConnectionRepository` at injection sites (NOT `connectionRepo` / `repo`).

    Create `CalendarEntity` (the per-sub-calendar row in `calendars`). Columns: `calendar_connection_id uuid`, `tenant_id uuid`, `external_calendar_id text NOT NULL`, `name varchar(512)`, `description text`, `is_primary boolean`, `is_enabled boolean`, `timezone varchar(64)`. Use `@ManyToOne` to `CalendarConnectionEntity` if the Modulith conventions allow JPA back-references; otherwise carry only the UUID FK + the back-ref is queried via repository. Decide based on what Gmail uses (per `GmailConnectionEntity` precedent — Gmail uses UUID FK + repository lookup, no `@ManyToOne` traversal). Mirror that pattern. Public ctor matching the Liquibase 132 column order.

    Create `CalendarRepository extends JpaRepository<CalendarEntity, UUID>` with `List<CalendarEntity> findAllByCalendarConnectionIdAndTenantId(UUID calendarConnectionId, UUID tenantId)`, `List<CalendarEntity> findAllByCalendarConnectionIdAndTenantIdAndIsEnabledTrue(UUID calendarConnectionId, UUID tenantId)`.

    Create `MailboxCalendarPreferenceEntity` matching the Liquibase 133 columns. Constructor `(UUID id, UUID tenantId, UUID mailboxId, UUID calendarConnectionId, UUID calendarId, MailboxCalendarRole role)`. `role` persisted as `varchar(32)` storing `role.id()` (CONVENTIONS §4 mapping).

    Create `MailboxCalendarPreferenceRepository extends JpaRepository<MailboxCalendarPreferenceEntity, UUID>` with: `List<MailboxCalendarPreferenceEntity> findAllByMailboxIdAndTenantId(UUID mailboxId, UUID tenantId)`, `@Modifying @Query("DELETE FROM MailboxCalendarPreferenceEntity p WHERE p.calendarConnectionId = :calendarConnectionId AND p.tenantId = :tenantId") int deleteByCalendarConnectionId(@Param("calendarConnectionId") UUID calendarConnectionId, @Param("tenantId") UUID tenantId)`. Tenant-scope the bulk delete explicitly per T-01.2.1-07 lesson in STATE.md. Also `int deleteByCalendarIdAndTenantId(UUID calendarId, UUID tenantId)` for D-13.

    Create `CalendarConnectionCipherTest` extending `PostgresContainerTest`. Test plan: (a) build an `OAuthTokenStore` against the test cipher (use the same key-bootstrap pattern as the existing RefreshTokenCipherTest); (b) encrypt a sample plaintext for tenantA + `RowDiscriminator.CALENDAR_CONNECTION`; (c) save a `CalendarConnectionEntity` with that ciphertext in `refreshTokenEncrypted`; (d) re-load via repository; (e) decrypt with OAuthTokenStore + tenantA + CALENDAR_CONNECTION → equals plaintext. ALSO: seed a row in `gmail_connections` for tenantA with a different known ciphertext; run the Calendar test path (encrypt+save the new ciphertext to `calendar_connections`); then re-load the `gmail_connections` row by tenant and assert its `refresh_token_encrypted` is byte-identical to the pre-test state (Pitfall 2 mitigation — Calendar OAuth path must never touch the Gmail row). Per TESTING.md §3 use `@DataJpaTest` slice or `PostgresContainerTest` — pick whichever the project's existing JPA test suite uses for cross-domain Gmail+Calendar reads. Per CONVENTIONS.md privacy logging — no `googleEmail` in test stderr.

    JetBrains MCP: after creating each Java file, run `mcp__jetbrains__get_file_problems` on the file and resolve any issues before moving to the next file (per memory `feedback_jetbrains_problem_check.md`).
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:core:test --tests "com.zeromail.core.calendar.persistence.CalendarConnectionCipherTest"</automated>
  </verify>
  <acceptance_criteria>
    - All 15 listed files exist; each `package-info.java` is in place.
    - `grep -c 'implements IdentifiedEnum' backend/core/src/main/java/com/zeromail/core/calendar/domain/CalendarConnectionStatus.java` returns at least 1.
    - `grep -c 'gmail_connection_id' backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarConnectionEntity.java | grep -v '^#'` returns 0 — entity must NOT carry a Gmail FK (CAL-CONN-06).
    - `CalendarConnectionCipherTest` passes; the Gmail-row byte-identity assertion is green (proves no cross-write).
    - `cd backend && ./gradlew :backend:core:test --tests "ApplicationModulesTest"` is green (Modulith boundary not violated by the new `core.calendar` allowedDependencies).
    - `cd backend && ./gradlew :backend:core:test --tests "DomainPurityArchTest"` (if exists) is green — `core.calendar.domain.*` has zero Spring / Jakarta / Hibernate imports per CONVENTIONS §2.
    - JetBrains `get_file_problems` returns no errors on any of the 15 new files.
  </acceptance_criteria>
  <done>The `core.calendar` package skeleton compiles, the cipher path is proven to write only the calendar row, and the Modulith boundary holds.</done>
</task>

<task type="auto">
  <name>Task 2: google-calendar ClientRegistration + GoogleAuthorizationRequestResolver branch + tests</name>
  <files>backend/api/src/main/resources/application.yml, backend/api/src/main/java/com/zeromail/api/security/CalendarClientRegistrationConfig.java, backend/api/src/main/java/com/zeromail/api/security/GoogleAuthorizationRequestResolver.java, backend/api/src/test/java/com/zeromail/api/security/CalendarClientRegistrationConfigTest.java</files>
  <read_first>
    - backend/api/src/main/resources/application.yml (existing `spring.security.oauth2.client.registration.google:` block — the Calendar registration appends as a sibling; do NOT modify the existing Google bundle)
    - backend/api/src/main/java/com/zeromail/api/security/GoogleAuthorizationRequestResolver.java (full file — the `customizeAuthorizationRequest` method this plan extends with a `google-calendar` branch)
    - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java (where the Calendar success handler will eventually wire — read to know the existing user-chain @Order(50) registers OAuth2 Login filter and how dispatch by registrationId works)
    - backend/core/src/main/java/com/zeromail/core/oauth/scope/GoogleOAuthScope.java (W0 enum — the source of truth for scope URLs)
    - .planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md (§Pattern 1 lines 327-391 — the exact YAML + Java config sketch + critical "read scope URLs from GoogleOAuthScope enum at the bean-config level" note at line 367)
    - .planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md (D-02 enum-reads-scope-URLs requirement; D-05 multi-account from day one)
    - CLAUDE.md hard "do not use" list (raw HTTP LLM calls + raw Google SDK outside the adapter — for Calendar, the gateway boundary is `core.calendar.gateway.CalendarApiClientFactory` introduced in Task 3 of this plan; the Spring Security `ClientRegistration` itself is NOT subject to that ban — it is OAuth wire shape)
  </read_first>
  <action>
    Edit `backend/api/src/main/resources/application.yml`: append a sibling block `spring.security.oauth2.client.registration.google-calendar:` after the existing `google:` block. Set `provider: google` so it reuses the existing provider URI block, `client-id: ${GOOGLE_OAUTH_CLIENT_ID:}` and `client-secret: ${GOOGLE_OAUTH_CLIENT_SECRET:}` (SAME values — Pitfall section in RESEARCH.md line 130 confirms shared GCP client), `authorization-grant-type: authorization_code`, `redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"`, and `scope:` listing the three calendar scope URLs as YAML strings (this is the ONE place the URL literal lives in production source, deliberately allowed because YAML resource files are NOT scanned by `OAuthScopeAllowListTest` per W0 Task 1 design). Add an inline `# zeromail: calendar registration; URLs mirror GoogleOAuthScope.CALENDAR_FREEBUSY/EVENTS/READONLY` comment above the scope block. Do NOT add `prompt`/`access_type`/`include_granted_scopes` to YAML — those are dynamic, set by the resolver.

    Create `backend/api/src/main/java/com/zeromail/api/security/CalendarClientRegistrationConfig.java` per RESEARCH.md §Pattern 1 lines 808-840. `@Configuration`. Do NOT define the `ClientRegistration` bean inline if Spring's `spring.security.oauth2.client.registration.google-calendar.*` YAML auto-binding already produces it via `ClientRegistrationRepository` — this is the project's existing pattern for the `google` registration; verify by reading `SecurityConfig` to confirm Spring auto-binds YAML registrations into the `ClientRegistrationRepository` bean. If auto-binding is in place, this `@Configuration` class is empty or holds only the registration sanity assertion (a `@PostConstruct` that reads `clientRegistrationRepository.findByRegistrationId("google-calendar")` and asserts the bean has exactly the three Calendar scopes from `GoogleOAuthScope.CALENDAR_FREEBUSY.value() / CALENDAR_EVENTS.value() / CALENDAR_READONLY.value()`, throwing `IllegalStateException` on mismatch — this is the ledger-vs-YAML drift guard).
    If auto-binding does NOT apply (project explicitly registers `ClientRegistration` Java beans), define `googleCalendarClientRegistration` as a `@Bean` per RESEARCH.md lines 822-839, with `.scope(GoogleOAuthScope.CALENDAR_FREEBUSY.value(), GoogleOAuthScope.CALENDAR_EVENTS.value(), GoogleOAuthScope.CALENDAR_READONLY.value())` — NO string literal. Inject `clientId` + `clientSecret` via `@Value("${spring.security.oauth2.client.registration.google.client-id}")` (SAME google registration's client-id, NOT a `google-calendar.client-id` placeholder — Pitfall avoidance per RESEARCH.md line 130).

    Edit `backend/api/src/main/java/com/zeromail/api/security/GoogleAuthorizationRequestResolver.java`. In `customizeAuthorizationRequest(...)`, extend the existing `prompt=consent` logic (currently triggered by `RECONNECT_PARAMETER`) so the `google-calendar` registrationId ALWAYS sets `prompt=consent` per RESEARCH.md §Pattern 1 lines 369-391 + D-09 acceptance of Pitfall 7. Implementation:
    - At the top of the method, after the existing null-guard, read `String registrationId = authorizationRequest.getAttribute(org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.REGISTRATION_ID);`
    - Compute `boolean calendarFlow = "google-calendar".equals(registrationId);`
    - In the existing `additionalParameters` block where `access_type` and `include_granted_scopes` are set, also set `prompt` to `consent` when `calendarFlow || "true".equals(servletRequest.getParameter(RECONNECT_PARAMETER))`.
    - Per RESEARCH.md line 388 the existing Gmail reconnect path is preserved (no behavioral change).
    Add a class-level JavaDoc paragraph noting the calendar branch and citing D-02 + CAL-CONN-01.

    Create `backend/api/src/test/java/com/zeromail/api/security/CalendarClientRegistrationConfigTest.java` per VALIDATION.md `TBD-w1-01`. Use `@SpringBootTest` + the existing test profile (per TESTING.md §3 — full app context test, justified because the assertion is on registered `ClientRegistration` beans). Assertions: (a) `clientRegistrationRepository.findByRegistrationId("google-calendar")` returns a non-null `ClientRegistration`; (b) its `getScopes()` returns EXACTLY `Set.of(GoogleOAuthScope.CALENDAR_FREEBUSY.value(), GoogleOAuthScope.CALENDAR_EVENTS.value(), GoogleOAuthScope.CALENDAR_READONLY.value())`; (c) `getClientId().equals(googleRegistration.getClientId())` (shared client-id with `google`); (d) per CAL-CONN-02 "no full calendar scope, ever" — assert `getScopes()` does NOT contain `"https://www.googleapis.com/auth/calendar"` (the unsafe full scope) — build the literal in the test by concatenation so the literal does not appear in the test source. Build the resolver with the `clientRegistrationRepository` + `ServletRequest` mock; mock a `google-calendar` registrationId on the authz request and assert the returned `OAuth2AuthorizationRequest.getAdditionalParameters()` contains `prompt=consent`.

    Privacy logging: any log statement in `CalendarClientRegistrationConfig` and `GoogleAuthorizationRequestResolver` uses the existing `event=` opaque-name pattern (CONVENTIONS §5), never logs scope URLs as data (only the registrationId).
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:api:test --tests "com.zeromail.api.security.CalendarClientRegistrationConfigTest"</automated>
  </verify>
  <acceptance_criteria>
    - `application.yml` carries a `google-calendar:` registration block with exactly three calendar scope URLs.
    - `grep -c 'auth/calendar' backend/api/src/main/resources/application.yml` returns at least 3 (freebusy, events, readonly entries).
    - `grep -cE '"https?://www\.googleapis\.com/auth/calendar' backend/api/src/main/java/com/zeromail/api/security/CalendarClientRegistrationConfig.java | grep -v '^#'` returns 0 — Java config reads from `GoogleOAuthScope.X.value()` only (D-03 invariant).
    - `OAuthScopeAllowListTest` from W0 is still green (Java config has no literal URL; YAML is allow-listed).
    - `CalendarClientRegistrationConfigTest` is green.
    - `GoogleAuthorizationRequestResolver` continues to pass any existing reconnect test (no regression on Gmail flow).
    - JetBrains `get_file_problems` returns no errors on the modified `GoogleAuthorizationRequestResolver.java`.
  </acceptance_criteria>
  <done>Hitting `/oauth2/authorization/google-calendar` redirects the user to Google requesting only the three calendar scopes with `prompt=consent`; the existing Gmail login bundle is untouched.</done>
</task>

<task type="auto">
  <name>Task 3: CalendarOAuthSuccessHandler + CalendarApiClientFactory + isolation + disconnect tests</name>
  <files>backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java, backend/core/src/main/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactory.java, backend/api/src/test/java/com/zeromail/api/security/CalendarOAuthSuccessHandlerTest.java, backend/api/src/test/java/com/zeromail/api/security/CalendarOAuthTokenIsolationTest.java, backend/core/src/test/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactoryDisconnectTest.java</files>
  <read_first>
    - backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java (full file — the dispatch shape, OAuth2AuthorizedClient consumption, refresh-token extraction + encryption + persistence pattern that CalendarOAuthSuccessHandler mirrors)
    - backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java (full file — the per-connection access-token cache, refresh-token decrypt path, fail-fast on non-CONNECTED status pattern CalendarApiClientFactory mirrors)
    - backend/core/src/main/java/com/zeromail/core/oauth/token/OAuthTokenStore.java (W0 Task 2 facade — the encrypt/decrypt API used here)
    - backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java (find the dispatch point between `GoogleOAuthSuccessHandler` and Calendar — verify whether SecurityConfig wires a single `AuthenticationSuccessHandler` that dispatches by registrationId, or two distinct success handler beans; mirror whichever shape exists)
    - .planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md (§Pattern 1 + §Pitfall 2 + §Pattern §"CalendarApiClientFactory" at lines 842-879)
    - .planning/phases/12-calendar-connection-triage-foundation/12-PATTERNS.md (§§ "CalendarApiClientFactory" + "GoogleOAuthSuccessHandler MODIFY-seed-calendar" — the latter clarifies the success-handler does NOT seed the system-calendar rule in this plan; that wiring is W5)
    - CLAUDE.md "Backend Code Style" (explicit naming — `oAuthAuthorizedClient`, `calendarConnectionId`, `accessTokenResponse` etc.)
  </read_first>
  <action>
    Create `backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java` per PATTERNS.md and RESEARCH.md §Pitfall 2.
    - Implement `org.springframework.security.web.authentication.AuthenticationSuccessHandler`.
    - Constructor-inject: `CalendarConnectionRepository calendarConnectionRepository`, `OAuthTokenStore oAuthTokenStore`, `OAuth2AuthorizedClientService oAuth2AuthorizedClientService`, `org.springframework.context.ApplicationEventPublisher applicationEventPublisher` (forward-decl for W2 event publication — used here only as the bean handle).
    - In `onAuthenticationSuccess(...)`: read the `OAuth2AuthenticationToken authenticationToken`; if `authenticationToken.getAuthorizedClientRegistrationId().equals("google-calendar")` then run the Calendar flow; else delegate to a noop (registrationId-based dispatch). Pull the `OAuth2AuthorizedClient` via `oAuth2AuthorizedClientService.loadAuthorizedClient("google-calendar", authenticationToken.getName())`.
    - Extract `String refreshTokenValue = oAuth2AuthorizedClient.getRefreshToken() == null ? null : oAuth2AuthorizedClient.getRefreshToken().getTokenValue();`. If `refreshTokenValue == null` per Pitfall 7 acceptance, throw `OAuth2AuthenticationException` with error code `consent_denied_calendar` and return — Google must have re-issued a refresh token because `prompt=consent` was set; null here means the user revoked midway.
    - Resolve `tenantId` from `TenantContext.currentOrThrow()` per existing handler pattern (the session is still bound to the same user — the calendar flow runs inside the user chain).
    - Resolve `googleEmail` from `authenticationToken.getPrincipal()` as an `OidcUser` (per Gmail handler pattern). If no OidcUser is present (calendar flow does NOT request openid/profile/email scopes), fall back to a Google profile API call — actually NO, per RESEARCH.md lines 327-366 and `include_granted_scopes=true`, the existing user-session already provides the OIDC profile from the original login; reuse `authenticationToken.getPrincipal()` if it is an OidcUser, else fall back to the existing session's authentication — TenantContext binding means we know the user.
    - Encrypt: `byte[] refreshTokenEncrypted = oAuthTokenStore.encrypt(refreshTokenValue.getBytes(StandardCharsets.UTF_8), tenantId, OAuthTokenStore.RowDiscriminator.CALENDAR_CONNECTION);`.
    - Build a `CalendarConnectionEntity` with `status = CalendarConnectionStatus.CONNECTED`, `connectedAt = Instant.now()`, `googleEmail`, `scopesGranted = String.join(" ", oAuth2AuthorizedClient.getAccessToken().getScopes())` (the three calendar URLs), and save via `calendarConnectionRepository`.
    - If a `CONNECTED` row already exists for the same `(tenantId, lower(googleEmail))` — surface from Liquibase 131 `uq_calendar_conn_active_email` partial unique index — catch `org.springframework.dao.DataIntegrityViolationException` and surface as `OAuth2AuthenticationException` with code `calendar_connection_already_active`. Per PROJECT.md "ASVS V4" the explicit constraint name match is the race-proof backstop (parallels Phase 10 D-08 pattern); the friendly message can be a pre-check via `findAllByTenantId` but the index is the truth.
    - DO NOT call `calendarList.list()` here — snapshot ingestion is W2 work.
    - DO NOT touch the `gmail_connections` row — the test in Task 1 already pins this; the success handler must respect it.
    - Wire the handler in `SecurityConfig` by EITHER registering it as a second `AuthenticationSuccessHandler` keyed by registrationId (per the existing dispatch convention), OR replacing the single user-chain handler with a thin dispatcher: `if (authentication.getAuthorizedClientRegistrationId().equals("google-calendar")) calendarHandler.onAuthenticationSuccess(...); else googleHandler.onAuthenticationSuccess(...);`. Choose based on the read-first inspection of `SecurityConfig` (Task 2 read it).
    - Privacy logging (CONVENTIONS §5): `log.info("event=calendar_oauth_connect_success tenantId={} calendarConnectionId={}", tenantId, calendarConnection.getId());`. NEVER log `refreshTokenValue` or `googleEmail`.

    Create `backend/core/src/main/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactory.java` per RESEARCH.md §Pattern + PATTERNS.md "CalendarApiClientFactory" lines 224-247.
    - `@Component`. Constructor-inject `CalendarConnectionRepository`, `OAuthTokenStore`, `@Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId`, `@Value("${spring.security.oauth2.client.registration.google.client-secret}") String clientSecret` (SAME google registration's secrets — Pitfall avoidance).
    - Maintain a `ConcurrentHashMap<UUID, TokenRefreshResult> accessTokenCache` keyed by `calendarConnectionId` (PATTERNS.md line 238).
    - Define `public Calendar buildClientForCalendarConnection(UUID tenantId, UUID calendarConnectionId)` throwing `IOException`:
      1. Load entity via `calendarConnectionRepository.findByIdAndTenantId(calendarConnectionId, tenantId).orElseThrow(() -> new CalendarConnectionNotOwnedException(tenantId, calendarConnectionId));`.
      2. If `calendarConnection.getStatus() != CalendarConnectionStatus.CONNECTED` throw `new CalendarDisconnectedException(tenantId, calendarConnectionId, calendarConnection.getStatus());`.
      3. Use the cached access token if `accessTokenCache.get(calendarConnectionId)` is still valid (5-minute safety window pre-expiry — mirror the existing Gmail factory's `isExpiring` check).
      4. Otherwise decrypt the refresh token: `byte[] refreshTokenBytes = oAuthTokenStore.decrypt(calendarConnection.getRefreshTokenEncrypted(), tenantId, OAuthTokenStore.RowDiscriminator.CALENDAR_CONNECTION);`. Convert to `String refreshToken = new String(refreshTokenBytes, StandardCharsets.UTF_8);`.
      5. POST to `https://oauth2.googleapis.com/token` with `grant_type=refresh_token` + `refresh_token` + `client_id` + `client_secret` (mirror the Gmail factory's existing HTTP refresh path verbatim — same `HttpClient` + `ObjectMapper`). Parse response into a `TokenRefreshResult` (access token + expiry). Cache.
      6. Build the Google `Calendar` API client via `new Calendar.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), new HttpCredentialsAdapter(GoogleCredentials.create(new AccessToken(accessToken, null)))).setApplicationName("ZeroMail").build();`.
    - Define `public void evictAccessToken(UUID calendarConnectionId) { accessTokenCache.remove(calendarConnectionId); }` — W2 disconnect path calls this.
    - Privacy: never log refresh token bytes or access token bytes; `log.info("event=calendar_access_token_refreshed tenantId={} calendarConnectionId={}", tenantId, calendarConnectionId);` is the only log statement on the refresh path.

    Create `backend/api/src/test/java/com/zeromail/api/security/CalendarOAuthSuccessHandlerTest.java` per VALIDATION.md TBD-w1-01-style. Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + a real session minted by `TestSessionSupport.TestSessionMinter` (per TESTING.md §3 "spring-security-testing"). Mock the upstream Google OAuth via WireMock or a stubbed `OAuth2AuthorizedClientService` returning a known `OAuth2AuthorizedClient` for registrationId `google-calendar` with a known refresh-token value. Drive the Spring success-handler entry point. Assertions: (a) one row exists in `calendar_connections` for the test tenant with `status='CONNECTED'`; (b) the row's `refreshTokenEncrypted` decrypts via `OAuthTokenStore.decrypt(..., RowDiscriminator.CALENDAR_CONNECTION)` to the known plaintext; (c) `event=calendar_oauth_connect_success` was logged (capture Logback events via the existing logback test appender pattern); (d) no `event=gmail_*` log lines appear during the calendar flow.

    Create `backend/api/src/test/java/com/zeromail/api/security/CalendarOAuthTokenIsolationTest.java` — the explicit T-12-05 mitigation. Seed `gmail_connections` for tenantA with a known `refreshTokenEncrypted = E1`. Run a Calendar OAuth flow for tenantA producing `E2` saved to `calendar_connections`. Re-load the `gmail_connections` row for tenantA. Assert `refreshTokenEncrypted` is byte-identical to `E1` (Pitfall 2). Assert the new `calendar_connections` row's `refreshTokenEncrypted` is `E2` and decrypts to the expected plaintext via `RowDiscriminator.CALENDAR_CONNECTION`.

    Create `backend/core/src/test/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactoryDisconnectTest.java` per VALIDATION.md TBD-w1-03. Plain JUnit 5 (TESTING.md §3 Layer 1 — no Spring context). Mock `CalendarConnectionRepository.findByIdAndTenantId(...)` to return a `CalendarConnectionEntity` with `status = CalendarConnectionStatus.DISCONNECTED`. Build the factory with mock collaborators. Assert `buildClientForCalendarConnection(tenantId, calendarConnectionId)` throws `CalendarDisconnectedException` carrying the offending status. Repeat for `REVOKED`. Also assert: (e) `findByIdAndTenantId` returning `Optional.empty()` → `CalendarConnectionNotOwnedException` thrown. (f) `evictAccessToken(X)` removes a previously-cached entry for `X` (build the factory, populate cache reflectively or via a successful build then evict, observe cache miss on next call).
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:api:test --tests "com.zeromail.api.security.CalendarOAuthSuccessHandlerTest" --tests "com.zeromail.api.security.CalendarOAuthTokenIsolationTest" :backend:core:test --tests "com.zeromail.core.calendar.gateway.CalendarApiClientFactoryDisconnectTest"</automated>
  </verify>
  <acceptance_criteria>
    - `CalendarOAuthSuccessHandler.java` exists; `grep -c 'gmail_connection' backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java | grep -v '^#'` returns 0 — the handler must never reference Gmail persistence.
    - `grep -c 'RowDiscriminator.CALENDAR_CONNECTION' backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java` returns at least 1.
    - `grep -c 'getRefreshToken' backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java` returns at least 1, AND no log statement in the file references the refresh-token variable (manual review — diagnostics output of `OAuthSuccessHandlerTest` does NOT include the refresh-token bytes).
    - `CalendarApiClientFactory.java` exists; `grep -c 'CalendarDisconnectedException' backend/core/src/main/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactory.java` returns at least 1.
    - `grep -c 'GmailConnection' backend/core/src/main/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactory.java | grep -v '^#'` returns 0.
    - `CalendarOAuthSuccessHandlerTest`, `CalendarOAuthTokenIsolationTest`, `CalendarApiClientFactoryDisconnectTest` all green.
    - JetBrains `get_file_problems` returns no errors on the two new production files.
    - `ApplicationModulesTest` is green — the new `core.calendar.gateway` package respects Modulith allowedDependencies.
  </acceptance_criteria>
  <done>OAuth round-trip end-to-end works in tests: a fake calendar grant produces exactly one `calendar_connections` row with an encrypted refresh token, and `CalendarApiClientFactory` can re-mint a `Calendar` API client for that row. The Gmail row is provably untouched.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Browser → /oauth2/authorization/google-calendar | User initiates calendar grant; Spring Security generates state + CSRF token. |
| Google OAuth callback → /login/oauth2/code/google-calendar | Untrusted query params; Spring's OAuth2 Login filter handles `state` + `code` exchange. |
| OAuth refresh token → calendar_connections.refresh_token_encrypted | Sensitive secret; encrypted at rest via AES-GCM with tenantId AAD. |
| `OAuth2AuthorizedClient` (Calendar registration) → CalendarConnectionEntity | Calendar handler must write ONLY to calendar_connections; cross-registration write would leak Calendar's RT into Gmail's row. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-12-01 | Information Disclosure | CalendarApiClientFactory — cross-tenant calendar leak | mitigate | `tenantId` AAD on cipher (W0); `findByIdAndTenantId(...)` enforces row ownership; `CalendarConnectionNotOwnedException` on mismatch. Verified by `CalendarApiClientFactoryDisconnectTest` case (e). |
| T-12-02 | Elevation of Privilege | Stale access token after disconnect | partial — fully mitigated in W2 | `CalendarApiClientFactory.evictAccessToken(connectionId)` exposed in W1; W2's disconnect path calls it. Disconnect-state guard via `CalendarDisconnectedException` already in place. |
| T-12-03 | Tampering | OAuth state mismatch / CSRF on callback | mitigate | Spring Security's built-in `state` + nonce checks — no custom impl. Verified indirectly: `CalendarOAuthSuccessHandlerTest` runs through the real Spring filter chain. |
| T-12-05 | Tampering | Cross-registration RT leak (Calendar RT overwrites Gmail RT) | mitigate | Calendar handler writes ONLY to `calendar_connections.refresh_token_encrypted` — explicit invariant + `CalendarOAuthTokenIsolationTest` integration test asserts Gmail row byte-identity (Pitfall 2). `grep -c 'gmail_connection' CalendarOAuthSuccessHandler.java` returns 0. |
| T-12-06 | Tampering | Open-redirect via OAuth success target | mitigate | Reuse `baseUrl` scheme+host validation from existing `GoogleOAuthSuccessHandler` (verified via read-first); no new redirect target introduced. `redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"` is the only redirect surface. |
| T-12-07 | Elevation of Privilege | Scope-string drift (developer types full `calendar` instead of `calendar.events`) | mitigate | INFRA-01 ledger (W0) + `CalendarClientRegistrationConfigTest` asserts the `getScopes()` set EXACTLY equals the three enum values AND does not contain the full `calendar` scope. |
| T-12-V6 | Cryptography | OAuth refresh-token-at-rest | mitigate | `OAuthTokenStore.encrypt(...)` with `RowDiscriminator.CALENDAR_CONNECTION` — AES-GCM via shared cipher. Verified by Task 1's `CalendarConnectionCipherTest`. |
</threat_model>

<verification>
- `cd backend && ./gradlew :backend:api:test --tests "com.zeromail.api.security.Calendar*Test"` — all three security tests green.
- `cd backend && ./gradlew :backend:core:test --tests "com.zeromail.core.calendar.*Test"` — entity, repository, cipher, factory disconnect tests green.
- `cd backend && ./gradlew :backend:api:check` — full backend check green (ApplicationModulesTest, DomainPurityArchTest, all unchanged).
- Manual check: start backend with the calendar registration; hit `GET /oauth2/authorization/google-calendar` in a browser; verify the Google consent screen shows only "View free/busy", "Make changes to events", "View events on calendars".
- `grep -rn 'auth/calendar' backend/api/src/main/java/com/zeromail/` returns ZERO matches (the only YAML hit is `application.yml` which the W0 scanner excludes).
</verification>

<success_criteria>
- A successful Calendar OAuth round-trip persists exactly one row in `calendar_connections` for the test tenant, with `status=CONNECTED` and a decryptable refresh token.
- The Gmail `gmail_connections` row for the same tenant is byte-identical to its pre-flow state (Pitfall 2 mitigation passes integration test).
- `CalendarApiClientFactory.buildClientForCalendarConnection(...)` returns a usable `Calendar` API client when status is CONNECTED, and throws `CalendarDisconnectedException` for DISCONNECTED/REVOKED + `CalendarConnectionNotOwnedException` when the tenant does not own the row.
- The `google-calendar` `ClientRegistration` carries exactly `[CALENDAR_FREEBUSY.value(), CALENDAR_EVENTS.value(), CALENDAR_READONLY.value()]` — verified at boot time by `CalendarClientRegistrationConfig`'s `@PostConstruct` assertion.
- Modulith + ArchUnit + Domain-purity gates all green.
</success_criteria>

<output>
Create `.planning/phases/12-calendar-connection-triage-foundation/12-02-SUMMARY.md` listing: (a) the chosen dispatch shape in `SecurityConfig` (two beans vs single dispatcher), (b) confirmation that `OAuthScopeAllowListTest` is still green, (c) the integration-test output proving Gmail-row byte-identity, (d) the JetBrains `get_file_problems` output for `CalendarOAuthSuccessHandler.java` and `CalendarApiClientFactory.java` showing no errors, (e) the `calendar_connections` row count + status after running `CalendarOAuthSuccessHandlerTest`.
</output>
