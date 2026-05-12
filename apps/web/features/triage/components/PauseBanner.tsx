'use client';

import { useTranslations } from 'next-intl';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { useTriagePauseState } from '@/features/triage/hooks/useTriagePauseState';
import { useToggleTriagePause } from '@/features/triage/hooks/useToggleTriagePause';

export function PauseBanner() {
  const pauseState = useTriagePauseState();
  const t = useTranslations();
  const { mutate: togglePause, isPending } = useToggleTriagePause();

  if (pauseState.data !== true) return null;

  return (
    <div className="mx-auto w-full max-w-2xl px-4 pt-4 sm:px-6 sm:pt-6" data-testid="pause-banner">
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
    </div>
  );
}
