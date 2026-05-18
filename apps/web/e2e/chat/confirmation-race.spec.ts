import { expect, test } from '@playwright/test';

import { chatDetail, chatSummary, openChat, toolMessage } from './chat-test-utils';

test.setTimeout(90_000);

test('double-clicking Send posts one confirmation request', async ({ page }) => {
  const chatId = 'chat-race';
  const state = await openChat(page, `/chat?chat=${chatId}`, {
    locale: 'vi',
    confirmDelayMs: 250,
    chats: [chatSummary(chatId, 'Soạn thư', 1)],
    details: {
      [chatId]: chatDetail(chatId, [
        toolMessage('sendEmail', {
          to: 'founder@example.com',
          subject: 'Hello',
          body: 'Body text',
        }),
      ]),
    },
  });

  const sendButton = page.getByTestId('preview-card-sendEmail').getByTestId('preview-send');
  await expect(sendButton).toBeEnabled();
  await sendButton.dblclick();
  await expect(page.getByText('Đã gửi')).toBeVisible();
  expect(state.confirmRequests).toHaveLength(1);
});
