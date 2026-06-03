---
status: complete
completed: 2026-05-26
---

# Summary

Improved the inbox list layout and search.

## Changes

- Reduced the app sidebar width from 260px to 224px.
- Widened the inbox list column on desktop.
- Simplified each email row: first line is sender name only, second line is subject/header, labels remain below.
- Added list date formatting: today's messages show time only; older messages show date only.
- Added search across loaded sender/email, subject, and Gmail labels.
- Added e2e assertions for sender-only rows, date formatting, and search by label/email/subject.

## Verification

- `pnpm --filter web i18n:build`
- `pnpm --filter web typecheck`
- `pnpm --filter web test:e2e inbox.spec.ts`
- `pnpm --filter web lint`
- `pnpm --filter web test:e2e analytics.spec.ts`
