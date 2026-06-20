---
phase: 12-calendar-connection-triage-foundation
plan: W0
subsystem: oauth-scope-ledger-and-token-store
status: complete
tags: [infra-01, cal-conn, oauth-scope-ledger, oauth-token-store, calendar-schema, liquibase, ical4j, google-calendar-api]
requirements_completed: [INFRA-01, CAL-CONN-03, CAL-CONN-06, CAL-CONN-07, CAL-TRIAGE-01]
requires:
  - RefreshTokenCipher (gmail.persistence.crypto) — unchanged delegate
  - PostgresContainerTest (core.support) — Testcontainers base for schema test
  - gmail_inbox_projection table — extended with two nullable columns
provides:
  - com.zeromail.core.oauth.scope.GoogleOAuthScope (enum ledger)
  - com.zeromail.core.oauth.scope.OAuthScopeAllowListTest (source-text scanner)
  - com.zeromail.core.oauth.token.OAuthTokenStore (cipher facade)
  - calendar_connections table (workspace-shared)
  - calendars table (per-connection sub-calendar rows)
  - mailbox_calendar_preferences table (per-role per-mailbox tagging)
  - gmail_inbox_projection.message_class + event_dt columns
  - libs.versions.toml entries: ical4j 4.2.5, calendarApi v3-rev20260614-2.0.0
affects:
  - backend/api: OAuthScopes.java now delegates to GoogleOAuthScope; E2eStubResetController inlines via the enum (Rule 3 auto-fix to make the new scanner green on the current repo)
tech_stack_added:
  - org.mnode.ical4j:ical4j:4.2.5 (worker compile)
  - com.google.apis:google-api-services-calendar:v3-rev20260614-2.0.0 (core compile)
patterns_followed:
  - IdentifiedEnum / fail-loud fromId (CONVENTIONS.md §4) — adapted as fromValue on the new enum
  - Workspace-shared schema pattern from 119-gmail-connections-multi-mailbox.yaml
  - Liquibase append-only + explicit rollback (CONVENTIONS.md §10)
  - PostgresContainerTest Testcontainer base for schema-invariant tests
  - Spring Modulith package-info @NamedInterface convention
key_files_created:
  - backend/core/src/main/java/com/zeromail/core/oauth/package-info.java
  - backend/core/src/main/java/com/zeromail/core/oauth/scope/GoogleOAuthScope.java
  - backend/core/src/main/java/com/zeromail/core/oauth/scope/package-info.java
  - backend/core/src/main/java/com/zeromail/core/oauth/token/OAuthTokenStore.java
  - backend/core/src/main/java/com/zeromail/core/oauth/token/package-info.java
  - backend/core/src/test/java/com/zeromail/core/oauth/scope/GoogleOAuthScopeEnumTest.java
  - backend/core/src/test/java/com/zeromail/core/oauth/scope/OAuthScopeAllowListTest.java
  - backend/core/src/test/java/com/zeromail/core/oauth/token/OAuthTokenStoreRoundTripTest.java
  - backend/core/src/test/java/com/zeromail/core/calendar/persistence/CalendarSchemaIsolationTest.java
  - backend/core/src/main/resources/db/changelog/changes/131-calendar-connections.yaml
  - backend/core/src/main/resources/db/changelog/changes/132-calendars.yaml
  - backend/core/src/main/resources/db/changelog/changes/133-mailbox-calendar-preferences.yaml
  - backend/core/src/main/resources/db/changelog/changes/134-inbox-projection-calendar-columns.yaml
key_files_modified:
  - gradle/libs.versions.toml (+ ical4j, calendarApi)
  - backend/core/build.gradle.kts (+ Calendar API client)
  - backend/worker/build.gradle.kts (+ ical4j)
  - backend/core/src/main/resources/db/changelog/db.changelog-master.yaml (append 131-134 includes)
  - backend/api/src/main/java/com/zeromail/api/security/OAuthScopes.java (delegate to enum)
  - backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubResetController.java (read via enum)
decisions:
  - "D-01 / D-03 implemented: GoogleOAuthScope is the single Java source of truth for every approved Google OAuth scope; production callers read via GoogleOAuthScope.X.value() instead of literal URLs."
  - "D-02 caveat respected: source-text scan (not ArchUnit byte-code rule) per RESEARCH §Pitfall 1, because ArchUnit's JavaClassProcessor drops constant string argument values during ASM import."
  - "D-14 (Claude's Discretion) honored: RefreshTokenCipher is byte-identical pre/post W0; OAuthTokenStore is a Spring @Component facade carrying a RowDiscriminator that is currently informational so future per-discriminator behavior is a non-breaking change."
  - "Q3 lock encoded at schema layer: FREEBUSY is multi-select per mailbox (no partial unique index); EVENT_WRITE and BRIEF_SOURCE are single-select per mailbox (uq_mailbox_event_write, uq_mailbox_brief_source partial unique indexes)."
  - "calendarApi pin = v3-rev20260614-2.0.0 (probed Maven Central 2026-06-20 — latest dated rev on the 2.0.0 line, supersedes the RESEARCH.md fallback v3-rev20260225-2.0.0)."
  - "ical4j pin = 4.2.5 (probed Maven Central 2026-06-20 — current GA on the 4.x line, supersedes the RESEARCH.md A1 assumption of 4.2.4)."
metrics:
  duration: "~20 minutes"
  tasks_completed: 3
  files_created: 13
  files_modified: 6
  tests_added: 14  # 5 enum + 2 scanner + 5 round-trip + 6 schema-isolation - 4 (CalendarSchemaIsolationTest is 6, but acceptance criteria lists "at least 9 tests" — actual added = 18 across 4 files: 5 + 2 + 5 + 6 = 18)
  changesets_added: 4
  completed_date: 2026-06-20
---

# Phase 12 Plan W0: OAuth Scope Ledger + Calendar Token Store + Schema Catalog — Summary

**One-liner:** Ship the W0 foundation — `GoogleOAuthScope` enum + source-text allow-list CI gate, `OAuthTokenStore` facade reusing the existing AES-GCM cipher, and the four workspace-shared Calendar Liquibase changesets — so W1..W5 can run in parallel against committed schema, dependency catalog, and crypto facade.

## Tasks Executed

| Task | Name                                                              | Commit     | Files                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| ---- | ----------------------------------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | GoogleOAuthScope + OAuthScopeAllowListTest + GoogleOAuthScopeEnumTest | `0d6da011` | `GoogleOAuthScope.java`, `OAuthScopeAllowListTest.java`, `GoogleOAuthScopeEnumTest.java`, two `package-info.java` under `core.oauth.*`; Rule 3 auto-fix to `OAuthScopes.java` + `E2eStubResetController.java`                                                                                                                                                                                                                                                                                                                                |
| 2    | OAuthTokenStore facade + round-trip test                           | `c23a1acb` | `OAuthTokenStore.java`, `OAuthTokenStoreRoundTripTest.java`, `package-info.java` (`core.oauth.token`)                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| 3    | Liquibase 131-134 + libs.versions.toml + Gradle wiring + CalendarSchemaIsolationTest | `5c0c05d2` | `libs.versions.toml`, `backend/core/build.gradle.kts`, `backend/worker/build.gradle.kts`, `131-calendar-connections.yaml`, `132-calendars.yaml`, `133-mailbox-calendar-preferences.yaml`, `134-inbox-projection-calendar-columns.yaml`, `db.changelog-master.yaml`, `CalendarSchemaIsolationTest.java` |

## Output Contract (from PLAN §output)

### (a) Chosen `calendarApi` revision
**`v3-rev20260614-2.0.0`** — probed Maven Central at 2026-06-20 via
`curl https://repo1.maven.org/maven2/com/google/apis/google-api-services-calendar/maven-metadata.xml`.
The probe returned `<latest>v3-rev20260614-2.0.0</latest>` / `<release>v3-rev20260614-2.0.0</release>`,
which supersedes the RESEARCH.md fallback `v3-rev20260225-2.0.0`. The chosen revision sits on the same `2.0.0`
line as the existing `gmailApi = "v1-rev20250331-2.0.0"` pin, so the underlying `google-api-client` BOM
converges per A2 in RESEARCH.md.

ical4j was probed in the same pass — Maven Central returned `<latest>4.2.5</latest>`, so the catalog
pins `ical4j = "4.2.5"` (point-release above the RESEARCH A1 assumption of 4.2.4; no API surface change).

### (b) Committed changeset numbers
- `131-calendar-connections.yaml` — workspace-shared connection table.
- `132-calendars.yaml` — per-connection sub-calendar rows.
- `133-mailbox-calendar-preferences.yaml` — per-(mailbox, role, calendar) join with per-role partial unique indexes.
- `134-inbox-projection-calendar-columns.yaml` — nullable `message_class` + `event_dt` on `gmail_inbox_projection`.

All four appended in order to `db.changelog-master.yaml` after `130-gmail-connection-profile.yaml`; the
previous tail entry (130) is unchanged.

### (c) OAuthTokenStore API surface
Public Spring `@Component`:
```java
public class OAuthTokenStore {
    public enum RowDiscriminator { GMAIL_CONNECTION, CALENDAR_CONNECTION }
    public OAuthTokenStore(RefreshTokenCipher refreshTokenCipher);
    public byte[] encrypt(byte[] plaintext, UUID tenantId, RowDiscriminator discriminator);
    public byte[] decrypt(byte[] envelope, UUID tenantId, RowDiscriminator discriminator);
}
```
Both methods delegate 1:1 to `RefreshTokenCipher.{encrypt,decrypt}(value, tenantId.toString())`. The
`discriminator` parameter is informational at the facade boundary today; it exists so a future change
to per-discriminator key material is a non-breaking call-site change.

### (d) RefreshTokenCipher byte-identical confirmation
`git diff backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java`
returns empty after all three task commits — the cipher class is unchanged pre/post W0 (D-14 invariant
preserved).

### (e) OAuthScopeAllowListTest diagnostic sample
Initial run against the unmodified repo (before the Rule 3 auto-fix to `OAuthScopes.java` and
`E2eStubResetController.java`) produced the following diagnostic via the AssertJ `fail(...)` call (paths
relative to repo root, format `path:line url`):

```
INFRA-01 source-text scanner found 3 stray Google OAuth scope URL literal(s) outside
backend/core/src/main/java/com/zeromail/core/oauth/scope. Move them through
GoogleOAuthScope.X.value() instead.
  - backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubResetController.java:39 <gmail-modify scope URL>
  - backend/api/src/main/java/com/zeromail/api/security/OAuthScopes.java:11 <gmail-modify scope URL>
  - backend/api/src/main/java/com/zeromail/api/security/OAuthScopes.java:14 <gmail-prefix>
```

The exact URL substrings are omitted from this SUMMARY per the same comment-text discipline the test
itself enforces. After the Rule 3 fix (OAuthScopes delegates to `GoogleOAuthScope.GMAIL_MODIFY.value()`;
E2eStubResetController reads the constant via the enum), the scanner passes against the current repo
with zero offending lines, confirming the file walk actually executes and the regex matches the intended
pattern.

## Verification

All targeted gradle commands green:

```
./gradlew :backend:core:test \
    --tests "com.zeromail.core.oauth.scope.GoogleOAuthScopeEnumTest" \
    --tests "com.zeromail.core.oauth.scope.OAuthScopeAllowListTest" \
    --tests "com.zeromail.core.oauth.token.OAuthTokenStoreRoundTripTest" \
    --tests "com.zeromail.core.calendar.persistence.CalendarSchemaIsolationTest"
```

Results:

| Test class                          | Tests | Failed | Notes                                                              |
| ----------------------------------- | ----- | ------ | ------------------------------------------------------------------ |
| `GoogleOAuthScopeEnumTest`          | 5     | 0      | round-trip, no-duplicates, fail-loud unknown, no-drive invariants  |
| `OAuthScopeAllowListTest`           | 2     | 0      | repo-wide source-text scan + seeded-violation positive control     |
| `OAuthTokenStoreRoundTripTest`      | 5     | 0      | both discriminators, cross-tenant AAD reject, 100× nonce uniqueness |
| `CalendarSchemaIsolationTest`       | 6     | 0      | CAL-CONN-06 isolation + W4 column presence + partial-index defs    |

Total: **18 new tests added, all green.** (Plan output said "9 new tests" — counted at the test-class
granularity; actual @Test method count is 18.)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Existing production source had Gmail scope URL literals outside the new ledger package**
- **Found during:** Task 1 verify run (`OAuthScopeAllowListTest` failed initially).
- **Issue:** The plan acceptance criterion said the scanner should pass against the unmodified repo
  ("`application.yml` only, which the scanner excludes by file-type filter"), but the actual repo
  also carried the literal in `backend/api/src/main/java/com/zeromail/api/security/OAuthScopes.java`
  (lines 11, 14 — `GMAIL_MODIFY` and `GMAIL_PREFIX` string constants) and in
  `backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubResetController.java:39`
  (`GRANTED_GMAIL_SCOPES`). The plan's Task 1 `<read_first>` explicitly named `OAuthScopes.java` as
  "the existing constant class to deprecate", so removing its literal URL is in-scope for INFRA-01.
- **Fix:**
  - Rewrote `OAuthScopes.java` to delegate `GMAIL_MODIFY` and `GMAIL_PREFIX` to
    `GoogleOAuthScope.GMAIL_MODIFY.value()` (and a derived `substring(...)` for the prefix), removing
    both literal URLs from the file while preserving the public API legacy callers depend on
    (`GoogleOAuthSuccessHandler`, `OAuthIntentRoutingTest`, `GoogleOAuthSuccessHandlerTest`).
  - Replaced the inline literal in `E2eStubResetController.java:39` with a fully-qualified
    `com.zeromail.core.oauth.scope.GoogleOAuthScope.GMAIL_MODIFY.value()` lookup.
- **Files modified:** `backend/api/src/main/java/com/zeromail/api/security/OAuthScopes.java`,
  `backend/api/src/main/java/com/zeromail/api/e2estub/E2eStubResetController.java`
- **Commit:** Folded into Task 1's commit `0d6da011`.

### Authentication Gates

None. W0 is a pure code/schema delivery — no OAuth flow exercised, no external auth needed.

### Scope Boundaries Respected

- `RefreshTokenCipher.java` is byte-identical pre/post W0 (D-14 invariant).
- No call site outside `OAuthScopes.java` and `E2eStubResetController.java` was edited. The Gmail OAuth
  flow keeps calling `RefreshTokenCipher` directly; `OAuthTokenStore` is offered only to W1's new
  Calendar callers, per plan §`<artifacts_this_phase_produces>`.

## Threat Surface

All Phase 12 W0 threats in `<threat_model>` are mitigated as planned:

| Threat ID | Mitigation Status |
| --------- | ----------------- |
| T-12-SC   | Both packages (`ical4j`, `google-api-services-calendar`) are canonical Maven Central artifacts (per RESEARCH §Package Legitimacy Audit); pinning matches existing Gmail dep pattern. |
| T-12-07   | `OAuthScopeAllowListTest` source-text scanner is green; would fail on any future production file outside `core.oauth.scope` carrying a `googleapis.com/auth/...` literal. |
| T-12-V6-1 | `OAuthTokenStoreRoundTripTest` verifies tamper rejection (cross-tenant `AEADBadTagException`) and 100× nonce uniqueness. |
| T-12-V4-1 | `CalendarSchemaIsolationTest.calendar_connections_has_no_gmail_connection_id_column` enforces CAL-CONN-06 at the schema layer. |
| T-12-01 (deferred) | Accept-in-W0 as planned; no query path introduced this wave. |

No new threat-flag surface (no new network endpoint, no new auth path, no new file access pattern, no
new trust-boundary schema change beyond the W0 plan).

## Known Stubs

None. W0 ships ledger + facade + schema only; no UI, no controllers, no data-flow stubs to track.

## Self-Check: PASSED

Files exist on disk (sample):
- `backend/core/src/main/java/com/zeromail/core/oauth/scope/GoogleOAuthScope.java` — FOUND
- `backend/core/src/main/java/com/zeromail/core/oauth/token/OAuthTokenStore.java` — FOUND
- `backend/core/src/main/resources/db/changelog/changes/131-calendar-connections.yaml` — FOUND
- `backend/core/src/main/resources/db/changelog/changes/134-inbox-projection-calendar-columns.yaml` — FOUND
- `backend/core/src/test/java/com/zeromail/core/calendar/persistence/CalendarSchemaIsolationTest.java` — FOUND

Commits exist in `git log --oneline`:
- `0d6da011` — FOUND (Task 1: scope ledger)
- `c23a1acb` — FOUND (Task 2: token store facade)
- `5c0c05d2` — FOUND (Task 3: schema + catalog)

All four targeted test classes report tests=N failures=0 in their respective `TEST-*.xml` outputs
under `backend/core/build/test-results/test/`.
