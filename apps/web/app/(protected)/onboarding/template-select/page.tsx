import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';

import type { CurrentUser } from '@/features/account/api/account-api';
import { getCurrentUserCached } from '@/features/account/api/account-api';
import AuthTopBar from '@/features/auth/components/AuthTopBar';
import { StepIndicator } from '@/features/auth/components/StepIndicator';
import {
  BETA_ONBOARDING_ENABLED,
  ONBOARDING_BYPASS_ROUTE,
  templateSelectStepRedirect,
} from '@/features/onboarding/config';
import { TemplateSelectClient } from './TemplateSelectClient';

export default async function OnboardingTemplateSelectPage() {
  if (!BETA_ONBOARDING_ENABLED) redirect(ONBOARDING_BYPASS_ROUTE);

  let currentUser: CurrentUser | undefined;
  try {
    currentUser = await getCurrentUserCached((await cookies()).toString());
  } catch {
    currentUser = undefined;
  }

  const target = templateSelectStepRedirect(currentUser?.onboardingStep);
  if (target) redirect(target);

  return (
    <div className="bg-background text-foreground min-h-screen [--bg-elevated:var(--card)] [--bg-subtle:var(--secondary)] [--line-strong:var(--border)] [--line:var(--border)] [--text-faint:var(--muted-foreground)] [--text-muted:var(--muted-foreground)]">
      <AuthTopBar surface="protected">
        <StepIndicator currentStep="TEMPLATE_SELECTED" />
      </AuthTopBar>
      <main className="relative z-[1] mx-auto w-full max-w-3xl px-4 py-10 sm:px-6 sm:py-12 lg:px-8">
        <div className="grid min-w-0 gap-8">
          <TemplateSelectClient initialUser={currentUser} />
        </div>
      </main>
    </div>
  );
}
