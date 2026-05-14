import type { AnalyticsWindow } from '@/features/analytics/api/analytics-api';

export const analyticsKeys = {
  all: ['analytics'] as const,
  summary: (window: AnalyticsWindow) => [...analyticsKeys.all, 'summary', window] as const,
} as const;
