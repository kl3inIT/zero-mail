import { expect, test } from '@playwright/test';

import {
  createChromeMockState,
  installChromeApiMock,
  seedAuthenticatedSession,
} from './chrome-test-utils';

// Phase 12 G1 (CAL-TRIAGE-02) e2e — proves the inbox calendar Badge branch
// (InboxPageClient.tsx) is no longer a dead branch end-to-end. We mock the inbox page endpoint to
// return a CANCEL row + a RESCHEDULE row + a plain row, navigate to /inbox, and assert the
// outline badges render.
//
// Mock surface the inbox page calls (stacked AFTER installChromeApiMock so these specific handlers
// win over chrome's catch-all 204):
//   GET /api/gmail/active-mailbox   -> the active connected mailbox the page reads
//   GET /api/gmail/inbox            -> the projection-backed inbox page
//
// Auto-run is gated on a reachable dev server; if the dev server is not running in the executor
// session, this spec ships as a durable gate (precedent: W3 calendar-settings.spec.ts).

const GMAIL_CONNECTION_ID = '00000000-0000-0000-0000-00000000abcd';

interface InboxRow {
  gmailMessageId: string;
  gmailThreadId: string;
  subject: string;
  snippet: string;
  from: string;
  to: string[];
  cc: string[];
  receivedAt: string;
  labelIds: string[];
  labels: Array<{ id: string; name: string }>;
  unread: boolean;
  hasAttachment: boolean;
  openInGmailUrl: string;
  messageClass: 'INVITE' | 'CANCEL' | 'RESCHEDULE' | 'RSVP' | null;
  eventDt: string | null;
}

function inboxRow(overrides: Partial<InboxRow>): InboxRow {
  return {
    gmailMessageId: 'msg-plain',
    gmailThreadId: 'thread-plain',
    subject: 'Plain message',
    snippet: 'snippet',
    from: 'tester@example.com',
    to: [],
    cc: [],
    receivedAt: '2026-06-20T08:00:00Z',
    labelIds: ['INBOX'],
    labels: [{ id: 'INBOX', name: 'Inbox' }],
    unread: false,
    hasAttachment: false,
    openInGmailUrl: 'https://mail.google.com/mail/u/0/#inbox/thread-plain',
    messageClass: null,
    eventDt: null,
    ...overrides,
  };
}

function inboxPage(items: InboxRow[]) {
  return {
    items,
    nextCursor: null,
    loadedCount: items.length,
    maxMessages: 100,
    dataSource: 'PROJECTION',
  };
}

async function installInboxMocks(page: import('@playwright/test').Page, items: InboxRow[]) {
  await page.route(/https?:\/\/[^/]+\/api\/gmail\/active-mailbox$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        gmailConnectionId: GMAIL_CONNECTION_ID,
        googleEmail: 'founder@example.com',
        status: 'CONNECTED',
      }),
    });
  });
  await page.route(/https?:\/\/[^/]+\/api\/gmail\/inbox(\?.*)?$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(inboxPage(items)),
    });
  });
}

test('renders the cancellation badge for a CANCEL calendar row', async ({ page }) => {
  const chromeState = createChromeMockState();
  await seedAuthenticatedSession(page, 'en');
  await installChromeApiMock(page, chromeState);
  await installInboxMocks(page, [
    inboxRow({
      gmailMessageId: 'msg-cancel',
      gmailThreadId: 'thread-cancel',
      subject: 'Cancelled: Q3 sync',
      messageClass: 'CANCEL',
      eventDt: '2026-06-25T15:00:00Z',
    }),
    inboxRow({}),
  ]);

  await page.goto('/inbox', { waitUntil: 'domcontentloaded' });

  await expect(page.getByTestId('inbox-message-list')).toBeVisible();
  await expect(page.getByTestId('inbox-message-cancellation-badge')).toBeVisible();
  await page.screenshot({ path: 'inbox-calendar-cancel-badge.png', fullPage: true });
});

test('renders the time-changed badge for a RESCHEDULE calendar row', async ({ page }) => {
  const chromeState = createChromeMockState();
  await seedAuthenticatedSession(page, 'en');
  await installChromeApiMock(page, chromeState);
  await installInboxMocks(page, [
    inboxRow({
      gmailMessageId: 'msg-reschedule',
      gmailThreadId: 'thread-reschedule',
      subject: 'Rescheduled: Q3 sync',
      messageClass: 'RESCHEDULE',
      eventDt: '2026-06-26T15:00:00Z',
    }),
    inboxRow({}),
  ]);

  await page.goto('/inbox', { waitUntil: 'domcontentloaded' });

  await expect(page.getByTestId('inbox-message-list')).toBeVisible();
  await expect(page.getByTestId('inbox-message-time-changed-badge')).toBeVisible();
});
