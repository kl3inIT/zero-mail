import { Suspense } from 'react';

import { LoadingState } from '@/components/states/LoadingState';
import { DashboardPageClient } from '@/features/dashboard/components/DashboardPageClient';

export default function DashboardPage() {
  return (
    <Suspense fallback={<LoadingState variant="cards" count={3} />}>
      <DashboardPageClient />
    </Suspense>
  );
}
