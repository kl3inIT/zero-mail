import { createFileRoute, Link, useNavigate } from '@tanstack/react-router';
import {
  ActivityIcon,
  ArrowLeftIcon,
  BadgeDollarSignIcon,
  CreditCardIcon,
  MailXIcon,
  PauseIcon,
  ShieldCheckIcon,
  Trash2Icon,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { useMemo, useState } from 'react';
import { z } from 'zod';

import { ConfirmTwiceDialog } from '@/components/ConfirmTwiceDialog';
import { Button, buttonVariants } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import type { TenantDeletionPreviewResponse, TenantDetailTab } from '@/features/tenants/tenants-api';
import {
  useTenantActivity,
  useTenantBilling,
  useTenantDeletionPreview,
  useTenantHealth,
  useTenantOverview,
  useTenantSpend,
} from '@/features/tenants/use-tenant-detail';
import { useTenantDelete } from '@/features/tenants/use-tenant-delete';
import { useTenantDisconnect } from '@/features/tenants/use-tenant-disconnect';
import { useTenantPause } from '@/features/tenants/use-tenant-pause';

import { StatusBadge, formatDateTime } from './tenants';

const tenantDetailSearchSchema = z.object({
  tab: z.enum(['overview', 'health', 'billing', 'spend', 'activity']).default('overview').catch('overview'),
});

type TenantDialogAction = 'pause' | 'disconnect' | 'delete';

const integerFormatter = new Intl.NumberFormat();

export const Route = createFileRoute('/_authenticated/tenants/$tenantId')({
  validateSearch: tenantDetailSearchSchema,
  component: TenantDetailRoute,
});

function TenantDetailRoute() {
  const { tenantId } = Route.useParams();
  const { tab } = Route.useSearch();
  const navigate = useNavigate();
  const [dialogAction, setDialogAction] = useState<TenantDialogAction | null>(null);
  const overview = useTenantOverview(tenantId, { enabled: tab === 'overview' });
  const health = useTenantHealth(tenantId, { enabled: tab === 'health' });
  const billing = useTenantBilling(tenantId, { enabled: tab === 'billing' });
  const spend = useTenantSpend(tenantId, { enabled: tab === 'spend' });
  const activity = useTenantActivity(tenantId, { enabled: tab === 'activity' });
  const deletionPreview = useTenantDeletionPreview(tenantId, dialogAction === 'delete');
  const pauseTenant = useTenantPause();
  const disconnectTenant = useTenantDisconnect();
  const deleteTenant = useTenantDelete();
  const overviewData = overview.data;
  const targetEmail = overviewData?.gmailAccountEmail ?? '';
  const dialogConfig = useMemo(
    () =>
      dialogAction
        ? buildDialogConfig({
            action: dialogAction,
            email: targetEmail,
            deletionPreview: deletionPreview.data,
            deletionPreviewLoading: deletionPreview.isLoading,
          })
        : null,
    [dialogAction, targetEmail, deletionPreview.data, deletionPreview.isLoading],
  );

  function changeTab(nextTab: string) {
    void navigate({
      to: '/tenants/$tenantId',
      params: { tenantId },
      search: { tab: nextTab as TenantDetailTab },
    });
  }

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <Link to="/tenants" className={buttonVariants({ variant: 'ghost', size: 'sm', className: 'mb-2 px-0' })}>
            <ArrowLeftIcon className="size-4" />
            Khách hàng
          </Link>
          <h1 className="break-all text-xl font-semibold text-ink">{targetEmail || tenantId}</h1>
        </div>
        {overviewData && <StatusBadge status={overviewData.status} />}
      </header>

      <Tabs value={tab} onValueChange={changeTab}>
        <TabsList variant="line" className="w-full justify-start">
          <TabsTrigger value="overview">Tổng quan</TabsTrigger>
          <TabsTrigger value="health">Tình trạng</TabsTrigger>
          <TabsTrigger value="billing">Thanh toán</TabsTrigger>
          <TabsTrigger value="spend">Chi phí</TabsTrigger>
          <TabsTrigger value="activity">Hoạt động</TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <PanelState isLoading={overview.isLoading} isError={overview.isError}>
            {overviewData && (
              <Card>
                <CardHeader>
                  <CardTitle>Tổng quan</CardTitle>
                  <CardDescription>Ảnh chụp khách hàng chỉ chứa siêu dữ liệu.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-6">
                  <dl className="grid gap-4 md:grid-cols-2">
                    <Fact label="Mã khách hàng" value={overviewData.tenantId} mono />
                    <Fact label="Ngày tạo" value={formatDateTime(overviewData.createdAt)} mono />
                    <Fact label="Tài khoản Gmail" value={overviewData.gmailAccountEmail ?? '-'} />
                    <Fact label="Hoạt động gần nhất" value={formatDateTime(overviewData.lastActivityAt)} mono />
                    <Fact label="Số quy tắc" value={String(overviewData.rulesCount)} />
                  </dl>
                  <div className="flex flex-wrap gap-2 border-t border-border pt-4">
                    <Button variant="destructive" onClick={() => setDialogAction('pause')}>
                      <PauseIcon className="size-4" />
                      Tạm dừng
                    </Button>
                    <Button
                      variant="destructive"
                      disabled={!overviewData.gmailAccountEmail}
                      onClick={() => setDialogAction('disconnect')}
                    >
                      <MailXIcon className="size-4" />
                      Ngắt kết nối Gmail
                    </Button>
                    <Button
                      variant="destructive"
                      disabled={!overviewData.gmailAccountEmail}
                      onClick={() => setDialogAction('delete')}
                    >
                      <Trash2Icon className="size-4" />
                      Xóa khách hàng
                    </Button>
                  </div>
                </CardContent>
              </Card>
            )}
          </PanelState>
        </TabsContent>

        <TabsContent value="health">
          <PanelState isLoading={health.isLoading} isError={health.isError}>
            {health.data && (
              <Card>
                <CardHeader>
                  <CardTitle className="inline-flex items-center gap-2">
                    <ShieldCheckIcon className="size-4 text-muted-foreground" />
                    Tình trạng
                  </CardTitle>
                  <CardDescription>Kết nối Gmail và siêu dữ liệu push.</CardDescription>
                </CardHeader>
                <CardContent>
                  <dl className="grid gap-4 md:grid-cols-2">
                    <Fact label="Tình trạng refresh token" value={health.data.tokenRefreshStatus} />
                    <Fact label="Refresh gần nhất" value={formatDateTime(health.data.lastTokenRefreshAt)} mono />
                    <Fact label="Tình trạng watch" value={health.data.watchStatus} />
                    <Fact label="Pub/Sub push gần nhất" value={formatDateTime(health.data.lastPubSubPushAt)} mono />
                    <Fact label="Tồn đọng Pub/Sub" value={String(health.data.pubsubBacklogCount)} />
                  </dl>
                </CardContent>
              </Card>
            )}
          </PanelState>
        </TabsContent>

        <TabsContent value="billing">
          <PanelState isLoading={billing.isLoading} isError={billing.isError}>
            {billing.data && (
              <Card>
                <CardHeader>
                  <CardTitle className="inline-flex items-center gap-2">
                    <CreditCardIcon className="size-4 text-muted-foreground" />
                    Thanh toán
                  </CardTitle>
                  <CardDescription>Siêu dữ liệu số dư tín dụng.</CardDescription>
                </CardHeader>
                <CardContent>
                  <dl className="grid gap-4 md:grid-cols-2">
                    <Fact label="Số dư tín dụng" value={formatInteger(billing.data.creditsBalance)} />
                    <Fact label="Gói cước" value={billing.data.plan} />
                  </dl>
                </CardContent>
              </Card>
            )}
          </PanelState>
        </TabsContent>

        <TabsContent value="spend">
          <PanelState isLoading={spend.isLoading} isError={spend.isError}>
            {spend.data && (
              <Card>
                <CardHeader>
                  <CardTitle className="inline-flex items-center gap-2">
                    <BadgeDollarSignIcon className="size-4 text-muted-foreground" />
                    Chi phí
                  </CardTitle>
                  <CardDescription>Số lượt gọi đã nhóm, không phải chi phí chính xác từng khách hàng.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-5">
                  <dl className="grid gap-4 md:grid-cols-2">
                    <Fact label="Lượt gọi 7 ngày" value={formatInteger(spend.data.last7dCallCount)} />
                    <Fact label="Lượt gọi 30 ngày" value={formatInteger(spend.data.last30dCallCount)} />
                    <Fact label="Nhóm 7 ngày" value={spend.data.spendBucket7d} />
                    <Fact label="Nhóm 30 ngày" value={spend.data.spendBucket30d} />
                  </dl>
                  <div className="space-y-2">
                    <h2 className="text-sm font-semibold">Lượt gọi theo tính năng</h2>
                    <div className="grid gap-2 md:grid-cols-3">
                      {Object.entries(spend.data.perFeatureCallCount).map(([feature, count]) => (
                        <div key={feature} className="rounded-md border border-border px-3 py-2">
                          <div className="text-xs text-muted-foreground">{feature}</div>
                          <div className="font-mono text-sm">{formatInteger(count)}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                </CardContent>
              </Card>
            )}
          </PanelState>
        </TabsContent>

        <TabsContent value="activity">
          <PanelState isLoading={activity.isLoading} isError={activity.isError}>
            {activity.data && (
              <Card>
                <CardHeader>
                  <CardTitle className="inline-flex items-center gap-2">
                    <ActivityIcon className="size-4 text-muted-foreground" />
                    Hoạt động
                  </CardTitle>
                  <CardDescription>Chỉ siêu dữ liệu phiên làm việc.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-5">
                  <dl className="grid gap-4 md:grid-cols-2">
                    <Fact label="Số lần fire quy tắc 30 ngày" value={formatInteger(activity.data.last30dRuleFireCount)} />
                    <Fact label="Phiên chat" value={formatInteger(activity.data.chatSessionCount)} />
                    <Fact label="Phiên chat gần nhất" value={formatDateTime(activity.data.lastChatSessionAt)} mono />
                    <Fact label="Mô hình chọn gần nhất" value={activity.data.lastChatModelSelection ?? '-'} mono />
                  </dl>
                  {activity.data.chatSessionCount === 0 && (
                    <div className="rounded-md border border-border bg-secondary px-3 py-2 text-sm">
                      <div className="font-medium">Không có hoạt động trong 30 ngày qua</div>
                      <div className="text-muted-foreground">
                        Khách hàng này chưa sử dụng Zero Mail gần đây. Đăng ký push inbox vẫn hợp lệ.
                      </div>
                    </div>
                  )}
                  <TooltipProvider>
                    <Tooltip>
                      <TooltipTrigger render={<span className="inline-flex w-fit" />}>
                        <Button disabled>Xem chi tiết</Button>
                      </TooltipTrigger>
                      <TooltipContent>
                        Kiểm tra chi tiết phiên được hoãn sang v1.3+ qua phiếu hỗ trợ gắn với khách hàng.
                      </TooltipContent>
                    </Tooltip>
                  </TooltipProvider>
                </CardContent>
              </Card>
            )}
          </PanelState>
        </TabsContent>
      </Tabs>

      {dialogConfig && (
        <ConfirmTwiceDialog
          open={dialogAction !== null}
          onOpenChange={(open) => {
            if (!open) {
              setDialogAction(null);
            }
          }}
          actionLabel={dialogConfig.actionLabel}
          targetLabel={dialogConfig.targetLabel}
          consequences={dialogConfig.consequences}
          confirmationToken={dialogConfig.confirmationToken}
          finalButtonLabel={dialogConfig.finalButtonLabel}
          onConfirm={async (reason) => {
            if (dialogAction === 'pause') {
              await pauseTenant.mutateAsync({ tenantId, reason });
            } else if (dialogAction === 'disconnect') {
              await disconnectTenant.mutateAsync({ tenantId, reason });
            } else if (dialogAction === 'delete') {
              await deleteTenant.mutateAsync({ tenantId, reason, confirmEmail: targetEmail });
            }
            // WR-10: backend returns 204 No Content; no fabricated audit id.
            return {};
          }}
        />
      )}
    </div>
  );
}

function PanelState({
  isLoading,
  isError,
  children,
}: {
  isLoading: boolean;
  isError: boolean;
  children: ReactNode;
}) {
  if (isLoading) {
    return (
      <Card>
        <CardContent className="py-8 text-sm text-muted-foreground">Đang tải dữ liệu khách hàng.</CardContent>
      </Card>
    );
  }
  if (isError) {
    return (
      <Card>
        <CardContent className="py-8 text-sm text-destructive">Không tải được dữ liệu khách hàng.</CardContent>
      </Card>
    );
  }
  return <>{children}</>;
}

function Fact({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="space-y-1">
      <dt className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">{label}</dt>
      <dd className={mono ? 'break-all font-mono text-sm text-ink' : 'break-words text-sm text-ink'}>{value}</dd>
    </div>
  );
}

function buildDialogConfig({
  action,
  email,
  deletionPreview,
  deletionPreviewLoading,
}: {
  action: TenantDialogAction;
  email: string;
  deletionPreview?: TenantDeletionPreviewResponse;
  deletionPreviewLoading: boolean;
}) {
  if (action === 'pause') {
    return {
      actionLabel: 'Tạm dừng khách hàng',
      targetLabel: email || 'khách hàng',
      confirmationToken: 'pause',
      finalButtonLabel: 'Tạm dừng khách hàng',
      consequences: [
        'Phân loại tự động và kích hoạt quy tắc sẽ dừng cho khách hàng này.',
        'Siêu dữ liệu và lịch sử audit vẫn được giữ lại.',
        'Lý do sẽ được ghi vào nhật ký audit của quản trị viên.',
      ],
    };
  }
  if (action === 'disconnect') {
    return {
      actionLabel: 'Ngắt kết nối Gmail',
      targetLabel: email,
      confirmationToken: email,
      finalButtonLabel: 'Ngắt kết nối Gmail',
      consequences: [
        'Hệ thống sẽ xếp lịch thu hồi OAuth Gmail mà không hiển thị byte token cho mã admin.',
        'Các lượt push Gmail trong tương lai cho khách hàng này sẽ dừng sau khi thu hồi thành công.',
        'Lý do sẽ được ghi vào nhật ký audit của quản trị viên.',
      ],
    };
  }
  return {
    actionLabel: 'Xóa khách hàng',
    targetLabel: email,
    confirmationToken: email,
    finalButtonLabel: 'Xóa khách hàng',
    consequences: deletionConsequences(deletionPreview, deletionPreviewLoading),
  };
}

function deletionConsequences(preview?: TenantDeletionPreviewResponse, loading = false): string[] {
  if (loading) {
    return ['Đang tải bản xem trước xóa.', 'Hành động này không thể hoàn tác sau khi xác nhận cuối cùng.'];
  }
  if (!preview) {
    return ['Không lấy được bản xem trước xóa.', 'Hành động này không thể hoàn tác sau khi xác nhận cuối cùng.'];
  }
  return [
    `${preview.gmailConnections} bản ghi kết nối Gmail sẽ bị xóa.`,
    `${preview.chatSessions} phiên chat và ${preview.chatMessages} tin nhắn chat sẽ bị xóa.`,
    `${preview.rules} quy tắc và ${preview.triageAudits} bản ghi audit phân loại sẽ bị xóa.`,
    `${preview.byokCredentials} thông tin xác thực BYOK sẽ bị xóa.`,
    'Bản ghi khách hàng được xóa sau khi mục audit được ghi nhận.',
  ];
}

function formatInteger(value: number): string {
  return integerFormatter.format(value);
}
