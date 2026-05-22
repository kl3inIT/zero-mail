export const BETA_ONBOARDING_ENABLED = false;
export const ONBOARDING_BYPASS_ROUTE = '/rules';

export function shouldShowBetaOnboarding(onboardingStep: string | undefined | null): boolean {
  return BETA_ONBOARDING_ENABLED && Boolean(onboardingStep && onboardingStep !== 'COMPLETE');
}
