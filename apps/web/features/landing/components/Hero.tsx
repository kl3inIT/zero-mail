import { headers } from 'next/headers';
import { getLocale, getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { buttonVariants } from '@/components/ui/button';
import { getCurrentUserCached } from '@/features/account/api/me';
import InboxPreview from '@/features/landing/components/InboxPreview';
import {
  ArrowDownIcon,
  ArrowRightIcon,
  CheckIcon,
} from '@/features/landing/components/PrototypeIcons';
import type { AppLocale } from '@/i18n/routing';
import { cn } from '@/lib/utils';

export default async function Hero() {
  const t = await getTranslations();
  const locale = (await getLocale()) as AppLocale;

  let ctaHref = '#cta';
  let ctaKey: 'landing.waitlistCta' | 'landing.continueSetupCta' = 'landing.waitlistCta';
  try {
    const headerStore = await headers();
    const cookieHeader = headerStore.get('cookie') ?? '';
    if (cookieHeader) {
      const user = await getCurrentUserCached(cookieHeader);
      if (user.onboardingStep && user.onboardingStep !== 'COMPLETE') {
        ctaHref = '/onboarding';
        ctaKey = 'landing.continueSetupCta';
      }
    }
  } catch {
    // Silent: default to /login and never log cookies or user data.
  }

  return (
    <section className="zm-hero">
      <div className="zm-container">
        <div className="zm-hero-grid">
          <div>
            <span className="zm-eyebrow">
              <span className="zm-dot" />
              {t('landing.eyebrow')}
            </span>
            <h1>
              {t('landing.heading.line1')}
              <br />
              {t('landing.heading.line2')}
              <br />
              <span className={locale === 'en' ? 'zm-serif' : 'text-[var(--accent)]'}>
                {t('landing.heading.accent')}
              </span>
            </h1>
            <p className="zm-hero-sub">{t('landing.tagline')}</p>
            <div className="zm-hero-ctas">
              <Link
                href={ctaHref}
                className={cn(
                  buttonVariants({ variant: 'ink', size: 'lg' }),
                  'h-[46px] px-6 text-[14.5px]',
                )}
              >
                {t(ctaKey)}
                <ArrowRightIcon size={15} />
              </Link>
              <a
                href="#how"
                className={cn(
                  buttonVariants({ variant: 'outline', size: 'lg' }),
                  'h-[46px] border-[var(--line-strong)] bg-[var(--bg-elevated)] px-6 text-[14.5px] text-[var(--ink)] hover:border-[var(--text-faint)] hover:bg-[var(--bg-subtle)]',
                )}
              >
                {t('landing.secondaryCta')}
              </a>
            </div>
            <div className="zm-hero-meta">
              <span>
                <CheckIcon size={13} /> {t('landing.bullets.noAutoSend')}
              </span>
              <span>
                <CheckIcon size={13} /> {t('landing.bullets.readOnlyPrompts')}
              </span>
              <span>
                <CheckIcon size={13} /> {t('landing.bullets.reversibleActions')}
              </span>
            </div>
          </div>
          <div className="min-w-0">
            <InboxPreview />
          </div>
        </div>
        <div className="zm-peek">
          <div className="flex flex-wrap items-end justify-between gap-6">
            <div>
              <span className="zm-eyebrow">
                <span className="zm-dot" />
                {t('landing.peek.eyebrow')}
              </span>
              <div className="mt-2 max-w-xl text-2xl leading-tight font-semibold tracking-tight text-[var(--ink)]">
                {t('landing.peek.title')}
              </div>
            </div>
            <a
              href="#how"
              className="inline-flex items-center gap-2 text-sm text-[var(--text-muted)]"
            >
              {t('landing.peek.link')} <ArrowDownIcon size={14} />
            </a>
          </div>
        </div>
      </div>
    </section>
  );
}
