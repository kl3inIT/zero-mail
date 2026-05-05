import { fireEvent, render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import enMessages from '@/i18n/messages/en.json';

const mocks = vi.hoisted(() => ({
  mutate: vi.fn(),
  useCurrentUser: vi.fn(),
}));

vi.mock('@/features/account/hooks/useCurrentUser', () => ({
  useCurrentUser: mocks.useCurrentUser,
}));

vi.mock('@/features/triage/hooks/useToggleTriagePause', () => ({
  useToggleTriagePause: () => ({ mutate: mocks.mutate }),
}));

import { PauseBanner } from '@/features/triage/components/PauseBanner';

function renderBanner() {
  return render(
    <NextIntlClientProvider locale="en" messages={enMessages}>
      <PauseBanner />
    </NextIntlClientProvider>,
  );
}

describe('PauseBanner', () => {
  beforeEach(() => {
    mocks.mutate.mockReset();
    mocks.useCurrentUser.mockReset();
  });

  it('renders_when_triagePaused_true', () => {
    mocks.useCurrentUser.mockReturnValue({
      triagePaused: true,
      data: { triagePaused: true },
    });

    renderBanner();

    const alert = screen.getByRole('alert');
    expect(alert).toBeInTheDocument();
    expect(alert.textContent ?? '').toMatch(/triage/i);
  });

  it('notRendered_when_triagePaused_false', () => {
    mocks.useCurrentUser.mockReturnValue({
      triagePaused: false,
      data: { triagePaused: false },
    });

    renderBanner();

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('unpauses_on_cta_click', () => {
    mocks.useCurrentUser.mockReturnValue({
      triagePaused: true,
      data: { triagePaused: true },
    });

    renderBanner();
    fireEvent.click(
      screen.getByRole('button', {
        name: enMessages.settings.triage.pause.banner.unpause,
      }),
    );

    expect(mocks.mutate).toHaveBeenCalledWith(false);
  });
});
