import { NextResponse, type NextRequest } from 'next/server';

import { getCurrentUser } from '@/features/account/api/account-api';
import { getApiBase } from '@/lib/api/base-url';
import { LOCALE_COOKIE_MAX_AGE, NEXT_LOCALE_COOKIE, routing } from './i18n/routing';

/**
 * Next.js 16 proxy.ts (formerly middleware.ts).
 *
 * Composes two responsibilities (CONTEXT.md D-B4):
 *  1. NEXT_LOCALE cookie upkeep without next-intl's routing middleware. With
 *     `localePrefix: 'never'`, that middleware rewrites `/login` to hidden
 *     `/vi/login` internally, which requires an `app/[locale]` mirror tree.
 *     Phase 1.3 keeps clean route groups instead, so i18n/request.ts reads the
 *     cookie directly and this proxy only maintains the cookie value.
 *  2. Phase 1 auth gate — keeps app routes and onboarding behind the
 *     redirect-to-/login behavior when the ZEROMAIL_SESSION cookie is missing.
 *
 * NO next-intl middleware here. This is deliberate: locale is data, not route
 * structure, in this app.
 */
const PROTECTED = [
  '/onboarding',
  '/rules',
  '/settings',
  '/triage',
  '/billing',
  '/needs-reply',
  '/analytics',
];

function setLocaleCookie(response: NextResponse, value: (typeof routing.locales)[number]) {
  response.cookies.set(NEXT_LOCALE_COOKIE, value, {
    maxAge: LOCALE_COOKIE_MAX_AGE,
    sameSite: 'lax',
    secure: true,
    path: '/',
  });
}

function ensureLocaleCookie(request: NextRequest, response: NextResponse): void {
  const current = request.cookies.get(NEXT_LOCALE_COOKIE)?.value;
  if ((routing.locales as readonly string[]).includes(current ?? '')) return;
  setLocaleCookie(response, routing.defaultLocale);
}

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

  const apiBase = getApiBase();
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

    setLocaleCookie(response, preferred);
  } catch {
    // Silent — never block navigation on a /me failure. Stale cookie wins.
  }
}

export default async function proxy(request: NextRequest): Promise<NextResponse> {
  const needsAuth = PROTECTED.some((p) => request.nextUrl.pathname.startsWith(p));
  if (needsAuth) {
    const session = request.cookies.get('ZEROMAIL_SESSION');
    if (!session) {
      const redirect = NextResponse.redirect(new URL('/login', request.url));
      ensureLocaleCookie(request, redirect);
      return redirect;
    }
  }

  const response = NextResponse.next();
  ensureLocaleCookie(request, response);

  // Reconcile NEXT_LOCALE with the server preference (authenticated users only).
  await reconcileLocaleCookie(request, response);

  return response;
}

export const config = {
  // Run on every page route except API, internals, and assets-with-dot.
  matcher: ['/((?!api|_next|_vercel|.*\\..*).*)'],
};
