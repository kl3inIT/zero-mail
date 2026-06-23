import { type NextRequest } from 'next/server';

import { NEXT_LOCALE_COOKIE } from '@/i18n/routing';
import { estimateTokens, htmlToMarkdown } from '@/lib/markdown/html-to-markdown';

/**
 * Markdown content-negotiation endpoint (Next.js "Backend for Frontend" pattern).
 *
 * `next.config.ts` rewrites public marketing/blog/docs URLs here ONLY when the
 * request carries `Accept: text/markdown`; browsers (which never send it) keep
 * getting the normal HTML page. This handler re-fetches the page's own rendered
 * HTML over loopback and returns a markdown rendition with `Vary: Accept`.
 *
 * The inner fetch sends `Accept: text/html`, so it does NOT re-trigger the
 * rewrite — no recursion. No session cookie is forwarded (agents are anonymous;
 * only the language cookie is propagated so the markdown matches the locale).
 */

// Public surface that may be exposed as markdown. Anything else is 404'd so a
// direct `/md/<path>` probe can never convert a protected/authenticated page.
const ALLOWED_EXACT = new Set(['/', '/features', '/about', '/privacy', '/terms', '/blog', '/docs']);
const ALLOWED_PREFIXES = ['/blog/', '/docs/'];

function isAllowedPath(path: string): boolean {
  if (ALLOWED_EXACT.has(path)) return true;
  return ALLOWED_PREFIXES.some((prefix) => path.startsWith(prefix) && path.length > prefix.length);
}

function originalPathFrom(segments: string[] | undefined): string {
  if (!segments || segments.length === 0) return '/';
  return `/${segments.map(encodeURIComponent).join('/')}`;
}

// Route handlers are dynamic here: the markdown is derived from a runtime
// loopback render, so it must never be pre-rendered at build time (there is no
// server to fetch from during the build).
export const dynamic = 'force-dynamic';

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ path?: string[] }> },
): Promise<Response> {
  const { path } = await context.params;
  const originalPath = originalPathFrom(path);
  if (!isAllowedPath(originalPath)) {
    return new Response(null, { status: 404 });
  }

  const port = process.env.PORT?.trim() || '3000';
  const localeCookie = request.cookies.get(NEXT_LOCALE_COOKIE)?.value;

  let html: string;
  try {
    const upstream = await fetch(`http://127.0.0.1:${port}${originalPath}`, {
      headers: {
        accept: 'text/html',
        ...(localeCookie ? { cookie: `${NEXT_LOCALE_COOKIE}=${localeCookie}` } : {}),
      },
      // A public page must render as HTML directly; a redirect (e.g. to /login)
      // means the path is not really public — never convert that.
      redirect: 'manual',
      signal: AbortSignal.timeout(8000),
    });
    if (!upstream.ok) {
      return new Response(null, { status: upstream.status === 404 ? 404 : 502 });
    }
    html = await upstream.text();
  } catch {
    return new Response(null, { status: 504 });
  }

  const markdown = htmlToMarkdown(html);
  if (!markdown) {
    return new Response(null, { status: 502 });
  }

  return new Response(markdown, {
    headers: {
      'Content-Type': 'text/markdown; charset=utf-8',
      // Caches must key on Accept so a browser never receives the markdown body.
      Vary: 'Accept',
      // Marketing content changes rarely; let shared caches hold the conversion.
      'Cache-Control': 'public, s-maxage=3600, stale-while-revalidate=86400',
      'X-Markdown-Tokens': String(estimateTokens(markdown)),
    },
  });
}
