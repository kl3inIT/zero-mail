import { expect, test } from '@playwright/test';

import type { Page } from '@playwright/test';

import {
  createChromeMockState,
  installChromeApiMock,
  seedAuthenticatedSession,
} from './chrome-test-utils';

/**
 * Phase 8 — Safe list e2e. The old `/cleanup/suppression` screen is folded into the
 * `/cleanup/unsubscribe-campaign` flow as an in-page dialog.
 *
 * Two scenarios (UNS-02 frontend half):
 *   - manual add: user opens "Danh sách an toàn", types `boss@example.com`, clicks
 *     "Thêm vào danh sách an toàn" → row appears with badge "Thủ công" → candidate page
 *     no longer lists the sender
 *   - auto-add visibility: a suppression entry with `source='replied'` displays the "Đã trả lời"
 *     badge
 */

test.describe.configure({ mode: 'serial' });

test('addManualSuppressionEntry_excludesSenderFromCandidates', async ({ page }) => {
  const state = createChromeMockState({ preferredLanguage: 'vi' });

  await seedAuthenticatedSession(page, state.preferredLanguage);
  await installChromeApiMock(page, state);
  await installSuppressionMock(page, { initialEntries: [] });
  await installCandidatesMockWithEmail(page, 'boss@example.com');
  await page.goto('/cleanup/unsubscribe-campaign', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Danh sách an toàn' })).toBeVisible();
  await expect(page.getByRole('row')).toHaveCount(2);

  await page.getByRole('button', { name: 'Danh sách an toàn' }).click();
  await page.getByLabel('Email người gửi').fill('boss@example.com');
  await page.getByRole('button', { name: 'Thêm vào danh sách an toàn' }).click();

  await expect(page.getByRole('dialog')).toContainText('boss@example.com');
  await expect(page.getByRole('dialog')).toContainText('Thủ công');

  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeHidden();
  await expect(page.getByText('boss@example.com')).toHaveCount(0);
});

test('autoAddedSenderShowsRepliedBadge', async ({ page }) => {
  const state = createChromeMockState({ preferredLanguage: 'vi' });

  await seedAuthenticatedSession(page, state.preferredLanguage);
  await installChromeApiMock(page, state);
  await installSuppressionMock(page, {
    initialEntries: [
      {
        id: '00000000-0000-0000-0000-000000000002',
        senderEmail: 'replied@example.com',
        senderDomain: null,
        reason: 'replied',
      },
    ],
  });
  await installCandidatesMockWithEmail(page, 'replied@example.com');
  await page.goto('/cleanup/unsubscribe-campaign', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Danh sách an toàn' })).toBeVisible();
  await expect(page.getByRole('row')).toHaveCount(2);

  await page.getByRole('button', { name: 'Danh sách an toàn' }).click();
  await expect(page.getByRole('dialog')).toContainText('replied@example.com');
  await expect(page.getByRole('dialog')).toContainText('Đã trả lời');
});

type SuppressionEntry = {
  id: string;
  senderEmail: string | null;
  senderDomain: string | null;
  reason: 'manual' | 'replied' | 'auto';
};

async function installSuppressionMock(page: Page, options: { initialEntries: SuppressionEntry[] }) {
  let entries: SuppressionEntry[] = [...options.initialEntries];

  await page.route(/\/api\/cleanup\/suppression$/, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(entries),
      });
      return;
    }
    if (route.request().method() === 'POST') {
      // Backend POST contract: { senderEmailOrDomain: string } (CONVENTIONS — derived from
      // OpenAPI schema SuppressionAddRequest). The fixture extracts whether the value is an
      // email or a domain by checking for `@`.
      const payload = route.request().postDataJSON() as { senderEmailOrDomain?: string };
      const raw = payload.senderEmailOrDomain ?? '';
      const isEmail = raw.includes('@');
      const newEntry: SuppressionEntry = {
        id: `00000000-0000-0000-0000-${String(entries.length + 1).padStart(12, '0')}`,
        senderEmail: isEmail ? raw : null,
        senderDomain: isEmail ? null : raw,
        reason: 'manual',
      };
      entries = [...entries, newEntry];
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(newEntry),
      });
      return;
    }
    await route.fallback();
  });
}

async function installCandidatesMockWithEmail(page: Page, suppressedEmail: string) {
  await page.route(/\/api\/unsubscribe\/candidates(\?.*)?$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        // Note: suppressedEmail is intentionally NOT included — backend has filtered it out.
        {
          senderEmail: 'unrelated@other.test',
          senderDomain: 'other.test',
          messageCount: 1,
          lastSeenAt: '2026-05-15T00:00:00Z',
          unsubscribeMethod: 'ONE_CLICK',
          suppressed: false,
          riskBadge: 'SAFE',
          excluded: suppressedEmail,
        },
      ]),
    });
  });
}
