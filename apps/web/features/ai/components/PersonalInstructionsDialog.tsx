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

type PersonalInstructionsDialogProps = {
  value: string;
  onSave: (value: string) => Promise<unknown> | unknown;
  disabled?: boolean;
};

export function PersonalInstructionsDialog({
  value,
  onSave,
  disabled,
}: PersonalInstructionsDialogProps) {
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
            <DialogTitle>{t('ai.voice.personalInstructions.title')}</DialogTitle>
            <DialogDescription>{t('ai.voice.personalInstructions.description')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="personal-instructions">
              {t('ai.voice.personalInstructions.fieldLabel')}
            </Label>
            <Textarea
              id="personal-instructions"
              value={draft}
              onChange={(changeEvent) => setDraft(changeEvent.target.value)}
              placeholder={t('ai.voice.personalInstructions.placeholder')}
              maxLength={2000}
              className="min-h-36"
              disabled={saving}
            />
            <p className="text-muted-foreground text-xs">{draft.length}/2000</p>
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
