import { createFileRoute, useNavigate } from '@tanstack/react-router';
import {
  CheckCircle2Icon,
  ClockIcon,
  MailCheckIcon,
  MailXIcon,
  RefreshCcwIcon,
  XCircleIcon,
} from 'lucide-react';
import { useMemo, useState } from 'react';
import { z } from 'zod';

import { KpiCard } from '@/components/KpiCard';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { useApproveWaitlist } from '@/features/waitlist/use-approve-waitlist';
import { useRejectWaitlist } from '@/features/waitlist/use-reject-waitlist';
import { useWaitlistList } from '@/features/waitlist/use-waitlist-list';
import {
  WAITLIST_STATUSES,
  type WaitlistEntry,
  type WaitlistStatus,
} from '@/features/waitlist/waitlist-api';

const STATUS_FILTER_ALL = 'ALL' as const;

const waitlistSearchSchema = z.object({
  status: z
    .enum(['ALL', 'PENDING', 'APPROVED', 'REJECTED', 'INVITED', 'INVITE_FAILED'])
    .default('PENDING')
    .catch('PENDING'),
  page: z.coerce.number().int().min(0).default(0).catch(0),
  size: z.coerce.number().int().min(5).max(100).default(25).catch(25),
});

export const Route = createFileRoute('/_authenticated/waitlist')({
  validateSearch: waitlistSearchSchema,
  component: WaitlistRoute,
});

type DialogAction = { kind: 'approve' | 'reject'; entry: WaitlistEntry } | null;

function WaitlistRoute() {
  const search = Route.useSearch();
  const navigate = useNavigate();
  const [pendingDialog, setPendingDialog] = useState<DialogAction>(null);

  const statusFilter: WaitlistStatus | undefined =
    search.status === STATUS_FILTER_ALL ? undefined : search.status;

  const listQuery = useWaitlistList({
    status: statusFilter,
    page: search.page,
    size: search.size,
  });
  // Independent total-count query (no status filter) — drives KPI cards regardless of which
  // filter the admin is browsing.
  const overallQuery = useWaitlistList({ status: undefined, page: 0, size: 1 });
  const pendingCountQuery = useWaitlistList({ status: 'PENDING', page: 0, size: 1 });
  const invitedCountQuery = useWaitlistList({ status: 'INVITED', page: 0, size: 1 });
  const failedCountQuery = useWaitlistList({ status: 'INVITE_FAILED', page: 0, size: 1 });

  const approveMutation = useApproveWaitlist();
  const rejectMutation = useRejectWaitlist();

  const totalElements = listQuery.data?.totalElements ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / search.size));
  const currentPage = Math.min(search.page, Math.max(0, totalPages - 1));

  const items = useMemo(() => listQuery.data?.items ?? [], [listQuery.data]);

  function setStatus(nextStatus: typeof search.status) {
    void navigate({
      to: '/waitlist',
      search: { status: nextStatus, page: 0, size: search.size },
    });
  }

  function setPage(nextPage: number) {
    void navigate({
      to: '/waitlist',
      search: { status: search.status, page: nextPage, size: search.size },
    });
  }

  function setSize(nextSize: number) {
    void navigate({
      to: '/waitlist',
      search: { status: search.status, page: 0, size: nextSize },
    });
  }

  function closeDialog() {
    setPendingDialog(null);
  }

  async function confirmAction() {
    if (!pendingDialog) return;
    const { kind, entry } = pendingDialog;
    try {
      if (kind === 'approve') {
        await approveMutation.mutateAsync(entry.id);
      } else {
        await rejectMutation.mutateAsync(entry.id);
      }
      setPendingDialog(null);
    } catch {
      // Global error handler shows toast; keep dialog open so admin can retry.
    }
  }

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <p className="font-mono text-[11px] tracking-wider text-muted-foreground uppercase">
            Hàng đợi đăng ký
          </p>
          <h1 className="text-xl font-semibold text-ink">Danh sách email chờ duyệt</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Email người dùng gửi từ landing page. Duyệt sẽ kích hoạt worker gửi mail mời trong 1 phút.
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            void listQuery.refetch();
            void overallQuery.refetch();
            void pendingCountQuery.refetch();
            void invitedCountQuery.refetch();
            void failedCountQuery.refetch();
          }}
          disabled={listQuery.isFetching}
        >
          <RefreshCcwIcon className={listQuery.isFetching ? 'size-4 animate-spin' : 'size-4'} />
          Làm mới
        </Button>
      </header>

      <section className="grid gap-3 md:grid-cols-4">
        <KpiCard
          label="Tổng đăng ký"
          value={formatInteger(overallQuery.data?.totalElements)}
          hint="Mọi trạng thái"
        />
        <KpiCard
          label="Đang chờ duyệt"
          value={formatInteger(pendingCountQuery.data?.totalElements)}
          hint="PENDING"
        />
        <KpiCard
          label="Đã mời"
          value={formatInteger(invitedCountQuery.data?.totalElements)}
          hint="INVITED"
        />
        <KpiCard
          label="Lỗi gửi mời"
          value={formatInteger(failedCountQuery.data?.totalElements)}
          hint="INVITE_FAILED"
        />
      </section>

      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <CardTitle>Danh sách</CardTitle>
              <CardDescription>
                Hiển thị {items.length} / {formatInteger(totalElements)} mục
              </CardDescription>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-2">
                <span className="text-xs text-muted-foreground">Trạng thái</span>
                <Select value={search.status} onValueChange={(value) => setStatus(value as typeof search.status)}>
                  <SelectTrigger className="w-[180px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">Tất cả</SelectItem>
                    {WAITLIST_STATUSES.map((status) => (
                      <SelectItem key={status} value={status}>
                        {statusLabel(status)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-muted-foreground">Số dòng/trang</span>
                <Select value={String(search.size)} onValueChange={(value) => setSize(Number(value))}>
                  <SelectTrigger className="w-[90px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="10">10</SelectItem>
                    <SelectItem value="25">25</SelectItem>
                    <SelectItem value="50">50</SelectItem>
                    <SelectItem value="100">100</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
        </CardHeader>
        <CardContent className="px-0">
          {listQuery.isError ? (
            <div className="px-6 py-8 text-sm text-destructive">Không tải được danh sách.</div>
          ) : listQuery.isLoading ? (
            <div className="px-6 py-8 text-sm text-muted-foreground">Đang tải...</div>
          ) : items.length === 0 ? (
            <div className="px-6 py-8 text-sm text-muted-foreground">
              Không có mục nào trong trạng thái này.
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Email</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead>Nguồn</TableHead>
                  <TableHead>Đăng ký lúc</TableHead>
                  <TableHead>Cập nhật</TableHead>
                  <TableHead className="text-right">Thao tác</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((entry) => (
                  <TableRow key={entry.id}>
                    <TableCell className="font-mono text-xs break-all">{entry.email}</TableCell>
                    <TableCell>
                      <StatusBadge status={entry.status} />
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">
                      {entry.source ?? '-'}
                    </TableCell>
                    <TableCell className="font-mono text-xs">{formatDateTime(entry.createdAt)}</TableCell>
                    <TableCell className="font-mono text-xs">
                      {formatDateTime(entry.inviteSentAt ?? entry.approvedAt)}
                      {entry.inviteFailureReason && (
                        <div className="mt-1 text-[11px] text-destructive">
                          {entry.inviteFailureReason}
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="text-right">
                      {entry.status === 'PENDING' ? (
                        <div className="inline-flex gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={approveMutation.isPending || rejectMutation.isPending}
                            onClick={() => setPendingDialog({ kind: 'approve', entry })}
                          >
                            <CheckCircle2Icon className="size-4 text-green" />
                            Duyệt
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={approveMutation.isPending || rejectMutation.isPending}
                            onClick={() => setPendingDialog({ kind: 'reject', entry })}
                          >
                            <XCircleIcon className="size-4 text-destructive" />
                            Từ chối
                          </Button>
                        </div>
                      ) : (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
        {totalElements > 0 && (
          <div className="flex items-center justify-between border-t border-border px-6 py-3">
            <div className="text-xs text-muted-foreground">
              Trang {currentPage + 1} / {totalPages}
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={currentPage === 0}
                onClick={() => setPage(currentPage - 1)}
              >
                Trang trước
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={currentPage >= totalPages - 1}
                onClick={() => setPage(currentPage + 1)}
              >
                Trang sau
              </Button>
            </div>
          </div>
        )}
      </Card>

      <AlertDialog
        open={pendingDialog !== null}
        onOpenChange={(open) => {
          if (!open) closeDialog();
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {pendingDialog?.kind === 'approve' ? 'Duyệt đăng ký?' : 'Từ chối đăng ký?'}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {pendingDialog?.kind === 'approve' ? (
                <>
                  Hệ thống sẽ gửi mail mời tới <span className="font-mono">{pendingDialog.entry.email}</span> trong vòng 1 phút.
                  Thao tác này không thể hoàn tác.
                </>
              ) : pendingDialog ? (
                <>
                  Đăng ký của <span className="font-mono">{pendingDialog.entry.email}</span> sẽ bị từ chối. Email người dùng KHÔNG được thông báo.
                </>
              ) : null}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={closeDialog}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                void confirmAction();
              }}
              disabled={approveMutation.isPending || rejectMutation.isPending}
            >
              {approveMutation.isPending || rejectMutation.isPending
                ? 'Đang xử lý...'
                : pendingDialog?.kind === 'approve'
                  ? 'Duyệt'
                  : 'Từ chối'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function statusLabel(status: WaitlistStatus): string {
  switch (status) {
    case 'PENDING':
      return 'Chờ duyệt';
    case 'APPROVED':
      return 'Đã duyệt';
    case 'REJECTED':
      return 'Đã từ chối';
    case 'INVITED':
      return 'Đã gửi mời';
    case 'INVITE_FAILED':
      return 'Lỗi gửi';
  }
}

function StatusBadge({ status }: { status: WaitlistStatus }) {
  const config: Record<WaitlistStatus, { className: string; Icon: typeof ClockIcon }> = {
    PENDING: { className: 'bg-amber-soft text-amber border-amber/40', Icon: ClockIcon },
    APPROVED: { className: 'bg-violet-soft text-primary border-primary/40', Icon: CheckCircle2Icon },
    REJECTED: { className: 'bg-destructive-soft text-destructive border-destructive/40', Icon: XCircleIcon },
    INVITED: { className: 'bg-green-soft text-green border-green/40', Icon: MailCheckIcon },
    INVITE_FAILED: { className: 'bg-destructive-soft text-destructive border-destructive/40', Icon: MailXIcon },
  };
  const { className, Icon } = config[status];
  return (
    <Badge variant="outline" className={`gap-1 ${className}`}>
      <Icon className="size-3" />
      {statusLabel(status)}
    </Badge>
  );
}

function formatInteger(value: number | undefined): string {
  if (value === undefined) return '—';
  return new Intl.NumberFormat('vi-VN').format(value);
}

function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toLocaleString('vi-VN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}
