---
status: complete
completed: 2026-05-25
---

# Summary

Removed visible border lines from the Inbox reply composer.

Changes:
- Removed the composer outer border and all internal horizontal separators.
- Switched the composer body to one white surface so background transitions do not read as lines.
- Removed visible borders from the AI generate control, attachment control, language selector, and attachment chips inside the composer.

Verification:
- `pnpm --filter web lint`
- `pnpm --filter web typecheck`
- `pnpm --filter web test:e2e -- inbox.spec.ts`
