'use client';

import { useTranslations } from 'next-intl';
import { Loader2 } from 'lucide-react';

import { EmptyState } from '@/components/states/EmptyState';
import { ErrorState } from '@/components/states/ErrorState';
import { LoadingState } from '@/components/states/LoadingState';
import { Button } from '@/components/ui/button';
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
      <CardContent className="space-y-4">
        {entries.length > 0 ? (
          <>
            <LedgerTable rows={entries} />
            <div className="flex flex-col gap-3 border-t pt-4 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-muted-foreground text-sm">
                {t('billing.ledger.pagination.loaded', { count: entries.length })}
              </p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={!ledger.hasNextPage || ledger.isFetchingNextPage}
                onClick={() => void ledger.fetchNextPage()}
              >
                {ledger.isFetchingNextPage && <Loader2 className="size-3.5 animate-spin" />}
                {ledger.hasNextPage
                  ? t('billing.ledger.pagination.loadMore')
                  : t('billing.ledger.pagination.end')}
              </Button>
            </div>
          </>
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
