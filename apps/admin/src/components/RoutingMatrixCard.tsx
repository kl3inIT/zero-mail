import { ChevronRightIcon } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import {
  type FeatureDefaultBinding,
  type RoutingFeature,
  type RoutingTier,
} from '@/features/feature-defaults/feature-defaults-api';
import { useFeatureDefaults } from '@/features/feature-defaults/use-feature-defaults';

const FEATURES: { id: RoutingFeature; label: string; description: string }[] = [
  { id: 'CHAT', label: 'Chat trợ lý', description: 'Người dùng trò chuyện trực tiếp.' },
  { id: 'TRIAGE', label: 'Phân loại email', description: 'Auto-triage cho hộp thư đến.' },
  { id: 'DRAFT', label: 'Soạn nháp', description: 'Soạn reply/forward theo template.' },
];

const TIERS: { id: RoutingTier; label: string; tone: string }[] = [
  { id: 'PRIMARY', label: 'Tier 1 — Primary', tone: 'bg-green-soft text-green border-transparent' },
  { id: 'FALLBACK', label: 'Tier 2 — Fallback', tone: 'bg-blue-soft text-blue border-transparent' },
  {
    id: 'LAST_RESORT',
    label: 'Tier 3 — Last Resort',
    tone: 'bg-amber-soft text-amber border-transparent',
  },
];

export type RoutingMatrixCardProps = {
  /**
   * Click handler for a single (feature, tier) cell. Receives the coordinates plus the current
   * binding (or `null` when unassigned). The picker modal lives outside this card — supply a
   * callback that opens it with the right pre-filled fields.
   */
  onCellClick?: (
    feature: RoutingFeature,
    tier: RoutingTier,
    current: FeatureDefaultBinding | null,
  ) => void;
};

export function RoutingMatrixCard({ onCellClick }: RoutingMatrixCardProps) {
  const matrixQuery = useFeatureDefaults();
  const bindings = matrixQuery.data?.bindings ?? [];

  // Per LiteLLM / 2026 consensus: tier boundaries should favor cross-provider diversity to
  // avoid correlated outages (shared moderation, regional outage). Warn (don't block) when
  // two tiers of the same feature share a provider.
  const featuresWithSameProviderTiers = detectFeaturesWithoutCrossProviderTiers(bindings);

  return (
    <Card>
      <CardContent className="space-y-3 pt-6">
        {matrixQuery.isPending ? (
          <Skeleton className="h-48 w-full" />
        ) : (
          <div className="grid gap-3 md:grid-cols-3">
            {FEATURES.map((feature) => (
              <FeatureColumn
                key={feature.id}
                feature={feature}
                bindings={bindings}
                onCellClick={onCellClick}
                warn={featuresWithSameProviderTiers.has(feature.id)}
              />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function FeatureColumn({
  feature,
  bindings,
  onCellClick,
  warn,
}: {
  feature: { id: RoutingFeature; label: string; description: string };
  bindings: FeatureDefaultBinding[];
  onCellClick?: RoutingMatrixCardProps['onCellClick'];
  warn: boolean;
}) {
  return (
    <div className="border-border bg-background rounded-md border p-3">
      <div className="mb-3">
        <div className="flex items-center gap-2">
          <div className="font-semibold">{feature.label}</div>
          {warn && (
            <Badge className="bg-amber-soft text-amber border-transparent text-xs">
              Trùng provider giữa tier
            </Badge>
          )}
        </div>
        <div className="text-muted-foreground text-xs">{feature.description}</div>
      </div>
      <div className="space-y-2">
        {TIERS.map((tier) => {
          const current =
            bindings.find((row) => row.feature === feature.id && row.tier === tier.id) ?? null;
          return (
            <TierCell
              key={tier.id}
              tier={tier}
              current={current}
              onClick={() => onCellClick?.(feature.id, tier.id, current)}
            />
          );
        })}
      </div>
    </div>
  );
}

function TierCell({
  tier,
  current,
  onClick,
}: {
  tier: { id: RoutingTier; label: string; tone: string };
  current: FeatureDefaultBinding | null;
  onClick: () => void;
}) {
  const modelIds = current?.modelIds ?? [];
  const isAssigned = modelIds.length > 0;
  return (
    <button
      type="button"
      onClick={onClick}
      className="group border-border hover:border-primary/40 bg-card flex w-full items-start gap-3 rounded-md border px-3 py-2 text-left transition-colors"
    >
      <Badge className={`shrink-0 font-mono ${tier.tone}`}>{tier.label}</Badge>
      <span className="min-w-0 flex-1 space-y-1">
        {isAssigned ? (
          <>
            <span className="text-muted-foreground block truncate font-mono text-xs">
              {current!.provider}
            </span>
            <span className="flex flex-wrap gap-1">
              {modelIds.map((modelId, index) => (
                <Badge
                  key={modelId}
                  className="bg-violet-soft text-primary border-transparent font-mono text-[10px]"
                >
                  {index + 1}. {modelId}
                </Badge>
              ))}
            </span>
          </>
        ) : (
          <span className="text-muted-foreground text-sm italic">Chưa gán</span>
        )}
      </span>
      <ChevronRightIcon className="text-muted-foreground group-hover:text-primary mt-1 size-4 shrink-0" />
    </button>
  );
}

function detectFeaturesWithoutCrossProviderTiers(
  bindings: FeatureDefaultBinding[],
): Set<RoutingFeature> {
  const tiersByFeature = new Map<RoutingFeature, Set<string>>();
  const tierCount = new Map<RoutingFeature, number>();
  for (const binding of bindings) {
    if (!binding.feature || !binding.provider) continue;
    const providerSet =
      tiersByFeature.get(binding.feature) ?? new Set<string>();
    providerSet.add(binding.provider);
    tiersByFeature.set(binding.feature, providerSet);
    tierCount.set(binding.feature, (tierCount.get(binding.feature) ?? 0) + 1);
  }
  const warned = new Set<RoutingFeature>();
  for (const [feature, providers] of tiersByFeature.entries()) {
    const count = tierCount.get(feature) ?? 0;
    if (count > 1 && providers.size < count) {
      warned.add(feature);
    }
  }
  return warned;
}
