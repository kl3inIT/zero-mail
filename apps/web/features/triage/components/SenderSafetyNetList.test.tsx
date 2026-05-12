import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import enMessages from '@/i18n/messages/en.json';
import { SenderSafetyNetList } from '@/features/triage/components/SenderSafetyNetList';

const mocks = vi.hoisted(() => ({
  mutate: vi.fn(),
}));

vi.mock('@/features/triage/hooks/useOptInSender', () => ({
  useOptInSender: () => ({ mutate: mocks.mutate, isPending: false }),
}));

describe('SenderSafetyNetList', () => {
  beforeEach(() => {
    mocks.mutate.mockReset();
    mocks.mutate.mockImplementation(
      (_senderEmail: string, options?: { onSuccess?: () => void }) => {
        options?.onSuccess?.();
      },
    );
  });

  it('renders the empty state', () => {
    renderWithMessages(<SenderSafetyNetList injectedSenders={[]} />);

    expect(screen.getByText('No protected senders yet')).toBeInTheDocument();
  });

  it('renders a populated sender list', () => {
    renderWithMessages(
      <SenderSafetyNetList
        injectedSenders={[
          { senderEmail: 'ceo@example.com', optedIn: false },
          { senderEmail: 'finance@example.com', optedIn: true },
        ]}
      />,
    );

    expect(screen.getByText('ceo@example.com')).toBeInTheDocument();
    expect(screen.getByText('finance@example.com')).toBeInTheDocument();
    expect(screen.getAllByText('Opted in').length).toBeGreaterThan(0);
  });

  it('opts a sender into automation', async () => {
    renderWithMessages(
      <SenderSafetyNetList
        injectedSenders={[{ senderEmail: 'founder@example.com', optedIn: false }]}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Opt into automation' }));

    expect(mocks.mutate).toHaveBeenCalledWith('founder@example.com', expect.any(Object));
    await waitFor(() => expect(screen.getAllByText('Opted in').length).toBeGreaterThan(0));
  });
});

function renderWithMessages(children: ReactNode) {
  return render(
    <NextIntlClientProvider locale="en" messages={enMessages}>
      {children}
    </NextIntlClientProvider>,
  );
}
