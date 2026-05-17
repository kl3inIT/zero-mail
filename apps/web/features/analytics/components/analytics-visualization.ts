import type {
  AnalyticsWindow,
  RuleHitResponse,
  TopSenderResponse,
} from '@/features/analytics/api/analytics-api';

export type TrustLevel = 'high' | 'medium' | 'low';

export type DomainSummary = {
  domain: string;
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
