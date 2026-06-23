'use client';

import Link from 'next/link';
import { ExternalLink } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { buttonVariants } from '@/components/ui/button';
import { SettingCard } from '@/features/ai/components/SettingCard';
import { ConnectCalendarButton } from '@/features/calendar/components/ConnectCalendarButton';
import { useCalendarConnections } from '@/features/calendar/hooks/use-calendar-connections';
import { useActiveMailbox } from '@/features/mailbox/hooks/useActiveMailbox';
import { cn } from '@/lib/utils';

/**
 * Calendar entry on the Integrations hub. The /settings/mailboxes/[mailboxId]/calendar
 * route shipped in Phase 12 W3 but had no navigation entry (UAT-1 gap) — this card is the
 * reachable launcher. Calendar connections are workspace-shared (CAL-CONN-06) but role
 * assignment is per-mailbox, so the deep link targets the active mailbox.
 */
export function CalendarIntegrationCard() {
  const t = useTranslations();
  const activeMailbox = useActiveMailbox();
  const activeMailboxId = activeMailbox.data?.gmailConnectionId ?? null;
  const connections = useCalendarConnections(activeMailboxId ?? '');

  if (!activeMailboxId) {
    return (
      <SettingCard>
        <p className="text-muted-foreground text-sm">{t('calendar.integration.noMailbox')}</p>
      </SettingCard>
    );
  }

  const connectionCount = connections.data?.length ?? 0;
  const hasConnections = connectionCount > 0;

  return (
    <SettingCard
      rightSlot={
        hasConnections ? (
          <Badge variant="secondary">
            {t('calendar.integration.connectedSummary', { count: connectionCount })}
          </Badge>
        ) : (
          <Badge variant="outline">{t('calendar.integration.notConnected')}</Badge>
        )
      }
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-muted-foreground text-sm">{t('calendar.subtitle')}</p>
        <div className="flex shrink-0 items-center gap-2">
          {!hasConnections && (
            <ConnectCalendarButton mailboxId={activeMailboxId} variant="outline" />
          )}
          <Link
            href={`/settings/mailboxes/${activeMailboxId}/calendar`}
            className={cn(buttonVariants({ variant: hasConnections ? 'default' : 'ghost' }))}
            data-testid="calendar-integration-manage-link"
          >
            {t('calendar.integration.manageCta')}
            <ExternalLink className="ml-1.5 size-4" />
          </Link>
        </div>
      </div>
    </SettingCard>
  );
}
