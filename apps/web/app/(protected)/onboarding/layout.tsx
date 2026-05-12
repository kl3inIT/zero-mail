/**
 * Onboarding inherits protected providers but intentionally suppresses the app
 * shell so the setup funnel keeps its focused chrome.
 */
export default function OnboardingLayout({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}
