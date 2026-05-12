import { NextIntlClientProvider } from 'next-intl';
import { getLocale, getMessages } from 'next-intl/server';

import { QueryProvider } from '@/lib/query-client';

/**
 * Protected route group layout.
 *
 * Provider host only: the persistent app shell is mounted by the nested
 * `(app)` route-group layout, while onboarding inherits these providers without
 * the sidebar chrome.
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
