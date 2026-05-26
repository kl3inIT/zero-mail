import { cache } from 'react';

import { adaptFetchForOpenApi, api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

type ApiError = components['schemas']['ApiError'];
export type CurrentUser = components['schemas']['MeResponse'];

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
  // Playwright e2e short-circuit (server-side only). Catches every RSC /me
  // call site — proxy.ts middleware, app/layout.tsx, settings/page.tsx, landing
  // Hero/TopBar — so the default playwright config never logs ECONNREFUSED from
  // Next's internal fetch error reporter. Browser-side fetches keep going
  // because Playwright's page.route() mocks intercept them at the network
  // layer, so client React Query hooks still work without a real backend.
  if (typeof window === 'undefined' && process.env.ZM_E2E === '1') {
    throw new Error('ZM_E2E: skipping /me fetch in test mode');
  }
  const { fetcher, signal, headers } = opts;
  const { data, error, response } = await api.GET('/api/me', {
    cache: fetcher || headers ? 'no-store' : undefined,
    fetch: adaptFetchForOpenApi(fetcher ?? (headers ? fetch : undefined)),
    headers,
    signal,
  });
  if (error || !response.ok || data === undefined) {
    throw error ?? new Error(`/api/me failed: ${response.status}`);
  }
  return data;
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
    // Playwright e2e short-circuit. The default playwright config runs against
    // `next dev` with no Spring Boot backend, so every RSC /me fetch loses to
    // ECONNREFUSED and Next's error reporter logs it loudly before our try/catch
    // recovers. Skip the fetch entirely in that mode — callers all wrap this in
    // try/catch and fall back to cookie state (locale, theme) cleanly.
    // launch-golden-path uses playwright.golden.config.ts which runs a real
    // backend and does NOT set ZM_E2E, so that flow still hits /me normally.
    if (process.env.ZM_E2E === '1') {
      throw new Error('ZM_E2E: skipping /me fetch in test mode');
    }
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
  const { error, response } = await api.DELETE('/api/me/account', {});
  if (error || !response.ok) {
    throw error ?? new Error(`/api/me/account DELETE failed: ${response.status}`);
  }
}

export async function updateLanguage(language: 'vi' | 'en') {
  const response = await api.PATCH('/api/me/language', {
    body: { language },
    headers: { 'Content-Type': 'application/json' },
  });
  if (response.error) {
    throw response.error as ApiError;
  }
  return response.data;
}
