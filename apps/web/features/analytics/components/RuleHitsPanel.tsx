'use client';

import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import type { RuleHitResponse } from '@/features/analytics/api/analytics-api';
import {
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
  const maxDecisions = Math.max(1, ...ruleHits.map((ruleHit) => safeCount(ruleHit.decisions)));

  return (
    <Card data-testid="analytics-rule-hits-panel" className={cn('bg-card/95 shadow-sm', className)}>
      <CardHeader>
        <CardDescription className="text-xs font-medium">
          {t('analytics.ruleHits.eyebrow')}
        </CardDescription>
        <CardTitle>
          <h3 className="text-base leading-snug font-semibold">{t('analytics.ruleHits.title')}</h3>
        </CardTitle>
      </CardHeader>
      <CardContent>
        {ruleHits.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t('analytics.ruleHits.empty')}</p>
        ) : (
          <>
            <div className="hidden overflow-hidden rounded-lg border md:block">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('analytics.ruleHits.column.rule')}</TableHead>
                    <TableHead className="text-right">
                      {t('analytics.ruleHits.column.decisions')}
                    </TableHead>
                    <TableHead className="text-right">
                      {t('analytics.ruleHits.column.applied')}
                    </TableHead>
                    <TableHead className="text-right">
                      {t('analytics.ruleHits.column.reverted')}
                    </TableHead>
                    <TableHead className="min-w-[9rem]">
                      {t('analytics.ruleHits.column.precision')}
                    </TableHead>
                    <TableHead className="text-right">
                      {t('analytics.ruleHits.column.trust')}
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {ruleHits.map((ruleHit, index) => {
                    const ruleName = ruleHit.ruleName ?? '';
                    const decisions = safeCount(ruleHit.decisions);
                    const applied = safeCount(ruleHit.applied);
                    const reverted = safeCount(ruleHit.reverted);
                    const precision = rulePrecision(ruleHit);
                    return (
                      <TableRow key={`${ruleName}-${index}`} data-testid="rule-hit-table-row">
                        <TableCell className="max-w-[28ch] py-2">
                          <Tooltip>
                            <TooltipTrigger
                              render={<span className="block truncate text-sm font-medium" />}
                            >
                              {ruleName}
                            </TooltipTrigger>
                            <TooltipContent>{ruleName}</TooltipContent>
                          </Tooltip>
                        </TableCell>
                        <MetricCell value={decisions} />
                        <MetricCell value={applied} />
                        <MetricCell value={reverted} />
                        <TableCell className="py-2">
                          <PrecisionBar
                            precision={precision}
                            volumeRatio={percentOf(decisions, maxDecisions)}
                          />
                        </TableCell>
                        <TableCell className="py-2 text-right">
                          <TrustBadge level={trustLevel(precision)} />
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
            <div className="space-y-2 md:hidden" data-testid="rule-hit-card-list">
              {ruleHits.map((ruleHit, index) => {
                const ruleName = ruleHit.ruleName ?? '';
                const decisions = safeCount(ruleHit.decisions);
                const applied = safeCount(ruleHit.applied);
                const reverted = safeCount(ruleHit.reverted);
                const precision = rulePrecision(ruleHit);
                return (
                  <div key={`${ruleName}-${index}`} className="rounded-lg border p-3">
                    <Tooltip>
                      <TooltipTrigger render={<p className="truncate text-sm font-medium" />}>
                        {ruleName}
                      </TooltipTrigger>
                      <TooltipContent>{ruleName}</TooltipContent>
                    </Tooltip>
                    <dl className="mt-3 grid grid-cols-3 gap-2 text-xs">
                      <MetricDefinition
                        label={t('analytics.ruleHits.column.decisions')}
                        value={decisions}
                      />
                      <MetricDefinition
                        label={t('analytics.ruleHits.column.applied')}
                        value={applied}
                      />
                      <MetricDefinition
                        label={t('analytics.ruleHits.column.reverted')}
                        value={reverted}
                      />
                    </dl>
                    <div className="mt-3 flex items-center gap-3">
                      <div className="min-w-0 flex-1">
                        <PrecisionBar
                          precision={precision}
                          volumeRatio={percentOf(decisions, maxDecisions)}
                        />
                      </div>
                      <TrustBadge level={trustLevel(precision)} />
                    </div>
                  </div>
                );
              })}
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}

function MetricCell({ value }: { value: number }) {
  return (
    <TableCell className="text-foreground py-2 text-right text-xs tabular-nums">{value}</TableCell>
  );
}

function MetricDefinition({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-foreground mt-1 text-sm tabular-nums">{value}</dd>
    </div>
  );
}

function PrecisionBar({ precision, volumeRatio }: { precision: number; volumeRatio: number }) {
  const t = useTranslations();
  const width = `${Math.max(8, Math.round(precision * 100))}%`;
  const volumeWidth = `${Math.max(8, Math.round(volumeRatio * 100))}%`;

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between gap-2">
        <span className="text-muted-foreground text-xs">
          {t('analytics.ruleHits.column.precision')}
        </span>
        <span className="text-foreground text-xs tabular-nums">{formatPercent(precision)}</span>
      </div>
      <div className="bg-muted relative h-2 overflow-hidden rounded-full">
        <div
          className="absolute inset-y-0 left-0 rounded-full bg-[var(--chart-2)]/20"
          style={{ width: volumeWidth }}
          aria-hidden="true"
        />
        <div
          className="absolute inset-y-0 left-0 rounded-full bg-[var(--chart-1)]"
          style={{ width }}
          aria-hidden="true"
        />
      </div>
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
