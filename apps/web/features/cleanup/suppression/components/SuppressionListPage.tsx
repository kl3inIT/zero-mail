'use client';

import { useTranslations } from 'next-intl';

import { Alert, AlertTitle } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { SuppressionAddForm } from '@/features/cleanup/suppression/components/SuppressionAddForm';
import { SuppressionTable } from '@/features/cleanup/suppression/components/SuppressionTable';
import { useSuppressionList } from '@/features/cleanup/suppression/hooks/useSuppressionList';

export function SuppressionListPage() {
  const t = useTranslations();
  const listQuery = useSuppressionList();

  return (
    <div className="flex flex-col gap-5">
      <SuppressionAddForm />

      {listQuery.isPending && (
        <div className="flex flex-col gap-2">
          {Array.from({ length: 5 }).map((_, idx) => (
            <Skeleton key={idx} className="h-10 w-full" />
          ))}
        </div>
      )}

      {listQuery.isError && (
        <Alert variant="destructive">
          <AlertTitle>{t('cleanup.suppression.error')}</AlertTitle>
        </Alert>
      )}

      {!listQuery.isPending && !listQuery.isError && (listQuery.data ?? []).length === 0 && (
        <div className="border-foreground/10 flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed py-10 text-center">
          <h2 className="text-foreground text-base font-medium">
            {t('cleanup.suppression.empty.title')}
          </h2>
          <p className="text-muted-foreground max-w-md text-sm">
            {t('cleanup.suppression.empty.body')}
          </p>
        </div>
      )}

      {!listQuery.isPending && !listQuery.isError && (listQuery.data ?? []).length > 0 && (
        <SuppressionTable entries={listQuery.data ?? []} />
      )}
    </div>
  );
}
