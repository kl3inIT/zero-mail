'use client';

import { Label } from '@/components/ui/label';
import { RadioGroupItem } from '@/components/ui/radio-group';
import { cn } from '@/lib/utils';

type Props = {
  templateKey: 'archive-receipts' | 'label-newsletters' | 'pin-calendar';
  title: string;
  description: string;
  badge: string;
  selected: boolean;
};

/**
 * TemplateCard — selectable template option on /onboarding step 2.
 * Phase 01.4 Plan 06 token sweep: ring-blue-600 -> ring-ring (token-aware),
 * text-stone-600 -> text-muted-foreground. Shape preserved.
 * Phase 01.5 D-D3: ring uses ring-primary token (brand-leaning), so Phase 5
 * brand swap propagates automatically to the selection indicator.
 */
export function TemplateCard({ templateKey, title, description, badge, selected }: Props) {
  return (
    <Label
      htmlFor={`template-${templateKey}`}
      className={cn(
        'border-border bg-background grid w-full cursor-pointer grid-cols-[36px_minmax(0,1fr)_auto] items-center gap-3 rounded-md border p-3 text-left transition-colors',
        'hover:bg-secondary/60',
        selected ? 'border-primary bg-accent-soft ring-primary/20 ring-2' : '',
      )}
    >
      <span className="bg-card text-foreground grid size-9 place-items-center rounded-md border text-xs font-semibold">
        {badge}
      </span>
      <span className="min-w-0">
        <span className="text-foreground block text-sm font-semibold">{title}</span>
        <span className="text-muted-foreground mt-1 block text-xs leading-relaxed">
          {description}
        </span>
      </span>
      <RadioGroupItem id={`template-${templateKey}`} value={templateKey} />
    </Label>
  );
}
