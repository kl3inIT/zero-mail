'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { UndoConfirmDialog } from '@/features/cleanup/unsubscribe-campaign/components/UndoConfirmDialog';

const UNDO_WINDOW_DAYS = 30;
const ONE_DAY_MS = 24 * 60 * 60 * 1000;

function computeDaysLeft(appliedAt: string | undefined): number {
  if (!appliedAt) return UNDO_WINDOW_DAYS;
  const appliedEpoch = new Date(appliedAt).getTime();
  if (Number.isNaN(appliedEpoch)) return UNDO_WINDOW_DAYS;
  const elapsedDays = Math.floor((Date.now() - appliedEpoch) / ONE_DAY_MS);
  return Math.max(0, UNDO_WINDOW_DAYS - elapsedDays);
}

export function UndoBanner({
  jobId,
  appliedAt,
  archivedCount,
  undoAvailable,
}: {
  jobId: string;
  appliedAt?: string;
  archivedCount: number;
  undoAvailable: boolean;
}) {
  const t = useTranslations();
  const [confirmOpen, setConfirmOpen] = useState(false);
  const daysLeft = computeDaysLeft(appliedAt);
  const expired = !undoAvailable || daysLeft <= 0;

  return (
    <>
      <Alert variant="warning">
        <AlertTitle>{t('cleanup.unsubscribe.status.undo.title')}</AlertTitle>
        <AlertDescription>
          <p>{t('cleanup.unsubscribe.status.undo.body', { daysLeft })}</p>
          <div className="mt-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={expired}
              title={
                expired
                  ? t('cleanup.unsubscribe.undo.windowExpired')
                  : t('cleanup.unsubscribe.status.undo.button')
              }
              onClick={() => setConfirmOpen(true)}
            >
              {t('cleanup.unsubscribe.status.undo.button')}
            </Button>
          </div>
        </AlertDescription>
      </Alert>
      <UndoConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        jobId={jobId}
        archivedCount={archivedCount}
      />
    </>
  );
}
