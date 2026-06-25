import { expect, test } from '@playwright/test';

let billingPackageRequests: string[];
let forbiddenApiCalls: string[];
let permissionPatchRequests: Array<{ url: string; body: unknown }>;
let creditCostPatchRequests: Array<{ url: string; body: unknown }>;
let releasePendingPermissionPatch: (() => void) | undefined;
let billingPackageFixtureState: ReturnType<typeof billingPackageFixture>;

test.beforeEach(async ({ page }) => {
  billingPackageRequests = [];
  forbiddenApiCalls = [];
  permissionPatchRequests = [];
  creditCostPatchRequests = [];
  releasePendingPermissionPatch = undefined;
  billingPackageFixtureState = billingPackageFixture();

  await page.route('**/api/admin/me', async (route) => {
    await route.fulfill({
      json: {
        id: '00000000-0000-4000-8000-00000000ad01',
        email: 'admin@example.com',
        displayName: 'Admin',
        status: 'ACTIVE',
        role: 'ADMIN',
      },
    });
  });

  for (const path of [
    '**/api/plan-upgrades/plans**',
    '**/api/admin/tenants**',
    '**/api/admin/spend/dashboard**',
  ]) {
    await page.route(path, async (route) => {
      forbiddenApiCalls.push(route.request().url());
      await route.fulfill({ status: 500, json: { message: 'unexpected billing package API call' } });
    });
  }

  await page.route('**/api/admin/billing-packages**', async (route) => {
    const request = route.request();
    billingPackageRequests.push(request.url());
    if (request.method() === 'GET') {
      await route.fulfill({ json: billingPackageFixtureState });
      return;
    }
    if (request.method() === 'PATCH') {
      const body = request.postDataJSON();
      if (request.url().includes('/credit-cost')) {
        creditCostPatchRequests.push({ url: request.url(), body });
        applyCreditCostPatchToFixture(request.url(), body);
        await route.fulfill({ status: 204 });
        return;
      }
      permissionPatchRequests.push({ url: request.url(), body });
      applyPermissionPatchToFixture(request.url(), body);
      if (request.url().includes('/features/SEND_EMAIL/plans/FREE/enabled')) {
        await new Promise<void>((resolve) => {
          releasePendingPermissionPatch = resolve;
        });
      }
      await route.fulfill({ status: 204 });
      return;
    }
    await route.fulfill({ status: 405 });
  });
});

test('admin manages billing packages with simple fixed feature credit costs', async ({ page }) => {
  await page.goto('/billing-packages');

  await expect(page.getByRole('heading', { name: 'Quản lý gói dịch vụ' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Thêm gói mới' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Danh sách gói dịch vụ' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Free' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Plus' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Pro' })).toBeVisible();
  await expect(page.getByText('199,000 đ')).toBeVisible();
  await expect(page.getByText('5,000 credit / tháng', { exact: true })).toBeVisible();

  await page.getByRole('tab', { name: 'Quyền hạn & Credit' }).click();
  await expect(page.getByRole('heading', { name: 'Quyền hạn & Credit' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Giá credit cố định' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'Đơn vị tính' })).toHaveCount(0);
  await expect(page.getByRole('spinbutton', { name: 'Email quan sát credit cost' })).toHaveValue('1');
  await expect(page.getByRole('spinbutton', { name: 'Gửi email credit cost' })).toHaveValue('5');
  await expect(page.getByRole('row', { name: /Email quan sát/ })).toContainText('credit/lần gọi');
  await expect(page.getByRole('row', { name: /Gửi email/ })).toContainText('credit/lần gọi');
  await expect(page.getByRole('row', { name: /Gửi email/ })).toContainText('Tắt');
  await page.getByRole('spinbutton', { name: 'Email quan sát credit cost' }).fill('4');
  await page.getByRole('button', { name: 'Lưu giá Email quan sát' }).click();
  await expect.poll(() => creditCostPatchRequests.length).toBe(1);
  expect(creditCostPatchRequests[0]).toMatchObject({
    url: expect.stringContaining('/api/admin/billing-packages/features/OBSERVE_EMAIL/credit-cost'),
    body: { fixedCreditCost: 4 },
  });
  await expect(page.getByRole('spinbutton', { name: 'Email quan sát credit cost' })).toHaveValue('4');

  const inboxTriageFreeSwitch = page.getByRole('switch', { name: 'Email quan sát Free' });
  const sendEmailFreeSwitch = page.getByRole('switch', { name: 'Gửi email Free' });
  await expect(sendEmailFreeSwitch).not.toBeChecked();
  await sendEmailFreeSwitch.click();
  await expect.poll(() => permissionPatchRequests.length).toBe(1);
  await expect(inboxTriageFreeSwitch).toBeEnabled();
  expect(permissionPatchRequests[0]).toMatchObject({
    url: expect.stringContaining('/api/admin/billing-packages/features/SEND_EMAIL/plans/FREE/enabled'),
    body: { enabled: true },
  });
  releasePendingPermissionPatch?.();
  await expect(sendEmailFreeSwitch).toBeChecked();

  await page.getByRole('tab', { name: 'Lịch sử thanh toán' }).click();
  await expect(page.getByRole('heading', { name: 'Lịch sử thanh toán' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Acme Company' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'SEPAY QR' })).toBeVisible();

  expect(billingPackageRequests.length).toBeGreaterThan(0);
  expect(forbiddenApiCalls).toEqual([]);
});

function billingPackageFixture() {
  return {
    plans: [
      plan('00000000-0000-4000-8000-000000000101', 'FREE', 'Free', 0, 'NONE', 0, 500, true, 0),
      plan('00000000-0000-4000-8000-000000000102', 'PLUS', 'Plus', 1, 'MONTH', 199000, 5000, true, 10),
      plan('00000000-0000-4000-8000-000000000103', 'PRO', 'Pro', 2, 'MONTH', 499000, 15000, true, 20),
    ],
    featurePermissions: [
      featurePermission('OBSERVE_EMAIL', 'Email quan sát', 'Quan sát email từ Gmail của người dùng', 'TRIAGE', 1, [
        ['FREE', true],
        ['PLUS', true],
        ['PRO', true],
      ]),
      featurePermission('SEND_EMAIL', 'Gửi email', 'Gửi email qua Gmail', 'COMPOSE', 5, [
        ['FREE', false],
        ['PLUS', true],
        ['PRO', true],
      ]),
      featurePermission('TRIAGE_AI', 'Triage AI', 'Phân loại và tóm tắt email bằng AI', 'TRIAGE', 5, [
        ['FREE', true],
        ['PLUS', true],
        ['PRO', true],
      ]),
    ],
    paymentHistory: [
      {
        paymentId: 'pay-1',
        tenantId: '00000000-0000-4000-8000-000000000201',
        customerDisplayName: 'Acme Company',
        customerEmail: 'owner@acme.test',
        planCode: 'PLUS',
        periodLabel: '1 tháng',
        amountVnd: 199000,
        currency: 'VND',
        paymentMethod: 'SEPAY_QR',
        transactionCode: 'ZM12345',
        status: 'PENDING',
        createdAt: '2026-06-06T09:15:00Z',
      },
    ],
    snapshotAt: '2026-06-18T10:00:00Z',
  };
}

function plan(
  planId: string,
  code: string,
  displayName: string,
  tierRank: number,
  billingCycle: string,
  priceVnd: number,
  monthlyCreditAllowance: number,
  active: boolean,
  sortOrder: number,
) {
  return {
    planId,
    code,
    displayName,
    tierRank,
    billingCycle,
    currency: 'VND',
    priceVnd,
    monthlyCreditAllowance,
    active,
    sortOrder,
  };
}

function featurePermission(
  featureCode: string,
  displayName: string,
  description: string,
  category: string,
  fixedCreditCost: number,
  planPermissions: Array<[string, boolean]>,
) {
  return {
    featureCode,
    displayName,
    description,
    category,
    fixedCreditCost,
    unitLabel: 'credit/lần gọi',
    sortOrder: 1,
    planPermissions: planPermissions.map(([planCode, enabled]) => ({ planCode, enabled })),
  };
}

function applyPermissionPatchToFixture(url: string, body: unknown) {
  if (!isPermissionPatchBody(body)) return;
  const match = url.match(/features\/([^/]+)\/plans\/([^/]+)\/enabled/);
  if (!match) return;
  const [, featureCode, planCode] = match;
  const feature = billingPackageFixtureState.featurePermissions.find(
    (featurePermissionRow) => featurePermissionRow.featureCode === featureCode,
  );
  const planPermission = feature?.planPermissions.find(
    (permission) => permission.planCode === planCode,
  );
  if (planPermission) {
    planPermission.enabled = body.enabled;
  }
}

function applyCreditCostPatchToFixture(url: string, body: unknown) {
  if (!isCreditCostPatchBody(body)) return;
  const match = url.match(/features\/([^/]+)\/credit-cost/);
  if (!match) return;
  const [, featureCode] = match;
  const feature = billingPackageFixtureState.featurePermissions.find(
    (featurePermissionRow) => featurePermissionRow.featureCode === featureCode,
  );
  if (feature) {
    feature.fixedCreditCost = body.fixedCreditCost;
  }
}

function isPermissionPatchBody(value: unknown): value is { enabled: boolean } {
  return (
    typeof value === 'object'
    && value !== null
    && 'enabled' in value
    && typeof value.enabled === 'boolean'
  );
}

function isCreditCostPatchBody(value: unknown): value is { fixedCreditCost: number } {
  return (
    typeof value === 'object'
    && value !== null
    && 'fixedCreditCost' in value
    && typeof value.fixedCreditCost === 'number'
  );
}
