import { fireEvent, render, screen, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { describe, expect, it, vi } from 'vitest';

import { NeedsReplyTable } from '@/features/needs-reply/components/NeedsReplyTable';
import type { NeedsReplyRow as NeedsReplyRowModel } from '@/features/needs-reply/api/needs-reply-api';
import enMessages from '@/i18n/messages/en.json';

describe('NeedsReplyTable', () => {
  it('renders both buckets at zero, one, and many thread counts', () => {
    renderWithProviders(
      <NeedsReplyTable
        activeBucket="to-reply"
        awaitingCount={1}
        rows={[needsReplyRow('thread-1'), needsReplyRow('thread-2')]}
        toReplyCount={2}
      />,
    );

    expect(screen.getByRole('tab', { name: /To reply.*2/ })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Awaiting reply.*1/ })).toBeInTheDocument();
    expect(screen.getAllByTestId('needs-reply-row')).toHaveLength(2);
  });

  it('renders loading skeleton rows and a classifying banner above stale rows', () => {
    const { rerender } = renderWithProviders(
      <NeedsReplyTable activeBucket="to-reply" isLoading rows={[]} toReplyCount={0} />,
    );
    expect(screen.getAllByTestId('needs-reply-skeleton-row')).not.toHaveLength(0);

    rerender(
      <ProviderShell>
        <NeedsReplyTable
          activeBucket="to-reply"
          isClassifying
          rows={[needsReplyRow('thread-1')]}
          toReplyCount={1}
        />
      </ProviderShell>,
    );
    expect(screen.getByText('Updating your needs-reply list…')).toBeInTheDocument();
    expect(screen.getByTestId('needs-reply-row')).toBeInTheDocument();
  });

  it('renders the empty and error states for both public buckets', () => {
    const { rerender } = renderWithProviders(
      <NeedsReplyTable activeBucket="to-reply" rows={[]} toReplyCount={0} />,
    );
    expect(screen.getByText('Inbox zero 🎉')).toBeInTheDocument();

    rerender(
      <ProviderShell>
        <NeedsReplyTable activeBucket="awaiting-their-reply" rows={[]} toReplyCount={0} />
      </ProviderShell>,
    );
    expect(screen.getByText('Nothing awaiting')).toBeInTheDocument();

    rerender(
      <ProviderShell>
        <NeedsReplyTable
          activeBucket="to-reply"
          error={new Error('request failed')}
          rows={[]}
          toReplyCount={0}
        />
      </ProviderShell>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Try again');
  });

  it('renders dense mailbox rows with selection and draft status', () => {
    const onOpenRow = vi.fn();
    renderWithProviders(
      <NeedsReplyTable
        activeBucket="to-reply"
        rows={[needsReplyRow('thread-1', { draftStatus: 'DRAFT_READY' })]}
        selectedThreadId="thread-1"
        toReplyCount={1}
        onOpenRow={onOpenRow}
      />,
    );

    const row = screen.getByTestId('needs-reply-row');
    expect(row).toHaveAttribute('aria-current', 'true');
    expect(within(row).getByText('AI drafted')).toBeInTheDocument();
    fireEvent.click(row);
    expect(onOpenRow).toHaveBeenCalledWith(expect.objectContaining({ gmailThreadId: 'thread-1' }));
  });

  it('keeps 320px list layout stable with scrollable tabs', () => {
    renderWithProviders(
      <div style={{ width: 320 }}>
        <NeedsReplyTable
          activeBucket="to-reply"
          rows={[needsReplyRow('thread-1')]}
          toReplyCount={1}
        />
      </div>,
    );

    expect(screen.getByTestId('needs-reply-tabs')).toHaveAttribute('data-overflow', 'scroll');
    expect(screen.getByTestId('needs-reply-row')).toHaveClass('min-h-[82px]');
  });
});

function renderWithProviders(children: ReactNode) {
  return render(<ProviderShell>{children}</ProviderShell>);
}

function ProviderShell({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return (
    <QueryClientProvider client={queryClient}>
      <NextIntlClientProvider locale="en" messages={enMessages}>
        {children}
      </NextIntlClientProvider>
    </QueryClientProvider>
  );
}

function needsReplyRow(
  threadId: string,
  overrides: Partial<NeedsReplyRowModel> = {},
): NeedsReplyRowModel {
  return {
    draftId: null,
    draftStatus: 'NO_DRAFT',
    gmailThreadId: threadId,
    lastActivityAt: '2026-05-12T10:30:00.000Z',
    latestMessageId: `message-${threadId}`,
    openInGmailUrl: `https://mail.google.com/mail/u/0/#all/${threadId}`,
    otherParty: 'Founding team',
    resolved: false,
    snippet: `Snippet for ${threadId}`,
    subject: `Quarterly update ${threadId}`,
    ...overrides,
  };
}
