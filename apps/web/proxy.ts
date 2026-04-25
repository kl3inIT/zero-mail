import createIntlMiddleware from 'next-intl/middleware';
import { NextResponse, type NextRequest } from 'next/server';

import { routing } from './i18n/routing';

/**
 * Next.js 16 proxy.ts (formerly middleware.ts).
 *
 * Composes two responsibilities (CONTEXT.md D-B4):
 *  1. next-intl locale negotiation — runs first so the NEXT_LOCALE cookie is
 *     read/written and `<html lang>` resolves correctly via getLocale() in the
 *     async root layout. Configuration lives in `i18n/routing.ts`.
 *  2. Phase 1 auth gate — keeps the existing `/onboarding` + `/settings`
 *     redirect-to-/login behavior when the ZEROMAIL_SESSION cookie is missing.
 *
 * Order matters: i18n must run first so its locale-cookie response headers are
 * preserved when we either pass the response through or replace it with a
 * redirect (NextResponse.redirect intentionally drops the i18n rewrite headers
 * because the user is being sent to /login anyway).
 *
 * Plan 07 deviation note (Rule 3 — Blocking): pnpm hoists `next` to BOTH
 * `apps/web/node_modules/next` AND the workspace root because Next.js declares
 * `@playwright/test` as an optional peer dep. Once `@playwright/test` exists at
 * root (Plan 07 Task 2), pnpm creates a second peer-permutation of `next` and
 * physical paths diverge. TypeScript then sees `NextResponse` from
 * `next-intl/middleware` (resolved through root's `next`) as a different type
 * from `NextResponse` declared at the proxy boundary (resolved through
 * apps/web's `next`). Runtime behavior is identical — both are the same Next
 * implementation. We bridge the type identity with an `as unknown as` cast at
 * the function-result boundary; the cast is type-only, not a structural check.
 */
const handleI18n = createIntlMiddleware(routing);

const PROTECTED = ['/onboarding', '/settings'];

export default function proxy(request: NextRequest): NextResponse {
  // See deviation note above re: duplicate-`next` peer-permutations under
  // pnpm. Cast through unknown to bridge the two structurally-identical
  // NextRequest / NextResponse type identities.
  const intlResponse = handleI18n(request as unknown as Parameters<typeof handleI18n>[0]) as unknown as NextResponse;

  const needsAuth = PROTECTED.some((p) => request.nextUrl.pathname.startsWith(p));
  if (needsAuth) {
    const session = request.cookies.get('ZEROMAIL_SESSION');
    if (!session) {
      return NextResponse.redirect(new URL('/login', request.url));
    }
  }

  return intlResponse;
}

export const config = {
  // Run on every page route except API, internals, and assets-with-dot.
  matcher: ['/((?!api|_next|_vercel|.*\\..*).*)'],
};
