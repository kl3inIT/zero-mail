'use client';

import { useMemo, useState } from 'react';
import {
  ArchiveIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  ExternalLinkIcon,
  Loader2Icon,
  MailXIcon,
  XIcon,
} from 'lucide-react';
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
import type { DateRangeSpec } from '@/features/cleanup/unsubscribe-campaign/date-range-spec';
import { cn } from '@/lib/utils';

type Tab = 'unarchived' | 'all';

export function SenderStatsDialog({
  senderEmail,
  senderName,
  senderDomain,
  unsubscribeMethod,
  dateRangeSpec,
  onOpenChange,
  onUnsubscribe,
  onAutoArchive,
  isExecuting,
}: {
  senderEmail: string | null;
  senderName: string | null;
  senderDomain: string | null;
  unsubscribeMethod: string | null;
  dateRangeSpec: DateRangeSpec;
  onOpenChange: (open: boolean) => void;
  onUnsubscribe: () => void;
  onAutoArchive: () => void;
  isExecuting: boolean;
}) {
  const t = useTranslations('cleanup.unsubscribe');
  const [tab, setTab] = useState<Tab>('unarchived');
  const [activeMessageId, setActiveMessageId] = useState<string | null>(null);
  const [chartHidden, setChartHidden] = useState(false);

  // Reset the open message + tab when the dialog switches senders, via render-time state
  // adjustment (React-recommended) instead of an effect — avoids a cascading re-render.
  const [trackedSenderEmail, setTrackedSenderEmail] = useState(senderEmail);
  if (trackedSenderEmail !== senderEmail) {
    setTrackedSenderEmail(senderEmail);
    setActiveMessageId(null);
    setTab('unarchived');
  }

  const archivedOnly = tab === 'all' ? false : false; // Inbox Zero: unarchived = NOT archived → use the tab to filter client-side after fetch
  const timelineQuery = useSenderTimeline(senderEmail, dateRangeSpec);
  const messagesQuery = useSenderMessages(senderEmail, archivedOnly, dateRangeSpec);
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

  const previewOpen = activeMessageId !== null;

  return (
    <Dialog open={senderEmail !== null} onOpenChange={onOpenChange}>
      <DialogContent
        // Inbox Zero parity: stats dialog takes the full available window
        // width on wide screens (capped at 1500px so the chart + list don't
        // visually stretch beyond a comfortable read on 4K displays). On
        // narrow screens fall back to the dialog's intrinsic responsive
        // width — `min()` keeps the responsive cap while letting the viewport
        // win on smaller widths.
        className="flex h-[92vh] max-h-[92vh] flex-col gap-3 sm:w-[min(95vw,1500px)] sm:!max-w-[min(95vw,1500px)]"
      >
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
          <Button
            type="button"
            size="sm"
            variant="ghost"
            className="ml-auto"
            onClick={() => setChartHidden((current) => !current)}
            aria-pressed={chartHidden}
            data-testid="stats-toggle-chart"
          >
            {chartHidden ? (
              <ChevronDownIcon className="size-4" aria-hidden="true" />
            ) : (
              <ChevronUpIcon className="size-4" aria-hidden="true" />
            )}
            {t(chartHidden ? 'stats.chartShow' : 'stats.chartHide')}
          </Button>
        </div>

        {/* Timeline chart. CSS vars are referenced DIRECTLY (not via
            hsl(var(--x))) because this project's design tokens hold
            already-resolved colors (hex/rgba), not the HSL-split shadcn
            v0 format — `hsl(#E5E7EB)` would parse as invalid and the SVG
            would fall back to Recharts defaults, producing the
            washed-out grey strip across the whole chart. */}
        <div
          className={cn(
            'border-border bg-card overflow-hidden rounded-md border transition-[height,padding] duration-200',
            chartHidden ? 'h-0 border-0 p-0' : 'h-[180px] p-3',
          )}
          aria-hidden={chartHidden}
        >
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
              {t('stats.chartEmpty', { days: chartEmptyDayHint(dateRangeSpec) })}
            </div>
          )}
          {!timelineQuery.isPending && chartData.length > 0 && (
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={chartData}
                margin={{ top: 8, right: 12, left: 0, bottom: 4 }}
                barCategoryGap="20%"
              >
                <CartesianGrid stroke="var(--border)" strokeDasharray="2 4" vertical={false} />
                <XAxis
                  dataKey="date"
                  tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }}
                  tickLine={false}
                  axisLine={{ stroke: 'var(--border)' }}
                  tickFormatter={(value: string) =>
                    new Date(value).toLocaleDateString('vi-VN', {
                      day: '2-digit',
                      month: '2-digit',
                    })
                  }
                />
                <YAxis
                  tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }}
                  tickLine={false}
                  axisLine={false}
                  allowDecimals={false}
                  width={28}
                />
                <RechartsTooltip
                  cursor={{ fill: 'var(--accent)', opacity: 0.4 }}
                  contentStyle={{
                    fontSize: 12,
                    backgroundColor: 'var(--card)',
                    border: '1px solid var(--border)',
                    borderRadius: 6,
                    color: 'var(--card-foreground)',
                    boxShadow: '0 4px 16px rgba(0,0,0,0.18)',
                  }}
                  itemStyle={{ color: 'var(--card-foreground)' }}
                  labelStyle={{ color: 'var(--muted-foreground)', marginBottom: 2 }}
                  labelFormatter={(label) => new Date(String(label)).toLocaleDateString('vi-VN')}
                  // Replace the default "count : N" row (which leaks the raw data
                  // key in any locale) with a locale-aware "Tổng N email" / "Total
                  // N emails" line. Returning [valueLabel, null] hides the dataKey
                  // name column so the tooltip is a single readable phrase.
                  formatter={(value) => [
                    t('stats.chartTooltipCount', { count: Number(value) }),
                    null,
                  ]}
                />
                <Bar dataKey="count" fill="var(--chart-4)" radius={[4, 4, 0, 0]} maxBarSize={36} />
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

          <div
            className={cn(
              'grid min-h-0 flex-1 gap-2',
              // Initial state: list spans full width (no preview pane).
              // After the user picks a row → split into 2 columns so the
              // body preview slides in beside the list.
              previewOpen ? 'grid-cols-1 md:grid-cols-2' : 'grid-cols-1',
            )}
          >
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

            {/* Preview pane — mounted only when the user has picked a row.
                Keeps the initial dialog clean (single column list) per the
                Inbox Zero-style layout, and avoids paying for the body
                fetch / iframe mount until something is actually selected. */}
            {previewOpen && (
              <div className="border-border bg-card flex min-h-0 flex-col overflow-hidden rounded-md border">
                {bodyQuery.isPending && (
                  <div className="text-muted-foreground flex h-full items-center justify-center text-sm">
                    <Loader2Icon className="mr-2 size-4 animate-spin" aria-hidden="true" />
                    {t('stats.previewLoading')}
                  </div>
                )}
                {bodyQuery.isError && (
                  <Alert variant="destructive" className="m-2">
                    <AlertTitle>{t('stats.previewError')}</AlertTitle>
                  </Alert>
                )}
                {bodyQuery.data && (
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
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function chartEmptyDayHint(spec: DateRangeSpec): number {
  // Translation slot expects a number ("Chưa có dữ liệu trong {days} ngày qua"). For preset
  // windows we already have it; for custom ranges compute the inclusive day span so the message
  // still reads naturally instead of leaking the raw start/end strings.
  if (spec.kind === 'window') return spec.windowDays;
  const start = new Date(`${spec.startDate}T00:00:00Z`).getTime();
  const end = new Date(`${spec.endDate}T00:00:00Z`).getTime();
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return 0;
  return Math.max(1, Math.round((end - start) / 86_400_000) + 1);
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
