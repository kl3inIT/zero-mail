// Locks the simplified one-card login + legal footer e2e contract
// (Phase 1.6 REQ-1.6-6 + REQ-1.6-8):
//  - Desktop and mobile render one login panel without the old side panel
//  - Company Gmail option is visible but disabled during beta
//  - Legal footer always visible at every viewport width
import { test, expect } from '@playwright/test';

test.describe('/login shell', () => {
  test('desktop renders one focused login panel', async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 768 });
    await page.goto('/login');
    await expect(page.locator('main aside')).toHaveCount(0);
    await expect(
      page.getByRole('heading', { name: /sign in to start|đăng nhập để bắt đầu/i }),
    ).toBeVisible();
  });

  test('mobile renders one focused login panel', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto('/login');
    await expect(page.locator('main aside')).toHaveCount(0);
    await expect(
      page.getByRole('heading', { name: /sign in to start|đăng nhập để bắt đầu/i }),
    ).toBeVisible();
  });

  test('company Gmail sign-in is visible but disabled during beta', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('button', { name: /company gmail|gmail công ty/i })).toBeDisabled();
  });

  test.describe('legal footer always visible', () => {
    for (const width of [320, 480, 768, 1024, 1280]) {
      test(`legal footer visible at ${width}px`, async ({ page }) => {
        await page.setViewportSize({ width, height: 800 });
        await page.goto('/login');
        const footer = page.getByRole('contentinfo');
        await expect(footer.locator('a[href="/terms"]')).toBeVisible();
        await expect(footer.locator('a[href="/privacy"]')).toBeVisible();
        await expect(
          page.getByText(/Google API User Data Policy|Chính sách dữ liệu người dùng Google API/i),
        ).toBeVisible();
      });
    }
  });
});
