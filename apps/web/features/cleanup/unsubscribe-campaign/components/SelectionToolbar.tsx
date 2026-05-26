'use client';

import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

const CAMPAIGN_SENDER_CAP = 25;

export function SelectionToolbar({
  selectedCount,
  selectedMailCount,
  onClear,
  onPreview,
  isPreviewing,
}: {
  selectedCount: number;
  selectedMailCount?: number;
  onClear: () => void;
  onPreview: () => void;
  isPreviewing?: boolean;
}) {
  const t = useTranslations();
  const overCap = selectedCount > CAMPAIGN_SENDER_CAP;
  const previewDisabled = selectedCount === 0 || overCap || Boolean(isPreviewing);
  const clearDisabled = selectedCount === 0;

  return (
    <div className="border-border bg-card flex flex-col gap-3 rounded-lg border px-4 py-3 shadow-sm md:flex-row md:items-center md:justify-between">
      <div aria-live="polite" className="flex flex-col gap-0.5">
        <span
          className={cn(
            'text-sm font-medium tabular-nums',
            overCap ? 'text-destructive font-semibold' : 'text-foreground',
          )}
        >
          {overCap
            ? t('cleanup.unsubscribe.list.counterOver', { count: selectedCount })
            : t('cleanup.unsubscribe.list.counter', { count: selectedCount })}
        </span>
        {selectedMailCount !== undefined && selectedCount > 0 && (
          <span className="text-muted-foreground text-xs">
            {t('cleanup.unsubscribe.list.selectedMail', { count: selectedMailCount })}
          </span>
        )}
      </div>
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
