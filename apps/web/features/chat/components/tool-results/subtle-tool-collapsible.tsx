'use client';

import { ChevronRight } from 'lucide-react';
import { useState } from 'react';

import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { cn } from '@/lib/utils';

export function SubtleToolCollapsible({
  title,
  defaultOpen = false,
  bordered = false,
  children,
}: {
  title: React.ReactNode;
  defaultOpen?: boolean;
  // Tool results render flat by default (content sits directly under the title)
  // so we never nest a redundant outer frame around self-bordered cards or body
  // boxes. Opt into a grouping frame with bordered only when it genuinely helps.
  bordered?: boolean;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger className="text-muted-foreground hover:text-foreground flex items-center gap-1.5 text-xs transition-colors">
        <ChevronRight
          className={cn('size-3 shrink-0 transition-transform duration-200', open && 'rotate-90')}
        />
        <span>{title}</span>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div
          className={cn(
            'mt-2 space-y-2',
            bordered && 'border-border space-y-3 rounded-md border p-3',
          )}
        >
          {children}
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}
