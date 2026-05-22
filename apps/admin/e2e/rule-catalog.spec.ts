import { expect, test } from '@playwright/test';

const personaId = '10000000-0000-4000-8000-000000000001';
const exampleId = '20000000-0000-4000-8000-000000000001';

test.beforeEach(async ({ page }) => {
  const personas = [
    {
      personaId,
      personaKey: 'founder',
      displayNameEn: 'Founder',
      displayNameVi: 'Nhà sáng lập',
      icon: 'rocket',
      displayOrder: 10,
      enabled: true,
      examples: [
        {
          exampleId,
          exampleTextEn: 'Label investor emails as @[Investor]',
          exampleTextVi: 'Gắn nhãn email nhà đầu tư là @[Investor]',
          displayOrder: 10,
          enabled: true,
          sourceRef: 'inbox-zero:founder:001',
        },
      ],
    },
  ];
  const actions = [
    {
      actionKey: 'archive',
      labelEn: 'Archive',
      labelVi: 'Lưu trữ',
      descriptionEn: 'Remove the message from inbox.',
      descriptionVi: 'Bỏ thư khỏi inbox.',
      riskLevel: 'LOW',
      availabilityStatus: 'AVAILABLE',
      displayOrder: 10,
      enabled: true,
    },
    {
      actionKey: 'send_reply',
      labelEn: 'Send reply',
      labelVi: 'Gửi trả lời',
      descriptionEn: 'Automatically send a reply.',
      descriptionVi: 'Tự động gửi trả lời.',
      riskLevel: 'HIGH',
      availabilityStatus: 'AVAILABLE',
      displayOrder: 20,
      enabled: true,
    },
  ];

  await page.route('**/api/admin/me', async (route) => {
    await route.fulfill({
      json: { adminUserId: 'admin-1', email: 'admin@example.com', env: 'dev' },
    });
  });

  await page.route('**/api/admin/rule-catalog/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();

    if (method === 'GET' && path === '/api/admin/rule-catalog/personas') {
      await route.fulfill({ json: { personas } });
      return;
    }

    if (method === 'GET' && path === '/api/admin/rule-catalog/actions') {
      await route.fulfill({ json: { actions } });
      return;
    }

    if (method === 'PUT' && path === `/api/admin/rule-catalog/examples/${exampleId}`) {
      const body = request.postDataJSON();
      personas[0].examples[0] = { ...personas[0].examples[0], ...body };
      await route.fulfill({ status: 204 });
      return;
    }

    if (method === 'PATCH' && path === `/api/admin/rule-catalog/examples/${exampleId}/enabled`) {
      const body = request.postDataJSON();
      personas[0].examples[0].enabled = body.enabled;
      await route.fulfill({ status: 204 });
      return;
    }

    if (method === 'PUT' && path === '/api/admin/rule-catalog/actions/send_reply') {
      const body = request.postDataJSON();
      const actionIndex = actions.findIndex((entry) => entry.actionKey === 'send_reply');
      actions[actionIndex] = { ...actions[actionIndex], ...body };
      await route.fulfill({ status: 204 });
      return;
    }

    if (method === 'PUT' && path === '/api/admin/rule-catalog/actions/reorder') {
      const body = request.postDataJSON();
      for (const orderEntry of body.items) {
        const action = actions.find((entry) => entry.actionKey === orderEntry.actionKey);
        if (action) action.displayOrder = orderEntry.displayOrder;
      }
      actions.sort((left, right) => left.displayOrder - right.displayOrder);
      await route.fulfill({ status: 204 });
      return;
    }

    await route.fulfill({ status: 404 });
  });
});

test('rule catalog edits bilingual examples, disables rows, and updates action descriptors', async ({
  page,
}) => {
  const consoleErrors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });

  await page.goto('/rule-catalog');

  await expect(page.getByText('Zero Mail')).toBeVisible();
  await expect(page.getByText('admin', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Rule Catalog' })).toBeVisible();
  await expect(page.getByRole('link', { name: /Rule Catalog/ })).toBeVisible();

  await page.getByRole('tab', { name: 'Examples' }).click();
  await expect(page.getByText('Label investor emails as @[Investor]')).toBeVisible();

  await page
    .getByRole('button', { name: 'Edit example inbox-zero:founder:001' })
    .click();
  await page.getByLabel('Prompt VI').fill('Gắn nhãn thư nhà đầu tư là Investor');
  await page.getByRole('button', { name: 'Lưu' }).click();
  await expect(page.getByText('Gắn nhãn thư nhà đầu tư là Investor')).toBeVisible();

  const exampleSwitch = page.getByRole('switch', {
    name: 'Enable example inbox-zero:founder:001',
  });
  await exampleSwitch.click();
  await expect(exampleSwitch).toHaveAttribute('aria-checked', 'false');

  await page.reload();
  await page.getByRole('tab', { name: 'Examples' }).click();
  await expect(page.getByText('Gắn nhãn thư nhà đầu tư là Investor')).toBeVisible();
  await expect(
    page.getByRole('switch', { name: 'Enable example inbox-zero:founder:001' }),
  ).toHaveAttribute('aria-checked', 'false');

  await page.getByRole('tab', { name: 'Actions' }).click();
  const sendReplyRow = page.getByRole('row').filter({ hasText: 'send_reply' });
  await sendReplyRow.getByRole('button', { name: 'Move up' }).click();
  await expect(sendReplyRow).toContainText('10');

  await sendReplyRow.getByRole('button', { name: 'Edit action send_reply' }).click();
  await page.getByLabel('Label VI').fill('Gửi phản hồi');
  await page.getByRole('button', { name: 'Lưu' }).click();
  await expect(page.getByText('Gửi phản hồi')).toBeVisible();

  expect(consoleErrors).toEqual([]);
});
