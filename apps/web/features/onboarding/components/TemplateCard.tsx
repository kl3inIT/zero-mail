'use client';

import { Card } from '@/components/ui/card';
import { cn } from '@/lib/utils';

type Props = {
  templateKey: 'archive-receipts' | 'label-newsletters' | 'pin-calendar';
  title: string;
  description: string;
  selected: boolean;
  onSelect: () => void;
};

/**
 * TemplateCard — selectable template option on /onboarding step 2.
 * Phase 01.4 Plan 06 token sweep: ring-blue-600 -> ring-ring (token-aware),
 * text-stone-600 -> text-muted-foreground. Shape preserved.
 */
export function TemplateCard({ title, description, selected, onSelect }: Props) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={cn('w-full rounded-xl text-left', selected ? 'ring-ring ring-2' : '')}
    >
      <Card className="p-6">
        <h3 className="text-lg font-semibold">{title}</h3>
        <p className="text-muted-foreground mt-2 text-sm">{description}</p>
      </Card>
    </button>
  );
}
