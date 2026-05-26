import {expect, test} from '@playwright/test';

import type {components} from '../src/lib/api/admin-schema';

type BillingPackageFixture = components['schemas']['BillingPackageAdminResponse'];

const starterPackageId = '00000000-0000-4000-8000-000000000101';
const scalePackageId = '00000000-0000-4000-8000-000000000102';
const createdPackageId = '00000000-0000-4000-8000-000000000103';

test.beforeEach(async ({page}) => {
  const packages: BillingPackageFixture[] = [
    {
      id: starterPackageId,
      code: 'PKG_STARTER',
      name: 'Starter',
      priceVnd: 10000,
      creditAmount: 10,
      description: 'Gói nhập môn',
      includedFeatures: ['Tạo mã chuyển khoản ngay lập tức'],
      featured: false,
      active: true,
      displayOrder: 10,
      createdAt: '2026-05-20T10:00:00Z',
      updatedAt: '2026-05-20T10:00:00Z',
      purchaseCount: 12,
      pendingIntentCount: 1,
      totalRevenueVnd: 120000,
      lastPurchasedAt: '2026-05-22T09:15:00Z',
    },
    {
      id: scalePackageId,
      code: 'PKG_SCALE',
      name: 'Scale',
      priceVnd: 50000,
      creditAmount: 50,
      includedFeatures: ['Ưu tiên hiển thị'],
      featured: true,
      active: false,
      displayOrder: 20,
      createdAt: '2026-05-20T10:00:00Z',
      updatedAt: '2026-05-20T10:00:00Z',
      purchaseCount: 30,
      pendingIntentCount: 2,
      totalRevenueVnd: 1500000,
      lastPurchasedAt: '2026-05-23T11:30:00Z',
    },
  ];

  await page.route('**/api/admin/me', async (route) => {
    await route.fulfill({
      json: {adminUserId: 'admin-1', email: 'admin@example.com', env: 'dev'},
    });
  });

  await page.route('**/api/admin/billing/packages', async (route) => {
    const request = route.request();
    if (request.method() === 'GET') {
      await route.fulfill({json: {packages}});
      return;
    }
    if (request.method() === 'POST') {
      const payload = request.postDataJSON() as Partial<BillingPackageFixture>;
      const created: BillingPackageFixture = {
        id: createdPackageId,
        code: payload.code ?? 'PKG_GROWTH',
        name: payload.name ?? 'Growth',
        priceVnd: payload.priceVnd ?? 20000,
        creditAmount: payload.creditAmount ?? 20,
        description: payload.description,
        includedFeatures: payload.includedFeatures ?? ['Quyền lợi mới'],
        featured: payload.featured ?? false,
        active: payload.active ?? true,
        displayOrder: payload.displayOrder ?? 30,
        createdAt: '2026-05-24T08:00:00Z',
        updatedAt: '2026-05-24T08:00:00Z',
        purchaseCount: 0,
        pendingIntentCount: 0,
        totalRevenueVnd: 0,
      };
      packages.push(created);
      await route.fulfill({status: 201, json: created});
      return;
    }
    await route.fallback();
  });

  await page.route('**/api/admin/billing/packages/reorder', async (route) => {
    const payload = route.request().postDataJSON() as {
      items: { packageId: string; displayOrder: number }[];
    };
    for (const item of payload.items) {
      const found = packages.find((billingPackage) => billingPackage.id === item.packageId);
      if (found) found.displayOrder = item.displayOrder;
    }
    packages.sort((left, right) => left.displayOrder - right.displayOrder);
    await route.fulfill({json: {packages}});
  });

  await page.route('**/api/admin/billing/packages/*/activate', async (route) => {
    const packageId = route.request().url().split('/').at(-2);
    const found = packages.find((billingPackage) => billingPackage.id === packageId);
    if (!found) {
      await route.fulfill({status: 404, json: {code: 'error.admin.billing.package_not_found'}});
      return;
    }
    found.active = true;
    await route.fulfill({json: found});
  });

  await page.route('**/api/admin/billing/packages/*/deactivate', async (route) => {
    const packageId = route.request().url().split('/').at(-2);
    const found = packages.find((billingPackage) => billingPackage.id === packageId);
    if (!found) {
      await route.fulfill({status: 404, json: {code: 'error.admin.billing.package_not_found'}});
      return;
    }
    found.active = false;
    await route.fulfill({json: found});
  });

  await page.route('**/api/admin/billing/packages/*', async (route) => {
    const request = route.request();
    const packageId = request.url().split('/').at(-1);
    if (packageId === 'reorder') {
      await route.fallback();
      return;
    }
    const found = packages.find((billingPackage) => billingPackage.id === packageId);
    if (!found) {
      await route.fulfill({status: 404, json: {code: 'error.admin.billing.package_not_found'}});
      return;
    }
    if (request.method() === 'PATCH') {
      const payload = request.postDataJSON() as Partial<BillingPackageFixture>;
      found.name = payload.name ?? found.name;
      found.priceVnd = payload.priceVnd ?? found.priceVnd;
      found.creditAmount = payload.creditAmount ?? found.creditAmount;
      found.description = payload.description;
      found.includedFeatures = payload.includedFeatures ?? found.includedFeatures;
      found.featured = payload.featured ?? found.featured;
      found.active = payload.active ?? found.active;
      found.displayOrder = payload.displayOrder ?? found.displayOrder;
      found.updatedAt = '2026-05-24T09:00:00Z';
      await route.fulfill({json: found});
      return;
    }
    if (request.method() === 'GET') {
      await route.fulfill({json: found});
      return;
    }
    await route.fallback();
  });
});

test('admin can manage billing packages catalog', async ({page}) => {
  await page.goto('/billing-packages');

  await expect(page.getByRole('heading', {name: 'Gói thanh toán'})).toBeVisible();
  await expect(page.getByTestId('billing-packages-total')).toContainText('2');
  await expect(page.getByRole('cell', {name: 'PKG_STARTER', exact: true})).toBeVisible();
  await expect(page.getByRole('row', {name: /scale/i})).toBeVisible();

  await page.getByRole('button', {name: 'Tạo gói'}).click();
  await page.getByLabel('Code').fill('PKG_GROWTH');
  await page.getByLabel('Tên gói').fill('Growth');
  await page.getByLabel('Giá VND').fill('20000');
  await page.getByLabel('Credit nhận được').fill('20');
  await page.getByLabel('Thứ tự hiển thị').fill('30');
  await page.getByLabel('Mô tả').fill('Gói tăng trưởng');
  await page.getByLabel('Bao gồm').fill('Tạo mã chuyển khoản riêng\nƯu tiên ghi nhận thanh toán');
  await page.getByRole('dialog').getByRole('button', {name: 'Tạo gói'}).click();
  await expect(page.getByRole('cell', {name: 'PKG_GROWTH', exact: true})).toBeVisible();

  await page.getByRole('row', {name: /Starter/}).getByRole('button', {name: 'Sửa'}).click();
  await page.getByLabel('Tên gói').fill('Starter Plus');
  await page.getByLabel('Giá VND').fill('15000');
  await page.getByLabel('Credit nhận được').fill('15');
  await page.getByLabel('Bao gồm').fill('Quyền lợi đã cập nhật');
  await page.getByRole('button', {name: 'Lưu thay đổi'}).click();
  await expect(page.getByText('Starter Plus')).toBeVisible();

  await page.getByRole('row', {name: /Scale/}).getByRole('button', {name: 'Bật'}).click();
  await expect(page.getByRole('row', {name: /Scale/})).toContainText('Đang bật');

  await page.getByLabel('Thứ tự PKG_STARTER').fill('40');
  await page.getByRole('button', {name: 'Lưu thứ tự'}).click();
  await expect(page.getByRole('button', {name: 'Lưu thứ tự'})).toBeDisabled();
});
