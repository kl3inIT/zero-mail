import { createFileRoute, Link } from '@tanstack/react-router';
import {
  CheckIcon,
  CreditCardIcon,
  PencilIcon,
  ReceiptTextIcon,
  SaveIcon,
} from 'lucide-react';
import { useMemo, useState } from 'react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import type {
  AdminBillingFeaturePermissionResponse,
  AdminBillingPackageResponse,
  AdminBillingPaymentResponse,
  AdminBillingPlanResponse,
  UpdateBillingFeatureCreditCostInput,
  UpdateBillingFeaturePermissionInput,
} from '@/features/billing-packages/billing-packages-api';
import {
  useBillingPackages,
  useUpdateBillingFeatureCreditCost,
  useUpdateBillingFeaturePermission,
} from '@/features/billing-packages/use-billing-packages';

export const Route = createFileRoute('/_authenticated/billing-packages')({
  component: BillingPackagesRoute,
});

const currencyFormatter = new Intl.NumberFormat();
const integerFormatter = new Intl.NumberFormat();

function BillingPackagesRoute() {
  const billingPackages = useBillingPackages();
  const updateBillingFeaturePermission = useUpdateBillingFeaturePermission();
  const updateBillingFeatureCreditCost = useUpdateBillingFeatureCreditCost();
  const data = billingPackages.data;
  const orderedPlans = useMemo(
    () => [...(data?.plans ?? [])].sort((firstPlan, secondPlan) => firstPlan.sortOrder - secondPlan.sortOrder),
    [data?.plans],
  );

  return (
    <div className="min-w-0 space-y-4">
      <header className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
        <div className="min-w-0">
          <h1 className="text-2xl font-semibold text-ink">Quản lý gói dịch vụ</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Quản lý gói dịch vụ, quyền hạn và giá credit cố định theo chức năng.
          </p>
        </div>
      </header>

      <Tabs defaultValue="plans" className="gap-4">
        <TabsList variant="line" aria-label="Billing package sections">
          <TabsTrigger value="plans">Gói dịch vụ</TabsTrigger>
          <TabsTrigger value="permissions">Quyền hạn & Credit</TabsTrigger>
          <TabsTrigger value="payments">Lịch sử thanh toán</TabsTrigger>
        </TabsList>

        <TabsContent value="plans">
          <PlansSection
            data={data}
            plans={orderedPlans}
            loading={billingPackages.isLoading}
          />
        </TabsContent>

        <TabsContent value="permissions">
          <PermissionsSection
            plans={orderedPlans}
            features={data?.featurePermissions ?? []}
            loading={billingPackages.isLoading}
            updatingPermission={
              updateBillingFeaturePermission.isPending
                ? updateBillingFeaturePermission.variables
                : undefined
            }
            updatingCreditCostFeatureCode={
              updateBillingFeatureCreditCost.isPending
                ? updateBillingFeatureCreditCost.variables?.featureCode
                : undefined
            }
            onTogglePermission={(input) => updateBillingFeaturePermission.mutate(input)}
            onSaveCreditCost={(input) => updateBillingFeatureCreditCost.mutate(input)}
          />
        </TabsContent>

        <TabsContent value="payments">
          <PaymentHistorySection
            payments={data?.paymentHistory ?? []}
            loading={billingPackages.isLoading}
          />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function PlansSection({
  data,
  plans,
  loading,
}: {
  data: AdminBillingPackageResponse | undefined;
  plans: AdminBillingPlanResponse[];
  loading: boolean;
}) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between gap-3">
        <div>
          <CardTitle role="heading" aria-level={2}>Danh sách gói dịch vụ</CardTitle>
          <p className="mt-1 text-sm text-muted-foreground">
            Giá và số credit lấy trực tiếp từ bảng billing plan hiện tại.
          </p>
        </div>
        {data?.snapshotAt && (
          <span className="text-xs text-muted-foreground">
            Cập nhật {formatDateTime(data.snapshotAt)}
          </span>
        )}
      </CardHeader>
      <CardContent>
        {loading ? (
          <div className="grid gap-3 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, index) => (
              <Skeleton key={index} className="h-64" />
            ))}
          </div>
        ) : (
          <div className="grid gap-3 lg:grid-cols-3">
            {plans.map((plan) => (
              <PlanCard key={plan.planId} plan={plan} />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function PlanCard({ plan }: { plan: AdminBillingPlanResponse }) {
  const bullets = planBullets(plan);
  return (
    <div className="flex min-h-64 flex-col rounded-lg border border-border bg-card">
      <div className="flex-1 space-y-4 p-4">
        <Badge variant="secondary" className="w-fit text-primary">
          {plan.code}
        </Badge>
        <div>
          <h2 className="text-lg font-semibold text-ink">{plan.displayName}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{planDescription(plan.code)}</p>
        </div>
        <div className="flex items-baseline gap-1">
          <span className="text-2xl font-semibold text-ink tabular-nums">
            {formatVnd(plan.priceVnd)}
          </span>
          <span className="text-sm text-muted-foreground">/ tháng</span>
        </div>
        <ul className="space-y-2 text-sm text-ink">
          {bullets.map((bullet) => (
            <li key={bullet} className="flex items-center gap-2">
              <CheckIcon className="size-4 text-primary" />
              <span>{bullet}</span>
            </li>
          ))}
        </ul>
      </div>
      <div className="flex items-center justify-between gap-3 border-t border-border p-4">
        <div className="space-y-1 text-xs text-muted-foreground">
          <div>Thứ tự: {plan.tierRank + 1}</div>
          <div>Chu kỳ: {formatBillingCycle(plan.billingCycle)}</div>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs font-medium text-green">{plan.active ? 'Đang hoạt động' : 'Đã tắt'}</span>
          <Switch checked={plan.active} disabled aria-label={`${plan.displayName} status`} />
          <Button type="button" size="sm" variant="outline" disabled>
            <PencilIcon className="size-3.5" />
            Chỉnh sửa
          </Button>
        </div>
      </div>
    </div>
  );
}

function PermissionsSection({
  plans,
  features,
  loading,
  updatingPermission,
  updatingCreditCostFeatureCode,
  onTogglePermission,
  onSaveCreditCost,
}: {
  plans: AdminBillingPlanResponse[];
  features: AdminBillingFeaturePermissionResponse[];
  loading: boolean;
  updatingPermission: UpdateBillingFeaturePermissionInput | undefined;
  updatingCreditCostFeatureCode: string | undefined;
  onTogglePermission: (input: UpdateBillingFeaturePermissionInput) => void;
  onSaveCreditCost: (input: UpdateBillingFeatureCreditCostInput) => void;
}) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between gap-3">
        <div>
          <CardTitle role="heading" aria-level={2}>Quyền hạn & Credit</CardTitle>
          <p className="mt-1 text-sm text-muted-foreground">
            Giá credit cố định theo chức năng. Mỗi gói chỉ bật hoặc tắt quyền sử dụng chức năng.
          </p>
        </div>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-12 text-right">#</TableHead>
              <TableHead>Chức năng</TableHead>
              <TableHead>Mô tả</TableHead>
              <TableHead className="text-right">Giá credit cố định</TableHead>
              {plans.map((plan) => (
                <TableHead key={plan.code} className="text-center">{plan.displayName}</TableHead>
              ))}
              <TableHead>Trạng thái</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading && (
              <TableRow>
                <TableCell colSpan={5 + plans.length} className="h-24 text-center text-muted-foreground">
                  Đang tải quyền hạn gói dịch vụ.
                </TableCell>
              </TableRow>
            )}
            {!loading && features.length === 0 && (
              <TableRow>
                <TableCell colSpan={5 + plans.length} className="h-24 text-center text-muted-foreground">
                  Chưa có chức năng credit nào.
                </TableCell>
              </TableRow>
            )}
            {features.map((feature, index) => (
              <TableRow key={feature.featureCode}>
                <TableCell className="text-right tabular-nums text-muted-foreground">
                  {index + 1}
                </TableCell>
                <TableCell>
                  <div className="font-medium text-ink">{feature.displayName}</div>
                  <div className="font-mono text-xs text-muted-foreground">{feature.featureCode}</div>
                </TableCell>
                <TableCell className="max-w-[360px] text-sm text-muted-foreground">
                  {feature.description ?? '-'}
                </TableCell>
                <TableCell className="text-right">
                  <FeatureCreditCostControl
                    key={`${feature.featureCode}-${feature.fixedCreditCost}`}
                    feature={feature}
                    disabled={loading || updatingCreditCostFeatureCode === feature.featureCode}
                    onSave={onSaveCreditCost}
                  />
                </TableCell>
                {plans.map((plan) => {
                  const enabled = isFeatureEnabledForPlan(feature, plan.code);
                  const updatingThisPermission = isUpdatingPermission(
                    updatingPermission,
                    feature.featureCode,
                    plan.code,
                  );
                  return (
                    <TableCell key={plan.code} className="text-center">
                      <div className="inline-flex items-center gap-2">
                        <Switch
                          checked={enabled}
                          disabled={loading || updatingThisPermission}
                          size="sm"
                          aria-label={`${feature.displayName} ${plan.displayName}`}
                          onCheckedChange={(value) =>
                            onTogglePermission({
                              featureCode: feature.featureCode,
                              planCode: plan.code,
                              enabled: value,
                            })
                          }
                        />
                        <span className={enabled ? 'text-green' : 'text-muted-foreground'}>
                          {enabled ? 'Bật' : 'Tắt'}
                        </span>
                      </div>
                    </TableCell>
                  );
                })}
                <TableCell>
                  <Badge variant="secondary" className="text-green">Đang bật</Badge>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function FeatureCreditCostControl({
  feature,
  disabled,
  onSave,
}: {
  feature: AdminBillingFeaturePermissionResponse;
  disabled: boolean;
  onSave: (input: UpdateBillingFeatureCreditCostInput) => void;
}) {
  const [draftCreditCost, setDraftCreditCost] = useState(String(feature.fixedCreditCost));

  const parsedCreditCost = Number(draftCreditCost);
  const canSave =
    draftCreditCost.trim() !== ''
    && Number.isInteger(parsedCreditCost)
    && parsedCreditCost >= 0
    && parsedCreditCost !== feature.fixedCreditCost;

  const saveCreditCost = () => {
    if (!canSave || disabled) return;
    onSave({
      featureCode: feature.featureCode,
      fixedCreditCost: parsedCreditCost,
    });
  };

  return (
    <div className="inline-flex min-w-[210px] items-center justify-end gap-2">
      <Input
        aria-label={`${feature.displayName} credit cost`}
        type="number"
        min={0}
        step={1}
        inputMode="numeric"
        value={draftCreditCost}
        disabled={disabled}
        onChange={(event) => setDraftCreditCost(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            saveCreditCost();
          }
        }}
        className="h-8 w-20 text-right tabular-nums"
      />
      <span className="whitespace-nowrap text-xs text-muted-foreground">{feature.unitLabel}</span>
      <Button
        type="button"
        size="icon"
        variant="outline"
        aria-label={`Lưu giá ${feature.displayName}`}
        disabled={disabled || !canSave}
        onClick={saveCreditCost}
      >
        <SaveIcon className="size-3.5" />
      </Button>
    </div>
  );
}

function PaymentHistorySection({
  payments,
  loading,
}: {
  payments: AdminBillingPaymentResponse[];
  loading: boolean;
}) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between gap-3">
        <div>
          <CardTitle role="heading" aria-level={2}>Lịch sử thanh toán</CardTitle>
          <p className="mt-1 text-sm text-muted-foreground">
            Danh sách giao dịch thanh toán và chuyển khoản gần nhất.
          </p>
        </div>
        <Button type="button" variant="outline" disabled>
          <ReceiptTextIcon className="size-4" />
          Xuất CSV
        </Button>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Khách hàng</TableHead>
              <TableHead>Gói</TableHead>
              <TableHead>Kỳ hạn</TableHead>
              <TableHead className="text-right">Số tiền</TableHead>
              <TableHead>Phương thức</TableHead>
              <TableHead>Mã giao dịch</TableHead>
              <TableHead>Trạng thái</TableHead>
              <TableHead>Ngày thanh toán</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading && (
              <TableRow>
                <TableCell colSpan={8} className="h-24 text-center text-muted-foreground">
                  Đang tải lịch sử thanh toán.
                </TableCell>
              </TableRow>
            )}
            {!loading && payments.length === 0 && (
              <TableRow>
                <TableCell colSpan={8} className="h-24 text-center text-muted-foreground">
                  Chưa có giao dịch thanh toán.
                </TableCell>
              </TableRow>
            )}
            {payments.map((payment) => (
              <TableRow key={payment.paymentId}>
                <TableCell>
                  <Link
                    to="/tenants/$tenantId"
                    params={{ tenantId: payment.tenantId }}
                    className="font-medium text-ink hover:text-primary hover:underline"
                  >
                    {payment.customerDisplayName}
                  </Link>
                  <div className="text-xs text-muted-foreground">{payment.customerEmail ?? '-'}</div>
                </TableCell>
                <TableCell>
                  <Badge variant="secondary">{payment.planCode}</Badge>
                </TableCell>
                <TableCell>{payment.periodLabel}</TableCell>
                <TableCell className="text-right tabular-nums">
                  {formatVnd(payment.amountVnd)}
                </TableCell>
                <TableCell>
                  <span className="inline-flex items-center gap-2">
                    <CreditCardIcon className="size-4 text-primary" />
                    {formatPaymentMethod(payment.paymentMethod)}
                  </span>
                </TableCell>
                <TableCell className="font-mono text-xs">{shortTransactionCode(payment.transactionCode)}</TableCell>
                <TableCell>
                  <PaymentStatusBadge status={payment.status} />
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {formatDateTime(payment.paidAt ?? payment.createdAt)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function PaymentStatusBadge({ status }: { status: AdminBillingPaymentResponse['status'] }) {
  if (status === 'PAID') {
    return <Badge variant="secondary" className="text-green">Thành công</Badge>;
  }
  if (status === 'PENDING') {
    return <Badge variant="secondary" className="text-amber">Chờ thanh toán</Badge>;
  }
  if (status === 'EXPIRED') {
    return <Badge variant="secondary">Hết hạn</Badge>;
  }
  return <Badge variant="destructive">Đã hủy</Badge>;
}

function isFeatureEnabledForPlan(
  feature: AdminBillingFeaturePermissionResponse,
  planCode: string,
): boolean {
  return feature.planPermissions.some(
    (planPermission) => planPermission.planCode === planCode && planPermission.enabled,
  );
}

function isUpdatingPermission(
  updatingPermission: UpdateBillingFeaturePermissionInput | undefined,
  featureCode: string,
  planCode: string,
): boolean {
  return (
    updatingPermission?.featureCode === featureCode
    && updatingPermission.planCode === planCode
  );
}

function planBullets(plan: AdminBillingPlanResponse): string[] {
  return [
    `${formatInteger(plan.monthlyCreditAllowance)} credit / tháng`,
    `Giá credit theo chức năng cố định`,
    `Gói ${plan.active ? 'đang hoạt động' : 'đã tắt'}`,
  ];
}

function planDescription(planCode: string): string {
  switch (planCode) {
    case 'FREE':
      return 'Dành cho cá nhân bắt đầu trải nghiệm Zero Mail.';
    case 'PLUS':
      return 'Dành cho cá nhân và nhóm nhỏ.';
    case 'PRO':
      return 'Dành cho doanh nghiệp và đội ngũ lớn.';
    default:
      return 'Gói dịch vụ Zero Mail.';
  }
}

function formatBillingCycle(value: AdminBillingPlanResponse['billingCycle']): string {
  return value === 'MONTH' ? '1 tháng' : 'Miễn phí';
}

function formatPaymentMethod(value: string): string {
  if (value === 'SEPAY_QR') return 'SEPAY QR';
  if (value === 'LEMON_SQUEEZY') return 'Lemon Squeezy';
  return value.replaceAll('_', ' ');
}

function formatVnd(value: number): string {
  return `${currencyFormatter.format(value)} đ`;
}

function formatInteger(value: number): string {
  return integerFormatter.format(value);
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function shortTransactionCode(value: string): string {
  if (value.length <= 14) return value;
  return `${value.slice(0, 8)}…${value.slice(-4)}`;
}
