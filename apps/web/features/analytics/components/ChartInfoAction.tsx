'use client';

import { Info } from 'lucide-react';

import { CardAction } from '@/components/ui/card';
import {
  Popover,
  PopoverContent,
  PopoverDescription,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger,
} from '@/components/ui/popover';

type ChartInfoActionProps = {
  title: string;
  description: string;
};

export function ChartInfoAction({ title, description }: ChartInfoActionProps) {
  return (
    <CardAction>
      <Popover>
        <PopoverTrigger
          render={
            <button
              type="button"
              className="text-muted-foreground hover:text-foreground focus-visible:ring-ring grid size-8 place-items-center rounded-md transition-colors outline-none focus-visible:ring-2"
              aria-label={title}
            />
          }
        >
          <Info className="size-4" aria-hidden="true" />
        </PopoverTrigger>
        <PopoverContent align="end" side="bottom" className="w-80">
          <PopoverHeader>
            <PopoverTitle>{title}</PopoverTitle>
            <PopoverDescription className="text-sm leading-6">{description}</PopoverDescription>
          </PopoverHeader>
        </PopoverContent>
      </Popover>
    </CardAction>
  );
}
