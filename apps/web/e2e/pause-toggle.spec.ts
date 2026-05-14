import { expect, test } from '@playwright/test';

import { createChromeMockState, openAuthenticatedRoute } from './chrome-test-utils';

test.describe.configure({ mode: 'serial' });

for (const viewport of [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 320, height: 740 },
]) {
  test(`pause toggle shares state across chrome, banner, and settings at ${viewport.name}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const state = createChromeMockState({ triagePaused: false });
    await openAuthenticatedRoute(page, '/settings', state);

    await expect(page.getByTestId('pause-switch')).toContainText('Running');
    await page.getByTestId('pause-switch').click();
    await expect(page.getByRole('alertdialog')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Pause automatic triage?' })).toBeVisible();
    await page.getByRole('button', { name: 'Pause' }).click();

    await expect.poll(() => state.pauseRequests).toContainEqual({ paused: true });
    await expect(page.getByTestId('pause-banner')).toBeVisible();
    await expect(page.getByTestId('pause-switch')).toContainText('Paused');
    await page.getByTestId('settings-pause-switch').scrollIntoViewIfNeeded();
    await expect(page.getByTestId('settings-pause-switch')).not.toBeChecked();

    await page.getByTestId('pause-switch').click();
    await expect(page.getByRole('alertdialog')).toHaveCount(0);
    await expect.poll(() => state.pauseRequests).toContainEqual({ paused: false });
    await expect(page.getByTestId('pause-switch')).toContainText('Running');
  });
}
