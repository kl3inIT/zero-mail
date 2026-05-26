import { Suspense } from 'react';

import { LoadingState } from '@/components/states/LoadingState';
import { InboxPageClient } from '@/features/inbox/components/InboxPageClient';

export default function InboxPage() {
  return (
    <div className="flex h-full flex-col overflow-hidden">
      <Suspense fallback={<LoadingState variant="rows" count={5} />}>
        <InboxPageClient />
      </Suspense>
    </div>
  );
}
