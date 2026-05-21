import { http, HttpResponse } from 'msw';

/**
 * MSW handlers for Next.js server-side fetches (RSC, proxy.ts, route handlers).
 *
 * Why this exists: Playwright's `page.route()` interceptors only see the BROWSER's
 * fetch traffic. Server Components and proxy.ts run inside the Next.js server
 * process and their `fetch()` calls go straight to the configured backend
 * (`API_BASE_URL` or `http://localhost:8080`). Without an interceptor here, an
 * RSC fetch like `getCurrentUserCached(...)` in `app/(protected)/(app)/layout.tsx`
 * crashes the server-rendered page when the backend is not running, which then
 * cascades into "[CSP] connect-src violated" or "render aborted" errors in e2e.
 *
 * Activation: `instrumentation.ts` calls `setupServer(...handlers).listen()`
 * ONLY when `NEXT_PUBLIC_E2E_MSW=1`. Production / regular `pnpm dev` is
 * unaffected — there is no MSW import on the hot path.
 *
 * Shape parity: keep these payloads in sync with the browser-side mocks in
 * `e2e/chrome-test-utils.ts`. The server-rendered initial paint and the
 * post-hydration TanStack Query refetch must agree, otherwise React will throw
 * a hydration mismatch.
 *
 * Wildcard host matching: handlers use a leading asterisk so they intercept
 * regardless of whether the server resolves `API_BASE_URL` to
 * `http://localhost:8080`, `http://backend:8080`, or any future override.
 */

const PROTECTED_USER = {
  userId: 'user-1',
  tenantId: 'tenant-1',
  email: 'founder@example.com',
  preferredLanguage: 'en',
  onboardingStep: 'COMPLETE',
  triagePaused: false,
  gmailConnectionStatus: {
    status: 'CONNECTED',
    ingestionHealth: 'HEALTHY',
    googleEmail: 'founder@example.com',
  },
} as const;

export const handlers = [
  http.get('*/api/me', () => HttpResponse.json(PROTECTED_USER)),

  http.get('*/api/billing/balance', () =>
    HttpResponse.json({
      availableCredits: 12,
      heldCredits: 0,
      currency: 'credits',
      betaCredits: 12,
      paidCredits: 0,
      monthlyGrantCredits: 300,
      resetsAt: '2026-06-01T00:00:00.000Z',
      freeDuringBeta: true,
    }),
  ),

  http.get('*/api/billing/ledger', () =>
    HttpResponse.json({
      entries: [],
      nextCursor: null,
    }),
  ),

  http.get('*/api/gmail/connection/status', () =>
    HttpResponse.json({
      connectionStatus: 'CONNECTED',
      googleEmail: 'founder@example.com',
    }),
  ),
];
