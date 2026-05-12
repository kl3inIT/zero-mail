'use client';

import { useTranslations } from 'next-intl';
import { AlertTriangle } from 'lucide-react';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';

type TopupExpiredProps = {
  onRestart: () => void;
};

export function TopupExpired({ onRestart }: TopupExpiredProps) {
  const t = useTranslations();

  return (
    <Alert variant="warning" data-testid="topup-expired-step">
      <AlertTriangle className="size-4" aria-hidden="true" />
      <AlertTitle>
        <h2>{t('billing.topup.expired.heading')}</h2>
      </AlertTitle>
      <AlertDescription>
        <div className="space-y-3">
          <p>{t('billing.topup.expired.body')}</p>
          <Button type="button" variant="outline" onClick={onRestart}>
            {t('billing.topup.expired.cta')}
          </Button>
        </div>
      </AlertDescription>
    </Alert>
  );
}
