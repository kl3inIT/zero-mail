import AuthTopBar from '@/features/auth/components/AuthTopBar';
import { StepIndicator } from '@/features/auth/components/StepIndicator';
import { TemplateSelectClient } from './TemplateSelectClient';

export default function OnboardingTemplateSelectPage() {
  return (
    <div className="bg-background text-foreground min-h-screen [--bg-elevated:var(--card)] [--bg-subtle:var(--secondary)] [--line-strong:var(--border)] [--line:var(--border)] [--text-faint:var(--muted-foreground)] [--text-muted:var(--muted-foreground)]">
      <AuthTopBar surface="protected">
        <StepIndicator currentStep="TEMPLATE_SELECTED" />
      </AuthTopBar>
      <main className="relative z-[1] mx-auto w-full max-w-3xl px-4 py-10 sm:px-6 sm:py-12 lg:px-8">
        <div className="grid min-w-0 gap-8">
          <TemplateSelectClient />
        </div>
      </main>
    </div>
  );
}
