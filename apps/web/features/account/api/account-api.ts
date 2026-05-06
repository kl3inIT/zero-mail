import { cache } from 'react';

import { api, xsrfHeader } from '@/lib/api/client';
import { getApiUrl } from '@/lib/api/base-url';
import type { components } from '@/lib/api/schema';

type ApiError = components['schemas']['ApiError'];

export interface CurrentUser {
  id?: string;
  userId?: string;
  tenantId?: string;
  email: string;
  preferredLanguage: 'vi' | 'en';
  onboardingStep?: string;
  triagePaused: boolean;
  gmailConnectionStatus: {
    status: 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING' | string;
    ingestionHealth: 'HEALTHY' | 'WATCH_UNHEALTHY' | 'HISTORY_LOST' | string;
    googleEmail: string | null;
  } | null;
}

export interface GetCurrentUserOptions {
  fetcher?: typeof fetch;
  signal?: AbortSignal;
  headers?: HeadersInit;
}

/**
 * Raw /me fetcher — isomorphic (Plan 04 Task 2 — D-B1, D-B4).
 *
 * Two execution paths:
 *   - RSC / proxy.ts: caller passes `headers: { cookie }` (and optionally a
 *     custom fetcher). Cookie forwarding is explicit because RSC `fetch()`
 *     does NOT auto-forward the request `Cookie` header.
 *   - CSR (TanStack Query hooks): no headers/fetcher; the credentialed
 *     openapi-fetch singleton handles cookies automatically.
 *
 * Throws on non-OK responses (status code only — never logs PII).
 *
 * RSC consumers should prefer getCurrentUserCached(cookieHeader) for
 * per-request dedupe. Client/TanStack callers use this directly (or via
 * the getCurrentUser backwards-compat alias).
 */
export async function fetchCurrentUser(opts: GetCurrentUserOptions = {}): Promise<CurrentUser> {
  const { fetcher, signal, headers } = opts;
  if (fetcher || headers) {
    const res = await (fetcher ?? fetch)(getApiUrl('/me'), {
      headers,
      cache: 'no-store',
      signal,
    });
    if (!res.ok) throw new Error(`/me failed: ${res.status}`);
    return res.json() as Promise<CurrentUser>;
  }
  const { data, error, response } = await api.GET('/me', { signal });
  if (error || !response.ok) throw error ?? new Error(`/me failed: ${response.status}`);
  return data as CurrentUser;
}

/**
 * Server-only cached /me fetch. Cache key = primitive cookie header string.
 *
 * Two calls with the SAME cookie string within one render pass dedupe to ONE
 * underlying fetch. Two calls with DIFFERENT cookie strings produce separate fetches.
 *
 * RSC callers MUST pass `(await cookies()).toString()` — NOT a constructed
 * `{ headers: { cookie } }` object, because React's cache() keys by argument
 * identity (reference equality for objects, VALUE equality for primitives).
 *
 * Phase 01.5 HIGH-2 review fix: previous design wrapped getCurrentUser(opts)
 * directly — fresh object arg per render → no real dedupe in RSC call graphs.
 */
export const getCurrentUserCached = cache(
  async (cookieHeader: string | undefined): Promise<CurrentUser> => {
    if (cookieHeader === undefined) {
      // No cookie context (unauthenticated path) — fall through to default fetch.
      return fetchCurrentUser();
    }
    return fetchCurrentUser({ headers: { cookie: cookieHeader } });
  },
);

/**
 * Backwards-compat alias for client/TanStack consumers (un-cached).
 * Server-side RSC consumers should prefer getCurrentUserCached(cookieHeader)
 * for per-request dedupe.
 */
export const getCurrentUser = fetchCurrentUser;

export async function deleteAccount(): Promise<void> {
  const { error, response } = await api.DELETE('/me/account', {
    headers: { ...xsrfHeader() },
  });
  if (error || !response.ok) {
    throw error ?? new Error(`/me/account DELETE failed: ${response.status}`);
  }
}

export async function updateLanguage(language: 'vi' | 'en') {
  const response = await api.PATCH('/me/language', {
    body: { language },
    headers: { 'Content-Type': 'application/json', ...xsrfHeader() },
  });
  if (response.error) {
    throw response.error as ApiError;
  }
  return response.data;
}
