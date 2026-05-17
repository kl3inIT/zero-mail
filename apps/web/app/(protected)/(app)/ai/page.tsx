import { Suspense } from 'react';

import { LoadingState } from '@/components/states/LoadingState';
import { AiConfigPage } from '@/features/ai/components/AiConfigPage';

export default function AiPage() {
  return (
    <div className="mx-auto w-full max-w-6xl space-y-5 p-4 md:p-6">
      <Suspense fallback={<LoadingState variant="cards" count={2} />}>
        <AiConfigPage />
      </Suspense>
    </div>
  );
}
