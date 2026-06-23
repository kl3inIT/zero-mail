'use client';

import { useMutation } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { prepareCalendarConnect } from '@/features/calendar/api/calendar-api';
import { getApiUrl } from '@/lib/api/base-url';

/**
 * Mutation hook for the "Connect Google Calendar" CTA. POSTs to
 * /api/calendar/connect-intent (W3 backend) to stamp the active mailboxId on
 * Spring Session, then navigates the browser to the canonical
 * /oauth2/authorization/google-calendar URL the backend returns.
 *
 * The backend returns a SAME-ORIGIN relative path. In dev the frontend (:3000)
 * and backend (:8080) are different origins, so a raw window.location.assign of
 * the relative path 404s on the frontend. Resolve it against the API base — the
 * same getApiUrl helper the Gmail connect (getConnectMailboxUrl) already uses;
 * in prod the API base is same-origin so the result is unchanged. An absolute
 * URL (if the backend ever returns one) is passed through untouched.
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
      if (typeof window === 'undefined') return;
      const { authorizationUrl } = response;
      const target = authorizationUrl.startsWith('/')
        ? getApiUrl(authorizationUrl as `/${string}`)
        : authorizationUrl;
      window.location.assign(target);
    },
    meta: {
      errorMessage: t('calendar.connectIntent.error'),
    },
  });
}
