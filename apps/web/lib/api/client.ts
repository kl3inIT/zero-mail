import createClient from 'openapi-fetch';

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
