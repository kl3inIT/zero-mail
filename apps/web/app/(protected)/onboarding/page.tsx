import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';

import type { CurrentUser } from '@/features/account/api/account-api';
import { getCurrentUserCached } from '@/features/account/api/account-api';
import {
  BETA_ONBOARDING_ENABLED,
  ONBOARDING_BYPASS_ROUTE,
  onboardingIndexRedirect,
} from '@/features/onboarding/config';
import { OnboardingIndexClient } from './OnboardingIndexClient';

/**
 * Onboarding entry — routes the user to the correct funnel step server-side.
 *
 * Step gating runs here (App Router best practice) via `getCurrentUserCached` +
 * `redirect()` so the right screen renders at request time without a client-side
 * `useEffect` redirect flash. The /me fetch is wrapped in try/catch so a backend
 * failure degrades to rendering the client fallback rather than a 500; the
 * `redirect()` call is intentionally outside the try/catch because Next.js
 * implements `redirect()` by throwing.
 */
export default async function OnboardingIndexPage() {
  if (!BETA_ONBOARDING_ENABLED) redirect(ONBOARDING_BYPASS_ROUTE);

  let currentUser: CurrentUser | undefined;
  try {
    currentUser = await getCurrentUserCached((await cookies()).toString());
  } catch {
    currentUser = undefined;
  }

  const target = onboardingIndexRedirect(currentUser?.onboardingStep);
  if (target) redirect(target);

  return <OnboardingIndexClient />;
}
