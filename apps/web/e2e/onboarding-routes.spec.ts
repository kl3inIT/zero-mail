// Wave 0 RED scaffold — locks the onboarding routes 200 + StepIndicator contract
// (Phase 1.6 REQ-1.6-6):
//  - /onboarding/gmail-connect, /onboarding/template-select, /onboarding/complete
//    return 200 (or redirect to /login when unauthenticated — both acceptable)
//  - When authenticated, each route renders StepIndicator nav
//
// RED-by-design: onboarding routes do not yet exist at the new URL split paths.
// This spec becomes GREEN when Phase 1.6 Wave 2 lands.
import { test, expect } from '@playwright/test';

test.describe('onboarding routes', () => {
  for (const route of [
    '/onboarding/gmail-connect',
    '/onboarding/template-select',
    '/onboarding/complete',
  ]) {
    test(`${route} returns 200 with StepIndicator`, async ({ page }) => {
      const resp = await page.goto(route);
      // (protected) routes redirect to /login when unauthenticated; allow 200 OR redirect target /login.
      // The hard requirement is that the route HANDLER exists.
      expect([200, 302, 307]).toContain(resp?.status() ?? 0);
      // If we land on /login, that is acceptable for unauthenticated; otherwise StepIndicator must render.
      const url = page.url();
      if (!url.includes('/login')) {
        await expect(
          page.getByRole('navigation', { name: /onboarding progress|tiến trình/i }),
        ).toBeVisible();
      }
    });
  }
});
