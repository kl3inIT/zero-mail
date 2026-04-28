import AuthTopBar from '@/features/auth/components/AuthTopBar';
import { StepIndicator } from '@/features/auth/components/StepIndicator';
import { TrustPanel } from '@/features/auth/components/TrustPanel';
import { CompleteClient } from './CompleteClient';

export default function OnboardingCompletePage() {
  return (
    <div className="zm-auth min-h-screen">
      <AuthTopBar>
        <StepIndicator currentStep="COMPLETE" />
      </AuthTopBar>
      <main className="zm-auth-main">
        <div className="zm-auth-grid">
          <CompleteClient />
          <TrustPanel />
        </div>
      </main>
    </div>
  );
}
