import createClient from 'openapi-fetch';

import type { paths } from './schema';

const typedApi = createClient<paths>({
  baseUrl: process.env.NEXT_PUBLIC_API_BASE ?? 'http://localhost:8080',
  credentials: 'include',
});

// Loose-typed surface for the placeholder schema. Once `pnpm generate:api`
// produces real `paths`, route components automatically get strong typing
// via `typedApi`. The cast below keeps the call sites in routes ergonomic
// while the placeholder is in effect.
type LooseClient = {
  GET: (
    path: string,
    init?: Record<string, unknown>,
  ) => Promise<{ data: unknown; error?: unknown; response: Response }>;
  POST: (
    path: string,
    init?: Record<string, unknown>,
  ) => Promise<{ data: unknown; error?: unknown; response: Response }>;
  PUT: (
    path: string,
    init?: Record<string, unknown>,
  ) => Promise<{ data: unknown; error?: unknown; response: Response }>;
  DELETE: (
    path: string,
    init?: Record<string, unknown>,
  ) => Promise<{ data: unknown; error?: unknown; response: Response }>;
  PATCH: (
    path: string,
    init?: Record<string, unknown>,
  ) => Promise<{ data: unknown; error?: unknown; response: Response }>;
};

export const api = typedApi as unknown as LooseClient;

export function xsrfHeader(): HeadersInit {
  if (typeof document === 'undefined') return {};
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
}

// IMPORTANT: do NOT re-export from ./errors here. errors.ts is "use client"
// + uses next-intl hooks. RSC and proxy.ts code paths import only the
// server-safe symbols above. Client-only callers import directly from
// "@/lib/api/errors" (REVIEWS Revision 1, Codex HIGH #2 — Plan 04 Task 0).
