import { test, expect } from '@playwright/test';

/**
 * Phase 1.3 Plan 05 Task 4 — Route smoke (REVIEWS Revision 2 — Codex HIGH #5).
 *
 * Runtime guard for clean URLs without an `app/[locale]` mirror. next-intl's
 * routing middleware rewrites `localePrefix: 'never'` requests to hidden
 * `/vi/...` paths; this app deliberately avoids that middleware and reads
 * NEXT_LOCALE directly from the cookie instead.
 *
 * Coverage:
 *   - / , /login, /docs, /docs/getting-started → 200 (anonymous)
 *   - /onboarding, /settings → anonymous users end at /login
 *   - "no nested <main>" → exactly one <main> per page (REVIEWS Rev 2 #4)
 *
 * Plan 06 shipped the docs scaffold (apps/web/content/docs/*.mdx + the (public)
 * docs index/[slug]/loading routes), so /docs and /docs/getting-started are now
 * runnable. The route-smoke runs them as ordinary tests; CI / fresh dev env
 * gives them the empirical signal we lacked in Plan 05's sandbox.
 */

const PUBLIC_ROUTES = [
  { path: '/', expectedStatus: 200 },
  { path: '/login', expectedStatus: 200 },
] as const;

const PROTECTED_ROUTES = ['/onboarding', '/settings'] as const;

const PLAN_06_PENDING_ROUTES = [
  { path: '/docs', expectedStatus: 200 },
  { path: '/docs/getting-started', expectedStatus: 200 },
] as const;

test.describe('Phase 1.3 route-smoke (REVIEWS Revision 2 — Codex HIGH #5)', () => {
  for (const route of PUBLIC_ROUTES) {
    test(`${route.path} resolves`, async ({ page }) => {
      const response = await page.goto(route.path, { waitUntil: 'domcontentloaded' });
      expect(response).not.toBeNull();
      expect(response!.status()).toBe(route.expectedStatus);
    });
  }

  for (const path of PROTECTED_ROUTES) {
    test(`${path} redirects anonymous users to login`, async ({ page }) => {
      await page.goto(path, { waitUntil: 'domcontentloaded' });
      await expect(page).toHaveURL(/\/login$/);
    });
  }

  for (const route of PLAN_06_PENDING_ROUTES) {
    test(`${route.path} resolves`, async ({ page }) => {
      const response = await page.goto(route.path, { waitUntil: 'domcontentloaded' });
      expect(response).not.toBeNull();
      expect(response!.status()).toBe(route.expectedStatus);
    });
  }

  test('no nested <main> elements anywhere (REVIEWS Revision 2 — Codex HIGH #4)', async ({
    page,
  }) => {
    for (const path of ['/', '/login']) {
      await page.goto(path, { waitUntil: 'domcontentloaded' });
      const mainCount = await page.locator('main').count();
      expect(mainCount, `expected exactly one <main> on ${path}`).toBe(1);
    }
  });
});
