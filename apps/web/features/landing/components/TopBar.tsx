import { getLocale, getTranslations } from 'next-intl/server';
import { cookies, headers } from 'next/headers';
import Link from 'next/link';

import { buttonVariants } from '@/components/ui/button';
import { getCurrentUserCached } from '@/features/account/api/me';
import { SegmentedLanguageToggle } from '@/features/landing/components/SegmentedLanguageToggle';
import { ThemeToggle } from '@/features/landing/components/ThemeToggle';
import ZMLogoMark from '@/features/landing/components/ZMLogoMark';
import { cn } from '@/lib/utils';

import type { AppLocale } from '@/i18n/routing';

export default async function TopBar() {
  const t = await getTranslations();
  const locale = (await getLocale()) as AppLocale;

  const cookieStore = await cookies();
  const theme: 'light' | 'dark' = cookieStore.get('zm-theme')?.value === 'dark' ? 'dark' : 'light';

  let ctaHref = '/login';
  let ctaKey: 'nav.signIn' | 'nav.continueSetup' | 'nav.openApp' = 'nav.signIn';
  try {
    const headerStore = await headers();
    const cookieHeader = headerStore.get('cookie') ?? '';
    if (cookieHeader) {
      const user = await getCurrentUserCached(cookieHeader);
      if (user.onboardingStep && user.onboardingStep !== 'COMPLETE') {
        ctaHref = '/onboarding';
        ctaKey = 'nav.continueSetup';
      } else {
        ctaHref = '/welcome';
        ctaKey = 'nav.openApp';
      }
    }
  } catch {
    // Silent: default to /login and never log cookies or user data.
  }

  return (
    <header className="zm-nav">
      <nav className="zm-nav-inner">
        <Link
          href="/"
          aria-label={`zeromail ${t('nav.beta')} - ${t('nav.logoLabel')}`}
          className="zm-brand"
        >
          <span className="zm-brand-mark">
            <ZMLogoMark size={15} />
          </span>
          <span className="zm-brand-wordmark">
            <span>zero</span>
            <span className="light">mail</span>
          </span>
          <span className="zm-pill zm-pill-mono ml-1 h-[18px] px-1.5 text-[10px]">
            {t('nav.beta')}
          </span>
        </Link>
        <div className="zm-nav-links">
          <a href="#how">{t('nav.how')}</a>
          <a href="#features">{t('nav.features')}</a>
          <a href="#rules">{t('nav.rules')}</a>
          <a href="#trust">{t('nav.trust')}</a>
        </div>
        <div className="zm-nav-cta">
          <SegmentedLanguageToggle currentLocale={locale} />
          <ThemeToggle
            currentTheme={theme}
            label={theme === 'dark' ? t('nav.themeToLight') : t('nav.themeToDark')}
          />
          <Link
            href={ctaHref}
            className={cn(
              buttonVariants({ variant: 'ghost', size: 'sm' }),
              'h-8 px-3 text-[var(--text-muted)] hover:text-[var(--ink)]',
            )}
          >
            {t(ctaKey)}
          </Link>
          <a
            href="#cta"
            className={cn(
              buttonVariants({ variant: 'ink', size: 'sm' }),
              'hidden h-8 px-4 sm:inline-flex',
            )}
          >
            {t('nav.waitlist')}
          </a>
        </div>
      </nav>
    </header>
  );
}
