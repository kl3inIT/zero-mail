import { getLocale, getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { LanguageSwitcher } from '@/features/i18n/components/LanguageSwitcher';

import type { AppLocale } from '@/i18n/routing';

/**
 * Public route group layout (Phase 1.3 Plan 05 — D-C2, D-C4).
 *
 * Owns the public chrome: header (logo, Docs link, Sign-in link, compact
 * LanguageSwitcher) wrapping a single <main>. The root layout owns
 * <html>/<body>; this layout MUST NOT redefine them (Pitfall 3 in RESEARCH).
 *
 * Plan 07 closes the i18n gap by adding `common.nav.docs` + `common.nav.signIn`
 * (and `landing.*`) keys. Until then this layout uses an unscoped `t()` and
 * casts the missing key paths to silence next-intl's typed-message-key check.
 * Warn-only `i18n:check` from Plan 02 already tolerates the parity gap.
 */
export default async function PublicLayout({ children }: { children: React.ReactNode }) {
  const t = await getTranslations();
  const locale = (await getLocale()) as AppLocale;
  // Plan 07 landed `common.nav.docs` / `common.nav.signIn` in vi.json + en.json,
  // so next-intl's typed-key check now resolves these directly (cast bypass removed).
  const docsLabel = t('common.nav.docs');
  const signInLabel = t('common.nav.signIn');
  return (
    <div className="flex min-h-full flex-col">
      <header className="bg-secondary border-b">
        <nav className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <Link href="/" className="font-semibold">
            Zero Mail
          </Link>
          <div className="flex items-center gap-4">
            <Link href="/docs" className="text-sm">
              {docsLabel}
            </Link>
            <Link href="/login" className="text-sm">
              {signInLabel}
            </Link>
            <LanguageSwitcher currentLocale={locale} authenticated={false} variant="compact" />
          </div>
        </nav>
      </header>
      <main className="flex-1">{children}</main>
    </div>
  );
}
