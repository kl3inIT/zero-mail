---
status: complete
completed: 2026-05-24
---

# Summary

Completed Inbox i18n cleanup, split-pane scroll behavior, composer overlay, AI body generation into the textarea, generation language selection, attachment review, and composer overflow actions.

Verification:
- `pnpm --filter web i18n:check`
- `pnpm --filter web lint`
- `pnpm --filter web typecheck`
- `pnpm --filter web test:e2e -- inbox.spec.ts`
