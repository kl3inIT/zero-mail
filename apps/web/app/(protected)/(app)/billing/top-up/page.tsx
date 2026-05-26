import { Suspense } from 'react';

import { LoadingState } from '@/components/states/LoadingState';
import { TopupClient } from '@/features/billing/components/TopupClient';

export default function TopupPage() {
  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-auto p-3 sm:p-4">
        <Suspense fallback={<LoadingState variant="cards" count={2} />}>
          <TopupClient />
        </Suspense>
      </div>
    </div>
  );
}
