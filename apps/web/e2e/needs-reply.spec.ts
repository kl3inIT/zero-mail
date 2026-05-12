import { expect, test } from '@playwright/test';

import {
  createChromeMockState,
  expectNoHorizontalOverflow,
  installChromeApiMock,
  seedAuthenticatedSession,
} from './chrome-test-utils';

test.describe.configure({ mode: 'serial' });

test.fixme('needs-reply golden path saves a Gmail draft for review', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 820 });
  await seedAuthenticatedSession(page);
  await installChromeApiMock(page, createChromeMockState());
  await page.goto('/needs-reply', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle');

  await expect(page.getByRole('heading', { name: 'Needs reply' })).toBeVisible();
  await expect(page.getByRole('tab', { name: /To reply/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /Awaiting reply/ })).toBeVisible();

  await page.getByRole('button', { name: 'Draft reply' }).first().click();

  await expect(page.getByText('Draft saved in Gmail — review and send it there.')).toBeVisible();
  await expect(page.getByText('Draft ready')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});
