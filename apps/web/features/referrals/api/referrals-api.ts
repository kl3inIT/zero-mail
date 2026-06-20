'use client';

import { getApiUrl } from '@/lib/api/base-url';
import { adaptFetchForOpenApi, api, xsrfHeader } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type ReferralMeResponse = components['schemas']['ReferralMeResponse'];

export interface FetchReferralMeOptions {
  fetcher?: typeof fetch;
  headers?: HeadersInit;
  signal?: AbortSignal;
}

export interface EndReferralCampaignIfExpiredOptions {
  fetcher?: typeof fetch;
  headers?: HeadersInit;
  signal?: AbortSignal;
}

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export async function fetchReferralMe(
  options: FetchReferralMeOptions = {},
): Promise<ReferralMeResponse> {
  const { fetcher, headers, signal } = options;
  const result = await api.GET('/api/referrals/me', {
    cache: fetcher || headers ? 'no-store' : undefined,
    fetch: adaptFetchForOpenApi(fetcher ?? (headers ? fetch : undefined)),
    headers,
    signal,
  });
  return unwrap(result, `referral me failed: ${result.response.status}`);
}

export async function endReferralCampaignIfExpired(
  campaignId: string,
  options: EndReferralCampaignIfExpiredOptions = {},
): Promise<void> {
  const { fetcher = fetch, headers, signal } = options;
  const requestHeaders = new Headers(xsrfHeader());
  if (headers) {
    new Headers(headers).forEach((value, key) => requestHeaders.set(key, value));
  }
  const response = await fetcher(
    getApiUrl(`/api/referrals/campaigns/${encodeURIComponent(campaignId)}/end-if-expired`),
    {
      credentials: 'include',
      headers: requestHeaders,
      method: 'POST',
      signal,
    },
  );
  if (!response.ok) {
    throw new Error(`referral campaign expiry failed: ${response.status}`);
  }
}
