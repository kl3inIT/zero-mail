'use client';

import { useState } from 'react';
import { Bell, PauseCircle } from 'lucide-react';
import { useTranslations } from 'next-intl';

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { SectionHeader } from '@/features/ai/components/SectionHeader';
import { SettingCard } from '@/features/ai/components/SettingCard';
import { useNotificationPreferences } from '@/features/notifications/hooks/useNotificationPreferences';
import { useUpdateNotificationPreferences } from '@/features/notifications/hooks/useUpdateNotificationPreferences';
import { useToggleTriagePause } from '@/features/triage/hooks/useToggleTriagePause';
import { useTriagePauseState } from '@/features/triage/hooks/useTriagePauseState';

const DIGEST_HOUR_OPTIONS = Array.from({ length: 24 }, (_, hour) => ({
  value: String(hour),
  label: `${hour.toString().padStart(2, '0')}:00`,
}));
const DEFAULT_DIGEST_TIME_ZONE = 'Asia/Ho_Chi_Minh';

export function UpdatesSection() {
  const t = useTranslations();
  const notificationPreferences = useNotificationPreferences();
  const updateNotificationPreferences = useUpdateNotificationPreferences();
  const pauseState = useTriagePauseState();
  const togglePause = useToggleTriagePause();
  const [pauseConfirmOpen, setPauseConfirmOpen] = useState(false);

  if (notificationPreferences.isError) throw notificationPreferences.error;
  if (pauseState.isError) throw pauseState.error;

  const digestEnabled = notificationPreferences.data?.digestEnabled ?? true;
  const digestSendHourLocal = notificationPreferences.data?.digestSendHourLocal ?? 20;
  const digestTimeZone = notificationPreferences.data?.timeZone ?? DEFAULT_DIGEST_TIME_ZONE;
  const digestControlsDisabled =
    notificationPreferences.isPending || updateNotificationPreferences.isPending;
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
              disabled={digestControlsDisabled}
              onCheckedChange={(checked) =>
                updateNotificationPreferences.mutate({
                  digestEnabled: checked,
                  digestSendHourLocal,
                })
              }
              data-testid="ai-daily-digest-switch"
            />
          }
        >
          {digestEnabled ? (
            <div className="space-y-2">
              <Label htmlFor="ai-digest-send-hour" className="text-sm font-medium">
                {t('settings.notifications.sendHour.label')}
              </Label>
              <Select
                items={DIGEST_HOUR_OPTIONS}
                value={String(digestSendHourLocal)}
                disabled={digestControlsDisabled}
                onValueChange={(nextValue) => {
                  if (typeof nextValue !== 'string') return;
                  const nextHour = Number.parseInt(nextValue, 10);
                  if (Number.isInteger(nextHour) && nextHour >= 0 && nextHour <= 23) {
                    updateNotificationPreferences.mutate({
                      digestEnabled,
                      digestSendHourLocal: nextHour,
                    });
                  }
                }}
              >
                <SelectTrigger
                  id="ai-digest-send-hour"
                  disabled={digestControlsDisabled}
                  className="min-h-11 w-full sm:w-48"
                  aria-describedby="ai-digest-send-hour-helper"
                  data-testid="ai-digest-send-hour-select"
                >
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {DIGEST_HOUR_OPTIONS.map((hourOption) => (
                      <SelectItem key={hourOption.value} value={hourOption.value}>
                        {hourOption.label}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
              <p id="ai-digest-send-hour-helper" className="text-muted-foreground text-xs">
                {t('settings.notifications.sendHour.helper')}
              </p>
              <p className="text-muted-foreground text-xs leading-5">
                {t('settings.notifications.sendHour.downtimeNote')}
              </p>
              <p className="text-muted-foreground text-xs">
                {t('settings.notifications.timeZone.label', { tz: digestTimeZone })}
              </p>
            </div>
          ) : null}
        </SettingCard>
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
              onCheckedChange={(paused) => {
                if (paused) {
                  setPauseConfirmOpen(true);
                } else {
                  togglePause.mutate(false);
                }
              }}
              className="data-unchecked:bg-warning/80"
              data-testid="ai-pause-triage-switch"
            />
          }
        />
      </div>

      <AlertDialog open={pauseConfirmOpen} onOpenChange={setPauseConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('shell.pause.confirm.title')}</AlertDialogTitle>
            <AlertDialogDescription>{t('shell.pause.confirm.body')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={togglePause.isPending}>
              {t('shell.pause.confirm.cancel')}
            </AlertDialogCancel>
            <AlertDialogAction
              disabled={togglePause.isPending}
              onClick={() =>
                togglePause.mutate(true, { onSettled: () => setPauseConfirmOpen(false) })
              }
            >
              {t('shell.pause.confirm.cta')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </section>
  );
}
