'use client';

import { useTranslations } from 'next-intl';

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

type RuleHitsPanelProps = {
  ruleHits?: RuleHitResponse[];
};

function safeCount(value: number | undefined): number {
  return Number.isFinite(value) ? Math.max(0, Math.trunc(value ?? 0)) : 0;
}

export function RuleHitsPanel({ ruleHits = [] }: RuleHitsPanelProps) {
  const t = useTranslations();

  return (
    <Card data-testid="analytics-rule-hits-panel">
      <CardHeader>
        <CardDescription className="font-mono text-[11px] font-medium tracking-[0.08em]">
          {t('analytics.ruleHits.eyebrow')}
        </CardDescription>
        <CardTitle>
          <h3 className="text-base leading-snug font-medium">{t('analytics.ruleHits.title')}</h3>
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
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {ruleHits.map((ruleHit, index) => {
                    const ruleName = ruleHit.ruleName ?? '';
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
                        <MetricCell value={safeCount(ruleHit.decisions)} />
                        <MetricCell value={safeCount(ruleHit.applied)} />
                        <MetricCell value={safeCount(ruleHit.reverted)} />
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
            <div className="space-y-2 md:hidden" data-testid="rule-hit-card-list">
              {ruleHits.map((ruleHit, index) => {
                const ruleName = ruleHit.ruleName ?? '';
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
                        value={safeCount(ruleHit.decisions)}
                      />
                      <MetricDefinition
                        label={t('analytics.ruleHits.column.applied')}
                        value={safeCount(ruleHit.applied)}
                      />
                      <MetricDefinition
                        label={t('analytics.ruleHits.column.reverted')}
                        value={safeCount(ruleHit.reverted)}
                      />
                    </dl>
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
    <TableCell className="text-foreground py-2 text-right font-mono text-xs tabular-nums">
      {value}
    </TableCell>
  );
}

function MetricDefinition({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-foreground mt-1 font-mono text-sm tabular-nums">{value}</dd>
    </div>
  );
}
