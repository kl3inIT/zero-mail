# Quick Task 260518-wai: Replace Manual Frontend Chat API DTOs

**Date:** 2026-05-18
**Status:** In progress

## Goal

Replace manually declared frontend chat API DTOs with generated OpenAPI types where available, and make the project rule explicit so future feature API files do not hand-write backend contract types when `schema.d.ts` has them.

## Tasks

1. Regenerate the backend OpenAPI spec and frontend TypeScript schema.
2. Refactor chat API calls to use `@/lib/api/client` and generated `components`/`paths` types.
3. Replace stale manual API DTOs in nearby touched feature API code if the generated schema already contains them.
4. Add explicit convention text to `CLAUDE.md`, `AGENTS.md`, and `CONVENTIONS.md`.
5. Run focused frontend verification.

## Verification

- `pnpm --filter web generate:api`
- `pnpm --filter web typecheck`
- `pnpm --filter web lint`

