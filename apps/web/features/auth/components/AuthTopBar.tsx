import type { ReactNode } from 'react';
import { getLocale, getTranslations } from 'next-intl/server';
import { cookies } from 'next/headers';
import Link from 'next/link';

import ZMLogoMark from '@/features/landing/components/ZMLogoMark';
import { ThemeToggle } from '@/features/landing/components/ThemeToggle';
import { LanguageSwitcher } from '@/i18n/components/LanguageSwitcher';

import type { AppLocale } from '@/i18n/routing';

type Props = {
  backHref?: string;
  children?: ReactNode;
};

export default async function AuthTopBar({ backHref = '/', children }: Props) {
  const t = await getTranslations();
  const locale = (await getLocale()) as AppLocale;
  const cookieStore = await cookies();
  const theme: 'light' | 'dark' = cookieStore.get('zm-theme')?.value === 'dark' ? 'dark' : 'light';

  return (
    <header className="border-border border-b" style={{ background: 'var(--nav-bg)' }}>
      <nav className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-3">
        <Link
          href={backHref}
          aria-label={t('nav.logoLabel')}
          className="text-foreground hover:text-accent flex items-center gap-2 transition-colors"
        >
          <span className="text-accent shrink-0">
            <ZMLogoMark size={20} />
          </span>
          <span className="text-base">
            <span className="font-medium">zero</span>
            <span className="font-light">mail</span>
          </span>
        </Link>
        <div className="flex items-center gap-1 sm:gap-2">
          <ThemeToggle currentTheme={theme} />
          <LanguageSwitcher currentLocale={locale} authenticated={false} variant="compact" />
        </div>
      </nav>
      {children ? (
        <div className="border-border bg-background border-t px-4 py-3">{children}</div>
      ) : null}
    </header>
  );
}
