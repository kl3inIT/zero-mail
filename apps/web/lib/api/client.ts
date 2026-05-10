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

export function xsrfHeader(): HeadersInit {
  if (typeof document === 'undefined') return {};
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
}

// IMPORTANT: do NOT re-export from ./errors here. errors.ts is "use client"
// + uses next-intl hooks. RSC and proxy.ts code paths import only the
// server-safe symbols above. Client-only callers import directly from
// "@/lib/api/errors" (REVIEWS Revision 1, Codex HIGH #2 — Plan 04 Task 0).
