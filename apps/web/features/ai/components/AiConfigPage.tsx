'use client';

import { useTranslations } from 'next-intl';

import { SenderSafetyNetList } from '@/features/triage/components/SenderSafetyNetList';

export function AiConfigPage() {
  const t = useTranslations();

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-foreground text-xl font-semibold">{t('ai.page.title')}</h1>
        <p className="text-muted-foreground max-w-3xl text-sm leading-6">
          {t('ai.page.description')}
        </p>
      </div>

      <section className="space-y-3">
        <div className="space-y-1">
          <h2 className="text-foreground text-base font-semibold">{t('ai.senders.heading')}</h2>
          <p className="text-muted-foreground max-w-3xl text-sm">{t('ai.senders.description')}</p>
        </div>
        <SenderSafetyNetList />
      </section>
    </div>
  );
}
