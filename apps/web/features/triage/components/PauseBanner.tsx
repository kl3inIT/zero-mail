'use client';

import { useTranslations } from 'next-intl';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { useToggleTriagePause } from '@/features/triage/hooks/useToggleTriagePause';

export function PauseBanner() {
  const { data: user } = useCurrentUser();
  const t = useTranslations();
  const { mutate: togglePause, isPending } = useToggleTriagePause();

  if (!user?.triagePaused) return null;

  return (
    <Alert variant="warning" role="alert">
      <AlertTitle>{t('settings.triage.pause.banner.heading')}</AlertTitle>
      <AlertDescription className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <span>{t('settings.triage.pause.banner.body')}</span>
        <button
          type="button"
          onClick={() => togglePause(false)}
          disabled={isPending}
          className="text-warning shrink-0 text-sm font-medium underline underline-offset-4 disabled:pointer-events-none disabled:opacity-60"
        >
          {t('settings.triage.pause.banner.unpause')}
        </button>
      </AlertDescription>
    </Alert>
  );
}
