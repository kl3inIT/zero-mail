import { expect, test } from '@playwright/test';

import {
  createChromeMockState,
  expectAppShellChrome,
  expectNoClaySkinClasses,
  expectNoHorizontalOverflow,
  installChromeApiMock,
  openAuthenticatedRoute,
  seedAuthenticatedSession,
} from './chrome-test-utils';

test.describe.configure({ mode: 'serial' });

for (const viewport of [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 320, height: 740 },
]) {
  test(`analytics panels and window switching at ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const state = createChromeMockState();

    await openAuthenticatedRoute(page, '/analytics', state);

    await expectAppShellChrome(page, { sidebarVisible: viewport.name === 'desktop' });
    await expect(page.getByRole('heading', { name: 'Analytics' })).toBeVisible();
    await expect(page.getByTestId('analytics-volume-panel')).toBeVisible();
    await expect(page.getByTestId('analytics-time-saved-panel')).toBeVisible();
    await expect(page.getByTestId('analytics-inbox-flow-panel')).toBeVisible();
    await expect(page.getByTestId('analytics-top-senders-panel')).toBeVisible();
    await expect(page.getByTestId('analytics-metadata-load-panel')).toBeVisible();
    await expect(page.getByTestId('analytics-metadata-control-panel')).toBeVisible();
    await expect(page.getByTestId('analytics-rule-hits-panel')).toBeVisible();
    await expect.poll(() => state.analyticsRequests).toContain('7d');

    // PR #66 redesign swapped the window selector from <Tabs> (TabsTrigger named
    // "Last 30 days") to shadcn <Select> (combobox "Date range" → option "Last
    // month"). Open the combobox, then click the option.
    await page.getByRole('combobox', { name: 'Date range' }).click();
    await page.getByRole('option', { name: 'Last month' }).click();

    await expect(page).toHaveURL(/\/analytics\?window=30d/);
    await expect.poll(() => state.analyticsRequests).toContain('30d');
    await expect(page.getByTestId('analytics-volume-panel')).toContainText('2494');
    await expectNoHorizontalOverflow(page);
    await expectNoClaySkinClasses(page);
  });
}

test('analytics canonicalizes empty and invalid window params before fetching', async ({
  page,
}) => {
  const state = createChromeMockState();
  await seedAuthenticatedSession(page);
  await installChromeApiMock(page, state);

  await page.goto('/analytics?window=', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');
  await expect(page.getByRole('heading', { name: 'Analytics' })).toBeVisible();
  await expect(page).toHaveURL(/\/analytics\?window=7d/, { timeout: 15_000 });
  await expect.poll(() => state.analyticsRequests).toEqual(['7d']);

  state.analyticsRequests = [];
  await page.goto('/analytics?window=bogus', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');
  await expect(page.getByRole('heading', { name: 'Analytics' })).toBeVisible();
  await expect(page).toHaveURL(/\/analytics\?window=7d/, { timeout: 15_000 });
  await expect.poll(() => state.analyticsRequests).toEqual(['7d']);
});

test('analytics renders Vietnamese copy', async ({ page }) => {
  const state = createChromeMockState({ preferredLanguage: 'vi' });
  await openAuthenticatedRoute(page, '/analytics', state);

  await expect(page.getByRole('heading', { name: 'Phân tích' })).toBeVisible();
  await expect(page.getByRole('tab', { name: '7 ngày qua' })).toBeVisible();
});
