import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import enMessages from '@/i18n/messages/en.json';
import { SenderSafetyNetList } from '@/features/triage/components/SenderSafetyNetList';
import type { ProtectedSenderResponse } from '@/features/triage/api/triage-api';

const mocks = vi.hoisted(() => ({
  mutate: vi.fn(),
  deleteMutate: vi.fn(),
}));

vi.mock('@/features/triage/hooks/useOptInSender', () => ({
  useOptInSender: () => ({ mutate: mocks.mutate, isPending: false }),
}));

vi.mock('@/features/triage/hooks/useDeleteProtectedSender', () => ({
  useDeleteProtectedSender: () => ({ mutate: mocks.deleteMutate, isPending: false }),
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

    expect(screen.getByText('No senders yet')).toBeInTheDocument();
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
    expect(screen.getAllByText('Email').length).toBeGreaterThan(0);
    expect(screen.getAllByText('System').length).toBeGreaterThan(0);
  });

  it('adds a sender pattern', async () => {
    renderWithMessages(
      <SenderSafetyNetList injectedSenders={[protectedSender('founder@example.com', false)]} />,
    );

    fireEvent.change(screen.getByPlaceholderText('ceo@acme.com or @acme.com'), {
      target: { value: '@example.com' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Add sender' }));

    expect(mocks.mutate).toHaveBeenCalledWith('@example.com', expect.any(Object));
    await waitFor(() =>
      expect(screen.getByPlaceholderText('ceo@acme.com or @acme.com')).toHaveValue(''),
    );
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
