import { api } from '@/lib/api/client';
import type { components } from '@/lib/api/schema';

export type AnalyticsWindow = '7d' | '30d' | '90d';
type GeneratedAnalyticsSummaryResponse = components['schemas']['AnalyticsSummaryResponse'];
export type TopSenderResponse = components['schemas']['TopSenderResponse'];
export type RuleHitResponse = components['schemas']['RuleHitResponse'];

export type DailyLoadResponse = {
  day?: string;
  observed?: number;
  applied?: number;
  reverted?: number;
};

export type ActionMixResponse = {
  actionType?: string;
  applied?: number;
  reverted?: number;
  failed?: number;
};

export type DomainLoadResponse = {
  domain?: string;
  count?: number;
};

export type CategoryLoadResponse = {
  category?: string;
  count?: number;
};

export type ReplyBucketResponse = {
  bucket?: string;
  count?: number;
  withDraft?: number;
};

export type AutomationOpportunityResponse = {
  noRuleMatched?: number;
  failedActions?: number;
  pendingActions?: number;
};

export type AnalyticsSummaryResponse = GeneratedAnalyticsSummaryResponse & {
  dailyLoad?: DailyLoadResponse[];
  actionMix?: ActionMixResponse[];
  domainLoad?: DomainLoadResponse[];
  categoryLoad?: CategoryLoadResponse[];
  replyBuckets?: ReplyBucketResponse[];
  automationOpportunities?: AutomationOpportunityResponse;
};

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

  return unwrap(
    result,
    `/api/analytics/summary failed: ${result.response.status}`,
  ) as AnalyticsSummaryResponse;
}
