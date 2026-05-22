'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';

export default function OnboardingIndexPage() {
  const router = useRouter();
  const me = useCurrentUser();
  const onboardingStep = me.data?.onboardingStep;

  useEffect(() => {
    if (!onboardingStep) return;
    if (onboardingStep === 'GMAIL_CONNECTED') router.replace('/onboarding/gmail-connect');
    else if (onboardingStep === 'TEMPLATE_SELECTED') router.replace('/onboarding/complete');
    else if (onboardingStep === 'COMPLETE') router.replace('/settings');
    else router.replace('/rules');
  }, [onboardingStep, router]);

  return null;
}
