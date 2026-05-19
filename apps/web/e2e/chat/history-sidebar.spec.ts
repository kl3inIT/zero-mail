import { expect, test } from '@playwright/test';

import { chatDetail, chatSummary, openChat } from './chat-test-utils';

test.setTimeout(90_000);

test('lists, opens, and soft-deletes chats without rename or search controls', async ({ page }) => {
  const chats = [
    chatSummary('chat-1', 'Tin nhắn đầu', 2),
    chatSummary('chat-2', 'VIP reply', 1),
    chatSummary('chat-3', 'Rule cleanup', 4),
  ];
  const state = await openChat(page, '/chat', {
    locale: 'vi',
    chats,
    details: Object.fromEntries(chats.map((chat) => [chat.id, chatDetail(chat.id, [])])),
  });

  await expect(page.getByText('Tin nhắn đầu')).toBeVisible();
  await page.getByText('VIP reply').click();
  await expect(page).toHaveURL(/chat=chat-2/);

  await page
    .getByTestId('chat-history-row-chat-2')
    .getByRole('button', { name: 'Thao tác trò chuyện' })
    .click();
  await page.getByTestId('chat-delete').click();
  await expect.poll(() => state.deletedChatIds).toContain('chat-2');

  await page.reload();
  await expect(page.getByText('Tin nhắn đầu')).toBeVisible();
  await expect(page.getByText('VIP reply')).toHaveCount(0);
  await expect(page.getByTestId('chat-rename')).toHaveCount(0);
  await expect(page.locator('input[type="search"]')).toHaveCount(0);
});
