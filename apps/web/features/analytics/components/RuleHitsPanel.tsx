'use client';

import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import type { RuleHitResponse } from '@/features/analytics/api/analytics-api';
import { ChartInfoAction } from '@/features/analytics/components/ChartInfoAction';
import {
  formatCompactCount,
  formatPercent,
  percentOf,
  rulePrecision,
  safeCount,
  trustLevel,
  type TrustLevel,
} from '@/features/analytics/components/analytics-visualization';
import { cn } from '@/lib/utils';

type RuleHitsPanelProps = {
  ruleHits?: RuleHitResponse[];
  className?: string;
};

export function RuleHitsPanel({ ruleHits = [], className }: RuleHitsPanelProps) {
  const t = useTranslations();
  const totalDecisions = ruleHits.reduce((sum, ruleHit) => sum + safeCount(ruleHit.decisions), 0);
  const totalApplied = ruleHits.reduce((sum, ruleHit) => sum + safeCount(ruleHit.applied), 0);
  const totalReverted = ruleHits.reduce((sum, ruleHit) => sum + safeCount(ruleHit.reverted), 0);

  return (
    <Card
      data-testid="analytics-rule-hits-panel"
      className={cn('border-border rounded-xl shadow-none', className)}
    >
      <CardHeader className="gap-1.5">
        <CardDescription className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
          {t('analytics.ruleHits.eyebrow')}
        </CardDescription>
        <CardTitle>
          <h3 className="text-lg leading-snug font-semibold">{t('analytics.ruleHits.title')}</h3>
        </CardTitle>
        <ChartInfoAction
          title={t('analytics.ruleHits.title')}
          description={t('analytics.ruleHits.tooltip')}
        />
      </CardHeader>
      <CardContent>
        {ruleHits.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t('analytics.ruleHits.empty')}</p>
        ) : (
          <div className="flex flex-col gap-5">
            <div className="border-border bg-muted/30 grid overflow-hidden rounded-lg border md:grid-cols-3">
              <RuleSummaryStat
                label={t('analytics.ruleHits.column.decisions')}
                value={totalDecisions}
              />
              <RuleSummaryStat
                label={t('analytics.ruleHits.column.applied')}
                value={totalApplied}
                tone="success"
              />
              <RuleSummaryStat
                label={t('analytics.ruleHits.column.reverted')}
                value={totalReverted}
                tone="danger"
              />
            </div>

            <div className="flex flex-col gap-3" data-testid="rule-hit-card-list">
              {ruleHits.map((ruleHit, index) => (
                <RuleTrustRow key={`${ruleHit.ruleName ?? ''}-${index}`} ruleHit={ruleHit} />
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function RuleSummaryStat({
  label,
  value,
  tone = 'neutral',
}: {
  label: string;
  value: number;
  tone?: 'neutral' | 'success' | 'danger';
}) {
  const toneClassName =
    tone === 'success' ? 'bg-(--chart-1)' : tone === 'danger' ? 'bg-(--red)' : 'bg-foreground/25';

  return (
    <div className="border-foreground/10 flex min-w-0 items-center gap-3 border-b px-4 py-3 last:border-b-0 md:border-r md:border-b-0 md:last:border-r-0">
      <span className={cn('h-8 w-1 shrink-0 rounded-full', toneClassName)} />
      <span className="min-w-0">
        <span className="text-muted-foreground block truncate text-xs font-medium">{label}</span>
        <span className="text-foreground block text-xl leading-tight font-semibold tabular-nums">
          {formatCompactCount(value)}
        </span>
      </span>
    </div>
  );
}

function RuleTrustRow({ ruleHit }: { ruleHit: RuleHitResponse }) {
  const t = useTranslations();
  const ruleName = ruleHit.ruleName ?? '';
  const decisions = safeCount(ruleHit.decisions);
  const applied = safeCount(ruleHit.applied);
  const reverted = safeCount(ruleHit.reverted);
  const precision = rulePrecision(ruleHit);
  const revertedRatio = percentOf(reverted, decisions);
  const trustedRatio = Math.max(0, precision - revertedRatio);
  const appliedWidth = trustedRatio > 0 ? `${Math.max(6, Math.round(trustedRatio * 100))}%` : '0%';
  const revertedWidth = revertedRatio > 0 ? `${Math.round(revertedRatio * 100)}%` : '0%';

  return (
    <div
      data-testid="rule-hit-table-row"
      className="border-border bg-muted/30 rounded-lg border p-3"
    >
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(16rem,28rem)_auto] lg:items-center">
        <div className="min-w-0">
          <Tooltip>
            <TooltipTrigger render={<p className="truncate text-sm font-semibold" />}>
              {ruleName}
            </TooltipTrigger>
            <TooltipContent>{ruleName}</TooltipContent>
          </Tooltip>
          <p className="text-muted-foreground mt-1 text-xs">
            {formatCompactCount(decisions)} {t('analytics.ruleHits.column.decisions')}
          </p>
        </div>

        <div className="min-w-0">
          <div className="mb-2 flex items-center justify-between gap-3">
            <span className="text-muted-foreground text-xs">
              {t('analytics.ruleHits.column.precision')}
            </span>
            <span className="text-sm font-semibold tabular-nums">{formatPercent(precision)}</span>
          </div>
          <div className="bg-background flex h-4 overflow-hidden rounded-full">
            <div className="bg-(--chart-1)" style={{ width: appliedWidth }} aria-hidden="true" />
            {reverted > 0 ? (
              <div className="bg-(--red)" style={{ width: revertedWidth }} aria-hidden="true" />
            ) : null}
          </div>
        </div>

        <div className="grid grid-cols-3 gap-2 lg:w-64">
          <RuleMiniStat label={t('analytics.ruleHits.column.applied')} value={applied} />
          <RuleMiniStat label={t('analytics.ruleHits.column.reverted')} value={reverted} />
          <div className="flex items-center justify-end">
            <TrustBadge level={trustLevel(precision)} />
          </div>
        </div>
      </div>
    </div>
  );
}

function RuleMiniStat({ label, value }: { label: string; value: number }) {
  return (
    <div className="min-w-0">
      <p className="text-muted-foreground truncate text-[11px]">{label}</p>
      <p className="text-foreground mt-1 text-sm font-semibold tabular-nums">
        {formatCompactCount(value)}
      </p>
    </div>
  );
}

function TrustBadge({ level }: { level: TrustLevel }) {
  const t = useTranslations();
  const className =
    level === 'high'
      ? 'bg-green-soft text-green'
      : level === 'medium'
        ? 'bg-amber-soft text-amber'
        : 'bg-red-soft text-red';

  return (
    <Badge variant="secondary" className={cn('tabular-nums', className)}>
      {t(`analytics.ruleHits.trust.${level}`)}
    </Badge>
  );
}
