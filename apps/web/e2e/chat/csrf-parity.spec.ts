import { expect, test } from '@playwright/test';

import { installChatApiMock, rawChatPost, rawConfirmPost } from './chat-test-utils';

test.setTimeout(90_000);

test('requires the same XSRF header on chat stream and confirm POSTs', async ({ page }) => {
  await installChatApiMock(page);
  await page.context().addCookies([
    {
      name: 'XSRF-TOKEN',
      value: 'playwright-xsrf',
      domain: 'localhost',
      path: '/',
      sameSite: 'Lax',
      secure: false,
    },
  ]);
  await page.goto('/chat', { waitUntil: 'domcontentloaded' });

  await expect(await rawChatPost(page, true)).toBe(200);
  await expect(await rawChatPost(page, false)).toBe(403);
  await expect(await rawConfirmPost(page, true)).toBe(200);
  await expect(await rawConfirmPost(page, false)).toBe(403);
});
