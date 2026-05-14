'use client';

import { Info } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { useNotificationPreferences } from '@/features/notifications/hooks/useNotificationPreferences';
import { useUpdateNotificationPreferences } from '@/features/notifications/hooks/useUpdateNotificationPreferences';

const HOUR_OPTIONS = Array.from({ length: 24 }, (_, hour) => ({
  value: String(hour),
  label: `${hour.toString().padStart(2, '0')}:00`,
}));

const DEFAULT_TIME_ZONE = 'Asia/Ho_Chi_Minh';

export function NotificationsSection() {
  const t = useTranslations();
  const preferencesQuery = useNotificationPreferences();
  const updatePreferences = useUpdateNotificationPreferences();

  const preferences = preferencesQuery.data;
  const digestEnabled = preferences?.digestEnabled ?? true;
  const digestSendHourLocal = preferences?.digestSendHourLocal ?? 20;
  const timeZone = preferences?.timeZone ?? DEFAULT_TIME_ZONE;
  const controlsDisabled = preferencesQuery.isPending || updatePreferences.isPending;
  const selectDisabled = controlsDisabled || !digestEnabled;

  if (preferencesQuery.isError) {
    throw preferencesQuery.error;
  }

  const updateDigest = (nextDigestEnabled: boolean, nextSendHour: number) => {
    updatePreferences.mutate({
      digestEnabled: nextDigestEnabled,
      digestSendHourLocal: nextSendHour,
    });
  };

  return (
    <Card data-testid="notifications-section">
      <CardHeader>
        <CardTitle>
          <h2 className="text-xl leading-tight font-semibold">
            {t('settings.notifications.title')}
          </h2>
        </CardTitle>
        <CardDescription>{t('settings.notifications.description')}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-5">
        {preferencesQuery.isPending ? (
          <NotificationsSkeleton />
        ) : (
          <>
            <div className="flex flex-col gap-3 rounded-lg border p-3 sm:flex-row sm:items-center sm:justify-between">
              <div className="space-y-1">
                <Label htmlFor="daily-digest-switch" className="text-sm font-medium">
                  {t('settings.notifications.toggle.label')}
                </Label>
                <p id="daily-digest-helper" className="text-muted-foreground text-xs leading-5">
                  {digestEnabled
                    ? t('settings.notifications.toggle.helperOn')
                    : t('settings.notifications.toggle.helperOff')}
                </p>
              </div>
              <Switch
                id="daily-digest-switch"
                checked={digestEnabled}
                aria-describedby="daily-digest-helper"
                aria-label={t('settings.notifications.toggle.label')}
                disabled={controlsDisabled}
                onCheckedChange={(checked) => updateDigest(checked, digestSendHourLocal)}
                data-testid="daily-digest-switch"
              />
            </div>

            <div className="grid gap-4">
              <div className="space-y-2">
                <Label htmlFor="digest-send-hour" className="text-sm font-medium">
                  {t('settings.notifications.sendHour.label')}
                </Label>
                <Select
                  items={HOUR_OPTIONS}
                  value={String(digestSendHourLocal)}
                  disabled={selectDisabled}
                  onValueChange={(nextValue) => {
                    if (typeof nextValue !== 'string') return;
                    const nextHour = Number.parseInt(nextValue, 10);
                    if (Number.isInteger(nextHour) && nextHour >= 0 && nextHour <= 23) {
                      updateDigest(digestEnabled, nextHour);
                    }
                  }}
                >
                  <SelectTrigger
                    id="digest-send-hour"
                    disabled={selectDisabled}
                    className="min-h-11 w-full sm:w-48"
                    aria-describedby="digest-send-hour-helper digest-send-hour-downtime-note"
                    data-testid="digest-send-hour-select"
                  >
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {HOUR_OPTIONS.map((hourOption) => (
                        <SelectItem key={hourOption.value} value={hourOption.value}>
                          {hourOption.label}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
                <p id="digest-send-hour-helper" className="text-muted-foreground text-xs">
                  {t('settings.notifications.sendHour.helper')}
                </p>
                <p
                  id="digest-send-hour-downtime-note"
                  className="text-muted-foreground text-xs leading-5"
                >
                  {t('settings.notifications.sendHour.downtimeNote')}
                </p>
              </div>

              <div className="text-muted-foreground flex flex-wrap items-center gap-2 text-sm">
                <span>{t('settings.notifications.timeZone.label', { tz: timeZone })}</span>
                <Tooltip>
                  <TooltipTrigger
                    render={
                      <button
                        type="button"
                        className="hover:text-foreground focus-visible:ring-ring grid size-8 place-items-center rounded-md outline-none focus-visible:ring-2"
                        aria-label={t('settings.notifications.timeZone.tooltip')}
                      />
                    }
                  >
                    <Info className="size-4" aria-hidden="true" />
                  </TooltipTrigger>
                  <TooltipContent>{t('settings.notifications.timeZone.tooltip')}</TooltipContent>
                </Tooltip>
              </div>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}

function NotificationsSkeleton() {
  return (
    <div className="space-y-4" aria-busy="true">
      <div className="flex items-center justify-between gap-4 rounded-lg border p-3">
        <div className="space-y-2">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-3 w-64 max-w-full" />
        </div>
        <Skeleton className="h-6 w-10 rounded-full" />
      </div>
      <div className="space-y-2">
        <Skeleton className="h-4 w-44" />
        <Skeleton className="h-11 w-48 max-w-full" />
        <Skeleton className="h-3 w-72 max-w-full" />
      </div>
    </div>
  );
}
