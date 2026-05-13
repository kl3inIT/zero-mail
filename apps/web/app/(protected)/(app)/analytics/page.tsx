import { Suspense } from 'react';
import { getTranslations } from 'next-intl/server';

import { AnalyticsSkeleton } from '@/features/analytics/components/AnalyticsSkeleton';
import { AnalyticsPageClient } from '@/features/analytics/components/AnalyticsPageClient';

export default async function AnalyticsPage() {
  const t = await getTranslations();

  return (
    <div className="mx-auto w-full max-w-6xl space-y-5 p-4 md:p-6">
      <div className="space-y-1">
        <h1 className="text-foreground text-xl font-semibold">{t('analytics.page.title')}</h1>
        <p className="text-muted-foreground max-w-3xl text-sm leading-6">
          {t('analytics.page.description')}
        </p>
      </div>
      <Suspense fallback={<AnalyticsSkeleton />}>
        <AnalyticsPageClient />
      </Suspense>
    </div>
  );
}
