'use client';

import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import type { TopSenderResponse } from '@/features/analytics/api/analytics-api';
import {
  formatCompactCount,
  percentOf,
  safeCount,
  topDomainSummaries,
} from '@/features/analytics/components/analytics-visualization';
import { cn } from '@/lib/utils';

type TopSendersPanelProps = {
  senders?: TopSenderResponse[];
  className?: string;
};

function senderCount(sender: TopSenderResponse): number {
  return safeCount(sender.count);
}

export function TopSendersPanel({ senders = [], className }: TopSendersPanelProps) {
  const t = useTranslations();
  const visibleSenders = senders.slice(0, 10);
  const maxSenderCount = Math.max(1, ...visibleSenders.map(senderCount));
  const domains = topDomainSummaries(senders).slice(0, 4);
  const maxDomainCount = Math.max(1, ...domains.map((domain) => domain.count));

  return (
    <Card
      data-testid="analytics-top-senders-panel"
      className={cn('bg-card/95 shadow-sm', className)}
    >
      <CardHeader>
        <CardDescription className="text-xs font-medium">
          {t('analytics.topSenders.eyebrow')}
        </CardDescription>
        <CardTitle>
          <h3 className="text-base leading-snug font-semibold">
            {t('analytics.topSenders.title')}
          </h3>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        {visibleSenders.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t('analytics.topSenders.empty')}</p>
        ) : (
          <>
            <ol className="space-y-3">
              {visibleSenders.map((sender, index) => {
                const email = sender.senderEmail ?? '';
                const count = senderCount(sender);
                const width = `${Math.max(8, Math.round(percentOf(count, maxSenderCount) * 100))}%`;

                return (
                  <li key={`${email}-${index}`} className="space-y-1.5">
                    <div className="grid grid-cols-[2.25rem_minmax(0,1fr)_auto] items-center gap-2">
                      <span
                        className={cn(
                          'text-muted-foreground text-xs tabular-nums',
                          index === 0 && 'text-primary font-semibold',
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
                    <div className="bg-muted ml-11 h-2 overflow-hidden rounded-full">
                      <div
                        className={cn(
                          'h-full rounded-full',
                          index === 0 ? 'bg-[var(--chart-1)]' : 'bg-[var(--chart-2)]/70',
                        )}
                        style={{ width }}
                        aria-hidden="true"
                      />
                    </div>
                  </li>
                );
              })}
            </ol>

            {domains.length > 0 ? (
              <div className="border-t pt-4">
                <p className="text-muted-foreground mb-3 text-xs font-medium">
                  {t('analytics.topSenders.domainsTitle')}
                </p>
                <div className="space-y-2">
                  {domains.map((domain) => (
                    <div
                      key={domain.domain}
                      className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3"
                    >
                      <div className="min-w-0">
                        <div className="mb-1 flex items-center justify-between gap-2">
                          <span className="truncate text-xs font-medium">{domain.domain}</span>
                        </div>
                        <div className="bg-muted h-2 overflow-hidden rounded-full">
                          <div
                            className="h-full rounded-full bg-[var(--chart-4)]"
                            style={{
                              width: `${Math.max(10, Math.round(percentOf(domain.count, maxDomainCount) * 100))}%`,
                            }}
                            aria-hidden="true"
                          />
                        </div>
                      </div>
                      <span className="text-muted-foreground text-xs tabular-nums">
                        {formatCompactCount(domain.count)}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            ) : null}
          </>
        )}
      </CardContent>
    </Card>
  );
}
