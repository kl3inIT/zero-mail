'use client';

import type { ReactNode } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Award,
  Bell,
  CalendarDays,
  Check,
  CircleCheck,
  Clipboard,
  Gift,
  History,
  Info,
  Mail,
  PartyPopper,
  Timer,
  Trophy,
} from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { useEndReferralCampaignIfExpired } from '@/features/referrals/hooks/use-end-referral-campaign-if-expired';
import { useReferralMe } from '@/features/referrals/hooks/use-referral-me';
import { getPublicApiUrl } from '@/lib/api/base-url';
import type { components } from '@/lib/api/schema';

type ReferralLeaderboardRow = components['schemas']['ReferralLeaderboardRowResponse'];

const integerFormatter = new Intl.NumberFormat('vi-VN');
const dateFormatter = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});
const timeFormatter = new Intl.DateTimeFormat('vi-VN', {
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});
const DEFAULT_REWARD_RANK_CUTOFF = 3;
const DEFAULT_REWARD_NOTIFICATION_TEXT = 'Thông báo nhận thưởng sẽ được gửi qua email.';

export function ReferralPageClient() {
  const referralQuery = useReferralMe();
  const referral = referralQuery.data;
  const { mutate: endCampaignIfExpired } = useEndReferralCampaignIfExpired();
  const expiryTriggeredCampaignIdRef = useRef<string | null>(null);
  const [copied, setCopied] = useState(false);

  const countdown = useCountdown(referral?.campaignEndsAt);
  const eventEnded = Boolean(referral?.campaignEndsAt && countdown.complete);
  const currentTenant = referral?.currentTenant;
  const rewardRankCutoff = rewardRankCutoffValue(referral?.rewardRankCutoff);
  const rewardNotificationText =
    referral?.rewardNotificationText?.trim() || DEFAULT_REWARD_NOTIFICATION_TEXT;
  const leaderboardRows = useMemo(
    () => buildLeaderboardRows(referral?.leaderboard ?? [], currentTenant),
    [currentTenant, referral?.leaderboard],
  );

  useEffect(() => {
    const campaignId = referral?.campaignId;
    if (!campaignId || !eventEnded) return;
    if (expiryTriggeredCampaignIdRef.current === campaignId) return;
    expiryTriggeredCampaignIdRef.current = campaignId;
    endCampaignIfExpired(campaignId, {
      onError: () => {
        if (expiryTriggeredCampaignIdRef.current === campaignId) {
          expiryTriggeredCampaignIdRef.current = null;
        }
      },
    });
  }, [endCampaignIfExpired, eventEnded, referral?.campaignId]);

  async function handleCopy() {
    if (!referral?.url) return;
    await navigator.clipboard.writeText(referral.url);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 1800);
  }

  if (referralQuery.isLoading) {
    return <ReferralLoadingState />;
  }

  if (!referral?.active) {
    return <ReferralInactiveState />;
  }

  return (
    <main className="bg-muted/30 flex min-h-full flex-col">
      <div className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-4 p-4 sm:p-6">
        <header className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <h1 className="text-foreground text-2xl font-bold tracking-normal md:text-3xl">
              <span className="md:hidden">Sự kiện</span>
              <span className="hidden md:inline">
                {referral.campaignName ?? 'Mời bạn bè dùng Zero Mail'}
              </span>
            </h1>
            <EventStatusPill ended={eventEnded} />
          </div>

          <div className="hidden sm:block">
            <DateRangePill startsAt={referral.campaignStartsAt} endsAt={referral.campaignEndsAt} />
          </div>
        </header>

        <ReferralHero referral={referral} ended={eventEnded} />

        <section
          className={
            eventEnded ? 'grid gap-4' : 'grid gap-4 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.55fr)]'
          }
        >
          {eventEnded ? (
            <EndedStatusPanel endsAt={referral.campaignEndsAt} />
          ) : (
            <>
              <CountdownPanel countdown={countdown} enabled={referral.countdownEnabled !== false} />
              <ShareLinkPanel
                copied={copied}
                referralUrl={referral.url ?? ''}
                onCopy={handleCopy}
              />
            </>
          )}
        </section>

        <section className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
          {eventEnded ? (
            <FinalResultPanel
              currentTenant={currentTenant}
              successfulReferrals={referral.successfulReferrals ?? 0}
              rewardRankCutoff={rewardRankCutoff}
              rewardNotificationText={rewardNotificationText}
              onFollowNewEvents={() => void referralQuery.refetch()}
              onViewEventHistory={() =>
                document
                  .getElementById('referral-final-leaderboard')
                  ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
              }
            />
          ) : (
            <CurrentRankPanel
              currentTenant={currentTenant}
              successfulReferrals={referral.successfulReferrals ?? 0}
              totalRankedTenants={referral.totalRankedTenants ?? 0}
            />
          )}
          <LeaderboardPanel
            rows={leaderboardRows}
            enabled={referral.leaderboardEnabled !== false}
            final={eventEnded}
          />
        </section>
      </div>
    </main>
  );
}

function ReferralLoadingState() {
  return (
    <main className="bg-muted/30 flex min-h-full flex-col">
      <div className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-4 p-4 sm:p-6">
        <div className="flex items-center justify-between">
          <Skeleton className="h-9 w-56" />
          <Skeleton className="hidden h-10 w-64 sm:block" />
        </div>
        <Skeleton className="h-56 w-full rounded-2xl" />
        <div className="grid gap-4 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.55fr)]">
          <Skeleton className="h-32 rounded-2xl" />
          <Skeleton className="h-32 rounded-2xl" />
        </div>
        <div className="grid gap-4 lg:grid-cols-2">
          <Skeleton className="h-64 rounded-2xl" />
          <Skeleton className="h-64 rounded-2xl" />
        </div>
      </div>
    </main>
  );
}

function ReferralInactiveState() {
  return (
    <main className="bg-muted/30 flex min-h-full flex-col">
      <div className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-4 p-4 sm:p-6">
        <h1 className="text-foreground text-2xl font-bold tracking-normal">Sự kiện</h1>
        <section className="border-border bg-card flex min-h-80 flex-col items-center justify-center gap-4 rounded-2xl border p-8 text-center shadow-sm">
          <div className="bg-primary/10 text-primary flex size-14 items-center justify-center rounded-2xl">
            <Gift className="size-7" />
          </div>
          <div className="space-y-1">
            <h2 className="text-foreground text-lg font-semibold">
              Chưa có sự kiện giới thiệu đang bật
            </h2>
            <p className="text-muted-foreground max-w-md text-sm">
              Link giới thiệu của bạn sẽ xuất hiện khi Zero Mail mở sự kiện mới.
            </p>
          </div>
        </section>
      </div>
    </main>
  );
}

function EventStatusPill({ ended }: { ended: boolean }) {
  if (ended) {
    return (
      <span className="bg-muted text-muted-foreground flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold sm:text-sm">
        <span className="bg-muted-foreground size-1.5 rounded-full" />
        Đã kết thúc
      </span>
    );
  }

  return (
    <span className="flex items-center gap-1.5 text-xs text-emerald-600 sm:text-sm">
      <span className="size-2 rounded-full bg-emerald-500" />
      Cập nhật trực tiếp
    </span>
  );
}

function ReferralHero({
  referral,
  ended,
}: {
  referral: components['schemas']['ReferralMeResponse'];
  ended: boolean;
}) {
  const bannerUrl =
    referral.bannerImageAvailable && referral.campaignId
      ? referralBannerUrl(referral.campaignId)
      : undefined;

  if (bannerUrl) {
    return (
      <section className="border-border bg-card relative overflow-hidden rounded-2xl border shadow-sm">
        {/* eslint-disable-next-line @next/next/no-img-element -- Banner is served by the backend API at runtime. */}
        <img
          src={bannerUrl}
          alt={referral.campaignName ?? 'Banner sự kiện giới thiệu'}
          className="block aspect-[16/5] w-full object-cover sm:aspect-[21/5]"
        />
        {ended ? (
          <div className="bg-primary text-primary-foreground absolute top-3 right-3 flex items-center gap-2 rounded-xl px-4 py-2 text-xs font-bold shadow-lg sm:top-5 sm:right-5 sm:text-sm">
            <Trophy className="size-5" />
            <span className="hidden sm:inline">SỰ KIỆN ĐÃ KẾT THÚC</span>
          </div>
        ) : null}
      </section>
    );
  }

  return (
    <section className="border-border bg-card overflow-hidden rounded-2xl border shadow-sm">
      <div className="from-primary/5 via-card to-primary/10 flex min-h-44 items-center gap-4 bg-gradient-to-br p-5 sm:p-7">
        <div className="bg-primary/10 text-primary flex size-14 shrink-0 items-center justify-center rounded-2xl">
          <Gift className="size-7" />
        </div>
        <div className="min-w-0 space-y-2">
          <h2 className="text-primary text-2xl font-bold tracking-normal sm:text-4xl">
            {referral.campaignName ?? 'Mời bạn bè dùng Zero Mail'}
          </h2>
          {referral.campaignDescription ? (
            <p className="text-muted-foreground max-w-3xl text-sm font-medium sm:text-base">
              {referral.campaignDescription}
            </p>
          ) : null}
          <div className="sm:hidden">
            <DateRangePill startsAt={referral.campaignStartsAt} endsAt={referral.campaignEndsAt} />
          </div>
        </div>
      </div>
    </section>
  );
}

function CountdownPanel({ countdown, enabled }: { countdown: CountdownParts; enabled: boolean }) {
  return (
    <section className="border-border bg-card rounded-2xl border p-4 shadow-sm sm:p-5">
      <PanelTitle icon={<Timer className="size-5" />} title="Đếm ngược" />
      {enabled ? (
        <div className="mt-4 grid grid-cols-4 gap-2">
          <CountdownBox value={countdown.days} label="Ngày" />
          <CountdownBox value={countdown.hours} label="Giờ" />
          <CountdownBox value={countdown.minutes} label="Phút" />
          <CountdownBox value={countdown.seconds} label="Giây" />
        </div>
      ) : (
        <div className="text-muted-foreground mt-4 rounded-xl border border-dashed p-4 text-sm">
          Sự kiện này không hiển thị đếm ngược.
        </div>
      )}
    </section>
  );
}

function EndedStatusPanel({ endsAt }: { endsAt: string | undefined }) {
  return (
    <section className="border-border bg-card overflow-hidden rounded-2xl border shadow-sm">
      <div className="grid min-h-32 gap-4 p-4 sm:grid-cols-[1fr_auto] sm:p-5">
        <div className="space-y-3">
          <PanelTitle icon={<Timer className="size-5" />} title="Trạng thái sự kiện" />
          <div>
            <div className="text-primary text-3xl font-bold tracking-normal">Đã kết thúc</div>
            <p className="text-muted-foreground mt-2 text-sm font-medium">
              {endsAt ? `Kết thúc lúc ${formatEndedAt(endsAt)}` : 'Sự kiện đã kết thúc.'}
            </p>
          </div>
        </div>
        <div className="from-primary/10 to-primary/5 text-primary hidden items-center justify-center rounded-2xl bg-gradient-to-br px-10 sm:flex">
          <div className="relative">
            <Trophy className="size-16 opacity-80" />
            <CircleCheck className="bg-card text-primary absolute -right-3 -bottom-2 size-9 rounded-full" />
          </div>
        </div>
      </div>
    </section>
  );
}

function ShareLinkPanel({
  referralUrl,
  copied,
  onCopy,
}: {
  referralUrl: string;
  copied: boolean;
  onCopy: () => void;
}) {
  return (
    <section className="border-border bg-card rounded-2xl border p-4 shadow-sm sm:p-5">
      <PanelTitle icon={<Clipboard className="size-5" />} title="Link chia sẻ của bạn" />
      <div className="mt-4 flex gap-2">
        <Input
          value={referralUrl}
          readOnly
          className="h-12 min-w-0 flex-1 rounded-xl font-mono text-sm"
          aria-label="Link chia sẻ của bạn"
        />
        <Button
          type="button"
          onClick={onCopy}
          className="h-12 shrink-0 rounded-xl px-4 sm:px-6"
          disabled={!referralUrl}
        >
          {copied ? <Check className="size-4" /> : <Clipboard className="size-4" />}
          <span className="hidden sm:inline">{copied ? 'Đã sao chép' : 'Sao chép link'}</span>
          <span className="sm:hidden">{copied ? 'Đã chép' : 'Sao chép'}</span>
        </Button>
      </div>
    </section>
  );
}

function CurrentRankPanel({
  currentTenant,
  successfulReferrals,
  totalRankedTenants,
}: {
  currentTenant: ReferralLeaderboardRow | undefined;
  successfulReferrals: number;
  totalRankedTenants: number;
}) {
  const rankLabel = currentTenant ? `TOP #${currentTenant.rank}` : 'TOP --';

  return (
    <section className="border-border bg-card overflow-hidden rounded-2xl border shadow-sm">
      <div className="grid min-h-64 gap-4 p-5 sm:grid-cols-[1fr_auto] sm:p-6">
        <div className="flex flex-col justify-between gap-6">
          <div className="space-y-3">
            <h2 className="text-foreground text-lg font-semibold">Vị trí hiện tại</h2>
            <div>
              <div className="text-primary text-5xl font-bold tracking-normal sm:text-7xl">
                {rankLabel}
              </div>
              <p className="text-muted-foreground mt-2 text-xl font-semibold">
                trên {formatInteger(totalRankedTenants)} tenant
              </p>
            </div>
          </div>

          <div className="w-fit rounded-full bg-emerald-50 px-3 py-1.5 text-sm font-semibold text-emerald-700">
            {formatInteger(successfulReferrals)} lượt giới thiệu
          </div>
        </div>

        <div className="flex items-center justify-center sm:justify-end">
          <RankPodiumIllustration />
        </div>
      </div>
    </section>
  );
}

function FinalResultPanel({
  currentTenant,
  successfulReferrals,
  rewardRankCutoff,
  rewardNotificationText,
  onFollowNewEvents,
  onViewEventHistory,
}: {
  currentTenant: ReferralLeaderboardRow | undefined;
  successfulReferrals: number;
  rewardRankCutoff: number;
  rewardNotificationText: string;
  onFollowNewEvents: () => void;
  onViewEventHistory: () => void;
}) {
  const isRewardEligible =
    currentTenant !== undefined &&
    currentTenant.rank <= rewardRankCutoff &&
    currentTenant.successfulReferrals > 0;

  if (isRewardEligible) {
    return (
      <section className="border-border bg-card overflow-hidden rounded-2xl border shadow-sm">
        <div className="relative grid min-h-80 gap-6 p-5 sm:grid-cols-[220px_1fr] sm:p-7">
          <ConfettiLayer />
          <div className="flex items-center justify-center">
            <AwardMedal rank={currentTenant.rank} />
          </div>
          <div className="relative z-10 flex flex-col justify-center gap-5">
            <div className="space-y-3">
              <div className="text-foreground flex items-center gap-2 text-xl font-bold">
                <PartyPopper className="text-primary size-6" />
                Chúc mừng! Bạn đã đạt
              </div>
              <div className="text-primary text-6xl font-bold tracking-normal sm:text-7xl">
                TOP {currentTenant.rank}
              </div>
              <p className="text-foreground text-2xl font-bold">của sự kiện.</p>
            </div>
            <div className="flex items-center gap-3 rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-emerald-900">
              <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-white text-emerald-600 shadow-sm">
                <Mail className="size-5" />
              </span>
              <div>
                <div className="font-bold">{rewardNotificationText}</div>
                <div className="text-sm text-emerald-800">
                  Vui lòng kiểm tra email đã đăng ký để xem hướng dẫn nhận thưởng.
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="overflow-hidden rounded-2xl border border-amber-200 bg-amber-50/40 shadow-sm">
      <div className="flex min-h-80 flex-col justify-center gap-6 p-6 sm:p-8">
        <div className="space-y-4">
          <div className="flex items-center gap-4">
            <span className="flex size-16 shrink-0 items-center justify-center rounded-full bg-orange-100 text-orange-600">
              <Award className="size-8" />
            </span>
            <h2 className="text-2xl font-bold text-orange-900">Cảm ơn bạn đã tham gia</h2>
          </div>
          <div className="space-y-3">
            <p className="text-foreground text-2xl font-bold">
              Rất tiếc, bạn chưa nằm trong TOP {rewardRankCutoff} lần này.
            </p>
            <p className="text-muted-foreground max-w-xl text-base">
              Bạn vẫn có thể xem kết quả cuối cùng và theo dõi các sự kiện tiếp theo.
            </p>
            <div className="w-fit rounded-full bg-white px-3 py-1.5 text-sm font-semibold text-orange-700 shadow-sm">
              {formatInteger(successfulReferrals)} lượt giới thiệu
            </div>
          </div>
        </div>

        <div className="flex flex-col gap-3 sm:flex-row">
          <Button type="button" className="h-12 rounded-xl px-5" onClick={onFollowNewEvents}>
            <Bell className="size-4" />
            Theo dõi sự kiện mới
          </Button>
          <Button
            type="button"
            variant="outline"
            className="h-12 rounded-xl px-5"
            onClick={onViewEventHistory}
          >
            <History className="size-4" />
            Xem lịch sử sự kiện
          </Button>
        </div>
      </div>
    </section>
  );
}

function LeaderboardPanel({
  rows,
  enabled,
  final = false,
}: {
  rows: LeaderboardDisplayRow[];
  enabled: boolean;
  final?: boolean;
}) {
  return (
    <section
      id={final ? 'referral-final-leaderboard' : undefined}
      className="border-border bg-card rounded-2xl border p-4 shadow-sm sm:p-5"
    >
      <PanelTitle
        icon={<Trophy className="size-5" />}
        title={final ? 'Bảng xếp hạng cuối cùng' : 'Bảng xếp hạng trực tiếp'}
        trailing={final ? <Info className="text-muted-foreground size-4" /> : undefined}
      />
      {!enabled ? (
        <div className="text-muted-foreground mt-4 rounded-xl border border-dashed p-4 text-sm">
          Sự kiện này không hiển thị bảng xếp hạng.
        </div>
      ) : rows.length === 0 ? (
        <div className="text-muted-foreground mt-4 rounded-xl border border-dashed p-4 text-sm">
          Chưa có tenant nào trong bảng xếp hạng.
        </div>
      ) : (
        <div className="mt-3 divide-y">
          {rows.map((row) =>
            row.kind === 'gap' ? (
              <div
                key={row.key}
                className="text-muted-foreground flex h-10 items-center justify-center text-lg font-bold"
              >
                ...
              </div>
            ) : (
              <LeaderboardRowView key={`${row.row.tenantId}-${row.row.rank}`} row={row.row} />
            ),
          )}
        </div>
      )}
    </section>
  );
}

function LeaderboardRowView({ row }: { row: ReferralLeaderboardRow }) {
  return (
    <div
      className={[
        'flex min-h-14 items-center gap-3 px-1 py-2 text-sm',
        row.currentTenant ? 'bg-primary/10 text-primary rounded-xl px-3 font-semibold' : '',
      ].join(' ')}
    >
      <RankBadge rank={row.rank} currentTenant={row.currentTenant} />
      <TenantAvatar name={row.tenantDisplayName} currentTenant={row.currentTenant} />
      <div className="min-w-0 flex-1">
        <div className="truncate font-semibold">
          {row.currentTenant ? `Bạn — ${row.tenantDisplayName}` : row.tenantDisplayName}
        </div>
        <div className="text-muted-foreground text-xs font-medium">Top #{row.rank}</div>
      </div>
      <div className="shrink-0 text-right font-medium tabular-nums">
        {formatInteger(row.successfulReferrals)} lượt giới thiệu
      </div>
    </div>
  );
}

function TenantAvatar({ name, currentTenant }: { name: string; currentTenant: boolean }) {
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');

  return (
    <span
      className={[
        'flex size-9 shrink-0 items-center justify-center rounded-full text-xs font-bold',
        currentTenant ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground',
      ].join(' ')}
      aria-hidden="true"
    >
      {initials || 'ZM'}
    </span>
  );
}

function RankBadge({ rank, currentTenant }: { rank: number; currentTenant: boolean }) {
  const className = currentTenant
    ? 'bg-primary text-primary-foreground'
    : rank === 1
      ? 'bg-amber-100 text-amber-800'
      : rank === 2
        ? 'bg-muted text-muted-foreground'
        : rank === 3
          ? 'bg-orange-100 text-orange-800'
          : 'bg-muted text-muted-foreground';

  return (
    <span
      className={`flex size-8 shrink-0 items-center justify-center rounded-full text-xs font-bold ${className}`}
    >
      {rank}
    </span>
  );
}

function CountdownBox({ value, label }: { value: number; label: string }) {
  return (
    <div className="bg-primary/5 border-border flex min-h-16 flex-col items-center justify-center rounded-xl border px-1">
      <span className="text-primary text-2xl font-bold tabular-nums sm:text-3xl">
        {padNumber(value)}
      </span>
      <span className="text-muted-foreground text-xs">{label}</span>
    </div>
  );
}

function PanelTitle({
  icon,
  title,
  trailing,
}: {
  icon: ReactNode;
  title: string;
  trailing?: ReactNode;
}) {
  return (
    <div className="text-foreground flex items-center gap-2 text-lg font-semibold">
      <span className="text-primary">{icon}</span>
      <span>{title}</span>
      {trailing ? <span>{trailing}</span> : null}
    </div>
  );
}

function AwardMedal({ rank }: { rank: number }) {
  const medalTone =
    rank === 1
      ? 'from-amber-100 to-amber-300 text-amber-700'
      : rank === 2
        ? 'from-slate-100 to-slate-300 text-slate-600'
        : 'from-orange-100 to-orange-300 text-orange-700';

  return (
    <div className="relative flex flex-col items-center" aria-hidden="true">
      <div
        className={`flex size-40 items-center justify-center rounded-full bg-gradient-to-br text-7xl font-bold shadow-xl ${medalTone}`}
      >
        {rank}
      </div>
      <div className="bg-primary mt-[-10px] h-16 w-20 rounded-b-xl shadow-lg [clip-path:polygon(0_0,100%_0,82%_100%,50%_76%,18%_100%)]" />
    </div>
  );
}

function ConfettiLayer() {
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
      <span className="bg-primary absolute top-8 left-8 size-2 rotate-12 rounded-sm" />
      <span className="absolute top-16 right-14 size-2 rotate-45 rounded-sm bg-amber-400" />
      <span className="absolute bottom-12 left-12 size-2 rounded-full bg-emerald-500" />
      <span className="absolute right-20 bottom-20 size-2 rotate-12 rounded-sm bg-fuchsia-500" />
      <span className="absolute top-1/2 left-1/2 size-1.5 rounded-full bg-sky-500" />
    </div>
  );
}

function DateRangePill({
  startsAt,
  endsAt,
}: {
  startsAt: string | undefined;
  endsAt: string | undefined;
}) {
  if (!startsAt || !endsAt) return null;

  return (
    <div className="border-border bg-card/90 text-foreground flex h-10 items-center gap-2 rounded-xl border px-3 text-sm font-medium shadow-sm">
      <CalendarDays className="text-primary size-4" />
      <span>{formatDateRange(startsAt, endsAt)}</span>
    </div>
  );
}

function RankPodiumIllustration() {
  return (
    <div
      className="bg-primary/5 border-border flex w-48 flex-col items-center rounded-2xl border p-4 sm:w-56"
      aria-hidden="true"
    >
      <div className="bg-primary text-primary-foreground flex size-16 items-center justify-center rounded-2xl shadow-sm">
        <Trophy className="size-9" />
      </div>
      <div className="mt-4 grid h-24 w-full grid-cols-3 items-end gap-3">
        <PodiumStep label="2" heightClassName="h-14" />
        <PodiumStep label="1" heightClassName="h-24" featured />
        <PodiumStep label="3" heightClassName="h-11" />
      </div>
    </div>
  );
}

function PodiumStep({
  label,
  heightClassName,
  featured = false,
}: {
  label: string;
  heightClassName: string;
  featured?: boolean;
}) {
  return (
    <div
      className={[
        'flex items-center justify-center rounded-t-2xl text-2xl font-bold shadow-sm',
        featured ? 'bg-primary text-primary-foreground' : 'bg-primary/15 text-primary',
        heightClassName,
      ].join(' ')}
    >
      {label}
    </div>
  );
}

type LeaderboardDisplayRow =
  | { kind: 'row'; row: ReferralLeaderboardRow }
  | { kind: 'gap'; key: string };

function buildLeaderboardRows(
  rows: ReferralLeaderboardRow[],
  currentTenant: ReferralLeaderboardRow | undefined,
): LeaderboardDisplayRow[] {
  const uniqueRows = new Map<number, ReferralLeaderboardRow>();
  for (const row of rows) {
    uniqueRows.set(row.rank, row);
  }
  if (currentTenant) {
    uniqueRows.set(currentTenant.rank, currentTenant);
  }

  const sortedRows = [...uniqueRows.values()].sort(
    (leftRow, rightRow) => leftRow.rank - rightRow.rank,
  );
  const displayRows: LeaderboardDisplayRow[] = [];
  let previousRank = 0;

  for (const row of sortedRows) {
    if (previousRank > 0 && row.rank - previousRank > 1) {
      displayRows.push({ kind: 'gap', key: `${previousRank}-${row.rank}` });
    }
    displayRows.push({ kind: 'row', row });
    previousRank = row.rank;
  }

  return displayRows;
}

type CountdownParts = {
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
  complete: boolean;
};

function useCountdown(targetDateTime: string | undefined): CountdownParts {
  const [nowMs, setNowMs] = useState(() => Date.now());

  useEffect(() => {
    if (!targetDateTime) return undefined;
    const intervalId = window.setInterval(() => setNowMs(Date.now()), 1000);
    return () => window.clearInterval(intervalId);
  }, [targetDateTime]);

  if (!targetDateTime) {
    return { days: 0, hours: 0, minutes: 0, seconds: 0, complete: false };
  }

  const targetMs = new Date(targetDateTime).getTime();
  if (!Number.isFinite(targetMs)) {
    return { days: 0, hours: 0, minutes: 0, seconds: 0, complete: false };
  }

  const remainingSeconds = Math.max(0, Math.floor((targetMs - nowMs) / 1000));
  const days = Math.floor(remainingSeconds / 86_400);
  const hours = Math.floor((remainingSeconds % 86_400) / 3_600);
  const minutes = Math.floor((remainingSeconds % 3_600) / 60);
  const seconds = remainingSeconds % 60;

  return { days, hours, minutes, seconds, complete: remainingSeconds === 0 };
}

function referralBannerUrl(campaignId: string): string {
  return getPublicApiUrl(`/api/referrals/campaigns/${campaignId}/banner`);
}

function formatDateRange(startsAt: string, endsAt: string): string {
  return `${dateFormatter.format(new Date(startsAt))} - ${dateFormatter.format(new Date(endsAt))}`;
}

function formatEndedAt(endsAt: string): string {
  const endDate = new Date(endsAt);
  return `${timeFormatter.format(endDate)} • ${dateFormatter.format(endDate)}`;
}

function formatInteger(value: number): string {
  return integerFormatter.format(value);
}

function rewardRankCutoffValue(value: number | null | undefined): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return DEFAULT_REWARD_RANK_CUTOFF;
  }
  return Math.max(1, Math.min(100, Math.trunc(value)));
}

function padNumber(value: number): string {
  return value.toString().padStart(2, '0');
}
