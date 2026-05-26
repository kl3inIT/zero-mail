'use client';

import Link from 'next/link';
import { useMemo } from 'react';
import { useTranslations } from 'next-intl';

import { Alert, AlertTitle } from '@/components/ui/alert';
import { Progress } from '@/components/ui/progress';
import { Skeleton } from '@/components/ui/skeleton';
import { useCampaignStatus } from '@/features/cleanup/unsubscribe-campaign/hooks/useCampaignStatus';
import { PerSenderStateTable } from '@/features/cleanup/unsubscribe-campaign/components/PerSenderStateTable';
import { UndoBanner } from '@/features/cleanup/unsubscribe-campaign/components/UndoBanner';

function shortenJobId(jobId: string): string {
  if (jobId.length <= 8) return jobId;
  return jobId.slice(0, 8);
}

function statusLabel(
  t: (
    key:
      | 'cleanup.unsubscribe.status.queued'
      | 'cleanup.unsubscribe.status.running'
      | 'cleanup.unsubscribe.status.completed'
      | 'cleanup.unsubscribe.status.failed',
  ) => string,
  status: string | undefined,
): string {
  switch (status) {
    case 'RUNNING':
      return t('cleanup.unsubscribe.status.running');
    case 'COMPLETED':
      return t('cleanup.unsubscribe.status.completed');
    case 'FAILED':
      return t('cleanup.unsubscribe.status.failed');
    case 'QUEUED':
    default:
      return t('cleanup.unsubscribe.status.queued');
  }
}

export function CampaignStatusPage({ jobId }: { jobId: string }) {
  const t = useTranslations();
  const statusQuery = useCampaignStatus(jobId);
  const shortId = useMemo(() => shortenJobId(jobId), [jobId]);

  const status = statusQuery.data?.status;
  const perSender = statusQuery.data?.perSender ?? [];
  const okCount = perSender.filter((senderState) => senderState.state === 'OK').length;
  const failedCount = perSender.filter((senderState) => senderState.state === 'FAILED').length;
  const totalCount = statusQuery.data?.totalSenderCount ?? perSender.length;
  const progressPct = statusQuery.data?.progressPct ?? 0;
  const totalHistoryMessageCount = statusQuery.data?.totalHistoryMessageCount ?? 0;

  return (
    <div className="flex flex-col gap-5">
      <nav className="text-muted-foreground text-xs" aria-label="breadcrumb">
        <Link href="/cleanup/unsubscribe-campaign" className="hover:underline">
          {t('cleanup.unsubscribe.status.breadcrumb', { shortId })}
        </Link>
      </nav>

      {statusQuery.isPending && (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-3 w-full" />
          {Array.from({ length: 4 }).map((_, idx) => (
            <Skeleton key={idx} className="h-10 w-full" />
          ))}
        </div>
      )}

      {statusQuery.isError && (
        <Alert variant="destructive">
          <AlertTitle>{t('cleanup.unsubscribe.status.error')}</AlertTitle>
        </Alert>
      )}

      {!statusQuery.isPending && statusQuery.data && (
        <>
          <div className="flex flex-col gap-2">
            <Progress value={progressPct} />
            <div className="flex flex-wrap items-center gap-2">
              <span className="bg-muted text-foreground rounded-md px-2 py-0.5 text-xs font-medium">
                {statusLabel(t, status)}
              </span>
              <p className="text-muted-foreground text-xs tabular-nums">
                {t('cleanup.unsubscribe.status.progress', {
                  percent: progressPct,
                  okCount,
                  failedCount,
                  totalCount,
                })}
              </p>
            </div>
          </div>

          {status === 'COMPLETED' && (
            <UndoBanner
              jobId={jobId}
              appliedAt={statusQuery.data.appliedAt}
              archivedCount={totalHistoryMessageCount}
              undoAvailable={statusQuery.data.undoAvailable ?? true}
            />
          )}

          {status === 'FAILED' && (
            <Alert variant="destructive">
              <AlertTitle>{t('cleanup.unsubscribe.status.errorBanner')}</AlertTitle>
            </Alert>
          )}

          <PerSenderStateTable jobId={jobId} perSender={perSender} />
        </>
      )}
    </div>
  );
}
