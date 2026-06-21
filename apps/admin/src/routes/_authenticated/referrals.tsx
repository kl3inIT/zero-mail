import { createFileRoute } from '@tanstack/react-router';
import {
  CalendarIcon,
  ChevronDownIcon,
  ClockIcon,
  EyeIcon,
  GiftIcon,
  ImageIcon,
  InfoIcon,
  Loader2Icon,
  MoreVerticalIcon,
  PencilIcon,
  RefreshCwIcon,
  SettingsIcon,
  TrophyIcon,
  UploadIcon,
  UsersIcon,
} from 'lucide-react';
import type { ChangeEvent, ReactNode } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Calendar } from '@/components/ui/calendar';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import {
  referralCampaignBannerUrl,
  subscribeReferralDashboard,
  type AdminReferralCampaignCreateRequest,
  type AdminReferralCampaignResponse,
  type AdminReferralCampaignStatus,
  type AdminReferralDashboardResponse,
  type AdminReferralLeaderboardRowResponse,
} from '@/features/referrals/referrals-api';
import {
  useCreateReferralCampaign,
  useReferralCampaigns,
  useReferralDashboard,
  useUpdateReferralCampaign,
  useUpdateReferralCampaignStatus,
  useUploadReferralCampaignBanner,
} from '@/features/referrals/use-referrals';

export const Route = createFileRoute('/_authenticated/referrals')({
  component: ReferralRoute,
});

const integerFormatter = new Intl.NumberFormat();
const CONFIG_STATUS_OPTIONS: Array<{ status: AdminReferralCampaignStatus; label: string }> = [
  { status: 'ARCHIVED', label: 'Tắt' },
  { status: 'ACTIVE', label: 'Bật' },
  { status: 'ENDED', label: 'Kết thúc' },
];
const CAMPAIGN_STATUS_ORDER: Record<AdminReferralCampaignStatus, number> = {
  ACTIVE: 0,
  DRAFT: 1,
  PAUSED: 2,
  ENDED: 3,
  ARCHIVED: 4,
};
const TIME_HOUR_OPTIONS = Array.from({ length: 24 }, (_, hour) =>
  hour.toString().padStart(2, '0'),
);
const TIME_MINUTE_OPTIONS = Array.from({ length: 60 }, (_, minute) =>
  minute.toString().padStart(2, '0'),
);
const MAX_BANNER_UPLOAD_BYTES = 5 * 1024 * 1024;
const ALLOWED_BANNER_UPLOAD_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

type CampaignDraft = {
  name: string;
  campaignCode: string;
  slug: string;
  description: string;
  startsAt: string;
  endsAt: string;
  webBannerEnabled: boolean;
  countdownEnabled: boolean;
  leaderboardEnabled: boolean;
  rewardRankCutoff: number;
  rewardNotificationText: string;
};

function ReferralRoute() {
  const [manualSelectedCampaignId, setManualSelectedCampaignId] = useState<string | undefined>();
  const [streamDashboard, setStreamDashboard] = useState<{
    key: string;
    dashboard: AdminReferralDashboardResponse;
  }>();
  const campaignQuery = useReferralCampaigns();
  const campaigns = useMemo(
    () => [...(campaignQuery.data?.campaigns ?? [])].sort(compareCampaigns),
    [campaignQuery.data?.campaigns],
  );
  const fallbackCampaign =
    campaigns.find((campaign) => campaign.status === 'ACTIVE') ?? campaigns[0];
  const selectedCampaignId =
    manualSelectedCampaignId &&
    campaigns.some((campaign) => campaign.campaignId === manualSelectedCampaignId)
      ? manualSelectedCampaignId
      : fallbackCampaign?.campaignId;
  const selectedCampaign = campaigns.find((campaign) => campaign.campaignId === selectedCampaignId);
  const defaultFromDateInput = selectedCampaign
    ? dateInputValue(new Date(selectedCampaign.startsAt))
    : firstDayOfMonthInput(new Date());
  const defaultToDateInput = selectedCampaign
    ? dateInputValue(new Date(selectedCampaign.endsAt))
    : dateInputValue(new Date());
  const fromDateInput = defaultFromDateInput;
  const toDateInput = defaultToDateInput;

  const dashboardQueryInput = useMemo(
    () =>
      selectedCampaign
        ? {
            campaignId: selectedCampaign.campaignId,
            from: startOfDateInput(fromDateInput).toISOString(),
            to: endOfDateInput(toDateInput).toISOString(),
          }
        : undefined,
    [fromDateInput, selectedCampaign, toDateInput],
  );
  const dashboardStreamKey = dashboardQueryInput
    ? `${dashboardQueryInput.campaignId}:${dashboardQueryInput.from}:${dashboardQueryInput.to}`
    : undefined;
  const dashboardQuery = useReferralDashboard(dashboardQueryInput);
  const dashboard =
    streamDashboard && streamDashboard.key === dashboardStreamKey
      ? streamDashboard.dashboard
      : dashboardQuery.data;
  const createCampaignMutation = useCreateReferralCampaign();
  const statusMutation = useUpdateReferralCampaignStatus();

  useEffect(() => {
    if (!dashboardQueryInput || !dashboardStreamKey) return undefined;
    const subscription = subscribeReferralDashboard(
      dashboardQueryInput,
      (nextDashboard) => setStreamDashboard({ key: dashboardStreamKey, dashboard: nextDashboard }),
      () => undefined,
    );
    return () => subscription.close();
  }, [dashboardQueryInput, dashboardStreamKey]);

  function handleCreateCampaign() {
    createCampaignMutation.mutate(createDefaultCampaign(), {
      onSuccess: (createdCampaign) => setManualSelectedCampaignId(createdCampaign.campaignId),
    });
  }

  function handleStatusChange(status: AdminReferralCampaignStatus) {
    if (!selectedCampaign || selectedCampaign.status === status) return;
    statusMutation.mutate({ campaignId: selectedCampaign.campaignId, status });
  }

  return (
    <div className="min-w-0 space-y-4">
      <header className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-ink text-2xl font-semibold">Quản lý sự kiện referral</h1>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <CampaignSelect
            campaigns={campaigns}
            selectedCampaignId={selectedCampaignId}
            onChange={setManualSelectedCampaignId}
          />
          <Button
            type="button"
            onClick={handleCreateCampaign}
            disabled={createCampaignMutation.isPending}
          >
            {createCampaignMutation.isPending ? (
              <Loader2Icon className="size-4 animate-spin" />
            ) : (
              <GiftIcon className="size-4" />
            )}
            Tạo sự kiện mới
          </Button>
        </div>
      </header>

      <Tabs defaultValue="realtime" className="gap-4">
        <TabsList variant="line" aria-label="Referral event sections">
          <TabsTrigger value="realtime">Theo dõi realtime</TabsTrigger>
          <TabsTrigger value="settings">Cấu hình sự kiện</TabsTrigger>
        </TabsList>

        <TabsContent value="realtime">
          <RealtimeSection
            selectedCampaign={selectedCampaign}
            dashboard={dashboard}
            loading={campaignQuery.isLoading || dashboardQuery.isLoading}
          />
        </TabsContent>

        <TabsContent value="settings">
          <SettingsSection
            campaigns={campaigns}
            selectedCampaign={selectedCampaign}
            loading={campaignQuery.isLoading}
            onSelectCampaign={setManualSelectedCampaignId}
            onStatusChange={handleStatusChange}
          />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function RealtimeSection({
  selectedCampaign,
  dashboard,
  loading,
}: {
  selectedCampaign: AdminReferralCampaignResponse | undefined;
  dashboard: AdminReferralDashboardResponse | undefined;
  loading: boolean;
}) {
  if (!selectedCampaign && !loading) {
    return <EmptyCampaignState />;
  }

  if (selectedCampaign || loading) {
    return (
      <RealtimeDashboardScreen
        selectedCampaign={selectedCampaign}
        dashboard={dashboard}
        loading={loading}
      />
    );
  }

  return <EmptyCampaignState />;
}

function RealtimeDashboardScreen({
  selectedCampaign,
  dashboard,
  loading,
}: {
  selectedCampaign: AdminReferralCampaignResponse | undefined;
  dashboard: AdminReferralDashboardResponse | undefined;
  loading: boolean;
}) {
  const [now, setNow] = useState(() => Date.now());
  const topTenant = dashboard?.currentTopTenant;
  const leaderboardRows = dashboard?.leaderboard ?? [];

  useEffect(() => {
    const intervalId = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(intervalId);
  }, []);

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div className="min-w-0">
          <h1 className="text-ink text-3xl font-semibold tracking-normal">
            Theo dõi sự kiện referral
          </h1>
          <div className="mt-6 flex flex-wrap items-center gap-3 text-sm">
            <Badge className="bg-primary px-3 py-1 text-primary-foreground">LIVE</Badge>
            <span className="bg-green size-2.5 rounded-full" aria-hidden="true" />
            <span className="text-muted-foreground">Cập nhật trực tiếp</span>
            <span className="bg-border h-5 w-px" aria-hidden="true" />
            <span className="text-muted-foreground">Cập nhật lần cuối:</span>
            <span className="text-ink font-medium tabular-nums">
              {dashboard?.snapshotAt ? formatClock(dashboard.snapshotAt) : '--:--:--'}
            </span>
            <RefreshCwIcon className="text-primary/50 size-4" />
          </div>
        </div>
      </div>

      <section className="grid gap-5 lg:grid-cols-3">
        <RealtimeMetricCard
          icon={<UsersIcon className="size-9" />}
          label="Tổng referral"
          value={
            loading ? (
              <Skeleton className="h-11 w-32" />
            ) : (
              formatInteger(dashboard?.totalSuccessfulReferrals ?? 0)
            )
          }
        />
        <RealtimeMetricCard
          icon={<TrophyIcon className="size-9" />}
          label="Top hiện tại"
          value={
            loading ? (
              <Skeleton className="h-9 w-44" />
            ) : (
              <span className="truncate">{topTenant?.tenantDisplayName ?? 'Chưa có'}</span>
            )
          }
          hint={
            topTenant ? `${formatInteger(topTenant.successfulReferrals)} referral` : 'Chưa có dữ liệu'
          }
          tabular={false}
        />
        <CountdownMetricCard
          endsAt={selectedCampaign?.endsAt}
          now={now}
          loading={loading}
        />
      </section>

      <section className="grid gap-5 xl:grid-cols-[minmax(0,0.9fr)_minmax(520px,1.1fr)]">
        <RealtimeLeaderboardCard rows={leaderboardRows} loading={loading} />
        <TopTenantReferralChart rows={leaderboardRows} loading={loading} />
      </section>
    </div>
  );
}

function _RealtimeDateRangeFilter({
  fromDateInput,
  toDateInput,
  onFromDateChange,
  onToDateChange,
}: {
  fromDateInput: string;
  toDateInput: string;
  onFromDateChange: (value: string) => void;
  onToDateChange: (value: string) => void;
}) {
  return (
    <Popover>
      <PopoverTrigger
        render={
          <Button
            type="button"
            variant="outline"
            className="h-12 min-w-[260px] justify-between gap-3 px-4 text-base font-normal shadow-sm"
          />
        }
      >
        <span className="flex min-w-0 items-center gap-3">
          <CalendarIcon className="text-ink size-5 shrink-0" />
          <span className="truncate">{formatDateRange(fromDateInput, toDateInput)}</span>
        </span>
        <ChevronDownIcon className="text-muted-foreground size-4 shrink-0" />
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[min(28rem,calc(100vw-2rem))] p-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-2">
            <Label>Từ ngày</Label>
            <DateInput label="Từ ngày" value={fromDateInput} onChange={onFromDateChange} />
          </div>
          <div className="space-y-2">
            <Label>Đến ngày</Label>
            <DateInput label="Đến ngày" value={toDateInput} onChange={onToDateChange} />
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}

function RealtimeMetricCard({
  icon,
  label,
  value,
  hint,
  tabular = true,
}: {
  icon: ReactNode;
  label: string;
  value: ReactNode;
  hint?: string;
  tabular?: boolean;
}) {
  return (
    <Card className="min-h-36 justify-center py-5">
      <CardContent className="flex items-center gap-8 px-6">
        <div className="bg-primary/10 text-primary flex size-20 shrink-0 items-center justify-center rounded-full">
          {icon}
        </div>
        <div className="min-w-0 space-y-2">
          <div className="text-muted-foreground text-base">{label}</div>
          <div
            className={`text-ink truncate text-4xl font-semibold tracking-normal ${tabular ? 'tabular-nums' : ''}`}
          >
            {value}
          </div>
          {hint && <div className="text-muted-foreground text-base">{hint}</div>}
        </div>
      </CardContent>
    </Card>
  );
}

function CountdownMetricCard({
  endsAt,
  now,
  loading,
}: {
  endsAt: string | undefined;
  now: number;
  loading: boolean;
}) {
  const remaining = countdownParts(endsAt, now);
  return (
    <Card className="min-h-36 justify-center py-5">
      <CardContent className="space-y-5 px-6">
        <div className="text-muted-foreground text-base">Thời gian sự kiện còn lại</div>
        {loading ? (
          <Skeleton className="h-16 w-full" />
        ) : (
          <div className="grid grid-cols-[1fr_auto_1fr_auto_1fr_auto_1fr] items-start gap-2">
            <CountdownUnit value={remaining.days} label="Ngày" />
            <CountdownSeparator />
            <CountdownUnit value={remaining.hours} label="Giờ" />
            <CountdownSeparator />
            <CountdownUnit value={remaining.minutes} label="Phút" />
            <CountdownSeparator />
            <CountdownUnit value={remaining.seconds} label="Giây" />
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function CountdownUnit({ value, label }: { value: string; label: string }) {
  return (
    <div className="min-w-0 text-center">
      <div className="bg-primary/10 text-primary rounded-lg px-2 py-2 text-3xl font-semibold tabular-nums">
        {value}
      </div>
      <div className="text-muted-foreground mt-2 text-sm">{label}</div>
    </div>
  );
}

function CountdownSeparator() {
  return <div className="text-ink pt-2 text-3xl font-semibold">:</div>;
}

function RealtimeLeaderboardCard({
  rows,
  loading,
}: {
  rows: AdminReferralLeaderboardRowResponse[];
  loading: boolean;
}) {
  return (
    <Card className="min-h-[470px]">
      <CardHeader className="px-6">
        <CardTitle role="heading" aria-level={2} className="text-xl font-semibold">
          Bảng xếp hạng tenant theo số referral
        </CardTitle>
      </CardHeader>
      <CardContent className="px-6">
        <div className="border-border max-h-[360px] overflow-y-auto rounded-xl border [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          <Table>
            <TableHeader className="bg-card sticky top-0 z-10">
              <TableRow className="bg-muted/30">
                <TableHead className="w-24 px-6">Hạng</TableHead>
                <TableHead>Tenant</TableHead>
                <TableHead className="px-6 text-right">Số referral</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading && (
                <TableRow>
                  <TableCell colSpan={3} className="h-72">
                    <Skeleton className="h-56 w-full" />
                  </TableCell>
                </TableRow>
              )}
              {!loading && rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={3} className="text-muted-foreground h-72 text-center">
                    Chưa có tenant nào giới thiệu thành công.
                  </TableCell>
                </TableRow>
              )}
              {rows.map((row) => (
                <TableRow
                  key={row.tenantId}
                  className={row.rank === 1 ? 'bg-primary/5 hover:bg-primary/5' : undefined}
                >
                  <TableCell className="px-6">
                    <span
                      className={`inline-flex size-8 items-center justify-center rounded-full text-sm font-semibold ${rankBadgeClass(row.rank)}`}
                    >
                      {row.rank}
                    </span>
                  </TableCell>
                  <TableCell className="text-ink min-w-0 truncate text-base font-medium">
                    {row.tenantDisplayName}
                  </TableCell>
                  <TableCell className="text-ink px-6 text-right text-base font-semibold tabular-nums">
                    {formatInteger(row.successfulReferrals)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  );
}

function TopTenantReferralChart({
  rows,
  loading,
}: {
  rows: AdminReferralLeaderboardRowResponse[];
  loading: boolean;
}) {
  const visibleRows = rows.slice(0, 8);
  const scaleMax = niceScaleMax(
    Math.max(1, ...visibleRows.map((row) => row.successfulReferrals)),
  );
  const axisTicks = [0, Math.round(scaleMax * 0.25), Math.round(scaleMax * 0.5), Math.round(scaleMax * 0.75), scaleMax];

  return (
    <Card className="min-h-[470px]">
      <CardHeader className="px-6">
        <CardTitle role="heading" aria-level={2} className="text-xl font-semibold">
          Top tenant theo số referral
        </CardTitle>
      </CardHeader>
      <CardContent className="px-6">
        {loading ? (
          <Skeleton className="h-80 w-full" />
        ) : visibleRows.length === 0 ? (
          <div className="border-border text-muted-foreground flex h-80 items-center justify-center rounded-xl border border-dashed text-sm">
            Chưa có dữ liệu chart.
          </div>
        ) : (
          <div className="space-y-4 pt-2">
            <div className="space-y-4">
              {visibleRows.map((row) => (
                <div
                  key={row.tenantId}
                  className="grid grid-cols-[9.5rem_minmax(0,1fr)_4.5rem] items-center gap-3"
                >
                  <div className="text-ink truncate text-base">{row.tenantDisplayName}</div>
                  <div className="bg-muted relative h-5 rounded-r-md">
                    <div
                      className="h-full rounded-r-md bg-primary shadow-[inset_0_0_0_1px_rgb(255_255_255/0.2)]"
                      style={{
                        width: `${Math.max(2, (row.successfulReferrals / scaleMax) * 100)}%`,
                      }}
                    />
                  </div>
                  <div className="text-ink text-base font-semibold tabular-nums">
                    {formatInteger(row.successfulReferrals)}
                  </div>
                </div>
              ))}
            </div>
            <div className="grid grid-cols-[9.5rem_minmax(0,1fr)_4.5rem] gap-3">
              <div />
              <div className="border-border border-t pt-3">
                <div className="text-muted-foreground flex justify-between text-sm tabular-nums">
                  {axisTicks.map((tick) => (
                    <span key={tick}>{formatInteger(tick)}</span>
                  ))}
                </div>
              </div>
              <div />
            </div>
            <div className="text-muted-foreground text-center text-sm">Số referral</div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function SettingsSection({
  campaigns,
  selectedCampaign,
  loading,
  onSelectCampaign,
  onStatusChange,
}: {
  campaigns: AdminReferralCampaignResponse[];
  selectedCampaign: AdminReferralCampaignResponse | undefined;
  loading: boolean;
  onSelectCampaign: (campaignId: string) => void;
  onStatusChange: (status: AdminReferralCampaignStatus) => void;
}) {
  return (
    <div className="space-y-3">
      {!selectedCampaign && !loading ? (
        <EmptyCampaignState />
      ) : selectedCampaign ? (
        <CampaignSettingsWorkspace
          key={selectedCampaign.campaignId}
          campaign={selectedCampaign}
          campaigns={campaigns}
          loading={loading}
          onSelectCampaign={onSelectCampaign}
          onStatusChange={onStatusChange}
        />
      ) : (
        <div className="grid gap-3 xl:grid-cols-[390px_minmax(0,1fr)]">
          <Skeleton className="h-80" />
          <Skeleton className="h-80" />
          <Skeleton className="h-96" />
          <Skeleton className="h-96" />
        </div>
      )}
    </div>
  );
}

function CampaignSettingsWorkspace({
  campaign,
  campaigns,
  loading,
  onSelectCampaign,
  onStatusChange,
}: {
  campaign: AdminReferralCampaignResponse;
  campaigns: AdminReferralCampaignResponse[];
  loading: boolean;
  onSelectCampaign: (campaignId: string) => void;
  onStatusChange: (status: AdminReferralCampaignStatus) => void;
}) {
  const updateCampaignMutation = useUpdateReferralCampaign();
  const uploadCampaignBannerMutation = useUploadReferralCampaignBanner();
  const [draft, setDraft] = useState<CampaignDraft>(() => draftFromCampaign(campaign));

  function persistDraft(nextDraft: CampaignDraft = draft) {
    updateCampaignMutation.mutate(
      {
        campaignId: campaign.campaignId,
        request: {
          name: nextDraft.name.trim(),
          campaignCode: nextDraft.campaignCode.trim(),
          slug: nextDraft.slug.trim(),
          description: optionalString(nextDraft.description),
          startsAt: fromLocalDateTimeValue(nextDraft.startsAt),
          endsAt: fromLocalDateTimeValue(nextDraft.endsAt),
          webBannerEnabled: nextDraft.webBannerEnabled,
          countdownEnabled: nextDraft.countdownEnabled,
          leaderboardEnabled: nextDraft.leaderboardEnabled,
          leaderboardLimit: campaign.leaderboardLimit,
          rewardRankCutoff: nextDraft.rewardRankCutoff,
          rewardNotificationText: nextDraft.rewardNotificationText.trim(),
        },
      },
      {
        onSuccess: (updatedCampaign) => setDraft(draftFromCampaign(updatedCampaign)),
      },
    );
  }

  function updateDraft(nextDraft: CampaignDraft, persistImmediately = false) {
    setDraft(nextDraft);
    if (persistImmediately) {
      persistDraft(nextDraft);
    }
  }

  function uploadBanner(file: File) {
    uploadCampaignBannerMutation.mutate({ campaignId: campaign.campaignId, file });
  }

  return (
    <div className="grid gap-3 xl:grid-cols-[390px_minmax(0,1fr)]">
      <CampaignTimingCard
        campaign={campaign}
        draft={draft}
        onStatusChange={onStatusChange}
        onDraftChange={updateDraft}
      />
      <CampaignBannerCard
        campaign={campaign}
        uploading={uploadCampaignBannerMutation.isPending}
        onUpload={uploadBanner}
      />
      <CampaignInfoCard
        draft={draft}
        saving={updateCampaignMutation.isPending}
        onDraftChange={updateDraft}
        onSave={() => persistDraft()}
      />
      <CampaignRoundsTable
        campaigns={campaigns}
        loading={loading}
        onSelectCampaign={onSelectCampaign}
      />
    </div>
  );
}

function CampaignTimingCard({
  campaign,
  draft,
  onStatusChange,
  onDraftChange,
}: {
  campaign: AdminReferralCampaignResponse;
  draft: CampaignDraft;
  onStatusChange: (status: AdminReferralCampaignStatus) => void;
  onDraftChange: (draft: CampaignDraft, persistImmediately?: boolean) => void;
}) {
  return (
    <Card className="min-h-[300px]">
      <CardHeader className="pb-3">
        <CardTitle role="heading" aria-level={2} className="flex items-center gap-3">
          <CardTitleIcon icon={<CalendarIcon className="size-4" />} />
          Thời gian diễn ra
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="space-y-3">
          <DateAndTimeField
            id="referral-start"
            label="Bắt đầu"
            value={draft.startsAt}
            onChange={(value, persistImmediately) =>
              onDraftChange({ ...draft, startsAt: value }, persistImmediately)
            }
          />
          <DateAndTimeField
            id="referral-end"
            label="Kết thúc"
            value={draft.endsAt}
            onChange={(value, persistImmediately) =>
              onDraftChange({ ...draft, endsAt: value }, persistImmediately)
            }
          />
        </div>

        <div className="space-y-2">
          <div className="text-ink text-sm font-medium">Trạng thái sự kiện</div>
          <div className="grid grid-cols-3 gap-1 rounded-lg">
            {CONFIG_STATUS_OPTIONS.map((option) => {
              const active = isConfigStatusActive(campaign.status, option.status);
              return (
                <Button
                  key={option.status}
                  type="button"
                  variant={active ? 'default' : 'outline'}
                  onClick={() => onStatusChange(option.status)}
                  aria-pressed={active}
                  className="h-12 text-base"
                >
                  {option.label}
                </Button>
              );
            })}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function CampaignBannerCard({
  campaign,
  uploading,
  onUpload,
}: {
  campaign: AdminReferralCampaignResponse;
  uploading: boolean;
  onUpload: (file: File) => void;
}) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploadError, setUploadError] = useState<string | undefined>();

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    if (!ALLOWED_BANNER_UPLOAD_TYPES.has(file.type)) {
      setUploadError('Chỉ hỗ trợ ảnh PNG, JPG hoặc WebP.');
      return;
    }
    if (file.size > MAX_BANNER_UPLOAD_BYTES) {
      setUploadError('Banner không được vượt quá 5MB.');
      return;
    }
    setUploadError(undefined);
    onUpload(file);
  }

  return (
    <Card className="min-h-[300px]">
      <CardHeader className="pb-3">
        <CardTitle role="heading" aria-level={2} className="flex items-center gap-3">
          <CardTitleIcon icon={<ImageIcon className="size-4" />} />
          Banner hiển thị web app
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <ReferralBannerPreview campaign={campaign} />
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <Button
            type="button"
            variant="outline"
            className="w-fit"
            disabled={uploading}
            onClick={() => fileInputRef.current?.click()}
          >
            {uploading ? (
              <Loader2Icon className="size-4 animate-spin" />
            ) : (
              <UploadIcon className="size-4" />
            )}
            Tải banner
          </Button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/png,image/jpeg,image/webp"
            className="sr-only"
            onChange={handleFileChange}
          />
          <span className="text-muted-foreground text-sm">
            Tỷ lệ đề xuất: 21:5, ví dụ 1680 x 400
          </span>
        </div>
        {uploadError && <p className="text-destructive text-sm">{uploadError}</p>}
      </CardContent>
    </Card>
  );
}

function CampaignInfoCard({
  draft,
  saving,
  onDraftChange,
  onSave,
}: {
  draft: CampaignDraft;
  saving: boolean;
  onDraftChange: (draft: CampaignDraft, persistImmediately?: boolean) => void;
  onSave: () => void;
}) {
  return (
    <Card className="min-h-[410px]">
      <CardHeader className="pb-3">
        <CardTitle role="heading" aria-level={2} className="flex items-center gap-3">
          <CardTitleIcon icon={<InfoIcon className="size-4" />} />
          Thông tin sự kiện
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <TextField
          id="referral-name"
          label="Tên sự kiện"
          value={draft.name}
          onChange={(value) => onDraftChange({ ...draft, name: value })}
          onBlur={onSave}
        />
        <TextField
          id="referral-code"
          label="Mã sự kiện"
          value={draft.campaignCode}
          onChange={(value) => onDraftChange({ ...draft, campaignCode: value })}
          onBlur={onSave}
        />
        <div className="space-y-2">
          <Label htmlFor="referral-description">Mô tả ngắn</Label>
          <Textarea
            id="referral-description"
            value={draft.description}
            onChange={(event) => onDraftChange({ ...draft, description: event.target.value })}
            onBlur={onSave}
            className="min-h-28 resize-y"
          />
        </div>
        <NumberField
          id="referral-reward-rank-cutoff"
          label="Top được nhận quà"
          value={draft.rewardRankCutoff}
          min={1}
          max={100}
          onChange={(value, persistImmediately) =>
            onDraftChange({
              ...draft,
              rewardRankCutoff: clampNumber(value, 1, 100),
            }, persistImmediately)
          }
        />
        <div className="space-y-2">
          <Label htmlFor="referral-reward-notification">Thông báo nhận quà</Label>
          <Textarea
            id="referral-reward-notification"
            value={draft.rewardNotificationText}
            maxLength={500}
            onChange={(event) =>
              onDraftChange({ ...draft, rewardNotificationText: event.target.value })
            }
            onBlur={onSave}
            className="min-h-24 resize-y"
          />
        </div>
        <div className="text-muted-foreground flex justify-end text-xs">
          {saving ? 'Đang lưu thay đổi...' : 'Tự lưu khi rời trường nhập'}
        </div>
      </CardContent>
    </Card>
  );
}

function CampaignRoundsTable({
  campaigns,
  loading,
  onSelectCampaign,
}: {
  campaigns: AdminReferralCampaignResponse[];
  loading: boolean;
  onSelectCampaign: (campaignId: string) => void;
}) {
  return (
    <Card className="flex min-h-[410px] flex-col">
      <CardHeader className="pb-3">
        <CardTitle role="heading" aria-level={2} className="flex items-center gap-3">
          <CardTitleIcon icon={<CalendarIcon className="size-4" />} />
          Danh sách các đợt sự kiện
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-1 flex-col">
        <div className="border-border overflow-x-auto rounded-lg border">
          <Table className="min-w-[720px]">
            <TableHeader>
              <TableRow>
                <TableHead>Sự kiện</TableHead>
                <TableHead>Thời gian</TableHead>
                <TableHead>Trạng thái</TableHead>
                <TableHead>Banner</TableHead>
                <TableHead className="text-right">Thao tác</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading && (
                <TableRow>
                  <TableCell colSpan={5} className="text-muted-foreground h-24 text-center">
                    Đang tải sự kiện.
                  </TableCell>
                </TableRow>
              )}
              {!loading && campaigns.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="text-muted-foreground h-24 text-center">
                    Chưa có đợt sự kiện referral.
                  </TableCell>
                </TableRow>
              )}
              {campaigns.map((campaign) => (
                <TableRow key={campaign.campaignId}>
                  <TableCell className="min-w-0">
                    <div className="text-ink truncate font-medium">{campaign.name}</div>
                  </TableCell>
                  <TableCell className="text-muted-foreground text-sm">
                    {formatDate(campaign.startsAt)} - {formatDate(campaign.endsAt)}
                  </TableCell>
                  <TableCell>
                    <CampaignStatusBadge status={campaign.status} />
                  </TableCell>
                  <TableCell>
                    <CampaignBannerThumb campaign={campaign} />
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-1">
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        className="text-primary"
                        onClick={() => onSelectCampaign(campaign.campaignId)}
                      >
                        {campaign.status === 'ENDED' || campaign.status === 'ARCHIVED' ? (
                          <>
                            <EyeIcon className="size-4" />
                            Xem
                          </>
                        ) : (
                          <>
                            <PencilIcon className="size-4" />
                            Sửa
                          </>
                        )}
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        aria-label="Tùy chọn sự kiện"
                      >
                        <MoreVerticalIcon className="size-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
        <div className="text-muted-foreground mt-auto flex flex-col gap-3 pt-3 text-sm sm:flex-row sm:items-center sm:justify-between">
          <span>
            Hiển thị 1 - {campaigns.length} trong tổng số {campaigns.length} sự kiện
          </span>
          <div className="flex items-center gap-2">
            <Button type="button" variant="outline" size="icon" disabled>
              ‹
            </Button>
            <Button type="button" size="icon">
              1
            </Button>
            <Button type="button" variant="outline" size="icon" disabled>
              ›
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function CampaignSelect({
  campaigns,
  selectedCampaignId,
  onChange,
}: {
  campaigns: AdminReferralCampaignResponse[];
  selectedCampaignId: string | undefined;
  onChange: (campaignId: string) => void;
}) {
  return (
    <label className="relative">
      <CalendarIcon className="text-muted-foreground pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2" />
      <span className="sr-only">Chọn sự kiện referral</span>
      <select
        aria-label="Chọn sự kiện referral"
        value={selectedCampaignId ?? ''}
        onChange={(event) => onChange(event.target.value)}
        className="border-input bg-background focus-visible:border-ring focus-visible:ring-ring/50 h-9 min-w-56 rounded-md border pr-8 pl-9 text-sm shadow-xs outline-none focus-visible:ring-[3px]"
      >
        {campaigns.length === 0 && <option value="">Chưa có sự kiện</option>}
        {campaigns.map((campaign) => (
          <option key={campaign.campaignId} value={campaign.campaignId}>
            {campaign.name}
          </option>
        ))}
      </select>
    </label>
  );
}

function CardTitleIcon({ icon }: { icon: ReactNode }) {
  return (
    <span className="bg-primary/10 text-primary flex size-8 shrink-0 items-center justify-center rounded-lg">
      {icon}
    </span>
  );
}

function ReferralBannerPreview({ campaign }: { campaign: AdminReferralCampaignResponse }) {
  if (campaign.bannerImageAvailable) {
    return (
      <div
        className="border-primary/20 aspect-[21/5] w-full rounded-lg border bg-cover bg-center"
        style={{ backgroundImage: `url(${referralCampaignBannerUrl(campaign)})` }}
      />
    );
  }

  return (
    <div className="border-primary/20 bg-violet-soft/70 relative flex aspect-[21/5] w-full overflow-hidden rounded-lg border">
      <div className="text-primary/25 absolute top-6 left-8">
        <ImageIcon className="size-24 rotate-[-12deg]" />
      </div>
      <div className="text-primary/25 absolute right-10 bottom-6">
        <GiftIcon className="size-24 rotate-6" />
      </div>
      <div className="relative z-10 mx-auto flex max-w-xl flex-col items-center justify-center px-6 text-center">
        <div className="text-primary text-2xl font-semibold">Mời bạn bè dùng Zero Mail</div>
        <div className="text-muted-foreground mt-4 text-lg">
          Cùng mời nhiều, càng thăng hạng - nhận quà xứng đáng!
        </div>
      </div>
    </div>
  );
}

function CampaignBannerThumb({ campaign }: { campaign: AdminReferralCampaignResponse }) {
  if (campaign.bannerImageAvailable) {
    return (
      <div
        className="border-primary/20 h-10 w-[168px] rounded-md border bg-cover bg-center"
        style={{ backgroundImage: `url(${referralCampaignBannerUrl(campaign)})` }}
      />
    );
  }
  return (
    <div className="border-primary/20 bg-violet-soft flex h-10 w-[168px] items-center justify-center rounded-md border">
      <GiftIcon className="text-primary size-5" />
    </div>
  );
}

function DateInput({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="relative min-w-40">
      <CalendarIcon className="text-muted-foreground pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
      <span className="sr-only">{label}</span>
      <Input
        type="date"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="pl-8"
      />
    </label>
  );
}

function TextField({
  id,
  label,
  value,
  onChange,
  onBlur,
  placeholder,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  onBlur?: () => void;
  placeholder?: string;
}) {
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        value={value}
        placeholder={placeholder}
        onChange={(event: ChangeEvent<HTMLInputElement>) => onChange(event.target.value)}
        onBlur={onBlur}
      />
    </div>
  );
}

function NumberField({
  id,
  label,
  value,
  min,
  max,
  onChange,
}: {
  id: string;
  label: string;
  value: number;
  min: number;
  max: number;
  onChange: (value: number, persistImmediately?: boolean) => void;
}) {
  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    if (event.target.value.trim() === '') return;
    const nextValue = Number(event.target.value);
    if (Number.isFinite(nextValue)) {
      onChange(nextValue);
    }
  }

  function handleBlur(event: ChangeEvent<HTMLInputElement>) {
    const rawValue = event.currentTarget.value.trim();
    const parsedValue = rawValue === '' ? min : Number(rawValue);
    const nextValue = clampNumber(parsedValue, min, max);
    event.currentTarget.value = String(nextValue);
    onChange(nextValue, true);
  }

  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        type="number"
        defaultValue={value}
        min={min}
        max={max}
        onChange={handleChange}
        onBlur={handleBlur}
      />
    </div>
  );
}

function DateAndTimeField({
  id,
  label,
  value,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string, persistImmediately?: boolean) => void;
}) {
  const [datePickerOpen, setDatePickerOpen] = useState(false);
  const [timePickerOpen, setTimePickerOpen] = useState(false);
  const selectedDate = dateFromLocalDateTimeValue(value);
  const [selectedHour, selectedMinute] = timeInputValue(value).split(':');

  function handleDateSelect(nextDate: Date | undefined) {
    if (!nextDate) return;
    onChange(mergeDatePart(value, dateInputValue(nextDate)), true);
    setDatePickerOpen(false);
  }

  function handleTimeSelect(nextHour: string, nextMinute: string, closeAfterSelect = false) {
    onChange(mergeTimePart(value, `${nextHour}:${nextMinute}`), true);
    if (closeAfterSelect) {
      setTimePickerOpen(false);
    }
  }

  return (
    <div className="space-y-2">
      <Label htmlFor={`${id}-date`}>{label}</Label>
      <div className="grid grid-cols-[minmax(0,1fr)_8.5rem] gap-2">
        <Popover open={datePickerOpen} onOpenChange={setDatePickerOpen}>
          <PopoverTrigger
            render={
              <Button
                id={`${id}-date`}
                type="button"
                variant="outline"
                className="h-12 justify-between px-3 text-base font-normal"
              />
            }
          >
            <span>{formatDateInputValue(value)}</span>
            <CalendarIcon className="text-muted-foreground size-4" />
          </PopoverTrigger>
          <PopoverContent align="start" side="bottom" className="w-auto max-w-[calc(100vw-2rem)] p-2">
            <Calendar
              mode="single"
              selected={selectedDate}
              defaultMonth={selectedDate}
              onSelect={handleDateSelect}
              captionLayout="dropdown"
            />
          </PopoverContent>
        </Popover>
        <Popover open={timePickerOpen} onOpenChange={setTimePickerOpen}>
          <PopoverTrigger
            render={
              <Button
                id={`${id}-time`}
                type="button"
                variant="outline"
                className="h-12 justify-between px-3 text-base font-normal"
                aria-label={`${label} giờ`}
              />
            }
          >
            <span>{timeInputValue(value)}</span>
            <ClockIcon className="text-muted-foreground size-4" />
          </PopoverTrigger>
          <PopoverContent
            align="end"
            side="bottom"
            className="w-[min(30rem,calc(100vw-2rem))] p-3"
          >
            <div className="space-y-4">
              <TimeOptionGrid
                label="Giờ"
                options={TIME_HOUR_OPTIONS}
                selectedOption={selectedHour}
                className="grid-cols-6"
                onSelect={(hour) => handleTimeSelect(hour, selectedMinute)}
              />
              <TimeOptionGrid
                label="Phút"
                options={TIME_MINUTE_OPTIONS}
                selectedOption={selectedMinute}
                className="grid-cols-10"
                onSelect={(minute) => handleTimeSelect(selectedHour, minute, true)}
              />
            </div>
          </PopoverContent>
        </Popover>
      </div>
    </div>
  );
}

function TimeOptionGrid({
  label,
  options,
  selectedOption,
  className,
  onSelect,
}: {
  label: string;
  options: string[];
  selectedOption: string;
  className: string;
  onSelect: (option: string) => void;
}) {
  return (
    <div className="space-y-2">
      <div className="text-muted-foreground text-xs font-medium">{label}</div>
      <div className={`grid gap-1 ${className}`}>
        {options.map((option) => (
          <Button
            key={option}
            type="button"
            variant={selectedOption === option ? 'default' : 'ghost'}
            size="sm"
            className="h-7 min-w-0 justify-center px-1 text-xs tabular-nums"
            aria-pressed={selectedOption === option}
            onClick={() => onSelect(option)}
          >
            {option}
          </Button>
        ))}
      </div>
    </div>
  );
}

function CampaignStatusBadge({ status }: { status: AdminReferralCampaignStatus }) {
  const className =
    status === 'ACTIVE'
      ? 'bg-green/10 text-green border-transparent'
      : status === 'PAUSED'
        ? 'bg-amber-soft text-amber border-transparent'
      : status === 'ENDED'
          ? 'bg-secondary text-secondary-foreground border-transparent'
      : status === 'ARCHIVED'
            ? 'bg-destructive/10 text-destructive border-transparent'
            : 'bg-blue-soft text-blue border-transparent';
  return (
    <Badge variant="secondary" className={className}>
      {statusLabel(status)}
    </Badge>
  );
}

function EmptyCampaignState() {
  return (
    <div className="border-border flex min-h-48 flex-col items-center justify-center gap-3 rounded-lg border border-dashed p-8 text-center">
      <div className="bg-primary/10 text-primary flex size-12 items-center justify-center rounded-xl">
        <SettingsIcon className="size-6" />
      </div>
      <div>
        <div className="text-ink font-semibold">Chưa có sự kiện referral</div>
        <p className="text-muted-foreground mt-1 text-sm">
          Tạo sự kiện mới để bắt đầu tracking tenant giới thiệu thành công.
        </p>
      </div>
    </div>
  );
}

function compareCampaigns(
  firstCampaign: AdminReferralCampaignResponse,
  secondCampaign: AdminReferralCampaignResponse,
) {
  const statusOrderDifference =
    CAMPAIGN_STATUS_ORDER[firstCampaign.status] - CAMPAIGN_STATUS_ORDER[secondCampaign.status];
  if (statusOrderDifference !== 0) return statusOrderDifference;
  return new Date(firstCampaign.startsAt).getTime() - new Date(secondCampaign.startsAt).getTime();
}

function createDefaultCampaign(): AdminReferralCampaignCreateRequest {
  const now = new Date();
  const endsAt = new Date(now);
  endsAt.setDate(endsAt.getDate() + 14);
  const code = `REF-${dateCompact(now)}`;
  return {
    name: `Referral ${dateInputValue(now)}`,
    campaignCode: code,
    slug: code.toLowerCase(),
    description:
      'Giới thiệu bạn bè dùng Zero Mail và nhận thưởng theo số tenant onboard thành công.',
    status: 'DRAFT',
    startsAt: now.toISOString(),
    endsAt: endsAt.toISOString(),
    webBannerEnabled: true,
    countdownEnabled: true,
    leaderboardEnabled: true,
    leaderboardLimit: 100,
    rewardRankCutoff: 3,
    rewardNotificationText: 'Thông báo nhận thưởng sẽ được gửi qua email.',
  };
}

function draftFromCampaign(campaign: AdminReferralCampaignResponse): CampaignDraft {
  return {
    name: campaign.name,
    campaignCode: campaign.campaignCode,
    slug: campaign.slug,
    description: campaign.description ?? '',
    startsAt: localDateTimeInputValue(new Date(campaign.startsAt)),
    endsAt: localDateTimeInputValue(new Date(campaign.endsAt)),
    webBannerEnabled: campaign.webBannerEnabled,
    countdownEnabled: campaign.countdownEnabled,
    leaderboardEnabled: campaign.leaderboardEnabled,
    rewardRankCutoff: campaign.rewardRankCutoff,
    rewardNotificationText: campaign.rewardNotificationText,
  };
}

function optionalString(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

function startOfDateInput(value: string): Date {
  return new Date(`${value}T00:00:00`);
}

function endOfDateInput(value: string): Date {
  return new Date(`${value}T23:59:59.999`);
}

function dateInputValue(date: Date): string {
  return localDateTimeInputValue(date).slice(0, 10);
}

function firstDayOfMonthInput(date: Date): string {
  const firstDay = new Date(date.getFullYear(), date.getMonth(), 1);
  return dateInputValue(firstDay);
}

function localDateTimeInputValue(date: Date): string {
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return localDate.toISOString().slice(0, 16);
}

function formatDateInputValue(localDateTimeValue: string): string {
  const [datePart] = localDateTimeValue.split('T');
  const [year, month, day] = datePart.split('-');
  if (!year || !month || !day) return '';
  return `${day}/${month}/${year}`;
}

function formatDateRange(fromDateInput: string, toDateInput: string): string {
  return `${formatDateInputValue(`${fromDateInput}T00:00`)} - ${formatDateInputValue(`${toDateInput}T00:00`)}`;
}

function formatClock(value: string): string {
  return new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(value));
}

function dateFromLocalDateTimeValue(localDateTimeValue: string): Date | undefined {
  const [datePart] = localDateTimeValue.split('T');
  const [year, month, day] = datePart.split('-').map(Number);
  if (!year || !month || !day) return undefined;
  return new Date(year, month - 1, day);
}

function timeInputValue(localDateTimeValue: string): string {
  return localDateTimeValue.split('T')[1]?.slice(0, 5) ?? '00:00';
}

function mergeDatePart(localDateTimeValue: string, datePart: string): string {
  return `${datePart}T${timeInputValue(localDateTimeValue)}`;
}

function mergeTimePart(localDateTimeValue: string, timePart: string): string {
  const [datePart] = localDateTimeValue.split('T');
  return `${datePart}T${timePart || '00:00'}`;
}

function fromLocalDateTimeValue(value: string): string {
  return new Date(value).toISOString();
}

function _campaignCountdown(endsAt: string): string {
  const remainingMilliseconds = new Date(endsAt).getTime() - Date.now();
  if (remainingMilliseconds <= 0) return 'Đã kết thúc';
  const days = Math.floor(remainingMilliseconds / 86_400_000);
  const hours = Math.floor((remainingMilliseconds % 86_400_000) / 3_600_000);
  if (days > 0) return `${days} ngày ${hours} giờ`;
  return `${hours} giờ`;
}

function countdownParts(endsAt: string | undefined, now: number) {
  const remainingMilliseconds = endsAt
    ? Math.max(0, new Date(endsAt).getTime() - now)
    : 0;
  const days = Math.floor(remainingMilliseconds / 86_400_000);
  const hours = Math.floor((remainingMilliseconds % 86_400_000) / 3_600_000);
  const minutes = Math.floor((remainingMilliseconds % 3_600_000) / 60_000);
  const seconds = Math.floor((remainingMilliseconds % 60_000) / 1000);
  return {
    days: padCountdown(days),
    hours: padCountdown(hours),
    minutes: padCountdown(minutes),
    seconds: padCountdown(seconds),
  };
}

function padCountdown(value: number): string {
  return value.toString().padStart(2, '0');
}

function rankBadgeClass(rank: number): string {
  if (rank === 1) return 'bg-amber text-ink';
  if (rank === 2) return 'bg-muted text-ink';
  if (rank === 3) return 'bg-amber-soft text-ink';
  return 'bg-muted text-muted-foreground';
}

function niceScaleMax(value: number): number {
  if (value <= 10) return 10;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  return Math.ceil(value / magnitude) * magnitude;
}

function statusLabel(status: AdminReferralCampaignStatus): string {
  switch (status) {
    case 'ACTIVE':
      return 'Đang diễn ra';
    case 'PAUSED':
      return 'Tạm dừng';
    case 'ENDED':
      return 'Kết thúc';
    case 'ARCHIVED':
      return 'Đã tắt';
    case 'DRAFT':
      return 'Sắp diễn ra';
  }
}

function isConfigStatusActive(
  currentStatus: AdminReferralCampaignStatus,
  optionStatus: AdminReferralCampaignStatus,
): boolean {
  if (optionStatus === 'ARCHIVED') {
    return currentStatus === 'ARCHIVED' || currentStatus === 'DRAFT' || currentStatus === 'PAUSED';
  }
  return currentStatus === optionStatus;
}

function clampNumber(value: number, min: number, max: number): number {
  if (Number.isNaN(value)) return min;
  return Math.min(max, Math.max(min, value));
}

function formatInteger(value: number): string {
  return integerFormatter.format(value);
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('vi-VN').format(new Date(value));
}

function dateCompact(date: Date): string {
  return localDateTimeInputValue(date).replaceAll('-', '').replaceAll(':', '').replace('T', '-');
}
