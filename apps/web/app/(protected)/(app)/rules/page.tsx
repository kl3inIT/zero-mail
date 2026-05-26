import { Suspense } from 'react';

import { LoadingState } from '@/components/states/LoadingState';
import { RulesWorkspace } from '@/features/rules/components/RulesWorkspace';

export default async function RulesPage() {
  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 space-y-4 overflow-auto p-4">
        <Suspense fallback={<LoadingState variant="cards" count={2} />}>
          <RulesWorkspace />
        </Suspense>
      </div>
    </div>
  );
}
