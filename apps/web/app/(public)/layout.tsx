import { NextIntlClientProvider } from 'next-intl';
import { getLocale, getMessages } from 'next-intl/server';

import Footer from '@/features/landing/components/Footer';
import TopBar from '@/features/landing/components/TopBar';

/**
 * Public route group layout (Phase 1.3 Plan 05 — D-C2, D-C4).
 *
 * Owns the public chrome around a single <main>. The root layout owns
 * <html>/<body>; this layout MUST NOT redefine them.
 *
 * Hosts NextIntlClientProvider so client components on public routes (e.g.
 * the hero demo video player) can call useTranslations — server components
 * use getTranslations directly and do not need the provider.
 */
export default async function PublicLayout({ children }: { children: React.ReactNode }) {
  const [locale, messages] = await Promise.all([getLocale(), getMessages()]);

  return (
    <NextIntlClientProvider locale={locale} messages={messages}>
      <div className="zm-proto flex min-h-full flex-col">
        <TopBar />
        <main className="flex-1">{children}</main>
        <Footer />
      </div>
    </NextIntlClientProvider>
  );
}
