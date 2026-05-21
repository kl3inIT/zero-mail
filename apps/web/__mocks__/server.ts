import { setupServer } from 'msw/node';

import { handlers } from './handlers';

/**
 * Node-side MSW server for Next.js e2e runs (App Router RSC + proxy.ts fetches).
 *
 * Boot is delegated to `instrumentation.ts`, gated on `NEXT_PUBLIC_E2E_MSW=1`.
 * `onUnhandledRequest: 'bypass'` lets unrelated traffic (Next telemetry, local
 * dev assets, the real backend when wired) pass through unmodified.
 */
export const server = setupServer(...handlers);
