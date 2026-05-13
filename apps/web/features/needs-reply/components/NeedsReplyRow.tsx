'use client';

import { useState } from 'react';
import { ExternalLink, X } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { TableCell, TableRow } from '@/components/ui/table';
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
  variant?: 'desktop' | 'mobile';
};

export function NeedsReplyRow({
  row,
  activeBucket,
  now = new Date(),
  variant = 'desktop',
}: NeedsReplyRowProps) {
  const [draftStatus, setDraftStatus] = useState<DraftStatus>(row.draftStatus);

  return variant === 'mobile' ? (
    <MobileNeedsReplyRow
      row={row}
      activeBucket={activeBucket}
      draftStatus={draftStatus}
      now={now}
      onDraftReady={() => setDraftStatus('DRAFT_READY')}
    />
  ) : (
    <DesktopNeedsReplyRow
      row={row}
      activeBucket={activeBucket}
      draftStatus={draftStatus}
      now={now}
      onDraftReady={() => setDraftStatus('DRAFT_READY')}
    />
  );
}

function DesktopNeedsReplyRow({
  row,
  activeBucket,
  draftStatus,
  now,
  onDraftReady,
}: {
  row: NeedsReplyRowModel;
  activeBucket: NeedsReplyBucket;
  draftStatus: DraftStatus;
  now: Date;
  onDraftReady: () => void;
}) {
  const t = useTranslations();

  return (
    <TableRow data-testid="needs-reply-row">
      <TableCell className="max-w-72 py-2">
        <span className="text-foreground block truncate text-sm font-medium">
          {row.subject || t('triage.audit.message.untitled')}
        </span>
      </TableCell>
      <TableCell className="text-muted-foreground max-w-52 truncate py-2 text-sm">
        {row.otherParty || t('triage.audit.message.unknownSender')}
      </TableCell>
      <TableCell className="py-2">
        <LastActivity value={row.lastActivityAt} now={now} />
      </TableCell>
      <TableCell className="py-2">
        <DraftStatusBadge draftStatus={draftStatus} />
      </TableCell>
      <TableCell className="py-2">
        <div className="flex justify-end gap-1.5">
          {activeBucket === 'to-reply' ? (
            <GenerateDraftButton
              gmailThreadId={row.gmailThreadId}
              draftStatus={draftStatus}
              onDraftReady={onDraftReady}
            />
          ) : null}
          <OpenInGmailLink href={row.openInGmailUrl} />
          <ResolveButton gmailThreadId={row.gmailThreadId} />
        </div>
      </TableCell>
    </TableRow>
  );
}

function MobileNeedsReplyRow({
  row,
  activeBucket,
  draftStatus,
  now,
  onDraftReady,
}: {
  row: NeedsReplyRowModel;
  activeBucket: NeedsReplyBucket;
  draftStatus: DraftStatus;
  now: Date;
  onDraftReady: () => void;
}) {
  const t = useTranslations();

  return (
    <div className="bg-card grid gap-3 rounded-lg border p-3" data-testid="needs-reply-mobile-row">
      <div className="flex min-w-0 items-start justify-between gap-3">
        <span className="text-foreground min-w-0 truncate text-sm font-medium">
          {row.subject || t('triage.audit.message.untitled')}
        </span>
        <DraftStatusBadge draftStatus={draftStatus} />
      </div>
      <div className="text-muted-foreground flex min-w-0 items-center justify-between gap-3 text-xs">
        <span className="min-w-0 truncate">
          {row.otherParty || t('triage.audit.message.unknownSender')}
        </span>
        <LastActivity value={row.lastActivityAt} now={now} />
      </div>
      <div className="flex items-start gap-2">
        {activeBucket === 'to-reply' ? (
          <GenerateDraftButton
            gmailThreadId={row.gmailThreadId}
            draftStatus={draftStatus}
            compact
            onDraftReady={onDraftReady}
          />
        ) : null}
        <OpenInGmailLink href={row.openInGmailUrl} large />
        <ResolveButton gmailThreadId={row.gmailThreadId} large />
      </div>
    </div>
  );
}

export function DraftStatusBadge({ draftStatus }: { draftStatus: DraftStatus }) {
  const t = useTranslations();
  const label =
    draftStatus === 'DRAFT_READY'
      ? t('needsReply.status.draftReady')
      : draftStatus === 'DRAFT_SENT'
        ? t('needsReply.status.draftSent')
        : t('needsReply.status.noDraft');

  return (
    <Badge
      variant="outline"
      className={cn('border-transparent', {
        'bg-muted text-muted-foreground': draftStatus === 'NO_DRAFT',
        'bg-blue-500/10 text-blue-700 dark:text-blue-300': draftStatus === 'DRAFT_READY',
        'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300': draftStatus === 'DRAFT_SENT',
      })}
    >
      {label}
    </Badge>
  );
}

function LastActivity({ value, now }: { value: string; now: Date }) {
  const absolute = formatAbsoluteTime(value);

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger>
          <span className="text-muted-foreground inline-flex cursor-default font-mono text-xs">
            {formatRelativeTime(value, now)}
          </span>
        </TooltipTrigger>
        <TooltipContent>{absolute}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

function OpenInGmailLink({ href, large = false }: { href: string; large?: boolean }) {
  const t = useTranslations();

  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      aria-label={t('needsReply.action.openInGmail')}
      className={cn(buttonVariants({ variant: 'ghost', size: large ? 'icon-lg' : 'icon-sm' }))}
    >
      <ExternalLink className="size-4" aria-hidden="true" />
      <span className="sr-only">{t('needsReply.action.openInGmail')}</span>
    </a>
  );
}

function ResolveButton({
  gmailThreadId,
  large = false,
}: {
  gmailThreadId: string;
  large?: boolean;
}) {
  const t = useTranslations();
  const markResolved = useMarkResolved();

  return (
    <Button
      type="button"
      variant="ghost"
      size={large ? 'icon-lg' : 'icon-sm'}
      aria-label={t('needsReply.action.markResolved')}
      disabled={markResolved.isPending}
      onClick={() => markResolved.mutate(gmailThreadId)}
    >
      <X className="size-4" aria-hidden="true" />
      <span className="sr-only">{t('needsReply.action.markResolved')}</span>
    </Button>
  );
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
