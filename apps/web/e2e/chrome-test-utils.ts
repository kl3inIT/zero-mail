import { expect, type Page, type Route } from '@playwright/test';

type GmailConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING';
type OnboardingStep = 'GMAIL_CONNECTED' | 'TEMPLATE_SELECTED' | 'COMPLETE';

export type ChromeMockState = {
  triagePaused: boolean;
  connectionStatus: GmailConnectionStatus;
  onboardingStep: OnboardingStep;
  availableCredits: number;
  balanceRequests: number;
  pauseRequests: Array<{ paused: boolean }>;
};

export function createChromeMockState(overrides: Partial<ChromeMockState> = {}): ChromeMockState {
  return {
    triagePaused: false,
    connectionStatus: 'CONNECTED',
    onboardingStep: 'COMPLETE',
    availableCredits: 12,
    balanceRequests: 0,
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
  await page.route('http://localhost:8080/**', async (route) => {
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

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}
