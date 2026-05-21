/**
 * Next.js instrumentation hook — runs once per Node worker when the server boots.
 *
 * Activates MSW node interceptor for Playwright e2e ONLY when
 * `NEXT_PUBLIC_E2E_MSW=1`. In production and regular `pnpm dev` the MSW module
 * is never imported, so the bundle and runtime cost are zero.
 *
 * Edge runtime is excluded because `msw/node` depends on Node's `http` module.
 * App Router pages and proxy.ts run in the Node runtime by default in this app.
 */
export async function register(): Promise<void> {
  if (process.env.NEXT_PUBLIC_E2E_MSW !== '1') return;
  if (process.env.NEXT_RUNTIME !== 'nodejs') return;
  const { server } = await import('./__mocks__/server');
  server.listen({ onUnhandledRequest: 'bypass' });
}
