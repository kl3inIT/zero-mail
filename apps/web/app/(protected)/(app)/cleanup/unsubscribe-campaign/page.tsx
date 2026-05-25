import { Suspense } from 'react';

import { CandidateListPage } from '@/features/cleanup/unsubscribe-campaign/components/CandidateListPage';
import { CandidateListSkeleton } from '@/features/cleanup/unsubscribe-campaign/components/CandidateListSkeleton';

export default function UnsubscribeCampaignPage() {
  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-5 p-4 md:p-6">
      <Suspense fallback={<CandidateListSkeleton />}>
        <CandidateListPage />
      </Suspense>
    </div>
  );
}
