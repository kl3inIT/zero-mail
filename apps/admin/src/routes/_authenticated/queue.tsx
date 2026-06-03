import { createFileRoute } from '@tanstack/react-router';
import { GaugeIcon, RefreshCwIcon, TriangleAlertIcon, XIcon } from 'lucide-react';
import { useMemo, useState } from 'react';

import { AutoRefreshIndicator } from '@/components/AutoRefreshIndicator';
import { ConfirmTwiceDialog } from '@/components/ConfirmTwiceDialog';
import { KpiCard } from '@/components/KpiCard';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  shortJobToken,
  type JobRow,
  type QueueHealth,
} from '@/features/queue/queue-api';
import { useCancelJob, useForceRetryJob } from '@/features/queue/use-job-actions';
import { useJobDetail } from '@/features/queue/use-job-detail';
import { useJobs } from '@/features/queue/use-jobs';
import { QUEUE_REFRESH_INTERVAL_MS, useQueueHealth } from '@/features/queue/use-queue-health';
import { useRequeueDeadLetter } from '@/features/queue/use-requeue';
import { useSchedulers } from '@/features/schedulers/use-schedulers';
import type { Scheduler } from '@/features/schedulers/schedulers-api';

export const Route = createFileRoute('/_authenticated/queue')({
  component: QueueRoute,
});

const SMALL_SAMPLE_THRESHOLD = 10;
const STUCK_AFTER_MS = 5 * 60 * 1000;

const STATUS_LABELS: Record<string, string> = {
  SCHEDULED: 'Hẹn giờ',
  PENDING: 'Đang chờ',
  PROCESSING: 'Đang xử lý',
  COMPLETED: 'Hoàn tất',
  FAILED: 'Thất bại',
  DEAD_LETTER: 'Hỏng (dead-letter)',
  CANCELLED: 'Đã hủy',
};

function statusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status;
}

const STATUS_OPTIONS = [
  { value: 'ALL', label: 'Mọi trạng thái' },
  ...Object.entries(STATUS_LABELS).map(([value, label]) => ({ value, label })),
];

const JOB_TYPE_LABELS: Record<string, string> = {
  UNSUBSCRIBE_CAMPAIGN: 'Hủy đăng ký hàng loạt',
  CATALOG_SYNC: 'Đồng bộ danh mục',
  INBOX_PROJECTION_BACKFILL: 'Dựng lại hộp thư',
};

function jobTypeLabel(jobType: string): string {
  return JOB_TYPE_LABELS[jobType] ?? jobType;
}

const TYPE_OPTIONS = [
  { value: 'ALL', label: 'Mọi loại' },
  { value: 'UNSUBSCRIBE_CAMPAIGN', label: jobTypeLabel('UNSUBSCRIBE_CAMPAIGN') },
  { value: 'CATALOG_SYNC', label: jobTypeLabel('CATALOG_SYNC') },
];

type PendingAction = {
  row: JobRow;
  kind: 'force-retry' | 'cancel' | 'requeue';
};

function QueueRoute() {
  const [paused, setPaused] = useState(false);
  const [tab, setTab] = useState('jobs');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [cursor, setCursor] = useState<string | null>(null);
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);

  const queueHealth = useQueueHealth({ paused });
  const jobs = useJobs(
    {
      status: statusFilter === 'ALL' ? null : statusFilter,
      jobType: typeFilter === 'ALL' ? null : typeFilter,
    },
    cursor,
    25,
    { paused },
  );
  const schedulers = useSchedulers();
  const forceRetry = useForceRetryJob();
  const cancel = useCancelJob();
  const requeue = useRequeueDeadLetter();

  const lastUpdatedAt = useMemo(
    () => (queueHealth.data ? new Date(queueHealth.data.snapshotAt) : null),
    [queueHealth.data],
  );

  const rows = jobs.data?.rows ?? [];
  const stuckRows = rows.filter(isStuck);

  function resetPaging() {
    setCursor(null);
  }

  return (
    <div className="space-y-6">
      <header className="flex items-center justify-between gap-4">
        <h1 className="text-lg font-semibold text-ink">Hàng đợi &amp; Tác vụ định kỳ</h1>
        <AutoRefreshIndicator
          lastUpdatedAt={lastUpdatedAt}
          intervalMs={QUEUE_REFRESH_INTERVAL_MS}
          paused={paused}
          onPauseToggle={() => setPaused((previous) => !previous)}
        />
      </header>

      <CounterStrip queueHealth={queueHealth.data} loading={queueHealth.isLoading} />

      <Tabs value={tab} onValueChange={(value) => setTab(String(value))}>
        <TabsList>
          <TabsTrigger value="jobs">Hàng đợi job</TabsTrigger>
          <TabsTrigger value="schedulers">
            Tác vụ định kỳ
            {schedulers.data ? (
              <span className="text-muted-foreground ml-1.5 tabular-nums">
                {schedulers.data.length}
              </span>
            ) : null}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="jobs" className="space-y-4 pt-2">
          {stuckRows.length > 0 && (
            <div className="flex items-start gap-3 rounded-lg border border-amber/40 bg-amber-soft/50 px-4 py-3 text-sm">
              <TriangleAlertIcon className="mt-0.5 size-4 shrink-0 text-amber" />
              <div>
                <p className="font-semibold text-ink">
                  {stuckRows.length} công việc nghi bị kẹt
                </p>
                <p className="text-ink-2">
                  Đang PROCESSING nhưng không cập nhật quá 5 phút — worker có thể đã chết. Reaper sẽ
                  reset, hoặc hủy thủ công bên dưới.
                </p>
              </div>
            </div>
          )}

          <div className="flex flex-wrap items-center gap-2">
            <FilterSelect
              value={statusFilter}
              options={STATUS_OPTIONS}
              prefix="Trạng thái"
              onValueChange={(value) => {
                setStatusFilter(value);
                resetPaging();
              }}
            />
            <FilterSelect
              value={typeFilter}
              options={TYPE_OPTIONS}
              prefix="Loại"
              onValueChange={(value) => {
                setTypeFilter(value);
                resetPaging();
              }}
            />
          </div>

          <div className="overflow-hidden rounded-lg border border-border bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Công việc</TableHead>
                  <TableHead>Loại</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead className="text-right">Thử</TableHead>
                  <TableHead>Cập nhật</TableHead>
                  <TableHead className="text-right">Hành động</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {jobs.isLoading && (
                  <TableRow>
                    <TableCell colSpan={6} className="text-muted-foreground h-24 text-center">
                      Đang tải công việc.
                    </TableCell>
                  </TableRow>
                )}
                {!jobs.isLoading && rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} className="text-muted-foreground h-24 text-center">
                      Không có công việc khớp bộ lọc.
                    </TableCell>
                  </TableRow>
                )}
                {rows.map((row) => (
                  <JobTableRow
                    key={row.jobId}
                    row={row}
                    onView={() => setSelectedJobId(row.jobId)}
                    onAction={(kind) => setPendingAction({ row, kind })}
                  />
                ))}
              </TableBody>
            </Table>
            <div className="flex items-center justify-between border-t border-border px-4 py-2.5 text-xs text-muted-foreground">
              <span className="tabular-nums">
                {rows.length} / ~{jobs.data?.totalEstimate ?? 0} công việc
              </span>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={!jobs.data?.hasNextPage}
                onClick={() => setCursor(jobs.data?.nextCursor ?? null)}
              >
                Tải thêm
              </Button>
            </div>
          </div>
        </TabsContent>

        <TabsContent value="schedulers" className="pt-2">
          <SchedulersTab schedulers={schedulers.data} loading={schedulers.isLoading} />
        </TabsContent>
      </Tabs>

      <JobDetailDialog jobId={selectedJobId} onClose={() => setSelectedJobId(null)} />

      {pendingAction && (
        <JobActionDialog
          pendingAction={pendingAction}
          onClose={() => setPendingAction(null)}
          onConfirm={async (reason) => {
            const input = { jobId: pendingAction.row.jobId, reason };
            if (pendingAction.kind === 'force-retry') return forceRetry.mutateAsync(input);
            if (pendingAction.kind === 'cancel') return cancel.mutateAsync(input);
            return requeue.mutateAsync(input);
          }}
        />
      )}
    </div>
  );
}

function CounterStrip({
  queueHealth,
  loading,
}: {
  queueHealth: QueueHealth | undefined;
  loading: boolean;
}) {
  if (loading || !queueHealth) {
    return (
      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
        {Array.from({ length: 6 }).map((_, index) => (
          <Skeleton key={index} className="h-[76px] w-full rounded-xl" />
        ))}
      </section>
    );
  }
  const totalPending = queueHealth.depthByType.reduce((sum, row) => sum + row.pendingCount, 0);
  const totalProcessing = queueHealth.depthByType.reduce((sum, row) => sum + row.processingCount, 0);
  const smallSample = queueHealth.sampleSizeLast24h < SMALL_SAMPLE_THRESHOLD;
  const sampleText = `${queueHealth.failedCountLast24h}/${queueHealth.sampleSizeLast24h}`;
  return (
    <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
      <KpiCard testId="kpi-pending" label="Đang chờ" value={totalPending.toLocaleString()} />
      <KpiCard label="Đang xử lý" value={totalProcessing.toLocaleString()} />
      <KpiCard
        label="Chờ lâu nhất"
        value={formatDuration(queueHealth.oldestUnleasedJobAgeSeconds)}
      />
      <KpiCard label="Tỷ lệ thử lại" value={formatRetryRate(queueHealth.retryHistogram)} />
      <KpiCard
        testId="kpi-failure-rate"
        label="Thất bại 24h"
        value={smallSample ? '—' : `${(queueHealth.failureRateLast24h * 100).toFixed(1)}%`}
        hint={smallSample ? `Chưa đủ mẫu · ${sampleText}` : `${sampleText} trong 24h`}
      />
      <KpiCard
        testId="kpi-dead-letter"
        label="Hỏng (dead-letter)"
        value={queueHealth.deadLetterCount.toLocaleString()}
      />
    </section>
  );
}

function JobTableRow({
  row,
  onView,
  onAction,
}: {
  row: JobRow;
  onView: () => void;
  onAction: (kind: PendingAction['kind']) => void;
}) {
  const stuck = isStuck(row);
  return (
    <TableRow className={stuck ? 'bg-amber-soft/30' : undefined}>
      <TableCell className="font-mono text-sm">{shortJobToken(row.jobId)}</TableCell>
      <TableCell>
        <div className="text-ink text-sm">{jobTypeLabel(row.jobType)}</div>
        <div className="text-muted-foreground font-mono text-[11px]">{row.jobType}</div>
      </TableCell>
      <TableCell>
        <StatusBadge row={row} stuck={stuck} />
      </TableCell>
      <TableCell className="text-right tabular-nums">{row.attempts}</TableCell>
      <TableCell className="text-muted-foreground text-sm">
        {relativeTime(row.updatedAt ?? row.createdAt)}
      </TableCell>
      <TableCell>
        <div className="flex justify-end gap-1.5">
          <Button type="button" variant="outline" size="sm" onClick={onView}>
            Xem
          </Button>
          {row.status === 'FAILED' && (
            <Button
              type="button"
              size="sm"
              className="border-primary/40 bg-primary/10 text-primary hover:bg-primary/15"
              variant="outline"
              onClick={() => onAction('force-retry')}
            >
              <RefreshCwIcon className="size-3.5" />
              Chạy lại
            </Button>
          )}
          {row.status === 'DEAD_LETTER' && (
            <Button type="button" variant="outline" size="sm" onClick={() => onAction('requeue')}>
              <RefreshCwIcon className="size-3.5" />
              Đưa lại
            </Button>
          )}
          {(row.status === 'PENDING' || (row.status === 'PROCESSING' && stuck)) && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="border-destructive/40 text-destructive hover:bg-destructive-soft/40"
              onClick={() => onAction('cancel')}
            >
              Hủy
            </Button>
          )}
        </div>
      </TableCell>
    </TableRow>
  );
}

function StatusBadge({ row, stuck }: { row: JobRow; stuck: boolean }) {
  if (row.scheduled) {
    return (
      <Badge variant="secondary" className="bg-violet-soft text-violet">
        Hẹn giờ
      </Badge>
    );
  }
  const toneByStatus: Record<string, string> = {
    PENDING: 'bg-secondary text-ink-2',
    PROCESSING: 'bg-blue-soft text-blue',
    COMPLETED: 'bg-green-soft text-green',
    FAILED: 'bg-amber-soft text-amber',
    DEAD_LETTER: 'bg-destructive-soft text-destructive',
    CANCELLED: 'bg-secondary text-muted-foreground',
  };
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium ${
        toneByStatus[row.status] ?? 'bg-secondary text-ink-2'
      }`}
    >
      {statusLabel(row.status)}
      {stuck && (
        <span className="rounded-full bg-amber px-1 text-[10px] font-semibold text-white">kẹt</span>
      )}
    </span>
  );
}

function JobDetailDialog({ jobId, onClose }: { jobId: string | null; onClose: () => void }) {
  const detail = useJobDetail(jobId);
  return (
    <Dialog open={Boolean(jobId)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-lg gap-0 overflow-hidden p-0">
        <div className="flex items-center justify-between border-b border-border px-5 py-3.5">
          <DialogHeader>
            <DialogTitle className="text-sm">Chi tiết công việc</DialogTitle>
            <DialogDescription className="sr-only">
              Metadata của một công việc trong hàng đợi.
            </DialogDescription>
          </DialogHeader>
          <Button type="button" variant="ghost" size="icon-sm" onClick={onClose}>
            <XIcon className="size-4" />
          </Button>
        </div>
        {detail.isLoading || !detail.data ? (
          <div className="p-5">
            <Skeleton className="h-48 w-full" />
          </div>
        ) : (
          <>
            <dl className="divide-y divide-border/60 px-5 pb-4 text-sm">
              <DetailRow label="ID" value={detail.data.jobId} mono />
              <DetailRow label="Nguồn" value={detail.data.source} mono />
              <DetailRow
                label="Loại"
                value={`${jobTypeLabel(detail.data.jobType)} (${detail.data.jobType})`}
              />
              <DetailRow
                label="Trạng thái"
                value={
                  detail.data.scheduled
                    ? `Hẹn giờ (${statusLabel(detail.data.status)})`
                    : statusLabel(detail.data.status)
                }
              />
              <DetailRow label="Số lần thử" value={String(detail.data.attempts)} />
              <DetailRow label="Tạo lúc" value={formatTimestamp(detail.data.createdAt)} mono />
              <DetailRow label="Lần chạy kế" value={formatTimestamp(detail.data.nextRunAt)} mono />
              <DetailRow label="Bắt đầu" value={formatTimestamp(detail.data.startedAt)} mono />
              <DetailRow label="Heartbeat" value={formatTimestamp(detail.data.heartbeatAt)} mono />
              <DetailRow label="Hoàn tất" value={formatTimestamp(detail.data.completedAt)} mono />
              <DetailRow label="Lý do lỗi" value={detail.data.lastFailureReason ?? '—'} mono />
              <DetailRow label="Admin đưa lại" value={String(detail.data.adminRequeueCount)} />
            </dl>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}

function DetailRow({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex justify-between gap-4 py-2.5">
      <dt className="text-muted-foreground shrink-0">{label}</dt>
      <dd className={mono ? 'truncate font-mono text-sm text-ink' : 'text-ink'}>{value}</dd>
    </div>
  );
}

function JobActionDialog({
  pendingAction,
  onClose,
  onConfirm,
}: {
  pendingAction: PendingAction;
  onClose: () => void;
  onConfirm: (reason: string) => Promise<{ auditId?: string | null }>;
}) {
  const { row, kind } = pendingAction;
  const config = ACTION_CONFIG[kind];
  return (
    <ConfirmTwiceDialog
      open
      onOpenChange={(open) => !open && onClose()}
      actionLabel={config.actionLabel}
      targetLabel={`${row.jobType} • ${shortJobToken(row.jobId)}`}
      consequences={config.consequences}
      confirmationToken={shortJobToken(row.jobId)}
      finalButtonLabel={config.finalButtonLabel}
      variant={config.variant}
      onConfirm={onConfirm}
    />
  );
}

const ACTION_CONFIG: Record<
  PendingAction['kind'],
  {
    actionLabel: string;
    consequences: string[];
    finalButtonLabel: string;
    variant: 'destructive' | 'warning';
  }
> = {
  'force-retry': {
    actionLabel: 'Chạy lại công việc thất bại',
    consequences: [
      'Bộ đếm số lần thử về 0 — worker được cấp ngân sách retry mới.',
      'Bộ đếm admin đưa lại tăng, công việc sẽ chạy lại ngay.',
      'Ghi một bản audit (JOB_RETRY_FORCED).',
    ],
    finalButtonLabel: 'Chạy lại',
    variant: 'warning',
  },
  cancel: {
    actionLabel: 'Hủy công việc',
    consequences: [
      'Công việc chuyển sang CANCELLED và sẽ không được worker nhặt nữa.',
      'Chỉ áp dụng cho job PENDING hoặc PROCESSING đã kẹt (hết hạn heartbeat).',
      'Ghi một bản audit (JOB_CANCELLED).',
    ],
    finalButtonLabel: 'Hủy công việc',
    variant: 'destructive',
  },
  requeue: {
    actionLabel: 'Đưa lại công việc thất bại',
    consequences: [
      'Bộ đếm số lần thử về 0 — worker được cấp ngân sách retry mới.',
      'Bộ đếm admin đưa lại tăng — công việc lặp lại sẽ hiện trên KPI.',
      'Ghi một bản audit (DEAD_LETTER_REQUEUED).',
    ],
    finalButtonLabel: 'Đưa lại công việc',
    variant: 'warning',
  },
};

function SchedulersTab({
  schedulers,
  loading,
}: {
  schedulers: Scheduler[] | undefined;
  loading: boolean;
}) {
  return (
    <div className="overflow-hidden rounded-lg border border-border bg-card">
      <div className="flex items-center gap-2 border-b border-border px-5 py-3">
        <GaugeIcon className="size-4 text-muted-foreground" />
        <div>
          <h2 className="text-sm font-semibold text-ink">Tác vụ định kỳ</h2>
          <p className="text-muted-foreground mt-0.5 text-xs">
            Mọi cron / vòng lặp nền chạy theo lịch cố định (API + worker). Chỉ xem.
          </p>
        </div>
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Tác vụ</TableHead>
            <TableHead>Lịch</TableHead>
            <TableHead>Tiến trình</TableHead>
            <TableHead>Nhóm</TableHead>
            <TableHead>Lần chạy kế (UTC)</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {loading && (
            <TableRow>
              <TableCell colSpan={5} className="text-muted-foreground h-24 text-center">
                Đang tải tác vụ định kỳ.
              </TableCell>
            </TableRow>
          )}
          {(schedulers ?? []).map((scheduler) => (
            <TableRow key={scheduler.key}>
              <TableCell>
                <div className="text-ink">{scheduler.displayName}</div>
                {scheduler.description && (
                  <div className="text-muted-foreground text-xs">{scheduler.description}</div>
                )}
              </TableCell>
              <TableCell className="font-mono text-xs">{scheduler.scheduleText}</TableCell>
              <TableCell>
                <Badge variant="secondary" className="font-mono text-[10px]">
                  {scheduler.process}
                </Badge>
              </TableCell>
              <TableCell className="text-muted-foreground font-mono text-[11px]">
                {scheduler.category}
              </TableCell>
              <TableCell className="text-muted-foreground font-mono text-xs">
                {scheduler.nextRunAt ? formatTimestamp(scheduler.nextRunAt) : '—'}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

function FilterSelect({
  value,
  options,
  prefix,
  onValueChange,
}: {
  value: string;
  options: { value: string; label: string }[];
  prefix: string;
  onValueChange: (value: string) => void;
}) {
  const selected = options.find((option) => option.value === value);
  return (
    <Select value={value} onValueChange={(next) => onValueChange(String(next))}>
      <SelectTrigger size="sm" className="text-xs">
        <span className="text-muted-foreground">{prefix}:</span>
        <span className="font-medium text-ink">{selected?.label ?? value}</span>
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

function isStuck(row: JobRow): boolean {
  if (row.status !== 'PROCESSING') return false;
  const reference = row.updatedAt ?? row.createdAt;
  if (!reference) return false;
  return Date.now() - Date.parse(reference) > STUCK_AFTER_MS;
}

function relativeTime(iso: string | undefined): string {
  if (!iso) return '—';
  const deltaSeconds = Math.max(0, Math.floor((Date.now() - Date.parse(iso)) / 1000));
  if (deltaSeconds < 60) return `${deltaSeconds}s trước`;
  const minutes = Math.floor(deltaSeconds / 60);
  if (minutes < 60) return `${minutes}m trước`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h trước`;
  return `${Math.floor(hours / 24)}d trước`;
}

function formatTimestamp(iso: string | undefined | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('vi-VN', { hour12: false });
}

function formatDuration(seconds: number): string {
  if (!seconds || seconds <= 0) return '—';
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m`;
}

function formatRetryRate(histogram: { attemptsBucket: number; rowCount: number }[]): string {
  const total = histogram.reduce((sum, row) => sum + row.rowCount, 0);
  if (total === 0) return '0%';
  const retried = histogram
    .filter((row) => row.attemptsBucket >= 1)
    .reduce((sum, row) => sum + row.rowCount, 0);
  return `${((retried / total) * 100).toFixed(1)}%`;
}
