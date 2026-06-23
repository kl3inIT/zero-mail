import type { Metadata } from 'next';
import { getTranslations } from 'next-intl/server';
import { CalendarDays, MessageCircle } from 'lucide-react';

import { SectionHeader } from '@/features/ai/components/SectionHeader';
import { CalendarIntegrationCard } from '@/features/calendar/components/CalendarIntegrationCard';
import { TelegramIntegrationCard } from '@/features/telegram/components/TelegramIntegrationCard';

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('integrations');
  return { title: t('heading') };
}

/**
 * Integrations hub. Telegram lives here as its own destination (moved out of
 * Settings) so the connect flow is reachable directly from the sidebar. Future
 * channels/integrations get their own sections on this page.
 */
export default async function IntegrationsPage() {
  const t = await getTranslations();
  return (
    <div className="flex h-full flex-col">
      <div className="flex-1 overflow-auto p-3 sm:p-6">
        <div className="mx-auto w-full max-w-3xl space-y-8">
          <header className="space-y-1">
            <h1 className="text-foreground text-2xl font-semibold tracking-normal">
              {t('integrations.heading')}
            </h1>
            <p className="text-muted-foreground text-sm">{t('integrations.description')}</p>
          </header>

          <section className="space-y-4" aria-labelledby="integration-calendar">
            <SectionHeader
              id="integration-calendar"
              title={t('calendar.title')}
              helperText={t('calendar.integration.helper')}
              icon={CalendarDays}
            />
            <CalendarIntegrationCard />
          </section>

          <section className="space-y-4" aria-labelledby="integration-telegram">
            <SectionHeader
              id="integration-telegram"
              title={t('settings.telegramConnection.heading')}
              helperText={t('settings.telegramConnection.helper')}
              icon={MessageCircle}
            />
            <TelegramIntegrationCard />
          </section>
        </div>
      </div>
    </div>
  );
}
