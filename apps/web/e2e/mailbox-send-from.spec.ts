import { expect, test } from '@playwright/test';

import { MAILBOX_B_ID, createMailboxMockState, openMailboxRoute } from './mailbox-test-utils';

test.describe('mailbox send-from provenance', () => {
  test('shows the executing mailbox around draft generation and audit history', async ({
    page,
  }) => {
    const state = createMailboxMockState({ activeMailboxId: MAILBOX_B_ID });
    await page.setViewportSize({ width: 1280, height: 820 });
    await openMailboxRoute(page, '/needs-reply', state);

    await expect(page.getByTestId('active-mailbox-scope').first()).toContainText('Support Gmail');
    await page.getByTestId('needs-reply-row').first().click();

    const reader = page.getByTestId('needs-reply-reader');
    await expect(reader).toBeVisible();
    await expect(reader.getByTestId('active-mailbox-scope')).toContainText('Support Gmail');
    await reader.getByRole('button', { name: 'Draft reply' }).click();

    await expect(page.getByTestId('needs-reply-draft-section')).toContainText(
      'Draft from support@example.com for the support mailbox.',
    );
    expect(state.draftRequests).toEqual([
      { gmailThreadId: 'thread-b', executingMailboxId: MAILBOX_B_ID },
    ]);

    await page.goto('/rules?tab=history', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('load');
    await expect(page.getByTestId('active-mailbox-scope').first()).toContainText('Support Gmail');
    await expect(page.getByTestId('audit-mailbox-provenance')).toContainText(
      'Source: support@example.com',
    );
    await expect(page.getByTestId('audit-mailbox-provenance')).toContainText(
      'Executing: support@example.com',
    );
    expect(state.auditRequests).toContain(MAILBOX_B_ID);
  });
});
