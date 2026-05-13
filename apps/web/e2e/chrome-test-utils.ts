import { expect, type Page, type Route } from '@playwright/test';

type GmailConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING';
type OnboardingStep = 'GMAIL_CONNECTED' | 'TEMPLATE_SELECTED' | 'COMPLETE';
type DraftStatus = 'NO_DRAFT' | 'DRAFT_READY' | 'DRAFT_SENT';

export type ChromeMockState = {
  triagePaused: boolean;
  connectionStatus: GmailConnectionStatus;
  onboardingStep: OnboardingStep;
  availableCredits: number;
  needsReplyDraftStatus: DraftStatus;
  balanceRequests: number;
  draftRequests: string[];
  pauseRequests: Array<{ paused: boolean }>;
};

export const API_ROUTE_PATTERN = /^https?:\/\/[^/]+\/(?:me|api\/|gmail\/|tenant\/).*$/;

export function createChromeMockState(overrides: Partial<ChromeMockState> = {}): ChromeMockState {
  return {
    triagePaused: false,
    connectionStatus: 'CONNECTED',
    onboardingStep: 'COMPLETE',
    availableCredits: 12,
    needsReplyDraftStatus: 'NO_DRAFT',
    balanceRequests: 0,
    draftRequests: [],
    pauseRequests: [],
    ...overrides,
  };
}

export async function seedAuthenticatedSession(page: Page) {
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
      value: 'en',
      domain: 'localhost',
      path: '/',
      sameSite: 'Lax',
      secure: false,
    },
  ]);
}

export async function installChromeApiMock(page: Page, state: ChromeMockState) {
  await page.route(API_ROUTE_PATTERN, async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (url.pathname === '/me') {
      await fulfillJson(route, {
        userId: 'user-1',
        tenantId: 'tenant-1',
        email: 'founder@example.com',
        preferredLanguage: 'en',
        onboardingStep: state.onboardingStep,
        triagePaused: state.triagePaused,
        gmailConnectionStatus: {
          status: state.connectionStatus,
          ingestionHealth: state.connectionStatus === 'CONNECTED' ? 'HEALTHY' : 'WATCH_UNHEALTHY',
          googleEmail: 'founder@example.com',
        },
      });
      return;
    }

    if (url.pathname === '/api/billing/balance' && request.method() === 'GET') {
      state.balanceRequests += 1;
      await fulfillJson(route, {
        availableCredits: state.availableCredits,
        heldCredits: 0,
        currency: 'credits',
      });
      return;
    }

    if (url.pathname === '/api/threads' && request.method() === 'GET') {
      const bucket = url.searchParams.get('bucket');
      await fulfillJson(route, {
        items:
          bucket === 'awaiting-their-reply'
            ? []
            : [
                {
                  gmailThreadId: 'thread-1',
                  subject: 'Re: Q3 partnership terms',
                  otherParty: 'priya@acme.io',
                  lastActivityAt: '2026-05-12T10:30:00.000Z',
                  draftStatus: state.needsReplyDraftStatus,
                  resolved: false,
                  openInGmailUrl: 'https://mail.google.com/mail/u/0/#all/thread-1',
                },
              ],
        nextCursor: null,
        toReplyCount: bucket === 'awaiting-their-reply' ? 0 : 1,
      });
      return;
    }

    if (url.pathname === '/api/threads/to-reply-count' && request.method() === 'GET') {
      await fulfillJson(route, {
        toReplyCount: state.needsReplyDraftStatus === 'DRAFT_SENT' ? 0 : 1,
      });
      return;
    }

    if (url.pathname === '/api/triage/audit' && request.method() === 'GET') {
      await fulfillJson(route, { items: [], nextCursor: null });
      return;
    }

    const draftMatch = url.pathname.match(/^\/api\/threads\/([^/]+)\/draft$/);
    if (draftMatch && request.method() === 'POST') {
      const gmailThreadId = decodeURIComponent(draftMatch[1]);
      state.draftRequests.push(gmailThreadId);
      state.needsReplyDraftStatus = 'DRAFT_READY';
      await fulfillJson(route, {
        draftId: 'draft-1',
        gmailThreadId,
        status: 'GENERATED',
        openInGmailUrl: `https://mail.google.com/mail/u/0/#all/${gmailThreadId}`,
      });
      return;
    }

    const resolveMatch = url.pathname.match(/^\/api\/threads\/([^/]+)\/resolve$/);
    if (resolveMatch && request.method() === 'POST') {
      await route.fulfill({ status: 200, body: '' });
      return;
    }

    if (url.pathname === '/gmail/connection/status' && request.method() === 'GET') {
      await fulfillJson(route, { connectionStatus: state.connectionStatus });
      return;
    }

    if (url.pathname === '/tenant/triage-pause' && request.method() === 'PUT') {
      const payload = request.postDataJSON() as { paused: boolean };
      expect(typeof payload.paused).toBe('boolean');
      state.pauseRequests.push(payload);
      state.triagePaused = payload.paused;
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    if (url.pathname === '/api/rules' && request.method() === 'GET') {
      await fulfillJson(route, {
        rules: [],
        templates: [],
        materialization: {
          createdCount: 0,
          skippedCount: 0,
          customizedPreservedCount: 0,
        },
      });
      return;
    }

    if (url.pathname === '/api/rules/templates' && request.method() === 'GET') {
      await fulfillJson(route, []);
      return;
    }

    if (url.pathname === '/api/llm/byok' && request.method() === 'GET') {
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    await route.fulfill({ status: 204, body: '' });
  });
}

export async function openAuthenticatedRoute(
  page: Page,
  path: '/rules' | '/settings' | '/onboarding/gmail-connect',
  state: ChromeMockState,
) {
  await seedAuthenticatedSession(page);
  await installChromeApiMock(page, state);
  await page.goto(path, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle');
}

export async function expectNoHorizontalOverflow(page: Page) {
  const horizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth,
  );
  expect(horizontalOverflow).toBe(false);
}

export async function expectAppShellChrome(page: Page, options: { sidebarVisible?: boolean } = {}) {
  await expect(page.getByTestId('app-shell')).toBeVisible();
  await expect(page.getByTestId('chrome-header')).toBeVisible();

  if (options.sidebarVisible) {
    await expect(page.getByTestId('app-sidebar')).toBeVisible();
  }
}

export async function expectChromeSuppressed(page: Page) {
  await expect(page.getByTestId('app-shell')).toHaveCount(0);
  await expect(page.getByTestId('app-sidebar')).toHaveCount(0);
  await expect(page.getByTestId('chrome-header')).toHaveCount(0);
}

export async function expectNoClaySkinClasses(page: Page) {
  await expect(page.locator('[class*="zm-proto"], [class*="zm-auth"]')).toHaveCount(0);
}

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}
