import { expect, test } from '@playwright/test';

const providerRows = [
  providerRow('OPENAI', 'OpenAI', 'https://api.openai.com/v1'),
  providerRow('ANTHROPIC', 'Anthropic', 'https://api.anthropic.com/v1'),
  providerRow('GOOGLE', 'Google', 'https://generativelanguage.googleapis.com/v1beta'),
  providerRow('DEEPSEEK', 'DeepSeek', 'https://api.deepseek.com'),
  providerRow('OPENROUTER', 'OpenRouter', 'https://openrouter.ai/api/v1', 0),
  providerRow('ROUTER_9R', '9Router', null, 1),
];

const routingFeatures = [
  'CHAT',
  'DRAFT',
  'RULE_AUTHORING',
  'RULE_PREVIEW_SEMANTIC',
  'TRIAGE_SEMANTIC',
  'TRIAGE',
  'DRIFT_CHECK',
] as const;

test.beforeEach(async ({ page }) => {
  await page.route('**/api/admin/me', async (route) => {
    await route.fulfill({
      json: {
        id: '00000000-0000-0000-0000-000000000001',
        email: 'admin@example.com',
        status: 'ACTIVE',
        role: 'ADMIN',
      },
    });
  });
  await page.route('**/api/admin/master-keys/', async (route) => {
    await route.fulfill({ json: { rows: providerRows } });
  });
  await page.route('**/api/admin/catalog/feature-defaults', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        json: {
          bindings: routingFeatures.map((feature) => ({
            feature,
            tier: 'PRIMARY',
            provider: 'OPENROUTER',
            modelIds: ['openai/gpt-5.4-nano'],
          })),
        },
      });
      return;
    }
    await route.fulfill({ status: 204 });
  });
  await page.route('**/api/admin/catalog/OPENROUTER', async (route) => {
    await route.fulfill({
      json: {
        provider: 'OPENROUTER',
        features: Object.fromEntries(
          routingFeatures.map((feature) => [
            feature,
            {
              feature,
              defaultModelId: 'openai/gpt-5.4-nano',
              models: [
                {
                  provider: 'OPENROUTER',
                  modelId: 'openai/gpt-5.4-nano',
                  displayName: 'GPT-5.4 Nano',
                  defaultModel: true,
                  recommended: true,
                  verificationStatus: 'VERIFIED',
                  costPer1kInput: 0.001,
                  costPer1kOutput: 0.002,
                  deprecatedAt: null,
                  pinnedTenantCount: 0,
                },
              ],
            },
          ]),
        ),
      },
    });
  });
});

test('operator sees business-facing AI routing tasks and can open a task picker', async ({
  page,
}) => {
  await page.goto('/master-keys');

  await expect(page.getByRole('heading', { name: 'Quản lý LLM' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Model cho tác vụ AI' })).toBeVisible();

  await expect(page.getByText('Tạo quy tắc').first()).toBeVisible();
  await expect(page.getByText('Test quy tắc').first()).toBeVisible();
  await expect(page.getByText('Chạy quy tắc').first()).toBeVisible();
  await expect(page.getByText('Soạn nội dung').first()).toBeVisible();
  await expect(page.getByText('Kiểm tra chất lượng').first()).toBeVisible();
  await expect(page.getByText(/RULE_|SEMANTIC|DRIFT_CHECK/)).toHaveCount(0);

  await page
    .locator('div')
    .filter({ hasText: /^Tạo quy tắc/ })
    .getByRole('button', { name: /OPENROUTER/ })
    .first()
    .click();

  await expect(page.getByRole('dialog', { name: 'Tạo quy tắc' })).toBeVisible();
  await expect(page.getByText('Chính. Model phía trên được thử trước.')).toBeVisible();
  await expect(page.getByText(/RULE_|SEMANTIC|DRIFT_CHECK/)).toHaveCount(0);
});

test('operator deletes a provider key through an in-app confirmation dialog', async ({
  page,
}) => {
  const keyId = '331ac9b1-eaa4-4d41-8845-76d5914a0187';
  let deleted = false;

  await page.route('**/api/admin/master-keys/ROUTER_9R', async (route) => {
    await route.fulfill({ json: providerRow('ROUTER_9R', '9Router', null, deleted ? 0 : 1) });
  });
  await page.route('**/api/admin/master-keys/ROUTER_9R/keys', async (route) => {
    await route.fulfill({
      json: {
        provider: 'ROUTER_9R',
        keys: deleted
          ? []
          : [
              {
                provider: 'ROUTER_9R',
                keyId,
                priority: 1,
                status: 'ACTIVE',
                label: 'Test',
                keyFormat: 'OPENAI_FORMAT',
                maskedKey: 'sk-...0187',
                baseUrl: null,
                providerSecretVersion: 1,
                createdAt: '2026-05-24T00:00:00Z',
                lastRotatedAt: '2026-05-24T00:00:00Z',
              },
            ],
      },
    });
  });
  await page.route(`**/api/admin/master-keys/ROUTER_9R/keys/${keyId}`, async (route) => {
    deleted = true;
    await route.fulfill({ status: 204 });
  });
  await page.route('**/api/admin/catalog/ROUTER_9R', async (route) => {
    await route.fulfill({
      json: {
        provider: 'ROUTER_9R',
        features: Object.fromEntries(
          routingFeatures.map((feature) => [feature, { feature, models: [] }]),
        ),
      },
    });
  });

  page.on('dialog', (dialog) => {
    throw new Error(`Unexpected native dialog: ${dialog.message()}`);
  });

  await page.goto('/master-keys/ROUTER_9R');
  await page.getByRole('button', { name: 'Xoá key' }).click();

  await expect(page.getByText('Xoá key?')).toBeVisible();
  await page.getByRole('button', { name: 'Xoá' }).click();

  await expect.poll(() => deleted).toBe(true);
  await expect(page.getByText('Chưa có key')).toBeVisible();
});

test('operator must test a new compatible provider before saving it', async ({ page }) => {
  const rows = [...providerRows];
  const providerId = 'GATEWAY_TEST';
  let connectionTestCount = 0;
  let providerCreated = false;

  await page.route('**/api/admin/master-keys/', async (route) => {
    await route.fulfill({ json: { rows } });
  });
  await page.route(`**/api/admin/master-keys/${providerId}/edit-session`, async (route) => {
    await route.fulfill({
      json: {
        token: 'edit-token-for-gateway-test',
        expiresAt: '2026-05-24T01:00:00Z',
      },
    });
  });
  await page.route(`**/api/admin/master-keys/${providerId}/test-connection`, async (route) => {
    const body = await route.request().postDataJSON();
    expect(body).toMatchObject({
      keyFormat: 'OPENAI_FORMAT',
      baseUrl: 'https://gateway.example.com/v1',
      editSessionToken: 'edit-token-for-gateway-test',
    });
    connectionTestCount += 1;
    await route.fulfill({ json: { result: 'OK' } });
  });
  await page.route('**/api/admin/master-keys/providers', async (route) => {
    const body = await route.request().postDataJSON();
    expect(connectionTestCount).toBe(2);
    expect(body).toMatchObject({
      providerId,
      displayName: 'Gateway Test',
      compatibleType: 'OPENAI_FORMAT',
      defaultBaseUrl: 'https://gateway.example.com/v1',
      editSessionToken: 'edit-token-for-gateway-test',
    });
    providerCreated = true;
    rows.push(providerRow(providerId, 'Gateway Test', 'https://gateway.example.com/v1', 1));
    await route.fulfill({
      status: 201,
      json: {
        provider: providerId,
        keyId: '11111111-1111-4111-8111-111111111111',
        priority: 1,
        testResult: 'OK',
      },
    });
  });

  await page.goto('/master-keys');
  await page.getByRole('button', { name: 'Thêm provider' }).click();

  const dialog = page.getByRole('dialog', { name: 'Thêm provider' });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel('Provider ID').fill(providerId.toLowerCase());
  await dialog.getByLabel('Tên hiển thị').fill('Gateway Test');
  await dialog.getByLabel('Base URL').fill('https://gateway.example.com/v1');
  await dialog.getByLabel('API key').fill('sk-test-provider-key');
  await dialog.getByLabel('Nhãn key').fill('primary');

  await expect(dialog.getByRole('button', { name: 'Lưu' })).toBeDisabled();
  await dialog.getByRole('button', { name: 'Test kết nối' }).click();
  await expect(dialog.getByText('Kết quả test: OK')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Lưu' })).toBeEnabled();
  await dialog.getByLabel('Base URL').fill('https://gateway.example.com/v2');
  await expect(dialog.getByText('Kết quả test: OK')).toHaveCount(0);
  await expect(dialog.getByRole('button', { name: 'Lưu' })).toBeDisabled();
  await dialog.getByLabel('Base URL').fill('https://gateway.example.com/v1');
  await dialog.getByRole('button', { name: 'Test kết nối' }).click();
  await expect(dialog.getByText('Kết quả test: OK')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Lưu' })).toBeEnabled();
  await dialog.getByRole('button', { name: 'Lưu' }).click();

  await expect.poll(() => providerCreated).toBe(true);
  await expect(page.getByText('Gateway Test')).toBeVisible();
});

function providerRow(
  provider: string,
  displayName: string,
  baseUrl: string | null,
  activeKeyCount = 0,
) {
  return {
    provider,
    displayName,
    providerKind: ['OPENAI', 'ANTHROPIC', 'GOOGLE', 'DEEPSEEK'].includes(provider)
      ? 'SPRING_AI_BUILT_IN'
      : 'COMPATIBLE_GATEWAY',
    compatibleType: provider === 'ANTHROPIC' ? 'ANTHROPIC_FORMAT' : 'OPENAI_FORMAT',
    defaultBaseUrl: baseUrl,
    maskedKey: null,
    keyFormat: null,
    lastRotatedAt: null,
    dependentsCount: 0,
    activeKeyCount,
    rotationRecommended: false,
    baseUrl,
    featureDefaultProviderChat: provider === 'OPENROUTER',
    featureDefaultProviderTriage: provider === 'OPENROUTER',
    featureDefaultProviderDraft: provider === 'OPENROUTER',
  };
}
