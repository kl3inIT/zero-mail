'use client';

import { Send, ShieldCheck } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Switch } from '@/components/ui/switch';
import { SectionHeader } from '@/features/ai/components/SectionHeader';
import { SettingCard } from '@/features/ai/components/SettingCard';
import {
  useRuleAutomationSettings,
  useUpdateRuleAutomationSettings,
} from '@/features/rules/hooks/use-rule-automation-settings';
import { SenderSafetyNetList } from '@/features/triage/components/SenderSafetyNetList';

export function SafetyNetSection() {
  const t = useTranslations();
  const automationSettings = useRuleAutomationSettings();
  const updateAutomationSettings = useUpdateRuleAutomationSettings();
  const autoSendRulesEnabled = automationSettings.data?.autoSendRulesEnabled ?? true;

  if (automationSettings.isError) throw automationSettings.error;

  return (
    <section className="space-y-4" aria-labelledby="ai-section-safety-net">
      <SectionHeader
        id="ai-section-safety-net"
        title={t('ai.sections.safetyNet.title')}
        helperText={t('ai.sections.safetyNet.helper')}
        icon={ShieldCheck}
      />
      <div className="space-y-3">
        <SenderSafetyNetList />
        <SettingCard
          title={t('ai.safetyNet.autoSend.title')}
          description={t('ai.safetyNet.autoSend.description')}
          icon={Send}
          rightSlot={
            <Switch
              checked={autoSendRulesEnabled}
              aria-label={t('ai.safetyNet.autoSend.title')}
              disabled={automationSettings.isLoading || updateAutomationSettings.isPending}
              onCheckedChange={(enabled) => updateAutomationSettings.mutate(enabled)}
              className="data-unchecked:bg-warning/80"
              data-testid="ai-auto-send-rules-switch"
            />
          }
        />
      </div>
    </section>
  );
}
