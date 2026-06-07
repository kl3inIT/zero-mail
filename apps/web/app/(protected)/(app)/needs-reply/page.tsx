import { Suspense } from 'react';

import { LoadingState } from '@/components/states/LoadingState';
import { NeedsReplyPageClient } from '@/features/needs-reply/components/NeedsReplyPageClient';

export default function NeedsReplyPage() {
  return (
    <div className="flex h-full flex-col overflow-hidden">
      <Suspense fallback={<LoadingState variant="rows" count={5} />}>
        <NeedsReplyPageClient />
      </Suspense>
    </div>
  );
}
