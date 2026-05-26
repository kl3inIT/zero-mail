import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import enMessages from '@/i18n/messages/en.json';
import { SenderSafetyNetList } from '@/features/triage/components/SenderSafetyNetList';
import type { ProtectedSenderResponse } from '@/features/triage/api/triage-api';

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
          protectedSender('ceo@example.com', false),
          protectedSender('finance@example.com', true),
        ]}
      />,
    );

    expect(screen.getByText('ceo@example.com')).toBeInTheDocument();
    expect(screen.getByText('finance@example.com')).toBeInTheDocument();
    expect(screen.getAllByText('Opted in').length).toBeGreaterThan(0);
  });

  it('opts a sender into automation', async () => {
    renderWithMessages(
      <SenderSafetyNetList injectedSenders={[protectedSender('founder@example.com', false)]} />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Opt into automation' }));

    expect(mocks.mutate).toHaveBeenCalledWith('founder@example.com', expect.any(Object));
    await waitFor(() => expect(screen.getAllByText('Opted in').length).toBeGreaterThan(0));
  });
});

function protectedSender(senderEmail: string, optedIn: boolean): ProtectedSenderResponse {
  return {
    id: `00000000-0000-0000-0000-${senderEmail === 'finance@example.com' ? '000000000002' : '000000000001'}`,
    pattern: senderEmail,
    patternKind: 'EMAIL',
    createdByUser: false,
    createdAt: '2026-05-26T00:00:00.000Z',
    senderEmail,
    optedIn,
  };
}

function renderWithMessages(children: ReactNode) {
  return render(
    <NextIntlClientProvider locale="en" messages={enMessages}>
      {children}
    </NextIntlClientProvider>,
  );
}
