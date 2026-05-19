import { expect, test } from '@playwright/test';

import { chatDetail, chatSummary, openChat, toolMessage } from './chat-test-utils';

test.setTimeout(90_000);

test('marks AI-added recipients outside the source thread', async ({ page }) => {
  const chatId = 'chat-outside-source';
  await openChat(page, `/chat?chat=${chatId}`, {
    locale: 'en',
    chats: [chatSummary(chatId, 'Reply recipients', 1)],
    details: {
      [chatId]: chatDetail(chatId, [
        toolMessage('replyEmail', {
          gmailThreadId: 'thread-1',
          toRecipients: [
            { email: 'bob@example.com', outsideSourceThread: false },
            { email: 'charlie@example.com', outsideSourceThread: true },
          ],
          body: 'Looping in Charlie for context.',
        }),
      ]),
    },
  });

  await expect(page.getByText('bob@example.com')).toBeVisible();
  const charlieRow = page.getByText('charlie@example.com').locator('..');
  await expect(charlieRow.getByText('Added by AI · verify')).toBeVisible();
});
