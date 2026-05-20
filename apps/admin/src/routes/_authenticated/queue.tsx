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
            Vận hành
          </p>
          <h1 className="text-ink text-xl font-semibold">Tình trạng hàng đợi</h1>
          <p className="text-muted-foreground mt-1 max-w-2xl text-sm">
            Hàng đợi worker tổng hợp từ <code>processing_job</code>. Không hiển thị payload công việc trên giao diện quản trị.
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
          <CardTitle>Số lượng theo loại công việc</CardTitle>
          <CardDescription>
            Số bản ghi đang chờ và đang xử lý trong hàng đợi, nhóm theo loại công việc của worker.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {queueHealth.isLoading ? (
            <Skeleton className="h-24 w-full" />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Loại công việc</TableHead>
                  <TableHead className="text-right">Đang chờ</TableHead>
                  <TableHead className="text-right">Đang xử lý</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(queueHealth.data?.depthByType ?? []).length === 0 && (
                  <TableRow>
                    <TableCell
                      colSpan={3}
                      className="text-muted-foreground h-16 text-center"
                    >
                      Không có công việc đang hoạt động.
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
              Công việc thất bại
            </CardTitle>
            <CardDescription>
              Đưa lại vào hàng đợi sẽ cấp ngân sách retry mới (attempts=0) và ghi một bản audit. Hệ thống không đọc hay hiển thị nội dung công việc đã lưu.
            </CardDescription>
          </div>
          {queueHealth.data && (
            <Badge variant="secondary" className="tabular-nums">
              {queueHealth.data.deadLetterCount} tổng
            </Badge>
          )}
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Công việc</TableHead>
                <TableHead>Loại</TableHead>
                <TableHead>Lỗi</TableHead>
                <TableHead className="text-right">Số lần retry</TableHead>
                <TableHead className="text-right">Lần admin đưa lại</TableHead>
                <TableHead>Thất bại gần nhất</TableHead>
                <TableHead className="text-right">Hành động</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {deadLetters.isLoading && (
                <TableRow>
                  <TableCell colSpan={7} className="text-muted-foreground h-24 text-center">
                    Đang tải danh sách công việc thất bại.
                  </TableCell>
                </TableRow>
              )}
              {!deadLetters.isLoading && (deadLetters.data?.rows.length ?? 0) === 0 && (
                <TableRow>
                  <TableCell colSpan={7} className="text-muted-foreground h-24 text-center">
                    Không có công việc thất bại.
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
                      Đưa lại
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
          actionLabel="Đưa lại công việc thất bại vào hàng đợi"
          targetLabel={`${rowPendingRequeue.jobType} • ${shortJobToken(rowPendingRequeue.jobId)}`}
          consequences={[
            'Bộ đếm số lần thử lại sẽ về 0 — worker được cấp ngân sách retry mới.',
            'Bộ đếm admin đưa lại sẽ tăng — các công việc lặp lại sẽ hiện trên KPI.',
            'Lần đưa lại được ghi vào chuỗi audit của quản trị viên (DEAD_LETTER_REQUEUED).',
          ]}
          confirmationToken={shortJobToken(rowPendingRequeue.jobId)}
          finalButtonLabel="Đưa lại công việc"
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
        label="Đang chờ"
        value={totalPending.toLocaleString()}
        hint={`${totalProcessing.toLocaleString()} đang xử lý`}
      />
      <KpiCard
        testId="kpi-oldest-age"
        label="Chờ lâu nhất"
        value={formatDuration(queueHealth.oldestUnleasedJobAgeSeconds)}
        hint="Thời gian công việc PENDING lâu nhất đã chờ."
      />
      <KpiCard
        testId="kpi-retry-rate"
        label="Tỷ lệ retry"
        value={formatRetryRate(queueHealth.retryHistogram)}
        hint="Tỷ lệ bản ghi đã thử lại ít nhất 1 lần."
      />
      <KpiCard
        testId="kpi-failure-rate"
        label="Tỷ lệ thất bại (24h)"
        value={`${(queueHealth.failureRateLast24h * 100).toFixed(1)}%`}
        hint="FAILED chia cho số bản ghi tạo trong 24h qua."
      />
      <KpiCard
        testId="kpi-dead-letter"
        label="Công việc thất bại"
        value={queueHealth.deadLetterCount.toLocaleString()}
        hint="Bản ghi ở DEAD_LETTER. Đưa lại từ bảng bên dưới."
      />
      <KpiCard
        testId="kpi-admin-requeued"
        label="Admin đưa lại (24h)"
        value={queueHealth.adminRequeuedLast24h.toLocaleString()}
        hint="Công việc tái phát do can thiệp của quản trị viên."
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
