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
  SelectItem,
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
  catalogProviders,
  providerLabel,
  type CatalogProvider,
} from '@/features/catalog/catalog-api';
import { useCatalog } from '@/features/catalog/use-catalog';

const FEATURE_LABELS: Record<RoutingFeature, string> = {
  CHAT: 'Chat trợ lý',
  TRIAGE: 'Phân loại email',
  DRAFT: 'Soạn nháp',
};

const TIER_LABELS: Record<RoutingTier, string> = {
  PRIMARY: 'Tier 1 — Primary',
  FALLBACK: 'Tier 2 — Fallback',
  LAST_RESORT: 'Tier 3 — Last Resort',
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
  const [provider, setProvider] = useState<CatalogProvider>(
    (state.current?.provider as CatalogProvider) ?? 'OPENROUTER',
  );
  const [selectedModelIds, setSelectedModelIds] = useState<string[]>(
    state.current?.modelIds ?? [],
  );

  const catalogQuery = useCatalog(provider);
  const availableModels = useMemo(() => {
    const featureMap = catalogQuery.data?.features;
    if (!featureMap) return [];
    const focusedBlock = featureMap[state.feature];
    const pool = focusedBlock
      ? focusedBlock.models
      : Object.values(featureMap).flatMap((block) => block.models);
    const seen = new Set<string>();
    return pool.filter((model) => {
      if (model.deprecatedAt) return false;
      if (seen.has(model.modelId)) return false;
      seen.add(model.modelId);
      return true;
    });
  }, [catalogQuery.data, state.feature]);

  const unselectedModels = availableModels.filter(
    (model) => !selectedModelIds.includes(model.modelId),
  );

  const canSubmit = !assignMutation.isPending && provider && selectedModelIds.length > 0;

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
        <DialogTitle>Gán mặc định — {FEATURE_LABELS[state.feature]}</DialogTitle>
        <DialogDescription>
          {TIER_LABELS[state.tier]}. Router thử models trong tier theo thứ tự, hết tier mới
          sang tier kế tiếp.
        </DialogDescription>
      </DialogHeader>

      <div className="space-y-5">
        <div className="space-y-2">
          <Label htmlFor="tier-provider">Provider</Label>
          <Select
            value={provider}
            onValueChange={(next) => {
              setProvider(next as CatalogProvider);
              setSelectedModelIds([]);
            }}
          >
            <SelectTrigger id="tier-provider" className="h-11 w-full">
              <SelectValue placeholder="Chọn provider" />
            </SelectTrigger>
            <SelectContent>
              {catalogProviders.map((entry) => (
                <SelectItem key={entry} value={entry}>
                  {providerLabel(entry)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label>Models theo thứ tự ưu tiên</Label>
          {selectedModelIds.length === 0 ? (
            <div className="border-border text-muted-foreground rounded-md border border-dashed px-3 py-6 text-center text-sm">
              Chưa chọn model nào. Thêm model bên dưới — Tier 1 sẽ thử theo thứ tự.
            </div>
          ) : (
            <ol className="space-y-2">
              {selectedModelIds.map((modelId, index) => (
                <li
                  key={modelId}
                  className="border-border bg-background flex items-center gap-2 rounded-md border px-2 py-2"
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
                      : 'Chọn model để thêm vào cuối list'
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
            Backend chỉ chấp nhận model đã <span className="font-medium">VERIFIED</span> hoặc
            STALE.
          </p>
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
                provider: provider as CatalogProvider,
                modelIds: selectedModelIds,
              },
              {
                onSuccess: () => onClose(),
              },
            )
          }
        >
          {assignMutation.isPending ? 'Đang lưu…' : 'Lưu tier'}
        </Button>
      </DialogFooter>
    </DialogContent>
  );
}
