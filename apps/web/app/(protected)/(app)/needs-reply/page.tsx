import { Suspense } from 'react';

import { LoadingState } from '@/components/states/LoadingState';
import { NeedsReplyPageClient } from '@/features/needs-reply/components/NeedsReplyPageClient';

export default function NeedsReplyPage() {
  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-auto p-3 sm:p-4">
        <Suspense fallback={<LoadingState variant="rows" count={5} />}>
          <NeedsReplyPageClient />
        </Suspense>
      </div>
    </div>
  );
}
