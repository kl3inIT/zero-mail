'use client';

import { Clock, FileText } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { SenderAvatar } from '@/features/needs-reply/components/SenderAvatar';
import type {
  DraftStatus,
  NeedsReplyRow as NeedsReplyRowModel,
} from '@/features/needs-reply/api/needs-reply-api';
import { cn } from '@/lib/utils';

type NeedsReplyRowProps = {
  row: NeedsReplyRowModel;
  selected?: boolean;
  onOpen?: () => void;
  now?: Date;
};

type Urgency = 'high' | 'med' | 'low';

export function NeedsReplyRow({
  row,
  selected = false,
  onOpen,
  now = new Date(),
}: NeedsReplyRowProps) {
  const t = useTranslations();
  const ageMs = ageInMilliseconds(row.lastActivityAt, now);
  const urgency = ageUrgency(ageMs);
  const senderLabel = row.otherParty || t('triage.audit.message.unknownSender');
  const subjectLabel = row.subject || t('triage.audit.message.untitled');

  return (
    <button
      type="button"
      aria-current={selected ? 'true' : undefined}
      onClick={onOpen}
      className={cn(
        'group relative flex min-h-[82px] w-full items-start gap-2.5 border-b px-4 py-3 text-left transition-colors',
        'hover:bg-muted/60',
        selected &&
          'bg-primary/10 hover:bg-primary/10 before:bg-primary before:absolute before:inset-y-0 before:left-0 before:w-[3px] before:content-[""]',
      )}
      data-testid="needs-reply-row"
    >
      <UrgencyBar urgency={urgency} />
      <SenderAvatar sender={row.otherParty} />
      <div className="min-w-0 flex-1 space-y-0.5">
        <div className="flex min-w-0 items-center gap-2">
          <span className="text-foreground min-w-0 truncate text-sm font-semibold">
            {senderLabel}
          </span>
          <DraftBadge draftStatus={row.draftStatus} />
          <span className="ml-auto shrink-0">
            <AgeChip ageMs={ageMs} urgency={urgency} lastActivityAt={row.lastActivityAt} />
          </span>
        </div>

        <p className="text-foreground/90 truncate text-sm leading-5 font-medium">{subjectLabel}</p>
        {row.snippet ? (
          <p className="text-muted-foreground line-clamp-1 text-xs leading-5">{row.snippet}</p>
        ) : null}
      </div>
    </button>
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
    <span
      className={cn(
        'inline-flex cursor-default items-center gap-1 font-mono text-[11px] whitespace-nowrap tabular-nums',
        {
          'text-destructive font-medium': urgency === 'high',
          'text-amber-600 dark:text-amber-400': urgency === 'med',
          'text-muted-foreground': urgency === 'low',
        },
      )}
      title={formatAbsoluteTime(lastActivityAt)}
    >
      <Clock className="size-3" aria-hidden="true" />
      {label}
    </span>
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
