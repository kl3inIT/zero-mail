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
 */
export default defineConfig({
  testDir: './e2e',
  testMatch: ['**/*.spec.ts'],
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? 'github' : 'list',
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
    command: 'pnpm dev',
    url: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
