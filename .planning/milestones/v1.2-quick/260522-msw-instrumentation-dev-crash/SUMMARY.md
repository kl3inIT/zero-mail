---
status: complete
completed: 2026-05-22
---

# Summary

Fixed the Next dev crash caused by MSW node setup being reachable from `instrumentation.ts`:
- Made `apps/web/instrumentation.ts` intentionally no-op.
- Deleted the unused node-side MSW server module that imported `msw/node`.
- Removed obsolete `serverExternalPackages` MSW externalization and stale comments that referred to instrumentation-based MSW.
- Kept MSW handlers available for tests that import handlers directly.

Verification:
- `pnpm --filter web run typecheck` passed.
- `pnpm --filter web dev:turbo` started successfully and served `/` + `/login` without the `@mswjs/interceptors/ClientRequest` module export error.
- Playwright MCP opened `http://localhost:3000/login`; browser console reported 0 warnings and 0 errors.
