import { expect, test, type Page, type Route } from '@playwright/test';

import {
  API_ROUTE_PATTERN,
  expectNoHorizontalOverflow,
  seedAuthenticatedSession,
} from './chrome-test-utils';

test.describe('rules examples and auto-send setting', () => {
  for (const viewport of [
    { name: 'desktop', width: 1280, height: 900 },
    { name: 'mobile', width: 390, height: 844 },
  ]) {
    test(`rules examples composer flow works at ${viewport.name}`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      const consoleErrors = captureConsoleErrors(page);
      await openWithRulesExamplesMock(page);

      await expect(page.getByRole('heading', { name: 'Automation rules' })).toBeVisible();
      await expect(page.getByText('Available actions')).toBeVisible();
      await expect(page.getByText('Will auto-send')).toHaveCount(3);

      await page.getByRole('button', { name: 'Create rule' }).click();
      await expect(page.getByText('Choose from examples')).toBeVisible();
      await page
        .getByRole('button', {
          name: /Archive investor updates from portfolio companies/i,
        })
        .click();

      const sourceTextarea = page.getByLabel(
        'Which emails should Zero Mail match, and what should it do?',
      );
      await expect(sourceTextarea).toHaveValue('Archive investor updates from portfolio companies');

      await page.getByRole('button', { name: 'Convert to rule' }).click();
      await expect(page.getByText('Rule to save')).toBeVisible();
      await expect(page.getByText('portfolio.com')).toBeVisible();
      await expectNoHorizontalOverflow(page);
      expect(consoleErrors).toEqual([]);
    });
  }

  test('rules examples settings toggle persists and changes outbound copy', async ({ page }) => {
    const consoleErrors = captureConsoleErrors(page);
    const mockState = await openWithRulesExamplesMock(page, '/settings');

    await page.getByTestId('settings-auto-send-rules-switch').scrollIntoViewIfNeeded();
    await expect(page.getByTestId('settings-auto-send-rules-switch')).toBeChecked();
    await expect(page.getByText('Outbound rules can send when safety gates pass.')).toBeVisible();

    await page.getByTestId('settings-auto-send-rules-switch').click();

    await expect(page.getByTestId('settings-auto-send-rules-switch')).not.toBeChecked();
    await expect(
      page.getByText(
        'Outbound rules still save, but runtime saves Gmail drafts instead of sending.',
      ),
    ).toBeVisible();
    expect(mockState.autoSendRulesEnabled).toBe(false);
    expect(mockState.automationSettingUpdates).toEqual([{ autoSendRulesEnabled: false }]);
    expect(consoleErrors).toEqual([]);
  });
});

type MockState = {
  autoSendRulesEnabled: boolean;
  automationSettingUpdates: Array<{ autoSendRulesEnabled: boolean }>;
};

async function openWithRulesExamplesMock(page: Page, path: '/rules' | '/settings' = '/rules') {
  const mockState: MockState = {
    autoSendRulesEnabled: true,
    automationSettingUpdates: [],
  };
  await seedAuthenticatedSession(page, 'en');
  await page.route(API_ROUTE_PATTERN, async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (url.pathname === '/api/me') {
      await fulfillJson(route, {
        userId: 'user-1',
        tenantId: 'tenant-1',
        email: 'founder@example.com',
        preferredLanguage: 'en',
        onboardingStep: 'COMPLETE',
        triagePaused: false,
        gmailConnectionStatus: {
          status: 'CONNECTED',
          ingestionHealth: 'HEALTHY',
          googleEmail: 'founder@example.com',
        },
      });
      return;
    }

    if (url.pathname === '/api/billing/balance' && request.method() === 'GET') {
      await fulfillJson(route, {
        availableCredits: 12,
        heldCredits: 0,
        currency: 'credits',
        betaCredits: 12,
        paidCredits: 0,
        monthlyGrantCredits: 300,
        resetsAt: '2026-06-01T00:00:00.000Z',
        freeDuringBeta: true,
      });
      return;
    }

    if (url.pathname === '/api/gmail/connection/status' && request.method() === 'GET') {
      await fulfillJson(route, { connectionStatus: 'CONNECTED' });
      return;
    }

    if (url.pathname === '/api/tenant/triage-pause' && request.method() === 'PUT') {
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    if (url.pathname === '/api/rules' && request.method() === 'GET') {
      await fulfillJson(route, {
        rules: [],
        templates: [],
        materialization: {
          createdCount: 0,
          skippedCount: 0,
          customizedPreservedCount: 0,
        },
      });
      return;
    }

    if (url.pathname === '/api/rules/templates' && request.method() === 'GET') {
      await fulfillJson(route, []);
      return;
    }

    if (url.pathname === '/api/rules/catalog/examples' && request.method() === 'GET') {
      await fulfillJson(route, {
        personas: [
          {
            personaId: '00000000-0000-0000-0000-000000000001',
            personaKey: 'founder',
            displayName: 'Founder',
            icon: 'sparkles',
            displayOrder: 10,
            examples: [
              {
                exampleId: '00000000-0000-0000-0000-000000000101',
                sourceRef: 'seed:founder:1',
                exampleText: 'Archive investor updates from portfolio companies',
                displayOrder: 10,
              },
            ],
          },
          {
            personaId: '00000000-0000-0000-0000-000000000002',
            personaKey: 'student',
            displayName: 'Student',
            icon: 'book',
            displayOrder: 120,
            examples: [
              {
                exampleId: '00000000-0000-0000-0000-000000000201',
                sourceRef: 'seed:student:1',
                exampleText: 'Label scholarships as School',
                displayOrder: 10,
              },
            ],
          },
        ],
      });
      return;
    }

    if (url.pathname === '/api/rules/catalog/actions' && request.method() === 'GET') {
      await fulfillJson(route, { actions: ruleCatalogActions() });
      return;
    }

    if (url.pathname === '/api/rules/settings/automation' && request.method() === 'GET') {
      await fulfillJson(route, { autoSendRulesEnabled: mockState.autoSendRulesEnabled });
      return;
    }

    if (url.pathname === '/api/rules/settings/automation' && request.method() === 'PUT') {
      const payload = request.postDataJSON() as { autoSendRulesEnabled: boolean };
      mockState.autoSendRulesEnabled = payload.autoSendRulesEnabled;
      mockState.automationSettingUpdates.push(payload);
      await fulfillJson(route, { autoSendRulesEnabled: mockState.autoSendRulesEnabled });
      return;
    }

    if (url.pathname === '/api/rules/compile' && request.method() === 'POST') {
      const payload = request.postDataJSON() as { sourceText: string };
      expect(payload.sourceText).toBe('Archive investor updates from portfolio companies');
      await fulfillJson(route, {
        status: 'compiled',
        compiled: {
          status: 'compiled',
          sourceLanguage: 'en',
          displayName: 'Investor updates',
          schemaVersion: 'rules.v1',
          matcherAst: JSON.stringify({
            schemaVersion: 'rules.v1',
            type: 'ALL',
            children: [{ type: 'SENDER_DOMAIN', domain: 'portfolio.com' }],
          }),
          actionIntents: JSON.stringify([{ type: 'archive' }]),
        },
      });
      return;
    }

    if (url.pathname === '/api/llm/byok' && request.method() === 'GET') {
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    if (url.pathname === '/api/me/notifications' && request.method() === 'GET') {
      await fulfillJson(route, {
        channel: 'DAILY_DIGEST',
        digestEnabled: true,
        digestSendHourLocal: 20,
        timeZone: 'Asia/Ho_Chi_Minh',
      });
      return;
    }

    await route.fulfill({ status: 204, body: '' });
  });
  await page.goto(path, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');
  return mockState;
}

function captureConsoleErrors(page: Page) {
  const messages: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') messages.push(message.text());
  });
  return messages;
}

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

function ruleCatalogActions() {
  return [
    ruleAction('label', 'Label', 'Apply a Gmail label.', 'LOW', 10),
    ruleAction('archive', 'Archive', 'Remove matching messages from Inbox.', 'LOW', 20),
    ruleAction('save_draft', 'Save draft', 'Create a Gmail draft.', 'MEDIUM', 30),
    ruleAction('mark_read', 'Mark read', 'Mark matching messages as read.', 'LOW', 40),
    ruleAction('star', 'Star', 'Star matching messages.', 'LOW', 50),
    ruleAction(
      'add_to_digest',
      'Add to digest',
      'Include matching messages in a digest.',
      'LOW',
      60,
    ),
    ruleAction('mark_spam', 'Mark spam', 'Move matching messages to spam.', 'MEDIUM', 70),
    ruleAction('send_reply', 'Send reply', 'Automatically send a reply.', 'HIGH', 80),
    ruleAction('forward_email', 'Forward', 'Automatically forward a message.', 'HIGH', 90),
    ruleAction('send_email', 'Send email', 'Automatically send a new email.', 'HIGH', 100),
  ];
}

function ruleAction(
  actionKey: string,
  label: string,
  description: string,
  riskLevel: string,
  displayOrder: number,
) {
  return {
    actionKey,
    label,
    description,
    riskLevel,
    availabilityStatus: 'AVAILABLE',
    displayOrder,
  };
}
