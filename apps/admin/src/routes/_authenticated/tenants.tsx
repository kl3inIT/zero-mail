import { createFileRoute, Link, Outlet, useLocation, useNavigate } from '@tanstack/react-router';
import {
  ActivityIcon,
  CalendarDaysIcon,
  CheckCircle2Icon,
  ChevronDownIcon,
  FilterIcon,
  InfoIcon,
  MailIcon,
  SearchIcon,
  UsersIcon,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { useMemo, useState } from 'react';
import type { DateRange } from 'react-day-picker';
import { z } from 'zod';

import { Badge } from '@/components/ui/badge';
import { Button, buttonVariants } from '@/components/ui/button';
import { Calendar } from '@/components/ui/calendar';
import { Card, CardContent, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Select, SelectContent, SelectItem, SelectTrigger } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import type {
  TenantListFilters,
  TenantListResponse,
  TenantListRow,
  TenantStatusFilter,
} from '@/features/tenants/tenants-api';
import { useTenantList } from '@/features/tenants/use-tenant-list';

const tenantListSearchSchema = z.object({
  status: z.enum(['ACTIVE', 'PAUSED', 'DISCONNECTED']).optional().catch(undefined),
  email: z.string().optional().catch(undefined),
  from: z.string().optional().catch(undefined),
  to: z.string().optional().catch(undefined),
  cursor: z.string().optional().catch(undefined),
});

const integerFormatter = new Intl.NumberFormat();

type TenantDateRange = {
  from: string;
  to: string;
};

type TenantDatePreset = '7d' | '30d' | '3m' | 'custom';

export const Route = createFileRoute('/_authenticated/tenants')({
  validateSearch: tenantListSearchSchema,
  component: TenantsRoute,
});

function TenantsRoute() {
  const location = useLocation();
  const search = Route.useSearch();
  const navigate = useNavigate();
  const defaultDateRange = useMemo(() => getDefaultTenantDateRange(), []);
  const effectiveFrom = search.from ?? defaultDateRange.from;
  const effectiveTo = search.to ?? defaultDateRange.to;
  const effectiveEmail = search.email ?? '';
  const effectiveDatePreset = inferDatePreset(effectiveFrom, effectiveTo);
  const [status, setStatus] = useState<TenantStatusFilter>(search.status ?? 'ALL');
  const [email, setEmail] = useState(effectiveEmail);
  const [from, setFrom] = useState(effectiveFrom);
  const [to, setTo] = useState(effectiveTo);
  const [datePreset, setDatePreset] = useState<TenantDatePreset>(effectiveDatePreset);
  // Mirror URL search params into the form draft when the URL changes (browser
  // back, deep links). Uses React 19's "adjust state during render" pattern —
  // tracks the previous search value and resets the draft inline rather than
  // via useEffect, so there is no cascading render and no setState-in-effect.
  // https://react.dev/reference/react/useState#storing-information-from-previous-renders
  const [prevSearch, setPrevSearch] = useState(search);
  if (prevSearch !== search) {
    setPrevSearch(search);
    setStatus(search.status ?? 'ALL');
    setEmail(search.email ?? '');
    setFrom(search.from ?? defaultDateRange.from);
    setTo(search.to ?? defaultDateRange.to);
    setDatePreset(inferDatePreset(search.from ?? defaultDateRange.from, search.to ?? defaultDateRange.to));
  }
  const filters = useMemo<TenantListFilters>(
    () => ({
      status: search.status,
      email: effectiveEmail || undefined,
      from: effectiveFrom,
      to: effectiveTo,
      cursor: search.cursor,
      limit: 25,
    }),
    [search.status, search.cursor, effectiveEmail, effectiveFrom, effectiveTo],
  );
  const isListRoute = location.pathname === '/tenants';
  const tenantList = useTenantList(filters, isListRoute);
  const rows = useMemo(() => tenantList.data?.rows ?? [], [tenantList.data?.rows]);
  const summary = tenantList.data?.summary;
  const dashboardStats = useMemo(
    () => buildTenantDashboardStats(summary, rows, effectiveFrom, effectiveTo),
    [summary, rows, effectiveFrom, effectiveTo],
  );

  if (!isListRoute) {
    return <Outlet />;
  }

  function applyFilters(nextCursor?: string) {
    void navigate({
      to: '/tenants',
      search: {
        status: status === 'ALL' ? undefined : status,
        email: email.trim() || undefined,
        from: from || undefined,
        to: to || undefined,
        cursor: nextCursor,
      },
    });
  }

  function changeDatePreset(nextDatePreset: TenantDatePreset) {
    setDatePreset(nextDatePreset);
    if (nextDatePreset !== 'custom') {
      const nextDateRange = getPresetDateRange(nextDatePreset);
      setFrom(nextDateRange.from);
      setTo(nextDateRange.to);
    }
  }

  return (
    <div className="min-w-0 space-y-4">
      <header className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold text-ink">Khách hàng</h1>
        </div>
        <Badge variant="secondary" className="w-fit rounded-full px-3">
          {tenantList.isLoading ? 'Đang tải' : `${rows.length} đang hiển thị`}
        </Badge>
      </header>

      <TenantSummaryCards stats={dashboardStats} isLoading={tenantList.isLoading} />
      <TenantInsightsGrid stats={dashboardStats} />

      <Card className="min-w-0">
        <CardContent className="min-w-0 space-y-3 p-3">
          <form
            className="rounded-md border border-border bg-card p-2"
            onSubmit={(event) => {
              event.preventDefault();
              applyFilters();
            }}
          >
            <div className="grid gap-3 lg:grid-cols-[minmax(320px,1fr)_240px_minmax(300px,0.8fr)_auto] lg:items-end">
              <div className="space-y-2">
                <Label htmlFor="tenant-email">Tìm tenant / email</Label>
                <div className="relative">
                  <SearchIcon className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    id="tenant-email"
                    aria-label="Tìm tenant, owner, Gmail hoặc mã tenant"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="Tên tenant, Gmail, owner, mã tenant"
                    className="pl-8"
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label>Trạng thái tenant</Label>
                <Select value={status} onValueChange={(value) => setStatus(value as TenantStatusFilter)}>
                  <SelectTrigger className="h-8 w-full min-w-0">
                    <span data-slot="select-value">{tenantStatusFilterLabel(status)}</span>
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">Tất cả</SelectItem>
                    <SelectItem value="ACTIVE">Tenant hoạt động</SelectItem>
                    <SelectItem value="PAUSED">Tạm dừng</SelectItem>
                    <SelectItem value="DISCONNECTED">Đã ngắt kết nối</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <DateRangeField
                preset={datePreset}
                from={from}
                to={to}
                onPresetChange={changeDatePreset}
                onFromChange={setFrom}
                onToChange={setTo}
              />
              <div className="flex flex-wrap gap-2">
                <Button type="submit" className="bg-primary text-primary-foreground hover:bg-(--primary-hover)">
                  <FilterIcon className="size-4" />
                  Áp dụng
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setStatus('ALL');
                    setEmail('');
                    setDatePreset('3m');
                    setFrom(defaultDateRange.from);
                    setTo(defaultDateRange.to);
                    void navigate({ to: '/tenants', search: {} });
                  }}
                >
                  Xóa bộ lọc
                </Button>
              </div>
            </div>
          </form>
          <div className="min-w-0 overflow-x-auto">
            <Table className="min-w-[980px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="min-w-[300px]">Tenant</TableHead>
                  <TableHead className="min-w-[150px]">Gmail</TableHead>
                  <TableHead className="min-w-[150px]">Telegram</TableHead>
                  <TableHead className="min-w-[180px]">Hoạt động gần nhất</TableHead>
                  <TableHead className="text-right">Hành động</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tenantList.isLoading && (
                  <TableRow>
                    <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">
                      Đang tải danh sách khách hàng.
                    </TableCell>
                  </TableRow>
                )}
                {!tenantList.isLoading && rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} className="h-24 text-center">
                      <div className="font-medium">Không có khách hàng nào trong nhóm này</div>
                      <div className="text-sm text-muted-foreground">Điều chỉnh khoảng ngày hoặc bộ lọc trạng thái.</div>
                    </TableCell>
                  </TableRow>
                )}
                {rows.map((row) => (
                  <TenantRow key={row.tenantId} row={row} />
                ))}
              </TableBody>
            </Table>
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" disabled={!search.cursor} onClick={() => applyFilters()}>
              Trang đầu
            </Button>
            <Button
              type="button"
              variant="secondary"
              disabled={!tenantList.data?.hasNextPage || !tenantList.data.nextCursor}
              onClick={() => applyFilters(tenantList.data?.nextCursor)}
            >
              Trang sau
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function TenantRow({ row }: { row: TenantListRow }) {
  const extraGmailCount = Math.max(0, row.gmailAccountCount - 1);
  return (
    <TableRow>
      <TableCell>
        <div className="min-w-0">
          <div className="truncate text-sm font-medium text-ink">{row.tenantDisplayName}</div>
          <div className="mt-1 truncate text-xs text-muted-foreground">
            Tenant #{shortTenantId(row.tenantId)} · Owner: {row.ownerEmail ?? 'Chưa có owner'}
          </div>
        </div>
      </TableCell>
      <TableCell>
        <div className="min-w-0 space-y-1">
          <GmailConnectionBadge status={row.gmailConnectionStatus} />
          <div className="truncate text-xs text-muted-foreground">
            {row.gmailAccountEmail ?? 'Chưa kết nối Gmail'}
            {extraGmailCount > 0 ? ` · +${extraGmailCount} Gmail` : ''}
          </div>
        </div>
      </TableCell>
      <TableCell>
        <TelegramStatusBadge status={row.telegramStatus} compact />
      </TableCell>
      <TableCell>
        <span className="text-sm text-ink">{activityKindLabel(row.lastActivityKind)}</span>
      </TableCell>
      <TableCell className="text-right">
        <Link
          to="/tenants/$tenantId"
          params={{ tenantId: row.tenantId }}
          search={{ tab: 'activity' }}
          aria-label={`Chi tiết ${row.tenantDisplayName}`}
          className={buttonVariants({ variant: 'link', size: 'sm', className: 'text-primary' })}
        >
          Chi tiết
        </Link>
      </TableCell>
    </TableRow>
  );
}

function shortTenantId(tenantId: string): string {
  return tenantId.slice(0, 8);
}

type TenantDashboardStats = {
  totalCount: number;
  activeCount: number;
  pausedCount: number;
  gmailConnectedCount: number;
  disconnectedCount: number;
  activeLast24hCount: number;
  activeLast7dCount: number;
  gmailConnectionRate: number;
  active24hRate: number;
  dailyActiveSeries: Array<{ label: string; value: number }>;
};

function TenantSummaryCards({
  stats,
  isLoading,
}: {
  stats: TenantDashboardStats;
  isLoading: boolean;
}) {
  const loadingValue = isLoading ? '-' : '0';
  return (
    <div className="grid min-w-0 gap-3 xl:grid-cols-3">
      <DashboardMetricCard
        testId="tenant-kpi-total"
        label="Tổng khách"
        value={isLoading ? loadingValue : formatInteger(stats.totalCount)}
        hint="+1 so với 7 ngày trước"
        icon={<UsersIcon className="size-5" />}
        tone="violet"
      />
      <DashboardMetricCard
        testId="tenant-kpi-active"
        label="Gmail đã kết nối"
        value={isLoading ? loadingValue : formatInteger(stats.gmailConnectedCount)}
        hint={`${formatPercent(stats.gmailConnectionRate)} tổng khách`}
        icon={<MailIcon className="size-5" />}
        tone="green"
      />
      <DashboardMetricCard
        testId="tenant-kpi-recent"
        label="Hoạt động 7 ngày"
        value={isLoading ? loadingValue : formatInteger(stats.activeLast7dCount)}
        hint={`${formatInteger(stats.activeLast24hCount)} trong 24h | ${formatPercent(stats.active24hRate)} tổng khách`}
        icon={<ActivityIcon className="size-5" />}
        tone="violet"
      />
    </div>
  );
}

function DashboardMetricCard({
  label,
  value,
  hint,
  icon,
  tone,
  testId,
}: {
  label: string;
  value: ReactNode;
  hint: string;
  icon: ReactNode;
  tone: 'green' | 'violet';
  testId: string;
}) {
  const iconClassName =
    tone === 'green' ? 'bg-green-soft text-green' : 'bg-violet-soft text-primary';
  return (
    <Card data-testid={testId} className="min-h-28 px-5 py-4">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="text-xs font-semibold uppercase text-muted-foreground">{label}</div>
          <div className="mt-2 text-3xl font-semibold leading-none text-ink tabular-nums">{value}</div>
          <div className="mt-3 flex items-center gap-1.5 text-xs text-muted-foreground">
            <span className="inline-flex size-3 items-center justify-center rounded-full bg-green text-[9px] font-bold text-primary-foreground">
              +
            </span>
            <span>{hint}</span>
          </div>
        </div>
        <div className={`flex size-11 shrink-0 items-center justify-center rounded-lg ${iconClassName}`}>{icon}</div>
      </div>
    </Card>
  );
}

function TenantInsightsGrid({ stats }: { stats: TenantDashboardStats }) {
  return (
    <div className="grid min-w-0 gap-3 lg:grid-cols-[minmax(0,1.4fr)_minmax(340px,0.8fr)]">
      <ChartCard title="Khách hoạt động theo ngày">
        <LineActivityChart series={stats.dailyActiveSeries} />
        <div className="mt-2 text-xs text-muted-foreground">30 ngày qua</div>
      </ChartCard>
      <ChartCard title="Phân bố khách hàng theo trạng thái tenant">
        <StatusDistribution stats={stats} />
      </ChartCard>
    </div>
  );
}

function ChartCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card className="min-h-[245px] px-4 py-3">
      <div className="mb-3 flex items-center justify-between gap-2">
        <CardTitle className="truncate text-sm font-semibold text-ink">{title}</CardTitle>
        <InfoIcon className="size-4 shrink-0 text-muted-foreground" />
      </div>
      {children}
    </Card>
  );
}

function LineActivityChart({ series }: { series: TenantDashboardStats['dailyActiveSeries'] }) {
  const maxValue = Math.max(1, ...series.map((point) => point.value));
  const width = 320;
  const height = 142;
  const paddingX = 16;
  const paddingTop = 12;
  const paddingBottom = 24;
  const plotWidth = width - paddingX * 2;
  const plotHeight = height - paddingTop - paddingBottom;
  const points = series.map((point, index) => {
    const x = paddingX + (plotWidth * index) / Math.max(1, series.length - 1);
    const y = paddingTop + plotHeight - (point.value / maxValue) * plotHeight;
    return { ...point, x, y };
  });
  const path = points.map((point) => `${point.x},${point.y}`).join(' ');

  return (
    <div className="min-w-0">
      <div className="mb-1 flex items-center justify-center gap-2 text-xs text-muted-foreground">
        <span className="h-0.5 w-6 rounded-full bg-primary" />
        <span>Số khách hoạt động</span>
      </div>
      <svg viewBox={`0 0 ${width} ${height}`} className="h-40 w-full overflow-visible" role="img" aria-label="Khách hoạt động theo ngày">
        {[0, 1, 2, 3].map((lineIndex) => {
          const y = paddingTop + (plotHeight * lineIndex) / 3;
          return <line key={lineIndex} x1={paddingX} x2={width - paddingX} y1={y} y2={y} className="stroke-border" />;
        })}
        <polyline points={path} fill="none" className="stroke-primary" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
        {points.map((point) => (
          <circle key={point.label} cx={point.x} cy={point.y} r="3.5" className="fill-primary" />
        ))}
        {points
          .filter((_, index) => index % 2 === 0 || index === points.length - 1)
          .map((point) => (
            <text key={`label-${point.label}`} x={point.x} y={height - 4} textAnchor="middle" className="fill-muted-foreground text-[10px]">
              {point.label}
            </text>
          ))}
      </svg>
    </div>
  );
}

function StatusDistribution({ stats }: { stats: TenantDashboardStats }) {
  const total = Math.max(1, stats.totalCount);
  return (
    <div className="space-y-5 pt-4">
      <DistributionRow label="Hoạt động" value={stats.activeCount} total={total} tone="green" />
      <DistributionRow label="Tạm dừng" value={stats.pausedCount} total={total} tone="violet" />
      <DistributionRow label="Ngắt kết nối" value={stats.disconnectedCount} total={total} tone="destructive" />
      <div className="flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
        <span className="inline-flex items-center gap-2"><span className="size-3 rounded-sm bg-green" /> Hoạt động</span>
        <span className="inline-flex items-center gap-2"><span className="size-3 rounded-sm bg-primary" /> Tạm dừng</span>
        <span className="inline-flex items-center gap-2"><span className="size-3 rounded-sm bg-destructive" /> Ngắt kết nối</span>
      </div>
    </div>
  );
}

function DistributionRow({
  label,
  value,
  total,
  tone,
}: {
  label: string;
  value: number;
  total: number;
  tone: 'green' | 'violet' | 'destructive';
}) {
  const valuePct = percentOf(value, total);
  const toneClassName: Record<'green' | 'violet' | 'destructive', string> = {
    green: 'bg-green',
    violet: 'bg-primary',
    destructive: 'bg-destructive',
  };
  return (
    <div className="grid grid-cols-[96px_1fr_28px] items-center gap-3 text-sm">
      <div className="truncate text-ink">{label}</div>
      <div className="flex h-7 overflow-hidden rounded-sm bg-muted">
        {value > 0 && <div className={toneClassName[tone]} style={{ width: `${valuePct}%` }} />}
      </div>
      <div className="text-right text-xs font-semibold text-ink tabular-nums">{formatInteger(value)}</div>
    </div>
  );
}

function DateRangeField({
  preset,
  from,
  to,
  onPresetChange,
  onFromChange,
  onToChange,
}: {
  preset: TenantDatePreset;
  from: string;
  to: string;
  onPresetChange: (value: TenantDatePreset) => void;
  onFromChange: (value: string) => void;
  onToChange: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [popoverView, setPopoverView] = useState<'options' | 'calendar'>('options');
  const selectedRange: DateRange = {
    from: parseDateInputValue(from),
    to: parseDateInputValue(to),
  };

  function choosePreset(nextPreset: TenantDatePreset) {
    onPresetChange(nextPreset);
    if (nextPreset === 'custom') {
      setPopoverView('calendar');
    } else {
      setPopoverView('options');
      setOpen(false);
    }
  }

  function selectRange(nextRange: DateRange | undefined) {
    if (nextRange?.from) {
      onFromChange(formatDateInputValue(nextRange.from));
    }
    if (nextRange?.to) {
      onToChange(formatDateInputValue(nextRange.to));
      setPopoverView('options');
      setOpen(false);
    }
  }

  return (
    <div className="space-y-2">
      <Label>Khoảng ngày</Label>
      <Popover
        open={open}
        onOpenChange={(nextOpen) => {
          setOpen(nextOpen);
          if (!nextOpen) {
            setPopoverView('options');
          }
        }}
      >
        <PopoverTrigger
          render={
            <Button
              type="button"
              variant="outline"
              className="h-8 w-full justify-between gap-2 px-2.5 text-left font-normal"
              data-testid="tenant-date-preset"
              role="combobox"
              aria-expanded={open}
            />
          }
        >
          <span className="flex min-w-0 items-center gap-2">
            <CalendarDaysIcon className="size-4 shrink-0 text-muted-foreground" />
            <span className="truncate">
              {preset === 'custom'
                ? `Tùy chọn · ${formatDisplayDate(from)} - ${formatDisplayDate(to)}`
                : datePresetLabel(preset)}
            </span>
          </span>
          <ChevronDownIcon className="size-4 shrink-0 text-muted-foreground" />
        </PopoverTrigger>
        <PopoverContent
          align={popoverView === 'calendar' ? 'end' : 'start'}
          side="bottom"
          className={
            popoverView === 'calendar'
              ? 'max-h-[min(440px,calc(100vh-2rem))] w-auto max-w-[calc(100vw-2rem)] overflow-auto p-2'
              : 'w-[min(350px,calc(100vw-2rem))] p-2'
          }
        >
          {popoverView === 'options' ? (
            <div className="grid gap-1" role="listbox" aria-label="Khoảng ngày">
              <DatePresetOption active={preset === '7d'} onClick={() => choosePreset('7d')}>
                7 ngày qua
              </DatePresetOption>
              <DatePresetOption active={preset === '30d'} onClick={() => choosePreset('30d')}>
                30 ngày qua
              </DatePresetOption>
              <DatePresetOption active={preset === '3m'} onClick={() => choosePreset('3m')}>
                3 tháng qua
              </DatePresetOption>
              <DatePresetOption active={preset === 'custom'} onClick={() => choosePreset('custom')}>
                Tùy chọn
              </DatePresetOption>
            </div>
          ) : (
            <div data-testid="tenant-date-range">
              <Calendar
                mode="range"
                selected={selectedRange}
                onSelect={selectRange}
                numberOfMonths={2}
                captionLayout="dropdown"
              />
            </div>
          )}
        </PopoverContent>
      </Popover>
    </div>
  );
}

function DatePresetOption({
  active,
  children,
  onClick,
}: {
  active: boolean;
  children: ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="option"
      aria-selected={active}
      className="flex h-8 w-full items-center justify-between rounded-md px-2 text-left text-sm hover:bg-accent hover:text-accent-foreground aria-selected:bg-accent aria-selected:text-accent-foreground"
      onClick={onClick}
    >
      <span>{children}</span>
      {active && <CheckCircle2Icon className="size-4" />}
    </button>
  );
}

function GmailConnectionBadge({ status }: { status: TenantListRow['gmailConnectionStatus'] }) {
  if (status === 'CONNECTED') {
    return <Badge variant="outline">Đã kết nối</Badge>;
  }
  return <Badge variant="secondary">Đã ngắt kết nối</Badge>;
}

function TelegramStatusBadge({
  status,
  compact = false,
}: {
  status: TenantListRow['telegramStatus'];
  compact?: boolean;
}) {
  if (status === 'CONNECTED') {
    return <Badge variant="outline">{compact ? 'Đã kết nối' : 'Telegram đã kết nối'}</Badge>;
  }
  if (status === 'BLOCKED') {
    return <Badge variant="destructive">Bị chặn</Badge>;
  }
  if (status === 'DISCONNECTED') {
    return <Badge variant="secondary">Đã ngắt kết nối</Badge>;
  }
  return <Badge variant="secondary">Chưa kết nối</Badge>;
}

function buildTenantDashboardStats(
  summary: TenantListResponse['summary'] | undefined,
  rows: TenantListRow[],
  from: string,
  to: string,
): TenantDashboardStats {
  const totalCount = summary?.totalCount ?? rows.length;
  const activeCount = summary?.activeCount ?? rows.filter((row) => row.status === 'ACTIVE').length;
  const pausedCount = summary?.pausedCount ?? rows.filter((row) => row.status === 'PAUSED').length;
  const gmailConnectedCount =
    summary?.gmailConnectedCount ?? rows.filter((row) => row.gmailConnectionStatus === 'CONNECTED').length;
  const disconnectedCount =
    summary?.disconnectedCount ?? rows.filter((row) => row.status === 'DISCONNECTED').length;
  const activeLast24hCount = summary?.activeLast24hCount ?? countRecentRows(rows, 'lastActivityAt', 1, to);
  const activeLast7dCount = summary?.activeLast7dCount ?? countRecentRows(rows, 'lastActivityAt', 7, to);

  return {
    totalCount,
    activeCount,
    pausedCount,
    gmailConnectedCount,
    disconnectedCount,
    activeLast24hCount,
    activeLast7dCount,
    gmailConnectionRate: percentOf(gmailConnectedCount, totalCount),
    active24hRate: percentOf(activeLast24hCount, totalCount),
    dailyActiveSeries: buildDailyActiveSeries(rows, from, to),
  };
}

function buildDailyActiveSeries(rows: TenantListRow[], from: string, to: string): TenantDashboardStats['dailyActiveSeries'] {
  const endDate = parseDateInputValue(to) ?? new Date();
  endDate.setHours(23, 59, 59, 999);
  const startDate = parseDateInputValue(from) ?? new Date(endDate);
  const windowStart = new Date(endDate);
  windowStart.setDate(windowStart.getDate() - 29);
  const seriesStart = startDate > windowStart ? startDate : windowStart;
  const bucketCount = 8;
  const windowMs = Math.max(1, endDate.getTime() - seriesStart.getTime());
  const bucketMs = windowMs / bucketCount;

  return Array.from({ length: bucketCount }, (_, index) => {
    const bucketStart = new Date(seriesStart.getTime() + bucketMs * index);
    const bucketEnd = new Date(seriesStart.getTime() + bucketMs * (index + 1));
    const value = rows.filter((row) => {
      const lastActivityAt = Date.parse(row.lastActivityAt);
      return Number.isFinite(lastActivityAt) && lastActivityAt >= bucketStart.getTime() && lastActivityAt < bucketEnd.getTime();
    }).length;
    return {
      label: formatShortDate(bucketStart),
      value,
    };
  });
}

function countRecentRows(rows: TenantListRow[], field: 'lastActivityAt', days: number, to: string): number {
  const endDate = parseDateInputValue(to) ?? new Date();
  endDate.setHours(23, 59, 59, 999);
  const startDate = new Date(endDate);
  startDate.setDate(startDate.getDate() - days);
  return rows.filter((row) => {
    const timestamp = Date.parse(row[field]);
    return Number.isFinite(timestamp) && timestamp >= startDate.getTime() && timestamp <= endDate.getTime();
  }).length;
}

function percentOf(value: number, total: number): number {
  if (total <= 0) {
    return 0;
  }
  return Math.round((value / total) * 100);
}

function formatPercent(value: number): string {
  return `${formatInteger(value)}%`;
}

function formatShortDate(value: Date): string {
  const day = String(value.getDate()).padStart(2, '0');
  const month = String(value.getMonth() + 1).padStart(2, '0');
  return `${day}/${month}`;
}

function getDefaultTenantDateRange(referenceDate = new Date()): TenantDateRange {
  return getPresetDateRange('3m', referenceDate);
}

function getPresetDateRange(datePreset: Exclude<TenantDatePreset, 'custom'>, referenceDate = new Date()): TenantDateRange {
  const endDate = new Date(referenceDate.getFullYear(), referenceDate.getMonth(), referenceDate.getDate());
  const startDate = new Date(endDate);
  if (datePreset === '7d') {
    startDate.setDate(startDate.getDate() - 7);
  } else if (datePreset === '30d') {
    startDate.setDate(startDate.getDate() - 30);
  } else {
    startDate.setMonth(startDate.getMonth() - 3);
  }
  return {
    from: formatDateInputValue(startDate),
    to: formatDateInputValue(endDate),
  };
}

function inferDatePreset(from: string, to: string): TenantDatePreset {
  for (const datePreset of ['7d', '30d', '3m'] as const) {
    const dateRange = getPresetDateRange(datePreset);
    if (dateRange.from === from && dateRange.to === to) {
      return datePreset;
    }
  }
  return 'custom';
}

function formatDateInputValue(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function parseDateInputValue(value: string): Date | undefined {
  if (!value) {
    return undefined;
  }
  const [year, month, day] = value.split('-').map(Number);
  if (!year || !month || !day) {
    return undefined;
  }
  return new Date(year, month - 1, day);
}

function formatDisplayDate(value: string): string {
  const date = parseDateInputValue(value);
  if (!date) {
    return value;
  }
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  return `${day}/${month}/${date.getFullYear()}`;
}

function formatInteger(value: number): string {
  return integerFormatter.format(value);
}

function tenantStatusFilterLabel(status: TenantStatusFilter): string {
  const labelByStatus: Record<TenantStatusFilter, string> = {
    ALL: 'Tất cả',
    ACTIVE: 'Tenant hoạt động',
    PAUSED: 'Tạm dừng',
    DISCONNECTED: 'Đã ngắt kết nối',
  };
  return labelByStatus[status];
}

function datePresetLabel(datePreset: TenantDatePreset): string {
  const labelByDatePreset: Record<TenantDatePreset, string> = {
    '7d': '7 ngày qua',
    '30d': '30 ngày qua',
    '3m': '3 tháng qua',
    custom: 'Tùy chọn',
  };
  return labelByDatePreset[datePreset];
}

function activityKindLabel(kind: TenantListRow['lastActivityKind']): string {
  const labelByKind: Record<TenantListRow['lastActivityKind'], string> = {
    TENANT_CREATED: 'Tạo khách hàng',
    GMAIL_CONNECTION: 'Gmail connection',
    RULE: 'Rule update',
    GMAIL_OBSERVED: 'Gmail observed',
    TRIAGE: 'Triage',
    CHAT: 'Chat',
    TELEGRAM: 'Telegram',
    ASSISTANT_ACTION: 'Assistant action',
    LLM: 'LLM call',
  };
  return labelByKind[kind];
}
