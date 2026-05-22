import { redirect } from 'next/navigation';

import AuthTopBar from '@/features/auth/components/AuthTopBar';
import { StepIndicator } from '@/features/auth/components/StepIndicator';
import { BETA_ONBOARDING_ENABLED, ONBOARDING_BYPASS_ROUTE } from '@/features/onboarding/config';
import { CompleteClient } from './CompleteClient';

export default function OnboardingCompletePage() {
  if (!BETA_ONBOARDING_ENABLED) redirect(ONBOARDING_BYPASS_ROUTE);

  return (
    <div className="bg-background text-foreground min-h-screen [--bg-elevated:var(--card)] [--bg-subtle:var(--secondary)] [--line-strong:var(--border)] [--line:var(--border)] [--text-faint:var(--muted-foreground)] [--text-muted:var(--muted-foreground)]">
      <AuthTopBar surface="protected">
        <StepIndicator currentStep="COMPLETE" />
      </AuthTopBar>
      <main className="relative z-[1] mx-auto w-full max-w-3xl px-4 py-10 sm:px-6 sm:py-12 lg:px-8">
        <div className="grid min-w-0 gap-8">
          <CompleteClient />
        </div>
      </main>
    </div>
  );
}
