# Phase 12: Calendar Connection + Triage Foundation - Context

**Gathered:** 2026-06-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 12 introduces Google Calendar as a second OAuth surface alongside the v1.3 Gmail mailbox foundation. It ships **three independently shippable user-visible capabilities** glued by one new shared piece of infrastructure:

1. **OAuth scope ledger (INFRA-01)** — the single source of truth for every Google OAuth scope this product is allowed to request, with ArchUnit enforcement. Reused by Phase 15 (Drive) to enforce `drive.file`-only.
2. **Multi-Google-Calendar incremental OAuth (CAL-CONN-01..08)** — workspace-shared `calendar_connection`, per-mailbox `mailbox_calendar_preference` table that role-tags each enabled calendar per Gmail mailbox to prevent personal-availability leakage into work-mailbox drafts.
3. **Calendar-aware Gmail triage (CAL-TRIAGE-01..04)** — `text/calendar` MIME classification, 24h top-of-inbox pinning with "Cancellation" / "Time changed" badges, and a **seeded default `SystemType=CALENDAR` rule** (Inbox Zero pattern) that auto-labels invites. Ships **without** any Calendar OAuth scope.

**Requirements (locked, sourced from REQUIREMENTS.md — no SPEC.md):** INFRA-01, CAL-CONN-01..08, CAL-TRIAGE-01..04 (13 requirements). One requirement (CAL-TRIAGE-03) was revised during this discussion — see D-09.

**Explicit non-goals (deferred to later v1.4 phases):**
- `CalendarReadGateway` + `freebusy.query` cache + `UnifiedAvailabilityService` → **Phase 13**
- `propose_meeting` rule action → **Phase 13**
- Public booking page `/book/{slug}` + `CalendarOutboundGateway` → **Phase 14**
- Drive connection / filing engine → **Phase 15** (reuses INFRA-01 scope ledger)
- Meeting briefs cron / agentic loop → **Phase 16**

</domain>

<decisions>
## Implementation Decisions

### OAuth scope ledger (INFRA-01)

- **D-01:** **Code-first Java enum** `GoogleOAuthScope` is the canonical ledger. Enum constants (`CALENDAR_FREEBUSY`, `CALENDAR_EVENTS`, `CALENDAR_READONLY`, `GMAIL_*`, etc.) carry the scope URL as the value. **No** `drive`, `drive.readonly`, `drive.metadata.readonly` entries — Phase 15 will add only `DRIVE_FILE`. JavaDoc on each entry documents purpose + phase-introduced + sensitivity tier for later doc generation.
- **D-02:** **ArchUnit literal-string scanner** enforces the ledger in CI. Rule shape: `noClasses().that().resideOutsideOfPackage("..core.oauth.scope..").should().containAnyConstantMatching("^https://www\\.googleapis\\.com/auth/.*$")`. Implemented as a custom `ArchCondition<JavaClass>` iterating `getMethodCallsFromSelf()` and inspecting argument constants. Test name: `OAuthScopeAllowListTest`. Lives in `backend/core/src/test/java/com/zeromail/core/oauth/scope/`.
- **D-03:** **Production scope requests** at every `ClientRegistration` builder site read the URL from `GoogleOAuthScope.X.value()` — never from a string literal. The enum package itself is whitelisted from the literal-scanner rule (otherwise the enum body would self-fail).
- **D-04:** **Human-readable `docs/oauth-scopes.md`** is **deferred** to Phase 15 (when GCP restricted-scope verification timing makes a generated PDF ledger necessary). v1.4 Phase 12 ships A — enum + ArchUnit — only. Upgrade path: add a Gradle task `generateOAuthScopesDoc` that emits `docs/oauth-scopes.md` from enum + JavaDoc + a CI freshness check, when needed.

### Multi-Google-Calendar OAuth + connection model (CAL-CONN-01..08)

- **D-05:** **Multi-account from day one** per the locked CAL-CONN-01 wording. v1.4 supports N Google Calendar accounts per workspace, free. Pricing-tier gating deferred to v1.5+ along with broader monetization (matches Inbox Zero's per-seat `emailAccountsAccess` pattern, which Zero Mail can mirror later without schema migration). Research showed 60–80% of Zero Mail's ICP (founders + fractional execs + multi-employer professionals) has 2–7 Google accounts; restricting to 1 would lock out the highest-LTV segment.
- **D-06:** **Default-on-connect role assignment.** When a user clicks "Connect Google Calendar" on mailbox X and completes OAuth:
  - The connection's **primary calendar** is auto-assigned all three roles (`freebusy`, `event_write`, `brief_source`) for **mailbox X only** — never for other mailboxes in the workspace, even if the same user owns them.
  - Other sub-calendars exposed by the connection get `is_enabled=true` (so the user sees them in the picker) but **no preference rows** until the user explicitly tags them.
  - If the user connects a second Google account, the logic repeats for that connection's primary calendar against whichever mailbox is active at connect time.
  - The connect flow surfaces a one-line disclosure ("We'll use Primary for free/busy, event creation, and brief source on `mailbox@x.com` — change anytime in mailbox settings").
- **D-07:** **Edit UX = per-mailbox settings page.** Route: `/settings/mailboxes/[mailboxId]/calendar`. Layout mirrors Inbox Zero's `CalendarConnections.tsx` shell (empty-state Card with value props + Connect CTA → stacked Card grid per connection, dropdown disconnect, collapsible sub-calendar list with `is_enabled` toggles). Beneath the IZ-style connection cards, a dedicated **role-assignment section** lists the three roles (`freebusy` multi-select, `event_write` single-select, `brief_source` single-select), each showing only `is_enabled=true` calendars across all the mailbox's connected accounts. Calendly-style "Check for conflicts" + "Add events to" framing. **Raw shadcn primitives only** — Card, Collapsible, Select, MultiSelect, Switch — no wrapper components.
- **D-08:** **Role-tag is runtime authority.** `is_enabled=true` on a calendar makes it **eligible** for a preference row, but never grants any role implicitly. Phase 13's `UnifiedAvailabilityService` query is `INNER JOIN mailbox_calendar_preference WHERE role='freebusy'` — no `is_enabled` fallback path. If a mailbox has no `freebusy` preference rows, Phase 13 returns an empty-state coaching card pointing to `/settings/mailboxes/[id]/calendar`. This is the **only** semantics consistent with CAL-CONN-07's stated rationale ("prevent personal-availability leakage into work-mailbox drafts").

### Calendar-aware Gmail triage (CAL-TRIAGE-01..04)

- **D-09:** **CAL-TRIAGE-03 revised** during this discussion. **Dropped the `CalendarAwareGuard` backend-downgrade design** in favor of Inbox Zero's proven pattern (`utils/parse/calender-event.ts` + `utils/ai/choose-rule/match-rules.ts:201`):
  - Seed every new tenant (via the existing `GoogleOAuthSuccessHandler` → `materializeDefaultRulesEnabled` path) with a default rule typed `SystemType=CALENDAR`, action `label "Calendar"`.
  - Detect calendar invites via `isCalendarInvite()`: `.ics` attachment OR `mimeType=text/calendar` OR `BEGIN:VCALENDAR` body marker (boolean — simpler than full RFC 5546 METHOD parse).
  - Rule engine: when `rule.systemType === CALENDAR && isCalendarInvite(message)` → push a `PRESET + CALENDAR` match before any AI matching. User rules still evaluate normally — **no downgrade, no audit reason, no badge UI**.
  - Users own the seeded rule like any other: disable, edit, delete — full authority. **Trade-off accepted:** if a user writes "archive `noreply@*`", invites from `noreply@google.com` will be archived. The product trusts the user's rule authoring + provides the seeded label rule as the default protection layer.
  - Existing CAL-TRIAGE-03 wording in REQUIREMENTS.md has been updated 2026-06-20 with cross-reference to this CONTEXT.
- **D-10:** **`text/calendar` MIME classification (CAL-TRIAGE-01)** still uses **ical4j** parsing for **METHOD + DTSTART extraction only** — needed by CAL-TRIAGE-02's pinning badges ("Cancellation" via `METHOD:CANCEL`, "Time changed" via `METHOD:REQUEST` on a previously-known event, etc.). Library choice: `org.mnode.ical4j:ical4j` (~700KB, RFC 5546-compliant, folded-line-safe, charset-safe). Regex-only and `biweekly` rejected: regex breaks on RFC 5545 §3.1 folded lines that Outlook/Apple emit; biweekly is staler (~2023). Parser runs in `backend/worker` on `MailMessageObserved` AFTER_COMMIT — does NOT block Pub/Sub ingestion latency.
- **D-11:** **Persistence shape (CAL-TRIAGE-01).** Add two metadata columns to the existing inbox-projection row: `message_class enum('INVITE','CANCEL','RESCHEDULE','RSVP')` (nullable, NULL = not a calendar message) + `event_dt timestamptz` (nullable, populated from `DTSTART`). **No** new long-term body storage; raw iCal text is parsed in memory and discarded. ARCH-02 invariant preserved (no prompts, no completions, no body).
- **D-12:** **Pin mechanism (CAL-TRIAGE-02).** Read-side: `InboxProjectionReadService` computes a derived `pin_until = event_dt + 24h` on the fly when `message_class IS NOT NULL`; ORDER BY treats `(message_class IS NOT NULL AND now() < pin_until) DESC, server_timestamp DESC`. **No** new Liquibase changeset for a `pinned_until` column — the 24h cutoff is derivable, and adding a column would need a backfill on the existing v1.3 projection table. UI side: render a small "Cancellation" or "Time changed" badge next to pinned messages via the existing message-row component (raw shadcn `Badge` `outline` variant).
- **D-13:** **`is_enabled` picker filter constraint.** In the role-assignment UI (D-07), the role picker dropdowns list only `is_enabled=true` calendars. Toggling a calendar off automatically removes any preference rows referencing it (cascade DELETE on `mailbox_calendar_preference.calendar_connection_id` is the schema reality, but per-calendar disable is logically the same — handled at the UI/service layer).

### Disconnect cascade

- **D-14:** **Synchronous cascade-revoke on disconnect.** When the user disconnects a calendar connection:
  - Transaction 1 (sync): mark `calendar_connection.status = DISCONNECTED`, delete `mailbox_calendar_preference` rows, null-out `booking_link.destination_calendar_id` if applicable, retain `triage_audit` rows for compliance.
  - Modulith event `CalendarConnectionDisconnected` published AFTER_COMMIT → Phase 13's free/busy Redis cache eviction listens.
  - User sees the connection card transition to "Disconnected" badge state immediately, no spinner / no async pending state.

### Claude's Discretion

- Liquibase changelog file naming + ordering (use repo convention from existing v1.3 changesets under `backend/core/src/main/resources/db/changelog/changes/`).
- Exact Spring `ClientRegistration` bean naming + `OAuth2AuthorizationRequestResolver` customizer placement — follow the existing pattern in `core.gmail` for the second `ClientRegistration` that shares the same Google client-id.
- DTO record shapes for `apps/web` — emit via the existing `springdoc-openapi` → `openapi-typescript` codegen pipeline; no hand-written mirror types in feature folders.
- Generalization of `RefreshTokenCipher` → `OAuthTokenStore`: keep the AES-GCM crypto class identical (single source of truth), parameterize the storage row identifier so the same cipher serves both `gmail_connection.refresh_token_encrypted` and `calendar_connection.refresh_token_encrypted`. No new key, no new envelope.
- Default seeded `SystemType=CALENDAR` rule's exact label text (`"Calendar"` recommended for English UI; project's existing default-rules seeding script in `GoogleOAuthSuccessHandler` is bilingual VN/EN — match its convention).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 12 source-of-truth requirements + roadmap
- `.planning/REQUIREMENTS.md` §"OAuth Infrastructure" (INFRA-01), §"Calendar Connection Foundation" (CAL-CONN-01..08), §"Calendar-Aware Triage" (CAL-TRIAGE-01..04) — 13 locked requirements, revised CAL-TRIAGE-03 wording dated 2026-06-20.
- `.planning/ROADMAP.md` §"v1.4 Phase 12" — phase goal, dependencies (v1.3 mailbox foundation), success criteria.
- `.planning/PROJECT.md` — current milestone v1.4 scope + project constraints.

### Privacy, architecture, code-style (project-wide invariants)
- `CLAUDE.md` "Privacy" section — ARCH-02 ban (no embeddings of user mail / attachment content / chat-extracted email body), `draft_body` carve-out, logging format. The `text/calendar` parser must not log invite bodies; `triage_audit` rows must not include extracted iCal text beyond enum class + event timestamp.
- `CLAUDE.md` "Backend Code Style" — explicit naming, no opaque abbreviations.
- `CONVENTIONS.md` — service-owned `@Transactional`, records-for-DTOs, OpenAPI schema discipline, Liquibase changelog discipline.

### v1.4 research synthesis
- `.planning/research/STACK.md` — Spring AI 2.0.0 GA, Google API client versions.
- `.planning/research/ARCHITECTURE.md` — `CalendarReadGateway` boundary plan (Phase 13), workspace-shared/mailbox-isolated invariant.
- `.planning/research/PITFALLS.md` — known OAuth + Picker pitfalls.
- `.planning/research/SPRING-AI-2.0-MIGRATION.md` — Spring AI GA pin confirmation (eb19ecbc); no migration debt for Phase 12.

### v1.3 prior phase context (workspace-shared / mailbox-isolated foundation)
- `.planning/milestones/v1.3-phases/10-gmail-mailbox-foundation-and-account-management/10-CONTEXT.md` — `GmailConnectionService`, `MailboxRef`, ownership seam, ScopedValue + servlet filter.
- `.planning/milestones/v1.3-phases/11-mailbox-scoped-ingestion-automation-ui-and-verification/11-CONTEXT.md` — Pub/Sub mailbox resolution, mailbox-scoped rules + outbound, `MailboxContext`.

### Inbox Zero reference repo (local clone)
- `D:\study-materials-summer-2026\EXE202\inbox-zero\apps\web\prisma\schema.prisma` — `CalendarConnection` + `Calendar` Prisma models (~L1135–L1195); reference shape for `calendar_connection` + per-calendar `is_enabled`.
- `D:\study-materials-summer-2026\EXE202\inbox-zero\apps\web\utils\parse\calender-event.ts` — `isCalendarInvite()` detection function (L281), `isCalendarInviteAttachment()` (L288), `hasICalendarContent()` (L303). This is the **canonical detection shape** Zero Mail mirrors per D-09.
- `D:\study-materials-summer-2026\EXE202\inbox-zero\apps\web\utils\ai\choose-rule\match-rules.ts` L201–213 — `SystemType.CALENDAR` PRESET match-before-AI logic. This is the **canonical rule-priority pattern** Zero Mail mirrors per D-09.
- `D:\study-materials-summer-2026\EXE202\inbox-zero\apps\web\utils\ai\choose-rule\run-rules.ts` L131–149, L311–341 — `ensureConversationRuleForAiCalendarMatch` thread-continuity helper (useful if Zero Mail conversation rules surface in Phase 13+).
- `D:\study-materials-summer-2026\EXE202\inbox-zero\apps\web\app\(app)\[emailAccountId]\calendars\page.tsx` + `CalendarConnections.tsx` + `CalendarConnectionCard.tsx` + `CalendarList.tsx` — IZ's settings-page layout shell; mirror the empty-state Card + stacked connection grid + collapsible sub-calendar list pattern per D-07.

### External (consult during planning; do not pre-pin)
- `spring-security-oauth2-client` ClientRegistration docs — used for incremental OAuth + scope set.
- `tngtech.archunit:archunit-junit5` — custom `ArchCondition` for INFRA-01 literal scanner (D-02).
- `org.mnode.ical4j:ical4j` v4.x — RFC 5546 METHOD parsing (D-10).
- RFC 5546 (iTIP) METHOD semantics + RFC 5545 §3.1 folded-line behavior.
- Google Calendar API push-notification + watch-channel docs (Phase 12 introduces watch for `calendarList.watch`; reuse Phase 10 push handler pattern).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `backend/core/src/main/java/com/zeromail/core/gmail/usecases/GmailConnectionService.java` — proven workspace-shared service shape; mirror for `CalendarConnectionService`.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/GmailConnectionEntity.java` — JPA entity shape with refresh-token-encrypted column; mirror for `CalendarConnectionEntity`.
- `backend/core/src/main/java/com/zeromail/core/gmail/persistence/crypto/RefreshTokenCipher.java` + `RefreshTokenCryptoConfig.java` — AES-GCM cipher; **generalize to `OAuthTokenStore`** so the same cipher serves Gmail and Calendar (D-14, Claude's Discretion).
- `backend/core/src/main/java/com/zeromail/core/gmail/gateway/GmailApiClientFactory.java` — connection→client builder pattern; mirror for `CalendarApiClientFactory` returning a `Calendar` (Google API client).
- `backend/api/src/main/java/com/zeromail/api/security/SecurityConfig.java` — primary filter chain (`@Order(50)` user-session chain). Phase 12 does **not** add a new chain — Calendar OAuth + settings endpoints live inside the existing user chain. Phase 14 (booking page) will add `@Order(40)` sessionless chain later.
- `backend/core/src/main/java/com/zeromail/core/inbox/usecases/InboxProjectionReadService.java` + `InboxProjectionMessage.java` — read-side projection that gets the new `message_class` + `event_dt` columns (D-11) and the derived `pin_until` ORDER BY (D-12).
- `apps/web/lib/api/schema.d.ts` — generated OpenAPI client types; regenerate via `pnpm --filter web run generate:api` after DTO additions.

### Established Patterns
- **Workspace-shared/mailbox-isolated invariant** — `calendar_connection` follows the v1.3 pattern: workspace owns it (`tenant_id` FK), mailbox interaction goes through `mailbox_calendar_preference (mailbox_id, calendar_connection_id, role)` per D-06/D-07/D-08.
- **Default-rule seeding** — `GoogleOAuthSuccessHandler` → `materializeDefaultRulesEnabled` already seeds 10 IZ-style rules on first login (memory `project_default_rules_seeded_first_login.md`). Add the new `SystemType=CALENDAR` rule to that list (D-09).
- **`@ApplicationModuleListener`** scope: in-process events INSIDE `backend/core` only. Phase 12 uses plain `@TransactionalEventListener(AFTER_COMMIT)` for the `CalendarConnectionDisconnected` event when listened from `backend/api`.
- **ArchUnit composite rules** — `AttachmentBytesNotPersistedRule` (planned Phase 15) sets the precedent for `OAuthScopeAllowListTest` (D-02).
- **OpenAPI codegen mandatory** — `apps/web/lib/api/schema.d.ts` is generated; never hand-edited (CLAUDE.md §11). DTO changes → boot backend → regen.
- **TanStack Query meta toasts** — new mutations pass `meta.successMessage` / `meta.errorMessage` rather than calling `toast.*` directly (CLAUDE.md §12).
- **Raw shadcn first** — no wrapper components for `Card`, `Select`, `Switch`, `Badge`, `Collapsible` (memory `feedback_raw_shadcn_first.md`).

### Integration Points
- **Gmail ingestion → message-class classification (D-10).** Insertion point: `backend/worker` consumer of `MailMessageObserved` (Spring Modulith event from `backend/core`). Add `CalendarMessageClassifier` service that runs after AFTER_COMMIT, parses `text/calendar` part if present via ical4j, writes `message_class + event_dt` to inbox projection.
- **Default-rule seeding (D-09).** Edit `GoogleOAuthSuccessHandler.materializeDefaultRulesEnabled` to append the `SystemType=CALENDAR` rule definition.
- **Rule engine PRESET match-before-AI (D-09).** Insertion point: existing match-rules service. Add early-return branch: `if (rule.systemType == CALENDAR && isCalendarInvite(message)) → push PRESET match`. Mirror IZ's `match-rules.ts:201` shape in Java.
- **Calendar OAuth `ClientRegistration` (D-03, CAL-CONN-02).** New bean in `backend/api/src/main/java/com/zeromail/api/security/` (or `core.oauth.calendar` if shared) with `include_granted_scopes=true` + `access_type=offline` + `prompt=consent` via `OAuth2AuthorizationRequestResolver` customizer. Reuses Google client-id from existing Gmail `ClientRegistration` (no new GCP OAuth client).
- **`/settings/mailboxes/[id]/calendar` page (D-07).** New Next.js route in `apps/web/app/(app)/settings/mailboxes/[mailboxId]/calendar/page.tsx`. Feature folder: `apps/web/features/calendar/`. Hooks: `useCalendarConnections`, `useToggleCalendar`, `useUpdateCalendarPreference`. TanStack Query keys: `features/calendar/query-keys.ts`. Mirror IZ's `CalendarConnections.tsx` + `CalendarConnectionCard.tsx` + `CalendarList.tsx` layout shell.

</code_context>

<specifics>
## Specific Ideas

- **"Làm giống Inbox Zero" — explicit directive from user 2026-06-20.** Three specific IZ patterns mirrored (without porting TypeScript code into the Java/Spring backend):
  1. `isCalendarInvite(.ics + text/calendar mime + BEGIN:VCALENDAR body)` detection shape (D-09).
  2. `SystemType.CALENDAR` PRESET rule that matches before AI (D-09).
  3. `CalendarConnections.tsx` settings-page layout: empty-state Card with value props → stacked Card grid per connection → collapsible sub-calendar Toggle list, dropdown disconnect (D-07).
- **NOT mirrored from IZ:** IZ's per-mailbox `CalendarConnection` model (we use workspace-shared per CAL-CONN-06); IZ's lack of role enum (we have `freebusy/event_write/brief_source` per CAL-CONN-07 — Calendly-pattern overlay on top of IZ shell).
- **Calendly-pattern overlay (D-07):** the per-role assignment section ("Check for conflicts" multi-select + "Add events to" single-select) is borrowed from Calendly, sitting beneath the IZ-style connection cards in the same `/settings/mailboxes/[id]/calendar` page.
- **Vietnamese-first UI copy** (user preference + existing default-rules seeding precedent). Default seeded rule label = `"Calendar"` in EN, match `materializeDefaultRulesEnabled` convention for VN/EN switch.

</specifics>

<deferred>
## Deferred Ideas

- **Per-message badge for guard interventions** — N/A because the guard itself was dropped (D-09). If a future product decision reintroduces backend rule-action overrides for any reason, revisit the badge surface (originally Vùng 4 Option B research).
- **Rule-create LLM warning** ("this rule may catch calendar invites") — deferred because the guard was dropped. Could resurface if telemetry shows users repeatedly archiving invites via overly-broad rules → backlog item for v1.5+.
- **`docs/oauth-scopes.md` generated human-readable ledger + CI freshness check** — Phase 15 trigger (D-04). Add a `generateOAuthScopesDoc` Gradle task + ArchUnit doc-freshness assertion when GCP restricted-scope verification timing demands it.
- **Per-seat / per-account billing for multi-account Calendar** — deferred to v1.5+ alongside broader monetization (D-05). Schema is shape-ready, only enforcement to add later.
- **Reverse / matrix views for `mailbox_calendar_preference`** — deferred (D-07). Phase 12 ships only the per-mailbox view; add per-calendar reverse view if multi-mailbox power users dominate telemetry.
- **`text/calendar` Schema.org JSON-LD + header-heuristic fallback detection** — deferred indefinitely (Vùng 3 Options C/D rejected during discussion). Acceptance: forwarded invites with stripped `.ics` parts are treated as plain emails, same as Gmail/Outlook/IZ.
- **Override flag** `rule.guardOverride` — N/A because the guard was dropped (D-09).
- **Phase 14 sessionless `@Order(40)` Spring Security chain** for public booking page — Phase 14 work, not Phase 12.

</deferred>

---

*Phase: 12-calendar-connection-triage-foundation*
*Context gathered: 2026-06-20*
