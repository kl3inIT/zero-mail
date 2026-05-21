import { expect, test } from '@playwright/test';

const syncJobId = '00000000-0000-4000-8000-0000000000d1';

test.beforeEach(async ({ page }) => {
  let syncConfirmed = false;

  await page.route('**/api/admin/me', async (route) => {
    await route.fulfill({
      json: { adminUserId: 'admin-1', email: 'admin@example.com', env: 'dev' },
    });
  });

  await page.route('**/api/admin/catalog/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (method === 'GET' && path === '/api/admin/catalog/ANTHROPIC') {
      await route.fulfill({ json: catalogResponse('ANTHROPIC', anthropicModels()) });
      return;
    }

    if (method === 'GET' && path === '/api/admin/catalog/OPENAI') {
      await route.fulfill({
        json: catalogResponse('OPENAI', [
          openAiModel('openai/gpt-4o', 'GPT-4o', true),
          ...(syncConfirmed ? [openAiModel('openai/gpt-4.1-mini', 'GPT-4.1 mini', false)] : []),
        ]),
      });
      return;
    }

    if (method === 'POST' && path === '/api/admin/catalog/OPENAI/sync/fetch') {
      await route.fulfill({ json: { jobId: syncJobId, status: 'IN_PROGRESS' } });
      return;
    }

    if (method === 'GET' && path === `/api/admin/catalog/sync/${syncJobId}/diff`) {
      await route.fulfill({
        json: {
          jobId: syncJobId,
          status: 'AWAITING_CONFIRM',
          added: [openAiModel('openai/gpt-4.1-mini', 'GPT-4.1 mini', false)],
          removed: [],
          changed: [],
        },
      });
      return;
    }

    if (method === 'POST' && path === `/api/admin/catalog/sync/${syncJobId}/confirm`) {
      syncConfirmed = true;
      await route.fulfill({ status: 204 });
      return;
    }

    await route.fulfill({ status: 404 });
  });
});

test('catalog sync wizard reviews diff before confirm and refreshes the table', async ({
  page,
}) => {
  await page.goto('/catalog');

  await expect(page.getByRole('heading', { name: 'Catalog' })).toBeVisible();
  await expect(page.getByText('Claude 4.7 Opus')).toBeVisible();
  await page.getByRole('button', { name: 'Sync from /models' }).hover({ force: true });
  await expect(page.getByText('Anthropic has no public /models endpoint')).toBeVisible();

  await page.getByRole('tab', { name: 'OpenAI' }).click();
  await expect(page.getByText('GPT-4o').first()).toBeVisible();
  await page.getByRole('button', { name: 'Sync from /models' }).click();

  await expect(page).toHaveURL(/\/catalog-sync\/00000000-0000-4000-8000-0000000000d1/);
  await expect(page.getByRole('heading', { name: 'OpenAI sync' })).toBeVisible();
  await expect(page.getByText('GPT-4.1 mini').first()).toBeVisible();
  await page.getByRole('button', { name: 'Confirm sync' }).click();

  await expect(page).toHaveURL(/\/catalog/);
  await expect(page.getByText('Sync OK')).toBeVisible();
  await expect(page.getByText('GPT-4.1 mini').first()).toBeVisible();
});

function catalogResponse(provider: string, models: Array<ReturnType<typeof openAiModel>>) {
  return {
    provider,
    features: {
      CHAT: { feature: 'CHAT', defaultModelId: models[0]?.modelId ?? null, models },
      TRIAGE: { feature: 'TRIAGE', defaultModelId: models[0]?.modelId ?? null, models },
      DRAFT: { feature: 'DRAFT', defaultModelId: models[0]?.modelId ?? null, models },
    },
  };
}

function openAiModel(modelId: string, displayName: string, defaultModel: boolean) {
  return {
    provider: 'OPENAI',
    modelId,
    displayName,
    defaultModel,
    recommended: !defaultModel,
    costPer1kInput: 0.0025,
    costPer1kOutput: 0.01,
    pinnedTenantCount: 0,
  };
}

function anthropicModels() {
  return [
    {
      provider: 'ANTHROPIC',
      modelId: 'anthropic/claude-4.7-opus',
      displayName: 'Claude 4.7 Opus',
      defaultModel: true,
      recommended: true,
      costPer1kInput: 0.015,
      costPer1kOutput: 0.075,
      pinnedTenantCount: 2,
    },
    {
      provider: 'ANTHROPIC',
      modelId: 'anthropic/claude-4.6-sonnet',
      displayName: 'Claude 4.6 Sonnet',
      defaultModel: false,
      recommended: true,
      costPer1kInput: 0.003,
      costPer1kOutput: 0.015,
      pinnedTenantCount: 0,
    },
  ];
}
