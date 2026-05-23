import { redirect } from 'next/navigation';

import { BETA_ONBOARDING_ENABLED, ONBOARDING_BYPASS_ROUTE } from '@/features/onboarding/config';
import { OnboardingIndexClient } from './OnboardingIndexClient';

export default function OnboardingIndexPage() {
  if (!BETA_ONBOARDING_ENABLED) redirect(ONBOARDING_BYPASS_ROUTE);

  return <OnboardingIndexClient />;
}
