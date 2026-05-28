'use client';

import { Check } from 'lucide-react';

type PricingFeatureListProps = {
  features: string[];
  includesIntro?: string;
};

export function PricingFeatureList({ features, includesIntro }: PricingFeatureListProps) {
  return (
    <div className="flex-1">
      {includesIntro ? (
        <div className="mb-4">
          <p className="text-foreground text-xs font-bold tracking-widest uppercase opacity-60">
            {includesIntro}
          </p>
        </div>
      ) : null}
      <ul className="space-y-4">
        {features.map((feature, index) => (
          <li key={index} className="flex items-start gap-3 text-sm">
            <div className="bg-primary/10 mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full">
              <Check className="text-primary h-3 w-3" aria-hidden="true" />
            </div>
            <span className="text-muted-foreground/90 leading-tight">{feature}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
