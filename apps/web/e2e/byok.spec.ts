import { expect, test, type Page } from '@playwright/test';

import {
  API_ROUTE_PATTERN,
  expectAppShellChrome,
  expectNoClaySkinClasses,
  expectNoHorizontalOverflow,
} from './chrome-test-utils';

async function mockSettingsApis(page: Page) {
  await page.route(API_ROUTE_PATTERN, async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (url.pathname === '/me') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
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
        }),
      });
      return;
    }

    if (url.pathname === '/gmail/connection/status') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ connectionStatus: 'CONNECTED', googleEmail: 'founder@example.com' }),
      });
      return;
    }

    if (url.pathname === '/api/llm/byok/validate' && request.method() === 'POST') {
      const payload = request.postDataJSON();
      expect(payload).toMatchObject({
        preset: 'openrouter',
        model: expect.any(String),
        apiKey: 'sk-or-v1-test',
      });
      expect(payload).not.toHaveProperty('endpoint');
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ok: true, models: ['openai/gpt-4o-mini'] }),
      });
      return;
    }

    if (url.pathname === '/api/llm/byok' && request.method() === 'POST') {
      const payload = request.postDataJSON();
      expect(payload).toMatchObject({
        preset: 'openrouter',
        model: expect.any(String),
        apiKey: 'sk-or-v1-test',
      });
      expect(payload).not.toHaveProperty('endpoint');
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ok: true, savedAt: '2026-05-08T04:00:00Z' }),
      });
      return;
    }

    if (url.pathname === '/api/llm/byok' && request.method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: 'null',
      });
      return;
    }

    await route.fulfill({ status: 204, body: '' });
  });
}

async function openSettings(page: Page) {
  await page.context().addCookies([
    {
      name: 'ZEROMAIL_SESSION',
      value: 'playwright-session',
      domain: 'localhost',
      path: '/',
      httpOnly: true,
      sameSite: 'Lax',
      secure: false,
    },
    {
      name: 'NEXT_LOCALE',
      value: 'en',
      domain: 'localhost',
      path: '/',
      sameSite: 'Lax',
      secure: false,
    },
  ]);
  await mockSettingsApis(page);
  await page.goto('/settings', { waitUntil: 'domcontentloaded' });
}

test('byok settings flow validates then saves without exposing the key in the URL', async ({
  page,
}) => {
  await openSettings(page);

  await expectAppShellChrome(page, { sidebarVisible: true });
  await expectNoClaySkinClasses(page);
  const triageBox = await page.getByText('Automated triage', { exact: true }).boundingBox();
  const byokBox = await page.getByText('AI provider key', { exact: true }).boundingBox();
  const privacyBox = await page.getByText('Privacy and safety', { exact: true }).boundingBox();
  expect(triageBox?.y ?? 0).toBeLessThan(byokBox?.y ?? 0);
  expect(byokBox?.y ?? 0).toBeLessThan(privacyBox?.y ?? Number.MAX_SAFE_INTEGER);

  await page.getByLabel('Model').fill('anthropic/claude-3.5-sonnet');
  await page.getByLabel('API key').fill('sk-or-v1-test');
  await page.getByRole('button', { name: 'Validate API key' }).click();

  await expect(page.getByRole('status')).toContainText('API key and API configuration are valid');
  await expect(page.getByTestId('byok-validation-success-alert')).toHaveClass(/bg-green-soft/);
  await page.getByRole('button', { name: 'Save API key' }).click();
  await expect(page.getByRole('status')).toContainText('Encrypted BYOK key saved');
  await expect(page.getByLabel('API key')).toHaveValue('');
  expect(page.url()).not.toContain('sk-or-v1-test');
});

test('byok settings card remains in-shell and usable at 320px', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 740 });
  await openSettings(page);

  await expectAppShellChrome(page);
  await expectNoClaySkinClasses(page);
  await expect(page.getByText('AI provider key')).toBeVisible();
  await expect(page.getByRole('radio', { name: 'OpenRouter' })).toBeChecked();
  await expect(page.getByLabel('Model')).toBeVisible();
  await page.getByLabel('API key').fill('sk-or-v1-test');
  await expect(page.getByRole('button', { name: 'Validate API key' })).toBeEnabled();

  await expectNoHorizontalOverflow(page);
});
