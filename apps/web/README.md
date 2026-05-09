# Zero Mail Web

Next.js App Router frontend for Zero Mail.

## Structure

- `app/` - Next.js routes and route groups.
  - `(public)/` - landing, docs, terms, privacy.
  - `(auth)/` - login.
  - `(protected)/` - authenticated product routes.
  - `actions/` - route handlers for frontend actions.
- `features/` - product feature modules.
  - `api/<feature>-api.ts` - small HTTP functions for that feature.
  - `query-keys.ts` - TanStack Query cache keys for cached server data.
  - `hooks/useX.ts` - one TanStack Query hook per use case.
  - `components/` - feature-owned UI.
- `components/ui/` - shadcn/ui primitives.
- `i18n/` - message bundles and i18n UI.
- `lib/` - shared infrastructure such as API client and docs loading.
- `e2e/` - Playwright browser tests.
- `__tests__/` - app-wide Vitest unit, component, and contract tests.

## Feature Rules

Keep query keys outside `api/`; they describe cache identity, not transport.
Mutation-only features do not need `query-keys.ts` unless they own cached query
data. Query keys are named after data, not actions: invalidating
`accountQueryKeys.me()` is correct when `/me` owns the changed state.

Keep hooks one file per use case. A hook owns its TanStack Query behavior:
`queryKey`, `queryFn`, mutation invalidation, optimistic updates, and error
handling.

Do not add feature root barrel files. Import concrete files directly.

## Tests

```bash
pnpm --filter web run lint
pnpm --filter web run typecheck
pnpm --filter web run test
pnpm --filter web run build
pnpm --filter web run test:e2e
```

Vitest collects `__tests__/**/*` and feature-owned `features/**/*` test files.
Playwright collects only `e2e/**/*.spec.ts`.

CI keeps E2E separate:

- `.github/workflows/ci.yml` - backend, frontend lint, typecheck, Vitest, build.
- `.github/workflows/e2e.yml` - Playwright browser tests.
