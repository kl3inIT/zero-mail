'use client';

import type { ReactNode } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { CheckCircle2, Filter, Inbox, RotateCcw } from 'lucide-react';

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { AuditLog } from '@/features/triage/components/AuditLog';
import { SenderSafetyNetList } from '@/features/triage/components/SenderSafetyNetList';

const TRIAGE_TABS = ['audit', 'senders'] as const;
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

      <div className="grid gap-2 sm:grid-cols-4">
        <FlowStep icon={<Inbox className="size-4" />} label={t('triage.flow.observed')} />
        <FlowStep icon={<Filter className="size-4" />} label={t('triage.flow.matched')} />
        <FlowStep icon={<CheckCircle2 className="size-4" />} label={t('triage.flow.applied')} />
        <FlowStep icon={<RotateCcw className="size-4" />} label={t('triage.flow.undo')} />
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
            <TabsTrigger value="senders">{t('triage.tabs.senders')}</TabsTrigger>
          </TabsList>
        </div>
        <TabsContent value="audit" className="mt-3">
          <AuditLog />
        </TabsContent>
        <TabsContent value="senders" className="mt-3">
          <SenderSafetyNetList />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function FlowStep({ icon, label }: { icon: ReactNode; label: string }) {
  return (
    <div className="bg-muted/25 flex min-h-12 items-center gap-2 rounded-md border px-3 py-2 text-xs font-medium">
      <span className="text-primary shrink-0" aria-hidden="true">
        {icon}
      </span>
      <span className="leading-5">{label}</span>
    </div>
  );
}
