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
  test(`triage audit tab renders shell and live empty state at ${viewport.name}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await openTriage(page, '/triage');

    await expect(page.getByTestId('chrome-header')).toBeVisible();
    await expectBalancePillForViewport(page, viewport.width);
    await expect(
      page.getByRole('heading', { name: 'AI email actions', exact: true }),
    ).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Activity' })).toBeVisible();
    await expect(page.getByText('No email actions yet')).toBeVisible();
    await expectNoHorizontalOverflow(page);

    if (viewport.width < 768) {
      await page.getByRole('button', { name: 'Toggle navigation' }).click();
      await expect(page.getByRole('link', { name: 'Triage' }).first()).toBeVisible();
    } else {
      await expect(page.getByRole('link', { name: 'Triage' }).first()).toBeVisible();
    }
  });

  test(`triage tab search param deep-links to protected senders at ${viewport.name}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await openTriage(page, '/triage?tab=senders');

    await expect(
      page.getByRole('heading', { name: 'AI email actions', exact: true }),
    ).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Protected senders' })).toHaveAttribute(
      'data-state',
      'active',
    );
    await expectNoHorizontalOverflow(page);
  });
}

async function openTriage(page: Page, path: '/triage' | '/triage?tab=senders') {
  await seedAuthenticatedSession(page);
  await installTriageApiMock(page);
  await page.goto(path, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle');
}

async function installTriageApiMock(page: Page) {
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

    if (url.pathname === '/api/triage/audit' && request.method() === 'GET') {
      await fulfillJson(route, { items: [], nextCursor: null });
      return;
    }

    if (url.pathname === '/api/triage/sender-safety-net' && request.method() === 'GET') {
      await fulfillJson(route, { items: [] });
      return;
    }

    if (url.pathname === '/api/threads' && request.method() === 'GET') {
      await fulfillJson(route, { items: [], nextCursor: null, toReplyCount: 0 });
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

async function expectBalancePillForViewport(page: Page, width: number) {
  const balancePill = page.getByTestId('balance-pill');
  if (width >= 420) {
    await expect(balancePill).toBeVisible();
    return;
  }

  await expect(balancePill).toHaveAttribute('aria-label', /Credits: 12/);
}
