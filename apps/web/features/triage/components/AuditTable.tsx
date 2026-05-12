'use client';

import { Fragment } from 'react';
import { useTranslations } from 'next-intl';

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { UndoBoundary } from '@/features/triage/components/AuditLog';
import { AuditRow, shouldShowUndoBoundary } from '@/features/triage/components/AuditRow';
import type { AuditEntry } from '@/features/triage/api/triage-api';

type AuditTableProps = {
  entries: AuditEntry[];
  now: Date;
};

export function AuditTable({ entries, now }: AuditTableProps) {
  const t = useTranslations();

  return (
    <div className="bg-card rounded-lg border" data-testid="audit-table">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('triage.audit.columns.timestamp')}</TableHead>
            <TableHead>{t('triage.audit.columns.message')}</TableHead>
            <TableHead>{t('triage.audit.columns.rule')}</TableHead>
            <TableHead>{t('triage.audit.columns.action')}</TableHead>
            <TableHead>{t('triage.audit.columns.reason')}</TableHead>
            <TableHead>{t('triage.audit.columns.undo')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {entries.map((entry, index) => (
            <Fragment key={entry.id}>
              {shouldShowUndoBoundary(entries, index, now) ? (
                <TableRow key={`${entry.id}-boundary`}>
                  <TableCell colSpan={6} className="p-2">
                    <UndoBoundary />
                  </TableCell>
                </TableRow>
              ) : null}
              <AuditRow entry={entry} now={now} />
            </Fragment>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
