---
status: complete
date: 2026-05-21
---

# Summary

- Renamed Bulk Unsubscribe filter labels so the dropdown explains the user-visible logic:
  - `Sẵn sàng xử lý` → `Có thể hủy đăng ký`
  - `Một nhấp` → `Hủy bằng liên kết`
  - `Gửi email` → `Hủy bằng email`
- Regenerated i18n message bundles.
- Added e2e assertions for the opened filter dropdown options.

# Verification

- `pnpm --filter web i18n:build`
- `pnpm --filter web i18n:check`
- `pnpm --filter web exec eslint features/cleanup/unsubscribe-campaign/messages.ts e2e/cleanup-unsubscribe-campaign.spec.ts`
- `pnpm exec playwright test --config=playwright.cleanup.config.ts --reporter=list` from `apps/web` passed 4/4.
- Manual screenshot verified the opened dropdown on `http://localhost:3000/cleanup/unsubscribe-campaign`.
