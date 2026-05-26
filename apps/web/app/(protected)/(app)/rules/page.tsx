import { Suspense } from 'react';
import { getTranslations } from 'next-intl/server';

import { LoadingState } from '@/components/states/LoadingState';
import { RulesWorkspace } from '@/features/rules/components/RulesWorkspace';

export default async function RulesPage() {
  const t = await getTranslations();

  return (
    <div className="flex h-full flex-col">
      <div className="border-border border-b px-4 py-3">
        <h1 className="text-foreground text-[17px] font-semibold">{t('rules.page.title')}</h1>
        <p className="text-muted-foreground text-sm">{t('rules.page.intro')}</p>
      </div>
      <div className="flex-1 space-y-4 overflow-auto p-4">
        <Suspense fallback={<LoadingState variant="cards" count={2} />}>
          <RulesWorkspace />
        </Suspense>
      </div>
    </div>
  );
}
