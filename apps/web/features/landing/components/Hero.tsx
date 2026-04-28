import { headers } from 'next/headers';
import { getLocale, getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { Badge } from '@/components/ui/badge';
import { buttonVariants } from '@/components/ui/button';
import { getCurrentUserCached } from '@/features/account/api/me';
import type { AppLocale } from '@/i18n/routing';
import { cn } from '@/lib/utils';

export default async function Hero() {
  const t = await getTranslations();
  const locale = (await getLocale()) as AppLocale;

  let ctaHref = '/login';
  let ctaKey: 'landing.primaryCta' | 'landing.continueSetupCta' = 'landing.primaryCta';
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

  const headingFontClass = locale === 'en' ? 'font-serif' : 'font-sans font-semibold';

  return (
    <section className="relative px-6 py-20 sm:py-28 lg:py-32">
      <div className="mx-auto flex w-full max-w-3xl flex-col items-start gap-8">
        <Badge className="bg-accent-soft text-accent hover:bg-accent-soft gap-2 rounded-full border-transparent px-3 py-1">
          <span className="bg-accent size-1.5 rounded-full" aria-hidden="true" />
          {t('landing.eyebrow')}
        </Badge>
        <h1
          className={cn(
            'text-foreground text-4xl leading-tight tracking-tight sm:text-5xl lg:text-6xl',
            headingFontClass,
          )}
        >
          {t('landing.heading')}
        </h1>
        <p className="text-muted-foreground max-w-2xl text-lg leading-relaxed">
          {t('landing.tagline')}
        </p>
        <Link href={ctaHref} className={cn(buttonVariants({ size: 'lg' }))}>
          {t(ctaKey)}
        </Link>
      </div>
    </section>
  );
}
