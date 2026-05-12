'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { toast } from 'sonner';

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
import { Button } from '@/components/ui/button';
import type { AuditEntry } from '@/features/triage/api/triage-api';
import { useUndoAuditEntry } from '@/features/triage/hooks/useUndoAuditEntry';

type UndoButtonProps = {
  entry: AuditEntry;
  onUndone: () => void;
};

export function UndoButton({ entry, onUndone }: UndoButtonProps) {
  const t = useTranslations();
  const undo = useUndoAuditEntry();
  const [open, setOpen] = useState(false);

  return (
    <>
      <Button type="button" variant="outline" size="sm" onClick={() => setOpen(true)}>
        {t('triage.audit.undo.cta')}
      </Button>
      <AlertDialog open={open} onOpenChange={setOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('triage.audit.undo.dialogTitle')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('triage.audit.undo.dialogDescription', { action: entry.inverseAction })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('triage.audit.undo.cancel')}</AlertDialogCancel>
            <AlertDialogAction
              variant="accent"
              disabled={undo.isPending}
              onClick={() => {
                undo.mutate(entry.id, {
                  onSuccess: () => {
                    setOpen(false);
                    onUndone();
                    toast.success(t('triage.audit.undo.success'));
                  },
                  onError: () => {
                    toast.error(t('triage.audit.undo.error'));
                  },
                });
              }}
            >
              {t('triage.audit.undo.confirm')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
