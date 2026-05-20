'use client';

import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

const CAMPAIGN_SENDER_CAP = 25;

export function SelectionToolbar({
  selectedCount,
  onClear,
  onPreview,
  isPreviewing,
}: {
  selectedCount: number;
  onClear: () => void;
  onPreview: () => void;
  isPreviewing?: boolean;
}) {
  const t = useTranslations();
  const overCap = selectedCount > CAMPAIGN_SENDER_CAP;
  const previewDisabled = selectedCount === 0 || overCap || Boolean(isPreviewing);
  const clearDisabled = selectedCount === 0;

  return (
    <div className="bg-muted/30 ring-foreground/10 sticky top-0 z-10 flex flex-col gap-2 rounded-lg px-3 py-2 ring-1 md:flex-row md:items-center md:justify-between">
      <span
        aria-live="polite"
        className={cn(
          'font-mono text-sm tabular-nums',
          overCap ? 'text-destructive font-semibold' : 'text-foreground',
        )}
      >
        {overCap
          ? t('cleanup.unsubscribe.list.counterOver', { count: selectedCount })
          : t('cleanup.unsubscribe.list.counter', { count: selectedCount })}
      </span>
      <div className="flex items-center gap-2">
        <Button type="button" variant="ghost" size="sm" disabled={clearDisabled} onClick={onClear}>
          {t('cleanup.unsubscribe.list.clear')}
        </Button>
        <Button
          type="button"
          variant="default"
          size="sm"
          disabled={previewDisabled}
          onClick={onPreview}
        >
          {t('cleanup.unsubscribe.list.preview')}
        </Button>
      </div>
    </div>
  );
}
