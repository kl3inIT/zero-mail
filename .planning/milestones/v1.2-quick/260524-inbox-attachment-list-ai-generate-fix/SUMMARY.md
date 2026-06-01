---
status: complete
completed: 2026-05-24
---

# Summary

Completed the Inbox composer attachment-list follow-up and fixed AI generation.

Changes:
- Attachment count now appears to the right of the Attach/Đính kèm label.
- Selected attachments are shown as compact filename chips in the composer toolbar.
- Individual selected files can be removed from the compact list.
- Inbox composer chat IDs now use real UUIDs, matching the backend `/api/chat` request contract.
- Inbox e2e now asserts UUID chat IDs for both preview and AI body generation.

Verification:
- `pnpm --filter web i18n:check`
- `pnpm --filter web lint`
- `pnpm --filter web typecheck`
- `pnpm --filter web test:e2e -- inbox.spec.ts`
