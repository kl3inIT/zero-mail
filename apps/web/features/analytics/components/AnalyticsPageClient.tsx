'use client';

import { useEffect, useMemo } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { AnalyticsSkeleton } from '@/features/analytics/components/AnalyticsSkeleton';
import { MetadataControlPanel } from '@/features/analytics/components/MetadataControlPanel';
import { MetadataLoadPanel } from '@/features/analytics/components/MetadataLoadPanel';
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
    <div className="flex flex-col gap-5">
      <div className="bg-muted/30 ring-foreground/10 flex flex-col gap-3 rounded-lg px-3 py-3 ring-1 md:flex-row md:items-center md:justify-between md:px-4">
        <WindowChips
          value={selectedWindow}
          onChange={(nextWindow) => {
            const nextSearchParams = new URLSearchParams(searchParams.toString());
            nextSearchParams.set('window', nextWindow);
            router.replace(`${pathname}?${nextSearchParams.toString()}`, { scroll: false });
          }}
        />
        <p className="text-muted-foreground text-right text-xs tabular-nums">
          {t('analytics.page.lastRefreshed', { age: '0s' })}
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-12">
        <VolumePanel
          observed={summaryQuery.data.volumeObserved}
          applied={summaryQuery.data.volumeApplied}
          className="xl:col-span-7"
        />
        <TimeSavedPanel
          seconds={summaryQuery.data.timeSavedSeconds}
          applied={summaryQuery.data.volumeApplied}
          window={selectedWindow}
          className="xl:col-span-5"
        />
        <TopSendersPanel
          senders={summaryQuery.data.topSenders}
          domainLoad={summaryQuery.data.domainLoad}
          className="xl:col-span-12"
        />
        <MetadataLoadPanel
          dailyLoad={summaryQuery.data.dailyLoad}
          categoryLoad={summaryQuery.data.categoryLoad}
          className="xl:col-span-12"
        />
        <MetadataControlPanel
          actionMix={summaryQuery.data.actionMix}
          replyBuckets={summaryQuery.data.replyBuckets}
          automationOpportunities={summaryQuery.data.automationOpportunities}
          className="xl:col-span-12"
        />
        <RuleHitsPanel ruleHits={summaryQuery.data.ruleHits} className="xl:col-span-12" />
      </div>
    </div>
  );
}
