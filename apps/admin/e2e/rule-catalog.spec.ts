import { expect, test } from '@playwright/test';

const founderPersonaId = '10000000-0000-4000-8000-000000000001';
const studentPersonaId = '10000000-0000-4000-8000-000000000002';
const founderExampleId = '20000000-0000-4000-8000-000000000001';
const studentExampleId = '20000000-0000-4000-8000-000000000002';

test.beforeEach(async ({ page }) => {
  const personas = [
    {
      personaId: founderPersonaId,
      personaKey: 'founder',
      displayNameEn: 'Founder',
      displayNameVi: 'Nhà sáng lập',
      icon: 'rocket',
      displayOrder: 10,
      enabled: true,
      examples: [
        {
          exampleId: founderExampleId,
          exampleTextEn: 'Label investor emails as @[Investor]',
          exampleTextVi: 'Gắn nhãn email nhà đầu tư là @[Investor]',
          displayOrder: 10,
          enabled: true,
          sourceRef: 'inbox-zero:founder:001',
        },
      ],
    },
    {
      personaId: studentPersonaId,
      personaKey: 'student',
      displayNameEn: 'Student',
      displayNameVi: 'Sinh viên',
      icon: 'book',
      displayOrder: 20,
      enabled: true,
      examples: [
        {
          exampleId: studentExampleId,
          exampleTextEn: 'Label scholarship updates as School',
          exampleTextVi: 'Gắn nhãn học bổng là Trường học',
          displayOrder: 10,
          enabled: true,
          sourceRef: 'inbox-zero:student:001',
        },
      ],
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

    if (method === 'PUT' && path === `/api/admin/rule-catalog/examples/${founderExampleId}`) {
      const body = request.postDataJSON();
      personas[0].examples[0] = { ...personas[0].examples[0], ...body };
      await route.fulfill({ status: 204 });
      return;
    }

    if (method === 'PATCH' && path === `/api/admin/rule-catalog/examples/${founderExampleId}/enabled`) {
      const body = request.postDataJSON();
      personas[0].examples[0].enabled = body.enabled;
      await route.fulfill({ status: 204 });
      return;
    }

    await route.fulfill({ status: 404 });
  });
});

test('rule catalog manages examples through selected personas only', async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });

  await page.goto('/rule-catalog');

  await expect(page.getByText('Zero Mail')).toBeVisible();
  await expect(page.getByText('admin', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Rule Catalog' })).toBeVisible();
  await expect(page.getByRole('link', { name: /Rule Catalog/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Actions' })).toHaveCount(0);

  await expect(page.getByText('Label investor emails as @[Investor]')).toBeVisible();
  await page.getByRole('button', { name: 'Select persona Student' }).click();
  await expect(page.getByText('Label scholarship updates as School')).toBeVisible();

  await page.getByRole('button', { name: 'Select persona Founder' }).click();
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
  await expect(page.getByText('Gắn nhãn thư nhà đầu tư là Investor')).toBeVisible();
  await expect(
    page.getByRole('switch', { name: 'Enable example inbox-zero:founder:001' }),
  ).toHaveAttribute('aria-checked', 'false');

  expect(consoleErrors).toEqual([]);
});
