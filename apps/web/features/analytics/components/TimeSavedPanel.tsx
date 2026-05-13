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

type TimeSavedPanelProps = {
  seconds?: number;
};

function safeSeconds(value: number | undefined): number {
  return Number.isFinite(value) ? Math.max(0, Math.trunc(value ?? 0)) : 0;
}

export function formatTimeSaved(seconds: number | undefined): {
  hours: number;
  minutes: number;
  label: string;
} {
  const totalMinutes = Math.floor(safeSeconds(seconds) / 60);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;

  if (hours === 0) {
    return { hours, minutes: totalMinutes, label: `${totalMinutes}m` };
  }

  return { hours, minutes, label: `${hours}h ${minutes.toString().padStart(2, '0')}m` };
}

export function TimeSavedPanel({ seconds }: TimeSavedPanelProps) {
  const t = useTranslations();
  const duration = formatTimeSaved(seconds);
  const empty = safeSeconds(seconds) === 0;

  return (
    <Card data-testid="analytics-time-saved-panel">
      <CardHeader className="has-data-[slot=card-action]:grid-cols-[1fr_auto]">
        <CardDescription className="font-mono text-[11px] font-medium tracking-[0.08em]">
          {t('analytics.timeSaved.eyebrow')}
        </CardDescription>
        <CardTitle>
          <h3 className="text-base leading-snug font-medium">{t('analytics.timeSaved.title')}</h3>
        </CardTitle>
        <CardAction>
          <Tooltip>
            <TooltipTrigger
              render={
                <button
                  type="button"
                  className="text-muted-foreground hover:text-foreground focus-visible:ring-ring grid size-8 place-items-center rounded-md outline-none focus-visible:ring-2"
                  aria-label={t('analytics.timeSaved.tooltip')}
                />
              }
            >
              <Info className="size-4" aria-hidden="true" />
            </TooltipTrigger>
            <TooltipContent>{t('analytics.timeSaved.tooltip')}</TooltipContent>
          </Tooltip>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-2">
        <p className="text-foreground font-mono text-[28px] leading-none font-semibold tabular-nums">
          <span aria-hidden="true">{duration.label}</span>
          <span className="sr-only">
            {t('analytics.timeSaved.screenReader', {
              hours: duration.hours,
              minutes: duration.minutes,
            })}
          </span>
        </p>
        {empty ? (
          <p className="text-muted-foreground text-sm">{t('analytics.timeSaved.empty')}</p>
        ) : null}
      </CardContent>
    </Card>
  );
}
