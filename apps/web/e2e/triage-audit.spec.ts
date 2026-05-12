import { expect, test, type Page, type Route } from '@playwright/test';

import {
  API_ROUTE_PATTERN,
  expectNoHorizontalOverflow,
  seedAuthenticatedSession,
} from './chrome-test-utils';

// GAP: the triage-audit list endpoint does not exist in the backend as of 05A.
// This e2e covers the real production degradation state: the audit tab renders
// "audit history not available yet". Populated audit rows are covered by
// AuditLog.test.tsx with injected fixture data.

test.describe.configure({ mode: 'serial' });

for (const viewport of [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 320, height: 740 },
]) {
  test(`triage audit tab renders shell and unavailable state at ${viewport.name}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await openTriage(page, '/triage');

    await expect(page.getByTestId('chrome-header')).toBeVisible();
    await expect(page.getByTestId('balance-pill')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Triage' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Audit log' })).toBeVisible();
    await expect(page.getByTestId('audit-unavailable-panel')).toContainText(
      'Audit history is not available yet',
    );
    await expectNoHorizontalOverflow(page);

    if (viewport.width < 768) {
      await page.getByRole('button', { name: 'Toggle navigation' }).click();
      await expect(page.getByRole('link', { name: 'Triage' }).first()).toBeVisible();
    } else {
      await expect(page.getByRole('link', { name: 'Triage' }).first()).toBeVisible();
    }
  });

  test(`triage tab search param deep-links to shadow mode at ${viewport.name}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    await openTriage(page, '/triage?tab=shadow');

    await expect(page.getByRole('heading', { name: 'Triage' })).toBeVisible();
    await expect(page.getByText('Run triage as a safe rehearsal')).toBeVisible();
    await expect(page.getByTestId('shadow-mode-switch')).toBeVisible();
    await expectNoHorizontalOverflow(page);
  });
}

async function openTriage(page: Page, path: '/triage' | '/triage?tab=shadow') {
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
