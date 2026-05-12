import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

type LoadingStateProps = {
  variant?: 'rows' | 'cards';
  count?: number;
  className?: string;
};

export function LoadingState({ variant = 'rows', count = 3, className }: LoadingStateProps) {
  const items = Array.from({ length: count });

  if (variant === 'cards') {
    return (
      <div className={cn('grid gap-3', className)} aria-busy="true">
        {items.map((_, index) => (
          <div key={index} className="bg-card rounded-lg border p-4">
            <Skeleton className="h-4 w-2/5" />
            <Skeleton className="mt-3 h-3 w-full" />
            <Skeleton className="mt-2 h-3 w-4/5" />
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className={cn('space-y-2', className)} aria-busy="true">
      {items.map((_, index) => (
        <div key={index} className="bg-card rounded-lg border p-3">
          <div className="flex items-center gap-3">
            <Skeleton className="size-9 rounded-md" />
            <div className="min-w-0 flex-1 space-y-2">
              <Skeleton className="h-3 w-3/5" />
              <Skeleton className="h-3 w-full" />
            </div>
            <Skeleton className="h-7 w-16" />
          </div>
        </div>
      ))}
    </div>
  );
}
