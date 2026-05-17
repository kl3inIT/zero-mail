'use client';

import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import type {
  ActionMixResponse,
  AutomationOpportunityResponse,
  ReplyBucketResponse,
} from '@/features/analytics/api/analytics-api';
import { ChartInfoAction } from '@/features/analytics/components/ChartInfoAction';
import {
  formatCompactCount,
  formatPercent,
  normalizeActionLabel,
  percentOf,
  replyBucketLabel,
  replyBucketTotal,
  safeCount,
  totalActionMix,
} from '@/features/analytics/components/analytics-visualization';
import { cn } from '@/lib/utils';

type MetadataControlPanelProps = {
  actionMix?: ActionMixResponse[];
  replyBuckets?: ReplyBucketResponse[];
  automationOpportunities?: AutomationOpportunityResponse;
  className?: string;
};

type ActionPoint = {
  label: string;
  applied: number;
  reverted: number;
  failed: number;
  total: number;
};

export function MetadataControlPanel({
  actionMix = [],
  replyBuckets = [],
  automationOpportunities,
  className,
}: MetadataControlPanelProps) {
  const t = useTranslations();
  const totalActions = totalActionMix(actionMix);
  const actionPoints = actionMix.slice(0, 5).map((action) => {
    const applied = safeCount(action.applied);
    const reverted = safeCount(action.reverted);
    const failed = safeCount(action.failed);

    return {
      label: normalizeActionLabel(action.actionType),
      applied,
      reverted,
      failed,
      total: applied + reverted + failed,
    };
  });
  const totalApplied = actionPoints.reduce((sum, action) => sum + action.applied, 0);
  const totalReverted = actionPoints.reduce((sum, action) => sum + action.reverted, 0);
  const totalFailed = actionPoints.reduce((sum, action) => sum + action.failed, 0);
  const replyTotal = replyBucketTotal(replyBuckets);
  const draftTotal = replyBuckets.reduce((sum, bucket) => sum + safeCount(bucket.withDraft), 0);
  const noRuleMatched = safeCount(automationOpportunities?.noRuleMatched);
  const failedActions = safeCount(automationOpportunities?.failedActions);
  const pendingActions = safeCount(automationOpportunities?.pendingActions);
  const opportunityTotal = noRuleMatched + failedActions + pendingActions;

  return (
    <div
      data-testid="analytics-metadata-control-panel"
      className={cn('grid grid-cols-1 gap-4 xl:grid-cols-12', className)}
    >
      <Card className="bg-card/95 shadow-sm xl:col-span-8">
        <CardHeader className="gap-1.5">
          <CardDescription className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
            {t('analytics.metadataControl.eyebrow')}
          </CardDescription>
          <CardTitle>
            <h3 className="text-lg leading-snug font-semibold">
              {t('analytics.metadataControl.actions')}
            </h3>
          </CardTitle>
          <ChartInfoAction
            title={t('analytics.metadataControl.actions')}
            description={t('analytics.metadataControl.actionsTooltip')}
          />
        </CardHeader>
        <CardContent className="flex min-w-0 flex-col gap-5">
          <div className="bg-muted/30 ring-foreground/10 grid overflow-hidden rounded-xl ring-1 sm:grid-cols-4">
            <MetricCell
              label={t('analytics.metadataControl.actionTotalShort')}
              value={totalActions}
            />
            <MetricCell
              label={t('analytics.metadataControl.appliedLegend')}
              value={totalApplied}
              tone="success"
            />
            <MetricCell
              label={t('analytics.metadataControl.revertedLegend')}
              value={totalReverted}
              tone="danger"
            />
            <MetricCell
              label={t('analytics.metadataControl.failedLegend')}
              value={totalFailed}
              tone="warning"
            />
          </div>

          {actionPoints.length > 0 ? (
            <div className="flex flex-col gap-3">
              {actionPoints.map((actionPoint) => (
                <ActionMixRow key={actionPoint.label} actionPoint={actionPoint} />
              ))}
            </div>
          ) : (
            <EmptyText>{t('analytics.metadataControl.actionsEmpty')}</EmptyText>
          )}
        </CardContent>
      </Card>

      <Card className="bg-card/95 shadow-sm xl:col-span-4">
        <CardHeader className="gap-1.5">
          <CardDescription className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
            {t('analytics.metadataControl.opportunitiesEyebrow')}
          </CardDescription>
          <CardTitle>
            <h3 className="text-lg leading-snug font-semibold">
              {t('analytics.metadataControl.opportunities')}
            </h3>
          </CardTitle>
          <ChartInfoAction
            title={t('analytics.metadataControl.opportunities')}
            description={t('analytics.metadataControl.opportunitiesTooltip')}
          />
        </CardHeader>
        <CardContent className="flex min-w-0 flex-col gap-5">
          <div className="bg-muted/30 ring-foreground/10 rounded-xl p-4 ring-1">
            <p className="text-muted-foreground text-xs font-medium">
              {t('analytics.metadataControl.tuningSummary')}
            </p>
            <div className="mt-2 flex items-end justify-between gap-3">
              <p className="text-4xl leading-none font-semibold tabular-nums">
                {formatCompactCount(opportunityTotal)}
              </p>
              <Badge variant={opportunityTotal > 0 ? 'secondary' : 'outline'}>
                {t('analytics.metadataControl.noRule')}
              </Badge>
            </div>
          </div>

          <OpportunityBar
            noRuleMatched={noRuleMatched}
            failedActions={failedActions}
            pendingActions={pendingActions}
            total={opportunityTotal}
          />

          <div className="flex flex-col gap-3">
            <OpportunityRow
              label={t('analytics.metadataControl.noRule')}
              value={noRuleMatched}
              total={opportunityTotal}
              tone="info"
            />
            <OpportunityRow
              label={t('analytics.metadataControl.failed')}
              value={failedActions}
              total={opportunityTotal}
              tone="danger"
            />
            <OpportunityRow
              label={t('analytics.metadataControl.pending')}
              value={pendingActions}
              total={opportunityTotal}
              tone="warning"
            />
          </div>
        </CardContent>
      </Card>

      <Card className="bg-card/95 shadow-sm xl:col-span-12">
        <CardHeader className="gap-1.5">
          <CardDescription className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
            {t('analytics.metadataControl.followUpEyebrow')}
          </CardDescription>
          <CardTitle>
            <h3 className="text-lg leading-snug font-semibold">
              {t('analytics.metadataControl.replyBuckets')}
            </h3>
          </CardTitle>
          <ChartInfoAction
            title={t('analytics.metadataControl.replyBuckets')}
            description={t('analytics.metadataControl.replyBucketsTooltip')}
          />
        </CardHeader>
        <CardContent className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_18rem]">
          <div className="bg-muted/30 ring-foreground/10 rounded-xl p-5 ring-1">
            {replyBuckets.length > 0 ? (
              <div className="flex flex-col gap-3">
                {replyBuckets.map((bucket) => (
                  <ReplyBucketRow
                    key={bucket.bucket ?? 'unknown'}
                    bucket={bucket}
                    total={replyTotal}
                  />
                ))}
              </div>
            ) : (
              <div className="flex min-h-32 flex-col justify-center gap-2">
                <p className="text-lg font-semibold">
                  {t('analytics.metadataControl.replyBuckets')}
                </p>
                <p className="text-muted-foreground text-sm">
                  {t('analytics.metadataControl.replyBucketsEmpty')}
                </p>
              </div>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3 xl:grid-cols-1">
            <FollowUpSummary
              label={t('analytics.metadataControl.replyTotal')}
              value={replyTotal}
              helper={t('analytics.metadataControl.replyBuckets')}
            />
            <FollowUpSummary
              label={t('analytics.metadataControl.draftTotal')}
              value={draftTotal}
              helper={t('analytics.metadataControl.replyDraftSummary', {
                count: formatCompactCount(draftTotal),
              })}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function MetricCell({
  label,
  value,
  tone = 'neutral',
}: {
  label: string;
  value: number;
  tone?: 'neutral' | 'success' | 'danger' | 'warning';
}) {
  const toneClassName =
    tone === 'success'
      ? 'bg-(--chart-1)'
      : tone === 'danger'
        ? 'bg-(--red)'
        : tone === 'warning'
          ? 'bg-(--amber)'
          : 'bg-foreground/25';

  return (
    <div className="border-foreground/10 flex min-w-0 items-center gap-3 border-b px-4 py-3 last:border-b-0 sm:border-r sm:border-b-0 sm:last:border-r-0">
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

function ActionMixRow({ actionPoint }: { actionPoint: ActionPoint }) {
  const t = useTranslations();
  const appliedRatio = percentOf(actionPoint.applied, actionPoint.total);
  const revertedRatio = percentOf(actionPoint.reverted, actionPoint.total);
  const failedRatio = percentOf(actionPoint.failed, actionPoint.total);

  return (
    <div className="bg-muted/30 ring-foreground/10 rounded-xl p-4 ring-1">
      <div className="flex min-w-0 items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{actionPoint.label}</p>
          <p className="text-muted-foreground mt-1 text-xs">
            {formatCompactCount(actionPoint.total)} {t('analytics.metadataControl.actions')}
          </p>
        </div>
        <Badge variant="secondary" className="tabular-nums">
          {formatPercent(appliedRatio)}
        </Badge>
      </div>

      <div className="bg-background mt-4 flex h-5 overflow-hidden rounded-full">
        <ActionSegment ratio={appliedRatio} className="bg-(--chart-1)" />
        <ActionSegment ratio={revertedRatio} className="bg-(--red)" />
        <ActionSegment ratio={failedRatio} className="bg-(--amber)" />
      </div>

      <div className="mt-3 grid grid-cols-3 gap-2 text-xs">
        <InlineStat
          label={t('analytics.metadataControl.appliedLegend')}
          value={actionPoint.applied}
          tone="success"
        />
        <InlineStat
          label={t('analytics.metadataControl.revertedLegend')}
          value={actionPoint.reverted}
          tone="danger"
        />
        <InlineStat
          label={t('analytics.metadataControl.failedLegend')}
          value={actionPoint.failed}
          tone="warning"
        />
      </div>
    </div>
  );
}

function ActionSegment({ ratio, className }: { ratio: number; className: string }) {
  if (ratio <= 0) {
    return null;
  }

  return <div className={className} style={{ width: `${Math.round(ratio * 100)}%` }} />;
}

function InlineStat({
  label,
  value,
  tone,
}: {
  label: string;
  value: number;
  tone: 'success' | 'danger' | 'warning';
}) {
  const toneClassName =
    tone === 'success'
      ? 'border-(--chart-1)'
      : tone === 'danger'
        ? 'border-(--red)'
        : 'border-(--amber)';

  return (
    <div className={cn('border-l-2 pl-2', toneClassName)}>
      <p className="text-muted-foreground truncate">{label}</p>
      <p className="text-foreground mt-0.5 font-semibold tabular-nums">
        {formatCompactCount(value)}
      </p>
    </div>
  );
}

function FollowUpSummary({
  label,
  value,
  helper,
}: {
  label: string;
  value: number;
  helper: string;
}) {
  return (
    <div className="bg-muted/30 ring-foreground/10 rounded-xl p-4 ring-1">
      <p className="text-muted-foreground truncate text-xs font-medium">{label}</p>
      <p className="text-foreground mt-1 text-3xl leading-none font-semibold tabular-nums">
        {formatCompactCount(value)}
      </p>
      <p className="text-muted-foreground mt-2 truncate text-[11px]">{helper}</p>
    </div>
  );
}

function ReplyBucketRow({ bucket, total }: { bucket: ReplyBucketResponse; total: number }) {
  const t = useTranslations();
  const count = safeCount(bucket.count);
  const withDraft = safeCount(bucket.withDraft);
  const width = `${Math.max(6, Math.round(percentOf(count, total) * 100))}%`;

  return (
    <div className="bg-background ring-foreground/10 rounded-lg px-3 py-2.5 ring-1">
      <div className="flex min-w-0 items-center justify-between gap-3">
        <span className="truncate text-sm font-medium">{replyBucketLabel(bucket.bucket)}</span>
        <span className="text-muted-foreground text-xs tabular-nums">
          {formatCompactCount(count)}
        </span>
      </div>
      <div className="bg-muted mt-2 h-2 overflow-hidden rounded-full">
        <div className="h-full rounded-full bg-(--chart-2)" style={{ width }} aria-hidden="true" />
      </div>
      <p className="text-muted-foreground mt-2 truncate text-xs">
        {withDraft > 0
          ? t('analytics.metadataControl.withDraft', {
              count: formatCompactCount(withDraft),
            })
          : t('analytics.metadataControl.noDraft')}
      </p>
    </div>
  );
}

function OpportunityBar({
  noRuleMatched,
  failedActions,
  pendingActions,
  total,
}: {
  noRuleMatched: number;
  failedActions: number;
  pendingActions: number;
  total: number;
}) {
  const noRuleWidth = `${Math.round(percentOf(noRuleMatched, total) * 100)}%`;
  const failedWidth = `${Math.round(percentOf(failedActions, total) * 100)}%`;
  const pendingWidth = `${Math.round(percentOf(pendingActions, total) * 100)}%`;

  return (
    <div className="bg-muted flex h-4 overflow-hidden rounded-full">
      <div className="bg-(--chart-2)" style={{ width: noRuleWidth }} aria-hidden="true" />
      <div className="bg-(--red)" style={{ width: failedWidth }} aria-hidden="true" />
      <div className="bg-(--amber)" style={{ width: pendingWidth }} aria-hidden="true" />
    </div>
  );
}

function OpportunityRow({
  label,
  value,
  total,
  tone,
}: {
  label: string;
  value: number;
  total: number;
  tone: 'info' | 'danger' | 'warning';
}) {
  const ratio = percentOf(value, total);
  const width = `${Math.max(6, Math.round(ratio * 100))}%`;
  const color =
    tone === 'info' ? 'var(--chart-2)' : tone === 'danger' ? 'var(--red)' : 'var(--amber)';

  return (
    <div className="flex flex-col gap-2">
      <div className="flex min-w-0 items-center justify-between gap-3">
        <span className="truncate text-sm font-medium">{label}</span>
        <span className="text-muted-foreground text-xs tabular-nums">
          {formatCompactCount(value)} · {formatPercent(ratio)}
        </span>
      </div>
      <div className="bg-muted h-2 overflow-hidden rounded-full">
        <div
          className="h-full rounded-full"
          style={{ width, background: color }}
          aria-hidden="true"
        />
      </div>
    </div>
  );
}

function EmptyText({ children }: { children: string }) {
  return <p className="text-muted-foreground text-sm">{children}</p>;
}
