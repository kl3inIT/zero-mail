---
status: complete
quick_id: 260517-global-zero-glyph
completed: 2026-05-17
---

# Summary

Changed the global mono font token so all `font-mono` usages render through the app's Roboto/system sans stack. This replaces the hard-to-read `0` glyph across pages without editing every component individually.

## Verification

- `pnpm --filter web typecheck` passed.
- `pnpm --filter web exec eslint app/layout.tsx __tests__/setup.ts` passed.
- Browser check on `http://localhost:3000` confirmed `.font-mono` resolves to the Roboto/system sans stack.
