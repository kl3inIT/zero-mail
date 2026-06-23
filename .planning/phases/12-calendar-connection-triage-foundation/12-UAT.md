---
status: testing
phase: 12-calendar-connection-triage-foundation
source:
  - 12-W1-calendar-oauth-and-connection-bootstrap-SUMMARY.md
  - 12-W2-calendar-connection-service-and-cascade-SUMMARY.md
  - 12-W3-calendar-settings-frontend-SUMMARY.md
  - 12-W4-text-calendar-classification-and-pinning-SUMMARY.md
  - 12-W5-preset-calendar-rule-wiring-SUMMARY.md
  - 12-G1-cal-triage-02-badge-wiring-SUMMARY.md
  - 12-VERIFICATION.md (human_verification[])
started: 2026-06-24T00:00:00Z
updated: 2026-06-24T00:00:00Z
---

## Current Test

number: 4
name: Seeded Calendar rule fires via PRESET match — no LLM call
expected: |
  See Tests section. (Tests 1-3 blocked on the nav-entry gap — the calendar settings page
  has no sidebar/settings link and is unreachable from the UI without typing the URL.)
awaiting: user response

## Tests

### 1. Connect Google Calendar — minimal-scope consent
expected: |
  Click "Connect Google Calendar" on the mailbox calendar settings page. Google consent
  shows ONLY the 3 calendar scopes (freebusy / events / readonly) — no Gmail, no Drive.
  After granting, a CONNECTED card appears with provider email; primary calendar enrolled
  with FREEBUSY+EVENT_WRITE+BRIEF_SOURCE. Gmail refresh token row unchanged.
result: blocked
blocked_by: third-party
reported: "trang calendar bạn chưa cho lên router à / sidebar — calendar page unreachable; then connect 404'd to :3000; now blocked on GCP redirect_uri registration"
severity: blocker
resolution_progress: |
  Three findings surfaced while driving Test 1 live (Playwright, authenticated session):

  FINDING 1 (CODE — FIXED, commit 58fdb93a): nav gap. W3 built the route
  /settings/mailboxes/[mailboxId]/calendar + full features/calendar folder but wired NO
  navigation entry (same pattern as the hidden Needs-Reply feature). FIX: CalendarIntegrationCard
  on the /integrations hub (already in the sidebar) deep-linking to the active mailbox's calendar
  page. Verified end-to-end in browser: Sidebar → Tích hợp → section Lịch → "Quản lý lịch" →
  page loads. Also localized 2 calendar backend error codes W1/W2 left untranslated (i18n gate).

  FINDING 2 (CODE — FIXED, commit eecea827): "Kết nối Google Calendar" 404'd. The connect-intent
  backend returns a same-origin RELATIVE path (/oauth2/authorization/google-calendar);
  window.location.assign resolved it against the frontend origin, so in dev (:3000 vs :8080) it
  hit the frontend → 404. FIX: resolve via getApiUrl (same helper Gmail connect uses). Verified:
  button now redirects to :8080/oauth2/authorization/google-calendar and reaches Google OAuth.

  FINDING 3 (CONFIG/OPS — USER ACTION, NOT a code defect): Google returns redirect_uri_mismatch.
  Both registrations use Spring default redirect-uri "{baseUrl}/login/oauth2/code/{registrationId}":
    - google           → http://localhost:8080/login/oauth2/code/google           (registered in GCP ✓)
    - google-calendar  → http://localhost:8080/login/oauth2/code/google-calendar  (NOT registered ✗)
  The new google-calendar redirect URI was never added to the GCP OAuth client's Authorized
  redirect URIs. Test 1 (and 2/3) cannot complete until the user registers it in Google Cloud
  Console. Add BOTH the dev URI above AND the prod URI
  https://{prod-domain}/login/oauth2/code/google-calendar.
artifacts:
  - "apps/web/features/calendar/components/CalendarIntegrationCard.tsx (new — nav entry, FIXED)"
  - "apps/web/features/calendar/hooks/use-connect-calendar-intent.ts (getApiUrl resolution, FIXED)"
  - "Google Cloud Console OAuth client — Authorized redirect URIs (config, USER ACTION)"
missing:
  - "USER: register http://localhost:8080/login/oauth2/code/google-calendar (+ prod equivalent) in the GCP OAuth client"

### 2. Calendar invite pinned top-of-inbox + Cancellation/Time-changed badge
expected: |
  Send yourself a Google Calendar invite (METHOD:REQUEST) to the connected Gmail, wait for
  Pub/Sub ingestion, then open /inbox. The invite row is pinned at the TOP of inbox (even if
  not the most recently received), and shows a calendar badge. A cancellation (METHOD:CANCEL)
  shows a "Cancellation" badge; a reschedule shows a "Time changed" badge. The pin holds for
  a 24-hour window after the event date. (This is the G1-closed CAL-TRIAGE-02 badge — it was
  a dead branch before the schema regen.)
result: [pending]

### 3. Disconnect calendar — cascade revoke
expected: |
  On the calendar settings page, use the connection's menu → Disconnect. Within ~5s the card
  flips to a DISCONNECTED badge. In the DB, mailbox_calendar_preferences rows for that
  connection are GONE, but triage_audit rows for the tenant are retained. A mid-flight read
  against the disconnected calendar fails fast (does not silently return stale data).
result: [pending]

### 4. Seeded Calendar rule fires via PRESET match — no LLM call
expected: |
  With the default seeded "Calendar" rule enabled, a calendar-class message (invite) is
  processed. In /audit (or rule history), the rule shows as matched via PRESET
  (diagnostic = preset_calendar), and NO llm_call_audit row is written for that evaluation
  (the calendar match is deterministic, never hits the LLM). User-authored rules still retain
  full action authority — no backend downgrade.
result: [pending]

## Summary

total: 4
passed: 0
issues: 1
pending: 3
skipped: 0
blocked: 0

## Gaps

- truth: "User can reach the calendar settings page from the app UI (sidebar or settings nav) to connect/manage Google Calendar."
  status: failed
  reason: "User reported: trang calendar chưa lên router/sidebar — calendar settings route exists but has no navigation entry; unreachable without typing the URL."
  severity: blocker
  test: 1
  artifacts:
    - "apps/web/components/shell/AppSidebar.tsx"
    - "apps/web/app/(protected)/(app)/settings/SettingsClient.tsx"
  missing:
    - "Add a Calendar navigation entry routing to /settings/mailboxes/{activeMailboxId}/calendar"
    - "Handle the no-active-mailbox / not-connected state for the entry"
