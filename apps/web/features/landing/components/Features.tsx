import { getTranslations } from 'next-intl/server';

import { Badge } from '@/components/ui/badge';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

export default async function Features() {
  const t = await getTranslations();
  const features = ['f1', 'f2', 'f3', 'f4', 'f5', 'f6'] as const;

  return (
    <section
      className="border-border border-t px-6 py-20 sm:py-24"
      style={{ background: 'var(--gm-chrome-grad)' }}
    >
      <div className="mx-auto flex w-full max-w-5xl flex-col gap-10">
        <div className="flex flex-col gap-3">
          <Badge className="bg-accent-soft text-accent hover:bg-accent-soft w-fit rounded-full border-transparent px-3 py-1">
            {t('feat.eyebrow')}
          </Badge>
          <h2 className="text-foreground text-3xl leading-tight tracking-tight sm:text-4xl">
            {t('feat.title')}
          </h2>
        </div>
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((feature) => (
            <Card key={feature} className="border-border bg-card transition-shadow hover:shadow-sm">
              <CardHeader>
                <CardTitle className="text-base">{t(`feat.${feature}.title` as never)}</CardTitle>
                <CardDescription className="leading-relaxed">
                  {t(`feat.${feature}.desc` as never)}
                </CardDescription>
              </CardHeader>
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
}
