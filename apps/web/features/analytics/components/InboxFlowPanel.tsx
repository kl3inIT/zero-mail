'use client';

import { useTranslations } from 'next-intl';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import type { RuleHitResponse } from '@/features/analytics/api/analytics-api';
import {
  formatCompactCount,
  formatPercent,
  percentOf,
  safeCount,
  totalReverted,
} from '@/features/analytics/components/analytics-visualization';
import { cn } from '@/lib/utils';

type InboxFlowPanelProps = {
  observed?: number;
  applied?: number;
  ruleHits?: RuleHitResponse[];
  className?: string;
};

export function InboxFlowPanel({ observed, applied, ruleHits, className }: InboxFlowPanelProps) {
  const t = useTranslations();
  const observedCount = safeCount(observed);
  const appliedCount = safeCount(applied);
  const revertedCount = Math.min(appliedCount, totalReverted(ruleHits));
  const automatedCount = Math.max(0, appliedCount - revertedCount);
  const untouchedCount = Math.max(0, observedCount - appliedCount);
  const automatedRatio = percentOf(automatedCount, observedCount);
  const revertedRatio = percentOf(revertedCount, observedCount);
  const untouchedRatio = percentOf(untouchedCount, observedCount);
  const ringDegrees = Math.round(percentOf(appliedCount, observedCount) * 360);

  return (
    <Card
      data-testid="analytics-inbox-flow-panel"
      className={cn('bg-card/95 shadow-sm', className)}
    >
      <CardHeader>
        <CardDescription className="text-xs font-medium">
          {t('analytics.inboxFlow.eyebrow')}
        </CardDescription>
        <CardTitle>
          <h3 className="text-base leading-snug font-semibold">{t('analytics.inboxFlow.title')}</h3>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="grid gap-4 sm:grid-cols-[auto_minmax(0,1fr)] sm:items-center">
          <div
            className="relative grid size-28 place-items-center rounded-full p-2"
            style={{
              background: `conic-gradient(var(--chart-1) ${ringDegrees}deg, color-mix(in srgb, var(--chart-1) 12%, transparent) 0deg)`,
            }}
            aria-hidden="true"
          >
            <div className="bg-card grid size-full place-items-center rounded-full text-center shadow-inner">
              <div>
                <p className="text-2xl font-semibold tabular-nums">
                  {formatPercent(percentOf(appliedCount, observedCount))}
                </p>
                <p className="text-muted-foreground px-3 text-[11px] leading-tight">
                  {t('analytics.inboxFlow.coverage')}
                </p>
              </div>
            </div>
          </div>

          <div className="min-w-0 space-y-3">
            <div className="bg-muted flex h-4 overflow-hidden rounded-full">
              <div
                className="bg-[var(--chart-1)]"
                style={{ width: `${Math.round(automatedRatio * 100)}%` }}
                aria-hidden="true"
              />
              <div
                className="bg-[var(--chart-5)]"
                style={{ width: `${Math.round(revertedRatio * 100)}%` }}
                aria-hidden="true"
              />
              <div
                className="bg-muted-foreground/20"
                style={{ width: `${Math.round(untouchedRatio * 100)}%` }}
                aria-hidden="true"
              />
            </div>
            <div className="grid grid-cols-1 gap-2 text-xs sm:grid-cols-3">
              <FlowLegend
                color="bg-[var(--chart-1)]"
                label={t('analytics.inboxFlow.automated')}
                value={automatedCount}
              />
              <FlowLegend
                color="bg-[var(--chart-5)]"
                label={t('analytics.inboxFlow.reverted')}
                value={revertedCount}
              />
              <FlowLegend
                color="bg-muted-foreground/30"
                label={t('analytics.inboxFlow.untouched')}
                value={untouchedCount}
              />
            </div>
          </div>
        </div>

        <p className="text-muted-foreground text-sm leading-6">
          {observedCount === 0
            ? t('analytics.inboxFlow.empty')
            : t('analytics.inboxFlow.summary', {
                automated: automatedCount,
                observed: observedCount,
                reverted: revertedCount,
              })}
        </p>
      </CardContent>
    </Card>
  );
}

function FlowLegend({ color, label, value }: { color: string; label: string; value: number }) {
  return (
    <div className="min-w-0">
      <div className="flex items-center gap-1.5">
        <span className={cn('size-2 rounded-full', color)} aria-hidden="true" />
        <span className="text-muted-foreground">{label}</span>
      </div>
      <p className="text-foreground mt-1 text-sm font-semibold tabular-nums">
        {formatCompactCount(value)}
      </p>
    </div>
  );
}
