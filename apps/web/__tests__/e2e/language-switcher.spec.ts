import { expect, test } from '@playwright/test';

/**
 * Phase 1.1 Plan 07 Task 2 — Switcher persistence smoke (REQ-2).
 *
 * Threat reference (planning_context security_threat_model #4):
 *   "NEXT_LOCALE downgraded to session cookie" — mitigated by asserting the
 *   cookie's `expires` is roughly 1 year from now (>= now + 364d).
 *
 * Coverage scope (intentionally narrow per VALIDATION.md):
 *   - Test #1 (UNAUTHENTICATED, runs): visit /login, default vi, switch to en,
 *     assert <html lang="en"> AND NEXT_LOCALE cookie persisted with 1y expiry.
 *   - Test #2 (AUTHENTICATED, skipped): the PATCH /me/language flow needs an
 *     auth fixture that Phase 1.2 will provide. Plan 06 Task 3 step 10 is the
 *     compensating manual checkpoint and MUST be filled in before this plan
 *     can be considered fully approved.
 */

test('default Vietnamese, switch to English, NEXT_LOCALE cookie persists across context restart with ~1y maxAge', async ({
  browser,
}) => {
  test.setTimeout(60_000);

  const ctx1 = await browser.newContext();
  const page1 = await ctx1.newPage();
  await page1.goto('/login', { waitUntil: 'domcontentloaded' });

  // Default is Vietnamese (i18n/routing.ts defaultLocale = 'vi').
  await expect(page1.locator('html')).toHaveAttribute('lang', 'vi');

  // Phase 1.6 reskinned the old menu into a segmented control in the topbar.
  await page1
    .getByRole('group', { name: /language/i })
    .getByRole('button', { name: /english/i })
    .click();

  await page1.waitForFunction(() => document.cookie.includes('NEXT_LOCALE=en'));

  // After router.refresh() the RSC re-renders <html lang="en">.
  await expect(page1.locator('html')).toHaveAttribute('lang', 'en', {
    timeout: 15_000,
  });

  // The cookie must be NEXT_LOCALE=en with a far-future expiry (~1y, REQ-2).
  const cookies = await ctx1.cookies();
  const next = cookies.find((c) => c.name === 'NEXT_LOCALE');
  expect(next, 'NEXT_LOCALE cookie must be set after switcher click').toBeDefined();
  expect(next?.value).toBe('en');

  // expires is a unix-epoch number-of-seconds; -1 means session cookie (FAIL).
  // We tolerate ~1 day clock drift but require >= now + 364d.
  const nowSec = Math.floor(Date.now() / 1000);
  const minExpiry = nowSec + 60 * 60 * 24 * 364;
  expect(next?.expires ?? -1).toBeGreaterThanOrEqual(minExpiry);

  await ctx1.close();

  // Simulate "browser restart": new context inheriting the persisted cookie.
  // newContext() does NOT inherit cookies by default — that IS the realistic
  // browser-restart scenario. We re-issue the cookie programmatically (the
  // browser would do this from its on-disk cookie jar) and assert that the
  // page renders English on first paint.
  const ctx2 = await browser.newContext({
    storageState: { cookies: cookies, origins: [] },
  });
  const page2 = await ctx2.newPage();
  await page2.goto('/login', { waitUntil: 'domcontentloaded' });
  await expect(page2.locator('html')).toHaveAttribute('lang', 'en');

  await ctx2.close();
});

test('authenticated PATCH /me/language smoke', async () => {
  test.skip(
    true,
    'Requires authenticated test fixture - Phase 1.2 will add the auth helper. ' +
      'Cross-browser cross-session PATCH /me/language coverage is provided MANUALLY ' +
      'by Plan 06 Task 3 step 10 (logged in 01.1-06-SUMMARY.md with date + reviewer initials).',
  );
});
