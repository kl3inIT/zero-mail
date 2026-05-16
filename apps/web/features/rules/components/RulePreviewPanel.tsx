'use client';

import { useTranslations } from 'next-intl';
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
            <div className="bg-background border-border flex w-fit items-center gap-0.5 rounded-xl border p-1 shadow-sm">
              {SAMPLE_SIZES.map((size) => {
                const isActive = sampleSize === size;
                return (
                  <Button
                    key={size}
                    type="button"
                    variant={isActive ? 'default' : 'ghost'}
                    size="sm"
                    className={cn(
                      'h-8 w-11 rounded-lg border text-sm font-medium transition-all',
                      isActive
                        ? 'border-[#0a3d3a]/10 bg-[#E7F0EF] font-bold text-[#0a3d3a] shadow-sm'
                        : 'bg-background border-border text-muted-foreground hover:bg-muted hover:text-foreground',
                    )}
                    onClick={() => onSampleSizeChange(size)}
                  >
                    {size}
                  </Button>
                );
              })}
            </div>
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
            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                <StatCard
                  value={summary?.sampledMessageCount ?? rows.length}
                  label={t('rules.preview.stat.sampled')}
                />
                <StatCard
                  value={summary?.matchedCount ?? 0}
                  label={t('rules.preview.stat.matched')}
                  variant="primary"
                />
                <StatCard
                  value={summary?.deferredCount ?? 0}
                  label={t('rules.preview.stat.deferred')}
                />
                <StatCard
                  value={summary?.conflictCount ?? 0}
                  label={t('rules.preview.stat.conflicts')}
                  variant={(summary?.conflictCount ?? 0) > 0 ? 'warning' : 'default'}
                />
              </div>
              {actionCounts.length > 0 && (
                <div className="flex flex-wrap gap-1.5">
                  {actionCounts.map(([actionType, count]) => (
                    <Badge key={actionType} variant="outline" className="text-xs">
                      {actionType}: {count}
                    </Badge>
                  ))}
                </div>
              )}
              <p className="text-green flex items-center gap-1.5 text-xs">
                <CheckCircle2 className="size-3.5 shrink-0" aria-hidden="true" />
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
              <div className="overflow-hidden rounded-lg border">
                {rows.map((row) => {
                  const visibleLabels = (row.gmailLabelIds ?? []).filter(isVisibleLabel);
                  const proposedActions = (row.proposedActionChips ?? [])
                    .map((chip) => {
                      const label = chip.safeLabel ?? chip.actionTypeId ?? '';
                      return label.startsWith('label:') ? label.replace('label:', '') : label;
                    })
                    .filter(Boolean);
                  const evidenceChips = (row.matchedEvidenceChips ?? [])
                    .map((chip) => chip.reasonKey ?? chip.matcherNodeId ?? '')
                    .filter(Boolean);
                  const hasSecondaryInfo =
                    proposedActions.length > 0 ||
                    evidenceChips.length > 0 ||
                    (row.deferredEvidenceChips ?? []).length > 0 ||
                    (row.conflictChips ?? []).length > 0;

                  return (
                    <article
                      key={row.gmailMessageId ?? row.gmailThreadId}
                      className={cn(
                        'hover:bg-muted/30 flex items-start gap-3 border-b px-3 py-2.5 text-sm transition-colors last:border-b-0',
                        !row.matched && 'opacity-50',
                      )}
                    >
                      <div
                        className={cn(
                          'mt-1.5 size-1.5 shrink-0 rounded-full',
                          row.matched ? 'bg-green' : 'bg-muted-foreground/20',
                        )}
                        aria-hidden="true"
                      />
                      <div className="w-36 shrink-0">
                        <p className="truncate font-medium">
                          {senderDisplayName(row.sanitizedSenderEmail ?? row.sanitizedSenderDomain)}
                        </p>
                        <p className="text-muted-foreground truncate text-xs">
                          {row.sanitizedSenderDomain}
                        </p>
                      </div>
                      <div className="min-w-0 flex-1 space-y-1.5">
                        <div className="flex min-w-0 flex-wrap items-center gap-1">
                          {visibleLabels.map((labelId) => (
                            <GmailLabelChip key={labelId} labelId={labelId} />
                          ))}
                          <span className="min-w-0 flex-1 truncate">
                            {row.sanitizedSubjectExcerpt}
                          </span>
                        </div>
                        {hasSecondaryInfo && (
                          <div className="flex flex-wrap items-center gap-1">
                            {proposedActions.map((action) => (
                              <span
                                key={action}
                                className="bg-primary/10 text-primary inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium"
                              >
                                <Archive className="size-2.5 shrink-0" aria-hidden="true" />
                                {action}
                              </span>
                            ))}
                            {evidenceChips.map((evidence) => (
                              <span
                                key={evidence}
                                className="bg-muted text-muted-foreground rounded-sm px-1.5 py-0.5 text-[10px]"
                              >
                                {evidence}
                              </span>
                            ))}
                            {row.deferredEvidenceChips?.map((chip) => (
                              <Tooltip key={chip.matcherNodeId ?? chip.reasonKey}>
                                <TooltipTrigger
                                  render={
                                    <span className="cursor-help rounded-sm bg-violet-50 px-1.5 py-0.5 text-[10px] text-violet-600 dark:bg-violet-900/20 dark:text-violet-400" />
                                  }
                                >
                                  {t('rules.preview.deferredSemantic')}
                                </TooltipTrigger>
                                <TooltipContent>
                                  {t('rules.preview.deferredTooltip')}
                                </TooltipContent>
                              </Tooltip>
                            ))}
                            {row.conflictChips?.map((chip) => (
                              <span
                                key={chip.conflictTypeId}
                                className="bg-warning/10 text-warning rounded-sm px-1.5 py-0.5 text-[10px]"
                              >
                                {chip.conflictTypeId}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                      <time
                        className="text-muted-foreground w-12 shrink-0 pt-0.5 text-right text-xs"
                        dateTime={row.internalDate}
                      >
                        {formatDate(row.internalDate)}
                      </time>
                    </article>
                  );
                })}
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

const GMAIL_LABEL_COLOR_MAP: Record<string, string> = {
  INBOX: 'bg-sky-100 text-sky-700 dark:bg-sky-900/30 dark:text-sky-400',
  IMPORTANT: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  STARRED: 'bg-yellow-100 text-yellow-600 dark:bg-yellow-900/30 dark:text-yellow-400',
  SENT: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300',
  DRAFT: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
  SPAM: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  CATEGORY_PROMOTIONS:
    'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
  CATEGORY_UPDATES: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  CATEGORY_SOCIAL: 'bg-rose-100 text-rose-700 dark:bg-rose-900/30 dark:text-rose-400',
  CATEGORY_FORUMS: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
  CATEGORY_PERSONAL: 'bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-400',
};

const CUSTOM_LABEL_COLORS = [
  'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
  'bg-teal-100 text-teal-700 dark:bg-teal-900/30 dark:text-teal-400',
  'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
  'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-400',
  'bg-lime-100 text-lime-700 dark:bg-lime-900/30 dark:text-lime-400',
] as const;

function gmailLabelColorClass(labelId: string): string {
  const known = GMAIL_LABEL_COLOR_MAP[labelId];
  if (known) return known;
  const hash = [...labelId].reduce(
    (accumulator, character) => accumulator + character.charCodeAt(0),
    0,
  );
  return CUSTOM_LABEL_COLORS[hash % CUSTOM_LABEL_COLORS.length] ?? CUSTOM_LABEL_COLORS[0];
}

function humanizeLabelId(labelId: string): string {
  return labelId
    .replace(/^CATEGORY_/, '')
    .replace(/_/g, ' ')
    .toLowerCase()
    .replace(/\b\w/g, (character) => character.toUpperCase());
}

function isVisibleLabel(labelId: string): boolean {
  return labelId !== 'UNREAD';
}

function senderDisplayName(emailOrDomain: string | undefined): string {
  if (!emailOrDomain) return '';
  const atIndex = emailOrDomain.indexOf('@');
  return atIndex > 0 ? emailOrDomain.slice(0, atIndex) : emailOrDomain;
}

function GmailLabelChip({ labelId }: { labelId: string }) {
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center rounded-sm px-1.5 py-0.5 text-[10px] leading-none font-medium',
        gmailLabelColorClass(labelId),
      )}
    >
      {humanizeLabelId(labelId)}
    </span>
  );
}

function StatCard({
  value,
  label,
  variant = 'default',
}: {
  value: number;
  label: string;
  variant?: 'default' | 'primary' | 'warning';
}) {
  return (
    <div
      className={cn(
        'rounded-lg border p-3 text-center',
        variant === 'primary' && 'border-primary/20 bg-primary/5',
        variant === 'warning' && 'border-warning/20 bg-warning/5',
        variant === 'default' && 'bg-muted/20',
      )}
    >
      <p
        className={cn(
          'text-2xl font-bold tabular-nums',
          variant === 'primary' && 'text-primary',
          variant === 'warning' && 'text-warning',
        )}
      >
        {value}
      </p>
      <p className="text-muted-foreground mt-0.5 text-xs">{label}</p>
    </div>
  );
}

function formatDate(value: string | undefined): string {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(date);
}
