'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ArchiveIcon,
  CalendarIcon,
  CheckIcon,
  ChevronDownIcon,
  InboxIcon,
  ListIcon,
  Loader2Icon,
  MailXIcon,
  SearchIcon,
  ThumbsUpIcon,
} from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Calendar } from '@/components/ui/calendar';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import type { DateRangeSpec } from '@/features/cleanup/unsubscribe-campaign/date-range-spec';
import { cn } from '@/lib/utils';
import type { DateRange } from 'react-day-picker';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Input } from '@/components/ui/input';
import type { CleanupSenderAction } from '@/features/cleanup/unsubscribe-campaign/api/unsubscribe-campaign-api';
import { CandidateListSkeleton } from '@/features/cleanup/unsubscribe-campaign/components/CandidateListSkeleton';
import {
  CandidateListTable,
  type CandidateRow,
  type CandidateStatus,
  type SortColumn,
  type SortDirection,
} from '@/features/cleanup/unsubscribe-campaign/components/CandidateListTable';
import { SelectionToolbar } from '@/features/cleanup/unsubscribe-campaign/components/SelectionToolbar';
import { SenderStatsDialog } from '@/features/cleanup/unsubscribe-campaign/components/SenderStatsDialog';
import { useCandidates } from '@/features/cleanup/unsubscribe-campaign/hooks/useCandidates';
import { useExecuteCampaign } from '@/features/cleanup/unsubscribe-campaign/hooks/useExecuteCampaign';
import { useSenderAction } from '@/features/cleanup/unsubscribe-campaign/hooks/useSenderAction';

type FilterType = 'unhandled' | 'all' | 'autoArchived' | 'approved';
type WindowId = '7d' | '30d' | '90d' | 'custom';

const DEFAULT_LIMIT = 50;
const LIMIT_STEP = 50;
// Hard ceiling matching `UnsubscribeCampaignPolicy.MAX_CANDIDATE_SENDERS` on the backend so we
// stop bumping the limit (and stop firing requests) once the user has scrolled to the cap.
const HARD_LIMIT = 500;
const PRESET_DAYS: Record<Exclude<WindowId, 'custom'>, number> = { '7d': 7, '30d': 30, '90d': 90 };

export function CandidateListPage() {
  const t = useTranslations('cleanup.unsubscribe');
  const [selectedEmails, setSelectedEmails] = useState<Set<string>>(new Set());
  const [searchQuery, setSearchQuery] = useState('');
  const [filter, setFilter] = useState<FilterType>('unhandled');
  const [windowId, setWindowId] = useState<WindowId>('90d');
  const [customRange, setCustomRange] = useState<{ startDate: string; endDate: string } | null>(
    null,
  );
  const [limit, setLimit] = useState(DEFAULT_LIMIT);

  // Effective window spec — preset windows resolve to a "last N days" Duration on the BE,
  // custom range carries explicit `[startDate, endDate]`. Memoize so identity is stable across
  // unrelated re-renders (avoids TanStack queryKey diffing churn).
  const dateRangeSpec = useMemo<DateRangeSpec>(() => {
    if (windowId === 'custom' && customRange) {
      return {
        kind: 'range',
        startDate: customRange.startDate,
        endDate: customRange.endDate,
      };
    }
    const presetId = windowId === 'custom' ? '90d' : windowId;
    return { kind: 'window', windowDays: PRESET_DAYS[presetId] };
  }, [windowId, customRange]);
  const [sortColumn, setSortColumn] = useState<SortColumn>('emails');
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc');
  const [statsCandidate, setStatsCandidate] = useState<CandidateRow | null>(null);
  const [labelCandidate, setLabelCandidate] = useState<CandidateRow | null>(null);
  const [labelName, setLabelName] = useState('');

  const candidatesQuery = useCandidates(dateRangeSpec, limit);
  const loadMoreSentinelRef = useRef<HTMLDivElement | null>(null);
  // True only when the last response returned exactly `limit` rows AND the limit is below the
  // backend hard cap — meaning BE may have more rows we haven't asked for yet. When fewer than
  // `limit` come back the dataset is exhausted; when limit == HARD_LIMIT we're at the ceiling
  // and would just refetch the same 500 rows.
  const hasMoreToLoad =
    !candidatesQuery.isError &&
    !candidatesQuery.isPending &&
    (candidatesQuery.data?.length ?? 0) >= limit &&
    limit < HARD_LIMIT;
  const clearSelection = useCallback(() => setSelectedEmails(new Set()), []);
  const executeMutation = useExecuteCampaign(dateRangeSpec, {
    onSuccess: () => {
      clearSelection();
      void candidatesQuery.refetch();
    },
  });
  const senderActionMutation = useSenderAction(dateRangeSpec);

  const rawCandidates = useMemo<CandidateRow[]>(
    () => (candidatesQuery.data ?? []) as CandidateRow[],
    [candidatesQuery.data],
  );
  const isBackgroundCandidateFetch = candidatesQuery.isFetching && !candidatesQuery.isPending;
  const isLoadingMoreCandidates =
    isBackgroundCandidateFetch && limit > DEFAULT_LIMIT && rawCandidates.length < limit;
  const candidateFetchLabel = isLoadingMoreCandidates
    ? t('list.loadingMore')
    : t('list.refreshing');

  // Reset the pagination limit whenever the user changes the window/range so a stale 200-row
  // limit from a previous search isn't carried into the new query (would just waste a fetch
  // for data we don't render anyway).
  const [trackedSpecKey, setTrackedSpecKey] = useState(JSON.stringify(dateRangeSpec));
  const currentSpecKey = JSON.stringify(dateRangeSpec);
  if (trackedSpecKey !== currentSpecKey) {
    setTrackedSpecKey(currentSpecKey);
    setLimit(DEFAULT_LIMIT);
  }

  // IntersectionObserver-driven infinite scroll. The sentinel sits just below the candidate
  // table; when it enters the viewport (200px early) we bump the limit by another page. The
  // bump triggers a TanStack refetch with `placeholderData: keepPreviousData`, so the existing
  // rows stay on screen and the new ones append underneath — no flash, no scroll jump.
  useEffect(() => {
    if (!hasMoreToLoad) return;
    const sentinel = loadMoreSentinelRef.current;
    if (!sentinel || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting && !candidatesQuery.isFetching) {
          setLimit((current) => Math.min(current + LIMIT_STEP, HARD_LIMIT));
        }
      },
      { rootMargin: '200px' },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMoreToLoad, candidatesQuery.isFetching]);

  // Clear the multi-select whenever the filter/search/window changes, via render-time state
  // adjustment (React-recommended) instead of an effect — avoids a cascading re-render.
  const selectionResetKey = JSON.stringify([filter, searchQuery, dateRangeSpec]);
  const [trackedSelectionResetKey, setTrackedSelectionResetKey] = useState(selectionResetKey);
  if (trackedSelectionResetKey !== selectionResetKey) {
    setTrackedSelectionResetKey(selectionResetKey);
    setSelectedEmails(new Set());
  }

  const visibleCandidates = useMemo(() => {
    const normalizedSearch = searchQuery.trim().toLowerCase();
    const filteredCandidates = rawCandidates.filter((candidate) => {
      const senderEmail = candidate.senderEmail?.toLowerCase() ?? '';
      const senderDomain = candidate.senderDomain?.toLowerCase() ?? '';
      const matchesSearch =
        normalizedSearch.length === 0 ||
        senderEmail.includes(normalizedSearch) ||
        senderDomain.includes(normalizedSearch);
      return matchesSearch && matchesFilter(candidate.status ?? null, filter);
    });

    return filteredCandidates.toSorted((leftCandidate, rightCandidate) => {
      const direction = sortDirection === 'desc' ? -1 : 1;
      if (sortColumn === 'read') {
        return (readRate(leftCandidate) - readRate(rightCandidate)) * direction;
      }
      return ((leftCandidate.messageCount ?? 0) - (rightCandidate.messageCount ?? 0)) * direction;
    });
  }, [filter, rawCandidates, searchQuery, sortColumn, sortDirection]);

  const selectedCandidates = useMemo(
    () => rawCandidates.filter((candidate) => selectedEmails.has(candidate.senderEmail ?? '')),
    [rawCandidates, selectedEmails],
  );
  const selectedMailCount = useMemo(
    () => selectedCandidates.reduce((sum, candidate) => sum + (candidate.messageCount ?? 0), 0),
    [selectedCandidates],
  );
  const selectedSenderEmails = useMemo(
    () => selectedCandidates.map((candidate) => candidate.senderEmail ?? '').filter(Boolean),
    [selectedCandidates],
  );
  const pendingSenderEmails = useMemo(() => {
    const pending = new Set<string>();
    if (executeMutation.isPending) {
      for (const senderEmail of executeMutation.variables?.senderEmails ?? [])
        pending.add(senderEmail);
    }
    if (senderActionMutation.isPending) {
      for (const senderEmail of senderActionMutation.variables?.senderEmails ?? [])
        pending.add(senderEmail);
    }
    return pending;
  }, [
    executeMutation.isPending,
    executeMutation.variables,
    senderActionMutation.isPending,
    senderActionMutation.variables,
  ]);

  const allSelectedApproved =
    selectedCandidates.length > 0 &&
    selectedCandidates.every((candidate) => candidate.status === 'APPROVED');
  const unsubscribeLabel = bulkPrimaryLabel(selectedCandidates, t);

  const toggleEmail = useCallback((senderEmail: string) => {
    setSelectedEmails((current) => {
      const next = new Set(current);
      if (next.has(senderEmail)) next.delete(senderEmail);
      else next.add(senderEmail);
      return next;
    });
  }, []);

  const toggleVisibleEmails = useCallback((senderEmails: string[], checked: boolean) => {
    setSelectedEmails((current) => {
      const next = new Set(current);
      for (const senderEmail of senderEmails) {
        if (checked) next.add(senderEmail);
        else next.delete(senderEmail);
      }
      return next;
    });
  }, []);

  const runSenderAction = useCallback(
    (
      action: CleanupSenderAction,
      senderEmails: string[],
      extra?: { labelName?: string; toastIntent?: 'block' },
    ) => {
      if (senderEmails.length === 0 || senderActionMutation.isPending) return;
      senderActionMutation.mutate({ action, senderEmails, ...extra });
    },
    [senderActionMutation],
  );

  const executeUnsubscribe = useCallback(
    (senderEmails: string[]) => {
      if (senderEmails.length === 0 || executeMutation.isPending) return;
      executeMutation.mutate({ senderEmails });
    },
    [executeMutation],
  );

  const primaryAction = useCallback(
    (candidate: CandidateRow) => {
      const senderEmail = candidate.senderEmail;
      if (!senderEmail) return;
      // Undo path: an AUTO_ARCHIVED sender's row button is "Bỏ chặn" / "Unblock".
      // Backend extends UNAPPROVE to also disable the auto-archive cleanup rule
      // for this sender, so clicking it both restores the row and stops future
      // emails from being silently archived.
      if (candidate.status === 'AUTO_ARCHIVED') {
        runSenderAction('UNAPPROVE', [senderEmail]);
        return;
      }
      if (candidate.unsubscribeMethod === 'NONE') {
        runSenderAction('AUTO_ARCHIVE', [senderEmail], { toastIntent: 'block' });
      } else {
        executeUnsubscribe([senderEmail]);
      }
    },
    [executeUnsubscribe, runSenderAction],
  );

  const bulkUnsubscribeOrBlock = useCallback(() => {
    const sendersWithUnsubscribe = selectedCandidates
      .filter((candidate) => candidate.unsubscribeMethod !== 'NONE')
      .map((candidate) => candidate.senderEmail ?? '')
      .filter(Boolean);
    const sendersToBlock = selectedCandidates
      .filter((candidate) => candidate.unsubscribeMethod === 'NONE')
      .map((candidate) => candidate.senderEmail ?? '')
      .filter(Boolean);

    if (sendersWithUnsubscribe.length > 0) executeUnsubscribe(sendersWithUnsubscribe);
    if (sendersToBlock.length > 0)
      runSenderAction('AUTO_ARCHIVE', sendersToBlock, { toastIntent: 'block' });
    clearSelection();
  }, [clearSelection, executeUnsubscribe, runSenderAction, selectedCandidates]);

  const handleSort = useCallback(
    (column: SortColumn) => {
      if (sortColumn === column) {
        setSortDirection((current) => (current === 'desc' ? 'asc' : 'desc'));
      } else {
        setSortColumn(column);
        setSortDirection('desc');
      }
    },
    [sortColumn],
  );

  const filterOption = filterOptions(t).find((option) => option.value === filter);
  const windowDisplayLabel =
    windowId === 'custom' && customRange
      ? t('window.customLabel', {
          startDate: formatIsoDateForDisplay(customRange.startDate),
          endDate: formatIsoDateForDisplay(customRange.endDate),
        })
      : (windowOptions(t).find((option) => option.value === windowId)?.label ?? t('window.90d'));

  return (
    <div className="flex flex-col gap-4">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{t('page.title')}</h1>
        <p className="text-muted-foreground text-sm">{t('page.description')}</p>
      </header>

      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-1 flex-col gap-3 sm:flex-row sm:items-center">
          <div className="relative w-full sm:max-w-[400px] lg:max-w-[460px]">
            <SearchIcon
              className="text-muted-foreground pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2"
              aria-hidden="true"
            />
            <Input
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.currentTarget.value)}
              placeholder={t('list.searchPlaceholder')}
              className="bg-background h-11 pl-9"
            />
          </div>
          <div className="flex flex-col gap-3 sm:ml-auto sm:flex-row sm:items-center">
            <OptionMenu
              label={filterOption?.label ?? t('filter.all')}
              icon={filterOption?.icon}
              options={filterOptions(t)}
              value={filter}
              onSelect={(value) => setFilter(value as FilterType)}
            />
            <DateRangePicker
              value={windowId}
              customRange={customRange}
              label={windowDisplayLabel}
              onSelectPreset={(preset) => {
                setWindowId(preset);
                setCustomRange(null);
              }}
              onApplyRange={(range) => {
                setCustomRange(range);
                setWindowId('custom');
              }}
            />
            {isBackgroundCandidateFetch && rawCandidates.length > 0 && (
              <div
                className="border-border bg-muted/30 text-muted-foreground inline-flex h-9 w-fit shrink-0 items-center gap-2 rounded-md border px-3 text-xs"
                role="status"
                aria-live="polite"
              >
                <Loader2Icon className="size-3.5 animate-spin" aria-hidden="true" />
                <span>{candidateFetchLabel}</span>
              </div>
            )}
          </div>
        </div>
      </div>

      {selectedEmails.size > 0 && (
        <SelectionToolbar
          selectedCount={selectedSenderEmails.length}
          totalCount={visibleCandidates.length}
          selectedMailCount={selectedMailCount}
          unsubscribeLabel={unsubscribeLabel}
          allSelectedApproved={allSelectedApproved}
          onClear={clearSelection}
          onUnsubscribeOrBlock={bulkUnsubscribeOrBlock}
          onAutoArchive={() => {
            runSenderAction('AUTO_ARCHIVE', selectedSenderEmails);
            clearSelection();
          }}
          onApproveToggle={() => {
            runSenderAction(allSelectedApproved ? 'UNAPPROVE' : 'APPROVE', selectedSenderEmails);
            clearSelection();
          }}
          onArchive={() => {
            runSenderAction('ARCHIVE', selectedSenderEmails);
            clearSelection();
          }}
          onDelete={() => {
            runSenderAction('DELETE', selectedSenderEmails);
            clearSelection();
          }}
          isExecuting={executeMutation.isPending || senderActionMutation.isPending}
        />
      )}

      {candidatesQuery.isPending && <CandidateListSkeleton />}

      {candidatesQuery.isError && (
        <Alert variant="destructive">
          <AlertTitle>{t('list.error')}</AlertTitle>
          <AlertDescription>
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => void candidatesQuery.refetch()}
            >
              {t('list.retry')}
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {!candidatesQuery.isPending && !candidatesQuery.isError && rawCandidates.length === 0 && (
        <div className="border-border bg-card flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed py-10 text-center shadow-sm">
          <MailXIcon className="text-muted-foreground size-5" aria-hidden="true" />
          <h2 className="text-foreground text-base font-medium">{t('list.empty.title')}</h2>
          <p className="text-muted-foreground max-w-md text-sm">{t('list.empty.body')}</p>
        </div>
      )}

      {!candidatesQuery.isPending && !candidatesQuery.isError && rawCandidates.length > 0 && (
        <CandidateListTable
          candidates={visibleCandidates}
          selectedEmails={selectedEmails}
          onToggleEmail={toggleEmail}
          onToggleVisibleEmails={toggleVisibleEmails}
          onPrimaryAction={primaryAction}
          onApproveToggle={(candidate) => {
            const senderEmail = candidate.senderEmail;
            if (!senderEmail) return;
            runSenderAction(candidate.status === 'APPROVED' ? 'UNAPPROVE' : 'APPROVE', [
              senderEmail,
            ]);
          }}
          onViewStats={setStatsCandidate}
          onLabelFuture={(candidate) => {
            setLabelCandidate(candidate);
            setLabelName('');
          }}
          onArchiveSender={(candidate) => {
            if (candidate.senderEmail) runSenderAction('ARCHIVE', [candidate.senderEmail]);
          }}
          onDeleteSender={(candidate) => {
            if (!candidate.senderEmail) return;
            if (window.confirm(t('confirm.deleteOne', { sender: candidate.senderEmail }))) {
              runSenderAction('DELETE', [candidate.senderEmail]);
            }
          }}
          pendingSenderEmails={pendingSenderEmails}
          sortColumn={sortColumn}
          sortDirection={sortDirection}
          onSort={handleSort}
        />
      )}

      {/* Infinite-scroll sentinel + footer status. The sentinel triggers the limit bump via
          IntersectionObserver above; the footer shows a spinner while the background refetch
          settles, then disappears once the dataset is exhausted (hasMoreToLoad = false). */}
      {rawCandidates.length > 0 && (
        <div
          ref={loadMoreSentinelRef}
          className="text-muted-foreground flex h-10 items-center justify-center text-xs"
          data-testid="cleanup-load-more-sentinel"
        >
          {isLoadingMoreCandidates ? (
            <span className="inline-flex items-center gap-2">
              <Loader2Icon className="size-3.5 animate-spin" aria-hidden="true" />
              {t('list.loadingMore')}
            </span>
          ) : null}
        </div>
      )}

      <SenderStatsDialog
        senderEmail={statsCandidate?.senderEmail ?? null}
        senderName={statsCandidate?.senderName ?? null}
        senderDomain={statsCandidate?.senderDomain ?? null}
        unsubscribeMethod={statsCandidate?.unsubscribeMethod ?? null}
        dateRangeSpec={dateRangeSpec}
        onOpenChange={(open) => !open && setStatsCandidate(null)}
        onUnsubscribe={() => {
          if (!statsCandidate?.senderEmail) return;
          if (statsCandidate.unsubscribeMethod === 'NONE') {
            runSenderAction('AUTO_ARCHIVE', [statsCandidate.senderEmail]);
          } else {
            executeUnsubscribe([statsCandidate.senderEmail]);
          }
          setStatsCandidate(null);
        }}
        onAutoArchive={() => {
          if (!statsCandidate?.senderEmail) return;
          runSenderAction('AUTO_ARCHIVE', [statsCandidate.senderEmail]);
          setStatsCandidate(null);
        }}
        isExecuting={executeMutation.isPending || senderActionMutation.isPending}
      />
      <Dialog
        open={labelCandidate !== null}
        onOpenChange={(open) => !open && setLabelCandidate(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('labelFuture.title')}</DialogTitle>
            <DialogDescription>
              {t('labelFuture.body', {
                sender: labelCandidate?.senderEmail ?? '',
              })}
            </DialogDescription>
          </DialogHeader>
          <Input
            value={labelName}
            onChange={(event) => setLabelName(event.currentTarget.value)}
            placeholder={t('labelFuture.placeholder')}
            autoFocus
          />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setLabelCandidate(null)}>
              {t('confirm.cancel')}
            </Button>
            <Button
              type="button"
              disabled={!labelName.trim() || !labelCandidate?.senderEmail}
              onClick={() => {
                if (!labelCandidate?.senderEmail) return;
                runSenderAction('LABEL_FUTURE', [labelCandidate.senderEmail], {
                  labelName: labelName.trim(),
                });
                setLabelCandidate(null);
              }}
            >
              {t('list.action.labelFuture')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function OptionMenu({
  label,
  icon,
  options,
  value,
  onSelect,
}: {
  label: string;
  icon?: React.ReactNode;
  options: { label: string; value: string; icon?: React.ReactNode; separatorAfter?: boolean }[];
  value: string;
  onSelect: (value: string) => void;
}) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            type="button"
            variant="outline"
            size="lg"
            className="h-11 w-full justify-between sm:w-[190px]"
          />
        }
      >
        <span className="flex items-center gap-2 truncate">
          {icon}
          {label}
        </span>
        <ChevronDownIcon className="text-muted-foreground size-4" aria-hidden="true" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-[190px]">
        {options.map((option) => (
          <div key={option.value}>
            <DropdownMenuItem onClick={() => onSelect(option.value)}>
              {option.icon}
              <span className="flex-1">{option.label}</span>
              {value === option.value && <CheckIcon className="size-4" aria-hidden="true" />}
            </DropdownMenuItem>
            {option.separatorAfter && <DropdownMenuSeparator />}
          </div>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function filterOptions(t: ReturnType<typeof useTranslations<'cleanup.unsubscribe'>>) {
  return [
    { label: t('filter.unhandled'), value: 'unhandled', icon: <InboxIcon className="size-4" /> },
    {
      label: t('filter.all'),
      value: 'all',
      icon: <ListIcon className="size-4" />,
      separatorAfter: true,
    },
    {
      label: t('filter.autoArchived'),
      value: 'autoArchived',
      icon: <ArchiveIcon className="size-4" />,
    },
    { label: t('filter.approved'), value: 'approved', icon: <ThumbsUpIcon className="size-4" /> },
  ];
}

function windowOptions(t: ReturnType<typeof useTranslations<'cleanup.unsubscribe'>>) {
  return [
    { label: t('window.7d'), value: '7d' as const },
    { label: t('window.30d'), value: '30d' as const },
    { label: t('window.90d'), value: '90d' as const },
    { label: t('window.custom'), value: 'custom' as const },
  ];
}

function formatIsoDateForDisplay(isoDate: string): string {
  const parsed = new Date(`${isoDate}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime())) return isoDate;
  return parsed.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

function DateRangePicker({
  value,
  customRange,
  label,
  onSelectPreset,
  onApplyRange,
}: {
  value: WindowId;
  customRange: { startDate: string; endDate: string } | null;
  label: string;
  onSelectPreset: (preset: Exclude<WindowId, 'custom'>) => void;
  onApplyRange: (range: { startDate: string; endDate: string }) => void;
}) {
  const t = useTranslations('cleanup.unsubscribe');
  const [open, setOpen] = useState(false);
  const [draftRange, setDraftRange] = useState<DateRange | undefined>(() =>
    isoRangeToDateRange(customRange),
  );
  const [validationError, setValidationError] = useState<string | null>(null);
  const today = useMemo(() => new Date(), []);
  const presets = windowOptions(t).filter((option) => option.value !== 'custom');

  // Reset the popover form whenever it reopens so a stale draft from a previous open does not
  // override the currently-applied range.
  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen);
    if (nextOpen) {
      setDraftRange(isoRangeToDateRange(customRange));
      setValidationError(null);
    }
  }

  function handleApply() {
    if (!draftRange?.from || !draftRange?.to) {
      setValidationError(t('window.customMissing'));
      return;
    }
    if (draftRange.from > draftRange.to) {
      setValidationError(t('window.customInvalidOrder'));
      return;
    }
    setValidationError(null);
    onApplyRange({
      startDate: dateToIsoDate(draftRange.from),
      endDate: dateToIsoDate(draftRange.to),
    });
    setOpen(false);
  }

  // Default the visible months to the active range's start (or two months ago for first open),
  // so users land near their last picked window instead of always today.
  const defaultMonth = useMemo(() => {
    if (draftRange?.from) return draftRange.from;
    const previousMonth = new Date(today);
    previousMonth.setMonth(previousMonth.getMonth() - 1);
    return previousMonth;
  }, [draftRange, today]);

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger
        render={
          <Button
            type="button"
            variant="outline"
            size="lg"
            className="h-11 w-full justify-between sm:w-auto sm:min-w-[220px]"
          />
        }
      >
        <span className="flex items-center gap-2 truncate">
          <CalendarIcon className="size-4" aria-hidden="true" />
          {label}
        </span>
        <ChevronDownIcon className="text-muted-foreground size-4" aria-hidden="true" />
      </PopoverTrigger>
      <PopoverContent align="start" className="w-auto p-0" data-testid="cleanup-date-range-picker">
        <div className="flex flex-col gap-0 sm:flex-row">
          <div className="border-border sm:border-r">
            <Calendar
              mode="range"
              numberOfMonths={2}
              defaultMonth={defaultMonth}
              selected={draftRange}
              onSelect={(range) => {
                setDraftRange(defaultRangeEndToToday(range, today));
                setValidationError(null);
              }}
              disabled={{ after: today }}
              // Hide outside days so the same calendar date never renders in two adjacent
              // months. With outside days on, picking "June 1" lit up both the real cell in
              // the June grid and the trailing outside cell in May's last row, producing the
              // confusing double-selection and stray lavender highlights the user reported.
              showOutsideDays={false}
            />
            {validationError && (
              <p className="text-destructive border-border border-t px-3 py-2 text-xs" role="alert">
                {validationError}
              </p>
            )}
            <div className="border-border flex items-center justify-between gap-2 border-t px-3 py-2">
              <p className="text-muted-foreground text-xs">
                {draftRange?.from && draftRange?.to
                  ? t('window.customLabel', {
                      startDate: formatDayMonthYear(draftRange.from),
                      endDate: formatDayMonthYear(draftRange.to),
                    })
                  : t('window.customMissing')}
              </p>
              <Button
                type="button"
                size="sm"
                disabled={!draftRange?.from || !draftRange?.to}
                onClick={handleApply}
              >
                {t('window.customApply')}
              </Button>
            </div>
          </div>
          <ul className="bg-background flex w-full shrink-0 flex-col gap-0.5 p-2 sm:w-44">
            {presets.map((preset) => {
              const active = value === preset.value;
              return (
                <li key={preset.value}>
                  <button
                    type="button"
                    className={cn(
                      'hover:bg-accent hover:text-accent-foreground flex w-full items-center justify-between rounded-md px-3 py-2 text-left text-sm transition-colors',
                      active && 'bg-accent text-accent-foreground font-medium',
                    )}
                    onClick={() => {
                      onSelectPreset(preset.value);
                      setOpen(false);
                    }}
                  >
                    <span>{preset.label}</span>
                    {active && <CheckIcon className="size-4" aria-hidden="true" />}
                  </button>
                </li>
              );
            })}
          </ul>
        </div>
      </PopoverContent>
    </Popover>
  );
}

function isoRangeToDateRange(
  range: { startDate: string; endDate: string } | null,
): DateRange | undefined {
  if (!range) return undefined;
  const from = new Date(`${range.startDate}T00:00:00`);
  const to = new Date(`${range.endDate}T00:00:00`);
  if (Number.isNaN(from.getTime()) || Number.isNaN(to.getTime())) return undefined;
  return { from, to };
}

function defaultRangeEndToToday(range: DateRange | undefined, today: Date): DateRange | undefined {
  if (!range?.from) return range;
  if (range.to) return range;
  return { from: range.from, to: today };
}

function dateToIsoDate(date: Date): string {
  // Use the local-calendar date (year-month-day) rather than UTC, so a click on "March 6" in
  // Vietnam time records 2026-03-06 instead of slipping to 2026-03-05 via toISOString() when
  // local midnight is still the prior UTC day.
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatDayMonthYear(date: Date): string {
  return date.toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

function matchesFilter(status: CandidateStatus | null, filter: FilterType): boolean {
  switch (filter) {
    case 'all':
      return true;
    case 'unhandled':
      return status === null;
    case 'autoArchived':
      return status === 'AUTO_ARCHIVED';
    case 'approved':
      return status === 'APPROVED';
  }
}

function readRate(candidate: CandidateRow): number {
  const messageCount = candidate.messageCount ?? 0;
  if (messageCount <= 0) return 0;
  return ((candidate.readMessageCount ?? 0) / messageCount) * 100;
}

function bulkPrimaryLabel(
  selectedCandidates: CandidateRow[],
  t: ReturnType<typeof useTranslations<'cleanup.unsubscribe'>>,
): string {
  const hasUnsubscribe = selectedCandidates.some(
    (candidate) => candidate.unsubscribeMethod !== 'NONE',
  );
  const hasBlock = selectedCandidates.some((candidate) => candidate.unsubscribeMethod === 'NONE');
  if (hasUnsubscribe && hasBlock) return t('list.action.unsubscribeBlock');
  if (hasBlock) return t('list.action.block');
  return t('list.action.unsubscribe');
}
