import createIntlMiddleware from 'next-intl/middleware';
import { NextResponse, type NextRequest } from 'next/server';

import { getCurrentUser } from '@/features/account/api/me';
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

const NEXT_LOCALE_COOKIE = 'NEXT_LOCALE';
const COOKIE_MAX_AGE = 60 * 60 * 24 * 365; // 1 year — REQ-2 persistence

/**
 * WR-02: persist the authenticated user's server-side preferredLanguage into
 * NEXT_LOCALE on the response.
 *
 * Why here and not in the root layout: Server Components cannot reliably mutate
 * response cookies during render — `cookies().set(...)` from a layout is
 * silently rejected in many code paths, leaving a stale cookie + correct render
 * split that breaks "device A -> device B" language sync. The proxy boundary
 * (formerly middleware.ts) owns response headers unambiguously, so the cookie
 * write here is durable.
 *
 * Failure mode is "silent fall-through": if /me is unreachable, the existing
 * NEXT_LOCALE cookie wins. Privacy: no response payload is logged, no locale
 * is correlated with the user's identity.
 */
async function reconcileLocaleCookie(request: NextRequest, response: NextResponse): Promise<void> {
  const session = request.cookies.get('ZEROMAIL_SESSION');
  if (!session) return; // unauthenticated — cookie owner is the LanguageSwitcher

  const apiBase = process.env.NEXT_PUBLIC_API_BASE ?? '';
  if (!apiBase) return;

  const cookieHeader = request.headers.get('cookie');
  if (!cookieHeader) return;

  try {
    // Plan 04 Task 2 (D-B4): isomorphic /me consolidation. The same function
    // backs proxy.ts, app/layout.tsx, and CSR hooks — single source of truth
    // for the "/me requires explicit cookie forwarding" invariant.
    const user = await getCurrentUser({ headers: { cookie: cookieHeader } });
    const preferred = user.preferredLanguage;
    if (preferred !== 'vi' && preferred !== 'en') return;

    const current = request.cookies.get(NEXT_LOCALE_COOKIE)?.value;
    if (current === preferred) return;

    response.cookies.set(NEXT_LOCALE_COOKIE, preferred, {
      maxAge: COOKIE_MAX_AGE,
      sameSite: 'lax',
      secure: true,
      path: '/',
    });
  } catch {
    // Silent — never block navigation on a /me failure. Stale cookie wins.
  }
}

export default async function proxy(request: NextRequest): Promise<NextResponse> {
  // See deviation note above re: duplicate-`next` peer-permutations under
  // pnpm. Cast through unknown to bridge the two structurally-identical
  // NextRequest / NextResponse type identities.
  const intlResponse = handleI18n(
    request as unknown as Parameters<typeof handleI18n>[0],
  ) as unknown as NextResponse;

  const needsAuth = PROTECTED.some((p) => request.nextUrl.pathname.startsWith(p));
  if (needsAuth) {
    const session = request.cookies.get('ZEROMAIL_SESSION');
    if (!session) {
      return NextResponse.redirect(new URL('/login', request.url));
    }
  }

  // Reconcile NEXT_LOCALE with the server preference (authenticated users only).
  await reconcileLocaleCookie(request, intlResponse);

  return intlResponse;
}

export const config = {
  // Run on every page route except API, internals, and assets-with-dot.
  matcher: ['/((?!api|_next|_vercel|.*\\..*).*)'],
};
