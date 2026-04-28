// Wave 0 RED scaffold — locks the login 2-col/1-col + legal footer e2e contract
// (Phase 1.6 REQ-1.6-6 + REQ-1.6-8):
//  - Desktop ≥768px renders 2-column shell with TrustPanel
//  - Mobile <768px hides TrustPanel, shows single column
//  - Legal footer always visible at every viewport width
//
// RED-by-design: login page does not yet have the 2-col layout or TrustPanel.
// This spec becomes GREEN when Phase 1.6 Wave 2 lands.
import { test, expect } from '@playwright/test';

test.describe('/login shell', () => {
  test('desktop ≥768px renders 2-column shell with TrustPanel', async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 768 });
    await page.goto('/login');
    // TrustPanel marker (aside with hidden md:flex visibility)
    const trustPanel = page
      .locator('aside')
      .filter({ hasText: /no email content stored|không lưu trữ nội dung email/i });
    await expect(trustPanel).toBeVisible();
  });

  test('mobile <768px hides TrustPanel, shows single column', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto('/login');
    const trustPanel = page
      .locator('aside')
      .filter({ hasText: /no email content stored|không lưu trữ nội dung email/i });
    await expect(trustPanel).not.toBeVisible();
  });

  test.describe('legal footer always visible', () => {
    for (const width of [320, 480, 768, 1024, 1280]) {
      test(`legal footer visible at ${width}px`, async ({ page }) => {
        await page.setViewportSize({ width, height: 800 });
        await page.goto('/login');
        await expect(page.getByRole('link', { name: /terms|điều khoản/i })).toBeVisible();
        await expect(page.getByRole('link', { name: /privacy|bảo mật/i })).toBeVisible();
        await expect(
          page.getByText(
            /Google API Services User Data|Chính sách bảo mật dữ liệu người dùng của Google API/i,
          ),
        ).toBeVisible();
      });
    }
  });
});
