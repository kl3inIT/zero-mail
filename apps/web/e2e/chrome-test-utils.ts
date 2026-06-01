import { expect, type Page, type Route } from '@playwright/test';

type GmailConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING';
type OnboardingStep = 'GMAIL_CONNECTED' | 'TEMPLATE_SELECTED' | 'COMPLETE';
type DraftStatus = 'NO_DRAFT' | 'DRAFT_READY' | 'DRAFT_SENT';
type AppLocale = 'en' | 'vi';
type AnalyticsWindow = '7d' | '30d' | '90d';
type BillingPlanCode = 'FREE' | 'PLUS' | 'PRO';

type NotificationPreferences = {
  channel: string;
  digestEnabled: boolean;
  digestSendHourLocal: number;
  timeZone: string;
};

export type ChromeMockState = {
  triagePaused: boolean;
  connectionStatus: GmailConnectionStatus;
  onboardingStep: OnboardingStep;
  preferredLanguage: AppLocale;
  availableCredits: number;
  needsReplyDraftStatus: DraftStatus;
  notificationPreferences: NotificationPreferences;
  autoSendRulesEnabled: boolean;
  currentPlanCode: BillingPlanCode;
  analyticsRequests: string[];
  notificationPreferenceUpdates: Array<{ digestEnabled: boolean; digestSendHourLocal: number }>;
  balanceRequests: number;
  draftRequests: string[];
  pauseRequests: Array<{ paused: boolean }>;
  automationSettingUpdates: Array<{ autoSendRulesEnabled: boolean }>;
};

export const API_ROUTE_PATTERN = /^https?:\/\/[^/]+\/(?:me|api\/|gmail\/|tenant\/).*$/;

export function createChromeMockState(overrides: Partial<ChromeMockState> = {}): ChromeMockState {
  return {
    triagePaused: false,
    connectionStatus: 'CONNECTED',
    onboardingStep: 'COMPLETE',
    preferredLanguage: 'en',
    availableCredits: 12,
    needsReplyDraftStatus: 'NO_DRAFT',
    notificationPreferences: {
      channel: 'DAILY_DIGEST',
      digestEnabled: true,
      digestSendHourLocal: 20,
      timeZone: 'Asia/Ho_Chi_Minh',
    },
    autoSendRulesEnabled: true,
    currentPlanCode: 'PLUS',
    analyticsRequests: [],
    notificationPreferenceUpdates: [],
    balanceRequests: 0,
    draftRequests: [],
    pauseRequests: [],
    automationSettingUpdates: [],
    ...overrides,
  };
}

export async function seedAuthenticatedSession(page: Page, locale: AppLocale = 'en') {
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
      value: locale,
      domain: 'localhost',
      path: '/',
      sameSite: 'Lax',
      secure: false,
    },
  ]);
}

export async function installChromeApiMock(page: Page, state: ChromeMockState) {
  await page.route(API_ROUTE_PATTERN, async (route) => {
    const request = route.request();
    const url = new URL(request.url());

    if (url.pathname === '/api/me') {
      await fulfillJson(route, {
        userId: 'user-1',
        tenantId: 'tenant-1',
        email: 'founder@example.com',
        preferredLanguage: state.preferredLanguage,
        onboardingStep: state.onboardingStep,
        triagePaused: state.triagePaused,
        gmailConnectionStatus: {
          status: state.connectionStatus,
          ingestionHealth: state.connectionStatus === 'CONNECTED' ? 'HEALTHY' : 'WATCH_UNHEALTHY',
          googleEmail: 'founder@example.com',
        },
      });
      return;
    }

    if (url.pathname === '/api/analytics/summary' && request.method() === 'GET') {
      const windowParam = url.searchParams.get('window') ?? '';
      state.analyticsRequests.push(windowParam);
      if (!isAnalyticsWindow(windowParam)) {
        await route.fulfill({ status: 400, body: '' });
        return;
      }
      await fulfillJson(route, analyticsSummary(windowParam));
      return;
    }

    if (url.pathname === '/api/me/notifications' && request.method() === 'GET') {
      await fulfillJson(route, state.notificationPreferences);
      return;
    }

    if (url.pathname === '/api/me/notifications' && request.method() === 'PATCH') {
      const payload = request.postDataJSON() as {
        digestEnabled: boolean;
        digestSendHourLocal: number;
      };
      expect(typeof payload.digestEnabled).toBe('boolean');
      expect(payload.digestSendHourLocal).toBeGreaterThanOrEqual(0);
      expect(payload.digestSendHourLocal).toBeLessThanOrEqual(23);
      state.notificationPreferenceUpdates.push(payload);
      state.notificationPreferences = {
        ...state.notificationPreferences,
        digestEnabled: payload.digestEnabled,
        digestSendHourLocal: payload.digestSendHourLocal,
      };
      await fulfillJson(route, state.notificationPreferences);
      return;
    }

    if (url.pathname === '/api/credits/balance' && request.method() === 'GET') {
      state.balanceRequests += 1;
      await fulfillJson(route, {
        availableCredits: state.availableCredits,
        heldCredits: 0,
        currency: 'credits',
        monthlyCredits: state.availableCredits,
        additionalCredits: 0,
        monthlyCreditAllowance: 300,
        resetsAt: '2026-06-01T00:00:00.000Z',
      });
      return;
    }

    if (url.pathname === '/api/plan-upgrades/plans' && request.method() === 'GET') {
      await fulfillJson(route, billingPlans(state.currentPlanCode));
      return;
    }

    if (url.pathname === '/api/credits/ledger' && request.method() === 'GET') {
      await fulfillJson(route, billingLedgerPage(url.searchParams));
      return;
    }

    if (url.pathname === '/api/plan-upgrades/checkout' && request.method() === 'POST') {
      const payload = request.postDataJSON() as {
        planCode: BillingPlanCode;
        paymentMethod: 'LEMON_SQUEEZY' | 'SEPAY_BANK_TRANSFER';
      };
      if (planTier(payload.planCode) < planTier(state.currentPlanCode)) {
        await fulfillJson(route, planDowngradeProblem(), 409);
        return;
      }
      if (payload.paymentMethod === 'SEPAY_BANK_TRANSFER') {
        await fulfillJson(route, {
          paymentMethod: 'SEPAY_BANK_TRANSFER',
          status: 'WAITING_FOR_TRANSFER',
          bankTransferIntent: bankTransferIntent(payload.planCode),
        });
        return;
      }
      await fulfillJson(route, {
        paymentMethod: 'LEMON_SQUEEZY',
        status: 'REDIRECT_REQUIRED',
        checkoutUrl: `https://checkout.test/${payload.planCode.toLowerCase()}`,
      });
      return;
    }

    const checkoutMatch = url.pathname.match(/^\/api\/plan-upgrades\/plans\/([^/]+)\/checkout$/);
    if (checkoutMatch && request.method() === 'POST') {
      const requestedPlanCode = checkoutMatch[1] as BillingPlanCode;
      if (planTier(requestedPlanCode) < planTier(state.currentPlanCode)) {
        await fulfillJson(route, planDowngradeProblem(), 409);
        return;
      }
      await fulfillJson(route, {
        paymentMethod: 'LEMON_SQUEEZY',
        status: 'REDIRECT_REQUIRED',
        checkoutUrl: `https://checkout.test/${requestedPlanCode.toLowerCase()}`,
      });
      return;
    }

    if (url.pathname === '/api/threads' && request.method() === 'GET') {
      const bucket = url.searchParams.get('bucket');
      await fulfillJson(route, {
        items:
          bucket === 'awaiting-their-reply'
            ? []
            : [
                {
                  gmailThreadId: 'thread-1',
                  subject: 'Re: Q3 partnership terms',
                  otherParty: 'priya@acme.io',
                  lastActivityAt: '2026-05-12T10:30:00.000Z',
                  draftStatus: state.needsReplyDraftStatus,
                  resolved: false,
                  openInGmailUrl: 'https://mail.google.com/mail/u/0/#all/thread-1',
                },
              ],
        nextCursor: null,
        toReplyCount: bucket === 'awaiting-their-reply' ? 0 : 1,
      });
      return;
    }

    if (url.pathname === '/api/threads/to-reply-count' && request.method() === 'GET') {
      await fulfillJson(route, {
        toReplyCount: state.needsReplyDraftStatus === 'DRAFT_SENT' ? 0 : 1,
      });
      return;
    }

    if (url.pathname === '/api/triage/audit' && request.method() === 'GET') {
      await fulfillJson(route, { items: [], nextCursor: null });
      return;
    }

    const draftMatch = url.pathname.match(/^\/api\/threads\/([^/]+)\/draft$/);
    if (draftMatch && request.method() === 'POST') {
      const gmailThreadId = decodeURIComponent(draftMatch[1]);
      state.draftRequests.push(gmailThreadId);
      state.needsReplyDraftStatus = 'DRAFT_READY';
      await fulfillJson(route, {
        draftId: 'draft-1',
        gmailThreadId,
        status: 'GENERATED',
        openInGmailUrl: `https://mail.google.com/mail/u/0/#all/${gmailThreadId}`,
      });
      return;
    }

    const resolveMatch = url.pathname.match(/^\/api\/threads\/([^/]+)\/resolve$/);
    if (resolveMatch && request.method() === 'POST') {
      await route.fulfill({ status: 200, body: '' });
      return;
    }

    if (url.pathname === '/api/gmail/connection/status' && request.method() === 'GET') {
      await fulfillJson(route, { connectionStatus: state.connectionStatus });
      return;
    }

    if (url.pathname === '/api/tenant/triage-pause' && request.method() === 'PUT') {
      const payload = request.postDataJSON() as { paused: boolean };
      expect(typeof payload.paused).toBe('boolean');
      state.pauseRequests.push(payload);
      state.triagePaused = payload.paused;
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
            displayName: url.searchParams.get('locale') === 'vi' ? 'Nhà sáng lập' : 'Founder',
            icon: 'sparkles',
            displayOrder: 10,
            examples: [
              {
                exampleId: '00000000-0000-0000-0000-000000000101',
                exampleText:
                  url.searchParams.get('locale') === 'vi'
                    ? 'Lưu trữ cập nhật từ nhà đầu tư'
                    : 'Archive investor updates from portfolio companies',
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
      await fulfillJson(route, { autoSendRulesEnabled: state.autoSendRulesEnabled });
      return;
    }

    if (url.pathname === '/api/rules/settings/automation' && request.method() === 'PUT') {
      const payload = request.postDataJSON() as { autoSendRulesEnabled: boolean };
      expect(typeof payload.autoSendRulesEnabled).toBe('boolean');
      state.automationSettingUpdates.push(payload);
      state.autoSendRulesEnabled = payload.autoSendRulesEnabled;
      await fulfillJson(route, { autoSendRulesEnabled: state.autoSendRulesEnabled });
      return;
    }

    if (url.pathname === '/api/byok' && request.method() === 'GET') {
      await fulfillJson(route, { code: 'ai.byok.no_row' }, 404);
      return;
    }

    if (url.pathname === '/api/settings/voice' && request.method() === 'GET') {
      await fulfillJson(route, voiceSettings());
      return;
    }

    if (url.pathname === '/api/settings/behavior' && request.method() === 'GET') {
      await fulfillJson(route, behaviorSettings());
      return;
    }

    if (url.pathname === '/api/settings/ai/cost' && request.method() === 'GET') {
      await fulfillJson(route, { usd: 0 });
      return;
    }

    if (url.pathname === '/api/knowledge-snippets' && request.method() === 'GET') {
      await fulfillJson(route, { items: [] });
      return;
    }

    await route.fulfill({ status: 204, body: '' });
  });
}

function billingPlans(currentPlanCode: BillingPlanCode) {
  return {
    currentPlanCode,
    plans: [
      billingPlan('FREE', 'Free', 0, 0, 300),
      billingPlan('PLUS', 'Plus', 1, 199000, 2000),
      billingPlan('PRO', 'Pro', 2, 399000, 8000),
    ],
  };
}

function billingPlan(
  code: BillingPlanCode,
  displayName: string,
  tierRank: number,
  priceVnd: number,
  monthlyCreditAllowance: number,
) {
  return {
    code,
    displayName,
    tierRank,
    billingCycle: code === 'FREE' ? 'NONE' : 'MONTH',
    currency: 'VND',
    priceVnd,
    monthlyCreditAllowance,
    sortOrder: tierRank * 10,
    features: billingPlanFeatures(code),
  };
}

function billingPlanFeatures(code: BillingPlanCode) {
  if (code === 'FREE') {
    return [
      billingPlanFeature('basic-triage', 'Phân loại AI cơ bản', 1),
      billingPlanFeature('manual-rules', 'Quy tắc thủ công', 0),
    ];
  }
  if (code === 'PLUS') {
    return [
      billingPlanFeature('smart-triage', 'Phân loại AI nâng cao', 1),
      billingPlanFeature('reply-drafts', 'Gợi ý trả lời email', 3),
      billingPlanFeature('automation-rules', 'Quy tắc tự động', 0),
    ];
  }
  return [
    billingPlanFeature('smart-triage', 'Phân loại AI nâng cao', 1),
    billingPlanFeature('premium-reply-drafts', 'Soạn trả lời bằng mô hình cao cấp', 5),
    billingPlanFeature('automation-rules', 'Quy tắc tự động không giới hạn', 0),
    billingPlanFeature('analytics', 'Phân tích hộp thư nâng cao', 0),
  ];
}

function billingPlanFeature(code: string, displayName: string, creditCost: number) {
  return {
    code,
    displayName,
    description: null,
    category: 'TRIAGE',
    creditCost,
    dailyInvocationLimit: null,
    monthlyInvocationLimit: null,
  };
}

function billingLedgerPage(searchParams: URLSearchParams) {
  const cursor = searchParams.get('cursor');
  const limit = Number(searchParams.get('limit') ?? '10');
  const allEntries = Array.from({ length: 12 }, (_, index) => ({
    id: `00000000-0000-0000-0000-${String(index + 1).padStart(12, '0')}`,
    timestamp: `2026-05-${String(28 - index).padStart(2, '0')}T08:00:00.000Z`,
    type: index % 3 === 0 ? 'grant' : index % 3 === 1 ? 'settle' : 'release',
    description:
      index % 3 === 0 ? 'Credit grant' : index % 3 === 1 ? 'Credit spent' : 'Credit released',
    amountCredits: index % 3 === 0 ? 300 : index % 3 === 1 ? -5 : 5,
    balanceAfterCredits: 300 - index * 5,
    reference: 'E2E',
  }));
  const start = cursor === 'page-2' ? 10 : 0;
  const entries = allEntries.slice(start, start + limit);
  const nextCursor = start + limit < allEntries.length ? 'page-2' : null;
  return { entries, nextCursor };
}

function bankTransferIntent(planCode: BillingPlanCode) {
  const amountVnd = planCode === 'PRO' ? 399000 : 199000;
  return {
    id: '00000000-0000-0000-0000-000000000900',
    code: 'ABCD2345',
    planCode,
    amountVnd,
    currency: 'VND',
    status: 'PENDING',
    expiresAt: '2026-06-01T10:15:00.000Z',
    bankCode: 'MBBANK',
    bankName: 'MB Bank',
    accountNumber: '123456789',
    accountName: 'ZERO MAIL',
    transferContent: `ZM ABCD2345 ${planCode}`,
    qrUrl: `https://qr.sepay.vn/img?acc=123456789&bank=MBBANK&amount=${amountVnd}&des=ZM%20ABCD2345%20${planCode}`,
  };
}

function planDowngradeProblem() {
  return {
    type: 'about:blank',
    title: 'Plan downgrade is not allowed',
    status: 409,
    detail: 'The selected plan is lower than the tenant active paid plan.',
    code: 'error.billing.plan.downgradeNotAllowed',
  };
}

function planTier(planCode: BillingPlanCode): number {
  switch (planCode) {
    case 'FREE':
      return 0;
    case 'PLUS':
      return 1;
    case 'PRO':
      return 2;
  }
}

export async function openAuthenticatedRoute(
  page: Page,
  path:
    | '/ai'
    | '/analytics'
    | '/rules'
    | '/settings'
    | '/onboarding/gmail-connect'
    | '/cleanup/unsubscribe-campaign'
    | '/cleanup/suppression',
  state: ChromeMockState,
) {
  await seedAuthenticatedSession(page, state.preferredLanguage);
  await installChromeApiMock(page, state);
  await page.goto(path, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('load');
}

export async function expectNoHorizontalOverflow(page: Page) {
  const horizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth,
  );
  expect(horizontalOverflow).toBe(false);
}

export async function expectAppShellChrome(page: Page, options: { sidebarVisible?: boolean } = {}) {
  await expect(page.getByTestId('app-shell')).toBeVisible();
  await expect(page.getByTestId('app-shell-content')).toBeVisible();

  if (options.sidebarVisible) {
    await expect(page.getByTestId('app-sidebar')).toBeVisible();
  }
}

export async function expectChromeSuppressed(page: Page) {
  await expect(page.getByTestId('app-shell')).toHaveCount(0);
  await expect(page.getByTestId('app-sidebar')).toHaveCount(0);
  await expect(page.getByTestId('app-shell-content')).toHaveCount(0);
}

export async function expectNoClaySkinClasses(page: Page) {
  await expect(page.locator('[class*="zm-proto"], [class*="zm-auth"]')).toHaveCount(0);
}

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });
}

function isAnalyticsWindow(value: string): value is AnalyticsWindow {
  return value === '7d' || value === '30d' || value === '90d';
}

function analyticsSummary(window: AnalyticsWindow) {
  const multiplier = window === '7d' ? 1 : window === '30d' ? 2 : 3;
  return {
    window,
    volumeObserved: 1500 * multiplier,
    volumeApplied: 1247 * multiplier,
    timeSavedSeconds: 15120 * multiplier,
    topSenders: [
      { senderEmail: 'founder@acme.test', count: 44 * multiplier },
      { senderEmail: 'billing@example.com', count: 21 * multiplier },
      { senderEmail: 'alerts@example.com', count: 12 * multiplier },
    ],
    dailyLoad: [
      {
        day: '2026-05-10',
        observed: 240 * multiplier,
        applied: 188 * multiplier,
        reverted: 3,
      },
      {
        day: '2026-05-11',
        observed: 320 * multiplier,
        applied: 270 * multiplier,
        reverted: 2,
      },
      {
        day: '2026-05-12',
        observed: 210 * multiplier,
        applied: 166 * multiplier,
        reverted: 1,
      },
    ],
    actionMix: [
      { actionType: 'archive', applied: 720 * multiplier, reverted: 4, failed: 1 },
      { actionType: 'label', applied: 360 * multiplier, reverted: 2, failed: 0 },
      { actionType: 'save_draft', applied: 167 * multiplier, reverted: 0, failed: 0 },
    ],
    domainLoad: [
      { domain: 'acme.test', count: 44 * multiplier },
      { domain: 'example.com', count: 33 * multiplier },
      { domain: 'alerts.example', count: 12 * multiplier },
    ],
    categoryLoad: [
      { category: 'updates', count: 510 * multiplier },
      { category: 'promotions', count: 260 * multiplier },
      { category: 'forums', count: 90 * multiplier },
    ],
    replyBuckets: [
      { bucket: 'TO_REPLY', count: 7 * multiplier, withDraft: 4 * multiplier },
      { bucket: 'AWAITING_THEIR_REPLY', count: 3 * multiplier, withDraft: 0 },
    ],
    automationOpportunities: {
      noRuleMatched: 31 * multiplier,
      failedActions: 2,
      pendingActions: 1,
    },
    ruleHits: [
      {
        ruleName: 'Archive receipts',
        decisions: 30 * multiplier,
        applied: 28 * multiplier,
        reverted: 2,
      },
      {
        ruleName: 'Draft investor updates',
        decisions: 9 * multiplier,
        applied: 9 * multiplier,
        reverted: 0,
      },
    ],
  };
}

function voiceSettings() {
  return {
    writingStyle: '',
    personalInstructions: '',
    emailSignature: '',
    aiOutputLanguage: 'en',
  };
}

function behaviorSettings() {
  return {
    autoDraftReplies: false,
    draftConfidence: 'MEDIUM',
    sensitiveDataProtection: true,
  };
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
