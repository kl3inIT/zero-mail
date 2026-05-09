import { expect, test, type Page } from '@playwright/test';

async function mockRulesApis(page: Page) {
  const rules = [
    {
      id: 'rule-receipts',
      name: 'Archive Stripe receipts',
      enabled: false,
      order: 1,
      templateKey: 'archive-receipts',
      templateVersion: 1,
      customized: false,
      lastPreviewStatus: null,
    },
    {
      id: 'rule-newsletters',
      name: 'Label newsletters',
      enabled: true,
      order: 2,
      templateKey: 'label-newsletters',
      templateVersion: 1,
      customized: false,
      lastPreviewStatus: 'success',
    },
  ];

  await page.route('http://localhost:8080/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (url.pathname === '/me') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          userId: 'user-1',
          tenantId: 'tenant-1',
          email: 'founder@example.com',
          preferredLanguage: 'en',
          onboardingStep: 'complete',
          triagePaused: false,
          gmailConnectionStatus: {
            status: 'CONNECTED',
            ingestionHealth: 'HEALTHY',
            googleEmail: 'founder@example.com',
          },
        }),
      });
      return;
    }

    if (url.pathname === '/api/rules' && request.method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ rules }),
      });
      return;
    }

    if (url.pathname === '/api/rules/compile' && request.method() === 'POST') {
      const payload = request.postDataJSON();
      expect(payload.sourceText).toContain('Stripe');
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          displayName: 'Archive Stripe receipts',
          matcherSummary: ['Sender domain is stripe.com', 'Subject contains receipt'],
          actionSummary: ['archive', 'label Finance'],
          clarification: null,
        }),
      });
      return;
    }

    if (url.pathname === '/api/rules' && request.method() === 'POST') {
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({ id: 'rule-new', enabled: false, version: 1 }),
      });
      return;
    }

    if (url.pathname === '/api/rules/rule-new/preview' && request.method() === 'POST') {
      const payload = request.postDataJSON();
      expect([10, 25, 50]).toContain(payload.sampleSize);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          sampleSize: payload.sampleSize,
          matchedCount: 2,
          deferredCount: 0,
          safeNotice: 'No Gmail changes were made.',
          actions: [{ type: 'archive', count: 2 }],
          rows: [
            {
              id: 'message-1',
              sender: 'billing@stripe.com',
              subject: 'Your Stripe receipt',
              date: '2026-05-09',
              labels: ['INBOX'],
              evidence: ['Sender domain is stripe.com'],
              proposedActions: ['archive'],
            },
          ],
        }),
      });
      return;
    }

    if (url.pathname.endsWith('/enable') || url.pathname.endsWith('/disable')) {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
      return;
    }

    if (url.pathname === '/api/rules/reorder' && request.method() === 'POST') {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
      return;
    }

    if (url.pathname === '/api/rules/rule-new' && request.method() === 'PATCH') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ id: 'rule-new', version: 2, enabled: false }),
      });
      return;
    }

    if (url.pathname === '/api/rules/rule-new' && request.method() === 'DELETE') {
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    await route.fulfill({ status: 204, body: '' });
  });
}

async function openRules(page: Page) {
  await page.context().addCookies([
    {
      name: 'ZEROMAIL_SESSION',
      value: 'playwright-session',
      domain: 'localhost',
      path: '/',
      httpOnly: true,
      sameSite: 'Lax',
      secure: false,
    },
    {
      name: 'NEXT_LOCALE',
      value: 'en',
      domain: 'localhost',
      path: '/',
      sameSite: 'Lax',
      secure: false,
    },
  ]);
  await mockRulesApis(page);
  await page.goto('/rules', { waitUntil: 'domcontentloaded' });
}

test.describe.skip('Plan 03-08 lands the interactive rules workspace', () => {
  test('rules golden path creates, compiles, saves disabled, previews, toggles, reorders, edits, and deletes', async ({
    page,
  }) => {
    await openRules(page);

    await expect(page.getByRole('heading', { name: 'Rules' })).toBeVisible();
    await page.getByLabel('Rule text').fill('Archive receipts from Stripe and label them Finance');
    await page.getByRole('button', { name: 'Compile rule' }).click();
    await expect(page.getByText('Sender domain is stripe.com')).toBeVisible();
    await page.getByRole('button', { name: 'Save disabled rule' }).click();
    await expect(page.getByText('disabled')).toBeVisible();

    await page.getByRole('button', { name: 'Preview rule' }).click();
    await expect(page.getByText('No Gmail changes were made.')).toBeVisible();
    await expect(page.getByText('Your Stripe receipt')).toBeVisible();
    await page.getByRole('button', { name: 'Enable rule' }).click();
    await page.getByRole('button', { name: 'Disable rule' }).click();

    await page.getByRole('button', { name: 'Move rule up' }).click();
    await page.getByRole('button', { name: 'Edit rule' }).click();
    await page.getByLabel('Rule text').fill('Archive Stripe receipts and label them Finance');
    await page.getByRole('button', { name: 'Save disabled rule' }).click();

    await page.getByRole('button', { name: 'Delete rule' }).click();
    await page.getByRole('button', { name: 'Delete rule' }).click();
  });

  test('rules workspace remains usable at 375x812 without horizontal overflow', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await openRules(page);

    await expect(page.getByRole('heading', { name: 'Rules' })).toBeVisible();
    await expect(page.getByText('No Gmail changes were made.')).toBeHidden();

    const horizontalOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth > window.innerWidth,
    );
    expect(horizontalOverflow).toBe(false);
  });
});
