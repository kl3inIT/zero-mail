import { type Page, type Route } from '@playwright/test';

import { API_ROUTE_PATTERN } from '../chrome-test-utils';

type AppLocale = 'en' | 'vi';

export type MockChatSummary = {
  id: string;
  title: string;
  updatedAt: string;
  messageCount: number;
};

export type MockChatPart = {
  type: string;
  partId?: string;
  toolCallId?: string;
  state?: string;
  input?: Record<string, unknown>;
  output?: Record<string, unknown>;
  confirmation?: Record<string, unknown>;
  text?: string;
};

export type MockChatMessage = {
  id: string;
  role: 'USER' | 'ASSISTANT';
  createdAt: string;
  parts: {
    schemaVersion: number;
    parts: MockChatPart[];
  };
};

export type MockChatDetail = {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messages: MockChatMessage[];
};

type MockOptions = {
  locale?: AppLocale;
  chats?: MockChatSummary[];
  details?: Record<string, MockChatDetail>;
  streamChunks?: string[];
  streamDelayMs?: number;
  confirmDelayMs?: number;
};

export type ChatMockState = {
  chatRequests: Array<{ headers: Record<string, string>; body: unknown }>;
  confirmRequests: Array<{ chatId: string; body: unknown; headers: Record<string, string> }>;
  cancelRequests: Array<{ chatId: string; body: unknown; headers: Record<string, string> }>;
  deletedChatIds: string[];
};

const XSRF_TOKEN = 'playwright-xsrf';

export async function openChat(page: Page, path = '/chat', options: MockOptions = {}) {
  await seedSession(page, options.locale ?? 'vi');
  const state = await installChatApiMock(page, options);
  await page.goto(path, { waitUntil: 'domcontentloaded' });
  await page.getByTestId('chat-pane').waitFor({ state: 'visible' });
  return state;
}

export async function installChatApiMock(page: Page, options: MockOptions = {}) {
  const chats = [...(options.chats ?? [])];
  const details = new Map(Object.entries(options.details ?? {}));
  const state: ChatMockState = {
    chatRequests: [],
    confirmRequests: [],
    cancelRequests: [],
    deletedChatIds: [],
  };

  await page.route(API_ROUTE_PATTERN, async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: corsHeaders(route) });
      return;
    }

    if (url.pathname === '/me') {
      await fulfillJson(route, {
        userId: 'user-1',
        tenantId: 'tenant-1',
        email: 'founder@example.com',
        preferredLanguage: options.locale ?? 'vi',
        onboardingStep: 'COMPLETE',
        triagePaused: false,
        gmailConnectionStatus: {
          status: 'CONNECTED',
          ingestionHealth: 'HEALTHY',
          googleEmail: 'founder@example.com',
        },
      });
      return;
    }

    if (url.pathname === '/api/billing/balance' && request.method() === 'GET') {
      await fulfillJson(route, { availableCredits: 12, heldCredits: 0, currency: 'credits' });
      return;
    }

    if (url.pathname === '/gmail/connection/status' && request.method() === 'GET') {
      await fulfillJson(route, { connectionStatus: 'CONNECTED' });
      return;
    }

    if (url.pathname === '/tenant/triage-pause' && request.method() === 'PUT') {
      await route.fulfill({ status: 204, headers: corsHeaders(route), body: '' });
      return;
    }

    if (url.pathname === '/api/chat/history' && request.method() === 'GET') {
      await fulfillJson(route, {
        chats: chats.filter((chat) => !state.deletedChatIds.includes(chat.id)),
        pageSize: Number(url.searchParams.get('pageSize') ?? 50),
        pageOffset: Number(url.searchParams.get('pageOffset') ?? 0),
      });
      return;
    }

    if (url.pathname === '/api/chat' && request.method() === 'POST') {
      if (!hasValidXsrf(route)) {
        await route.fulfill({ status: 403, headers: corsHeaders(route), body: '' });
        return;
      }
      state.chatRequests.push({
        headers: request.headers(),
        body: safeJson(route),
      });
      if (options.streamDelayMs) {
        await delay(options.streamDelayMs);
      }
      await fulfillUiStream(route, options.streamChunks ?? ['Xin chao tu Zero Mail.']);
      return;
    }

    const detailMatch = url.pathname.match(/^\/api\/chat\/([^/]+)$/);
    if (detailMatch && request.method() === 'GET') {
      const chatId = decodeURIComponent(detailMatch[1]);
      await fulfillJson(route, details.get(chatId) ?? emptyDetail(chatId));
      return;
    }

    if (detailMatch && request.method() === 'DELETE') {
      const chatId = decodeURIComponent(detailMatch[1]);
      if (!hasValidXsrf(route)) {
        await route.fulfill({ status: 403, headers: corsHeaders(route), body: '' });
        return;
      }
      state.deletedChatIds.push(chatId);
      await route.fulfill({ status: 204, headers: corsHeaders(route), body: '' });
      return;
    }

    const confirmMatch = url.pathname.match(/^\/api\/chat\/([^/]+)\/confirm$/);
    if (confirmMatch && request.method() === 'POST') {
      if (!hasValidXsrf(route)) {
        await route.fulfill({ status: 403, headers: corsHeaders(route), body: '' });
        return;
      }
      state.confirmRequests.push({
        chatId: decodeURIComponent(confirmMatch[1]),
        body: safeJson(route),
        headers: request.headers(),
      });
      if (options.confirmDelayMs) {
        await delay(options.confirmDelayMs);
      }
      await fulfillJson(route, { state: 'SENT' });
      return;
    }

    const cancelMatch = url.pathname.match(/^\/api\/chat\/([^/]+)\/cancel$/);
    if (cancelMatch && request.method() === 'POST') {
      if (!hasValidXsrf(route)) {
        await route.fulfill({ status: 403, headers: corsHeaders(route), body: '' });
        return;
      }
      state.cancelRequests.push({
        chatId: decodeURIComponent(cancelMatch[1]),
        body: safeJson(route),
        headers: request.headers(),
      });
      await fulfillJson(route, { state: 'CANCELLED' });
      return;
    }

    await route.fulfill({ status: 204, headers: corsHeaders(route), body: '' });
  });

  return state;
}

export function chatSummary(id: string, title: string, messageCount = 1): MockChatSummary {
  return {
    id,
    title,
    updatedAt: '2026-05-18T08:00:00Z',
    messageCount,
  };
}

export function chatDetail(
  id: string,
  messages: MockChatMessage[],
  title = 'Chat',
): MockChatDetail {
  return {
    id,
    title,
    createdAt: '2026-05-18T07:00:00Z',
    updatedAt: '2026-05-18T08:00:00Z',
    messages,
  };
}

export function toolMessage(
  toolName: string,
  input: Record<string, unknown>,
  overrides: Partial<MockChatPart> = {},
): MockChatMessage {
  return {
    id: `message-${toolName}`,
    role: 'ASSISTANT',
    createdAt: '2026-05-18T08:00:00Z',
    parts: {
      schemaVersion: 1,
      parts: [
        {
          type: `tool-${toolName}`,
          partId: `part-${toolName}`,
          toolCallId: `tool-call-${toolName}`,
          state: 'input-available',
          input,
          ...overrides,
        },
      ],
    },
  };
}

export async function rawChatPost(page: Page, withXsrfHeader: boolean) {
  return page.evaluate(
    async ({ token, withHeader }) => {
      const response = await fetch('http://localhost:8080/api/chat', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          ...(withHeader ? { 'X-XSRF-TOKEN': token } : {}),
        },
        body: JSON.stringify({ chatId: 'csrf-chat', userText: 'hello' }),
      });
      return response.status;
    },
    { token: XSRF_TOKEN, withHeader: withXsrfHeader },
  );
}

export async function rawConfirmPost(page: Page, withXsrfHeader: boolean) {
  return page.evaluate(
    async ({ token, withHeader }) => {
      const response = await fetch('http://localhost:8080/api/chat/csrf-chat/confirm', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          ...(withHeader ? { 'X-XSRF-TOKEN': token } : {}),
        },
        body: JSON.stringify({ toolCallId: 'tool-call-sendEmail', vipAcknowledged: false }),
      });
      return response.status;
    },
    { token: XSRF_TOKEN, withHeader: withXsrfHeader },
  );
}

async function seedSession(page: Page, locale: AppLocale) {
  await page.context().addCookies([
    {
      name: 'ZEROMAIL_SESSION',
      value: 'playwright-session',
      domain: 'localhost',
      path: '/',
      httpOnly: true,
      sameSite: 'Lax',
      secure: false,
    },
    {
      name: 'NEXT_LOCALE',
      value: locale,
      domain: 'localhost',
      path: '/',
      sameSite: 'Lax',
      secure: false,
    },
    {
      name: 'XSRF-TOKEN',
      value: XSRF_TOKEN,
      domain: 'localhost',
      path: '/',
      sameSite: 'Lax',
      secure: false,
    },
  ]);
}

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    headers: corsHeaders(route),
    body: JSON.stringify(body),
  });
}

async function fulfillUiStream(route: Route, chunks: string[]) {
  const parts = [
    { type: 'start', messageId: 'assistant-stream' },
    { type: 'text-start', id: 'text-1' },
    ...chunks.map((delta) => ({ type: 'text-delta', id: 'text-1', delta })),
    { type: 'text-end', id: 'text-1' },
    { type: 'finish', finishReason: 'stop' },
  ];
  await route.fulfill({
    status: 200,
    headers: {
      ...corsHeaders(route),
      'content-type': 'text/event-stream',
      'cache-control': 'no-cache',
      connection: 'keep-alive',
      'x-vercel-ai-ui-message-stream': 'v1',
    },
    body: `${parts.map((part) => `data: ${JSON.stringify(part)}\n\n`).join('')}data: [DONE]\n\n`,
  });
}

function emptyDetail(chatId: string): MockChatDetail {
  return chatDetail(chatId, [], 'New chat');
}

function corsHeaders(route: Route): Record<string, string> {
  const origin = route.request().headers().origin ?? 'http://localhost:3000';
  return {
    'access-control-allow-origin': origin,
    'access-control-allow-credentials': 'true',
    'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
    'access-control-allow-headers': 'content-type,x-xsrf-token',
  };
}

function hasValidXsrf(route: Route): boolean {
  return route.request().headers()['x-xsrf-token'] === XSRF_TOKEN;
}

function safeJson(route: Route): unknown {
  try {
    return route.request().postDataJSON();
  } catch {
    return null;
  }
}

async function delay(milliseconds: number) {
  await new Promise((resolve) => setTimeout(resolve, milliseconds));
}
