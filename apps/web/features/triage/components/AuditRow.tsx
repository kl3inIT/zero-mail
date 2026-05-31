'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { Archive, FileEdit, Tag, Wand2 } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { GenerateDraftButton } from '@/features/needs-reply/components/GenerateDraftButton';
import { AuditSafetyNetBadge } from '@/features/triage/components/AuditSafetyNetBadge';
import { UndoButton } from '@/features/triage/components/UndoButton';
import type { AuditEntry } from '@/features/triage/api/triage-api';
import {
  formatAuditTimestamp,
  isUndoAvailable,
  shouldShowDraftAction,
} from '@/features/triage/components/audit-row-utils';
import { cn } from '@/lib/utils';

type AuditRowProps = {
  entry: AuditEntry;
  now: Date;
};

export function AuditRow({ entry, now }: AuditRowProps) {
  const t = useTranslations();
  const [undone, setUndone] = useState(Boolean(entry.undone));
  const undoAvailable = isUndoAvailable(entry, now) && !undone;

  const senderEmail = entry.messageRef?.sender;
  const rawSubject = entry.messageRef?.subject;
  const hasSubject = Boolean(rawSubject && rawSubject.trim());
  const senderParts = splitSenderEmail(senderEmail, t('triage.audit.message.unknownSender'));

  return (
    <div
      className="group hover:bg-muted flex items-center gap-4 px-5 py-3.5 transition-colors"
      data-testid="audit-table-row"
    >
      <div
        className={cn(
          'flex size-10 shrink-0 items-center justify-center rounded-full',
          avatarClassName(entry.action),
        )}
        aria-hidden="true"
      >
        <ActionIcon action={entry.action} />
      </div>

      <div className="flex min-w-0 flex-1 items-center gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 items-baseline gap-2">
            <span
              className="text-foreground shrink-0 truncate text-sm font-semibold"
              title={senderEmail ?? undefined}
            >
              {senderParts.handle}
            </span>
            {senderParts.domain ? (
              <span className="text-muted-foreground shrink-0 truncate text-xs">
                @{senderParts.domain}
              </span>
            ) : null}
            {hasSubject ? (
              <>
                <span className="text-muted-foreground/40 shrink-0 text-xs" aria-hidden="true">
                  ·
                </span>
                <span className="text-muted-foreground min-w-0 truncate text-sm">{rawSubject}</span>
              </>
            ) : null}
          </div>
          <div className="text-muted-foreground/80 mt-0.5 flex min-w-0 items-center gap-1.5 text-xs">
            <Wand2 className="size-3 shrink-0" aria-hidden="true" />
            <span className="truncate">{entry.ruleName}</span>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-4">
          <div className="flex max-w-64 flex-col items-end gap-1">
            <ActionBadge entry={entry} />
            <AuditSafetyNetBadge pattern={entry.blockedBySafetyNetPattern} />
          </div>
          <time
            className="text-muted-foreground hidden w-24 shrink-0 text-right text-xs tabular-nums sm:block"
            title={new Date(entry.timestamp).toLocaleString()}
          >
            {formatAuditTimestamp(entry.timestamp, now)}
          </time>
          <div className="flex w-24 shrink-0 items-center justify-end gap-1">
            {shouldShowDraftAction(entry) ? (
              <GenerateDraftButton
                gmailThreadId={entry.gmailThreadId}
                draftStatus={entry.draftId ? 'DRAFT_READY' : 'NO_DRAFT'}
              />
            ) : null}
            {undone ? (
              <span className="text-muted-foreground text-xs">{t('triage.audit.undo.undone')}</span>
            ) : undoAvailable ? (
              <UndoButton entry={entry} onUndone={() => setUndone(true)} />
            ) : (
              <UndoClosedLabel />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function splitSenderEmail(
  email: string | undefined,
  fallback: string,
): { handle: string; domain: string | null } {
  if (!email) return { handle: fallback, domain: null };
  const atIndex = email.lastIndexOf('@');
  if (atIndex < 0) return { handle: email, domain: null };
  return { handle: email.slice(0, atIndex), domain: email.slice(atIndex + 1) };
}

function ActionIcon({ action }: { action: string }) {
  const normalized = action.toLowerCase();
  if (normalized.includes('archive')) return <Archive className="size-4" />;
  if (normalized.includes('draft')) return <FileEdit className="size-4" />;
  return <Tag className="size-4" />;
}

function avatarClassName(action: string): string {
  const normalized = action.toLowerCase();
  if (normalized.includes('archive')) {
    return 'bg-sky-50 text-sky-700 dark:bg-sky-900/20 dark:text-sky-400';
  }
  if (normalized.includes('label')) {
    return 'bg-accent text-accent-foreground';
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
      className={cn(
        'border-transparent px-2 py-0.5 text-[11px] font-semibold tracking-wide uppercase',
        actionBadgeClassName(entry.action),
      )}
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

function actionBadgeClassName(action: string): string {
  const normalizedAction = action.toLowerCase();
  if (normalizedAction.includes('archive')) {
    return 'bg-sky-500/15 text-sky-700 dark:bg-sky-500/20 dark:text-sky-300';
  }
  if (normalizedAction.includes('label')) {
    return 'bg-primary/15 text-primary';
  }
  if (normalizedAction.includes('draft')) {
    return 'bg-amber-500/15 text-amber-800 dark:bg-amber-500/20 dark:text-amber-300';
  }
  return 'bg-muted text-muted-foreground';
}
