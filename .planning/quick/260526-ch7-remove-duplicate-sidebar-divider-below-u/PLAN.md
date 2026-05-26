---
created: 2026-05-26
status: complete
---

# Remove Duplicate Sidebar Divider

Task: remove the extra horizontal line below the sidebar user block.

Scope:
- Only update the app sidebar chrome in `apps/web/components/shell/AppSidebar.tsx`.
- Keep the header bottom border as the single normal divider.
- Preserve the reconnect Gmail row separation when that row is visible.

Verification:
- Run targeted frontend lint/type checks.
- Verify the app shell in a browser or Playwright flow.
