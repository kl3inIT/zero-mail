// Locks the theme persistence e2e contract (Phase 1.6 REQ-1.6-7):
//  - Toggling theme writes zm-theme cookie and survives reload
//  - New tab inherits theme from cookie
import { test, expect } from '@playwright/test';

test.describe('theme persistence', () => {
  test('toggling theme writes zm-theme cookie and survives reload', async ({ page, context }) => {
    await page.goto('/');
    const toggle = page.getByRole('button', {
      name: /switch to dark mode|chuyển sang chế độ tối/i,
    });
    await toggle.click();

    // Cookie present
    await expect
      .poll(async () => {
        const cookies = await context.cookies();
        return cookies.find((c) => c.name === 'zm-theme')?.value;
      })
      .toBe('dark');

    // Reload — html.dark class persists
    await page.reload();
    const html = page.locator('html');
    await expect(html).toHaveClass(/\bdark\b/);
  });

  test('new tab inherits theme from cookie', async ({ context }) => {
    // NOTE (revision ISSUE-4): use url: form (not domain:) — Playwright's cookie matching
    // is strict on domain (no leading dot for localhost is fine, but url: is the canonical
    // form for fixtures bound to a baseURL). Switch to baseURL helper if the spec imports it.
    await context.addCookies([
      {
        name: 'zm-theme',
        value: 'dark',
        url: 'http://localhost:3000',
      },
    ]);
    const newPage = await context.newPage();
    await newPage.goto('/');
    await expect(newPage.locator('html')).toHaveClass(/\bdark\b/);
  });
});
