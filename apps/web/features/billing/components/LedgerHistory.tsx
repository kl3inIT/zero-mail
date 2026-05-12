'use client';

import { useTranslations } from 'next-intl';

import { EmptyState } from '@/components/states/EmptyState';
import { ErrorState } from '@/components/states/ErrorState';
import { LoadingState } from '@/components/states/LoadingState';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { LedgerTable } from '@/features/billing/components/LedgerTable';
import { useLedgerHistory } from '@/features/billing/hooks/useLedgerHistory';

export function LedgerHistory() {
  const t = useTranslations();
  const ledger = useLedgerHistory();

  if (ledger.isPending) {
    return <LoadingState variant="rows" count={4} />;
  }

  if (ledger.isError) {
    return (
      <ErrorState
        heading={t('billing.ledger.error.title')}
        body={t('billing.ledger.error.body')}
        retryLabel={t('billing.ledger.error.retry')}
        onRetry={() => void ledger.refetch()}
      />
    );
  }

  const unavailable = ledger.data?.pages.some((page) => page.unavailable) ?? false;

  if (unavailable) {
    return (
      // GAP: no backend ledger-history endpoint as of 05A. This is a distinct
      // unavailable state, not the available-but-empty ledger state.
      <Card className="border-warning/40 bg-warning/5" data-testid="ledger-unavailable-panel">
        <CardHeader>
          <CardTitle>{t('billing.ledger.title')}</CardTitle>
          <CardDescription>{t('billing.ledger.unavailable.heading')}</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground max-w-2xl text-sm leading-6">
            {t('billing.ledger.unavailable.body')}
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('billing.ledger.title')}</CardTitle>
        <CardDescription>{t('billing.ledger.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        <EmptyState
          heading={t('billing.ledger.empty.heading')}
          body={t('billing.ledger.empty.body')}
        />
        <div className="sr-only">
          <LedgerTable rows={[]} />
        </div>
      </CardContent>
    </Card>
  );
}
