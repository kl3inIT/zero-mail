import { expect, test } from '@playwright/test';

import { openChat } from './chat-test-utils';

test.setTimeout(90_000);

test('uses Vietnamese chrome by default and flips to English by locale cookie', async ({
  page,
}) => {
  await openChat(page, '/chat', { locale: 'vi' });
  await expect(page.getByRole('heading', { name: 'Trợ lý email' })).toBeVisible();
  await expect(page.getByPlaceholder('Nhắn cho Zero Mail...')).toBeVisible();

  await openChat(page, '/chat', { locale: 'en' });
  await expect(page.getByRole('heading', { name: 'Email assistant' })).toBeVisible();
  await expect(page.getByPlaceholder('Message Zero Mail...')).toBeVisible();
});
