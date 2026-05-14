'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { AuditLog } from '@/features/triage/components/AuditLog';
import { SenderSafetyNetList } from '@/features/triage/components/SenderSafetyNetList';
import { ShadowModeCard } from '@/features/triage/components/ShadowModeCard';

const TRIAGE_TABS = ['audit', 'shadow', 'senders'] as const;
type TriageTab = (typeof TRIAGE_TABS)[number];

function normalizeTab(value: string | null): TriageTab {
  return TRIAGE_TABS.includes(value as TriageTab) ? (value as TriageTab) : 'audit';
}

export function TriagePageClient() {
  const t = useTranslations();
  const router = useRouter();
  const searchParams = useSearchParams();
  const tab = normalizeTab(searchParams.get('tab'));

  return (
    <div className="space-y-5">
      <div className="space-y-1">
        <h1 className="text-foreground text-xl font-semibold">{t('triage.page.title')}</h1>
        <p className="text-muted-foreground max-w-3xl text-sm leading-6">
          {t('triage.page.description')}
        </p>
      </div>

      <Tabs
        value={tab}
        onValueChange={(nextTab) => {
          const normalizedTab = normalizeTab(nextTab);
          router.replace(`/triage?tab=${normalizedTab}`, { scroll: false });
        }}
      >
        <div className="overflow-x-auto pb-1">
          <TabsList className="min-w-max" aria-label={t('triage.tabs.label')}>
            <TabsTrigger value="audit">{t('triage.tabs.audit')}</TabsTrigger>
            <TabsTrigger value="shadow">{t('triage.tabs.shadow')}</TabsTrigger>
            <TabsTrigger value="senders">{t('triage.tabs.senders')}</TabsTrigger>
          </TabsList>
        </div>
        <TabsContent value="audit" className="mt-3">
          <AuditLog />
        </TabsContent>
        <TabsContent value="shadow" className="mt-3">
          <ShadowModeCard />
        </TabsContent>
        <TabsContent value="senders" className="mt-3">
          <SenderSafetyNetList />
        </TabsContent>
      </Tabs>
    </div>
  );
}
