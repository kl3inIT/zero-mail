import AuthTopBar from '@/features/auth/components/AuthTopBar';
import { StepIndicator } from '@/features/auth/components/StepIndicator';
import { TrustPanel } from '@/features/auth/components/TrustPanel';
import { GmailConnectClient } from './GmailConnectClient';

export default function OnboardingGmailConnectPage() {
  return (
    <div className="bg-background text-foreground min-h-screen [--bg-elevated:var(--card)] [--bg-subtle:var(--secondary)] [--line-strong:var(--border)] [--line:var(--border)] [--text-faint:var(--muted-foreground)] [--text-muted:var(--muted-foreground)]">
      <AuthTopBar surface="protected">
        <StepIndicator currentStep="GMAIL_CONNECTED" />
      </AuthTopBar>
      <main className="relative z-[1] mx-auto w-full max-w-6xl px-4 py-10 sm:px-6 sm:py-12 lg:px-8">
        <div className="grid min-w-0 gap-8 lg:grid-cols-[minmax(0,1fr)_380px]">
          <GmailConnectClient />
          <TrustPanel />
        </div>
      </main>
    </div>
  );
}
