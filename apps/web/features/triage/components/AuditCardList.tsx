'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { Card, CardContent, CardFooter, CardHeader } from '@/components/ui/card';
import { GenerateDraftButton } from '@/features/needs-reply/components/GenerateDraftButton';
import { UndoBoundary } from '@/features/triage/components/AuditLog';
import {
  ActionBadge,
  formatAuditTimestamp,
  isUndoAvailable,
  MessageRef,
  shouldShowDraftAction,
  shouldShowUndoBoundary,
  UndoClosedLabel,
} from '@/features/triage/components/AuditRow';
import { UndoButton } from '@/features/triage/components/UndoButton';
import type { AuditEntry } from '@/features/triage/api/triage-api';

type AuditCardListProps = {
  entries: AuditEntry[];
  now: Date;
};

export function AuditCardList({ entries, now }: AuditCardListProps) {
  return (
    <div className="space-y-3" data-testid="audit-card-list">
      {entries.map((entry, index) => (
        <div key={entry.id}>
          {shouldShowUndoBoundary(entries, index, now) ? <UndoBoundary /> : null}
          <AuditCard entry={entry} now={now} />
        </div>
      ))}
    </div>
  );
}

function AuditCard({ entry, now }: { entry: AuditEntry; now: Date }) {
  const t = useTranslations();
  const [undone, setUndone] = useState(Boolean(entry.undone));
  const undoAvailable = isUndoAvailable(entry, now) && !undone;

  return (
    <Card size="sm" data-testid="audit-card">
      <CardHeader className="grid-cols-[1fr_auto]">
        <div className="min-w-0 space-y-1">
          <ActionBadge entry={entry} />
          <p className="text-muted-foreground font-mono text-xs">
            {formatAuditTimestamp(entry.timestamp)}
          </p>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <MessageRef entry={entry} />
        <div className="grid gap-1">
          <span className="text-muted-foreground text-xs">{t('triage.audit.whenLabel')}</span>
          <span className="text-foreground text-sm">{entry.ruleName}</span>
        </div>
        <div className="grid gap-1">
          <span className="text-muted-foreground text-xs">{t('triage.audit.thenLabel')}</span>
          <span className="text-foreground text-sm">{entry.actionLabel}</span>
        </div>
        <div className="grid gap-1">
          <span className="text-muted-foreground text-xs">{t('triage.audit.whyLabel')}</span>
          <p className="text-foreground text-sm leading-6" data-testid="audit-card-reason">
            {entry.reason || t('triage.audit.noReason')}
          </p>
        </div>
      </CardContent>
      <CardFooter className="justify-between gap-3">
        <div className="flex flex-wrap items-start gap-2">
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
      </CardFooter>
    </Card>
  );
}
