import { headers } from 'next/headers';
import { getLocale, getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { buttonVariants } from '@/components/ui/button';
import { getCurrentUser } from '@/features/account/api/me';

import type { AppLocale } from '@/i18n/routing';

/**
 * Public landing (Phase 1.3 Plan 05 — D-C3, D-D4 "Light skeleton" scope).
 *
 * Replaces `app/page.tsx → redirect('/login')`. Renders the public marketing
 * surface for everyone (authenticated or not). The CTA target is auth-aware:
 *   - Unauthenticated: → /login
 *   - Authenticated with onboardingStep !== 'COMPLETE': → /onboarding
 *   - Errors / no cookie: silent fallback → /login
 *
 * REVIEWS Revision 2 — Codex HIGH #3: the CTA uses
 * `<Link className={buttonVariants(...)}>`, NOT `<Button asChild>`. The local
 * `Button` wraps `@base-ui/react/button` and does NOT support `asChild`.
 *
 * Plan 07 will add the `landing.*` namespace; until then `t()` returns key
 * paths as fallback (warn-only i18n:check tolerates the parity gap).
 */
export default async function LandingPage() {
  const t = await getTranslations();
  // Locale read for parity with sibling layouts (and for future generateMetadata).
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const _locale = (await getLocale()) as AppLocale;

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
    <div className="mx-auto flex max-w-3xl flex-col gap-8 px-4 py-16">
      <h1 className="text-2xl font-semibold">{t('landing.heading')}</h1>
      <p className="text-muted-foreground text-base">{t('landing.tagline')}</p>
      <div>
        <Link href={ctaHref} className={buttonVariants()}>
          {t(ctaKey)}
        </Link>
      </div>
    </div>
  );
}
