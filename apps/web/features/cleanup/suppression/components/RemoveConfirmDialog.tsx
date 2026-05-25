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
import { useRemoveSuppression } from '@/features/cleanup/suppression/hooks/useRemoveSuppression';

export function RemoveConfirmDialog({
  open,
  onOpenChange,
  suppressionId,
}: {
  open: boolean;
  onOpenChange: (next: boolean) => void;
  suppressionId: string | null;
}) {
  const t = useTranslations();
  const remove = useRemoveSuppression();

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t('cleanup.suppression.remove.confirmTitle')}</AlertDialogTitle>
          <AlertDialogDescription>
            {t('cleanup.suppression.remove.confirmBody')}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={remove.isPending}>
            {t('cleanup.suppression.remove.cancel')}
          </AlertDialogCancel>
          <AlertDialogAction
            disabled={remove.isPending || !suppressionId}
            onClick={() => {
              if (!suppressionId) return;
              remove.mutate({ id: suppressionId }, { onSettled: () => onOpenChange(false) });
            }}
          >
            {t('cleanup.suppression.remove.confirmCta')}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
