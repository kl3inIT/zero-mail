'use client';

import { Fragment, useMemo, useState } from 'react';
import {
  ArchiveIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  Clock3Icon,
  MailXIcon,
  ShieldIcon,
} from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import type { UnsubscribeCandidateResponse } from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import { RiskBadge } from '@/features/cleanup/unsubscribe-campaign/components/RiskBadge';
import { cn } from '@/lib/utils';

type CandidateRow = UnsubscribeCandidateResponse & { riskBadge?: string };

const lastSeenFormatter = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

function deriveRiskBadge(candidate: CandidateRow): string {
  if (candidate.riskBadge) return candidate.riskBadge;
  if (candidate.suppressed) return 'SUPPRESSED_BLOCKED';
  if (candidate.unsubscribeMethod === 'NONE') return 'NO_HEADER_DISABLED';
  return 'SAFE';
}

export function CandidateListTable({
  candidates,
  selectedEmails,
  onToggleEmail,
  onToggleVisibleEmails,
  onPreviewSender,
  onKeepSender,
  keepingSenderEmail,
}: {
  candidates: CandidateRow[];
  selectedEmails: Set<string>;
  onToggleEmail: (senderEmail: string) => void;
  onToggleVisibleEmails: (senderEmails: string[], checked: boolean) => void;
  onPreviewSender: (senderEmail: string) => void;
  onKeepSender: (senderEmail: string) => void;
  keepingSenderEmail?: string;
}) {
  const t = useTranslations();
  const [expandedSenderEmail, setExpandedSenderEmail] = useState<string | null>(null);
  const maxMessageCount = useMemo(
    () => Math.max(1, ...candidates.map((candidate) => candidate.messageCount ?? 0)),
    [candidates],
  );
  const selectableVisibleEmails = useMemo(
    () =>
      candidates.reduce<string[]>((emails, candidate) => {
        if (
          candidate.senderEmail &&
          candidate.unsubscribeMethod !== 'NONE' &&
          candidate.suppressed !== true
        ) {
          emails.push(candidate.senderEmail);
        }
        return emails;
      }, []),
    [candidates],
  );
  const selectedVisibleCount = selectableVisibleEmails.filter((senderEmail) =>
    selectedEmails.has(senderEmail),
  ).length;
  const allVisibleSelected =
    selectableVisibleEmails.length > 0 && selectedVisibleCount === selectableVisibleEmails.length;
  const someVisibleSelected = selectedVisibleCount > 0 && !allVisibleSelected;

  return (
    <div className="border-border bg-card overflow-x-auto rounded-lg border shadow-sm">
      <Table className="min-w-[760px]">
        <TableHeader>
          <TableRow className="border-border bg-muted/30 hover:bg-muted/30">
            <TableHead className="w-10">
              <Checkbox
                aria-label={t('cleanup.unsubscribe.list.selectAll')}
                checked={allVisibleSelected}
                indeterminate={someVisibleSelected}
                disabled={selectableVisibleEmails.length === 0}
                onCheckedChange={(checked) =>
                  onToggleVisibleEmails(selectableVisibleEmails, checked === true)
                }
              />
            </TableHead>
            <TableHead className="h-12">{t('cleanup.unsubscribe.list.col.sender')}</TableHead>
            <TableHead className="h-12 min-w-36">
              {t('cleanup.unsubscribe.list.col.history')}
            </TableHead>
            <TableHead className="h-12">{t('cleanup.unsubscribe.list.col.risk')}</TableHead>
            <TableHead className="text-right">
              {t('cleanup.unsubscribe.list.col.actions')}
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {candidates.map((candidate) => {
            const senderEmail = candidate.senderEmail ?? '';
            const senderDomain = candidate.senderDomain ?? '';
            const method = candidate.unsubscribeMethod ?? 'NONE';
            const isDisabled = method === 'NONE' || candidate.suppressed === true;
            const isChecked = selectedEmails.has(senderEmail);
            const risk = deriveRiskBadge(candidate);
            const showTooltip = risk === 'NO_HEADER_DISABLED';
            const isExpanded = expandedSenderEmail === senderEmail;
            const messageCount = candidate.messageCount ?? 0;
            const messageShare = Math.max(6, Math.round((messageCount / maxMessageCount) * 100));
            const isKeeping = keepingSenderEmail === senderEmail;

            const rowContent = (
              <TableRow
                key={senderEmail}
                className={cn(
                  'border-border hover:bg-muted/25 align-middle',
                  isDisabled && 'opacity-60',
                )}
                data-state={isChecked ? 'selected' : undefined}
              >
                <TableCell>
                  <Checkbox
                    aria-label={senderEmail}
                    checked={isChecked}
                    disabled={isDisabled}
                    onCheckedChange={() => {
                      if (!isDisabled) onToggleEmail(senderEmail);
                    }}
                  />
                </TableCell>
                <TableCell>
                  <div className="flex min-w-0 flex-col gap-1">
                    <div className="flex items-center gap-2">
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="size-7 shrink-0"
                        aria-label={
                          isExpanded
                            ? t('cleanup.unsubscribe.list.action.collapse')
                            : t('cleanup.unsubscribe.list.action.details')
                        }
                        onClick={() =>
                          setExpandedSenderEmail((current) =>
                            current === senderEmail ? null : senderEmail,
                          )
                        }
                      >
                        {isExpanded ? (
                          <ChevronDownIcon className="size-4" aria-hidden="true" />
                        ) : (
                          <ChevronRightIcon className="size-4" aria-hidden="true" />
                        )}
                      </Button>
                      <span className="truncate font-medium">{senderEmail}</span>
                    </div>
                    <span className="text-muted-foreground truncate pl-9 text-xs">
                      {senderDomain}
                    </span>
                  </div>
                </TableCell>
                <TableCell>
                  <div className="flex min-w-32 flex-col gap-1.5">
                    <span className="text-sm font-medium tabular-nums">
                      {t('cleanup.unsubscribe.list.historyCount', { count: messageCount })}
                    </span>
                    <div className="bg-muted/70 h-2 overflow-hidden rounded-full">
                      <div className="bg-primary/70 h-full" style={{ width: `${messageShare}%` }} />
                    </div>
                  </div>
                </TableCell>
                <TableCell>
                  <RiskBadge risk={risk} />
                </TableCell>
                <TableCell>
                  <div className="flex justify-end">
                    <Button
                      type="button"
                      variant="default"
                      size="sm"
                      disabled={isDisabled}
                      aria-label={`${t('cleanup.unsubscribe.list.action.unsubscribe')} ${senderEmail}`}
                      onClick={() => onPreviewSender(senderEmail)}
                    >
                      <MailXIcon className="size-4" aria-hidden="true" />
                      <span className="hidden lg:inline">
                        {t('cleanup.unsubscribe.list.action.unsubscribe')}
                      </span>
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            );

            const renderedRow = showTooltip ? (
              <Tooltip key={senderEmail}>
                <TooltipTrigger render={rowContent} />
                <TooltipContent>{t('cleanup.unsubscribe.risk.noHeaderTooltip')}</TooltipContent>
              </Tooltip>
            ) : (
              rowContent
            );

            return (
              <Fragment key={senderEmail}>
                {renderedRow}
                {isExpanded && (
                  <TableRow className="border-border bg-muted/20 hover:bg-muted/20">
                    <TableCell />
                    <TableCell colSpan={4} className="whitespace-normal">
                      <div className="grid gap-3 py-3 text-sm md:grid-cols-4">
                        <div className="flex min-w-0 items-start gap-2">
                          <Clock3Icon
                            className="text-muted-foreground mt-0.5 size-4 shrink-0"
                            aria-hidden="true"
                          />
                          <div className="min-w-0">
                            <p className="text-muted-foreground text-xs">
                              {t('cleanup.unsubscribe.list.detail.lastSeen')}
                            </p>
                            <p className="font-medium break-words">
                              {formatLastSeen(candidate.lastSeenAt)}
                            </p>
                          </div>
                        </div>
                        <div className="flex min-w-0 items-start gap-2">
                          <ArchiveIcon
                            className="text-muted-foreground mt-0.5 size-4 shrink-0"
                            aria-hidden="true"
                          />
                          <div className="min-w-0">
                            <p className="text-muted-foreground text-xs">
                              {t('cleanup.unsubscribe.list.detail.archive')}
                            </p>
                            <p className="font-medium break-words">
                              {t('cleanup.unsubscribe.list.detail.archiveValue', {
                                count: messageCount,
                              })}
                            </p>
                          </div>
                        </div>
                        <div className="flex min-w-0 items-start gap-2">
                          <ShieldIcon
                            className="text-muted-foreground mt-0.5 size-4 shrink-0"
                            aria-hidden="true"
                          />
                          <div className="min-w-0">
                            <p className="text-muted-foreground text-xs">
                              {t('cleanup.unsubscribe.list.detail.safety')}
                            </p>
                            <p className="font-medium break-words">
                              {isDisabled
                                ? t('cleanup.unsubscribe.list.detail.disabled')
                                : t('cleanup.unsubscribe.list.detail.safe')}
                            </p>
                          </div>
                        </div>
                        <div className="flex min-w-0 items-start gap-2">
                          <ShieldIcon
                            className="text-muted-foreground mt-0.5 size-4 shrink-0"
                            aria-hidden="true"
                          />
                          <div className="flex min-w-0 flex-col gap-2">
                            <div>
                              <p className="text-muted-foreground text-xs">
                                {t('cleanup.unsubscribe.list.detail.skip')}
                              </p>
                              <p className="text-muted-foreground text-xs leading-5 break-words">
                                {t('cleanup.unsubscribe.list.detail.skipDescription')}
                              </p>
                            </div>
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              className="w-fit"
                              disabled={isKeeping}
                              aria-label={`${t('cleanup.unsubscribe.list.action.keep')} ${senderEmail}`}
                              onClick={() => onKeepSender(senderEmail)}
                            >
                              <ShieldIcon className="size-4" aria-hidden="true" />
                              {t('cleanup.unsubscribe.list.action.keep')}
                            </Button>
                          </div>
                        </div>
                      </div>
                    </TableCell>
                  </TableRow>
                )}
              </Fragment>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}

function formatLastSeen(value: string | undefined): string {
  if (!value) return '-';
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) return value;
  return lastSeenFormatter.format(new Date(timestamp));
}
