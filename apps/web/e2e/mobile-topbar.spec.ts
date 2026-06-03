import { expect, test } from '@playwright/test';

test.describe('mobile public topbar', () => {
  test('keeps language switcher and get-started CTA visible at 320px', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 740 });
    await page.goto('/', { waitUntil: 'domcontentloaded' });

    await expect(page.getByRole('group', { name: /language/i })).toBeVisible();
    // Bundled Google OAuth = login is signup, so logged-out visitors see a single
    // "Get started free" / "Bắt đầu miễn phí" CTA (nav.getStarted), not a Sign in link.
    // Scope to the header — the hero CTA shares the same accessible name.
    await expect(
      page.getByRole('banner').getByRole('link', { name: /bắt đầu miễn phí|get started free/i }),
    ).toBeVisible();
  });
});
