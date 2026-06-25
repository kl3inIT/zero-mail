import { Gift, Timer, Trophy } from 'lucide-react';
import type { Route } from 'next';
import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

import { buttonVariants } from '@/components/ui/button';
import {
  activeReferralBannerUrl,
  fetchActiveReferralCampaign,
} from '@/features/referrals/api/active-campaign-api';
import { LandingReferralBanner } from '@/features/referrals/components/LandingReferralBanner';
import { LandingReferralCountdown } from '@/features/referrals/components/LandingReferralCountdown';
import { ArrowRightIcon } from '@/features/landing/components/PrototypeIcons';
import { cn } from '@/lib/utils';

/**
 * Public marketing band for the live referral campaign, rendered on the landing page right after
 * the hero. Server component: fetches the unauthenticated active-campaign endpoint and renders
 * nothing when no campaign is live or the admin disabled the web banner — so the section is fully
 * admin-controlled with zero fake data. Mirrors the other landing sections (centered header +
 * `rounded-[32px]` container card + `rounded-[24px]` inner cards) on the landing token system.
 */
export async function LandingReferralSection() {
  const campaign = await fetchActiveReferralCampaign();
  if (!campaign.active) return null;

  const t = await getTranslations('landingReferral');
  const name = campaign.name?.trim() || t('defaultName');
  const showCountdown = campaign.countdownEnabled !== false && Boolean(campaign.endsAt);
  const showBanner = Boolean(campaign.bannerImageAvailable);
  const rewardNotification = campaign.rewardNotificationText?.trim();
  const rewardCutoff =
    typeof campaign.rewardRankCutoff === 'number' && campaign.rewardRankCutoff > 0
      ? campaign.rewardRankCutoff
      : undefined;

  return (
    <section className="bg-(--bg) py-20" id="referral-event">
      <div className="zm-container">
        {/* Centered section header — same rhythm as the other landing sections */}
        <div className="mb-12 text-center">
          <h2 className="text-4xl leading-[1.2] font-extrabold tracking-tighter text-(--ink) md:text-5xl">
            {t('heading')}
          </h2>
          <p className="mx-auto mt-5 max-w-2xl text-xl leading-relaxed font-semibold text-(--ink)">
            {name}
          </p>
        </div>

        <div className="mx-auto max-w-5xl">
          {showBanner ? (
            <div className="mb-4 md:mb-6">
              <LandingReferralBanner src={activeReferralBannerUrl()} alt={name} />
            </div>
          ) : null}

          <div className="grid gap-4 md:gap-6 lg:grid-cols-2">
            {/* Card A — reward + join CTAs */}
            <div className="flex flex-col justify-between gap-8 rounded-[24px] border border-(--line) bg-(--bg-elevated) p-8 shadow-sm">
              <div>
                <div className="mb-5 flex size-10 items-center justify-center rounded-xl bg-(--accent-soft) text-(--accent)">
                  <Gift className="size-5" />
                </div>
                {rewardCutoff ? (
                  <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-(--accent)/25 bg-(--accent-soft) px-3 py-1.5 text-sm font-semibold text-(--accent)">
                    <Trophy className="size-4" />
                    {t('rewardChip', { count: rewardCutoff })}
                  </div>
                ) : null}
                <p className="text-[15px] leading-relaxed text-(--text-muted)">
                  {rewardNotification || t('rewardFallback')}
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <Link
                  href="/login"
                  className={cn(
                    buttonVariants({ variant: 'ink', size: 'lg' }),
                    'h-12 rounded-full px-7 text-[15px] font-medium shadow-sm',
                  )}
                >
                  {t('cta')}
                  <ArrowRightIcon size={16} className="ml-2" />
                </Link>
                <Link
                  href={'/referrals' as Route}
                  className={cn(
                    buttonVariants({ variant: 'outline', size: 'lg' }),
                    'h-12 rounded-full border-(--line-strong) bg-(--bg-elevated) px-6 text-[15px] font-medium text-(--ink) hover:bg-(--bg-subtle)',
                  )}
                >
                  {t('leaderboardCta')}
                </Link>
              </div>
            </div>

            {/* Card B — live countdown */}
            <div className="flex flex-col rounded-[24px] border border-(--line) bg-(--bg-elevated) p-8 shadow-sm">
              <div className="mb-6 flex size-10 items-center justify-center rounded-xl bg-(--accent-soft) text-(--accent)">
                <Timer className="size-5" />
              </div>
              <div className="mb-5 text-2xl leading-tight font-bold text-(--ink)">
                {t('countdownTitle')}
              </div>
              {showCountdown && campaign.endsAt ? (
                <div className="mt-auto">
                  <LandingReferralCountdown
                    endsAt={campaign.endsAt}
                    labels={{
                      days: t('countdown.days'),
                      hours: t('countdown.hours'),
                      minutes: t('countdown.minutes'),
                      seconds: t('countdown.seconds'),
                    }}
                  />
                </div>
              ) : (
                <p className="text-[15px] leading-relaxed text-(--text-muted)">
                  {t('noCountdown')}
                </p>
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
