import { getTranslations } from 'next-intl/server';

import { BalanceCard } from '@/features/billing/components/BalanceCard';
import { LedgerHistory } from '@/features/billing/components/LedgerHistory';

export default async function CreditsPage() {
  const t = await getTranslations();

  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-auto p-3 sm:p-4">
        <div className="space-y-5">
          <header className="space-y-1">
            <h1 className="text-foreground text-2xl font-semibold tracking-normal">
              {t('billing.page.title')}
            </h1>
            <p className="text-muted-foreground text-sm">{t('billing.page.description')}</p>
          </header>

          <div className="grid gap-4 xl:grid-cols-[minmax(320px,0.75fr)_minmax(0,1.25fr)]">
            <BalanceCard />
            <LedgerHistory />
          </div>
        </div>
      </div>
    </div>
  );
}
