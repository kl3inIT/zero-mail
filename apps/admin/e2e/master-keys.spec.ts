import { expect, test } from '@playwright/test';

const rows = [
  {
    provider: 'OPENAI',
    displayName: 'OpenAI',
    maskedKey: null,
    keyFormat: null,
    lastRotatedAt: null,
    dependentsCount: 0,
    rotationRecommended: false,
    baseUrl: 'https://api.openai.com/v1',
    featureDefaultProviderChat: false,
    featureDefaultProviderTriage: false,
    featureDefaultProviderDraft: false,
  },
  {
    provider: 'ANTHROPIC',
    displayName: 'Anthropic',
    maskedKey: null,
    keyFormat: null,
    lastRotatedAt: null,
    dependentsCount: 0,
    rotationRecommended: false,
    baseUrl: 'https://api.anthropic.com/v1',
    featureDefaultProviderChat: false,
    featureDefaultProviderTriage: false,
    featureDefaultProviderDraft: false,
  },
  {
    provider: 'GOOGLE',
    displayName: 'Google',
    maskedKey: null,
    keyFormat: null,
    lastRotatedAt: null,
    dependentsCount: 0,
    rotationRecommended: false,
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta',
    featureDefaultProviderChat: false,
    featureDefaultProviderTriage: false,
    featureDefaultProviderDraft: false,
  },
  {
    provider: 'DEEPSEEK',
    displayName: 'DeepSeek',
    maskedKey: null,
    keyFormat: null,
    lastRotatedAt: null,
    dependentsCount: 0,
    rotationRecommended: false,
    baseUrl: 'https://api.deepseek.com',
    featureDefaultProviderChat: false,
    featureDefaultProviderTriage: false,
    featureDefaultProviderDraft: false,
  },
  {
    provider: 'OPENROUTER',
    displayName: 'OpenRouter',
    maskedKey: null,
    keyFormat: null,
    lastRotatedAt: null,
    dependentsCount: 3,
    rotationRecommended: false,
    baseUrl: 'https://openrouter.ai/api/v1',
    featureDefaultProviderChat: true,
    featureDefaultProviderTriage: true,
    featureDefaultProviderDraft: true,
  },
  {
    provider: 'ROUTER_9R',
    displayName: '9Router',
    maskedKey: null,
    keyFormat: null,
    lastRotatedAt: null,
    dependentsCount: 0,
    rotationRecommended: false,
    baseUrl: null,
    featureDefaultProviderChat: false,
    featureDefaultProviderTriage: false,
    featureDefaultProviderDraft: false,
  },
];

test.beforeEach(async ({ page }) => {
  await page.route('**/api/admin/me', async (route) => {
    await route.fulfill({ json: { adminUserId: 'admin-1', email: 'admin@example.com', env: 'dev' } });
  });
  await page.route('**/api/admin/master-keys/', async (route) => {
    await route.fulfill({ json: { rows } });
  });
  await page.route('**/api/admin/master-keys/OPENAI', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: rows[0] });
      return;
    }
    await route.fulfill({ status: 204 });
  });
  await page.route('**/api/admin/master-keys/OPENAI/edit-session', async (route) => {
    await route.fulfill({ json: { token: 'edit-token', expiresAt: '2026-05-20T00:05:00Z' } });
  });
  await page.route('**/api/admin/master-keys/OPENAI/test-connection', async (route) => {
    await route.fulfill({ json: { result: 'OK' } });
  });
});

test('operator can test and save an OpenAI master key with masked-only refresh', async ({ page }) => {
  await page.goto('/master-keys');

  await expect(page.getByRole('heading', { name: 'Master keys' })).toBeVisible();
  await expect(page.getByRole('row')).toHaveCount(7);
  await page.getByRole('row', { name: /OpenAI/ }).click();

  await expect(page.getByRole('heading', { name: 'OpenAI master key' })).toBeVisible();
  await page.getByRole('button', { name: 'Edit key' }).click();
  await page.getByLabel('Plaintext key').fill('sk-proj-test-1234');
  await page.getByRole('button', { name: 'Test connection' }).click();
  await expect(page.getByText(/Tested OK/)).toBeVisible();
  await page.getByLabel('Reason').fill('rotating platform key for test coverage');
  await page.getByRole('button', { name: 'Save key' }).click();

  await expect(page.getByText('OpenAI key saved')).toBeVisible();
});
