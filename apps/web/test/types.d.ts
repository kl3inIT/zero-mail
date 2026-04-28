// Wave 0 type stubs — declares module shapes for components that do not yet exist.
// These stubs allow `tsc --noEmit` to pass while the specs remain RED at runtime
// (module-not-found at Vitest resolution time).
// Remove each stub as its implementation lands in Waves 1–3.

declare module '@/features/landing/components/ZMLogoMark' {
  import type * as React from 'react';
  export interface ZMLogoMarkProps {
    size?: number;
  }
  const ZMLogoMark: React.ComponentType<ZMLogoMarkProps>;
  export default ZMLogoMark;
}

declare module '@/features/auth/components/StepIndicator' {
  import type * as React from 'react';
  type OnboardingStepValue = 'GMAIL_CONNECTED' | 'TEMPLATE_SELECTED' | 'COMPLETE';
  export interface StepIndicatorProps {
    currentStep: OnboardingStepValue;
  }
  export function StepIndicator(props: StepIndicatorProps): Promise<React.ReactElement>;
}
