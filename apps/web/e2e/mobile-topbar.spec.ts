import { expect, test } from '@playwright/test';

test.describe('mobile public topbar', () => {
  test('keeps language switcher and sign-in CTA visible at 320px', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 740 });
    await page.goto('/', { waitUntil: 'domcontentloaded' });

    await expect(page.getByRole('group', { name: /language/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /đăng nhập|sign in/i })).toBeVisible();
  });
});
