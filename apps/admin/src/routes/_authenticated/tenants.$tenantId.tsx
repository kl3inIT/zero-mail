import {createFileRoute, Link, useNavigate} from '@tanstack/react-router';
import {
    ActivityIcon,
    ArrowLeftIcon,
    CalendarDaysIcon,
    CheckCircle2Icon,
    ChevronDownIcon,
    ChevronLeftIcon,
    ChevronRightIcon,
    DownloadIcon,
    InboxIcon,
    LogInIcon,
    MailCheckIcon,
    MailXIcon,
    PauseIcon,
    SearchIcon,
    SettingsIcon,
    SlidersHorizontalIcon,
    Trash2Icon,
} from 'lucide-react';
import type {ReactNode} from 'react';
import {useMemo, useState} from 'react';
import type {DateRange} from 'react-day-picker';
import {z} from 'zod';

import {ConfirmTwiceDialog} from '@/components/ConfirmTwiceDialog';
import {Avatar, AvatarFallback} from '@/components/ui/avatar';
import {Badge} from '@/components/ui/badge';
import {Button, buttonVariants} from '@/components/ui/button';
import {Calendar} from '@/components/ui/calendar';
import {Card, CardContent} from '@/components/ui/card';
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {Input} from '@/components/ui/input';
import {Popover, PopoverContent, PopoverTrigger} from '@/components/ui/popover';
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {Tabs, TabsContent, TabsList, TabsTrigger} from '@/components/ui/tabs';
import type {
    TenantActivityResponse,
    TenantBillingResponse,
    TenantDeletionPreviewResponse,
    TenantDetailResponse,
    TenantDetailTab,
    TenantHealthResponse,
} from '@/features/tenants/tenants-api';
import {
    useTenantActivity,
    useTenantBilling,
    useTenantDeletionPreview,
    useTenantHealth,
    useTenantOverview,
    useTenantSpend,
} from '@/features/tenants/use-tenant-detail';
import {useTenantDelete} from '@/features/tenants/use-tenant-delete';
import {useTenantDisconnect} from '@/features/tenants/use-tenant-disconnect';
import {useTenantPause} from '@/features/tenants/use-tenant-pause';

const tenantDetailSearchSchema = z.object({
    tab: z
        .enum(['overview', 'activity', 'email', 'settings', 'billing'])
        .default('activity')
        .catch('activity'),
});

const integerFormatter = new Intl.NumberFormat();
const ACTIVITY_PAGE_SIZE = 10;

type TenantDialogAction = 'pause' | 'disconnect' | 'delete';
type TenantActivityEvent = TenantActivityResponse['events'][number];
type ActivityFilter = 'ALL' | TenantActivityEvent['eventType'];

export const Route = createFileRoute('/_authenticated/tenants/$tenantId')({
    validateSearch: tenantDetailSearchSchema,
    component: TenantDetailRoute,
});

function TenantDetailRoute() {
    const {tenantId} = Route.useParams();
    const {tab} = Route.useSearch();
    const navigate = useNavigate();
    const [dialogAction, setDialogAction] = useState<TenantDialogAction | null>(null);
    const [activityFilter, setActivityFilter] = useState<ActivityFilter>('ALL');
    const [activitySearch, setActivitySearch] = useState('');
    const [dateRange, setDateRange] = useState<DateRange>(() => lastDaysRange(7));
    const [selectedEventId, setSelectedEventId] = useState<string | null>(null);
    const [activityPage, setActivityPage] = useState(1);

    const overview = useTenantOverview(tenantId, {enabled: true});
    const health = useTenantHealth(tenantId, {enabled: true});
    const billing = useTenantBilling(tenantId, {enabled: true});
    const spend = useTenantSpend(tenantId, {enabled: tab === 'billing'});
    const activity = useTenantActivity(tenantId, {enabled: true});
    const deletionPreview = useTenantDeletionPreview(tenantId, dialogAction === 'delete');
    const pauseTenant = useTenantPause();
    const disconnectTenant = useTenantDisconnect();
    const deleteTenant = useTenantDelete();

    const overviewData = overview.data;
    const activityData = activity.data;
    const targetEmail = overviewData?.gmailAccountEmail ?? '';
    const actionOptions = useMemo(() => uniqueEventTypes(activityData?.events ?? []), [activityData?.events]);
    const filteredEvents = useMemo(
        () =>
            filterActivityEvents({
                events: activityData?.events ?? [],
                dateRange,
                activityFilter,
                search: activitySearch,
            }),
        [activityData?.events, dateRange, activityFilter, activitySearch],
    );
    const activityPageCount = Math.max(1, Math.ceil(filteredEvents.length / ACTIVITY_PAGE_SIZE));
    const currentActivityPage = Math.min(activityPage, activityPageCount);
    const paginatedEvents = useMemo(() => {
        const startIndex = (currentActivityPage - 1) * ACTIVITY_PAGE_SIZE;
        return filteredEvents.slice(startIndex, startIndex + ACTIVITY_PAGE_SIZE);
    }, [currentActivityPage, filteredEvents]);
    const selectedEvent =
        paginatedEvents.find((event) => event.eventId === selectedEventId) ?? paginatedEvents[0] ?? null;
    const dialogConfig = useMemo(
        () =>
            dialogAction
                ? buildDialogConfig({
                    action: dialogAction,
                    email: targetEmail,
                    deletionPreview: deletionPreview.data,
                    deletionPreviewLoading: deletionPreview.isLoading,
                })
                : null,
        [dialogAction, targetEmail, deletionPreview.data, deletionPreview.isLoading],
    );

    function changeTab(nextTab: string) {
        void navigate({
            to: '/tenants/$tenantId',
            params: {tenantId},
            search: {tab: nextTab as TenantDetailTab},
        });
    }

    function exportCsv() {
        const rows = filteredEvents.map((event) => [
            formatDateTime(event.occurredAt),
            event.actionLabel,
            event.detail ?? '',
            event.status,
        ]);
        const csv = [['Thời gian', 'Hành động', 'Chi tiết', 'Trạng thái'], ...rows]
            .map((row) => row.map(csvCell).join(','))
            .join('\n');
        const blob = new Blob([csv], {type: 'text/csv;charset=utf-8'});
        const downloadUrl = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = downloadUrl;
        anchor.download = `tenant-activity-${tenantId}.csv`;
        anchor.click();
        URL.revokeObjectURL(downloadUrl);
    }

    function resetActivityList() {
        setActivityPage(1);
        setSelectedEventId(null);
    }

    return (
        <div className="min-w-0 space-y-5">
            <TenantBreadcrumb activeTab={tab} tenantId={tenantId}/>

            <CustomerProfileHeader
                tenantId={tenantId}
                overview={overviewData}
                health={health.data}
                billing={billing.data}
                activity={activityData}
                onAction={setDialogAction}
            />

            <Tabs value={tab} onValueChange={changeTab} className="min-w-0">
                <TabsList variant="line" className="w-full justify-start overflow-x-auto">
                    <TabsTrigger value="overview">Thông tin chung</TabsTrigger>
                    <TabsTrigger value="activity">Hoạt động</TabsTrigger>
                    <TabsTrigger value="email">Email</TabsTrigger>
                    <TabsTrigger value="settings">Cài đặt</TabsTrigger>
                    <TabsTrigger value="billing">Thanh toán</TabsTrigger>
                </TabsList>

                <TabsContent value="overview">
                    <PanelState isLoading={overview.isLoading || activity.isLoading}
                                isError={overview.isError || activity.isError}>
                        {overviewData && (
                            <OverviewTab overview={overviewData} activity={activityData} health={health.data}
                                         billing={billing.data}/>
                        )}
                    </PanelState>
                </TabsContent>

                <TabsContent value="activity">
                    <PanelState isLoading={activity.isLoading} isError={activity.isError}>
                        <div className="grid min-w-0 gap-4 xl:grid-cols-[minmax(0,1fr)_300px]">
                            <Card className="min-w-0">
                                <CardContent className="space-y-4 p-4">
                                    <div
                                        className="grid min-w-0 gap-2 md:grid-cols-2 xl:grid-cols-[220px_160px_minmax(180px,1fr)_112px]">
                                        <DateRangeControl
                                            value={dateRange}
                                            onChange={(nextRange) => {
                                                setDateRange(nextRange);
                                                resetActivityList();
                                            }}
                                        />
                                        <Select
                                            value={activityFilter}
                                            onValueChange={(value) => {
                                                if (value) {
                                                    setActivityFilter(value as ActivityFilter);
                                                    resetActivityList();
                                                }
                                            }}
                                        >
                                            <SelectTrigger className="h-9 w-full">
                                                <SelectValue>{activityFilter === 'ALL' ? 'Tất cả hành động' : eventTypeLabel(activityFilter)}</SelectValue>
                                            </SelectTrigger>
                                            <SelectContent>
                                                <SelectItem value="ALL">Tất cả hành động</SelectItem>
                                                {actionOptions.map((eventType) => (
                                                    <SelectItem key={eventType} value={eventType}>
                                                        {eventTypeLabel(eventType)}
                                                    </SelectItem>
                                                ))}
                                            </SelectContent>
                                        </Select>
                                        <div className="relative min-w-0">
                                            <SearchIcon
                                                className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"/>
                                            <Input
                                                aria-label="Tìm kiếm hoạt động"
                                                value={activitySearch}
                                                onChange={(event) => {
                                                    setActivitySearch(event.target.value);
                                                    resetActivityList();
                                                }}
                                                placeholder="Tìm kiếm hoạt động..."
                                                className="h-9 pl-8"
                                            />
                                        </div>
                                        <Button
                                            type="button"
                                            variant="outline"
                                            className="h-9 w-full justify-center whitespace-nowrap px-3"
                                            onClick={exportCsv}
                                        >
                                            <DownloadIcon className="size-4"/>
                                            Xuất CSV
                                        </Button>
                                    </div>

                                    <ActivityTable
                                        events={paginatedEvents}
                                        selectedEventId={selectedEvent?.eventId}
                                        onSelect={(event) => setSelectedEventId(event.eventId)}
                                    />
                                    <ActivityPagination
                                        page={currentActivityPage}
                                        pageSize={ACTIVITY_PAGE_SIZE}
                                        totalItems={filteredEvents.length}
                                        onPageChange={(nextPage) => {
                                            setActivityPage(nextPage);
                                            setSelectedEventId(null);
                                        }}
                                    />
                                </CardContent>
                            </Card>

                            <ActivityDetailPanel event={selectedEvent}/>
                        </div>
                    </PanelState>
                </TabsContent>

                <TabsContent value="email">
                    <PanelState isLoading={health.isLoading} isError={health.isError}>
                        {health.data && <EmailTab health={health.data} overview={overviewData}/>}
                    </PanelState>
                </TabsContent>

                <TabsContent value="settings">
                    <PanelState isLoading={overview.isLoading} isError={overview.isError}>
                        {overviewData && <SettingsTab overview={overviewData}/>}
                    </PanelState>
                </TabsContent>

                <TabsContent value="billing">
                    <PanelState isLoading={billing.isLoading || spend.isLoading}
                                isError={billing.isError || spend.isError}>
                        {billing.data && <BillingTab billing={billing.data} spend={spend.data}/>}
                    </PanelState>
                </TabsContent>
            </Tabs>

            {dialogConfig && (
                <ConfirmTwiceDialog
                    open={dialogAction !== null}
                    onOpenChange={(open) => {
                        if (!open) {
                            setDialogAction(null);
                        }
                    }}
                    actionLabel={dialogConfig.actionLabel}
                    targetLabel={dialogConfig.targetLabel}
                    consequences={dialogConfig.consequences}
                    confirmationToken={dialogConfig.confirmationToken}
                    finalButtonLabel={dialogConfig.finalButtonLabel}
                    onConfirm={async (reason) => {
                        if (dialogAction === 'pause') {
                            await pauseTenant.mutateAsync({tenantId, reason});
                        } else if (dialogAction === 'disconnect') {
                            await disconnectTenant.mutateAsync({tenantId, reason});
                        } else if (dialogAction === 'delete') {
                            await deleteTenant.mutateAsync({tenantId, reason, confirmEmail: targetEmail});
                        }
                        return {};
                    }}
                />
            )}
        </div>
    );
}

function TenantBreadcrumb({activeTab, tenantId}: { activeTab: TenantDetailTab; tenantId: string }) {
    return (
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Link to="/tenants" className={buttonVariants({variant: 'ghost', size: 'sm', className: 'px-0 text-ink'})}>
                <ArrowLeftIcon className="size-4"/>
                Khách hàng
            </Link>
            <span>/</span>
            <span>Chi tiết</span>
            <span>/</span>
            <span className="font-medium text-ink">{tabLabel(activeTab)}</span>
            <span className="sr-only">{tenantId}</span>
        </div>
    );
}

function CustomerProfileHeader({
                                   tenantId,
                                   overview,
                                   health,
                                   billing,
                                   activity,
                                   onAction,
                               }: {
    tenantId: string;
    overview?: TenantDetailResponse;
    health?: TenantHealthResponse;
    billing?: TenantBillingResponse;
    activity?: TenantActivityResponse;
    onAction: (action: TenantDialogAction) => void;
}) {
    const email = overview?.gmailAccountEmail ?? tenantId;
    return (
        <Card className="min-w-0">
            <CardContent className="p-5">
                <div
                    className="grid gap-5 xl:grid-cols-[minmax(330px,1.45fr)_repeat(3,minmax(150px,0.7fr))_140px] xl:items-center">
                    <div className="flex min-w-0 items-center gap-4">
                        <Avatar className="size-16" size="lg">
                            <AvatarFallback className="text-xl">{avatarInitial(email)}</AvatarFallback>
                        </Avatar>
                        <div className="min-w-0 space-y-2">
                            <h1 className="break-all text-lg font-semibold text-ink">{email}</h1>
                            <GmailStatusBadge
                                status={overview?.gmailConnectionStatus ?? health?.tokenRefreshStatus ?? 'DISCONNECTED'}/>
                            <div className="space-y-1 text-sm text-muted-foreground">
                                <div>Khách hàng từ: {formatDate(overview?.createdAt)}</div>
                                <div>Gói: {formatPlanName(billing?.plan)}</div>
                            </div>
                        </div>
                    </div>
                    <HeaderMetric
                        label="Tổng hoạt động"
                        value={activity ? formatInteger(activity.totalActivity7dCount) : '-'}
                        sub="7 ngày qua"
                    />
                    <HeaderMetric
                        label="Đăng nhập lần cuối"
                        value={activity?.lastLoginAt ? relativeTime(activity.lastLoginAt) : 'Chưa có'}
                        sub={activity?.lastLoginAt ? formatDateTime(activity.lastLoginAt) : 'Dữ liệu cũ chưa có'}
                    />
                    <HeaderMetric
                        label="Trạng thái"
                        value={<TenantStatusBadge status={overview?.status ?? 'DISCONNECTED'}/>}
                        sub={overview?.telegramStatus ? `Telegram: ${telegramStatusLabel(overview.telegramStatus)}` : 'Telegram: -'}
                    />
                    <DropdownMenu>
                        <DropdownMenuTrigger
                            render={
                                <Button type="button" variant="outline" className="w-full justify-between">
                                    Hành động
                                    <ChevronDownIcon className="size-4"/>
                                </Button>
                            }
                        />
                        <DropdownMenuContent align="end" className="w-48">
                            <DropdownMenuItem onClick={() => onAction('pause')}>
                                <PauseIcon className="size-4"/>
                                Tạm dừng
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => onAction('disconnect')}>
                                <MailXIcon className="size-4"/>
                                Ngắt Gmail
                            </DropdownMenuItem>
                            <DropdownMenuItem variant="destructive" onClick={() => onAction('delete')}>
                                <Trash2Icon className="size-4"/>
                                Xóa khách hàng
                            </DropdownMenuItem>
                        </DropdownMenuContent>
                    </DropdownMenu>
                </div>
            </CardContent>
        </Card>
    );
}

function HeaderMetric({label, value, sub}: { label: string; value: ReactNode; sub: string }) {
    return (
        <div className="min-w-0 border-t border-border pt-3 xl:border-l xl:border-t-0 xl:pl-5 xl:pt-0">
            <div className="text-sm font-medium text-ink">{label}</div>
            <div className="mt-2 min-h-8 text-2xl font-semibold text-ink">{value}</div>
            <div className="text-sm text-muted-foreground">{sub}</div>
        </div>
    );
}

function ActivityTable({
                           events,
                           selectedEventId,
                           onSelect,
                       }: {
    events: TenantActivityEvent[];
    selectedEventId?: string;
    onSelect: (event: TenantActivityEvent) => void;
}) {
    return (
        <div className="min-w-0 overflow-x-auto rounded-md border border-border">
            <Table className="min-w-[620px]">
                <TableHeader>
                    <TableRow>
                        <TableHead className="w-[170px]">Thời gian</TableHead>
                        <TableHead className="w-[160px]">Hành động</TableHead>
                        <TableHead>Chi tiết</TableHead>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {events.length === 0 && (
                        <TableRow>
                            <TableCell colSpan={3} className="h-28 text-center text-muted-foreground">
                                Không có hoạt động trong khoảng ngày này.
                            </TableCell>
                        </TableRow>
                    )}
                    {events.map((event) => (
                        <TableRow
                            key={event.eventId}
                            data-state={event.eventId === selectedEventId ? 'selected' : undefined}
                            className="cursor-pointer data-[state=selected]:bg-muted"
                            onClick={() => onSelect(event)}
                        >
                            <TableCell className="font-mono text-xs">{formatDateTime(event.occurredAt)}</TableCell>
                            <TableCell>
                <span className="inline-flex items-center gap-2 text-sm font-medium text-ink">
                  {eventIcon(event.eventType)}
                    {event.actionLabel}
                </span>
                            </TableCell>
                            <TableCell>
                                <div
                                    className="line-clamp-1 text-sm text-ink">{event.detail ?? 'Dữ liệu cũ chưa có'}</div>
                            </TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </div>
    );
}

function ActivityPagination({
                                page,
                                pageSize,
                                totalItems,
                                onPageChange,
                            }: {
    page: number;
    pageSize: number;
    totalItems: number;
    onPageChange: (page: number) => void;
}) {
    const pageCount = Math.max(1, Math.ceil(totalItems / pageSize));
    const fromItem = totalItems === 0 ? 0 : (page - 1) * pageSize + 1;
    const toItem = Math.min(page * pageSize, totalItems);

    return (
        <div
            className="flex flex-col gap-3 rounded-md border border-border px-3 py-2 sm:flex-row sm:items-center sm:justify-between">
            <div className="text-sm text-muted-foreground">
                {fromItem}-{toItem} / {formatInteger(totalItems)}
            </div>
            <div className="flex items-center gap-2">
                <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    aria-label="Trang trước"
                    disabled={page <= 1}
                    onClick={() => onPageChange(Math.max(1, page - 1))}
                >
                    <ChevronLeftIcon className="size-4"/>
                </Button>
                <div className="min-w-16 text-center text-sm text-muted-foreground">
                    {page} / {pageCount}
                </div>
                <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    aria-label="Trang sau"
                    disabled={page >= pageCount}
                    onClick={() => onPageChange(Math.min(pageCount, page + 1))}
                >
                    <ChevronRightIcon className="size-4"/>
                </Button>
            </div>
        </div>
    );
}

function ActivityDetailPanel({event}: { event: TenantActivityEvent | null }) {
    return (
        <Card className="min-w-0 xl:sticky xl:top-4">
            <CardContent className="p-0">
                {!event ? (
                    <div className="p-4 text-sm text-muted-foreground">Chọn một hoạt động để xem chi tiết.</div>
                ) : (
                    <div className="min-w-0">
                        <div className="space-y-3 border-b border-border bg-muted/30 p-4">
                            <div className="flex min-w-0 items-start gap-3">
                                <div
                                    className="rounded-md border border-border bg-card p-2">{eventIcon(event.eventType)}</div>
                                <div className="min-w-0 flex-1">
                                    <h2 className="text-sm font-semibold text-ink">Chi tiết hoạt động</h2>
                                    <div
                                        className="mt-1 truncate text-base font-semibold text-ink">{event.actionLabel}</div>
                                    <div className="mt-1 line-clamp-2 text-sm text-muted-foreground">
                                        {event.detail ?? 'Dữ liệu cũ chưa có'}
                                    </div>
                                </div>
                            </div>
                            <div className="flex flex-wrap items-center gap-2">
                                <EventStatusBadge status={event.status}/>
                                <Badge variant="outline">{eventTypeLabel(event.eventType)}</Badge>
                            </div>
                        </div>

                        <div className="space-y-4 p-4">
                            {event.legacyDataMissing && (
                                <div
                                    className="rounded-md border border-border bg-secondary px-3 py-2 text-xs text-muted-foreground">
                                    Một số trường thời lượng không có vì đây là dữ liệu cũ trước khi thêm bảng activity.
                                </div>
                            )}
                            <div className="grid gap-3">
                                <ActivityFact label="Thời gian" value={formatDateTime(event.occurredAt)}/>
                                <ActivityFact label="Thời lượng" value={formatDuration(event.durationSeconds)}/>
                                <ActivityFact label="Nguồn" value={event.source}/>
                            </div>
                        </div>
                    </div>
                )}
            </CardContent>
        </Card>
    );
}

function ActivityFact({label, value}: { label: string; value: ReactNode }) {
    return (
        <div className="rounded-md border border-border px-3 py-2">
            <div className="text-xs font-medium text-muted-foreground">{label}</div>
            <div className="mt-1 truncate text-sm font-semibold text-ink">{value}</div>
        </div>
    );
}

function OverviewTab({
                         overview,
                         activity,
                         health,
                         billing,
                     }: {
    overview: TenantDetailResponse;
    activity?: TenantActivityResponse;
    health?: TenantHealthResponse;
    billing?: TenantBillingResponse;
}) {
    return (
        <div className="grid gap-4 lg:grid-cols-2">
            <InfoPanel title="Thông tin khách hàng">
                <DetailRows>
                    <DetailRow label="Email" value={overview.gmailAccountEmail ?? 'Chưa kết nối Gmail'}/>
                    <DetailRow label="Mã khách hàng" value={overview.tenantId}/>
                    <DetailRow label="Ngày tạo" value={formatDateTime(overview.createdAt)}/>
                    <DetailRow label="Trạng thái" value={<TenantStatusBadge status={overview.status}/>}/>
                    <DetailRow label="Gmail" value={<GmailStatusBadge status={overview.gmailConnectionStatus}/>}/>
                    <DetailRow label="Telegram" value={telegramStatusLabel(overview.telegramStatus)}/>
                </DetailRows>
            </InfoPanel>
            <InfoPanel title="Tóm tắt sử dụng">
                <DetailRows>
                    <DetailRow label="Tổng hoạt động 7 ngày"
                               value={formatInteger(activity?.totalActivity7dCount ?? 0)}/>
                    <DetailRow label="Đăng nhập lần cuối" value={formatDateTime(activity?.lastLoginAt)}/>
                    <DetailRow label="Thời lượng trong app" value={formatDuration(activity?.totalAppDurationSeconds)}/>
                    <DetailRow label="Rule"
                               value={`${formatInteger(overview.enabledRulesCount)} / ${formatInteger(overview.rulesCount)} bật`}/>
                    <DetailRow label="Gói" value={formatPlanName(billing?.plan)}/>
                    <DetailRow label="Gmail watch" value={health?.watchStatus ?? 'Dữ liệu cũ chưa có'}/>
                </DetailRows>
            </InfoPanel>
        </div>
    );
}

function EmailTab({health, overview}: { health: TenantHealthResponse; overview?: TenantDetailResponse }) {
    return (
        <InfoPanel title="Email">
            <DetailRows>
                <DetailRow label="Tài khoản Gmail" value={overview?.gmailAccountEmail ?? 'Chưa kết nối Gmail'}/>
                <DetailRow label="Trạng thái Gmail" value={<GmailStatusBadge
                    status={overview?.gmailConnectionStatus ?? health.tokenRefreshStatus}/>}/>
                <DetailRow label="Refresh token" value={health.tokenRefreshStatus}/>
                <DetailRow label="Refresh gần nhất" value={formatDateTime(health.lastTokenRefreshAt)}/>
                <DetailRow label="Watch" value={health.watchStatus}/>
                <DetailRow label="Pub/Sub gần nhất" value={formatDateTime(health.lastPubSubPushAt)}/>
                <DetailRow label="Pub/Sub backlog" value={formatInteger(health.pubsubBacklogCount)}/>
            </DetailRows>
        </InfoPanel>
    );
}

function SettingsTab({overview}: { overview: TenantDetailResponse }) {
    return (
        <InfoPanel title="Cài đặt">
            <div className="space-y-4">
                <DetailRows>
                    <DetailRow label="Rule đang bật"
                               value={`${formatInteger(overview.enabledRulesCount)} / ${formatInteger(overview.rulesCount)}`}/>
                    <DetailRow label="Gmail" value={<GmailStatusBadge status={overview.gmailConnectionStatus}/>}/>
                    <DetailRow label="Telegram" value={telegramStatusLabel(overview.telegramStatus)}/>
                </DetailRows>
                <div className="flex flex-wrap gap-2">
                    {overview.enabledRuleNames.length === 0 ? (
                        <div className="text-sm text-muted-foreground">Không có rule đang bật.</div>
                    ) : (
                        overview.enabledRuleNames.map((ruleName, index) => (
                            <Badge key={`${ruleName}-${index}`} variant="outline">
                                {ruleName}
                            </Badge>
                        ))
                    )}
                </div>
            </div>
        </InfoPanel>
    );
}

function BillingTab({billing, spend}: {
    billing: TenantBillingResponse;
    spend?: {
        last7dCallCount: number;
        last30dCallCount: number;
        spendBucket7d: string;
        spendBucket30d: string;
        perFeatureCallCount: Record<string, number>
    }
}) {
    return (
        <div className="grid gap-4 lg:grid-cols-2">
            <InfoPanel title="Thanh toán">
                <DetailRows>
                    <DetailRow label="Gói" value={formatPlanName(billing.plan)}/>
                    <DetailRow label="Credit balance" value={`${formatInteger(billing.creditsBalance)} credit`}/>
                </DetailRows>
            </InfoPanel>
            <InfoPanel title="Chi phí">
                <DetailRows>
                    <DetailRow label="Lượt gọi 7 ngày" value={formatInteger(spend?.last7dCallCount ?? 0)}/>
                    <DetailRow label="Lượt gọi 30 ngày" value={formatInteger(spend?.last30dCallCount ?? 0)}/>
                    <DetailRow label="Nhóm 7 ngày" value={spend?.spendBucket7d ?? '-'}/>
                    <DetailRow label="Nhóm 30 ngày" value={spend?.spendBucket30d ?? '-'}/>
                </DetailRows>
            </InfoPanel>
        </div>
    );
}

function DateRangeControl({value, onChange}: { value: DateRange; onChange: (value: DateRange) => void }) {
    const label =
        value.from && value.to ? `${formatDateInput(value.from)} - ${formatDateInput(value.to)}` : 'Chọn khoảng ngày';
    return (
        <Popover>
            <PopoverTrigger
                render={
                    <Button type="button" variant="outline" className="h-9 w-full justify-between px-3 font-normal">
            <span className="inline-flex min-w-0 items-center gap-2">
              <CalendarDaysIcon className="size-4 shrink-0 text-muted-foreground"/>
              <span className="truncate">{label}</span>
            </span>
                        <ChevronDownIcon className="size-4 text-muted-foreground"/>
                    </Button>
                }
            />
            <PopoverContent align="start" className="w-auto max-w-[calc(100vw-2rem)] p-2">
                <Calendar
                    mode="range"
                    selected={value}
                    onSelect={(nextRange) => onChange(nextRange ?? {from: undefined})}
                    numberOfMonths={2}
                    captionLayout="dropdown"
                />
            </PopoverContent>
        </Popover>
    );
}

function PanelState({isLoading, isError, children}: { isLoading: boolean; isError: boolean; children: ReactNode }) {
    if (isLoading) {
        return (
            <Card>
                <CardContent className="py-8 text-sm text-muted-foreground">Đang tải dữ liệu khách hàng.</CardContent>
            </Card>
        );
    }
    if (isError) {
        return (
            <Card>
                <CardContent className="py-8 text-sm text-destructive">Không tải được dữ liệu khách hàng.</CardContent>
            </Card>
        );
    }
    return <>{children}</>;
}

function InfoPanel({title, children}: { title: string; children: ReactNode }) {
    return (
        <Card className="min-w-0">
            <CardContent className="space-y-4 p-4">
                <h2 className="text-sm font-semibold text-ink">{title}</h2>
                {children}
            </CardContent>
        </Card>
    );
}

function DetailRows({children}: { children: ReactNode }) {
    return <div className="divide-y divide-border">{children}</div>;
}

function DetailRow({label, value}: { label: string; value: ReactNode }) {
    return (
        <div className="grid min-w-0 gap-2 py-3 first:pt-0 last:pb-0 sm:grid-cols-[160px_minmax(0,1fr)] sm:items-start">
            <div className="text-xs font-medium text-muted-foreground">{label}:</div>
            <div className="min-w-0 break-words text-sm font-medium text-ink">{value}</div>
        </div>
    );
}

function GmailStatusBadge({status}: { status: string }) {
    if (status === 'CONNECTED') {
        return (
            <Badge variant="outline" className="gap-1">
                <CheckCircle2Icon className="size-3.5"/>
                Đã kết nối Gmail
            </Badge>
        );
    }
    return <Badge variant="secondary">Đã ngắt kết nối Gmail</Badge>;
}

function TenantStatusBadge({status}: { status: TenantDetailResponse['status'] }) {
    if (status === 'ACTIVE') {
        return <Badge>Hoạt động</Badge>;
    }
    if (status === 'PAUSED') {
        return <Badge variant="outline">Tạm dừng</Badge>;
    }
    return <Badge variant="secondary">Đã ngắt kết nối</Badge>;
}

function EventStatusBadge({status}: { status: string }) {
    if (status === 'SUCCESS' || status === 'COMMITTED') {
        return <Badge variant="outline">Thành công</Badge>;
    }
    if (status === 'BLOCKED') {
        return <Badge variant="secondary">Bị chặn</Badge>;
    }
    if (status === 'PENDING') {
        return <Badge variant="secondary">Đang xử lý</Badge>;
    }
    if (status === 'FAILED') {
        return <Badge variant="destructive">Thất bại</Badge>;
    }
    return <Badge variant="secondary">Chưa rõ</Badge>;
}

function eventIcon(eventType: string) {
    if (eventType === 'LOGIN' || eventType === 'LOGOUT') {
        return <LogInIcon className="size-4 text-muted-foreground"/>;
    }
    if (eventType.startsWith('GMAIL')) {
        return <MailCheckIcon className="size-4 text-muted-foreground"/>;
    }
    if (eventType.startsWith('RULE')) {
        return <SlidersHorizontalIcon className="size-4 text-muted-foreground"/>;
    }
    if (eventType === 'CHAT_SESSION' || eventType === 'ASSISTANT_ACTION') {
        return <ActivityIcon className="size-4 text-muted-foreground"/>;
    }
    if (eventType.startsWith('TELEGRAM')) {
        return <InboxIcon className="size-4 text-muted-foreground"/>;
    }
    if (eventType === 'LLM_CALL') {
        return <SettingsIcon className="size-4 text-muted-foreground"/>;
    }
    return <ActivityIcon className="size-4 text-muted-foreground"/>;
}

function filterActivityEvents({
                                  events,
                                  dateRange,
                                  activityFilter,
                                  search,
                              }: {
    events: TenantActivityEvent[];
    dateRange: DateRange;
    activityFilter: ActivityFilter;
    search: string;
}) {
    const normalizedSearch = search.trim().toLowerCase();
    return events.filter((event) => {
        const occurredAt = new Date(event.occurredAt);
        if (dateRange.from && occurredAt < startOfDay(dateRange.from)) {
            return false;
        }
        if (dateRange.to && occurredAt > endOfDay(dateRange.to)) {
            return false;
        }
        if (activityFilter !== 'ALL' && event.eventType !== activityFilter) {
            return false;
        }
        if (!normalizedSearch) {
            return true;
        }
        return [event.actionLabel, event.detail]
            .filter(Boolean)
            .some((value) => value?.toLowerCase().includes(normalizedSearch));
    });
}

function uniqueEventTypes(events: TenantActivityEvent[]) {
    return Array.from(new Set(events.map((event) => event.eventType))).sort();
}

function lastDaysRange(days: number): DateRange {
    const to = new Date();
    const from = new Date(to);
    from.setDate(from.getDate() - days);
    return {from, to};
}

function startOfDay(value: Date) {
    return new Date(value.getFullYear(), value.getMonth(), value.getDate());
}

function endOfDay(value: Date) {
    return new Date(value.getFullYear(), value.getMonth(), value.getDate(), 23, 59, 59, 999);
}

function formatDateTime(value?: string): string {
    const date = parseDate(value);
    if (!date) {
        return 'Dữ liệu cũ chưa có';
    }
    return `${formatDateParts(date)} ${formatTimeParts(date)}`;
}

function formatDate(value?: string): string {
    const date = parseDate(value);
    if (!date) {
        return 'Dữ liệu cũ chưa có';
    }
    return formatDateParts(date);
}

function formatDateInput(value: Date): string {
    return formatDateParts(value);
}

function parseDate(value?: string): Date | null {
    if (!value) {
        return null;
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date;
}

function formatDateParts(value: Date): string {
    return [value.getDate(), value.getMonth() + 1, value.getFullYear()]
        .map((part) => String(part).padStart(2, '0'))
        .join('/');
}

function formatTimeParts(value: Date): string {
    return [value.getHours(), value.getMinutes()]
        .map((part) => String(part).padStart(2, '0'))
        .join(':');
}

function formatPlanName(plan?: string | null): string {
    if (!plan) {
        return 'Dữ liệu cũ chưa có';
    }
    const normalizedPlan = plan.trim().toUpperCase();
    const labels: Record<string, string> = {
        FREE: 'Free',
        PAY_AS_YOU_GO: 'Free',
        PLUS: 'Plus',
        PRO: 'Pro',
    };
    return labels[normalizedPlan] ?? plan.trim();
}

function formatInteger(value: number): string {
    return integerFormatter.format(value);
}

function formatDuration(seconds?: number | null): string {
    if (seconds === null || seconds === undefined) {
        return 'Dữ liệu cũ chưa có';
    }
    if (seconds < 60) {
        return `${seconds} giây`;
    }
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    if (minutes < 60) {
        return remainingSeconds > 0 ? `${minutes} phút ${remainingSeconds} giây` : `${minutes} phút`;
    }
    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    return remainingMinutes > 0 ? `${hours} giờ ${remainingMinutes} phút` : `${hours} giờ`;
}

function relativeTime(value: string): string {
    const diffMs = Date.now() - Date.parse(value);
    const diffMinutes = Math.max(0, Math.floor(diffMs / 60_000));
    if (diffMinutes < 60) {
        return `${diffMinutes} phút trước`;
    }
    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) {
        return `${diffHours} giờ trước`;
    }
    return `${Math.floor(diffHours / 24)} ngày trước`;
}

function eventTypeLabel(eventType: string): string {
    const labels: Record<string, string> = {
        LOGIN: 'Đăng nhập',
        LOGOUT: 'Đăng xuất',
        GMAIL_CONNECTED: 'Kết nối Gmail',
        GMAIL_DISCONNECTED: 'Ngắt Gmail',
        RULE_UPDATED: 'Rule',
        TRIAGE_ACTION: 'Triage',
        CHAT_SESSION: 'Chat',
        ASSISTANT_ACTION: 'Assistant',
        TELEGRAM_CONNECTED: 'Telegram',
        TELEGRAM_BLOCKED: 'Telegram bị chặn',
        TELEGRAM_DISCONNECTED: 'Ngắt Telegram',
        LLM_CALL: 'LLM',
    };
    return labels[eventType] ?? eventType;
}

function tabLabel(tab: TenantDetailTab): string {
    const labels: Record<TenantDetailTab, string> = {
        overview: 'Thông tin chung',
        activity: 'Nhật ký hoạt động',
        email: 'Email',
        settings: 'Cài đặt',
        billing: 'Thanh toán',
    };
    return labels[tab];
}

function telegramStatusLabel(status: string): string {
    if (status === 'CONNECTED') {
        return 'Đã kết nối';
    }
    if (status === 'BLOCKED') {
        return 'Bị chặn';
    }
    if (status === 'DISCONNECTED') {
        return 'Đã ngắt kết nối';
    }
    return 'Chưa kết nối';
}

function avatarInitial(value: string): string {
    return value.trim().slice(0, 1).toUpperCase() || 'K';
}

function csvCell(value: string): string {
    return `"${value.replaceAll('"', '""')}"`;
}

function buildDialogConfig({
                               action,
                               email,
                               deletionPreview,
                               deletionPreviewLoading,
                           }: {
    action: TenantDialogAction;
    email: string;
    deletionPreview?: TenantDeletionPreviewResponse;
    deletionPreviewLoading: boolean;
}) {
    if (action === 'pause') {
        return {
            actionLabel: 'Tạm dừng khách hàng',
            targetLabel: email || 'khách hàng',
            confirmationToken: 'pause',
            finalButtonLabel: 'Tạm dừng khách hàng',
            consequences: [
                'Phân loại tự động và kích hoạt quy tắc sẽ dừng cho khách hàng này.',
                'Siêu dữ liệu và lịch sử audit vẫn được giữ lại.',
                'Lý do sẽ được ghi vào nhật ký audit của quản trị viên.',
            ],
        };
    }
    if (action === 'disconnect') {
        return {
            actionLabel: 'Ngắt kết nối Gmail',
            targetLabel: email,
            confirmationToken: email,
            finalButtonLabel: 'Ngắt kết nối Gmail',
            consequences: [
                'Hệ thống sẽ xếp lịch thu hồi OAuth Gmail mà không hiển thị byte token cho mã admin.',
                'Các lượt push Gmail trong tương lai cho khách hàng này sẽ dừng sau khi thu hồi thành công.',
                'Lý do sẽ được ghi vào nhật ký audit của quản trị viên.',
            ],
        };
    }
    return {
        actionLabel: 'Xóa khách hàng',
        targetLabel: email,
        confirmationToken: email,
        finalButtonLabel: 'Xóa khách hàng',
        consequences: deletionConsequences(deletionPreview, deletionPreviewLoading),
    };
}

function deletionConsequences(preview?: TenantDeletionPreviewResponse, loading = false): string[] {
    if (loading) {
        return ['Đang tải bản xem trước xóa.', 'Hành động này không thể hoàn tác sau khi xác nhận cuối cùng.'];
    }
    if (!preview) {
        return ['Không lấy được bản xem trước xóa.', 'Hành động này không thể hoàn tác sau khi xác nhận cuối cùng.'];
    }
    return [
        `${preview.gmailConnections} bản ghi kết nối Gmail sẽ bị xóa.`,
        `${preview.chatSessions} phiên chat và ${preview.chatMessages} tin nhắn chat sẽ bị xóa.`,
        `${preview.rules} quy tắc và ${preview.triageAudits} bản ghi audit phân loại sẽ bị xóa.`,
        `${preview.byokCredentials} thông tin xác thực BYOK sẽ bị xóa.`,
        'Bản ghi khách hàng được xóa sau khi mục audit được ghi nhận.',
    ];
}
