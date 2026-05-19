import { expect, test, type Page } from '@playwright/test';

import {
  createChromeMockState,
  expectNoHorizontalOverflow,
  openAuthenticatedRoute,
} from './chrome-test-utils';

test.describe.configure({ mode: 'serial' });

// Plan 03/04 own the /triage and /billing shell-presence checks.

for (const viewport of [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 320, height: 740 },
]) {
  test(`app shell renders authenticated chrome on existing routes at ${viewport.name}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const state = createChromeMockState();
    await openAuthenticatedRoute(page, '/rules', state);

    await expect(page.getByTestId('chrome-header')).toBeVisible();
    await expectBalancePillForViewport(page, viewport.width);
    await expect(page.getByTestId('connection-health-dot')).toBeVisible();
    await expect(page.getByTestId('pause-switch')).toBeVisible();
    await expectNoHorizontalOverflow(page);

    if (viewport.width < 768) {
      await page.getByRole('button', { name: 'Toggle navigation' }).click();
      await expect(page.getByRole('link', { name: 'Rules' }).first()).toBeVisible();
    } else {
      await expect(page.getByRole('link', { name: 'Rules' }).first()).toBeVisible();
    }

    await page.goto('/settings', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('load');
    await expect(page.getByTestId('chrome-header')).toBeVisible();
    await expectBalancePillForViewport(page, viewport.width);
    await expect(page.getByTestId('connection-health-dot')).toBeVisible();
    await expect(page.getByTestId('pause-switch')).toBeVisible();
    await expectNoHorizontalOverflow(page);
  });
}

test('app shell remains mounted across client navigation and onboarding is chrome-suppressed', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1280, height: 820 });
  const state = createChromeMockState();
  await openAuthenticatedRoute(page, '/rules', state);

  await page
    .getByTestId('chrome-header')
    .evaluate((headerElement) =>
      headerElement.setAttribute('data-preservation-check', 'before-settings'),
    );
  await page.getByRole('link', { name: 'Settings' }).first().click();
  await expect(page).toHaveURL(/\/settings$/);
  await expect(page.getByTestId('chrome-header')).toHaveAttribute(
    'data-preservation-check',
    'before-settings',
  );

  state.onboardingStep = 'GMAIL_CONNECTED';
  await page.goto('/onboarding/gmail-connect', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');
  await expect(page.getByTestId('chrome-header')).toHaveCount(0);
  await expect(page.getByTestId('app-sidebar')).toHaveCount(0);
});

async function expectBalancePillForViewport(page: Page, width: number) {
  const balancePill = page.getByTestId('balance-pill');
  if (width >= 420) {
    await expect(balancePill).toBeVisible();
    return;
  }

  await expect(balancePill).toHaveAttribute('aria-label', /Credits: 12/);
}
