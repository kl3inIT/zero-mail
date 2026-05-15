import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const replace = vi.fn();

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace }),
  useSearchParams: () => new URLSearchParams('code=ZM123456'),
}));

vi.mock('@/features/billing/hooks/useBillingBalance', () => ({
  useBillingBalance: () => ({ data: { availableCredits: 25 } }),
}));

vi.mock('./TopupPackageSelector', () => ({
  TopupPackageSelector: () => <div data-testid="package-selector" />,
}));

vi.mock('./TopupInstructions', () => ({
  TopupInstructions: ({ onCredited }: { onCredited: () => void }) => (
    <button type="button" onClick={onCredited}>
      credited
    </button>
  ),
}));

vi.mock('./TopupExpired', () => ({
  TopupExpired: () => <div data-testid="expired-step" />,
}));

import { TopupClient } from '@/features/billing/components/TopupClient';

describe('TopupClient', () => {
  beforeEach(() => {
    replace.mockReset();
    window.sessionStorage.clear();
    window.sessionStorage.setItem(
      'zero-mail:billing-topup:ZM123456',
      JSON.stringify({
        code: 'ZM123456',
        amountVnd: 100000,
        expiresAt: '2099-05-15T00:00:00Z',
        baselineCredits: 25,
        qrPayload: '',
      }),
    );
  });

  it('redirects to the dedicated top-up success route when the active intent is credited', () => {
    render(<TopupClient />);

    fireEvent.click(screen.getByRole('button', { name: 'credited' }));

    expect(window.sessionStorage.getItem('zero-mail:billing-topup:ZM123456')).toBeNull();
    expect(replace).toHaveBeenCalledWith('/billing/top-up/success?code=ZM123456', {
      scroll: false,
    });
  });
});
