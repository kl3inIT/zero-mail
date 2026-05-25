'use client';

import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';

type Method = 'ONE_CLICK' | 'MAILTO' | 'NONE';

export function MethodBadge({ method, className }: { method: string; className?: string }) {
  const t = useTranslations();
  const normalized = (method ?? 'NONE') as Method;

  if (normalized === 'ONE_CLICK') {
    return (
      <Tooltip>
        <TooltipTrigger
          render={
            <Badge variant="outline" className={className}>
              {t('cleanup.unsubscribe.method.oneClick')}
            </Badge>
          }
        />
        <TooltipContent>{t('cleanup.unsubscribe.method.oneClickTooltip')}</TooltipContent>
      </Tooltip>
    );
  }
  if (normalized === 'MAILTO') {
    return (
      <Tooltip>
        <TooltipTrigger
          render={
            <Badge variant="outline" className={className}>
              {t('cleanup.unsubscribe.method.mailto')}
            </Badge>
          }
        />
        <TooltipContent>{t('cleanup.unsubscribe.method.mailtoTooltip')}</TooltipContent>
      </Tooltip>
    );
  }
  return (
    <Badge variant="secondary" className={className}>
      {t('cleanup.unsubscribe.method.none')}
    </Badge>
  );
}
