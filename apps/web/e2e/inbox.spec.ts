import { expect, test, type Page, type Route } from '@playwright/test';

import {
  API_ROUTE_PATTERN,
  createChromeMockState,
  expectNoHorizontalOverflow,
  installChromeApiMock,
  seedAuthenticatedSession,
} from './chrome-test-utils';

type InboxMessageFixture = {
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
  renderedText: string;
  renderedHtml: string;
};

const INBOX_FIXTURES: InboxMessageFixture[] = [
  {
    gmailMessageId: 'gmail-message-1',
    gmailThreadId: 'gmail-thread-1',
    subject: 'Investor update for May',
    snippet: 'Draft numbers are ready for review.',
    from: 'Maya Chen <maya@example.com>',
    to: ['founder@example.com'],
    cc: ['ops@example.com'],
    receivedAt: '2026-05-23T08:30:00.000Z',
    labelIds: ['INBOX', 'UNREAD', 'Label_finance'],
    labels: [
      { id: 'INBOX', name: 'INBOX' },
      { id: 'UNREAD', name: 'UNREAD' },
      { id: 'Label_finance', name: 'Finance' },
    ],
    unread: true,
    hasAttachment: true,
    openInGmailUrl: 'https://mail.google.com/mail/u/0/#inbox/gmail-thread-1',
    renderedText: 'Draft numbers are ready for review.\nPlease confirm the ARR slide.',
    renderedHtml:
      '<div><p>Draft numbers are ready for review.</p><p><strong>Please confirm the ARR slide.</strong></p><img src="https://example.com/chart.png" alt="ARR chart"></div>',
  },
  {
    gmailMessageId: 'gmail-message-2',
    gmailThreadId: 'gmail-thread-2',
    subject: 'Demo deck notes',
    snippet: 'I left comments on slide 4.',
    from: 'Alex Rivera <alex@example.com>',
    to: ['founder@example.com'],
    cc: [],
    receivedAt: '2026-05-22T14:05:00.000Z',
    labelIds: ['INBOX'],
    labels: [{ id: 'INBOX', name: 'INBOX' }],
    unread: false,
    hasAttachment: false,
    openInGmailUrl: 'https://mail.google.com/mail/u/0/#inbox/gmail-thread-2',
    renderedText: 'I left comments on slide 4. The pricing page is ready.',
    renderedHtml: '',
  },
  {
    gmailMessageId: 'gmail-message-3',
    gmailThreadId: 'gmail-thread-3',
    subject: 'Partnership follow-up',
    snippet: 'Can we meet next Tuesday?',
    from: 'Priya Shah <priya@example.com>',
    to: ['founder@example.com'],
    cc: [],
    receivedAt: '2026-05-21T09:10:00.000Z',
    labelIds: ['INBOX'],
    labels: [{ id: 'INBOX', name: 'INBOX' }],
    unread: false,
    hasAttachment: false,
    openInGmailUrl: 'https://mail.google.com/mail/u/0/#inbox/gmail-thread-3',
    renderedText: 'Can we meet next Tuesday to close the partnership terms?',
    renderedHtml: '',
  },
];

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

test.describe.configure({ mode: 'serial' });

test('inbox renders recent Gmail messages, fetches detail, and lazy-loads the next page', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1280, height: 820 });
  await seedAuthenticatedSession(page);
  const chromeState = createChromeMockState();
  await installChromeApiMock(page, chromeState);
  const inboxPageRequests: Array<{ cursor: string; limit: string | null }> = [];
  const markReadRequests: string[] = [];
  const chatPreviewRequests: Array<{ headers: Record<string, string>; body: unknown }> = [];
  const chatGenerationRequests: Array<{ headers: Record<string, string>; body: unknown }> = [];
  const chatConfirmRequests: Array<{ chatId: string; body: unknown }> = [];
  const nativeButtonWarnings: string[] = [];
  page.on('console', (message) => {
    if (message.text().includes('nativeButton')) {
      nativeButtonWarnings.push(message.text());
    }
  });
  await installInboxApiMock(
    page,
    inboxPageRequests,
    markReadRequests,
    chatPreviewRequests,
    chatGenerationRequests,
    chatConfirmRequests,
  );
  await page.context().addCookies([
    {
      name: 'XSRF-TOKEN',
      value: 'playwright-xsrf',
      domain: 'localhost',
      path: '/',
      sameSite: 'Lax',
      secure: false,
    },
  ]);

  await page.goto('/inbox', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');

  await expect(page.getByRole('heading', { name: 'Inbox' })).toBeVisible();
  await expect(
    page.getByTestId('inbox-message-row').filter({ hasText: 'Investor update for May' }),
  ).toBeVisible();
  await expect(
    page.getByTestId('inbox-message-row').filter({ hasText: 'Demo deck notes' }),
  ).toBeVisible();
  await expect(page.getByText('Finance')).toBeVisible();
  await expect(page.getByText('Select a message to read it.')).toBeVisible();

  await page.getByText('Investor update for May').click();
  await expect(
    page
      .frameLocator('iframe[title="Investor update for May"]')
      .getByText('Please confirm the ARR slide.'),
  ).toBeVisible();
  await expect.poll(() => markReadRequests).toEqual(['gmail-message-1']);
  await expect(page.getByText('Unread', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Reply', { exact: true })).toBeVisible();
  await expect(page.getByText('Reply all', { exact: true })).toBeVisible();
  await expect(page.getByText('Forward', { exact: true })).toBeVisible();
  await expect(page.getByTestId('inbox-action-generate')).toBeVisible();
  await expect(page.getByTestId('inbox-message-list')).toHaveCSS('overflow-y', 'auto');
  await expect(page.getByTestId('inbox-detail-scroll')).toHaveCSS('overflow-y', 'auto');

  await page.getByRole('button', { name: 'Reply', exact: true }).click();
  await expect(page.getByTestId('inbox-reply-composer')).toBeVisible();
  const detailPaneScrollState = await page
    .getByTestId('inbox-detail-scroll')
    .evaluate((element) => {
      element.scrollTop = element.scrollHeight;
      return {
        scrollTop: element.scrollTop,
        scrollHeight: element.scrollHeight,
        clientHeight: element.clientHeight,
      };
    });
  expect(detailPaneScrollState.scrollHeight).toBeGreaterThan(detailPaneScrollState.clientHeight);
  expect(detailPaneScrollState.scrollTop).toBeGreaterThan(0);
  await expect(page.getByTestId('inbox-composer-to')).toHaveValue('maya@example.com');
  await expect(page.getByTestId('inbox-composer-subject')).toHaveValue(
    'Re: Investor update for May',
  );
  await expect(page.getByRole('button', { name: 'Vietnamese' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'English', exact: true })).toBeVisible();
  await page.getByTestId('inbox-composer-body').fill('Thanks, I will review the ARR slide today.');
  await page.getByRole('button', { name: 'Send' }).click();
  const confirmDialog = page.getByRole('alertdialog');
  await expect(confirmDialog).toContainText(
    'Have you carefully reviewed this email and are sure you want to send it?',
  );
  await confirmDialog.getByRole('button', { name: 'Send' }).click();
  expect(chatPreviewRequests).toHaveLength(1);
  expect(chatPreviewRequests[0]?.headers['x-xsrf-token']).toBe('playwright-xsrf');
  expect(chatIdFromBody(chatPreviewRequests[0]?.body)).toMatch(UUID_PATTERN);
  expect(JSON.stringify(chatPreviewRequests[0]?.body)).toContain(
    'Thanks, I will review the ARR slide today.',
  );
  expect(JSON.stringify(chatPreviewRequests[0]?.body)).not.toContain(
    'Please confirm the ARR slide',
  );
  await expect(page.getByTestId('preview-card-replyEmail')).toHaveCount(0);
  await expect(page.getByText("I can't directly run")).toHaveCount(0);
  await expect(page.getByTestId('inbox-auto-send-status')).toContainText('Email sent.');
  expect(chatConfirmRequests).toHaveLength(1);
  expect(chatConfirmRequests[0]?.chatId).toBe(chatIdFromBody(chatPreviewRequests[0]?.body));
  await page.getByLabel('Close composer').click();

  await page.getByRole('button', { name: 'Reply all', exact: true }).click();
  await expect(page.getByTestId('inbox-composer-to')).toHaveValue('maya@example.com');
  await expect(page.getByTestId('inbox-composer-cc')).toHaveValue('ops@example.com');
  await page.getByLabel('Close composer').click();

  await page.getByRole('button', { name: 'Forward', exact: true }).click();
  await expect(page.getByTestId('inbox-composer-to')).toHaveValue('');
  await expect(page.getByTestId('inbox-composer-subject')).toHaveValue(
    'Fwd: Investor update for May',
  );
  await page.getByLabel('Close composer').click();

  await page.getByTestId('inbox-action-generate').click();
  await expect(page.getByTestId('inbox-reply-composer')).toBeVisible();
  await expect(page.getByTestId('inbox-composer-body')).toHaveValue(
    'Thanks for the update. I will review the ARR slide and follow up today.',
  );
  expect(chatGenerationRequests).toHaveLength(1);
  expect(chatIdFromBody(chatGenerationRequests[0]?.body)).toMatch(UUID_PATTERN);
  expect(JSON.stringify(chatGenerationRequests[0]?.body)).toContain('Language: English');
  expect(chromeState.draftRequests).toEqual([]);

  await page.getByTestId('inbox-composer-file-input').setInputFiles({
    name: 'arr-notes.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('attachment preview'),
  });
  await expect(page.getByTestId('inbox-composer-attach')).toContainText(/Attach\s*1/);
  await expect(page.getByTestId('inbox-attachment-list')).toContainText('arr-notes.txt');
  await expect(page.getByTestId('inbox-attachment-review')).toHaveCount(0);
  await page.getByLabel('Close composer').click();

  await page.getByText('Demo deck notes').click();
  await expect(page.getByText('The pricing page is ready.')).toBeVisible();

  await page.getByTestId('inbox-message-list').evaluate((listElement) => {
    listElement.scrollTop = listElement.scrollHeight;
    listElement.dispatchEvent(new Event('scroll', { bubbles: true }));
  });

  await expect(page.getByText('Partnership follow-up')).toBeVisible();
  expect(inboxPageRequests).toContainEqual({ cursor: '', limit: '20' });
  expect(inboxPageRequests).toContainEqual({ cursor: 'cursor-2', limit: '20' });
  expect(nativeButtonWarnings).toEqual([]);
  await expectNoHorizontalOverflow(page);
});

async function installInboxApiMock(
  page: Page,
  inboxPageRequests: Array<{ cursor: string; limit: string | null }>,
  markReadRequests: string[],
  chatPreviewRequests: Array<{ headers: Record<string, string>; body: unknown }>,
  chatGenerationRequests: Array<{ headers: Record<string, string>; body: unknown }>,
  chatConfirmRequests: Array<{ chatId: string; body: unknown }>,
) {
  const readMessageIds = new Set<string>();
  await page.route(API_ROUTE_PATTERN, async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (url.pathname === '/api/gmail/inbox' && request.method() === 'GET') {
      const cursor = url.searchParams.get('cursor') ?? '';
      inboxPageRequests.push({ cursor, limit: url.searchParams.get('limit') });
      await fulfillJson(route, {
        items: pageItemsForCursor(cursor).map((message) =>
          toMessageResponse(message, readMessageIds),
        ),
        nextCursor: cursor === '' ? 'cursor-2' : null,
        loadedCount: cursor === '' ? 2 : 3,
        maxMessages: 100,
      });
      return;
    }

    const detailMatch = url.pathname.match(/^\/api\/gmail\/inbox\/([^/]+)$/);
    if (detailMatch && request.method() === 'GET') {
      const gmailMessageId = decodeURIComponent(detailMatch[1]!);
      const message = INBOX_FIXTURES.find((fixture) => fixture.gmailMessageId === gmailMessageId);
      if (!message) {
        await route.fulfill({ status: 404, body: '' });
        return;
      }
      const { renderedText, renderedHtml } = message;
      await fulfillJson(route, {
        message: toMessageResponse(message, readMessageIds),
        renderedText,
        renderedHtml,
      });
      return;
    }

    const readMatch = url.pathname.match(/^\/api\/gmail\/inbox\/([^/]+)\/read$/);
    if (readMatch && request.method() === 'POST') {
      const gmailMessageId = decodeURIComponent(readMatch[1]!);
      markReadRequests.push(gmailMessageId);
      readMessageIds.add(gmailMessageId);
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    if (url.pathname === '/api/chat' && request.method() === 'POST') {
      const body = safeJson(route);
      const userText =
        typeof body === 'object' && body
          ? String((body as { userText?: unknown }).userText ?? '')
          : '';
      if (userText.includes('Write only the email body text')) {
        chatGenerationRequests.push({ headers: request.headers(), body });
        await fulfillUiTextStream(route, [
          userText.includes('Language: Vietnamese')
            ? 'Cảm ơn bạn đã cập nhật. Tôi sẽ xem slide ARR và phản hồi trong hôm nay.'
            : 'Thanks for the update. I will review the ARR slide and follow up today.',
        ]);
        return;
      }
      chatPreviewRequests.push({ headers: request.headers(), body });
      await fulfillUiToolStream(route, {
        sourceMessageId: 'gmail-message-1',
        gmailThreadId: 'gmail-thread-1',
        to: 'maya@example.com',
        cc: '',
        subject: 'Re: Investor update for May',
        body: 'Thanks, I will review the ARR slide today.',
      });
      return;
    }

    const chatConfirmMatch = url.pathname.match(/^\/api\/chat\/([^/]+)\/confirm$/);
    if (chatConfirmMatch && request.method() === 'POST') {
      chatConfirmRequests.push({
        chatId: decodeURIComponent(chatConfirmMatch[1]!),
        body: safeJson(route),
      });
      await fulfillJson(route, { state: 'SENT' });
      return;
    }

    await route.fallback();
  });
}

function pageItemsForCursor(cursor: string): InboxMessageFixture[] {
  return cursor === '' ? INBOX_FIXTURES.slice(0, 2) : INBOX_FIXTURES.slice(2);
}

function toMessageResponse(message: InboxMessageFixture, readMessageIds: Set<string>) {
  const markedRead = readMessageIds.has(message.gmailMessageId);
  return {
    gmailMessageId: message.gmailMessageId,
    gmailThreadId: message.gmailThreadId,
    subject: message.subject,
    snippet: message.snippet,
    from: message.from,
    to: message.to,
    cc: message.cc,
    receivedAt: message.receivedAt,
    labelIds: markedRead
      ? message.labelIds.filter((labelId) => labelId !== 'UNREAD')
      : message.labelIds,
    labels: markedRead ? message.labels.filter((label) => label.id !== 'UNREAD') : message.labels,
    unread: markedRead ? false : message.unread,
    hasAttachment: message.hasAttachment,
    openInGmailUrl: message.openInGmailUrl,
  };
}

async function fulfillJson(route: Route, body: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

async function fulfillUiTextStream(route: Route, chunks: string[]) {
  const parts = [
    { type: 'start', messageId: 'assistant-inbox-generation' },
    { type: 'text-start', id: 'text-1' },
    ...chunks.map((delta) => ({ type: 'text-delta', id: 'text-1', delta })),
    { type: 'text-end', id: 'text-1' },
    { type: 'finish', finishReason: 'stop' },
  ];

  await route.fulfill({
    status: 200,
    headers: {
      'content-type': 'text/event-stream',
      'cache-control': 'no-cache',
      connection: 'keep-alive',
      'x-vercel-ai-ui-message-stream': 'v1',
    },
    body: `${parts.map((part) => `data: ${JSON.stringify(part)}\n\n`).join('')}data: [DONE]\n\n`,
  });
}

async function fulfillUiToolStream(route: Route, input: Record<string, unknown>) {
  const parts = [
    { type: 'start', messageId: 'assistant-inbox-preview' },
    { type: 'text-start', id: 'text-1' },
    {
      type: 'text-delta',
      id: 'text-1',
      delta: "I can't directly run replyEmail yet because it requires a preview card.",
    },
    { type: 'text-end', id: 'text-1' },
    { type: 'tool-input-start', toolCallId: 'tool-call-replyEmail', toolName: 'replyEmail' },
    {
      type: 'tool-input-available',
      toolCallId: 'tool-call-replyEmail',
      toolName: 'replyEmail',
      input: JSON.stringify(input),
    },
    {
      type: 'data-persistence',
      id: '00000000-0000-4000-8000-000000000001',
      data: {
        chatMessageId: '00000000-0000-4000-8000-000000000001',
        state: 'tool-call-saved',
      },
    },
    { type: 'finish', finishReason: 'tool-calls' },
  ];

  await route.fulfill({
    status: 200,
    headers: {
      'content-type': 'text/event-stream',
      'cache-control': 'no-cache',
      connection: 'keep-alive',
      'x-vercel-ai-ui-message-stream': 'v1',
    },
    body: `${parts.map((part) => `data: ${JSON.stringify(part)}\n\n`).join('')}data: [DONE]\n\n`,
  });
}

function safeJson(route: Route): unknown {
  try {
    return route.request().postDataJSON();
  } catch {
    return null;
  }
}

function chatIdFromBody(body: unknown): string {
  if (!body || typeof body !== 'object') return '';
  const chatId = (body as { chatId?: unknown }).chatId;
  return typeof chatId === 'string' ? chatId : '';
}
