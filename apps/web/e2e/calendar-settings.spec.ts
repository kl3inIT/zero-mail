import { expect, test } from '@playwright/test';

import {
  createChromeMockState,
  installChromeApiMock,
  seedAuthenticatedSession,
} from './chrome-test-utils';

// Phase 12 W3 e2e — /settings/mailboxes/[mailboxId]/calendar
//
// Per memory `reference_ai_page_e2e_and_hydration.md` we MUST mock every API
// the page calls; the calendar feature owns
//   GET    /api/calendar/mailboxes/{mailboxId}/connections
//   DELETE /api/calendar/connections/{calendarConnectionId}
//   PATCH  /api/calendar/calendars/{calendarId}/enabled
//   PATCH  /api/calendar/mailboxes/{mailboxId}/preferences
//   POST   /api/calendar/connect-intent
// The default chrome mock covers the auth surface (/api/me, /api/credits/balance,
// etc.); these handlers are stacked via `page.route` before navigation so they
// fire before chrome's catch-all 204.

const MAILBOX_ID = '00000000-0000-0000-0000-00000000bbbb';
const CONNECTION_ID = '00000000-0000-0000-0000-00000000cccc';
const CALENDAR_ID = '00000000-0000-0000-0000-00000000dddd';

interface E2eConnection {
  id: string;
  googleEmail: string;
  status: string;
  connectedAt: string | null;
  disconnectedAt: string | null;
  googleProfileName: string | null;
  googleProfilePictureUrl: string | null;
  calendars: Array<{
    id: string;
    externalCalendarId: string;
    name: string;
    isPrimary: boolean;
    isEnabled: boolean;
    timezone: string | null;
  }>;
  preferences: Array<{
    id: string;
    mailboxId: string;
    calendarConnectionId: string;
    calendarId: string;
    role: string;
  }>;
}

function populatedConnections(): E2eConnection[] {
  return [
    {
      id: CONNECTION_ID,
      googleEmail: 'founder@example.com',
      status: 'CONNECTED',
      connectedAt: '2026-06-20T10:00:00Z',
      disconnectedAt: null,
      googleProfileName: 'Founder',
      googleProfilePictureUrl: null,
      calendars: [
        {
          id: CALENDAR_ID,
          externalCalendarId: 'primary',
          name: 'Primary',
          isPrimary: true,
          isEnabled: true,
          timezone: 'UTC',
        },
        {
          id: '00000000-0000-0000-0000-00000000eeee',
          externalCalendarId: 'work',
          name: 'Work',
          isPrimary: false,
          isEnabled: true,
          timezone: 'UTC',
        },
      ],
      preferences: [
        {
          id: '00000000-0000-0000-0000-00000000ffff',
          mailboxId: MAILBOX_ID,
          calendarConnectionId: CONNECTION_ID,
          calendarId: CALENDAR_ID,
          role: 'FREEBUSY',
        },
      ],
    },
  ];
}

function disconnectedConnections(): E2eConnection[] {
  return [
    {
      ...populatedConnections()[0],
      status: 'DISCONNECTED',
      disconnectedAt: '2026-06-21T10:00:00Z',
    },
  ];
}

interface CalendarMockState {
  connections: E2eConnection[];
  disconnectCalls: string[];
}

async function installCalendarMocks(
  page: import('@playwright/test').Page,
  state: CalendarMockState,
) {
  await page.route(
    new RegExp(`https?://[^/]+/api/calendar/mailboxes/${MAILBOX_ID}/connections$`),
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(state.connections),
      });
    },
  );
  await page.route(/https?:\/\/[^/]+\/api\/calendar\/connections\/[^/]+$/, async (route) => {
    if (route.request().method() === 'DELETE') {
      const match = route
        .request()
        .url()
        .match(/connections\/([^/?#]+)/);
      if (match) state.disconnectCalls.push(match[1]);
      // Flip the cached snapshot so the post-invalidation refetch sees a DISCONNECTED card.
      state.connections = disconnectedConnections();
      await route.fulfill({ status: 204, body: '' });
      return;
    }
    await route.fulfill({ status: 204, body: '' });
  });
  await page.route(/https?:\/\/[^/]+\/api\/calendar\/calendars\/[^/]+\/enabled$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ preferencesRemoved: 0 }),
    });
  });
  await page.route(
    new RegExp(`https?://[^/]+/api/calendar/mailboxes/${MAILBOX_ID}/preferences$`),
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      });
    },
  );
  await page.route(/https?:\/\/[^/]+\/api\/calendar\/connect-intent$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ authorizationUrl: '/oauth2/authorization/google-calendar' }),
    });
  });
}

test.describe('Calendar settings page', () => {
  test('renders the empty state when no calendars are connected', async ({ page }) => {
    const calendarState: CalendarMockState = { connections: [], disconnectCalls: [] };
    const chromeState = createChromeMockState();

    await seedAuthenticatedSession(page, 'en');
    await installCalendarMocks(page, calendarState);
    await installChromeApiMock(page, chromeState);

    await page.goto(`/settings/mailboxes/${MAILBOX_ID}/calendar`, {
      waitUntil: 'domcontentloaded',
    });

    await expect(page.getByTestId('calendar-empty-state')).toBeVisible();
    await expect(page.getByText('Connect Google Calendar', { exact: false })).toBeVisible();
  });

  test('renders a connected card with sub-calendars and the role-assignment section', async ({
    page,
  }) => {
    const calendarState: CalendarMockState = {
      connections: populatedConnections(),
      disconnectCalls: [],
    };
    const chromeState = createChromeMockState();

    await seedAuthenticatedSession(page, 'en');
    await installCalendarMocks(page, calendarState);
    await installChromeApiMock(page, chromeState);

    await page.goto(`/settings/mailboxes/${MAILBOX_ID}/calendar`, {
      waitUntil: 'domcontentloaded',
    });

    await expect(page.getByTestId(`calendar-connection-card-${CONNECTION_ID}`)).toBeVisible();
    await expect(page.getByTestId(`calendar-status-badge-${CONNECTION_ID}`)).toContainText(
      'Connected',
    );
    await expect(page.getByTestId('calendar-list')).toBeVisible();
    await expect(page.getByText('Primary')).toBeVisible();
    await expect(page.getByText('Work')).toBeVisible();
    await expect(page.getByTestId('calendar-role-assignment')).toBeVisible();
    await expect(page.getByTestId('calendar-role-freebusy')).toBeVisible();
    await expect(page.getByTestId('calendar-role-event-write')).toBeVisible();
    await expect(page.getByTestId('calendar-role-brief-source')).toBeVisible();
  });

  test('disconnect transitions the card to a Disconnected badge state', async ({ page }) => {
    const calendarState: CalendarMockState = {
      connections: populatedConnections(),
      disconnectCalls: [],
    };
    const chromeState = createChromeMockState();

    await seedAuthenticatedSession(page, 'en');
    await installCalendarMocks(page, calendarState);
    await installChromeApiMock(page, chromeState);

    await page.goto(`/settings/mailboxes/${MAILBOX_ID}/calendar`, {
      waitUntil: 'domcontentloaded',
    });

    await page.getByTestId(`calendar-card-menu-${CONNECTION_ID}`).click();
    await page.getByTestId(`calendar-disconnect-${CONNECTION_ID}`).click();

    await expect.poll(() => calendarState.disconnectCalls).toContain(CONNECTION_ID);
    await expect(page.getByTestId(`calendar-status-badge-${CONNECTION_ID}`)).toContainText(
      'Disconnected',
      { timeout: 10_000 },
    );
  });
});
