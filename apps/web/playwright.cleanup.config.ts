/**
 * Phase 8 Wave 8 — Playwright config for cleanup specs that runs against an
 * already-running dev server (PLAYWRIGHT_BASE_URL). Bypasses the auto-webServer
 * boot which currently SSR-errors on the (pre-existing) landing page issue
 * unrelated to Phase 8.
 *
 * Usage: ensure `pnpm dev` is running on localhost:3000, then
 *   pnpm exec playwright test --config=playwright.cleanup.config.ts
 */
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  testMatch: ['**/cleanup-*.spec.ts'],
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: 'line',
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
