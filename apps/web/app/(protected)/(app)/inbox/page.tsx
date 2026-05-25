import { Suspense } from 'react';

import { LoadingState } from '@/components/states/LoadingState';
import { InboxPageClient } from '@/features/inbox/components/InboxPageClient';

export default function InboxPage() {
  return (
    <div className="mx-auto flex h-full w-full max-w-7xl flex-col p-4 md:p-6">
      <Suspense fallback={<LoadingState variant="rows" count={5} />}>
        <InboxPageClient />
      </Suspense>
    </div>
  );
}
