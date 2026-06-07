import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { cn } from '@/lib/utils';

type SettingCardProps = {
  title?: string;
  description?: string;
  icon?: LucideIcon;
  rightSlot?: ReactNode;
  children?: ReactNode;
  className?: string;
  contentClassName?: string;
};

export function SettingCard({
  title,
  description,
  icon: Icon,
  rightSlot,
  children,
  className,
  contentClassName,
}: SettingCardProps) {
  const hasHeader = Boolean(title || description || rightSlot);
  return (
    <Card className={cn('rounded-lg', className)}>
      {hasHeader ? (
        <CardHeader>
          {title ? (
            <CardTitle className="flex items-center gap-2">
              {Icon ? <Icon className="text-muted-foreground size-4" aria-hidden="true" /> : null}
              <span>{title}</span>
            </CardTitle>
          ) : null}
          {description ? <CardDescription>{description}</CardDescription> : null}
          {rightSlot ? <CardAction>{rightSlot}</CardAction> : null}
        </CardHeader>
      ) : null}
      {children ? (
        <CardContent className={cn(hasHeader ? undefined : 'pt-6', contentClassName)}>
          {children}
        </CardContent>
      ) : null}
    </Card>
  );
}
