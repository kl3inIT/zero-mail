import type { Route } from 'next';

export const BETA_ONBOARDING_ENABLED = false;
export const ONBOARDING_BYPASS_ROUTE = '/rules';

export function shouldShowBetaOnboarding(onboardingStep: string | undefined | null): boolean {
  return BETA_ONBOARDING_ENABLED && Boolean(onboardingStep && onboardingStep !== 'COMPLETE');
}

/**
 * Step-gating for the onboarding funnel.
 *
 * Each onboarding screen is only valid for the user whose `onboardingStep`
 * matches that screen. A user further along (or already COMPLETE) must be sent
 * forward to the correct screen instead of seeing the wrong step. These helpers
 * encode the EXACT same conditions/targets the client gate used to run in a
 * `useEffect`, but they are consumed by the Server Component pages via
 * `redirect()` so the correct screen renders at request time with no UI flash
 * and no broken back-button (react-doctor/nextjs-no-client-side-redirect).
 *
 * Returning `null` means "the current step is valid for this screen — render
 * it". `onboardingStep` being undefined (RSC /me fetch failed / unavailable)
 * also returns `null` so the page still renders and the client TanStack Query
 * hook owns the recovery — never block the funnel on a /me failure.
 */

/** Where the index (`/onboarding`) sends a user based on their current step. */
export function onboardingIndexRedirect(onboardingStep: string | undefined | null): Route | null {
  if (!onboardingStep) return null;
  if (onboardingStep === 'GMAIL_CONNECTED') return '/onboarding/gmail-connect';
  if (onboardingStep === 'TEMPLATE_SELECTED') return '/onboarding/complete';
  if (onboardingStep === 'COMPLETE') return '/settings';
  return '/rules';
}

/** Gate for `/onboarding/gmail-connect`. */
export function gmailConnectStepRedirect(onboardingStep: string | undefined | null): Route | null {
  if (!onboardingStep) return null;
  if (onboardingStep === 'TEMPLATE_SELECTED') return '/onboarding/complete';
  if (onboardingStep === 'COMPLETE') return '/settings';
  return null;
}

/** Gate for `/onboarding/template-select`. */
export function templateSelectStepRedirect(
  onboardingStep: string | undefined | null,
): Route | null {
  if (!onboardingStep) return null;
  if (onboardingStep === 'TEMPLATE_SELECTED') return '/onboarding/complete';
  if (onboardingStep === 'COMPLETE') return '/settings';
  return null;
}

/** Gate for `/onboarding/complete`. */
export function completeStepRedirect(onboardingStep: string | undefined | null): Route | null {
  if (!onboardingStep) return null;
  if (onboardingStep === 'GMAIL_CONNECTED') return '/onboarding/template-select';
  if (onboardingStep === 'COMPLETE') return '/settings';
  return null;
}
