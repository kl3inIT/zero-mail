'use client';

import { useTranslations } from 'next-intl';

import { Alert, AlertAction, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';

/**
 * ReconnectPrompt — Phase 01.5 Plan 02 deflation (D-C1, D-C2, D-C3).
 *
 * Replaces StatusAlert variant=warn with raw <Alert variant="warning">.
 * Token-aware classes only — no hardcoded amber literals (closes REVIEW.md §6).
 *
 * Design intent (Plan 04):
 *  - AlertTitle: short subject line ("Google access revoked") — scannable.
 *  - AlertDescription: full sentence body from connectionHealth.reconnectPrompt
 *    i18n key, text-sm — read at leisure after title.
 *  - AlertAction: reconnect button outline/sm — secondary to the alert message.
 *
 * Plain DOM <button> pattern preserved (STATE.md line 153 — vitest @base-ui
 * useRef null-dispatcher boundary).
 */
export function ReconnectPrompt({ onReconnect }: { onReconnect: () => void }) {
  const t = useTranslations();
  return (
    <Alert variant="warning">
      <AlertTitle>{t('connectionHealth.disconnected')}</AlertTitle>
      <AlertDescription>{t('connectionHealth.reconnectPrompt')}</AlertDescription>
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

export function shouldShowReconnectPrompt(
  status: string,
  ingestionHealth: string | undefined,
): boolean {
  return status === 'DISCONNECTED' || (status === 'CONNECTED' && ingestionHealth !== 'HEALTHY');
}

export function ReconnectPromptGate({
  status,
  ingestionHealth,
  onReconnect,
}: {
  status: string;
  ingestionHealth?: string;
  onReconnect: () => void;
}) {
  if (!shouldShowReconnectPrompt(status, ingestionHealth ?? 'HEALTHY')) {
    return null;
  }

  return <ReconnectPrompt onReconnect={onReconnect} />;
}
