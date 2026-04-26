import * as React from 'react';

import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

interface LoadingStateProps extends React.ComponentProps<'div'> {
  /**
   * Skeleton layout variant.
   *  - card (default): 3-line skeleton inside a card-shaped container.
   *  - list: 5 stacked rows for list-style placeholders.
   *  - page: header + paragraph + body block matching PageShell rhythm.
   */
  variant?: 'card' | 'list' | 'page';
}

/**
 * LoadingState — composes shadcn Skeleton with three layout variants.
 * Used as the default content of `loading.tsx` files. RSC-compatible.
 *
 * Each variant renders ≥3 (card / page) or ≥5 (list) skeleton elements
 * to satisfy the Wave 0 contract assertions.
 */
export function LoadingState({ variant = 'card', className, ...props }: LoadingStateProps) {
  if (variant === 'list') {
    return (
      <div
        data-slot="loading-state"
        data-variant="list"
        className={cn('space-y-3', className)}
        {...props}
      >
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-12 w-full" />
        ))}
      </div>
    );
  }

  if (variant === 'page') {
    return (
      <div
        data-slot="loading-state"
        data-variant="page"
        className={cn('space-y-6', className)}
        {...props}
      >
        <Skeleton className="h-10 w-2/3" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  // card (default)
  return (
    <div
      data-slot="loading-state"
      data-variant="card"
      className={cn('border-border space-y-3 rounded-xl border p-4', className)}
      {...props}
    >
      <Skeleton className="h-5 w-1/2" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-4/5" />
    </div>
  );
}
