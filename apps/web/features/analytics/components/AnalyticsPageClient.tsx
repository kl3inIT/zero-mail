'use client';

import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import type { Route } from 'next';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useLocale, useTranslations } from 'next-intl';
import {
  Archive,
  Clock3,
  Calendar,
  Grid2X2,
  Mail,
  Send,
  TrendingUp,
  type LucideIcon,
} from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  XAxis,
  YAxis,
} from 'recharts';

import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from '@/components/ui/chart';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import type { AnalyticsWindow, DailyLoadResponse } from '@/features/analytics/api/analytics-api';
import { normalizeAnalyticsWindow } from '@/features/analytics/components/analytics-window';
import {
  formatCompactCount,
  formatTimeSaved,
  percentOf,
  safeCount,
  topDomainLoad,
} from '@/features/analytics/components/analytics-visualization';
import { useAnalyticsSummary } from '@/features/analytics/hooks/useAnalyticsSummary';
import { cn } from '@/lib/utils';

type SourceMode = 'email' | 'domain';
type GroupByMode = 'day' | 'week' | 'month' | 'year';

type SourceRow = {
  key: string;
  label: string;
  count: number;
};

type ActivityChartRow = {
  groupKey: string;
  day: string;
  archived: number;
  deleted: number;
};

export function AnalyticsPageClient() {
  const t = useTranslations();
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const rawWindow = searchParams.get('window');
  const selectedWindow = normalizeAnalyticsWindow(rawWindow);
  const summaryQuery = useAnalyticsSummary(selectedWindow);
  const previousSelectedWindowRef = useRef(selectedWindow);
  const [groupBy, setGroupBy] = useState<GroupByMode>('week');
  const [sourceMode, setSourceMode] = useState<SourceMode>('email');
  const [sourcesExpanded, setSourcesExpanded] = useState(false);

  useEffect(() => {
    if (previousSelectedWindowRef.current === selectedWindow) {
      return;
    }

    previousSelectedWindowRef.current = selectedWindow;
    if (rawWindow === selectedWindow) {
      void summaryQuery.refetch();
    }
  }, [rawWindow, selectedWindow, summaryQuery]);

  const observed = safeCount(summaryQuery.data.volumeObserved);
  const applied = safeCount(summaryQuery.data.volumeApplied);
  const untouched = Math.max(0, observed - applied);
  const triageRatio = percentOf(applied, observed);
  const triagePercent = Math.round(triageRatio * 100);
  const timeSaved = formatTimeSaved(summaryQuery.data.timeSavedSeconds);

  const dayFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat(locale === 'vi' ? 'vi-VN' : 'en-US', {
        day: '2-digit',
        month: '2-digit',
        timeZone: 'UTC',
      }),
    [locale],
  );

  const dailyLoadChartData = useMemo(() => {
    const dailyLoad = summaryQuery.data.dailyLoad ?? [];
    return dailyLoad.map((entry) => ({
      day: dayFormatter.format(new Date(entry.day)),
      observed: safeCount(entry.observed),
      applied: safeCount(entry.applied),
      reverted: safeCount(entry.reverted),
    }));
  }, [summaryQuery.data.dailyLoad, dayFormatter]);

  const archiveChartData = useMemo(() => {
    return groupDailyActivity(summaryQuery.data.dailyLoad ?? [], groupBy, locale);
  }, [groupBy, locale, summaryQuery.data.dailyLoad]);

  const triageDonutData = [
    { key: 'applied', value: applied },
    { key: 'untouched', value: untouched },
  ];

  const sourceRows = useMemo<SourceRow[]>(() => {
    if (sourceMode === 'domain') {
      return topDomainLoad(summaryQuery.data.domainLoad, summaryQuery.data.topSenders).map(
        (domain) => ({
          key: domain.domain,
          label: domain.domain,
          count: safeCount(domain.count),
        }),
      );
    }

    return (summaryQuery.data.topSenders ?? []).map((sender) => {
      const senderEmail = sender.senderEmail ?? '';
      return {
        key: senderEmail,
        label: senderEmail,
        count: safeCount(sender.count),
      };
    });
  }, [sourceMode, summaryQuery.data.domainLoad, summaryQuery.data.topSenders]);

  const visibleSourceRows = sourcesExpanded ? sourceRows : sourceRows.slice(0, 6);
  const maxSourceCount = Math.max(1, ...sourceRows.map((row) => row.count));

  const ruleChartData = useMemo(() => {
    const ruleHits = summaryQuery.data.ruleHits ?? [];
    return ruleHits
      .toSorted(
        (firstRule, secondRule) => safeCount(secondRule.applied) - safeCount(firstRule.applied),
      )
      .slice(0, 8)
      .map((rule) => ({
        name: (rule.ruleName ?? '').slice(0, 28) || '-',
        applied: safeCount(rule.applied),
        reverted: safeCount(rule.reverted),
      }));
  }, [summaryQuery.data.ruleHits]);

  const dailyLoadChartConfig: ChartConfig = {
    observed: {
      label: t('analytics.metadataLoad.observedLegend'),
      color: 'var(--chart-2)',
    },
    applied: {
      label: t('analytics.metadataLoad.appliedLegend'),
      color: 'var(--chart-1)',
    },
    reverted: {
      label: t('analytics.metadataLoad.revertedLegend'),
      color: 'var(--red)',
    },
  };

  const archiveChartConfig: ChartConfig = {
    archived: {
      label: t('analytics.archiveChart.archivedLegend'),
      color: 'var(--chart-1)',
    },
    deleted: {
      label: t('analytics.archiveChart.deletedLegend'),
      color: 'var(--chart-5)',
    },
  };

  const triageChartConfig: ChartConfig = {
    applied: {
      label: t('analytics.volume.appliedLabel'),
      color: 'var(--chart-1)',
    },
    untouched: {
      label: t('analytics.volume.untouchedLabel'),
      color: 'var(--muted-foreground)',
    },
  };

  const ruleChartConfig: ChartConfig = {
    applied: {
      label: t('analytics.ruleHits.column.applied'),
      color: 'var(--chart-1)',
    },
    reverted: {
      label: t('analytics.ruleHits.column.reverted'),
      color: 'var(--red)',
    },
  };

  const dailyLoadEmpty = dailyLoadChartData.length === 0;
  const archiveChartEmpty = archiveChartData.length === 0;
  const triageEmpty = observed === 0;
  const ruleEmpty = ruleChartData.length === 0;

  return (
    <div className="flex min-w-0 flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <AnalyticsToolbar
          selectedWindow={selectedWindow}
          groupBy={groupBy}
          onWindowChange={(nextWindow) => {
            const nextSearchParams = new URLSearchParams(searchParams.toString());
            nextSearchParams.set('window', nextWindow);
            router.replace(`${pathname}?${nextSearchParams.toString()}` as Route, {
              scroll: false,
            });
          }}
          onGroupByChange={setGroupBy}
        />
        <p className="text-muted-foreground text-xs tabular-nums sm:text-right">
          {t('analytics.page.lastRefreshed', { age: '0s' })}
        </p>
      </div>

      <div
        data-testid="analytics-volume-panel"
        className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4"
      >
        <MetricCard
          label={t('analytics.volume.observedLabel')}
          value={formatCompactCount(observed)}
          icon={Mail}
        />
        <MetricCard
          label={t('analytics.volume.appliedLabel')}
          value={formatCompactCount(applied)}
          icon={Archive}
        />
        <MetricCard
          data-testid="analytics-time-saved-panel"
          label={t('analytics.timeSaved.title')}
          value={timeSaved.label}
          icon={Clock3}
        />
        <MetricCard
          label={t('analytics.volume.triageRate')}
          value={`${triagePercent}%`}
          icon={TrendingUp}
        />
      </div>

      <div className="grid min-w-0 gap-4 lg:grid-cols-3">
        <PanelCard
          data-testid="analytics-metadata-load-panel"
          title={t('analytics.metadataLoad.dailyVolume')}
          className="lg:col-span-2"
        >
          {dailyLoadEmpty ? (
            <EmptyHint message={t('analytics.metadataLoad.empty')} />
          ) : (
            <ChartContainer config={dailyLoadChartConfig} className="h-[260px] w-full">
              <LineChart
                data={dailyLoadChartData}
                margin={{ top: 8, right: 8, left: -16, bottom: 0 }}
              >
                <CartesianGrid vertical={false} strokeDasharray="3 3" className="stroke-border" />
                <XAxis dataKey="day" tickLine={false} axisLine={false} fontSize={11} />
                <YAxis tickLine={false} axisLine={false} fontSize={11} width={32} />
                <ChartTooltip content={<ChartTooltipContent indicator="line" />} />
                <ChartLegend content={<ChartLegendContent />} />
                <Line
                  type="monotone"
                  dataKey="observed"
                  stroke="var(--chart-2)"
                  strokeWidth={2}
                  dot={false}
                />
                <Line
                  type="monotone"
                  dataKey="applied"
                  stroke="var(--chart-1)"
                  strokeWidth={2}
                  dot={false}
                />
              </LineChart>
            </ChartContainer>
          )}
        </PanelCard>

        <PanelCard data-testid="analytics-inbox-flow-panel" title={t('analytics.volume.title')}>
          {triageEmpty ? (
            <EmptyHint message={t('analytics.volume.empty')} />
          ) : (
            <ChartContainer
              config={triageChartConfig}
              className="mx-auto h-[200px] w-full max-w-[220px] sm:h-[220px]"
            >
              <PieChart>
                <ChartTooltip content={<ChartTooltipContent nameKey="key" />} />
                <Pie
                  data={triageDonutData}
                  dataKey="value"
                  nameKey="key"
                  innerRadius={48}
                  outerRadius={80}
                  strokeWidth={2}
                >
                  <Cell fill="var(--chart-1)" />
                  <Cell fill="var(--muted)" />
                </Pie>
              </PieChart>
            </ChartContainer>
          )}
          <div className="mt-2 flex flex-wrap items-center justify-center gap-x-4 gap-y-2 text-xs">
            <span className="flex items-center gap-1.5">
              <span className="inline-block size-2 rounded-full bg-(--chart-1)" />
              <span className="text-muted-foreground">{t('analytics.volume.appliedLabel')}</span>
              <span className="font-mono font-semibold tabular-nums">{triagePercent}%</span>
            </span>
            <span className="flex items-center gap-1.5">
              <span className="bg-muted inline-block size-2 rounded-full" />
              <span className="text-muted-foreground">{t('analytics.volume.untouchedLabel')}</span>
            </span>
          </div>
        </PanelCard>
      </div>

      <PanelCard
        data-testid="analytics-archive-delete-panel"
        title={t('analytics.archiveChart.title')}
      >
        {archiveChartEmpty ? (
          <EmptyHint message={t('analytics.metadataLoad.empty')} />
        ) : (
          <div className="bg-muted/30 rounded-lg px-2 py-3 sm:px-4">
            <ChartContainer config={archiveChartConfig} className="h-[300px] w-full">
              <BarChart
                data={archiveChartData}
                margin={{ top: 12, right: 16, left: -8, bottom: 0 }}
              >
                <defs>
                  <linearGradient id="analytics-archive-bar" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--chart-1)" stopOpacity={0.92} />
                    <stop offset="100%" stopColor="var(--chart-1)" stopOpacity={0.32} />
                  </linearGradient>
                </defs>
                <CartesianGrid vertical={false} className="stroke-border/70" />
                <XAxis
                  dataKey="day"
                  tickLine={false}
                  axisLine={false}
                  fontSize={12}
                  tickMargin={10}
                />
                <YAxis tickLine={false} axisLine={false} fontSize={12} width={36} />
                <ChartTooltip content={<ArchiveChartTooltip />} />
                <ChartLegend content={<ChartLegendContent />} />
                <Bar
                  dataKey="archived"
                  stackId="activity"
                  fill="url(#analytics-archive-bar)"
                  radius={[5, 5, 0, 0]}
                  maxBarSize={84}
                />
                <Bar
                  dataKey="deleted"
                  stackId="activity"
                  fill="var(--chart-5)"
                  radius={[5, 5, 0, 0]}
                  maxBarSize={84}
                />
              </BarChart>
            </ChartContainer>
          </div>
        )}
      </PanelCard>

      <div data-testid="analytics-metadata-control-panel">
        <SourceLoadPanel
          mode={sourceMode}
          rows={visibleSourceRows}
          totalRows={sourceRows.length}
          maxCount={maxSourceCount}
          expanded={sourcesExpanded}
          onModeChange={(nextMode) => {
            setSourceMode(nextMode);
            setSourcesExpanded(false);
          }}
          onToggleExpanded={() => setSourcesExpanded((expanded) => !expanded)}
        />
      </div>

      <PanelCard data-testid="analytics-rule-hits-panel" title={t('analytics.ruleHits.title')}>
        {ruleEmpty ? (
          <EmptyHint message={t('analytics.ruleHits.empty')} />
        ) : (
          <ChartContainer
            config={ruleChartConfig}
            className="w-full"
            style={{ height: `${Math.max(190, ruleChartData.length * 40)}px` }}
          >
            <BarChart
              data={ruleChartData}
              layout="vertical"
              margin={{ top: 4, right: 16, left: 0, bottom: 0 }}
            >
              <CartesianGrid horizontal={false} className="stroke-border/70" />
              <XAxis type="number" tickLine={false} axisLine={false} fontSize={12} />
              <YAxis
                type="category"
                dataKey="name"
                tickLine={false}
                axisLine={false}
                fontSize={12}
                width={150}
              />
              <ChartTooltip
                cursor={{ fill: 'var(--muted)', opacity: 0.4 }}
                content={<ChartTooltipContent indicator="dot" />}
              />
              <ChartLegend content={<ChartLegendContent />} />
              <Bar dataKey="applied" stackId="rule" fill="var(--chart-1)" radius={[4, 0, 0, 4]} />
              <Bar dataKey="reverted" stackId="rule" fill="var(--red)" radius={[0, 4, 4, 0]} />
            </BarChart>
          </ChartContainer>
        )}
      </PanelCard>
    </div>
  );
}

function AnalyticsToolbar({
  selectedWindow,
  groupBy,
  onWindowChange,
  onGroupByChange,
}: {
  selectedWindow: AnalyticsWindow;
  groupBy: GroupByMode;
  onWindowChange: (window: AnalyticsWindow) => void;
  onGroupByChange: (groupBy: GroupByMode) => void;
}) {
  const t = useTranslations();
  const rangeLabel = t(`analytics.range.${rangeKey(selectedWindow)}`);
  const groupByLabel = t(`analytics.groupBy.${groupBy}`);

  return (
    <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
      <Select
        value={selectedWindow}
        onValueChange={(nextValue) => onWindowChange(nextValue as AnalyticsWindow)}
      >
        <SelectTrigger
          className="bg-background h-11 w-full justify-between gap-3 px-4 text-base sm:w-[260px]"
          aria-label={t('analytics.range.label')}
        >
          <Calendar className="text-muted-foreground size-4" aria-hidden="true" />
          <SelectValue>{rangeLabel}</SelectValue>
        </SelectTrigger>
        <SelectContent align="start" className="min-w-[240px]">
          <SelectItem value="7d">{t('analytics.range.lastWeek')}</SelectItem>
          <SelectItem value="30d">{t('analytics.range.lastMonth')}</SelectItem>
          <SelectItem value="90d">{t('analytics.range.lastThreeMonths')}</SelectItem>
        </SelectContent>
      </Select>

      <Select
        value={groupBy}
        onValueChange={(nextValue) => onGroupByChange(nextValue as GroupByMode)}
      >
        <SelectTrigger
          className="bg-background h-11 w-full justify-between gap-3 px-4 text-base sm:w-[220px]"
          aria-label={t('analytics.groupBy.label')}
        >
          <Grid2X2 className="text-muted-foreground size-4" aria-hidden="true" />
          <SelectValue>{groupByLabel}</SelectValue>
        </SelectTrigger>
        <SelectContent align="start" className="min-w-[188px]">
          <SelectItem value="day">{t('analytics.groupBy.day')}</SelectItem>
          <SelectItem value="week">{t('analytics.groupBy.week')}</SelectItem>
          <SelectItem value="month">{t('analytics.groupBy.month')}</SelectItem>
          <SelectItem value="year">{t('analytics.groupBy.year')}</SelectItem>
        </SelectContent>
      </Select>
    </div>
  );
}

function MetricCard({
  label,
  value,
  icon: Icon,
  'data-testid': dataTestId,
}: {
  label: string;
  value: string;
  icon: LucideIcon;
  'data-testid'?: string;
}) {
  return (
    <div data-testid={dataTestId} className="border-border bg-card rounded-lg border p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <p className="text-muted-foreground min-w-0 text-sm leading-5 font-medium">{label}</p>
        <Icon className="text-muted-foreground size-4 shrink-0" aria-hidden="true" />
      </div>
      <p className="text-foreground mt-4 text-3xl leading-none font-semibold tracking-normal tabular-nums">
        {value}
      </p>
    </div>
  );
}

function SourceLoadPanel({
  mode,
  rows,
  totalRows,
  maxCount,
  expanded,
  onModeChange,
  onToggleExpanded,
}: {
  mode: SourceMode;
  rows: SourceRow[];
  totalRows: number;
  maxCount: number;
  expanded: boolean;
  onModeChange: (mode: SourceMode) => void;
  onToggleExpanded: () => void;
}) {
  const t = useTranslations();
  const hasRows = rows.length > 0;
  const canToggle = totalRows > 6;

  return (
    <div data-testid="analytics-top-senders-panel" className="grid gap-4 lg:grid-cols-2">
      <div className="border-border bg-card rounded-lg border shadow-sm">
        <div className="border-border flex min-h-16 items-stretch justify-between gap-3 border-b px-4">
          <div role="tablist" aria-label={t('analytics.sources.modeLabel')} className="flex">
            <SourceModeTab active={mode === 'email'} onClick={() => onModeChange('email')}>
              {t('analytics.sources.emailAddress')}
            </SourceModeTab>
            <SourceModeTab active={mode === 'domain'} onClick={() => onModeChange('domain')}>
              {t('analytics.sources.domain')}
            </SourceModeTab>
          </div>
          <div className="text-muted-foreground flex items-center gap-2 text-xs font-medium tracking-normal uppercase">
            <Mail className="size-4" aria-hidden="true" />
            {t('analytics.sources.received')}
          </div>
        </div>

        <div className="min-h-[300px] p-4">
          {hasRows ? (
            <div className="space-y-3">
              <ol className="space-y-2.5">
                {rows.map((row) => (
                  <SourceRowItem key={`${mode}-${row.key}`} row={row} maxCount={maxCount} />
                ))}
              </ol>
              {canToggle ? (
                <button
                  type="button"
                  className="border-border bg-background hover:bg-muted mx-auto block rounded-md border px-3 py-1.5 text-sm font-medium transition-colors"
                  onClick={onToggleExpanded}
                >
                  {expanded ? t('analytics.sources.viewLess') : t('analytics.sources.viewMore')}
                </button>
              ) : null}
            </div>
          ) : (
            <EmptySourcePanel />
          )}
        </div>
      </div>

      <div className="border-border bg-card rounded-lg border shadow-sm">
        <div className="border-border flex min-h-16 items-center justify-between gap-3 border-b px-6">
          <p className="text-foreground text-base font-medium">
            {t('analytics.sources.emailAddress')}
          </p>
          <div className="text-muted-foreground flex items-center gap-2 text-xs font-medium tracking-normal uppercase">
            <Send className="size-4" aria-hidden="true" />
            {t('analytics.sources.sent')}
          </div>
        </div>
        <div className="flex min-h-[300px] items-center justify-center px-6 text-center">
          <div>
            <p className="text-muted-foreground text-base">{t('analytics.sources.emptyTitle')}</p>
            <p className="text-muted-foreground/80 mt-2 text-sm">
              {t('analytics.sources.emptyBody')}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

function SourceModeTab({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      className={cn(
        'relative px-5 text-base font-medium transition-colors',
        active ? 'text-foreground' : 'text-muted-foreground hover:text-foreground',
      )}
      onClick={onClick}
    >
      {children}
      <span
        className={cn(
          'bg-foreground absolute inset-x-2 bottom-0 h-0.5 rounded-full transition-opacity',
          active ? 'opacity-100' : 'opacity-0',
        )}
        aria-hidden="true"
      />
    </button>
  );
}

function SourceRowItem({ row, maxCount }: { row: SourceRow; maxCount: number }) {
  const width = `${Math.max(10, Math.round(percentOf(row.count, maxCount) * 100))}%`;

  return (
    <li className="bg-muted/35 relative overflow-hidden rounded-md">
      <div className="bg-primary/10 absolute inset-y-0 left-0 rounded-md" style={{ width }} />
      <div className="relative grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 px-3 py-2.5">
        <div className="flex min-w-0 items-center gap-2.5">
          <span className="bg-background ring-border grid size-7 shrink-0 place-items-center rounded-full ring-1">
            <Mail className="text-muted-foreground size-4" aria-hidden="true" />
          </span>
          <span className="text-foreground truncate text-sm font-medium">{row.label}</span>
        </div>
        <span className="text-foreground text-sm font-medium tabular-nums">
          {formatCompactCount(row.count)}
        </span>
      </div>
    </li>
  );
}

function EmptySourcePanel() {
  const t = useTranslations();

  return (
    <div className="flex min-h-[260px] items-center justify-center text-center">
      <div>
        <p className="text-muted-foreground text-base">{t('analytics.sources.emptyTitle')}</p>
        <p className="text-muted-foreground/80 mt-2 text-sm">{t('analytics.sources.emptyBody')}</p>
      </div>
    </div>
  );
}

function PanelCard({
  title,
  children,
  className,
  'data-testid': dataTestId,
}: {
  title: string;
  children: ReactNode;
  className?: string;
  'data-testid'?: string;
}) {
  return (
    <section
      data-testid={dataTestId}
      className={cn('border-border bg-card min-w-0 rounded-lg border p-4 shadow-sm', className)}
    >
      <header className="mb-4">
        <h2 className="text-foreground text-lg leading-snug font-medium">{title}</h2>
      </header>
      {children}
    </section>
  );
}

function EmptyHint({ message }: { message: string }) {
  return (
    <div className="border-border text-muted-foreground flex h-[220px] items-center justify-center rounded-lg border border-dashed text-sm">
      {message}
    </div>
  );
}

function ArchiveChartTooltip({
  active,
  payload,
  label,
}: {
  active?: boolean;
  payload?: Array<{ dataKey?: string | number; value?: number | string }>;
  label?: string;
}) {
  const t = useTranslations();

  if (!active || !payload?.length) {
    return null;
  }

  const archived = payload.find((entry) => entry.dataKey === 'archived')?.value ?? 0;
  const deleted = payload.find((entry) => entry.dataKey === 'deleted')?.value ?? 0;

  return (
    <div className="bg-popover text-popover-foreground rounded-lg border px-3 py-2 text-sm shadow-md">
      <p className="mb-2 font-medium">{label}</p>
      <div className="space-y-1">
        <TooltipLine
          colorClassName="bg-(--chart-1)"
          label={t('analytics.archiveChart.archivedLegend')}
          value={formatCompactCount(safeCount(Number(archived)))}
        />
        <TooltipLine
          colorClassName="bg-(--chart-5)"
          label={t('analytics.archiveChart.deletedLegend')}
          value={formatCompactCount(safeCount(Number(deleted)))}
        />
      </div>
    </div>
  );
}

function TooltipLine({
  colorClassName,
  label,
  value,
}: {
  colorClassName: string;
  label: string;
  value: string;
}) {
  return (
    <div className="grid grid-cols-[auto_1fr_auto] items-center gap-2">
      <span className={cn('size-2.5 rounded-full', colorClassName)} aria-hidden="true" />
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium tabular-nums">{value}</span>
    </div>
  );
}

function rangeKey(window: AnalyticsWindow): 'lastWeek' | 'lastMonth' | 'lastThreeMonths' {
  switch (window) {
    case '30d':
      return 'lastMonth';
    case '90d':
      return 'lastThreeMonths';
    case '7d':
    default:
      return 'lastWeek';
  }
}

function groupDailyActivity(
  dailyLoad: DailyLoadResponse[],
  groupBy: GroupByMode,
  locale: string,
): ActivityChartRow[] {
  const groupedRows = new Map<string, { date: Date; archived: number; deleted: number }>();

  for (const entry of dailyLoad) {
    const date = parseDate(entry.day);
    if (!date) {
      continue;
    }

    const groupDate = groupStartDate(date, groupBy);
    const groupKey = groupDate.toISOString().slice(0, 10);
    const existingRow = groupedRows.get(groupKey) ?? { date: groupDate, archived: 0, deleted: 0 };
    existingRow.archived += safeCount(entry.applied);
    // deleted intentionally not incremented: DailyLoadResponse has no deleted field
    // until the backend wires one. The chart renders 0 truthfully for now.
    groupedRows.set(groupKey, existingRow);
  }

  return Array.from(groupedRows.entries())
    .sort(([, firstRow], [, secondRow]) => firstRow.date.getTime() - secondRow.date.getTime())
    .map(([groupKey, row]) => ({
      groupKey,
      day: formatGroupLabel(row.date, groupBy, locale),
      archived: row.archived,
      deleted: row.deleted,
    }));
}

function parseDate(value: string | undefined): Date | null {
  if (!value) {
    return null;
  }

  const date = new Date(`${value}T00:00:00.000Z`);
  return Number.isNaN(date.getTime()) ? null : date;
}

function groupStartDate(date: Date, groupBy: GroupByMode): Date {
  const groupedDate = new Date(
    Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()),
  );

  if (groupBy === 'week') {
    const day = groupedDate.getUTCDay();
    const mondayOffset = day === 0 ? -6 : 1 - day;
    groupedDate.setUTCDate(groupedDate.getUTCDate() + mondayOffset);
  }

  if (groupBy === 'month') {
    groupedDate.setUTCDate(1);
  }

  if (groupBy === 'year') {
    groupedDate.setUTCMonth(0, 1);
  }

  return groupedDate;
}

const GROUP_LABEL_FORMAT_OPTIONS: Record<'year' | 'month' | 'day', Intl.DateTimeFormatOptions> = {
  year: { year: 'numeric', timeZone: 'UTC' },
  month: { month: 'short', year: 'numeric', timeZone: 'UTC' },
  day: { day: 'numeric', month: 'short', timeZone: 'UTC' },
};

const groupLabelFormatterCache = new Map<string, Intl.DateTimeFormat>();

function getGroupLabelFormatter(
  normalizedLocale: string,
  scale: 'year' | 'month' | 'day',
): Intl.DateTimeFormat {
  const cacheKey = `${normalizedLocale}:${scale}`;
  let formatter = groupLabelFormatterCache.get(cacheKey);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat(normalizedLocale, GROUP_LABEL_FORMAT_OPTIONS[scale]);
    groupLabelFormatterCache.set(cacheKey, formatter);
  }
  return formatter;
}

function formatGroupLabel(date: Date, groupBy: GroupByMode, locale: string): string {
  const normalizedLocale = locale === 'vi' ? 'vi-VN' : 'en-US';
  const scale = groupBy === 'year' || groupBy === 'month' ? groupBy : 'day';
  return getGroupLabelFormatter(normalizedLocale, scale).format(date);
}
