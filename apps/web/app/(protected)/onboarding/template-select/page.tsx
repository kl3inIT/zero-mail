import AuthTopBar from '@/features/auth/components/AuthTopBar';
import { StepIndicator } from '@/features/auth/components/StepIndicator';
import { TrustPanel } from '@/features/auth/components/TrustPanel';
import { TemplateSelectClient } from './TemplateSelectClient';

export default function OnboardingTemplateSelectPage() {
  return (
    <div className="zm-auth min-h-screen">
      <AuthTopBar>
        <StepIndicator currentStep="TEMPLATE_SELECTED" />
      </AuthTopBar>
      <main className="zm-auth-main">
        <div className="zm-auth-grid">
          <TemplateSelectClient />
          <TrustPanel />
        </div>
      </main>
    </div>
  );
}
