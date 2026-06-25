export const referralMessages = {
  'errors.referral.campaign.activeConflict': {
    vi: 'Chỉ được bật một sự kiện referral trên toàn hệ thống.',
    en: 'Only one referral campaign can be active across the system.',
  },
  'errors.referral.campaign.bannerInvalid': {
    vi: 'Banner phải là ảnh PNG, JPG hoặc WebP và không vượt quá 5MB.',
    en: 'Banner must be a PNG, JPG, or WebP image and must not exceed 5MB.',
  },
} as const;

/**
 * Public marketing-landing referral band (LandingReferralSection). Distinct namespace from the
 * authenticated /referrals page copy — these strings are shown to logged-out visitors.
 */
export const landingReferralMessages = {
  'landingReferral.heading': {
    vi: 'Sự kiện đang diễn ra',
    en: 'Event in progress',
  },
  'landingReferral.defaultName': {
    vi: 'Mời bạn bè dùng Zero Mail',
    en: 'Invite friends to Zero Mail',
  },
  'landingReferral.defaultDescription': {
    vi: 'Mỗi người bạn đăng ký thành công qua link của bạn, cả hai cùng nhận thưởng. Đăng nhập để lấy link giới thiệu của riêng bạn.',
    en: 'For every friend who signs up through your link, you both get rewarded. Sign in to grab your personal referral link.',
  },
  'landingReferral.cta': {
    vi: 'Tham gia ngay',
    en: 'Join now',
  },
  'landingReferral.leaderboardCta': {
    vi: 'Xem bảng xếp hạng',
    en: 'View leaderboard',
  },
  'landingReferral.rewardChip': {
    vi: 'Top {count} nhận thưởng đặc biệt',
    en: 'Top {count} win special rewards',
  },
  'landingReferral.countdownTitle': {
    vi: 'Sự kiện kết thúc sau',
    en: 'Event ends in',
  },
  'landingReferral.rewardFallback': {
    vi: 'Mời càng nhiều bạn, phần thưởng càng lớn. Đăng nhập để lấy link giới thiệu của bạn.',
    en: 'The more friends you invite, the bigger the reward. Sign in to get your referral link.',
  },
  'landingReferral.noCountdown': {
    vi: 'Sự kiện đang diễn ra — tham gia bất cứ lúc nào.',
    en: 'The event is live — join any time.',
  },
  'landingReferral.countdown.days': {
    vi: 'Ngày',
    en: 'Days',
  },
  'landingReferral.countdown.hours': {
    vi: 'Giờ',
    en: 'Hours',
  },
  'landingReferral.countdown.minutes': {
    vi: 'Phút',
    en: 'Mins',
  },
  'landingReferral.countdown.seconds': {
    vi: 'Giây',
    en: 'Secs',
  },
} as const;
