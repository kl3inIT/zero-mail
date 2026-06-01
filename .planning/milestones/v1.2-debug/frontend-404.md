---
status: resolved
trigger: "User reports frontend starts but shows stock Next.js 404; screenshot path: C:\\Users\\admin\\Pictures\\Screenshots\\Screenshot 2026-04-26 231515.png"
created: 2026-04-26
updated: 2026-04-26
---

# Debug Session: Frontend 404

## Symptoms

- Expected behavior: Starting the frontend and opening the app should render the Zero Mail public landing page or login flow.
- Actual behavior: Browser shows the stock Next.js `404 This page could not be found` screen.
- Error messages: No app error shown; screenshot shows only a Next 404 page.
- Timeline: Reported after Phase 01.3 route-group migration and code-review fixes.
- Reproduction: Open the running frontend in the browser on the local dev server.

## Current Focus

- hypothesis: The running Next dev server has a stale `.next/dev` route manifest after route-group migration; source code and production build still define `/`, `/login`, `/docs`, `/onboarding`, and `/settings`.
- test: Remove next-intl routing middleware rewrite, read NEXT_LOCALE directly from cookies, and request `/`, `/login`, and `/docs`.
- expecting: Public clean URLs return 200 with no `x-middleware-rewrite`; protected clean URLs still redirect anonymous users to `/login`.
- next_action: complete; fix verified.

## Evidence

- timestamp: 2026-04-26
  observation: Screenshot shows stock Next.js 404.
- timestamp: 2026-04-26
  observation: `http://localhost:3000/`, `/login`, and `/docs` return 404, while `/onboarding` and `/settings` redirect to `/login`, proving `proxy.ts` is running.
- timestamp: 2026-04-26
  observation: `apps/web/.next/dev/server/app-paths-manifest.json` contains only `/_not-found/page`.
- timestamp: 2026-04-26
  observation: `pnpm --filter zeromail-web exec next build` succeeds and lists routes `/`, `/docs`, `/docs/[slug]`, `/login`, `/onboarding`, and `/settings`.
- timestamp: 2026-04-26
  observation: Context7 Next.js docs confirm route groups in parentheses are omitted from the URL, so `app/(public)/page.tsx` should map to `/`.
- timestamp: 2026-04-26
  observation: Context7 next-intl docs confirm `localePrefix: 'never'` with routing middleware still rewrites requests internally to locale-prefixed paths and requires pages in `[locale]`.
- timestamp: 2026-04-26
  observation: Response headers for `/`, `/login`, and `/docs` included `x-middleware-rewrite: /vi`, `/vi/login`, and `/vi/docs`; these hidden paths did not exist because `app/[locale]` was intentionally removed.
- timestamp: 2026-04-26
  observation: After removing next-intl routing middleware and resolving locale from `NEXT_LOCALE` cookie in `i18n/request.ts`, `/`, `/login`, `/docs`, and `/docs/getting-started` return 200 with no middleware rewrite.

## Eliminated

- hypothesis: Current source is missing the root page.
  reason: `apps/web/app/(public)/page.tsx` exists and production build lists `/`.
- hypothesis: Route groups require `(public)` in the URL.
  reason: Current Next.js docs state route-group folders are omitted from URLs.
- hypothesis: A stale Next dev cache was the only cause.
  reason: Restarting with a clean `.next/dev` cache still returned `/login` 404 until the hidden `/vi/login` rewrite was removed.

## Resolution

- root_cause: `next-intl` routing middleware with `localePrefix: 'never'` rewrote clean URLs like `/login` to hidden locale routes like `/vi/login`. Phase 01.3 intentionally deleted `app/[locale]`, so the hidden rewrite target returned the stock Next.js 404 even though production build still saw the route-group pages.
- fix: Removed `next-intl/middleware` from `proxy.ts`, changed `i18n/request.ts` to read `NEXT_LOCALE` directly from cookies, and kept locale-cookie maintenance/auth redirects in `proxy.ts` without URL rewriting.
- verification: `tsc --noEmit` passed; Vitest route/workspace/docs tests passed; Playwright route-smoke passed 7/7; manual HTTP checks returned 200 for `/`, `/login`, `/docs`, `/docs/getting-started` and 307 redirects to `/login` for `/onboarding`, `/settings`.
- files_changed: apps/web/proxy.ts, apps/web/i18n/request.ts, apps/web/i18n/routing.ts, apps/web/__tests__/architecture/route-groups.test.ts, apps/web/__tests__/e2e/route-smoke.spec.ts
