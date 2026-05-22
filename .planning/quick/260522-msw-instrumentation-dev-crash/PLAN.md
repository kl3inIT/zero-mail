---
status: in_progress
created: 2026-05-22
---

# Quick Fix: MSW Instrumentation Dev Crash

Goal: fix Next dev startup failing because Turbopack analyzes `instrumentation.ts` and follows the `msw/node` import path.

Steps:
- Inspect the Next instrumentation hook and MSW mock server wiring.
- Remove the unused node-side MSW instrumentation path now that Playwright e2e uses browser route mocks.
- Keep browser/test MSW handlers available for tests that import them directly.
- Verify `pnpm --filter web run typecheck` and a local Next dev startup/page request.
