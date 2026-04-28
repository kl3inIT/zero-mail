import { NextIntlClientProvider } from 'next-intl';
import { getLocale, getMessages } from 'next-intl/server';

import { QueryProvider } from '@/lib/query-client';

/**
 * Protected route group layout (Phase 1.3 Plan 05 — D-C4, D-A3).
 *
 * Bare passthrough for now. Each protected page (/onboarding, /settings)
 * already renders its own <main> with localized chrome. ProtectedHeader will
 * land lazily once Phase 5 has actual chrome to render — until then the layout
 * slot just exists so route grouping works and proxy.ts auth-gate logic still
 * fires on /onboarding + /settings.
 *
 * The root layout owns <html>/<body>; this layout MUST NOT redefine them.
 */
export default async function ProtectedLayout({ children }: { children: React.ReactNode }) {
  const locale = await getLocale();
  const messages = await getMessages();

  return (
    <NextIntlClientProvider locale={locale} messages={messages}>
      <QueryProvider>{children}</QueryProvider>
    </NextIntlClientProvider>
  );
}
