# Quick Task 260524-wjr: Consolidate cleanup UI into Huy dang ky flow

## Goal

Make the cleanup/unsubscribe UI match the requested product shape:

- One navigation surface named "Hủy đăng ký"; no cleanup submenu.
- Remove the "Cách hủy" table column.
- Replace the separate "Danh sách không hủy" screen entry with an in-page "Danh sách an toàn" dialog.
- Rename the destructive action from "Chạy tác vụ" to "Hủy đăng ký".
- Fix the preview dialog layout/loading/empty states.
- Add a header checkbox to select all visible candidates.

## Tasks

1. Inspect the current cleanup frontend components, messages, and e2e coverage.
2. Update the UI and labels while keeping existing API contracts.
3. Run frontend type/i18n/e2e verification and record results.

