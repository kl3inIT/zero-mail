import { expect, test } from '@playwright/test';

import {
  createChromeMockState,
  expectNoHorizontalOverflow,
  installChromeApiMock,
  seedAuthenticatedSession,
} from './chrome-test-utils';

test.describe.configure({ mode: 'serial' });

test('needs-reply golden path saves a Gmail draft for review', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 820 });
  await seedAuthenticatedSession(page);
  await installChromeApiMock(page, createChromeMockState());
  await page.goto('/needs-reply', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');

  await expect(page.getByRole('tab', { name: /To reply/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /Awaiting reply/ })).toBeVisible();

  await page.getByTestId('needs-reply-row').first().click();
  await expect(page.getByTestId('needs-reply-reader')).toBeVisible();
  await page.getByRole('button', { name: 'Draft reply' }).first().click();

  await expect(page.getByText('Draft saved in Gmail — review and send it there.')).toBeVisible();
  await expect(page.getByTestId('needs-reply-row').getByText('AI drafted')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('needs-reply mobile opens a selected thread as a detail view', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 });
  await seedAuthenticatedSession(page);
  await installChromeApiMock(page, createChromeMockState());
  await page.goto('/needs-reply', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');

  const firstRow = page.getByTestId('needs-reply-row').first();
  await expect(firstRow).toBeVisible();
  await expect(page.getByTestId('needs-reply-reader')).toBeHidden();

  await firstRow.click();
  await expect(page.getByTestId('needs-reply-reader')).toBeVisible();
  await expect(firstRow).toBeHidden();

  await page.getByTestId('needs-reply-reader-back').click();
  await expect(firstRow).toBeVisible();
  await expectNoHorizontalOverflow(page);
});
