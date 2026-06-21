'use client';

import { useTranslations } from 'next-intl';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ConnectCalendarButton } from '@/features/calendar/components/ConnectCalendarButton';

interface EmptyStateProps {
  mailboxId: string;
}

/**
 * Empty-state Card shown when the mailbox has zero connected calendar accounts
 * (D-07). Mirrors Inbox Zero's CalendarConnections.tsx empty-state layout but
 * with token classes only (no hex colors per AGENTS.md) and raw shadcn Card.
 */
export function EmptyState({ mailboxId }: EmptyStateProps) {
  const t = useTranslations();
  return (
    <Card data-testid="calendar-empty-state">
      <CardHeader>
        <CardTitle>{t('calendar.empty.heading')}</CardTitle>
        <CardDescription>{t('calendar.empty.body')}</CardDescription>
      </CardHeader>
      <CardContent>
        <ConnectCalendarButton mailboxId={mailboxId} />
      </CardContent>
    </Card>
  );
}
