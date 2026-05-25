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
type TemplateTitleKey =
  | 'templates.receipts.title'
  | 'templates.newsletters.title'
  | 'templates.calendarInvites.title';
type TemplateDescriptionKey =
  | 'templates.receipts.description'
  | 'templates.newsletters.description'
  | 'templates.calendarInvites.description';
type PreviewVariant = 'action' | 'ignore';

const templates: {
  key: TemplateKey;
  badge: string;
  titleKey: TemplateTitleKey;
  descKey: TemplateDescriptionKey;
}[] = [
  {
    key: 'label-newsletters',
    badge: 'N',
    titleKey: 'templates.newsletters.title',
    descKey: 'templates.newsletters.description',
  },
  {
    key: 'archive-receipts',
    badge: 'R',
    titleKey: 'templates.receipts.title',
    descKey: 'templates.receipts.description',
  },
  {
    key: 'pin-calendar',
    badge: 'C',
    titleKey: 'templates.calendarInvites.title',
    descKey: 'templates.calendarInvites.description',
  },
];

const previewByTemplate: Record<
  TemplateKey,
  {
    titleKey: string;
    statusKey: string;
    rows: {
      senderKey: string;
      clueKey: string;
      actionKey: string;
      variant: PreviewVariant;
    }[];
  }
> = {
  'label-newsletters': {
    titleKey: 'onboarding.template.preview.newsletters.title',
    statusKey: 'onboarding.template.preview.status',
    rows: [
      {
        senderKey: 'onboarding.template.preview.newsletters.row1Sender',
        clueKey: 'onboarding.template.preview.newsletters.row1Clue',
        actionKey: 'onboarding.template.preview.newsletters.row1Action',
        variant: 'action',
      },
      {
        senderKey: 'onboarding.template.preview.newsletters.row2Sender',
        clueKey: 'onboarding.template.preview.newsletters.row2Clue',
        actionKey: 'onboarding.template.preview.newsletters.row2Action',
        variant: 'action',
      },
      {
        senderKey: 'onboarding.template.preview.newsletters.row3Sender',
        clueKey: 'onboarding.template.preview.newsletters.row3Clue',
        actionKey: 'onboarding.template.preview.newsletters.row3Action',
        variant: 'ignore',
      },
    ],
  },
  'archive-receipts': {
    titleKey: 'onboarding.template.preview.receipts.title',
    statusKey: 'onboarding.template.preview.status',
    rows: [
      {
        senderKey: 'onboarding.template.preview.receipts.row1Sender',
        clueKey: 'onboarding.template.preview.receipts.row1Clue',
        actionKey: 'onboarding.template.preview.receipts.row1Action',
        variant: 'action',
      },
      {
        senderKey: 'onboarding.template.preview.receipts.row2Sender',
        clueKey: 'onboarding.template.preview.receipts.row2Clue',
        actionKey: 'onboarding.template.preview.receipts.row2Action',
        variant: 'action',
      },
      {
        senderKey: 'onboarding.template.preview.receipts.row3Sender',
        clueKey: 'onboarding.template.preview.receipts.row3Clue',
        actionKey: 'onboarding.template.preview.receipts.row3Action',
        variant: 'ignore',
      },
    ],
  },
  'pin-calendar': {
    titleKey: 'onboarding.template.preview.calendar.title',
    statusKey: 'onboarding.template.preview.status',
    rows: [
      {
        senderKey: 'onboarding.template.preview.calendar.row1Sender',
        clueKey: 'onboarding.template.preview.calendar.row1Clue',
        actionKey: 'onboarding.template.preview.calendar.row1Action',
        variant: 'action',
      },
      {
        senderKey: 'onboarding.template.preview.calendar.row2Sender',
        clueKey: 'onboarding.template.preview.calendar.row2Clue',
        actionKey: 'onboarding.template.preview.calendar.row2Action',
        variant: 'action',
      },
      {
        senderKey: 'onboarding.template.preview.calendar.row3Sender',
        clueKey: 'onboarding.template.preview.calendar.row3Clue',
        actionKey: 'onboarding.template.preview.calendar.row3Action',
        variant: 'ignore',
      },
    ],
  },
};

function isTemplateKey(value: unknown): value is TemplateKey {
  return templates.some((tpl) => tpl.key === value);
}

export function TemplateSelectClient() {
  const t = useTranslations();
  const router = useRouter();
  const me = useCurrentUser();
  const selectMut = useSelectTemplate();
  const [selected, setSelected] = useState<TemplateKey>('label-newsletters');

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

  const preview = previewByTemplate[selected];

  return (
    <section className="bg-card w-full min-w-0 rounded-md border p-5 shadow-sm sm:p-7">
      <div className="grid gap-7 lg:grid-cols-[minmax(280px,0.9fr)_minmax(0,1.1fr)]">
        <div className="min-w-0">
          <span className="text-muted-foreground inline-flex items-center gap-2 font-mono text-xs font-medium uppercase">
            <span className="bg-primary size-1.5 rounded-full" />
            {t('onboarding.template.eyebrow')}
          </span>
          <h1 className="text-foreground mt-4 text-[28px] leading-tight font-semibold">
            {t('onboarding.template.heading')}
          </h1>
          <p className="text-muted-foreground mt-4 text-sm leading-relaxed">
            {t('onboarding.template.body')}
          </p>
          <RadioGroup
            className="mt-6 gap-3"
            value={selected}
            onValueChange={(value) => {
              if (isTemplateKey(value)) setSelected(value);
            }}
            aria-label={t('onboarding.template.heading')}
          >
            {templates.map((template) => (
              <TemplateCard
                key={template.key}
                templateKey={template.key}
                badge={template.badge}
                title={t(template.titleKey)}
                description={t(template.descKey)}
                selected={selected === template.key}
              />
            ))}
          </RadioGroup>
          <Button
            type="button"
            variant="accent"
            size="lg"
            disabled={selectMut.isPending}
            onClick={() => selectMut.mutate({ templateKey: selected })}
            className="mt-5 h-11 w-full"
          >
            {selectMut.isPending ? t('common.loading') : t('onboarding.template.saveCta')}
          </Button>
        </div>

        <div className="bg-background overflow-hidden rounded-md border shadow-sm">
          <div className="bg-secondary/70 grid gap-1 border-b px-4 py-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
            <strong className="text-foreground min-w-0 text-sm">
              {t(preview.titleKey as never)}
            </strong>
            <span className="text-accent text-xs font-medium">{t(preview.statusKey as never)}</span>
          </div>
          <div className="divide-y">
            {preview.rows.map((row) => (
              <div
                key={`${selected}-${row.senderKey}`}
                className="grid gap-2 px-4 py-3 sm:grid-cols-[minmax(96px,0.52fr)_minmax(0,1fr)_auto] sm:items-center"
              >
                <strong className="text-foreground min-w-0 truncate text-sm">
                  {t(row.senderKey as never)}
                </strong>
                <p className="text-muted-foreground min-w-0 text-sm">{t(row.clueKey as never)}</p>
                <span
                  className={
                    row.variant === 'action'
                      ? 'bg-accent-soft text-accent-foreground inline-flex w-fit rounded-full px-2 py-0.5 text-xs font-medium'
                      : 'bg-secondary text-muted-foreground inline-flex w-fit rounded-full px-2 py-0.5 text-xs font-medium'
                  }
                >
                  {t(row.actionKey as never)}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
