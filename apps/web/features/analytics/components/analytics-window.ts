import type { AnalyticsWindow } from '@/features/analytics/api/analytics-api';

export const ANALYTICS_WINDOWS: AnalyticsWindow[] = ['7d', '30d', '90d'];

export function isAnalyticsWindow(value: string): value is AnalyticsWindow {
  return ANALYTICS_WINDOWS.includes(value as AnalyticsWindow);
}

export function normalizeAnalyticsWindow(raw: string | null | undefined): AnalyticsWindow {
  const trimmed = raw?.trim();
  return trimmed && isAnalyticsWindow(trimmed) ? trimmed : '7d';
}
