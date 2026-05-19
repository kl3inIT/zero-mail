import { expect, type Page, type Route } from '@playwright/test';

type GmailConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'NOT_CONNECTED' | 'PENDING';
type OnboardingStep = 'GMAIL_CONNECTED' | 'TEMPLATE_SELECTED' | 'COMPLETE';
type DraftStatus = 'NO_DRAFT' | 'DRAFT_READY' | 'DRAFT_SENT';
type AppLocale = 'en' | 'vi';
type AnalyticsWindow = '7d' | '30d' | '90d';

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
  analyticsRequests: string[];
  notificationPreferenceUpdates: Array<{ digestEnabled: boolean; digestSendHourLocal: number }>;
  balanceRequests: number;
  draftRequests: string[];
  pauseRequests: Array<{ paused: boolean }>;
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
    analyticsRequests: [],
    notificationPreferenceUpdates: [],
    balanceRequests: 0,
    draftRequests: [],
    pauseRequests: [],
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

    if (url.pathname === '/me') {
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

    if (url.pathname === '/api/billing/balance' && request.method() === 'GET') {
      state.balanceRequests += 1;
      await fulfillJson(route, {
        availableCredits: state.availableCredits,
        heldCredits: 0,
        currency: 'credits',
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

    if (url.pathname === '/gmail/connection/status' && request.method() === 'GET') {
      await fulfillJson(route, { connectionStatus: state.connectionStatus });
      return;
    }

    if (url.pathname === '/tenant/triage-pause' && request.method() === 'PUT') {
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

    if (url.pathname === '/api/llm/byok' && request.method() === 'GET') {
      await route.fulfill({ status: 204, body: '' });
      return;
    }

    await route.fulfill({ status: 204, body: '' });
  });
}

export async function openAuthenticatedRoute(
  page: Page,
  path: '/analytics' | '/rules' | '/settings' | '/onboarding/gmail-connect',
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
  await expect(page.getByTestId('chrome-header')).toBeVisible();

  if (options.sidebarVisible) {
    await expect(page.getByTestId('app-sidebar')).toBeVisible();
  }
}

export async function expectChromeSuppressed(page: Page) {
  await expect(page.getByTestId('app-shell')).toHaveCount(0);
  await expect(page.getByTestId('app-sidebar')).toHaveCount(0);
  await expect(page.getByTestId('chrome-header')).toHaveCount(0);
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
