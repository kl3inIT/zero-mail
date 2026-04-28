import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

const replace = vi.fn();
const completeMutate = vi.fn();
let onboardingStep: string | undefined = 'COMPLETE';

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace }),
}));

vi.mock('@/features/account/hooks/useCurrentUser', () => ({
  useCurrentUser: () => ({
    data: {
      email: 'tester@example.com',
      preferredLanguage: 'vi',
      onboardingStep,
    },
  }),
}));

vi.mock('@/features/onboarding/hooks/useSelectTemplate', () => ({
  useSelectTemplate: () => ({
    isPending: false,
    mutate: vi.fn(),
  }),
}));

vi.mock('@/features/onboarding/hooks/useCompleteOnboarding', () => ({
  useCompleteOnboarding: () => ({
    mutate: completeMutate,
  }),
}));

import OnboardingPage from '@/app/(protected)/onboarding/page';

describe('/onboarding completion redirect', () => {
  it('redirects completed users to settings instead of staying on loading copy', async () => {
    onboardingStep = 'COMPLETE';
    render(<OnboardingPage />);

    await waitFor(() => expect(replace).toHaveBeenCalledWith('/settings'));
  });

  it('redirects to settings after completing onboarding from the completion CTA', () => {
    onboardingStep = 'TEMPLATE_SELECTED';
    completeMutate.mockImplementationOnce((_variables, options) => options?.onSuccess?.());

    render(<OnboardingPage />);
    fireEvent.click(screen.getByRole('button', { name: 'onboarding.completion.cta' }));

    expect(completeMutate).toHaveBeenCalled();
    expect(replace).toHaveBeenCalledWith('/settings');
  });
});
