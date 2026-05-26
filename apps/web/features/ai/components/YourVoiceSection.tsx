'use client';

import { BookOpen, Languages, PenLine, Signature, SlidersHorizontal, Sparkles } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { SectionHeader } from '@/features/ai/components/SectionHeader';
import { SettingCard } from '@/features/ai/components/SettingCard';

export function YourVoiceSection() {
  const t = useTranslations();

  return (
    <section className="space-y-4" aria-labelledby="ai-section-voice">
      <SectionHeader
        id="ai-section-voice"
        title={t('ai.sections.voice.title')}
        helperText={t('ai.sections.voice.helper')}
        icon={Sparkles}
      />
      <div className="space-y-3">
        <SettingCard
          title={t('ai.voice.writingStyle.title')}
          description={t('ai.voice.writingStyle.description')}
          icon={PenLine}
          rightSlot={<Button variant="outline">{t('ai.actions.set')}</Button>}
        />
        <SettingCard
          title={t('ai.voice.personalInstructions.title')}
          description={t('ai.voice.personalInstructions.description')}
          icon={SlidersHorizontal}
          rightSlot={<Button variant="outline">{t('ai.actions.set')}</Button>}
        />
        <SettingCard
          title={t('ai.voice.signature.title')}
          description={t('ai.voice.signature.description')}
          icon={Signature}
          rightSlot={<Button variant="outline">{t('ai.actions.set')}</Button>}
        />
        <SettingCard
          title={t('ai.voice.tone.title')}
          description={t('ai.voice.tone.description')}
          icon={SlidersHorizontal}
          rightSlot={<Button variant="outline">{t('ai.actions.edit')}</Button>}
        />
        <SettingCard
          title={t('ai.voice.language.title')}
          description={t('ai.voice.language.description')}
          icon={Languages}
          rightSlot={<Button variant="outline">{t('ai.actions.edit')}</Button>}
        />
        <SettingCard
          title={t('ai.knowledge.title.text')}
          description={t('ai.knowledge.title.description')}
          icon={BookOpen}
          rightSlot={<Button variant="outline">{t('ai.actions.addSnippet')}</Button>}
        />
      </div>
    </section>
  );
}
