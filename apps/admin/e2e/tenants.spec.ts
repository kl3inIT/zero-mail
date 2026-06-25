import {expect, type Page, test} from '@playwright/test';

const tenantId = '00000000-0000-4000-8000-000000000008';
const tenantEmail = 'founder@example.com';
const tenantDisplayName = 'Founder Workspace';
const ownerEmail = 'founder.owner@example.com';

test.beforeEach(async ({page}) => {
    await page.route('**/api/admin/me', async (route) => {
        await route.fulfill({json: {adminUserId: 'admin-1', email: 'admin@example.com', env: 'dev'}});
    });
});

test('operator can scan tenant operations and inspect tenant tabs', async ({page}) => {
    await page.setViewportSize({width: 1640, height: 920});
    const tenantRoutes = await setupTenantRoutes(page);

    await page.goto('/tenants');
    await expect(page.getByRole('heading', {name: 'Khách hàng'})).toBeVisible();
    await expect(page.getByTestId('tenant-kpi-total')).toContainText('2');
    await expect(page.getByTestId('tenant-kpi-active')).toContainText('1');
    await expect(page.getByTestId('tenant-kpi-recent')).toContainText('1');
    await expect(page.getByTestId('tenant-kpi-attention')).toHaveCount(0);
    await expect(page.getByTestId('tenant-date-preset')).toContainText('3 tháng qua');
    await expect(page.getByTestId('tenant-date-range')).toHaveCount(0);
    await page.getByTestId('tenant-date-preset').click();
    await page.getByRole('option', {name: 'Tùy chọn'}).click();
    await expect(page.getByTestId('tenant-date-range')).toBeVisible();
    await expect(page.getByRole('grid')).toHaveCount(2);
    await page.keyboard.press('Escape');
    const defaultFrom = dateOnly(tenantRoutes.listRequests[0]?.from);
    const defaultTo = dateOnly(tenantRoutes.listRequests[0]?.to);
    expect(defaultFrom).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(defaultTo).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(dateDiffInDays(defaultFrom, defaultTo)).toBeGreaterThanOrEqual(80);
    expect(dateDiffInDays(defaultFrom, defaultTo)).toBeLessThanOrEqual(95);
    await expect(page.getByText('118 email')).toHaveCount(0);
    await expect(page.getByText('91 triage')).toHaveCount(0);
    await expect(page.getByText('4 / 5 rule bật')).toHaveCount(0);
    await expect(page.getByRole('table').getByText('Assistant action')).toBeVisible();
    const founderRow = page.getByRole('row', {name: /Founder Workspace/});
    await expect(founderRow).toBeVisible();
    await expect(founderRow).toContainText(tenantEmail);
    await expect(founderRow).toContainText(ownerEmail);
    await expect(founderRow).not.toContainText('Cần xử lý');
    await expect(founderRow).not.toContainText(tenantId);
    await expect(founderRow).not.toContainText('May 20');
    await expect(founderRow).toContainText('Đã kết nối');
    await page.getByLabel('Tìm tenant, owner, Gmail hoặc mã tenant').fill('founder@example.com');
    await page.getByRole('button', {name: 'Áp dụng'}).click();
    await expect.poll(() => tenantRoutes.listRequests.at(-1)?.email).toBe('founder@example.com');
    await page.getByRole('link', {name: 'Chi tiết Founder Workspace'}).click();

    await expect(page).toHaveURL(/\/tenants\/00000000-0000-4000-8000-000000000008\?tab=activity/);
    await expect(page.getByRole('heading', {name: tenantDisplayName})).toBeVisible();
    await expect(page.getByText('Đã kết nối Gmail')).toBeVisible();
    await expect(page.getByText('Gói: Free')).toBeVisible();
    await expect(page.getByText('PAY_AS_YOU_GO')).toHaveCount(0);
    await expect(page.getByText('Tổng hoạt động')).toBeVisible();
    await expect(page.getByRole('tab', {name: 'Hoạt động'})).toBeVisible();
    const loginRow = page.getByRole('row', {name: /Đăng nhập/});
    await expect(loginRow).toBeVisible();
    await expect(loginRow).toContainText(/\d{2}\/\d{2}\/\d{4}/);
    await expect(page.getByText(/\d{2}\/\d{2}\/\d{4} - \d{2}\/\d{2}\/\d{4}/)).toBeVisible();
    await expect.poll(() => activityExportButtonSharesSearchRow(page)).toBe(true);
    await expect(page.getByText('1-10 / 12')).toBeVisible();
    await expect(page.getByText('Sự kiện 11')).toHaveCount(0);
    await page.getByRole('button', {name: 'Trang sau'}).click();
    await expect(page.getByText('11-12 / 12')).toBeVisible();
    await expect(page.getByRole('row', {name: /Sự kiện 11/})).toBeVisible();
    await page.getByRole('button', {name: 'Trang trước'}).click();
    await expect(page.getByRole('heading', {name: 'Chi tiết hoạt động'})).toBeVisible();
    await page.getByRole('row', {name: /Kết nối Gmail/}).click();
    await expect(page.getByText('Một số trường thời lượng không có')).toBeVisible();

    for (const tab of ['Thông tin chung', 'Email']) {
        await page.getByRole('tab', {name: tab}).click();
    }
    await page.getByRole('tab', {name: 'Cài đặt'}).click();
    await expect(page.getByText('Receipts')).toBeVisible();
    await expect(page.getByText('VIP')).toBeVisible();
    await page.getByRole('tab', {name: 'Thanh toán'}).click();
    await expect.poll(() => tenantRoutes.readTabs.size).toBe(5);

    await page.getByRole('button', {name: 'Hành động'}).click();
    await page.getByRole('menuitem', {name: 'Tạm dừng'}).click();
    await page.getByLabel('Lý do (ghi vào nhật ký audit)').fill('support ticket requested tenant pause');
    await page.getByRole('button', {name: 'Tiếp tục'}).click();
    await page.getByLabel('Nhập "pause" để xác nhận').fill('pause');
    await page.getByRole('button', {name: 'Tạm dừng khách hàng'}).click();

    await expect(page.getByText('Đã ghi nhận thao tác.')).toBeVisible();
    expect(tenantRoutes.actions).toContainEqual({
        action: 'pause',
        reason: 'support ticket requested tenant pause',
    });
});

test('delete tenant dialog loads deletion preview counts before final confirmation', async ({page}) => {
    await setupTenantRoutes(page);

    await page.goto(`/tenants/${tenantId}?tab=overview`);
    await expect(page.getByRole('heading', {name: tenantDisplayName})).toBeVisible();
    await page.getByRole('button', {name: 'Hành động'}).click();
    await page.getByRole('menuitem', {name: 'Xóa khách hàng'}).click();

    await expect(page.getByText('1 bản ghi kết nối Gmail sẽ bị xóa.')).toBeVisible();
    await expect(page.getByText('3 phiên chat và 9 tin nhắn chat sẽ bị xóa.')).toBeVisible();
    await expect(page.getByText('5 quy tắc và 4 bản ghi audit phân loại sẽ bị xóa.')).toBeVisible();
});

type TenantRoutes = {
    listRequests: Array<{ from?: string; to?: string; status?: string; email?: string }>;
    readTabs: Set<string>;
    actions: Array<{ action: string; reason: string }>;
};

async function setupTenantRoutes(page: Page): Promise<TenantRoutes> {
    const listRequests: Array<{ from?: string; to?: string; status?: string; email?: string }> = [];
    const readTabs = new Set<string>();
    const actions: Array<{ action: string; reason: string }> = [];
    await page.route('**/api/admin/tenants**', async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const path = url.pathname;
        const method = request.method();

        if (method === 'GET' && (path === '/api/admin/tenants' || path === '/api/admin/tenants/')) {
            listRequests.push({
                from: url.searchParams.get('from') ?? undefined,
                to: url.searchParams.get('to') ?? undefined,
                status: url.searchParams.get('status') ?? undefined,
                email: url.searchParams.get('email') ?? undefined,
            });
            await route.fulfill({
                json: {
                    rows: [
                        {
                            tenantId,
                            tenantDisplayName,
                            ownerEmail,
                            createdAt: '2026-05-20T01:00:00Z',
                            gmailAccountEmail: tenantEmail,
                            gmailAccountCount: 2,
                            connectedGmailAccountCount: 2,
                            status: 'ACTIVE',
                            gmailConnectionStatus: 'CONNECTED',
                            spendBucket7d: 'MEDIUM',
                            lastActivityAt: '2026-05-20T02:20:00Z',
                            lastActivityKind: 'ASSISTANT_ACTION',
                            totalRulesCount: 5,
                            enabledRulesCount: 4,
                            enabledRuleNames: ['Receipts', 'VIP', 'Investor follow-up', 'Newsletter archive'],
                            observedEmail30dCount: 118,
                            triageAction30dCount: 91,
                            failedTriageAction30dCount: 2,
                            outboundAction30dCount: 6,
                            blockedOutboundAction30dCount: 1,
                            chatSessionCount: 7,
                            lastChatSessionAt: '2026-05-20T02:15:00Z',
                            assistantAction30dCount: 3,
                            llmCall30dCount: 142,
                            creditBalance: 4200,
                            pubsubBacklogCount: 0,
                            gmailWatchStatus: 'WATCHING',
                            telegramStatus: 'CONNECTED',
                            telegramLastActiveAt: '2026-05-20T02:16:00Z',
                            autoSendRulesEnabled: true,
                        },
                        {
                            tenantId: '00000000-0000-4000-8000-000000000009',
                            tenantDisplayName: 'Quiet Workspace',
                            ownerEmail: null,
                            createdAt: '2026-05-19T01:00:00Z',
                            gmailAccountEmail: 'quiet@example.com',
                            gmailAccountCount: 1,
                            connectedGmailAccountCount: 0,
                            status: 'DISCONNECTED',
                            gmailConnectionStatus: 'DISCONNECTED',
                            spendBucket7d: 'LOW',
                            lastActivityAt: '2026-05-19T01:00:00Z',
                            lastActivityKind: 'TENANT_CREATED',
                            totalRulesCount: 0,
                            enabledRulesCount: 0,
                            enabledRuleNames: [],
                            observedEmail30dCount: 0,
                            triageAction30dCount: 0,
                            failedTriageAction30dCount: 0,
                            outboundAction30dCount: 0,
                            blockedOutboundAction30dCount: 0,
                            chatSessionCount: 0,
                            lastChatSessionAt: null,
                            assistantAction30dCount: 0,
                            llmCall30dCount: 0,
                            creditBalance: 0,
                            pubsubBacklogCount: 0,
                            gmailWatchStatus: 'NO_CONNECTION',
                            telegramStatus: 'NO_CONNECTION',
                            telegramLastActiveAt: null,
                            autoSendRulesEnabled: false,
                        },
                    ],
                    nextCursor: null,
                    hasNextPage: false,
                    summary: {
                        totalCount: 2,
                        activeCount: 1,
                        pausedCount: 0,
                        disconnectedCount: 1,
                        gmailConnectedCount: 1,
                        telegramConnectedCount: 1,
                        activeLast24hCount: 1,
                        activeLast7dCount: 1,
                        gmailUnhealthyCount: 1,
                        automationFailure30dCount: 3,
                        outboundBlocked30dCount: 1,
                        lowCreditCount: 1,
                    },
                },
            });
            return;
        }

        if (method === 'GET' && path.endsWith('/overview')) {
            readTabs.add('overview');
            await route.fulfill({
                json: {
                    tenantId,
                    tenantDisplayName,
                    ownerEmail,
                    createdAt: '2026-05-20T01:00:00Z',
                    gmailAccountEmail: tenantEmail,
                    gmailAccountCount: 2,
                    connectedGmailAccountCount: 2,
                    status: 'ACTIVE',
                    gmailConnectionStatus: 'CONNECTED',
                    telegramStatus: 'CONNECTED',
                    lastActivityAt: '2026-05-20T02:00:00Z',
                    rulesCount: 5,
                    enabledRulesCount: 4,
                    enabledRuleNames: ['Receipts', 'VIP', 'Investor follow-up', 'Newsletter archive'],
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
                    totalActivity7dCount: 48,
                    lastLoginAt: new Date().toISOString(),
                    totalAppDurationSeconds: 420,
                    events: activityEvents(),
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
            actions.push({action: 'pause', reason: requestBody.reason ?? ''});
            await route.fulfill({status: 204});
            return;
        }

        if (method === 'POST' && path.endsWith('/disconnect')) {
            await route.fulfill({status: 204});
            return;
        }

        if (method === 'POST' && path.endsWith('/delete')) {
            await route.fulfill({status: 204});
            return;
        }

        await route.fulfill({status: 404});
    });
    return {listRequests, readTabs, actions};
}

function activityEvents() {
    const now = Date.now();
    return [
        {
            eventId: '10000000-0000-4000-8000-000000000001',
            occurredAt: new Date(now - 10 * 60_000).toISOString(),
            eventType: 'LOGIN',
            actionLabel: 'Đăng nhập',
            detail: 'Đăng nhập thành công qua web',
            status: 'SUCCESS',
            durationSeconds: 420,
            source: 'AUTH',
            legacyDataMissing: false,
        },
        {
            eventId: '10000000-0000-4000-8000-000000000002',
            occurredAt: new Date(now - 20 * 60_000).toISOString(),
            eventType: 'GMAIL_CONNECTED',
            actionLabel: 'Kết nối Gmail',
            detail: 'Kết nối tài khoản Gmail thành công',
            status: 'SUCCESS',
            durationSeconds: null,
            source: 'LEGACY_GMAIL',
            legacyDataMissing: true,
        },
        {
            eventId: '10000000-0000-4000-8000-000000000003',
            occurredAt: new Date(now - 30 * 60_000).toISOString(),
            eventType: 'RULE_UPDATED',
            actionLabel: 'Bật rule',
            detail: 'Rule "Receipts"',
            status: 'SUCCESS',
            durationSeconds: null,
            source: 'LEGACY_RULE',
            legacyDataMissing: true,
        },
        ...Array.from({length: 9}, (_, index) => {
            const eventIndex = index + 4;
            return {
                eventId: `10000000-0000-4000-8000-${String(eventIndex).padStart(12, '0')}`,
                occurredAt: new Date(now - eventIndex * 10 * 60_000).toISOString(),
                eventType: 'ASSISTANT_ACTION',
                actionLabel: `Sự kiện ${eventIndex}`,
                detail: `Chi tiết hoạt động ${eventIndex}`,
                status: 'SUCCESS',
                durationSeconds: null,
                source: 'LEGACY_ASSISTANT',
                legacyDataMissing: true,
            };
        }),
    ];
}

function dateDiffInDays(from?: string, to?: string): number {
    if (!from || !to) {
        return 0;
    }
    return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000);
}

function dateOnly(value?: string): string | undefined {
    return value?.slice(0, 10);
}

async function activityExportButtonSharesSearchRow(page: Page): Promise<boolean> {
    const searchBox = await page.getByLabel('Tìm kiếm hoạt động').boundingBox();
    const exportBox = await page.getByRole('button', {name: 'Xuất CSV'}).boundingBox();
    if (!searchBox || !exportBox) {
        return false;
    }
    return Math.abs(exportBox.y - searchBox.y) <= 2 && exportBox.x >= searchBox.x + searchBox.width;
}
