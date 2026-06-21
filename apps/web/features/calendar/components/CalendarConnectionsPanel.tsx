'use client';

import { useTranslations } from 'next-intl';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { CalendarConnectionCard } from '@/features/calendar/components/CalendarConnectionCard';
import { ConnectCalendarButton } from '@/features/calendar/components/ConnectCalendarButton';
import { EmptyState } from '@/features/calendar/components/EmptyState';
import { RoleAssignmentSection } from '@/features/calendar/components/RoleAssignmentSection';
import { useCalendarConnections } from '@/features/calendar/hooks/use-calendar-connections';

interface CalendarConnectionsPanelProps {
  mailboxId: string;
}

/**
 * Top-level orchestrator for the /settings/mailboxes/[mailboxId]/calendar page.
 * Renders one of three states:
 *  - Loading: 2 stacked skeleton cards.
 *  - Error: an Alert pointing back to the settings page.
 *  - Empty (data.length === 0): the empty-state Card with Connect CTA.
 *  - Populated: a top "Add another account" Connect CTA, the stacked
 *    CalendarConnectionCard list, and the Calendly-style RoleAssignmentSection.
 */
export function CalendarConnectionsPanel({ mailboxId }: CalendarConnectionsPanelProps) {
  const t = useTranslations();
  const { data, isLoading, isError } = useCalendarConnections(mailboxId);

  if (isLoading) {
    return (
      <div className="space-y-4" data-testid="calendar-panel-loading">
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }

  if (isError) {
    return (
      <Alert variant="destructive" data-testid="calendar-panel-error">
        <AlertTitle>{t('calendar.error.loadFailed')}</AlertTitle>
        <AlertDescription>{t('calendar.error.loadFailed')}</AlertDescription>
      </Alert>
    );
  }

  if (!data || data.length === 0) {
    return <EmptyState mailboxId={mailboxId} />;
  }

  return (
    <div className="space-y-6" data-testid="calendar-panel-populated">
      <div className="flex justify-end">
        <ConnectCalendarButton
          mailboxId={mailboxId}
          variant="outline"
          testId="calendar-connect-additional-button"
        />
      </div>
      <div className="space-y-4">
        {data.map((connection) => (
          <CalendarConnectionCard
            key={connection.id}
            mailboxId={mailboxId}
            connection={connection}
          />
        ))}
      </div>
      <RoleAssignmentSection mailboxId={mailboxId} connections={data} />
    </div>
  );
}
