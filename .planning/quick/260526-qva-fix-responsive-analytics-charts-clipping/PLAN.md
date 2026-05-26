---
status: complete
created: 2026-05-26
---

# Fix responsive analytics charts clipping

## Scope

- Diagnose mobile clipping on the analytics daily line chart and triage donut.
- Keep the existing analytics UI structure and shadcn/Recharts primitives.
- Verify with focused frontend checks and browser rendering where possible.

## Tasks

- Make shared chart containers shrink inside narrow card/grid layouts.
- Make analytics cards opt into `min-width: 0` so Recharts content cannot force page overflow.
- Run type/test checks and inspect the responsive page in a browser.
