'use client';

import { Mail } from 'lucide-react';

import { useActiveMailbox } from '@/features/mailbox/hooks/useActiveMailbox';
import { cn } from '@/lib/utils';

export function ActiveMailboxBadge({ className }: { className?: string }) {
  const activeMailbox = useActiveMailbox();
  const mailbox = activeMailbox.data;
  if (!mailbox) return null;

  const label = mailbox.displayPurpose?.trim() || mailbox.email;

  return (
    <div
      className={cn(
        'border-border bg-muted/30 text-muted-foreground inline-flex min-w-0 items-center gap-1.5 rounded-md border px-2 py-1 text-xs',
        className,
      )}
      data-testid="active-mailbox-scope"
    >
      <Mail className="size-3.5 shrink-0" aria-hidden="true" />
      <span className="text-foreground min-w-0 truncate">{label}</span>
    </div>
  );
}
