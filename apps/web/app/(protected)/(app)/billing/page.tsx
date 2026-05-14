import Link from 'next/link';
import { getTranslations } from 'next-intl/server';
import { CreditCard } from 'lucide-react';

import { buttonVariants } from '@/components/ui/button';
import { BalanceCard } from '@/features/billing/components/BalanceCard';
import { LedgerHistory } from '@/features/billing/components/LedgerHistory';

export default async function BillingPage() {
  const t = await getTranslations();

  return (
    <div className="mx-auto w-full max-w-6xl space-y-5 p-4 md:p-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0 space-y-1">
          <h1 className="text-foreground text-xl font-semibold">{t('billing.page.title')}</h1>
          <p className="text-muted-foreground max-w-3xl text-sm leading-6">
            {t('billing.page.description')}
          </p>
        </div>
        <Link
          href="/billing/top-up"
          className={buttonVariants({ variant: 'accent', className: 'w-full shrink-0 sm:w-auto' })}
        >
          <CreditCard className="size-4" aria-hidden="true" />
          {t('billing.balance.topupCta')}
        </Link>
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
        <BalanceCard />
        <LedgerHistory />
      </div>
    </div>
  );
}
