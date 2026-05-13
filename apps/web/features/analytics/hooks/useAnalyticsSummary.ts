'use client';

import { useQuery } from '@tanstack/react-query';

import {
  fetchAnalyticsSummary,
  type AnalyticsWindow,
} from '@/features/analytics/api/analytics-api';
import { analyticsKeys } from '@/features/analytics/query-keys';

export function useAnalyticsSummary(window: AnalyticsWindow) {
  return useQuery({
    queryKey: analyticsKeys.summary(window),
    queryFn: () => fetchAnalyticsSummary(window),
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });
}
