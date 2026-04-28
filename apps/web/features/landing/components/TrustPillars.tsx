import { getTranslations } from 'next-intl/server';

import { Badge } from '@/components/ui/badge';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

export default async function TrustPillars() {
  const t = await getTranslations();
  const pillars = ['c1', 'c2', 'c3', 'c4'] as const;

  return (
    <section className="border-border border-t px-6 py-20 sm:py-24">
      <div className="mx-auto flex w-full max-w-5xl flex-col gap-10">
        <div className="flex flex-col gap-3">
          <Badge className="bg-accent-soft text-accent hover:bg-accent-soft w-fit rounded-full border-transparent px-3 py-1">
            {t('trust.eyebrow')}
          </Badge>
          <h2 className="text-foreground text-3xl leading-tight tracking-tight sm:text-4xl">
            {t('trust.title')}
          </h2>
        </div>
        <div className="grid gap-6 sm:grid-cols-2">
          {pillars.map((pillar) => (
            <Card key={pillar} className="border-border transition-shadow hover:shadow-sm">
              <CardHeader>
                <CardTitle>{t(`trust.${pillar}.title` as never)}</CardTitle>
                <CardDescription className="leading-relaxed">
                  {t(`trust.${pillar}.body` as never)}
                </CardDescription>
              </CardHeader>
            </Card>
          ))}
        </div>
      </div>
    </section>
  );
}
