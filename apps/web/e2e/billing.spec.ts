import { expect, test } from '@playwright/test';

import {
  createChromeMockState,
  installChromeApiMock,
  seedAuthenticatedSession,
} from './chrome-test-utils';

test('credits page shows balance, paginates history, and keeps upgrade in account menu', async ({
  page,
}) => {
  const state = createChromeMockState({ currentPlanCode: 'PLUS', preferredLanguage: 'vi' });

  await seedAuthenticatedSession(page, 'vi');
  await installChromeApiMock(page, state);

  await page.goto('/credits');

  await expect(page.getByRole('heading', { name: 'Tín dụng & lịch sử sử dụng' })).toBeVisible();
  await expect(page.getByTestId('sidebar-footer-account')).toContainText('Plus');
  await expect(page.getByTestId('app-sidebar').getByText('Tín dụng')).toHaveCount(0);
  await expect(page.getByTestId('app-sidebar').getByText('Cài đặt')).toHaveCount(0);
  await expect(page.getByTestId('app-sidebar').getByText('Nâng cấp gói')).toHaveCount(0);
  await expect(page.getByTestId('app-sidebar').getByText('Cấu hình AI')).toBeVisible();

  await expect(page.getByTestId('ledger-row')).toHaveCount(10);
  await page.getByRole('button', { name: 'Tải thêm' }).click();
  await expect(page.getByTestId('ledger-row')).toHaveCount(12);
  await expect(page.getByRole('button', { name: 'Đã hết' })).toBeDisabled();

  await page.getByTestId('user-menu-trigger').click();
  await expect(page.getByText('Tài khoản Gmail')).toBeVisible();
  await expect(page.getByRole('menuitem', { name: 'Thêm hoặc quản lý tài khoản' })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: 'Nâng cấp gói' })).toHaveCount(0);

  await page.keyboard.press('Escape');
  await page.getByTestId('sidebar-footer-account').click();
  await expect(page.getByRole('menuitem', { name: 'Tín dụng' })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: 'Nâng cấp gói' })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: 'Cấu hình AI' })).toHaveCount(0);
});

test('upgrade page disables tiers below the active plan', async ({ page }) => {
  const state = createChromeMockState({ currentPlanCode: 'PLUS', preferredLanguage: 'vi' });

  await seedAuthenticatedSession(page, 'vi');
  await installChromeApiMock(page, state);

  await page.goto('/upgrade-plan');

  await expect(page.getByRole('heading', { name: 'Nâng cấp gói' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Bạn đã có toàn quyền' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Gói hiện tại' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Thanh toán ngay' })).toBeEnabled();
});
