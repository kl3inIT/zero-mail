'use client';

import type { Route } from 'next';
import { useEffect, useRef } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';

/**
 * Surfaces a failed in-app add/reconnect-mailbox OAuth attempt as a toast.
 *
 * <p>When an already-authenticated user's mailbox-management OAuth flow fails, the backend
 * {@code LoginRedirectAuthenticationFailureHandler} keeps them in the app and redirects to {@code
 * /inbox?mailboxError=<code>} instead of bouncing to the public /login page. This component reads
 * that one-shot query param, shows the matching localized error toast, and strips the param so a
 * refresh or back-navigation does not re-fire it.
 *
 * <p>Closed-enum guard: only KNOWN_MAILBOX_ERROR_CODES render a toast; any other value (including
 * tampered input) is ignored. Codes reuse the shared {@code auth.error.<code>} bundle.
 */
const KNOWN_MAILBOX_ERROR_CODES = [
  'mailbox_already_connected',
  'mailbox_in_other_workspace',
  'gmail_scope_required',
  'consent_denied',
  'signin_failed',
] as const;
type MailboxErrorCode = (typeof KNOWN_MAILBOX_ERROR_CODES)[number];

function isKnownMailboxError(value: string | null): value is MailboxErrorCode {
  return value !== null && (KNOWN_MAILBOX_ERROR_CODES as readonly string[]).includes(value);
}

export function MailboxErrorToast() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const tAuthError = useTranslations('auth.error');
  // Loose cast for dynamic key construction — next-intl 4.x strict bundles cannot narrow template
  // literals; mirrors the /login page LooseTranslator pattern.
  const tError = tAuthError as unknown as (key: string) => string;
  // Guard against React 19 effect double-invocation / re-renders before the URL strip lands.
  const shownCodeRef = useRef<string | null>(null);

  const mailboxError = searchParams.get('mailboxError');

  useEffect(() => {
    if (!isKnownMailboxError(mailboxError)) {
      return;
    }
    if (shownCodeRef.current === mailboxError) {
      return;
    }
    shownCodeRef.current = mailboxError;

    toast.error(tError(`${mailboxError}.title`), {
      description: tError(`${mailboxError}.body`),
    });

    // Strip only the one-shot param, preserving any other query state.
    const nextParams = new URLSearchParams(searchParams.toString());
    nextParams.delete('mailboxError');
    const nextQuery = nextParams.toString();
    router.replace((nextQuery ? `${pathname}?${nextQuery}` : pathname) as Route, { scroll: false });
  }, [mailboxError, pathname, router, searchParams, tError]);

  return null;
}
