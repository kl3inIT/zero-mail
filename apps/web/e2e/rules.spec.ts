import { expect, test, type Page, type Route } from '@playwright/test';

import {
  API_ROUTE_PATTERN,
  expectAppShellChrome,
  expectNoClaySkinClasses,
  expectNoHorizontalOverflow,
} from './chrome-test-utils';

test.describe.configure({ mode: 'serial' });

/**
 * Rules E2E contract:
 * - Dev server is started by apps/web/playwright.config.ts with `pnpm dev`.
 * - The spec mocks the backend at http://localhost:8080 and keeps route state in memory.
 * - API shapes mirror apps/web/lib/api/schema.d.ts rules schemas.
 */

type MockMode = 'full-flow' | 'template-flow' | 'error-flow' | 'mobile-flow';

type MockRule = {
  ruleId: string;
  displayName: string;
  sourceText: string;
  enabled: boolean;
  orderIndex: number;
  sourceLanguage: string;
  schemaVersion: string;
  matcherAst: string;
  actionIntents: string;
  entityVersion: number;
  lastPreviewedEntityVersion?: number;
  lastPreviewedAt?: string;
  templateKey?: string;
  templateVersion?: number;
  customized?: boolean;
};

type MockTemplate = {
  templateKey: string;
  templateVersion: number;
  displayName: string;
  localizedCopyKey: string;
  sourceText: string;
  actionSummary: string;
  status: string;
  sourcedFromOnboarding: boolean;
  materialized: boolean;
  customized: boolean;
};

const compiledPayload = {
  status: 'compiled',
  sourceLanguage: 'en',
  displayName: 'Archive Stripe receipts',
  schemaVersion: 'rules.v1',
  matcherAst: JSON.stringify({
    schemaVersion: 'rules.v1',
    type: 'ALL',
    children: [
      { type: 'SENDER_DOMAIN', domain: 'stripe.com' },
      { type: 'SUBJECT_CONTAINS', text: 'receipt' },
    ],
  }),
  actionIntents: JSON.stringify([{ type: 'archive' }, { type: 'label', labelName: 'Finance' }]),
};

function createStripeRule(overrides: Partial<MockRule> = {}): MockRule {
  return {
    ruleId: 'rule-new',
    displayName: 'Archive Stripe receipts',
    sourceText: 'Archive receipts from Stripe and label them Finance',
    enabled: false,
    orderIndex: 1,
    sourceLanguage: 'en',
    schemaVersion: 'rules.v1',
    matcherAst: compiledPayload.matcherAst,
    actionIntents: compiledPayload.actionIntents,
    entityVersion: 1,
    customized: false,
    ...overrides,
  };
}

function createNewsletterRule(): MockRule {
  return {
    ruleId: 'rule-newsletters',
    displayName: 'Label newsletters',
    sourceText: 'Label newsletters as Reading',
    enabled: true,
    orderIndex: 2,
    sourceLanguage: 'en',
    schemaVersion: 'rules.v1',
    matcherAst: JSON.stringify({ schemaVersion: 'rules.v1', type: 'NEWSLETTER_INDICATOR' }),
    actionIntents: JSON.stringify([{ type: 'label', labelName: 'Reading' }]),
    entityVersion: 1,
    lastPreviewedEntityVersion: 1,
    lastPreviewedAt: '2026-05-10T00:00:00Z',
    templateKey: 'label-newsletters',
    templateVersion: 1,
    customized: false,
  };
}

function createTemplates(materialized = false): MockTemplate[] {
  return [
    {
      templateKey: 'archive-receipts',
      templateVersion: 1,
      displayName: 'Archive receipts',
      localizedCopyKey: 'templates.receipts',
      sourceText: 'Archive receipts and label them Finance',
      actionSummary: 'archive · label Finance',
      status: 'active',
      sourcedFromOnboarding: true,
      materialized,
      customized: false,
    },
  ];
}

async function mockRulesApis(page: Page, mode: MockMode) {
  const rules: MockRule[] =
    mode === 'error-flow' || mode === 'mobile-flow' ? [createStripeRule()] : [];
  const templates = createTemplates(false);

  await page.route(API_ROUTE_PATTERN, async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (url.pathname === '/me') {
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
      await fulfillJson(route, { availableCredits: 12, heldCredits: 0, currency: 'credits' });
      return;
    }

    if (url.pathname === '/gmail/connection/status' && request.method() === 'GET') {
      await fulfillJson(route, { connectionStatus: 'CONNECTED' });
      return;
    }

    if (url.pathname === '/tenant/triage-pause' && request.method() === 'PUT') {
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    if (url.pathname === '/api/rules' && request.method() === 'GET') {
      await fulfillJson(route, {
        rules: rules.sort((left, right) => left.orderIndex - right.orderIndex),
        templates,
        materialization: {
          createdCount: 0,
          skippedCount: 0,
          customizedPreservedCount: 0,
        },
      });
      return;
    }

    if (url.pathname === '/api/rules/templates' && request.method() === 'GET') {
      await fulfillJson(route, templates);
      return;
    }

    if (url.pathname === '/api/rules/compile' && request.method() === 'POST') {
      const payload = request.postDataJSON();

      if (mode === 'error-flow') {
        await fulfillProblem(route, 402, 'error.billing.insufficient');
        return;
      }

      expect(payload.sourceText).toBe('Archive receipts from Stripe and label them Finance');
      if (!payload.clarificationAnswer) {
        await fulfillJson(route, {
          status: 'clarificationRequired',
          clarification: {
            language: 'en',
            question: 'Which receipts should Zero Mail archive?',
          },
        });
        return;
      }

      expect(payload.clarificationAnswer).toBe('Only Stripe payment receipts');
      await fulfillJson(route, { status: 'compiled', compiled: compiledPayload });
      return;
    }

    if (url.pathname === '/api/rules' && request.method() === 'POST') {
      const payload = request.postDataJSON();
      expect(payload.compiled).toMatchObject({ status: 'compiled' });
      const createdRule = createStripeRule({
        displayName: payload.displayName,
        sourceText: payload.sourceText,
      });
      rules.splice(0, rules.length, createdRule, createNewsletterRule());
      await fulfillJson(route, createdRule);
      return;
    }

    if (url.pathname === '/api/rules/rule-new' && request.method() === 'PUT') {
      const payload = request.postDataJSON();
      const rule = rules.find((candidate) => candidate.ruleId === 'rule-new');
      expect(rule).toBeDefined();
      Object.assign(rule as MockRule, {
        displayName: payload.displayName,
        sourceText: payload.sourceText,
        matcherAst: payload.compiled.matcherAst,
        actionIntents: payload.compiled.actionIntents,
        entityVersion: (rule?.entityVersion ?? 1) + 1,
        enabled: false,
        lastPreviewedEntityVersion: undefined,
        customized: true,
      });
      await fulfillJson(route, rule);
      return;
    }

    if (url.pathname === '/api/rules/rule-new/preview' && request.method() === 'POST') {
      const payload = request.postDataJSON();
      expect([10, 25, 50]).toContain(payload.sampleSize);

      if (mode === 'error-flow') {
        await fulfillProblem(route, 409, 'error.rules.gmail.unavailable');
        return;
      }

      const rule = rules.find((candidate) => candidate.ruleId === 'rule-new');
      if (rule) {
        rule.lastPreviewedEntityVersion = rule.entityVersion;
        rule.lastPreviewedAt = '2026-05-10T01:00:00Z';
      }
      await fulfillJson(route, previewResponse(payload.sampleSize));
      return;
    }

    if (url.pathname === '/api/rules/rule-new/enabled' && request.method() === 'PATCH') {
      const payload = request.postDataJSON();
      const rule = rules.find((candidate) => candidate.ruleId === 'rule-new');
      if (rule) rule.enabled = payload.enabled;
      await fulfillJson(route, rule ?? createStripeRule({ enabled: payload.enabled }));
      return;
    }

    if (url.pathname === '/api/rules/reorder' && request.method() === 'PUT') {
      const payload = request.postDataJSON();
      expect(payload.entries).toHaveLength(rules.length);
      for (const [index, entry] of payload.entries.entries()) {
        const rule = rules.find((candidate) => candidate.ruleId === entry.ruleId);
        if (rule) rule.orderIndex = index + 1;
      }
      await fulfillJson(
        route,
        rules.sort((left, right) => left.orderIndex - right.orderIndex),
      );
      return;
    }

    if (url.pathname === '/api/rules/rule-new' && request.method() === 'DELETE') {
      const index = rules.findIndex((rule) => rule.ruleId === 'rule-new');
      if (index >= 0) rules.splice(index, 1);
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    if (
      url.pathname === '/api/rules/templates/archive-receipts/materialize' &&
      request.method() === 'POST'
    ) {
      templates[0] = { ...templates[0], materialized: true };
      const templateRule = createStripeRule({
        ruleId: 'rule-template',
        displayName: 'Archive receipts',
        sourceText: 'Archive receipts and label them Finance',
        templateKey: 'archive-receipts',
        templateVersion: 1,
      });
      rules.splice(0, rules.length, templateRule);
      await fulfillJson(route, {
        createdCount: 1,
        skippedCount: 0,
        customizedPreservedCount: 0,
        createdRules: [templateRule],
        skippedTemplates: [],
      });
      return;
    }

    await route.fulfill({ status: 204, body: '' });
  });
}

async function openRules(page: Page, mode: MockMode = 'full-flow') {
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
  await mockRulesApis(page, mode);
  await page.goto('/rules', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle');
}

// TODO(rules-v2): rewrite end-to-end against the new two-tab flow
// (Danh sách | Kiểm tra). The legacy single-rule preview shortcut, the
// 3-dot dropdown menu (Edit/Delete are now icon buttons), and the
// manual reorder feature have all been removed, and "Save (stays off
// until preview)" copy + "Test selected rule" heading no longer exist.
test.skip('rules desktop flow compiles, clarifies, saves disabled, previews, toggles, reorders, edits, and deletes', async ({
  page,
}) => {
  await openRules(page);

  await expectAppShellChrome(page, { sidebarVisible: true });
  await expectNoClaySkinClasses(page);
  await expect(page.getByRole('heading', { name: 'Automation rules', exact: true })).toBeVisible();
  await expect(page.getByText('No rules yet')).toBeVisible();

  await page.getByRole('button', { name: 'Create rule' }).click();
  const ruleText = page.getByLabel('Which emails should Zero Mail match, and what should it do?');
  await ruleText.fill('Archive receipts from Stripe and label them Finance');
  await page.getByRole('button', { name: 'Convert to rule', exact: true }).click();
  await expect(page.getByText('Which receipts should Zero Mail archive?')).toBeVisible();
  await expect(ruleText).toHaveValue('Archive receipts from Stripe and label them Finance');
  await expect(page.getByText('Zero Mail could not review this rule')).toHaveCount(0);

  await page.getByLabel('Your answer').fill('Only Stripe payment receipts');
  await page.getByRole('button', { name: 'Send answer' }).click();
  await expect(page.getByText('stripe.com')).toBeVisible();
  await page.getByRole('button', { name: 'Save (stays off until preview)' }).click();

  await page.getByRole('button', { name: 'Preview rule' }).click();
  await expect(
    page.getByLabel('Which emails should Zero Mail match, and what should it do?'),
  ).toHaveCount(0);
  await page.getByRole('button', { name: 'Preview rule' }).click();
  await expect(page.getByText('No Gmail changes were made.')).toBeVisible();
  await expect(page.getByText('Your Stripe receipt')).toBeVisible();
  await page.getByRole('button', { name: 'Enable rule' }).last().click();
  await expect(page.getByRole('button', { name: 'Disable rule' }).last()).toBeVisible();
  await page.getByRole('button', { name: 'Disable rule' }).last().click();
  await expect(page.getByRole('button', { name: 'Enable rule' }).last()).toBeVisible();

  await openRuleMenu(page, 'Archive Stripe receipts');
  await page.getByRole('menuitem', { name: 'Move rule down' }).click();
  await openRuleMenu(page, 'Archive Stripe receipts');
  await page.getByRole('menuitem', { name: 'Edit rule' }).click();
  // Editing an existing compiled rule opens the manual tab by default;
  // switch to Describe so the source textarea is mounted.
  await page.getByRole('tab', { name: 'Describe', exact: true }).click();
  await page
    .getByLabel('Which emails should Zero Mail match, and what should it do?')
    .fill('Archive receipts from Stripe and label them Finance');
  await page.getByRole('button', { name: 'Convert to rule', exact: true }).click();
  await page.getByLabel('Your answer').fill('Only Stripe payment receipts');
  await page.getByRole('button', { name: 'Send answer' }).click();
  await page.getByRole('button', { name: 'Save (stays off until preview)' }).click();
  await page.getByRole('button', { name: 'Preview rule' }).click();
  await expect(
    page.getByLabel('Which emails should Zero Mail match, and what should it do?'),
  ).toHaveCount(0);
  await page.getByRole('button', { name: 'Preview rule' }).click();
  await expect(page.getByText('No Gmail changes were made.')).toBeVisible();

  await openRuleMenu(page, 'Archive Stripe receipts');
  await page.getByRole('menuitem', { name: 'Delete rule' }).click();
  await page.getByRole('button', { name: 'Delete rule' }).last().click();
  await expect(page.getByText('Archive Stripe receipts')).toHaveCount(0);
});

test('template gallery materializes a disabled starter rule with provenance', async ({ page }) => {
  await openRules(page, 'template-flow');

  await page.getByRole('button', { name: 'Browse templates' }).click();
  await page.getByRole('button', { name: 'Use starter rule' }).click();
  await expect(page.getByText('Archive receipts').first()).toBeVisible();
  await expect(page.getByText('Template', { exact: true })).toBeVisible();
  await expect(page.getByText('archive-receipts · v1')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Enable rule' }).first()).toBeDisabled();
});

// TODO(rules-v2): rewrite for the two-tab layout — "Test selected rule"
// heading no longer exists (moved to "Preview on real Gmail" inside the
// Kiểm tra quy tắc tab) and the in-list selection UX changed.
test.skip('rules workspace remains in-shell and usable at 320px without horizontal overflow', async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 740 });
  await openRules(page, 'mobile-flow');

  await expectAppShellChrome(page);
  await expectNoClaySkinClasses(page);
  await expect(page.getByRole('heading', { name: 'Automation rules', exact: true })).toBeVisible();
  await expect(page.getByText('Test selected rule')).toBeVisible();
  await expect(page.getByText('Rule list')).toBeVisible();
  await page.getByRole('button', { name: 'Create rule' }).click();
  await expect(page.getByRole('dialog', { name: 'Create email rule' })).toBeVisible();
  await expect(
    page.getByLabel('Which emails should Zero Mail match, and what should it do?'),
  ).toHaveValue('');
  await expectNoHorizontalOverflow(page);
});

// TODO(rules-v2): rewrite to use the new icon-button edit (not the
// removed 3-dot dropdown) and to drive preview from the Kiểm tra tab.
test.skip('rules errors keep compile billing alerts in composer and Gmail preview errors in preview panel', async ({
  page,
}) => {
  await openRules(page, 'error-flow');

  await openRuleMenu(page, 'Archive Stripe receipts');
  await page.getByRole('menuitem', { name: 'Edit rule' }).click();
  // Editing an existing compiled rule opens the manual tab by default;
  // switch to Describe so the source textarea is mounted.
  await page.getByRole('tab', { name: 'Describe', exact: true }).click();
  await page
    .getByLabel('Which emails should Zero Mail match, and what should it do?')
    .fill('Archive receipts from Stripe and label them Finance');
  await page.getByRole('button', { name: 'Convert to rule', exact: true }).click();
  await expect(page.getByText('Platform credits are depleted.').first()).toBeVisible();

  await page.keyboard.press('Escape');
  await expect(
    page.getByLabel('Which emails should Zero Mail match, and what should it do?'),
  ).toHaveCount(0);
  await page.getByRole('button', { name: 'Preview rule' }).click();
  await expect(
    page.getByText('Gmail preview is unavailable. Reconnect Gmail and try again.').first(),
  ).toBeVisible();
});

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

async function openRuleMenu(page: Page, ruleName: string) {
  const ruleRow = page.getByRole('row', { name: new RegExp(ruleName) });
  await ruleRow.getByRole('button', { name: 'Rule actions' }).click();
}

async function fulfillProblem(route: Route, status: number, code: string) {
  await route.fulfill({
    status,
    contentType: 'application/problem+json',
    body: JSON.stringify({
      type: 'about:blank',
      title: 'Problem',
      status,
      code,
    }),
  });
}

function previewResponse(sampleSize: number) {
  return {
    impactSummary: {
      sampleSize,
      sampledMessageCount: sampleSize,
      matchedCount: 2,
      proposedActionCounts: { archive: 2, label: 2 },
      deferredCount: 1,
      conflictCount: 1,
      noWriteNotice: true,
      noWriteNoticeKey: 'rules.preview.noWriteNotice',
    },
    rows: [
      {
        gmailMessageId: 'message-1',
        gmailThreadId: 'thread-1',
        sanitizedSenderEmail: 'billing@stripe.com',
        sanitizedSenderDomain: 'stripe.com',
        sanitizedSubjectExcerpt: 'Your Stripe receipt',
        internalDate: '2026-05-09T00:00:00Z',
        gmailLabelIds: ['INBOX'],
        matched: true,
        proposedActionChips: [
          { actionTypeId: 'archive', safeLabel: 'archive' },
          { actionTypeId: 'label', safeLabel: 'label Finance' },
        ],
        matchedEvidenceChips: [{ matcherNodeId: 'sender-domain', reasonKey: 'sender domain' }],
        deferredEvidenceChips: [{ matcherNodeId: 'semantic-intent', reasonKey: 'semantic' }],
        conflictChips: [{ conflictTypeId: 'label_conflict', contributingRuleIds: ['rule-new'] }],
      },
    ],
    savedRuleMarkedPreviewed: true,
  };
}
