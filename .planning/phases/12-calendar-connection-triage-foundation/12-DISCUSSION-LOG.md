# Phase 12: Calendar Connection + Triage Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-20
**Phase:** 12-calendar-connection-triage-foundation
**Areas discussed:** Scope ledger + ArchUnit mechanism (INFRA-01), mailbox_calendar_preference setup UX, text/calendar detection strictness, CalendarAwareGuard user-facing surface
**Inter-area decision:** Multi-account vs single-account (driven by user mid-discussion)

---

## Scope ledger schema + ArchUnit mechanism (INFRA-01)

Research run via gsd-advisor-researcher subagent (full_maturity tier, 5 options table).

| Option | Description | Selected |
|--------|-------------|----------|
| A. Java enum + ArchUnit literal scanner | Code-first source of truth. Enum `GoogleOAuthScope`. ArchUnit fails CI on raw scope literals outside the enum package. Phase 15 `drive.file` enforced by simple omission. | ✓ |
| B. YAML ledger + ArchUnit parses YAML | Human-editable `oauth-scopes.yml`. Security/compliance can PR YAML directly. Dual source of truth. | |
| C. Hybrid: enum + generated MD doc + CI freshness check | Best for compliance review (GCP restricted-scope verification). Most ceremony — Gradle task + CI doc-freshness check. | |
| D. Markdown ledger + ArchUnit MD-table parser | Lowest-ceremony human doc. MD parsing brittle — rejected for security-critical ledger. | |
| E. Sealed interface + `@ApprovedScope` annotation | Strongest typing for multi-provider scope catalogs. Overkill for Google-only v1.4. | |

**User's choice:** A
**Notes:** User accepted promotion path to C in Phase 15 when GCP restricted-scope verification timing makes a generated PDF ledger necessary. Doc generation deferred until then. Captured as D-04 in CONTEXT.

---

## mailbox_calendar_preference setup UX

Research run via gsd-advisor-researcher (compared with Calendly, Reclaim.ai, Google Workspace delegate-calendar, inbox-zero local reference). User detoured into Inbox Zero precedent inspection before deciding.

### Q2.1 — Default-on-connect behavior

| Option | Description | Selected |
|--------|-------------|----------|
| A. Auto-assign primary cal to all 3 roles for active mailbox only | Zero-friction first-run; Phase 13 free/busy works immediately; matches Calendly/Reclaim "primary is default" convention; scope-limited so no cross-mailbox surprise. | ✓ |
| B. Auto-assign primary to all roles for ALL tenant mailboxes | Maximum onboarding velocity. Violates privacy boundary schema exists to enforce. Rejected. | |
| C. No auto-default — force explicit picker | Most explicit privacy posture. Highest onboarding friction; users skipping picker hit Phase 13 "no slots" empty state. | |

### Q2.2 — Edit UX placement

| Option | Description | Selected |
|--------|-------------|----------|
| A. Per-mailbox settings page `/settings/mailboxes/[id]/calendar` | Mental model = "this mailbox uses these calendars". Matches Calendly + inbox-zero `[emailAccountId]/calendars` route. Raw shadcn primitives. | ✓ |
| B. Per-calendar reverse view | Power-user friendly for 1-calendar-N-mailbox case. Inverts the natural question. Defer to secondary view. | |
| C. Matrix grid (rows=mailbox, cols=role) | Single screen for all preferences. Vỡ trên mobile; freebusy multi-select breaks cell-dropdown mental model. | |
| D. Per-role settings card | Surfaces role taxonomy. Scale kém khi nhiều mailbox. | |

### Q2.3 — `is_enabled` vs role-tag semantics

| Option | Description | Selected |
|--------|-------------|----------|
| A. Role-tag is runtime authority. `is_enabled=true` alone does NOT grant any role. | Strictest privacy posture. Phase 13 query = INNER JOIN preference rows. Requires empty-state coaching in Phase 13. | ✓ |
| B. `is_enabled` implies freebusy fallback when no preference row exists | No "no slots" empty state. Breaks privacy boundary — exactly the leak schema was designed to prevent. Rejected. | |
| C. (Paired with A) `is_enabled=true` constraint in picker — role picker dropdown only lists enabled calendars | Clear two-step model: enable to make eligible, role-tag to actually use. | ✓ paired |

**User's choice:** 2.1=A, 2.2=A, 2.3=A (with C as picker filter pairing).
**Notes:** User asked to check Inbox Zero precedent first. IZ's simpler model (per-mailbox `CalendarConnection`, no role enum, all enabled calendars participate) was rejected because v1.4 schema is workspace-shared and needs role enum for cross-mailbox privacy boundary. UI shell (D-07) mirrors IZ's `CalendarConnections.tsx` + `CalendarConnectionCard.tsx` + `CalendarList.tsx` layout pattern but adds a Calendly-style role-assignment section beneath. Captured as D-06, D-07, D-08 in CONTEXT.

---

## Inter-area decision — Multi-account vs single-account

User mid-discussion asked to research whether real users actually use multiple calendars / Google accounts. Spawned dedicated research agent (general-purpose).

| Option | Description | Selected |
|--------|-------------|----------|
| A. Multi-account ngay theo ROADMAP (locked behavior) | N Google accounts free in v1.4. May gate paid later. Matches Cal.com "free" approach. | ✓ |
| B. Multi-account-shaped schema + enforce 1 account v1.4 (research rec) | Schema ready for multi-account, app-layer enforces 1 in v1.4. Zero data migration to v1.5 unlock. Inbox Zero + cal.com proven path. | |
| C. Hard restrict to 1 account (collapse fields onto User) | Maximum simplification. Migration pain when reversing. Rejected. | |

**User's choice:** A
**Notes:** User accepted the implementation complexity (N watch channels, free/busy aggregation cache, per-credential token refresh state machine) to ship multi-account from day one rather than constrain at app layer. Research evidence: 76% knowledge workers manage multi-calendar; fractional execs sit at 4–7 accounts; Inbox Zero `User → EmailAccount[] → CalendarConnection[]` schema is the closest competitive reference. Captured as D-05 in CONTEXT.

---

## text/calendar detection strictness

Research run via gsd-advisor-researcher (4 options + parser sub-decision).

| Option | Description | Selected |
|--------|-------------|----------|
| A. STRICT (MIME part only) | Zero false-positives, no library dep. INVITE/CANCEL/RSVP undifferentiated. | |
| B. MIME + iCal METHOD (RFC 5546) | Match đúng cách Gmail web + Outlook tự classify. Map sạch sang 4 class downstream. False-pos cực thấp. | ✓ |
| C. MIME + METHOD + JSON-LD Schema.org | False-pos cao trên order receipts. Rejected. | |
| D. FUZZY (header heuristics) | Marketing email từ `*@google.com` cũng match. Rejected. | |

**Sub-decision — METHOD parser:**

| Sub | Parser | Selected |
|-----|--------|----------|
| B1. ical4j | Mature 833★, v4.2.5, RFC 5546 full. Folded-line safe. +700KB jar. | ✓ |
| B2. biweekly | Lighter API. Last active ~2023. | |
| B3. Regex-only | Zero dep. Folded-line edge case risk (RFC 5545 §3.1). | |

**User's choice:** B + B1 (ical4j)
**Notes:** Parser scope deliberately narrow — only `Calendar.getMethod()` + `VEvent.getStartDate()`, no VEVENT persistence. Runs in `backend/worker` AFTER_COMMIT — does not block Pub/Sub ingestion latency. ARCH-02 invariant preserved. Captured as D-10, D-11 in CONTEXT.

---

## CalendarAwareGuard user-facing surface (revised mid-discussion)

Initial research presented 5 visibility options for the guard. User asked "what is this?" — explained guard concept. User then asked "how does Inbox Zero handle this?" — discovered IZ uses a fundamentally different pattern (seeded `SystemType=CALENDAR` rule + no backend guard). User chose to mirror IZ pattern, dropping the guard entirely.

| Option | Description | Selected |
|--------|-------------|----------|
| A. SILENT (audit only) | Backend protect, user never sees downgrade. Erodes trust when discovered. | |
| B. RULE-LIST BADGE | Aggregated badge on rule table. Click → trip history. Original recommendation. | |
| C. PER-MESSAGE BADGE | Badge on inbox message. Requires Zero Mail inbox reader UI (not v1.2 scope). | |
| D. RULE-CREATE WARNING | LLM warns at rule authoring time. Add LLM call to save path. Defer. | |
| E. COMPOUND (A+B+D+override flag) | Over-engineer before measuring detection precision. Defer. | |
| **F. Drop guard, follow IZ pattern — seed default `SystemType=CALENDAR` rule (label-only); rule engine PRESET-matches invites before AI; user rules retain full action authority** | New option emerged after IZ inspection. Simpler. Trades off backend protection for trust-the-user authoring. | ✓ |

**User's choice:** F
**Notes:** This revised CAL-TRIAGE-03 in REQUIREMENTS.md (edited inline 2026-06-20). Trade-off accepted: if a user writes "archive `noreply@*`", invites from `noreply@google.com` will be archived — user owns rule semantics, seeded `Calendar` label rule is the default protection layer. No `CalendarAwareGuard` class, no new audit reason, no badge UI. Captured as D-09 in CONTEXT.

---

## Claude's Discretion

The user explicitly left these to Claude (planner + executor):
- Liquibase changelog file naming + ordering — follow repo convention.
- Spring `ClientRegistration` bean naming + `OAuth2AuthorizationRequestResolver` customizer placement.
- DTO record shapes for `apps/web` — emit via existing OpenAPI codegen.
- Generalization of `RefreshTokenCipher` → `OAuthTokenStore` — AES-GCM crypto unchanged, parameterize storage row identifier.
- Default seeded `SystemType=CALENDAR` rule's exact label text (match `materializeDefaultRulesEnabled` VN/EN convention).

## Deferred Ideas

- Per-message badge + rule-create warning + override flag — all dropped because the guard was dropped (D-09). Resurface only if telemetry shows user-rule-over-broad invite archiving as a real problem.
- `docs/oauth-scopes.md` generated ledger + CI freshness check — Phase 15 trigger when GCP verification timing demands it.
- Per-seat / per-account billing for multi-account Calendar — v1.5+.
- Reverse / matrix views for `mailbox_calendar_preference` — Phase 12 ships per-mailbox view only; reconsider if multi-mailbox power users dominate.
- `text/calendar` JSON-LD + header-heuristic fallback detection — rejected indefinitely.
- Phase 14 sessionless `@Order(40)` Spring Security chain — Phase 14 work.
