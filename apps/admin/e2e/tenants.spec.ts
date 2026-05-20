import { expect, type Page, test } from '@playwright/test';

const tenantId = '00000000-0000-4000-8000-000000000008';
const tenantEmail = 'founder@example.com';

test.beforeEach(async ({ page }) => {
  await page.route('**/api/admin/me', async (route) => {
    await route.fulfill({ json: { adminUserId: 'admin-1', email: 'admin@example.com', env: 'dev' } });
  });
});

test('operator can inspect tenant tabs and pause with confirm-twice', async ({ page }) => {
  const tenantRoutes = await setupTenantRoutes(page);

  await page.goto('/tenants');
  await expect(page.getByRole('heading', { name: 'Tenants' })).toBeVisible();
  await expect(page.getByRole('row', { name: /founder@example.com/ })).toBeVisible();
  await page.getByRole('link', { name: 'View details' }).click();

  await expect(page).toHaveURL(/\/tenants\/00000000-0000-4000-8000-000000000008\?tab=overview/);
  await expect(page.getByRole('heading', { name: tenantEmail })).toBeVisible();

  for (const tab of ['Health', 'Billing', 'Spend', 'Activity']) {
    await page.getByRole('tab', { name: tab }).click();
    await expect(page).toHaveURL(new RegExp(`tab=${tab.toLowerCase()}`));
  }
  await expect.poll(() => tenantRoutes.readTabs.size).toBe(5);

  const showDetails = page.getByRole('button', { name: 'Show details' });
  await expect(showDetails).toBeDisabled();
  await showDetails.hover({ force: true });
  await expect(
    page.getByText('Session detail inspection is deferred to v1.3+ via tenant-bound support ticket grant.'),
  ).toBeVisible();

  await page.getByRole('tab', { name: 'Overview' }).click();
  await page.getByRole('button', { name: 'Pause' }).click();
  await page.getByLabel('Reason (recorded in audit log)').fill('support ticket requested tenant pause');
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByLabel('Type "pause" to confirm').fill('pause');
  await page.getByRole('button', { name: 'Pause tenant' }).click();

  await expect(page.getByText('Action recorded. Audit row recorded.')).toBeVisible();
  expect(tenantRoutes.actions).toContainEqual({
    action: 'pause',
    reason: 'support ticket requested tenant pause',
  });
});

test('delete tenant dialog loads deletion preview counts before final confirmation', async ({ page }) => {
  await setupTenantRoutes(page);

  await page.goto(`/tenants/${tenantId}?tab=overview`);
  await expect(page.getByRole('heading', { name: tenantEmail })).toBeVisible();
  await page.getByRole('button', { name: 'Delete tenant' }).click();

  await expect(page.getByText('1 Gmail connection row(s) will be removed.')).toBeVisible();
  await expect(page.getByText('3 chat session row(s) and 9 chat message row(s) will be removed.')).toBeVisible();
  await expect(page.getByText('5 rule row(s) and 4 triage audit row(s) will be removed.')).toBeVisible();
});

type TenantRoutes = {
  readTabs: Set<string>;
  actions: Array<{ action: string; reason: string }>;
};

async function setupTenantRoutes(page: Page): Promise<TenantRoutes> {
  const readTabs = new Set<string>();
  const actions: Array<{ action: string; reason: string }> = [];
  await page.route('**/api/admin/tenants**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    if (method === 'GET' && (path === '/api/admin/tenants' || path === '/api/admin/tenants/')) {
      await route.fulfill({
        json: {
          rows: [
            {
              tenantId,
              createdAt: '2026-05-20T01:00:00Z',
              gmailAccountEmail: tenantEmail,
              status: 'ACTIVE',
              spendBucket7d: 'LOW',
            },
          ],
          nextCursor: null,
          hasNextPage: false,
        },
      });
      return;
    }

    if (method === 'GET' && path.endsWith('/overview')) {
      readTabs.add('overview');
      await route.fulfill({
        json: {
          tenantId,
          createdAt: '2026-05-20T01:00:00Z',
          gmailAccountEmail: tenantEmail,
          status: 'ACTIVE',
          lastActivityAt: '2026-05-20T02:00:00Z',
          rulesCount: 5,
        },
      });
      return;
    }

    if (method === 'GET' && path.endsWith('/health')) {
      readTabs.add('health');
      await route.fulfill({
        json: {
          tokenRefreshStatus: 'CONNECTED',
          lastTokenRefreshAt: '2026-05-20T02:10:00Z',
          watchStatus: 'WATCHING',
          lastPubSubPushAt: '2026-05-20T02:12:00Z',
          pubsubBacklogCount: 0,
        },
      });
      return;
    }

    if (method === 'GET' && path.endsWith('/billing')) {
      readTabs.add('billing');
      await route.fulfill({
        json: {
          creditsBalance: 4200,
          plan: 'PAY_AS_YOU_GO',
          lastTopUpAt: '2026-05-19T10:00:00Z',
        },
      });
      return;
    }

    if (method === 'GET' && path.endsWith('/spend')) {
      readTabs.add('spend');
      await route.fulfill({
        json: {
          last7dCallCount: 7,
          last30dCallCount: 21,
          spendBucket7d: 'LOW',
          spendBucket30d: 'MEDIUM',
          perFeatureCallCount: {
            CHAT: 10,
            TRIAGE: 11,
          },
        },
      });
      return;
    }

    if (method === 'GET' && path.endsWith('/activity')) {
      readTabs.add('activity');
      await route.fulfill({
        json: {
          last30dRuleFireCount: 12,
          chatSessionCount: 2,
          lastChatSessionAt: '2026-05-20T02:15:00Z',
          lastChatModelSelection: 'openrouter:anthropic/claude-sonnet',
        },
      });
      return;
    }

    if (method === 'GET' && path.endsWith('/deletion-preview')) {
      await route.fulfill({
        json: {
          gmailConnections: 1,
          chatSessions: 3,
          rules: 5,
          triageAudits: 4,
          chatMessages: 9,
          byokCredentials: 1,
        },
      });
      return;
    }

    if (method === 'POST' && path.endsWith('/pause')) {
      const requestBody = JSON.parse(request.postData() ?? '{}') as { reason?: string };
      actions.push({ action: 'pause', reason: requestBody.reason ?? '' });
      await route.fulfill({ status: 204 });
      return;
    }

    if (method === 'POST' && path.endsWith('/disconnect')) {
      await route.fulfill({ status: 204 });
      return;
    }

    if (method === 'POST' && path.endsWith('/delete')) {
      await route.fulfill({ status: 204 });
      return;
    }

    await route.fulfill({ status: 404 });
  });
  return { readTabs, actions };
}
