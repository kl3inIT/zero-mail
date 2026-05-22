/**
 * Next.js instrumentation hook — runs once per Node worker when the server boots.
 *
 * Keep this hook intentionally empty. Playwright e2e uses browser-level route
 * mocks now; importing `msw/node` from instrumentation makes Turbopack analyze
 * MSW's Node interceptor graph during regular `pnpm dev` and can fail before
 * any environment guard runs.
 */
export async function register(): Promise<void> {
  return;
}
