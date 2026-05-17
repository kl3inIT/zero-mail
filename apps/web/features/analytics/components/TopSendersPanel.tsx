'use client';

import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import type { DomainLoadResponse, TopSenderResponse } from '@/features/analytics/api/analytics-api';
import { ChartInfoAction } from '@/features/analytics/components/ChartInfoAction';
import {
  formatCompactCount,
  percentOf,
  safeCount,
  topDomainLoad,
} from '@/features/analytics/components/analytics-visualization';
import { cn } from '@/lib/utils';

type TopSendersPanelProps = {
  senders?: TopSenderResponse[];
  domainLoad?: DomainLoadResponse[];
  className?: string;
};

function senderCount(sender: TopSenderResponse): number {
  return safeCount(sender.count);
}

export function TopSendersPanel({
  senders = [],
  domainLoad = [],
  className,
}: TopSendersPanelProps) {
  const t = useTranslations();
  const visibleSenders = senders.slice(0, 10);
  const maxSenderCount = Math.max(1, ...visibleSenders.map(senderCount));
  const domains = topDomainLoad(domainLoad, senders).slice(0, 5);
  const maxDomainCount = Math.max(1, ...domains.map((domain) => domain.count));

  return (
    <div
      data-testid="analytics-top-senders-panel"
      className={cn('grid grid-cols-1 gap-4 xl:grid-cols-12', className)}
    >
      <Card className="bg-card/95 shadow-sm xl:col-span-7">
        <CardHeader className="gap-1.5">
          <CardDescription className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
            {t('analytics.topSenders.eyebrow')}
          </CardDescription>
          <CardTitle>
            <h3 className="text-lg leading-snug font-semibold">
              {t('analytics.topSenders.title')}
            </h3>
          </CardTitle>
          <ChartInfoAction
            title={t('analytics.topSenders.title')}
            description={t('analytics.topSenders.tooltip')}
          />
        </CardHeader>
        <CardContent>
          {visibleSenders.length === 0 ? (
            <p className="text-muted-foreground text-sm">{t('analytics.topSenders.empty')}</p>
          ) : (
            <ol className="flex flex-col gap-3">
              {visibleSenders.map((sender, index) => {
                const email = sender.senderEmail ?? '';
                const count = senderCount(sender);
                const width = `${Math.max(8, Math.round(percentOf(count, maxSenderCount) * 100))}%`;

                return (
                  <li
                    key={`${email}-${index}`}
                    className="bg-muted/30 ring-foreground/10 rounded-lg px-3 py-2.5 ring-1"
                  >
                    <div className="grid grid-cols-[2rem_minmax(0,1fr)_auto] items-center gap-3">
                      <span
                        className={cn(
                          'text-muted-foreground text-xs font-medium tabular-nums',
                          index === 0 && 'text-primary',
                        )}
                      >
                        {index + 1}
                      </span>
                      <Tooltip>
                        <TooltipTrigger render={<span className="truncate text-sm font-medium" />}>
                          {email}
                        </TooltipTrigger>
                        <TooltipContent>{email}</TooltipContent>
                      </Tooltip>
                      <Badge variant="secondary" className="tabular-nums">
                        {formatCompactCount(count)}
                      </Badge>
                    </div>
                    <div className="bg-background mt-2 h-2 overflow-hidden rounded-full">
                      <div
                        className={cn(
                          'h-full rounded-full',
                          index === 0 ? 'bg-(--chart-1)' : 'bg-(--chart-2)/70',
                        )}
                        style={{ width }}
                        aria-hidden="true"
                      />
                    </div>
                  </li>
                );
              })}
            </ol>
          )}
        </CardContent>
      </Card>

      <Card className="bg-card/95 shadow-sm xl:col-span-5">
        <CardHeader className="gap-1.5">
          <CardDescription className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
            {t('analytics.topSenders.domainEyebrow')}
          </CardDescription>
          <CardTitle>
            <h3 className="text-lg leading-snug font-semibold">
              {t('analytics.topSenders.domainsTitle')}
            </h3>
          </CardTitle>
          <ChartInfoAction
            title={t('analytics.topSenders.domainsTitle')}
            description={t('analytics.topSenders.domainsTooltip')}
          />
        </CardHeader>
        <CardContent>
          {domains.length > 0 ? (
            <div className="flex flex-col gap-3">
              {domains.map((domain) => (
                <DomainRow
                  key={domain.domain}
                  domain={domain.domain}
                  count={domain.count}
                  maxCount={maxDomainCount}
                />
              ))}
            </div>
          ) : (
            <p className="text-muted-foreground text-sm">
              {t('analytics.topSenders.domainsEmpty')}
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function DomainRow({
  domain,
  count,
  maxCount,
}: {
  domain: string;
  count: number;
  maxCount: number;
}) {
  const width = `${Math.max(10, Math.round(percentOf(count, maxCount) * 100))}%`;

  return (
    <div className="bg-muted/30 ring-foreground/10 flex flex-col gap-2 rounded-lg px-3 py-3 ring-1">
      <div className="flex min-w-0 items-center justify-between gap-3">
        <span className="truncate text-sm font-medium">{domain}</span>
        <Badge variant="secondary" className="tabular-nums">
          {formatCompactCount(count)}
        </Badge>
      </div>
      <div className="bg-background h-2.5 overflow-hidden rounded-full">
        <div className="h-full rounded-full bg-(--chart-4)" style={{ width }} aria-hidden="true" />
      </div>
    </div>
  );
}
