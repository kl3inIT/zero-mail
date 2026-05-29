import createClient, { type Middleware } from 'openapi-fetch';

import { getApiBase } from './base-url';
import type { paths } from './schema';

// Real, typed openapi-fetch client. The earlier LooseClient cast was a
// placeholder from when `schema.d.ts` had no `paths` and route components
// needed an ergonomic untyped surface. `pnpm generate:api` now produces
// a full `paths` map, so the cast is no longer needed and was actively
// stripping path-parameter validation and request-body shape checking
// from every callsite (REVIEW WR-10).
export const api = createClient<paths>({
  baseUrl: getApiBase(),
  credentials: 'include',
});

// 401 → hard redirect to /login. Runs at fetch boundary so every caller
// (queries, mutations, server actions invoking client.ts on the client)
// gets the same behavior without per-callsite checks. Skip when already
// on a public auth surface to avoid redirect loops.
const PUBLIC_AUTH_PATHS = ['/login', '/error', '/privacy', '/terms', '/docs'];
const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

function readXsrfCookie(): string | undefined {
  if (typeof document === 'undefined') return undefined;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

// Spring Security `csrf().spa()` issues an XSRF-TOKEN cookie (HttpOnly=false).
// Mutating requests must echo it back as X-XSRF-TOKEN. Doing this at the fetch
// boundary means feature API wrappers no longer need to plumb the header
// per-endpoint — single source of truth, parity with admin app.
const xsrfMiddleware: Middleware = {
  async onRequest({ request }) {
    if (!MUTATING_METHODS.has(request.method.toUpperCase())) return undefined;
    if (request.headers.has('X-XSRF-TOKEN')) return undefined;
    const token = readXsrfCookie();
    if (!token) return undefined;
    request.headers.set('X-XSRF-TOKEN', token);
    return request;
  },
};

const unauthorizedRedirectMiddleware: Middleware = {
  async onResponse({ response }) {
    if (response.status !== 401 || typeof window === 'undefined') return undefined;
    const path = window.location.pathname;
    if (PUBLIC_AUTH_PATHS.some((publicPath) => path.startsWith(publicPath))) return undefined;
    window.location.assign('/login');
    return undefined;
  },
};

// Playwright e2e short-circuit (server-side only). The default
// playwright.config.ts spins up `next dev` with NO Spring Boot backend, so
// EVERY server-side fetch loses to ECONNREFUSED:
//   - proxy.ts middleware /me on every navigation
//   - app/layout.tsx /me (locale reassert)
//   - (protected)/(app)/layout.tsx prefetchQuery triplet (me, balance, status)
//   - analytics/page.tsx prefetchQuery (summary)
//   - settings/page.tsx /me
//   - landing Hero/TopBar /me
// Next's error reporter logs "fetch failed / Switched to client rendering"
// stack BEFORE caller try/catch (or queryClient.prefetchQuery's rejected-
// promise handling) can absorb it, drowning Playwright output in noise AND
// slowing hydration enough to flake real assertions (e.g. the analytics
// "Last 30 days" tab click in analytics.spec.ts:36).
//
// Throwing at the openapi-fetch request boundary catches every api.GET/POST/
// PUT/PATCH/DELETE through ONE chokepoint. Browser side keeps working —
// Playwright's page.route() mocks own the per-test response surface for
// client React Query hooks, which is the existing test contract.
//
// playwright.golden.config.ts runs against a real Spring Boot e2e-stub backend
// and leaves ZM_E2E unset, so the launch-golden-path spec still exercises
// real /me, /balance, /status, /analytics end-to-end.
const zmE2eShortCircuitMiddleware: Middleware = {
  async onRequest({ request }) {
    if (typeof window === 'undefined' && process.env.ZM_E2E === '1') {
      return e2eServerResponse(request);
    }
    return request;
  },
};

api.use(zmE2eShortCircuitMiddleware);
api.use(xsrfMiddleware);
api.use(unauthorizedRedirectMiddleware);

export function adaptFetchForOpenApi(
  fetcher: typeof fetch | undefined,
): ((request: Request) => Promise<Response>) | undefined {
  if (!fetcher) return undefined;
  return async (request) => {
    const response = await fetcher(request.url, {
      body: request.body,
      cache: request.cache,
      credentials: request.credentials,
      headers: Object.fromEntries(request.headers.entries()),
      integrity: request.integrity,
      keepalive: request.keepalive,
      method: request.method,
      mode: request.mode,
      redirect: request.redirect,
      referrer: request.referrer,
      referrerPolicy: request.referrerPolicy,
      signal: request.signal,
    });
    if (!response.headers) {
      Object.assign(response, { headers: new Headers() });
    }
    if (!response.text && typeof response.json === 'function') {
      Object.assign(response, { text: async () => JSON.stringify(await response.json()) });
    }
    return response;
  };
}

export function xsrfHeader(): HeadersInit {
  if (typeof document === 'undefined') return {};
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
}

function e2eServerResponse(request: Request): Response {
  const requestUrl = new URL(request.url);
  const pathname = requestUrl.pathname;

  if (pathname === '/api/me') {
    return jsonResponse({
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
    });
  }

  if (pathname === '/api/billing/balance') {
    return jsonResponse({
      availableCredits: 12,
      heldCredits: 0,
      currency: 'credits',
      betaCredits: 12,
      paidCredits: 0,
      monthlyGrantCredits: 300,
      resetsAt: '2026-06-01T00:00:00.000Z',
      freeDuringBeta: true,
    });
  }

  if (pathname === '/api/gmail/connection/status') {
    return jsonResponse({ connectionStatus: 'CONNECTED' });
  }

  if (pathname === '/api/analytics/summary') {
    return jsonResponse(e2eAnalyticsSummary(requestUrl.searchParams.get('window') ?? '7d'));
  }

  if (pathname === '/api/llm/byok') {
    return jsonResponse(null, 204);
  }

  return jsonResponse({});
}

function e2eAnalyticsSummary(window: string) {
  return {
    window,
    volumeObserved: 0,
    volumeApplied: 0,
    timeSavedSeconds: 0,
    topSenders: [],
    ruleHits: [],
    dailyLoad: [],
    actionMix: [],
    domainLoad: [],
    categoryLoad: [],
    replyBuckets: [],
    automationOpportunities: {
      noRuleMatched: 0,
      failedActions: 0,
      pendingActions: 0,
    },
  };
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: status === 204 ? undefined : { 'content-type': 'application/json' },
  });
}

// IMPORTANT: do NOT re-export from ./errors here. errors.ts is "use client"
// + uses next-intl hooks. RSC and proxy.ts code paths import only the
// server-safe symbols above. Client-only callers import directly from
// "@/lib/api/errors" (REVIEWS Revision 1, Codex HIGH #2 — Plan 04 Task 0).
