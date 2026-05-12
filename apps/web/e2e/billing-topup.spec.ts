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
// - TopupIntentResponse has only code/amountVnd/expiresAt/qrPayload, so the
//   instructions screen shows QR payload + code + amount + expiry only.

test.describe.configure({ mode: 'serial' });

type BillingMockState = {
  availableCredits: number;
  heldCredits: number;
  balanceRequests: number;
  topupRequests: number[];
  nextIntent: {
    code: string;
    amountVnd: number;
    expiresAt: string;
    qrPayload: string;
  };
};

const VIEWPORTS = [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 320, height: 740 },
] as const;

const TOPUP_CODE = 'ZMABCD2345';
const QR_PAYLOAD =
  '00020101021238540010A0000007270124000697042201101234567890208QRIBFTTA530370454062500005802VN6304ABCD';

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

  test(`top-up amount to credited success flow works at ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const state = createBillingMockState({
      availableCredits: 12,
      nextIntent: {
        code: TOPUP_CODE,
        amountVnd: 250_000,
        expiresAt: futureExpiresAt(),
        qrPayload: QR_PAYLOAD,
      },
    });

    await openBilling(page, '/billing', state);
    await page.getByRole('link', { name: 'Top up credits' }).click();

    await expect(page).toHaveURL(/\/billing\/top-up$/);
    await expect(page.getByTestId('topup-amount-step')).toBeVisible();
    await page.getByLabel('Top-up amount').fill('250000');
    await page.getByRole('button', { name: 'Continue to payment' }).click();

    await expect.poll(() => state.topupRequests).toEqual([250_000]);
    await expect(page).toHaveURL(new RegExp(`/billing/top-up\\?code=${TOPUP_CODE}`));
    await expect(page.getByTestId('topup-instructions-step')).toBeVisible();
    await expect(page.getByText('Scan this QR with your banking app')).toBeVisible();
    await expect(page.getByText(QR_PAYLOAD)).toBeVisible();
    await expect(page.getByText(TOPUP_CODE)).toBeVisible();
    await expect(page.getByText('₫250,000')).toBeVisible();
    await expect(page.getByText(/Expires in \d\d:\d\d/)).toBeVisible();
    await expect(page.getByText('Bank name')).toHaveCount(0);
    await expect(page.getByText('Bank account')).toHaveCount(0);
    await expect(page.getByText('Account holder')).toHaveCount(0);

    await page.getByRole('button', { name: 'Copy Transfer reference' }).click();
    await expect(page.getByRole('button', { name: 'Copy Transfer reference' })).toContainText(
      'Copied',
    );
    await expectNoHorizontalOverflow(page);

    const requestsBeforeCredit = state.balanceRequests;
    state.availableCredits = 42;
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const successStep = page.getByTestId('topup-success-step');
    await expect(successStep).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Credits added' })).toBeVisible();
    await expect(successStep.getByText('42')).toBeVisible();
    expect(state.balanceRequests).toBeGreaterThan(requestsBeforeCredit);

    await page.getByRole('link', { name: 'Back to billing' }).click();
    await expect(page).toHaveURL(/\/billing$/);
    await expect(page.getByTestId('billing-balance-figure')).toContainText('42');
    await expectNoHorizontalOverflow(page);
  });
}

test('top-up route rehydrates a pending intent from code search param and sessionStorage', async ({
  page,
}) => {
  const state = createBillingMockState({
    nextIntent: {
      code: TOPUP_CODE,
      amountVnd: 300_000,
      expiresAt: futureExpiresAt(),
      qrPayload: QR_PAYLOAD,
    },
  });

  await openBilling(page, '/billing/top-up', state);
  await page.getByLabel('Top-up amount').fill('300000');
  await page.getByRole('button', { name: 'Continue to payment' }).click();

  await expect(page.getByTestId('topup-instructions-step')).toBeVisible();
  await expect(page.getByText(TOPUP_CODE)).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`/billing/top-up\\?code=${TOPUP_CODE}`));
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle');

  await expect(page).toHaveURL(new RegExp(`/billing/top-up\\?code=${TOPUP_CODE}`));
  await expect(page.getByTestId('topup-instructions-step')).toBeVisible();
  await expect(page.getByText(TOPUP_CODE)).toBeVisible();
  await expect(page.getByText(QR_PAYLOAD)).toBeVisible();
});

test('expired top-up panel resets to the amount step and clears the code query param', async ({
  page,
}) => {
  const state = createBillingMockState({
    nextIntent: {
      code: TOPUP_CODE,
      amountVnd: 150_000,
      expiresAt: pastExpiresAt(),
      qrPayload: QR_PAYLOAD,
    },
  });

  await openBilling(page, '/billing/top-up', state);
  await page.getByLabel('Top-up amount').fill('150000');
  await page.getByRole('button', { name: 'Continue to payment' }).click();

  await expect(page.getByTestId('topup-expired-step')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'This top-up expired' })).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`/billing/top-up\\?code=${TOPUP_CODE}`));

  await page.getByRole('button', { name: 'Start a new top-up' }).click();

  await expect(page).toHaveURL(/\/billing\/top-up$/);
  await expect(page.getByTestId('topup-amount-step')).toBeVisible();
});

async function openBilling(
  page: Page,
  path: '/billing' | '/billing/top-up',
  state: BillingMockState,
) {
  await seedAuthenticatedSession(page);
  await installBillingApiMock(page, state);
  await page.goto(path, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle');
}

async function installBillingApiMock(page: Page, state: BillingMockState) {
  await page.route(API_ROUTE_PATTERN, async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (url.pathname === '/me') {
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

    if (url.pathname === '/api/billing/topup/intent' && request.method() === 'POST') {
      const payload = request.postDataJSON() as { amountVnd?: number };
      expect(payload.amountVnd).toBe(state.nextIntent.amountVnd);
      state.topupRequests.push(payload.amountVnd ?? 0);
      await fulfillJson(route, state.nextIntent);
      return;
    }

    if (url.pathname === '/gmail/connection/status' && request.method() === 'GET') {
      await fulfillJson(route, { connectionStatus: 'CONNECTED' });
      return;
    }

    if (url.pathname === '/tenant/triage-pause' && request.method() === 'PUT') {
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
    nextIntent: {
      code: TOPUP_CODE,
      amountVnd: 250_000,
      expiresAt: futureExpiresAt(),
      qrPayload: QR_PAYLOAD,
    },
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
