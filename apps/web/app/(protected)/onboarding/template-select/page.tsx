import AuthTopBar from '@/features/auth/components/AuthTopBar';
import { StepIndicator } from '@/features/auth/components/StepIndicator';
import { TemplateSelectClient } from './TemplateSelectClient';

export default function OnboardingTemplateSelectPage() {
  return (
    <div className="bg-background min-h-screen">
      <AuthTopBar>
        <StepIndicator currentStep="TEMPLATE_SELECTED" />
      </AuthTopBar>
      <main className="mx-auto max-w-2xl p-6 sm:p-10">
        <TemplateSelectClient />
      </main>
    </div>
  );
}
