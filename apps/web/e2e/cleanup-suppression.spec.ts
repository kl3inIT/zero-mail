import { expect, test } from '@playwright/test';

import type { Page } from '@playwright/test';

import {
  createChromeMockState,
  installChromeApiMock,
  openAuthenticatedRoute,
  seedAuthenticatedSession,
} from './chrome-test-utils';

/**
 * Phase 8 — Suppression list e2e (`/cleanup/suppression`). Wave 0 RED: the route + the page UI
 * + the suppression CRUD endpoints are introduced in Wave 5b (Plan 09). Until then this spec
 * fails with "no element found" — expected RED state.
 *
 * Two scenarios (UNS-02 frontend half):
 *   - manual add: user types `boss@example.com` + clicks "Thêm vào suppression" → row appears
 *     with badge "Thủ công" → candidate page no longer lists the sender
 *   - auto-add visibility: a suppression entry with `source='replied'` displays the "Đã reply"
 *     badge
 */

test.describe.configure({ mode: 'serial' });

test('addManualSuppressionEntry_excludesSenderFromCandidates', async ({ page }) => {
  const state = createChromeMockState({ preferredLanguage: 'vi' });
  await seedAuthenticatedSession(page, 'vi');
  await installChromeApiMock(page, state);
  await installSuppressionMock(page, { initialEntries: [] });
  await installCandidatesMockWithEmail(page, 'boss@example.com');

  await openAuthenticatedRoute(page, '/cleanup/suppression', state);

  await page.getByLabel('Email người gửi').fill('boss@example.com');
  await page.getByRole('button', { name: 'Thêm vào suppression' }).click();

  await expect(page.getByText('boss@example.com')).toBeVisible();
  await expect(page.getByText('Thủ công')).toBeVisible();

  await page.goto('/cleanup/unsubscribe-campaign', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('boss@example.com')).toHaveCount(0);
});

test('autoAddedSenderShowsRepliedBadge', async ({ page }) => {
  const state = createChromeMockState({ preferredLanguage: 'vi' });
  await seedAuthenticatedSession(page, 'vi');
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

  await openAuthenticatedRoute(page, '/cleanup/suppression', state);

  await expect(page.getByText('replied@example.com')).toBeVisible();
  await expect(page.getByText('Đã reply')).toBeVisible();
});

type SuppressionEntry = {
  id: string;
  senderEmail: string | null;
  senderDomain: string | null;
  reason: 'manual' | 'replied' | 'auto';
};

async function installSuppressionMock(page: Page, options: { initialEntries: SuppressionEntry[] }) {
  let entries: SuppressionEntry[] = [...options.initialEntries];

  await page.route(/\/api\/unsubscribe\/suppression$/, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(entries),
      });
      return;
    }
    if (route.request().method() === 'POST') {
      const payload = route.request().postDataJSON() as Partial<SuppressionEntry>;
      const newEntry: SuppressionEntry = {
        id: `00000000-0000-0000-0000-${String(entries.length + 1).padStart(12, '0')}`,
        senderEmail: payload.senderEmail ?? null,
        senderDomain: payload.senderDomain ?? null,
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
