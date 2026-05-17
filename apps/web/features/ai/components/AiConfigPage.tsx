'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { Plus } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { SenderSafetyNetList } from '@/features/triage/components/SenderSafetyNetList';
import { useOptInSender } from '@/features/triage/hooks/useOptInSender';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function AiConfigPage() {
  const t = useTranslations();
  const [senderEmail, setSenderEmail] = useState('');
  const optInMutation = useOptInSender();

  function handleAddSender(formEvent: React.FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    const trimmed = senderEmail.trim().toLowerCase();
    if (!EMAIL_PATTERN.test(trimmed)) {
      toast.error(t('ai.senders.invalidEmail'));
      return;
    }
    optInMutation.mutate(trimmed, {
      onSuccess: () => {
        toast.success(t('ai.senders.added', { email: trimmed }));
        setSenderEmail('');
      },
      onError: () => {
        toast.error(t('ai.senders.addFailed'));
      },
    });
  }

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-foreground text-xl font-semibold">{t('ai.page.title')}</h1>
        <p className="text-muted-foreground max-w-3xl text-sm leading-6">
          {t('ai.page.description')}
        </p>
      </div>

      <form className="flex flex-col gap-2 sm:flex-row sm:items-center" onSubmit={handleAddSender}>
        <Input
          type="email"
          value={senderEmail}
          onChange={(changeEvent) => setSenderEmail(changeEvent.target.value)}
          placeholder={t('ai.senders.inputPlaceholder')}
          aria-label={t('ai.senders.inputLabel')}
          className="sm:max-w-md"
          disabled={optInMutation.isPending}
        />
        <Button
          type="submit"
          disabled={optInMutation.isPending || senderEmail.trim().length === 0}
          className="gap-1.5"
        >
          <Plus className="size-4" aria-hidden="true" />
          {optInMutation.isPending ? t('ai.senders.adding') : t('ai.senders.add')}
        </Button>
      </form>

      <SenderSafetyNetList />
    </div>
  );
}
