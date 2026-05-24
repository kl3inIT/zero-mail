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

type RoutingFeatureMeta = { id: RoutingFeature; label: string };

const ROUTING_GROUPS: { title: string; features: RoutingFeatureMeta[] }[] = [
  {
    title: 'Người dùng',
    features: [
      { id: 'CHAT', label: 'Chat trợ lý' },
      { id: 'DRAFT', label: 'Soạn nội dung' },
    ],
  },
  {
    title: 'Quy tắc',
    features: [
      { id: 'RULE_AUTHORING', label: 'Tạo quy tắc' },
      { id: 'RULE_PREVIEW_SEMANTIC', label: 'Test quy tắc' },
      { id: 'TRIAGE_SEMANTIC', label: 'Chạy quy tắc' },
    ],
  },
  {
    title: 'Nâng cao',
    features: [
      { id: 'TRIAGE', label: 'Chọn hành động AI' },
      { id: 'DRIFT_CHECK', label: 'Kiểm tra chất lượng' },
    ],
  },
];

const TIERS: { id: RoutingTier; label: string; tone: string }[] = [
  { id: 'PRIMARY', label: 'Chính', tone: 'bg-green-soft text-green border-transparent' },
  { id: 'FALLBACK', label: 'Dự phòng', tone: 'bg-blue-soft text-blue border-transparent' },
  {
    id: 'LAST_RESORT',
    label: 'Cuối cùng',
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
      <CardContent className="space-y-5 pt-6">
        {matrixQuery.isPending ? (
          <Skeleton className="h-48 w-full" />
        ) : (
          <>
            <div className="hidden grid-cols-[minmax(160px,1fr)_repeat(3,minmax(0,1fr))] gap-2 px-1 text-xs font-medium text-muted-foreground md:grid">
              <div>Tác vụ</div>
              {TIERS.map((tier) => (
                <div key={tier.id}>{tier.label}</div>
              ))}
            </div>
            {ROUTING_GROUPS.map((group) => (
              <section key={group.title} className="space-y-2">
                <h3 className="text-muted-foreground text-xs font-semibold uppercase tracking-wide">
                  {group.title}
                </h3>
                <div className="space-y-2">
                  {group.features.map((feature) => (
                    <FeatureRow
                      key={feature.id}
                      feature={feature}
                      bindings={bindings}
                      onCellClick={onCellClick}
                      warn={featuresWithSameProviderTiers.has(feature.id)}
                    />
                  ))}
                </div>
              </section>
            ))}
          </>
        )}
      </CardContent>
    </Card>
  );
}

function FeatureRow({
  feature,
  bindings,
  onCellClick,
  warn,
}: {
  feature: RoutingFeatureMeta;
  bindings: FeatureDefaultBinding[];
  onCellClick?: RoutingMatrixCardProps['onCellClick'];
  warn: boolean;
}) {
  return (
    <div className="border-border bg-background grid gap-2 rounded-md border p-2 md:grid-cols-[minmax(160px,1fr)_repeat(3,minmax(0,1fr))]">
      <div className="flex min-h-12 items-center gap-2 px-2">
        <div className="font-medium">{feature.label}</div>
        {warn && (
          <Badge className="bg-amber-soft text-amber border-transparent text-xs">
            Trùng provider
          </Badge>
        )}
      </div>
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
  const primaryModel = modelIds[0];
  const extraModelCount = Math.max(0, modelIds.length - 1);
  return (
    <button
      type="button"
      onClick={onClick}
      className="group border-border hover:border-primary/40 bg-card flex min-h-12 w-full items-center gap-2 rounded-md border px-3 py-2 text-left transition-colors"
    >
      <Badge className={`shrink-0 md:hidden ${tier.tone}`}>{tier.label}</Badge>
      <span className="min-w-0 flex-1">
        {isAssigned ? (
          <span className="block min-w-0">
            <span className="block truncate text-sm font-medium">{current!.provider}</span>
            <span className="text-muted-foreground block truncate font-mono text-xs">
              {primaryModel}
              {extraModelCount > 0 ? ` +${extraModelCount}` : ''}
            </span>
          </span>
        ) : (
          <span className="text-muted-foreground text-sm">Chưa gán</span>
        )}
      </span>
      <ChevronRightIcon className="text-muted-foreground group-hover:text-primary size-4 shrink-0" />
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
