'use client';

import { Fragment } from 'react';

import { UndoBoundary } from '@/features/triage/components/AuditLog';
import { AuditRow, shouldShowUndoBoundary } from '@/features/triage/components/AuditRow';
import type { AuditEntry } from '@/features/triage/api/triage-api';

type AuditTableProps = {
  entries: AuditEntry[];
  now: Date;
};

export function AuditTable({ entries, now }: AuditTableProps) {
  return (
    <div
      className="bg-card overflow-hidden rounded-xl border border-[#0a3d3a]/15 shadow-sm"
      data-testid="audit-table"
    >
      <div className="divide-y divide-[#0a3d3a]/10">
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
