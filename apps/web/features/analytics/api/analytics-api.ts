import { api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type AnalyticsWindow = '7d' | '30d' | '90d';
export type AnalyticsSummaryResponse = components['schemas']['AnalyticsSummaryResponse'];
export type TopSenderResponse = components['schemas']['TopSenderResponse'];
export type RuleHitResponse = components['schemas']['RuleHitResponse'];

function unwrap<T>(
  result: { data?: T; error?: unknown; response: Response },
  fallbackMessage: string,
): T {
  if (result.error || !result.response.ok || result.data === undefined) {
    throw result.error ?? new Error(fallbackMessage);
  }
  return result.data;
}

export async function fetchAnalyticsSummary(
  window: AnalyticsWindow,
): Promise<AnalyticsSummaryResponse> {
  const result = await api.GET('/api/analytics/summary', {
    params: {
      query: { window },
    },
  });

  return unwrap(result, `/api/analytics/summary failed: ${result.response.status}`);
}
