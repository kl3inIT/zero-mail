import { Suspense } from 'react';
import { getTranslations } from 'next-intl/server';

import { AnalyticsSkeleton } from '@/features/analytics/components/AnalyticsSkeleton';
import { AnalyticsPageClient } from '@/features/analytics/components/AnalyticsPageClient';

export default async function AnalyticsPage() {
  const t = await getTranslations();

  return (
    <div className="mx-auto w-full max-w-7xl space-y-5 p-4 md:p-6">
      <div className="flex flex-col gap-2 border-b pb-4 md:flex-row md:items-end md:justify-between">
        <div className="space-y-1">
          <p className="text-muted-foreground text-xs font-medium">{t('analytics.page.eyebrow')}</p>
          <h1 className="text-foreground text-2xl font-semibold">{t('analytics.page.title')}</h1>
        </div>
        <p className="text-muted-foreground max-w-2xl text-sm leading-6 md:text-right">
          {t('analytics.page.description')}
        </p>
      </div>
      <Suspense fallback={<AnalyticsSkeleton />}>
        <AnalyticsPageClient />
      </Suspense>
    </div>
  );
}
