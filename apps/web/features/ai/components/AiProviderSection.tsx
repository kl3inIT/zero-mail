'use client';

import { KeyRound } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { SectionHeader } from '@/features/ai/components/SectionHeader';
import { SettingCard } from '@/features/ai/components/SettingCard';

export function AiProviderSection() {
  const t = useTranslations();

  return (
    <section className="space-y-4" aria-labelledby="ai-section-provider">
      <SectionHeader
        id="ai-section-provider"
        title={t('ai.sections.provider.title')}
        helperText={t('ai.sections.provider.helper')}
        icon={KeyRound}
      />
      <SettingCard
        title={t('ai.byok.title')}
        description={t('ai.byok.titleDescription')}
        icon={KeyRound}
        rightSlot={<Button variant="outline">{t('ai.actions.set')}</Button>}
      >
        <div className="space-y-1 text-sm">
          <p className="font-medium">{t('ai.byok.empty.title')}</p>
          <p className="text-muted-foreground">{t('ai.byok.empty.body')}</p>
        </div>
      </SettingCard>
      <p className="text-muted-foreground px-1 text-sm">
        {t('ai.byok.costFooter', { amount: '$0.00' })}
      </p>
    </section>
  );
}
