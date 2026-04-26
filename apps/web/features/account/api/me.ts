import { api } from '@/lib/api/client';
import { getApiUrl } from '@/lib/api/base-url';

export interface CurrentUser {
  id: string;
  email: string;
  preferredLanguage: 'vi' | 'en';
  onboardingStep?: string;
}

export interface GetCurrentUserOptions {
  fetcher?: typeof fetch;
  signal?: AbortSignal;
  headers?: HeadersInit;
}

/**
 * Isomorphic /me fetcher (Plan 04 Task 2 — D-B1, D-B4).
 *
 * Two execution paths:
 *   - RSC / proxy.ts: caller passes `headers: { cookie }` (and optionally a
 *     custom fetcher). Cookie forwarding is explicit because RSC `fetch()`
 *     does NOT auto-forward the request `Cookie` header.
 *   - CSR (TanStack Query hooks): no headers/fetcher; the credentialed
 *     openapi-fetch singleton handles cookies automatically.
 *
 * Throws on non-OK responses (status code only — never logs PII).
 */
export async function getCurrentUser(opts: GetCurrentUserOptions = {}): Promise<CurrentUser> {
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
