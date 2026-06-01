import { expect, test } from '@playwright/test';

import {
  createChromeMockState,
  expectNoHorizontalOverflow,
  openAuthenticatedRoute,
} from './chrome-test-utils';

test.describe.configure({ mode: 'serial' });

// Post-PR-#66 redesign: the top chrome header bar was removed entirely along
// with the balance pill, connection-health dot, and pause switch features.
// What remains: a sidebar-first shell (app-sidebar) wrapping the content
// region (app-shell-content) inside a SidebarProvider (app-shell).

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

    await expect(page.getByTestId('app-shell')).toBeVisible();
    await expect(page.getByTestId('app-shell-content')).toBeVisible();
    await expectNoHorizontalOverflow(page);

    if (viewport.width < 768) {
      await page.getByRole('button', { name: 'Toggle navigation' }).click();
      await expect(page.getByRole('link', { name: 'Rules' }).first()).toBeVisible();
    } else {
      await expect(page.getByRole('link', { name: 'Rules' }).first()).toBeVisible();
    }

    await page.goto('/settings', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('load');
    await expect(page.getByTestId('app-shell')).toBeVisible();
    await expect(page.getByTestId('app-shell-content')).toBeVisible();
    await expectNoHorizontalOverflow(page);
  });
}

test('app shell remains mounted across client navigation and hidden onboarding redirects to app chrome', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1280, height: 820 });
  const state = createChromeMockState();
  await openAuthenticatedRoute(page, '/rules', state);

  await page
    .getByTestId('app-shell-content')
    .evaluate((contentElement) =>
      contentElement.setAttribute('data-preservation-check', 'before-settings'),
    );
  // Settings moved into the user dropdown post-refactor; use a still-visible
  // top-level sidebar link to exercise client-side navigation shell preservation.
  await page.getByRole('link', { name: 'Analytics' }).first().click();
  await expect(page).toHaveURL(/\/analytics$/);
  await expect(page.getByTestId('app-shell-content')).toHaveAttribute(
    'data-preservation-check',
    'before-settings',
  );

  state.onboardingStep = 'GMAIL_CONNECTED';
  await page.goto('/onboarding/gmail-connect', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');
  await expect(page).toHaveURL(/\/rules$/);
  await expect(page.getByTestId('app-shell')).toBeVisible();
  await expect(page.getByTestId('app-sidebar')).toBeVisible();
});
