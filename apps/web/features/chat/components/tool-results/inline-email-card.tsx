'use client';

import { useQuery } from '@tanstack/react-query';
import { ChevronRight, ExternalLink, Loader2, Paperclip } from 'lucide-react';
import { useLocale } from 'next-intl';
import { useState } from 'react';

import { Badge } from '@/components/ui/badge';
import { getInboxMessageDetail } from '@/features/inbox/api/inbox-api';
import { EmailHtmlFrame, PlainEmailContent } from '@/features/inbox/components/EmailHtmlFrame';
import { cn } from '@/lib/utils';

import { EmptyHint } from './empty-hint';
import { gmailThreadUrl } from './gmail-url';
import { formatRelativeDate, type EmailRowData } from './helpers';

/**
 * A clickable chat email row that expands to read the full thread in place. The
 * body is fetched live on expand via the inbox detail endpoint and rendered in
 * the same sandboxed EmailHtmlFrame the mailbox uses — it is never persisted
 * into chat_message.parts, satisfying the ARCH-02 email-content ban.
 */
export function InlineEmailCard({
  email,
  fallbackTitle,
}: {
  email: EmailRowData;
  fallbackTitle?: string;
}) {
  const [expanded, setExpanded] = useState(false);
  const locale = useLocale();
  const detail = useQuery({
    queryKey: ['chat-inline-email', email.messageId],
    queryFn: () => getInboxMessageDetail(email.messageId),
    enabled: expanded,
    staleTime: 5 * 60_000,
  });

  // Row metadata may be sparse (getThread only knows message ids); fill in the
  // subject/sender from the fetched detail once the row is expanded.
  const subject =
    email.subject || detail.data?.message.subject || fallbackTitle || '(không có chủ đề)';
  const from = email.from || detail.data?.message.from;
  const openInGmailUrl = detail.data?.message.openInGmailUrl ?? gmailThreadUrl(email.threadId);

  return (
    <div className="border-border rounded-md border">
      <div className="flex items-start gap-2 p-2.5">
        <button
          type="button"
          onClick={() => setExpanded((value) => !value)}
          aria-expanded={expanded}
          className="flex min-w-0 flex-1 items-start gap-2 text-left"
        >
          <ChevronRight
            className={cn(
              'text-muted-foreground mt-0.5 size-3.5 shrink-0 transition-transform duration-200',
              expanded && 'rotate-90',
            )}
          />
          <span className="min-w-0 flex-1 space-y-0.5">
            <span className="flex items-center gap-1.5">
              <span className="text-foreground line-clamp-1 text-sm font-medium">{subject}</span>
              {email.hasAttachment && (
                <Paperclip className="text-muted-foreground size-3 shrink-0" />
              )}
              {email.isUnread && (
                <Badge variant="secondary" className="shrink-0 text-[10px]">
                  Chưa đọc
                </Badge>
              )}
            </span>
            {from && (
              <span className="text-muted-foreground line-clamp-1 block text-xs">{from}</span>
            )}
            {!expanded && email.snippet && (
              <span className="text-muted-foreground line-clamp-1 block text-xs">
                {email.snippet}
              </span>
            )}
            {email.date && (
              <span className="text-muted-foreground block text-[11px]">
                {formatRelativeDate(email.date)}
              </span>
            )}
          </span>
        </button>
        <a
          href={openInGmailUrl}
          target="_blank"
          rel="noreferrer"
          title="Mở trong Gmail"
          className="text-muted-foreground hover:text-foreground hover:bg-accent inline-flex shrink-0 items-center rounded p-1.5"
        >
          <ExternalLink className="size-3.5" />
        </a>
      </div>
      {expanded && (
        <div className="border-border overflow-hidden border-t">
          {detail.isPending ? (
            <div className="text-muted-foreground flex items-center gap-2 p-3 text-xs">
              <Loader2 className="size-3.5 animate-spin" /> Đang tải nội dung…
            </div>
          ) : detail.isError ? (
            <div className="text-destructive p-3 text-xs">Không tải được nội dung email.</div>
          ) : detail.data?.renderedHtml ? (
            <EmailHtmlFrame
              renderedHtml={detail.data.renderedHtml}
              title={subject}
              locale={locale}
            />
          ) : detail.data ? (
            <PlainEmailContent text={detail.data.renderedText} />
          ) : null}
        </div>
      )}
    </div>
  );
}

/** Deduped list of expandable email cards, used by search + write-action results. */
export function InlineEmailCardList({ emails }: { emails: EmailRowData[] }) {
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
    <div className="space-y-1.5">
      {unique.map((email) => (
        <InlineEmailCard key={email.threadId} email={email} />
      ))}
    </div>
  );
}
