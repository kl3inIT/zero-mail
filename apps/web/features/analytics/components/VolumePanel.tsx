'use client';

import { Info } from 'lucide-react';
import { useTranslations } from 'next-intl';

import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';

type VolumePanelProps = {
  observed?: number;
  applied?: number;
};

function safeCount(value: number | undefined): number {
  return Number.isFinite(value) ? Math.max(0, Math.trunc(value ?? 0)) : 0;
}

export function VolumePanel({ observed, applied }: VolumePanelProps) {
  const t = useTranslations();
  const observedCount = safeCount(observed);
  const appliedCount = safeCount(applied);
  const empty = observedCount === 0 && appliedCount === 0;

  return (
    <Card data-testid="analytics-volume-panel">
      <CardHeader className="has-data-[slot=card-action]:grid-cols-[1fr_auto]">
        <CardDescription className="font-mono text-[11px] font-medium tracking-[0.08em]">
          {t('analytics.volume.eyebrow')}
        </CardDescription>
        <CardTitle>
          <h3 className="text-base leading-snug font-medium">{t('analytics.volume.title')}</h3>
        </CardTitle>
        <CardAction>
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  className="text-muted-foreground hover:text-foreground focus-visible:ring-ring grid size-8 place-items-center rounded-md outline-none focus-visible:ring-2"
                  aria-label={t('analytics.volume.tooltip')}
                />
              }
            >
              <Info className="size-4" aria-hidden="true" />
            </TooltipTrigger>
            <TooltipContent>{t('analytics.volume.tooltip')}</TooltipContent>
          </Tooltip>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-2">
        <p className="text-foreground font-mono text-[28px] leading-none font-semibold tabular-nums">
          {appliedCount}
        </p>
        <p className="text-muted-foreground text-sm">
          {empty
            ? t('analytics.volume.empty')
            : t('analytics.volume.supplementary', {
                applied: appliedCount,
                observed: observedCount,
              })}
        </p>
      </CardContent>
    </Card>
  );
}
