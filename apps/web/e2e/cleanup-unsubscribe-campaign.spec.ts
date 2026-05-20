import { expect, test } from '@playwright/test';

import type { Page } from '@playwright/test';

import {
  createChromeMockState,
  installChromeApiMock,
  openAuthenticatedRoute,
  seedAuthenticatedSession,
} from './chrome-test-utils';

/**
 * Phase 8 — Golden path Playwright e2e for `/cleanup/unsubscribe-campaign` (UI-SPEC §Playwright
 * e2e). Wave 0 RED: the route, the candidate list, the preview dialog, the execute button, the
 * status page, and the undo button are all introduced in Wave 5b (Plan 09). Until then this spec
 * will fail with "no element found" assertions — that is the expected RED state.
 *
 * 9-step golden path (UNS-05 + UNS-06 + UNS-07):
 *   1. open `/cleanup/unsubscribe-campaign`
 *   2. assert 3 fixture candidate rows + 1 header row
 *   3. select 2 SAFE senders, counter shows `2 / 25 sender đã chọn`
 *   4. click "Xem trước campaign" → preview dialog opens
 *   5. dialog shows `2 mail sẽ archive`
 *   6. click "Execute campaign" → URL matches `/cleanup/unsubscribe-campaign/<uuid>`
 *   7. polling status reaches "Hoàn tất"
 *   8. "Undo campaign" button is visible
 *   9. navigate to `/cleanup/suppression` (cross-link surface)
 */

test.describe.configure({ mode: 'serial' });

for (const viewport of [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 320, height: 740 },
]) {
  test(`unsubscribe campaign golden path at ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const state = createChromeMockState({ preferredLanguage: 'vi' });

    await seedAuthenticatedSession(page, 'vi');
    await installChromeApiMock(page, state);
    await installUnsubscribeCampaignMock(page);

    // Step 1 — open the campaign page.
    await openAuthenticatedRoute(page, '/cleanup/unsubscribe-campaign', state);

    // Step 2 — candidate list rows (3 fixture + 1 header = 4 rows).
    await expect(page.getByRole('row')).toHaveCount(4);

    // Step 3 — select 2 SAFE senders → counter updates.
    await page.getByRole('row').nth(1).getByRole('checkbox').check();
    await page.getByRole('row').nth(2).getByRole('checkbox').check();
    await expect(page.getByText(/2 \/ 25 sender đã chọn/)).toBeVisible();

    // Step 4 — open preview dialog.
    await page.getByRole('button', { name: 'Xem trước campaign' }).click();

    // Step 5 — preview summary.
    await expect(page.getByText(/2 mail sẽ archive/)).toBeVisible();

    // Step 6 — execute campaign.
    await page.getByRole('button', { name: 'Execute campaign' }).click();
    await expect(page).toHaveURL(/\/cleanup\/unsubscribe-campaign\/[0-9a-f-]{36}/);

    // Step 7 — polling reaches "Hoàn tất".
    await expect(page.getByText('Hoàn tất')).toBeVisible({ timeout: 10_000 });

    // Step 8 — undo button visible (within 30-day window).
    await expect(page.getByRole('button', { name: 'Undo campaign' })).toBeVisible();

    // Step 9 — navigate to suppression page.
    await page.goto('/cleanup/suppression', { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(/\/cleanup\/suppression/);
  });
}

async function installUnsubscribeCampaignMock(page: Page) {
  let executeCount = 0;
  const jobId = '00000000-0000-0000-0000-000000000abc';

  await page.route(/\/api\/unsubscribe\/candidates(\?.*)?$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          senderEmail: 'a@a.test',
          senderDomain: 'a.test',
          messageCount: 5,
          lastSeenAt: '2026-05-15T00:00:00Z',
          unsubscribeMethod: 'ONE_CLICK',
          suppressed: false,
          riskBadge: 'SAFE',
        },
        {
          senderEmail: 'b@b.test',
          senderDomain: 'b.test',
          messageCount: 7,
          lastSeenAt: '2026-05-14T00:00:00Z',
          unsubscribeMethod: 'MAILTO',
          suppressed: false,
          riskBadge: 'SAFE',
        },
        {
          senderEmail: 'c@c.test',
          senderDomain: 'c.test',
          messageCount: 2,
          lastSeenAt: '2026-05-13T00:00:00Z',
          unsubscribeMethod: 'NONE',
          suppressed: false,
          riskBadge: 'NO_HEADER_DISABLED',
        },
      ]),
    });
  });

  await page.route(/\/api\/unsubscribe\/campaigns\/preview$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        campaignId: 'preview-1',
        perSender: [
          {
            senderEmail: 'a@a.test',
            unsubscribeMethod: 'ONE_CLICK',
            historyMessageCount: 1,
            willArchive: true,
            riskBadge: 'SAFE',
          },
          {
            senderEmail: 'b@b.test',
            unsubscribeMethod: 'MAILTO',
            historyMessageCount: 1,
            willArchive: true,
            riskBadge: 'SAFE',
          },
        ],
        totalArchiveCount: 2,
      }),
    });
  });

  await page.route(/\/api\/unsubscribe\/campaigns\/execute$/, async (route) => {
    executeCount += 1;
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({ jobId, status: 'QUEUED' }),
    });
  });

  await page.route(/\/api\/unsubscribe\/campaigns\/[0-9a-f-]{36}$/, async (route) => {
    const status = executeCount === 0 ? 'QUEUED' : 'COMPLETED';
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        jobId,
        status,
        progressPct: status === 'COMPLETED' ? 100 : 40,
        perSender: [
          { senderEmail: 'a@a.test', state: 'OK', archivedMessageCount: 1 },
          { senderEmail: 'b@b.test', state: 'OK', archivedMessageCount: 1 },
        ],
      }),
    });
  });

  await page.route(/\/api\/unsubscribe\/campaigns\/[0-9a-f-]{36}\/undo$/, async (route) => {
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({ status: 'UNDO_RUNNING' }),
    });
  });
}
