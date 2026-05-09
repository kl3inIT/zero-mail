// Locks the legal routes 200 contract (Phase 1.6 REQ-1.6-8):
//  - /terms and /privacy return HTTP 200 with non-empty body
import { test, expect } from '@playwright/test';

test.describe('legal stub routes', () => {
  for (const route of ['/terms', '/privacy']) {
    test(`${route} returns 200 with non-empty body`, async ({ page }) => {
      const resp = await page.goto(route);
      expect(resp?.status()).toBe(200);
      const main = page.locator('main');
      await expect(main).toBeVisible();
      const text = await main.textContent();
      expect((text ?? '').trim().length).toBeGreaterThan(20);
    });
  }
});
