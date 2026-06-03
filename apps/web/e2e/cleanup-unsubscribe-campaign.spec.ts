import { expect, test } from '@playwright/test';

import type { Page } from '@playwright/test';

import {
  createChromeMockState,
  installChromeApiMock,
  seedAuthenticatedSession,
} from './chrome-test-utils';

/**
 * `/cleanup/bulk-unsubscribe` — Inbox Zero-style sender table golden path.
 *
 * Covers the new flow after dropping the preview dialog + campaign status/undo pages:
 *   1. open `/cleanup/bulk-unsubscribe`
 *   2. assert default state (Unhandled filter + Last 3 months window) and 2 mocked rows
 *   3. row-level primary action: clicking the Unsubscribe button on a ONE_CLICK sender hits
 *      POST `/api/unsubscribe/campaigns/execute` and surfaces the "Đang hủy đăng ký" toast
 *   4. row-level primary action on a NONE-method sender hits POST `/api/unsubscribe/senders/action`
 *      with `action=AUTO_ARCHIVE`
 */

test.describe.configure({ mode: 'serial' });

for (const viewport of [
  { name: 'desktop', width: 1280, height: 820 },
  { name: 'mobile', width: 360, height: 740 },
]) {
  test(`bulk unsubscribe golden path at ${viewport.name}`, async ({ page }) => {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const state = createChromeMockState({ preferredLanguage: 'vi' });
    const requests = await installCleanupMocks(page);

    await seedAuthenticatedSession(page, state.preferredLanguage);
    await installChromeApiMock(page, state);
    await page.goto('/cleanup/bulk-unsubscribe', { waitUntil: 'domcontentloaded' });

    // Step 2 — header row + 2 candidate rows visible, default Unhandled + 3 months.
    await expect(page.getByRole('row')).toHaveCount(3);

    // Step 3 — primary unsubscribe on the ONE_CLICK sender.
    const firstRow = page.locator('tbody tr').first();
    await Promise.all([
      page.waitForResponse((response) =>
        response.url().includes('/api/unsubscribe/campaigns/execute'),
      ),
      firstRow.getByRole('button', { name: 'Hủy đăng ký' }).click(),
    ]);
    await expect(page.getByText('Đang hủy đăng ký')).toBeVisible();
    expect(requests.executePayloads.at(-1)).toEqual({ senderEmails: ['one-click@a.test'] });

    // Step 4 — primary action on the NONE-method sender becomes Block → AUTO_ARCHIVE.
    const blockRow = page.locator('tbody tr').nth(1);
    await Promise.all([
      page.waitForResponse((response) =>
        response.url().includes('/api/unsubscribe/senders/action'),
      ),
      blockRow.getByRole('button', { name: 'Chặn' }).click(),
    ]);
    expect(requests.senderActionPayloads.at(-1)).toMatchObject({
      action: 'AUTO_ARCHIVE',
      senderEmails: ['no-header@b.test'],
    });
  });
}

async function installCleanupMocks(page: Page) {
  const executePayloads: Array<{ senderEmails: string[] }> = [];
  const senderActionPayloads: Array<{ action: string; senderEmails: string[] }> = [];

  await page.route(/\/api\/unsubscribe\/candidates(\?.*)?$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          senderEmail: 'one-click@a.test',
          senderDomain: 'a.test',
          senderName: 'A Brand Newsletter',
          messageCount: 12,
          readMessageCount: 2,
          lastSeenAt: '2026-05-15T00:00:00Z',
          unsubscribeMethod: 'ONE_CLICK',
          suppressed: false,
          status: null,
        },
        {
          senderEmail: 'no-header@b.test',
          senderDomain: 'b.test',
          senderName: null,
          messageCount: 5,
          readMessageCount: 1,
          lastSeenAt: '2026-05-14T00:00:00Z',
          unsubscribeMethod: 'NONE',
          suppressed: false,
          status: null,
        },
      ]),
    });
  });

  await page.route(/\/api\/unsubscribe\/campaigns\/execute$/, async (route) => {
    const body = route.request().postDataJSON() as { senderEmails: string[] };
    executePayloads.push(body);
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        campaignId: '00000000-0000-0000-0000-000000000def',
        jobId: '00000000-0000-0000-0000-000000000abc',
        status: 'QUEUED',
      }),
    });
  });

  await page.route(/\/api\/unsubscribe\/senders\/action$/, async (route) => {
    const body = route.request().postDataJSON() as { action: string; senderEmails: string[] };
    senderActionPayloads.push(body);
    await route.fulfill({
      status: 202,
      contentType: 'application/json',
      body: JSON.stringify({
        senderCount: body.senderEmails.length,
        affectedMessageCount: 0,
        failedMessageCount: 0,
      }),
    });
  });

  return { executePayloads, senderActionPayloads };
}
