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
  // Draft inputs the user is typing.
  const [customFrom, setCustomFrom] = useState<string>('');
  const [customTo, setCustomTo] = useState<string>('');
  // Applied values that actually drive the fetch — only updated on Apply (WR-06/WR-09).
  const [appliedFrom, setAppliedFrom] = useState<string>('');
  const [appliedTo, setAppliedTo] = useState<string>('');
  const [csvDownloading, setCsvDownloading] = useState(false);
  const [csvError, setCsvError] = useState<string | null>(null);

  const queryInput = useMemo<SpendQueryInput>(() => {
    if (preset === 'custom' && appliedFrom && appliedTo) {
      return { from: new Date(appliedFrom), to: new Date(appliedTo) };
    }
    const presetKey = (preset === 'custom' ? '30d' : preset) as Exclude<PresetId, 'custom'>;
    const now = new Date();
    const from = new Date(now);
    from.setDate(from.getDate() - PRESET_DAY_COUNT[presetKey]);
    return { from, to: now };
  }, [preset, appliedFrom, appliedTo]);

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
            Vận hành
          </p>
          <h1 className="text-ink text-xl font-semibold">Bảng chi phí</h1>
          <p className="text-muted-foreground mt-1 max-w-2xl text-sm">
            Chỉ tổng hợp chi phí từ <code>llm_call_audit</code>. Trang này không bao giờ đọc văn bản prompt và completion.
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
              setCustomRangeError('Vui lòng chọn cả ngày bắt đầu và ngày kết thúc.');
              return;
            }
            const candidate = { from: new Date(customFrom), to: new Date(customTo) };
            if (!isRangeWithinLimit(candidate)) {
              setCustomRangeError(
                'Khoảng ngày tối đa là 90 ngày. Chọn khoảng nhỏ hơn hoặc dùng preset 7 ngày / 30 ngày.',
              );
              return;
            }
            if (candidate.from >= candidate.to) {
              setCustomRangeError('Ngày bắt đầu phải trước ngày kết thúc.');
              return;
            }
            setCustomRangeError(null);
            // Commit the draft inputs so the fetch picks them up.
            setAppliedFrom(customFrom);
            setAppliedTo(customTo);
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
                  error instanceof Error ? error.message : 'Xuất CSV thất bại.',
                );
              } finally {
                setCsvDownloading(false);
              }
            }}
          >
            <DownloadIcon className="size-3.5" />
            {csvDownloading ? 'Đang xuất…' : 'Xuất CSV'}
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
          {spendDashboard.data.unknownPercentOfTotal.toFixed(1)}% chi phí trong khoảng này có trước khi phân loại nguồn khóa theo bản ghi và được hiển thị là Không xác định. Các lượt gọi mới sẽ được phân loại ngay khi ghi nhận. (Triển khai Phase 8F:{' '}
          {spendDashboard.data.rowLevelClassificationSince})
        </p>
      )}

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Chi phí theo ngày và nhà cung cấp</CardTitle>
            <CardDescription>
              Xếp chồng: <span className="text-emerald-600">nền tảng</span> +{' '}
              <span className="text-blue-600">BYOK</span> +{' '}
              <span className="text-muted-foreground">không xác định</span>. Toàn bộ giá trị tính bằng USD.
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
            <CardTitle>Chi phí theo tính năng</CardTitle>
            <CardDescription>Tỷ lệ chi phí phân bổ giữa CHAT, TRIAGE, DRAFT.</CardDescription>
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
          <CardTitle>Top 20 khách hàng</CardTitle>
          <CardDescription>
            Xếp hạng theo tổng chi phí. Các nhóm dưới k = 5 được gộp lại; bản ghi của khách hàng đã xóa được gộp thành một mục tổng hợp.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Khách hàng</TableHead>
                <TableHead className="text-right">Tổng chi phí</TableHead>
                <TableHead className="text-right">% Không xác định</TableHead>
                <TableHead className="text-right">Lượt gọi</TableHead>
                <TableHead className="text-right">Nhóm</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {spendDashboard.isLoading && (
                <TableRow>
                  <TableCell colSpan={5} className="text-muted-foreground h-24 text-center">
                    Đang tải phân tích theo khách hàng.
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
                      Không có chi phí khách hàng trong khoảng này.
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
                    {row.isKAnonymized ? 'Tổng hợp' : 'Riêng lẻ'}
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

function formatMoney(value: number): string {
  if (!Number.isFinite(value)) return String(value);
  return value.toFixed(2);
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
        label="Nền tảng hôm nay"
        value={`$${formatMoney(kpis.todayPlatformCost)}`}
        hint={`${kpis.todayCallCount.toLocaleString()} lượt gọi hôm nay`}
      />
      <KpiCard
        testId="kpi-today-byok"
        label="BYOK hôm nay"
        value={`$${formatMoney(kpis.todayByokCost)}`}
        hint="Khóa do người dùng cung cấp"
      />
      <KpiCard
        testId="kpi-7d-platform"
        label="Nền tảng 7 ngày"
        value={`$${formatMoney(kpis.sevenDayPlatformCost)}`}
        hint={`${kpis.sevenDayCallCount.toLocaleString()} lượt gọi trong 7 ngày`}
      />
      <KpiCard
        testId="kpi-7d-byok"
        label="BYOK 7 ngày"
        value={`$${formatMoney(kpis.sevenDayByokCost)}`}
        hint="Khóa do người dùng cung cấp"
      />
      <KpiCard
        testId="kpi-30d-platform"
        label="Nền tảng 30 ngày"
        value={`$${formatMoney(kpis.thirtyDayPlatformCost)}`}
        hint={`${kpis.thirtyDayCallCount.toLocaleString()} lượt gọi trong 30 ngày`}
      />
      <KpiCard
        testId="kpi-30d-byok"
        label="BYOK 30 ngày"
        value={`$${formatMoney(kpis.thirtyDayByokCost)}`}
        hint="Khóa do người dùng cung cấp"
      />
    </>
  );
}

function StackedProviderBar({ rows }: { rows: ProviderStackBarRowResponse[] }) {
  if (rows.length === 0) {
    return (
      <div className="text-muted-foreground py-8 text-center text-sm">
        Không có chi phí trong khoảng này.
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
        Không có chi phí tính năng trong khoảng này.
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
          {option === 'today' ? 'Hôm nay' : option === 'custom' ? 'Tùy chỉnh' : option}
        </Button>
      ))}
      {props.preset === 'custom' && (
        <div className="flex flex-wrap items-center gap-2">
          <input
            type="date"
            value={props.customFrom}
            onChange={(event) => props.onCustomFromChange(event.target.value)}
            className="border-border bg-background h-8 rounded border px-2 text-xs"
            aria-label="Từ ngày"
            data-testid="spend-custom-from"
          />
          <span className="text-muted-foreground text-xs">đến</span>
          <input
            type="date"
            value={props.customTo}
            onChange={(event) => props.onCustomToChange(event.target.value)}
            className="border-border bg-background h-8 rounded border px-2 text-xs"
            aria-label="Đến ngày"
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
            Áp dụng
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
