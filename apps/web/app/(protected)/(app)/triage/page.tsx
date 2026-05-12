import { Suspense } from 'react';

import { LoadingState } from '@/components/states/LoadingState';
import { TriagePageClient } from '@/features/triage/components/TriagePageClient';

export default function TriagePage() {
  return (
    <div className="mx-auto w-full max-w-6xl p-4 md:p-6">
      <Suspense fallback={<LoadingState variant="cards" count={2} />}>
        <TriagePageClient />
      </Suspense>
    </div>
  );
}
