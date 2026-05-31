export function ToolDetailRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex gap-4 text-sm">
      <span className="text-muted-foreground w-24 shrink-0 pt-0.5 text-xs tracking-wide uppercase">
        {label}
      </span>
      <div className="text-foreground min-w-0 flex-1">{value}</div>
    </div>
  );
}
