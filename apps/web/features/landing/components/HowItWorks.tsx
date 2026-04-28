import { getTranslations } from 'next-intl/server';

import { Badge } from '@/components/ui/badge';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

export default async function HowItWorks() {
  const t = await getTranslations();
  const steps = [
    { key: 'step1', titleKey: 'how.step1.title', descKey: 'how.step1.desc' },
    { key: 'step2', titleKey: 'how.step2.title', descKey: 'how.step2.desc' },
    { key: 'step3', titleKey: 'how.step3.title', descKey: 'how.step3.desc' },
  ] as const;

  return (
    <section className="border-border border-t px-6 py-20 sm:py-24">
      <div className="mx-auto flex w-full max-w-5xl flex-col gap-10">
        <div className="flex flex-col gap-3">
          <Badge className="bg-accent-soft text-accent hover:bg-accent-soft w-fit rounded-full border-transparent px-3 py-1">
            {t('how.eyebrow')}
          </Badge>
          <h2 className="text-foreground text-3xl leading-tight tracking-tight sm:text-4xl">
            {t('how.title')}
          </h2>
          <p className="text-muted-foreground max-w-2xl text-base leading-relaxed">
            {t('how.desc')}
          </p>
        </div>
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {steps.map((step, index) => (
            <Card key={step.key} className="border-border transition-shadow hover:shadow-sm">
              <CardHeader>
                <span
                  className="text-text-faint border-border mb-2 inline-flex size-7 items-center justify-center rounded-full border text-xs font-medium"
                  aria-hidden="true"
                >
                  {index + 1}
                </span>
                <CardTitle>{t(step.titleKey as never)}</CardTitle>
                <CardDescription className="leading-relaxed">
                  {t(step.descKey as never)}
                </CardDescription>
              </CardHeader>
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
}
