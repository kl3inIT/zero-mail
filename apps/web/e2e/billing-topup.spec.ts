import { expect, test, type Page, type Route } from '@playwright/test';

import {
  API_ROUTE_PATTERN,
  expectNoHorizontalOverflow,
  seedAuthenticatedSession,
} from './chrome-test-utils';

// Phase 05A billing e2e contract:
// - The backend has no ledger-history endpoint, so this covers the production
//   "transaction history is not available yet" panel. Populated rows are covered
//   by LedgerTable.test.tsx with injected fixture data.
// - The backend has no top-up intent-status endpoint; credited is inferred from
//   /api/billing/balance rising during polling.
// - PR #36 changes top-up creation from free-form VND amount entry to package
//   selection. The E2E mock follows that API shape: packages list + packageCode
//   intent creation.

test.describe.configure({ mode: 'serial' });

type BillingMockState = {
  availableCredits: number;
  heldCredits: number;
  balanceRequests: number;
  topupRequests: string[];
  packages: BillingPackageMock[];
  nextIntent: TopupIntentMock;
};

type BillingPackageMock = {
  code: string;
  name: string;
  priceVnd: number;
  creditAmount: number;
  description: string;
  displayOrder: number;
};

type TopupIntentMock = {
  orderCode: string;
  packageCode: string;
  packageName: string;
  amountVnd: number;
  creditAmount: number;
  expiresAt: string;
  bankCode: string;
  bankName: string;
  accountNumber: string;
  accountName: string;
  transferContent: string;
  qrPayload: string;
};

const VIEWPORTS = [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 320, height: 740 },
] as const;

const TOPUP_CODE = 'ABCD2345';
const DEFAULT_PACKAGE_CODE = 'PKG_50K';
const DEFAULT_TRANSFER_CONTENT = `ZM ${TOPUP_CODE} ${DEFAULT_PACKAGE_CODE}`;
const QR_PAYLOAD =
  '00020101021238540010A0000007270124000697042201101234567890208QRIBFTTA530370454062500005802VN6304ABCD';
const DEFAULT_PACKAGES: BillingPackageMock[] = [
  {
    code: 'PKG_10K',
    name: 'Starter 10K',
    priceVnd: 10_000,
    creditAmount: 10,
    description: 'Starter credit package',
    displayOrder: 10,
  },
  {
    code: 'PKG_20K',
    name: 'Growth 20K',
    priceVnd: 20_000,
    creditAmount: 20,
    description: 'Growth credit package',
    displayOrder: 20,
  },
  {
    code: DEFAULT_PACKAGE_CODE,
    name: 'Scale 50K',
    priceVnd: 50_000,
    creditAmount: 50,
    description: 'Scale credit package',
    displayOrder: 50,
  },
];

for (const viewport of VIEWPORTS) {
  test(`billing page renders shell, balance, and unavailable ledger at ${viewport.name}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const state = createBillingMockState({ availableCredits: 12 });

    await openBilling(page, '/billing', state);

    await expect(page.getByTestId('app-shell')).toBeVisible();
    await expect(page.getByTestId('chrome-header')).toBeVisible();
    await expect(page.getByTestId('balance-pill')).toContainText('12');
    await expect(page.getByRole('heading', { name: 'Billing' })).toBeVisible();
    await expect(page.getByTestId('billing-balance-figure')).toContainText('12');
    await expect(page.getByRole('link', { name: 'Top up credits' })).toBeVisible();
    await expect(page.getByTestId('ledger-unavailable-panel')).toContainText(
      "Transaction history isn't available yet",
    );
    await expect(page.getByText('No transactions yet')).toHaveCount(0);
    await expectNoHorizontalOverflow(page);

    if (viewport.width < 768) {
      await page.getByRole('button', { name: 'Toggle navigation' }).click();
    }
    await expect(page.getByRole('link', { name: 'Billing' }).first()).toBeVisible();
  });

  test(`top-up package to credited success flow works at ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const state = createBillingMockState({ availableCredits: 12 });

    await openBilling(page, '/billing', state);
    await page.getByRole('link', { name: 'Top up credits' }).click();

    await expect(page).toHaveURL(/\/billing\/top-up$/, { timeout: 15_000 });
    await expect(page.getByTestId('topup-package-step')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Scale 50K' })).toBeVisible();
    await page.getByRole('button', { name: 'Choose package' }).nth(2).click();

    await expect.poll(() => state.topupRequests).toEqual([DEFAULT_PACKAGE_CODE]);
    await expect(page).toHaveURL(new RegExp(`/billing/top-up\\?code=${TOPUP_CODE}`));
    const instructionsStep = page.getByTestId('topup-instructions-step');
    await expect(instructionsStep).toBeVisible();
    await expect(instructionsStep.getByText('Waiting for your transfer')).toBeVisible();
    await expect(page.getByAltText('SePay payment QR code')).toBeVisible();
    await expect(instructionsStep.getByText(TOPUP_CODE, { exact: true })).toBeVisible();
    await expect(instructionsStep.getByText(/50,000/)).toBeVisible();
    await expect(instructionsStep.getByText('Vietcombank (VCB)')).toBeVisible();
    await expect(instructionsStep.getByText('0123456789')).toBeVisible();
    await expect(instructionsStep.getByText('Zero Mail', { exact: true })).toBeVisible();
    await expect(instructionsStep.getByText(DEFAULT_TRANSFER_CONTENT)).toBeVisible();
    await expect(instructionsStep.getByText(/Expires in \d\d:\d\d/)).toBeVisible();

    await page.getByRole('button', { name: 'Copy Transfer reference' }).click();
    await expect(page.getByRole('button', { name: 'Copy Transfer reference' })).toContainText(
      'Copied',
    );
    await expectNoHorizontalOverflow(page);

    const requestsBeforeCredit = state.balanceRequests;
    state.availableCredits = 42;
    await page.reload({ waitUntil: 'domcontentloaded' });

    const successStep = page.getByTestId('topup-success-step');
    await expect(successStep).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Credits added' })).toBeVisible();
    await expect(successStep.getByText('42')).toBeVisible();
    expect(state.balanceRequests).toBeGreaterThan(requestsBeforeCredit);

    await page.getByRole('link', { name: 'Back to billing' }).click();
    await expect(page).toHaveURL(/\/billing$/, { timeout: 15_000 });
    await expect(page.getByTestId('billing-balance-figure')).toContainText('42');
    await expectNoHorizontalOverflow(page);
  });
}

test('top-up route rehydrates a pending intent from code search param and sessionStorage', async ({
  page,
}) => {
  const state = createBillingMockState();

  await openBilling(page, '/billing/top-up', state);
  await expect(page.getByTestId('topup-package-step')).toBeVisible();
  await page.getByRole('button', { name: 'Choose package' }).nth(2).click();

  await expect(page.getByTestId('topup-instructions-step')).toBeVisible();
  await expect(page.getByText(TOPUP_CODE, { exact: true })).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`/billing/top-up\\?code=${TOPUP_CODE}`));
  await page.reload({ waitUntil: 'domcontentloaded' });

  await expect(page).toHaveURL(new RegExp(`/billing/top-up\\?code=${TOPUP_CODE}`));
  await expect(page.getByTestId('topup-instructions-step')).toBeVisible();
  await expect(page.getByText(TOPUP_CODE, { exact: true })).toBeVisible();
  await expect(page.getByText(DEFAULT_TRANSFER_CONTENT)).toBeVisible();
});

test('expired top-up panel resets to the package step and clears the code query param', async ({
  page,
}) => {
  const state = createBillingMockState({
    nextIntent: createTopupIntentMock({ expiresAt: pastExpiresAt() }),
  });

  await openBilling(page, '/billing/top-up', state);
  await expect(page.getByTestId('topup-package-step')).toBeVisible();
  await page.getByRole('button', { name: 'Choose package' }).nth(2).click();

  await expect(page.getByTestId('topup-expired-step')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'This top-up expired' })).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`/billing/top-up\\?code=${TOPUP_CODE}`));

  await page.getByRole('button', { name: 'Start a new top-up' }).click();

  await expect(page).toHaveURL(/\/billing\/top-up$/);
  await expect(page.getByTestId('topup-package-step')).toBeVisible();
});

async function openBilling(
  page: Page,
  path: '/billing' | '/billing/top-up',
  state: BillingMockState,
) {
  await seedAuthenticatedSession(page);
  await installBillingApiMock(page, state);
  await page.goto(path, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');
}

async function installBillingApiMock(page: Page, state: BillingMockState) {
  await page.route(API_ROUTE_PATTERN, async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (url.pathname === '/api/me') {
      await fulfillJson(route, {
        userId: 'user-1',
        tenantId: 'tenant-1',
        email: 'founder@example.com',
        preferredLanguage: 'en',
        onboardingStep: 'COMPLETE',
        triagePaused: false,
        gmailConnectionStatus: {
          status: 'CONNECTED',
          ingestionHealth: 'HEALTHY',
          googleEmail: 'founder@example.com',
        },
      });
      return;
    }

    if (url.pathname === '/api/billing/balance' && request.method() === 'GET') {
      state.balanceRequests += 1;
      await fulfillJson(route, {
        availableCredits: state.availableCredits,
        heldCredits: state.heldCredits,
        currency: 'credits',
      });
      return;
    }

    if (url.pathname === '/api/me/notifications' && request.method() === 'GET') {
      await fulfillJson(route, {
        channel: 'DAILY_DIGEST',
        digestEnabled: true,
        digestSendHourLocal: 20,
        timeZone: 'Asia/Ho_Chi_Minh',
      });
      return;
    }

    if (url.pathname === '/api/billing/packages' && request.method() === 'GET') {
      await fulfillJson(route, state.packages);
      return;
    }

    if (url.pathname === '/api/billing/topup/intent' && request.method() === 'POST') {
      const payload = request.postDataJSON() as { packageCode?: string };
      const requestedPackageCode = payload.packageCode;
      if (
        !requestedPackageCode ||
        !state.packages.some((item) => item.code === requestedPackageCode)
      ) {
        await fulfillJson(route, { message: 'Unknown billing package' }, 400);
        return;
      }
      expect(payload.packageCode).toBe(state.nextIntent.packageCode);
      state.topupRequests.push(requestedPackageCode);
      await fulfillJson(route, state.nextIntent);
      return;
    }

    if (url.pathname === '/api/gmail/connection/status' && request.method() === 'GET') {
      await fulfillJson(route, { connectionStatus: 'CONNECTED' });
      return;
    }

    if (url.pathname === '/api/tenant/triage-pause' && request.method() === 'PUT') {
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    await route.fulfill({ status: 204, body: '' });
  });
}

function createBillingMockState(overrides: Partial<BillingMockState> = {}): BillingMockState {
  return {
    availableCredits: 12,
    heldCredits: 0,
    balanceRequests: 0,
    topupRequests: [],
    packages: DEFAULT_PACKAGES,
    nextIntent: createTopupIntentMock(),
    ...overrides,
  };
}

function createTopupIntentMock(overrides: Partial<TopupIntentMock> = {}): TopupIntentMock {
  return {
    orderCode: TOPUP_CODE,
    packageCode: DEFAULT_PACKAGE_CODE,
    packageName: 'Scale 50K',
    amountVnd: 50_000,
    creditAmount: 50,
    expiresAt: futureExpiresAt(),
    bankCode: 'VCB',
    bankName: 'Vietcombank',
    accountNumber: '0123456789',
    accountName: 'Zero Mail',
    transferContent: DEFAULT_TRANSFER_CONTENT,
    qrPayload: QR_PAYLOAD,
    ...overrides,
  };
}

function futureExpiresAt(): string {
  return new Date(Date.now() + 10 * 60_000).toISOString();
}

function pastExpiresAt(): string {
  return new Date(Date.now() - 60_000).toISOString();
}

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}
