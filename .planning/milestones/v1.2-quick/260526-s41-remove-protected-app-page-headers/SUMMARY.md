---
status: complete
completed: 2026-05-26
---

# Summary

Removed protected app page-level headers.

## Changes

- Removed top title/description header bars from protected pages including Inbox, Analytics, Billing, Rules, Settings, Privacy, Chat, Dashboard, AI, Needs Reply, and cleanup pages.
- Moved Inbox refresh/count into the list toolbar.
- Moved Billing top-up CTA into the content area.
- Kept local card, table, dialog, and section headers intact.
- Updated affected Playwright specs to assert functional content instead of removed page headers.

## Verification

- `pnpm --filter web typecheck`
- `pnpm --filter web lint`
- `pnpm --filter web test:e2e analytics.spec.ts`
- `pnpm --filter web test:e2e inbox.spec.ts`
- `pnpm --filter web test:e2e needs-reply.spec.ts`
- `pnpm --filter web test:e2e privacy-page.spec.ts`
- `pnpm --filter web test:e2e billing-topup.spec.ts`
- `pnpm --filter web test:e2e rules-examples.spec.ts`
- `pnpm --filter web test:e2e chat/stream-happy-path.spec.ts chat/vietnamese-default.spec.ts`
- `pnpm --filter web test:e2e cleanup-unsubscribe-campaign.spec.ts`
