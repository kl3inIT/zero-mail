'use client';

import { useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useUpdateCalendarPreference } from '@/features/calendar/hooks/use-update-calendar-preference';
import type { CalendarConnection } from '@/features/calendar/types';

interface RoleAssignmentSectionProps {
  mailboxId: string;
  connections: CalendarConnection[];
}

interface EligibleCalendar {
  id: string;
  displayLabel: string;
  accountEmail: string;
}

const NONE_VALUE = '__none__';

/**
 * Calendly-style per-role assignment overlay sitting beneath the IZ-style
 * connection cards (D-07). Three rows:
 *  - FREEBUSY: multi-select via Checkbox list (D-08 — only is_enabled=true
 *    calendars are eligible per D-13 picker filter)
 *  - EVENT_WRITE: single-select via raw shadcn Select
 *  - BRIEF_SOURCE: single-select via raw shadcn Select
 *
 * Save sends the full replacement payload through useUpdateCalendarPreference.
 * Per memory feedback_raw_shadcn_first.md no wrapper component — composed from
 * Card + Checkbox + Select directly.
 */
export function RoleAssignmentSection({ mailboxId, connections }: RoleAssignmentSectionProps) {
  const t = useTranslations();
  const update = useUpdateCalendarPreference(mailboxId);

  const eligibleCalendars: EligibleCalendar[] = useMemo(() => {
    const collected: EligibleCalendar[] = [];
    for (const connection of connections) {
      if (connection.status !== 'CONNECTED') continue;
      for (const calendar of connection.calendars) {
        if (!calendar.isEnabled) continue;
        collected.push({
          id: calendar.id,
          displayLabel: calendar.name,
          accountEmail: connection.googleEmail,
        });
      }
    }
    return collected;
  }, [connections]);

  const initialFreebusy = useMemo(() => collectRoleIds(connections, 'FREEBUSY'), [connections]);
  const initialEventWrite = useMemo(
    () => collectRoleIds(connections, 'EVENT_WRITE')[0] ?? null,
    [connections],
  );
  const initialBriefSource = useMemo(
    () => collectRoleIds(connections, 'BRIEF_SOURCE')[0] ?? null,
    [connections],
  );

  const [freebusyIds, setFreebusyIds] = useState<string[]>(initialFreebusy);
  const [eventWriteId, setEventWriteId] = useState<string | null>(initialEventWrite);
  const [briefSourceId, setBriefSourceId] = useState<string | null>(initialBriefSource);

  const isDirty =
    !sameMembership(freebusyIds, initialFreebusy) ||
    eventWriteId !== initialEventWrite ||
    briefSourceId !== initialBriefSource;

  function toggleFreebusy(calendarId: string, next: boolean) {
    setFreebusyIds((current) => {
      const filtered = current.filter((id) => id !== calendarId);
      return next ? [...filtered, calendarId] : filtered;
    });
  }

  function onSave() {
    update.mutate({
      freebusyCalendarIds: freebusyIds,
      eventWriteCalendarId: eventWriteId,
      briefSourceCalendarId: briefSourceId,
    });
  }

  return (
    <Card data-testid="calendar-role-assignment">
      <CardHeader>
        <CardTitle>{t('calendar.roles.heading')}</CardTitle>
        <CardDescription>{t('calendar.roles.intro')}</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-6">
          <section data-testid="calendar-role-freebusy">
            <h3 className="text-foreground text-sm font-medium">
              {t('calendar.roles.freebusyLabel')}
            </h3>
            <p className="text-muted-foreground mt-1 text-xs">
              {t('calendar.roles.freebusyHelper')}
            </p>
            <ul className="mt-3 space-y-2">
              {eligibleCalendars.map((calendar) => {
                const checked = freebusyIds.includes(calendar.id);
                return (
                  <li
                    key={calendar.id}
                    className="flex items-start gap-3"
                    data-testid={`freebusy-item-${calendar.id}`}
                  >
                    <Checkbox
                      checked={checked}
                      onCheckedChange={(next) => toggleFreebusy(calendar.id, Boolean(next))}
                      aria-label={`${t('calendar.roles.freebusyLabel')}: ${calendar.displayLabel}`}
                    />
                    <div className="min-w-0 text-sm">
                      <p className="text-foreground truncate">{calendar.displayLabel}</p>
                      <p className="text-muted-foreground truncate text-xs">
                        {calendar.accountEmail}
                      </p>
                    </div>
                  </li>
                );
              })}
            </ul>
          </section>

          <section data-testid="calendar-role-event-write">
            <h3 className="text-foreground text-sm font-medium">
              {t('calendar.roles.eventWriteLabel')}
            </h3>
            <p className="text-muted-foreground mt-1 text-xs">
              {t('calendar.roles.eventWriteHelper')}
            </p>
            <div className="mt-3">
              <Select
                value={eventWriteId ?? NONE_VALUE}
                onValueChange={(next) => setEventWriteId(next === NONE_VALUE ? null : next)}
              >
                <SelectTrigger className="w-full" data-testid="calendar-role-event-write-trigger">
                  <SelectValue placeholder={t('calendar.roles.placeholderNone')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE_VALUE}>{t('calendar.roles.placeholderNone')}</SelectItem>
                  {eligibleCalendars.map((calendar) => (
                    <SelectItem key={calendar.id} value={calendar.id}>
                      {calendar.displayLabel} — {calendar.accountEmail}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </section>

          <section data-testid="calendar-role-brief-source">
            <h3 className="text-foreground text-sm font-medium">
              {t('calendar.roles.briefSourceLabel')}
            </h3>
            <p className="text-muted-foreground mt-1 text-xs">
              {t('calendar.roles.briefSourceHelper')}
            </p>
            <div className="mt-3">
              <Select
                value={briefSourceId ?? NONE_VALUE}
                onValueChange={(next) => setBriefSourceId(next === NONE_VALUE ? null : next)}
              >
                <SelectTrigger className="w-full" data-testid="calendar-role-brief-source-trigger">
                  <SelectValue placeholder={t('calendar.roles.placeholderNone')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE_VALUE}>{t('calendar.roles.placeholderNone')}</SelectItem>
                  {eligibleCalendars.map((calendar) => (
                    <SelectItem key={calendar.id} value={calendar.id}>
                      {calendar.displayLabel} — {calendar.accountEmail}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </section>

          <div className="flex justify-end">
            <Button
              type="button"
              disabled={!isDirty || update.isPending}
              onClick={onSave}
              data-testid="calendar-role-save"
            >
              {t('calendar.roles.save')}
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function collectRoleIds(
  connections: CalendarConnection[],
  role: 'FREEBUSY' | 'EVENT_WRITE' | 'BRIEF_SOURCE',
): string[] {
  const ids: string[] = [];
  for (const connection of connections) {
    for (const preference of connection.preferences) {
      if (preference.role === role) ids.push(preference.calendarId);
    }
  }
  return ids;
}

function sameMembership(left: string[], right: string[]): boolean {
  if (left.length !== right.length) return false;
  const set = new Set(left);
  return right.every((value) => set.has(value));
}
