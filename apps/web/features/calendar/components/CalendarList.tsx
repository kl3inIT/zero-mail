'use client';

import { ChevronDownIcon } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useState } from 'react';

import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { Switch } from '@/components/ui/switch';
import { useToggleCalendar } from '@/features/calendar/hooks/use-toggle-calendar';
import type { CalendarSub } from '@/features/calendar/types';

interface CalendarListProps {
  mailboxId: string;
  calendars: CalendarSub[];
}

/**
 * Collapsible list of sub-calendars for a single connection (D-07). Each row
 * shows the calendar name, its time zone, and a Switch wired to the
 * useToggleCalendar mutation. Toggling off cascade-deletes any dependent
 * preference rows on the server (D-13); the UI invalidates the connections
 * query so the role-assignment section re-renders.
 */
export function CalendarList({ mailboxId, calendars }: CalendarListProps) {
  const t = useTranslations();
  const toggle = useToggleCalendar(mailboxId);
  const [open, setOpen] = useState(true);

  if (calendars.length === 0) {
    return (
      <p className="text-muted-foreground text-sm" data-testid="calendar-list-empty">
        {t('calendar.list.empty')}
      </p>
    );
  }

  return (
    <Collapsible open={open} onOpenChange={setOpen} data-testid="calendar-list">
      <CollapsibleTrigger className="text-foreground hover:bg-accent hover:text-accent-foreground flex w-full items-center justify-between rounded-md px-1 py-1 text-sm font-medium">
        <span>{t('calendar.list.heading', { count: calendars.length })}</span>
        <ChevronDownIcon
          className={`size-4 transition-transform ${open ? 'rotate-180' : ''}`}
          aria-hidden="true"
        />
      </CollapsibleTrigger>
      <CollapsibleContent>
        <ul className="mt-2 space-y-1">
          {calendars.map((calendar) => (
            <li
              key={calendar.id}
              className="flex items-center justify-between rounded-md px-1 py-2"
              data-testid={`calendar-list-item-${calendar.id}`}
            >
              <div className="min-w-0">
                <p className="text-foreground truncate text-sm">{calendar.name}</p>
                {calendar.timezone ? (
                  <p className="text-muted-foreground text-xs">
                    {t('calendar.list.timezone', { tz: calendar.timezone })}
                  </p>
                ) : null}
              </div>
              <Switch
                checked={calendar.isEnabled}
                onCheckedChange={(next) =>
                  toggle.mutate({ calendarId: calendar.id, enabled: next })
                }
                aria-label={t('calendar.list.toggleLabel', { name: calendar.name })}
                data-testid={`calendar-list-toggle-${calendar.id}`}
              />
            </li>
          ))}
        </ul>
      </CollapsibleContent>
    </Collapsible>
  );
}
