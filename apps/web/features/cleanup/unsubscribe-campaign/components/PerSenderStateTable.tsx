'use client';

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
import type { PerSenderStateResponse } from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import { PerSenderStateBadge } from '@/features/cleanup/unsubscribe-campaign/components/PerSenderStateBadge';
import { useRetrySender } from '@/features/cleanup/unsubscribe-campaign/hooks/useRetrySender';

export function PerSenderStateTable({
  jobId,
  perSender,
}: {
  jobId: string;
  perSender: PerSenderStateResponse[];
}) {
  const t = useTranslations();
  const retry = useRetrySender();

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('cleanup.unsubscribe.status.col.sender')}</TableHead>
          <TableHead>{t('cleanup.unsubscribe.status.col.state')}</TableHead>
          <TableHead className="tabular-nums">
            {t('cleanup.unsubscribe.status.col.archived')}
          </TableHead>
          <TableHead>{t('cleanup.unsubscribe.status.col.action')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {perSender.map((senderState) => {
          const senderEmail = senderState.senderEmail ?? '';
          const state = senderState.state ?? 'PENDING';
          const showRetry = state === 'FAILED';
          return (
            <TableRow key={senderEmail}>
              <TableCell className="font-medium">{senderEmail}</TableCell>
              <TableCell>
                <PerSenderStateBadge state={state} />
              </TableCell>
              <TableCell className="tabular-nums">
                {senderState.archivedMessageCount ?? 0}
              </TableCell>
              <TableCell>
                {showRetry && (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    disabled={retry.isPending}
                    onClick={() => retry.mutate({ jobId, senderEmail })}
                  >
                    {t('cleanup.unsubscribe.status.retry')}
                  </Button>
                )}
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
