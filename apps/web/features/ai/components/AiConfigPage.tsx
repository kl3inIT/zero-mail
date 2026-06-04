'use client';

import { useTranslations } from 'next-intl';

import { Separator } from '@/components/ui/separator';
import { AiProviderSection } from '@/features/ai/components/AiProviderSection';
import { BehaviorSection } from '@/features/ai/components/BehaviorSection';
import { SafetyNetSection } from '@/features/ai/components/SafetyNetSection';
import { UpdatesSection } from '@/features/ai/components/UpdatesSection';
import { YourVoiceSection } from '@/features/ai/components/YourVoiceSection';

export function AiConfigPage() {
  const t = useTranslations();

  return (
    <div className="w-full space-y-8">
      <header className="space-y-1">
        <h1 className="text-foreground text-2xl font-semibold tracking-normal">
          {t('ai.page.title')}
        </h1>
        <p className="text-muted-foreground text-sm">{t('ai.page.description')}</p>
      </header>

      <div className="space-y-8">
        <YourVoiceSection />
        <Separator className="my-8" />
        <BehaviorSection />
        <Separator className="my-8" />
        <UpdatesSection />
        <Separator className="my-8" />
        <SafetyNetSection />
        <Separator className="my-8" />
        <AiProviderSection />
      </div>
    </div>
  );
}
