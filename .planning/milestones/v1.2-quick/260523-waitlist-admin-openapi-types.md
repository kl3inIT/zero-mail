# Waitlist Admin OpenAPI Types

## Goal

Replace the admin waitlist raw-fetch helper types with generated OpenAPI-derived types and the shared `admin-client` wrapper.

## Scope

- Regenerate or use `apps/admin/src/lib/api/admin-schema.d.ts` for `/api/admin/waitlist`.
- Update `apps/admin/src/features/waitlist/waitlist-api.ts` to derive response/query/status types from the OpenAPI schema.
- Verify TypeScript for `@zeromail/admin`.

## Notes

- Do not hand-edit generated OpenAPI files.
- Keep behavior equivalent: list, approve, and reject waitlist entries.

## Result

- Refreshed `apps/admin/openapi/admin-spec.json` from the running API on `localhost:8080`.
- Regenerated `apps/admin/src/lib/api/admin-schema.d.ts` via `pnpm --filter @zeromail/admin run generate-api`.
- Updated waitlist admin helpers to use `api.GET` / `api.POST` and OpenAPI-derived types.
- Verified with `pnpm --filter @zeromail/admin run typecheck`.
