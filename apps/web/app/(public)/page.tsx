import { headers } from 'next/headers';
import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { buttonVariants } from '@/components/ui/button';
import { getCurrentUser } from '@/features/account/api/me';

/**
 * Public landing (Phase 1.3 Plan 05 — D-C3, D-D4 "Light skeleton" scope).
 * Phase 01.5 Plan 02 — deflated from PageShell to raw <main> (D-C1, D-C2).
 *
 * CTA target is auth-aware:
 *   - Unauthenticated: → /login
 *   - Authenticated with onboardingStep !== 'COMPLETE': → /onboarding
 *   - Errors / no cookie: silent fallback → /login
 */
export default async function LandingPage() {
  const t = await getTranslations();

  let ctaHref = '/login';
  let ctaKey: 'landing.primaryCta' | 'landing.continueSetupCta' = 'landing.primaryCta';
  try {
    const headerStore = await headers();
    const cookieHeader = headerStore.get('cookie') ?? '';
    if (cookieHeader) {
      const user = await getCurrentUser({ headers: { cookie: cookieHeader } });
      if (user.onboardingStep && user.onboardingStep !== 'COMPLETE') {
        ctaHref = '/onboarding';
        ctaKey = 'landing.continueSetupCta';
      }
    }
  } catch {
    // Silent — default to /login. Privacy: never log cookie or response.
  }

  return (
    <main className="mx-auto max-w-5xl px-4 py-12 lg:py-16">
      <div className="flex flex-col gap-8">
        <h1 className="text-3xl font-semibold tracking-tight">{t('landing.heading')}</h1>
        <p className="text-muted-foreground text-base">{t('landing.tagline')}</p>
        <div>
          <Link href={ctaHref} className={buttonVariants()}>
            {t(ctaKey)}
          </Link>
        </div>
      </div>
    </main>
  );
}
