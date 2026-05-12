'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { TableCell, TableRow } from '@/components/ui/table';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
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

  return (
    <TableRow data-testid="audit-table-row">
      <TableCell className="text-muted-foreground font-mono text-xs whitespace-nowrap">
        {formatAuditTimestamp(entry.timestamp)}
      </TableCell>
      <TableCell className="max-w-56 whitespace-normal">
        <MessageRef entry={entry} />
      </TableCell>
      <TableCell className="max-w-44 whitespace-normal">
        <span className="text-foreground text-sm">{entry.ruleName}</span>
      </TableCell>
      <TableCell>
        <ActionBadge entry={entry} />
      </TableCell>
      <TableCell className="min-w-72 whitespace-normal">
        <p className="text-foreground text-sm leading-6">{entry.reason}</p>
      </TableCell>
      <TableCell>
        {undone ? (
          <span className="text-muted-foreground text-xs">{t('triage.audit.undo.undone')}</span>
        ) : undoAvailable ? (
          <UndoButton entry={entry} onUndone={() => setUndone(true)} />
        ) : (
          <UndoClosedLabel />
        )}
      </TableCell>
    </TableRow>
  );
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
    return 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300';
  }
  if (normalizedAction.includes('draft')) {
    return 'bg-violet-500/10 text-violet-700 dark:text-violet-300';
  }
  return 'bg-muted text-muted-foreground';
}
