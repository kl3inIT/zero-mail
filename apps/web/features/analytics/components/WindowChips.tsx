'use client';

import { useTranslations } from 'next-intl';

import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import type { AnalyticsWindow } from '@/features/analytics/api/analytics-api';

const WINDOWS: AnalyticsWindow[] = ['7d', '30d', '90d'];

export function isAnalyticsWindow(value: string): value is AnalyticsWindow {
  return WINDOWS.includes(value as AnalyticsWindow);
}

export function normalizeAnalyticsWindow(raw: string | null): AnalyticsWindow {
  const trimmed = raw?.trim();
  return trimmed && isAnalyticsWindow(trimmed) ? trimmed : '7d';
}

type WindowChipsProps = {
  value: AnalyticsWindow;
  onChange: (window: AnalyticsWindow) => void;
};

export function WindowChips({ value, onChange }: WindowChipsProps) {
  const t = useTranslations();

  return (
    <Tabs
      value={value}
      onValueChange={(nextValue) => {
        if (typeof nextValue === 'string' && isAnalyticsWindow(nextValue)) {
          onChange(nextValue);
        }
      }}
    >
      <TabsList
        aria-label={t('analytics.page.title')}
        className="max-w-full flex-wrap justify-start gap-1 p-1"
      >
        {WINDOWS.map((window) => (
          <TabsTrigger key={window} value={window} className="min-h-10 px-3 sm:px-4">
            {t(`analytics.window.${window}`)}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  );
}
