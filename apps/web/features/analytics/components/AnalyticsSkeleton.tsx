'use client';

import { useTranslations } from 'next-intl';

import { Card, CardContent, CardHeader } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

export function AnalyticsSkeleton() {
  const t = useTranslations();

  return (
    <div aria-live="polite" aria-busy="true" className="space-y-5">
      <p className="sr-only">{t('analytics.loading')}</p>
      <div className="bg-card/80 ring-primary/10 flex flex-wrap gap-2 rounded-xl p-3 shadow-sm ring-1">
        <Skeleton className="h-10 w-28 rounded-lg" />
        <Skeleton className="h-10 w-32 rounded-lg" />
        <Skeleton className="h-10 w-32 rounded-lg" />
      </div>
      <div className="grid grid-cols-1 gap-4 xl:grid-cols-12">
        <MetricSkeleton className="xl:col-span-7" />
        <MetricSkeleton className="xl:col-span-5" />
        <ListSkeleton className="xl:col-span-12" />
        <ListSkeleton className="xl:col-span-12" />
        <ListSkeleton className="xl:col-span-12" />
      </div>
      <Card className="xl:col-span-12">
        <CardHeader>
          <Skeleton className="h-3 w-24" />
          <Skeleton className="h-5 w-36" />
        </CardHeader>
        <CardContent className="space-y-2">
          {Array.from({ length: 5 }, (_, index) => (
            <Skeleton key={index} className="hidden h-9 w-full md:block" />
          ))}
          {Array.from({ length: 3 }, (_, index) => (
            <Skeleton key={index} className="h-24 w-full md:hidden" />
          ))}
        </CardContent>
      </Card>
    </div>
  );
}

function MetricSkeleton({ className }: { className?: string }) {
  return (
    <Card className={className}>
      <CardHeader>
        <Skeleton className="h-3 w-24" />
        <Skeleton className="h-5 w-40" />
      </CardHeader>
      <CardContent className="space-y-3">
        <Skeleton className="h-24 w-24 rounded-full" />
        <Skeleton className="h-4 w-36" />
        <Skeleton className="h-3 w-full rounded-full" />
      </CardContent>
    </Card>
  );
}

function ListSkeleton({ className }: { className?: string }) {
  return (
    <Card className={className}>
      <CardHeader>
        <Skeleton className="h-3 w-28" />
        <Skeleton className="h-5 w-44" />
      </CardHeader>
      <CardContent className="space-y-2">
        {Array.from({ length: 3 }, (_, index) => (
          <div key={index} className="grid grid-cols-[auto_1fr_auto] items-center gap-3">
            <Skeleton className="size-4 rounded-full" />
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-5 w-10 rounded-full" />
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
