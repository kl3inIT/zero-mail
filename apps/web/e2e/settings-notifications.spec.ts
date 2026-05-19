import { expect, test } from '@playwright/test';

import {
  createChromeMockState,
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
  test(`notification preferences persist at ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const state = createChromeMockState();

    await openAuthenticatedRoute(page, '/settings', state);

    await expect(page.getByTestId('notifications-section')).toBeVisible();
    await expect(page.getByTestId('daily-digest-switch')).toBeChecked();
    await expect(page.getByText(/no automatic catch-up/i)).toBeVisible();

    await page.getByTestId('daily-digest-switch').click();
    await expect
      .poll(() => state.notificationPreferenceUpdates)
      .toContainEqual({
        digestEnabled: false,
        digestSendHourLocal: 20,
      });
    await expect(page.getByTestId('daily-digest-switch')).not.toBeChecked();
    await expect(page.getByTestId('digest-send-hour-select')).toBeDisabled();

    await page.reload({ waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('daily-digest-switch')).not.toBeChecked({ timeout: 15_000 });

    await page.getByTestId('daily-digest-switch').click();
    await expect(page.getByTestId('digest-send-hour-select')).toBeEnabled();
    await page.getByTestId('digest-send-hour-select').click();
    await page.getByRole('option', { name: '08:00' }).click();

    await expect
      .poll(() => state.notificationPreferenceUpdates)
      .toContainEqual({
        digestEnabled: true,
        digestSendHourLocal: 8,
      });
    await expect(page.getByTestId('digest-send-hour-select')).toContainText('08:00');
    await expectNoHorizontalOverflow(page);
  });
}

test('notification downtime note is localized', async ({ page }) => {
  const state = createChromeMockState({ preferredLanguage: 'vi' });
  await seedAuthenticatedSession(page, 'vi');
  await installChromeApiMock(page, state);
  await page.goto('/settings', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');

  await expect(page.getByTestId('notifications-section')).toBeVisible();
  await expect(page.getByText(/không có cơ chế gửi bù/i)).toBeVisible();
});
