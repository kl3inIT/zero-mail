import { createFileRoute } from '@tanstack/react-router';
import { GaugeIcon, RefreshCwIcon } from 'lucide-react';
import { useMemo, useState } from 'react';

import { AutoRefreshIndicator } from '@/components/AutoRefreshIndicator';
import { ConfirmTwiceDialog } from '@/components/ConfirmTwiceDialog';
import { KpiCard } from '@/components/KpiCard';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { shortJobToken, type DeadLetterRow, type QueueHealth } from '@/features/queue/queue-api';
import { useDeadLetters } from '@/features/queue/use-dead-letters';
import {
  QUEUE_REFRESH_INTERVAL_MS,
  useQueueHealth,
} from '@/features/queue/use-queue-health';
import { useRequeueDeadLetter } from '@/features/queue/use-requeue';

export const Route = createFileRoute('/_authenticated/queue')({
  component: QueueRoute,
});

function QueueRoute() {
  const [paused, setPaused] = useState(false);
  const queueHealth = useQueueHealth({ paused });
  const deadLetters = useDeadLetters(null, 25);
  const requeueMutation = useRequeueDeadLetter();
  const [rowPendingRequeue, setRowPendingRequeue] = useState<DeadLetterRow | null>(null);

  const lastUpdatedAt = useMemo(() => {
    if (!queueHealth.data) return null;
    // `snapshotAt` is an ISO-8601 string from the backend.
    return new Date(queueHealth.data.snapshotAt);
  }, [queueHealth.data]);

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <p className="text-muted-foreground font-mono text-[11px] tracking-wider uppercase">
            Operations
          </p>
          <h1 className="text-ink text-xl font-semibold">Queue health</h1>
          <p className="text-muted-foreground mt-1 max-w-2xl text-sm">
            Worker queue aggregates over <code>processing_job</code>. No job payload is exposed
            to admin surfaces.
          </p>
        </div>
        <AutoRefreshIndicator
          lastUpdatedAt={lastUpdatedAt}
          intervalMs={QUEUE_REFRESH_INTERVAL_MS}
          paused={paused}
          onPauseToggle={() => setPaused((previous) => !previous)}
        />
      </header>

      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
        <KpiTiles queueHealth={queueHealth.data} loading={queueHealth.isLoading} />
      </section>

      <Card>
        <CardHeader>
          <CardTitle>Depth by job type</CardTitle>
          <CardDescription>
            Pending and processing rows in the active queue, grouped by worker job type.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {queueHealth.isLoading ? (
            <Skeleton className="h-24 w-full" />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Job type</TableHead>
                  <TableHead className="text-right">Pending</TableHead>
                  <TableHead className="text-right">Processing</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(queueHealth.data?.depthByType ?? []).length === 0 && (
                  <TableRow>
                    <TableCell
                      colSpan={3}
                      className="text-muted-foreground h-16 text-center"
                    >
                      No active jobs.
                    </TableCell>
                  </TableRow>
                )}
                {queueHealth.data?.depthByType.map((row) => (
                  <TableRow key={row.jobType}>
                    <TableCell className="font-mono text-xs">{row.jobType}</TableCell>
                    <TableCell className="text-right tabular-nums">
                      {row.pendingCount}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {row.processingCount}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between gap-4">
          <div>
            <CardTitle className="flex items-center gap-2">
              <GaugeIcon className="size-4" />
              Dead letters
            </CardTitle>
            <CardDescription>
              Re-queue restores a fresh retry budget (attempts=0) and stamps an audit row. The
              job&apos;s stored body is never read or shown.
            </CardDescription>
          </div>
          {queueHealth.data && (
            <Badge variant="secondary" className="tabular-nums">
              {queueHealth.data.deadLetterCount} total
            </Badge>
          )}
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Job</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Failure</TableHead>
                <TableHead className="text-right">Retries</TableHead>
                <TableHead className="text-right">Admin re-queues</TableHead>
                <TableHead>Last failed</TableHead>
                <TableHead className="text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {deadLetters.isLoading && (
                <TableRow>
                  <TableCell colSpan={7} className="text-muted-foreground h-24 text-center">
                    Loading dead letters.
                  </TableCell>
                </TableRow>
              )}
              {!deadLetters.isLoading && (deadLetters.data?.rows.length ?? 0) === 0 && (
                <TableRow>
                  <TableCell colSpan={7} className="text-muted-foreground h-24 text-center">
                    No dead letters.
                  </TableCell>
                </TableRow>
              )}
              {deadLetters.data?.rows.map((row) => (
                <TableRow key={row.jobId}>
                  <TableCell className="font-mono text-xs">{shortJobToken(row.jobId)}</TableCell>
                  <TableCell className="font-mono text-xs">{row.jobType}</TableCell>
                  <TableCell className="font-mono text-xs">
                    {row.lastFailureReason ?? '—'}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{row.retryCount}</TableCell>
                  <TableCell className="text-right tabular-nums">
                    {row.adminRequeueCount}
                  </TableCell>
                  <TableCell className="text-muted-foreground font-mono text-xs">
                    {row.lastFailedAt ?? '—'}
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setRowPendingRequeue(row)}
                    >
                      <RefreshCwIcon className="size-3.5" />
                      Re-queue
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {rowPendingRequeue && (
        <ConfirmTwiceDialog
          open
          onOpenChange={(nextOpen) => {
            if (!nextOpen) setRowPendingRequeue(null);
          }}
          actionLabel="Re-queue dead-letter job"
          targetLabel={`${rowPendingRequeue.jobType} • ${shortJobToken(rowPendingRequeue.jobId)}`}
          consequences={[
            'Attempts counter resets to 0 — worker gets a fresh retry budget.',
            'Admin re-queue counter increments — repeat offenders surface in the KPI.',
            'Re-queue is recorded in the admin audit chain (DEAD_LETTER_REQUEUED).',
          ]}
          confirmationToken={shortJobToken(rowPendingRequeue.jobId)}
          finalButtonLabel="Re-queue job"
          variant="warning"
          onConfirm={async (reason) =>
            requeueMutation.mutateAsync({
              jobId: rowPendingRequeue.jobId,
              reason,
            })
          }
        />
      )}
    </div>
  );
}

function KpiTiles({
  queueHealth,
  loading,
}: {
  queueHealth: QueueHealth | undefined;
  loading: boolean;
}) {
  if (loading || !queueHealth) {
    return (
      <>
        {Array.from({ length: 6 }).map((_, kpiIndex) => (
          <Skeleton key={kpiIndex} className="h-24" />
        ))}
      </>
    );
  }
  const totalPending = queueHealth.depthByType.reduce(
    (accumulator, row) => accumulator + row.pendingCount,
    0,
  );
  const totalProcessing = queueHealth.depthByType.reduce(
    (accumulator, row) => accumulator + row.processingCount,
    0,
  );
  return (
    <>
      <KpiCard
        testId="kpi-pending"
        label="Pending"
        value={totalPending.toLocaleString()}
        hint={`${totalProcessing.toLocaleString()} currently processing`}
      />
      <KpiCard
        testId="kpi-oldest-age"
        label="Oldest unleased"
        value={formatDuration(queueHealth.oldestUnleasedJobAgeSeconds)}
        hint="Time the oldest PENDING job has been waiting."
      />
      <KpiCard
        testId="kpi-retry-rate"
        label="Retry rate"
        value={formatRetryRate(queueHealth.retryHistogram)}
        hint="Share of rows that already attempted 1+ times."
      />
      <KpiCard
        testId="kpi-failure-rate"
        label="Failure rate (24h)"
        value={`${(queueHealth.failureRateLast24h * 100).toFixed(1)}%`}
        hint="FAILED ÷ rows created in the last 24h."
      />
      <KpiCard
        testId="kpi-dead-letter"
        label="Dead letters"
        value={queueHealth.deadLetterCount.toLocaleString()}
        hint="Rows in DEAD_LETTER. Re-queue from the table below."
      />
      <KpiCard
        testId="kpi-admin-requeued"
        label="Admin-requeued (24h)"
        value={queueHealth.adminRequeuedLast24h.toLocaleString()}
        hint="Repeat offenders flagged by operator intervention."
      />
    </>
  );
}

function formatDuration(seconds: number): string {
  if (!seconds || seconds <= 0) return '—';
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m`;
}

function formatRetryRate(
  histogram: { attemptsBucket: number; rowCount: number }[],
): string {
  const total = histogram.reduce((accumulator, row) => accumulator + row.rowCount, 0);
  if (total === 0) return '0%';
  const retried = histogram
    .filter((row) => row.attemptsBucket >= 1)
    .reduce((accumulator, row) => accumulator + row.rowCount, 0);
  return `${((retried / total) * 100).toFixed(1)}%`;
}
