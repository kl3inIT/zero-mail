import { NextResponse, type NextRequest } from 'next/server';

/**
 * Resolve the public origin behind the nginx-proxy-manager reverse proxy.
 *
 * Next.js 16 standalone builds `request.url` and `request.nextUrl.origin` from
 * the internal bind address (HOSTNAME=0.0.0.0:3000 in the production
 * container). Using either for a redirect Location header sends the browser to
 * `https://0.0.0.0:3000/` — the symptom seen when clicking the theme or
 * locale toggle on production (form-POST to `/actions/*` returns 303 with the
 * wrong Location).
 *
 * NPM forwards `Host: $host` + `X-Forwarded-Proto: $scheme` per the live
 * proxy config (see /data/nginx/proxy_host/*.conf). Honor `X-Forwarded-Host`
 * first (multi-hop proxy convention), then fall back to the `host` header.
 */
function getPublicOrigin(request: NextRequest): string {
  const forwardedHost = request.headers.get('x-forwarded-host');
  const hostHeader = forwardedHost ?? request.headers.get('host');
  if (hostHeader) {
    const proto = request.headers.get('x-forwarded-proto') ?? 'https';
    return `${proto}://${hostHeader}`;
  }
  return request.nextUrl.origin;
}

export function redirectBack(request: NextRequest): NextResponse {
  const origin = getPublicOrigin(request);
  const referer = request.headers.get('referer');
  const fallback = new URL('/', origin);

  if (!referer) {
    return NextResponse.redirect(fallback, 303);
  }

  try {
    const target = new URL(referer);
    if (target.origin !== origin) {
      return NextResponse.redirect(fallback, 303);
    }
    return NextResponse.redirect(target, 303);
  } catch {
    return NextResponse.redirect(fallback, 303);
  }
}
