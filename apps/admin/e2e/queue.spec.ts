import { expect, test } from '@playwright/test';

const fixtureJobId = '00000000-0000-4000-8000-0000000008e1';
const fixtureJobToken = '00000000';

test.beforeEach(async ({ page }) => {
  let requeued = false;
  let snapshotTick = 0;

  await page.route('**/api/admin/me', async (route) => {
    await route.fulfill({
      json: { adminUserId: 'admin-1', email: 'admin@example.com', env: 'dev' },
    });
  });

  await page.route('**/api/admin/queue/health', async (route) => {
    snapshotTick += 1;
    await route.fulfill({
      json: {
        depthByType: [
          { jobType: 'TRIAGE_INCOMING', pendingCount: 8 + snapshotTick, processingCount: 4 },
          { jobType: 'DRAFT_REPLY', pendingCount: 2, processingCount: 1 },
        ],
        oldestUnleasedJobAgeSeconds: 312,
        retryHistogram: [
          { attemptsBucket: 0, rowCount: 10 },
          { attemptsBucket: 1, rowCount: 3 },
          { attemptsBucket: 2, rowCount: 1 },
          { attemptsBucket: 3, rowCount: 0 },
          { attemptsBucket: 4, rowCount: 1 },
        ],
        failureRateLast24h: 0.024,
        deadLetterCount: requeued ? 0 : 1,
        adminRequeuedLast24h: requeued ? 1 : 0,
        snapshotAt: new Date().toISOString(),
      },
    });
  });

  await page.route('**/api/admin/queue/dead-letters**', async (route) => {
    await route.fulfill({
      json: {
        rows: requeued
          ? []
          : [
              {
                jobId: fixtureJobId,
                jobType: 'TRIAGE_INCOMING',
                lastFailureReason: 'DOWNSTREAM_TIMEOUT',
                retryCount: 5,
                adminRequeueCount: 0,
                lastFailedAt: '2026-05-19T22:14:03Z',
                createdAt: '2026-05-19T20:00:00Z',
              },
            ],
        nextCursor: null,
        totalEstimate: requeued ? 0 : 1,
        hasNextPage: false,
      },
    });
  });

  await page.route(
    `**/api/admin/queue/dead-letters/${fixtureJobId}/requeue`,
    async (route) => {
      requeued = true;
      await route.fulfill({ status: 204 });
    },
  );
});

test('queue dashboard renders KPIs and re-queues a dead letter via confirm-twice', async ({
  page,
}) => {
  await page.goto('/queue');

  await expect(page.getByRole('heading', { name: 'Queue health' })).toBeVisible();
  // KpiCards are rendered with deterministic test ids.
  await expect(page.getByTestId('kpi-pending')).toBeVisible();
  await expect(page.getByTestId('kpi-dead-letter')).toContainText('1');
  await expect(page.getByTestId('kpi-failure-rate')).toContainText('2.4%');

  // AutoRefreshIndicator shows "Updated Ns ago" (polite live region).
  await expect(page.getByText(/Updated \d+s ago/)).toBeVisible();

  // Dead-letter row visible with short jobId token.
  const requeueButton = page.getByRole('button', { name: /Re-queue/ }).first();
  await expect(requeueButton).toBeVisible();
  await requeueButton.click();

  // Step 1 of ConfirmTwiceDialog: reason input.
  await page.getByLabel('Reason (recorded in audit log)').fill('transient downstream stall');
  await page.getByRole('button', { name: 'Continue' }).click();

  // Step 2: type the confirmation token = first 8 chars of jobId.
  await expect(
    page.getByLabel(`Type "${fixtureJobToken}" to confirm`),
  ).toBeVisible();
  await page.getByLabel(`Type "${fixtureJobToken}" to confirm`).fill(fixtureJobToken);
  await page.getByRole('button', { name: 'Re-queue job' }).click();

  // Backend returned 204; the dialog renders the inline audit-id confirmation.
  await expect(page.getByText(/Action recorded\. Audit row/)).toBeVisible();
});

test('queue dashboard never receives or renders a job payload', async ({ page }) => {
  await page.goto('/queue');
  await expect(page.getByRole('heading', { name: 'Queue health' })).toBeVisible();

  const rawHtml = await page.content();
  expect(rawHtml.toLowerCase()).not.toContain('payload_json');
  expect(rawHtml.toLowerCase()).not.toContain('payloadjson');
});
