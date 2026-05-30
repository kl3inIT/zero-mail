import { getTranslations } from 'next-intl/server';

import { PlanList } from '@/features/billing/components/PlanList';

export default async function UpgradePlanPage() {
  const t = await getTranslations();

  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-auto p-3 sm:p-4">
        <div className="space-y-5">
          <header className="space-y-1">
            <h1 className="text-foreground text-2xl font-semibold tracking-normal">
              {t('billing.upgrade.title')}
            </h1>
            <p className="text-muted-foreground text-sm">{t('billing.upgrade.description')}</p>
          </header>
          <PlanList />
        </div>
      </div>
    </div>
  );
}
