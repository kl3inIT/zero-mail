import AuthTopBar from '@/features/auth/components/AuthTopBar';
import { StepIndicator } from '@/features/auth/components/StepIndicator';
import { CompleteClient } from './CompleteClient';

export default function OnboardingCompletePage() {
  return (
    <div className="bg-background min-h-screen">
      <AuthTopBar>
        <StepIndicator currentStep="COMPLETE" />
      </AuthTopBar>
      <main className="mx-auto max-w-2xl p-6 sm:p-10">
        <CompleteClient />
      </main>
    </div>
  );
}
