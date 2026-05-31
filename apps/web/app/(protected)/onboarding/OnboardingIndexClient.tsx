'use client';

/**
 * Fallback view for the onboarding entry.
 *
 * Step gating lives server-side in `page.tsx` (`getCurrentUserCached` +
 * `redirect()`), so under normal conditions this component never paints — the
 * server has already routed the user to the correct funnel step. It only
 * renders on the degraded path where the RSC /me fetch failed (backend down /
 * e2e mode) and the page fell through. We show a neutral placeholder rather
 * than a client-side `useEffect` redirect, which would flash the wrong UI and
 * break the back button.
 */
export function OnboardingIndexClient() {
  return null;
}
