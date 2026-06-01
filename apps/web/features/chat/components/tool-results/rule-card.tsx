import { Badge } from '@/components/ui/badge';

import type { RuleRow } from './helpers';

export function RuleCard({ rule }: { rule: RuleRow }) {
  return (
    <div className="border-border space-y-1.5 rounded-md border p-2.5">
      <div className="flex items-start justify-between gap-2">
        <p className="text-foreground text-sm font-medium">{rule.displayName ?? 'Quy tắc'}</p>
        <Badge variant={rule.enabled ? 'default' : 'secondary'} className="shrink-0 text-[10px]">
          {rule.enabled ? 'Đang bật' : 'Đang tắt'}
        </Badge>
      </div>
      {rule.sourceText && (
        <p className="text-muted-foreground line-clamp-3 text-xs">{rule.sourceText}</p>
      )}
    </div>
  );
}
