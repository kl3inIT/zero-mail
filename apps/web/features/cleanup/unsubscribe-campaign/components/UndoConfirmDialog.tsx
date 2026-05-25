'use client';

import { useTranslations } from 'next-intl';

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { useUndoCampaign } from '@/features/cleanup/unsubscribe-campaign/hooks/useUndoCampaign';

export function UndoConfirmDialog({
  open,
  onOpenChange,
  jobId,
  archivedCount,
}: {
  open: boolean;
  onOpenChange: (next: boolean) => void;
  jobId: string;
  archivedCount: number;
}) {
  const t = useTranslations();
  const undo = useUndoCampaign();

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t('cleanup.unsubscribe.undo.confirmTitle')}</AlertDialogTitle>
          <AlertDialogDescription>
            {t('cleanup.unsubscribe.undo.confirmBody', { count: archivedCount })}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={undo.isPending}>
            {t('cleanup.unsubscribe.undo.cancel')}
          </AlertDialogCancel>
          <AlertDialogAction
            disabled={undo.isPending}
            onClick={() => {
              undo.mutate(
                { jobId, restoredCount: archivedCount },
                { onSettled: () => onOpenChange(false) },
              );
            }}
          >
            {t('cleanup.unsubscribe.undo.confirmCta')}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
