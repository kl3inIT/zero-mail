import { expect, test } from '@playwright/test';

import { chatDetail, chatSummary, openChat, toolMessage } from './chat-test-utils';

test.setTimeout(90_000);

test('renders confirmed send cards from history without re-executing confirm', async ({ page }) => {
  const chatId = 'chat-sent';
  const state = await openChat(page, `/chat?chat=${chatId}`, {
    locale: 'vi',
    chats: [chatSummary(chatId, 'Đã gửi thư', 1)],
    details: {
      [chatId]: chatDetail(chatId, [
        toolMessage(
          'sendEmail',
          {
            to: 'founder@example.com',
            subject: 'Follow up',
            body: 'Thanks for your note.',
          },
          { state: 'SENT', output: { gmailMessageId: 'gmail-1' } },
        ),
      ]),
    },
  });

  const previewCard = page.getByTestId('preview-card-sendEmail');
  await expect(previewCard).toBeVisible();
  await expect(previewCard.getByText('Đã gửi')).toBeVisible();
  await expect(previewCard.getByTestId('preview-send')).toHaveCount(0);
  expect(state.confirmRequests).toHaveLength(0);
});
