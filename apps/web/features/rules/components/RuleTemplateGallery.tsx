'use client';

import { useTranslations } from 'next-intl';
import { Loader2, Sparkles } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { RuleTemplateResponse } from '@/features/rules/api/rules-api';
import { cn } from '@/lib/utils';

type Props = {
  templates: RuleTemplateResponse[];
  isLoading: boolean;
  pendingTemplateKey: string | null;
  onUseTemplate: (template: RuleTemplateResponse) => void;
};

export function RuleTemplateGallery({
  templates,
  isLoading,
  pendingTemplateKey,
  onUseTemplate,
}: Props) {
  const t = useTranslations();

  return (
    <section className="bg-card rounded-xl border p-3">
      <div className="mb-3">
        <h2 className="text-xl font-semibold">{t('rules.templates.title')}</h2>
        <p className="text-muted-foreground text-sm">{t('rules.templates.disabledByDefault')}</p>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          <div className="bg-muted h-14 animate-pulse rounded-lg" />
          <div className="bg-muted h-14 animate-pulse rounded-lg" />
        </div>
      ) : (
        <div className="space-y-2">
          {templates.map((template) => {
            const pending = pendingTemplateKey === template.templateKey;
            const disabled = Boolean(template.materialized);

            return (
              <article
                key={template.templateKey}
                className={cn(
                  'grid gap-2 rounded-lg border p-3 text-sm sm:grid-cols-[1fr_auto]',
                  template.materialized ? 'bg-muted/30' : 'bg-background',
                )}
              >
                <div className="min-w-0 space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="truncate font-medium">{template.displayName}</p>
                    {template.materialized && (
                      <Badge variant="outline">{t('rules.templates.materialized')}</Badge>
                    )}
                    {template.customized && (
                      <Badge variant="outline">{t('rules.list.customizedBadge')}</Badge>
                    )}
                  </div>
                  <p className="text-muted-foreground line-clamp-2 text-xs">
                    {template.actionSummary ?? template.sourceText}
                  </p>
                  <p className="text-muted-foreground truncate font-mono text-xs">
                    {template.templateKey} · v{template.templateVersion ?? 1}
                  </p>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  disabled={disabled || pending}
                  onClick={() => onUseTemplate(template)}
                >
                  {pending ? (
                    <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                  ) : (
                    <Sparkles className="size-4" aria-hidden="true" />
                  )}
                  {t('rules.templates.useStarter')}
                </Button>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
