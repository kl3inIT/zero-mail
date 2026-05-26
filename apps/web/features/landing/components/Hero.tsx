import type { Route } from 'next';
import { headers } from 'next/headers';
import { getLocale, getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { buttonVariants } from '@/components/ui/button';
import { getCurrentUserCached } from '@/features/account/api/account-api';
import { ArrowRightIcon } from '@/features/landing/components/PrototypeIcons';
import { ONBOARDING_BYPASS_ROUTE, shouldShowBetaOnboarding } from '@/features/onboarding/config';
import type { AppLocale } from '@/i18n/routing';
import { cn } from '@/lib/utils';

export default async function Hero() {
  const t = await getTranslations();
  const locale = (await getLocale()) as AppLocale;

  let ctaHref: Route = '#waitlist' as Route;
  let ctaKey: 'landing.waitlistCta' | 'landing.continueSetupCta' | 'landing.openAppCta' =
    'landing.waitlistCta';
  try {
    const headerStore = await headers();
    const cookieHeader = headerStore.get('cookie') ?? '';
    if (cookieHeader) {
      const user = await getCurrentUserCached(cookieHeader);
      if (shouldShowBetaOnboarding(user.onboardingStep)) {
        ctaHref = '/onboarding';
        ctaKey = 'landing.continueSetupCta';
      } else {
        ctaHref = ONBOARDING_BYPASS_ROUTE;
        ctaKey = 'landing.openAppCta';
      }
    }
  } catch {
    // Silent: default to /login and never log cookies or user data.
  }

  return (
    <section className="zm-hero pb-20">
      <div className="zm-container flex flex-col items-center text-center">
        {/* Headline + CTAs */}
        <div className="flex max-w-3xl flex-col items-center">
          <h1 className="mb-6 text-5xl leading-[1.05] font-extrabold tracking-tighter text-(--ink) md:text-7xl">
            {t('landing.heading.line1')} {t('landing.heading.line2')}{' '}
            <span className={locale === 'en' ? 'zm-serif' : 'text-(--accent)'}>
              {t('landing.heading.accent')}
            </span>
          </h1>
          <p className="mb-10 max-w-2xl text-lg leading-relaxed text-(--text-muted) md:text-xl">
            {t('landing.tagline')}
          </p>
          <div className="flex flex-wrap items-center justify-center gap-4">
            <Link
              href={ctaHref}
              className={cn(
                buttonVariants({ variant: 'ink', size: 'lg' }),
                'h-[48px] rounded-full px-8 text-[15px] font-medium shadow-sm',
              )}
            >
              {t(ctaKey)}
              <ArrowRightIcon size={16} className="ml-2" />
            </Link>
            <Link
              href="/#features"
              className={cn(
                buttonVariants({ variant: 'outline', size: 'lg' }),
                'h-[48px] rounded-full border-(--line-strong) bg-(--bg-elevated) px-8 text-[15px] font-medium text-(--ink) hover:border-(--text-faint) hover:bg-(--bg-subtle)',
              )}
            >
              {t('landing.secondaryCta')}
            </Link>
          </div>
        </div>

        {/* Video placeholder — replace with <video> when ready */}
        <div className="relative mt-20 w-full max-w-5xl">
          <div className="absolute -inset-4 -z-10 rounded-[3rem] bg-gradient-to-b from-(--accent-soft) to-transparent opacity-20 blur-3xl" />
          <div
            className="relative aspect-video w-full overflow-hidden rounded-2xl border border-(--line-strong) bg-(--bg-subtle) shadow-[0_24px_80px_-12px_rgba(0,0,0,0.1)]"
            data-slot="hero-video"
            aria-label={t('landing.videoPlaceholder')}
          >
            <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 text-(--text-muted)">
              <span className="flex h-16 w-16 items-center justify-center rounded-full border border-(--line-strong) bg-(--bg-elevated)">
                <svg
                  width="22"
                  height="22"
                  viewBox="0 0 24 24"
                  fill="currentColor"
                  aria-hidden="true"
                >
                  <path d="M8 5v14l11-7z" />
                </svg>
              </span>
              <span className="text-sm font-medium">{t('landing.videoPlaceholder')}</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
