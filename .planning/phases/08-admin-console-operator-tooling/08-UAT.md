---
status: testing
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

number: 3
name: Tenant List + Detail Tabs
expected: |
  Click sidebar "Tenants" hoặc nav vào /tenants → bảng list paginated. Mỗi row: tenant id, google email, status, created_at.

  Click 1 row bất kỳ → /tenants/{id}?tab=overview detail page. Top tabs: Overview, Health, Billing, Spend, Activity, Deletion Preview. Mỗi tab fetch data riêng (network có call /api/admin/tenants/{id}/{tab}).

  Activity tab: nếu tenant disabled, tooltip "Activity is disabled for this tenant".

  KIỂM CỨNG: không có field nào tên payload_json, body_text, prompt_text, completion_text trong network responses hoặc DOM.
awaiting: user response

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
result: [pending]

### 4. Master Keys List + Edit (CR-04 / WR-02 verify)
expected: /master-keys shows 6 provider rows (OpenAI, Anthropic, Google, OpenRouter, DeepSeek, BYOK) with masked_key value (e.g., `sk-…AbCd`) loaded WITHOUT a decrypt round-trip per row. Click a provider → /master-keys/{provider} edit page. Paste a fake plaintext key → click Test → click Save. After save, the plaintext field is cleared (NOT retained in React state — DevTools heap inspection should not show the plaintext). Cookie sent on requests has `Secure` attribute when served over HTTPS dev.
result: [pending]

### 5. Catalog List + Sync Wizard (CR-01 + CR-03 verify)
expected: /catalog shows provider tabs (OpenAI, Anthropic, Google, OpenRouter, DeepSeek). Anthropic tab shows "Sync disabled" tooltip + manual entry form. Click Sync on OpenAI → routed to /catalog-sync/{jobId} wizard. The wizard polls status: while IN_PROGRESS shows spinner, transitions to AWAITING_CONFIRM with diff table. Click Confirm → wizard advances to terminal CONFIRMED state and stops polling. If you click Sync again within 60 seconds, you should land back on the SAME jobId (debounce held, not deleted).
result: [pending]

### 6. Queue Health + Dead-Letter Requeue (WR-07 verify)
expected: /queue shows 6 KpiCards (pending depth, oldest unleased age, retry rate, 24h failure rate, dead-letter count, admin-requeued 24h). AutoRefreshIndicator shows "Updated Ns ago" and ticks every 10s; toggling Pause stops the refresh. Depth-by-type table renders per JOB_TYPE row. Dead-letter table per row has Re-queue button → ConfirmTwiceDialog (amber warning, step-2 token = first 8 chars of jobId) → on confirm, row leaves DEAD_LETTER and toast shows "Action recorded." (no fake audit id placeholder).
result: [pending]

### 7. Spend Dashboard + CSV Export (WR-04 + WR-05 + WR-09 verify)
expected: /spend shows 6 KpiCards + three-segment stacked-provider bar (emerald PLATFORM + blue BYOK + gray UNKNOWN) + feature donut + top-20 tenant table. Date-range presets (today / 7d / 30d / custom) work. Selecting custom > 90 days shows inline error before fetch. Apply button (custom range) actually triggers refetch (no longer decorative). CSV Export downloads `spend-{from}-{to}.csv` with 6 columns, no tenant id; any cell starting with `= + - @` is prefixed with `'` to defang formula injection. Streaming export does not crash the body-ban filter.
result: [pending]

### 8. Audit Viewer + HMAC Chain Verifier
expected: /audit shows admin_audit_event rows (event, actor, target, timestamp). Run the chain verifier (admin CLI or button if present) — passes with no chain breaks. Performing any audited action (e.g., requeue, master-key save) appends a new row with monotonically increasing chain_seq.
result: [pending]

### 9. Role Grants
expected: /role-grants lists current admin_users. Granting/revoking another passkey-enrolled admin updates the list immediately (TanStack Query invalidation). Last remaining admin cannot revoke themselves (UI disables button OR backend returns 409).
result: [pending]

### 10. ADMIN MODE Banner + Apps/Admin Isolation
expected: Every authenticated admin route shows a persistent "ADMIN MODE" banner (red/amber) at the top of the layout — clear visual distinction from apps/web. apps/web routes do not show the banner. Hitting an apps/admin route while not authenticated redirects to /login (not to apps/web /login).
result: [pending]

### 11. Privacy Invariant — No Body/Prompt/Completion in Admin UI
expected: On every admin route (especially /queue dead-letters, /audit, /spend, /tenants/{id}/activity), DOM inspection shows zero occurrences of `payload_json`, `prompt_text`, `completion_text`, `request_body`, `response_body`, `body_text`, or email body excerpts. Network responses (`/api/admin/**`) also contain none of these field names. AdminResponseBodyBanFilter is the runtime gate; ArchUnit tests are the build gate.
result: [pending]

## Summary

total: 11
passed: 2
issues: 0
pending: 9
skipped: 0
blocked: 0

## Gaps

[none yet]
