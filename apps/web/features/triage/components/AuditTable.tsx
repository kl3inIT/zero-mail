'use client';

import { Fragment } from 'react';

import { ActiveMailboxBadge } from '@/features/mailbox/components/ActiveMailboxBadge';
import { UndoBoundary } from '@/features/triage/components/AuditLog';
import { AuditRow } from '@/features/triage/components/AuditRow';
import { shouldShowUndoBoundary } from '@/features/triage/components/audit-row-utils';
import type { AuditEntry } from '@/features/triage/api/triage-api';

type AuditTableProps = {
  entries: AuditEntry[];
  now: Date;
};

export function AuditTable({ entries, now }: AuditTableProps) {
  return (
    <div className="bg-card overflow-hidden rounded-xl border shadow-sm" data-testid="audit-table">
      <div className="border-border flex border-b px-4 py-2">
        <ActiveMailboxBadge />
      </div>
      <div className="divide-border divide-y">
        {entries.map((entry, index) => (
          <Fragment key={entry.id}>
            {shouldShowUndoBoundary(entries, index, now) ? (
              <div className="bg-muted/30 px-4 py-1.5">
                <UndoBoundary />
              </div>
            ) : null}
            <AuditRow entry={entry} now={now} />
          </Fragment>
        ))}
      </div>
    </div>
  );
}
