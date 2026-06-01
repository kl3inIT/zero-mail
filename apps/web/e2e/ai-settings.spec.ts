import { expect, test, type Locator, type Page, type Route } from '@playwright/test';

import {
  API_ROUTE_PATTERN,
  expectNoHorizontalOverflow,
  seedAuthenticatedSession,
} from './chrome-test-utils';

const DUMMY_API_KEY = 'sk-test-PLAYWRIGHT-DUMMY-KEY-32CHARSLONG';
const NOW = '2026-05-27T09:30:00.000Z';

test.describe('AI settings', () => {
  test('flat-section golden path persists voice, behavior, knowledge, safety-net, and BYOK state', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1280, height: 1000 });
    const mockState = await openAiSettingsMock(page);

    await expect(page.getByRole('heading', { name: 'AI settings' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Your voice' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Behavior' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Updates' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Safety net' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'AI Provider' })).toBeVisible();

    const writingStyle = Array.from({ length: 220 }, (_, index) => `styleword${index + 1}`).join(
      ' ',
    );
    await openSettingDialog(page, 'Writing style', 'Set');
    let dialog = page.getByRole('dialog', { name: 'Writing style' });
    await dialog.getByLabel('Style content').fill(writingStyle);
    await submitDialogForm(dialog);
    await expect(page.getByText('Voice saved')).toBeVisible();
    expect(mockState.voice.writingStyle).toBe(writingStyle);

    await reloadAiSettings(page);
    await openSettingDialog(page, 'Writing style', 'Edit');
    dialog = page.getByRole('dialog', { name: 'Writing style' });
    await expect(dialog.getByLabel('Style content')).toHaveValue(writingStyle);
    await closeDialog(page);

    const personalInstructions =
      `${'Keep replies concise and specific. '.repeat(45)}Never render [SYSTEM] or XML fences.`.slice(
        0,
        1500,
      );
    await openSettingDialog(page, 'About me (personal instructions)', 'Set');
    dialog = page.getByRole('dialog', { name: 'About me (personal instructions)' });
    await dialog.getByLabel('Personal instructions').fill(personalInstructions);
    await submitDialogForm(dialog);
    await expect(page.getByText('Voice saved')).toBeVisible();

    await reloadAiSettings(page);
    await openSettingDialog(page, 'About me (personal instructions)', 'Edit');
    dialog = page.getByRole('dialog', { name: 'About me (personal instructions)' });
    await expect(dialog.getByLabel('Personal instructions')).toHaveValue(personalInstructions);
    await expect(page.getByText('<system>')).toHaveCount(0);
    await closeDialog(page);

    const autoDraftSwitch = page.getByRole('switch', { name: 'Auto-draft replies' });
    await expect(autoDraftSwitch).toBeChecked();
    await autoDraftSwitch.click();
    await expect(autoDraftSwitch).not.toBeChecked();
    await expect(page.getByText('Behavior saved')).toBeVisible();
    expect(mockState.behavior.autoDraftReplies).toBe(false);

    await reloadAiSettings(page);
    await expect(page.getByRole('switch', { name: 'Auto-draft replies' })).not.toBeChecked();

    await expect(settingCard(page, 'Tone')).toHaveCount(0);

    await openSettingDialog(page, 'Draft confidence threshold', 'Edit');
    dialog = page.getByRole('dialog', { name: 'Draft confidence threshold' });
    await dialog.getByRole('combobox', { name: 'Select threshold' }).click();
    await page.getByRole('option', { name: /HIGH/ }).click();
    await submitDialogForm(dialog);
    await expect(page.getByText('Behavior saved')).toBeVisible();

    await reloadAiSettings(page);
    await openSettingDialog(page, 'Draft confidence threshold', 'Edit');
    dialog = page.getByRole('dialog', { name: 'Draft confidence threshold' });
    await expect(dialog.getByRole('combobox', { name: 'Select threshold' })).toContainText('HIGH');
    await closeDialog(page);

    await page.getByRole('button', { name: 'Add snippet' }).click();
    dialog = page.getByRole('dialog', { name: 'Add snippet' });
    await dialog.getByLabel('Title').fill('Board briefing');
    await dialog
      .getByLabel('Content')
      .fill('Mention the Q3 board packet and close with next steps.');
    await submitDialogForm(dialog);
    await expect(page.getByText('Snippet added')).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Board briefing' })).toBeVisible();

    const knowledgeRow = page.getByRole('row', { name: /Board briefing/ });
    await knowledgeRow.getByRole('button', { name: 'Edit' }).click();
    dialog = page.getByRole('dialog', { name: 'Edit snippet' });
    await dialog.getByLabel('Content').fill('Updated context for the weekly board packet.');
    await submitDialogForm(dialog);
    await expect(page.getByText('Snippet updated')).toBeVisible();
    await page
      .getByRole('row', { name: /Board briefing/ })
      .getByRole('button', { name: 'Edit' })
      .click();
    dialog = page.getByRole('dialog', { name: 'Edit snippet' });
    await expect(dialog.getByLabel('Content')).toHaveValue(
      'Updated context for the weekly board packet.',
    );
    await closeDialog(page);

    await page
      .getByRole('row', { name: /Board briefing/ })
      .getByRole('button', { name: 'Delete' })
      .click();
    dialog = page.getByRole('dialog', { name: 'Delete' });
    await dialog.getByRole('button', { name: 'Delete' }).click();
    await expect(page.getByText('Snippet deleted')).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Board briefing' })).toHaveCount(0);

    await addProtectedSender(page, 'vip@acme.com');
    const emailSenderRow = senderRow(page, 'vip@acme.com');
    await expect(emailSenderRow.getByText('Email')).toBeVisible();
    await expect(emailSenderRow.getByText('You')).toBeVisible();

    const domainListResponse = page.waitForResponse((response) => {
      const request = response.request();
      const url = new URL(response.url());
      return (
        request.method() === 'GET' &&
        url.pathname === '/api/triage/sender-safety-net' &&
        response.status() === 200
      );
    });
    await addProtectedSender(page, '@evilcorp.com');
    const domainSenderRow = senderRow(page, '@evilcorp.com');
    await expect(domainSenderRow.getByText('Domain')).toBeVisible();
    const domainText = (await domainSenderRow.locator('p').first().textContent())?.trim();
    expect(domainText).toBe('@evilcorp.com');
    const domainListJson = (await (await domainListResponse).json()) as ProtectedSendersResponse;
    expect(domainListJson.senders).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ pattern: '@evilcorp.com', patternKind: 'DOMAIN' }),
      ]),
    );

    await emailSenderRow.getByRole('button', { name: 'Remove' }).click();
    await expect(senderRow(page, 'vip@acme.com')).toHaveCount(0);

    await expect(page.getByLabel('Base URL')).toHaveValue('https://api.openai.com/v1');
    await page.getByLabel('API key').fill(DUMMY_API_KEY);
    await page.getByRole('button', { name: 'Save' }).last().click();
    await expect(page.getByText('Key saved (will not be shown again)')).toBeVisible();
    await expect(
      settingCard(page, 'Personal key (BYOK)')
        .locator('p')
        .filter({ hasText: 'Saved key. Test the connection to load models' }),
    ).toBeVisible();
    const activeSwitch = page.getByRole('switch', { name: 'Active' });
    await expect(activeSwitch).toBeDisabled();
    await activeSwitch.hover({ force: true });
    await expect(
      page.getByText('Pick a model and pass the connection test before enabling BYOK.'),
    ).toBeVisible();
    await expect(page.getByRole('button', { name: 'Test connection' })).toBeEnabled();

    await reloadAiSettings(page);
    await expect(page.getByText('Saved key: ****LONG')).toBeVisible();
    await expect(page.getByRole('switch', { name: 'Active' })).toBeDisabled();
    await expect(page.getByText(/AI cost last 7 days: \$\d+\.\d{2}/)).toBeVisible();
    await expectNoPlaintextApiKeyInDom(page);
    await expectNoHorizontalOverflow(page);
  });
});

type VoiceSettings = {
  writingStyle: string;
  personalInstructions: string;
  emailSignature: string;
  aiOutputLanguage: 'vi' | 'en';
};

type BehaviorSettings = {
  autoDraftReplies: boolean;
  draftConfidence: 'LOW' | 'MEDIUM' | 'HIGH';
  sensitiveDataProtection: boolean;
};

type KnowledgeSnippet = {
  id: string;
  title: string;
  content: string;
  updatedAt: string;
};

type ProtectedSender = {
  id: string;
  pattern: string;
  patternKind: 'EMAIL' | 'DOMAIN';
  createdByUser: boolean;
  createdAt: string;
  senderEmail: string;
  optedIn: boolean;
};

type ProtectedSendersResponse = {
  senders: ProtectedSender[];
};

type ByokRow = {
  active: boolean;
  baseUrl: string;
  lastFourChars: string;
  lastTestResult?: 'OK' | 'ERROR' | null;
  lastTestedAt?: string | null;
  modelId?: string | null;
  provider: 'OPENAI' | 'ANTHROPIC' | 'GOOGLE' | 'DEEPSEEK';
};

type AiSettingsMockState = {
  voice: VoiceSettings;
  behavior: BehaviorSettings;
  knowledge: KnowledgeSnippet[];
  protectedSenders: ProtectedSender[];
  byok: ByokRow | null;
  triagePaused: boolean;
  digestEnabled: boolean;
  autoSendRulesEnabled: boolean;
  nextKnowledgeIndex: number;
  nextSenderIndex: number;
};

async function openAiSettingsMock(page: Page): Promise<AiSettingsMockState> {
  const mockState: AiSettingsMockState = {
    voice: {
      writingStyle: '',
      personalInstructions: '',
      emailSignature: '',
      aiOutputLanguage: 'vi',
    },
    behavior: {
      autoDraftReplies: true,
      draftConfidence: 'MEDIUM',
      sensitiveDataProtection: true,
    },
    knowledge: [],
    protectedSenders: [],
    byok: null,
    triagePaused: false,
    digestEnabled: true,
    autoSendRulesEnabled: true,
    nextKnowledgeIndex: 1,
    nextSenderIndex: 1,
  };

  await seedAuthenticatedSession(page, 'en');
  await page.route(API_ROUTE_PATTERN, async (route) => handleApiRoute(route, mockState));
  await page.goto('/ai', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');
  return mockState;
}

async function handleApiRoute(route: Route, mockState: AiSettingsMockState) {
  const request = route.request();
  const url = new URL(request.url());

  if (url.pathname === '/api/me' && request.method() === 'GET') {
    await fulfillJson(route, {
      userId: 'user-1',
      tenantId: 'tenant-1',
      email: 'founder@example.com',
      preferredLanguage: 'en',
      onboardingStep: 'COMPLETE',
      triagePaused: mockState.triagePaused,
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

  if (url.pathname === '/api/threads/to-reply-count' && request.method() === 'GET') {
    await fulfillJson(route, { toReplyCount: 0 });
    return;
  }

  if (url.pathname === '/api/settings/voice' && request.method() === 'GET') {
    await fulfillJson(route, mockState.voice);
    return;
  }

  if (url.pathname === '/api/settings/voice' && request.method() === 'PUT') {
    mockState.voice = { ...mockState.voice, ...(request.postDataJSON() as Partial<VoiceSettings>) };
    await fulfillJson(route, mockState.voice);
    return;
  }

  if (url.pathname === '/api/settings/voice/generate-from-sent' && request.method() === 'POST') {
    await fulfillJson(route, {
      generatedStyle:
        'Write in concise paragraphs, explain tradeoffs, and close with a clear next step.',
    });
    return;
  }

  if (url.pathname === '/api/settings/behavior' && request.method() === 'GET') {
    await fulfillJson(route, mockState.behavior);
    return;
  }

  if (url.pathname === '/api/settings/behavior' && request.method() === 'PUT') {
    mockState.behavior = {
      ...mockState.behavior,
      ...(request.postDataJSON() as Partial<BehaviorSettings>),
    };
    await fulfillJson(route, mockState.behavior);
    return;
  }

  if (url.pathname === '/api/me/notifications' && request.method() === 'GET') {
    await fulfillJson(route, {
      channel: 'DAILY_DIGEST',
      digestEnabled: mockState.digestEnabled,
      digestSendHourLocal: 20,
      timeZone: 'Asia/Ho_Chi_Minh',
    });
    return;
  }

  if (url.pathname === '/api/me/notifications' && request.method() === 'PATCH') {
    const payload = request.postDataJSON() as { digestEnabled: boolean };
    mockState.digestEnabled = payload.digestEnabled;
    await fulfillJson(route, {
      channel: 'DAILY_DIGEST',
      digestEnabled: mockState.digestEnabled,
      digestSendHourLocal: 20,
      timeZone: 'Asia/Ho_Chi_Minh',
    });
    return;
  }

  if (url.pathname === '/api/tenant/triage-pause' && request.method() === 'PUT') {
    const payload = request.postDataJSON() as { paused: boolean };
    mockState.triagePaused = payload.paused;
    await route.fulfill({ status: 204, body: '' });
    return;
  }

  if (url.pathname === '/api/rules/settings/automation' && request.method() === 'GET') {
    await fulfillJson(route, { autoSendRulesEnabled: mockState.autoSendRulesEnabled });
    return;
  }

  if (url.pathname === '/api/rules/settings/automation' && request.method() === 'PUT') {
    const payload = request.postDataJSON() as { autoSendRulesEnabled: boolean };
    mockState.autoSendRulesEnabled = payload.autoSendRulesEnabled;
    await fulfillJson(route, { autoSendRulesEnabled: mockState.autoSendRulesEnabled });
    return;
  }

  if (url.pathname === '/api/knowledge-snippets' && request.method() === 'GET') {
    await fulfillJson(route, { items: mockState.knowledge });
    return;
  }

  if (url.pathname === '/api/knowledge-snippets' && request.method() === 'POST') {
    const payload = request.postDataJSON() as { title: string; content: string };
    const snippet: KnowledgeSnippet = {
      id: `snippet-${mockState.nextKnowledgeIndex++}`,
      title: payload.title,
      content: payload.content,
      updatedAt: NOW,
    };
    mockState.knowledge = [snippet, ...mockState.knowledge];
    await fulfillJson(route, snippet);
    return;
  }

  const knowledgeMatch = url.pathname.match(/^\/api\/knowledge-snippets\/([^/]+)$/);
  if (knowledgeMatch && request.method() === 'PUT') {
    const snippetId = decodeURIComponent(knowledgeMatch[1]);
    const payload = request.postDataJSON() as { title: string; content: string };
    let updatedSnippet: KnowledgeSnippet | null = null;
    mockState.knowledge = mockState.knowledge.map((snippet) => {
      if (snippet.id !== snippetId) return snippet;
      updatedSnippet = { ...snippet, ...payload, updatedAt: NOW };
      return updatedSnippet;
    });
    await fulfillJson(route, updatedSnippet);
    return;
  }

  if (knowledgeMatch && request.method() === 'DELETE') {
    const snippetId = decodeURIComponent(knowledgeMatch[1]);
    mockState.knowledge = mockState.knowledge.filter((snippet) => snippet.id !== snippetId);
    await route.fulfill({ status: 204, body: '' });
    return;
  }

  if (url.pathname === '/api/triage/sender-safety-net' && request.method() === 'GET') {
    await fulfillJson(route, { senders: mockState.protectedSenders });
    return;
  }

  const optInMatch = url.pathname.match(/^\/api\/triage\/sender-safety-net\/(.+)\/opt-in$/);
  if (optInMatch && request.method() === 'POST') {
    const pattern = decodeURIComponent(optInMatch[1]).toLowerCase();
    const sender: ProtectedSender = {
      id: `sender-${mockState.nextSenderIndex++}`,
      pattern,
      patternKind: pattern.startsWith('@') ? 'DOMAIN' : 'EMAIL',
      createdByUser: true,
      createdAt: NOW,
      senderEmail: pattern,
      optedIn: true,
    };
    mockState.protectedSenders = [sender, ...mockState.protectedSenders];
    await fulfillJson(route, sender);
    return;
  }

  const senderDeleteMatch = url.pathname.match(/^\/api\/triage\/sender-safety-net\/([^/]+)$/);
  if (senderDeleteMatch && request.method() === 'DELETE') {
    const senderId = decodeURIComponent(senderDeleteMatch[1]);
    mockState.protectedSenders = mockState.protectedSenders.filter(
      (sender) => sender.id !== senderId,
    );
    await route.fulfill({ status: 204, body: '' });
    return;
  }

  if (url.pathname === '/api/byok' && request.method() === 'GET') {
    if (!mockState.byok) {
      await fulfillJson(route, { code: 'ai.byok.no_row' }, 404);
      return;
    }
    await fulfillJson(route, mockState.byok);
    return;
  }

  if (url.pathname === '/api/byok' && request.method() === 'POST') {
    const payload = request.postDataJSON() as {
      provider: ByokRow['provider'];
      baseUrl: string;
      apiKey: string;
    };
    mockState.byok = {
      active: false,
      baseUrl: payload.baseUrl,
      lastFourChars: payload.apiKey.slice(-4),
      lastTestResult: null,
      lastTestedAt: null,
      modelId: null,
      provider: payload.provider,
    };
    await fulfillJson(route, mockState.byok);
    return;
  }

  if (url.pathname === '/api/byok/model' && request.method() === 'PUT') {
    const payload = request.postDataJSON() as { modelId: string };
    mockState.byok = mockState.byok ? { ...mockState.byok, modelId: payload.modelId } : null;
    await fulfillJson(route, mockState.byok);
    return;
  }

  if (url.pathname === '/api/byok/active' && request.method() === 'PUT') {
    const payload = request.postDataJSON() as { active: boolean };
    mockState.byok = mockState.byok ? { ...mockState.byok, active: payload.active } : null;
    await fulfillJson(route, mockState.byok);
    return;
  }

  if (url.pathname === '/api/settings/ai/cost' && request.method() === 'GET') {
    await fulfillJson(route, { usd: 2.43 });
    return;
  }

  if (url.pathname === '/api/triage/audit' && request.method() === 'GET') {
    await fulfillJson(route, {
      items: [
        auditEntry('audit-blocked', 'blocked@evilcorp.com', '@evilcorp.com'),
        auditEntry('audit-normal', 'normal@example.com', null),
      ],
      nextCursor: null,
    });
    return;
  }

  await route.fulfill({ status: 204, body: '' });
}

function auditEntry(
  auditId: string,
  senderEmail: string,
  blockedBySafetyNetPattern: string | null,
) {
  return {
    auditId,
    createdAt: NOW,
    action: 'archive',
    ruleName: 'Safety check',
    reason: 'Matched a configured rule.',
    gmailMessageId: `${auditId}-message`,
    gmailThreadId: `${auditId}-thread`,
    subject: 'Quarterly update',
    senderEmail,
    undoableUntil: '2026-05-27T10:30:00.000Z',
    decisionState: blockedBySafetyNetPattern ? 'REJECTED_BY_SAFETY_NET' : 'APPLIED',
    blockedBySafetyNetPattern,
  };
}

function settingCard(page: Page, title: string) {
  return page.locator('[data-slot="card"]').filter({ hasText: title }).first();
}

async function openSettingDialog(page: Page, cardTitle: string, buttonName: 'Edit' | 'Set') {
  await settingCard(page, cardTitle).getByRole('button', { name: buttonName }).click();
}

async function submitDialogForm(dialog: Locator) {
  await dialog.locator('form').evaluate((formElement) => {
    (formElement as HTMLFormElement).requestSubmit();
  });
}

async function closeDialog(page: Page) {
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
}

async function reloadAiSettings(page: Page) {
  await page.reload({ waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: 'AI settings' })).toBeVisible();
}

async function addProtectedSender(page: Page, pattern: string) {
  await page.getByPlaceholder('ceo@acme.com or @acme.com').fill(pattern);
  await page.getByPlaceholder('ceo@acme.com or @acme.com').press('Enter');
  await expect(senderRow(page, pattern)).toBeVisible();
}

function senderRow(page: Page, pattern: string) {
  return page.getByTestId('sender-safety-net-list').locator('> div').filter({ hasText: pattern });
}

async function expectNoPlaintextApiKeyInDom(page: Page) {
  const pageMarkup = await page.evaluate(() => document.documentElement.outerHTML);
  expect(pageMarkup).not.toContain(DUMMY_API_KEY);
  expect(pageMarkup.match(/sk-[A-Za-z0-9]{20,}/g) ?? []).toEqual([]);
  expect(pageMarkup.match(/sk-[A-Za-z0-9-]{20,}/g) ?? []).toEqual([]);
}

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}
