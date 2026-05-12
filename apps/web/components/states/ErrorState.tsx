import type { ReactNode } from 'react';

import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

type ErrorStateProps = {
  heading: ReactNode;
  body: ReactNode;
  retryLabel: ReactNode;
  onRetry: () => void;
  className?: string;
};

export function ErrorState({ heading, body, retryLabel, onRetry, className }: ErrorStateProps) {
  return (
    <div
      className={cn(
        'bg-card flex min-h-40 flex-col items-center justify-center rounded-lg border px-6 py-12 text-center',
        className,
      )}
    >
      <div className="max-w-md space-y-2">
        <h2 className="text-foreground text-xl font-semibold">{heading}</h2>
        <p className="text-muted-foreground text-sm leading-6">{body}</p>
      </div>
      <Button type="button" variant="outline" className="mt-4" onClick={onRetry}>
        {retryLabel}
      </Button>
    </div>
  );
}
