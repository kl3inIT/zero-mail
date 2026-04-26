'use client';

import { useTranslations } from 'next-intl';

import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';

export function ReconnectPrompt({ onReconnect }: { onReconnect: () => void }) {
  const t = useTranslations();
  return (
    <Alert variant="default" className="border-amber-500 bg-amber-50">
      <p>{t('connectionHealth.reconnectPrompt')}</p>
      <Button onClick={onReconnect} className="mt-3">
        {t('settings.gmailConnection.reconnectCta')}
      </Button>
    </Alert>
  );
}
