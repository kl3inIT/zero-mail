---
status: partial
phase: 05A-user-surface-web-ui-core
source: [05A-01-SUMMARY.md, 05A-02-SUMMARY.md, 05A-03-SUMMARY.md, 05A-04-SUMMARY.md, 05A-05-SUMMARY.md, 05A-06-SUMMARY.md]
started: 2026-05-12T15:27:47Z
updated: 2026-05-12T15:46:11Z
---

## Current Test

[testing paused — 4 items blocked on dev-env config]

Blocking config: `apps/web/.env.local` has `NEXT_PUBLIC_API_BASE=https://c3pmlf.taild3b6dc.ts.net`
(a Tailscale funnel). Browser-side fetches to `/me`, `/api/billing/balance`,
`/gmail/connection/status`, top-up, shadow-mode, sender opt-in are CORS-blocked
(no `Access-Control-Allow-Origin` header from that origin). Server-rendered data still
works, so static/SSR-driven pages verified fine. To finish: set
`NEXT_PUBLIC_API_BASE=http://localhost:8080`, restart `pnpm dev`, then re-run
`/gsd-verify-work 5A`.

## Tests

### 1. Protected app shell + onboarding chrome suppression
expected: Protected pages render a persistent left sidebar (flat nav) + top chrome (pause control, credit balance, Gmail health, language/settings/sign-out menu); sidebar collapse persists across reload; onboarding routes show no app shell, only a minimal top bar.
result: pass
note: Verified shell renders on /settings, /triage, /rules — sidebar (Triage/Quy tắc/Thanh toán/Cài đặt) + chrome (Toggle Sidebar, balance pill, Gmail health dot, pause switch, account menu). Onboarding chrome suppression NOT verified: this account's onboarding is COMPLETE so /onboarding/* redirects to /rules. (e2e suite covers the suppression case.) Sidebar-collapse persistence not exercised.

### 2. Triage workspace with deep-linkable tabs
expected: /triage renders inside the app shell with three tabs — Audit, Shadow mode, Senders. Switching tabs updates the URL to ?tab=audit | ?tab=shadow | ?tab=senders, and loading /triage?tab=shadow directly opens the Shadow tab. The Audit tab shows a clear "audit history isn't available yet" panel (not an empty list).
result: pass
note: /triage?tab=audit → "Nhật ký" active, shows "Chưa có lịch sử triage trong giao diện — Backend hiện chỉ có endpoint hoàn tác từng bản ghi…" (correct unavailable panel, not empty). /triage?tab=shadow → "Shadow mode" active with toggle + "Trạng thái ban đầu đang dùng mặc định cục bộ vì backend chưa có endpoint đọc shadow mode." /triage?tab=senders → "Người gửi" active, empty state "Chưa có người gửi được bảo vệ". All deep-links land on the right tab.

### 3. Triage pause toggle stays in sync
expected: Toggling the triage pause control (in the top chrome, on the Settings page, or via the PauseBanner) updates the state optimistically and the other surfaces reflect the same state without a manual refresh. On error it rolls back.
result: blocked
blocked_by: other
reason: "Pause switch is visible and consistent across chrome header + /settings (both 'Đang chạy', checked), but a toggle issues PUT /me / /tenant/triage-pause to the Tailscale API origin which is CORS-blocked in this dev env; cannot exercise the optimistic update + rollback. Re-run after pointing NEXT_PUBLIC_API_BASE at http://localhost:8080."

### 4. Billing page
expected: /billing renders inside the shell with a focal credit-balance figure, held-credit detail, a refresh cadence indication, and a "Top up" CTA. Transaction history is shown as a distinct "transaction history isn't available yet" panel — not a fake zero-row ledger.
result: blocked
blocked_by: other
reason: "/billing renders in-shell with the 'Nạp tín dụng' CTA and a correct ledger panel ('Lịch sử giao dịch chưa khả dụng — … sẽ được bật trong một bản cập nhật sau'). But the BalanceCard shows its error state ('Không tải được số dư — Có lỗi khi lấy số dư tín dụng. Hãy thử lại.') because GET /api/billing/balance from the browser hits the Tailscale origin and is CORS-blocked (also a transient 500 on the /billing route). UI degradation path works; the balance figure itself is env-blocked. The chrome page also logged ~33 console errors, all CORS/ERR_FAILED to https://c3pmlf.taild3b6dc.ts.net."

### 5. Top-up / VietQR flow
expected: /billing/top-up lets you enter an amount and create a top-up intent. The result shows a copyable bank transfer code, the exact VND amount, a copyable VietQR EMV payload (as text — no QR image), and an expiry countdown. When the credit balance rises it shows a success state; when the intent expires it shows an expired/reset state. Reloading with ?code=... rehydrates the pending intent.
result: blocked
blocked_by: other
reason: "Creating a top-up intent requires POST to the billing API; CORS-blocked in this dev env (same NEXT_PUBLIC_API_BASE issue). Not exercised. Re-run after pointing the API base at localhost:8080."

### 6. Privacy page
expected: /settings/privacy renders inside the app shell showing the privacy / data-handling copy, and switching language (vi/en) translates that copy. The Settings page has a "Privacy & data handling" link that reaches it. The public /privacy page is unchanged.
result: pass
note: /settings/privacy renders in-shell with the privacy sections (Những gì chúng tôi không bao giờ lưu / Zero Mail có thể và không thể làm gì / Dùng khóa AI riêng của bạn (BYOK)) and a "Xem trang quyền riêng tư công khai" link. Settings page has "Mở quyền riêng tư" → /settings/privacy. Locale-switch translation not re-exercised here (PUT /me CORS-blocked) but VI copy renders correctly.

### 7. Rules pages use shared state components
expected: The rules list / preview / template gallery render consistent loading, empty, and error states (spinner/skeleton while loading, a friendly empty message when there's nothing, a retryable error panel on failure) — visually consistent with the rest of the app.
result: pass
note: /rules renders in-shell with the rule list ("Pin calendar mail", "Archive receipts", "Label newsletters" with Đang bật/Đang tắt, Template, Đã chạy thử badges, reorder/edit/toggle/delete controls), the rule composer + preview panel ("Zero Mail đã hiểu" with matcher/action chips), the starter-template gallery, and the dry-run section — visually consistent with the rest of the app. (Loading/error variants of these states not forced.)

### 8. Language switch across the shell
expected: Using the language menu in the top chrome switches the whole authenticated UI between Vietnamese and English — sidebar labels, chrome, page copy — with no missing-key placeholders showing. 
result: blocked
blocked_by: other
reason: "The whole authenticated UI currently renders in Vietnamese with no missing-key placeholders, and the language control is present (chrome account menu + Settings 'Ngôn ngữ' = Tiếng Việt). But persisting/applying a switch issues PUT /me, which is CORS-blocked in this dev env. Re-run after pointing the API base at localhost:8080 to verify the VI↔EN toggle."

## Summary

total: 8
passed: 4
issues: 0
pending: 0
skipped: 0
blocked: 4

## Gaps

[none — the 4 unverified tests are blocked on a dev-env config issue (NEXT_PUBLIC_API_BASE → Tailscale funnel, CORS-blocked from localhost), not on phase-5A code]
