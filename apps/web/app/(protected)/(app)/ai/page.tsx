import { Suspense } from 'react';

import { LoadingState } from '@/components/states/LoadingState';
import { AiConfigPage } from '@/features/ai/components/AiConfigPage';

export default function AiPage() {
  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 space-y-4 overflow-auto p-3 sm:p-4">
        <Suspense fallback={<LoadingState variant="cards" count={2} />}>
          <AiConfigPage />
        </Suspense>
      </div>
    </div>
  );
}
