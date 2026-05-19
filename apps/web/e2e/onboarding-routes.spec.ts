// Locks the onboarding routes 200 + StepIndicator contract
// (Phase 1.6 REQ-1.6-6):
//  - /onboarding/gmail-connect, /onboarding/template-select, /onboarding/complete
//    return 200 (or redirect to /login when unauthenticated — both acceptable)
//  - When authenticated, each route renders StepIndicator nav
import { expect, test, type Page, type Route } from '@playwright/test';

import {
  API_ROUTE_PATTERN,
  expectChromeSuppressed,
  expectNoClaySkinClasses,
  expectNoHorizontalOverflow,
} from './chrome-test-utils';

type OnboardingRoute = {
  path: '/onboarding/gmail-connect' | '/onboarding/template-select' | '/onboarding/complete';
  step: 'GMAIL_CONNECTED' | 'TEMPLATE_SELECTED';
};

const ROUTES: OnboardingRoute[] = [
  { path: '/onboarding/gmail-connect', step: 'GMAIL_CONNECTED' },
  { path: '/onboarding/template-select', step: 'GMAIL_CONNECTED' },
  { path: '/onboarding/complete', step: 'TEMPLATE_SELECTED' },
];

const VIEWPORTS = [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 320, height: 740 },
] as const;

test.describe('onboarding routes', () => {
  for (const route of ROUTES) {
    test(`${route.path} returns 200 with StepIndicator`, async ({ page }) => {
      const resp = await page.goto(route.path);
      // (protected) routes redirect to /login when unauthenticated; allow 200 OR redirect target /login.
      // The hard requirement is that the route HANDLER exists.
      expect([200, 302, 307]).toContain(resp?.status() ?? 0);
      // If we land on /login, that is acceptable for unauthenticated; otherwise StepIndicator must render.
      const url = page.url();
      if (!url.includes('/login')) {
        await expect(
          page.getByRole('navigation', { name: /onboarding progress|tiến trình/i }),
        ).toBeVisible();
      }
    });

    for (const viewport of VIEWPORTS) {
      test(`${route.path} suppresses app chrome at ${viewport.name}`, async ({ page }) => {
        await page.setViewportSize({ width: viewport.width, height: viewport.height });
        await openOnboardingRoute(page, route);

        await expect(
          page.getByRole('navigation', { name: /onboarding progress|tiến trình/i }),
        ).toBeVisible();
        await expectChromeSuppressed(page);
        await expectNoClaySkinClasses(page);
        await expectNoHorizontalOverflow(page);
      });
    }
  }
});

async function openOnboardingRoute(page: Page, route: OnboardingRoute) {
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
  await installOnboardingApiMock(page, route.step);
  await page.goto(route.path, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');
}

async function installOnboardingApiMock(
  page: Page,
  onboardingStep: 'GMAIL_CONNECTED' | 'TEMPLATE_SELECTED',
) {
  await page.route(API_ROUTE_PATTERN, async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (url.pathname === '/me') {
      await fulfillJson(route, {
        userId: 'user-1',
        tenantId: 'tenant-1',
        email: 'founder@example.com',
        preferredLanguage: 'en',
        onboardingStep,
        triagePaused: false,
        gmailConnectionStatus: {
          status: 'CONNECTED',
          ingestionHealth: 'HEALTHY',
          googleEmail: 'founder@example.com',
        },
      });
      return;
    }

    if (url.pathname === '/gmail/connection/status' && request.method() === 'GET') {
      await fulfillJson(route, { connectionStatus: 'CONNECTED' });
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
