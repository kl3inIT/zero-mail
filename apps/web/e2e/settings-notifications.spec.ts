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

    // Notification preferences moved from /settings into the /ai page's Updates
    // section (refactor 676e36b9 "flatten /settings") with `ai-` prefixed testids.
    await openAuthenticatedRoute(page, '/ai', state);

    await expect(page.getByTestId('notifications-section')).toBeVisible();
    await expect(page.getByTestId('ai-daily-digest-switch')).toBeChecked();
    await expect(page.getByText(/no automatic catch-up/i)).toBeVisible();

    await page.getByTestId('ai-daily-digest-switch').click();
    // objectContaining tolerates the weekly-digest `digestSendDayOfWeek` field the payload now
    // carries — this test only asserts the enabled flag and send hour persist.
    await expect
      .poll(() => state.notificationPreferenceUpdates)
      .toContainEqual(
        expect.objectContaining({
          digestEnabled: false,
          digestSendHourLocal: 20,
        }),
      );
    await expect(page.getByTestId('ai-daily-digest-switch')).not.toBeChecked();
    // When the digest is off the send-hour select is unmounted (not just disabled).
    await expect(page.getByTestId('ai-digest-send-hour-select')).toHaveCount(0);

    await page.reload({ waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('ai-daily-digest-switch')).not.toBeChecked({ timeout: 15_000 });

    await page.getByTestId('ai-daily-digest-switch').click();
    await expect(page.getByTestId('ai-digest-send-hour-select')).toBeEnabled();
    await page.getByTestId('ai-digest-send-hour-select').click();
    await page.getByRole('option', { name: '08:00' }).click();

    await expect
      .poll(() => state.notificationPreferenceUpdates)
      .toContainEqual(
        expect.objectContaining({
          digestEnabled: true,
          digestSendHourLocal: 8,
        }),
      );
    await expect(page.getByTestId('ai-digest-send-hour-select')).toContainText('08:00');
    await expectNoHorizontalOverflow(page);
  });
}

test('notification downtime note is localized', async ({ page }) => {
  const state = createChromeMockState({ preferredLanguage: 'vi' });
  await seedAuthenticatedSession(page, 'vi');
  await installChromeApiMock(page, state);
  await page.goto('/ai', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');

  await expect(page.getByTestId('notifications-section')).toBeVisible();
  await expect(page.getByText(/không có cơ chế gửi bù/i)).toBeVisible();
});
