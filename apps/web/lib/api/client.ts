import createClient, { type Middleware } from 'openapi-fetch';

import { getApiBase } from './base-url';
import type { paths } from './schema';

// Real, typed openapi-fetch client. The earlier LooseClient cast was a
// placeholder from when `schema.d.ts` had no `paths` and route components
// needed an ergonomic untyped surface. `pnpm generate:api` now produces
// a full `paths` map, so the cast is no longer needed and was actively
// stripping path-parameter validation and request-body shape checking
// from every callsite (REVIEW WR-10).
export const api = createClient<paths>({
  baseUrl: getApiBase(),
  credentials: 'include',
});

// 401 → hard redirect to /login. Runs at fetch boundary so every caller
// (queries, mutations, server actions invoking client.ts on the client)
// gets the same behavior without per-callsite checks. Skip when already
// on a public auth surface to avoid redirect loops.
const PUBLIC_AUTH_PATHS = ['/login', '/error', '/privacy', '/terms', '/docs'];

const unauthorizedRedirectMiddleware: Middleware = {
  async onResponse({ response }) {
    if (response.status !== 401 || typeof window === 'undefined') return undefined;
    const path = window.location.pathname;
    if (PUBLIC_AUTH_PATHS.some((publicPath) => path.startsWith(publicPath))) return undefined;
    window.location.assign('/login');
    return undefined;
  },
};

api.use(unauthorizedRedirectMiddleware);

export function adaptFetchForOpenApi(
  fetcher: typeof fetch | undefined,
): ((request: Request) => Promise<Response>) | undefined {
  if (!fetcher) return undefined;
  return async (request) => {
    const response = await fetcher(request.url, {
      body: request.body,
      cache: request.cache,
      credentials: request.credentials,
      headers: Object.fromEntries(request.headers.entries()),
      integrity: request.integrity,
      keepalive: request.keepalive,
      method: request.method,
      mode: request.mode,
      redirect: request.redirect,
      referrer: request.referrer,
      referrerPolicy: request.referrerPolicy,
      signal: request.signal,
    });
    if (!response.headers) {
      Object.assign(response, { headers: new Headers() });
    }
    if (!response.text && typeof response.json === 'function') {
      Object.assign(response, { text: async () => JSON.stringify(await response.json()) });
    }
    return response;
  };
}

export function xsrfHeader(): HeadersInit {
  if (typeof document === 'undefined') return {};
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
}

// IMPORTANT: do NOT re-export from ./errors here. errors.ts is "use client"
// + uses next-intl hooks. RSC and proxy.ts code paths import only the
// server-safe symbols above. Client-only callers import directly from
// "@/lib/api/errors" (REVIEWS Revision 1, Codex HIGH #2 — Plan 04 Task 0).
