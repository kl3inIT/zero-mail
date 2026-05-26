import { expect, test } from '@playwright/test';

import { openChat } from './chat-test-utils';

test.setTimeout(90_000);

test('streams assistant text from /api/chat with cookie auth and XSRF', async ({ page }) => {
  const state = await openChat(page, '/chat', {
    locale: 'vi',
    streamChunks: ['Xin chao ', 'tu Zero Mail.'],
  });

  const prompt = page.getByPlaceholder('Nhắn cho Zero Mail...');
  await prompt.click();
  await prompt.pressSequentially('Tìm email từ Acme tuần này');
  await expect(prompt).toHaveValue('Tìm email từ Acme tuần này');
  await expect(page.getByRole('button', { name: 'Gửi tin nhắn' })).toBeEnabled();
  await page.getByRole('button', { name: 'Gửi tin nhắn' }).click();

  await expect(page.getByText('Xin chao tu Zero Mail.')).toBeVisible();
  expect(state.chatRequests).toHaveLength(1);
  expect(state.chatRequests[0]?.headers['x-xsrf-token']).toBe('playwright-xsrf');
  expect(state.chatRequests[0]?.body).toMatchObject({
    userText: 'Tìm email từ Acme tuần này',
  });
});
