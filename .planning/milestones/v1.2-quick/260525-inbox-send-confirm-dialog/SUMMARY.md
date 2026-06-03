---
status: complete
completed: 2026-05-25
---

# Summary

Completed the Inbox send confirmation dialog follow-up.

Changes:
- Inbox send now opens a short confirmation dialog before sending.
- The verbose assistant preview text is hidden in the Inbox send path.
- After confirmation, Inbox auto-confirms the generated send action without rendering the full preview card.
- Composer overlay now has margin from the email content.

Verification:
- `pnpm --filter web i18n:check`
- `pnpm --filter web lint`
- `pnpm --filter web typecheck`
- `pnpm --filter web test:e2e -- inbox.spec.ts`
