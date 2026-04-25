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
 */
const handleI18n = createIntlMiddleware(routing);

const PROTECTED = ['/onboarding', '/settings'];

export default function proxy(request: NextRequest): NextResponse {
  const intlResponse = handleI18n(request);

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
