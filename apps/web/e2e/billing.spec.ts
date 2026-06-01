import { expect, type Page, test } from '@playwright/test';

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
  await expect(page.getByRole('button', { name: 'Thanh toán bằng thẻ' })).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Chuyển khoản QR' })).toBeEnabled();

  await page.getByRole('button', { name: 'Chuyển khoản QR' }).click();

  const bankTransferDialog = page.getByRole('dialog', { name: 'Chuyển khoản ngân hàng' });
  await expect(bankTransferDialog).toBeVisible();
  await expect(bankTransferDialog.getByRole('img', { name: 'Mã QR chuyển khoản' })).toBeVisible();
  await expect(bankTransferDialog.getByText('399.000₫')).toBeVisible();
  await expect(bankTransferDialog.getByText('ZM ABCD2345 PRO')).toBeVisible();
});

test('bank transfer success notification redirects to credits', async ({ page }) => {
  const state = createChromeMockState({ currentPlanCode: 'PLUS', preferredLanguage: 'vi' });

  await installPlanUpgradePaymentWebSocketMock(page);
  await seedAuthenticatedSession(page, 'vi');
  await installChromeApiMock(page, state);

  await page.goto('/upgrade-plan');
  await page.getByRole('button', { name: 'Chuyển khoản QR' }).click();

  await expect(page.getByRole('dialog', { name: 'Chuyển khoản ngân hàng' })).toBeVisible();
  await expect(
    page.getByText('Thanh toán thành công. Gói của bạn đã được kích hoạt.'),
  ).toBeVisible();
  await expect(page).toHaveURL(/\/credits$/);
});

async function installPlanUpgradePaymentWebSocketMock(page: Page) {
  await page.addInitScript(() => {
    const NativeWebSocket = window.WebSocket;
    const paymentFrameBody = JSON.stringify({
      type: 'PLAN_UPGRADE_PAYMENT_COMPLETED',
      bankTransferIntentId: '00000000-0000-0000-0000-000000000900',
      bankTransferCode: 'ABCD2345',
      planCode: 'PRO',
      provider: 'SEPAY',
      amountVnd: 399000,
      currency: 'VND',
      paidAt: '2026-06-01T10:00:00.000Z',
    });

    class FakePaymentWebSocket {
      static readonly CONNECTING = 0;
      static readonly OPEN = 1;
      static readonly CLOSING = 2;
      static readonly CLOSED = 3;

      readonly url!: string;
      readonly protocol = '';
      readonly extensions = '';
      binaryType: BinaryType = 'blob';
      bufferedAmount = 0;
      readyState = FakePaymentWebSocket.CONNECTING;
      onopen: ((event: Event) => void) | null = null;
      onmessage: ((event: MessageEvent) => void) | null = null;
      onerror: ((event: Event) => void) | null = null;
      onclose: ((event: CloseEvent) => void) | null = null;

      constructor(url: string | URL, protocols?: string | string[]) {
        const websocketUrl = String(url);
        if (!websocketUrl.endsWith('/ws')) {
          return (protocols === undefined
            ? new NativeWebSocket(url)
            : new NativeWebSocket(url, protocols)) as unknown as FakePaymentWebSocket;
        }

        this.url = websocketUrl;
        window.setTimeout(() => {
          this.readyState = FakePaymentWebSocket.OPEN;
          this.onopen?.(new Event('open'));
        }, 0);
      }

      send(frame: string | ArrayBufferLike | Blob | ArrayBufferView) {
        const frameText = String(frame);
        if (frameText.startsWith('CONNECT')) {
          this.emitMessage('CONNECTED\nversion:1.2\n\n\0');
          return;
        }
        if (frameText.startsWith('SUBSCRIBE')) {
          window.setTimeout(() => {
            this.emitMessage(
              `MESSAGE\nsubscription:sub-0\nmessage-id:payment-1\ndestination:/topic/tenants/tenant-1/billing\ncontent-type:application/json\n\n${paymentFrameBody}\0`,
            );
          }, 200);
        }
      }

      close() {
        this.readyState = FakePaymentWebSocket.CLOSED;
        this.onclose?.(new CloseEvent('close'));
      }

      addEventListener() {}

      removeEventListener() {}

      dispatchEvent() {
        return true;
      }

      private emitMessage(data: string) {
        window.setTimeout(() => {
          this.onmessage?.(new MessageEvent('message', { data }));
        }, 0);
      }
    }

    Object.defineProperty(window, 'WebSocket', {
      configurable: true,
      writable: true,
      value: FakePaymentWebSocket as unknown as typeof WebSocket,
    });
  });
}
