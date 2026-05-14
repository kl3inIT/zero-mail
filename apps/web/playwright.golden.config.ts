import { defineConfig } from '@playwright/test';

import baseConfig from './playwright.config';

const gradleExecutable = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';

/**
 * Golden-path-only Playwright config. Adds a second webServer entry that starts the
 * Spring Boot backend under the e2e-stub profile from Plan 06-01.
 *
 * The default config stays unchanged so daily page-route E2E gates do not need
 * local Postgres, Redis, or Spring Boot.
 */
export default defineConfig({
  ...baseConfig,
  testMatch: ['**/launch-golden-path.spec.ts'],
  webServer: [
    {
      command: 'pnpm dev',
      url: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3000',
      name: 'Frontend',
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
    {
      command: `${gradleExecutable} :backend:api:bootRun --args='--spring.profiles.active=e2e-stub --zeromail.e2e-stub.enabled=true'`,
      url: 'http://localhost:8080/actuator/health/readiness',
      name: 'Backend',
      reuseExistingServer: !process.env.CI,
      timeout: 240_000,
      cwd: '../..',
    },
  ],
});
