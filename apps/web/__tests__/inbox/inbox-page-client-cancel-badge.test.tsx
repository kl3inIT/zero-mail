// Phase 12 G1 (CAL-TRIAGE-02): pins the inbox calendar Badge branch that was a dead branch until
// the projection -> DTO -> schema wiring shipped. Renders InboxMessageRow directly because the
// full virtualized InboxPageClient yields no rows in jsdom (the scroll container measures 0px so
// TanStack Virtual returns no items). The branch under test is InboxPageClient.tsx lines ~593-610:
//   message.messageClass === 'CANCEL'     -> [data-testid='inbox-message-cancellation-badge']
//   message.messageClass === 'RESCHEDULE' -> [data-testid='inbox-message-time-changed-badge']
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';

import enMessages from '@/i18n/messages/en.json';
import { InboxMessageRow } from '@/features/inbox/components/InboxPageClient';
import type { InboxMessage } from '@/features/inbox/api/inbox-api';

function baseMessage(overrides: Partial<InboxMessage>): InboxMessage {
  return {
    gmailMessageId: 'msg-1',
    gmailThreadId: 'thread-1',
    subject: 'Test subject',
    snippet: 'snippet',
    from: 'tester@example.com',
    to: [],
    cc: [],
    receivedAt: '2026-06-20T08:00:00Z',
    labelIds: ['INBOX'],
    labels: [{ id: 'INBOX', name: 'Inbox' }],
    unread: false,
    hasAttachment: false,
    openInGmailUrl: 'https://mail.google.com/mail/u/0/#inbox/thread-1',
    messageClass: null,
    eventDt: null,
    ...overrides,
  };
}

function renderRow(message: InboxMessage) {
  return render(
    <NextIntlClientProvider locale="en" messages={enMessages}>
      <InboxMessageRow message={message} active={false} locale="en" onSelect={() => {}} />
    </NextIntlClientProvider>,
  );
}

describe('InboxMessageRow calendar badge', () => {
  it('renders the cancellation badge for a CANCEL row', () => {
    renderRow(baseMessage({ messageClass: 'CANCEL', eventDt: '2026-06-25T15:00:00Z' }));

    const badge = screen.getByTestId('inbox-message-cancellation-badge');
    expect(badge).toBeVisible();
    expect(screen.queryByTestId('inbox-message-time-changed-badge')).not.toBeInTheDocument();
  });

  it('renders the time-changed badge for a RESCHEDULE row', () => {
    renderRow(baseMessage({ messageClass: 'RESCHEDULE', eventDt: '2026-06-25T15:00:00Z' }));

    const badge = screen.getByTestId('inbox-message-time-changed-badge');
    expect(badge).toBeVisible();
    expect(screen.queryByTestId('inbox-message-cancellation-badge')).not.toBeInTheDocument();
  });

  it('renders no calendar badge for a non-calendar row', () => {
    renderRow(baseMessage({ messageClass: null, eventDt: null }));

    expect(screen.queryByTestId('inbox-message-cancellation-badge')).not.toBeInTheDocument();
    expect(screen.queryByTestId('inbox-message-time-changed-badge')).not.toBeInTheDocument();
  });
});
