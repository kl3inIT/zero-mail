'use client';

import { useMutation } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { prepareCalendarConnect } from '@/features/calendar/api/calendar-api';

/**
 * Mutation hook for the "Connect Google Calendar" CTA. POSTs to
 * /api/calendar/connect-intent (W3 backend) to stamp the active mailboxId on
 * Spring Session, then navigates the browser to the canonical
 * /oauth2/authorization/google-calendar URL the backend returns.
 *
 * Error path is surfaced via the global MutationCache.onError handler reading
 * {@code meta.errorMessage}; the happy path does not emit a toast because the
 * browser is already in the middle of a top-level navigation.
 */
export function useConnectCalendarIntent(mailboxId: string) {
  const t = useTranslations();

  return useMutation({
    mutationFn: () => prepareCalendarConnect(mailboxId),
    onSuccess: (response) => {
      if (typeof window !== 'undefined') {
        window.location.assign(response.authorizationUrl);
      }
    },
    meta: {
      errorMessage: t('calendar.connectIntent.error'),
    },
  });
}
