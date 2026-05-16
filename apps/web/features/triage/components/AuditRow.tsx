'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { GenerateDraftButton } from '@/features/needs-reply/components/GenerateDraftButton';
import { UndoButton } from '@/features/triage/components/UndoButton';
import type { AuditEntry } from '@/features/triage/api/triage-api';
import { cn } from '@/lib/utils';

type AuditRowProps = {
  entry: AuditEntry;
  now: Date;
};

export function AuditRow({ entry, now }: AuditRowProps) {
  const t = useTranslations();
  const [undone, setUndone] = useState(Boolean(entry.undone));
  const undoAvailable = isUndoAvailable(entry, now) && !undone;

  const sender = entry.messageRef?.sender || t('triage.audit.message.unknownSender');
  const subject = entry.messageRef?.subject || t('triage.audit.message.untitled');
  const initial = sender.charAt(0).toUpperCase();

  return (
    <div
      className="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-[#0a3d3a]/[0.04]"
      data-testid="audit-table-row"
    >
      {/* Action-colored avatar */}
      <div
        className={cn(
          'flex size-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold',
          avatarClassName(entry.action),
        )}
        aria-hidden="true"
      >
        {initial}
      </div>

      <div className="flex min-w-0 flex-1 items-center gap-3">
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex min-w-0 items-center gap-2">
            <span className="w-28 shrink-0 truncate text-sm font-medium">{sender}</span>
            <span className="min-w-0 truncate text-sm font-medium">{subject}</span>
          </div>
          <div className="flex min-w-0 flex-wrap items-center gap-1.5">
            <AuditFact label={t('triage.audit.whenLabel')} value={entry.ruleName} />
            <AuditFact label={t('triage.audit.thenLabel')} value={entry.actionLabel} action />
            <AuditFact
              label={t('triage.audit.whyLabel')}
              value={entry.reason || t('triage.audit.noReason')}
            />
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <ActionBadge entry={entry} />
          <time className="text-muted-foreground w-20 shrink-0 text-right font-mono text-xs">
            {formatAuditTimestamp(entry.timestamp)}
          </time>
          <div className="flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
            {shouldShowDraftAction(entry) ? (
              <GenerateDraftButton
                gmailThreadId={entry.gmailThreadId}
                draftStatus={entry.draftId ? 'DRAFT_READY' : 'NO_DRAFT'}
              />
            ) : null}
            {undone ? null : undoAvailable ? (
              <UndoButton entry={entry} onUndone={() => setUndone(true)} />
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}

function AuditFact({
  label,
  value,
  action = false,
}: {
  label: string;
  value: string;
  action?: boolean;
}) {
  return (
    <span
      className={cn(
        'inline-flex max-w-[16rem] items-center gap-1 rounded-sm px-1.5 py-0.5 text-[10px] leading-4',
        action
          ? 'bg-amber-500/10 text-amber-700 dark:text-amber-300'
          : 'bg-muted text-muted-foreground',
      )}
    >
      <span className="font-semibold tracking-wide uppercase">{label}</span>
      <span className="truncate">{value}</span>
    </span>
  );
}

function avatarClassName(action: string): string {
  const normalized = action.toLowerCase();
  if (normalized.includes('archive')) {
    return 'bg-sky-50 text-sky-700 dark:bg-sky-900/20 dark:text-sky-400';
  }
  if (normalized.includes('label')) {
    return 'bg-[#E7F0EF] text-[#0a3d3a] dark:bg-[#0a3d3a]/20 dark:text-[#E7F0EF]';
  }
  if (normalized.includes('draft')) {
    return 'bg-amber-50 text-amber-700 dark:bg-amber-900/20 dark:text-amber-400';
  }
  return 'bg-muted text-muted-foreground';
}

export function MessageRef({ entry }: { entry: AuditEntry }) {
  const t = useTranslations();
  const subject = entry.messageRef?.subject || t('triage.audit.message.untitled');
  const sender = entry.messageRef?.sender || t('triage.audit.message.unknownSender');

  return (
    <span className="grid min-w-0 gap-0.5">
      <span className="text-foreground truncate text-sm font-medium">{subject}</span>
      <span className="text-muted-foreground truncate text-xs">{sender}</span>
    </span>
  );
}

export function ActionBadge({ entry }: { entry: AuditEntry }) {
  return (
    <Badge
      variant="outline"
      className={cn('border-transparent', actionBadgeClassName(entry.action))}
    >
      {entry.actionLabel}
    </Badge>
  );
}

export function UndoClosedLabel() {
  const t = useTranslations();

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger>
          <span className="text-muted-foreground inline-flex cursor-default text-xs">
            {t('triage.audit.undo.windowClosed')}
          </span>
        </TooltipTrigger>
        <TooltipContent>{t('triage.audit.undo.windowTooltip')}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

export function isUndoAvailable(entry: AuditEntry, now: Date): boolean {
  return !entry.undone && new Date(entry.undoableUntil).getTime() > now.getTime();
}

export function shouldShowUndoBoundary(entries: AuditEntry[], index: number, now: Date): boolean {
  if (index === 0) return false;
  return isUndoAvailable(entries[index - 1], now) && !isUndoAvailable(entries[index], now);
}

export function shouldShowDraftAction(entry: AuditEntry): entry is AuditEntry & {
  gmailThreadId: string;
} {
  const normalizedAction = entry.action.toLowerCase().replace(/[-\s]+/g, '_');
  return normalizedAction === 'save_draft' && Boolean(entry.gmailThreadId);
}

export function formatAuditTimestamp(timestamp: string): string {
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(timestamp));
}

function actionBadgeClassName(action: string): string {
  const normalizedAction = action.toLowerCase();
  if (normalizedAction.includes('archive')) {
    return 'bg-sky-500/10 text-sky-700 dark:text-sky-300';
  }
  if (normalizedAction.includes('label')) {
    return 'bg-[#0a3d3a]/10 text-[#0a3d3a] dark:text-[#0a3d3a]/40';
  }
  if (normalizedAction.includes('draft')) {
    return 'bg-amber-500/10 text-amber-700 dark:text-amber-300';
  }
  return 'bg-muted text-muted-foreground';
}
