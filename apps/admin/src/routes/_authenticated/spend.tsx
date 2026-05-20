import { createFileRoute, Link } from '@tanstack/react-router';
import { DownloadIcon } from 'lucide-react';
import { useMemo, useState } from 'react';

import { AutoRefreshIndicator } from '@/components/AutoRefreshIndicator';
import { KpiCard } from '@/components/KpiCard';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  downloadSpendCsv,
  isRangeWithinLimit,
  type FeatureDonutSliceResponse,
  type ProviderStackBarRowResponse,
  type SpendDashboardResponse,
  type SpendQueryInput,
  type TopTenantRowResponse,
} from '@/features/spend/spend-api';
import {
  SPEND_REFRESH_INTERVAL_MS,
  useSpendDashboard,
} from '@/features/spend/use-spend-dashboard';

export const Route = createFileRoute('/_authenticated/spend')({
  component: SpendRoute,
});

type PresetId = 'today' | '7d' | '30d' | 'custom';

const PRESET_DAY_COUNT: Record<Exclude<PresetId, 'custom'>, number> = {
  today: 1,
  '7d': 7,
  '30d': 30,
};

function SpendRoute() {
  const [paused, setPaused] = useState(false);
  const [preset, setPreset] = useState<PresetId>('30d');
  const [customRangeError, setCustomRangeError] = useState<string | null>(null);
  const [customFrom, setCustomFrom] = useState<string>('');
  const [customTo, setCustomTo] = useState<string>('');
  const [csvDownloading, setCsvDownloading] = useState(false);
  const [csvError, setCsvError] = useState<string | null>(null);

  const queryInput = useMemo<SpendQueryInput>(() => {
    if (preset === 'custom' && customFrom && customTo) {
      return { from: new Date(customFrom), to: new Date(customTo) };
    }
    const presetKey = (preset === 'custom' ? '30d' : preset) as Exclude<PresetId, 'custom'>;
    const now = new Date();
    const from = new Date(now);
    from.setDate(from.getDate() - PRESET_DAY_COUNT[presetKey]);
    return { from, to: now };
  }, [preset, customFrom, customTo]);

  const rangeOk = isRangeWithinLimit(queryInput);

  const spendDashboard = useSpendDashboard(queryInput, { paused: paused || !rangeOk });

  const lastUpdatedAt = useMemo(() => {
    if (!spendDashboard.data) return null;
    return new Date(spendDashboard.data.snapshotAt);
  }, [spendDashboard.data]);

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between gap-4">
        <div>
          <p className="text-muted-foreground font-mono text-[11px] tracking-wider uppercase">
            Operations
          </p>
          <h1 className="text-ink text-xl font-semibold">Spend dashboard</h1>
          <p className="text-muted-foreground mt-1 max-w-2xl text-sm">
            Aggregate-only spend over <code>llm_call_audit</code>. Prompt and completion text
            are never read by this page.
          </p>
        </div>
        <AutoRefreshIndicator
          lastUpdatedAt={lastUpdatedAt}
          intervalMs={SPEND_REFRESH_INTERVAL_MS}
          paused={paused}
          onPauseToggle={() => setPaused((previous) => !previous)}
        />
      </header>

      <div className="bg-card border-border flex flex-wrap items-end justify-between gap-3 rounded-lg border p-3">
        <PresetPicker
          preset={preset}
          onPresetChange={(nextPreset) => {
            setPreset(nextPreset);
            setCustomRangeError(null);
          }}
          customFrom={customFrom}
          customTo={customTo}
          onCustomFromChange={(value) => {
            setCustomFrom(value);
            setCustomRangeError(null);
          }}
          onCustomToChange={(value) => {
            setCustomTo(value);
            setCustomRangeError(null);
          }}
          onApplyCustom={() => {
            if (!customFrom || !customTo) {
              setCustomRangeError('Pick both a start and end date.');
              return;
            }
            const candidate = { from: new Date(customFrom), to: new Date(customTo) };
            if (!isRangeWithinLimit(candidate)) {
              setCustomRangeError(
                'Date range maximum is 90 days. Choose a narrower window or use 7d/30d presets.',
              );
              return;
            }
            if (candidate.from >= candidate.to) {
              setCustomRangeError('Start date must be before end date.');
              return;
            }
            setCustomRangeError(null);
          }}
          customRangeError={customRangeError}
        />
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            data-testid="spend-export-csv"
            disabled={!rangeOk || csvDownloading}
            onClick={async () => {
              setCsvError(null);
              setCsvDownloading(true);
              try {
                await downloadSpendCsv(queryInput);
              } catch (error) {
                setCsvError(
                  error instanceof Error ? error.message : 'CSV export failed.',
                );
              } finally {
                setCsvDownloading(false);
              }
            }}
          >
            <DownloadIcon className="size-3.5" />
            {csvDownloading ? 'Exporting…' : 'Export CSV'}
          </Button>
        </div>
      </div>
      {csvError && (
        <p className="text-destructive text-xs" data-testid="spend-export-error">
          {csvError}
        </p>
      )}

      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
        <KpiTiles dashboard={spendDashboard.data} loading={spendDashboard.isLoading} />
      </section>

      {spendDashboard.data && spendDashboard.data.unknownPercentOfTotal > 0 && (
        <p
          className="text-muted-foreground text-xs"
          data-testid="spend-unknown-caveat"
        >
          {spendDashboard.data.unknownPercentOfTotal.toFixed(1)}% of spend in this range
          predates row-level credential classification and is shown as Unknown. New calls are
          classified at write time. (Phase 8F deploy:{' '}
          {spendDashboard.data.rowLevelClassificationSince})
        </p>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Daily spend by provider</CardTitle>
            <CardDescription>
              Stacked: <span className="text-emerald-600">platform</span> +{' '}
              <span className="text-blue-600">BYOK</span> +{' '}
              <span className="text-muted-foreground">unknown</span>. All values in USD.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {spendDashboard.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : (
              <StackedProviderBar rows={spendDashboard.data?.stackBar ?? []} />
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Spend by feature</CardTitle>
            <CardDescription>Share of cost split across CHAT, TRIAGE, DRAFT.</CardDescription>
          </CardHeader>
          <CardContent>
            {spendDashboard.isLoading ? (
              <Skeleton className="h-48 w-full" />
            ) : (
              <FeatureDonut slices={spendDashboard.data?.donut ?? []} />
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Top 20 tenants</CardTitle>
          <CardDescription>
            Ranked by total cost. Buckets below k = 5 are rolled up; deleted-tenant rows
            collapse into a single aggregate entry.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Tenant</TableHead>
                <TableHead className="text-right">Total cost</TableHead>
                <TableHead className="text-right">Unknown %</TableHead>
                <TableHead className="text-right">Calls</TableHead>
                <TableHead className="text-right">Bucket</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {spendDashboard.isLoading && (
                <TableRow>
                  <TableCell colSpan={5} className="text-muted-foreground h-24 text-center">
                    Loading tenant breakdown.
                  </TableCell>
                </TableRow>
              )}
              {!spendDashboard.isLoading &&
                (spendDashboard.data?.topTenants ?? []).length === 0 && (
                  <TableRow>
                    <TableCell
                      colSpan={5}
                      className="text-muted-foreground h-24 text-center"
                    >
                      No tenant spend in this range.
                    </TableCell>
                  </TableRow>
                )}
              {spendDashboard.data?.topTenants.map((row, rowIndex) => (
                <TableRow key={row.tenantId ?? `rollup-${rowIndex}`}>
                  <TableCell>{renderTenantLabel(row)}</TableCell>
                  <TableCell className="text-right tabular-nums">
                    ${formatMoney(row.totalCost)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {row.unknownPct.toFixed(1)}%
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {row.callCount.toLocaleString()}
                  </TableCell>
                  <TableCell className="text-muted-foreground text-right text-xs">
                    {row.isKAnonymized ? 'Aggregated' : 'Individual'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          {spendDashboard.data && (
            <p
              className="text-muted-foreground mt-3 text-xs"
              data-testid="spend-kanonymity-footer"
            >
              {spendDashboard.data.kAnonymityFooterNote}
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function renderTenantLabel(row: TopTenantRowResponse) {
  if (row.tenantId && !row.isKAnonymized) {
    return (
      <Link
        to="/tenants/$tenantId"
        params={{ tenantId: row.tenantId }}
        className="text-primary font-mono text-xs hover:underline"
      >
        {row.gmailAccountEmailOrPlaceholder}
      </Link>
    );
  }
  return (
    <span className="text-muted-foreground font-mono text-xs">
      {row.gmailAccountEmailOrPlaceholder}
    </span>
  );
}

function formatMoney(value: string): string {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) return value;
  return numeric.toFixed(2);
}

function KpiTiles({
  dashboard,
  loading,
}: {
  dashboard: SpendDashboardResponse | undefined;
  loading: boolean;
}) {
  if (loading || !dashboard) {
    return (
      <>
        {Array.from({ length: 6 }).map((_, index) => (
          <Skeleton key={index} className="h-24" />
        ))}
      </>
    );
  }
  const { kpis } = dashboard;
  return (
    <>
      <KpiCard
        testId="kpi-today-platform"
        label="Today platform"
        value={`$${formatMoney(kpis.todayPlatformCost)}`}
        hint={`${kpis.todayCallCount.toLocaleString()} calls today`}
      />
      <KpiCard
        testId="kpi-today-byok"
        label="Today BYOK"
        value={`$${formatMoney(kpis.todayByokCost)}`}
        hint="User-supplied keys"
      />
      <KpiCard
        testId="kpi-7d-platform"
        label="7d platform"
        value={`$${formatMoney(kpis.sevenDayPlatformCost)}`}
        hint={`${kpis.sevenDayCallCount.toLocaleString()} calls in 7d`}
      />
      <KpiCard
        testId="kpi-7d-byok"
        label="7d BYOK"
        value={`$${formatMoney(kpis.sevenDayByokCost)}`}
        hint="User-supplied keys"
      />
      <KpiCard
        testId="kpi-30d-platform"
        label="30d platform"
        value={`$${formatMoney(kpis.thirtyDayPlatformCost)}`}
        hint={`${kpis.thirtyDayCallCount.toLocaleString()} calls in 30d`}
      />
      <KpiCard
        testId="kpi-30d-byok"
        label="30d BYOK"
        value={`$${formatMoney(kpis.thirtyDayByokCost)}`}
        hint="User-supplied keys"
      />
    </>
  );
}

function StackedProviderBar({ rows }: { rows: ProviderStackBarRowResponse[] }) {
  if (rows.length === 0) {
    return (
      <div className="text-muted-foreground py-8 text-center text-sm">
        No spend in this range.
      </div>
    );
  }
  const maxCost = rows.reduce((acc, row) => {
    const total =
      Number(row.platformCost) + Number(row.byokCost) + Number(row.unknownCost);
    return Math.max(acc, total);
  }, 0);
  return (
    <div
      role="img"
      aria-label="Stacked bar chart of daily spend by provider, segmented by credential source"
      data-testid="spend-stack-bar"
      className="space-y-2"
    >
      {rows.map((row) => {
        const platform = Number(row.platformCost);
        const byok = Number(row.byokCost);
        const unknown = Number(row.unknownCost);
        const total = platform + byok + unknown;
        const widthFactor = maxCost > 0 ? total / maxCost : 0;
        return (
          <div
            key={`${row.bucketDate}-${row.provider}`}
            className="flex items-center gap-3"
            data-testid={`spend-bar-${row.provider}`}
          >
            <div className="text-muted-foreground w-32 font-mono text-xs">
              {row.bucketDate.slice(0, 10)} · {row.provider}
            </div>
            <div className="bg-secondary relative h-6 flex-1 overflow-hidden rounded">
              <div className="absolute inset-0 flex" style={{ width: `${widthFactor * 100}%` }}>
                {total > 0 && (
                  <>
                    <div
                      className="h-full bg-emerald-500"
                      style={{ width: `${(platform / total) * 100}%` }}
                      title={`platform $${platform.toFixed(4)}`}
                    />
                    <div
                      className="h-full bg-blue-500"
                      style={{ width: `${(byok / total) * 100}%` }}
                      title={`BYOK $${byok.toFixed(4)}`}
                    />
                    <div
                      className="h-full bg-gray-400"
                      style={{ width: `${(unknown / total) * 100}%` }}
                      title={`unknown $${unknown.toFixed(4)}`}
                    />
                  </>
                )}
              </div>
            </div>
            <div className="w-20 text-right text-xs tabular-nums">${total.toFixed(2)}</div>
          </div>
        );
      })}
    </div>
  );
}

function FeatureDonut({ slices }: { slices: FeatureDonutSliceResponse[] }) {
  const total = slices.reduce((acc, slice) => acc + Number(slice.totalCost), 0);
  if (total <= 0) {
    return (
      <div className="text-muted-foreground py-8 text-center text-sm">
        No feature spend in this range.
      </div>
    );
  }
  const colors: Record<string, string> = {
    CHAT: 'bg-violet-500',
    TRIAGE: 'bg-blue-500',
    DRAFT: 'bg-emerald-500',
  };
  return (
    <div
      role="img"
      aria-label="Donut chart of spend split by feature"
      data-testid="spend-feature-donut"
      className="space-y-3"
    >
      <div className="bg-secondary flex h-6 overflow-hidden rounded-full">
        {slices.map((slice) => {
          const share = Number(slice.totalCost) / total;
          return (
            <div
              key={slice.feature}
              className={colors[slice.feature] ?? 'bg-gray-400'}
              style={{ width: `${share * 100}%` }}
              title={`${slice.feature} ${slice.percentOfTotal.toFixed(1)}%`}
            />
          );
        })}
      </div>
      <ul className="space-y-1 text-xs">
        {slices.map((slice) => (
          <li
            key={slice.feature}
            className="flex items-center justify-between gap-2"
            data-testid={`spend-donut-${slice.feature}`}
          >
            <span className="flex items-center gap-2">
              <span
                aria-hidden
                className={`size-2 rounded-full ${colors[slice.feature] ?? 'bg-gray-400'}`}
              />
              <span className="font-mono">{slice.feature}</span>
            </span>
            <span className="text-muted-foreground tabular-nums">
              ${formatMoney(slice.totalCost)} · {slice.percentOfTotal.toFixed(1)}%
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function PresetPicker(props: {
  preset: PresetId;
  onPresetChange: (preset: PresetId) => void;
  customFrom: string;
  customTo: string;
  onCustomFromChange: (value: string) => void;
  onCustomToChange: (value: string) => void;
  onApplyCustom: () => void;
  customRangeError: string | null;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {(['today', '7d', '30d', 'custom'] as PresetId[]).map((option) => (
        <Button
          key={option}
          type="button"
          variant={props.preset === option ? 'default' : 'outline'}
          size="sm"
          onClick={() => props.onPresetChange(option)}
          data-testid={`spend-preset-${option}`}
        >
          {option === 'today' ? 'Today' : option === 'custom' ? 'Custom' : option}
        </Button>
      ))}
      {props.preset === 'custom' && (
        <div className="flex flex-wrap items-center gap-2">
          <input
            type="date"
            value={props.customFrom}
            onChange={(event) => props.onCustomFromChange(event.target.value)}
            className="border-border bg-background h-8 rounded border px-2 text-xs"
            aria-label="From date"
            data-testid="spend-custom-from"
          />
          <span className="text-muted-foreground text-xs">to</span>
          <input
            type="date"
            value={props.customTo}
            onChange={(event) => props.onCustomToChange(event.target.value)}
            className="border-border bg-background h-8 rounded border px-2 text-xs"
            aria-label="To date"
            data-testid="spend-custom-to"
          />
          <Button
            type="button"
            variant="secondary"
            size="sm"
            disabled={Boolean(props.customRangeError)}
            onClick={props.onApplyCustom}
            data-testid="spend-custom-apply"
          >
            Apply
          </Button>
          {props.customRangeError && (
            <span
              className="text-destructive text-xs"
              data-testid="spend-custom-error"
              role="alert"
            >
              {props.customRangeError}
            </span>
          )}
        </div>
      )}
    </div>
  );
}
