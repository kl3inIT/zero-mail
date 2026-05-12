import type { ReactNode } from 'react';

import { cn } from '@/lib/utils';

type EmptyStateProps = {
  heading: ReactNode;
  body: ReactNode;
  cta?: ReactNode;
  className?: string;
};

export function EmptyState({ heading, body, cta, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        'bg-card flex min-h-40 flex-col items-center justify-center rounded-lg border border-dashed px-6 py-12 text-center',
        className,
      )}
    >
      <div className="max-w-md space-y-2">
        <h2 className="text-foreground text-xl font-semibold">{heading}</h2>
        <p className="text-muted-foreground text-sm leading-6">{body}</p>
      </div>
      {cta ? <div className="mt-4">{cta}</div> : null}
    </div>
  );
}
