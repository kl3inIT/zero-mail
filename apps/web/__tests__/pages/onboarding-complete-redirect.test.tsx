import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { completeStepRedirect, onboardingIndexRedirect } from '@/features/onboarding/config';

const replace = vi.fn();
const completeMutate = vi.fn();

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
      onboardingStep: 'TEMPLATE_SELECTED',
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

import { CompleteClient } from '@/app/(protected)/onboarding/complete/CompleteClient';

/**
 * Onboarding step gating moved out of client `useEffect` redirects and into the
 * Server Component pages (`getCurrentUserCached` + `redirect()` from
 * next/navigation), per react-doctor/nextjs-no-client-side-redirect. The pure
 * step→target mapping is unit-tested here; the actual `redirect()` happens in
 * each route's `page.tsx` at request time.
 */
describe('/onboarding step gating (server-side)', () => {
  it('routes a COMPLETE user from the index to /settings', () => {
    expect(onboardingIndexRedirect('COMPLETE')).toBe('/settings');
  });

  it('routes a GMAIL_CONNECTED user from /complete forward to template-select', () => {
    expect(completeStepRedirect('GMAIL_CONNECTED')).toBe('/onboarding/template-select');
    expect(completeStepRedirect('COMPLETE')).toBe('/settings');
  });

  it('does not redirect when the step is unknown/unavailable (RSC fetch failed)', () => {
    expect(onboardingIndexRedirect(undefined)).toBeNull();
    expect(completeStepRedirect(undefined)).toBeNull();
  });
});

describe('/onboarding completion CTA', () => {
  it('redirects to settings after completing onboarding from the completion CTA', () => {
    completeMutate.mockImplementationOnce((_variables, options) => options?.onSuccess?.());

    render(<CompleteClient />);
    fireEvent.click(screen.getByRole('button', { name: 'onboarding.completion.cta' }));

    expect(completeMutate).toHaveBeenCalled();
    expect(replace).toHaveBeenCalledWith('/settings');
  });
});
