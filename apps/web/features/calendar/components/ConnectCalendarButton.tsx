'use client';

import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { useConnectCalendarIntent } from '@/features/calendar/hooks/use-connect-calendar-intent';

interface ConnectCalendarButtonProps {
  mailboxId: string;
  variant?: 'default' | 'outline';
  testId?: string;
}

/**
 * "Connect Google Calendar" CTA. POSTs /api/calendar/connect-intent to stamp the
 * active mailboxId on Spring Session, then redirects to the canonical
 * /oauth2/authorization/google-calendar URL. Per CAL-CONN-01 the calendar OAuth
 * flow is its OWN round-trip — NOT bundled with login.
 */
export function ConnectCalendarButton({
  mailboxId,
  variant = 'default',
  testId,
}: ConnectCalendarButtonProps) {
  const t = useTranslations();
  const connectIntent = useConnectCalendarIntent(mailboxId);

  return (
    <Button
      type="button"
      variant={variant}
      disabled={connectIntent.isPending}
      onClick={() => connectIntent.mutate()}
      data-testid={testId ?? 'calendar-connect-button'}
    >
      {t('calendar.empty.connectCta')}
    </Button>
  );
}
