'use client';

import { Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import type { SuppressionEntryResponse } from '@/features/cleanup/suppression/api/suppression-api';
import { RemoveConfirmDialog } from '@/features/cleanup/suppression/components/RemoveConfirmDialog';
import { SuppressionSourceBadge } from '@/features/cleanup/suppression/components/SuppressionSourceBadge';

function formatAddedAt(raw: string | undefined): string {
  if (!raw) return '';
  const parsed = new Date(raw);
  if (Number.isNaN(parsed.getTime())) return '';
  return parsed.toLocaleDateString();
}

export function SuppressionTable({ entries }: { entries: SuppressionEntryResponse[] }) {
  const t = useTranslations();
  const [pendingRemovalId, setPendingRemovalId] = useState<string | null>(null);

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('cleanup.suppression.col.target')}</TableHead>
            <TableHead>{t('cleanup.suppression.col.source')}</TableHead>
            <TableHead>{t('cleanup.suppression.col.added')}</TableHead>
            <TableHead className="w-12" aria-label="remove" />
          </TableRow>
        </TableHeader>
        <TableBody>
          {entries.map((entry) => {
            const target = entry.senderEmail ?? entry.senderDomain ?? '';
            return (
              <TableRow key={entry.id ?? target}>
                <TableCell className="font-medium">{target}</TableCell>
                <TableCell>
                  <SuppressionSourceBadge source={entry.reason ?? 'manual'} />
                </TableCell>
                <TableCell className="text-muted-foreground tabular-nums">
                  {formatAddedAt(entry.createdAt)}
                </TableCell>
                <TableCell>
                  <Button
                    type="button"
                    size="icon-sm"
                    variant="ghost"
                    aria-label={t('cleanup.suppression.remove.aria')}
                    onClick={() => setPendingRemovalId(entry.id ?? null)}
                    disabled={!entry.id}
                  >
                    <Trash2 />
                  </Button>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
      <RemoveConfirmDialog
        open={pendingRemovalId !== null}
        onOpenChange={(nextOpen) => {
          if (!nextOpen) setPendingRemovalId(null);
        }}
        suppressionId={pendingRemovalId}
      />
    </>
  );
}
