'use client';

import { useTranslations } from 'next-intl';

import { EmptyState } from '@/components/states/EmptyState';
import { ErrorState } from '@/components/states/ErrorState';
import { LoadingState } from '@/components/states/LoadingState';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useLedgerHistory } from '@/features/billing/hooks/useLedgerHistory';
import { LedgerTable } from '@/features/billing/components/LedgerTable';
import { useHydrated } from '@/lib/use-hydrated';

export function LedgerHistory() {
  const t = useTranslations();
  const hydrated = useHydrated();
  const ledger = useLedgerHistory();

  if (ledger.isPending || !hydrated) {
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

  const entries = ledger.data?.pages.flatMap((page) => page.entries) ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('billing.ledger.title')}</CardTitle>
        <CardDescription>{t('billing.ledger.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        {entries.length > 0 ? (
          <LedgerTable rows={entries} />
        ) : (
          <EmptyState
            heading={t('billing.ledger.empty.heading')}
            body={t('billing.ledger.empty.body')}
          />
        )}
      </CardContent>
    </Card>
  );
}
