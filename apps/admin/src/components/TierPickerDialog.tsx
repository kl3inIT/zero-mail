import { ArrowDownIcon, ArrowUpIcon, XIcon } from 'lucide-react';
import { useMemo, useState } from 'react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectSeparator,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  type FeatureDefaultBinding,
  type RoutingFeature,
  type RoutingTier,
} from '@/features/feature-defaults/feature-defaults-api';
import { useAssignTier } from '@/features/feature-defaults/use-assign-tier';
import {
  providerLabel,
  type CatalogModel,
  type CatalogProvider,
} from '@/features/catalog/catalog-api';
import { useCatalog } from '@/features/catalog/use-catalog';
import type { MasterKeyRow } from '@/features/master-keys/master-keys-api';
import { useMasterKeys } from '@/features/master-keys/use-master-keys';

const FEATURE_LABELS: Record<RoutingFeature, string> = {
  CHAT: 'Chat trợ lý',
  TRIAGE: 'Chọn hành động AI',
  DRAFT: 'Soạn nội dung',
  RULE_AUTHORING: 'Tạo quy tắc',
  RULE_PREVIEW_SEMANTIC: 'Test quy tắc',
  TRIAGE_SEMANTIC: 'Chạy quy tắc',
  DRIFT_CHECK: 'Kiểm tra chất lượng',
};

const TIER_LABELS: Record<RoutingTier, string> = {
  PRIMARY: 'Chính',
  FALLBACK: 'Dự phòng',
  LAST_RESORT: 'Cuối cùng',
};

export type TierPickerState = {
  feature: RoutingFeature;
  tier: RoutingTier;
  current: FeatureDefaultBinding | null;
} | null;

export type TierPickerDialogProps = {
  state: TierPickerState;
  onClose: () => void;
};

/**
 * Modal that assigns one (provider, ordered models) pair to a (feature, tier) slot. Within the
 * tier, position 0 is tried first, then 1, etc. The router only escalates to the next tier after
 * exhausting this list.
 */
export function TierPickerDialog({ state, onClose }: TierPickerDialogProps) {
  const open = state !== null;
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      {state && (
        <PickerForm
          key={`${state.feature}:${state.tier}:${(state.current?.modelIds ?? []).join(',')}`}
          state={state}
          onClose={onClose}
        />
      )}
    </Dialog>
  );
}

function PickerForm({
  state,
  onClose,
}: {
  state: NonNullable<TierPickerState>;
  onClose: () => void;
}) {
  const assignMutation = useAssignTier();
  const masterKeysQuery = useMasterKeys();
  const [provider, setProvider] = useState<CatalogProvider>(
    (state.current?.provider as CatalogProvider) ?? '',
  );
  const [selectedModelIds, setSelectedModelIds] = useState<string[]>(
    state.current?.modelIds ?? [],
  );

  const providerRows = masterKeysQuery.data?.rows ?? [];
  const providerGroups = useMemo(
    () => groupProviderRows(masterKeysQuery.data?.rows ?? []),
    [masterKeysQuery.data?.rows],
  );
  const firstAvailableProvider =
    providerGroups.springAi[0]?.provider ??
    providerGroups.openAi[0]?.provider ??
    providerGroups.anthropic[0]?.provider ??
    providerGroups.other[0]?.provider ??
    '';
  const effectiveProvider = provider || firstAvailableProvider;
  const catalogQuery = useCatalog(effectiveProvider);
  const availableModels = useMemo(() => {
    const featureMap = catalogQuery.data?.features;
    if (!featureMap) return [];
    const focusedBlock = featureMap[state.feature];
    const pool = focusedBlock && focusedBlock.models.length > 0
      ? focusedBlock.models
      : Object.values(featureMap).flatMap((block) => block.models);
    const seen = new Set<string>();
    return pool.filter((model) => {
      if (!isRoutableModel(model)) return false;
      if (seen.has(model.modelId)) return false;
      seen.add(model.modelId);
      return true;
    });
  }, [catalogQuery.data, state.feature]);

  const availableModelIds = new Set(availableModels.map((model) => model.modelId));
  const selectedModelsAreEligible = selectedModelIds.every((modelId) =>
    availableModelIds.has(modelId),
  );
  const unselectedModels = availableModels.filter(
    (model) => !selectedModelIds.includes(model.modelId),
  );

  const canSubmit =
    !assignMutation.isPending &&
    Boolean(effectiveProvider) &&
    selectedModelIds.length > 0 &&
    selectedModelsAreEligible;

  function addModel(modelId: string) {
    if (!modelId || selectedModelIds.includes(modelId)) return;
    setSelectedModelIds((prev) => [...prev, modelId]);
  }

  function removeModel(modelId: string) {
    setSelectedModelIds((prev) => prev.filter((entry) => entry !== modelId));
  }

  function moveModel(index: number, direction: -1 | 1) {
    const target = index + direction;
    if (target < 0 || target >= selectedModelIds.length) return;
    setSelectedModelIds((prev) => {
      const next = prev.slice();
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  function lookupDisplayName(modelId: string): string {
    return (
      availableModels.find((model) => model.modelId === modelId)?.displayName ?? modelId
    );
  }

  return (
    <DialogContent className="sm:max-w-2xl">
      <DialogHeader>
        <DialogTitle>{FEATURE_LABELS[state.feature]}</DialogTitle>
        <DialogDescription>
          {TIER_LABELS[state.tier]}. Model phía trên được thử trước.
        </DialogDescription>
      </DialogHeader>

      <div className="space-y-5">
        <div className="space-y-2">
          <Label htmlFor="tier-provider">Provider</Label>
          <Select
            value={effectiveProvider}
            onValueChange={(next) => {
              setProvider(next as CatalogProvider);
              setSelectedModelIds([]);
            }}
            disabled={masterKeysQuery.isPending || providerRows.length === 0}
          >
            <SelectTrigger id="tier-provider" className="h-11 w-full">
              <SelectValue placeholder="Chọn provider" />
            </SelectTrigger>
            <SelectContent>
              <ProviderSelectGroup label="Spring AI" rows={providerGroups.springAi}/>
              {providerGroups.springAi.length > 0 &&
                (providerGroups.openAi.length > 0 || providerGroups.anthropic.length > 0) && (
                  <SelectSeparator/>
                )}
              <ProviderSelectGroup
                label="OpenAI-compatible"
                rows={providerGroups.openAi}
              />
              {providerGroups.openAi.length > 0 && providerGroups.anthropic.length > 0 && (
                <SelectSeparator/>
              )}
              <ProviderSelectGroup
                label="Anthropic-compatible"
                rows={providerGroups.anthropic}
              />
              {providerGroups.other.length > 0 && (
                <>
                  <SelectSeparator/>
                  <ProviderSelectGroup label="Khác" rows={providerGroups.other}/>
                </>
              )}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label>Model ưu tiên</Label>
          {selectedModelIds.length === 0 ? (
            <div className="border-border text-muted-foreground rounded-md border border-dashed px-3 py-6 text-center text-sm">
              Chưa chọn model nào.
            </div>
          ) : (
            <ol className="space-y-2">
              {selectedModelIds.map((modelId, index) => (
                <li
                  key={modelId}
                  className="border-border bg-background flex items-center gap-2 rounded-md border p-2"
                >
                  <Badge variant="secondary" className="font-mono">
                    {index + 1}
                  </Badge>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-sm font-medium">
                      {lookupDisplayName(modelId)}
                    </div>
                    <div className="text-muted-foreground truncate font-mono text-xs">
                      {modelId}
                    </div>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label="Lên ưu tiên"
                    disabled={index === 0}
                    onClick={() => moveModel(index, -1)}
                  >
                    <ArrowUpIcon className="size-4" />
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label="Xuống ưu tiên"
                    disabled={index === selectedModelIds.length - 1}
                    onClick={() => moveModel(index, 1)}
                  >
                    <ArrowDownIcon className="size-4" />
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label="Bỏ model"
                    onClick={() => removeModel(modelId)}
                  >
                    <XIcon className="size-4" />
                  </Button>
                </li>
              ))}
            </ol>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="tier-add-model">Thêm model</Label>
          <Select
            value=""
            onValueChange={(next) => addModel(next ?? '')}
            disabled={catalogQuery.isPending || unselectedModels.length === 0}
          >
            <SelectTrigger id="tier-add-model" className="h-11 w-full">
              <SelectValue
                placeholder={
                  catalogQuery.isPending
                    ? 'Đang tải models…'
                    : unselectedModels.length === 0
                      ? 'Đã thêm hết models có sẵn'
                      : 'Chọn model'
                }
              />
            </SelectTrigger>
            <SelectContent>
              {unselectedModels.map((model) => (
                <SelectItem key={model.modelId} value={model.modelId}>
                  <div className="flex flex-col">
                    <span>{model.displayName}</span>
                    <span className="text-muted-foreground font-mono text-xs">
                      {model.modelId}
                    </span>
                  </div>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-muted-foreground text-xs">
            Chỉ model VERIFIED hoặc STALE được dùng khi chạy thật.
          </p>
          {!selectedModelsAreEligible && (
            <p className="text-destructive text-xs">
              Model đang chọn cần được test lại trước khi lưu.
            </p>
          )}
        </div>
      </div>

      <DialogFooter className="gap-2">
        <DialogClose render={(closeProps) => <Button variant="ghost" {...closeProps} />}>
          Hủy
        </DialogClose>
        <Button
          type="button"
          disabled={!canSubmit}
          onClick={() =>
            assignMutation.mutate(
              {
                feature: state.feature,
                tier: state.tier,
                provider: effectiveProvider as CatalogProvider,
                modelIds: selectedModelIds,
              },
              {
                onSuccess: () => onClose(),
              },
            )
          }
        >
          {assignMutation.isPending ? 'Đang lưu…' : 'Lưu'}
        </Button>
      </DialogFooter>
    </DialogContent>
  );
}

function isRoutableModel(model: CatalogModel): boolean {
  return (
    !model.deprecatedAt &&
    (model.verificationStatus === 'VERIFIED' || model.verificationStatus === 'STALE')
  );
}

function ProviderSelectGroup({label, rows}: { label: string; rows: MasterKeyRow[] }) {
  if (rows.length === 0) return null;
  return (
    <SelectGroup>
      <SelectLabel>{label}</SelectLabel>
      {rows.map((row) => (
        <SelectItem key={row.provider} value={row.provider}>
          {row.displayName || providerLabel(row.provider)}
        </SelectItem>
      ))}
    </SelectGroup>
  );
}

function groupProviderRows(rows: MasterKeyRow[]) {
  return {
    springAi: rows.filter((row) => row.providerKind === 'SPRING_AI_BUILT_IN'),
    openAi: rows.filter(
      (row) =>
        row.providerKind !== 'SPRING_AI_BUILT_IN' &&
        compatibleTypeFor(row) === 'OPENAI_FORMAT',
    ),
    anthropic: rows.filter(
      (row) =>
        row.providerKind !== 'SPRING_AI_BUILT_IN' &&
        compatibleTypeFor(row) === 'ANTHROPIC_FORMAT',
    ),
    other: rows.filter(
      (row) =>
        row.providerKind !== 'SPRING_AI_BUILT_IN' &&
        compatibleTypeFor(row) !== 'OPENAI_FORMAT' &&
        compatibleTypeFor(row) !== 'ANTHROPIC_FORMAT',
    ),
  };
}

function compatibleTypeFor(row: MasterKeyRow) {
  return row.compatibleType ?? row.keyFormat;
}
