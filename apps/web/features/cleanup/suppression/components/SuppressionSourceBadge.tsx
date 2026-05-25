'use client';

import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';

type Source = 'manual' | 'replied' | 'auto';

export function SuppressionSourceBadge({
  source,
  className,
}: {
  source: string;
  className?: string;
}) {
  const t = useTranslations();
  const normalized = ((source ?? 'manual').toLowerCase() as Source) ?? 'manual';

  if (normalized === 'replied') {
    return (
      <Badge variant="secondary" className={className}>
        {t('cleanup.suppression.source.replied')}
      </Badge>
    );
  }
  if (normalized === 'auto') {
    return (
      <Badge variant="secondary" className={className}>
        {t('cleanup.suppression.source.auto')}
      </Badge>
    );
  }
  return (
    <Badge variant="outline" className={className}>
      {t('cleanup.suppression.source.manual')}
    </Badge>
  );
}
