'use client';

import { useSuspenseQuery } from '@tanstack/react-query';

import {
  fetchAnalyticsSummary,
  type AnalyticsWindow,
} from '@/features/analytics/api/analytics-api';
import { analyticsKeys } from '@/features/analytics/query-keys';

// 2026 pattern: read-only data hook suspends — loading.tsx renders the
// AnalyticsSkeleton automatically, error.tsx catches throws. No isPending /
// isError checks needed at the call site.
export function useAnalyticsSummary(window: AnalyticsWindow) {
  return useSuspenseQuery({
    queryKey: analyticsKeys.summary(window),
    queryFn: () => fetchAnalyticsSummary(window),
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });
}
