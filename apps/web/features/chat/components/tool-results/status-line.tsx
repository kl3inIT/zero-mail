import type { Mail } from 'lucide-react';

export function StatusLine({
  icon: Icon,
  children,
}: {
  icon: typeof Mail;
  children: React.ReactNode;
}) {
  return (
    <div className="text-muted-foreground flex items-center gap-2 text-xs">
      <Icon className="text-foreground/70 size-3.5 shrink-0" />
      <span>{children}</span>
    </div>
  );
}
