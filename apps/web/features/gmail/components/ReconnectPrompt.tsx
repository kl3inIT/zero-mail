'use client';

import { useTranslations } from 'next-intl';

import { Alert, AlertAction, AlertTitle } from '@/components/ui/alert';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';

/**
 * ReconnectPrompt — Phase 01.5 Plan 02 deflation (D-C1, D-C2, D-C3).
 *
 * Replaces StatusAlert variant=warn with raw <Alert variant="warning">.
 * Token-aware classes only — no hardcoded amber literals (closes REVIEW.md §6).
 *
 * Plain DOM <button> pattern preserved (STATE.md line 153 — vitest @base-ui
 * useRef null-dispatcher boundary).
 */
export function ReconnectPrompt({ onReconnect }: { onReconnect: () => void }) {
  const t = useTranslations();
  return (
    <Alert variant="warning">
      <AlertTitle>{t('connectionHealth.reconnectPrompt')}</AlertTitle>
      <AlertAction>
        <button
          type="button"
          onClick={onReconnect}
          className={cn(buttonVariants({ variant: 'outline', size: 'sm' }))}
        >
          {t('settings.gmailConnection.reconnectCta')}
        </button>
      </AlertAction>
    </Alert>
  );
}
