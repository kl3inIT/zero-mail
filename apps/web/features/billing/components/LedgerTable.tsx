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
import { cn } from '@/lib/utils';

export type LedgerEntry = {
  id: string;
  timestamp: string;
  type: 'topup' | 'reserve' | 'settle' | 'release' | 'adjustment';
  description: string;
  amountCredits: number;
  balanceAfterCredits?: number;
  amountVnd?: number;
  reference?: string;
};

type LedgerTableProps = {
  rows?: LedgerEntry[];
  injectedRows?: LedgerEntry[];
};

export function LedgerTable({ rows = [], injectedRows }: LedgerTableProps) {
  const t = useTranslations();
  const locale = useLocale();
  const entries = injectedRows ?? rows;

  return (
    <div className="bg-card rounded-lg border" data-testid="ledger-table">
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
            return (
              <TableRow
                key={entry.id}
                data-testid="ledger-row"
                className={cn(topup && 'bg-green-soft/40 hover:bg-green-soft/60')}
              >
                <TableCell className="font-mono text-xs">
                  {formatTimestamp(entry.timestamp, locale)}
                </TableCell>
                <TableCell>
                  <Badge
                    variant="outline"
                    className={topup ? 'border-green/30 text-green' : undefined}
                  >
                    {t(`billing.ledger.type.${entry.type}`)}
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

function formatTimestamp(value: string, locale: string): string {
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) return value;
  return new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(timestamp);
}

function formatCredits(value: number): string {
  return new Intl.NumberFormat().format(value);
}

function formatSignedCredits(value: number): string {
  const formatted = formatCredits(Math.abs(value));
  if (value > 0) return `+${formatted}`;
  if (value < 0) return `-${formatted}`;
  return formatted;
}
