import { expect, test, type Page } from '@playwright/test';

async function mockSettingsApis(page: Page) {
  await page.route('http://localhost:8080/**', async (route) => {
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
          onboardingStep: 'complete',
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
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ok: true, models: ['openai/gpt-4o-mini'] }),
      });
      return;
    }

    if (url.pathname === '/api/llm/byok' && request.method() === 'POST') {
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

  const triageBox = await page.getByText('Automated triage', { exact: true }).boundingBox();
  const byokBox = await page.getByText('AI provider key', { exact: true }).boundingBox();
  const privacyBox = await page.getByText('Privacy and safety', { exact: true }).boundingBox();
  expect(triageBox?.y ?? 0).toBeLessThan(byokBox?.y ?? 0);
  expect(byokBox?.y ?? 0).toBeLessThan(privacyBox?.y ?? Number.MAX_SAFE_INTEGER);

  await page.getByLabel('OpenAI Compatible endpoint').fill('https://openrouter.ai/api/v1');
  await page.getByLabel('API key').fill('sk-or-v1-test');
  await page.getByRole('button', { name: 'Validate API key' }).click();

  await expect(page.getByRole('status')).toContainText('Key validated');
  await page.getByRole('button', { name: 'Save API key' }).click();
  await expect(page.getByRole('status')).toContainText('Encrypted BYOK key saved');
  await expect(page.getByLabel('API key')).toHaveValue('');
  expect(page.url()).not.toContain('sk-or-v1-test');
});

test('byok settings card remains usable at 375x812', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await openSettings(page);

  await expect(page.getByText('AI provider key')).toBeVisible();
  await page.getByLabel('OpenAI Compatible endpoint').fill('https://openrouter.ai/api/v1');
  await page.getByLabel('API key').fill('sk-or-v1-test');
  await expect(page.getByRole('button', { name: 'Validate API key' })).toBeEnabled();

  const horizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth,
  );
  expect(horizontalOverflow).toBe(false);
});
