import { defineConfig, devices } from '@playwright/test';

/**
 * Phase 1.1 Plan 07 — Playwright config (smoke-only).
 *
 * Per VALIDATION.md "Test Infrastructure" the Playwright surface is intentionally
 * minimal: language-switcher persistence is the ONE smoke we run.
 *
 * - testDir is a top-level path under apps/web so it's never confused with
 *   vitest's __tests__/components or __tests__/i18n trees.
 * - retries=0 locally per RESEARCH.md "Validation Architecture" (failures should
 *   be deterministic; retries hide flakes). CI gets retries=2 as a small safety
 *   net for environment hiccups.
 * - webServer auto-starts `next dev` and reuses an existing server when running
 *   locally (so `pnpm dev` in another terminal short-circuits the boot).
 */
export default defineConfig({
  testDir: '.',
  testMatch: ['__tests__/e2e/**/*.spec.ts', 'e2e/**/*.spec.ts'],
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
