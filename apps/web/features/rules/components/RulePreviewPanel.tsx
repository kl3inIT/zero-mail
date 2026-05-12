'use client';

import { useTranslations } from 'next-intl';
import type { ReactNode } from 'react';
import { AlertTriangle, Archive, CheckCircle2, Loader2, Play, Tags } from 'lucide-react';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/states/EmptyState';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import type { RulePreviewResponse, RuleResponse } from '@/features/rules/api/rules-api';
import { cn } from '@/lib/utils';

type SampleSize = 10 | 25 | 50;

type Props = {
  selectedRule: RuleResponse | null;
  preview: RulePreviewResponse | null;
  previewError: string | null;
  gmailUnavailableError: string | null;
  isPreviewing: boolean;
  isToggling: boolean;
  canPreview: boolean;
  canEnable: boolean;
  sampleSize: SampleSize;
  onSampleSizeChange: (sampleSize: SampleSize) => void;
  onPreview: () => void;
  onToggleEnabled: () => void;
};

const SAMPLE_SIZES = [10, 25, 50] as const;

export function RulePreviewPanel({
  selectedRule,
  preview,
  previewError,
  gmailUnavailableError,
  isPreviewing,
  isToggling,
  canPreview,
  canEnable,
  sampleSize,
  onSampleSizeChange,
  onPreview,
  onToggleEnabled,
}: Props) {
  const t = useTranslations();
  const rows = preview?.rows ?? [];
  const summary = preview?.impactSummary;
  const actionCounts = Object.entries(summary?.proposedActionCounts ?? {});
  const hasConflicts =
    (summary?.conflictCount ?? 0) > 0 || rows.some((row) => row.conflictChips?.length);

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('rules.preview.title')}</CardTitle>
        <CardDescription>{t('rules.preview.empty.body')}</CardDescription>
      </CardHeader>

      <CardContent className="space-y-4">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div className="space-y-1">
            <p className="text-sm font-medium">{t('rules.preview.sampleSize')}</p>
            <ToggleGroup
              value={[String(sampleSize)]}
              onValueChange={(value) => {
                const next = Number(value[0]);
                if (next === 10 || next === 25 || next === 50) onSampleSizeChange(next);
              }}
              className="rounded-lg border"
            >
              {SAMPLE_SIZES.map((size) => (
                <ToggleGroupItem key={size} value={String(size)} aria-label={String(size)}>
                  {size}
                </ToggleGroupItem>
              ))}
            </ToggleGroup>
          </div>
          <Button type="button" disabled={!canPreview || isPreviewing} onClick={onPreview}>
            {isPreviewing ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              <Play className="size-4" aria-hidden="true" />
            )}
            {isPreviewing ? t('rules.preview.previewing') : t('rules.preview.previewCta')}
          </Button>
        </div>

        {gmailUnavailableError && (
          <Alert variant="warning">
            <AlertTriangle className="size-4" aria-hidden="true" />
            <AlertTitle>{t('errors.rules.gmail.unavailable')}</AlertTitle>
            <AlertDescription>{gmailUnavailableError}</AlertDescription>
          </Alert>
        )}

        {previewError && !gmailUnavailableError && (
          <Alert variant="destructive">
            <AlertTriangle className="size-4" aria-hidden="true" />
            <AlertDescription>{previewError}</AlertDescription>
          </Alert>
        )}

        {!preview ? (
          <EmptyState
            heading={t('rules.preview.empty.heading')}
            body={t('rules.preview.empty.body')}
            className="min-h-32 px-4 py-8"
          />
        ) : (
          <div className="space-y-4">
            <div className="bg-muted/30 rounded-lg border p-3">
              <div className="flex flex-wrap gap-2">
                <Badge variant="outline">
                  {t('rules.preview.sampled', {
                    count: summary?.sampledMessageCount ?? rows.length,
                  })}
                </Badge>
                <Badge className="bg-primary text-primary-foreground">
                  {t('rules.preview.matched', { count: summary?.matchedCount ?? 0 })}
                </Badge>
                <Badge variant="outline">
                  {t('rules.preview.deferred', { count: summary?.deferredCount ?? 0 })}
                </Badge>
                <Badge variant="outline">
                  {t('rules.preview.conflicts', { count: summary?.conflictCount ?? 0 })}
                </Badge>
                {actionCounts.map(([actionType, count]) => (
                  <Badge key={actionType} variant="outline">
                    {actionType}: {count}
                  </Badge>
                ))}
              </div>
              <p className="text-green mt-3 flex items-center gap-2 text-sm">
                <CheckCircle2 className="size-4" aria-hidden="true" />
                {t('rules.preview.noWriteNotice')}
              </p>
            </div>

            {hasConflicts && (
              <Alert variant="warning">
                <AlertTriangle className="size-4" aria-hidden="true" />
                <AlertTitle>{t('rules.preview.conflictWarning')}</AlertTitle>
              </Alert>
            )}

            <TooltipProvider>
              <div className="space-y-2">
                {rows.map((row) => (
                  <article
                    key={row.gmailMessageId ?? row.gmailThreadId}
                    className={cn(
                      'grid gap-3 rounded-lg border p-3 text-sm lg:grid-cols-[minmax(8rem,11rem)_minmax(0,1fr)_auto]',
                      row.matched ? 'bg-card' : 'bg-muted/30',
                    )}
                  >
                    <div className="min-w-0">
                      <p className="truncate font-medium">
                        {row.sanitizedSenderEmail ?? row.sanitizedSenderDomain}
                      </p>
                      <p className="text-muted-foreground truncate text-xs">
                        {row.sanitizedSenderDomain}
                      </p>
                    </div>
                    <div className="min-w-0 space-y-2">
                      <p className="line-clamp-2">{row.sanitizedSubjectExcerpt}</p>
                      <ChipGroup
                        label={t('rules.preview.gmailLabels')}
                        chips={row.gmailLabelIds ?? []}
                        variant="outline"
                      />
                      <ChipGroup
                        label={t('rules.preview.proposedActions')}
                        chips={(row.proposedActionChips ?? []).map(
                          (chip) => chip.safeLabel ?? chip.actionTypeId ?? '',
                        )}
                        icon={<Archive className="size-3" aria-hidden="true" />}
                      />
                      <ChipGroup
                        label={t('rules.preview.evidence')}
                        chips={(row.matchedEvidenceChips ?? []).map(
                          (chip) => chip.reasonKey ?? chip.matcherNodeId ?? '',
                        )}
                        variant="outline"
                      />
                      {row.deferredEvidenceChips?.map((chip) => (
                        <Tooltip key={chip.matcherNodeId ?? chip.reasonKey}>
                          <TooltipTrigger
                            render={<Badge variant="outline" className="text-violet" />}
                          >
                            {t('rules.preview.deferredSemantic')}
                          </TooltipTrigger>
                          <TooltipContent>{t('rules.preview.deferredTooltip')}</TooltipContent>
                        </Tooltip>
                      ))}
                      {row.conflictChips?.map((chip) => (
                        <Badge
                          key={chip.conflictTypeId}
                          variant="outline"
                          className="border-warning/40 text-warning"
                        >
                          {chip.conflictTypeId}
                        </Badge>
                      ))}
                    </div>
                    <time className="text-muted-foreground text-xs" dateTime={row.internalDate}>
                      {formatDate(row.internalDate)}
                    </time>
                  </article>
                ))}
              </div>
            </TooltipProvider>
          </div>
        )}
      </CardContent>

      <CardFooter className="justify-between gap-3">
        <p className="text-muted-foreground min-w-0 truncate text-xs">
          {selectedRule?.displayName ?? t('rules.preview.empty.heading')}
        </p>
        {selectedRule?.enabled ? (
          <Button type="button" variant="secondary" disabled={isToggling} onClick={onToggleEnabled}>
            <PowerOffIcon />
            {t('rules.preview.disableCta')}
          </Button>
        ) : (
          <Button type="button" disabled={!canEnable || isToggling} onClick={onToggleEnabled}>
            <Tags className="size-4" aria-hidden="true" />
            {t('rules.preview.enableCta')}
          </Button>
        )}
      </CardFooter>
    </Card>
  );
}

function PowerOffIcon() {
  return <Tags className="size-4 rotate-45" aria-hidden="true" />;
}

function ChipGroup({
  label,
  chips,
  icon,
  variant,
}: {
  label: string;
  chips: string[];
  icon?: ReactNode;
  variant?: 'outline';
}) {
  const visibleChips = chips.filter(Boolean);
  if (visibleChips.length === 0) return null;

  return (
    <div className="flex flex-wrap items-center gap-1">
      <span className="text-muted-foreground text-xs">{label}</span>
      {visibleChips.map((chip) => (
        <Badge key={`${label}-${chip}`} variant={variant ?? 'secondary'} className="max-w-full">
          {icon}
          <span className="truncate">{chip}</span>
        </Badge>
      ))}
    </div>
  );
}

function formatDate(value: string | undefined): string {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(date);
}
