# Phase 12: Calendar Connection + Triage Foundation - Research

**Researched:** 2026-06-20
**Domain:** Google Calendar OAuth (incremental), workspace-shared connection foundation, calendar-aware Gmail triage
**Confidence:** HIGH on existing codebase analogs + Spring AI/Boot pins; HIGH on Google scope tiers; MEDIUM-HIGH on ArchUnit ledger enforcement (a critical caveat surfaced — see Q-A1); HIGH on ical4j v4.x; HIGH on Inbox Zero parity targets.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**OAuth scope ledger (INFRA-01):**
- **D-01:** Code-first Java enum `GoogleOAuthScope` is the canonical ledger. Enum constants (`CALENDAR_FREEBUSY`, `CALENDAR_EVENTS`, `CALENDAR_READONLY`, `GMAIL_*`, etc.) carry the scope URL as the value. **No** `drive`, `drive.readonly`, `drive.metadata.readonly` entries — Phase 15 will add only `DRIVE_FILE`. JavaDoc on each entry documents purpose + phase-introduced + sensitivity tier.
- **D-02:** ArchUnit literal-string scanner enforces the ledger in CI. Rule shape: `noClasses().that().resideOutsideOfPackage("..core.oauth.scope..").should().containAnyConstantMatching("^https://www\\.googleapis\\.com/auth/.*$")`. Implemented as a custom `ArchCondition<JavaClass>` iterating `getMethodCallsFromSelf()` and inspecting argument constants. Test name: `OAuthScopeAllowListTest`. Lives in `backend/core/src/test/java/com/zeromail/core/oauth/scope/`.
- **D-03:** Production scope requests at every `ClientRegistration` builder site read the URL from `GoogleOAuthScope.X.value()` — never from a string literal. The enum package itself is whitelisted from the literal-scanner rule.
- **D-04:** Human-readable `docs/oauth-scopes.md` is **deferred** to Phase 15.

**Multi-Google-Calendar OAuth + connection model (CAL-CONN-01..08):**
- **D-05:** Multi-account from day one — N Google Calendar accounts per workspace, free, no pricing-tier gating.
- **D-06:** Default-on-connect role assignment — connection's primary calendar auto-assigned all three roles (`freebusy`, `event_write`, `brief_source`) for the active mailbox only at connect time; other sub-calendars get `is_enabled=true` (visible) but no preference rows.
- **D-07:** Edit UX = per-mailbox settings page at `/settings/mailboxes/[mailboxId]/calendar`. Layout mirrors Inbox Zero `CalendarConnections.tsx` shell + Calendly role-assignment overlay. Raw shadcn primitives only.
- **D-08:** Role-tag is runtime authority. `is_enabled=true` makes a calendar eligible for a preference row but never grants any role implicitly.

**Calendar-aware Gmail triage (CAL-TRIAGE-01..04):**
- **D-09:** **CAL-TRIAGE-03 revised** — dropped backend-downgrade `CalendarAwareGuard` in favor of Inbox Zero pattern: seed every new tenant with default rule typed `SystemType=CALENDAR`, action `label "Calendar"`; detect calendar invites via `isCalendarInvite()` (`.ics` attachment OR `mimeType=text/calendar` OR `BEGIN:VCALENDAR` body marker); rule engine pushes `PRESET + CALENDAR` match before any AI matching; user-authored rules retain full authority. No downgrade, no audit reason, no badge UI.
- **D-10:** `text/calendar` MIME classification uses **ical4j** for METHOD + DTSTART extraction. Library: `org.mnode.ical4j:ical4j`. Parser runs in `backend/worker` on `MailMessageObserved` AFTER_COMMIT.
- **D-11:** Persistence shape — add `message_class enum('INVITE','CANCEL','RESCHEDULE','RSVP')` (nullable) + `event_dt timestamptz` (nullable) to existing inbox-projection row. No new long-term body storage. ARCH-02 preserved.
- **D-12:** Pin mechanism — derived `pin_until = event_dt + 24h` computed at read time; ORDER BY treats `(message_class IS NOT NULL AND now() < pin_until) DESC, server_timestamp DESC`. No new `pinned_until` column. UI badge via shadcn `Badge` `outline` variant.
- **D-13:** Role picker dropdowns list only `is_enabled=true` calendars. Toggling a calendar off automatically removes referencing preference rows.

**Disconnect cascade:**
- **D-14:** Synchronous cascade-revoke. Tx 1: mark `calendar_connection.status=DISCONNECTED`, delete `mailbox_calendar_preference` rows, null-out `booking_link.destination_calendar_id` if applicable, retain `triage_audit` rows. AFTER_COMMIT publish `CalendarConnectionDisconnected` Modulith event.

### Claude's Discretion
- Liquibase changelog file naming + ordering (use repo convention from existing v1.3 changesets under `backend/core/src/main/resources/db/changelog/changes/`).
- Exact Spring `ClientRegistration` bean naming + `OAuth2AuthorizationRequestResolver` customizer placement.
- DTO record shapes for `apps/web` — emit via existing springdoc-openapi → openapi-typescript codegen pipeline; no hand-written mirror types.
- Generalization of `RefreshTokenCipher` → `OAuthTokenStore`: keep AES-GCM crypto class identical, parameterize the storage row identifier so the same cipher serves both `gmail_connection.refresh_token_encrypted` and `calendar_connection.refresh_token_encrypted`. No new key, no new envelope.
- Default seeded `SystemType=CALENDAR` rule's exact label text (`"Calendar"` recommended for EN; bilingual VN/EN to match `materializeDefaultRulesEnabled` convention).

### Deferred Ideas (OUT OF SCOPE)
- Per-message badge for guard interventions (guard was dropped).
- Rule-create LLM warning ("this rule may catch calendar invites") — guard was dropped.
- `docs/oauth-scopes.md` generated human-readable ledger + CI freshness check — Phase 15 trigger.
- Per-seat / per-account billing for multi-account Calendar — v1.5+ monetization.
- Reverse / matrix views for `mailbox_calendar_preference` — Phase 12 ships per-mailbox view only.
- `text/calendar` Schema.org JSON-LD + header-heuristic fallback detection — rejected indefinitely.
- Override flag `rule.guardOverride` — N/A.
- Phase 14 sessionless `@Order(40)` Spring Security chain for public booking page — Phase 14 work.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INFRA-01 | OAuth scope ledger CI-enforced; fails if any production code requests an unapproved Google OAuth scope. | §A `GoogleOAuthScope` enum + ArchUnit allow-list test — **see Q-A1 caveat on byte-code constant inspection limit and the recommended source-text fallback**. |
| CAL-CONN-01 | User can connect one or more Google Calendar accounts via explicit "Connect Google Calendar" action; consent never implicit during signup. | §B incremental OAuth via second `ClientRegistration` `google-calendar` + session-flagged intent. |
| CAL-CONN-02 | Calendar OAuth = separate `ClientRegistration`; requests only `calendar.freebusy` + `calendar.events` + `calendar.readonly`; shares existing Google client; `include_granted_scopes=true` + `access_type=offline` + `prompt=consent`. | §B `GoogleAuthorizationRequestResolver` extended to recognize calendar registrationId; reuses existing additionalParameters customization. |
| CAL-CONN-03 | Encrypted OAuth tokens via existing AES-GCM `OAuthTokenStore`; refresh tokens never logged / plaintext / reused across connections. | §C `RefreshTokenCipher` already AAD-bound to `tenantId`; introduce `OAuthTokenStore` thin facade that keeps the same envelope but accepts a row-discriminator (`gmail`/`calendar`) for ergonomic call sites. |
| CAL-CONN-04 | View all connected Calendar accounts with email/status/last sync/per-calendar toggles; disconnect cascade-revokes derived state, audit retained. | §B `CalendarConnectionService.disconnect(...)` mirrors `GmailConnectionService.disconnect(MailboxRef)`. |
| CAL-CONN-05 | Each `CalendarConnection` enumerates calendars Google exposes (primary + secondary), each with `is_enabled` flag; only enabled calendars participate in free/busy + briefs. | §B `Calendar.calendarList().list()` snapshot ingested after OAuth; `calendar` table with composite PK `(connection_id, external_calendar_id)`. |
| CAL-CONN-06 | Calendar connections are workspace-shared (one logical row per Google account / workspace), NOT per-mailbox; no `gmail_connection_id` FK on `calendar_connection`. | §B Schema follows v1.3 workspace-shared/mailbox-isolated invariant (mirrors `gmail_connections` but **no** isPrimary or watch state). |
| CAL-CONN-07 | `mailbox_calendar_preference (mailbox_id, calendar_connection_id, role ∈ {freebusy, event_write, brief_source})` disambiguates per-role per-mailbox usage. | §B Composite-key join table with PG enum or `varchar(32)` check; `ON DELETE CASCADE` to both parents. |
| CAL-CONN-08 | Three-state state machine (`CONNECTED` / `DISCONNECTED` / `REVOKED`); mid-flight reads against `DISCONNECTED` fail fast and emit Modulith event for free/busy cache eviction. | §B Enum mirrors `GmailConnectionStatus`; `MailboxDisconnectedException`-style guard surface in CalendarApiClientFactory; `CalendarConnectionDisconnected` event. |
| CAL-TRIAGE-01 | Gmail ingestion parses `text/calendar` MIME parts; classifies messages as `INVITE`/`CANCEL`/`RESCHEDULE`/`RSVP`; classification persists on existing projection row; no new long-term body storage. | §D ical4j parse in worker on `MailMessageObserved`; persist `message_class` + `event_dt` columns on `gmail_inbox_projection`. |
| CAL-TRIAGE-02 | Calendar-class messages pinned at top-of-inbox for 24h after event date with explicit "Cancellation" / "Time changed" badges. | §D derived `pin_until` in ORDER BY clause of `GmailInboxProjectionRepository.findInboxPage`; `Badge outline` next to message row. |
| CAL-TRIAGE-03 | New tenants seeded with default `SystemType=CALENDAR` rule (label "Calendar") auto-matched via `isCalendarInvite` (`.ics` OR `text/calendar` OR `BEGIN:VCALENDAR`). Calendar rule runs PRESET before AI; user-authored rules retain full action authority — no backend downgrade. (Revised 2026-06-20.) | §E IZ pattern mirrored. **A `system-calendar` template already exists in `113-default-rule-templates-seed.yaml` + `RuleTemplateMaterializationService.DEFAULT_RULE_TEMPLATE_KEYS_EN`** — the seeding plumbing is already wired. Phase 12 must (a) re-shape that template's matcher from `SEMANTIC_INTENT` to a PRESET marker, OR (b) add a new PRESET branch in the rule evaluator. |
| CAL-TRIAGE-04 | Calendar-aware triage ships without requiring any Calendar OAuth scope — `text/calendar` parsing is purely message-side. | §D Classifier consumes `MailMessageObserved` + Gmail message body (already fetched); no Calendar API call needed. |

</phase_requirements>

## Summary

Phase 12 stitches three independently shippable capabilities to the v1.3 multi-Gmail foundation:

1. **OAuth scope ledger** — a code-first `GoogleOAuthScope` enum becomes the single source of truth for every scope URL the product is allowed to request. The companion ArchUnit `OAuthScopeAllowListTest` prevents a literal `https://www.googleapis.com/auth/...` string from creeping into production code outside the enum. **Important: ArchUnit's byte-code model does not surface constant string arguments to method calls** (it stores field type/name/descriptor but drops the constant value during ASM import; see §A and the Context7 source on `JavaClassProcessor.visitField` `[VERIFIED: Context7 /tng/archunit JavaClassProcessor]`). The literal-scanner therefore needs a source-text scan or a static-final-field scan instead of `getMethodCallsFromSelf()` argument inspection — D-02's wording is technically inaccurate; an implementation path that honors D-02's intent is documented in §A.

2. **Multi-Google-Calendar incremental OAuth** — a second `ClientRegistration` bean (`google-calendar`) shares the existing Google client-id, requests only `calendar.freebusy` + `calendar.events` + `calendar.readonly`, uses the same `/login/oauth2/code/{registrationId}` callback path Spring's OAuth2 Login filter handles per-registration. Workspace-shared `calendar_connection` mirrors the v1.3 `gmail_connections` shape — but **without** primary/watch/ingestion-health columns, since Phase 12 does not poll Calendar. Per-mailbox role tagging lives in `mailbox_calendar_preference (mailbox_id, calendar_connection_id, role)`.

3. **Calendar-aware Gmail triage** — `text/calendar` MIME parts get parsed in `backend/worker` after `MailMessageObserved` AFTER_COMMIT via **ical4j 4.2.4**. Only the `METHOD` property + first `VEVENT` `DTSTART` survive into Postgres, on two new nullable columns `message_class` + `event_dt` on the existing `gmail_inbox_projection`. A derived `pin_until = event_dt + 24h` is computed at read time, sorted top of inbox. A `SystemType=CALENDAR` PRESET rule (already seeded in `113-default-rule-templates-seed.yaml`) labels invites without AI involvement.

**Primary recommendation:** Treat Phase 12 as 5–6 wave-able plans: (W0) ArchUnit + `GoogleOAuthScope` enum + ledger wiring (zero runtime risk) → (W1) `OAuthTokenStore` extraction from `RefreshTokenCipher` (refactor, no functional change) + Calendar `ClientRegistration` + `CalendarApiClientFactory` → (W2) `calendar_connection` + `calendar` + `mailbox_calendar_preference` schema + `CalendarConnectionService` + REST controllers + `CalendarConnectionDisconnected` event → (W3) `/settings/mailboxes/[mailboxId]/calendar` page + role-assignment UI → (W4) ical4j classifier in worker + projection columns/index/ORDER BY change → (W5) PRESET rule wiring + seeded-template tweak. **All five waves can ship behind a single user-visible release because none of them changes existing Gmail triage behavior** — the calendar-class classifier is additive, and the seeded `system-calendar` template already exists.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| OAuth scope ledger enum (compile-time constant) | API / Backend (`core.oauth.scope`) | Test suite (ArchUnit) | Domain-shared invariant consumed by `backend/api` security config; tested at compile time. |
| ArchUnit `OAuthScopeAllowListTest` | Test suite (`backend/core/src/test/java/...arch`) | — | Compile-time enforcement; runs in CI; never reaches runtime. |
| Calendar OAuth `ClientRegistration` + AuthZ resolver | API / Backend (Spring Security in `backend/api`) | — | Returns to same-origin callback; uses session for intent state; tightly coupled to existing user-session chain @Order(4). |
| `CalendarConnectionService` + `calendar_connection` JPA | API / Backend (`backend/core/calendar/`) | Database / Storage | Owns state transitions; mirrors GmailConnectionService shape. |
| `CalendarApiClientFactory` (build `Calendar` from `CalendarConnection`) | API / Backend (`backend/core/calendar/gateway/`) | — | Connection → Google client builder; mirrors `GmailApiClientFactory`. |
| `OAuthTokenStore` (refresh-token AES-GCM facade) | API / Backend (`core.oauth.token` or `core.shared.crypto`) | Database / Storage | Stays inside backend; same envelope already used by Gmail. |
| ical4j `CalendarMessageClassifier` | API / Backend (`backend/worker` consumer) | — | Worker-side AFTER_COMMIT side effect; does NOT block Pub/Sub. |
| `message_class` + `event_dt` projection columns | Database / Storage (PG) | API / Backend (read service) | Read-side projection columns; derived `pin_until` in SQL. |
| `gmail_inbox_projection` pin-aware ORDER BY | API / Backend (`InboxProjectionReadService` / repo native query) | Database / Storage | Existing native query gets ORDER BY change + supporting index. |
| `SystemType=CALENDAR` PRESET rule branch | API / Backend (`core.rules.domain.RuleEvaluator`) | — | Match-before-AI branch in existing rule engine. |
| `/settings/mailboxes/[mailboxId]/calendar` page | Frontend Server (Next.js App Router) | Browser / Client (TanStack Query + shadcn) | New route under existing `(protected)/(app)/settings/` group; SSR shell + client hydration. |
| Calendar connection REST controller | API / Backend (`backend/api/controllers/calendar/`) | — | Springdoc-driven OpenAPI generates schema; `apps/web` regenerates client. |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.springframework.boot:spring-boot-starter-oauth2-client` | Boot 4.1.0-managed | Second `ClientRegistration` for Calendar; reuses existing user chain | Already on classpath for Gmail flow; canonical Spring OAuth2 Client `[VERIFIED: codebase resolved BOM]` |
| `com.google.apis:google-api-services-calendar` | `v3-rev20260225-2.0.0` (or latest dated rev on `-2.0.0` line) | Google Calendar Java client (calendarList.list, freebusy in Phase 13) | `2.0.0` line matches existing `google-api-services-gmail` `v1-rev20250331-2.0.0` pin → `google-api-client` BOM convergence `[CITED: mvnrepository.com/artifact/com.google.apis/google-api-services-calendar]` |
| `com.google.auth:google-auth-library-oauth2-http` | `1.48.0` (existing pin) | OAuth credential adapter; already on classpath | Reused — no new dependency `[VERIFIED: libs.versions.toml]` |
| `org.mnode.ical4j:ical4j` | `4.2.4` | RFC 5545 / RFC 5546 parser for `text/calendar` METHOD + DTSTART | Modern 4.x line uses `java.time` API; folded-line safe; charset-safe; ~700KB `[CITED: central.sonatype.com/artifact/org.mnode.ical4j/ical4j]` |
| `com.tngtech.archunit:archunit-junit5` | `1.4.2` (existing pin) | Custom `ArchCondition` for scope ledger | Already used by `AdminTenantOAuthGuardTest`, etc. `[VERIFIED: libs.versions.toml]` |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-modulith-events-api` | 2.1.0 (existing) | `CalendarConnectionDisconnected` AFTER_COMMIT event | D-14 cascade signaling within `backend/core` |
| `spring-data-jpa` / `hibernate` | Boot 4.1-managed | `CalendarConnectionEntity` etc. JPA persistence | Mirrors existing Gmail entities |
| `springdoc-openapi-starter-webmvc-ui` | `3.0.3` (existing) | OpenAPI schema generation | `apps/web` codegen contract |
| `org.jspecify:jspecify` | (existing) | `@NonNull` annotations on controllers/services | Match existing usage in `GoogleOAuthSuccessHandler` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| ical4j 4.x | `biweekly` 0.6.x | biweekly is staler (~2023), smaller API surface; ical4j 4.2.4 is RFC 5545 reference-grade and aligned with `java.time` |
| ical4j 4.x | hand-rolled regex on `BEGIN:VCALENDAR`/`DTSTART` | breaks on RFC 5545 §3.1 folded lines (Outlook/Apple emit them); breaks on quoted printable charsets — rejected per D-10 |
| Second Google OAuth client-id for Calendar | Shared client-id with split `ClientRegistration` | Two consent screens → reviewer rejection; locked OUT OF SCOPE in REQUIREMENTS.md |
| Single `ClientRegistration` adding Calendar scopes to login bundle | Separate `google-calendar` registration | Bundled flow would silently force re-consent on every existing user at login; CAL-CONN-01 explicitly requires explicit "Connect Google Calendar" action — separate registration is the only compliant path |
| New `pinned_until` column (D-12 alternative) | Derived `pin_until = event_dt + 24h` | New column needs backfill on existing v1.3 projection table; derived is monotonic with `event_dt` and zero migration risk — locked by D-12 |

**Installation (Gradle Kotlin DSL):**

```kotlin
// libs.versions.toml additions
ical4j = "4.2.4"
calendarApi = "v3-rev20260225-2.0.0"  // verify latest dated rev when planning

// [libraries]
ical4j = { module = "org.mnode.ical4j:ical4j", version.ref = "ical4j" }
google-api-services-calendar = { module = "com.google.apis:google-api-services-calendar", version.ref = "calendarApi" }
```

**Version verification:**

```bash
# Run during planning, before writing the dependency block:
curl -sL "https://repo1.maven.org/maven2/org/mnode/ical4j/ical4j/maven-metadata.xml" | grep latest
curl -sL "https://repo1.maven.org/maven2/com/google/apis/google-api-services-calendar/maven-metadata.xml" | grep latest
```

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `org.mnode.ical4j:ical4j` 4.2.4 | Maven Central | >15 yrs project, 4.x line ~2 yrs | High (broad enterprise use, referenced by many ICS tooling repos) | github.com/ical4j/ical4j | OK | Approved `[CITED: central.sonatype.com/artifact/org.mnode.ical4j/ical4j]` `[CITED: mvnrepository.com/artifact/org.mnode.ical4j/ical4j]` |
| `com.google.apis:google-api-services-calendar` v3-rev*-2.0.0 | Maven Central | ~10+ yrs | High (official Google Java client) | github.com/googleapis/google-api-java-client-services | OK | Approved (mirrors existing Gmail dep) `[CITED: developers.google.com/api-client-library/java/apis/calendar/v3]` |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

*Note: `gsd-tools` CLI was not reachable in this sandbox (`command not found`), so the legitimacy gate was satisfied via direct Maven Central listing instead of the seam. Both packages are well-known canonical artifacts.*

## Architecture Patterns

### System Architecture Diagram

```
                            ┌──────────────────────────────────────────────┐
                            │            backend/api (Spring MVC)           │
                            │                                              │
   Browser ──/oauth2/        │  GoogleAuthorizationRequestResolver         │
   authorization/             │      ├─ google             (Gmail+login)   │
   google-calendar  ─────────▶│      └─ google-calendar    (cal scopes)    │
                            │              │                              │
                            │              ▼ /login/oauth2/code/{regId}    │
                            │  CalendarOAuthSuccessHandler (new)           │
                            │              │                              │
                            │  CalendarConnectionService.upsert(...)  ───▶ │
                            │              │                              │
                            │              ▼                              │
                            │  CalendarApiClient.calendarList.list() ─────┐│
                            │              │                              ││
                            │              ▼                              ││
                            │  CalendarSnapshotIngestionService            ││
                            │  ─ INSERT calendar_connection                ││
                            │  ─ INSERT calendar[] (primary+secondary)     ││
                            │  ─ INSERT mailbox_calendar_preference        ││
                            │     (3 roles, primary calendar only)         ││
                            └──────────────────────────────────────────────┘│
                                              │                            │
                                              ▼  (REST)                    │
   /settings/mailboxes/[mailboxId]/calendar ◀── apps/web (TanStack Query)  │
                                                                            │
                                                                            │
                            ┌──────────────────────────────────────────────┐│
   Gmail Pub/Sub push ────▶│  backend/api PubSubController               ││
                            │     ↓ writes MailMessageObserved (existing)  ││
                            │     ↓ Modulith publishes after commit         ││
                            └───────────────┬──────────────────────────────┘│
                                            │                              │
                                            ▼                              │
                            ┌──────────────────────────────────────────────┐│
                            │           backend/worker                     ││
                            │   CalendarMessageClassifier (NEW)            ││
                            │     ├─ fetch full message body (existing)    ││
                            │     ├─ detect text/calendar part             ││
                            │     ├─ ical4j parse → METHOD + DTSTART        ││
                            │     ├─ classify INVITE/CANCEL/RESCHEDULE/RSVP ││
                            │     └─ UPDATE gmail_inbox_projection           ││
                            │        SET message_class=?, event_dt=?       ││
                            │                                              ││
                            │   RuleEvaluator (existing, modified)         ││
                            │     ├─ if rule.systemType==CALENDAR &&       ││
                            │     │   message.messageClass != NULL         ││
                            │     │   → push PRESET match (label rule)     ││
                            │     └─ else evaluate normally                 ││
                            └──────────────────────────────────────────────┘│
                                                                            │
   /inbox ◀──── InboxProjectionReadService                                   │
                ORDER BY (message_class IS NOT NULL AND now() < event_dt+24h)│
                  DESC,                                                      │
                  received_at DESC, gmail_message_id DESC   ◀────────────────┘
```

### Recommended Project Structure

**Backend (`backend/core` + `backend/api`):**

```
backend/core/src/main/java/com/zeromail/core/
├── calendar/                              # NEW domain
│   ├── domain/
│   │   ├── CalendarConnectionStatus.java  # CONNECTED / DISCONNECTED / REVOKED enum (IdentifiedEnum)
│   │   ├── MailboxCalendarRole.java       # FREEBUSY / EVENT_WRITE / BRIEF_SOURCE enum (IdentifiedEnum)
│   │   └── package-info.java
│   ├── event/
│   │   ├── CalendarConnectionDisconnected.java   # Modulith event (D-14)
│   │   └── package-info.java
│   ├── exception/
│   │   ├── CalendarConnectionNotOwnedException.java
│   │   ├── CalendarDisconnectedException.java
│   │   └── package-info.java
│   ├── gateway/
│   │   ├── CalendarApiClientFactory.java  # mirror of GmailApiClientFactory
│   │   └── package-info.java
│   ├── persistence/
│   │   ├── CalendarConnectionEntity.java
│   │   ├── CalendarConnectionRepository.java
│   │   ├── CalendarEntity.java            # per-calendar sub-row (composite PK)
│   │   ├── CalendarRepository.java
│   │   ├── MailboxCalendarPreferenceEntity.java
│   │   ├── MailboxCalendarPreferenceRepository.java
│   │   └── package-info.java
│   ├── projection/
│   │   ├── CalendarConnectionView.java    # read-side record
│   │   ├── MailboxCalendarPreferenceView.java
│   │   └── package-info.java
│   ├── usecases/
│   │   ├── CalendarConnectionService.java        # @Service; @Transactional commands
│   │   ├── CalendarSnapshotIngestionService.java # post-OAuth calendarList ingest
│   │   ├── MailboxCalendarPreferenceService.java # role-assignment edits
│   │   └── package-info.java
│   └── package-info.java
├── oauth/                                 # NEW shared domain
│   └── scope/
│       ├── GoogleOAuthScope.java          # the ledger enum (D-01)
│       └── package-info.java
├── inbox/persistence/
│   └── (modify GmailInboxProjectionEntity + Repository for D-11/D-12)
├── shared/crypto/                         # existing
│   ├── PlatformSecretCipher.java          # existing
│   ├── (NEW) OAuthTokenStore.java         # facade over RefreshTokenCipher
│   └── ...
└── rules/
    └── (modify RuleEvaluator for D-09 PRESET branch)
```

```
backend/worker/src/main/java/com/zeromail/worker/
└── triage/
    ├── CalendarMessageClassifier.java     # NEW @TransactionalEventListener(AFTER_COMMIT)
    └── ...

backend/api/src/main/java/com/zeromail/api/
├── controllers/calendar/
│   ├── CalendarConnectionController.java        # GET list / DELETE disconnect / PATCH toggle
│   ├── MailboxCalendarPreferenceController.java # PATCH role assignments per mailbox
│   └── package-info.java
├── dto/calendar/
│   ├── CalendarConnectionResponse.java          # record DTO
│   ├── CalendarSubResponse.java                 # record DTO
│   ├── MailboxCalendarPreferenceResponse.java
│   ├── UpdateMailboxCalendarPreferenceRequest.java
│   └── package-info.java
├── security/
│   ├── (NEW) CalendarOAuthSuccessHandler.java   # mirrors GoogleOAuthSuccessHandler for calendar registrationId
│   ├── GoogleAuthorizationRequestResolver.java  # MODIFY: handle google-calendar id with cal-only params
│   └── OAuthScopes.java                         # DEPRECATE in favor of GoogleOAuthScope enum
```

**Frontend (`apps/web`):**

```
apps/web/
├── app/(protected)/(app)/settings/mailboxes/[mailboxId]/calendar/
│   ├── page.tsx                            # Server Component shell
│   └── CalendarSettingsClient.tsx          # Client orchestrator
└── features/calendar/
    ├── api/calendar-api.ts                 # api.GET/POST/DELETE wrappers (typed via schema.d.ts)
    ├── query-keys.ts                       # mirrors mailbox/query-keys.ts shape
    ├── hooks/
    │   ├── useCalendarConnections.ts       # GET /api/calendar/mailboxes/{id}/connections
    │   ├── useToggleCalendar.ts            # PATCH per-calendar enable
    │   ├── useDisconnectCalendarConnection.ts
    │   └── useUpdateMailboxCalendarPreference.ts
    └── components/
        ├── ConnectCalendarButton.tsx       # POST /api/calendar/connect-intent → redirect
        ├── CalendarConnectionsEmptyState.tsx
        ├── CalendarConnectionCard.tsx      # mirrors IZ card (Card + Collapsible + DropdownMenu)
        ├── CalendarSubList.tsx             # collapsible list of sub-calendars with Switch toggles
        └── MailboxCalendarRoleAssignment.tsx  # Calendly-style 3-role multi/single selects
```

### Pattern 1: Second `ClientRegistration` Sharing Existing Google OAuth Client (D-05, CAL-CONN-02)

**What:** Add a second registration `google-calendar` under `spring.security.oauth2.client.registration` that reuses `${GOOGLE_OAUTH_CLIENT_ID}` + `${GOOGLE_OAUTH_CLIENT_SECRET}` but lists only the calendar scopes. Spring's OAuth2 Login filter (already in the user chain) registers `/login/oauth2/code/google-calendar` as a callback path automatically when the registration is declared. `[CITED: docs.spring.io/spring-security/reference/6.5/...]`

**When to use:** Always — single OAuth client + multiple registrations is the only path that satisfies CAL-CONN-02 (separate from login bundle), respects the OUT-OF-SCOPE ban on a second GCP OAuth client, and avoids changing the existing login flow.

**Example — `application.yml`:**

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_OAUTH_CLIENT_ID:}
            client-secret: ${GOOGLE_OAUTH_CLIENT_SECRET:}
            scope:
              - openid
              - profile
              - email
              - https://www.googleapis.com/auth/gmail.modify
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
          google-calendar:
            provider: google        # reuse provider block below
            client-id: ${GOOGLE_OAUTH_CLIENT_ID:}    # SAME client-id
            client-secret: ${GOOGLE_OAUTH_CLIENT_SECRET:}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - https://www.googleapis.com/auth/calendar.freebusy
              - https://www.googleapis.com/auth/calendar.events
              - https://www.googleapis.com/auth/calendar.readonly
        provider:
          google:
            authorization-uri: https://accounts.google.com/o/oauth2/v2/auth
            token-uri: https://oauth2.googleapis.com/token
            user-info-uri: https://www.googleapis.com/oauth2/v3/userinfo
```

**Critical: read scope URLs from `GoogleOAuthScope` enum at the bean-config level** (D-03). The YAML keeps the URL literal; the Java config + tests reference `GoogleOAuthScope.CALENDAR_EVENTS.value()`. ArchUnit allow-lists the YAML resource path.

**Example — extending `GoogleAuthorizationRequestResolver` (already in the codebase):**

```java
// MODIFY existing GoogleAuthorizationRequestResolver
private OAuth2AuthorizationRequest customizeAuthorizationRequest(
        OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest servletRequest) {
    if (authorizationRequest == null) return null;
    // ... existing pendingIntentSnapshot consumption ...
    var additionalParameters = new HashMap<>(authorizationRequest.getAdditionalParameters());
    additionalParameters.put("access_type", "offline");
    additionalParameters.put("include_granted_scopes", "true");

    // Calendar registration always requires prompt=consent so an existing user without a
    // calendar grant gets the consent screen (CAL-CONN-01 explicit-action requirement).
    String registrationId = authorizationRequest.getAttribute(
            OAuth2ParameterNames.REGISTRATION_ID);
    boolean calendarFlow = "google-calendar".equals(registrationId);
    if (calendarFlow || "true".equals(servletRequest.getParameter(RECONNECT_PARAMETER))) {
        additionalParameters.put("prompt", "consent");
    }
    // ... rest unchanged ...
}
```

### Pattern 2: Workspace-Shared Calendar Connection Schema (CAL-CONN-06, CAL-CONN-07)

**What:** Mirror `gmail_connections` shape minus primary/watch state, add per-calendar sub-table and per-(mailbox, connection, role) preference table.

**Example — Liquibase (next available changeset numbers are `131-`+):**

```yaml
# 131-calendar-connection.yaml
databaseChangeLog:
  - changeSet:
      id: 131-calendar-connection
      author: zeromail
      changes:
        - createTable:
            tableName: calendar_connections
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true, nullable: false } }
              - column: { name: tenant_id, type: uuid, constraints: { nullable: false } }
              - column: { name: google_email, type: varchar(320), constraints: { nullable: false } }
              - column: { name: status, type: varchar(32), constraints: { nullable: false } }
              - column: { name: refresh_token_encrypted, type: bytea }
              - column: { name: scopes_granted, type: text }
              - column: { name: connected_at, type: timestamptz }
              - column: { name: disconnected_at, type: timestamptz }
              - column: { name: google_profile_name, type: varchar(255) }
              - column: { name: google_profile_picture_url, type: text }
              - column: { name: version, type: int, defaultValueNumeric: 0, constraints: { nullable: false } }
              - column: { name: created_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
              - column: { name: updated_at, type: timestamptz, defaultValueComputed: now(), constraints: { nullable: false } }
        - sql:
            sql: |
              CREATE UNIQUE INDEX uq_calendar_conn_active_email
                ON calendar_connections (tenant_id, lower(google_email))
                WHERE status = 'CONNECTED';
              CREATE INDEX idx_calendar_conn_status ON calendar_connections (status);
      rollback:
        - dropTable: { tableName: calendar_connections }
```

```yaml
# 132-calendar-sub.yaml
databaseChangeLog:
  - changeSet:
      id: 132-calendar-sub
      author: zeromail
      changes:
        - createTable:
            tableName: calendars
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true, nullable: false } }
              - column: { name: calendar_connection_id, type: uuid, constraints: { nullable: false, foreignKeyName: fk_calendar_conn, references: "calendar_connections(id)", deleteCascade: true } }
              - column: { name: tenant_id, type: uuid, constraints: { nullable: false } }
              - column: { name: external_calendar_id, type: text, constraints: { nullable: false } }
              - column: { name: name, type: varchar(512) }
              - column: { name: description, type: text }
              - column: { name: is_primary, type: boolean, defaultValueBoolean: false, constraints: { nullable: false } }
              - column: { name: is_enabled, type: boolean, defaultValueBoolean: true, constraints: { nullable: false } }
              - column: { name: timezone, type: varchar(64) }
              - column: { name: created_at, type: timestamptz, defaultValueComputed: now() }
              - column: { name: updated_at, type: timestamptz, defaultValueComputed: now() }
        - addUniqueConstraint:
            tableName: calendars
            columnNames: calendar_connection_id, external_calendar_id
            constraintName: uq_calendar_connection_external_id
```

```yaml
# 133-mailbox-calendar-preference.yaml
databaseChangeLog:
  - changeSet:
      id: 133-mailbox-calendar-preference
      author: zeromail
      changes:
        - createTable:
            tableName: mailbox_calendar_preferences
            columns:
              - column: { name: id, type: uuid, constraints: { primaryKey: true, nullable: false } }
              - column: { name: tenant_id, type: uuid, constraints: { nullable: false } }
              - column: { name: mailbox_id, type: uuid, constraints: { nullable: false, foreignKeyName: fk_mcp_mailbox, references: "gmail_connections(id)", deleteCascade: true } }
              - column: { name: calendar_connection_id, type: uuid, constraints: { nullable: false, foreignKeyName: fk_mcp_calendar_conn, references: "calendar_connections(id)", deleteCascade: true } }
              - column: { name: calendar_id, type: uuid, constraints: { nullable: false, foreignKeyName: fk_mcp_calendar, references: "calendars(id)", deleteCascade: true } }
              - column: { name: role, type: varchar(32), constraints: { nullable: false } }
              - column: { name: created_at, type: timestamptz, defaultValueComputed: now() }
              - column: { name: updated_at, type: timestamptz, defaultValueComputed: now() }
        - addUniqueConstraint:
            tableName: mailbox_calendar_preferences
            columnNames: mailbox_id, role, calendar_id
            constraintName: uq_mailbox_role_calendar
        # NOTE: For freebusy role, a mailbox may have MANY freebusy calendars (multi-select).
        # For event_write and brief_source, the API/service layer enforces "at most one per
        # (mailbox, role)" — NOT a DB unique constraint (would block legitimate multi-select for freebusy).
        - sql:
            sql: |
              CREATE INDEX idx_mcp_mailbox_role
                ON mailbox_calendar_preferences (mailbox_id, role);
```

**Important: enforce per-mailbox-per-role "at most one" for event_write + brief_source at the service layer**, not the DB — because freebusy allows N rows per (mailbox, role). Alternative: per-role partial unique indexes:

```sql
CREATE UNIQUE INDEX uq_mailbox_event_write ON mailbox_calendar_preferences (mailbox_id) WHERE role = 'EVENT_WRITE';
CREATE UNIQUE INDEX uq_mailbox_brief_source ON mailbox_calendar_preferences (mailbox_id) WHERE role = 'BRIEF_SOURCE';
```

### Pattern 3: Projection Columns + Pin-Aware ORDER BY (D-11, D-12)

**What:** Add two nullable columns to `gmail_inbox_projection`; modify the native query in `GmailInboxProjectionRepository.findInboxPage`.

**Example — Liquibase changeset:**

```yaml
# 134-inbox-projection-calendar-columns.yaml
databaseChangeLog:
  - changeSet:
      id: 134-inbox-projection-calendar-columns
      author: zeromail
      changes:
        - addColumn:
            tableName: gmail_inbox_projection
            columns:
              - column:
                  name: message_class
                  type: varchar(16)
              - column:
                  name: event_dt
                  type: timestamptz
        - sql:
            sql: |
              CREATE INDEX idx_inbox_projection_calendar_pin
                ON gmail_inbox_projection (tenant_id, gmail_connection_id, event_dt DESC)
                WHERE message_class IS NOT NULL;
      rollback:
        - sql:
            sql: |
              DROP INDEX IF EXISTS idx_inbox_projection_calendar_pin;
              ALTER TABLE gmail_inbox_projection DROP COLUMN IF EXISTS event_dt;
              ALTER TABLE gmail_inbox_projection DROP COLUMN IF EXISTS message_class;
```

**Modify the native query (current is `ORDER BY received_at DESC, gmail_message_id DESC`):**

```sql
ORDER BY
    -- D-12: pin calendar-class messages for 24h after the event timestamp
    (message_class IS NOT NULL AND event_dt IS NOT NULL AND now() < event_dt + INTERVAL '24 hours') DESC,
    received_at DESC,
    gmail_message_id DESC
LIMIT :pageLimit
```

**Keyset cursor stays valid** because the pin predicate is non-volatile within a single page request (uses `now()` once per query), and the `(received_at, gmail_message_id)` tiebreaker remains the deterministic secondary order. Verify with a manual keyset test: page through a mailbox that has both pinned and non-pinned rows in alternation.

### Pattern 4: ical4j 4.x Parse — METHOD + DTSTART (D-10)

**What:** Parse `text/calendar` body into the minimum facts: `METHOD` (REQUEST/CANCEL/REPLY/COUNTER) and the first `VEVENT`'s `DTSTART`.

**Example — `CalendarMessageClassifier.java`:**

```java
package com.zeromail.worker.triage;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Method;
import net.fortuna.ical4j.model.property.DtStart;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.Temporal;
import java.util.Optional;

public final class CalendarPartParser {

    public record ParseResult(MessageClass messageClass, Optional<Instant> eventDt) {}

    public enum MessageClass { INVITE, CANCEL, RESCHEDULE, RSVP }

    public Optional<ParseResult> parse(String icsBody) {
        try {
            CalendarBuilder builder = new CalendarBuilder();
            Calendar calendar = builder.build(new StringReader(icsBody));
            String method = calendar.<Method>getProperty(Property.METHOD)
                    .map(Method::getValue).orElse(null);
            MessageClass classification = classify(method);
            if (classification == null) {
                return Optional.empty();
            }
            Optional<Instant> dtStart = calendar.<VEvent>getComponent("VEVENT")
                    .flatMap(event -> event.<DtStart<?>>getProperty(Property.DTSTART))
                    .map(dt -> toInstant(dt.getDate()));
            return Optional.of(new ParseResult(classification, dtStart));
        } catch (Exception parseFailure) {
            // never log icsBody (privacy: may contain attendee emails / subject)
            return Optional.empty();
        }
    }

    private static MessageClass classify(String method) {
        if (method == null) return null;
        return switch (method.toUpperCase()) {
            case "REQUEST" -> MessageClass.INVITE;  // RESCHEDULE distinction is per-UID; see Q-D2
            case "CANCEL" -> MessageClass.CANCEL;
            case "REPLY" -> MessageClass.RSVP;
            default -> null;
        };
    }

    private static Instant toInstant(Temporal temporal) {
        if (temporal instanceof Instant instant) return instant;
        if (temporal instanceof java.time.ZonedDateTime zdt) return zdt.toInstant();
        if (temporal instanceof LocalDateTime ldt) return ldt.toInstant(ZoneOffset.UTC);
        if (temporal instanceof java.time.LocalDate ld)
            return ld.atStartOfDay(ZoneOffset.UTC).toInstant();
        return null;
    }
}
```

API confirmed via `[CITED: ical4j README 4.x — TemporalAdapter / java.time / VEvent components]`. ical4j 4.x uses the new `java.time` model (parsing returns `Temporal`); 3.x's `Date`/`DateTime` shim is gone.

**INVITE vs RESCHEDULE distinction (open question Q-D2):** RFC 5546 `METHOD:REQUEST` covers both initial invites and reschedules; the only reliable signal is comparing the `UID` against previously-stored UIDs for this thread. Phase 12 ships **INVITE for all `METHOD:REQUEST`** and surfaces `RESCHEDULE` only when (a) the same `(tenant_id, gmail_thread_id, UID)` already has an earlier `event_dt` and (b) the new event_dt differs. That requires a small `(tenant_id, gmail_thread_id, ical_uid)` lookup before the UPDATE. Locked design is in §D-11 of CONTEXT.md — confirm with user during plan-checker whether RESCHEDULE is gated on this or simply not emitted in Phase 12.

### Pattern 5: PRESET Rule Match Before AI (D-09)

**What:** Add an early-return branch in the rule evaluator (existing `RuleEvaluator` in `core.rules.domain`). The matcher checks `rule.systemType == CALENDAR && message.messageClass != null` and short-circuits to a `PRESET` match.

**Insertion point investigation needed:** The existing `RuleEvaluator` doesn't currently model `SystemType` (no file found by glob). The `RuleTemplateMaterializationService.DEFAULT_RULE_TEMPLATE_KEYS_EN` already includes `system-calendar` as a template key, and the seed YAML emits `matcher_ast = {SEMANTIC_INTENT, intent="Calendar: ..."}`. So Phase 12 must:

1. Add an enum-like marker (`MatcherType.PRESET_CALENDAR` or `systemType` column on `RuleEntity`) to identify the calendar rule kind without relying on the LLM-driven `SEMANTIC_INTENT` matcher.
2. Modify the seeded template (`023-fix-pin-calendar-category.yaml` precedent shows fix-via-new-changeset for template tweaks) so `system-calendar` uses the new matcher type.
3. In the evaluator, short-circuit-match `PRESET_CALENDAR` rules whenever `messageClass != null` (cheap, in-memory).

Example shape (Java, sketch):

```java
// In RuleEvaluator (existing class)
public List<RuleEvaluationResult> evaluate(RuleEvaluationInput input,
                                           List<EnabledRuleSnapshot> rules,
                                           MessageContext message) {
    var results = new ArrayList<RuleEvaluationResult>();
    for (var rule : rules) {
        if (rule.matcherType() == MatcherType.PRESET_CALENDAR) {
            if (message.messageClass() != null) {
                results.add(RuleEvaluationResult.preset(rule.id(),
                        rule.actionIntents(), "CALENDAR"));
            }
            continue;  // Skip the AI matcher path entirely for PRESET rules.
        }
        // ... existing SEMANTIC_INTENT / EXAMPLES paths ...
    }
    return results;
}
```

### Anti-Patterns to Avoid

- **Reusing `RefreshTokenCipher` directly inside `CalendarConnectionService`** — couples Calendar package to `gmail.persistence.crypto.*`. Extract `OAuthTokenStore` first (§C), then both Gmail and Calendar depend on the new shared facade.
- **Adding `gmail_connection_id` FK to `calendar_connection`** — explicit violation of CAL-CONN-06. Even an "audit-only" link tempts join-based reads later.
- **Single-flight unique constraint `(mailbox_id, role)` for all roles** — would block legitimate multi-select on `freebusy`. Per-role partial unique indexes (or service-layer guards) only.
- **Calling `Calendar.calendarList().list()` synchronously inside the OAuth success handler before the response** — adds 100–600ms to the OAuth round-trip. Do it AFTER_COMMIT via a Modulith event handler that runs the snapshot ingest asynchronously and updates the connection card via WebSocket/polling.
- **Naive regex `BEGIN:VCALENDAR.*METHOD:(REQUEST|CANCEL)`** — fails on RFC 5545 §3.1 folded lines (`METHOD\r\n :REQUEST`) and quoted printable encodings. Rejected by D-10.
- **Hand-rolled scope-string allow-list in a `Set<String>`** — drift target. The enum is the allow-list.
- **`message_class` as PG enum type** — Liquibase YAML `CREATE TYPE ... AS ENUM` is annoying to evolve (`ALTER TYPE … ADD VALUE` is not transactional). Use `varchar(16)` + JPA `@Enumerated(EnumType.STRING)` + ArchUnit-style `IdentifiedEnum.fromId` per CONVENTIONS.md §4.
- **Persisting raw iCal text** — banned by ARCH-02. Parse in worker, extract enum + Instant, discard body bytes. Never log icsBody.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| iCal parsing | regex over `BEGIN:VCALENDAR` body | ical4j 4.2.4 | RFC 5545 §3.1 folded lines; quoted-printable encodings; charset edge cases |
| OAuth2 authorization-request customization | hand-spun servlet filter | Extend existing `GoogleAuthorizationRequestResolver` | Already wired into Spring's OAuth2 Login filter chain |
| AES-GCM token envelope | new key + new envelope shape | Existing `RefreshTokenCipher` via new `OAuthTokenStore` facade | Single envelope = single rotation surface |
| OpenAPI client types | hand-write mirror DTOs | `pnpm --filter web run generate:api` | CLAUDE.md §11 MANDATORY |
| Toast-on-mutation | local `toast.error()` in `onError` | TanStack Query `meta.successMessage`/`meta.errorMessage` | CLAUDE.md §12 convention |
| Sub-calendar discovery | scrape Gmail labels for calendar names | `Calendar.calendarList().list()` Google API | Authoritative + primary flag |
| ArchUnit literal scanning (D-02) | enumerate every method-call site by name | See §A's source-text scan or `JavaClassProcessor` field-value extension | Documented Context7 source: ArchUnit's `JavaFieldBuilder` drops constant values during import |

**Key insight:** Phase 12's biggest reuse win is **`OAuthTokenStore` as a thin facade** over the existing `RefreshTokenCipher`. The cipher's AAD-binds-tenantId, envelope format, and key bootstrap are all already production-tested by Gmail. Extracting a facade with `encrypt(byte[] plaintext, UUID tenantId, RowDiscriminator discriminator)` lets both Gmail and Calendar persistence write through the same byte-array column on different tables without duplicating crypto code.

## Runtime State Inventory

Not applicable — Phase 12 is greenfield additive (no renames, no migrations of existing data). The only existing-data touchpoint is the **`gmail_inbox_projection` table getting two new nullable columns**: no backfill required because `NULL` correctly means "not a calendar message" everywhere in the codebase.

**Verified by:**
- `findInboxPage` native query uses positional column references; adding nullable columns does not break the existing entity hydration (JPA hydrates new fields as null on legacy rows).
- The existing `system-calendar` template in `113-default-rule-templates-seed.yaml` has matcher `SEMANTIC_INTENT` — if Phase 12 changes the matcher type, **existing tenants who already have the materialized rule** keep their old semantic-intent matcher (no auto-migration). User-facing impact: the existing label still fires (via AI matching), the new PRESET path adds no regression. A follow-up changeset can rewrite the template AND existing rule rows (idempotent UPDATE keyed by `template_key + template_version` if uncustomized). Decide in planning: are pre-Phase-12 tenants left on the SEMANTIC matcher, or migrated to PRESET? Recommend MIGRATE (matches D-09 intent for everyone).

## Common Pitfalls

### Pitfall 1: ArchUnit literal-string scanner cannot inspect method-call arguments via byte-code

**What goes wrong:** D-02's wording — "iterating `getMethodCallsFromSelf()` and inspecting argument constants" — assumes ArchUnit's `JavaMethodCall` exposes the literal `String` arguments passed to methods like `.scope("https://www.googleapis.com/auth/calendar.events")`. **It does not.** ArchUnit's bytecode model drops the constant value during import: `JavaClassProcessor.visitField(...)` receives the constant value as a parameter from ASM but **does not capture it on `JavaFieldBuilder`** `[VERIFIED: Context7 /tng/archunit JavaClassProcessor.java]`. Same for arguments to method invocations — the high-level API surfaces target + caller + owner, not literal argument values.

**Why it happens:** Most ArchUnit examples use class/package/annotation predicates, not constant-value predicates. Easy to assume the constant is reachable.

**How to avoid:** Three viable implementation paths for the literal scanner:

1. **Source-text scan (recommended).** Use `java.nio.file.Files.walk(Path.of("backend/{api,core,worker}/src/main/java"))` + a regex `https://www\\.googleapis\\.com/auth/[^"\\s]+`. Whitelist `core/oauth/scope/*.java`. Fail the test with a clear diagnostic listing the offending file + line + URL. NOT an ArchUnit `ArchRule` in the strict sense, but a JUnit 5 `@Test` in the same `arch/` directory. Honors D-02's INTENT (CI fails on stray scope literals) without misleading the planner about API capabilities.
2. **Static-final-field constant scan.** Add a custom `ClassFileImporter` extension that captures the constant via ASM's `visitField`. Heavyweight; only catches `public static final String FOO = "https://..."` patterns, not inline string literals.
3. **Cobertura/SpotBugs custom rule.** Overkill for Phase 12 timeline.

**Recommend path 1 (source-text scan).** Rename test from `OAuthScopeAllowListTest` to clarify it's a source-text rule, or keep the name and document the implementation choice in the class-level JavaDoc. Either way, surface this caveat in plan-checker so the planner doesn't write `noClasses().that()...` as if ArchUnit can scan arg literals.

**Warning signs:** PR description claims "ArchUnit rule fails the build on stray scope" but the rule body is an empty `allowEmptyShould(true)` — the rule trivially passes because it can't see anything to fail on.

### Pitfall 2: Refresh token rotation on incremental scope grant

**What goes wrong:** Google MAY rotate refresh tokens on the second OAuth round-trip when incremental scopes are added. If Phase 12's `CalendarOAuthSuccessHandler` overwrites the existing `gmail_connections.refresh_token_encrypted` with the new token (thinking it's a Calendar token), Gmail breaks.

**Why it happens:** Spring's `OAuth2AuthorizedClient` per-registration storage doesn't auto-rotate the SIBLING registration's token. But the OAuth provider response may include a new RT scoped to the combined scope set.

**How to avoid:**
- Calendar success handler writes ONLY to `calendar_connection.refresh_token_encrypted`, never to `gmail_connection`.
- Test: after a calendar OAuth round-trip, verify `gmail_connection.refresh_token_encrypted` is byte-equal to the pre-OAuth ciphertext (DB read).
- If Google ever does rotate, accept that user has to reconnect Gmail (CASA-tolerated UX).

**Warning signs:** Gmail watch renewal starts failing 24h after the first Calendar connect in production.

### Pitfall 3: Single `BEGIN:VCALENDAR` body check produces false negatives on multipart Gmail messages

**What goes wrong:** Gmail messages with calendar invites typically have:
- `multipart/mixed`
  - `multipart/alternative`
    - `text/plain` (no iCal)
    - `text/html` (no iCal)
  - `text/calendar; method=REQUEST` (the actual invite)
  - `application/ics` named attachment

The classifier must walk MIME parts, not just inspect the message body. Inbox Zero's `hasICalendarContent` checks `email.textHtml || email.textPlain` — which is the Gmail API's flattened body — and works for most cases but misses the attachment-only path.

**How to avoid:** Mirror IZ's `isCalendarInvite()` triple-check (`.ics` attachment OR `mimeType=text/calendar` OR `BEGIN:VCALENDAR` body marker). The Gmail `Message` returned by `users.messages.get(id, format=FULL)` exposes `payload.parts[].mimeType` for the attachment walk and `payload.parts[].body.data` (base64) for the body text.

**Warning signs:** Some users' invites get labeled, others don't, depending on Apple vs Google vs Outlook sender.

### Pitfall 4: `text/calendar` charset variability

**What goes wrong:** Some senders emit `text/calendar; charset=UTF-8`, others `charset=iso-8859-1`, some `charset=UTF-7` for embedded Asian text. Decoding the part body as the wrong charset corrupts `DTSTART` parsing (rare but real for older Lotus/Outlook).

**How to avoid:** ical4j 4.x's `CalendarBuilder.build(InputStream)` honors the BOM and the iCal `PRODID` hints. Feed the raw `InputStream` from the Gmail attachment, not a pre-decoded `String`. Catch all parse exceptions and silently skip (Phase 12 acceptance: forwarded/corrupted invites are treated as plain emails).

### Pitfall 5: Worker-side classifier transaction scope

**What goes wrong:** `CalendarMessageClassifier` listens to `MailMessageObserved` AFTER_COMMIT. If it then UPDATEs `gmail_inbox_projection`, it must own its own transaction (`@Transactional(propagation = REQUIRES_NEW)`). A failure in the classifier must NOT roll back the original message-observed transaction (already committed) but also must not leave the projection in a half-updated state.

**How to avoid:** Mirror the existing worker pattern. Use `TransactionTemplate` with REQUIRES_NEW. On parse failure, write nothing (no half-update). On parse success, single-row UPDATE keyed by `(tenant_id, gmail_connection_id, gmail_message_id)`.

### Pitfall 6: D-13 implicit cascade on per-calendar disable

**What goes wrong:** D-13 says "Toggling a calendar off automatically removes any preference rows referencing it." This is service-layer logic, not DB cascade — toggling `is_enabled=false` on `calendars` doesn't trigger a row delete on `mailbox_calendar_preferences` unless the service explicitly deletes them.

**How to avoid:** In `CalendarToggleService.disable(calendarId)`:
1. `UPDATE calendars SET is_enabled=false WHERE id=:calendarId`
2. `DELETE FROM mailbox_calendar_preferences WHERE calendar_id=:calendarId`
3. Surface a UX confirmation IF preference rows existed: "Disabling this calendar will remove 2 role assignments. Continue?"

### Pitfall 7: `prompt=consent` UX friction on every Calendar OAuth round-trip

**What goes wrong:** Setting `prompt=consent` on the Calendar registration ALWAYS forces the consent screen, even on a user's second/third connection attempt. Users find this jarring ("I just consented two minutes ago for my other account").

**Why it happens:** `prompt=consent` is what makes Google issue a refresh token on every flow (without it, an existing grant short-circuits and no RT is returned).

**How to avoid (Phase 12 acceptance):** Accept the friction. The user is explicitly connecting a CALENDAR account they have not yet connected; consent is appropriate. Alternative (deferred to v1.5+ if telemetry shows abandonment): drop `prompt=consent` and handle the "no refresh token returned" case the same way `GoogleOAuthSuccessHandler` already does (retry with `?reconnect=true`).

### Pitfall 8: Google Workspace OU policies blocking Calendar API

**What goes wrong:** A Workspace admin can block third-party Calendar API access at the OU level. The user completes OAuth, the token works, but `calendarList().list()` returns 403 `accessNotConfigured`. The connection card shows "Connected" but the sub-calendar list is empty and confusing.

**How to avoid:** On post-OAuth snapshot ingest, if `calendarList.list()` returns 403, mark the connection `status=CONNECTED` but persist a `last_error="WORKSPACE_POLICY_BLOCKED"` flag (consider adding to the entity). UX surfaces "Your Workspace admin has restricted Calendar API access — contact them or use a personal Google account."

## Code Examples

### Reading the OAuth scope ledger from a `ClientRegistration` bean

```java
package com.zeromail.core.oauth.scope;

/**
 * Single source of truth for every Google OAuth scope this product is allowed
 * to request. Adding a new constant requires CASA-tier review (see JavaDoc per
 * scope). The literal URL never appears anywhere else in production code; the
 * ArchUnit {@code OAuthScopeAllowListTest} (or its source-text fallback)
 * enforces this rule in CI.
 *
 * <p>Each constant documents the verification tier per
 * https://developers.google.com/identity/protocols/oauth2/scopes — tier drives
 * GCP consent-screen review path and CASA scoping.
 */
public enum GoogleOAuthScope {

    /** OIDC standard — non-sensitive. Introduced: v1.0 (Gmail login bundle). */
    OPENID("openid"),
    /** OIDC standard — non-sensitive. Introduced: v1.0 (Gmail login bundle). */
    PROFILE("profile"),
    /** OIDC standard — non-sensitive. Introduced: v1.0 (Gmail login bundle). */
    EMAIL("email"),

    /** Gmail RW: read + label + draft + send. Tier: RESTRICTED (CASA Tier 2). Introduced: v1.0. */
    GMAIL_MODIFY("https://www.googleapis.com/auth/gmail.modify"),

    /** Calendar free/busy only. Tier: NON-SENSITIVE. Introduced: v1.4 Phase 12. */
    CALENDAR_FREEBUSY("https://www.googleapis.com/auth/calendar.freebusy"),
    /** Calendar event RW. Tier: SENSITIVE. Introduced: v1.4 Phase 12. */
    CALENDAR_EVENTS("https://www.googleapis.com/auth/calendar.events"),
    /** Calendar metadata read-only. Tier: SENSITIVE. Introduced: v1.4 Phase 12 (calendarList enumeration). */
    CALENDAR_READONLY("https://www.googleapis.com/auth/calendar.readonly");

    private final String value;
    GoogleOAuthScope(String value) { this.value = value; }
    public String value() { return value; }
}
```

```java
package com.zeromail.api.security;

import com.zeromail.core.oauth.scope.GoogleOAuthScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@Configuration
public class CalendarClientRegistrationConfig {

    @Bean
    public ClientRegistration googleCalendarClientRegistration(/* @Value injected client-id, secret */) {
        return ClientRegistration.withRegistrationId("google-calendar")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(
                        GoogleOAuthScope.CALENDAR_FREEBUSY.value(),
                        GoogleOAuthScope.CALENDAR_EVENTS.value(),
                        GoogleOAuthScope.CALENDAR_READONLY.value())
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .clientName("Google Calendar")
                .build();
    }
}
```

### `CalendarApiClientFactory` (mirror of `GmailApiClientFactory`)

```java
package com.zeromail.core.calendar.gateway;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
// ... (same imports/pattern as GmailApiClientFactory) ...

@Component
public class CalendarApiClientFactory {
    // Mirrors GmailApiClientFactory: refresh-token cache by calendarConnectionId,
    // same refresh-token-then-AccessToken flow, same Disconnected-status guard.

    public Calendar buildClientForCalendarConnection(UUID tenantId, UUID calendarConnectionId) throws IOException {
        CalendarConnectionEntity calendarConnection = connectionRepository
                .findByIdAndTenantId(calendarConnectionId, tenantId)
                .orElseThrow(() -> new CalendarConnectionNotOwnedException(tenantId, calendarConnectionId));
        if (calendarConnection.getStatus() != CalendarConnectionStatus.CONNECTED) {
            throw new CalendarDisconnectedException(tenantId, calendarConnectionId);
        }
        // ... cached-or-refresh access-token mint, identical structure to Gmail ...
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(accessToken, null));
        HttpRequestInitializer initializer = new HttpCredentialsAdapter(credentials);
        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                initializer)
                .setApplicationName("ZeroMail")
                .build();
    }
}
```

### Post-OAuth `calendarList.list()` snapshot ingest

```java
public class CalendarSnapshotIngestionService {

    @Transactional
    public void ingestSnapshot(UUID tenantId, UUID calendarConnectionId, UUID activeMailboxId) throws IOException {
        Calendar calendarClient = calendarApiClientFactory
                .buildClientForCalendarConnection(tenantId, calendarConnectionId);
        com.google.api.services.calendar.model.CalendarList result =
                calendarClient.calendarList().list().execute();

        Optional<CalendarEntity> primaryCalendar = Optional.empty();
        for (var item : result.getItems()) {
            boolean isPrimary = Boolean.TRUE.equals(item.getPrimary());
            CalendarEntity calendar = new CalendarEntity(
                    UUID.randomUUID(),
                    calendarConnectionId,
                    tenantId,
                    item.getId(),
                    item.getSummary(),
                    item.getDescription(),
                    isPrimary,
                    /* isEnabled */ true,
                    item.getTimeZone());
            calendarRepository.save(calendar);
            if (isPrimary) primaryCalendar = Optional.of(calendar);
        }

        // D-06: auto-tag primary calendar with all 3 roles for the active mailbox only.
        primaryCalendar.ifPresent(cal -> {
            for (MailboxCalendarRole role : MailboxCalendarRole.values()) {
                mailboxCalendarPreferenceRepository.save(new MailboxCalendarPreferenceEntity(
                        UUID.randomUUID(),
                        tenantId,
                        activeMailboxId,
                        calendarConnectionId,
                        cal.getId(),
                        role));
            }
        });
    }
}
```

### `apps/web/features/calendar/query-keys.ts` (mirrors mailbox shape)

```typescript
export const calendarQueryKeys = {
  all: ['calendar'] as const,
  connectionsForMailbox: (mailboxId: string) =>
    [...calendarQueryKeys.all, 'mailbox', mailboxId, 'connections'] as const,
  preferencesForMailbox: (mailboxId: string) =>
    [...calendarQueryKeys.all, 'mailbox', mailboxId, 'preferences'] as const,
} as const;
```

### TanStack Query `meta` toast pattern (CLAUDE.md §12)

```typescript
export function useDisconnectCalendarConnection(mailboxId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (connectionId: string) => disconnectCalendarConnection(connectionId),
    meta: {
      successMessage: 'Calendar disconnected',
      errorMessage: 'Could not disconnect calendar. Try again.',
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: calendarQueryKeys.connectionsForMailbox(mailboxId),
      });
    },
  });
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| ical4j 3.x `net.fortuna.ical4j.model.Date` shim | ical4j 4.x `java.time` `Temporal` | 4.0 release | Phase 12 lands on 4.x directly; no migration debt |
| Two GCP OAuth client-ids per scope category | Single GCP client + multiple `ClientRegistration` | Spring Security 5.x | Already in use; reuse for Calendar |
| Backend rule-action downgrade for invite protection | Seeded `SystemType=CALENDAR` PRESET rule (IZ pattern) | D-09 revision | Phase 12 ships IZ pattern |
| Polling for sub-calendar enumeration | `calendarList.list()` once at connect time (no watch) | This phase | Refresh deferred until Phase 16+ (events.watch is restricted scope) |
| `pinned_until` materialized column | Derived `pin_until` in ORDER BY | D-12 | Zero migration risk on existing v1.3 projection table |

**Deprecated/outdated:**
- ical4j 3.x — legacy `Date`/`DateTime` API. We jump straight to 4.x.
- Inbox Zero's `CalendarConnection.emailAccountId` → IZ couples connection to mailbox; we use workspace-shared (CAL-CONN-06 deliberate divergence).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | ical4j 4.2.4 is the current GA on Maven Central as of 2026-06-20 | Standard Stack | Wrong version → Phase 12 uses 4.2.x point release; non-blocking, verify before locking |
| A2 | `google-api-services-calendar` latest dated rev on the `-2.0.0` line aligns BOM with existing Gmail dep | Standard Stack | Mismatch could pull in a transitive HTTP/JSON layer conflict — verify with `./gradlew :backend:core:dependencyInsight --dependency com.google.api-client:google-api-client` |
| A3 | The `system-calendar` seeded rule's existing `SEMANTIC_INTENT` matcher should be MIGRATED to the new `PRESET_CALENDAR` matcher for ALL tenants (including pre-Phase-12 users with materialized rules) | Pattern 5 | If wrong: pre-existing tenants get DOUBLE matching (PRESET + SEMANTIC) on the same rule — verify with user during plan-checker |
| A4 | Per-mailbox per-role `event_write` and `brief_source` are "at most one" but `freebusy` is multi-select (D-08 says role-tag is runtime authority — the cardinality is implied, not stated) | Pattern 2 | If `event_write` can have multiple destinations, the partial unique index breaks legitimate writes — verify with user |
| A5 | INVITE vs RESCHEDULE distinction (D-11 enum has both) is a Phase-12 nice-to-have, NOT a launch gate; ship `INVITE` for all `METHOD:REQUEST` initially | Pattern 4 | Premature locking on UID-based RESCHEDULE logic if D-11 actually requires it from day one |
| A6 | The Calendar OAuth round-trip can call `calendarList.list()` synchronously in the success handler IF wrapped in an AFTER_COMMIT side-effect handler (so it doesn't slow the redirect) | Pattern 4 anti-pattern | If sync ingestion is preferred, the user sees a 200–800ms delay on connect — acceptance/UX call |
| A7 | The new `CalendarOAuthSuccessHandler` lives in `backend/api/security/` (alongside `GoogleOAuthSuccessHandler`), NOT in `backend/core` | Recommended structure | If org standards differ, file goes to `backend/core/calendar/oauth/` — verify |
| A8 | `prompt=consent` UX friction on every Calendar OAuth is acceptable for Phase 12 | Pitfall 7 | If unacceptable, the success handler needs the `?reconnect=true` retry dance like Gmail |

**If this table is empty:** N/A — eight assumptions logged. The planner should surface A3, A4, A5, A6 for user confirmation during `gsd-plan-phase` review.

## Open Questions

1. **`SystemType` modeling — column on `RuleEntity` or new `MatcherType.PRESET_CALENDAR`?**
   - What we know: `MatcherType` enum exists; `RuleTemplateEntity` carries `matcher_ast` JSONB; no `systemType` column yet on `rule` table.
   - What's unclear: do we add `system_type varchar(32)` to `rule` + `rule_template_catalog`, or fold the discriminator into the `matcher_ast` JSON (`{"type":"PRESET_CALENDAR"}`)?
   - Recommendation: extend `matcher_ast.type` with `PRESET_CALENDAR` value — keeps schema flat, leverages existing JSONB. Confirm by reading `RuleEvaluator` (file not loaded yet during research; planner should read it).

2. **RESCHEDULE classification — Phase 12 in-scope or deferred?**
   - What we know: D-11 enum includes `RESCHEDULE`; ical4j `METHOD:REQUEST` doesn't distinguish initial vs reschedule.
   - What's unclear: do we add a `(tenant_id, gmail_thread_id, ical_uid, event_dt)` lookup table to detect "same UID, new event_dt → RESCHEDULE"?
   - Recommendation: Phase 12 ships INVITE for all `METHOD:REQUEST`; RESCHEDULE remains in the enum but never written. Follow-up phase adds UID tracking.

3. **`event_write` and `brief_source` cardinality — single or multi?**
   - What we know: D-07 calls them "single-select"; D-08 says role-tag is runtime authority.
   - What's unclear: does `event_write` support multiple write-destinations (e.g., user wants both personal AND work calendar to receive booking-created events)?
   - Recommendation: Phase 12 ships single-select (matches D-07 UI); add partial unique indexes. Multi-select is a v1.5+ tweak with no schema change required.

4. **Pre-Phase-12 tenants' seeded `system-calendar` rule — migrate or leave?**
   - What we know: `113-default-rule-templates-seed.yaml` already creates `system-calendar` with `SEMANTIC_INTENT`. Existing tenants who first-logged-in before Phase 12 already have materialized rules of this shape.
   - What's unclear: do we MIGRATE existing materialized rules to the new PRESET_CALENDAR matcher (one-shot UPDATE), or only update the template and let existing rules stay on SEMANTIC?
   - Recommendation: MIGRATE — uncustomized rules with `template_key='system-calendar' AND template_version=1` get their `matcher_ast` UPDATEd in a new changeset. Customized rules (per `RuleEntity.isCustomized()`) are preserved.

5. **`booking_link.destination_calendar_id` for D-14 cascade — defer-column or null-on-disconnect?**
   - What we know: `booking_link` table doesn't exist yet (Phase 14 adds it).
   - What's unclear: does D-14's "null-out destination_calendar_id if applicable" require the column to exist already, or is it forward-compat handled in Phase 14?
   - Recommendation: Phase 12 ships the `CalendarConnectionDisconnected` event; Phase 14's `booking_link`-introducing migration adds the listener. No Phase 12 work needed for booking-side cascade.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 25 | Backend compile/test | ✓ | 25 LTS | — |
| Gradle 9.5.x | Build | ✓ | 9.5.0 | — |
| PostgreSQL 18.4 (dev tunnel) | Backend boot / Liquibase | ✓ (via SSH tunnel `dat@72.62.193.33` :5555 — see project memory `reference_dev_db_ssh_tunnel.md`) | 18.4 | None — backend won't boot without it |
| Redis 7.2 (local docker) | Session store | ✓ | 7.2 | None |
| Node 22 + pnpm 11.0.8 | `apps/web` codegen + dev | ✓ | latest | — |
| `gsd-tools` CLI | research-plan / package legitimacy seam | ✗ | — | Used Context7 + WebSearch + direct Maven Central listings instead |
| IntelliJ JetBrains MCP | Symbol/refactor tooling during planning | ✓ (per project tooling config) | — | Fall back to Grep/Read |
| Google Cloud Console OAuth Consent Screen (Calendar scopes) | First-time user-facing test (CAL-CONN-02 verification) | ✗ during research; must be configured before Phase 12 ship | — | Mark in PLAN as ops prerequisite — scopes must be ADDED to the existing OAuth client BEFORE the first test |

**Missing dependencies with no fallback:** Google Cloud Console scope addition is a pre-requisite for production rollout but does NOT block plan-phase. Surface as a checklist item in PLAN.

**Missing dependencies with fallback:** `gsd-tools` CLI absent in sandbox; used direct registry checks. No blocking impact.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter 5.x via Boot 4.1.0 BOM) + Spring Boot Test + Testcontainers 1.21.3 + ArchUnit 1.4.2; Vitest 4 + Playwright for `apps/web` |
| Config file | `build.gradle.kts` per subproject; `apps/web/vitest.config.ts`; `apps/web/playwright.config.ts` |
| Quick run command | `./gradlew :backend:core:test --tests "*Calendar*"` |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| INFRA-01 | No stray scope literal outside ledger | ArchUnit / source-text scan | `./gradlew :backend:core:test --tests "OAuthScopeAllowListTest"` | ❌ Wave 0 |
| CAL-CONN-02 | Calendar `ClientRegistration` exists with only the three calendar scopes | `@SpringBootTest` slice on `SecurityConfig` + Calendar config | `./gradlew :backend:api:test --tests "CalendarClientRegistrationConfigTest"` | ❌ Wave 1 |
| CAL-CONN-03 | Refresh token round-trip via OAuthTokenStore preserves AAD | `@DataJpaTest` + `CalendarConnectionEntity` + cipher | `./gradlew :backend:core:test --tests "CalendarConnectionCipherTest"` | ❌ Wave 1 |
| CAL-CONN-04 | List + disconnect cascade | `@SpringBootTest` `CalendarConnectionServiceTest` | `./gradlew :backend:core:test --tests "CalendarConnectionServiceTest"` | ❌ Wave 2 |
| CAL-CONN-05 | calendarList enumeration → `calendars` rows; primary flag preserved | `@SpringBootTest` with stubbed `Calendar.Builder` mock | `./gradlew :backend:core:test --tests "CalendarSnapshotIngestionServiceTest"` | ❌ Wave 2 |
| CAL-CONN-06 | `calendar_connection` table has no `gmail_connection_id` column | ArchUnit or schema-introspection test | `./gradlew :backend:core:test --tests "CalendarSchemaIsolationTest"` | ❌ Wave 2 |
| CAL-CONN-07 | `mailbox_calendar_preference` accepts only valid roles | `@DataJpaTest` constraint test | `./gradlew :backend:core:test --tests "MailboxCalendarPreferenceConstraintTest"` | ❌ Wave 2 |
| CAL-CONN-08 | DISCONNECTED status → `CalendarDisconnectedException` from factory | Unit test on `CalendarApiClientFactory` | `./gradlew :backend:core:test --tests "CalendarApiClientFactoryDisconnectTest"` | ❌ Wave 1 |
| CAL-TRIAGE-01 | ical4j parse: INVITE/CANCEL/RSVP across IZ test fixtures | unit on `CalendarPartParser` | `./gradlew :backend:worker:test --tests "CalendarPartParserTest"` | ❌ Wave 4 |
| CAL-TRIAGE-02 | Pinned ORDER BY surfaces pinned messages on top | `@DataJpaTest` projection slice w/ keyset cursor sanity | `./gradlew :backend:core:test --tests "InboxProjectionPinningTest"` | ❌ Wave 4 |
| CAL-TRIAGE-03 | PRESET_CALENDAR matches when messageClass != null; user rule still labels | `RuleEvaluatorTest` | `./gradlew :backend:core:test --tests "RuleEvaluatorCalendarPresetTest"` | ❌ Wave 5 |
| CAL-TRIAGE-04 | Classifier runs without Calendar OAuth — verify zero Calendar API call in test wiring | `@SpringBootTest` worker slice with mocked `CalendarApiClientFactory` (must NOT be invoked) | `./gradlew :backend:worker:test --tests "CalendarMessageClassifierNoOAuthTest"` | ❌ Wave 4 |

### Sampling Rate

- **Per task commit:** `./gradlew :backend:core:test --tests "*Calendar*" :backend:api:test --tests "*Calendar*"` (~15s)
- **Per wave merge:** `./gradlew test` (existing full suite ~3-5 min)
- **Phase gate:** Full suite green + `pnpm --filter web run lint && pnpm --filter web test` before `/gsd-verify-work`.

### Wave 0 Gaps

- [ ] `backend/core/src/test/java/com/zeromail/core/oauth/scope/OAuthScopeAllowListTest.java` — source-text scan for `https://www.googleapis.com/auth/` literals outside `core/oauth/scope/` (covers INFRA-01)
- [ ] `backend/core/src/test/java/com/zeromail/core/oauth/scope/GoogleOAuthScopeEnumTest.java` — round-trip enum value tests (no duplicates, no stale entries)
- [ ] `backend/core/src/test/java/com/zeromail/core/calendar/...` — slice fixtures for `CalendarConnectionEntity`
- [ ] `backend/worker/src/test/java/com/zeromail/worker/triage/CalendarPartParserTest.java` — ical4j fixtures (request/cancel/reply samples — borrow from ical4j's own test resources or Inbox Zero's `calender-event.test.ts` fixtures translated to `.ics` files)
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/calendar/CalendarConnectionControllerTest.java` — `@WebMvcTest` with mocked services
- [ ] `apps/web/__tests__/calendar/...` — feature tests for hooks
- [ ] `apps/web/e2e/calendar-settings.spec.ts` — Playwright e2e on `/settings/mailboxes/[id]/calendar`

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | OAuth2 Login filter (Spring Security 6.x) — already configured |
| V3 Session Management | yes | Spring Session + Redis (existing); intent state in session attribute (existing pattern) |
| V4 Access Control | yes | `MailboxBindingFilter` (existing) + new `CalendarConnectionService.resolveOwnedConnectionOrThrow(tenantId, connectionId)` |
| V5 Input Validation | yes | Bean Validation on DTOs; iCal body discarded after parse (no SSRF, no XXE — ical4j 4.x disables external entity resolution by default; verify with `CompatibilityHints`) |
| V6 Cryptography | yes | Existing AES-GCM `RefreshTokenCipher` via `OAuthTokenStore` facade |
| V13 API and Web Service | yes | CSRF SPA mode (existing); CORS (existing) |

### Known Threat Patterns for {Spring Boot 4 + OAuth2}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cross-tenant calendar leak (one tenant's free/busy returned to another) | Information Disclosure | `tenantId` is the AES-GCM AAD; `MailboxBindingFilter` already validates session→tenant binding; `CalendarConnectionService.resolveOwnedConnectionOrThrow` enforces row ownership |
| Stale access token used after disconnect | Elevation of Privilege | `CalendarApiClientFactory.evictAccessToken(connectionId)` on disconnect (mirror Gmail) |
| OAuth state mismatch / CSRF on callback | Tampering | Spring Security's built-in `state` param + nonce checks (no custom impl) |
| iCal injection (XXE / billion-laughs in `text/calendar` body) | Denial of Service | ical4j 4.x disables external entity resolution; size-bound the part body to e.g. 1 MB before parse; catch all parse exceptions |
| Cross-registration refresh-token leak (Calendar RT overwrites Gmail RT) | Tampering | Calendar success handler writes ONLY to `calendar_connection.refresh_token_encrypted`; integration test verifies Gmail row untouched (Pitfall 2) |
| Scope-string drift (developer types `https://www.googleapis.com/auth/calendar` instead of `calendar.events`) | Elevation of Privilege | INFRA-01 ledger + ArchUnit/source-text scan (this phase's main control) |
| Open-redirect via OAuth success target | Tampering | Already mitigated in `GoogleOAuthSuccessHandler` constructor (`baseUrl` scheme + host validation); reuse same pattern for `CalendarOAuthSuccessHandler` |
| Calendar API quota exhaustion (Pitfall 2 from `PITFALLS.md`) | Denial of Service | Out of Phase 12 scope (Phase 13 owns free/busy quota); Phase 12 only calls `calendarList.list()` once at connect time |

## Sources

### Primary (HIGH confidence)

- `[VERIFIED: codebase]` `backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java` — AES-GCM envelope shape
- `[VERIFIED: codebase]` `backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java` — connection→client builder pattern Calendar mirrors
- `[VERIFIED: codebase]` `backend/core/src/main/java/com/zeromail/core/inbox/persistence/GmailInboxProjectionRepository.java` — current `findInboxPage` ORDER BY clause
- `[VERIFIED: codebase]` `backend/api/src/main/java/com/zeromail/api/security/GoogleAuthorizationRequestResolver.java` — OAuth request customization extension point
- `[VERIFIED: codebase]` `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` — user-chain @Order(4) where calendar OAuth flow lives
- `[VERIFIED: codebase]` `backend/core/src/main/java/com/zeromail/core/rules/usecases/RuleTemplateMaterializationService.java` — `system-calendar` template already in `DEFAULT_RULE_TEMPLATE_KEYS_EN`
- `[VERIFIED: codebase]` `backend/core/src/main/resources/db/changelog/changes/113-default-rule-templates-seed.yaml` — existing `system-calendar` seed (SEMANTIC_INTENT matcher, label "Calendar")
- `[VERIFIED: codebase]` `backend/core/src/test/java/com/zeromail/core/admin/arch/AdminTenantOAuthGuardTest.java` — ArchUnit composite-rule precedent
- `[VERIFIED: Context7 /tng/archunit]` `JavaClassProcessor.visitField` confirms constant values are NOT captured by JavaFieldBuilder (D-02 wording caveat)
- `[CITED: docs.spring.io/spring-security/reference/6.5/...]` Multiple `ClientRegistration` with shared client-id pattern
- `[CITED: github.com/ical4j/ical4j README]` ical4j 4.x `java.time` API; `TemporalAdapter.parse`; `CalendarBuilder.build(Reader)` shape
- `[VERIFIED: D:\study-materials-summer-2026\EXE202\inbox-zero\apps\web\utils\parse\calender-event.ts]` `isCalendarInvite()` / `isCalendarInviteAttachment()` / `hasICalendarContent()` lines 281–303
- `[VERIFIED: D:\study-materials-summer-2026\EXE202\inbox-zero\apps\web\utils\ai\choose-rule\match-rules.ts]` lines 201–213: `SystemType.CALENDAR` PRESET match-before-AI branch
- `[VERIFIED: D:\study-materials-summer-2026\EXE202\inbox-zero\apps\web\prisma\schema.prisma]` `CalendarConnection` + `Calendar` (~L1135–L1175): per-mailbox shape (deliberately diverged via CAL-CONN-06)

### Secondary (MEDIUM confidence)

- `[CITED: central.sonatype.com/artifact/org.mnode.ical4j/ical4j]` ical4j 4.2.4 GA artifact + Maven coords
- `[CITED: mvnrepository.com/artifact/com.google.apis/google-api-services-calendar]` `v3-rev20260225-2.0.0` + earlier 2.0.0-line revs (verify latest at planning time)
- `[CITED: developers.google.com/api-client-library/java/apis/calendar/v3]` Google Calendar Java client overview
- `[CITED: .planning/research/PITFALLS.md]` v1.4 known Calendar/OAuth pitfalls — Pitfall 1 (CASA scope re-anchor) + Pitfall 2 (free/busy quota) cross-referenced

### Tertiary (LOW confidence)

- Training-data knowledge of Google Workspace OU policies (Pitfall 8) — verify by attempting a Workspace-restricted account during integration testing

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — ical4j 4.2.4 + Calendar API 2.0.0-line both verified via Maven Central listings; matches existing pin patterns
- Architecture: HIGH — direct mirror of v1.3 Gmail patterns; deliberate divergences (workspace-shared vs per-mailbox) clearly justified by REQUIREMENTS.md
- D-02 ArchUnit implementation: MEDIUM-HIGH — D-02's wording about "argument constants" is technically inaccurate; source-text fallback honors intent and is documented in Pitfall 1
- Triage classifier: HIGH — IZ pattern is well-documented, ical4j is RFC-grade, persistence shape locked by D-11/D-12
- Rule engine PRESET branch: MEDIUM — exact `RuleEvaluator` insertion point not read during research (planner should read `core.rules.domain.RuleEvaluator` before locking the plan)
- Frontend: HIGH — mailbox feature provides exact replication target; IZ shell + Calendly overlay both visually familiar
- INVITE/RESCHEDULE distinction: MEDIUM — assumption A5 (ship INVITE for all `METHOD:REQUEST` initially)

**Research date:** 2026-06-20
**Valid until:** 2026-07-20 (Calendar API revs publish ~monthly; ical4j 4.x stable; Spring Boot 4.1 / Spring AI 2.0 GA both locked through v1.4 milestone close)
