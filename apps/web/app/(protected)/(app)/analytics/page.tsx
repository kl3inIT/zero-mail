import { Suspense } from 'react';
import { getTranslations } from 'next-intl/server';

import { AnalyticsSkeleton } from '@/features/analytics/components/AnalyticsSkeleton';
import { AnalyticsPageClient } from '@/features/analytics/components/AnalyticsPageClient';

export default async function AnalyticsPage() {
  const t = await getTranslations();

  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-5 p-4 md:p-6">
      <div className="border-foreground/10 flex flex-col gap-3 border-b pb-5 md:flex-row md:items-end md:justify-between">
        <div className="flex flex-col gap-1">
          <p className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
            {t('analytics.page.eyebrow')}
          </p>
          <h1 className="text-foreground text-3xl leading-tight font-semibold">
            {t('analytics.page.title')}
          </h1>
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
