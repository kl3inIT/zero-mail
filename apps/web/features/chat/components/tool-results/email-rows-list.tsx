import { Badge } from '@/components/ui/badge';

import { EmptyHint } from './empty-hint';
import { formatRelativeDate, type EmailRowData } from './helpers';

export function EmailRowsList({ emails }: { emails: EmailRowData[] }) {
  const seen = new Set<string>();
  const unique = emails.filter((email) => {
    if (seen.has(email.threadId)) return false;
    seen.add(email.threadId);
    return true;
  });
  if (unique.length === 0) {
    return <EmptyHint>Không có email nào.</EmptyHint>;
  }
  return (
    <ul className="divide-border divide-y">
      {unique.map((email) => (
        <li key={email.threadId} className="flex flex-col gap-1 py-2 first:pt-0 last:pb-0">
          <div className="flex items-start justify-between gap-3">
            <p className="text-foreground line-clamp-1 text-sm font-medium">
              {email.subject || '(không có chủ đề)'}
            </p>
            {email.isUnread && (
              <Badge variant="secondary" className="shrink-0 text-[10px]">
                Chưa đọc
              </Badge>
            )}
          </div>
          {email.from && <p className="text-muted-foreground line-clamp-1 text-xs">{email.from}</p>}
          {email.snippet && (
            <p className="text-muted-foreground line-clamp-2 text-xs">{email.snippet}</p>
          )}
          {email.date && (
            <p className="text-muted-foreground text-[11px]">{formatRelativeDate(email.date)}</p>
          )}
        </li>
      ))}
    </ul>
  );
}
