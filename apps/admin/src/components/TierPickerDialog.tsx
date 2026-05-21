import { useMemo, useState } from 'react';

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
import { catalogProviders, type CatalogProvider } from '@/features/catalog/catalog-api';
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
 * Modal that assigns a (provider, model) pair to a specific (feature, tier)
 * slot of the routing matrix. Server-side guard rejects models that are not
 * VERIFIED/STALE; the picker surfaces every undeprecated model to keep the
 * UI lightweight, and lets the backend gate the final write.
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
          key={`${state.feature}:${state.tier}:${state.current?.modelId ?? ''}`}
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
  const [modelId, setModelId] = useState<string>(state.current?.modelId ?? '');

  const catalogQuery = useCatalog(provider);
  const models = useMemo(() => {
    const featureMap = catalogQuery.data?.features;
    if (!featureMap) return [];
    const focusedBlock = featureMap[state.feature];
    const candidatePool = focusedBlock
      ? focusedBlock.models
      : Object.values(featureMap).flatMap((block) => block.models);
    const seen = new Set<string>();
    return candidatePool.filter((model) => {
      if (model.deprecatedAt) return false;
      if (seen.has(model.modelId)) return false;
      seen.add(model.modelId);
      return true;
    });
  }, [catalogQuery.data, state.feature]);

  const canSubmit =
    !assignMutation.isPending && provider && modelId && modelId.trim().length > 0;

  return (
    <>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Gán mặc định — {FEATURE_LABELS[state.feature]}</DialogTitle>
          <DialogDescription>{TIER_LABELS[state.tier]}</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="tier-provider">Provider</Label>
            <Select
              value={provider}
              onValueChange={(next) => {
                setProvider(next as CatalogProvider);
                setModelId('');
              }}
            >
              <SelectTrigger id="tier-provider">
                <SelectValue placeholder="Chọn provider" />
              </SelectTrigger>
              <SelectContent>
                {catalogProviders.map((entry) => (
                  <SelectItem key={entry} value={entry}>
                    {entry}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="tier-model">Model</Label>
            <Select
              value={modelId}
              onValueChange={(next) => setModelId(next ?? '')}
              disabled={catalogQuery.isPending || models.length === 0}
            >
              <SelectTrigger id="tier-model">
                <SelectValue
                  placeholder={
                    catalogQuery.isPending
                      ? 'Đang tải models…'
                      : models.length === 0
                        ? 'Provider chưa có model'
                        : 'Chọn model'
                  }
                />
              </SelectTrigger>
              <SelectContent>
                {models.map((model) => (
                  <SelectItem key={model.modelId} value={model.modelId}>
                    <span className="block text-sm">{model.displayName}</span>
                    <span className="text-muted-foreground block font-mono text-[11px]">
                      {model.modelId}
                    </span>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-muted-foreground text-xs">
              Backend chỉ chấp nhận model đã <span className="font-medium">VERIFIED</span> hoặc
              STALE — nếu chọn model FAILED/UNTESTED, request sẽ bị từ chối.
            </p>
          </div>
        </div>

        <DialogFooter className="gap-2">
          <DialogClose render={(p) => <Button variant="ghost" {...p} />}>Hủy</DialogClose>
          <Button
            type="button"
            disabled={!canSubmit}
            onClick={() =>
              assignMutation.mutate(
                {
                  feature: state.feature,
                  tier: state.tier,
                  provider: provider as CatalogProvider,
                  modelId,
                },
                {
                  onSuccess: () => onClose(),
                },
              )
            }
          >
            {assignMutation.isPending ? 'Đang lưu…' : 'Gán'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </>
  );
}
