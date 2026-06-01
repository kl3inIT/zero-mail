'use client';

import { useTranslations } from 'next-intl';
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from 'recharts';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from '@/components/ui/chart';
import type {
  CategoryLoadResponse,
  DailyLoadResponse,
} from '@/features/analytics/api/analytics-api';
import { ChartInfoAction } from '@/features/analytics/components/ChartInfoAction';
import {
  compactDayLabel,
  formatCompactCount,
  percentOf,
  safeCount,
  topCategories,
} from '@/features/analytics/components/analytics-visualization';
import { cn } from '@/lib/utils';

type MetadataLoadPanelProps = {
  dailyLoad?: DailyLoadResponse[];
  categoryLoad?: CategoryLoadResponse[];
  className?: string;
};

const CATEGORY_CONFIG_COLORS = [
  'var(--chart-3)',
  'var(--chart-2)',
  'var(--chart-4)',
  'var(--chart-5)',
  'var(--chart-1)',
];

const EMPTY_DAILY_LOAD: DailyLoadResponse[] = [];
const EMPTY_CATEGORY_LOAD: CategoryLoadResponse[] = [];

export function MetadataLoadPanel({
  dailyLoad = EMPTY_DAILY_LOAD,
  categoryLoad = EMPTY_CATEGORY_LOAD,
  className,
}: MetadataLoadPanelProps) {
  const t = useTranslations();
  const dailyChartConfig = {
    observed: {
      label: t('analytics.metadataLoad.observedLegend'),
      color: 'var(--muted)',
    },
    applied: {
      label: t('analytics.metadataLoad.appliedLegend'),
      color: 'var(--chart-1)',
    },
    untouched: {
      label: t('analytics.volume.untouchedLabel'),
      color: 'var(--muted)',
    },
  } satisfies ChartConfig;
  const dailyPoints = dailyLoad.map((day) => ({
    day: compactDayLabel(day.day),
    observed: safeCount(day.observed),
    applied: safeCount(day.applied),
    untouched: Math.max(0, safeCount(day.observed) - safeCount(day.applied)),
    reverted: safeCount(day.reverted),
  }));
  const categories = topCategories(categoryLoad).slice(0, 5);
  const totalObserved = dailyPoints.reduce((sum, day) => sum + day.observed, 0);
  const totalApplied = dailyPoints.reduce((sum, day) => sum + day.applied, 0);
  const totalReverted = dailyPoints.reduce((sum, day) => sum + day.reverted, 0);
  const categoryTotal = categories.reduce((sum, category) => sum + category.count, 0);
  const categoryRows = categories.map((category, index) => ({
    ...category,
    key: `category-${index}`,
    fill: CATEGORY_CONFIG_COLORS[index % CATEGORY_CONFIG_COLORS.length],
    share: percentOf(category.count, categoryTotal),
  }));
  const hasDailyLoad = dailyPoints.length > 0;
  const hasCategories = categories.length > 0;

  return (
    <div
      data-testid="analytics-metadata-load-panel"
      className={cn('grid grid-cols-1 gap-4 xl:grid-cols-12', className)}
    >
      <Card className="bg-card/95 shadow-sm xl:col-span-8">
        <CardHeader className="gap-1.5">
          <CardDescription className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
            {t('analytics.metadataLoad.eyebrow')}
          </CardDescription>
          <CardTitle>
            <h3 className="text-lg leading-snug font-semibold">
              {t('analytics.metadataLoad.dailyVolume')}
            </h3>
          </CardTitle>
          <ChartInfoAction
            title={t('analytics.metadataLoad.dailyVolume')}
            description={t('analytics.metadataLoad.dailyTooltip')}
          />
        </CardHeader>
        <CardContent className="flex min-w-0 flex-col gap-5">
          <div className="bg-muted/35 ring-foreground/10 grid overflow-hidden rounded-lg ring-1 sm:grid-cols-3">
            <SummaryMetric
              label={t('analytics.metadataLoad.observedLegend')}
              value={formatCompactCount(totalObserved)}
              tone="observed"
            />
            <SummaryMetric
              label={t('analytics.metadataLoad.appliedLegend')}
              value={formatCompactCount(totalApplied)}
              tone="applied"
            />
            <SummaryMetric
              label={t('analytics.metadataLoad.revertedLegend')}
              value={formatCompactCount(totalReverted)}
              tone="reverted"
            />
          </div>

          {hasDailyLoad ? (
            <ChartContainer
              config={dailyChartConfig}
              className="h-80 w-full"
              initialDimension={{ width: 720, height: 320 }}
            >
              <BarChart
                accessibilityLayer
                data={dailyPoints}
                margin={{ top: 12, right: 12, left: -18, bottom: 4 }}
                barSize={30}
              >
                <CartesianGrid vertical={false} />
                <XAxis dataKey="day" axisLine={false} tickLine={false} tickMargin={10} />
                <YAxis allowDecimals={false} axisLine={false} tickLine={false} />
                <ChartTooltip cursor={false} content={<ChartTooltipContent indicator="line" />} />
                <Bar
                  dataKey="applied"
                  stackId="load"
                  fill="var(--color-applied)"
                  radius={[0, 0, 5, 5]}
                />
                <Bar
                  dataKey="untouched"
                  stackId="load"
                  fill="var(--color-untouched)"
                  radius={[5, 5, 0, 0]}
                />
              </BarChart>
            </ChartContainer>
          ) : (
            <p className="text-muted-foreground text-sm">{t('analytics.metadataLoad.empty')}</p>
          )}
        </CardContent>
      </Card>

      <Card className="bg-card/95 shadow-sm xl:col-span-4">
        <CardHeader className="gap-1.5">
          <CardDescription className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
            {t('analytics.metadataLoad.categoriesEyebrow')}
          </CardDescription>
          <CardTitle>
            <h3 className="text-lg leading-snug font-semibold">
              {t('analytics.metadataLoad.categories')}
            </h3>
          </CardTitle>
          <ChartInfoAction
            title={t('analytics.metadataLoad.categories')}
            description={t('analytics.metadataLoad.categoryTooltip')}
          />
        </CardHeader>
        <CardContent className="flex min-w-0 flex-col gap-4">
          <p className="text-muted-foreground text-sm leading-6">
            {t('analytics.metadataLoad.categoryShare')}
          </p>

          {hasCategories ? (
            <div className="flex min-w-0 flex-col gap-4">
              <CategoryDistributionBar categories={categoryRows} />
              <div className="flex min-w-0 flex-col gap-3">
                {categoryRows.map((category) => (
                  <CategoryLegendRow
                    key={category.key}
                    label={category.label}
                    count={category.count}
                    share={category.share}
                    color={category.fill}
                    countUnit={t('analytics.metadataLoad.categoryTotalLabel')}
                  />
                ))}
              </div>
            </div>
          ) : (
            <p className="text-muted-foreground text-sm">
              {t('analytics.metadataLoad.categoriesEmpty')}
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function SummaryMetric({
  label,
  value,
  tone,
}: {
  label: string;
  value: string;
  tone: 'observed' | 'applied' | 'reverted';
}) {
  const markerClassName =
    tone === 'observed' ? 'bg-(--chart-2)' : tone === 'applied' ? 'bg-(--chart-1)' : 'bg-(--red)';

  return (
    <div className="border-foreground/10 flex min-w-0 items-center gap-3 border-b px-4 py-3 last:border-b-0 sm:border-r sm:border-b-0 sm:last:border-r-0">
      <span className={cn('h-8 w-1 shrink-0 rounded-full', markerClassName)} />
      <span className="min-w-0">
        <span className="text-muted-foreground block truncate text-xs font-medium">{label}</span>
        <span className="text-foreground block text-xl leading-tight font-semibold tabular-nums">
          {value}
        </span>
      </span>
    </div>
  );
}

function CategoryLegendRow({
  label,
  count,
  share,
  color,
  countUnit,
}: {
  label: string;
  count: number;
  share: number;
  color: string;
  countUnit: string;
}) {
  const sharePercent = Math.round(share * 100);

  return (
    <div
      className="bg-muted/30 ring-foreground/10 grid min-w-0 grid-cols-[minmax(0,1fr)_auto] items-center gap-3 rounded-lg px-3 py-2.5 ring-1"
      style={{ boxShadow: `inset 3px 0 0 ${color}` }}
    >
      <span className="min-w-0">
        <span className="block truncate text-sm font-medium">{label}</span>
        <span className="text-muted-foreground block text-xs tabular-nums">
          {formatCompactCount(count)} {countUnit}
        </span>
      </span>
      <span className="text-foreground text-sm font-semibold tabular-nums">{sharePercent}%</span>
    </div>
  );
}

function CategoryDistributionBar({
  categories,
}: {
  categories: Array<{
    key: string;
    label: string;
    count: number;
    fill: string;
    share: number;
  }>;
}) {
  return (
    <div className="bg-muted/30 ring-foreground/10 flex h-12 overflow-hidden rounded-lg ring-1">
      {categories.map((category) => {
        const width =
          category.count > 0 ? `${Math.max(4, Math.round(category.share * 100))}%` : '0%';

        return (
          <div
            key={category.key}
            className="h-full"
            style={{ width, background: category.fill }}
            aria-label={`${category.label}: ${Math.round(category.share * 100)}%`}
          />
        );
      })}
    </div>
  );
}
