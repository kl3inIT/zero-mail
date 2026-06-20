---
phase: 12
slug: calendar-connection-triage-foundation
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-20
approved: 2026-06-20
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Sourced from `12-RESEARCH.md` §Validation Architecture (line 1031). Status moves to `approved` after the planner finalizes per-task verify commands and Wave 0 stubs land green.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter 5.x via Boot 4.1.0 BOM) + Spring Boot Test + Testcontainers 1.21.3 + ArchUnit 1.4.2; Vitest 4 + Playwright 1.60 for `apps/web` |
| **Config file** | `build.gradle.kts` per subproject; `apps/web/vitest.config.ts`; `apps/web/playwright.config.ts` |
| **Quick run command** | `./gradlew :backend:core:test --tests "*Calendar*" :backend:api:test --tests "*Calendar*"` |
| **Full suite command** | `./gradlew test && pnpm --filter web run lint && pnpm --filter web test` |
| **Estimated runtime** | ~15s quick, ~3–5 min full |

---

## Sampling Rate

- **After every task commit:** Run quick run command (~15s)
- **After every plan wave:** Run full suite command
- **Before `/gsd-verify-work`:** Full suite must be green AND `apps/web` Playwright `e2e/calendar-settings.spec.ts` must pass against a dev backend (SSH tunnel to remote dev DB on `localhost:5555`, per memory `reference_dev_db_ssh_tunnel.md`)
- **Max feedback latency:** ~15s for backend slice; ~60s for Playwright e2e

---

## Per-Task Verification Map

> Filled by the planner during PLAN.md authoring. Each task carries a `<verify>` command that maps to one row here. The rows below are the Requirement-driven backstops the planner MUST cover; per-task rows are added as PLAN.md files are created.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD-w0-01 | W0 | 0 | INFRA-01 | T-12-01 (scope drift) | No `https://www.googleapis.com/auth/` literal outside `core.oauth.scope` | ArchUnit / source-text scan | `./gradlew :backend:core:test --tests "OAuthScopeAllowListTest"` | ❌ W0 | ⬜ pending |
| TBD-w0-02 | W0 | 0 | INFRA-01 | — | `GoogleOAuthScope` enum round-trip (no dupes, no stale) | unit | `./gradlew :backend:core:test --tests "GoogleOAuthScopeEnumTest"` | ❌ W0 | ⬜ pending |
| TBD-w1-01 | W1 | 1 | CAL-CONN-02 | T-12-06 (open-redirect on success) | Calendar `ClientRegistration` exposes only `calendar.freebusy` + `calendar.events` + `calendar.readonly`; shares Google client-id | Spring slice (`@SpringBootTest` partial) | `./gradlew :backend:api:test --tests "CalendarClientRegistrationConfigTest"` | ❌ W1 | ⬜ pending |
| TBD-w1-02 | W1 | 1 | CAL-CONN-03 | T-12-05 (cross-registration RT leak) | Refresh token round-trips via `OAuthTokenStore`; AAD preserved; Gmail row untouched | `@DataJpaTest` + cipher | `./gradlew :backend:core:test --tests "CalendarConnectionCipherTest"` | ❌ W1 | ⬜ pending |
| TBD-w1-03 | W1 | 1 | CAL-CONN-08 | T-12-02 (stale token after disconnect) | `DISCONNECTED` status → `CalendarDisconnectedException` from `CalendarApiClientFactory` | unit | `./gradlew :backend:core:test --tests "CalendarApiClientFactoryDisconnectTest"` | ❌ W1 | ⬜ pending |
| TBD-w2-01 | W2 | 2 | CAL-CONN-04 | T-12-01 (cross-tenant calendar leak) | List/disconnect cascade-delete preference rows + retain audit + publish `CalendarConnectionDisconnected` AFTER_COMMIT | `@SpringBootTest` | `./gradlew :backend:core:test --tests "CalendarConnectionServiceTest"` | ❌ W2 | ⬜ pending |
| TBD-w2-02 | W2 | 2 | CAL-CONN-05 | — | `calendarList.list()` → `calendars` rows; primary flag preserved | `@SpringBootTest` with stubbed `Calendar.Builder` | `./gradlew :backend:core:test --tests "CalendarSnapshotIngestionServiceTest"` | ❌ W2 | ⬜ pending |
| TBD-w2-03 | W2 | 2 | CAL-CONN-06 | — | `calendar_connection` has NO `gmail_connection_id` FK | schema-introspection test | `./gradlew :backend:core:test --tests "CalendarSchemaIsolationTest"` | ❌ W2 | ⬜ pending |
| TBD-w2-04 | W2 | 2 | CAL-CONN-07 | — | `mailbox_calendar_preference` accepts only `freebusy`/`event_write`/`brief_source` roles | `@DataJpaTest` constraint | `./gradlew :backend:core:test --tests "MailboxCalendarPreferenceConstraintTest"` | ❌ W2 | ⬜ pending |
| TBD-w3-01 | W3 | 3 | CAL-CONN-01 | — | `/settings/mailboxes/[id]/calendar` page renders empty state + connection cards + role-assignment section | Playwright e2e | `pnpm --filter web exec playwright test e2e/calendar-settings.spec.ts` | ❌ W3 | ⬜ pending |
| TBD-w4-01 | W4 | 4 | CAL-TRIAGE-01 | T-12-04 (iCal injection) | ical4j parse: `INVITE` / `CANCEL` / `RSVP` across IZ fixtures; size-bound 1 MB; XXE disabled | unit | `./gradlew :backend:worker:test --tests "CalendarPartParserTest"` | ❌ W4 | ⬜ pending |
| TBD-w4-02 | W4 | 4 | CAL-TRIAGE-02 | — | Pinned ORDER BY surfaces calendar messages on top for 24h post-event | `@DataJpaTest` projection slice + keyset cursor sanity | `./gradlew :backend:core:test --tests "InboxProjectionPinningTest"` | ❌ W4 | ⬜ pending |
| TBD-w4-03 | W4 | 4 | CAL-TRIAGE-04 | — | Classifier runs without Calendar OAuth — `CalendarApiClientFactory` never invoked | `@SpringBootTest` worker slice with mocked factory | `./gradlew :backend:worker:test --tests "CalendarMessageClassifierNoOAuthTest"` | ❌ W4 | ⬜ pending |
| TBD-w5-01 | W5 | 5 | CAL-TRIAGE-03 | — | `PRESET_CALENDAR` matches when `messageClass != null` before AI; existing user rule still labels normally | `RuleEvaluatorTest` | `./gradlew :backend:core:test --tests "RuleEvaluatorCalendarPresetTest"` | ❌ W5 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/core/src/test/java/com/zeromail/core/oauth/scope/OAuthScopeAllowListTest.java` — source-text scan for `https://www.googleapis.com/auth/` literals outside `core/oauth/scope/` (covers INFRA-01)
- [ ] `backend/core/src/test/java/com/zeromail/core/oauth/scope/GoogleOAuthScopeEnumTest.java` — round-trip enum value tests (no duplicates, no stale entries)
- [ ] `backend/core/src/test/java/com/zeromail/core/calendar/...` — slice fixtures for `CalendarConnectionEntity`, `MailboxCalendarPreferenceEntity`
- [ ] `backend/worker/src/test/java/com/zeromail/worker/triage/CalendarPartParserTest.java` — ical4j fixtures (request/cancel/reply samples — translate Inbox Zero's `calender-event.test.ts` fixtures to `.ics` files in `src/test/resources/ical/`)
- [ ] `backend/api/src/test/java/com/zeromail/api/controllers/calendar/CalendarConnectionControllerTest.java` — `@WebMvcTest` with mocked services
- [ ] `apps/web/__tests__/calendar/...` — feature tests for hooks (`useCalendarConnections`, `useToggleCalendar`, `useUpdateCalendarPreference`)
- [ ] `apps/web/e2e/calendar-settings.spec.ts` — Playwright e2e on `/settings/mailboxes/[id]/calendar`

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| End-to-end Google OAuth handshake on a real Google account | CAL-CONN-02 | Cannot test live Google consent screen in CI; requires human-clickable browser flow | Boot backend with SSH tunnel; click "Connect Google Calendar" on `/settings/mailboxes/[id]/calendar`; complete consent; confirm card transitions to CONNECTED + primary calendar auto-assigned three roles |
| Disconnect cascade observed in Google Account permissions list | CAL-CONN-04 | Google's token revocation endpoint is not safe to hit in CI test mode | After disconnect in UI, open `https://myaccount.google.com/permissions` and confirm Zero Mail Calendar grant is revoked |
| `prompt=consent` re-triggers if user revoked Calendar but kept Gmail | CAL-CONN-02 | Live Google consent UX | Manually revoke Calendar from Google Account permissions; re-click "Connect Google Calendar"; confirm consent screen reappears |
| Pinned-message UI badge ("Cancellation" / "Time changed") | CAL-TRIAGE-02 | Visual rendering on inbox row | Send self a Google Calendar invite, then cancel it; confirm pinned-top + "Cancellation" badge appears |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (7 Wave 0 stubs listed above)
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s for any single test slice
- [ ] `nyquist_compliant: true` set in frontmatter after planner finalizes per-task rows

**Approval:** pending — finalized during gsd-planner PLAN.md authoring.
