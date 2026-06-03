'use client';

import { useMemo, useState } from 'react';
import {
  ArchiveIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  ExternalLinkIcon,
  Loader2Icon,
  MailIcon,
  MailXIcon,
  MoreHorizontalIcon,
  TagIcon,
  ThumbsUpIcon,
  TrashIcon,
  ZoomInIcon,
} from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
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
import { cn } from '@/lib/utils';

export type CandidateStatus = 'APPROVED' | 'UNSUBSCRIBED' | 'AUTO_ARCHIVED';
export type SortColumn = 'emails' | 'read';
export type SortDirection = 'asc' | 'desc';

// senderName is added to the backend DTO by Phase 2; the optional access keeps the FE
// compiling against the not-yet-regenerated schema.d.ts and switches to fully-typed after
// `pnpm --filter web run generate:api` re-pulls the spec.
export type CandidateRow = UnsubscribeCandidateResponse & {
  readMessageCount?: number;
  status?: CandidateStatus | null;
  senderName?: string | null;
};

const LOW_READ_THRESHOLD = 30;
const HIGH_READ_THRESHOLD = 70;

export function CandidateListTable({
  candidates,
  selectedEmails,
  onToggleEmail,
  onToggleVisibleEmails,
  onPrimaryAction,
  onApproveToggle,
  onViewStats,
  onLabelFuture,
  onArchiveSender,
  onDeleteSender,
  pendingSenderEmails,
  sortColumn,
  sortDirection,
  onSort,
}: {
  candidates: CandidateRow[];
  selectedEmails: Set<string>;
  onToggleEmail: (senderEmail: string) => void;
  onToggleVisibleEmails: (senderEmails: string[], checked: boolean) => void;
  onPrimaryAction: (candidate: CandidateRow) => void;
  onApproveToggle: (candidate: CandidateRow) => void;
  onViewStats: (candidate: CandidateRow) => void;
  onLabelFuture: (candidate: CandidateRow) => void;
  onArchiveSender: (candidate: CandidateRow) => void;
  onDeleteSender: (candidate: CandidateRow) => void;
  pendingSenderEmails?: Set<string>;
  sortColumn: SortColumn;
  sortDirection: SortDirection;
  onSort: (column: SortColumn) => void;
}) {
  const t = useTranslations('cleanup.unsubscribe');
  const visibleEmails = useMemo(
    () => candidates.map((candidate) => candidate.senderEmail ?? '').filter(Boolean),
    [candidates],
  );
  const selectedVisibleCount = visibleEmails.filter((senderEmail) =>
    selectedEmails.has(senderEmail),
  ).length;
  const allVisibleSelected =
    visibleEmails.length > 0 && selectedVisibleCount === visibleEmails.length;
  const someVisibleSelected = selectedVisibleCount > 0 && !allVisibleSelected;

  return (
    <div className="border-border bg-card overflow-x-auto rounded-lg border shadow-sm">
      <Table className="min-w-[850px]">
        <TableHeader>
          <TableRow className="border-border bg-muted/30 hover:bg-muted/30">
            <TableHead className="w-10">
              <Checkbox
                aria-label={t('list.selectAll')}
                checked={allVisibleSelected}
                indeterminate={someVisibleSelected}
                disabled={visibleEmails.length === 0}
                onCheckedChange={(checked) =>
                  onToggleVisibleEmails(visibleEmails, checked === true)
                }
              />
            </TableHead>
            <TableHead className="h-12 min-w-80">{t('list.col.from')}</TableHead>
            <TableHead className="h-12 w-32 whitespace-nowrap">
              <HeaderButton
                sorted={sortColumn === 'emails'}
                sortDirection={sortColumn === 'emails' ? sortDirection : undefined}
                onClick={() => onSort('emails')}
              >
                {t('list.col.emails')}
              </HeaderButton>
            </TableHead>
            <TableHead className="h-12 w-44 whitespace-nowrap">
              <HeaderButton
                sorted={sortColumn === 'read'}
                sortDirection={sortColumn === 'read' ? sortDirection : undefined}
                onClick={() => onSort('read')}
              >
                {t('list.col.read')}
              </HeaderButton>
            </TableHead>
            <TableHead className="h-12 w-[210px] text-right" />
          </TableRow>
        </TableHeader>
        <TableBody>
          {candidates.map((candidate) => {
            const senderEmail = candidate.senderEmail ?? '';
            const isChecked = selectedEmails.has(senderEmail);
            const isPending = pendingSenderEmails?.has(senderEmail) ?? false;
            const readPercentage = readRate(candidate);
            const readRateStyle = readRateBarClasses(readPercentage);
            const approved = candidate.status === 'APPROVED';
            const unsubscribed = candidate.status === 'UNSUBSCRIBED';
            const autoArchived = candidate.status === 'AUTO_ARCHIVED';

            return (
              <TableRow
                key={senderEmail}
                className="border-border hover:bg-muted/25 align-middle"
                data-state={isChecked ? 'selected' : undefined}
              >
                <TableCell>
                  <Checkbox
                    aria-label={senderEmail}
                    checked={isChecked}
                    disabled={isPending}
                    onCheckedChange={() => onToggleEmail(senderEmail)}
                  />
                </TableCell>
                <TableCell>
                  <div className="flex min-w-0 items-center gap-3">
                    <SenderAvatar
                      senderEmail={senderEmail}
                      senderDomain={candidate.senderDomain ?? ''}
                      senderName={candidate.senderName ?? null}
                    />
                    <div className="min-w-0 lg:flex lg:items-baseline lg:gap-2">
                      <p className="truncate font-medium">
                        {senderDisplayLabel(candidate, senderEmail)}
                      </p>
                      <p className="text-muted-foreground truncate text-xs lg:text-sm">
                        {senderEmail}
                      </p>
                    </div>
                    {unsubscribed && <StatusPill label={t('statusLabel.unsubscribed')} />}
                    {autoArchived && <StatusPill label={t('statusLabel.autoArchived')} />}
                  </div>
                </TableCell>
                <TableCell className="whitespace-nowrap">
                  <span className="text-foreground/80 text-sm font-medium tabular-nums">
                    {candidate.messageCount ?? 0}
                  </span>
                </TableCell>
                <TableCell className="whitespace-nowrap">
                  <div className="flex items-center gap-2">
                    <div className="bg-muted h-1.5 w-16 overflow-hidden rounded-full">
                      <div
                        className={cn('h-full rounded-full', readRateStyle.fill)}
                        style={{ width: `${readPercentage}%` }}
                      />
                    </div>
                    <span className={cn('text-sm font-medium tabular-nums', readRateStyle.text)}>
                      {Math.round(readPercentage)}%
                    </span>
                  </div>
                </TableCell>
                <TableCell>
                  <div className="flex items-center justify-end gap-2">
                    <Tooltip>
                      <TooltipTrigger
                        render={
                          <Button
                            type="button"
                            variant={approved ? 'secondary' : 'ghost'}
                            size="icon-sm"
                            disabled={isPending || unsubscribed}
                            onClick={() => onApproveToggle(candidate)}
                            aria-label={
                              approved ? t('list.action.unapprove') : t('list.action.approve')
                            }
                          >
                            <ThumbsUpIcon
                              className={cn(
                                'size-4',
                                approved
                                  ? 'fill-violet-400 text-violet-500 dark:fill-violet-500/70 dark:text-violet-400'
                                  : 'text-muted-foreground',
                              )}
                              aria-hidden="true"
                            />
                          </Button>
                        }
                      />
                      <TooltipContent>
                        {approved ? t('list.action.unapprove') : t('list.action.approve')}
                      </TooltipContent>
                    </Tooltip>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      className="w-[110px] justify-center"
                      disabled={isPending || unsubscribed}
                      onClick={() => onPrimaryAction(candidate)}
                    >
                      {isPending ? (
                        <Loader2Icon className="size-4 animate-spin" aria-hidden="true" />
                      ) : (
                        <MailXIcon className="size-4" aria-hidden="true" />
                      )}
                      {primaryActionLabel(candidate, t)}
                    </Button>
                    <RowMenu
                      candidate={candidate}
                      onViewStats={onViewStats}
                      onLabelFuture={onLabelFuture}
                      onArchiveSender={onArchiveSender}
                      onDeleteSender={onDeleteSender}
                    />
                  </div>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}

function RowMenu({
  candidate,
  onViewStats,
  onLabelFuture,
  onArchiveSender,
  onDeleteSender,
}: {
  candidate: CandidateRow;
  onViewStats: (candidate: CandidateRow) => void;
  onLabelFuture: (candidate: CandidateRow) => void;
  onArchiveSender: (candidate: CandidateRow) => void;
  onDeleteSender: (candidate: CandidateRow) => void;
}) {
  const t = useTranslations('cleanup.unsubscribe');
  const senderEmail = candidate.senderEmail ?? '';

  return (
    <DropdownMenu>
      <DropdownMenuTrigger render={<Button type="button" variant="ghost" size="icon-sm" />}>
        <MoreHorizontalIcon className="size-4" aria-hidden="true" />
        <span className="sr-only">{t('list.action.menu')}</span>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuItem onClick={() => onViewStats(candidate)}>
          <ZoomInIcon className="mr-2 size-4" aria-hidden="true" />
          {t('list.action.viewStats')}
        </DropdownMenuItem>
        <DropdownMenuItem
          onClick={() => window.open(gmailSearchUrl(senderEmail), '_blank', 'noopener,noreferrer')}
        >
          <ExternalLinkIcon className="mr-2 size-4" aria-hidden="true" />
          {t('list.action.viewGmail')}
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={() => onLabelFuture(candidate)}>
          <TagIcon className="mr-2 size-4" aria-hidden="true" />
          {t('list.action.labelFuture')}
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={() => onArchiveSender(candidate)}>
          <ArchiveIcon className="mr-2 size-4" aria-hidden="true" />
          {t('list.action.archiveAll')}
        </DropdownMenuItem>
        <DropdownMenuItem variant="destructive" onClick={() => onDeleteSender(candidate)}>
          <TrashIcon className="mr-2 size-4" aria-hidden="true" />
          {t('list.action.deleteAll')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function HeaderButton({
  children,
  sorted,
  sortDirection,
  onClick,
}: {
  children: React.ReactNode;
  sorted: boolean;
  sortDirection?: SortDirection;
  onClick: () => void;
}) {
  return (
    <Button type="button" variant="ghost" size="sm" className="-ml-3 h-8" onClick={onClick}>
      <span className="text-muted-foreground">{children}</span>
      {sorted && sortDirection === 'asc' ? (
        <ChevronUpIcon className="text-muted-foreground ml-1 size-4" aria-hidden="true" />
      ) : (
        <ChevronDownIcon className="text-muted-foreground ml-1 size-4" aria-hidden="true" />
      )}
    </Button>
  );
}

function SenderAvatar({
  senderEmail,
  senderDomain,
  senderName,
}: {
  senderEmail: string;
  senderDomain: string;
  senderName: string | null;
}) {
  const domain = senderDomain.trim();
  // Inbox Zero pattern: always resolve to the second-level domain — Google's s2/favicons
  // returns the real brand logo for `domain=http://miro.com` but a globe placeholder for
  // subdomains like `product.miro.com`.
  const rootDomain = useMemo(() => {
    if (!domain) return null;
    const parts = domain.split('.');
    if (parts.length <= 2) return domain;
    return parts.slice(-2).join('.');
  }, [domain]);
  const [iconFailed, setIconFailed] = useState(false);
  const initials = senderInitials(senderName, senderDomain, senderEmail);
  // faviconV2 (Chrome's internal API) returns the real brand favicon at size=64 when the root
  // domain has one. When it doesn't, it answers HTTP 404 with a 16×16 globe placeholder PNG —
  // and the browser still renders that body, so onError never fires. We therefore also detect
  // the tiny placeholder in onLoad (naturalWidth <= 16) and fall back to initials, instead of
  // showing a blurry upscaled globe.
  const faviconUrl = rootDomain
    ? `https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=${encodeURIComponent(
        `https://${rootDomain}`,
      )}&size=64`
    : null;
  return (
    <div
      className="bg-muted text-muted-foreground relative flex size-8 shrink-0 items-center justify-center overflow-hidden rounded-full border text-xs font-semibold"
      aria-hidden="true"
    >
      {faviconUrl && !iconFailed ? (
        // eslint-disable-next-line @next/next/no-img-element -- Inbox Zero-style sender favicon via Google faviconV2; not worth a next/image domain entry.
        <img
          src={faviconUrl}
          alt=""
          className="size-full object-cover"
          loading="lazy"
          referrerPolicy="no-referrer"
          onError={() => setIconFailed(true)}
          onLoad={(event) => {
            if (event.currentTarget.naturalWidth > 0 && event.currentTarget.naturalWidth <= 16) {
              setIconFailed(true);
            }
          }}
        />
      ) : initials ? (
        initials
      ) : (
        <MailIcon className="size-3.5" />
      )}
    </div>
  );
}

function StatusPill({ label }: { label: string }) {
  return (
    <span className="bg-muted text-muted-foreground hidden rounded-md px-1.5 py-0.5 text-xs font-medium xl:inline-flex">
      {label}
    </span>
  );
}

/**
 * Inbox Zero-style display label: prefer the From-header display-name; fall back to the
 * sender domain (e.g. `user-service.klingai.com`) which is what Inbox Zero shows when a
 * sender has no human-set name. Local-part word-split is a last resort.
 */
function senderDisplayLabel(
  candidate: { senderName?: string | null; senderDomain?: string | null },
  senderEmail: string,
): string {
  const senderName = candidate.senderName?.trim();
  if (senderName) return senderName;
  const senderDomain = candidate.senderDomain?.trim();
  if (senderDomain) return senderDomain;
  const localPart = senderEmail.split('@')[0] ?? senderEmail;
  return localPart.replace(/[._-]+/g, ' ') || senderEmail;
}

function senderInitials(
  senderName: string | null,
  senderDomain: string,
  senderEmail: string,
): string {
  if (senderName && senderName.trim().length > 0) {
    const words = senderName.trim().split(/\s+/);
    const first = words[0]?.[0] ?? '';
    const second = words[1]?.[0] ?? '';
    const initials = `${first}${second}`.toUpperCase().slice(0, 2);
    if (initials) return initials;
  }
  if (senderDomain && senderDomain.length > 0) {
    return senderDomain.charAt(0).toUpperCase();
  }
  const localPart = senderEmail.split('@')[0] ?? '';
  const parts = localPart.split(/[._-]+/).filter(Boolean);
  const first = parts[0]?.[0] ?? '';
  const second = parts[1]?.[0] ?? '';
  return `${first}${second}`.toUpperCase().slice(0, 2);
}

function readRate(candidate: CandidateRow): number {
  const messageCount = candidate.messageCount ?? 0;
  if (messageCount <= 0) return 0;
  return ((candidate.readMessageCount ?? 0) / messageCount) * 100;
}

// Read-rate traffic light (design tokens only): <30% warning (rarely read → unsubscribe
// candidate), 30–70% primary (mid), ≥70% chart-4 green (highly read → engaged sender).
function readRateBarClasses(percentage: number): { fill: string; text: string } {
  if (percentage < LOW_READ_THRESHOLD) {
    return { fill: 'bg-warning', text: 'text-warning' };
  }
  if (percentage < HIGH_READ_THRESHOLD) {
    return { fill: 'bg-primary', text: 'text-foreground/80' };
  }
  return { fill: 'bg-(--chart-4)', text: 'text-foreground/80' };
}

function primaryActionLabel(
  candidate: CandidateRow,
  t: ReturnType<typeof useTranslations<'cleanup.unsubscribe'>>,
) {
  if (candidate.unsubscribeMethod === 'NONE') {
    return t('list.action.block');
  }
  return t('list.action.unsubscribe');
}

function gmailSearchUrl(senderEmail: string): string {
  return `https://mail.google.com/mail/u/0/#search/${encodeURIComponent(`from:${senderEmail}`)}`;
}
