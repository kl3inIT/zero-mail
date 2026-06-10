import { expect, test } from '@playwright/test';

import {
  MAILBOX_B_ID,
  createMailboxMockState,
  expectNoHorizontalOverflow,
  openAccountMenu,
  openMailboxRoute,
} from './mailbox-test-utils';

test.describe('mailbox switcher', () => {
  test('lists connected mailboxes and refetches the inbox after switching', async ({ page }) => {
    const state = createMailboxMockState();
    await page.setViewportSize({ width: 1280, height: 820 });
    await openMailboxRoute(page, '/inbox', state);

    await expect(page.getByTestId('active-mailbox-scope').first()).toContainText('Founder Gmail');
    await expect(page.getByText('Alpha investor update')).toBeVisible();

    await openAccountMenu(page);
    const accountMenu = page.getByRole('menu');
    await expect(accountMenu.getByText('Accounts')).toBeVisible();
    await expect(accountMenu.getByText('Founder Gmail')).toBeVisible();
    await expect(accountMenu.getByText('Support Gmail')).toBeVisible();
    await expect(accountMenu.getByText('Primary')).toBeVisible();
    await expect(page.getByTestId('mailbox-add-gmail')).toBeVisible();
    await expect(page.getByTestId(`mailbox-switch-${MAILBOX_B_ID}`)).toContainText('Switch');

    await page.getByTestId(`mailbox-switch-${MAILBOX_B_ID}`).click();

    await expect(page.getByTestId('active-mailbox-scope').first()).toContainText('Support Gmail');
    await expect(page.getByText('Beta support ticket')).toBeVisible();
    await expect(page.getByText('Alpha investor update')).toHaveCount(0);
    expect(state.setActiveRequests).toEqual([MAILBOX_B_ID]);
    expect(state.inboxRequests).toContain(MAILBOX_B_ID);

    await openAccountMenu(page);
    await expect(
      page.getByTestId(`mailbox-switch-${MAILBOX_B_ID}`).getByTestId('mailbox-active-marker'),
    ).toBeVisible();
  });

  test('keeps the switcher reachable at 320px and refetches needs-reply scope', async ({
    page,
  }) => {
    const state = createMailboxMockState();
    await page.setViewportSize({ width: 320, height: 740 });
    await openMailboxRoute(page, '/needs-reply', state);

    await expect(page.getByText('Founder reply needed')).toBeVisible();
    await openAccountMenu(page);
    await expect(page.getByTestId(`mailbox-switch-${MAILBOX_B_ID}`)).toBeVisible();
    await page.getByTestId(`mailbox-switch-${MAILBOX_B_ID}`).click();

    await expect(page.getByTestId('active-mailbox-scope').first()).toContainText('Support Gmail');
    await expect(page.getByText('Support reply needed')).toBeVisible();
    await expect(page.getByText('Founder reply needed')).toHaveCount(0);
    expect(state.needsReplyRequests).toContain(MAILBOX_B_ID);
    await expectNoHorizontalOverflow(page);
  });
});
