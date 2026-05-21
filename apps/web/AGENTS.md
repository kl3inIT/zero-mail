<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.

<!-- END:nextjs-agent-rules -->

## shadcn/ui Primitive Rule

Before building or refactoring UI, check whether shadcn/ui already provides the needed primitive. If it exists and is not already in `components/ui`, install it with `pnpm dlx shadcn@latest add <component>` from `apps/web`, then import from `@/components/ui/*` and compose project-specific components around it.

`components/ui/**` is copied shadcn primitive source and is ignored by ESLint and Prettier. Avoid hand-rolling primitives that shadcn already provides.

## Error handling, toasts, retries — TanStack Query v5 patterns

All TanStack Query callbacks are wired centrally in `apps/web/lib/query-client.tsx`. Follow these rules instead of re-implementing locally:

- **Toasts come from `MutationCache.onSuccess`/`onError`, opt-in via `meta`.** When writing a new `useMutation`, set `meta: { successMessage: '...', errorMessage: '...' }` rather than calling `toast.success/error` from a local `onError`. The global handler reads meta and toasts via Sonner.
- **Use `meta.silent: true`** when a query or mutation should never produce a toast (e.g. background polling, optimistic flows that own their own UX).
- **Query background refetch failures auto-toast** (per TkDodo's pattern — initial fetch failures are caught by Next's `error.tsx` at the route group level, not the toast layer). Don't add `onError` to a query just to surface a generic toast.
- **Module augmentation already declares the meta shape.** Use the typed fields (`errorMessage`, `successMessage`, `silent`) only. If you need a new meta key, add it to the `interface Register` block in `query-client.tsx` so every callsite is typed.
- **Retry policy is global.** Default is `retry: 1` for queries, skipping 4xx client errors; mutations don't retry. Don't override unless you have a documented reason (network polling, eventual-consistency reads).
- **401 redirect is handled at the fetch layer** by the `onResponse` middleware in `apps/web/lib/api/client.ts` — don't write per-callsite 401 checks. Public auth surfaces (`/login`, `/privacy`, `/terms`, `/docs`, etc.) are excluded from the redirect.
- **Code-based error localization lives in `lib/api/errors.ts`** via `useLocalizedApiError()` / `useLocalizedFieldError()`. Switch on `err.code` only; never read server-provided `title` / `detail` strings into the DOM (threat model T-1.1.06-01/02).

## No hardcoded color hex

Consume design tokens — `bg-card`, `bg-background`, `bg-muted`, `bg-accent`, `bg-primary/10`, `text-foreground`, `text-muted-foreground`, `text-accent-foreground`, `border`, `border-border`, `ring-border`. Never write `bg-[#xxxxxx]` or `text-[#xxxxxx]` in primitives or feature components. Marketing-only surfaces (`.zm-proto`, `.zm-auth` in `app/globals.css`) are the only place that overrides token CSS variables locally. Palette pivots stay in `globals.css` so the whole app updates in one place.
