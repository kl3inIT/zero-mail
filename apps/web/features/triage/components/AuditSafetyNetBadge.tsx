'use client';

import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';

type AuditSafetyNetBadgeProps = {
  pattern?: string | null;
};

export function AuditSafetyNetBadge({ pattern }: AuditSafetyNetBadgeProps) {
  const t = useTranslations();
  const trimmedPattern = pattern?.trim();
  if (!trimmedPattern) return null;

  return (
    <Badge
      variant="destructive"
      className="border-destructive/30 max-w-64 truncate"
      title={t('audit.badge.blockedBySafetyNet', { pattern: trimmedPattern })}
    >
      {t('audit.badge.blockedBySafetyNet', { pattern: trimmedPattern })}
    </Badge>
  );
}
