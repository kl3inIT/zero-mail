'use client';

import { useMemo, useState } from 'react';
import { ArchiveIcon, ExternalLinkIcon, Loader2Icon, MailXIcon, XIcon } from 'lucide-react';
import { useTranslations } from 'next-intl';
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  useSenderMessageBody,
  useSenderMessages,
  useSenderTimeline,
} from '@/features/cleanup/unsubscribe-campaign/hooks/useSenderStats';
import type { SenderMessageSummary } from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import { cn } from '@/lib/utils';

type Tab = 'unarchived' | 'all';

export function SenderStatsDialog({
  senderEmail,
  senderName,
  senderDomain,
  unsubscribeMethod,
  windowDays,
  onOpenChange,
  onUnsubscribe,
  onAutoArchive,
  isExecuting,
}: {
  senderEmail: string | null;
  senderName: string | null;
  senderDomain: string | null;
  unsubscribeMethod: string | null;
  windowDays: number;
  onOpenChange: (open: boolean) => void;
  onUnsubscribe: () => void;
  onAutoArchive: () => void;
  isExecuting: boolean;
}) {
  const t = useTranslations('cleanup.unsubscribe');
  const [tab, setTab] = useState<Tab>('unarchived');
  const [activeMessageId, setActiveMessageId] = useState<string | null>(null);

  // Reset the open message + tab when the dialog switches senders, via render-time state
  // adjustment (React-recommended) instead of an effect — avoids a cascading re-render.
  const [trackedSenderEmail, setTrackedSenderEmail] = useState(senderEmail);
  if (trackedSenderEmail !== senderEmail) {
    setTrackedSenderEmail(senderEmail);
    setActiveMessageId(null);
    setTab('unarchived');
  }

  const archivedOnly = tab === 'all' ? false : false; // Inbox Zero: unarchived = NOT archived → use the tab to filter client-side after fetch
  const timelineQuery = useSenderTimeline(senderEmail, windowDays);
  const messagesQuery = useSenderMessages(senderEmail, archivedOnly);
  const bodyQuery = useSenderMessageBody(activeMessageId);

  const filteredMessages = useMemo(() => {
    const all = messagesQuery.data ?? [];
    if (tab === 'unarchived') return all.filter((message) => !message.archived);
    return all;
  }, [messagesQuery.data, tab]);

  const chartData = useMemo(() => {
    return (timelineQuery.data ?? []).map((entry) => ({
      date: entry.date,
      count: entry.count,
    }));
  }, [timelineQuery.data]);

  const isUnsubscribable = unsubscribeMethod !== 'NONE';
  const displayTitle = senderName ?? senderDomain ?? senderEmail ?? '';

  return (
    <Dialog open={senderEmail !== null} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[90vh] flex-col gap-3 sm:max-w-6xl">
        <DialogHeader>
          <DialogTitle className="truncate text-base">
            {t('stats.titleWith', { sender: displayTitle })}
          </DialogTitle>
          <DialogDescription className="text-muted-foreground truncate text-xs">
            {senderEmail}
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-wrap items-center gap-2">
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={!isUnsubscribable || isExecuting}
            onClick={onUnsubscribe}
          >
            {isExecuting ? (
              <Loader2Icon className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              <MailXIcon className="size-4" aria-hidden="true" />
            )}
            {t(isUnsubscribable ? 'list.action.unsubscribe' : 'list.action.block')}
          </Button>
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={isExecuting}
            onClick={onAutoArchive}
          >
            <ArchiveIcon className="size-4" aria-hidden="true" />
            {t('list.action.autoArchive')}
          </Button>
        </div>

        {/* Timeline chart */}
        <div className="border-border bg-card h-[160px] rounded-md border p-3">
          {timelineQuery.isPending && (
            <div className="text-muted-foreground flex h-full items-center justify-center text-sm">
              {t('stats.chartLoading')}
            </div>
          )}
          {timelineQuery.isError && (
            <div className="text-muted-foreground flex h-full items-center justify-center text-sm">
              {t('stats.chartError')}
            </div>
          )}
          {!timelineQuery.isPending && !timelineQuery.isError && chartData.length === 0 && (
            <div className="text-muted-foreground flex h-full items-center justify-center text-sm">
              {t('stats.chartEmpty', { days: windowDays })}
            </div>
          )}
          {!timelineQuery.isPending && chartData.length > 0 && (
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid stroke="hsl(var(--border))" strokeDasharray="2 2" vertical={false} />
                <XAxis
                  dataKey="date"
                  tick={{ fontSize: 11 }}
                  tickFormatter={(value: string) =>
                    new Date(value).toLocaleDateString('vi-VN', {
                      day: '2-digit',
                      month: '2-digit',
                    })
                  }
                />
                <YAxis tick={{ fontSize: 11 }} allowDecimals={false} width={28} />
                <RechartsTooltip
                  contentStyle={{ fontSize: 12 }}
                  labelFormatter={(label) => new Date(String(label)).toLocaleDateString('vi-VN')}
                />
                <Bar dataKey="count" fill="#16a34a" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Emails section */}
        <div className="flex min-h-0 flex-1 flex-col gap-2">
          <Tabs value={tab} onValueChange={(value) => setTab(value as Tab)}>
            <TabsList>
              <TabsTrigger value="unarchived">{t('stats.tabs.unarchived')}</TabsTrigger>
              <TabsTrigger value="all">{t('stats.tabs.all')}</TabsTrigger>
            </TabsList>
            <TabsContent value="unarchived" className="mt-0 hidden" />
            <TabsContent value="all" className="mt-0 hidden" />
          </Tabs>

          <div className="grid min-h-0 flex-1 grid-cols-1 gap-2 md:grid-cols-2">
            {/* Message list */}
            <div className="border-border bg-card flex min-h-0 flex-col overflow-hidden rounded-md border">
              {messagesQuery.isPending && (
                <div className="text-muted-foreground flex h-32 items-center justify-center text-sm">
                  {t('stats.messagesLoading')}
                </div>
              )}
              {messagesQuery.isError && (
                <Alert variant="destructive" className="m-2">
                  <AlertTitle>{t('stats.messagesError')}</AlertTitle>
                  <AlertDescription>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={() => void messagesQuery.refetch()}
                    >
                      {t('list.retry')}
                    </Button>
                  </AlertDescription>
                </Alert>
              )}
              {!messagesQuery.isPending &&
                !messagesQuery.isError &&
                filteredMessages.length === 0 && (
                  <div className="text-muted-foreground flex h-32 items-center justify-center text-sm">
                    {t('stats.messagesEmpty')}
                  </div>
                )}
              {filteredMessages.length > 0 && (
                <ul className="overflow-y-auto">
                  {filteredMessages.map((message) => (
                    <MessageRow
                      key={message.gmailMessageId}
                      message={message}
                      active={message.gmailMessageId === activeMessageId}
                      onSelect={() => setActiveMessageId(message.gmailMessageId)}
                    />
                  ))}
                </ul>
              )}
            </div>

            {/* Preview pane */}
            <div className="border-border bg-card flex min-h-0 flex-col overflow-hidden rounded-md border">
              {!activeMessageId && (
                <div className="text-muted-foreground flex h-full items-center justify-center px-4 text-center text-sm">
                  {t('stats.previewHint')}
                </div>
              )}
              {activeMessageId && bodyQuery.isPending && (
                <div className="text-muted-foreground flex h-full items-center justify-center text-sm">
                  <Loader2Icon className="mr-2 size-4 animate-spin" aria-hidden="true" />
                  {t('stats.previewLoading')}
                </div>
              )}
              {activeMessageId && bodyQuery.isError && (
                <Alert variant="destructive" className="m-2">
                  <AlertTitle>{t('stats.previewError')}</AlertTitle>
                </Alert>
              )}
              {activeMessageId && bodyQuery.data && (
                <BodyPreview
                  subject={bodyQuery.data.subject}
                  fromHeader={bodyQuery.data.fromHeader}
                  htmlBody={bodyQuery.data.htmlBody ?? null}
                  plainBody={bodyQuery.data.plainBody ?? null}
                  gmailMessageId={bodyQuery.data.gmailMessageId}
                  onClose={() => setActiveMessageId(null)}
                />
              )}
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function MessageRow({
  message,
  active,
  onSelect,
}: {
  message: SenderMessageSummary;
  active: boolean;
  onSelect: () => void;
}) {
  const internalDate = new Date(message.internalDate);
  const dateLabel = internalDate.toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
  });
  return (
    <li>
      <button
        type="button"
        onClick={onSelect}
        className={cn(
          'flex w-full flex-col items-start gap-1 border-b px-3 py-2 text-left transition-colors',
          'border-border hover:bg-muted/40',
          active && 'bg-accent text-accent-foreground hover:bg-accent',
        )}
      >
        <div className="flex w-full items-center gap-2">
          <span
            className={cn('truncate text-sm', message.unread ? 'font-semibold' : 'font-medium')}
          >
            {message.subject || '(no subject)'}
          </span>
          <span className="text-muted-foreground ml-auto shrink-0 text-xs tabular-nums">
            {dateLabel}
          </span>
        </div>
      </button>
    </li>
  );
}

function BodyPreview({
  subject,
  fromHeader,
  htmlBody,
  plainBody,
  gmailMessageId,
  onClose,
}: {
  subject: string;
  fromHeader: string;
  htmlBody: string | null;
  plainBody: string | null;
  gmailMessageId: string;
  onClose: () => void;
}) {
  const t = useTranslations('cleanup.unsubscribe');
  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="border-border flex items-start gap-2 border-b p-2">
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium">{subject || '(no subject)'}</p>
          <p className="text-muted-foreground truncate text-xs">{fromHeader}</p>
        </div>
        <a
          className="text-muted-foreground hover:text-foreground"
          href={`https://mail.google.com/mail/u/0/#inbox/${encodeURIComponent(gmailMessageId)}`}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={t('list.action.viewGmail')}
        >
          <ExternalLinkIcon className="size-4" aria-hidden="true" />
        </a>
        <button
          type="button"
          onClick={onClose}
          className="text-muted-foreground hover:text-foreground"
          aria-label={t('stats.closePreview')}
        >
          <XIcon className="size-4" aria-hidden="true" />
        </button>
      </div>
      <div className="flex-1 overflow-hidden">
        {htmlBody ? (
          <iframe
            title={subject || 'email body'}
            sandbox=""
            srcDoc={htmlBody}
            className="size-full border-0"
            referrerPolicy="no-referrer"
          />
        ) : plainBody ? (
          <pre className="size-full overflow-auto p-3 text-sm whitespace-pre-wrap">{plainBody}</pre>
        ) : (
          <div className="text-muted-foreground flex h-full items-center justify-center text-sm">
            {t('stats.previewEmpty')}
          </div>
        )}
      </div>
    </div>
  );
}
