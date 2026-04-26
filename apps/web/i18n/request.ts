import { getRequestConfig } from 'next-intl/server';
import { hasLocale } from 'next-intl';
import { cookies } from 'next/headers';

import { NEXT_LOCALE_COOKIE, routing } from './routing';

/**
 * Per-request next-intl configuration (server-only).
 *
 * Resolution chain (UI-SPEC §"Persistence behavior"):
 *  1. NEXT_LOCALE cookie — read directly so clean App Router routes can live
 *     outside a `[locale]` segment. next-intl's routing middleware rewrites
 *     `localePrefix: 'never'` requests to hidden `/vi/...` paths, which would
 *     require `app/[locale]`; Phase 1.3 intentionally keeps the route tree
 *     grouped by product surface instead.
 *  2. Fallback — defaultLocale ('vi') per D-B3.
 *
 * NO Accept-Language negotiation per D-B2.
 */
export default getRequestConfig(async () => {
  const cookieStore = await cookies();
  const requested = cookieStore.get(NEXT_LOCALE_COOKIE)?.value;
  const locale = hasLocale(routing.locales, requested) ? requested : routing.defaultLocale;

  return {
    locale,
    messages: (await import(`../messages/${locale}.json`)).default,
  };
});
