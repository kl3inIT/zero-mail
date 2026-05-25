'use client';

import { Ban, ShieldCheck, ShieldX } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

type Risk = 'SAFE' | 'NO_HEADER_DISABLED' | 'SUPPRESSED_BLOCKED';

export function RiskBadge({ risk, className }: { risk: string; className?: string }) {
  const t = useTranslations();
  const normalized = (risk ?? 'SAFE') as Risk;

  if (normalized === 'SAFE') {
    return (
      <Badge
        variant="secondary"
        className={cn(
          'bg-[var(--green-soft,theme(colors.green.100))] text-[var(--green,theme(colors.green.700))]',
          className,
        )}
      >
        <ShieldCheck />
        {t('cleanup.unsubscribe.risk.safe')}
      </Badge>
    );
  }
  if (normalized === 'SUPPRESSED_BLOCKED') {
    return (
      <Badge variant="destructive" className={className}>
        <ShieldX />
        {t('cleanup.unsubscribe.risk.suppressed')}
      </Badge>
    );
  }
  // NO_HEADER_DISABLED
  return (
    <Badge variant="secondary" className={className}>
      <Ban />
      {t('cleanup.unsubscribe.risk.noHeader')}
    </Badge>
  );
}
