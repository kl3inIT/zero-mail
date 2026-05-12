import { render, screen, within } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import type { ComponentType, ReactNode } from 'react';
import { describe, expect, it } from 'vitest';

import enMessages from '@/i18n/messages/en.json';

const NEEDS_REPLY_TABLE_MODULE = '@/features/needs-reply/components/NeedsReplyTable';

describe.skip('NeedsReplyTable - RED until Plan 06 lands NeedsReplyTable', () => {
  it('renders both buckets at zero, one, and many thread counts', async () => {
    const { NeedsReplyTable } = await loadNeedsReplyTable();

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

  it('renders loading skeleton rows and a classifying banner above stale rows', async () => {
    const { NeedsReplyTable } = await loadNeedsReplyTable();

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
    expect(screen.getByText(/classifying/i)).toBeInTheDocument();
    expect(screen.getByTestId('needs-reply-row')).toBeInTheDocument();
  });

  it('renders the empty and error states for both public buckets', async () => {
    const { NeedsReplyTable } = await loadNeedsReplyTable();

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

  it('exposes draft, Gmail, status, resolved, and public-bucket affordances on a row', async () => {
    const { NeedsReplyTable } = await loadNeedsReplyTable();

    renderWithProviders(
      <NeedsReplyTable
        activeBucket="to-reply"
        rows={[needsReplyRow('thread-1', { draftStatus: 'DRAFT_READY' })]}
        toReplyCount={1}
      />,
    );

    const row = screen.getByTestId('needs-reply-row');
    expect(within(row).getByRole('button', { name: 'Regenerate draft' })).toBeInTheDocument();
    expect(within(row).getByRole('link', { name: 'Open in Gmail' })).toHaveAttribute(
      'href',
      'https://mail.google.com/mail/u/0/#all/thread-1',
    );
    expect(within(row).getByText('Draft ready')).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: 'Mark resolved' })).toBeInTheDocument();
  });

  it('keeps 320px layout actions stable with icon-only row controls', async () => {
    const { NeedsReplyTable } = await loadNeedsReplyTable();

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
    expect(screen.getByRole('button', { name: 'Draft reply' })).toBeInTheDocument();
  });
});

async function loadNeedsReplyTable(): Promise<{
  NeedsReplyTable: ComponentType<Record<string, unknown>>;
}> {
  return import(NEEDS_REPLY_TABLE_MODULE);
}

function renderWithProviders(children: ReactNode) {
  return render(<ProviderShell>{children}</ProviderShell>);
}

function ProviderShell({ children }: { children: ReactNode }) {
  return (
    <NextIntlClientProvider locale="en" messages={enMessages}>
      {children}
    </NextIntlClientProvider>
  );
}

function needsReplyRow(threadId: string, overrides: Record<string, unknown> = {}) {
  return {
    draftId: null,
    draftStatus: 'NO_DRAFT',
    gmailThreadId: threadId,
    lastActivityAt: '2026-05-12T10:30:00.000Z',
    otherParty: 'Founding team',
    subject: `Quarterly update ${threadId}`,
    ...overrides,
  };
}
