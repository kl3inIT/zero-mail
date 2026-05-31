'use client';

import { Bot, ShieldCheck, SlidersHorizontal } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Switch } from '@/components/ui/switch';
import { DraftConfidenceDialog } from '@/features/ai/components/DraftConfidenceDialog';
import { SectionHeader } from '@/features/ai/components/SectionHeader';
import { SettingCard } from '@/features/ai/components/SettingCard';
import { useBehaviorSettings } from '@/features/ai/hooks/useBehaviorSettings';
import { useUpdateBehaviorSettings } from '@/features/ai/hooks/useUpdateBehaviorSettings';

export function BehaviorSection() {
  const t = useTranslations();
  const behaviorSettings = useBehaviorSettings();
  const updateBehaviorSettings = useUpdateBehaviorSettings();

  if (behaviorSettings.isError) throw behaviorSettings.error;

  const settings = behaviorSettings.data;
  const controlsDisabled = behaviorSettings.isPending || updateBehaviorSettings.isPending;

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
            <Switch
              aria-label={t('ai.behavior.autoDraftReplies.title')}
              checked={settings?.autoDraftReplies ?? false}
              disabled={controlsDisabled}
              onCheckedChange={(autoDraftReplies) =>
                updateBehaviorSettings.mutate({ autoDraftReplies })
              }
            />
          }
        />
        <SettingCard
          title={t('ai.behavior.draftConfidence.title')}
          description={t('ai.behavior.draftConfidence.description')}
          icon={SlidersHorizontal}
          rightSlot={
            <DraftConfidenceDialog
              value={settings?.draftConfidence ?? 'MEDIUM'}
              disabled={controlsDisabled}
              onSave={(draftConfidence) => updateBehaviorSettings.mutateAsync({ draftConfidence })}
            />
          }
        />
        <SettingCard
          title={t('ai.behavior.sensitiveData.title')}
          description={t('ai.behavior.sensitiveData.description')}
          icon={ShieldCheck}
          rightSlot={
            <Switch
              aria-label={t('ai.behavior.sensitiveData.title')}
              checked={settings?.sensitiveDataProtection ?? true}
              disabled={controlsDisabled}
              onCheckedChange={(sensitiveDataProtection) =>
                updateBehaviorSettings.mutate({ sensitiveDataProtection })
              }
            />
          }
        />
      </div>
    </section>
  );
}
