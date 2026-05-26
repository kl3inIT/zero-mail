'use client';

import { BookOpen, Languages, PenLine, Signature, SlidersHorizontal, Sparkles } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { AiOutputLanguageDialog } from '@/features/ai/components/AiOutputLanguageDialog';
import { EmailSignatureDialog } from '@/features/ai/components/EmailSignatureDialog';
import { PersonalInstructionsDialog } from '@/features/ai/components/PersonalInstructionsDialog';
import { SectionHeader } from '@/features/ai/components/SectionHeader';
import { SettingCard } from '@/features/ai/components/SettingCard';
import { TonePresetDialog } from '@/features/ai/components/TonePresetDialog';
import { WritingStyleDialog } from '@/features/ai/components/WritingStyleDialog';
import { useUpdateVoiceSettings } from '@/features/ai/hooks/useUpdateVoiceSettings';
import { useVoiceSettings } from '@/features/ai/hooks/useVoiceSettings';
import { KnowledgeTable } from '@/features/knowledge/components/KnowledgeTable';

export function YourVoiceSection() {
  const t = useTranslations();
  const voiceSettings = useVoiceSettings();
  const updateVoiceSettings = useUpdateVoiceSettings();

  if (voiceSettings.isError) throw voiceSettings.error;

  const settings = voiceSettings.data;
  const controlsDisabled = voiceSettings.isPending || updateVoiceSettings.isPending;

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
          rightSlot={
            <WritingStyleDialog
              value={settings?.writingStyle ?? ''}
              disabled={controlsDisabled}
              onSave={(writingStyle) => updateVoiceSettings.mutateAsync({ writingStyle })}
            />
          }
        />
        <SettingCard
          title={t('ai.voice.personalInstructions.title')}
          description={t('ai.voice.personalInstructions.description')}
          icon={SlidersHorizontal}
          rightSlot={
            <PersonalInstructionsDialog
              value={settings?.personalInstructions ?? ''}
              disabled={controlsDisabled}
              onSave={(personalInstructions) =>
                updateVoiceSettings.mutateAsync({ personalInstructions })
              }
            />
          }
        />
        <SettingCard
          title={t('ai.voice.signature.title')}
          description={t('ai.voice.signature.description')}
          icon={Signature}
          rightSlot={
            <EmailSignatureDialog
              value={settings?.emailSignature ?? ''}
              disabled={controlsDisabled}
              onSave={(emailSignature) => updateVoiceSettings.mutateAsync({ emailSignature })}
            />
          }
        />
        <SettingCard
          title={t('ai.voice.tone.title')}
          description={t('ai.voice.tone.description')}
          icon={SlidersHorizontal}
          rightSlot={
            <TonePresetDialog
              value={settings?.tonePreset ?? 'PROFESSIONAL'}
              disabled={controlsDisabled}
              onSave={(tonePreset) => updateVoiceSettings.mutateAsync({ tonePreset })}
            />
          }
        />
        <SettingCard
          title={t('ai.voice.language.title')}
          description={t('ai.voice.language.description')}
          icon={Languages}
          rightSlot={
            <AiOutputLanguageDialog
              value={settings?.aiOutputLanguage ?? 'vi'}
              disabled={controlsDisabled}
              onSave={(aiOutputLanguage) => updateVoiceSettings.mutateAsync({ aiOutputLanguage })}
            />
          }
        />
        <SettingCard
          title={t('ai.knowledge.title.text')}
          description={t('ai.knowledge.title.description')}
          icon={BookOpen}
        >
          <KnowledgeTable />
        </SettingCard>
      </div>
    </section>
  );
}
