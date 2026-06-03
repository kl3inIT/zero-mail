---
status: complete
completed: 2026-05-26
---

# Summary

Reduced the visual gap between the expanded sidebar and app content.

## Changes

- Added a desktop-only negative left margin on `SidebarInset` while the sidebar is expanded.
- Kept mobile and collapsed-sidebar behavior covered by existing app-shell tests.

## Verification

- `pnpm --filter web typecheck`
- `pnpm --filter web lint`
- `pnpm --filter web test:e2e app-shell.spec.ts`
- `pnpm --filter web test:e2e inbox.spec.ts`
