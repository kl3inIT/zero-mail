'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { buttonVariants } from '@/components/ui/button';
import { CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { TemplateCard } from '@/features/onboarding/components/TemplateCard';
import { useSelectTemplate } from '@/features/onboarding/hooks/useSelectTemplate';
import { cn } from '@/lib/utils';

type TemplateKey = 'archive-receipts' | 'label-newsletters' | 'pin-calendar';

const templates: { key: TemplateKey; titleKey: string; descKey: string }[] = [
  {
    key: 'archive-receipts',
    titleKey: 'templates.receipts.title',
    descKey: 'templates.receipts.description',
  },
  {
    key: 'label-newsletters',
    titleKey: 'templates.newsletters.title',
    descKey: 'templates.newsletters.description',
  },
  {
    key: 'pin-calendar',
    titleKey: 'templates.calendarInvites.title',
    descKey: 'templates.calendarInvites.description',
  },
];

export function TemplateSelectClient() {
  const t = useTranslations();
  const router = useRouter();
  const me = useCurrentUser();
  const selectMut = useSelectTemplate();
  const [selected, setSelected] = useState<TemplateKey | null>(null);

  useEffect(() => {
    if (!me.data) return;
    const step = me.data.onboardingStep;
    if (step === 'TEMPLATE_SELECTED') router.replace('/onboarding/complete');
    else if (step === 'COMPLETE') router.replace('/settings');
  }, [me.data, router]);

  if (!me.data) {
    return (
      <p className="text-muted-foreground text-sm leading-relaxed">{t('onboarding.loading')}</p>
    );
  }

  return (
    <section className="space-y-6">
      <CardHeader className="px-0">
        <CardTitle>{t('onboarding.template.heading')}</CardTitle>
        <CardDescription className="leading-relaxed">
          {t('onboarding.template.body')}
        </CardDescription>
      </CardHeader>
      <div className="flex flex-col gap-4">
        {templates.map((tpl) => (
          <TemplateCard
            key={tpl.key}
            templateKey={tpl.key}
            title={t(tpl.titleKey as never)}
            description={t(tpl.descKey as never)}
            selected={selected === tpl.key}
            onSelect={() => setSelected(tpl.key)}
          />
        ))}
        <button
          type="button"
          disabled={!selected || selectMut.isPending}
          onClick={() => selected && selectMut.mutate({ templateKey: selected })}
          className={cn(buttonVariants(), 'mt-2 w-full')}
        >
          {selectMut.isPending ? t('common.loading') : t('onboarding.template.saveCta')}
        </button>
      </div>
    </section>
  );
}
