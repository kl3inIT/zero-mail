import { expect, type Page, type Route } from '@playwright/test';

import {
  API_ROUTE_PATTERN,
  expectNoHorizontalOverflow,
  seedAuthenticatedSession,
} from './chrome-test-utils';

export const MAILBOX_A_ID = '00000000-0000-0000-0000-0000000000a1';
export const MAILBOX_B_ID = '00000000-0000-0000-0000-0000000000b2';

export type MailboxMockState = {
  activeMailboxId: string;
  mailboxRequests: string[];
  activeMailboxRequests: string[];
  setActiveRequests: string[];
  inboxRequests: string[];
  needsReplyRequests: string[];
  rulesRequests: string[];
  copyRequests: Array<{ sourceGmailConnectionId: string; targetGmailConnectionId: string }>;
  draftRequests: Array<{ gmailThreadId: string; executingMailboxId: string }>;
  auditRequests: string[];
  analyticsRequests: string[];
  rulesByMailbox: Record<string, MockRule[]>;
  draftedThreadIds: Set<string>;
};

type MailboxSummary = {
  gmailConnectionId: string;
  googleEmail: string;
  displayPurpose: string;
  status: string;
  isPrimary: boolean;
};

type MockRule = {
  ruleId: string;
  gmailConnectionId: string;
  displayName: string;
  sourceText: string;
  enabled: boolean;
  orderIndex: number;
  sourceLanguage: string;
  schemaVersion: string;
  matcherAst: string;
  actionIntents: string;
  entityVersion: number;
  lastPreviewedEntityVersion: number | null;
  lastPreviewedAt: string | null;
  templateKey: string | null;
  templateVersion: number | null;
  customized: boolean;
};

const mailboxSummaries: MailboxSummary[] = [
  {
    gmailConnectionId: MAILBOX_A_ID,
    googleEmail: 'founder@example.com',
    displayPurpose: 'Founder Gmail',
    status: 'CONNECTED',
    isPrimary: true,
  },
  {
    gmailConnectionId: MAILBOX_B_ID,
    googleEmail: 'support@example.com',
    displayPurpose: 'Support Gmail',
    status: 'CONNECTED',
    isPrimary: false,
  },
];

export function createMailboxMockState(
  overrides: Partial<Pick<MailboxMockState, 'activeMailboxId'>> = {},
): MailboxMockState {
  return {
    activeMailboxId: overrides.activeMailboxId ?? MAILBOX_A_ID,
    mailboxRequests: [],
    activeMailboxRequests: [],
    setActiveRequests: [],
    inboxRequests: [],
    needsReplyRequests: [],
    rulesRequests: [],
    copyRequests: [],
    draftRequests: [],
    auditRequests: [],
    analyticsRequests: [],
    draftedThreadIds: new Set<string>(),
    rulesByMailbox: {
      [MAILBOX_A_ID]: [archiveReceiptRule(MAILBOX_A_ID)],
      [MAILBOX_B_ID]: [supportEscalationRule(MAILBOX_B_ID)],
    },
  };
}

export async function openMailboxRoute(
  page: Page,
  path: '/inbox' | '/needs-reply' | '/rules' | '/analytics',
  state: MailboxMockState,
) {
  await seedAuthenticatedSession(page, 'en');
  await installMailboxApiMock(page, state);
  await page.goto(path, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');
}

export async function openAccountMenu(page: Page) {
  const accountButton = page.getByTestId('sidebar-footer-account');
  if (!(await accountButton.isVisible())) {
    await page.getByRole('button', { name: 'Toggle navigation' }).click();
  }
  await page.getByTestId('sidebar-footer-account').click();
}

export { expectNoHorizontalOverflow };

function activeMailbox(state: MailboxMockState): MailboxSummary {
  return mailboxSummaries.find((mailbox) => mailbox.gmailConnectionId === state.activeMailboxId)!;
}

async function installMailboxApiMock(page: Page, state: MailboxMockState) {
  await page.route(API_ROUTE_PATTERN, async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const pathname = url.pathname;

    if (pathname === '/api/me' && request.method() === 'GET') {
      const mailbox = activeMailbox(state);
      await fulfillJson(route, {
        userId: 'user-1',
        tenantId: 'tenant-1',
        email: 'founder@example.com',
        displayName: 'Zero Founder',
        preferredLanguage: 'en',
        onboardingStep: 'COMPLETE',
        triagePaused: false,
        gmailConnectionStatus: {
          status: 'CONNECTED',
          ingestionHealth: 'HEALTHY',
          googleEmail: mailbox.googleEmail,
        },
      });
      return;
    }

    if (pathname === '/api/credits/balance' && request.method() === 'GET') {
      await fulfillJson(route, {
        availableCredits: 42,
        heldCredits: 0,
        currency: 'credits',
        monthlyCredits: 42,
        additionalCredits: 0,
        monthlyCreditAllowance: 300,
        resetsAt: '2026-07-01T00:00:00.000Z',
      });
      return;
    }

    if (pathname === '/api/plan-upgrades/plans' && request.method() === 'GET') {
      await fulfillJson(route, { currentPlanCode: 'PLUS', plans: [] });
      return;
    }

    if (pathname === '/api/gmail/connection/status' && request.method() === 'GET') {
      await fulfillJson(route, { connectionStatus: 'CONNECTED' });
      return;
    }

    if (pathname === '/api/gmail/mailboxes' && request.method() === 'GET') {
      state.mailboxRequests.push(state.activeMailboxId);
      await fulfillJson(route, mailboxSummaries);
      return;
    }

    if (pathname === '/api/gmail/active-mailbox' && request.method() === 'GET') {
      const mailbox = activeMailbox(state);
      state.activeMailboxRequests.push(mailbox.gmailConnectionId);
      await fulfillJson(route, {
        gmailConnectionId: mailbox.gmailConnectionId,
        email: mailbox.googleEmail,
        displayPurpose: mailbox.displayPurpose,
        status: mailbox.status,
        isPrimary: mailbox.isPrimary,
      });
      return;
    }

    const setActiveMatch = pathname.match(/^\/api\/gmail\/active-mailbox\/([^/]+)$/);
    if (setActiveMatch && request.method() === 'PUT') {
      const gmailConnectionId = decodeURIComponent(setActiveMatch[1]);
      expect(mailboxSummaries.map((mailbox) => mailbox.gmailConnectionId)).toContain(
        gmailConnectionId,
      );
      state.activeMailboxId = gmailConnectionId;
      state.setActiveRequests.push(gmailConnectionId);
      const mailbox = activeMailbox(state);
      await fulfillJson(route, {
        gmailConnectionId: mailbox.gmailConnectionId,
        email: mailbox.googleEmail,
        displayPurpose: mailbox.displayPurpose,
        status: mailbox.status,
        isPrimary: mailbox.isPrimary,
      });
      return;
    }

    if (pathname === '/api/tenant/triage-pause' && request.method() === 'PUT') {
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    if (pathname === '/api/gmail/inbox' && request.method() === 'GET') {
      state.inboxRequests.push(state.activeMailboxId);
      await fulfillJson(route, inboxPageForMailbox(state.activeMailboxId));
      return;
    }

    const inboxDetailMatch = pathname.match(/^\/api\/gmail\/inbox\/(message-[ab])$/);
    if (inboxDetailMatch && request.method() === 'GET') {
      await fulfillJson(route, inboxDetailForMessage(inboxDetailMatch[1]));
      return;
    }

    const draftDetailMatch = pathname.match(/^\/api\/gmail\/inbox\/drafts\/(draft-[ab])$/);
    if (draftDetailMatch && request.method() === 'GET') {
      await fulfillJson(route, draftDetailForMailbox(state.activeMailboxId));
      return;
    }

    if (pathname === '/api/threads' && request.method() === 'GET') {
      state.needsReplyRequests.push(state.activeMailboxId);
      await fulfillJson(route, needsReplyPageForMailbox(state));
      return;
    }

    if (pathname === '/api/threads/to-reply-count' && request.method() === 'GET') {
      await fulfillJson(route, { toReplyCount: 1 });
      return;
    }

    if (pathname === '/api/threads/counts' && request.method() === 'GET') {
      await fulfillJson(route, { toReplyCount: 1, awaitingCount: 0, draftedCount: 0 });
      return;
    }

    const draftMatch = pathname.match(/^\/api\/threads\/([^/]+)\/draft$/);
    if (draftMatch && request.method() === 'POST') {
      const gmailThreadId = decodeURIComponent(draftMatch[1]);
      state.draftRequests.push({ gmailThreadId, executingMailboxId: state.activeMailboxId });
      state.draftedThreadIds.add(gmailThreadId);
      await fulfillJson(route, {
        draftId: state.activeMailboxId === MAILBOX_A_ID ? 'draft-a' : 'draft-b',
        gmailThreadId,
        status: 'GENERATED',
        openInGmailUrl: `https://mail.google.com/mail/u/0/#drafts/${gmailThreadId}`,
      });
      return;
    }

    const resolveMatch = pathname.match(/^\/api\/threads\/([^/]+)\/resolve$/);
    if (resolveMatch && request.method() === 'POST') {
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    if (pathname === '/api/rules' && request.method() === 'GET') {
      state.rulesRequests.push(state.activeMailboxId);
      await fulfillJson(route, {
        rules: state.rulesByMailbox[state.activeMailboxId] ?? [],
        templates: [],
        materialization: {
          createdCount: 0,
          skippedCount: 0,
          customizedPreservedCount: 0,
        },
      });
      return;
    }

    if (pathname === '/api/rules/templates' && request.method() === 'GET') {
      await fulfillJson(route, []);
      return;
    }

    if (pathname === '/api/rules/catalog/examples' && request.method() === 'GET') {
      await fulfillJson(route, { personas: [] });
      return;
    }

    if (pathname === '/api/rules/catalog/actions' && request.method() === 'GET') {
      await fulfillJson(route, { actions: ruleCatalogActions() });
      return;
    }

    if (pathname === '/api/rules/settings/automation' && request.method() === 'GET') {
      await fulfillJson(route, { autoSendRulesEnabled: true });
      return;
    }

    if (pathname === '/api/rules/copy' && request.method() === 'POST') {
      const payload = request.postDataJSON() as {
        sourceGmailConnectionId: string;
        targetGmailConnectionId: string;
      };
      state.copyRequests.push(payload);
      expect(payload.targetGmailConnectionId).toBe(state.activeMailboxId);
      const copiedRule = copiedArchiveRule(payload.targetGmailConnectionId);
      state.rulesByMailbox[payload.targetGmailConnectionId] = [
        ...(state.rulesByMailbox[payload.targetGmailConnectionId] ?? []),
        copiedRule,
      ];
      await fulfillJson(route, {
        copiedCount: 1,
        copiedRuleIds: [copiedRule.ruleId],
      });
      return;
    }

    if (pathname === '/api/triage/audit' && request.method() === 'GET') {
      state.auditRequests.push(state.activeMailboxId);
      await fulfillJson(route, {
        items: [auditEntryForMailbox(state.activeMailboxId)],
        nextCursor: null,
      });
      return;
    }

    if (pathname === '/api/triage/sender-safety-net' && request.method() === 'GET') {
      await fulfillJson(route, { senders: [] });
      return;
    }

    if (pathname === '/api/analytics/summary' && request.method() === 'GET') {
      state.analyticsRequests.push(state.activeMailboxId);
      await fulfillJson(route, analyticsSummary(url.searchParams.get('window') ?? '7d'));
      return;
    }

    await route.fulfill({ status: 204, body: '' });
  });
}

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

function archiveReceiptRule(gmailConnectionId: string): MockRule {
  return rule(gmailConnectionId, {
    ruleId: 'rule-archive-receipts',
    displayName: 'Archive founder receipts',
    sourceText: 'Archive receipts for the founder mailbox',
    enabled: true,
    orderIndex: 1,
    actionIntents: JSON.stringify([{ type: 'archive' }, { type: 'label', labelName: 'Finance' }]),
  });
}

function supportEscalationRule(gmailConnectionId: string): MockRule {
  return rule(gmailConnectionId, {
    ruleId: 'rule-support-escalations',
    displayName: 'Handle support escalations',
    sourceText: 'Draft a reply for angry support escalations',
    enabled: true,
    orderIndex: 1,
    actionIntents: JSON.stringify([
      { type: 'save_draft', instruction: 'Draft a calm support reply' },
    ]),
  });
}

function copiedArchiveRule(gmailConnectionId: string): MockRule {
  return rule(gmailConnectionId, {
    ruleId: 'rule-copied-archive-receipts',
    displayName: 'Copied archive receipts',
    sourceText: 'Archive receipts copied from another mailbox',
    enabled: false,
    orderIndex: 2,
    actionIntents: JSON.stringify([{ type: 'archive' }]),
  });
}

function rule(gmailConnectionId: string, overrides: Partial<MockRule>): MockRule {
  return {
    ruleId: 'rule-id',
    gmailConnectionId,
    displayName: 'Rule',
    sourceText: 'Rule source',
    enabled: false,
    orderIndex: 1,
    sourceLanguage: 'en',
    schemaVersion: 'rules.v1',
    matcherAst: JSON.stringify({ schemaVersion: 'rules.v1', type: 'ALL', children: [] }),
    actionIntents: JSON.stringify([{ type: 'archive' }]),
    entityVersion: 1,
    lastPreviewedEntityVersion: null,
    lastPreviewedAt: null,
    templateKey: null,
    templateVersion: null,
    customized: false,
    ...overrides,
  };
}

function inboxPageForMailbox(gmailConnectionId: string) {
  const item = gmailConnectionId === MAILBOX_A_ID ? inboxMessageA() : inboxMessageB();
  return {
    items: [item],
    nextCursor: null,
    loadedCount: 1,
    maxMessages: 100,
    dataSource: 'PROJECTION',
  };
}

function inboxMessageA() {
  return {
    gmailMessageId: 'message-a',
    gmailThreadId: 'thread-a',
    subject: 'Alpha investor update',
    snippet: 'Founder mailbox only',
    from: 'alpha@investor.test',
    to: ['founder@example.com'],
    cc: [],
    receivedAt: '2026-06-10T01:00:00.000Z',
    labelIds: ['INBOX'],
    labels: [{ id: 'INBOX', name: 'Inbox' }],
    unread: true,
    hasAttachment: false,
    openInGmailUrl: 'https://mail.google.com/mail/u/0/#inbox/thread-a',
  };
}

function inboxMessageB() {
  return {
    gmailMessageId: 'message-b',
    gmailThreadId: 'thread-b',
    subject: 'Beta support ticket',
    snippet: 'Support mailbox only',
    from: 'client@support.test',
    to: ['support@example.com'],
    cc: [],
    receivedAt: '2026-06-10T02:00:00.000Z',
    labelIds: ['INBOX'],
    labels: [{ id: 'INBOX', name: 'Inbox' }],
    unread: true,
    hasAttachment: false,
    openInGmailUrl: 'https://mail.google.com/mail/u/0/#inbox/thread-b',
  };
}

function inboxDetailForMessage(gmailMessageId: string) {
  const message = gmailMessageId === 'message-a' ? inboxMessageA() : inboxMessageB();
  return {
    message,
    renderedText:
      gmailMessageId === 'message-a' ? 'Founder-only email body.' : 'Support-only email body.',
    renderedHtml: '',
  };
}

function needsReplyPageForMailbox(state: MailboxMockState) {
  const isFounder = state.activeMailboxId === MAILBOX_A_ID;
  const gmailThreadId = isFounder ? 'thread-a' : 'thread-b';
  const drafted = state.draftedThreadIds.has(gmailThreadId);
  return {
    items: [
      {
        gmailThreadId,
        subject: isFounder ? 'Founder reply needed' : 'Support reply needed',
        otherParty: isFounder ? 'alpha@investor.test' : 'client@support.test',
        snippet: isFounder ? 'Founder account thread' : 'Support account thread',
        latestMessageId: isFounder ? 'message-a' : 'message-b',
        draftId: drafted ? (isFounder ? 'draft-a' : 'draft-b') : null,
        lastActivityAt: isFounder ? '2026-06-10T01:00:00.000Z' : '2026-06-10T02:00:00.000Z',
        draftStatus: drafted ? 'DRAFT_READY' : 'NO_DRAFT',
        resolved: false,
        openInGmailUrl: `https://mail.google.com/mail/u/0/#all/${gmailThreadId}`,
      },
    ],
    nextCursor: null,
    toReplyCount: 1,
  };
}

function draftDetailForMailbox(gmailConnectionId: string) {
  const isFounder = gmailConnectionId === MAILBOX_A_ID;
  return {
    message: isFounder ? inboxMessageA() : inboxMessageB(),
    renderedText: isFounder
      ? 'Draft from founder@example.com for the founder mailbox.'
      : 'Draft from support@example.com for the support mailbox.',
    renderedHtml: '',
  };
}

function auditEntryForMailbox(gmailConnectionId: string) {
  const isFounder = gmailConnectionId === MAILBOX_A_ID;
  return {
    auditId: isFounder ? 'audit-a' : 'audit-b',
    createdAt: '2026-06-10T03:00:00.000Z',
    action: isFounder ? 'archive' : 'save_draft',
    ruleName: isFounder ? 'Archive founder receipts' : 'Handle support escalations',
    reason: 'Mailbox-scoped mock audit row',
    gmailMessageId: isFounder ? 'message-a' : 'message-b',
    gmailThreadId: isFounder ? 'thread-a' : 'thread-b',
    subject: isFounder ? 'Alpha investor update' : 'Beta support ticket',
    senderEmail: isFounder ? 'alpha@investor.test' : 'client@support.test',
    undoableUntil: '2026-07-10T03:00:00.000Z',
    decisionState: 'APPLIED',
    blockedBySafetyNetPattern: null,
    sourceMailboxId: gmailConnectionId,
    sourceMailboxEmail: isFounder ? 'founder@example.com' : 'support@example.com',
    executingMailboxId: gmailConnectionId,
    executingMailboxEmail: isFounder ? 'founder@example.com' : 'support@example.com',
  };
}

function analyticsSummary(window: string) {
  return {
    window,
    volumeObserved: 12,
    volumeApplied: 8,
    timeSavedSeconds: 3600,
    topSenders: [{ senderEmail: 'client@support.test', count: 4 }],
    dailyLoad: [{ day: '2026-06-10', observed: 12, applied: 8, reverted: 0 }],
    actionMix: [{ actionType: 'save_draft', applied: 8, reverted: 0, failed: 0 }],
    domainLoad: [{ domain: 'support.test', count: 4 }],
    categoryLoad: [{ category: 'updates', count: 4 }],
    replyBuckets: [{ bucket: 'TO_REPLY', count: 1, withDraft: 0 }],
    automationOpportunities: { noRuleMatched: 1, failedActions: 0, pendingActions: 0 },
    ruleHits: [{ ruleName: 'Handle support escalations', decisions: 4, applied: 4, reverted: 0 }],
  };
}

function ruleCatalogActions() {
  return [
    ruleAction('archive', 'Archive', 'Remove matching messages from Inbox.', 'LOW', 10),
    ruleAction('save_draft', 'Save draft', 'Create a Gmail draft.', 'MEDIUM', 20),
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
