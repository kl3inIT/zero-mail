import { ChevronRightIcon } from 'lucide-react';

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
  { id: 'PRIMARY', label: 'Tier 1 — Primary', tone: 'bg-violet-soft text-primary' },
  { id: 'FALLBACK', label: 'Tier 2 — Fallback', tone: 'bg-blue-soft text-blue' },
  { id: 'LAST_RESORT', label: 'Tier 3 — Last Resort', tone: 'bg-amber-soft text-amber' },
];

export type RoutingMatrixCardProps = {
  /**
   * Click handler for a single (feature, tier) cell. Receives the cell
   * coordinates plus the current binding (or `null` if unassigned). The
   * picker modal lives outside this card — supply a callback that opens
   * it with the right pre-filled fields.
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

  return (
    <Card>
      <CardContent className="pt-6">
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
}: {
  feature: { id: RoutingFeature; label: string; description: string };
  bindings: FeatureDefaultBinding[];
  onCellClick?: RoutingMatrixCardProps['onCellClick'];
}) {
  return (
    <div className="border-border bg-background rounded-md border p-3">
      <div className="mb-3">
        <div className="text-ink font-semibold">{feature.label}</div>
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
  const isAssigned = Boolean(current?.modelId);
  return (
    <button
      type="button"
      onClick={onClick}
      className="group border-border hover:border-primary/40 bg-card flex w-full items-center gap-3 rounded-md border px-3 py-2 text-left transition-colors"
    >
      <span
        className={`inline-flex shrink-0 items-center rounded-md px-2 py-0.5 font-mono text-[10px] tracking-wide uppercase ${tier.tone}`}
      >
        {tier.label}
      </span>
      <span className="min-w-0 flex-1">
        {isAssigned ? (
          <>
            <span className="text-ink block truncate text-sm font-medium">{current!.modelId}</span>
            <span className="text-muted-foreground block truncate font-mono text-[11px]">
              {current!.provider}
            </span>
          </>
        ) : (
          <span className="text-muted-foreground text-sm italic">Chưa gán</span>
        )}
      </span>
      <ChevronRightIcon className="text-muted-foreground group-hover:text-primary size-4 shrink-0" />
    </button>
  );
}
