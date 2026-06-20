import { expect, test } from '@playwright/test';

import {
  MAILBOX_A_ID,
  MAILBOX_B_ID,
  createMailboxMockState,
  openMailboxRoute,
} from './mailbox-test-utils';

test.describe('mailbox-owned rules', () => {
  test('shows active-mailbox rules and copies rules disabled into the active mailbox', async ({
    page,
  }) => {
    const state = createMailboxMockState({ activeMailboxId: MAILBOX_B_ID });
    await page.setViewportSize({ width: 1280, height: 820 });
    await openMailboxRoute(page, '/rules', state);

    await expect(page.getByTestId('active-mailbox-scope')).toHaveCount(0);
    await expect(page.getByText('Handle support escalations').first()).toBeVisible();
    await expect(page.getByText('Archive founder receipts')).toHaveCount(0);

    await page.getByTestId('copy-rules-button').click();
    await expect(
      page.getByRole('heading', { name: 'Copy rules from another mailbox' }),
    ).toBeVisible();
    const copyDialog = page.getByRole('dialog');
    await expect(copyDialog.getByText('Founder Gmail').first()).toBeVisible();
    await expect(copyDialog.getByText('Active target mailbox')).toBeVisible();
    await expect(copyDialog.getByText('support@example.com')).toBeVisible();

    await page.getByRole('button', { name: 'Copy' }).click();

    await expect(page.getByText('Copied archive receipts').first()).toBeVisible();
    expect(state.copyRequests).toEqual([
      {
        sourceGmailConnectionId: MAILBOX_A_ID,
        targetGmailConnectionId: MAILBOX_B_ID,
      },
    ]);
    expect(
      state.rulesByMailbox[MAILBOX_B_ID].find(
        (rule) => rule.ruleId === 'rule-copied-archive-receipts',
      )?.enabled,
    ).toBe(false);
    expect(state.rulesRequests).toContain(MAILBOX_B_ID);
  });
});
