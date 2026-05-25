---
status: in-progress
date: 2026-05-21
---

# Sửa filter cleanup hiển thị nhãn tiếng Việt thay vì raw enum

## Scope

- Sửa select filter/sort trong Bulk Unsubscribe để trigger hiển thị nhãn i18n thay vì value kỹ thuật `ALL` / `SENDER_ASC`.
- Thêm e2e assertion cho label filter/sort mặc định.

## Verification

- Targeted ESLint cho component/test liên quan.
- Cleanup Playwright e2e.
