'use client';

import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';

type Method = 'ONE_CLICK' | 'MAILTO' | 'NONE';

export function MethodBadge({ method, className }: { method: string; className?: string }) {
  const t = useTranslations();
  const normalized = (method ?? 'NONE') as Method;

  if (normalized === 'ONE_CLICK') {
    return (
      <Badge variant="outline" className={className}>
        {t('cleanup.unsubscribe.method.oneClick')}
      </Badge>
    );
  }
  if (normalized === 'MAILTO') {
    return (
      <Badge variant="outline" className={className}>
        {t('cleanup.unsubscribe.method.mailto')}
      </Badge>
    );
  }
  return (
    <Badge variant="secondary" className={className}>
      {t('cleanup.unsubscribe.method.none')}
    </Badge>
  );
}
