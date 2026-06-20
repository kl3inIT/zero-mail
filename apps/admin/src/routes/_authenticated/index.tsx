import { createFileRoute, Link } from '@tanstack/react-router';
import {
  ActivityIcon,
  BellIcon,
  BrainCircuitIcon,
  CalendarIcon,
  ClipboardListIcon,
  DatabaseIcon,
  DollarSignIcon,
  EyeIcon,
  InboxIcon,
  KeyRoundIcon,
  MailIcon,
  ServerCogIcon,
  ShieldAlertIcon,
  UsersIcon,
} from 'lucide-react';
import type { ChangeEvent, ReactNode } from 'react';
import { useMemo, useState } from 'react';

import { KpiCard } from '@/components/KpiCard';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Input } from '@/components/ui/input';
import type {
  AdminOverviewActionDistribution,
  AdminOverviewAlert,
  AdminOverviewResponse,
  AdminOverviewTopActivityTenant,
  AdminOverviewTopSpendTenant,
} from '@/features/overview/overview-api';
import { useAdminOverview } from '@/features/overview/use-admin-overview';

export const Route = createFileRoute('/_authenticated/')({
  component: DashboardRoute,
});

const integerFormatter = new Intl.NumberFormat();
const percentFormatter = new Intl.NumberFormat(undefined, {
  maximumFractionDigits: 1,
  minimumFractionDigits: 0,
});

type DashboardRange = {
  selectedDateInput: string;
  fromDate: Date;
  toDisplayDate: Date;
  toExclusiveDate: Date;
};

type DailyActivityPoint = {
  label: string;
  observed: number;
  triaged: number;
  failed: number;
};

type ActionDistributionItem = {
  key: string;
  label: string;
  value: number;
  className: string;
};

function DashboardRoute() {
  const [selectedDateInput, setSelectedDateInput] = useState(() => formatDateInputValue(new Date()));
  const dashboardRange = useMemo(() => getDashboardRange(selectedDateInput), [selectedDateInput]);
  const overviewQuery = useAdminOverview(
    {
      from: dashboardRange.fromDate,
      to: dashboardRange.toExclusiveDate,
    },
    { paused: false },
  );
  const stats = useMemo(
    () => buildDashboardStats(overviewQuery.data, dashboardRange),
    [overviewQuery.data, dashboardRange],
  );
  const handleDashboardDateChange = (event: ChangeEvent<HTMLInputElement>) => {
    setSelectedDateInput(event.target.value);
  };

  return (
    <div className="min-w-0 space-y-4">
      <header className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
        <div className="min-w-0">
          <h1 className="text-2xl font-semibold text-ink">Dashboard tổng quan</h1>
        </div>
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <div className="relative min-w-[160px]">
            <CalendarIcon className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              aria-label="Chọn ngày dashboard"
              type="date"
              value={dashboardRange.selectedDateInput}
              onChange={handleDashboardDateChange}
              className="pl-8"
            />
          </div>
        </div>
      </header>

      <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-6">
        <KpiCard
          testId="overview-kpi-tenants"
          label="Tổng tenant"
          value={formatInteger(stats.totalTenants)}
          hint="Tất cả tenant trong hệ thống"
          sparkline={<KpiIcon icon={<UsersIcon className="size-5" />} />}
        />
        <KpiCard
          testId="overview-kpi-gmail-connected"
          label="Gmail đã kết nối"
          value={formatInteger(stats.gmailConnectedTenants)}
          hint={`${formatPercent(stats.gmailConnectedRate)} tổng tenant`}
          sparkline={<KpiIcon icon={<MailIcon className="size-5" />} />}
        />
        <KpiCard
          testId="overview-kpi-active-tenants"
          label="Tenant hoạt động 7 ngày"
          value={formatInteger(stats.activeLast7dTenants)}
          hint={`${formatPercent(stats.activeLast7dRate)} tổng tenant`}
          sparkline={<KpiIcon icon={<ActivityIcon className="size-5" />} />}
        />
        <KpiCard
          testId="overview-kpi-observed"
          label="Email quan sát"
          value={formatInteger(stats.observedEmailCount)}
          hint="Trong khoảng đã chọn"
          sparkline={<KpiIcon icon={<EyeIcon className="size-5" />} />}
        />
        <KpiCard
          testId="overview-kpi-triage"
          label="Triage actions"
          value={formatInteger(stats.triageActionCount)}
          hint={`${formatInteger(stats.failedTriageCount)} lỗi, ${formatInteger(stats.blockedOutboundCount)} blocked`}
          sparkline={<KpiIcon icon={<BrainCircuitIcon className="size-5" />} />}
        />
        <KpiCard
          testId="overview-kpi-spend"
          label="Credits LLM 7 ngày"
          value={formatInteger(stats.llmChargedCredits)}
          hint={`${formatInteger(stats.llmCallCount)} LLM calls`}
          sparkline={<KpiIcon icon={<DollarSignIcon className="size-5" />} />}
        />
      </section>

      <section className="grid min-w-0 gap-3 xl:grid-cols-[minmax(0,1.25fr)_minmax(420px,0.8fr)]">
        <ChartCard title="Hoạt động theo ngày">
          <DailyActivityChart points={stats.dailyActivity} />
        </ChartCard>
        <div className="grid gap-3">
          <ChartCard title="Tỷ lệ triage thành công">
            <SuccessRateChart points={stats.dailyActivity} fallbackRate={stats.triageSuccessRate} />
          </ChartCard>
          <ChartCard title="Phân bổ hành động">
            <ActionDistribution stats={stats} />
          </ChartCard>
        </div>
      </section>

      <section className="grid min-w-0 gap-3 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(360px,0.9fr)]">
        <DashboardTableCard title="Top tenant theo hoạt động" linkTo="/tenants">
          <TenantActivityTable rows={stats.topActivityTenants} />
        </DashboardTableCard>
        <DashboardTableCard title="Top tenant theo credits" linkTo="/spend">
          <TenantSpendTable rows={stats.topSpendTenants} />
        </DashboardTableCard>
        <SystemAlerts alerts={stats.alerts} />
      </section>

      <QuickActions />
    </div>
  );
}

function KpiIcon({ icon }: { icon: ReactNode }) {
  return (
    <div className="flex size-10 items-center justify-center rounded-xl bg-violet-soft text-primary">
      {icon}
    </div>
  );
}

function ChartCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card className="min-h-[235px]">
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle role="heading" aria-level={2}>{title}</CardTitle>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

function DailyActivityChart({ points }: { points: DailyActivityPoint[] }) {
  const maxValue = Math.max(1, ...points.flatMap((point) => [point.observed, point.triaged, point.failed]));
  return (
    <div className="min-w-0 space-y-4">
      <div className="flex flex-wrap items-center gap-4 text-xs text-muted-foreground">
        <LegendItem className="bg-primary" label="Email quan sát" />
        <LegendItem className="bg-green" label="Triage thành công" />
        <LegendItem className="bg-destructive" label="Triage lỗi" />
      </div>
      <div className="flex h-56 items-end gap-4 border-b border-border px-3 pt-4">
        {points.map((point) => (
          <div key={point.label} className="flex min-w-0 flex-1 flex-col items-center gap-2">
            <div className="flex h-44 w-full items-end justify-center gap-1">
              <Bar value={point.observed} maxValue={maxValue} className="bg-primary" />
              <Bar value={point.triaged} maxValue={maxValue} className="bg-green" />
              <Bar value={point.failed} maxValue={maxValue} className="bg-destructive" />
            </div>
            <div className="truncate text-xs text-muted-foreground">{point.label}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

function Bar({ value, maxValue, className }: { value: number; maxValue: number; className: string }) {
  return (
    <div
      className={`w-4 rounded-t-md ${className}`}
      title={formatInteger(value)}
      style={{ height: `${Math.max(value > 0 ? 5 : 0, (value / maxValue) * 100)}%` }}
    />
  );
}

function SuccessRateChart({
  points,
  fallbackRate,
}: {
  points: DailyActivityPoint[];
  fallbackRate: number;
}) {
  const width = 360;
  const height = 120;
  const paddingX = 18;
  const paddingY = 16;
  const plotWidth = width - paddingX * 2;
  const plotHeight = height - paddingY * 2;
  const chartPoints = points.map((point, index) => {
    const total = point.triaged + point.failed;
    const rate = total > 0 ? (point.triaged / total) * 100 : fallbackRate;
    const x = paddingX + (plotWidth * index) / Math.max(1, points.length - 1);
    const y = paddingY + plotHeight - ((rate - 85) / 15) * plotHeight;
    return { ...point, x, y: clamp(y, paddingY, height - paddingY), rate };
  });
  const polyline = chartPoints.map((point) => `${point.x},${point.y}`).join(' ');
  return (
    <div className="min-w-0">
      <div className="mb-2 text-xs text-muted-foreground">Tỷ lệ triage thành công trong khoảng đã chọn</div>
      <svg viewBox={`0 0 ${width} ${height}`} className="h-32 w-full overflow-visible" role="img" aria-label="Tỷ lệ triage thành công">
        {[0, 1, 2, 3].map((lineIndex) => {
          const y = paddingY + (plotHeight * lineIndex) / 3;
          return <line key={lineIndex} x1={paddingX} x2={width - paddingX} y1={y} y2={y} className="stroke-border" />;
        })}
        <polyline points={polyline} fill="none" className="stroke-primary" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
        {chartPoints.map((point) => (
          <g key={point.label}>
            <circle cx={point.x} cy={point.y} r="3.5" className="fill-primary" />
            <text x={point.x} y={height - 2} textAnchor="middle" className="fill-muted-foreground text-[10px]">
              {point.label}
            </text>
          </g>
        ))}
      </svg>
    </div>
  );
}

function ActionDistribution({ stats }: { stats: DashboardStats }) {
  const total = Math.max(1, stats.actionDistribution.reduce((sum, slice) => sum + slice.value, 0));
  const ringBackgroundImage = buildActionDistributionGradient(stats.actionDistribution);
  return (
    <div className="grid gap-4 md:grid-cols-[160px_1fr] md:items-center xl:grid-cols-1 2xl:grid-cols-[160px_1fr]">
      <div
        className="relative mx-auto flex size-36 items-center justify-center rounded-full bg-secondary"
        style={{ backgroundImage: ringBackgroundImage }}
      >
        <div className="absolute inset-5 rounded-full bg-card" />
        <div className="relative text-center">
          <div className="text-2xl font-semibold text-ink tabular-nums">{formatInteger(total)}</div>
          <div className="text-xs text-muted-foreground">Tổng</div>
        </div>
      </div>
      <div className="space-y-2">
        {stats.actionDistribution.map((slice) => (
          <div key={slice.key} className="grid grid-cols-[1fr_auto] items-center gap-3 text-sm">
            <div className="flex min-w-0 items-center gap-2">
              <span className={`size-2.5 shrink-0 rounded-full ${slice.className}`} />
              <span className="truncate text-ink">{slice.label}</span>
            </div>
            <span className="text-xs text-muted-foreground tabular-nums">
              {formatInteger(slice.value)} ({formatPercent((slice.value / total) * 100)})
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function DashboardTableCard({
  title,
  linkTo,
  children,
}: {
  title: string;
  linkTo: '/tenants' | '/spend';
  children: ReactNode;
}) {
  return (
    <Card className="min-w-0">
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle role="heading" aria-level={2}>{title}</CardTitle>
        <Link to={linkTo} className="text-xs font-medium text-primary hover:underline">
          Xem tất cả
        </Link>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

function TenantActivityTable({ rows }: { rows: AdminOverviewTopActivityTenant[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Tenant</TableHead>
          <TableHead className="text-right">Email quan sát</TableHead>
          <TableHead className="text-right">Triage actions</TableHead>
          <TableHead className="text-right">Tỷ lệ lỗi</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.tenantId}>
            <TableCell>
              <div className="font-medium text-ink">{row.tenantDisplayName}</div>
              <div className="text-xs text-muted-foreground">{row.primaryEmail ?? row.ownerEmail ?? 'Chưa có Gmail'}</div>
            </TableCell>
            <TableCell className="text-right tabular-nums">{formatInteger(row.observedEmailCount)}</TableCell>
            <TableCell className="text-right tabular-nums">{formatInteger(row.triageActionCount)}</TableCell>
            <TableCell className="text-right">
              <Badge variant={row.failureRatePercent >= 2 ? 'destructive' : 'secondary'}>
                {formatPercent(row.failureRatePercent)}
              </Badge>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function TenantSpendTable({ rows }: { rows: AdminOverviewTopSpendTenant[] }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Tenant</TableHead>
          <TableHead className="text-right">LLM calls</TableHead>
          <TableHead className="text-right">Credits</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => (
          <TableRow key={row.tenantId}>
            <TableCell>
              <div className="font-medium text-ink">{row.tenantDisplayName}</div>
              <div className="text-xs text-muted-foreground">{row.primaryEmail ?? row.ownerEmail ?? 'Chưa có Gmail'}</div>
            </TableCell>
            <TableCell className="text-right tabular-nums">{formatInteger(row.llmCallCount)}</TableCell>
            <TableCell className="text-right tabular-nums">{formatInteger(row.chargedCredits)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function SystemAlerts({ alerts }: { alerts: DashboardAlert[] }) {
  return (
    <Card className="min-w-0">
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle role="heading" aria-level={2}>Cảnh báo hệ thống</CardTitle>
        <Link to="/queue" className="text-xs font-medium text-primary hover:underline">
          Xem tất cả
        </Link>
      </CardHeader>
      <CardContent className="space-y-3">
        {alerts.map((alert) => (
          <div key={alert.key} className="flex items-start gap-3 rounded-lg border border-border/70 p-3">
            <span className={`mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full ${alert.className}`}>
              {alert.icon}
            </span>
            <div className="min-w-0 flex-1">
              <div className="text-sm font-medium text-ink">{alert.title}</div>
              <div className="text-xs text-muted-foreground">{alert.detail}</div>
            </div>
            <div className="text-xs text-muted-foreground">{alert.timeLabel}</div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function QuickActions() {
  const actions = [
    { to: '/role-grants', label: 'Thêm admin', icon: <UsersIcon className="size-6" /> },
    { to: '/tenants', label: 'Xem khách hàng', icon: <DatabaseIcon className="size-6" /> },
    { to: '/queue', label: 'Xem hàng đợi', icon: <ServerCogIcon className="size-6" /> },
    { to: '/spend', label: 'Xem chi phí', icon: <DollarSignIcon className="size-6" /> },
    { to: '/audit', label: 'Nhật ký audit', icon: <ClipboardListIcon className="size-6" /> },
    { to: '/master-keys', label: 'Quản lý LLM', icon: <KeyRoundIcon className="size-6" /> },
  ] as const;
  return (
    <Card>
      <CardHeader>
        <CardTitle role="heading" aria-level={2}>Thao tác nhanh</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-3 md:grid-cols-2 xl:grid-cols-6">
        {actions.map((action) => (
          <Link
            key={action.to}
            to={action.to}
            className="flex min-h-20 items-center justify-center gap-3 rounded-lg border border-border bg-card px-4 text-sm font-medium text-ink transition-colors hover:bg-secondary/70"
          >
            <span className="text-primary">{action.icon}</span>
            {action.label}
          </Link>
        ))}
      </CardContent>
    </Card>
  );
}

type DashboardStats = {
  totalTenants: number;
  gmailConnectedTenants: number;
  gmailConnectedRate: number;
  activeLast7dTenants: number;
  activeLast7dRate: number;
  observedEmailCount: number;
  triageActionCount: number;
  failedTriageCount: number;
  blockedOutboundCount: number;
  triageSuccessRate: number;
  llmChargedCredits: number;
  llmCallCount: number;
  activeAlertCount: number;
  dailyActivity: DailyActivityPoint[];
  actionDistribution: ActionDistributionItem[];
  topActivityTenants: AdminOverviewTopActivityTenant[];
  topSpendTenants: AdminOverviewTopSpendTenant[];
  alerts: DashboardAlert[];
};

type DashboardAlert = {
  key: string;
  title: string;
  detail: string;
  count: number;
  timeLabel: string;
  className: string;
  icon: ReactNode;
};

function buildDashboardStats(
  overview: AdminOverviewResponse | undefined,
  range: DashboardRange,
): DashboardStats {
  const kpis = overview?.kpis;
  const totalTenants = kpis?.totalTenants ?? 0;
  const gmailConnectedTenants = kpis?.gmailConnectedTenants ?? 0;
  const activeLast7dTenants = kpis?.activeLast7dTenants ?? 0;
  const alerts = buildAlerts(overview?.alerts ?? []);

  return {
    totalTenants,
    gmailConnectedTenants,
    gmailConnectedRate: percentOf(gmailConnectedTenants, totalTenants),
    activeLast7dTenants,
    activeLast7dRate: percentOf(activeLast7dTenants, totalTenants),
    observedEmailCount: kpis?.observedEmailCount ?? 0,
    triageActionCount: kpis?.triageActionCount ?? 0,
    failedTriageCount: kpis?.failedTriageActionCount ?? 0,
    blockedOutboundCount: kpis?.blockedOutboundActionCount ?? 0,
    triageSuccessRate: overview?.successRate.successRatePercent ?? 0,
    llmChargedCredits: kpis?.llmChargedCredits ?? 0,
    llmCallCount: kpis?.llmCallCount ?? 0,
    activeAlertCount: alerts.filter((alert) => alert.count > 0).length,
    dailyActivity: buildDailyActivity(overview, range),
    actionDistribution: buildActionDistribution(overview?.actionDistribution ?? []),
    topActivityTenants: overview?.topActivityTenants ?? [],
    topSpendTenants: overview?.topSpendTenants ?? [],
    alerts,
  };
}

function buildDailyActivity(
  overview: AdminOverviewResponse | undefined,
  range: DashboardRange,
): DailyActivityPoint[] {
  const rows = overview?.dailyActivity ?? [];
  if (rows.length === 0) {
    return eachDay(range.fromDate, range.toDisplayDate).map((day) => ({
      label: formatShortDate(day),
      observed: 0,
      triaged: 0,
      failed: 0,
    }));
  }
  return rows.map((point) => ({
    label: formatApiDateLabel(point.date),
    observed: point.observedEmailCount,
    triaged: point.triageActionCount - point.failedTriageActionCount,
    failed: point.failedTriageActionCount,
  }));
}

function buildActionDistribution(rows: AdminOverviewActionDistribution[]): ActionDistributionItem[] {
  const sourceRows =
    rows.length > 0
      ? rows
      : [
          { key: 'OBSERVED_EMAIL', label: 'Email quan sát', count: 0 },
          { key: 'TRIAGE_ACTION', label: 'Triage thành công', count: 0 },
          { key: 'OUTBOUND_ACTION', label: 'Outbound actions', count: 0 },
          { key: 'FAILED_OR_BLOCKED', label: 'Lỗi / Bị chặn', count: 0 },
        ];
  return sourceRows.map((row) => ({
    key: row.key,
    label: row.label,
    value: row.count,
    className: actionColor(row.key),
  }));
}

function buildAlerts(rows: AdminOverviewAlert[]): DashboardAlert[] {
  return rows.map((alert) => ({
    key: alert.key,
    title: alert.title,
    detail: alert.detail,
    timeLabel: alert.timeLabel,
    className: alertTone(alert),
    icon: alertIcon(alert.key),
    count: alert.count,
  }));
}

function LegendItem({ className, label }: { className: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-2">
      <span className={`size-2.5 rounded-full ${className}`} />
      {label}
    </span>
  );
}

function actionColor(key: string): string {
  switch (key) {
    case 'OBSERVED_EMAIL':
      return 'bg-primary';
    case 'TRIAGE_ACTION':
      return 'bg-green';
    case 'OUTBOUND_ACTION':
      return 'bg-blue';
    case 'FAILED_OR_BLOCKED':
      return 'bg-destructive';
    default:
      return 'bg-muted-foreground';
  }
}

function actionGradientColor(key: string): string {
  switch (key) {
    case 'OBSERVED_EMAIL':
      return 'var(--primary)';
    case 'TRIAGE_ACTION':
      return 'var(--green)';
    case 'OUTBOUND_ACTION':
      return 'var(--blue)';
    case 'FAILED_OR_BLOCKED':
      return 'var(--destructive)';
    default:
      return 'var(--muted-foreground)';
  }
}

function buildActionDistributionGradient(items: ActionDistributionItem[]): string | undefined {
  const total = items.reduce((sum, item) => sum + Math.max(0, item.value), 0);
  if (total <= 0) {
    return undefined;
  }
  let cursor = 0;
  const segments = items
    .filter((item) => item.value > 0)
    .map((item) => {
      const start = cursor;
      cursor += (item.value / total) * 100;
      return `${actionGradientColor(item.key)} ${start}% ${cursor}%`;
    });
  return `conic-gradient(${segments.join(', ')})`;
}

function alertTone(alert: AdminOverviewAlert): string {
  if (alert.severity === 'ERROR') {
    return 'bg-destructive/10 text-destructive';
  }
  if (alert.severity === 'WARNING') {
    return 'bg-amber-soft text-amber';
  }
  return 'bg-blue-soft text-blue';
}

function alertIcon(key: string): ReactNode {
  switch (key) {
    case 'GMAIL_UNHEALTHY':
    case 'OUTBOUND_BLOCKED':
      return <ShieldAlertIcon className="size-4" />;
    case 'PUBSUB_BACKLOG':
      return <InboxIcon className="size-4" />;
    case 'TRIAGE_FAILURE_RATE':
      return <ActivityIcon className="size-4" />;
    case 'LOW_CREDIT':
      return <DollarSignIcon className="size-4" />;
    case 'DEAD_LETTER':
      return <ServerCogIcon className="size-4" />;
    default:
      return <BellIcon className="size-4" />;
  }
}

function getDashboardRange(selectedDateInput: string): DashboardRange {
  const toDisplayDate = parseDateInputValue(selectedDateInput);
  const toExclusiveDate = new Date(toDisplayDate);
  toExclusiveDate.setDate(toExclusiveDate.getDate() + 1);
  const fromDate = new Date(toDisplayDate);
  fromDate.setDate(fromDate.getDate() - 6);
  return {
    selectedDateInput,
    fromDate,
    toDisplayDate,
    toExclusiveDate,
  };
}

function formatDateInputValue(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function parseDateInputValue(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  if (!year || !month || !day) {
    const today = new Date();
    return new Date(today.getFullYear(), today.getMonth(), today.getDate());
  }
  return new Date(year, month - 1, day);
}

function eachDay(fromDate: Date, toDate: Date): Date[] {
  const days: Date[] = [];
  const cursor = new Date(fromDate);
  while (cursor <= toDate) {
    days.push(new Date(cursor));
    cursor.setDate(cursor.getDate() + 1);
  }
  return days;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function percentOf(value: number, total: number): number {
  if (total <= 0) {
    return 0;
  }
  return (value / total) * 100;
}

function formatInteger(value: number): string {
  return integerFormatter.format(Math.round(value));
}

function formatPercent(value: number): string {
  return `${percentFormatter.format(value)}%`;
}

function formatShortDate(value: Date): string {
  const day = String(value.getDate()).padStart(2, '0');
  const month = String(value.getMonth() + 1).padStart(2, '0');
  return `${day}/${month}`;
}

function formatApiDateLabel(value: string): string {
  const [year, month, day] = value.split('-');
  if (!year || !month || !day) {
    return value;
  }
  return `${day}/${month}`;
}
