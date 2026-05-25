import {createFileRoute} from '@tanstack/react-router';
import {
  PackageIcon,
  PencilIcon,
  PlusIcon,
  PowerIcon,
  RefreshCwIcon,
  SaveIcon,
} from 'lucide-react';
import {useMemo, useState} from 'react';

import {KpiCard} from '@/components/KpiCard';
import {Badge} from '@/components/ui/badge';
import {Button} from '@/components/ui/button';
import {Card, CardContent, CardHeader, CardTitle} from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';
import {Skeleton} from '@/components/ui/skeleton';
import {Switch} from '@/components/ui/switch';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {Textarea} from '@/components/ui/textarea';
import type {
  BillingPackageAdminCreateRequest,
  BillingPackageAdminResponse,
  BillingPackageAdminUpdateRequest,
} from '@/features/billing-packages/billing-packages-api';
import {useActivateBillingPackage} from '@/features/billing-packages/use-activate-billing-package';
import {useBillingPackages} from '@/features/billing-packages/use-billing-packages';
import {useCreateBillingPackage} from '@/features/billing-packages/use-create-billing-package';
import {useDeactivateBillingPackage} from '@/features/billing-packages/use-deactivate-billing-package';
import {useReorderBillingPackages} from '@/features/billing-packages/use-reorder-billing-packages';
import {useUpdateBillingPackage} from '@/features/billing-packages/use-update-billing-package';

export const Route = createFileRoute('/_authenticated/billing-packages')({
  component: BillingPackagesRoute,
});

type PackageFormMode = 'create' | 'edit';
type PackageFormState = Omit<BillingPackageAdminCreateRequest, 'includedFeatures'> & {
  active: boolean;
  creditAmount: number;
  description: string;
  displayOrder: number;
  featured: boolean;
  includedFeaturesText: string;
  priceVnd: number;
};

const PACKAGE_CODE_PATTERN = /^PKG_[A-Z0-9_]{1,60}$/;

function BillingPackagesRoute() {
  const billingPackages = useBillingPackages();
  const activateMutation = useActivateBillingPackage();
  const deactivateMutation = useDeactivateBillingPackage();
  const reorderMutation = useReorderBillingPackages();
  const [dialogState, setDialogState] = useState<{
    mode: PackageFormMode;
    billingPackage?: BillingPackageAdminResponse;
  } | null>(null);
  const [displayOrderOverrides, setDisplayOrderOverrides] = useState<Record<string, number>>({});

  const packages = useMemo(() => billingPackages.data?.packages ?? [], [billingPackages.data?.packages]);
  const metrics = useMemo(() => summarizePackages(packages), [packages]);

  const hasOrderChanges = packages.some(
    (billingPackage) =>
      (displayOrderOverrides[billingPackage.id] ?? billingPackage.displayOrder) !==
      billingPackage.displayOrder,
  );

  async function saveDisplayOrders() {
    await reorderMutation.mutateAsync({
      items: packages.map((billingPackage) => ({
        packageId: billingPackage.id,
        displayOrder: displayOrderOverrides[billingPackage.id] ?? billingPackage.displayOrder,
      })),
    });
    setDisplayOrderOverrides({});
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-muted-foreground font-mono text-[11px] tracking-wider uppercase">
            Billing
          </p>
          <h1 className="text-ink text-xl font-semibold">Gói thanh toán</h1>
          <p className="text-muted-foreground mt-1 max-w-2xl text-sm">
            Catalog gói nạp tiền hiển thị cho người dùng, kèm số lượt mua và doanh thu theo gói.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => void billingPackages.refetch()}
            disabled={billingPackages.isFetching}
          >
            <RefreshCwIcon className={billingPackages.isFetching ? 'size-4 animate-spin' : 'size-4'}/>
            Làm mới
          </Button>
          <Button
            type="button"
            onClick={() => setDialogState({mode: 'create'})}
            data-testid="billing-package-create-button"
          >
            <PlusIcon className="size-4"/>
            Tạo gói
          </Button>
        </div>
      </header>

      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {billingPackages.isLoading ? (
          Array.from({length: 4}).map((_, index) => <Skeleton key={index} className="h-24"/>)
        ) : (
          <>
            <KpiCard
              testId="billing-packages-total"
              label="Tổng gói"
              value={metrics.totalPackages.toLocaleString('vi-VN')}
              hint={`${metrics.activePackages.toLocaleString('vi-VN')} gói đang bật`}
            />
            <KpiCard
              testId="billing-packages-purchases"
              label="Lượt mua"
              value={metrics.purchaseCount.toLocaleString('vi-VN')}
              hint={`${metrics.pendingIntentCount.toLocaleString('vi-VN')} intent đang chờ`}
            />
            <KpiCard
              testId="billing-packages-revenue"
              label="Doanh thu"
              value={formatVnd(metrics.totalRevenueVnd)}
              hint="Tính từ intent đã thanh toán"
            />
            <KpiCard
              testId="billing-packages-top"
              label="Gói mua nhiều nhất"
              value={metrics.topPackage?.code ?? '—'}
              hint={
                metrics.topPackage
                  ? `${metrics.topPackage.purchaseCount.toLocaleString('vi-VN')} lượt mua`
                  : 'Chưa có dữ liệu'
              }
              tabular={false}
            />
          </>
        )}
      </section>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between gap-4">
          <CardTitle className="flex items-center gap-2">
            <PackageIcon className="size-4"/>
            Danh sách gói
          </CardTitle>
          <Button
            type="button"
            variant="outline"
            disabled={!hasOrderChanges || reorderMutation.isPending}
            onClick={() => void saveDisplayOrders()}
          >
            <SaveIcon className="size-4"/>
            Lưu thứ tự
          </Button>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-24">Thứ tự</TableHead>
                <TableHead>Code</TableHead>
                <TableHead>Tên gói</TableHead>
                <TableHead className="text-right">Giá</TableHead>
                <TableHead className="text-right">Credit</TableHead>
                <TableHead>Nổi bật</TableHead>
                <TableHead>Trạng thái</TableHead>
                <TableHead className="text-right">Lượt mua</TableHead>
                <TableHead className="text-right">Đang chờ</TableHead>
                <TableHead className="text-right">Doanh thu</TableHead>
                <TableHead>Mua gần nhất</TableHead>
                <TableHead className="text-right">Hành động</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {billingPackages.isLoading && (
                <TableRow>
                  <TableCell colSpan={12} className="h-24 text-center">
                    Đang tải danh sách gói.
                  </TableCell>
                </TableRow>
              )}
              {!billingPackages.isLoading && packages.length === 0 && (
                <TableRow>
                  <TableCell colSpan={12} className="text-muted-foreground h-24 text-center">
                    Chưa có gói nào.
                  </TableCell>
                </TableRow>
              )}
              {packages.map((billingPackage) => {
                const mutating =
                  activateMutation.isPending || deactivateMutation.isPending || reorderMutation.isPending;
                return (
                  <TableRow key={billingPackage.id}>
                    <TableCell>
                      <Input
                        aria-label={`Thứ tự ${billingPackage.code}`}
                        type="number"
                        min={0}
                        value={displayOrderOverrides[billingPackage.id] ?? billingPackage.displayOrder}
                        onChange={(event) =>
                          setDisplayOrderOverrides((current) => ({
                            ...current,
                            [billingPackage.id]: nonNegativeNumber(event.target.value),
                          }))
                        }
                      />
                    </TableCell>
                    <TableCell className="font-mono text-xs">{billingPackage.code}</TableCell>
                    <TableCell>
                      <div className="font-medium">{billingPackage.name}</div>
                      {billingPackage.description && (
                        <div className="text-muted-foreground mt-1 max-w-64 truncate text-xs">
                          {billingPackage.description}
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatVnd(billingPackage.priceVnd)}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {billingPackage.creditAmount.toLocaleString('vi-VN')}
                    </TableCell>
                    <TableCell>
                      {billingPackage.featured ? (
                        <Badge className="bg-primary/10 text-primary border-transparent">Nổi bật</Badge>
                      ) : (
                        <span className="text-muted-foreground text-xs">—</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <StatusBadge active={billingPackage.active}/>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {billingPackage.purchaseCount.toLocaleString('vi-VN')}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {billingPackage.pendingIntentCount.toLocaleString('vi-VN')}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatVnd(billingPackage.totalRevenueVnd)}
                    </TableCell>
                    <TableCell className="text-muted-foreground font-mono text-xs">
                      {formatDateTime(billingPackage.lastPurchasedAt)}
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-2">
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => setDialogState({mode: 'edit', billingPackage})}
                        >
                          <PencilIcon className="size-3.5"/>
                          Sửa
                        </Button>
                        <Button
                          type="button"
                          variant={billingPackage.active ? 'outline' : 'default'}
                          size="sm"
                          disabled={mutating}
                          onClick={() =>
                            billingPackage.active
                              ? deactivateMutation.mutate(billingPackage.id)
                              : activateMutation.mutate(billingPackage.id)
                          }
                        >
                          <PowerIcon className="size-3.5"/>
                          {billingPackage.active ? 'Tắt' : 'Bật'}
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <BillingPackageDialog
        key={dialogState ? `${dialogState.mode}-${dialogState.billingPackage?.id ?? 'new'}` : 'closed'}
        state={dialogState}
        onOpenChange={(open) => {
          if (!open) setDialogState(null);
        }}
      />
    </div>
  );
}

function BillingPackageDialog({
                                state,
                                onOpenChange,
                              }: {
  state: { mode: PackageFormMode; billingPackage?: BillingPackageAdminResponse } | null;
  onOpenChange: (open: boolean) => void;
}) {
  const createMutation = useCreateBillingPackage();
  const updateMutation = useUpdateBillingPackage();
  const open = state !== null;
  const editingPackage = state?.billingPackage;
  const [form, setForm] = useState<PackageFormState>(() => formFromPackage(editingPackage));

  const mode = state?.mode ?? 'create';
  const valid = isFormValid(form, mode);
  const submitting = createMutation.isPending || updateMutation.isPending;

  async function submit() {
    if (!valid) return;
    if (mode === 'create') {
      const request: BillingPackageAdminCreateRequest = {
        code: form.code.trim(),
        name: form.name.trim(),
        priceVnd: form.priceVnd,
        creditAmount: form.creditAmount,
        description: optionalText(form.description),
        includedFeatures: includedFeaturesFromText(form.includedFeaturesText),
        featured: form.featured,
        active: form.active,
        displayOrder: form.displayOrder,
      };
      await createMutation.mutateAsync(request);
      onOpenChange(false);
      return;
    }
    if (!editingPackage) return;
    const request: BillingPackageAdminUpdateRequest = {
      name: form.name.trim(),
      priceVnd: form.priceVnd,
      creditAmount: form.creditAmount,
      description: optionalText(form.description),
      includedFeatures: includedFeaturesFromText(form.includedFeaturesText),
      featured: form.featured,
      active: form.active,
      displayOrder: form.displayOrder,
    };
    await updateMutation.mutateAsync({packageId: editingPackage.id, request});
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {open && (
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>{mode === 'create' ? 'Tạo gói thanh toán' : 'Sửa gói thanh toán'}</DialogTitle>
            <DialogDescription>
              Code chỉ dùng để định danh gói và không đổi sau khi tạo.
            </DialogDescription>
          </DialogHeader>

          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault();
              void submit();
            }}
          >
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="billing-package-code">Code</Label>
                <Input
                  id="billing-package-code"
                  value={form.code}
                  maxLength={64}
                  pattern="^PKG_[A-Z0-9_]{1,60}$"
                  disabled={mode === 'edit'}
                  onChange={(event) =>
                    setForm((current) => ({...current, code: event.target.value.toUpperCase()}))
                  }
                  placeholder="PKG_STARTER"
                />
                <p className="text-muted-foreground text-xs">Định dạng: PKG_ + chữ hoa, số hoặc dấu gạch dưới.</p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="billing-package-name">Tên gói</Label>
                <Input
                  id="billing-package-name"
                  value={form.name}
                  maxLength={120}
                  onChange={(event) => setForm((current) => ({...current, name: event.target.value}))}
                  placeholder="Starter"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="billing-package-price">Giá VND</Label>
                <Input
                  id="billing-package-price"
                  type="number"
                  min={1}
                  value={form.priceVnd}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      priceVnd: positiveNumber(event.target.value),
                    }))
                  }
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="billing-package-credit">Credit nhận được</Label>
                <Input
                  id="billing-package-credit"
                  type="number"
                  min={1}
                  value={form.creditAmount}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      creditAmount: positiveNumber(event.target.value),
                    }))
                  }
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="billing-package-order">Thứ tự hiển thị</Label>
                <Input
                  id="billing-package-order"
                  type="number"
                  min={0}
                  value={form.displayOrder}
                  onChange={(event) =>
                    setForm((current) => ({
                      ...current,
                      displayOrder: nonNegativeNumber(event.target.value),
                    }))
                  }
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="billing-package-description">Mô tả</Label>
              <Textarea
                id="billing-package-description"
                value={form.description}
                maxLength={512}
                onChange={(event) =>
                  setForm((current) => ({...current, description: event.target.value}))
                }
                placeholder="Mô tả ngắn hiển thị trong luồng nạp tiền."
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="billing-package-included-features">Bao gồm</Label>
              <Textarea
                id="billing-package-included-features"
                value={form.includedFeaturesText}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    includedFeaturesText: event.target.value,
                  }))
                }
                placeholder="Mỗi dòng là một quyền lợi hiển thị trên card gói."
              />
              <p className="text-muted-foreground text-xs">Tối đa 8 dòng, mỗi dòng tối đa 120 ký tự.</p>
            </div>

            <div className="flex items-center justify-between rounded-md border p-3">
              <div>
                <Label htmlFor="billing-package-featured">Gói nổi bật</Label>
                <p className="text-muted-foreground mt-1 text-xs">
                  Đánh dấu để ưu tiên hiển thị trên trang nạp tiền.
                </p>
              </div>
              <Switch
                id="billing-package-featured"
                checked={form.featured}
                onCheckedChange={(value) => setForm((current) => ({
                  ...current,
                  featured: value
                }))}
              />
            </div>

            <div className="flex items-center justify-between rounded-md border p-3">
              <div>
                <Label htmlFor="billing-package-active">Đang hiển thị</Label>
                <p className="text-muted-foreground mt-1 text-xs">
                  Gói tắt sẽ không xuất hiện ở luồng nạp tiền của người dùng.
                </p>
              </div>
              <Switch
                id="billing-package-active"
                checked={form.active}
                onCheckedChange={(value) => setForm((current) => ({
                  ...current,
                  active: value
                }))}
              />
            </div>

            <DialogFooter>
              <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
                Hủy
              </Button>
              <Button type="submit" disabled={!valid || submitting}>
                {submitting ? 'Đang lưu…' : mode === 'create' ? 'Tạo gói' : 'Lưu thay đổi'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      )}
    </Dialog>
  );
}

function StatusBadge({active}: { active: boolean }) {
  return active ? (
    <Badge className="bg-green-soft text-green border-transparent">Đang bật</Badge>
  ) : (
    <Badge variant="secondary">Đã tắt</Badge>
  );
}

function summarizePackages(packages: BillingPackageAdminResponse[]) {
  const topPackage =
    packages.length === 0
      ? null
      : [...packages].sort((left, right) => right.purchaseCount - left.purchaseCount)[0];
  return {
    totalPackages: packages.length,
    activePackages: packages.filter((billingPackage) => billingPackage.active).length,
    purchaseCount: packages.reduce(
      (accumulator, billingPackage) => accumulator + billingPackage.purchaseCount,
      0,
    ),
    pendingIntentCount: packages.reduce(
      (accumulator, billingPackage) => accumulator + billingPackage.pendingIntentCount,
      0,
    ),
    totalRevenueVnd: packages.reduce(
      (accumulator, billingPackage) => accumulator + billingPackage.totalRevenueVnd,
      0,
    ),
    topPackage,
  };
}

function formFromPackage(billingPackage: BillingPackageAdminResponse | undefined): PackageFormState {
  return {
    code: billingPackage?.code ?? '',
    name: billingPackage?.name ?? '',
    priceVnd: billingPackage?.priceVnd ?? 10_000,
    creditAmount: billingPackage?.creditAmount ?? 10,
    description: billingPackage?.description ?? '',
    includedFeaturesText: (billingPackage?.includedFeatures ?? []).join('\n'),
    featured: billingPackage?.featured ?? false,
    active: billingPackage?.active ?? true,
    displayOrder: billingPackage?.displayOrder ?? 0,
  };
}

function isFormValid(form: PackageFormState, mode: PackageFormMode): boolean {
  return (
    (mode === 'edit' || PACKAGE_CODE_PATTERN.test(form.code.trim())) &&
    form.name.trim().length > 0 &&
    form.name.trim().length <= 120 &&
    form.description.length <= 512 &&
    hasValidIncludedFeatures(form.includedFeaturesText) &&
    form.priceVnd > 0 &&
    form.creditAmount > 0 &&
    form.displayOrder >= 0
  );
}

function includedFeaturesFromText(value: string): string[] {
  return includedFeatureLinesFromText(value);
}

function hasValidIncludedFeatures(value: string): boolean {
  const includedFeatureLines = includedFeatureLinesFromText(value);
  return (
    includedFeatureLines.length > 0 &&
    includedFeatureLines.length <= 8 &&
    includedFeatureLines.every((includedFeature) => includedFeature.length <= 120)
  );
}

function includedFeatureLinesFromText(value: string): string[] {
  return value
    .split('\n')
    .map((includedFeature) => includedFeature.trim())
    .filter((includedFeature) => includedFeature.length > 0);
}

function optionalText(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed.length === 0 ? undefined : trimmed;
}

function positiveNumber(value: string): number {
  const parsedValue = Number(value);
  return Number.isFinite(parsedValue) && parsedValue > 0 ? Math.floor(parsedValue) : 1;
}

function nonNegativeNumber(value: string): number {
  const parsedValue = Number(value);
  return Number.isFinite(parsedValue) && parsedValue >= 0 ? Math.floor(parsedValue) : 0;
}

function formatVnd(value: number): string {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value);
}

function formatDateTime(value: string | undefined): string {
  if (!value) return '—';
  return new Date(value).toLocaleString('vi-VN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}
