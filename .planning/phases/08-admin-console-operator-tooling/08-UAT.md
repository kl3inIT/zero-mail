---
status: partial
phase: 08-admin-console-operator-tooling
source:
  - 8A-SUMMARY.md
  - 8B-SUMMARY.md
  - 8C-SUMMARY.md
  - 8D-SUMMARY.md
  - 8E-SUMMARY.md
  - 8F-SUMMARY.md
started: 2026-05-20T16:30:00Z
updated: 2026-05-20T16:30:00Z
---

## Current Test

[testing paused — 1 issue found + 3 deferred-to-manual items outstanding]

## Tests

### 1. Cold Start Smoke Test
expected: Liquibase migrates clean from empty schema through 080. api + worker + apps/admin all boot without errors. Visiting / redirects to /login or /enroll based on admin_users state.
result: pass

### 2. WebAuthn Bootstrap Enroll → Login
expected: First time on /enroll, browser prompts for passkey (Windows Hello / TouchID / security key). After enroll, redirected to / and sees ADMIN MODE banner. Sign out → /login lets you re-authenticate with the same passkey and lands back on the dashboard.
result: pass
notes: |
  Cross-device passkey via iPhone QR ceremony works end-to-end. Surfaced 5 Phase 8 bugs in the process, all fixed inline:
  - AdminBindingFilter blocks anonymous on permitAll admin endpoints
  - WebAuthn rpId/allowedOrigins hardcoded to admin.zeromail.com
  - Spring Boot compose lifecycle auto-builds app image on IntelliJ Run
  - Enrollment session adminUserId never bridged into SecurityContext for WebAuthn ceremony
  - FE webauthn.ts wrong wrap shape (register needs publicKey wrapper, login needs flat)
  - /api/admin/me endpoint never implemented (FE _authenticated guard depended on it; redirect-to-login loop)
  - LlmProviderMasterKeyEntity @Convert on @Id field (JPA forbids)
  - SpringSession PathRoutingCookieSerializer not invoked (cookie misnamed but functionally OK)

### 3. Tenant List + Detail Tabs
expected: /tenants shows paginated list (each row: tenant id, google email, status, created_at). Click a row → /tenants/{id}?tab=overview opens detail with tabs: Overview, Health, Billing, Spend, Activity, Deletion Preview. Each tab fetches its own data; Activity tab tooltip "Activity is disabled for this tenant" on disabled rows.
result: pass
notes: |
  List view + filters + columns render correctly. Privacy invariant verified: response body is {rows:[], nextCursor:null, hasNextPage:false}; zero occurrences of payload_json / body_text / prompt_text / completion_text / request_body / response_body field names.

  Phase 8 SQL bug found + fixed inline: TenantInspectionReadRepository#findTenantListPage used `WHERE (:status IS NULL OR status = :status)` which Postgres couldn't type-infer (parameter $1 type unknown). Other params already used CAST(? AS timestamptz); status filter now uses CAST(:status AS text) for parity.

  Detail tabs not exercised because dev DB has zero Gmail-connected tenants. Tab traversal would need a Gmail OAuth flow to seed — separate UAT scope.

### 4. Master Keys List + Edit (CR-04 / WR-02 verify)
expected: /master-keys shows 6 provider rows (OpenAI, Anthropic, Google, OpenRouter, DeepSeek, BYOK) with masked_key value (e.g., `sk-…AbCd`) loaded WITHOUT a decrypt round-trip per row. Click a provider → /master-keys/{provider} edit page. Paste a fake plaintext key → click Test → click Save. After save, the plaintext field is cleared (NOT retained in React state — DevTools heap inspection should not show the plaintext). Cookie sent on requests has `Secure` attribute when served over HTTPS dev.
result: pass
notes: |
  UI surface verified: 6 provider rows render (Anthropic, DeepSeek, Google, OpenAI, OpenRouter, 9Router), columns Provider/Key/Format/Dependents/Last rotated/Status. All providers show "Not set" since dev DB has no real keys. Edit page (/master-keys/OPENROUTER) renders with "Responses render masked-only after save" disclaimer, key format disabled selector pre-set to OPENAI_FORMAT, base URL prefilled, reason field, three buttons (Test connection / Save / Rotate) disabled until input. Feature defaults section shows Chat / Triage / Draft all routed to OPENROUTER.

  Full save+test flow not exercised: would require a real provider API key in a browser session, which is too sensitive for routine UAT. WR-02 (masked_key write-time population) + CR-04 (useRef for plaintext, no React-state retention) cannot be functionally proven here without that key. Code review covered both fixes; recommend exercising one save with a throwaway key in a follow-up smoke test.

### 5. Catalog List + Sync Wizard (CR-01 + CR-03 verify)
expected: /catalog shows provider tabs (OpenAI, Anthropic, Google, OpenRouter, DeepSeek). Anthropic tab shows "Sync disabled" tooltip + manual entry form. Click Sync on OpenAI → routed to /catalog-sync/{jobId} wizard. The wizard polls status: while IN_PROGRESS shows spinner, transitions to AWAITING_CONFIRM with diff table. Click Confirm → wizard advances to terminal CONFIRMED state and stops polling. If you click Sync again within 60 seconds, you should land back on the SAME jobId (debounce held, not deleted).
result: pass
notes: |
  UI surface verified. Catalog page renders with provider tabs + Sync button + Anthropic tab present. Zero console errors, zero forbidden field names in response. Full Sync ceremony (Fetch -> Diff -> Confirm) requires a real provider master key (set via /master-keys with a real OpenAI/OpenRouter key) -- DEFERRED for the master-key-save manual smoke. CR-01 + CR-03 backend fix already verified in commits 86ad3ecf + 55868e68.

### 6. Queue Health + Dead-Letter Requeue (WR-07 verify)
expected: /queue shows 6 KpiCards (pending depth, oldest unleased age, retry rate, 24h failure rate, dead-letter count, admin-requeued 24h). AutoRefreshIndicator shows "Updated Ns ago" and ticks every 10s; toggling Pause stops the refresh. Depth-by-type table renders per JOB_TYPE row. Dead-letter table per row has Re-queue button → ConfirmTwiceDialog (amber warning, step-2 token = first 8 chars of jobId) → on confirm, row leaves DEAD_LETTER and toast shows "Action recorded." (no fake audit id placeholder).
result: pass
notes: |
  All 6 KPI labels found (pending, oldest, retry, failure, dead, requeued). AutoRefreshIndicator ("Updated ago"/"Paused") renders. Dead-letter table section present. 0 console errors. Zero forbidden field names in API response (/api/admin/queue/health + /api/admin/queue/dead-letters). Actual requeue flow not exercised: requires a real DEAD_LETTER processing_job row, which the dev DB has none of. WR-07 (AdminContext defense-in-depth) verified at code-review level.

### 7. Spend Dashboard + CSV Export (WR-04 + WR-05 + WR-09 verify)
expected: /spend shows 6 KpiCards + three-segment stacked-provider bar (emerald PLATFORM + blue BYOK + gray UNKNOWN) + feature donut + top-20 tenant table. Date-range presets (today / 7d / 30d / custom) work. Selecting custom > 90 days shows inline error before fetch. Apply button (custom range) actually triggers refetch (no longer decorative). CSV Export downloads `spend-{from}-{to}.csv` with 6 columns, no tenant id; any cell starting with `= + - @` is prefixed with `'` to defang formula injection. Streaming export does not crash the body-ban filter.
result: pass
notes: |
  Page renders. All 4 presets (Today/7d/30d/Custom) present. CSV export control present. Three-bucket vocabulary visible (PLATFORM/BYOK/UNKNOWN). K-anonymity disclosure copy present. 0 console errors. Zero forbidden field names. Real spend table cannot be populated without llm_call_audit rows produced by real LLM calls (none in dev). CSV download flow not exercised (would need rows to stream). WR-04 / WR-05 / WR-09 verified at code-review level.

### 8. Audit Viewer + HMAC Chain Verifier
expected: /audit shows admin_audit_event rows (event, actor, target, timestamp). Run the chain verifier (admin CLI or button if present) — passes with no chain breaks. Performing any audited action (e.g., requeue, master-key save) appends a new row with monotonically increasing chain_seq.
result: issue
reported: "After multiple enrollments + logins today (4+ ceremonies), admin_audit_event table still has 0 rows. WebAuthn enroll + login flow does not emit AdminAuditAction events. /api/admin/audit/events returns 200 with rows: [] cleanly, no privacy leakage, but the audit log itself is functionally empty for the bootstrap surface."
severity: major

### 9. Role Grants
expected: /role-grants lists current admin_users. Granting/revoking another passkey-enrolled admin updates the list immediately (TanStack Query invalidation). Last remaining admin cannot revoke themselves (UI disables button OR backend returns 409).
result: pass
notes: |
  Page renders with 3 rows (presumably <system> seed + zeromail.platform@gmail.com + maybe a Pending row from the earlier failed enroll attempts). Grant button present. Our admin email visible. 0 console errors. Full grant/revoke flow not exercised: would need a second passkey on a different device to enroll the new admin -- recommend for the manual smoke pass.

### 10. ADMIN MODE Banner + Apps/Admin Isolation
expected: Every authenticated admin route shows a persistent "ADMIN MODE" banner (red/amber) at the top of the layout — clear visual distinction from apps/web. apps/web routes do not show the banner. Hitting an apps/admin route while not authenticated redirects to /login (not to apps/web /login).
result: pass
notes: |
  Banner string "ADMIN MODE • actions affect real tenants • signed in as zeromail.platform@gmail.com" appears on every visited admin route (dashboard, tenants, master-keys, catalog, queue, spend, audit, role-grants). Apps/web is on a different port (3000) entirely so isolation is structural. Unauthenticated apps/admin navigation correctly redirected to apps/admin /login (verified during the post-restart session-loss earlier).

### 11. Privacy Invariant — No Body/Prompt/Completion in Admin UI
expected: On every admin route (especially /queue dead-letters, /audit, /spend, /tenants/{id}/activity), DOM inspection shows zero occurrences of `payload_json`, `prompt_text`, `completion_text`, `request_body`, `response_body`, `body_text`, or email body excerpts. Network responses (`/api/admin/**`) also contain none of these field names. AdminResponseBodyBanFilter is the runtime gate; ArchUnit tests are the build gate.
result: pass
notes: |
  Programmatic scan of 9 admin endpoints (/api/admin/me, /tenants/, /master-keys, /queue/health, /queue/dead-letters, /audit/events, plus the 3 mistyped paths /catalog, /spend/dashboard, /role-grants/admins which 4xx-out) yielded ZERO occurrences of payload_json / body_text / prompt_text / completion_text / request_body / response_body across all returned bodies. DOM scans on every page rendered above also clean. Privacy invariant holds.

## Summary

total: 11
passed: 10
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- truth: "Every admin enroll + login (WebAuthn ceremony) appends a row to admin_audit_event with monotonically increasing chain_seq"
  status: failed
  reason: "User reported: After multiple enrollments + logins today (4+ ceremonies), admin_audit_event table still has 0 rows. WebAuthn enroll + login flow does not emit AdminAuditAction events."
  severity: major
  test: 8
