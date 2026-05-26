'use client';

import { useState, type FormEvent } from 'react';
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
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';

type EmailSignatureDialogProps = {
  value: string;
  onSave: (value: string) => Promise<unknown> | unknown;
  disabled?: boolean;
};

export function EmailSignatureDialog({ value, onSave, disabled }: EmailSignatureDialogProps) {
  const t = useTranslations();
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState(value);
  const [saving, setSaving] = useState(false);

  function handleOpenChange(nextOpen: boolean) {
    if (nextOpen) {
      setDraft(value);
    }
    setOpen(nextOpen);
  }

  async function handleSubmit(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    setSaving(true);
    try {
      await onSave(draft);
      setOpen(false);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger render={<Button variant="outline" disabled={disabled} />}>
        {value.trim().length > 0 ? t('ai.actions.edit') : t('ai.actions.set')}
      </DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{t('ai.voice.signature.title')}</DialogTitle>
            <DialogDescription>{t('ai.voice.signature.description')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="email-signature">{t('ai.voice.signature.title')}</Label>
            <Textarea
              id="email-signature"
              value={draft}
              onChange={(changeEvent) => setDraft(changeEvent.target.value)}
              placeholder={t('ai.voice.signature.placeholder')}
              maxLength={500}
              className="min-h-28"
              disabled={saving}
            />
            <p className="text-muted-foreground text-xs">{draft.length}/500</p>
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
              disabled={saving}
            >
              {t('ai.actions.cancel')}
            </Button>
            <Button type="submit" disabled={saving}>
              {t('ai.actions.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
