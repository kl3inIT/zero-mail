import { Suspense } from 'react';

import { CandidateListPage } from '@/features/cleanup/unsubscribe-campaign/components/CandidateListPage';
import { CandidateListSkeleton } from '@/features/cleanup/unsubscribe-campaign/components/CandidateListSkeleton';

export default function UnsubscribeCampaignPage() {
  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-auto p-3 sm:p-4">
        <Suspense fallback={<CandidateListSkeleton />}>
          <CandidateListPage />
        </Suspense>
      </div>
    </div>
  );
}
