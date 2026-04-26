import { test, expect } from '@playwright/test';

/**
 * Phase 1.3 Plan 05 Task 4 — Route smoke (REVIEWS Revision 2 — Codex HIGH #5).
 *
 * Gates the deletion of `app/[locale]/` (Task 5). Context7 next-intl docs warn
 * that `localePrefix: 'never'` may still rely on a [locale] segment for some
 * rewrite paths; PATTERNS.md asserts the mirror files are dead but empirical
 * confirmation against `next dev` is mandatory before deletion.
 *
 * Coverage:
 *   - / , /login, /docs, /docs/getting-started → 200 (anonymous)
 *   - /onboarding, /settings → redirect to /login (302/307) or 200 after chain
 *   - "no nested <main>" → exactly one <main> per page (REVIEWS Rev 2 #4)
 *
 * Plan 06 shipped the docs scaffold (apps/web/content/docs/*.mdx + the (public)
 * docs index/[slug]/loading routes), so /docs and /docs/getting-started are now
 * runnable. The route-smoke runs them as ordinary tests; CI / fresh dev env
 * gives them the empirical signal we lacked in Plan 05's sandbox.
 */

const ALWAYS_AVAILABLE_ROUTES = [
  { path: '/', expectedStatus: 200 },
  { path: '/login', expectedStatus: 200 },
  // /onboarding + /settings: anonymous user → proxy.ts redirects to /login.
  // Accept either redirect-then-200 or direct 200.
  { path: '/onboarding', expectedStatuses: [200, 302, 307] },
  { path: '/settings', expectedStatuses: [200, 302, 307] },
] as const;

const PLAN_06_PENDING_ROUTES = [
  { path: '/docs', expectedStatus: 200 },
  { path: '/docs/getting-started', expectedStatus: 200 },
] as const;

test.describe('Phase 1.3 route-smoke (REVIEWS Revision 2 — Codex HIGH #5)', () => {
  for (const route of ALWAYS_AVAILABLE_ROUTES) {
    test(`${route.path} resolves`, async ({ page }) => {
      const response = await page.goto(route.path, { waitUntil: 'domcontentloaded' });
      expect(response).not.toBeNull();
      const status = response!.status();
      if ('expectedStatus' in route) {
        expect(status).toBe(route.expectedStatus);
      } else {
        expect(route.expectedStatuses).toContain(status);
      }
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
