---
status: complete
---

# Quick Task 260524-wjr Summary

Implemented the cleanup UI consolidation requested by the user:

- Sidebar now exposes one direct "Hủy đăng ký" item and no cleanup submenu.
- `/cleanup/suppression` redirects back to `/cleanup/unsubscribe-campaign`; safe-list management is opened from the unsubscribe page as a dialog.
- Candidate table has a header select-all checkbox and no "Cách hủy" column.
- Preview dialog refetches for the current selection, wraps sender rows/buttons safely, handles empty preview results, and labels the final action "Hủy đăng ký".
- Safe-list wording is now "Danh sách an toàn"; add/remove mutations invalidate the unsubscribe candidate cache.

Verification:

- `pnpm --dir apps/web typecheck` passed.
- `pnpm --dir apps/web i18n:check` passed.
- `pnpm --dir apps/web lint` passed.
- `pnpm --dir apps/web test:e2e cleanup-unsubscribe-campaign.spec.ts --reporter=list` passed.
- `pnpm --dir apps/web test:e2e cleanup-suppression.spec.ts --reporter=list` passed.
- `pnpm --dir apps/web test:e2e cleanup-unsubscribe-campaign.spec.ts cleanup-suppression.spec.ts --reporter=list --workers=1` passed.
- `.\gradlew.bat :backend:core:test --tests com.zeromail.core.arch.NoGmailSendAllowedTest --tests com.zeromail.core.arch.SafetyContractArchTests` passed.

