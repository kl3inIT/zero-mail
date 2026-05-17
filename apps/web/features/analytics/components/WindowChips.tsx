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
      className="w-full sm:w-auto"
      onValueChange={(nextValue) => {
        if (typeof nextValue === 'string' && isAnalyticsWindow(nextValue)) {
          onChange(nextValue);
        }
      }}
    >
      <TabsList
        aria-label={t('analytics.page.title')}
        className="!grid !h-auto w-full grid-cols-3 gap-1 p-1 sm:w-fit"
      >
        {WINDOWS.map((window) => {
          const fullLabel = t(`analytics.window.${window}`);
          return (
            <TabsTrigger
              key={window}
              value={window}
              aria-label={fullLabel}
              className="h-9 min-w-0 px-2 sm:min-w-28 sm:px-4"
            >
              <span aria-hidden="true" className="sm:hidden">
                {window}
              </span>
              <span className="hidden sm:inline">{fullLabel}</span>
            </TabsTrigger>
          );
        })}
      </TabsList>
    </Tabs>
  );
}
