'use client';

import { useEffect, useState } from 'react';
import { useTranslations } from 'next-intl';
import { Check, Copy } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

type CopyableFieldProps = {
  label: string;
  value: string;
  displayValue?: string;
  multiline?: boolean;
  className?: string;
};

export function CopyableField({
  label,
  value,
  displayValue,
  multiline = false,
  className,
}: CopyableFieldProps) {
  const t = useTranslations();
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) return;
    const timeout = window.setTimeout(() => setCopied(false), 1600);
    return () => window.clearTimeout(timeout);
  }, [copied]);

  return (
    <div className={cn('bg-background rounded-lg border p-3', className)}>
      <div className="flex min-w-0 flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0 flex-1 space-y-1">
          <p className="text-muted-foreground text-xs font-medium">{label}</p>
          <code
            className={cn(
              'text-foreground block min-w-0 font-mono text-sm',
              multiline ? 'max-h-28 overflow-y-auto break-all whitespace-pre-wrap' : 'break-all',
            )}
          >
            {displayValue ?? value}
          </code>
        </div>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="w-full sm:w-auto"
          aria-label={t('billing.copy.aria', { label })}
          onClick={() => {
            void navigator.clipboard?.writeText(value).catch(() => undefined);
            setCopied(true);
          }}
        >
          {copied ? (
            <Check className="size-4" aria-hidden="true" />
          ) : (
            <Copy className="size-4" aria-hidden="true" />
          )}
          {copied ? t('billing.copy.done') : t('billing.copy.cta')}
        </Button>
      </div>
    </div>
  );
}
