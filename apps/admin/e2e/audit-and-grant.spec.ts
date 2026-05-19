import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.route('**/api/admin/me', async (route) => {
    await route.fulfill({ json: { adminUserId: 'admin-1', email: 'admin@example.com', env: 'dev' } });
  });
  await page.route('**/api/admin/audit/events**', async (route) => {
    await route.fulfill({
      json: {
        rows: [
          {
            auditId: 'audit-1',
            chainIndex: 1,
            actorEmail: 'admin@example.com',
            action: 'ADMIN_GRANTED',
            targetKind: 'admin_user',
            targetId: '00000000-0000-4000-8000-000000000001',
            createdAt: '2026-05-20T00:00:00Z',
          },
        ],
        hasNextPage: false,
        totalEstimate: 1,
      },
    });
  });
  await page.route('**/api/admin/admins', async (route) => {
    await route.fulfill({
      json: [
        {
          adminUserId: '00000000-0000-4000-8000-000000000001',
          email: 'admin@example.com',
          status: 'ACTIVE',
          lastUsedAt: '2026-05-20T00:00:00Z',
          hasCredential: true,
        },
      ],
    });
  });
  await page.route('**/api/admin/grant-admin', async (route) => {
    await route.fulfill({
      json: {
        adminUserId: '00000000-0000-4000-8000-000000000002',
        enrollmentUrl:
          'https://admin.zeromail.com/enroll?token=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
        expiresAt: '2026-05-20T00:10:00Z',
      },
    });
  });
  await page.route('**/api/admin/admins/*/revoke', async (route) => {
    await route.fulfill({ status: 204 });
  });
});

test('audit page renders rows', async ({ page }) => {
  await page.goto('/audit');
  await expect(page.getByRole('heading', { name: 'Audit log' })).toBeVisible();
  await expect(page.getByText('ADMIN_GRANTED')).toBeVisible();
});

test('grant and revoke admin flows expose copy URL and confirm token', async ({ page }) => {
  await page.goto('/role-grants');
  await page.getByRole('button', { name: 'Grant admin' }).click();
  await page.getByLabel('Admin email').fill('second@example.com');
  await page.getByRole('button', { name: 'Grant admin' }).last().click();

  await expect(page.getByText('One-time enrollment URL')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Copy URL' })).toBeVisible();
  await page.getByRole('button', { name: 'Close' }).click();

  await page.getByRole('button', { name: 'Revoke admin' }).click();
  await page.getByLabel('Reason (recorded in audit log)').fill('decommissioning test admin');
  await page.getByRole('button', { name: 'Continue' }).click();

  await expect(page.getByLabel('Type "admin@example.com" to confirm')).toBeVisible();
});
