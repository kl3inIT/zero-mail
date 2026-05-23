import type { Route } from 'next';
import { headers } from 'next/headers';
import { getLocale, getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { buttonVariants } from '@/components/ui/button';
import { getCurrentUserCached } from '@/features/account/api/account-api';
import InboxPreview from '@/features/landing/components/InboxPreview';
import { ArrowRightIcon, CheckIcon } from '@/features/landing/components/PrototypeIcons';
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
            <a
              href="#how"
              className={cn(
                buttonVariants({ variant: 'outline', size: 'lg' }),
                'h-[48px] rounded-full border-(--line-strong) bg-(--bg-elevated) px-8 text-[15px] font-medium text-(--ink) hover:border-(--text-faint) hover:bg-(--bg-subtle)',
              )}
            >
              {t('landing.secondaryCta')}
            </a>
          </div>
          <div className="mt-8 flex flex-wrap items-center justify-center gap-6 text-[13px] text-(--text-faint)">
            <span className="flex items-center gap-2">
              <CheckIcon size={14} className="text-(--green)" /> {t('landing.bullets.noAutoSend')}
            </span>
            <span className="flex items-center gap-2">
              <CheckIcon size={14} className="text-(--green)" />{' '}
              {t('landing.bullets.readOnlyPrompts')}
            </span>
            <span className="flex items-center gap-2">
              <CheckIcon size={14} className="text-(--green)" />{' '}
              {t('landing.bullets.reversibleActions')}
            </span>
          </div>
        </div>

        {/* InboxPreview - Centered Large Mockup */}
        <div className="relative mt-20 w-full max-w-5xl">
          <div className="absolute -inset-4 -z-10 rounded-[3rem] bg-gradient-to-b from-(--accent-soft) to-transparent opacity-20 blur-3xl" />
          <div className="overflow-hidden rounded-2xl border border-(--line-strong) bg-(--bg-elevated) shadow-[0_24px_80px_-12px_rgba(0,0,0,0.1)] transition-transform duration-700 hover:scale-[1.01]">
            <InboxPreview />
          </div>
        </div>

        {/* Metrics bar */}
        <div className="mt-20 flex w-full max-w-4xl flex-wrap items-center justify-center gap-8 border-t border-(--line) pt-10">
          <div className="flex flex-col items-center gap-1">
            <span className="text-2xl font-semibold text-(--ink)">2,847</span>
            <span className="text-xs font-medium tracking-wider text-(--text-muted) uppercase">
              {t('landing.metrics.emailsProcessed')}
            </span>
          </div>
          <div className="hidden h-8 w-px bg-(--line) md:block" />
          <div className="flex flex-col items-center gap-1">
            <span className="text-2xl font-semibold text-(--ink)">94%</span>
            <span className="text-xs font-medium tracking-wider text-(--text-muted) uppercase">
              {t('landing.metrics.triageAccuracy')}
            </span>
          </div>
          <div className="hidden h-8 w-px bg-(--line) md:block" />
          <div className="flex flex-col items-center gap-1">
            <span className="text-2xl font-semibold text-(--ink)">0</span>
            <span className="text-xs font-medium tracking-wider text-(--text-muted) uppercase">
              {t('landing.metrics.autoSendsEver')}
            </span>
          </div>
          <div className="hidden h-8 w-px bg-(--line) md:block" />
          <div className="flex flex-col items-center gap-1">
            <span className="text-2xl font-semibold text-(--ink)">&#60;2s</span>
            <span className="text-xs font-medium tracking-wider text-(--text-muted) uppercase">
              {t('landing.metrics.triagePerEmail')}
            </span>
          </div>
        </div>
      </div>
    </section>
  );
}
