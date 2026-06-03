'use client';

import { Check, Clock, Eye, ExternalLink, FileText } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { GenerateDraftButton } from '@/features/needs-reply/components/GenerateDraftButton';
import { SenderAvatar } from '@/features/needs-reply/components/SenderAvatar';
import { useMarkResolved } from '@/features/needs-reply/hooks/useMarkResolved';
import type {
  DraftStatus,
  NeedsReplyBucket,
  NeedsReplyRow as NeedsReplyRowModel,
} from '@/features/needs-reply/api/needs-reply-api';
import { cn } from '@/lib/utils';

type NeedsReplyRowProps = {
  row: NeedsReplyRowModel;
  activeBucket: NeedsReplyBucket;
  onOpen?: () => void;
  now?: Date;
};

type Urgency = 'high' | 'med' | 'low';

export function NeedsReplyRow({ row, activeBucket, onOpen, now = new Date() }: NeedsReplyRowProps) {
  const t = useTranslations();
  const ageMs = ageInMilliseconds(row.lastActivityAt, now);
  const urgency = ageUrgency(ageMs);
  const senderLabel = row.otherParty || t('triage.audit.message.unknownSender');
  const subjectLabel = row.subject || t('triage.audit.message.untitled');

  return (
    <div
      className="bg-card hover:bg-muted/30 flex gap-2.5 rounded-lg border p-3 transition-colors"
      data-testid="needs-reply-row"
    >
      <UrgencyBar urgency={urgency} />
      <SenderAvatar sender={row.otherParty} />
      <div className="min-w-0 flex-1 space-y-1.5">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="text-muted-foreground truncate text-xs">{senderLabel}</span>
          <DraftBadge draftStatus={row.draftStatus} />
          <AgeChip ageMs={ageMs} urgency={urgency} lastActivityAt={row.lastActivityAt} />
        </div>

        <button
          type="button"
          onClick={onOpen}
          className="group block w-full min-w-0 cursor-pointer text-left"
          data-testid="needs-reply-open"
        >
          <p className="text-foreground truncate text-sm font-medium group-hover:underline">
            {subjectLabel}
          </p>

          {row.snippet ? (
            <p className="text-muted-foreground mt-0.5 line-clamp-1 text-xs">{row.snippet}</p>
          ) : null}
        </button>

        <div className="flex flex-wrap items-center gap-1">
          {activeBucket !== 'awaiting-their-reply' ? (
            <GenerateDraftButton gmailThreadId={row.gmailThreadId} draftStatus={row.draftStatus} />
          ) : null}
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="gap-1.5"
            onClick={onOpen}
            data-testid="needs-reply-view"
          >
            <Eye className="size-3.5" aria-hidden="true" />
            {t('needsReply.action.viewContent')}
          </Button>
          <ResolveButton gmailThreadId={row.gmailThreadId} />
          <a
            href={row.openInGmailUrl}
            target="_blank"
            rel="noopener noreferrer"
            className={cn(
              buttonVariants({ variant: 'ghost', size: 'sm' }),
              'text-muted-foreground gap-1.5',
            )}
          >
            <ExternalLink className="size-3.5" aria-hidden="true" />
            {t('needsReply.action.openInGmail')}
          </a>
        </div>
      </div>
    </div>
  );
}

function UrgencyBar({ urgency }: { urgency: Urgency }) {
  return (
    <div
      className={cn('w-[3px] shrink-0 self-stretch rounded-full', {
        'bg-destructive': urgency === 'high',
        'bg-amber-500': urgency === 'med',
        'bg-muted-foreground/30': urgency === 'low',
      })}
      aria-hidden="true"
    />
  );
}

function DraftBadge({ draftStatus }: { draftStatus: DraftStatus }) {
  const t = useTranslations();
  if (draftStatus === 'NO_DRAFT') return null;

  const isSent = draftStatus === 'DRAFT_SENT';
  return (
    <Badge
      variant="outline"
      className={cn(
        'gap-1 px-1.5 py-0 text-[10px] font-medium',
        isSent
          ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
          : 'border-blue-500/30 bg-blue-500/10 text-blue-700 dark:text-blue-300',
      )}
    >
      <FileText className="size-3" aria-hidden="true" />
      {isSent ? t('needsReply.row.draftSent') : t('needsReply.row.draftWritten')}
    </Badge>
  );
}

function AgeChip({
  ageMs,
  urgency,
  lastActivityAt,
}: {
  ageMs: number;
  urgency: Urgency;
  lastActivityAt: string;
}) {
  const t = useTranslations();
  const minutes = ageMs / 60_000;
  const hours = ageMs / 3_600_000;
  const days = Math.round(ageMs / 86_400_000);
  const label =
    minutes < 60
      ? t('needsReply.row.ageMinutes')
      : hours < 24
        ? t('needsReply.row.ageHours', { count: Math.round(hours) })
        : t('needsReply.row.ageDays', { count: days });
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger>
          <span
            className={cn(
              'ml-auto inline-flex cursor-default items-center gap-1 font-mono text-[11px]',
              {
                'text-destructive font-medium': urgency === 'high',
                'text-amber-600 dark:text-amber-400': urgency === 'med',
                'text-muted-foreground': urgency === 'low',
              },
            )}
          >
            <Clock className="size-3" aria-hidden="true" />
            {label}
          </span>
        </TooltipTrigger>
        <TooltipContent>{formatAbsoluteTime(lastActivityAt)}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

function ResolveButton({ gmailThreadId }: { gmailThreadId: string }) {
  const t = useTranslations();
  const markResolved = useMarkResolved();
  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      className="gap-1.5"
      disabled={markResolved.isPending}
      onClick={() => markResolved.mutate(gmailThreadId)}
    >
      <Check className="size-3.5" aria-hidden="true" />
      {t('needsReply.action.markResolved')}
    </Button>
  );
}

function ageInMilliseconds(value: string, now: Date): number {
  const then = new Date(value).getTime();
  if (Number.isNaN(then)) return 0;
  return Math.max(0, now.getTime() - then);
}

function ageUrgency(ageMs: number): Urgency {
  const days = ageMs / 86_400_000;
  if (days >= 3) return 'high';
  if (days >= 1) return 'med';
  return 'low';
}

const absoluteTimeFormatter = new Intl.DateTimeFormat(undefined, {
  dateStyle: 'medium',
  timeStyle: 'short',
});

function formatAbsoluteTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return absoluteTimeFormatter.format(date);
}
