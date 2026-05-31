import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

import { cn } from '@/lib/utils';

type SectionHeaderProps = {
  title: string;
  icon?: LucideIcon;
  helperText?: string;
  rightSlot?: ReactNode;
  id?: string;
  className?: string;
};

export function SectionHeader({
  title,
  icon: Icon,
  helperText,
  rightSlot,
  id,
  className,
}: SectionHeaderProps) {
  return (
    <div
      className={cn('flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between', className)}
    >
      <div className="flex min-w-0 gap-3">
        <div className="bg-primary mt-1 h-9 w-1 shrink-0 rounded-full" aria-hidden="true" />
        <div className="min-w-0 space-y-1">
          <h2 id={id} className="flex items-center gap-2 text-xl font-semibold tracking-tight">
            {Icon ? <Icon className="text-muted-foreground size-5" aria-hidden="true" /> : null}
            <span>{title}</span>
          </h2>
          {helperText ? <p className="text-muted-foreground text-sm">{helperText}</p> : null}
        </div>
      </div>
      {rightSlot ? <div className="shrink-0">{rightSlot}</div> : null}
    </div>
  );
}
