import type {
  ActionMixResponse,
  AnalyticsWindow,
  CategoryLoadResponse,
  DailyLoadResponse,
  DomainLoadResponse,
  ReplyBucketResponse,
  RuleHitResponse,
  TopSenderResponse,
} from '@/features/analytics/api/analytics-api';

export type TrustLevel = 'high' | 'medium' | 'low';

export type DomainSummary = {
  domain: string;
  count: number;
};

export type LabeledCount = {
  label: string;
  count: number;
};

export function safeCount(value: number | undefined): number {
  return Number.isFinite(value) ? Math.max(0, Math.trunc(value ?? 0)) : 0;
}

export function clampRatio(value: number): number {
  if (!Number.isFinite(value)) {
    return 0;
  }

  return Math.min(1, Math.max(0, value));
}

export function percentOf(numerator: number, denominator: number): number {
  if (denominator <= 0) {
    return 0;
  }

  return clampRatio(numerator / denominator);
}

export function formatPercent(ratio: number): string {
  return `${Math.round(clampRatio(ratio) * 100)}%`;
}

export function formatCompactCount(value: number): string {
  return new Intl.NumberFormat('en', {
    notation: value >= 10000 ? 'compact' : 'standard',
    maximumFractionDigits: 1,
  }).format(value);
}

export function windowDays(window: AnalyticsWindow): number {
  switch (window) {
    case '30d':
      return 30;
    case '90d':
      return 90;
    case '7d':
    default:
      return 7;
  }
}

export function totalReverted(ruleHits: RuleHitResponse[] | undefined): number {
  return (ruleHits ?? []).reduce((sum, ruleHit) => sum + safeCount(ruleHit.reverted), 0);
}

export function rulePrecision(ruleHit: RuleHitResponse): number {
  return percentOf(safeCount(ruleHit.applied), safeCount(ruleHit.decisions));
}

export function trustLevel(precision: number): TrustLevel {
  if (precision >= 0.9) {
    return 'high';
  }

  if (precision >= 0.7) {
    return 'medium';
  }

  return 'low';
}

export function topDomainSummaries(senders: TopSenderResponse[] | undefined): DomainSummary[] {
  const domainCounts = new Map<string, number>();

  for (const sender of senders ?? []) {
    const email = sender.senderEmail ?? '';
    const domain = email.includes('@') ? email.split('@').pop()?.toLowerCase() : undefined;

    if (!domain) {
      continue;
    }

    domainCounts.set(domain, (domainCounts.get(domain) ?? 0) + safeCount(sender.count));
  }

  return Array.from(domainCounts.entries())
    .map(([domain, count]) => ({ domain, count }))
    .sort((firstDomain, secondDomain) => secondDomain.count - firstDomain.count);
}

export function totalDailyObserved(dailyLoad: DailyLoadResponse[] | undefined): number {
  return (dailyLoad ?? []).reduce((sum, day) => sum + safeCount(day.observed), 0);
}

export function maxDailyTotal(dailyLoad: DailyLoadResponse[] | undefined): number {
  return Math.max(
    1,
    ...(dailyLoad ?? []).map(
      (day) => safeCount(day.observed) + safeCount(day.applied) + safeCount(day.reverted),
    ),
  );
}

export function totalActionMix(actionMix: ActionMixResponse[] | undefined): number {
  return (actionMix ?? []).reduce(
    (sum, action) =>
      sum + safeCount(action.applied) + safeCount(action.reverted) + safeCount(action.failed),
    0,
  );
}

export function normalizeCategoryLabel(category: string | undefined): string {
  if (!category) {
    return 'unknown';
  }

  return category
    .toLowerCase()
    .split(/[_\s-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export function normalizeActionLabel(actionType: string | undefined): string {
  if (!actionType) {
    return 'Unknown';
  }

  return actionType
    .toLowerCase()
    .split(/[_\s-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export function compactDayLabel(day: string | undefined): string {
  if (!day) {
    return '';
  }

  const [, month, date] = day.split('-');
  return month && date ? `${month}/${date}` : day;
}

export function topDomainLoad(
  domainLoad: DomainLoadResponse[] | undefined,
  fallbackSenders: TopSenderResponse[] | undefined,
): DomainSummary[] {
  const serverDomains = (domainLoad ?? [])
    .filter((domain) => domain.domain)
    .map((domain) => ({
      domain: domain.domain ?? '',
      count: safeCount(domain.count),
    }));

  return serverDomains.length > 0 ? serverDomains : topDomainSummaries(fallbackSenders);
}

export function topCategories(categoryLoad: CategoryLoadResponse[] | undefined): LabeledCount[] {
  return (categoryLoad ?? []).map((category) => ({
    label: normalizeCategoryLabel(category.category),
    count: safeCount(category.count),
  }));
}

export function replyBucketLabel(bucket: string | undefined): string {
  switch (bucket) {
    case 'TO_REPLY':
      return 'To reply';
    case 'AWAITING_THEIR_REPLY':
      return 'Waiting';
    case 'FYI':
      return 'FYI';
    case 'ACTIONED':
      return 'Actioned';
    default:
      return bucket ? normalizeActionLabel(bucket) : 'Unknown';
  }
}

export function replyBucketTotal(replyBuckets: ReplyBucketResponse[] | undefined): number {
  return (replyBuckets ?? []).reduce((sum, bucket) => sum + safeCount(bucket.count), 0);
}
