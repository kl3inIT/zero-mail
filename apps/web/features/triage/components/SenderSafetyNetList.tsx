'use client';

import { Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslations } from 'next-intl';

import { EmptyState } from '@/components/states/EmptyState';
import { ErrorState } from '@/components/states/ErrorState';
import { LoadingState } from '@/components/states/LoadingState';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import type { ProtectedSenderResponse } from '@/features/triage/api/triage-api';
import { SenderRow } from '@/features/triage/components/SenderRow';
import { useOptInSender } from '@/features/triage/hooks/useOptInSender';
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
  const optInSender = useOptInSender();
  const [pattern, setPattern] = useState('');

  function handleSubmit(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    const trimmedPattern = pattern.trim().toLowerCase();
    if (!trimmedPattern) return;
    optInSender.mutate(trimmedPattern, {
      onSuccess: () => setPattern(''),
    });
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('ai.safetyNet.protectedSenders.title')}</CardTitle>
        <CardDescription>{t('ai.safetyNet.protectedSenders.description')}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <form className="flex flex-col gap-2 sm:flex-row" onSubmit={handleSubmit}>
          <Input
            value={pattern}
            onChange={(changeEvent) => setPattern(changeEvent.target.value)}
            placeholder={t('ai.safetyNet.add.placeholder')}
            aria-label={t('ai.actions.addSender')}
            disabled={optInSender.isPending}
          />
          <Button type="submit" disabled={optInSender.isPending || pattern.trim().length === 0}>
            <Plus className="size-4" aria-hidden="true" />
            {t('ai.actions.addSender')}
          </Button>
        </form>
        <p className="text-muted-foreground text-xs">{t('ai.safetyNet.tip')}</p>
        {senders.length === 0 ? (
          <EmptyState heading={t('ai.empty.safetyNet.title')} body={t('ai.empty.safetyNet.body')} />
        ) : (
          <div
            className="divide-border bg-muted/40 divide-y rounded-lg border"
            data-testid="sender-safety-net-list"
          >
            {senders.map((sender) => (
              <SenderRow key={sender.id} sender={sender} />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
