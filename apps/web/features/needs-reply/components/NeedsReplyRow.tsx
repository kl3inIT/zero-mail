'use client';

import { Check, Clock, ExternalLink, FileText } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { GenerateDraftButton } from '@/features/needs-reply/components/GenerateDraftButton';
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
  now?: Date;
};

type Urgency = 'high' | 'med' | 'low';

export function NeedsReplyRow({ row, activeBucket, now = new Date() }: NeedsReplyRowProps) {
  const t = useTranslations();
  const ageMs = ageInMilliseconds(row.lastActivityAt, now);
  const urgency = ageUrgency(ageMs);

  return (
    <div
      className="bg-card hover:bg-muted/30 flex gap-3 rounded-lg border p-4 transition-colors"
      data-testid="needs-reply-row"
    >
      <UrgencyBar urgency={urgency} />
      <SenderAvatar name={row.otherParty} />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
          <span className="text-foreground truncate text-sm font-semibold">
            {row.otherParty || t('triage.audit.message.unknownSender')}
          </span>
          <DraftBadge draftStatus={row.draftStatus} />
          <LastActivity value={row.lastActivityAt} now={now} />
        </div>

        <p className="text-foreground mt-1 truncate text-sm">
          {row.subject || t('triage.audit.message.untitled')}
        </p>

        <div className="text-muted-foreground mt-2 flex flex-wrap items-center gap-3 text-[11px]">
          <AgeChip ageMs={ageMs} urgency={urgency} />
        </div>

        <div className="mt-3 flex flex-wrap items-center gap-1">
          {activeBucket !== 'awaiting-their-reply' ? (
            <GenerateDraftButton gmailThreadId={row.gmailThreadId} draftStatus={row.draftStatus} />
          ) : null}
          <ResolveButton gmailThreadId={row.gmailThreadId} />
          <a
            href={row.openInGmailUrl}
            target="_blank"
            rel="noopener noreferrer"
            className={cn(buttonVariants({ variant: 'ghost', size: 'sm' }), 'ml-auto gap-1.5')}
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

function SenderAvatar({ name }: { name: string }) {
  const initials = senderInitials(name);
  return (
    <div
      className="bg-muted text-muted-foreground mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full border text-xs font-semibold"
      aria-hidden="true"
    >
      {initials}
    </div>
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

function AgeChip({ ageMs, urgency }: { ageMs: number; urgency: Urgency }) {
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
    <span
      className={cn('inline-flex items-center gap-1', {
        'text-destructive font-medium': urgency === 'high',
        'text-amber-600 dark:text-amber-400': urgency === 'med',
      })}
    >
      <Clock className="size-3" aria-hidden="true" />
      {label}
    </span>
  );
}

function LastActivity({ value, now }: { value: string; now: Date }) {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger>
          <span className="text-muted-foreground ml-auto cursor-default font-mono text-xs">
            {formatRelativeTime(value, now)}
          </span>
        </TooltipTrigger>
        <TooltipContent>{formatAbsoluteTime(value)}</TooltipContent>
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

// Re-exported so NeedsReplyTable (and the test still importing it) keep working.
export function DraftStatusBadge({ draftStatus }: { draftStatus: DraftStatus }) {
  return <DraftBadge draftStatus={draftStatus} />;
}

function senderInitials(name: string): string {
  if (!name) return '?';
  const cleaned = name.split('<')[0]?.trim() ?? name;
  const parts = cleaned.split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0]!.slice(0, 2).toUpperCase();
  return (parts[0]![0]! + parts[parts.length - 1]![0]!).toUpperCase();
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

function formatRelativeTime(value: string, now: Date): string {
  const then = new Date(value);
  if (Number.isNaN(then.getTime())) return '—';
  const diffSeconds = Math.round((then.getTime() - now.getTime()) / 1000);
  const absoluteSeconds = Math.abs(diffSeconds);
  const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
  if (absoluteSeconds < 60) return formatter.format(diffSeconds, 'second');
  if (absoluteSeconds < 3600) return formatter.format(Math.round(diffSeconds / 60), 'minute');
  if (absoluteSeconds < 86_400) return formatter.format(Math.round(diffSeconds / 3600), 'hour');
  return formatter.format(Math.round(diffSeconds / 86_400), 'day');
}

function formatAbsoluteTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}
