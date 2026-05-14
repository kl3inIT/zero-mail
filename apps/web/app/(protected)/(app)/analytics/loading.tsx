import { AnalyticsSkeleton } from '@/features/analytics/components/AnalyticsSkeleton';

export default function AnalyticsLoading() {
  return (
    <div className="mx-auto w-full max-w-6xl space-y-5 p-4 md:p-6">
      <AnalyticsSkeleton />
    </div>
  );
}
