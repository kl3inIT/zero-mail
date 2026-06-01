'use client';

import { useState, type ReactElement } from 'react';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';

type ConfirmDialogProps = {
  title: string;
  description: string;
  trigger: ReactElement;
  confirmLabel: string;
  onConfirm: () => void | Promise<void>;
  variant?: 'destructive';
};

export function ConfirmDialog({
  title,
  description,
  trigger,
  confirmLabel,
  onConfirm,
  variant = 'destructive',
}: ConfirmDialogProps) {
  const t = useTranslations();
  const [open, setOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);

  async function handleConfirm() {
    setConfirming(true);
    try {
      await onConfirm();
      setOpen(false);
    } finally {
      setConfirming(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={trigger} />
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => setOpen(false)}
            disabled={confirming}
          >
            {t('ai.actions.cancel')}
          </Button>
          <Button type="button" variant={variant} onClick={handleConfirm} disabled={confirming}>
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
