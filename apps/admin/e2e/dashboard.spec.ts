import { expect, test } from '@playwright/test';

let forbiddenApiCalls: string[];
let overviewRequests: string[];

test.beforeEach(async ({ page }) => {
  forbiddenApiCalls = [];
  overviewRequests = [];

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
    '**/api/admin/tenants**',
    '**/api/admin/spend/dashboard**',
    '**/api/admin/queue/health',
    '**/api/admin/audit/events**',
  ]) {
    await page.route(path, async (route) => {
      forbiddenApiCalls.push(route.request().url());
      await route.fulfill({ status: 500, json: { message: 'unexpected dashboard API call' } });
    });
  }

  await page.route('**/api/admin/overview**', async (route) => {
    overviewRequests.push(route.request().url());
    if (route.request().method() !== 'GET') {
      await route.fulfill({ status: 405 });
      return;
    }
    await route.fulfill({ json: overviewFixture() });
  });
});

test('admin lands on the Zero Mail overview dashboard after login', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'Dashboard tổng quan' })).toBeVisible();

  await expect(page.getByTestId('overview-kpi-tenants')).toContainText('24');
  await expect(page.getByTestId('overview-kpi-gmail-connected')).toContainText('19');
  await expect(page.getByTestId('overview-kpi-observed')).toContainText('8,630');
  await expect(page.getByTestId('overview-kpi-triage')).toContainText('4,661');
  await expect(page.getByTestId('overview-kpi-spend')).toContainText('12,450');

  await expect(page.getByRole('heading', { name: 'Hoạt động theo ngày' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Tỷ lệ triage thành công' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Phân bổ hành động' })).toBeVisible();

  await expect(page.getByRole('heading', { name: 'Top tenant theo hoạt động' })).toBeVisible();
  await expect(page.getByRole('cell', { name: '3,240' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Top tenant theo credits' })).toBeVisible();
  await expect(page.getByRole('cell', { name: '2,150' })).toBeVisible();

  await expect(page.getByRole('heading', { name: 'Cảnh báo hệ thống' })).toBeVisible();
  await expect(page.getByText('Pub/Sub backlog cao')).toBeVisible();
  await expect(page.getByText('Queue job stuck / dead-letter')).toBeVisible();

  const mainRegion = page.getByRole('main');
  await expect(mainRegion.getByRole('link', { name: 'Xem khách hàng' })).toBeVisible();
  await expect(mainRegion.getByRole('link', { name: 'Xem hàng đợi' })).toBeVisible();
  await expect(mainRegion.getByRole('link', { name: 'Quản lý LLM' })).toBeVisible();
  expect(forbiddenApiCalls).toEqual([]);
});

test('overview header only keeps a selectable date control', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Dashboard tổng quan' })).toBeVisible();

  await expect(page.getByText('Theo dõi vận hành hệ thống Zero Mail và hoạt động AI triage Gmail.')).toHaveCount(0);
  await expect(page.getByLabel('Tìm kiếm admin')).toHaveCount(0);

  const dateInput = page.getByLabel('Chọn ngày dashboard');
  await expect(dateInput).toBeVisible();
  const firstRequestUrl = overviewRequests[0];

  await dateInput.fill('2026-06-10');
  await expect.poll(() => overviewRequests.length).toBeGreaterThan(1);
  expect(overviewRequests.at(-1)).not.toEqual(firstRequestUrl);
  expect(forbiddenApiCalls).toEqual([]);
});

test('overview dashboard does not render raw prompt or email body fields', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Dashboard tổng quan' })).toBeVisible();

  const rawHtml = await page.content();
  const lowered = rawHtml.toLowerCase();
  expect(lowered).not.toContain('prompt_text');
  expect(lowered).not.toContain('completion_text');
  expect(lowered).not.toContain('email_body');
  expect(lowered).not.toContain('raw_message');
  expect(forbiddenApiCalls).toEqual([]);
});

test('action distribution chart renders colored donut segments', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Phân bổ hành động' })).toBeVisible();

  const distributionCard = page
    .getByRole('heading', { name: 'Phân bổ hành động' })
    .locator('xpath=ancestor::*[@data-slot="card"]');
  const donutRing = distributionCard.locator('div.relative.mx-auto.flex.size-36');
  const backgroundImage = await donutRing.evaluate(
    (element) => getComputedStyle(element).backgroundImage,
  );

  expect(backgroundImage).toContain('conic-gradient');
  expect(forbiddenApiCalls).toEqual([]);
});

function overviewFixture() {
  return {
    range: {
      from: '2026-06-12T00:00:00Z',
      to: '2026-06-19T00:00:00Z',
    },
    kpis: {
      totalTenants: 24,
      gmailConnectedTenants: 19,
      activeLast7dTenants: 17,
      observedEmailCount: 8630,
      triageActionCount: 4661,
      failedTriageActionCount: 51,
      outboundActionCount: 120,
      blockedOutboundActionCount: 8,
      llmCallCount: 9860,
      llmChargedCredits: 12450,
      llmCostUsd: 0,
      gmailUnhealthyTenants: 3,
      pubsubBacklogCount: 12460,
      deadLetterJobCount: 9,
      lowCreditTenantCount: 3,
    },
    successRate: {
      successRatePercent: 98.9,
      failureRatePercent: 1.1,
    },
    dailyActivity: [
      activityPoint('2026-06-12', 2560, 1900, 42),
      activityPoint('2026-06-13', 3310, 2300, 35),
      activityPoint('2026-06-14', 3780, 2780, 61),
      activityPoint('2026-06-15', 2890, 2120, 38),
      activityPoint('2026-06-16', 4120, 3150, 52),
      activityPoint('2026-06-17', 3560, 2580, 36),
      activityPoint('2026-06-18', 2610, 1940, 24),
    ],
    actionDistribution: [
      { key: 'OBSERVED_EMAIL', label: 'Email quan sát', count: 8630 },
      { key: 'TRIAGE_ACTION', label: 'Triage thành công', count: 4610 },
      { key: 'OUTBOUND_ACTION', label: 'Outbound actions', count: 112 },
      { key: 'FAILED_OR_BLOCKED', label: 'Lỗi / Bị chặn', count: 59 },
    ],
    topActivityTenants: [
      topActivityTenant('00000000-0000-4000-8000-000000000001', 'Acme Corp', 'acme.team@gmail.com', 3240, 1842, 12),
      topActivityTenant('00000000-0000-4000-8000-000000000002', 'Vina Retail', 'support@vinaretail.co', 2980, 1532, 17),
      topActivityTenant('00000000-0000-4000-8000-000000000003', 'EduSmart', 'help@edusmart.edu.vn', 2410, 1287, 22),
    ],
    topSpendTenants: [
      topSpendTenant('00000000-0000-4000-8000-000000000001', 'Acme Corp', 'acme.team@gmail.com', 142560, 2860),
      topSpendTenant('00000000-0000-4000-8000-000000000002', 'Vina Retail', 'support@vinaretail.co', 108214, 2150),
    ],
    alerts: [
      alertRow('GMAIL_UNHEALTHY', 'ERROR', 'Gmail token refresh lỗi', '3 tenant cần kiểm tra kết nối Gmail', 3),
      alertRow('PUBSUB_BACKLOG', 'WARNING', 'Pub/Sub backlog cao', '12460 message/job đang chờ xử lý', 12460),
      alertRow('TRIAGE_FAILURE_RATE', 'INFO', 'Triage failure rate cao', '1.1% trong khoảng đã chọn', 51),
      alertRow('OUTBOUND_BLOCKED', 'ERROR', 'Outbound action bị blocked nhiều', '8 action bị chặn trong khoảng đã chọn', 8),
      alertRow('LOW_CREDIT', 'WARNING', 'Tenant sắp hết credit', '3 tenant có số dư thấp', 3),
      alertRow('DEAD_LETTER', 'ERROR', 'Queue job stuck / dead-letter', '9 job trong dead-letter', 9),
    ],
    snapshotAt: '2026-06-18T10:00:00Z',
  };
}

function activityPoint(date: string, observed: number, triage: number, failed: number) {
  return {
    date,
    observedEmailCount: observed,
    triageActionCount: triage,
    failedTriageActionCount: failed,
  };
}

function topActivityTenant(
  tenantId: string,
  tenantDisplayName: string,
  primaryEmail: string,
  observedEmailCount: number,
  triageActionCount: number,
  failedTriageActionCount: number,
) {
  return {
    tenantId,
    tenantDisplayName,
    ownerEmail: primaryEmail,
    primaryEmail,
    observedEmailCount,
    triageActionCount,
    failedTriageActionCount,
    outboundActionCount: 120,
    blockedOutboundActionCount: 8,
    failureRatePercent: (failedTriageActionCount / triageActionCount) * 100,
  };
}

function topSpendTenant(
  tenantId: string,
  tenantDisplayName: string,
  primaryEmail: string,
  llmCallCount: number,
  chargedCredits: number,
) {
  return {
    tenantId,
    tenantDisplayName,
    ownerEmail: primaryEmail,
    primaryEmail,
    llmCallCount,
    chargedCredits,
    totalCostUsd: 0,
  };
}

function alertRow(
  key: string,
  severity: string,
  title: string,
  detail: string,
  count: number,
) {
  return {
    key,
    severity,
    title,
    detail,
    count,
    timeLabel: 'Hiện tại',
  };
}
