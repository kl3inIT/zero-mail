'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArchiveIcon,
  CalendarIcon,
  CheckIcon,
  ChevronDownIcon,
  InboxIcon,
  ListIcon,
  MailXIcon,
  RefreshCwIcon,
  SearchIcon,
  ThumbsUpIcon,
} from 'lucide-react';
import { useTranslations } from 'next-intl';

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

type FilterType = 'unhandled' | 'all' | 'unsubscribed' | 'autoArchived' | 'approved';
type WindowId = '7d' | '30d' | '90d';

const DEFAULT_LIMIT = 50;
const EXPANDED_LIMIT = 500;

export function CandidateListPage() {
  const t = useTranslations();
  const [selectedEmails, setSelectedEmails] = useState<Set<string>>(new Set());
  const [searchQuery, setSearchQuery] = useState('');
  const [filter, setFilter] = useState<FilterType>('unhandled');
  const [windowId, setWindowId] = useState<WindowId>('90d');
  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [sortColumn, setSortColumn] = useState<SortColumn>('emails');
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc');
  const [statsCandidate, setStatsCandidate] = useState<CandidateRow | null>(null);
  const [labelCandidate, setLabelCandidate] = useState<CandidateRow | null>(null);
  const [labelName, setLabelName] = useState('');

  const candidatesQuery = useCandidates(windowId, limit);
  const clearSelection = useCallback(() => setSelectedEmails(new Set()), []);
  const executeMutation = useExecuteCampaign(windowId, {
    onSuccess: () => {
      clearSelection();
      void candidatesQuery.refetch();
    },
  });
  const senderActionMutation = useSenderAction(windowId, limit);

  const rawCandidates = useMemo<CandidateRow[]>(
    () => ((candidatesQuery.data ?? []) as CandidateRow[]),
    [candidatesQuery.data],
  );

  useEffect(() => {
    clearSelection();
  }, [clearSelection, filter, searchQuery, windowId]);

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
      for (const senderEmail of executeMutation.variables?.senderEmails ?? []) pending.add(senderEmail);
    }
    if (senderActionMutation.isPending) {
      for (const senderEmail of senderActionMutation.variables?.senderEmails ?? []) pending.add(senderEmail);
    }
    return pending;
  }, [executeMutation.isPending, executeMutation.variables, senderActionMutation.isPending, senderActionMutation.variables]);

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
  const windowOption = windowOptions(t).find((option) => option.value === windowId);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-1 flex-col gap-3 sm:flex-row sm:items-center">
          <OptionMenu
            label={filterOption?.label ?? t('cleanup.unsubscribe.filter.all')}
            icon={filterOption?.icon}
            options={filterOptions(t)}
            value={filter}
            onSelect={(value) => setFilter(value as FilterType)}
          />
          <OptionMenu
            label={windowOption?.label ?? t('cleanup.unsubscribe.window.90d')}
            icon={<CalendarIcon className="size-4" aria-hidden="true" />}
            options={windowOptions(t)}
            value={windowId}
            onSelect={(value) => setWindowId(value as WindowId)}
          />
          <div className="relative w-full sm:max-w-[320px]">
            <SearchIcon
              className="text-muted-foreground pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2"
              aria-hidden="true"
            />
            <Input
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.currentTarget.value)}
              placeholder={t('cleanup.unsubscribe.list.searchPlaceholder')}
              className="bg-background h-11 pl-9"
            />
          </div>
        </div>
        <Button
          type="button"
          variant="outline"
          size="lg"
          className="h-11 w-full sm:w-auto"
          disabled={limit >= EXPANDED_LIMIT || candidatesQuery.isFetching}
          onClick={() => setLimit(EXPANDED_LIMIT)}
        >
          <RefreshCwIcon className={candidatesQuery.isFetching ? 'size-4 animate-spin' : 'size-4'} aria-hidden="true" />
          {t('cleanup.unsubscribe.list.loadMore')}
        </Button>
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
          <AlertTitle>{t('cleanup.unsubscribe.list.error')}</AlertTitle>
          <AlertDescription>
            <Button type="button" size="sm" variant="outline" onClick={() => void candidatesQuery.refetch()}>
              {t('cleanup.unsubscribe.list.retry')}
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {!candidatesQuery.isPending && !candidatesQuery.isError && rawCandidates.length === 0 && (
        <div className="border-border bg-card flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed py-10 text-center shadow-sm">
          <MailXIcon className="text-muted-foreground size-5" aria-hidden="true" />
          <h2 className="text-foreground text-base font-medium">
            {t('cleanup.unsubscribe.list.empty.title')}
          </h2>
          <p className="text-muted-foreground max-w-md text-sm">
            {t('cleanup.unsubscribe.list.empty.body')}
          </p>
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
            runSenderAction(candidate.status === 'APPROVED' ? 'UNAPPROVE' : 'APPROVE', [senderEmail]);
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
            if (window.confirm(t('cleanup.unsubscribe.confirm.deleteOne', { sender: candidate.senderEmail }))) {
              runSenderAction('DELETE', [candidate.senderEmail]);
            }
          }}
          pendingSenderEmails={pendingSenderEmails}
          sortColumn={sortColumn}
          sortDirection={sortDirection}
          onSort={handleSort}
        />
      )}

      <SenderStatsDialog
        senderEmail={statsCandidate?.senderEmail ?? null}
        senderName={statsCandidate?.senderName ?? null}
        senderDomain={statsCandidate?.senderDomain ?? null}
        unsubscribeMethod={statsCandidate?.unsubscribeMethod ?? null}
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
      <Dialog open={labelCandidate !== null} onOpenChange={(open) => !open && setLabelCandidate(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('cleanup.unsubscribe.labelFuture.title')}</DialogTitle>
            <DialogDescription>
              {t('cleanup.unsubscribe.labelFuture.body', {
                sender: labelCandidate?.senderEmail ?? '',
              })}
            </DialogDescription>
          </DialogHeader>
          <Input
            value={labelName}
            onChange={(event) => setLabelName(event.currentTarget.value)}
            placeholder={t('cleanup.unsubscribe.labelFuture.placeholder')}
            autoFocus
          />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setLabelCandidate(null)}>
              {t('cleanup.unsubscribe.confirm.cancel')}
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
              {t('cleanup.unsubscribe.list.action.labelFuture')}
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
      <DropdownMenuTrigger render={<Button type="button" variant="outline" size="lg" className="h-11 w-full justify-between sm:w-[190px]" />}>
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


function filterOptions(t: ReturnType<typeof useTranslations>) {
  return [
    { label: t('cleanup.unsubscribe.filter.unhandled'), value: 'unhandled', icon: <InboxIcon className="size-4" /> },
    { label: t('cleanup.unsubscribe.filter.all'), value: 'all', icon: <ListIcon className="size-4" />, separatorAfter: true },
    { label: t('cleanup.unsubscribe.filter.unsubscribed'), value: 'unsubscribed', icon: <MailXIcon className="size-4" /> },
    { label: t('cleanup.unsubscribe.filter.autoArchived'), value: 'autoArchived', icon: <ArchiveIcon className="size-4" /> },
    { label: t('cleanup.unsubscribe.filter.approved'), value: 'approved', icon: <ThumbsUpIcon className="size-4" /> },
  ];
}

function windowOptions(t: ReturnType<typeof useTranslations>) {
  return [
    { label: t('cleanup.unsubscribe.window.7d'), value: '7d' },
    { label: t('cleanup.unsubscribe.window.30d'), value: '30d' },
    { label: t('cleanup.unsubscribe.window.90d'), value: '90d' },
  ];
}

function matchesFilter(status: CandidateStatus | null, filter: FilterType): boolean {
  switch (filter) {
    case 'all':
      return true;
    case 'unhandled':
      return status === null;
    case 'unsubscribed':
      return status === 'UNSUBSCRIBED';
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

function bulkPrimaryLabel(selectedCandidates: CandidateRow[], t: ReturnType<typeof useTranslations>): string {
  const hasUnsubscribe = selectedCandidates.some((candidate) => candidate.unsubscribeMethod !== 'NONE');
  const hasBlock = selectedCandidates.some((candidate) => candidate.unsubscribeMethod === 'NONE');
  if (hasUnsubscribe && hasBlock) return t('cleanup.unsubscribe.list.action.unsubscribeBlock');
  if (hasBlock) return t('cleanup.unsubscribe.list.action.block');
  return t('cleanup.unsubscribe.list.action.unsubscribe');
}

