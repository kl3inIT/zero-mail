'use client';

import type { ReactNode } from 'react';
import { useTranslations } from 'next-intl';

import { EmptyState } from '@/components/states/EmptyState';
import { ErrorState } from '@/components/states/ErrorState';
import { LoadingState } from '@/components/states/LoadingState';
import { Button } from '@/components/ui/button';
import { AuditCardList } from '@/features/triage/components/AuditCardList';
import { AuditTable } from '@/features/triage/components/AuditTable';
import type { AuditEntry } from '@/features/triage/api/triage-api';
import { flattenAuditEntries, useTriageAuditLog } from '@/features/triage/hooks/useTriageAuditLog';

export type AuditLogInjectedData = {
  entries?: AuditEntry[];
  hasNextPage?: boolean;
  isFetchingNextPage?: boolean;
  onLoadMore?: () => void;
};

type AuditLogProps = {
  injectedData?: AuditLogInjectedData;
  now?: Date;
};

export function AuditLog({ injectedData, now = new Date() }: AuditLogProps) {
  if (injectedData) {
    return (
      <AuditLogView
        entries={injectedData.entries ?? []}
        hasNextPage={Boolean(injectedData.hasNextPage)}
        isFetchingNextPage={Boolean(injectedData.isFetchingNextPage)}
        onLoadMore={injectedData.onLoadMore}
        now={now}
      />
    );
  }

  return <AuditLogQueryState now={now} />;
}

function AuditLogQueryState({ now }: { now: Date }) {
  const t = useTranslations();
  const query = useTriageAuditLog();

  if (query.isPending) {
    return <LoadingState variant="rows" count={5} />;
  }

  if (query.isError) {
    return (
      <ErrorState
        heading={t('triage.audit.error.title')}
        body={t('triage.audit.error.body')}
        retryLabel={t('triage.audit.error.retry')}
        onRetry={() => void query.refetch()}
      />
    );
  }

  return (
    <AuditLogView
      entries={flattenAuditEntries(query.data)}
      hasNextPage={Boolean(query.hasNextPage)}
      isFetchingNextPage={query.isFetchingNextPage}
      onLoadMore={() => void query.fetchNextPage()}
      now={now}
    />
  );
}

function AuditLogView({
  entries,
  hasNextPage,
  isFetchingNextPage,
  onLoadMore,
  now,
}: {
  entries: AuditEntry[];
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  onLoadMore?: () => void;
  now: Date;
}) {
  const t = useTranslations();

  if (entries.length === 0) {
    return (
      <EmptyState heading={t('triage.audit.empty.title')} body={t('triage.audit.empty.body')} />
    );
  }

  return (
    <div className="space-y-4">
      <div className="hidden md:block">
        <AuditTable entries={entries} now={now} />
      </div>
      <div className="md:hidden">
        <AuditCardList entries={entries} now={now} />
      </div>
      {hasNextPage ? (
        <div className="flex justify-center">
          <Button
            type="button"
            variant="outline"
            onClick={onLoadMore}
            disabled={isFetchingNextPage}
          >
            {isFetchingNextPage ? t('triage.audit.loadingMore') : t('triage.audit.loadMore')}
          </Button>
        </div>
      ) : (
        <p className="text-muted-foreground text-center text-xs">{t('triage.audit.endOfList')}</p>
      )}
    </div>
  );
}

export function UndoBoundary({ children }: { children?: ReactNode }) {
  const t = useTranslations();

  return (
    <div
      className="border-border bg-muted/40 text-muted-foreground my-2 rounded-md border px-3 py-2 text-xs font-medium"
      data-testid="audit-undo-boundary"
    >
      {children ?? t('triage.audit.boundary')}
    </div>
  );
}
