import AuthTopBar from '@/features/auth/components/AuthTopBar';
import { StepIndicator } from '@/features/auth/components/StepIndicator';
import { GmailConnectClient } from './GmailConnectClient';

export default function OnboardingGmailConnectPage() {
  return (
    <div className="bg-background min-h-screen">
      <AuthTopBar>
        <StepIndicator currentStep="GMAIL_CONNECTED" />
      </AuthTopBar>
      <main className="mx-auto max-w-2xl p-6 sm:p-10">
        <GmailConnectClient />
      </main>
    </div>
  );
}
