import { headers } from 'next/headers';
import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { getCurrentUser } from '@/features/account/api/me';

/**
 * Public landing (Phase 1.3 Plan 05 — D-C3, D-D4 "Light skeleton" scope).
 * Phase 01.5 Plan 02 — deflated from PageShell to raw <main> (D-C1, D-C2).
 * Phase 01.5 Plan 04 — visual polish via frontend-design skill (D-D1).
 *
 * Design intent (Plan 04):
 *  - Narrow centered column (max-w-xl) with generous vertical breathing.
 *  - Strong typographic hierarchy: heading text-4xl font-bold → tagline text-lg
 *    muted-foreground → CTA anchored below.
 *  - Controlled precision aesthetic — no decorative elements, no gradient.
 *    Tool credibility through restraint.
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
    <main className="flex min-h-[80vh] items-center justify-center px-6 py-16">
      <div className="mx-auto flex w-full max-w-xl flex-col gap-10">
        <div className="flex flex-col gap-4">
          <h1 className="text-foreground text-4xl leading-tight font-bold tracking-tight">
            {t('landing.heading')}
          </h1>
          <p className="text-muted-foreground max-w-md text-lg leading-relaxed">
            {t('landing.tagline')}
          </p>
        </div>
        <div>
          <Link href={ctaHref} className={cn(buttonVariants({ size: 'lg' }))}>
            {t(ctaKey)}
          </Link>
        </div>
      </div>
    </main>
  );
}
