---
status: complete
completed: 2026-05-26
---

# Summary

Tightened inbox rows and moved the unread/active dot beside the sender name.

## Changes

- Removed the left-side unread dot gutter from message rows.
- Placed the dot directly after the sender name.
- Reduced row padding, horizontal gap, and vertical spacing between sender, subject, and labels.
- Added e2e coverage that verifies the dot is next to the sender.

## Verification

- `pnpm --filter web typecheck`
- `pnpm --filter web test:e2e inbox.spec.ts`
- `pnpm --filter web lint`
