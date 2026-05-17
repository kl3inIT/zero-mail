---
status: executing
quick_id: 260517-global-zero-glyph
created: 2026-05-17
---

# Quick Task 260517-global-zero-glyph: Replace Hard-To-Read Zero Glyph Globally

## Goal

Make the digit `0` render consistently with the app's sans UI font across all pages, including places that currently use `font-mono`.

## Tasks

1. Change the global mono font token to use the app sans stack.
   - Files: `apps/web/app/globals.css`
   - Action: point `--font-mono` to Roboto/system sans so existing `font-mono` utility classes no longer use Roboto Mono's zero glyph.

2. Remove the unused Roboto Mono font loader.
   - Files: `apps/web/app/layout.tsx`
   - Action: stop loading `Roboto_Mono` and remove the root `--font-roboto-mono` class.

3. Verify frontend type/lint health for the touched app shell.
