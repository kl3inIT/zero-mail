import { createFileRoute, useNavigate } from '@tanstack/react-router';
import {
  BookOpenIcon,
  CheckCircle2Icon,
  CloudDownloadIcon,
  PlusIcon,
  RefreshCwIcon,
  SlidersHorizontalIcon,
  StarIcon,
} from 'lucide-react';
import { useMemo, useState } from 'react';
import { z } from 'zod';

import { ConfirmTwiceDialog } from '@/components/ConfirmTwiceDialog';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import {
  catalogFeatures,
  catalogProviders,
  featureLabel,
  providerLabel,
  type CatalogFeature,
  type CatalogModel,
  type CatalogProvider,
} from '@/features/catalog/catalog-api';
import { useCatalog } from '@/features/catalog/use-catalog';
import { useCreateModel } from '@/features/catalog/use-create-model';
import { useDisableModel } from '@/features/catalog/use-disable-model';
import { useSetDefaultModel } from '@/features/catalog/use-set-default-model';
import { useSyncFetch } from '@/features/catalog/use-sync-fetch';

const catalogSearchSchema = z.object({
  provider: z
    .enum(['OPENAI', 'ANTHROPIC', 'GOOGLE', 'DEEPSEEK', 'OPENROUTER', 'ROUTER_9R'])
    .default('ANTHROPIC')
    .catch('ANTHROPIC'),
  feature: z.enum(['CHAT', 'TRIAGE', 'DRAFT']).default('CHAT').catch('CHAT'),
  syncOk: z.string().optional().catch(undefined),
});

type ManualFormState = {
  modelId: string;
  displayName: string;
  costPer1kInput: string;
  costPer1kOutput: string;
  recommended: boolean;
};

const emptyManualForm: ManualFormState = {
  modelId: '',
  displayName: '',
  costPer1kInput: '',
  costPer1kOutput: '',
  recommended: false,
};

export const Route = createFileRoute('/_authenticated/catalog')({
  validateSearch: catalogSearchSchema,
  component: CatalogRoute,
});

function CatalogRoute() {
  const navigate = useNavigate();
  const search = Route.useSearch();
  const selectedProvider = search.provider as CatalogProvider;
  const selectedFeature = search.feature as CatalogFeature;
  const catalog = useCatalog(selectedProvider);
  const syncFetch = useSyncFetch();
  const createModel = useCreateModel();
  const disableModel = useDisableModel();
  const setDefault = useSetDefaultModel();
  const [manualForm, setManualForm] = useState<ManualFormState>(emptyManualForm);
  const [modelPendingDisable, setModelPendingDisable] = useState<CatalogModel | null>(null);
  const featureCatalog = catalog.data?.features?.[selectedFeature];
  const models = useMemo(() => featureCatalog?.models ?? [], [featureCatalog?.models]);
  const selectedModelCount = models.length;

  function changeProvider(provider: string) {
    void navigate({
      to: '/catalog',
      search: {
        provider: provider as CatalogProvider,
        feature: selectedFeature,
      },
    });
  }

  function changeFeature(feature: string) {
    void navigate({
      to: '/catalog',
      search: {
        provider: selectedProvider,
        feature: feature as CatalogFeature,
      },
    });
  }

  async function startSync() {
    if (selectedProvider === 'ANTHROPIC') {
      // Anthropic has no list-models endpoint; the Sync button is disabled for
      // this provider so this branch should be unreachable.
      return;
    }
    const result = await syncFetch.mutateAsync(selectedProvider);
    await navigate({
      to: '/catalog-sync/$jobId',
      params: { jobId: result.jobId },
      search: { provider: selectedProvider },
    });
  }

  async function submitManualModel() {
    await createModel.mutateAsync({
      provider: selectedProvider,
      modelId: manualForm.modelId.trim(),
      displayName: manualForm.displayName.trim(),
      costPer1kInput: optionalNumber(manualForm.costPer1kInput),
      costPer1kOutput: optionalNumber(manualForm.costPer1kOutput),
      recommended: manualForm.recommended,
    });
    setManualForm(emptyManualForm);
  }

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <p className="text-muted-foreground font-mono text-[11px] tracking-wider uppercase">
            Vận hành LLM
          </p>
          <h1 className="text-ink text-xl font-semibold">Danh mục mô hình</h1>
        </div>
        <div className="flex items-center gap-2">
          {search.syncOk && (
            <Badge className="bg-green-soft text-green hover:bg-green-soft">Đồng bộ OK</Badge>
          )}
          <Badge variant="secondary">
            {catalog.isLoading ? 'Đang tải' : `${selectedModelCount} mô hình`}
          </Badge>
        </div>
      </header>

      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-4">
          <div>
            <CardTitle className="inline-flex items-center gap-2">
              <BookOpenIcon className="text-muted-foreground size-4" />
              Danh mục theo nhà cung cấp
            </CardTitle>
            <CardDescription>
              Danh sách mô hình đã chọn lọc cho chat, phân loại và soạn nháp.
            </CardDescription>
          </div>
          <SyncAction
            provider={selectedProvider}
            isPending={syncFetch.isPending}
            onStart={() => void startSync()}
          />
        </CardHeader>
        <CardContent className="space-y-5">
          <Tabs value={selectedProvider} onValueChange={changeProvider}>
            <TabsList variant="line" className="w-full justify-start">
              {catalogProviders.map((provider) => (
                <TabsTrigger key={provider} value={provider}>
                  {providerLabel(provider)}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>

          <Tabs value={selectedFeature} onValueChange={changeFeature}>
            <TabsList className="w-fit">
              {catalogFeatures.map((feature) => (
                <TabsTrigger key={feature} value={feature}>
                  {featureLabel(feature)}
                </TabsTrigger>
              ))}
            </TabsList>
            {catalogFeatures.map((feature) => (
              <TabsContent key={feature} value={feature} className="mt-4">
                <CatalogTable
                  isLoading={catalog.isLoading}
                  isError={catalog.isError}
                  provider={selectedProvider}
                  feature={feature}
                  models={feature === selectedFeature ? models : []}
                  onSetDefault={(model) =>
                    setDefault.mutate({
                      provider: selectedProvider,
                      feature,
                      modelId: model.modelId,
                      reason: `Đặt mô hình mặc định cho ${featureLabel(feature)}`,
                    })
                  }
                  onDisable={setModelPendingDisable}
                />
              </TabsContent>
            ))}
          </Tabs>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="inline-flex items-center gap-2">
            <PlusIcon className="text-muted-foreground size-4" />
            Thêm mô hình thủ công
          </CardTitle>
          <CardDescription>
            {selectedProvider === 'ANTHROPIC'
              ? 'Đồng bộ Anthropic bị vô hiệu; nhập thủ công là luồng chính để cập nhật danh mục.'
              : 'Nhập thủ công khả dụng cho các trường hợp đặc biệt và alias định tuyến riêng.'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="grid gap-4 lg:grid-cols-[1fr_220px_150px_150px_auto]"
            onSubmit={(event) => {
              event.preventDefault();
              void submitManualModel();
            }}
          >
            <div className="space-y-2">
              <Label htmlFor="catalog-model-id">Mã mô hình</Label>
              <Input
                id="catalog-model-id"
                className="font-mono"
                value={manualForm.modelId}
                onChange={(event) =>
                  setManualForm((current) => ({ ...current, modelId: event.target.value }))
                }
                placeholder={`${selectedProvider.toLowerCase()}/model-id`}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="catalog-display-name">Tên hiển thị</Label>
              <Input
                id="catalog-display-name"
                value={manualForm.displayName}
                onChange={(event) =>
                  setManualForm((current) => ({ ...current, displayName: event.target.value }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="catalog-cost-in">Đầu vào / 1K</Label>
              <Input
                id="catalog-cost-in"
                inputMode="decimal"
                value={manualForm.costPer1kInput}
                onChange={(event) =>
                  setManualForm((current) => ({ ...current, costPer1kInput: event.target.value }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="catalog-cost-out">Đầu ra / 1K</Label>
              <Input
                id="catalog-cost-out"
                inputMode="decimal"
                value={manualForm.costPer1kOutput}
                onChange={(event) =>
                  setManualForm((current) => ({ ...current, costPer1kOutput: event.target.value }))
                }
              />
            </div>
            <div className="flex items-end gap-3">
              <Label className="flex h-8 items-center gap-2 whitespace-nowrap">
                <Checkbox
                  checked={manualForm.recommended}
                  onCheckedChange={(value) =>
                    setManualForm((current) => ({ ...current, recommended: value === true }))
                  }
                />
                Khuyến nghị
              </Label>
              <Button
                type="submit"
                disabled={
                  !manualForm.modelId.trim() ||
                  !manualForm.displayName.trim() ||
                  createModel.isPending
                }
              >
                <PlusIcon className="size-4" />
                Thêm
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {modelPendingDisable && (
        <ConfirmTwiceDialog
          open={Boolean(modelPendingDisable)}
          onOpenChange={(open) => {
            if (!open) {
              setModelPendingDisable(null);
            }
          }}
          actionLabel="Vô hiệu mô hình"
          targetLabel={modelPendingDisable.modelId}
          confirmationToken={modelPendingDisable.modelId}
          finalButtonLabel={
            modelPendingDisable.pinnedTenantCount > 0
              ? `Vô hiệu mô hình đang được ${modelPendingDisable.pinnedTenantCount} khách hàng dùng`
              : 'Vô hiệu mô hình'
          }
          consequences={disableConsequences(modelPendingDisable)}
          onConfirm={async (reason) => {
            await disableModel.mutateAsync({
              modelId: modelPendingDisable.modelId,
              reason,
              confirmedPinned: modelPendingDisable.pinnedTenantCount > 0,
              pinnedCountAcknowledged: modelPendingDisable.pinnedTenantCount,
            });
            // WR-10: backend returns 204 No Content; no fabricated audit id.
            return {};
          }}
        />
      )}
    </div>
  );
}

function SyncAction({
  provider,
  isPending,
  onStart,
}: {
  provider: CatalogProvider;
  isPending: boolean;
  onStart: () => void;
}) {
  if (provider === 'ANTHROPIC') {
    return (
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger render={<span className="inline-flex" />}>
            <Button disabled>
              <CloudDownloadIcon className="size-4" />
              Đồng bộ từ /models
            </Button>
          </TooltipTrigger>
          <TooltipContent>
            Anthropic không cung cấp endpoint /models công khai — vui lòng thêm mô hình mới qua nhập thủ công.
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    );
  }
  return (
    <Button type="button" onClick={onStart} disabled={isPending}>
      {isPending ? (
        <RefreshCwIcon className="size-4 animate-spin" />
      ) : (
        <CloudDownloadIcon className="size-4" />
      )}
      Sync from /models
    </Button>
  );
}

function CatalogTable({
  isLoading,
  isError,
  provider,
  feature,
  models,
  onSetDefault,
  onDisable,
}: {
  isLoading: boolean;
  isError: boolean;
  provider: CatalogProvider;
  feature: CatalogFeature;
  models: CatalogModel[];
  onSetDefault: (model: CatalogModel) => void;
  onDisable: (model: CatalogModel) => void;
}) {
  if (isLoading) {
    return (
      <div className="border-border bg-secondary text-muted-foreground rounded-md border px-3 py-8 text-sm">
        Đang tải danh mục.
      </div>
    );
  }
  if (isError) {
    return (
      <div className="border-border bg-secondary text-destructive rounded-md border px-3 py-8 text-sm">
        Không tải được danh mục.
      </div>
    );
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Mô hình</TableHead>
          <TableHead>Tên hiển thị</TableHead>
          <TableHead>Nhãn</TableHead>
          <TableHead>Ghim</TableHead>
          <TableHead>Chi phí</TableHead>
          <TableHead className="text-right">Hành động</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {models.length === 0 && (
          <TableRow>
            <TableCell colSpan={6} className="text-muted-foreground h-24 text-center">
              Không có mô hình {featureLabel(feature).toLowerCase()} cho {providerLabel(provider)}.
            </TableCell>
          </TableRow>
        )}
        {models.map((model) => (
          <TableRow
            key={`${feature}:${model.modelId}`}
            className={model.deprecatedAt ? 'opacity-60' : undefined}
          >
            <TableCell className="max-w-[360px] font-mono text-xs">
              <span className="block truncate" title={model.modelId}>
                {model.modelId}
              </span>
            </TableCell>
            <TableCell>{model.displayName}</TableCell>
            <TableCell>
              <div className="flex flex-wrap gap-1">
                {model.defaultModel && (
                  <Badge className="bg-green-soft text-green hover:bg-green-soft">
                    <CheckCircle2Icon className="size-3" />
                    Mặc định
                  </Badge>
                )}
                {model.recommended && (
                  <Badge className="bg-amber-soft text-amber hover:bg-amber-soft">
                    <StarIcon className="size-3" />
                    Khuyến nghị
                  </Badge>
                )}
                {model.deprecatedAt && <Badge variant="secondary">Đã vô hiệu</Badge>}
              </div>
            </TableCell>
            <TableCell>
              <Badge variant="outline">{model.pinnedTenantCount}</Badge>
            </TableCell>
            <TableCell className="font-mono text-xs">
              {formatCost(model.costPer1kInput)} / {formatCost(model.costPer1kOutput)}
            </TableCell>
            <TableCell className="text-right">
              <div className="flex justify-end gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={model.defaultModel || Boolean(model.deprecatedAt)}
                  onClick={() => onSetDefault(model)}
                >
                  <SlidersHorizontalIcon className="size-3.5" />
                  Đặt mặc định
                </Button>
                <Button
                  type="button"
                  variant="destructive"
                  size="sm"
                  disabled={Boolean(model.deprecatedAt)}
                  onClick={() => onDisable(model)}
                >
                  Vô hiệu
                </Button>
              </div>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function optionalNumber(value: string): number | undefined {
  const trimmed = value.trim();
  if (!trimmed) return undefined;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function formatCost(value?: number): string {
  if (value == null) return '-';
  return value.toFixed(6);
}

function disableConsequences(model: CatalogModel): string[] {
  const consequences = [
    'Mô hình vẫn tồn tại trong danh mục để giữ ràng buộc khóa ngoại hiện có.',
    'Sau khi làm mới danh mục, các lượt chọn mới sẽ không thấy mô hình này.',
    'Lý do sẽ được ghi vào nhật ký audit của quản trị viên.',
  ];
  if (model.pinnedTenantCount > 0) {
    consequences.unshift(
      `${model.pinnedTenantCount} khách hàng đang dùng mô hình này; họ sẽ tiếp tục dùng cho đến khi chọn mô hình khác.`,
    );
  }
  return consequences;
}
