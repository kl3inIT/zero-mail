import { expect, test } from '@playwright/test';

import { createChromeMockState, openAuthenticatedRoute } from './chrome-test-utils';

test.describe.configure({ mode: 'serial' });

for (const routePath of ['/rules', '/settings'] as const) {
  for (const viewport of [
    { name: 'desktop', width: 1280, height: 820 },
    { name: 'mobile', width: 320, height: 740 },
  ]) {
    test(`connection health covers connected and disconnected on ${routePath} at ${viewport.name}`, async ({
      page,
    }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      const state = createChromeMockState({ connectionStatus: 'CONNECTED' });
      await openAuthenticatedRoute(page, routePath, state);

      await expect(page.getByTestId('connection-health-dot')).toHaveAttribute(
        'data-status',
        'CONNECTED',
      );
      await expect(page.getByTestId('reconnect-gmail-button')).toHaveCount(0);

      state.connectionStatus = 'DISCONNECTED';
      await page.reload({ waitUntil: 'domcontentloaded' });

      await expect(page.getByTestId('connection-health-dot')).toHaveAttribute(
        'data-status',
        'DISCONNECTED',
      );
      await expect(page.getByTestId('reconnect-gmail-button')).toBeVisible();
    });
  }
}
