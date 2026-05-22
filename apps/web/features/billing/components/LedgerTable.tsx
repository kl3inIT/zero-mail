'use client';

import { useLocale, useTranslations } from 'next-intl';

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import type { BillingLedgerEntryResponse } from '@/features/billing/api/billing-api';
import { formatCredits, formatDateTime } from '@/lib/format';
import { cn } from '@/lib/utils';

export type LedgerEntry = BillingLedgerEntryResponse;

type LedgerTableProps = {
  rows?: LedgerEntry[];
  injectedRows?: LedgerEntry[];
};

export function LedgerTable({ rows = [], injectedRows }: LedgerTableProps) {
  const t = useTranslations();
  const locale = useLocale();
  const entries = injectedRows ?? rows;
  const ledgerTypeLabels = {
    topup: t('billing.ledger.type.topup'),
    grant: t('billing.ledger.type.grant'),
    reserve: t('billing.ledger.type.reserve'),
    settle: t('billing.ledger.type.settle'),
    release: t('billing.ledger.type.release'),
    expire: t('billing.ledger.type.expire'),
    adjustment: t('billing.ledger.type.adjustment'),
  };

  return (
    <div className="bg-card overflow-x-auto rounded-lg border" data-testid="ledger-table">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('billing.ledger.columns.timestamp')}</TableHead>
            <TableHead>{t('billing.ledger.columns.type')}</TableHead>
            <TableHead>{t('billing.ledger.columns.description')}</TableHead>
            <TableHead>{t('billing.ledger.columns.amount')}</TableHead>
            <TableHead>{t('billing.ledger.columns.balance')}</TableHead>
            <TableHead>{t('billing.ledger.columns.reference')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {entries.map((entry) => {
            const topup = entry.type === 'topup' || entry.amountCredits > 0;
            const ledgerTypeLabel =
              ledgerTypeLabels[entry.type as keyof typeof ledgerTypeLabels] ?? entry.type;
            return (
              <TableRow
                key={entry.id}
                data-testid="ledger-row"
                className={cn(topup && 'bg-green-soft/40 hover:bg-green-soft/60')}
              >
                <TableCell className="font-mono text-xs">
                  {formatDateTime(entry.timestamp, locale)}
                </TableCell>
                <TableCell>
                  <Badge
                    variant="outline"
                    className={topup ? 'border-green/30 text-green' : undefined}
                  >
                    {ledgerTypeLabel}
                  </Badge>
                </TableCell>
                <TableCell className="min-w-64 whitespace-normal">{entry.description}</TableCell>
                <TableCell className={cn('font-mono', topup && 'text-green')}>
                  {formatSignedCredits(entry.amountCredits)}
                </TableCell>
                <TableCell className="font-mono">
                  {entry.balanceAfterCredits === undefined
                    ? t('billing.ledger.valueMissing')
                    : formatCredits(entry.balanceAfterCredits)}
                </TableCell>
                <TableCell className="font-mono text-xs">
                  {entry.reference ?? t('billing.ledger.valueMissing')}
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}

function formatSignedCredits(value: number): string {
  const formatted = formatCredits(Math.abs(value));
  if (value > 0) return `+${formatted}`;
  if (value < 0) return `-${formatted}`;
  return formatted;
}
