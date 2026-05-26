'use client';

import { Bell, PauseCircle } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Switch } from '@/components/ui/switch';
import { SectionHeader } from '@/features/ai/components/SectionHeader';
import { SettingCard } from '@/features/ai/components/SettingCard';
import { useNotificationPreferences } from '@/features/notifications/hooks/useNotificationPreferences';
import { useUpdateNotificationPreferences } from '@/features/notifications/hooks/useUpdateNotificationPreferences';
import { useToggleTriagePause } from '@/features/triage/hooks/useToggleTriagePause';
import { useTriagePauseState } from '@/features/triage/hooks/useTriagePauseState';

export function UpdatesSection() {
  const t = useTranslations();
  const notificationPreferences = useNotificationPreferences();
  const updateNotificationPreferences = useUpdateNotificationPreferences();
  const pauseState = useTriagePauseState();
  const togglePause = useToggleTriagePause();

  if (notificationPreferences.isError) throw notificationPreferences.error;
  if (pauseState.isError) throw pauseState.error;

  const digestEnabled = notificationPreferences.data?.digestEnabled ?? true;
  const digestSendHourLocal = notificationPreferences.data?.digestSendHourLocal ?? 20;
  const triagePaused = pauseState.data ?? false;

  return (
    <section className="space-y-4" aria-labelledby="ai-section-updates">
      <SectionHeader
        id="ai-section-updates"
        title={t('ai.sections.updates.title')}
        helperText={t('ai.sections.updates.helper')}
        icon={Bell}
      />
      <div className="space-y-3">
        <SettingCard
          title={t('ai.updates.dailyDigest.title')}
          description={t('ai.updates.dailyDigest.description')}
          icon={Bell}
          rightSlot={
            <Switch
              id="ai-daily-digest-switch"
              checked={digestEnabled}
              aria-label={t('ai.updates.dailyDigest.title')}
              disabled={
                notificationPreferences.isPending || updateNotificationPreferences.isPending
              }
              onCheckedChange={(checked) =>
                updateNotificationPreferences.mutate({
                  digestEnabled: checked,
                  digestSendHourLocal,
                })
              }
              data-testid="ai-daily-digest-switch"
            />
          }
        />
        <SettingCard
          title={t('ai.updates.pauseTriage.title')}
          description={t('ai.updates.pauseTriage.description')}
          icon={PauseCircle}
          rightSlot={
            <Switch
              id="ai-pause-triage-switch"
              checked={triagePaused}
              aria-label={t('ai.updates.pauseTriage.title')}
              disabled={pauseState.isLoading || togglePause.isPending}
              onCheckedChange={(paused) => togglePause.mutate(paused)}
              className="data-unchecked:bg-warning/80"
              data-testid="ai-pause-triage-switch"
            />
          }
        />
      </div>
    </section>
  );
}
