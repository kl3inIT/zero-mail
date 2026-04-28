import { getLocale, getTranslations } from 'next-intl/server';
import { cookies, headers } from 'next/headers';
import Link from 'next/link';

import { buttonVariants } from '@/components/ui/button';
import { getCurrentUserCached } from '@/features/account/api/me';
import { ThemeToggle } from '@/features/landing/components/ThemeToggle';
import ZMLogoMark from '@/features/landing/components/ZMLogoMark';
import { LanguageSwitcher } from '@/i18n/components/LanguageSwitcher';
import { cn } from '@/lib/utils';

import type { AppLocale } from '@/i18n/routing';

export default async function TopBar() {
  const t = await getTranslations();
  const locale = (await getLocale()) as AppLocale;

  const cookieStore = await cookies();
  const theme: 'light' | 'dark' = cookieStore.get('zm-theme')?.value === 'dark' ? 'dark' : 'light';

  let ctaHref = '/login';
  let ctaKey: 'nav.signIn' | 'nav.continueSetup' | 'nav.openApp' = 'nav.signIn';
  let isAuthed = false;
  try {
    const headerStore = await headers();
    const cookieHeader = headerStore.get('cookie') ?? '';
    if (cookieHeader) {
      const user = await getCurrentUserCached(cookieHeader);
      isAuthed = true;
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
    <header className="border-border border-b" style={{ background: 'var(--nav-bg)' }}>
      <nav className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-3">
        <Link
          href="/"
          aria-label={t('nav.logoLabel')}
          className="text-foreground hover:text-accent flex items-center gap-2 transition-colors"
        >
          <span className="text-accent">
            <ZMLogoMark size={20} />
          </span>
          <span className="text-base">
            <span className="font-medium">zero</span>
            <span className="font-light">mail</span>
          </span>
        </Link>
        <div className="flex items-center gap-1 sm:gap-2">
          <ThemeToggle currentTheme={theme} />
          <LanguageSwitcher currentLocale={locale} authenticated={isAuthed} variant="compact" />
          <Link href={ctaHref} className={cn(buttonVariants({ size: 'sm' }), 'ml-1 sm:ml-2')}>
            {t(ctaKey)}
          </Link>
        </div>
      </nav>
    </header>
  );
}
