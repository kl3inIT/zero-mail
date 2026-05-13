'use client';

import { useEffect, useMemo } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { AnalyticsSkeleton } from '@/features/analytics/components/AnalyticsSkeleton';
import { normalizeAnalyticsWindow, WindowChips } from '@/features/analytics/components/WindowChips';
import { RuleHitsPanel } from '@/features/analytics/components/RuleHitsPanel';
import { TimeSavedPanel } from '@/features/analytics/components/TimeSavedPanel';
import { TopSendersPanel } from '@/features/analytics/components/TopSendersPanel';
import { VolumePanel } from '@/features/analytics/components/VolumePanel';
import { useAnalyticsSummary } from '@/features/analytics/hooks/useAnalyticsSummary';

export function AnalyticsPageClient() {
  const t = useTranslations();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const rawWindow = searchParams.get('window');
  const selectedWindow = normalizeAnalyticsWindow(rawWindow);
  const summaryQuery = useAnalyticsSummary(selectedWindow);

  const canonicalHref = useMemo(() => {
    const nextSearchParams = new URLSearchParams(searchParams.toString());
    nextSearchParams.set('window', selectedWindow);
    return `${pathname}?${nextSearchParams.toString()}`;
  }, [pathname, searchParams, selectedWindow]);

  useEffect(() => {
    if (rawWindow !== selectedWindow) {
      router.replace(canonicalHref, { scroll: false });
    }
  }, [canonicalHref, rawWindow, router, selectedWindow]);

  if (summaryQuery.isPending) {
    return <AnalyticsSkeleton />;
  }

  if (summaryQuery.isError) {
    throw summaryQuery.error;
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <WindowChips
          value={selectedWindow}
          onChange={(nextWindow) => {
            const nextSearchParams = new URLSearchParams(searchParams.toString());
            nextSearchParams.set('window', nextWindow);
            router.replace(`${pathname}?${nextSearchParams.toString()}`, { scroll: false });
          }}
        />
        <p className="text-muted-foreground font-mono text-xs tabular-nums">
          {t('analytics.page.lastRefreshed', { age: '0s' })}
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <VolumePanel
          observed={summaryQuery.data.volumeObserved}
          applied={summaryQuery.data.volumeApplied}
        />
        <TimeSavedPanel seconds={summaryQuery.data.timeSavedSeconds} />
        <TopSendersPanel senders={summaryQuery.data.topSenders} />
        <div className="md:col-span-2">
          <RuleHitsPanel ruleHits={summaryQuery.data.ruleHits} />
        </div>
      </div>
    </div>
  );
}
