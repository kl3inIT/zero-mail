import { Suspense } from 'react';

import { LoadingState } from '@/components/states/LoadingState';
import { TopupClient } from '@/features/billing/components/TopupClient';

export default function TopupPage() {
  return (
    <div className="mx-auto w-full max-w-3xl p-4 md:p-6">
      <Suspense fallback={<LoadingState variant="cards" count={2} />}>
        <TopupClient />
      </Suspense>
    </div>
  );
}
