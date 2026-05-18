import { expect, test } from '@playwright/test';

import { chatDetail, chatSummary, openChat, toolMessage } from './chat-test-utils';

test.setTimeout(90_000);

test('requires VIP acknowledgement before confirming a send tool', async ({ page }) => {
  const chatId = 'chat-vip';
  const state = await openChat(page, `/chat?chat=${chatId}`, {
    locale: 'vi',
    chats: [chatSummary(chatId, 'VIP recipient', 1)],
    details: {
      [chatId]: chatDetail(chatId, [
        toolMessage('sendEmail', {
          to: 'vip@example.com',
          subject: 'Sensitive update',
          body: 'Please review this before sending.',
          vipRequired: true,
        }),
      ]),
    },
  });

  await expect(page.getByText('Người nhận có trong danh sách an toàn')).toBeVisible();
  const sendButton = page.getByTestId('preview-card-sendEmail').getByTestId('preview-send');
  await expect(sendButton).toBeDisabled();

  await page.getByRole('checkbox', { name: 'Tôi đã xem kỹ và muốn gửi' }).click();
  await expect(sendButton).toBeEnabled();
  await sendButton.click();

  await expect(page.getByText('Đã gửi')).toBeVisible();
  expect(state.confirmRequests[0]?.body).toMatchObject({ vipAcknowledged: true });
});
