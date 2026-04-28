'use client';

import { Card } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { RadioGroupItem } from '@/components/ui/radio-group';
import { cn } from '@/lib/utils';

type Props = {
  templateKey: 'archive-receipts' | 'label-newsletters' | 'pin-calendar';
  title: string;
  description: string;
  selected: boolean;
};

/**
 * TemplateCard — selectable template option on /onboarding step 2.
 * Phase 01.4 Plan 06 token sweep: ring-blue-600 -> ring-ring (token-aware),
 * text-stone-600 -> text-muted-foreground. Shape preserved.
 * Phase 01.5 D-D3: ring uses ring-primary token (brand-leaning), so Phase 5
 * brand swap propagates automatically to the selection indicator.
 */
export function TemplateCard({ templateKey, title, description, selected }: Props) {
  return (
    <Label
      htmlFor={`template-${templateKey}`}
      className={cn(
        'block w-full cursor-pointer rounded-[10px] text-left transition-colors',
        selected ? 'ring-primary bg-accent-soft ring-2' : '',
      )}
    >
      <Card className="border-border bg-card p-5">
        <div className="grid grid-cols-[auto_1fr] gap-3">
          <RadioGroupItem id={`template-${templateKey}`} value={templateKey} className="mt-1" />
          <div>
            <h3 className="text-lg font-semibold">{title}</h3>
            <p className="text-muted-foreground mt-2 text-sm">{description}</p>
          </div>
        </div>
      </Card>
    </Label>
  );
}
