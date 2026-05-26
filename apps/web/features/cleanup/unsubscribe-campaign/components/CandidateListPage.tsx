'use client';

import { useCallback, useMemo, useState } from 'react';
import {
  ArchiveIcon,
  CircleHelpIcon,
  MailXIcon,
  SearchIcon,
  ShieldIcon,
  type LucideIcon,
} from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Popover,
  PopoverContent,
  PopoverDescription,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger,
} from '@/components/ui/popover';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { CandidateListSkeleton } from '@/features/cleanup/unsubscribe-campaign/components/CandidateListSkeleton';
import { CandidateListTable } from '@/features/cleanup/unsubscribe-campaign/components/CandidateListTable';
import { PreviewCampaignDialog } from '@/features/cleanup/unsubscribe-campaign/components/PreviewCampaignDialog';
import { SelectionToolbar } from '@/features/cleanup/unsubscribe-campaign/components/SelectionToolbar';
import { useCandidates } from '@/features/cleanup/unsubscribe-campaign/hooks/useCandidates';
import { SuppressionDialog } from '@/features/cleanup/suppression/components/SuppressionDialog';
import { useAddSuppression } from '@/features/cleanup/suppression/hooks/useAddSuppression';

type MethodFilter = 'ALL' | 'READY' | 'ONE_CLICK' | 'MAILTO';
type SortMode = 'COUNT_DESC' | 'RECENT_DESC' | 'SENDER_ASC';

export function CandidateListPage() {
  const t = useTranslations();
  const candidatesQuery = useCandidates('30d', 25);
  const addSuppressionMutation = useAddSuppression();
  const [selectedEmails, setSelectedEmails] = useState<Set<string>>(new Set());
  const [previewOpen, setPreviewOpen] = useState(false);
  const [suppressionDialogOpen, setSuppressionDialogOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [methodFilter, setMethodFilter] = useState<MethodFilter>('READY');
  const [sortMode, setSortMode] = useState<SortMode>('COUNT_DESC');

  const rawCandidates = useMemo(() => candidatesQuery.data ?? [], [candidatesQuery.data]);

  const stats = useMemo(() => {
    const readyCandidates = rawCandidates.filter(
      (candidate) => candidate.unsubscribeMethod !== 'NONE' && !candidate.suppressed,
    );
    const oneClickCount = rawCandidates.filter(
      (candidate) => candidate.unsubscribeMethod === 'ONE_CLICK',
    ).length;
    const totalMessages = rawCandidates.reduce(
      (sum, candidate) => sum + (candidate.messageCount ?? 0),
      0,
    );
    return {
      total: rawCandidates.length,
      ready: readyCandidates.length,
      oneClick: oneClickCount,
      totalMessages,
    };
  }, [rawCandidates]);

  const visibleCandidates = useMemo(() => {
    const normalizedSearch = searchQuery.trim().toLowerCase();
    const filtered = rawCandidates.filter((candidate) => {
      const senderEmail = candidate.senderEmail?.toLowerCase() ?? '';
      const senderDomain = candidate.senderDomain?.toLowerCase() ?? '';
      const matchesSearch =
        normalizedSearch.length === 0 ||
        senderEmail.includes(normalizedSearch) ||
        senderDomain.includes(normalizedSearch);
      const isReady = candidate.unsubscribeMethod !== 'NONE' && !candidate.suppressed;
      const matchesFilter =
        methodFilter === 'ALL' ||
        (methodFilter === 'READY' && isReady) ||
        candidate.unsubscribeMethod === methodFilter;
      return matchesSearch && matchesFilter;
    });

    return [...filtered].sort((left, right) => {
      if (sortMode === 'RECENT_DESC') {
        return Date.parse(right.lastSeenAt ?? '') - Date.parse(left.lastSeenAt ?? '');
      }
      if (sortMode === 'SENDER_ASC') {
        return (left.senderEmail ?? '').localeCompare(right.senderEmail ?? '');
      }
      return (right.messageCount ?? 0) - (left.messageCount ?? 0);
    });
  }, [methodFilter, rawCandidates, searchQuery, sortMode]);

  const selectedMailCount = useMemo(
    () =>
      rawCandidates
        .filter((candidate) => selectedEmails.has(candidate.senderEmail ?? ''))
        .reduce((sum, candidate) => sum + (candidate.messageCount ?? 0), 0),
    [rawCandidates, selectedEmails],
  );

  const toggleEmail = useCallback((senderEmail: string) => {
    setSelectedEmails((current) => {
      const next = new Set(current);
      if (next.has(senderEmail)) {
        next.delete(senderEmail);
      } else {
        next.add(senderEmail);
      }
      return next;
    });
  }, []);

  const toggleVisibleEmails = useCallback((senderEmails: string[], checked: boolean) => {
    setSelectedEmails((current) => {
      const next = new Set(current);
      for (const senderEmail of senderEmails) {
        if (checked) {
          next.add(senderEmail);
        } else {
          next.delete(senderEmail);
        }
      }
      return next;
    });
  }, []);

  const clearSelection = useCallback(() => {
    setSelectedEmails(new Set());
  }, []);

  const previewSingleSender = useCallback((senderEmail: string) => {
    setSelectedEmails(new Set([senderEmail]));
    setPreviewOpen(true);
  }, []);

  const keepSender = useCallback(
    (senderEmail: string) => {
      addSuppressionMutation.mutate(
        { senderEmailOrDomain: senderEmail },
        {
          onSuccess: () => {
            setSelectedEmails((current) => {
              const next = new Set(current);
              next.delete(senderEmail);
              return next;
            });
            void candidatesQuery.refetch();
          },
        },
      );
    },
    [addSuppressionMutation, candidatesQuery],
  );

  const selectedEmailsArray = useMemo(() => [...selectedEmails], [selectedEmails]);
  const methodFilterLabel = t(methodFilterMessageKey(methodFilter));
  const sortModeLabel = t(sortModeMessageKey(sortMode));

  return (
    <div className="flex flex-col gap-4">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          icon={MailXIcon}
          label={t('cleanup.unsubscribe.list.stats.total')}
          value={stats.total}
        />
        <MetricCard
          icon={ShieldIcon}
          label={t('cleanup.unsubscribe.list.stats.ready')}
          value={stats.ready}
        />
        <MetricCard
          icon={ArchiveIcon}
          label={t('cleanup.unsubscribe.list.stats.messages')}
          value={stats.totalMessages}
        />
        <MetricCard
          icon={MailXIcon}
          label={t('cleanup.unsubscribe.list.stats.oneClick')}
          value={stats.oneClick}
        />
      </div>

      <div className="border-border bg-card flex flex-col gap-3 rounded-lg border p-3 shadow-sm xl:flex-row xl:items-center xl:justify-between">
        <div className="relative w-full lg:w-[420px]">
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

        <div className="flex w-full flex-wrap items-center gap-2 lg:w-auto">
          <UnsubscribeHelpAction />
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="h-11 w-full sm:w-auto"
            onClick={() => setSuppressionDialogOpen(true)}
          >
            <ShieldIcon className="size-4" aria-hidden="true" />
            {t('cleanup.unsubscribe.list.suppressionLink')}
          </Button>
          <Select
            value={methodFilter}
            onValueChange={(value) => setMethodFilter(value as MethodFilter)}
          >
            <SelectTrigger
              className="bg-background h-11 w-full justify-between gap-3 px-4 sm:w-[190px]"
              aria-label={t('cleanup.unsubscribe.list.filterLabel')}
            >
              <SelectValue>{methodFilterLabel}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">{t('cleanup.unsubscribe.list.filter.all')}</SelectItem>
              <SelectItem value="READY">{t('cleanup.unsubscribe.list.filter.ready')}</SelectItem>
              <SelectItem value="ONE_CLICK">
                {t('cleanup.unsubscribe.list.filter.oneClick')}
              </SelectItem>
              <SelectItem value="MAILTO">{t('cleanup.unsubscribe.list.filter.mailto')}</SelectItem>
            </SelectContent>
          </Select>

          <Select value={sortMode} onValueChange={(value) => setSortMode(value as SortMode)}>
            <SelectTrigger
              className="bg-background h-11 w-full justify-between gap-3 px-4 sm:w-[210px]"
              aria-label={t('cleanup.unsubscribe.list.sortLabel')}
            >
              <SelectValue>{sortModeLabel}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="COUNT_DESC">{t('cleanup.unsubscribe.list.sort.count')}</SelectItem>
              <SelectItem value="RECENT_DESC">
                {t('cleanup.unsubscribe.list.sort.recent')}
              </SelectItem>
              <SelectItem value="SENDER_ASC">
                {t('cleanup.unsubscribe.list.sort.sender')}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {candidatesQuery.isPending && <CandidateListSkeleton />}

      {candidatesQuery.isError && (
        <Alert variant="destructive">
          <AlertTitle>{t('cleanup.unsubscribe.list.error')}</AlertTitle>
          <AlertDescription>
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => void candidatesQuery.refetch()}
            >
              {t('cleanup.unsubscribe.list.retry')}
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {!candidatesQuery.isPending && !candidatesQuery.isError && rawCandidates.length === 0 && (
        <div className="border-border bg-card flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed py-10 text-center shadow-sm">
          <h2 className="text-foreground text-base font-medium">
            {t('cleanup.unsubscribe.list.empty.title')}
          </h2>
          <p className="text-muted-foreground max-w-md text-sm">
            {t('cleanup.unsubscribe.list.empty.body')}
          </p>
        </div>
      )}

      {!candidatesQuery.isPending && !candidatesQuery.isError && rawCandidates.length > 0 && (
        <>
          <SelectionToolbar
            selectedCount={selectedEmails.size}
            selectedMailCount={selectedMailCount}
            onClear={clearSelection}
            onPreview={() => setPreviewOpen(true)}
          />
          <CandidateListTable
            candidates={visibleCandidates}
            selectedEmails={selectedEmails}
            onToggleEmail={toggleEmail}
            onToggleVisibleEmails={toggleVisibleEmails}
            onPreviewSender={previewSingleSender}
            onKeepSender={keepSender}
            keepingSenderEmail={
              addSuppressionMutation.isPending
                ? addSuppressionMutation.variables?.senderEmailOrDomain
                : undefined
            }
          />
          <PreviewCampaignDialog
            open={previewOpen}
            onOpenChange={setPreviewOpen}
            senderEmails={selectedEmailsArray}
          />
        </>
      )}
      <SuppressionDialog open={suppressionDialogOpen} onOpenChange={setSuppressionDialogOpen} />
    </div>
  );
}

function methodFilterMessageKey(
  methodFilter: MethodFilter,
):
  | 'cleanup.unsubscribe.list.filter.all'
  | 'cleanup.unsubscribe.list.filter.ready'
  | 'cleanup.unsubscribe.list.filter.oneClick'
  | 'cleanup.unsubscribe.list.filter.mailto' {
  switch (methodFilter) {
    case 'READY':
      return 'cleanup.unsubscribe.list.filter.ready';
    case 'ONE_CLICK':
      return 'cleanup.unsubscribe.list.filter.oneClick';
    case 'MAILTO':
      return 'cleanup.unsubscribe.list.filter.mailto';
    case 'ALL':
      return 'cleanup.unsubscribe.list.filter.all';
  }
}

function sortModeMessageKey(
  sortMode: SortMode,
):
  | 'cleanup.unsubscribe.list.sort.count'
  | 'cleanup.unsubscribe.list.sort.recent'
  | 'cleanup.unsubscribe.list.sort.sender' {
  switch (sortMode) {
    case 'RECENT_DESC':
      return 'cleanup.unsubscribe.list.sort.recent';
    case 'SENDER_ASC':
      return 'cleanup.unsubscribe.list.sort.sender';
    case 'COUNT_DESC':
      return 'cleanup.unsubscribe.list.sort.count';
  }
}

function UnsubscribeHelpAction() {
  const t = useTranslations();

  return (
    <Popover>
      <PopoverTrigger
        render={
          <button
            type="button"
            className="text-muted-foreground hover:text-foreground focus-visible:ring-ring grid size-8 place-items-center rounded-md transition-colors outline-none focus-visible:ring-2"
            aria-label={t('cleanup.unsubscribe.list.helpLabel')}
          />
        }
      >
        <CircleHelpIcon className="size-4" aria-hidden="true" />
      </PopoverTrigger>
      <PopoverContent align="end" side="bottom" className="w-80">
        <PopoverHeader>
          <PopoverTitle>{t('cleanup.unsubscribe.list.helpTitle')}</PopoverTitle>
          <PopoverDescription className="text-sm leading-6">
            {t('cleanup.unsubscribe.list.helpBody')}
          </PopoverDescription>
        </PopoverHeader>
      </PopoverContent>
    </Popover>
  );
}

function MetricCard({
  icon: Icon,
  label,
  value,
}: {
  icon: LucideIcon;
  label: string;
  value: number;
}) {
  const locale = useLocale();
  return (
    <div className="border-border bg-card rounded-lg border px-5 py-5 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <p className="text-muted-foreground min-w-0 text-sm leading-5 font-medium">{label}</p>
        <Icon className="text-muted-foreground size-4 shrink-0" aria-hidden="true" />
      </div>
      <p className="text-foreground mt-4 text-3xl leading-none font-semibold tracking-normal tabular-nums">
        {formatMetricValue(value, locale)}
      </p>
    </div>
  );
}

function formatMetricValue(value: number, locale: string): string {
  const intlLocale = locale === 'vi' ? 'vi-VN' : 'en-US';
  return new Intl.NumberFormat(intlLocale, {
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value);
}
