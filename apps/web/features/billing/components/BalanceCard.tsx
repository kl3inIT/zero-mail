'use client';

import { useLocale, useTranslations } from 'next-intl';
import { CalendarClock, Gift, WalletCards } from 'lucide-react';
import type { ReactNode } from 'react';

import { ErrorState } from '@/components/states/ErrorState';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useBillingBalance } from '@/features/billing/hooks/useBillingBalance';
import { formatCredits, formatDateTime } from '@/lib/format';
import { useHydrated } from '@/lib/use-hydrated';

export function BalanceCard() {
  const t = useTranslations();
  const locale = useLocale();
  const hydrated = useHydrated();
  const balance = useBillingBalance();
  const header = (
    <CardHeader>
      <div className="min-w-0 space-y-1">
        <CardTitle>{t('billing.balance.label')}</CardTitle>
        <CardDescription>{t('billing.balance.description')}</CardDescription>
      </div>
    </CardHeader>
  );

  if (balance.isError) {
    return (
      <Card data-testid="billing-balance-card">
        {header}
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
  const monthlyCredits = balance.data?.monthlyCredits ?? 0;
  const additionalCredits = balance.data?.additionalCredits ?? 0;
  const monthlyCreditAllowance = balance.data?.monthlyCreditAllowance ?? 0;
  const resetLabel = balance.data?.resetsAt
    ? formatDateTime(balance.data.resetsAt, locale)
    : t('billing.balance.resetUnknown');
  const showLoading = balance.isPending || !hydrated;

  return (
    <Card data-testid="billing-balance-card" className="min-h-72">
      {header}
      <CardContent className="space-y-4">
        {showLoading ? (
          <div className="space-y-3" aria-busy="true">
            <Skeleton className="h-10 w-36" />
            <Skeleton className="h-4 w-28" />
            <div className="grid gap-3 sm:grid-cols-2">
              <Skeleton className="h-20" />
              <Skeleton className="h-20" />
              <Skeleton className="h-20" />
              <Skeleton className="h-20" />
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            <div>
              <p
                className="text-foreground text-3xl font-semibold tracking-normal"
                data-testid="billing-balance-figure"
              >
                {formatCredits(availableCredits, locale)}
              </p>
              <p className="text-muted-foreground mt-1 text-sm">{t('billing.balance.unit')}</p>
            </div>
          </div>
        )}

        {!showLoading ? (
          <div className="grid gap-3 sm:grid-cols-2">
            <CreditMetric
              label={t('billing.balance.monthlyCredits')}
              value={formatCredits(monthlyCredits, locale)}
              detail={t('billing.balance.monthlyGrant', {
                credits: formatCredits(monthlyCreditAllowance, locale),
              })}
              icon={<Gift className="size-4" aria-hidden="true" />}
              testId="billing-monthly-credits"
            />
            <CreditMetric
              label={t('billing.balance.additionalCredits')}
              value={formatCredits(additionalCredits, locale)}
              detail={t('billing.balance.additionalCreditDetail')}
              icon={<WalletCards className="size-4" aria-hidden="true" />}
              testId="billing-additional-credits"
            />
            <CreditMetric
              label={t('billing.balance.held')}
              value={formatCredits(heldCredits, locale)}
              detail={t('billing.balance.heldDetail')}
            />
            <CreditMetric
              label={t('billing.balance.resetsAt')}
              value={resetLabel}
              detail={t('billing.balance.refreshValue')}
              icon={<CalendarClock className="size-4" aria-hidden="true" />}
              testId="billing-reset-at"
            />
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

type CreditMetricProps = {
  label: string;
  value: string;
  detail: string;
  icon?: ReactNode;
  testId?: string;
};

function CreditMetric({ label, value, detail, icon, testId }: CreditMetricProps) {
  return (
    <div className="border-border bg-muted/40 min-w-0 rounded-lg border p-3" data-testid={testId}>
      <div className="flex min-w-0 items-center gap-2">
        {icon ? <span className="text-muted-foreground shrink-0">{icon}</span> : null}
        <p className="text-muted-foreground truncate text-xs">{label}</p>
      </div>
      <p className="text-foreground mt-1 truncate font-mono text-sm">{value}</p>
      <p className="text-muted-foreground mt-1 text-xs leading-5">{detail}</p>
    </div>
  );
}
