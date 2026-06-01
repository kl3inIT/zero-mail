---
status: complete
completed: 2026-05-24
---

# Summary

Completed compact Inbox composer attachment UI and send-label follow-up.

Changes:
- Removed the large inline attachment review block.
- Removed local `blob:` preview/open behavior.
- Show selected attachment count directly next to the attachment icon.
- Added a three-dot menu action to clear selected files.
- Renamed the composer submit label to Send/Gửi while preserving the existing confirmation-preview safety flow.

Verification:
- `pnpm --filter web i18n:check`
- `pnpm --filter web lint`
- `pnpm --filter web typecheck`
- `pnpm --filter web test:e2e -- inbox.spec.ts`
