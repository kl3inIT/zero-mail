import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright config for browser-level smoke and route tests.
 *
 * - testDir is the top-level e2e/ directory so Playwright specs are never
 *   confused with Vitest's __tests__ or feature-owned unit tests.
 * - retries=0 locally per RESEARCH.md "Validation Architecture" (failures should
 *   be deterministic; retries hide flakes). CI gets retries=2 as a small safety
 *   net for environment hiccups.
 * - webServer auto-starts `next dev` and reuses an existing server when running
 *   locally (so `pnpm dev` in another terminal short-circuits the boot).
 * - 320px mobile coverage is applied per spec with `page.setViewportSize(...)`
 *   instead of a global project so desktop-first flows are not rerun at 320px.
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: ['**/*.spec.ts'],
  // Launch golden path needs the Spring Boot e2e-stub backend from playwright.golden.config.ts.
  testIgnore: ['**/launch-golden-path.spec.ts'],
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? 'github' : 'list',
  timeout: 60_000,
  expect: {
    timeout: 15_000,
  },
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    // Force turbopack for the e2e dev server. `pnpm dev` is pinned to
    // `next dev --webpack` for the local DX flow, but webpack's cold-start on
    // CI runners blows past Playwright's webServer timeout (~2-3 min compile
    // before first byte). Turbopack is the Next 16 default, starts in seconds,
    // and MSW node interception still works because it hooks
    // `instrumentation.ts` (bundler-independent).
    command: 'pnpm dev:turbo',
    url: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
    // Activate the MSW node interceptor in instrumentation.ts so RSC and
    // proxy.ts server-side fetches (e.g. /api/me from layouts) are mocked
    // BEFORE Playwright's browser-side route handlers run. Without this, the
    // App Router server fetches the real backend (or fails when none exists),
    // which breaks RSC pages like /settings.
    env: {
      NEXT_PUBLIC_E2E_MSW: '1',
    },
  },
});
