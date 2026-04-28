import AuthTopBar from '@/features/auth/components/AuthTopBar';
import { StepIndicator } from '@/features/auth/components/StepIndicator';
import { TrustPanel } from '@/features/auth/components/TrustPanel';
import { GmailConnectClient } from './GmailConnectClient';

export default function OnboardingGmailConnectPage() {
  return (
    <div className="zm-auth min-h-screen">
      <AuthTopBar>
        <StepIndicator currentStep="GMAIL_CONNECTED" />
      </AuthTopBar>
      <main className="zm-auth-main">
        <div className="zm-auth-grid">
          <GmailConnectClient />
          <TrustPanel />
        </div>
      </main>
    </div>
  );
}
