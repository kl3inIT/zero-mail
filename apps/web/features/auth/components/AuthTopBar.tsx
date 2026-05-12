import type { ReactNode } from 'react';
import { getLocale, getTranslations } from 'next-intl/server';
import { cookies } from 'next/headers';
import Link from 'next/link';

import { buttonVariants } from '@/components/ui/button';
import { ArrowLeftIcon } from '@/features/landing/components/PrototypeIcons';
import { SegmentedLanguageToggle } from '@/features/landing/components/SegmentedLanguageToggle';
import ZMLogoMark from '@/features/landing/components/ZMLogoMark';
import { ThemeToggle } from '@/features/landing/components/ThemeToggle';
import { cn } from '@/lib/utils';

import type { AppLocale } from '@/i18n/routing';

type Props = {
  backHref?: string;
  children?: ReactNode;
  surface?: 'auth' | 'protected';
};

export default async function AuthTopBar({ backHref = '/', children, surface = 'auth' }: Props) {
  const t = await getTranslations();
  const locale = (await getLocale()) as AppLocale;
  const cookieStore = await cookies();
  const theme: 'light' | 'dark' = cookieStore.get('zm-theme')?.value === 'dark' ? 'dark' : 'light';

  return (
    <header
      className={
        surface === 'auth'
          ? 'zm-auth-topbar'
          : 'border-border bg-background/95 sticky top-0 z-50 border-b backdrop-blur'
      }
    >
      <nav
        className={
          surface === 'auth'
            ? 'zm-auth-topbar-inner'
            : 'relative z-[1] mx-auto flex h-[60px] w-full max-w-7xl items-center justify-between gap-4 px-4 sm:px-6 lg:px-8'
        }
      >
        <Link href={backHref} aria-label={`zeromail - ${t('nav.logoLabel')}`} className="zm-brand">
          <span className="zm-brand-mark">
            <ZMLogoMark size={15} />
          </span>
          <span className="zm-brand-wordmark">
            <span>zero</span>
            <span className="light">mail</span>
          </span>
        </Link>
        {children ? <div className="hidden md:block">{children}</div> : null}
        <div className={surface === 'auth' ? 'zm-auth-actions' : 'flex items-center gap-2'}>
          <Link
            href={backHref}
            className={cn(
              buttonVariants({ variant: 'ghost', size: 'sm' }),
              'hidden h-8 px-3 text-[var(--text-muted)] hover:text-[var(--ink)] sm:inline-flex',
            )}
          >
            <ArrowLeftIcon size={13} />
            {t('auth.backToSite')}
          </Link>
          <SegmentedLanguageToggle currentLocale={locale} />
          <ThemeToggle
            currentTheme={theme}
            label={theme === 'dark' ? t('nav.themeToLight') : t('nav.themeToDark')}
          />
        </div>
      </nav>
      {children ? (
        <div className="border-border bg-background border-t px-4 py-3 md:hidden">{children}</div>
      ) : null}
    </header>
  );
}
