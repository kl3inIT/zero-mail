'use client';

import { Bot, ShieldCheck, SlidersHorizontal } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { SectionHeader } from '@/features/ai/components/SectionHeader';
import { SettingCard } from '@/features/ai/components/SettingCard';

export function BehaviorSection() {
  const t = useTranslations();

  return (
    <section className="space-y-4" aria-labelledby="ai-section-behavior">
      <SectionHeader
        id="ai-section-behavior"
        title={t('ai.sections.behavior.title')}
        helperText={t('ai.sections.behavior.helper')}
        icon={Bot}
      />
      <div className="space-y-3">
        <SettingCard
          title={t('ai.behavior.autoDraftReplies.title')}
          description={t('ai.behavior.autoDraftReplies.description')}
          icon={Bot}
          rightSlot={
            <Switch aria-label={t('ai.behavior.autoDraftReplies.title')} checked={false} disabled />
          }
        />
        <SettingCard
          title={t('ai.behavior.draftConfidence.title')}
          description={t('ai.behavior.draftConfidence.description')}
          icon={SlidersHorizontal}
          rightSlot={<Button variant="outline">{t('ai.actions.edit')}</Button>}
        />
        <SettingCard
          title={t('ai.behavior.sensitiveData.title')}
          description={t('ai.behavior.sensitiveData.description')}
          icon={ShieldCheck}
          rightSlot={
            <Switch aria-label={t('ai.behavior.sensitiveData.title')} checked={true} disabled />
          }
        />
      </div>
    </section>
  );
}
