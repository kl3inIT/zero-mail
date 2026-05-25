import { expect, test } from '@playwright/test';

import type { Page } from '@playwright/test';

import {
  createChromeMockState,
  installChromeApiMock,
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
 *   2. assert 2 ready fixture candidate rows + 1 header row
 *   3. click the header checkbox to select 2 SAFE senders, counter shows `2 / 25 người gửi đã chọn`
 *   4. click "Xem trước" → preview dialog opens
 *   5. dialog shows `Email sẽ lưu trữ nếu thành công: 2`
 *   6. click "Hủy đăng ký" → URL matches `/cleanup/unsubscribe-campaign/<uuid>`
 *   7. polling status reaches "Hoàn tất"
 *   8. "Hoàn tác lưu trữ" button is visible
 *   9. open "Danh sách an toàn" as an in-page dialog
 */

test.describe.configure({ mode: 'serial' });

for (const viewport of [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 320, height: 740 },
]) {
  test(`unsubscribe campaign golden path at ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const state = createChromeMockState({ preferredLanguage: 'vi' });

    // Step 1 — install route mocks before first navigation so React Query never caches the
    // generic 204 fallback for cleanup endpoints.
    await seedAuthenticatedSession(page, state.preferredLanguage);
    await installChromeApiMock(page, state);
    await installUnsubscribeCampaignMock(page);
    await installSuppressionDialogMock(page);
    await page.goto('/cleanup/unsubscribe-campaign', { waitUntil: 'domcontentloaded' });

    // Step 2 — ready candidate rows (2 fixture + 1 header = 3 rows).
    await expect(page.getByRole('row')).toHaveCount(3);
    await expect(page.getByRole('combobox', { name: 'Lọc người gửi' })).toContainText(
      'Có thể hủy nhận',
    );
    await expect(page.getByRole('combobox', { name: 'Sắp xếp người gửi' })).toContainText(
      'Nhiều email nhất',
    );
    await page.getByRole('combobox', { name: 'Lọc người gửi' }).click();
    await expect(page.getByRole('option', { name: 'Có thể hủy nhận' })).toBeVisible();
    await expect(page.getByRole('option', { name: 'Hủy nhận an toàn' })).toBeVisible();
    await expect(page.getByRole('option', { name: 'Gửi email hủy nhận' })).toBeVisible();
    await page.keyboard.press('Escape');

    // Step 3 — select all visible SAFE senders via the header checkbox → counter updates.
    await page.getByRole('checkbox', { name: 'Chọn tất cả người gửi đang hiển thị' }).check();
    await expect(page.getByText(/2 \/ 25 người gửi đã chọn/)).toBeVisible();

    // Step 4 — open preview dialog.
    await page.getByRole('button', { name: 'Xem trước', exact: true }).click();

    // Step 5 — preview summary.
    await expect(page.getByText('Email sẽ lưu trữ nếu thành công: 2')).toBeVisible();

    // Step 6 — execute campaign. The mutation onSuccess in useExecuteCampaign navigates to
    // `/cleanup/unsubscribe-campaign/{jobId}` via router.push after the POST resolves; wait
    // for the POST to round-trip first, then for the URL change.
    await Promise.all([
      page.waitForResponse((response) =>
        response.url().includes('/api/unsubscribe/campaigns/execute'),
      ),
      page.getByRole('button', { name: 'Hủy đăng ký' }).click(),
    ]);
    await expect(page).toHaveURL(/\/cleanup\/unsubscribe-campaign\/[0-9a-f-]{36}/, {
      timeout: 30_000,
    });

    // Step 7 — polling reaches "Hoàn tất" (exact match on the status label paragraph; the
    // undo banner separately contains completion copy which would match a substring).
    await expect(page.getByText('Hoàn tất', { exact: true })).toBeVisible({ timeout: 10_000 });

    // Step 8 — undo button visible (within 30-day window).
    await expect(page.getByRole('button', { name: 'Hoàn tác lưu trữ' })).toBeVisible();

    // Step 9 — safe list is now an in-page dialog, not a sidebar submenu route.
    await page.goto('/cleanup/unsubscribe-campaign', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('row')).toHaveCount(3);
    await page.getByRole('button', { name: 'Danh sách an toàn' }).click();
    await expect(page.getByRole('dialog')).toContainText('Danh sách an toàn');
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
        totalHistoryCount: 2,
      }),
    });
  });

  await page.route(/\/api\/unsubscribe\/campaigns\/execute$/, async (route) => {
    executeCount += 1;
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        campaignId: '00000000-0000-0000-0000-000000000def',
        jobId,
        status: 'QUEUED',
      }),
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

async function installSuppressionDialogMock(page: Page) {
  await page.route(/\/api\/cleanup\/suppression$/, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
      });
      return;
    }
    await route.fallback();
  });
}
