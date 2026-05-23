// Beta onboarding is intentionally hidden: direct onboarding routes redirect to
// the main app while the implementation remains in the codebase for later.
import { expect, test } from '@playwright/test';

type OnboardingPath =
  | '/onboarding'
  | '/onboarding/gmail-connect'
  | '/onboarding/template-select'
  | '/onboarding/complete';

const ROUTES: OnboardingPath[] = [
  '/onboarding',
  '/onboarding/gmail-connect',
  '/onboarding/template-select',
  '/onboarding/complete',
];

test.describe('hidden beta onboarding routes', () => {
  for (const path of ROUTES) {
    test(`${path} redirects to rules`, async ({ request }) => {
      const response = await request.get(path, {
        headers: {
          Cookie: 'ZEROMAIL_SESSION=playwright-session; NEXT_LOCALE=en',
        },
        maxRedirects: 0,
      });

      expect([302, 303, 307, 308]).toContain(response.status());
      expect(response.headers().location).toBe('/rules');
    });
  }
});
