'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { RadioGroup } from '@/components/ui/radio-group';
import { LoadingState } from '@/components/states/LoadingState';
import { useCurrentUser } from '@/features/account/hooks/useCurrentUser';
import { TemplateCard } from '@/features/onboarding/components/TemplateCard';
import { useSelectTemplate } from '@/features/onboarding/hooks/useSelectTemplate';

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

function isTemplateKey(value: unknown): value is TemplateKey {
  return templates.some((tpl) => tpl.key === value);
}

export function TemplateSelectClient() {
  const t = useTranslations();
  const router = useRouter();
  const me = useCurrentUser();
  const selectMut = useSelectTemplate();
  const [selected, setSelected] = useState<TemplateKey | ''>('');

  useEffect(() => {
    if (!me.data) return;
    const step = me.data.onboardingStep;
    if (step === 'TEMPLATE_SELECTED') router.replace('/onboarding/complete');
    else if (step === 'COMPLETE') router.replace('/settings');
  }, [me.data, router]);

  if (!me.data) {
    return (
      <section className="bg-card w-full min-w-0 rounded-md border p-5 shadow-sm sm:p-7">
        <LoadingState count={1} />
      </section>
    );
  }

  return (
    <section className="bg-card w-full min-w-0 rounded-md border p-6 shadow-sm sm:p-8">
      <span className="text-muted-foreground inline-flex items-center gap-2 font-mono text-xs font-medium uppercase">
        <span className="bg-primary size-1.5 rounded-full" />
        {t('onboarding.template.eyebrow')}
      </span>
      <h1 className="text-foreground mt-4 text-[28px] leading-tight font-semibold">
        <span>{t('onboarding.template.heading')}</span>
      </h1>
      <p className="text-muted-foreground mt-4 max-w-xl text-sm leading-relaxed">
        {t('onboarding.template.body')}
      </p>
      <RadioGroup
        className="mt-7 gap-3"
        value={selected}
        onValueChange={(value) => {
          if (isTemplateKey(value)) setSelected(value);
        }}
        aria-label={t('onboarding.template.heading')}
      >
        {templates.map((tpl) => (
          <TemplateCard
            key={tpl.key}
            templateKey={tpl.key}
            title={t(tpl.titleKey as never)}
            description={t(tpl.descKey as never)}
            selected={selected === tpl.key}
          />
        ))}
      </RadioGroup>
      <div className="flex flex-col gap-4">
        <Button
          type="button"
          variant="accent"
          size="lg"
          disabled={!selected || selectMut.isPending}
          onClick={() => selected && selectMut.mutate({ templateKey: selected })}
          className="mt-2 h-11 w-full"
        >
          {selectMut.isPending ? t('common.loading') : t('onboarding.template.saveCta')}
        </Button>
      </div>
    </section>
  );
}
