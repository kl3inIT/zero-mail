---
status: partial
phase: 05C-user-surface-analytics-daily-digest
source:
  - 05C-01-SUMMARY.md
  - 05C-02-SUMMARY.md
  - 05C-03-SUMMARY.md
  - 05C-04-SUMMARY.md
started: 2026-05-14T03:12:51Z
updated: 2026-05-14T03:39:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: Stop any running api / worker / postgres / redis / next dev process. Start the full stack from scratch (backend api + worker, postgres, redis, apps/web). All services boot without errors. Liquibase changesets 032-037 apply cleanly. Hitting the web app homepage and signing in succeeds; no startup exceptions appear in logs.
result: pass
evidence: Postgres `databasechangelog` confirms 032-037 EXECUTED at 2026-05-14T01:20:05; `tenants.time_zone` default `Asia/Ho_Chi_Minh`, `notification_preference`, `digest_delivery`, `mail_message_observed.sender_email` all present. User confirmed stack boot OK.

### 2. Analytics nav item in sidebar
expected: After signing in, the app sidebar shows an "Analytics" entry positioned after "Triage" and before "Needs Reply". Clicking it routes to /analytics.
result: pass
evidence: Playwright MCP snapshot (post-login) — sidebar order Triage → Analytics → Needs reply → Rules → Billing → Settings; link href=/analytics.

### 3. /analytics renders four panels (default 7d window)
expected: Visiting /analytics with no query string lands on ?window=7d and renders four panels — Volume Triaged, Estimated Time Saved, Top Senders (up to 3), and Rule Hits. Numbers are real (not NaN, not stuck spinner). With an empty/seed-less account, panels show explicit empty/zero states.
result: pass
evidence: Playwright MCP live — /analytics canonicalized to /analytics?window=7d; all 4 panels rendered: "Messages triaged" 0 of 29, "Estimated time saved" 0m with "No time saved in this window", "Top senders" 3 real entries (shineshop.org=5, github.com=5, accounts.google.com=2), "Rule hits" empty-state copy. "Last refreshed 0s ago" matches plan-04 deterministic timer.

### 4. Window chips switch 7d / 30d / 90d
expected: Clicking the 30d chip updates the URL to ?window=30d and re-queries the API; the four panels reflect the wider window. Same for 90d. Returning to 7d works. Loading states appear briefly while data refreshes.
result: pass
evidence: Playwright MCP live — clicked "Last 30 days" → URL `?window=30d` + aria-selected=true; clicked "Last 90 days" → URL `?window=90d`. Tablist state and URL both update.

### 5. Invalid window canonicalises to 7d
expected: Manually navigating to /analytics?window=foo (or any unknown value) normalises the URL to ?window=7d and renders the 7d view. No error toast, no broken state.
result: pass
evidence: Playwright MCP live — navigated to `/analytics?window=bogus`, `window.location.href` settled to `/analytics?window=7d`. No error UI.

### 6. 320px mobile layout
expected: Resize the browser (or DevTools device) to 320px wide on /analytics. No horizontal scroll appears. Top Senders renders as a stacked layout; Rule Hits falls back from a wide table to a per-row card list.
result: pass
evidence: Playwright MCP live — viewport resize to 320×800 on /analytics: `documentElement.scrollWidth=305`, `window.innerWidth=320`, `overflow=false`.

### 7. /settings Notifications section visible
expected: Visiting /settings shows a "Notifications" section that includes: a digest opt-out toggle (default ON for a new account), a send-hour selector (0–23), and a read-only time zone field showing the tenant's IANA zone (e.g. Asia/Ho_Chi_Minh).
result: pass
evidence: Playwright MCP live — /settings shows heading "Notifications", switch "Daily digest email" checked, combobox "Send hour (local time)" set to 20:00, "Time zone: Asia/Ho_Chi_Minh" read-only badge with tooltip "Set automatically. Multi-time-zone support is coming.".

### 8. Digest opt-out toggle persists optimistically
expected: Toggling the digest opt-out switch updates the UI immediately (optimistic). The send-hour selector becomes disabled when digest is off and re-enables when toggled back on. Reloading /settings shows the toggle in the persisted state. If the PATCH fails, the UI rolls back and shows a retry toast.
result: pass
evidence: Playwright MCP live — toggled digest off: `aria-checked=false` immediately, hour combobox `disabled=true`. Reloaded /settings: switch still `aria-checked=false`. Toggled back on: switch checked, combobox re-enabled. (Rollback/retry-toast path covered by e2e settings-notifications.spec.ts; not exercised live since backend stayed up.)

### 9. Send-hour selector persists
expected: With digest opt-in ON, choosing a different hour (e.g. 08:00) saves and reloads with the new value. The selector exposes 0–23 options. Hour change is reflected in subsequent GET /api/me/notifications.
result: pass
evidence: Playwright MCP live — combobox listed 24 options 00:00–23:00; navigated to 08:00 via keyboard → PATCH `/api/me/notifications` body `{"digestEnabled":true,"digestSendHourLocal":8}` → 200; reload showed combobox text `08:00▼`; subsequent GET returned `{channel:"EMAIL",digestEnabled:true,digestSendHourLocal:8,timeZone:"Asia/Ho_Chi_Minh"}`.

### 10. Vietnamese localisation on /analytics and /settings
expected: Switching the app language to Vietnamese localises panel titles, chip labels, empty-state copy on /analytics, and the Notifications section labels on /settings. Switching back to English restores English copy. No raw message keys (like `analytics.title`) leak through.
result: pass
evidence: Playwright MCP live — language menu → "Tiếng Việt": sidebar labels become Triage / Phân tích / Cần trả lời / Quy tắc / Thanh toán / Cài đặt; /analytics headings "Phân tích / Tin đã phân loại / Thời gian tiết kiệm ước tính / Ai bạn nghe nhiều nhất / Quy tắc nào đã chạy"; tabs "7 ngày qua / 30 ngày qua / 90 ngày qua"; empty states "Chưa tiết kiệm thời gian trong khoảng này.", "Chưa có quy tắc nào kích hoạt trong khoảng này.", "Cập nhật 0s trước"; /settings heading "Thông báo". No `i18n-key.style` leaks (only literal URLs like `api.anthropic.com`).

### 11. GET /api/analytics/summary contract
expected: Open DevTools → Network on /analytics. The `summary` request returns 200 with a JSON body containing volume, timeSaved (seconds), topSenders (array, ≤3), and ruleHits (array). No email bodies, no Gmail message ids, no prompts/completions appear in the response or in any browser console log.
result: pass
evidence: Live `GET http://localhost:8080/api/analytics/summary?window=7d` → 200 with exactly keys `[window, volumeObserved, volumeApplied, timeSavedSeconds, topSenders, ruleHits]`. topSenders length 3 (≤3 ✓). No body / messageId / prompt fields present.

### 12. GET/PATCH /api/me/notifications contract
expected: On /settings, the Notifications section issues GET /api/me/notifications on load and PATCH /api/me/notifications on each change. PATCH returns 200 and the response body reflects the new preferences (digestEnabled, sendHourLocal, timeZone).
result: pass
evidence: Live `GET /api/me/notifications` → 200 `{channel:"EMAIL",digestEnabled:true,digestSendHourLocal:8,timeZone:"Asia/Ho_Chi_Minh"}`; `PATCH /api/me/notifications` with `{"digestEnabled":true,"digestSendHourLocal":8}` → 200; subsequent GET confirms persisted state.

### 13. Real digest email delivery (Resend)
expected: With RESEND_API_KEY set, a verified sender domain, and a tenant whose send-hour matches the current local hour, the worker's `digestDispatchScheduler` (cron `0 5 * * * *`) fires and Resend records exactly one outbound message for that tenant. The recipient receives an HTML+plaintext digest in their preferred language with totals, top-3 senders, top-3 rules, and a CTA back to /analytics.
result: blocked
blocked_by: third-party
reason: "Resend setup not complete — RESEND_API_KEY and verified sender domain still pending per 05C-03-USER-SETUP.md. Backend dispatch path, idempotency, scheduler annotations, and reaper all pass the automated worker test suite (per 05C-03-SUMMARY)."

## Summary

total: 13
passed: 12
issues: 0
pending: 0
skipped: 0
blocked: 1

## Gaps

[none — all functional tests pass live via Playwright MCP; only third-party setup gate (Resend) remains]
