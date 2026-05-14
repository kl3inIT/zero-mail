import { expect, test, type Page, type Route } from '@playwright/test';

import {
  API_ROUTE_PATTERN,
  expectNoHorizontalOverflow,
  seedAuthenticatedSession,
} from './chrome-test-utils';

test.describe.configure({ mode: 'serial' });

type Sender = {
  senderEmail: string;
  optedIn: boolean;
};

type TriageMockState = {
  shadowModeRequests: Array<{ enabled: boolean }>;
  shadowModeEnabled: boolean;
  optInRequests: string[];
  protectedSenders: Sender[];
};

for (const viewport of [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 320, height: 740 },
]) {
  test(`shadow mode writes and confirms turn-off at ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const state = createTriageMockState();
    await openTriage(page, '/triage?tab=shadow', state);

    await expect(page.getByTestId('shadow-mode-switch')).not.toBeChecked();
    await page.getByTestId('shadow-mode-switch').click();
    await expect(page.getByRole('alertdialog')).toHaveCount(0);
    await expect.poll(() => state.shadowModeRequests).toContainEqual({ enabled: true });
    await expect(page.getByText('Shadow mode on')).toBeVisible();
    await expect(page.getByTestId('shadow-mode-switch')).toBeChecked();

    await page.getByTestId('shadow-mode-switch').click();
    await expect(page.getByRole('alertdialog')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Turn off shadow mode?' })).toBeVisible();
    await page.getByRole('button', { name: 'Turn off shadow mode' }).click();
    await expect.poll(() => state.shadowModeRequests).toContainEqual({ enabled: false });
    await expect(page.getByTestId('shadow-mode-switch')).not.toBeChecked();
    await expectNoHorizontalOverflow(page);
  });

  test(`sender safety net renders empty, populated, and opt-in states at ${viewport.name}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const state = createTriageMockState({ protectedSenders: [] });
    await openTriage(page, '/triage?tab=senders', state);

    await expect(page.getByText('No protected senders yet')).toBeVisible();
    await expectNoHorizontalOverflow(page);

    state.protectedSenders = [
      { senderEmail: 'founder@example.com', optedIn: false },
      { senderEmail: 'finance@example.com', optedIn: true },
    ];
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const senderList = page.getByTestId('sender-safety-net-list');
    await expect(senderList.getByText('founder@example.com')).toBeVisible();
    await expect(senderList.getByText('finance@example.com')).toBeVisible();
    await page.getByRole('button', { name: 'Opt into automation' }).click();
    await expect.poll(() => state.optInRequests).toContain('founder@example.com');
    await expect(senderList.getByText('founder@example.com')).toBeVisible();
    await expect(senderList.getByText('Opted in').first()).toBeVisible();
    await expectNoHorizontalOverflow(page);
  });
}

function createTriageMockState(overrides: Partial<TriageMockState> = {}): TriageMockState {
  return {
    shadowModeRequests: [],
    shadowModeEnabled: false,
    optInRequests: [],
    protectedSenders: [],
    ...overrides,
  };
}

async function openTriage(
  page: Page,
  path: '/triage?tab=shadow' | '/triage?tab=senders',
  state: TriageMockState,
) {
  await seedAuthenticatedSession(page);
  await installTriageApiMock(page, state);
  await page.goto(path, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle');
}

async function installTriageApiMock(page: Page, state: TriageMockState) {
  await page.route(API_ROUTE_PATTERN, async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (url.pathname === '/me') {
      await fulfillJson(route, {
        userId: 'user-1',
        tenantId: 'tenant-1',
        email: 'founder@example.com',
        preferredLanguage: 'en',
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
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    if (url.pathname === '/api/tenant/triage/shadow-mode' && request.method() === 'GET') {
      await fulfillJson(route, { enabled: state.shadowModeEnabled });
      return;
    }

    if (url.pathname === '/api/tenant/triage/shadow-mode' && request.method() === 'PATCH') {
      const payload = request.postDataJSON() as { enabled: boolean };
      expect(typeof payload.enabled).toBe('boolean');
      state.shadowModeRequests.push(payload);
      state.shadowModeEnabled = payload.enabled;
      await fulfillJson(route, { enabled: payload.enabled });
      return;
    }

    if (url.pathname === '/api/triage/sender-safety-net' && request.method() === 'GET') {
      await fulfillJson(route, { senders: state.protectedSenders });
      return;
    }

    if (
      url.pathname.startsWith('/api/triage/sender-safety-net/') &&
      url.pathname.endsWith('/opt-in') &&
      request.method() === 'POST'
    ) {
      const encodedSender = url.pathname
        .replace('/api/triage/sender-safety-net/', '')
        .replace('/opt-in', '');
      const senderEmail = decodeURIComponent(encodedSender);
      state.optInRequests.push(senderEmail);
      state.protectedSenders = state.protectedSenders.map((sender) =>
        sender.senderEmail === senderEmail ? { ...sender, optedIn: true } : sender,
      );
      await fulfillJson(route, { senderEmail, optedIn: true });
      return;
    }

    await route.fulfill({ status: 204, body: '' });
  });
}

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}
