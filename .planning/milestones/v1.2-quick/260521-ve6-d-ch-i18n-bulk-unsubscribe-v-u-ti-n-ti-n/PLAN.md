---
status: in-progress
date: 2026-05-21
---

# Dịch i18n Bulk Unsubscribe và ưu tiên tiếng Việt cho menu cleanup

## Scope

- Dịch các chuỗi `vi` còn lẫn tiếng Anh trong Bulk Unsubscribe, suppression list và menu cleanup.
- Build lại generated i18n messages.
- Cập nhật test e2e cleanup nếu assert theo copy cũ.

## Verification

- `pnpm --filter web i18n:build`
- `pnpm --filter web i18n:check`
- Targeted ESLint cho các file messages/test liên quan.
