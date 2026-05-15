import { render, screen, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';

import enMessages from '@/i18n/messages/en.json';
import { AuditLog } from '@/features/triage/components/AuditLog';
import type { AuditEntry } from '@/features/triage/api/triage-api';

const NOW = new Date('2026-05-12T12:00:00.000Z');

describe('AuditLog', () => {
  it('renders the empty state for a zero-entry result', () => {
    renderWithProviders(<AuditLog injectedData={{ entries: [] }} now={NOW} />);

    expect(screen.getByText('No email actions yet')).toBeInTheDocument();
  });

  it('renders one entry across the shared table and card models', () => {
    renderWithProviders(<AuditLog injectedData={{ entries: [auditEntry('one')] }} now={NOW} />);

    expect(screen.getAllByTestId('audit-table-row')).toHaveLength(1);
    expect(screen.getAllByTestId('audit-card')).toHaveLength(1);
    expect(screen.getAllByText('Archive receipts')).not.toHaveLength(0);
  });

  it('renders a page-full of entries and the load-older affordance', () => {
    const entries = Array.from({ length: 8 }, (_, index) => auditEntry(`entry-${index}`));
    const onLoadMore = vi.fn();

    renderWithProviders(
      <AuditLog injectedData={{ entries, hasNextPage: true, onLoadMore }} now={NOW} />,
    );

    expect(screen.getAllByTestId('audit-table-row')).toHaveLength(8);
    expect(screen.getByRole('button', { name: 'Load more' })).toBeInTheDocument();
  });

  it('places the undo boundary between the last in-window and first out-of-window entry', () => {
    const inWindow = auditEntry('in-window', {
      reason: 'In-window evidence stays before the boundary',
      undoableUntil: '2026-05-20T12:00:00.000Z',
    });
    const outOfWindow = auditEntry('out-of-window', {
      reason: 'Out-of-window evidence stays after the boundary',
      undoableUntil: '2026-04-01T12:00:00.000Z',
    });

    renderWithProviders(<AuditLog injectedData={{ entries: [inWindow, outOfWindow] }} now={NOW} />);

    const cardListText = screen.getByTestId('audit-card-list').textContent ?? '';
    expect(cardListText.indexOf('In-window evidence')).toBeLessThan(
      cardListText.indexOf('Older than 30 days - undo no longer available'),
    );
    expect(cardListText.indexOf('Older than 30 days - undo no longer available')).toBeLessThan(
      cardListText.indexOf('Out-of-window evidence'),
    );
  });

  it('renders the full reason on the card variant', () => {
    const fullReason =
      'Matched the protected sender and archived the message only after the deterministic rule confirmed the receipt pattern.';

    renderWithProviders(
      <AuditLog
        injectedData={{ entries: [auditEntry('full-reason', { reason: fullReason })] }}
        now={NOW}
      />,
    );

    expect(within(screen.getByTestId('audit-card-list')).getByText(fullReason)).toBeInTheDocument();
  });
});

function renderWithProviders(children: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <NextIntlClientProvider locale="en" messages={enMessages}>
        {children}
      </NextIntlClientProvider>
    </QueryClientProvider>,
  );
}

function auditEntry(id: string, overrides: Partial<AuditEntry> = {}): AuditEntry {
  return {
    id,
    timestamp: '2026-05-12T10:30:00.000Z',
    action: 'archive',
    actionLabel: 'Archive',
    ruleName: 'Archive receipts',
    reason: 'Matched sender domain and receipt keywords.',
    inverseAction: 'Move the message back to Inbox.',
    messageRef: {
      subject: `Receipt ${id}`,
      sender: 'billing@example.com',
    },
    undoableUntil: '2026-05-20T12:00:00.000Z',
    ...overrides,
  };
}
