import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { CreditCard } from 'lucide-react';

import { buttonVariants } from '@/components/ui/button';
import { BalanceCard } from '@/features/billing/components/BalanceCard';
import { LedgerHistory } from '@/features/billing/components/LedgerHistory';

export default async function BillingPage() {
  const t = await getTranslations();

  return (
    <div className="flex h-full flex-col">
      <div className="border-border border-b px-4 py-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="min-w-0">
            <h1 className="text-foreground text-[17px] font-semibold">{t('billing.page.title')}</h1>
            <p className="text-muted-foreground text-sm">{t('billing.page.description')}</p>
          </div>
          <Link
            href="/billing/top-up"
            className={buttonVariants({
              variant: 'accent',
              size: 'sm',
              className: 'w-full shrink-0 sm:w-auto',
            })}
          >
            <CreditCard className="size-4" aria-hidden="true" />
            {t('billing.balance.topupCta')}
          </Link>
        </div>
      </div>
      <div className="flex-1 overflow-auto p-3 sm:p-4">
        <div className="grid gap-4 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
          <BalanceCard />
          <LedgerHistory />
        </div>
      </div>
    </div>
  );
}
