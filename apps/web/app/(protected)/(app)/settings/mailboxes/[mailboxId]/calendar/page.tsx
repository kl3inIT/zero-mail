import { getTranslations } from 'next-intl/server';

import { CalendarSettingsClient } from './CalendarSettingsClient';

interface PageProps {
  params: Promise<{ mailboxId: string }>;
}

/**
 * Phase 12 W3 — per-mailbox calendar settings page (D-07). Server-component
 * shell renders the title + subtitle, then mounts the client orchestrator
 * which owns TanStack Query state.
 *
 * Layout per D-07: Inbox Zero shell (empty-state Card OR stacked connection
 * cards with collapsible sub-calendar Switch list + DropdownMenu disconnect)
 * PLUS the Calendly-style role-assignment section beneath it.
 */
export default async function CalendarSettingsPage({ params }: PageProps) {
  const { mailboxId } = await params;
  const t = await getTranslations();

  return (
    <div className="space-y-6 p-6">
      <header>
        <h1 className="text-foreground text-2xl font-semibold">{t('calendar.title')}</h1>
        <p className="text-muted-foreground mt-1 text-sm">{t('calendar.subtitle')}</p>
      </header>
      <CalendarSettingsClient mailboxId={mailboxId} />
    </div>
  );
}
