import { Suspense } from 'react';

import { LoadingState } from '@/components/states/LoadingState';
import { NeedsReplyPageClient } from '@/features/needs-reply/components/NeedsReplyPageClient';

export default function NeedsReplyPage() {
  return (
    <div className="mx-auto w-full max-w-6xl p-4 md:p-6">
      <Suspense fallback={<LoadingState variant="rows" count={5} />}>
        <NeedsReplyPageClient />
      </Suspense>
    </div>
  );
}
