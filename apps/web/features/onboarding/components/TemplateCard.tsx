'use client';

import { Card } from '@/components/ui/card';

type Props = {
  templateKey: 'archive-receipts' | 'label-newsletters' | 'pin-calendar';
  title: string;
  description: string;
  selected: boolean;
  onSelect: () => void;
};

export function TemplateCard({ title, description, selected, onSelect }: Props) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={`w-full text-left ${selected ? 'rounded-xl ring-2 ring-blue-600' : ''}`}
    >
      <Card className="p-6">
        <h3 className="text-lg font-semibold">{title}</h3>
        <p className="mt-2 text-sm text-stone-600">{description}</p>
      </Card>
    </button>
  );
}
