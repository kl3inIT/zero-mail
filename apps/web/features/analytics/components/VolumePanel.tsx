'use client';

import { useTranslations } from 'next-intl';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ChartInfoAction } from '@/features/analytics/components/ChartInfoAction';
import {
  formatCompactCount,
  formatPercent,
  percentOf,
  safeCount,
} from '@/features/analytics/components/analytics-visualization';
import { cn } from '@/lib/utils';

type VolumePanelProps = {
  observed?: number;
  applied?: number;
  className?: string;
};

export function VolumePanel({ observed, applied, className }: VolumePanelProps) {
  const t = useTranslations();
  const observedCount = safeCount(observed);
  const appliedCount = safeCount(applied);
  const untouchedCount = Math.max(0, observedCount - appliedCount);
  const triageRatio = percentOf(appliedCount, observedCount);
  const empty = observedCount === 0 && appliedCount === 0;
  const ringDegrees = Math.round(triageRatio * 360);
  const appliedWidth = `${Math.round(triageRatio * 100)}%`;

  return (
    <Card data-testid="analytics-volume-panel" className={cn('bg-card/95 shadow-sm', className)}>
      <CardHeader className="has-data-[slot=card-action]:grid-cols-[1fr_auto]">
        <CardDescription className="text-xs font-medium">
          {t('analytics.volume.eyebrow')}
        </CardDescription>
        <CardTitle>
          <h3 className="text-base leading-snug font-semibold">{t('analytics.volume.title')}</h3>
        </CardTitle>
        <ChartInfoAction
          title={t('analytics.volume.title')}
          description={t('analytics.volume.tooltip')}
        />
      </CardHeader>
      <CardContent className="grid gap-5 md:grid-cols-[auto_minmax(0,1fr)] md:items-center">
        <div className="grid justify-center">
          <div
            className="relative grid size-36 place-items-center rounded-full p-3"
            style={{
              background: `conic-gradient(var(--chart-1) ${ringDegrees}deg, color-mix(in srgb, var(--chart-1) 12%, transparent) 0deg)`,
            }}
            aria-hidden="true"
          >
            <div className="bg-card grid size-full place-items-center rounded-full text-center shadow-inner">
              <div>
                <p className="text-3xl leading-none font-semibold tabular-nums">
                  {formatPercent(triageRatio)}
                </p>
                <p className="text-muted-foreground mt-1 text-xs">
                  {t('analytics.volume.triageRate')}
                </p>
              </div>
            </div>
          </div>
        </div>

        <div className="min-w-0 space-y-4">
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
            <VolumeStat label={t('analytics.volume.appliedLabel')} value={appliedCount} />
            <VolumeStat label={t('analytics.volume.observedLabel')} value={observedCount} />
            <VolumeStat label={t('analytics.volume.untouchedLabel')} value={untouchedCount} />
          </div>

          <div className="space-y-2">
            <div className="bg-muted h-3 overflow-hidden rounded-full">
              <div
                className="h-full rounded-full bg-(--chart-1)"
                style={{ width: appliedWidth }}
                aria-hidden="true"
              />
            </div>
            <div className="text-muted-foreground flex justify-between gap-3 text-xs">
              <span>{t('analytics.volume.appliedLabel')}</span>
              <span>{t('analytics.volume.untouchedLabel')}</span>
            </div>
          </div>

          <p className="text-muted-foreground text-sm leading-6">
            {empty
              ? t('analytics.volume.empty')
              : t('analytics.volume.supplementary', {
                  applied: appliedCount,
                  observed: observedCount,
                })}
          </p>
        </div>
      </CardContent>
    </Card>
  );
}

function VolumeStat({ label, value }: { label: string; value: number }) {
  return (
    <div className="bg-muted/60 rounded-lg px-3 py-2">
      <p className="text-muted-foreground text-[11px] leading-tight">{label}</p>
      <p className="text-foreground mt-1 text-lg font-semibold tabular-nums">
        {formatCompactCount(value)}
      </p>
    </div>
  );
}
