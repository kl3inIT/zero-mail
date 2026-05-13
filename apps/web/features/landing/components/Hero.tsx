import { headers } from 'next/headers';
import { getLocale, getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { buttonVariants } from '@/components/ui/button';
import { getCurrentUserCached } from '@/features/account/api/account-api';
import InboxPreview from '@/features/landing/components/InboxPreview';
import {
  ArchiveIcon,
  ArrowRightIcon,
  CheckIcon,
  PenIcon,
  SparklesIcon,
  TagIcon,
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
          {/* Left: headline + CTAs */}
          <div className="zm-hero-text">
            <span className="zm-eyebrow">
              <span className="zm-hero-live" />
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
                  'zm-hero-cta-primary h-[48px] px-7 text-[14.5px]',
                )}
              >
                {t(ctaKey)}
                <ArrowRightIcon size={15} />
              </Link>
              <a
                href="#how"
                className={cn(
                  buttonVariants({ variant: 'outline', size: 'lg' }),
                  'h-[48px] border-[var(--line-strong)] bg-[var(--bg-elevated)] px-6 text-[14.5px] text-[var(--ink)] hover:border-[var(--text-faint)] hover:bg-[var(--bg-subtle)]',
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

          {/* Right: InboxPreview with floating badges */}
          <div className="zm-hero-preview">
            {/* Floating badge — archived */}
            <div className="zm-float-badge zm-float-badge-green zm-float-badge-tl">
              <span className="zm-float-badge-icon">
                <ArchiveIcon size={12} />
              </span>
              <div>
                <div className="zm-float-badge-title">32 archived</div>
                <div className="zm-float-badge-sub">this morning</div>
              </div>
            </div>

            {/* Floating badge — drafts ready */}
            <div className="zm-float-badge zm-float-badge-accent zm-float-badge-br">
              <span className="zm-float-badge-icon">
                <PenIcon size={12} />
              </span>
              <div>
                <div className="zm-float-badge-title">3 drafts ready</div>
                <div className="zm-float-badge-sub">review &amp; send</div>
              </div>
            </div>

            {/* Floating badge — labels applied */}
            <div className="zm-float-badge zm-float-badge-blue zm-float-badge-tr">
              <span className="zm-float-badge-icon">
                <TagIcon size={12} />
              </span>
              <div>
                <div className="zm-float-badge-title">9 labeled</div>
                <div className="zm-float-badge-sub">by rule</div>
              </div>
            </div>

            {/* AI active pill */}
            <div className="zm-float-pill">
              <SparklesIcon size={11} className="text-[var(--accent)]" />
              <span>AI triage active</span>
            </div>

            <InboxPreview />
          </div>
        </div>

        {/* Metrics bar */}
        <div className="zm-hero-metrics">
          <div className="zm-hero-metric">
            <span className="zm-hero-metric-num">2,847</span>
            <span className="zm-hero-metric-label">emails processed</span>
          </div>
          <div className="zm-hero-metric-sep" />
          <div className="zm-hero-metric">
            <span className="zm-hero-metric-num">94%</span>
            <span className="zm-hero-metric-label">triage accuracy</span>
          </div>
          <div className="zm-hero-metric-sep" />
          <div className="zm-hero-metric">
            <span className="zm-hero-metric-num">0</span>
            <span className="zm-hero-metric-label">auto-sends ever</span>
          </div>
          <div className="zm-hero-metric-sep" />
          <div className="zm-hero-metric">
            <span className="zm-hero-metric-num">&#60;2s</span>
            <span className="zm-hero-metric-label">triage per email</span>
          </div>
        </div>
      </div>
    </section>
  );
}
