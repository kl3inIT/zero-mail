---
status: complete
phase: 05A-user-surface-web-ui-core
source: [05A-01-SUMMARY.md, 05A-02-SUMMARY.md, 05A-03-SUMMARY.md, 05A-04-SUMMARY.md, 05A-05-SUMMARY.md, 05A-06-SUMMARY.md]
started: 2026-05-12T15:27:47Z
updated: 2026-05-12T16:03:20Z
---

## Current Test

[testing complete]

Verified live in a real browser via Playwright (logged in as dathip04@gmail.com,
with apps/web/.env.local set to NEXT_PUBLIC_API_BASE=http://localhost:8080).

## Tests

### 1. Protected app shell + onboarding chrome suppression
expected: Protected pages render a persistent left sidebar (flat nav) + top chrome (pause control, credit balance, Gmail health, language/settings/sign-out menu); sidebar collapse persists across reload; onboarding routes show no app shell, only a minimal top bar.
result: pass
note: Shell renders on /settings, /triage, /rules, /billing — sidebar (Triage/Rules/Billing/Settings) + chrome (Toggle Sidebar, Credits pill, Gmail-connected dot, Automatic-triage switch, Account menu), all client-navigation-stable. Onboarding chrome suppression NOT exercised: this account's onboarding is COMPLETE so /onboarding/* redirects to /rules (e2e suite covers the suppression case). Sidebar-collapse persistence not exercised.

### 2. Triage workspace with deep-linkable tabs
expected: /triage renders inside the app shell with three tabs — Audit, Shadow mode, Senders. Switching tabs updates the URL to ?tab=audit | ?tab=shadow | ?tab=senders, and loading /triage?tab=shadow directly opens the Shadow tab. The Audit tab shows a clear "audit history isn't available yet" panel (not an empty list).
result: pass
note: /triage?tab=audit → "Nhật ký" active, "Chưa có lịch sử triage trong giao diện — Backend hiện chỉ có endpoint hoàn tác từng bản ghi…" (correct unavailable panel). ?tab=shadow → Shadow tab with toggle + "Trạng thái ban đầu đang dùng mặc định cục bộ vì backend chưa có endpoint đọc shadow mode." ?tab=senders → "Người gửi" empty state "Chưa có người gửi được bảo vệ". All deep-links land on the right tab.

### 3. Triage pause toggle stays in sync
expected: Toggling the triage pause control (in the top chrome, on the Settings page, or via the PauseBanner) updates the state optimistically and the other surfaces reflect the same state without a manual refresh. On error it rolls back.
result: pass
note: Toggled the Settings pause switch → chrome switch flipped to "Đang tạm dừng" and a PauseBanner appeared ("Tự động xử lý đang tạm dừng … Bật lại"), no refresh. Clicked the banner's "Bật lại" → chrome back to "Đang chạy", banner gone, settings switch back. PUT went through cleanly (no errors). Minor: the Settings switch labeled "Tạm dừng tự động xử lý" is ON (checked/green) when triage is RUNNING and OFF when paused — a "Pause X" toggle reading inverted from its label is mildly confusing; consider relabeling or inverting.

### 4. Billing page
expected: /billing renders inside the shell with a focal credit-balance figure, held-credit detail, a refresh cadence indication, and a "Top up" CTA. Transaction history is shown as a distinct "transaction history isn't available yet" panel — not a fake zero-row ledger.
result: pass
note: /billing shows "Tín dụng hiện có … 0 tín dụng", "Đang giữ 0", "Tự cập nhật Mỗi 45 giây", and a "Nạp tín dụng" CTA. Ledger panel: "Lịch sử giao dịch chưa khả dụng — … sẽ được bật trong một bản cập nhật sau" (distinct unavailable panel, not an empty ledger). No console errors.

### 5. Top-up / VietQR flow
expected: /billing/top-up lets you enter an amount and create a top-up intent. The result shows a copyable bank transfer code, the exact VND amount, a copyable VietQR EMV payload (as text — no QR image), and an expiry countdown. When the credit balance rises it shows a success state; when the intent expires it shows an expired/reset state. Reloading with ?code=... rehydrates the pending intent.
result: issue
reported: "Clicking 'Tiếp tục thanh toán' shows error 'Backend chưa trả đủ thông tin VietQR. Hãy thử lại.' — POST /api/billing/topup/intent returns 200 with {code, amountVnd, expiresAt, qrPayload:null}; qrPayload is null so the UI refuses to render the transfer instructions and the top-up flow dead-ends."
severity: major
note: Root cause is backend (TopupIntentResponse.qrPayload is null — VietQR EMV payload not generated/configured; a phase-2B billing concern, not phase-5A code). The 5A UI does fail safe (clear error, no crash), but it could degrade more gracefully — show the code + exact amount + expiry and just hide the QR — rather than blocking the whole flow when only qrPayload is missing.

### 6. Privacy page
expected: /settings/privacy renders inside the app shell showing the privacy / data-handling copy, and switching language (vi/en) translates that copy. The Settings page has a "Privacy & data handling" link that reaches it. The public /privacy page is unchanged.
result: pass
note: /settings/privacy renders in-shell with the privacy sections (Những gì chúng tôi không bao giờ lưu / Zero Mail có thể và không thể làm gì / Dùng khóa AI riêng của bạn (BYOK)) + "Xem trang quyền riêng tư công khai" link. Settings page has "Mở quyền riêng tư" / "Privacy & data handling" → /settings/privacy. (See Test 8 — VI↔EN translation of this copy verified there.)

### 7. Rules pages use shared state components
expected: The rules list / preview / template gallery render consistent loading, empty, and error states (spinner/skeleton while loading, a friendly empty message when there's nothing, a retryable error panel on failure) — visually consistent with the rest of the app.
result: pass
note: /rules renders in-shell with the rule list ("Pin calendar mail", "Archive receipts", "Label newsletters" with Enabled/Disabled, Template, Previewed/Preview-required badges + reorder/edit/toggle/delete controls), the rule composer with the "What Zero Mail understood" matcher/action chips, the starter-template gallery, and the safe-preview panel. Ran "Preview rule" → it dry-ran against recent Gmail: "1 sampled · 0 matched · 0 deferred · 0 conflicts · No Gmail changes were made" and showed a real Gmail message (bytebytego@substack.com, "How Figma Upgraded Data Pipeline…", labels UNREAD/CATEGORY_UPDATES/INBOX). The earlier-reported "mail no longer loading in the rule test" was the NEXT_PUBLIC_API_BASE misconfig — resolved. (Explicit loading/error variants of the shared states not force-triggered.)

### 8. Language switch across the shell
expected: Using the language menu in the top chrome switches the whole authenticated UI between Vietnamese and English — sidebar labels, chrome, page copy — with no missing-key placeholders showing.
result: pass
note: Switched VI→EN via the Settings "Ngôn ngữ" control → sidebar became Triage/Rules/Billing/Settings, chrome became "Credits 0 / Automatic triage / Running", and page copy translated throughout ("Provider", "Official providers", "Model", "API key", "Validate API key", "Save API key", "Privacy and safety", "No long-term storage of email bodies, prompts, or AI completions", "No auto-send", "You can revoke access anytime", "Privacy & data handling", "Danger zone", "Disconnect Gmail", "Delete account and data"). No missing-key placeholders. Persisted via PUT and re-rendered without manual refresh.

## Summary

total: 8
passed: 7
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

- truth: "Top-up flow produces a usable VietQR transfer code/amount/expiry the user can pay"
  status: failed
  reason: "User-equivalent: clicking 'Tiếp tục thanh toán' on /billing/top-up dead-ends with 'Backend chưa trả đủ thông tin VietQR. Hãy thử lại.' POST /api/billing/topup/intent returns 200 with qrPayload:null, so the UI's TopupInstructions cannot render and there's no way forward."
  severity: major
  test: 5
  root_cause: "Backend TopupIntentResponse.qrPayload is null — the VietQR EMV payload is not generated/configured in the billing service (phase-2B scope), not a phase-5A code defect. Secondary 5A consideration: TopupClient/TopupInstructions treat a missing qrPayload as a hard failure for the whole flow instead of degrading (render code + amount + expiry, hide the QR)."
  artifacts:
    - path: "apps/web/features/billing/components/TopupClient.tsx"
      issue: "Treats incomplete TopupIntentResponse (qrPayload null) as a blocking error for the entire top-up flow"
    - path: "apps/web/features/billing/components/TopupInstructions.tsx"
      issue: "Requires qrPayload; no graceful render path when only the QR payload is absent"
  missing:
    - "Backend: generate a valid VietQR EMV qrPayload in the top-up intent response (phase 2B)"
    - "Optional 5A hardening: render the transfer code + exact amount + expiry even when qrPayload is null, instead of erroring out"
  debug_session: ""
