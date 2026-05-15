'use client';

import { useTranslations } from 'next-intl';
import { WalletCards } from 'lucide-react';

import { ErrorState } from '@/components/states/ErrorState';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useBillingBalance } from '@/features/billing/hooks/useBillingBalance';
import { formatCredits } from '@/lib/format';

export function BalanceCard() {
  const t = useTranslations();
  const balance = useBillingBalance();

  if (balance.isError) {
    return (
      <Card data-testid="billing-balance-card">
        <CardContent className="py-6">
          <ErrorState
            heading={t('billing.balance.error.title')}
            body={t('billing.balance.error.body')}
            retryLabel={t('billing.balance.error.retry')}
            onRetry={() => void balance.refetch()}
            className="min-h-44 border-0 bg-transparent py-8"
          />
        </CardContent>
      </Card>
    );
  }

  const availableCredits = balance.data?.availableCredits ?? 0;
  const heldCredits = balance.data?.heldCredits ?? 0;

  return (
    <Card data-testid="billing-balance-card" className="min-h-72">
      <CardHeader>
        <div className="flex items-center justify-between gap-3">
          <div className="space-y-1">
            <CardTitle>{t('billing.balance.label')}</CardTitle>
            <CardDescription>{t('billing.balance.description')}</CardDescription>
          </div>
          <div className="bg-accent-soft text-accent flex size-10 items-center justify-center rounded-lg">
            <WalletCards className="size-5" aria-hidden="true" />
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {balance.isPending ? (
          <div className="space-y-3" aria-busy="true">
            <Skeleton className="h-10 w-36" />
            <Skeleton className="h-4 w-28" />
          </div>
        ) : (
          <div>
            <p
              className="text-foreground text-3xl font-semibold tracking-normal"
              data-testid="billing-balance-figure"
            >
              {formatCredits(availableCredits)}
            </p>
            <p className="text-muted-foreground mt-1 text-sm">{t('billing.balance.unit')}</p>
          </div>
        )}

        <div className="border-border bg-muted/40 grid grid-cols-2 gap-3 rounded-lg border p-3">
          <div>
            <p className="text-muted-foreground text-xs">{t('billing.balance.held')}</p>
            <p className="text-foreground font-mono text-sm">{formatCredits(heldCredits)}</p>
          </div>
          <div>
            <p className="text-muted-foreground text-xs">{t('billing.balance.refreshLabel')}</p>
            <p className="text-foreground text-sm">{t('billing.balance.refreshValue')}</p>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
