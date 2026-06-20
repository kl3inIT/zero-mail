---
phase: 12-calendar-connection-triage-foundation
plan: 01
type: execute
wave: 0
depends_on: []
files_modified:
  - gradle/libs.versions.toml
  - backend/core/build.gradle.kts
  - backend/worker/build.gradle.kts
  - backend/core/src/main/java/com/zeromail/core/oauth/scope/GoogleOAuthScope.java
  - backend/core/src/main/java/com/zeromail/core/oauth/scope/package-info.java
  - backend/core/src/main/java/com/zeromail/core/oauth/token/OAuthTokenStore.java
  - backend/core/src/main/java/com/zeromail/core/oauth/token/package-info.java
  - backend/core/src/test/java/com/zeromail/core/oauth/scope/GoogleOAuthScopeEnumTest.java
  - backend/core/src/test/java/com/zeromail/core/oauth/scope/OAuthScopeAllowListTest.java
  - backend/core/src/test/java/com/zeromail/core/oauth/token/OAuthTokenStoreRoundTripTest.java
  - backend/core/src/main/resources/db/changelog/changes/131-calendar-connections.yaml
  - backend/core/src/main/resources/db/changelog/changes/132-calendars.yaml
  - backend/core/src/main/resources/db/changelog/changes/133-mailbox-calendar-preferences.yaml
  - backend/core/src/main/resources/db/changelog/changes/134-inbox-projection-calendar-columns.yaml
  - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml
  - backend/core/src/test/java/com/zeromail/core/calendar/persistence/CalendarSchemaIsolationTest.java
autonomous: true
requirements:
  - INFRA-01
  - CAL-CONN-03
  - CAL-CONN-06
  - CAL-CONN-07
  - CAL-TRIAGE-01
must_haves:
  truths:
    - "Any Java file outside core.oauth.scope that hard-codes the string 'https://www.googleapis.com/auth/...' fails OAuthScopeAllowListTest with a clear file+line diagnostic"
    - "GoogleOAuthScope enum exposes CALENDAR_FREEBUSY, CALENDAR_EVENTS, CALENDAR_READONLY, GMAIL_MODIFY, OPENID, PROFILE, EMAIL with NO drive/drive.readonly/drive.metadata.readonly entries"
    - "OAuthTokenStore encrypts plaintext for tenantId X and decrypts to the same plaintext for tenantId X; tampering tenantId Y rejects the GCM tag"
    - "Liquibase migrates a fresh dev Postgres adding 4 tables/columns: calendar_connections, calendars, mailbox_calendar_preferences, and the gmail_inbox_projection.message_class + event_dt columns"
    - "calendar_connections has NO gmail_connection_id column (CalendarSchemaIsolationTest fails if added)"
    - "mailbox_calendar_preferences has partial unique indexes on (mailbox_id) WHERE role IN ('EVENT_WRITE','BRIEF_SOURCE')"
  artifacts:
    - path: "backend/core/src/main/java/com/zeromail/core/oauth/scope/GoogleOAuthScope.java"
      provides: "Canonical Java enum ledger (D-01); each constant's value() returns the scope URL"
      contains: "CALENDAR_FREEBUSY"
    - path: "backend/core/src/test/java/com/zeromail/core/oauth/scope/OAuthScopeAllowListTest.java"
      provides: "Source-text scanner (D-02) — fails CI on any unapproved Google scope literal outside core.oauth.scope"
    - path: "backend/core/src/main/java/com/zeromail/core/oauth/token/OAuthTokenStore.java"
      provides: "Thin facade over existing RefreshTokenCipher; row-discriminator enum lets Gmail and Calendar share the same AES-GCM envelope"
    - path: "backend/core/src/main/resources/db/changelog/changes/131-calendar-connections.yaml"
      provides: "calendar_connections table; workspace-shared; no gmail_connection_id FK"
      contains: "calendar_connections"
    - path: "backend/core/src/main/resources/db/changelog/changes/132-calendars.yaml"
      provides: "Per-connection sub-calendar rows (primary + secondary)"
      contains: "calendars"
    - path: "backend/core/src/main/resources/db/changelog/changes/133-mailbox-calendar-preferences.yaml"
      provides: "Mailbox role-tag join table (freebusy/event_write/brief_source)"
      contains: "mailbox_calendar_preferences"
    - path: "backend/core/src/main/resources/db/changelog/changes/134-inbox-projection-calendar-columns.yaml"
      provides: "Two nullable columns on gmail_inbox_projection: message_class varchar(16) + event_dt timestamptz (D-11)"
      contains: "message_class"
    - path: "gradle/libs.versions.toml"
      provides: "ical4j 4.2.4 + google-api-services-calendar v3-rev2026*-2.0.0 catalog entries"
      contains: "ical4j"
  key_links:
    - from: "OAuthTokenStore"
      to: "RefreshTokenCipher"
      via: "delegation — facade calls cipher.encrypt(plaintext, tenantId.toString()) unchanged"
      pattern: "RefreshTokenCipher"
    - from: "131-calendar-connections.yaml"
      to: "db.changelog-master.yaml"
      via: "include statement at the master changelog tail (append-only, after 130-gmail-connection-profile.yaml)"
      pattern: "131-calendar-connections.yaml"
    - from: "OAuthScopeAllowListTest"
      to: "GoogleOAuthScope"
      via: "package whitelist ..core.oauth.scope.. so the enum body itself does not self-fail"
      pattern: "core/oauth/scope"
---

<objective>
Establish the Phase 12 foundation that every later wave (W1..W5) consumes:

1. The OAuth scope ledger (INFRA-01): `GoogleOAuthScope` enum + `OAuthScopeAllowListTest` source-text scan that fails CI if any production class outside `core.oauth.scope` hard-codes a `https://www.googleapis.com/auth/...` string. Lets Phase 15 enforce `drive.file`-only by simple omission.
2. The `OAuthTokenStore` thin facade over the existing `RefreshTokenCipher`. The cipher class is NOT modified; only a discriminator-aware facade is added so W1 can persist Calendar refresh tokens through the same AES-GCM envelope Gmail already uses (per D-14 Claude's Discretion).
3. The four Liquibase changesets (131, 132, 133, 134) that create the workspace-shared Calendar schema + the two new nullable columns on `gmail_inbox_projection` — schema-only; no service/code reads them yet.
4. Catalog entries for `ical4j 4.2.4` + `google-api-services-calendar v3-rev2026*-2.0.0` in `libs.versions.toml`, wired as compile/runtime dependencies on `backend/core` (Calendar client) and `backend/worker` (ical4j).

Per D-02 caveat from RESEARCH.md §A and Pitfall 1 (line 685), ArchUnit cannot inspect method-call argument constants via byte-code (`JavaClassProcessor.visitField` drops the value during ASM import). The ledger enforcement is therefore implemented as a source-text scan over `backend/{api,core,worker}/src/main/java`, NOT as `noClasses().that()...containAnyConstantMatching(...)`. The test still lives under `src/test/java/.../oauth/scope/` and runs via `./gradlew :backend:core:test`.

Purpose: Make W1..W5 stop being blocked on schema/dep prep. After this plan ships, every later wave can run in parallel against committed schema + catalog state.
Output: 9 new tests + production files + 4 changesets + 1 master-include edit + 1 toml edit.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md
@.planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md
@.planning/phases/12-calendar-connection-triage-foundation/12-PATTERNS.md
@.planning/phases/12-calendar-connection-triage-foundation/12-VALIDATION.md
</context>

<artifacts_this_phase_produces>
This plan creates the following Phase 12 symbols/artifacts that W1..W5 will consume:

- Java enum `com.zeromail.core.oauth.scope.GoogleOAuthScope` with constants `OPENID`, `PROFILE`, `EMAIL`, `GMAIL_MODIFY`, `CALENDAR_FREEBUSY`, `CALENDAR_EVENTS`, `CALENDAR_READONLY`; method `value()` returns the literal URL.
- Test `OAuthScopeAllowListTest` — source-text scan; fails on any unapproved scope literal outside `..core.oauth.scope..`.
- Test `GoogleOAuthScopeEnumTest` — `fromValue(...)` round-trip + no-duplicates + no-drive-entries invariants.
- Spring component `com.zeromail.core.oauth.token.OAuthTokenStore` with inner enum `RowDiscriminator { GMAIL_CONNECTION, CALENDAR_CONNECTION }`; methods `encrypt(byte[] plaintext, UUID tenantId, RowDiscriminator d)` + `decrypt(byte[] envelope, UUID tenantId, RowDiscriminator d)`.
- Test `OAuthTokenStoreRoundTripTest` — encrypt-then-decrypt for both discriminators and AAD-tamper rejection.
- Liquibase changesets 131, 132, 133, 134 included in `db.changelog-master.yaml` (append-only after 130).
- Schema-introspection test `CalendarSchemaIsolationTest` — asserts `calendar_connections` has NO `gmail_connection_id` column (CAL-CONN-06 invariant).
- `libs.versions.toml` versions `ical4j = "4.2.4"`, `calendarApi = "v3-rev2026*-2.0.0"` (latest dated rev on the `2.0.0` line; resolved with the Maven Central probe in Task 1); library aliases `ical4j` + `google-api-services-calendar`.

NOT in this plan (deferred to later waves):
- Any `CalendarConnectionEntity`, JPA repository, service, or controller — W1/W2.
- Any reference to `OAuthTokenStore` from Gmail call sites — W1/W2 introduce Calendar callers; Gmail keeps calling `RefreshTokenCipher` directly until a later refactor phase.
- Any read or write of the new `message_class` / `event_dt` columns — W4 classifier writes them; W4 read-side ORDER BY consumes them.
</artifacts_this_phase_produces>

<tasks>

<task type="auto">
  <name>Task 1: GoogleOAuthScope enum + OAuthScopeAllowListTest source-text scanner + GoogleOAuthScopeEnumTest</name>
  <files>backend/core/src/main/java/com/zeromail/core/oauth/scope/GoogleOAuthScope.java, backend/core/src/main/java/com/zeromail/core/oauth/scope/package-info.java, backend/core/src/test/java/com/zeromail/core/oauth/scope/GoogleOAuthScopeEnumTest.java, backend/core/src/test/java/com/zeromail/core/oauth/scope/OAuthScopeAllowListTest.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/onboarding/domain/OnboardingStep.java (IdentifiedEnum-like fail-loud fromId convention CONVENTIONS.md §4 — the static lookup style this enum mirrors, even though the lookup key is the URL not an id)
    - backend/core/src/main/java/com/zeromail/core/shared/lang/IdentifiedEnum.java (interface this enum does NOT implement — IdentifiedEnum is for id() == name() pairs; this ledger's key is a scope URL distinct from name())
    - backend/api/src/main/java/com/zeromail/api/security/OAuthScopes.java (existing constant class to deprecate; copy its scope set + add the three Calendar entries)
    - backend/core/src/test/java/com/zeromail/core/admin/arch/AdminTenantOAuthGuardTest.java (ArchUnit composite-rule precedent — the test directory + naming convention this test joins; the ArchUnit `noClasses().that()` predicate shape is NOT directly reused per D-02 caveat — read RESEARCH.md §Pitfall 1 to understand why)
    - .planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md (§Pitfall 1 lines 685-700 — the source-text-scan rationale and recommended regex shape)
    - backend/api/src/main/resources/application.yml (the `spring.security.oauth2.client.registration.google.scope:` block whose URL literals must be allow-listed by the scanner because YAML is the wire format Spring expects)
  </read_first>
  <action>
    Create `backend/core/src/main/java/com/zeromail/core/oauth/scope/package-info.java` declaring the package only (no Modulith `@ApplicationModule` annotation — the existing `core.shared` family is the pattern; treat `core.oauth.scope` as a leaf under an `oauth` package; if the existing Modulith conventions require a leaf-module decl, mirror `core.shared.lang/package-info.java` and use `displayName="OAuth Scope Ledger"` with `allowedDependencies={}`).

    Create `backend/core/src/main/java/com/zeromail/core/oauth/scope/GoogleOAuthScope.java` as a Java 25 enum. Each constant carries one `https://www.googleapis.com/auth/...` URL in a `private final String value` field; expose `public String value()`. Per D-01, define exactly: `OPENID("openid")`, `PROFILE("profile")`, `EMAIL("email")`, `GMAIL_MODIFY("https://www.googleapis.com/auth/gmail.modify")`, `CALENDAR_FREEBUSY("https://www.googleapis.com/auth/calendar.freebusy")`, `CALENDAR_EVENTS("https://www.googleapis.com/auth/calendar.events")`, `CALENDAR_READONLY("https://www.googleapis.com/auth/calendar.readonly")`. Do NOT add `drive`, `drive.readonly`, `drive.metadata.readonly`, full `calendar`, `gmail.readonly`, `gmail.send`, or any other Google scope — Phase 15 will add only `DRIVE_FILE`. Per CLAUDE.md "Backend Code Style", use explicit names, no opaque abbreviations. JavaDoc each constant naming: (a) tier per developers.google.com/identity/protocols/oauth2/scopes (NON_SENSITIVE / SENSITIVE / RESTRICTED), (b) "Introduced: v1.X Phase NN", (c) one-sentence purpose. Provide a static `public static GoogleOAuthScope fromValue(String url)` that throws `java.util.NoSuchElementException("Unknown Google OAuth scope: " + url)` on unknown — fail-loud per CONVENTIONS.md §4.

    Create `backend/core/src/test/java/com/zeromail/core/oauth/scope/GoogleOAuthScopeEnumTest.java` — plain JUnit 5 (TESTING.md §3 — Layer 1, no Spring context). Assert: (a) every `value()` URL is unique across the enum (no two constants share a URL); (b) `fromValue(GoogleOAuthScope.X.value())` round-trips for every constant; (c) `fromValue("https://www.googleapis.com/auth/drive")` throws `NoSuchElementException` (ledger must not silently accept drive); (d) iterating `values()` contains the three calendar constants AND `GMAIL_MODIFY` but does NOT contain any `String` whose value starts with `https://www.googleapis.com/auth/drive`.

    Create `backend/core/src/test/java/com/zeromail/core/oauth/scope/OAuthScopeAllowListTest.java` — plain JUnit 5 source-text scanner per RESEARCH.md §Pitfall 1 path 1. The test walks `backend/api/src/main/java`, `backend/core/src/main/java`, `backend/worker/src/main/java` from the repo root (resolve the repo root by walking up from `System.getProperty("user.dir")` until a `settings.gradle.kts` is found, so the test runs from either subproject working directory). Use `java.nio.file.Files.walk(...)` filtered to `.java` files. For each file, scan each line for the regex pattern matching any literal Google scope URL of the form `auth slash X` where X is `[a-zA-Z0-9._-]+` (build the regex string in code by concatenating `https://` + `www\\.googleapis\\.com` + `/auth/` + `[a-zA-Z0-9._-]+`, so this source file itself does not contain the literal trigger string).
    Whitelist: the file path must NOT start with the absolute path of `backend/core/src/main/java/com/zeromail/core/oauth/scope/` (the enum body is the canonical home for the literals). Comments and JavaDoc are NOT exempt — the discipline rule is "no scope literal anywhere in production source except the enum"; if a constant URL appears in a JavaDoc comment, refactor to reference `GoogleOAuthScope.X` by symbol. The test FAILS via `org.assertj.core.api.Assertions.fail("<diagnostic>")` listing every offending `path:line URL` triple. Allowed-empty: the test must pass when the enum is the only source of URLs (initial green state). Provide a single negative test that creates a temporary file under a temp path representing a "production" Java file with the literal `auth slash calendar` substring and asserts the diagnostic message contains its path — this validates the scanner DOES catch violations (do NOT add a real production source containing the literal for this).
    Per anti-pattern in this PLAN's `<planner_antipatterns>` rule (CLAUDE.md `Comment-Text Discipline`), this `<action>` body must NOT itself contain the verbatim URL the test searches for; the description above paraphrases it as "auth slash X".

    Privacy: source-text scanner reads only `.java` files; never read `.env*`, `application*.yml`, `*.log`, or test resources.
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:core:test --tests "com.zeromail.core.oauth.scope.GoogleOAuthScopeEnumTest" --tests "com.zeromail.core.oauth.scope.OAuthScopeAllowListTest"</automated>
  </verify>
  <acceptance_criteria>
    - `backend/core/src/main/java/com/zeromail/core/oauth/scope/GoogleOAuthScope.java` exists; `grep -c '"https://www.googleapis.com/auth/calendar' backend/core/src/main/java/com/zeromail/core/oauth/scope/GoogleOAuthScope.java` returns at least 3 (FREEBUSY, EVENTS, READONLY).
    - `grep -c 'drive' backend/core/src/main/java/com/zeromail/core/oauth/scope/GoogleOAuthScope.java | grep -v '^#'` returns 0 (no drive entries).
    - `GoogleOAuthScopeEnumTest` passes.
    - `OAuthScopeAllowListTest` passes against the current repo (production source currently has the literal in `application.yml` only, which the scanner excludes by file-type filter).
    - JetBrains `get_file_problems` on `GoogleOAuthScope.java` returns no errors after the file is created.
  </acceptance_criteria>
  <done>The ledger enum exists with exactly the seven approved scopes, both tests green, and the scanner is the documented INFRA-01 enforcement seam (test JavaDoc cites RESEARCH.md §Pitfall 1 for the source-text-scan rationale).</done>
</task>

<task type="auto">
  <name>Task 2: OAuthTokenStore facade over RefreshTokenCipher + round-trip test</name>
  <files>backend/core/src/main/java/com/zeromail/core/oauth/token/OAuthTokenStore.java, backend/core/src/main/java/com/zeromail/core/oauth/token/package-info.java, backend/core/src/test/java/com/zeromail/core/oauth/token/OAuthTokenStoreRoundTripTest.java</files>
  <read_first>
    - backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java (full file — the AES-GCM envelope + AAD-binds-tenantId pattern this facade delegates to)
    - backend/core/src/test/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipherTest.java (existing round-trip test pattern — mirror its key-bootstrap setup so the facade test does not duplicate boilerplate)
    - .planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md (D-14 Claude's Discretion — "keep the AES-GCM crypto class identical; parameterize the storage row identifier so the same cipher serves both gmail_connection.refresh_token_encrypted and calendar_connection.refresh_token_encrypted; no new key, no new envelope")
    - .planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md (§C "Refresh token store generalization" + §Pattern §Adapt per Claude's Discretion at lines 148-160)
    - CLAUDE.md Conventions §6 (direct calls vs Modulith events — this is a direct-call utility, not an event publisher)
  </read_first>
  <action>
    Create `backend/core/src/main/java/com/zeromail/core/oauth/token/package-info.java` declaring the leaf package (Modulith convention: `displayName="OAuth Token Store"`, `allowedDependencies={"gmail.persistence.crypto"}` if the existing modulith config requires it — check `backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/package-info.java` to confirm naming).

    Create `backend/core/src/main/java/com/zeromail/core/oauth/token/OAuthTokenStore.java` as a `@org.springframework.stereotype.Component`. Constructor-inject `RefreshTokenCipher refreshTokenCipher`. Define `public enum RowDiscriminator { GMAIL_CONNECTION, CALENDAR_CONNECTION }`. Expose: `public byte[] encrypt(byte[] plaintext, java.util.UUID tenantId, RowDiscriminator discriminator)` returning `refreshTokenCipher.encrypt(plaintext, tenantId.toString())`. Expose: `public byte[] decrypt(byte[] envelope, java.util.UUID tenantId, RowDiscriminator discriminator)` returning `refreshTokenCipher.decrypt(envelope, tenantId.toString())`. The `discriminator` parameter is currently informational — it exists at the facade boundary so future per-discriminator behavior (separate keys, audit fields) is a non-breaking change. Do NOT modify the cipher AAD. Per CLAUDE.md "Backend Code Style", use explicit names — `refreshTokenCipher`, `tenantId`, `discriminator`, `plaintext`, `envelope`; NO `cipher`, `id`, `d`, `pt`, `env`. Methods must include `@org.jspecify.annotations.NonNull` on every parameter and return value (CONVENTIONS.md jspecify usage); throw `java.util.Objects.requireNonNull(...)` on each arg with explicit field names.

    Create `backend/core/src/test/java/com/zeromail/core/oauth/token/OAuthTokenStoreRoundTripTest.java` extending the existing `RefreshTokenCipherTest` setup pattern. Build a `RefreshTokenCipher` directly (not @SpringBootTest — Layer 1 unit test) using the same key bootstrap the existing cipher test uses. Construct `OAuthTokenStore` with the real cipher. Cases: (a) encrypt-then-decrypt for `tenantId = randomUUID()` + `RowDiscriminator.GMAIL_CONNECTION` round-trips the plaintext; (b) same with `CALENDAR_CONNECTION`; (c) decrypting a `GMAIL_CONNECTION` ciphertext with a DIFFERENT `tenantId` throws `javax.crypto.AEADBadTagException` (AAD binding intact); (d) decrypting a `CALENDAR_CONNECTION` ciphertext produced for tenant X with tenant Y throws `AEADBadTagException`; (e) the same plaintext encrypted twice for the same tenant produces DIFFERENT ciphertext (nonce uniqueness — sample 100 round-trips and assert no two envelopes are byte-equal). Per TESTING.md §1 must-test list — "Crypto round-trips" is a mandatory invariant.
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:core:test --tests "com.zeromail.core.oauth.token.OAuthTokenStoreRoundTripTest"</automated>
  </verify>
  <acceptance_criteria>
    - `OAuthTokenStore.java` exists; `grep -c 'RowDiscriminator' backend/core/src/main/java/com/zeromail/core/oauth/token/OAuthTokenStore.java` returns at least 1.
    - `grep -c 'AAD' backend/core/src/main/java/com/zeromail/core/oauth/token/OAuthTokenStore.java | grep -v '^#'` returns 0 — facade must NOT re-implement crypto, only delegate.
    - `OAuthTokenStoreRoundTripTest` passes; nonce-uniqueness assertion runs without flakiness on 100 iterations.
    - `RefreshTokenCipher.java` is byte-identical pre- and post-task (`git diff backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java` returns empty).
    - JetBrains `get_file_problems` on `OAuthTokenStore.java` returns no errors.
  </acceptance_criteria>
  <done>OAuthTokenStore is the new public-facing API for OAuth refresh-token persistence in Phase 12+; it delegates 1:1 to the unchanged Gmail cipher, leaving all v1.3 Gmail call sites untouched.</done>
</task>

<task type="auto">
  <name>Task 3: Liquibase changesets 131-134 + master include + libs.versions.toml + CalendarSchemaIsolationTest</name>
  <files>gradle/libs.versions.toml, backend/core/build.gradle.kts, backend/worker/build.gradle.kts, backend/core/src/main/resources/db/changelog/changes/131-calendar-connections.yaml, backend/core/src/main/resources/db/changelog/changes/132-calendars.yaml, backend/core/src/main/resources/db/changelog/changes/133-mailbox-calendar-preferences.yaml, backend/core/src/main/resources/db/changelog/changes/134-inbox-projection-calendar-columns.yaml, backend/core/src/main/resources/db/changelog/db.changelog-master.yaml, backend/core/src/test/java/com/zeromail/core/calendar/persistence/CalendarSchemaIsolationTest.java</files>
  <read_first>
    - backend/core/src/main/resources/db/changelog/changes/119-gmail-connections-multi-mailbox.yaml (workspace-shared-table precedent — preConditions HALT pattern, splitStatements:false raw SQL, explicit rollback)
    - backend/core/src/main/resources/db/changelog/changes/127-gmail-conn-global-active-email-unique.yaml (partial-unique-index precedent — the partial index shape the mailbox_calendar_preferences event_write/brief_source indexes mirror)
    - backend/core/src/main/resources/db/changelog/changes/130-gmail-connection-profile.yaml (the latest existing changeset — the new changesets append AFTER this)
    - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml (the master changelog include order — verify the tail is line 384 `130-gmail-connection-profile.yaml` and that NO 131+ files are already included)
    - gradle/libs.versions.toml (current shape — append the new versions + libraries at the existing block tail; do NOT reformat unrelated lines)
    - backend/core/build.gradle.kts and backend/worker/build.gradle.kts (where existing google-api-services-gmail / google-auth-library-oauth2-http dependencies are declared — the new calendar API + ical4j dependencies follow the same `implementation(libs.X)` pattern)
    - .planning/phases/12-calendar-connection-triage-foundation/12-RESEARCH.md (§Pattern 2 lines 393-490 — the exact Liquibase shape for each of the 4 changesets, including the partial unique index note at lines 490-495)
    - .planning/phases/12-calendar-connection-triage-foundation/12-CONTEXT.md (D-11 column types — `message_class` is `varchar(16)` storing the enum id, `event_dt` is `timestamptz`; D-13 schema-vs-service split for is_enabled)
    - CLAUDE.md Conventions §10 (Liquibase changelog discipline — append-only, explicit rollback, preConditions for destructive/data-dependent changes only)
  </read_first>
  <action>
    BEFORE writing dependency versions: probe Maven Central for the latest dated rev on the `2.0.0` line of `google-api-services-calendar` and confirm `ical4j` 4.2.4 is still GA. Run `curl -sL "https://repo1.maven.org/maven2/com/google/apis/google-api-services-calendar/maven-metadata.xml"` and `curl -sL "https://repo1.maven.org/maven2/org/mnode/ical4j/ical4j/maven-metadata.xml"` and pin the most recent `v3-rev2026*-2.0.0` revision for Calendar (Assumption A1/A2 in RESEARCH.md). If the probe fails, fall back to `v3-rev20260225-2.0.0` per RESEARCH.md line 110 and `4.2.4` for ical4j per RESEARCH.md line 112; note the actual chosen revision in the file header comment of `libs.versions.toml`.

    Edit `gradle/libs.versions.toml`: under `[versions]` add `ical4j = "4.2.4"` and `calendarApi = "<chosen rev>"` after the existing `gmailApi` line. Under `[libraries]` add `ical4j = { module = "org.mnode.ical4j:ical4j", version.ref = "ical4j" }` and `google-api-services-calendar = { module = "com.google.apis:google-api-services-calendar", version.ref = "calendarApi" }` after the existing `google-api-services-gmail` line. Do NOT change indentation of unrelated lines.

    Edit `backend/core/build.gradle.kts`: add `implementation(libs.google.api.services.calendar)` adjacent to the existing `implementation(libs.google.api.services.gmail)` line.

    Edit `backend/worker/build.gradle.kts`: add `implementation(libs.ical4j)` AND (if the worker also calls Calendar API directly for sub-calendar enumeration in W2 — per Pattern Map line 60 the gateway lives in `backend/core`, so worker may not need it) keep ical4j worker-only. The Calendar API dependency lives in `backend/core` because `CalendarApiClientFactory` (W1) is a `backend/core` component. Verify worker test compilation by adding the dep to `testImplementation` if needed.

    Create `backend/core/src/main/resources/db/changelog/changes/131-calendar-connections.yaml` per RESEARCH.md Pattern 2 lines 400-430. Single changeSet id `131-calendar-connections` author `zeromail`. Columns mirror `gmail_connections` shape minus primary/watch/ingestion state: `id uuid PK`, `tenant_id uuid NOT NULL`, `google_email varchar(320) NOT NULL`, `status varchar(32) NOT NULL`, `refresh_token_encrypted bytea` (nullable until first connect commits), `scopes_granted text`, `connected_at timestamptz`, `disconnected_at timestamptz`, `google_profile_name varchar(255)`, `google_profile_picture_url text`, `version int NOT NULL DEFAULT 0`, `created_at timestamptz NOT NULL DEFAULT now()`, `updated_at timestamptz NOT NULL DEFAULT now()`. Use a `sql:` change with `splitStatements: false` for the partial unique index `uq_calendar_conn_active_email ON calendar_connections (tenant_id, lower(google_email)) WHERE status = 'CONNECTED'` and the supporting `idx_calendar_conn_status` per RESEARCH.md line 423-428. Provide explicit `rollback:` that `dropTable: { tableName: calendar_connections }` (drop index implicit on drop table). EXPLICIT INVARIANT (CAL-CONN-06): no `gmail_connection_id` column or FK; this is verified by `CalendarSchemaIsolationTest`.

    Create `132-calendars.yaml` per RESEARCH.md lines 432-457. Columns: `id uuid PK`, `calendar_connection_id uuid NOT NULL FK calendar_connections(id) ON DELETE CASCADE`, `tenant_id uuid NOT NULL`, `external_calendar_id text NOT NULL` (Google's calendarList item id, opaque string), `name varchar(512)`, `description text`, `is_primary boolean NOT NULL DEFAULT false`, `is_enabled boolean NOT NULL DEFAULT true`, `timezone varchar(64)`, `created_at`/`updated_at` defaults. Add unique constraint `uq_calendar_connection_external_id ON (calendar_connection_id, external_calendar_id)`. Provide explicit `rollback:` drop-table.

    Create `133-mailbox-calendar-preferences.yaml` per RESEARCH.md lines 459-495 + the explicit cardinality answer from `<open_questions_from_research>` Q3: ship single-select for `event_write` + `brief_source`, multi-select for `freebusy`. Columns: `id uuid PK`, `tenant_id uuid NOT NULL`, `mailbox_id uuid NOT NULL FK gmail_connections(id) ON DELETE CASCADE`, `calendar_connection_id uuid NOT NULL FK calendar_connections(id) ON DELETE CASCADE`, `calendar_id uuid NOT NULL FK calendars(id) ON DELETE CASCADE`, `role varchar(32) NOT NULL` (stores enum id `FREEBUSY` / `EVENT_WRITE` / `BRIEF_SOURCE`), `created_at`/`updated_at`. Add `uq_mailbox_role_calendar ON (mailbox_id, role, calendar_id)` (prevents duplicate role assignments for the same calendar) AND TWO PARTIAL UNIQUE INDEXES per the locked Q3 answer: `uq_mailbox_event_write ON (mailbox_id) WHERE role = 'EVENT_WRITE'` and `uq_mailbox_brief_source ON (mailbox_id) WHERE role = 'BRIEF_SOURCE'`. Add `idx_mcp_mailbox_role ON (mailbox_id, role)` for fast role lookup. Provide explicit `rollback:` drop-index then drop-table.

    Create `134-inbox-projection-calendar-columns.yaml` per RESEARCH.md Pattern 3 lines 503-530 + D-11. Use `addColumn` (NOT raw SQL) so Liquibase dialect-translates: add `message_class varchar(16)` (nullable; stores `INVITE` / `CANCEL` / `RESCHEDULE` / `RSVP`) and `event_dt timestamptz` (nullable). Then a `sql:` change creates the supporting partial index `idx_inbox_projection_calendar_pin ON gmail_inbox_projection (tenant_id, gmail_connection_id, event_dt DESC) WHERE message_class IS NOT NULL` for the W4 read-side ORDER BY. Provide explicit `rollback:` that drops the index then drops both columns. The `gmail_inbox_projection` table has many existing rows post-v1.3 — `NULL` correctly means "not a calendar message", so NO backfill is required (RESEARCH.md lines 677-681 confirms safety).

    Edit `db.changelog-master.yaml`: append four `include:` blocks at the file tail, in order 131, 132, 133, 134, each with `relativeToChangelogFile: true`. Do NOT reorder any existing entries.

    Create `backend/core/src/test/java/com/zeromail/core/calendar/persistence/CalendarSchemaIsolationTest.java` extending `PostgresContainerTest` (the Testcontainers base used by W1.3 phase 10/11 schema tests — find it via `glob` if needed; per memory `reference_dev_db_ssh_tunnel.md` the test container is separate from the dev SSH tunnel). After Liquibase migration runs, query `information_schema.columns` for `table_name = 'calendar_connections'` and assert the row count for `column_name = 'gmail_connection_id'` is 0 (CAL-CONN-06 invariant: workspace-shared, NO mailbox FK on the connection table). Also assert columns named `tenant_id`, `google_email`, `status`, `refresh_token_encrypted` DO exist. This test is the "schema invariant" gate per Convention 4 in TESTING.md (Layer fundamentals — schema is a contract). Tag with `org.junit.jupiter.api.Tag("schema")` (no execution gate; the tag is for filterability later).

    Privacy: per CLAUDE.md privacy logging — no test prints `google_email` values; the assertions are metadata-only.
  </action>
  <verify>
    <automated>cd backend && ./gradlew :backend:core:test --tests "com.zeromail.core.calendar.persistence.CalendarSchemaIsolationTest"</automated>
  </verify>
  <acceptance_criteria>
    - `gradle/libs.versions.toml` contains `ical4j` and `calendarApi` under `[versions]` and `ical4j` + `google-api-services-calendar` under `[libraries]`.
    - `grep -c 'libs.google.api.services.calendar' backend/core/build.gradle.kts` returns at least 1.
    - `grep -c 'libs.ical4j' backend/worker/build.gradle.kts` returns at least 1.
    - The four changeset files exist; `grep -c 'calendar_connections' backend/core/src/main/resources/db/changelog/changes/131-calendar-connections.yaml` returns at least 1; `grep -c 'gmail_connection_id' backend/core/src/main/resources/db/changelog/changes/131-calendar-connections.yaml | grep -v '^#'` returns 0.
    - `grep -c 'uq_mailbox_event_write' backend/core/src/main/resources/db/changelog/changes/133-mailbox-calendar-preferences.yaml` returns at least 1.
    - `grep -c 'message_class' backend/core/src/main/resources/db/changelog/changes/134-inbox-projection-calendar-columns.yaml` returns at least 1.
    - `db.changelog-master.yaml` includes lines for 131, 132, 133, 134 in that order, and the previous tail entry (130) is unchanged.
    - `CalendarSchemaIsolationTest` passes against a freshly-migrated Testcontainers Postgres.
    - `./gradlew :backend:api:bootRun --args='--spring.profiles.active=test --liquibase.migrate-only=true'` returns success — Liquibase applies the four new changesets without errors.
  </acceptance_criteria>
  <done>Schema + dependency catalog + worker ical4j wiring are committed and CI-green; W1..W5 can now reference `OAuthTokenStore`, the new tables, and the calendar API client class on the compile classpath.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| developer source code → CI | Production scope literals enter the codebase only via PR; OAuthScopeAllowListTest is the gate. |
| Postgres schema → application | Newly-added columns/tables are read/written by W2 (service) and W4 (worker classifier); the schema invariant must be enforced before any caller exists. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-12-SC | Tampering | `gradle/libs.versions.toml` package adds (ical4j, google-api-services-calendar) | mitigate | Per `<contribution from="schema-gate">` this project uses Liquibase YAML, not npm/pip/cargo — the package legitimacy table in RESEARCH.md §Package Legitimacy Audit (lines 156-164) is the audit. Both packages are well-known Maven Central canonical artifacts (ical4j 15+ yrs, official Google API client). No `[ASSUMED]`/`[SUS]` packages; no human-verify checkpoint required by the GSD slopcheck gate. |
| T-12-07 | Elevation of Privilege | Production source files anywhere in `backend/{api,core,worker}/src/main/java` | mitigate | `OAuthScopeAllowListTest` source-text scan — fails CI when any file outside `..core.oauth.scope..` contains a `googleapis.com/auth/*` literal. Per RESEARCH.md §Pitfall 1, ArchUnit byte-code scan CANNOT see the literals; source-text scan honors D-02 intent. |
| T-12-V6-1 | Cryptography (ASVS V6) | `OAuthTokenStore.encrypt/decrypt` | mitigate | Facade delegates 1:1 to existing AES-GCM `RefreshTokenCipher` (project-tested by Gmail since v1.0). No new key, no new envelope, no new AAD format. `OAuthTokenStoreRoundTripTest` verifies tamper-rejection (cross-tenant decrypt throws `AEADBadTagException`) + nonce uniqueness over 100 iterations. |
| T-12-V4-1 | Access Control (ASVS V4) — schema isolation | `calendar_connections` table | mitigate | `CalendarSchemaIsolationTest` asserts NO `gmail_connection_id` column exists. CAL-CONN-06 workspace-shared invariant is gated at the schema layer before any service code is written; W2's `CalendarConnectionService` cannot accidentally join on a non-existent column. |
| T-12-01 (deferred) | Information Disclosure — cross-tenant leak | `CalendarConnectionService` reads | accept-in-W0 | This plan does NOT introduce a query path. `tenantId` AAD on the cipher is in place. Full mitigation (`resolveOwnedConnectionOrThrow` + `MailboxBindingFilter`) lands in W2. |

Mitigations reference specific code: `OAuthScopeAllowListTest` and `OAuthTokenStoreRoundTripTest` and `CalendarSchemaIsolationTest` are the executable gates.
</threat_model>

<verification>
- `cd backend && ./gradlew :backend:core:test --tests "com.zeromail.core.oauth.scope.*" --tests "com.zeromail.core.oauth.token.*" --tests "com.zeromail.core.calendar.persistence.CalendarSchemaIsolationTest"` — all targeted tests green.
- `cd backend && ./gradlew :backend:core:compileJava :backend:worker:compileJava` — both compile after the new deps are wired.
- `cd backend && ./gradlew :backend:api:check` — full backend `check` task green (catches any ArchUnit/Modulith regression introduced by the new `core.oauth.scope` + `core.oauth.token` packages).
- `git diff backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java` returns empty — the cipher class is UNCHANGED (D-14 invariant).
</verification>

<success_criteria>
- All 9 new files exist at their declared paths.
- All four changesets land in `db.changelog-master.yaml` after 130-gmail-connection-profile.yaml.
- `libs.versions.toml` carries `ical4j` 4.2.4 and `calendarApi` pin.
- `OAuthScopeAllowListTest` is green against the current repo and would fail if a test fixture introduces a `googleapis.com/auth/calendar` literal under `backend/core/src/main/java/...` outside the enum package.
- `OAuthTokenStoreRoundTripTest` green for both `RowDiscriminator` values; cross-tenant decrypt throws.
- `CalendarSchemaIsolationTest` green; Liquibase migration applies cleanly against a fresh Testcontainers Postgres.
- JetBrains `get_file_problems` returns no errors on any of: `GoogleOAuthScope.java`, `OAuthTokenStore.java`, `CalendarSchemaIsolationTest.java`.
- Full `./gradlew check` green (no Modulith / ArchUnit regression).
</success_criteria>

<output>
Create `.planning/phases/12-calendar-connection-triage-foundation/12-01-SUMMARY.md` when done. Summary must list: (a) the chosen `calendarApi` revision, (b) the four committed changeset numbers, (c) the `OAuthTokenStore` API surface, (d) confirmation that `RefreshTokenCipher.java` is byte-identical, and (e) the diagnostic output sample from `OAuthScopeAllowListTest` running against the current repo (proving the scanner actually executes file walks).
</output>
