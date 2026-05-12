import { expect, test } from '@playwright/test';

import { createChromeMockState, openAuthenticatedRoute } from './chrome-test-utils';

test.describe.configure({ mode: 'serial' });

// The /billing page balance card + ledger + top-up assertions belong to Plan 04's e2e/billing-topup.spec.ts.

for (const routePath of ['/rules', '/settings'] as const) {
  for (const viewport of [
    { name: 'desktop', width: 1280, height: 820 },
    { name: 'mobile', width: 320, height: 740 },
  ]) {
    test(`billing balance pill refetches on ${routePath} at ${viewport.name}`, async ({ page }) => {
      await page.clock.install({ time: new Date('2026-05-12T00:00:00Z') });
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      const state = createChromeMockState({ availableCredits: 12 });
      await openAuthenticatedRoute(page, routePath, state);

      await expect(page.getByTestId('balance-pill')).toContainText('12');
      const initialBalanceRequests = state.balanceRequests;
      state.availableCredits = 19;

      await page.clock.fastForward(45_000);

      await expect(page.getByTestId('balance-pill')).toContainText('19');
      expect(state.balanceRequests).toBeGreaterThan(initialBalanceRequests);
    });
  }
}
