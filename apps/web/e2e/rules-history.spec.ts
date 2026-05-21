import { expect, test, type Page, type Route } from '@playwright/test';

import {
  API_ROUTE_PATTERN,
  expectNoHorizontalOverflow,
  seedAuthenticatedSession,
} from './chrome-test-utils';

test.describe.configure({ mode: 'serial' });

for (const viewport of [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 320, height: 740 },
]) {
  test(`rules history tab renders empty audit log at ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await openRules(page);

    await page.getByRole('tab', { name: 'History' }).click();
    await expect(page.getByRole('tab', { name: 'History' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    await expect(page.getByText('No email actions yet')).toBeVisible();
    await expectNoHorizontalOverflow(page);
  });

  test(`ai page renders protected senders empty state at ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await openAi(page);

    await expect(
      page.getByRole('heading', { name: 'AI configuration', exact: true }),
    ).toBeVisible();
    await expect(page.getByText('Protected senders', { exact: true })).toBeVisible();
    await expectNoHorizontalOverflow(page);
  });
}

async function openRules(page: Page) {
  await seedAuthenticatedSession(page);
  await installApiMock(page);
  await page.goto('/rules', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');
}

async function openAi(page: Page) {
  await seedAuthenticatedSession(page);
  await installApiMock(page);
  await page.goto('/ai', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');
}

async function installApiMock(page: Page) {
  await page.route(API_ROUTE_PATTERN, async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (url.pathname === '/api/me') {
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
      await fulfillJson(route, {
        availableCredits: 12,
        heldCredits: 0,
        currency: 'credits',
        betaCredits: 12,
        paidCredits: 0,
        monthlyGrantCredits: 300,
        resetsAt: '2026-06-01T00:00:00.000Z',
        freeDuringBeta: true,
      });
      return;
    }

    if (url.pathname === '/api/me/notifications' && request.method() === 'GET') {
      await fulfillJson(route, {
        channel: 'DAILY_DIGEST',
        digestEnabled: true,
        digestSendHourLocal: 20,
        timeZone: 'Asia/Ho_Chi_Minh',
      });
      return;
    }

    if (url.pathname === '/api/triage/audit' && request.method() === 'GET') {
      await fulfillJson(route, { items: [], nextCursor: null });
      return;
    }

    if (url.pathname === '/api/triage/sender-safety-net' && request.method() === 'GET') {
      await fulfillJson(route, { items: [] });
      return;
    }

    if (url.pathname === '/api/rules' && request.method() === 'GET') {
      await fulfillJson(route, { items: [] });
      return;
    }

    if (url.pathname === '/api/rule-templates' && request.method() === 'GET') {
      await fulfillJson(route, { items: [] });
      return;
    }

    if (url.pathname === '/api/threads' && request.method() === 'GET') {
      await fulfillJson(route, { items: [], nextCursor: null, toReplyCount: 0 });
      return;
    }

    if (url.pathname === '/api/gmail/connection/status' && request.method() === 'GET') {
      await fulfillJson(route, { connectionStatus: 'CONNECTED' });
      return;
    }

    if (url.pathname === '/api/tenant/triage-pause' && request.method() === 'PUT') {
      await route.fulfill({ status: 204, body: '' });
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
