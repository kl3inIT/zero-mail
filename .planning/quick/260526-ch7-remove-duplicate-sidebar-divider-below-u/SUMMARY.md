---
status: complete
completed: 2026-05-26
---

# Summary

Removed the duplicate horizontal divider below the sidebar user block.

Changes:
- Kept the sidebar header `border-b` as the normal single divider.
- Moved the extra `SidebarSeparator` into the disconnected Gmail reconnect row so it only appears when that row is visible.

Verification:
- `pnpm --filter web lint -- components/shell/AppSidebar.tsx`
- `pnpm --filter web typecheck`
- Playwright direct sidebar check: connected state has `separatorCount: 0` and header `borderBottomWidth: 1px`.
- `pnpm --filter web test:e2e -- app-shell.spec.ts` did not pass because the existing spec still expects `chrome-header`, which is absent from the current shell.
