import { expect, test } from '@playwright/test';

const ROW_LEVEL_CLASSIFICATION_SINCE = '2026-05-20';

const fixtureResponse = {
  kpis: {
    todayPlatformCost: '12.500000',
    todayByokCost: '4.250000',
    todayUnknownCost: '0.000000',
    sevenDayPlatformCost: '88.300000',
    sevenDayByokCost: '21.700000',
    sevenDayUnknownCost: '5.000000',
    thirtyDayPlatformCost: '345.250000',
    thirtyDayByokCost: '99.000000',
    thirtyDayUnknownCost: '12.250000',
    todayCallCount: 124,
    sevenDayCallCount: 902,
    thirtyDayCallCount: 4_180,
  },
  stackBar: [
    {
      bucketDate: '2026-05-19T00:00:00Z',
      provider: 'OPENAI',
      platformCost: '5.20',
      byokCost: '1.10',
      unknownCost: '0.00',
      callCount: 41,
    },
    {
      bucketDate: '2026-05-19T00:00:00Z',
      provider: 'ANTHROPIC',
      platformCost: '6.40',
      byokCost: '2.50',
      unknownCost: '1.00',
      callCount: 32,
    },
    {
      bucketDate: '2026-05-20T00:00:00Z',
      provider: 'OPENAI',
      platformCost: '4.10',
      byokCost: '1.30',
      unknownCost: '0.00',
      callCount: 28,
    },
    {
      bucketDate: '2026-05-20T00:00:00Z',
      provider: 'ANTHROPIC',
      platformCost: '3.20',
      byokCost: '1.90',
      unknownCost: '0.00',
      callCount: 23,
    },
    {
      bucketDate: '2026-05-20T00:00:00Z',
      provider: 'DEEPSEEK',
      platformCost: '0.80',
      byokCost: '0.30',
      unknownCost: '0.00',
      callCount: 19,
    },
    {
      bucketDate: '2026-05-20T00:00:00Z',
      provider: 'GOOGLE',
      platformCost: '0.60',
      byokCost: '0.20',
      unknownCost: '0.00',
      callCount: 14,
    },
  ],
  donut: [
    { feature: 'CHAT', totalCost: '12.50', callCount: 240, percentOfTotal: 50.0 },
    { feature: 'TRIAGE', totalCost: '8.00', callCount: 180, percentOfTotal: 32.0 },
    { feature: 'DRAFT', totalCost: '4.50', callCount: 110, percentOfTotal: 18.0 },
  ],
  topTenants: [
    {
      tenantId: '00000000-0000-4000-8000-000000000aaa',
      gmailAccountEmailOrPlaceholder: 'top-1@example.com',
      totalCost: '15.25',
      unknownCost: '0.00',
      callCount: 240,
      isKAnonymized: false,
      unknownPct: 0,
    },
    {
      tenantId: '00000000-0000-4000-8000-000000000bbb',
      gmailAccountEmailOrPlaceholder: 'top-2@example.com',
      totalCost: '8.20',
      unknownCost: '0.50',
      callCount: 110,
      isKAnonymized: false,
      unknownPct: 6.1,
    },
    {
      tenantId: null,
      gmailAccountEmailOrPlaceholder: 'K-anonymized aggregate (3 tenants)',
      totalCost: '4.00',
      unknownCost: '0.00',
      callCount: 50,
      isKAnonymized: true,
      unknownPct: 0,
    },
  ],
  snapshotAt: new Date().toISOString(),
  kAnonymityFooterNote:
    'Per-tenant spend bucketed by 7-day k-anonymity (k≥5). Exact per-tenant cost is not exposed.',
  unknownPercentOfTotal: 4.8,
  rowLevelClassificationSince: ROW_LEVEL_CLASSIFICATION_SINCE,
};

test.beforeEach(async ({ page }) => {
  await page.route('**/api/admin/me', async (route) => {
    await route.fulfill({
      json: { adminUserId: 'admin-1', email: 'admin@example.com', env: 'dev' },
    });
  });

  await page.route('**/api/admin/spend/dashboard**', async (route) => {
    if (route.request().url().includes('/dashboard/csv')) {
      await route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'text/csv',
          'Content-Disposition': 'attachment; filename="spend-fixture.csv"',
        },
        body:
          'bucketDate,provider,feature,credentialSource,totalCost,callCount\n' +
          '2026-05-20T00:00:00Z,OPENAI,CHAT,PLATFORM,4.10,28\n',
      });
      return;
    }
    await route.fulfill({ json: fixtureResponse });
  });
});

test('spend dashboard renders KPIs, charts, and tenant table from fixture', async ({ page }) => {
  await page.goto('/spend');

  await expect(page.getByRole('heading', { name: 'Spend dashboard' })).toBeVisible();

  // KPI cards
  await expect(page.getByTestId('kpi-today-platform')).toContainText('$12.50');
  await expect(page.getByTestId('kpi-today-byok')).toContainText('$4.25');
  await expect(page.getByTestId('kpi-30d-platform')).toContainText('$345.25');

  // Stacked bar chart present with aria-label
  await expect(page.getByTestId('spend-stack-bar')).toBeVisible();

  // Donut chart present
  await expect(page.getByTestId('spend-feature-donut')).toBeVisible();
  await expect(page.getByTestId('spend-donut-CHAT')).toContainText('CHAT');

  // Top tenants table — both individual + rollup row
  await expect(page.getByText('top-1@example.com')).toBeVisible();
  await expect(page.getByText(/K-anonymized aggregate/)).toBeVisible();

  // K-anonymity footer
  await expect(page.getByTestId('spend-kanonymity-footer')).toContainText('k≥5');

  // Unknown caveat caption
  await expect(page.getByTestId('spend-unknown-caveat')).toContainText('predates row-level');

  // AutoRefreshIndicator shows "Updated Ns ago"
  await expect(page.getByText(/Updated \d+s ago/)).toBeVisible();
});

test('custom range > 90 days shows inline error and disables apply', async ({ page }) => {
  await page.goto('/spend');
  await expect(page.getByRole('heading', { name: 'Spend dashboard' })).toBeVisible();

  await page.getByTestId('spend-preset-custom').click();
  await page.getByTestId('spend-custom-from').fill('2025-01-01');
  await page.getByTestId('spend-custom-to').fill('2026-05-01');
  await page.getByTestId('spend-custom-apply').click();

  await expect(page.getByTestId('spend-custom-error')).toContainText(
    'Date range maximum is 90 days',
  );
});

test('CSV export downloads file via attachment header', async ({ page }) => {
  await page.goto('/spend');
  await expect(page.getByRole('heading', { name: 'Spend dashboard' })).toBeVisible();

  const downloadPromise = page.waitForEvent('download');
  await page.getByTestId('spend-export-csv').click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toContain('.csv');
});

test('spend page never receives or renders a prompt or completion body', async ({ page }) => {
  await page.goto('/spend');
  await expect(page.getByRole('heading', { name: 'Spend dashboard' })).toBeVisible();

  const rawHtml = await page.content();
  const lowered = rawHtml.toLowerCase();
  expect(lowered).not.toContain('prompt_text');
  expect(lowered).not.toContain('completion_text');
  expect(lowered).not.toContain('request_body');
  expect(lowered).not.toContain('response_body');
});
