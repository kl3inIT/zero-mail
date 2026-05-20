'use client';

import Link from 'next/link';
import { useCallback, useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { CandidateListSkeleton } from '@/features/cleanup/unsubscribe-campaign/components/CandidateListSkeleton';
import { CandidateListTable } from '@/features/cleanup/unsubscribe-campaign/components/CandidateListTable';
import { PreviewCampaignDialog } from '@/features/cleanup/unsubscribe-campaign/components/PreviewCampaignDialog';
import { SelectionToolbar } from '@/features/cleanup/unsubscribe-campaign/components/SelectionToolbar';
import { useCandidates } from '@/features/cleanup/unsubscribe-campaign/hooks/useCandidates';

export function CandidateListPage() {
  const t = useTranslations();
  const candidatesQuery = useCandidates('30d', 25);
  const [selectedEmails, setSelectedEmails] = useState<Set<string>>(new Set());
  const [previewOpen, setPreviewOpen] = useState(false);

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

  const clearSelection = useCallback(() => {
    setSelectedEmails(new Set());
  }, []);

  const selectedEmailsArray = useMemo(() => [...selectedEmails], [selectedEmails]);

  return (
    <div className="flex flex-col gap-5">
      <div className="border-foreground/10 flex flex-col gap-3 border-b pb-5 md:flex-row md:items-end md:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-foreground text-2xl leading-tight font-semibold">
            {t('cleanup.unsubscribe.list.title')}
          </h1>
          <p className="text-muted-foreground max-w-3xl text-sm leading-6">
            {t('cleanup.unsubscribe.list.lead')}
          </p>
        </div>
        <Link
          href="/cleanup/suppression"
          className="text-primary text-sm underline-offset-4 hover:underline"
        >
          {t('cleanup.unsubscribe.list.suppressionLink')}
        </Link>
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

      {!candidatesQuery.isPending &&
        !candidatesQuery.isError &&
        (candidatesQuery.data ?? []).length === 0 && (
          <div className="border-foreground/10 flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed py-10 text-center">
            <h2 className="text-foreground text-base font-medium">
              {t('cleanup.unsubscribe.list.empty.title')}
            </h2>
            <p className="text-muted-foreground max-w-md text-sm">
              {t('cleanup.unsubscribe.list.empty.body')}
            </p>
          </div>
        )}

      {!candidatesQuery.isPending &&
        !candidatesQuery.isError &&
        (candidatesQuery.data ?? []).length > 0 && (
          <>
            <SelectionToolbar
              selectedCount={selectedEmails.size}
              onClear={clearSelection}
              onPreview={() => setPreviewOpen(true)}
            />
            <CandidateListTable
              candidates={candidatesQuery.data ?? []}
              selectedEmails={selectedEmails}
              onToggleEmail={toggleEmail}
            />
            <PreviewCampaignDialog
              open={previewOpen}
              onOpenChange={setPreviewOpen}
              senderEmails={selectedEmailsArray}
            />
          </>
        )}
    </div>
  );
}
