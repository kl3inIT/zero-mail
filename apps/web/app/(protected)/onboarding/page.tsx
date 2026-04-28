'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';

export default function OnboardingIndexPage() {
  const router = useRouter();
  const me = useCurrentUser();

  useEffect(() => {
    if (!me.data) return;
    const step = me.data.onboardingStep;
    if (step === 'GMAIL_CONNECTED') router.replace('/onboarding/template-select');
    else if (step === 'TEMPLATE_SELECTED') router.replace('/onboarding/complete');
    else if (step === 'COMPLETE') router.replace('/settings');
    else router.replace('/welcome');
  }, [me.data?.onboardingStep, router]);

  return null;
}
