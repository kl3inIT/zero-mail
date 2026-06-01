'use client';

import { useTranslations } from 'next-intl';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import type { AnalyticsWindow } from '@/features/analytics/api/analytics-api';
import { ChartInfoAction } from '@/features/analytics/components/ChartInfoAction';
import {
  formatPercent,
  formatTimeSaved,
  percentOf,
  safeCount,
  windowDays,
} from '@/features/analytics/components/analytics-visualization';
import { cn } from '@/lib/utils';

type TimeSavedPanelProps = {
  seconds?: number;
  applied?: number;
  window?: AnalyticsWindow;
  className?: string;
};

export function TimeSavedPanel({
  seconds,
  applied,
  window = '7d',
  className,
}: TimeSavedPanelProps) {
  const t = useTranslations();
  const duration = formatTimeSaved(seconds);
  const totalSeconds = safeCount(seconds);
  const empty = totalSeconds === 0;
  const days = windowDays(window);
  const averageMinutesPerDay = Math.round(totalSeconds / 60 / days);
  const appliedCount = safeCount(applied);
  const minutesPerAction =
    appliedCount > 0 ? Math.max(1, Math.round(totalSeconds / 60 / appliedCount)) : 0;
  const focusBlockRatio = percentOf(totalSeconds, 8 * 60 * 60);
  const ringDegrees = Math.round(focusBlockRatio * 360);

  return (
    <Card
      data-testid="analytics-time-saved-panel"
      className={cn('bg-card/95 shadow-sm', className)}
    >
      <CardHeader className="has-data-[slot=card-action]:grid-cols-[1fr_auto]">
        <CardDescription className="text-xs font-medium">
          {t('analytics.timeSaved.eyebrow')}
        </CardDescription>
        <CardTitle>
          <h3 className="text-base leading-snug font-semibold">{t('analytics.timeSaved.title')}</h3>
        </CardTitle>
        <ChartInfoAction
          title={t('analytics.timeSaved.title')}
          description={t('analytics.timeSaved.tooltip')}
        />
      </CardHeader>
      <CardContent className="grid gap-5 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
        <div className="space-y-4">
          <p className="text-foreground text-[34px] leading-none font-semibold tabular-nums">
            <span aria-hidden="true">{duration.label}</span>
            <span className="sr-only">
              {t('analytics.timeSaved.screenReader', {
                hours: duration.hours,
                minutes: duration.minutes,
              })}
            </span>
          </p>

          <div className="grid grid-cols-2 gap-2">
            <TimeStat
              label={t('analytics.timeSaved.averagePerDay')}
              value={`${averageMinutesPerDay}m`}
            />
            <TimeStat
              label={t('analytics.timeSaved.perAction')}
              value={minutesPerAction === 0 ? '0m' : `${minutesPerAction}m`}
            />
          </div>

          {empty ? (
            <p className="text-muted-foreground text-sm">{t('analytics.timeSaved.empty')}</p>
          ) : null}
        </div>

        <div className="grid justify-center">
          <div
            className="relative grid size-28 place-items-center rounded-full p-2"
            style={{
              background: `conic-gradient(var(--chart-5) ${ringDegrees}deg, color-mix(in srgb, var(--chart-5) 14%, transparent) 0deg)`,
            }}
            aria-hidden="true"
          >
            <div className="bg-card grid size-full place-items-center rounded-full text-center shadow-inner">
              <div>
                <p className="text-xl font-semibold tabular-nums">
                  {formatPercent(focusBlockRatio)}
                </p>
                <p className="text-muted-foreground px-3 text-[11px] leading-tight">
                  {t('analytics.timeSaved.focusBlock')}
                </p>
              </div>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function TimeStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-muted/60 rounded-lg px-3 py-2">
      <p className="text-muted-foreground text-[11px] leading-tight">{label}</p>
      <p className="text-foreground mt-1 text-lg font-semibold tabular-nums">{value}</p>
    </div>
  );
}
