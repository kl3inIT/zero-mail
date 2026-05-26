import { adaptFetchForOpenApi, api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type AnalyticsWindow = '7d' | '30d' | '90d';
export type AnalyticsSummaryResponse = components['schemas']['AnalyticsSummaryResponse'];
export type TopSenderResponse = components['schemas']['TopSenderResponse'];
export type RuleHitResponse = components['schemas']['RuleHitResponse'];
export type DailyLoadResponse = components['schemas']['DailyLoadResponse'];
export type ActionMixResponse = components['schemas']['ActionMixResponse'];
export type DomainLoadResponse = components['schemas']['DomainLoadResponse'];
export type CategoryLoadResponse = components['schemas']['CategoryLoadResponse'];
export type ReplyBucketResponse = components['schemas']['ReplyBucketResponse'];
export type AutomationOpportunityResponse = components['schemas']['AutomationOpportunityResponse'];

export interface FetchAnalyticsSummaryOptions {
  fetcher?: typeof fetch;
  signal?: AbortSignal;
  headers?: HeadersInit;
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

// RSC callers pass `headers: { cookie }` (and optionally a custom fetcher) so
// server-side fetch forwards the session cookie. CSR callers omit options —
// the credentialed openapi-fetch singleton handles cookies in the browser.
export async function fetchAnalyticsSummary(
  window: AnalyticsWindow,
  options: FetchAnalyticsSummaryOptions = {},
): Promise<AnalyticsSummaryResponse> {
  const { fetcher, signal, headers } = options;
  const result = await api.GET('/api/analytics/summary', {
    params: {
      query: { window },
    },
    cache: fetcher || headers ? 'no-store' : undefined,
    fetch: adaptFetchForOpenApi(fetcher ?? (headers ? fetch : undefined)),
    headers,
    signal,
  });

  return unwrap(result, `/api/analytics/summary failed: ${result.response.status}`);
}
