'use client';

import { CheckCircle2, Clock, Loader2, XCircle } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

type State = 'PENDING' | 'RUNNING' | 'OK' | 'FAILED';

export function PerSenderStateBadge({ state, className }: { state: string; className?: string }) {
  const t = useTranslations();
  const normalized = (state ?? 'PENDING') as State;

  if (normalized === 'PENDING') {
    return (
      <Badge variant="secondary" className={className}>
        <Clock />
        {t('cleanup.unsubscribe.status.state.pending')}
      </Badge>
    );
  }
  if (normalized === 'RUNNING') {
    return (
      <Badge
        variant="secondary"
        className={cn(
          'bg-[var(--blue-soft,theme(colors.blue.100))] text-[var(--blue,theme(colors.blue.700))]',
          className,
        )}
      >
        <Loader2 className="animate-spin" />
        {t('cleanup.unsubscribe.status.state.running')}
      </Badge>
    );
  }
  if (normalized === 'OK') {
    return (
      <Badge
        variant="secondary"
        className={cn(
          'bg-[var(--green-soft,theme(colors.green.100))] text-[var(--green,theme(colors.green.700))]',
          className,
        )}
      >
        <CheckCircle2 />
        {t('cleanup.unsubscribe.status.state.ok')}
      </Badge>
    );
  }
  return (
    <Badge variant="destructive" className={className}>
      <XCircle />
      {t('cleanup.unsubscribe.status.state.failed')}
    </Badge>
  );
}
