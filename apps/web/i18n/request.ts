import { getRequestConfig } from 'next-intl/server';
import { hasLocale } from 'next-intl';

import { routing } from './routing';

/**
 * Per-request next-intl configuration (server-only).
 *
 * Resolution chain (UI-SPEC §"Persistence behavior"):
 *  1. requestLocale — comes from the next-intl proxy/middleware after it has
 *     read NEXT_LOCALE (Plan 06 will additionally overwrite the cookie from
 *     /me.preferred_language when the authenticated value differs).
 *  2. Fallback — defaultLocale ('vi') per D-B3.
 *
 * NO Accept-Language negotiation per D-B2.
 */
export default getRequestConfig(async ({ requestLocale }) => {
  const requested = await requestLocale;
  const locale = hasLocale(routing.locales, requested) ? requested : routing.defaultLocale;

  return {
    locale,
    messages: (await import(`../messages/${locale}.json`)).default,
  };
});
