import type { Route } from 'next';
import { getLocale, getTranslations } from 'next-intl/server';
import { cookies, headers } from 'next/headers';
import Link from 'next/link';

import { buttonVariants } from '@/components/ui/button';
import { getCurrentUserCached } from '@/features/account/api/account-api';
import { SegmentedLanguageToggle } from '@/features/landing/components/SegmentedLanguageToggle';
import { ThemeToggle } from '@/features/landing/components/ThemeToggle';
import ZMLogoMark from '@/features/landing/components/ZMLogoMark';
import { ONBOARDING_BYPASS_ROUTE, shouldShowBetaOnboarding } from '@/features/onboarding/config';
import { cn } from '@/lib/utils';

import type { AppLocale } from '@/i18n/routing';

export default async function TopBar() {
  const t = await getTranslations();
  const locale = (await getLocale()) as AppLocale;

  const cookieStore = await cookies();
  const theme: 'light' | 'dark' = cookieStore.get('zm-theme')?.value === 'dark' ? 'dark' : 'light';

  let ctaHref: Route = '/login';
  let ctaKey: 'nav.signIn' | 'nav.continueSetup' | 'nav.openApp' = 'nav.signIn';
  try {
    const headerStore = await headers();
    const cookieHeader = headerStore.get('cookie') ?? '';
    if (cookieHeader) {
      const user = await getCurrentUserCached(cookieHeader);
      if (shouldShowBetaOnboarding(user.onboardingStep)) {
        ctaHref = '/onboarding';
        ctaKey = 'nav.continueSetup';
      } else {
        ctaHref = ONBOARDING_BYPASS_ROUTE;
        ctaKey = 'nav.openApp';
      }
    }
  } catch {
    // Silent: default to /login and never log cookies or user data.
  }

  return (
    <header className="zm-nav">
      <nav className="zm-nav-inner">
        <Link href="/" className="zm-brand">
          <span className="zm-brand-mark">
            <ZMLogoMark size={64} />
          </span>
          <span className="zm-brand-wordmark">
            <span>Zero</span>
            <span className="light">Mail</span>
          </span>
        </Link>
        <div className="zm-nav-links">
          <Link href="/#features">{t('nav.features')}</Link>
          <Link href="/#testimonials">{t('nav.testimonials')}</Link>
          <Link href="/#faq">{t('nav.faq')}</Link>
          <Link href="/privacy">{t('footer.privacy')}</Link>
          <Link href="/terms">{t('footer.terms')}</Link>
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
              'h-8 px-3 text-(--text-muted) hover:text-(--ink)',
            )}
          >
            {t(ctaKey)}
          </Link>
          <a
            href="#waitlist"
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
