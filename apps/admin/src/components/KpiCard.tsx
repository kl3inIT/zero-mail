import { ArrowDownRightIcon, ArrowUpRightIcon, MinusIcon } from 'lucide-react';
import type { ReactNode } from 'react';

import { Card } from '@/components/ui/card';

export type KpiDelta = {
  sign: 'up' | 'down' | 'flat';
  value: string;
};

export type KpiCardProps = {
  label: string;
  value: ReactNode;
  hint?: string;
  delta?: KpiDelta;
  tabular?: boolean;
  /** Optional render-prop for a sparkline / mini-chart slot. */
  sparkline?: ReactNode;
  /** Disambiguator for Playwright `getByTestId(...)`. */
  testId?: string;
};

/**
 * Shared KPI primitive used on `/queue` (Phase 8E) and `/spend` (Phase 8F). Justified by
 * UI-SPEC §Component Composition row "Used 4+ times on Dashboard, 4+ times on Spend".
 *
 * Numeric rendering uses `tabular-nums` so columns don't jitter on the 10s auto-refresh tick.
 */
export function KpiCard({
  label,
  value,
  hint,
  delta,
  tabular = true,
  sparkline,
  testId,
}: KpiCardProps) {
  return (
    <Card data-slot="kpi-card" data-testid={testId} className="gap-2 px-4 py-3">
      <div className="text-muted-foreground text-xs font-medium tracking-wide uppercase">
        {label}
      </div>
      <div className="flex items-baseline justify-between gap-3">
        <div
          className={
            tabular
              ? 'text-ink text-2xl font-semibold tabular-nums'
              : 'text-ink text-2xl font-semibold'
          }
        >
          {value}
        </div>
        {delta && <KpiDeltaBadge delta={delta} />}
      </div>
      {sparkline && <div className="mt-1">{sparkline}</div>}
      {hint && <div className="text-muted-foreground text-xs">{hint}</div>}
    </Card>
  );
}

function KpiDeltaBadge({ delta }: { delta: KpiDelta }) {
  const Icon =
    delta.sign === 'up'
      ? ArrowUpRightIcon
      : delta.sign === 'down'
        ? ArrowDownRightIcon
        : MinusIcon;
  const tone =
    delta.sign === 'up'
      ? 'text-green'
      : delta.sign === 'down'
        ? 'text-destructive'
        : 'text-muted-foreground';
  return (
    <span className={`inline-flex items-center gap-1 text-xs font-medium ${tone}`}>
      <Icon className="size-3" />
      <span className="tabular-nums">{delta.value}</span>
    </span>
  );
}
