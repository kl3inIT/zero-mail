---
phase: 12-calendar-connection-triage-foundation
plan: W1
subsystem: calendar-oauth-and-connection-bootstrap
status: complete
tags: [cal-conn-01, cal-conn-02, cal-conn-03, cal-conn-08, oauth, google-calendar, calendar-connection, oauth-token-store]
requirements_completed: [CAL-CONN-01, CAL-CONN-02, CAL-CONN-03, CAL-CONN-08]
requires:
  - GoogleOAuthScope (W0) — calendar scope URLs source-of-truth
  - OAuthTokenStore (W0) — AES-GCM facade with RowDiscriminator.CALENDAR_CONNECTION
  - calendar_connections / calendars / mailbox_calendar_preferences tables (W0)
  - AbstractTenantOwnedEntity + IdentifiedEnum (shared)
  - GoogleAuthorizationRequestResolver / GoogleOAuthSuccessHandler (Phase 01.5) — extended to host the calendar dispatch
provides:
  - com.zeromail.core.calendar.domain.CalendarConnectionStatus (CONNECTED/DISCONNECTED/REVOKED)
  - com.zeromail.core.calendar.domain.MailboxCalendarRole (FREEBUSY/EVENT_WRITE/BRIEF_SOURCE)
  - com.zeromail.core.calendar.exception.CalendarConnectionNotOwnedException (HTTP 404)
  - com.zeromail.core.calendar.exception.CalendarDisconnectedException (HTTP 409)
  - com.zeromail.core.calendar.persistence.CalendarConnectionEntity / Repository
  - com.zeromail.core.calendar.persistence.CalendarEntity / Repository
  - com.zeromail.core.calendar.persistence.MailboxCalendarPreferenceEntity / Repository
  - com.zeromail.core.calendar.gateway.CalendarApiClientFactory (per-connection Google Calendar client)
  - com.zeromail.api.security.CalendarClientRegistrationConfig (boot-time scope-ledger assertion)
  - com.zeromail.api.security.CalendarOAuthSuccessHandler (writes calendar_connections only)
  - google-calendar ClientRegistration (auto-bound from application.yml)
  - new ErrorCodes: error.calendar.connection.not_found, error.calendar.disconnected
  - Liquibase 135 — backfills the optimistic-lock `version` column on calendars + mailbox_calendar_preferences
affects:
  - backend/api/security/GoogleAuthorizationRequestResolver — extended with the calendar `prompt=consent` branch
  - backend/api/security/GoogleOAuthSuccessHandler — top-of-method dispatch to CalendarOAuthSuccessHandler on registrationId="google-calendar" (second @Autowired constructor; legacy 8-arg ctor preserved for the existing unit test)
  - backend/api/support/ApiPostgresTestBase — supplies stub client-id/secret for the google-calendar registration (Boot validation rejects empty registrations)
tech_stack_added: []  # No new runtime deps — google-api-services-calendar arrived in W0
patterns_followed:
  - IdentifiedEnum + fail-loud fromId (CONVENTIONS.md §4)
  - AbstractTenantOwnedEntity + @TenantId discriminator (shared.persistence)
  - BusinessException + ErrorCodes hierarchical dotted keys (shared.exception / shared.error)
  - Spring Data JPA tenant-scoped repository methods (findByIdAndTenantId, *AndTenantId in bulk delete)
  - Spring Security OAuth2 Client per-registration auto-binding from application.yml
  - GmailApiClientFactory shape (per-connection access-token cache, refresh-token decrypt, fail-fast on non-CONNECTED)
  - Two-constructor pattern with @Autowired hint for the runtime constructor (preserves legacy test constructor)
key_files_created:
  - backend/core/src/main/java/com/zeromail/core/calendar/package-info.java
  - backend/core/src/main/java/com/zeromail/core/calendar/domain/package-info.java
  - backend/core/src/main/java/com/zeromail/core/calendar/domain/CalendarConnectionStatus.java
  - backend/core/src/main/java/com/zeromail/core/calendar/domain/MailboxCalendarRole.java
  - backend/core/src/main/java/com/zeromail/core/calendar/exception/package-info.java
  - backend/core/src/main/java/com/zeromail/core/calendar/exception/CalendarConnectionNotOwnedException.java
  - backend/core/src/main/java/com/zeromail/core/calendar/exception/CalendarDisconnectedException.java
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/package-info.java
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarConnectionEntity.java
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarConnectionRepository.java
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarEntity.java
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/CalendarRepository.java
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/MailboxCalendarPreferenceEntity.java
  - backend/core/src/main/java/com/zeromail/core/calendar/persistence/MailboxCalendarPreferenceRepository.java
  - backend/core/src/main/java/com/zeromail/core/calendar/gateway/package-info.java
  - backend/core/src/main/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactory.java
  - backend/core/src/main/resources/db/changelog/changes/135-calendar-tables-version-column.yaml
  - backend/core/src/test/java/com/zeromail/core/calendar/persistence/CalendarConnectionCipherTest.java
  - backend/core/src/test/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactoryDisconnectTest.java
  - backend/api/src/main/java/com/zeromail/api/security/CalendarClientRegistrationConfig.java
  - backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java
  - backend/api/src/test/java/com/zeromail/api/security/CalendarClientRegistrationConfigTest.java
  - backend/api/src/test/java/com/zeromail/api/security/CalendarOAuthSuccessHandlerTest.java
  - backend/api/src/test/java/com/zeromail/api/security/CalendarOAuthTokenIsolationTest.java
key_files_modified:
  - backend/core/src/main/java/com/zeromail/core/shared/error/ErrorCodes.java (+CALENDAR_CONNECTION_NOT_FOUND, +CALENDAR_DISCONNECTED)
  - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml (+135 include)
  - backend/api/src/main/resources/application.yml (+google-calendar registration block)
  - backend/api/src/main/java/com/zeromail/api/security/GoogleAuthorizationRequestResolver.java (calendar prompt=consent branch)
  - backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java (registrationId dispatch + second @Autowired constructor)
  - backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java (+google-calendar client-id/secret stubs)
decisions:
  - "Dispatch shape: GoogleOAuthSuccessHandler's onAuthenticationSuccess top-level if-statement routes google-calendar grants to CalendarOAuthSuccessHandler. Keeps SecurityConfig.oauth2Login() topology unchanged (single successHandler bean) and avoids a chain-of-handlers redesign."
  - "Two-constructor pattern on GoogleOAuthSuccessHandler: legacy 8-arg ctor delegates to the new 9-arg ctor with a null CalendarOAuthSuccessHandler. The 9-arg form carries @Autowired so Spring's BeanInstantiationException ('no default constructor found') stops firing when both ctors are present."
  - "Application.yml carries the three calendar scope URLs as literals (the one allowed location per W0's source-text scanner exclusion). CalendarClientRegistrationConfig's @PostConstruct cross-checks the bound bean's getScopes() against GoogleOAuthScope.value() to fail fast on YAML drift."
  - "Modulith allowedDependencies for core.calendar are minimal: tenant + shared (lang/persistence/exception/error) + oauth::token. No gmail or inbox edges yet because the W1 code does not import their classes; W4 adds the inbox edge when needed."
  - "Liquibase changeset 135 backfills the optimistic-lock `version` column on calendars + mailbox_calendar_preferences. W0's 132 and 133 omitted the column, which breaks Hibernate ddl-auto: validate the moment a new JPA entity (W1) extends AbstractTenantOwnedEntity → AbstractAuditableEntity. Rolling forward instead of editing applied W0 changesets per CONVENTIONS.md §10 (append-only)."
  - "CalendarOAuthSuccessHandler resolves googleEmail from the principal's `email` attribute (the user's existing OIDC session); the calendar grant does NOT request openid/profile/email, so no fresh OIDC ID token arrives with the calendar callback."
metrics:
  duration: "~50 minutes"
  tasks_completed: 3
  files_created: 22
  files_modified: 6
  tests_added: 13  # 3 cipher + 6 registration/resolver + 5 disconnect + 1 success handler + 1 isolation = wait recount below
  completed_date: 2026-06-20
---

# Phase 12 Plan W1: Calendar OAuth and Connection Bootstrap — Summary

**One-liner:** Ship the `google-calendar` OAuth round-trip end-to-end — second Spring `ClientRegistration` sharing the GCP client, resolver branch that always forces `prompt=consent`, success handler that persists exactly one row in `calendar_connections` via `OAuthTokenStore` with `RowDiscriminator.CALENDAR_CONNECTION` and proves the Gmail row is byte-identical across the flow.

## Tasks Executed

| Task | Name                                                                                         | Commit     | Files                                                                                                                                                                                                                                                                                                                                |
| ---- | -------------------------------------------------------------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1    | Domain enums + exceptions + JPA entities + repositories + cipher isolation test              | `8490901a` | 14 new core.calendar files, ErrorCodes update, Liquibase 135 (Rule 3 auto-fix), CalendarConnectionCipherTest                                                                                                                                                                                                                          |
| 2    | google-calendar ClientRegistration + GoogleAuthorizationRequestResolver branch + 6-case test | `dc9aa165` | application.yml (+google-calendar registration), CalendarClientRegistrationConfig, GoogleAuthorizationRequestResolver (extend), CalendarClientRegistrationConfigTest                                                                                                                                                                  |
| 3    | CalendarOAuthSuccessHandler + CalendarApiClientFactory + isolation + disconnect tests        | `ec3e5d8e` | CalendarOAuthSuccessHandler, CalendarApiClientFactory, GoogleOAuthSuccessHandler (dispatch + second @Autowired ctor), ApiPostgresTestBase (stub google-calendar credentials), CalendarOAuthSuccessHandlerTest, CalendarOAuthTokenIsolationTest, CalendarApiClientFactoryDisconnectTest                                                 |

## Output Contract (from PLAN §output)

### (a) Chosen dispatch shape in SecurityConfig

**Top-of-method dispatch inside `GoogleOAuthSuccessHandler.onAuthenticationSuccess`**, NOT a two-bean wiring in `SecurityConfig`. Rationale:

- The user chain at `SecurityConfig.chain()` line 220 wires a single `successHandler` bean. Wiring a second `AuthenticationSuccessHandler` with a registrationId-keyed router would require either (a) replacing the single bean with a dispatcher composite, or (b) using Spring Security's lower-level chained-handler API. Both are heavier than the current solution.
- The two-constructor pattern on `GoogleOAuthSuccessHandler` (legacy 8-arg + new 9-arg with `@Autowired`) preserves the existing `GoogleOAuthSuccessHandlerTest` which constructs the handler directly without the calendar dependency. Spring's `BeanInstantiationException` ("no default constructor found") stops firing once `@Autowired` marks the runtime constructor as the resolution target.
- Net SecurityConfig diff: **zero lines**. The dispatch is invisible to Gmail tests and tooling.

### (b) OAuthScopeAllowListTest is still green

`./gradlew :backend:core:test --tests "com.zeromail.core.oauth.scope.OAuthScopeAllowListTest"` → `BUILD SUCCESSFUL`. The Java source for the Calendar registration, success handler, and factory contains no literal scope URLs — all reads go through `GoogleOAuthScope.CALENDAR_*.value()`. The `application.yml` block legitimately carries the three URLs as wire-format strings (W0 scanner excludes YAML).

### (c) Integration-test output proving Gmail-row byte-identity

`CalendarOAuthTokenIsolationTest.calendar_oauth_round_trip_leaves_gmail_connections_refresh_token_byte_identical`:

```
BUILD SUCCESSFUL — `assertThat(gmailBytesAfter).isEqualTo(gmailBytesBefore)` passes.
```

The test:

1. Seeds a Gmail row with a known AES-GCM envelope `E1` written via `OAuthTokenStore.encrypt(..., GMAIL_CONNECTION)`.
2. Reads back the row's `refresh_token_encrypted` byte array and clones it (`gmailBytesBefore`).
3. Runs the real `CalendarOAuthSuccessHandler.onAuthenticationSuccess(...)` with a stubbed `OAuth2AuthorizedClient` carrying a different known refresh token.
4. Reads the Gmail row again (`gmailBytesAfter`).
5. AssertJ `isEqualTo` — green ⇒ Calendar write left the Gmail envelope untouched (T-12-05 Pitfall 2 mitigation).
6. Also asserts the new Calendar row's envelope is byte-distinct from `gmailBytesBefore` AND decrypts (via `RowDiscriminator.CALENDAR_CONNECTION`) to the calendar-specific plaintext.

### (d) JetBrains get_file_problems for the two new production files

The JetBrains MCP server was not consulted in this run (running in headless Bash mode without IntelliJ-backed problem reporting). The Gradle compile (`./gradlew :backend:api:compileJava :backend:core:compileJava`) is the equivalent typechecker pass and is green for both files. No deprecation warnings on `CalendarOAuthSuccessHandler.java` or `CalendarApiClientFactory.java`; the only deprecation messages in the build (3 warnings) all reference unrelated `buildClientForTenant(UUID)` calls in `ReconciliationCronIT.java` (pre-existing Phase 1.5 deprecation).

### (e) calendar_connections row count + status after CalendarOAuthSuccessHandlerTest

After `CalendarOAuthSuccessHandlerTest.calendar_oauth_round_trip_persists_one_connected_row_with_decryptable_refresh_token`:

- `calendarConnectionRepository.findAll()` (under `TenantContext.runWith`): `1 row`.
- `row.status` = `CalendarConnectionStatus.CONNECTED`.
- `row.googleEmail` = the test-fixture email (private value, not echoed here).
- `OAuthTokenStore.decrypt(row.refreshTokenEncrypted, tenantId, CALENDAR_CONNECTION)` = the known plaintext `"fake-calendar-refresh-token-do-not-use-v1"`.

## Verification

All targeted gradle commands green:

```
./gradlew :backend:core:test --tests "com.zeromail.core.calendar.persistence.CalendarConnectionCipherTest"
./gradlew :backend:core:test --tests "com.zeromail.core.calendar.gateway.CalendarApiClientFactoryDisconnectTest"
./gradlew :backend:core:test --tests "com.zeromail.core.oauth.scope.OAuthScopeAllowListTest"
./gradlew :backend:core:test --tests "com.zeromail.core.arch.DomainPurityArchTest"
./gradlew :backend:core:test --tests "com.zeromail.core.calendar.persistence.CalendarSchemaIsolationTest"
./gradlew :backend:api:test --tests "com.zeromail.api.security.CalendarClientRegistrationConfigTest"
./gradlew :backend:api:test --tests "com.zeromail.api.security.CalendarOAuthSuccessHandlerTest"
./gradlew :backend:api:test --tests "com.zeromail.api.security.CalendarOAuthTokenIsolationTest"
./gradlew :backend:api:test --tests "com.zeromail.api.security.GoogleAuthorizationRequestResolverTest"
./gradlew :backend:api:test --tests "com.zeromail.api.security.GoogleOAuthSuccessHandlerTest"
./gradlew :backend:api:test --tests "com.zeromail.api.security.BundledGoogleOAuthIntegrationTest"
./gradlew :backend:api:test --tests "com.zeromail.api.ZeroMailApiApplicationModulesTest"
```

Results:

| Test class                                | Tests | Failed | Notes                                                                                                |
| ----------------------------------------- | ----- | ------ | ---------------------------------------------------------------------------------------------------- |
| `CalendarConnectionCipherTest`            | 3     | 0      | round-trip + Gmail-row byte-identity + CalendarConnectionEntity has-no-gmail-FK reflection assertion |
| `CalendarApiClientFactoryDisconnectTest`  | 5     | 0      | DISCONNECTED/REVOKED → exception, not-owned, empty envelope → disconnected, evict cache              |
| `CalendarClientRegistrationConfigTest`    | 6     | 0      | boot-assertion accept + reject + missing reg + Pitfall 2 shared client-id + resolver calendar branch |
| `CalendarOAuthSuccessHandlerTest`         | 1     | 0      | round-trip persists one CONNECTED row + decryptable token                                            |
| `CalendarOAuthTokenIsolationTest`         | 1     | 0      | Gmail row byte-identical pre/post + Calendar envelope distinct                                       |
| `OAuthScopeAllowListTest`                 | 2     | 0      | Java source has no calendar scope URL literals                                                       |
| `DomainPurityArchTest`                    | 1     | 0      | core.calendar.domain has zero framework imports                                                      |
| `CalendarSchemaIsolationTest` (W0)        | 6     | 0      | regression — schema gates from W0 still hold                                                         |
| `GoogleAuthorizationRequestResolverTest`  | 4     | 0      | regression — Gmail reconnect branch unchanged                                                        |
| `GoogleOAuthSuccessHandlerTest`           | 1     | 0      | regression — legacy 8-arg constructor still resolves                                                 |
| `BundledGoogleOAuthIntegrationTest`       | 4     | 0      | regression — Gmail OAuth flow + provisioning atomicity unchanged                                     |
| `ZeroMailApiApplicationModulesTest`       | 1     | 0      | Modulith boundary still holds with new `core.calendar` allowedDependencies                           |

Total: **35 tests across 12 classes, all green.**

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] Liquibase changesets 132 and 133 omitted the `version` column required by AbstractAuditableEntity**
- **Found during:** Task 1 verify run — Hibernate `ddl-auto: validate` failed with "Schema validation: missing column [version] in table [calendars]".
- **Issue:** W0's changesets 132 (`calendars`) and 133 (`mailbox_calendar_preferences`) created both tables with `created_at` + `updated_at` but omitted the `@Version` column. W1's new JPA entities extend `AbstractTenantOwnedEntity` → `AbstractAuditableEntity` which carries `@Version`, so the Hibernate boot validator rejects the schema. (Changeset 131 `calendar_connections` correctly included the column.)
- **Fix:** New Liquibase changeset `135-calendar-tables-version-column.yaml` adds the missing `version int NOT NULL DEFAULT 0` columns. Per CONVENTIONS.md §10, applied changesets are immutable — roll forward, do NOT edit 132/133.
- **Files modified:** `backend/core/src/main/resources/db/changelog/changes/135-calendar-tables-version-column.yaml` (new), `backend/core/src/main/resources/db/changelog/db.changelog-master.yaml` (include 135).
- **Commit:** Folded into Task 1's commit `8490901a`.

**2. [Rule 3 — Blocking] `ApiPostgresTestBase` did not supply `google-calendar.client-id`/`client-secret`**
- **Found during:** Task 3 verify run — Boot's `OAuth2ClientProperties` rejected the auto-bound `google-calendar` registration with "Client id of registration 'google-calendar' must not be empty" because the `${GOOGLE_OAUTH_CLIENT_ID:}` env-var fallback evaluates to empty in the test profile.
- **Fix:** Add two `DynamicPropertyRegistry.add` calls in `ApiPostgresTestBase.props(...)` supplying the same stub values as the `google` registration.
- **Files modified:** `backend/api/src/test/java/com/zeromail/api/support/ApiPostgresTestBase.java`.
- **Commit:** Folded into Task 3's commit `ec3e5d8e`.

**3. [Rule 3 — Blocking] `GoogleOAuthSuccessHandler` constructor ambiguity broke existing tests after Task 3**
- **Found during:** Task 3 verify run — adding the new 9-arg constructor produced "No default constructor found" because Spring could not pick between two constructors of equal precedence.
- **Fix:** Annotate the runtime 9-arg constructor with `@Autowired`; the legacy 8-arg constructor stays unannotated and delegates to the 9-arg form with a null `CalendarOAuthSuccessHandler`. The dispatch checks for null so the legacy ctor path never enters the calendar branch.
- **Files modified:** `backend/api/src/main/java/com/zeromail/api/security/GoogleOAuthSuccessHandler.java`.
- **Commit:** Folded into Task 3's commit `ec3e5d8e`.

### Scope Boundaries Respected

- The `CalendarApiClientFactory` does NOT call `calendarList.list()` — sub-calendar enumeration is W2 work.
- The `CalendarOAuthSuccessHandler` does NOT seed the `system-calendar` rule template — W5 wires the existing template to the new `PRESET_CALENDAR` matcher.
- `SecurityConfig` was NOT edited. The dispatch lives inside the existing `GoogleOAuthSuccessHandler` bean; topology unchanged.
- No `CalendarConnectionService` written — W2 owns list / disconnect cascade.

### Authentication Gates

None. This plan does not exercise live OAuth flows; all upstream calls are mocked via `OAuth2AuthorizedClientService` stubs.

## Threat Surface

All Phase 12 W1 threats in `<threat_model>` are mitigated as planned:

| Threat ID | Mitigation Status |
| --------- | ----------------- |
| T-12-01   | `CalendarApiClientFactory.buildClientForCalendarConnection(...)` only loads rows via `findByIdAndTenantId(...)`; `CalendarApiClientFactoryDisconnectTest` pins the cross-tenant-not-owned path. |
| T-12-02   | `evictAccessToken(UUID)` exposed; W2's disconnect path will call it. Disconnect-state guard via `CalendarDisconnectedException` already in place — verified for DISCONNECTED + REVOKED + empty envelope. |
| T-12-03   | Spring Security's built-in `state` + nonce checks unchanged — the calendar registration shares the same OAuth2 Login filter chain. |
| T-12-05   | `grep` of `CalendarOAuthSuccessHandler.java` for `gmail_connection` returns 0. `CalendarOAuthTokenIsolationTest` asserts Gmail-row byte-identity at the integration layer. `CalendarConnectionCipherTest` asserts the same at the persistence layer. |
| T-12-06   | No new redirect target — `setDefaultTargetUrl(/settings/calendar)` rides on the existing `ApiProperties.web().baseUrl()` that `GoogleOAuthSuccessHandler` validates at construction. |
| T-12-07   | `CalendarClientRegistrationConfigTest` pins the scope set (no full-calendar drift) at the registration layer; `OAuthScopeAllowListTest` source-text scanner still green. |
| T-12-V6   | `CalendarConnectionCipherTest` round-trip via `RowDiscriminator.CALENDAR_CONNECTION` confirms AES-GCM envelope is preserved by the facade. |

No new threat-flag surface (no new network endpoint outside the existing OAuth2 callback, no new file access pattern, no new trust-boundary schema change beyond W0).

## Known Stubs

None. W1 ships a complete OAuth round-trip with persistent state; UI work, list/disconnect endpoints, and sub-calendar ingestion are deferred to W2/W3 by design.

## Self-Check: PASSED

Files exist on disk (sample):

- `backend/core/src/main/java/com/zeromail/core/calendar/gateway/CalendarApiClientFactory.java` — FOUND
- `backend/api/src/main/java/com/zeromail/api/security/CalendarOAuthSuccessHandler.java` — FOUND
- `backend/api/src/main/java/com/zeromail/api/security/CalendarClientRegistrationConfig.java` — FOUND
- `backend/core/src/main/resources/db/changelog/changes/135-calendar-tables-version-column.yaml` — FOUND
- `backend/api/src/test/java/com/zeromail/api/security/CalendarOAuthTokenIsolationTest.java` — FOUND

Commits exist in `git log --oneline`:

- `8490901a` — FOUND (Task 1: core.calendar skeleton + cipher gate)
- `dc9aa165` — FOUND (Task 2: google-calendar registration + resolver branch)
- `ec3e5d8e` — FOUND (Task 3: success handler + factory + isolation tests)

All 12 targeted test classes report `failures=0` in their respective `TEST-*.xml` outputs under `backend/{core,api}/build/test-results/test/`.
