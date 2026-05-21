import { render, screen, within } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';

import enMessages from '@/i18n/messages/en.json';
import { LedgerHistory } from '@/features/billing/components/LedgerHistory';
import { LedgerTable, type LedgerEntry } from '@/features/billing/components/LedgerTable';

const ledgerHookMocks = vi.hoisted(() => ({
  useLedgerHistory: vi.fn(),
}));

vi.mock('@/features/billing/hooks/useLedgerHistory', () => ({
  useLedgerHistory: () => ledgerHookMocks.useLedgerHistory(),
}));

describe('LedgerTable', () => {
  it('renders injected ledger rows without a backend endpoint', () => {
    renderWithIntl(<LedgerTable injectedRows={[topupEntry(), settleEntry()]} />);

    expect(screen.getAllByTestId('ledger-row')).toHaveLength(2);
    expect(screen.getByText('Top-up credited')).toBeInTheDocument();
    expect(screen.getByText('Rule preview charge')).toBeInTheDocument();
  });

  it('uses mono amounts and green-soft styling for top-up rows', () => {
    renderWithIntl(<LedgerTable injectedRows={[topupEntry()]} />);

    const topupRow = screen.getByTestId('ledger-row');
    expect(topupRow).toHaveClass('bg-green-soft/40');
    expect(within(topupRow).getByText('+45')).toHaveClass('font-mono', 'text-green');
  });

  it('renders ledger history rows from the backend endpoint', () => {
    ledgerHookMocks.useLedgerHistory.mockReturnValue({
      isPending: false,
      isError: false,
      data: { pages: [{ entries: [topupEntry(), settleEntry()], nextCursor: null }] },
      refetch: vi.fn(),
    });

    renderWithIntl(<LedgerHistory />);

    expect(screen.getAllByTestId('ledger-row')).toHaveLength(2);
    expect(screen.getByText('Top-up credited')).toBeInTheDocument();
    expect(screen.getByText('Rule preview charge')).toBeInTheDocument();
    expect(screen.queryByText('No transactions yet')).not.toBeInTheDocument();
  });

  it('renders an empty state when the backend ledger is available but empty', () => {
    ledgerHookMocks.useLedgerHistory.mockReturnValue({
      isPending: false,
      isError: false,
      data: { pages: [{ entries: [], nextCursor: null }] },
      refetch: vi.fn(),
    });

    renderWithIntl(<LedgerHistory />);

    expect(screen.getByText('No transactions yet')).toBeInTheDocument();
    expect(screen.queryByTestId('ledger-table')).not.toBeInTheDocument();
  });
});

function renderWithIntl(children: ReactNode) {
  return render(
    <NextIntlClientProvider locale="en" messages={enMessages}>
      {children}
    </NextIntlClientProvider>,
  );
}

function topupEntry(): LedgerEntry {
  return {
    id: 'ledger-topup',
    timestamp: '2026-05-12T10:00:00.000Z',
    type: 'topup',
    description: 'Top-up credited',
    amountCredits: 45,
    balanceAfterCredits: 57,
    reference: 'ZMABCD2345',
  };
}

function settleEntry(): LedgerEntry {
  return {
    id: 'ledger-settle',
    timestamp: '2026-05-12T10:10:00.000Z',
    type: 'settle',
    description: 'Rule preview charge',
    amountCredits: -2,
    balanceAfterCredits: 55,
    reference: 'TRIAGE',
  };
}
