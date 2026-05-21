import { NextResponse, type NextRequest } from 'next/server';

import { getCurrentUser } from '@/features/account/api/account-api';
import { getApiBase } from '@/lib/api/base-url';
import { LOCALE_COOKIE_MAX_AGE, NEXT_LOCALE_COOKIE, routing } from './i18n/routing';

/**
 * Next.js 16 proxy.ts (formerly middleware.ts).
 *
 * Composes three responsibilities (CONTEXT.md D-B4):
 *  1. NEXT_LOCALE cookie upkeep without next-intl's routing middleware. With
 *     `localePrefix: 'never'`, that middleware rewrites `/login` to hidden
 *     `/vi/login` internally, which requires an `app/[locale]` mirror tree.
 *     Phase 1.3 keeps clean route groups instead, so i18n/request.ts reads the
 *     cookie directly and this proxy only maintains the cookie value.
 *  2. Phase 1 auth gate — keeps app routes and onboarding behind the
 *     redirect-to-/login behavior when the ZEROMAIL_SESSION cookie is missing.
 *  3. CSP nonce generation + security headers (Next.js official nonce pattern).
 *     Per-request nonce powers strict-dynamic script-src; framework scripts
 *     pick the nonce up automatically via the `x-nonce` request header. Non-
 *     CSP headers (X-Frame-Options, Referrer-Policy, etc.) are also set here
 *     so the policy stays in one place — next.config.ts only carries the
 *     RFC 8288 Link headers for agent discovery.
 *
 * NO next-intl middleware here. This is deliberate: locale is data, not route
 * structure, in this app.
 */

/**
 * Build the per-request CSP. Notes:
 *  - script-src uses strict-dynamic + nonce (CSP3): nonced bootstrap scripts
 *    can dynamically load further scripts without each needing a nonce. This
 *    is the Next.js docs' recommended posture for App Router.
 *  - style-src keeps 'unsafe-inline' pragmatically — Tailwind v4 + shadcn +
 *    Sonner emit inline <style> tags that Next.js does not (yet) nonce reliably.
 *    Tightening this requires nonce wiring inside every primitive.
 *  - connect-src needs the backend API host explicitly when it lives on a
 *    different origin (e.g. https://api.zeromail.vn). Same-origin deployments
 *    are covered by 'self'.
 *  - frame-ancestors 'none' + X-Frame-Options DENY (defense in depth).
 *  - upgrade-insecure-requests forces http→https on any straggler URL.
 */
function buildCsp(nonce: string): string {
  const isDev = process.env.NODE_ENV === 'development';
  const apiOrigin = (() => {
    const raw = process.env.NEXT_PUBLIC_API_BASE?.trim();
    if (!raw) return '';
    try {
      return new URL(raw).origin;
    } catch {
      return '';
    }
  })();
  const connectExtras = [apiOrigin, isDev ? 'ws:' : ''].filter(Boolean).join(' ');
  const directives = [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isDev ? " 'unsafe-eval'" : ''}`,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: blob: https:",
    "font-src 'self' data:",
    `connect-src 'self'${connectExtras ? ` ${connectExtras}` : ''}`,
    "form-action 'self'",
    "frame-ancestors 'none'",
    "base-uri 'self'",
    "object-src 'none'",
    'upgrade-insecure-requests',
  ];
  return directives.join('; ');
}

const STATIC_SECURITY_HEADERS: Readonly<Record<string, string>> = {
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), browsing-topics=()',
  // HSTS is only meaningful over HTTPS; the reverse proxy in front of the VPS
  // terminates TLS so it's safe to set unconditionally.
  'Strict-Transport-Security': 'max-age=63072000; includeSubDomains; preload',
};

function applySecurityHeaders(response: NextResponse, csp: string): void {
  response.headers.set('Content-Security-Policy', csp);
  for (const [name, value] of Object.entries(STATIC_SECURITY_HEADERS)) {
    response.headers.set(name, value);
  }
}
const PROTECTED = [
  '/onboarding',
  '/chat',
  '/rules',
  '/settings',
  '/ai',
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
  const nonce = Buffer.from(crypto.randomUUID()).toString('base64');
  const csp = buildCsp(nonce);

  const needsAuth = PROTECTED.some((p) => request.nextUrl.pathname.startsWith(p));
  if (needsAuth) {
    const session = request.cookies.get('ZEROMAIL_SESSION');
    if (!session) {
      const redirect = NextResponse.redirect(new URL('/login', request.url));
      ensureLocaleCookie(request, redirect);
      applySecurityHeaders(redirect, csp);
      return redirect;
    }
  }

  // Inject the nonce + CSP into the REQUEST headers so Next.js's renderer can
  // pick the nonce up for framework <script>/<style> tags. The same CSP also
  // goes on the response so the browser actually enforces it.
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set('x-nonce', nonce);
  requestHeaders.set('Content-Security-Policy', csp);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  ensureLocaleCookie(request, response);

  // Reconcile NEXT_LOCALE with the server preference (authenticated users only).
  await reconcileLocaleCookie(request, response);

  applySecurityHeaders(response, csp);
  return response;
}

export const config = {
  // Run on every page route except API, internals, and assets-with-dot.
  matcher: ['/((?!api|_next|_vercel|.*\\..*).*)'],
};
