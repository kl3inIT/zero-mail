'use client';

import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import type { TopSenderResponse } from '@/features/analytics/api/analytics-api';
import { cn } from '@/lib/utils';

type TopSendersPanelProps = {
  senders?: TopSenderResponse[];
};

function senderCount(sender: TopSenderResponse): number {
  return Number.isFinite(sender.count) ? Math.max(0, Math.trunc(sender.count ?? 0)) : 0;
}

export function TopSendersPanel({ senders = [] }: TopSendersPanelProps) {
  const t = useTranslations();
  const visibleSenders = senders.slice(0, 3);

  return (
    <Card data-testid="analytics-top-senders-panel">
      <CardHeader>
        <CardDescription className="font-mono text-[11px] font-medium tracking-[0.08em]">
          {t('analytics.topSenders.eyebrow')}
        </CardDescription>
        <CardTitle>
          <h3 className="text-base leading-snug font-medium">{t('analytics.topSenders.title')}</h3>
        </CardTitle>
      </CardHeader>
      <CardContent>
        {visibleSenders.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t('analytics.topSenders.empty')}</p>
        ) : (
          <ol className="space-y-2">
            {visibleSenders.map((sender, index) => {
              const email = sender.senderEmail ?? '';
              return (
                <li
                  key={`${email}-${index}`}
                  className={cn(
                    'grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 rounded-lg border p-2.5',
                    index === 0 && 'border-primary/20 bg-primary/5 shadow-[inset_4px_0_0_#0E5E5A]',
                  )}
                >
                  <span className="text-muted-foreground font-mono text-xs tabular-nums">
                    {index + 1}
                  </span>
                  <Tooltip>
                    <TooltipTrigger render={<span className="truncate text-sm font-medium" />}>
                      {email}
                    </TooltipTrigger>
                    <TooltipContent>{email}</TooltipContent>
                  </Tooltip>
                  <Badge variant="secondary" className="font-mono tabular-nums">
                    {senderCount(sender)}
                  </Badge>
                </li>
              );
            })}
          </ol>
        )}
      </CardContent>
    </Card>
  );
}
