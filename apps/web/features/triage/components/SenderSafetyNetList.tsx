'use client';

import { useTranslations } from 'next-intl';

import { EmptyState } from '@/components/states/EmptyState';
import { ErrorState } from '@/components/states/ErrorState';
import { LoadingState } from '@/components/states/LoadingState';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { SenderRow } from '@/features/triage/components/SenderRow';
import type { ProtectedSenderResponse } from '@/features/triage/api/triage-api';
import { useProtectedSenders } from '@/features/triage/hooks/useProtectedSenders';

export type SenderSafetyNetListProps = {
  injectedSenders?: ProtectedSenderResponse[];
};

export function SenderSafetyNetList({ injectedSenders }: SenderSafetyNetListProps) {
  if (injectedSenders) {
    return <SenderSafetyNetView senders={injectedSenders} />;
  }

  return <SenderSafetyNetQueryState />;
}

function SenderSafetyNetQueryState() {
  const t = useTranslations();
  const query = useProtectedSenders();

  if (query.isPending) {
    return <LoadingState variant="rows" count={4} />;
  }

  if (query.isError) {
    return (
      <ErrorState
        heading={t('triage.senders.error.title')}
        body={t('triage.senders.error.body')}
        retryLabel={t('triage.senders.error.retry')}
        onRetry={() => void query.refetch()}
      />
    );
  }

  return <SenderSafetyNetView senders={query.data?.senders ?? []} />;
}

function SenderSafetyNetView({ senders }: { senders: ProtectedSenderResponse[] }) {
  const t = useTranslations();

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('triage.senders.title')}</CardTitle>
        <CardDescription>{t('triage.senders.body')}</CardDescription>
      </CardHeader>
      <CardContent>
        {senders.length === 0 ? (
          <EmptyState
            heading={t('triage.senders.empty.title')}
            body={t('triage.senders.empty.body')}
          />
        ) : (
          <div className="divide-border rounded-lg border" data-testid="sender-safety-net-list">
            {senders.map((sender, index) => (
              <SenderRow key={sender.senderEmail ?? `unknown-sender-${index}`} sender={sender} />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
