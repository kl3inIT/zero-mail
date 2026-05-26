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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import type { BehaviorSettings } from '@/features/ai/api/ai-settings-api';

type DraftConfidence = BehaviorSettings['draftConfidence'];

type DraftConfidenceDialogProps = {
  value: DraftConfidence;
  onSave: (value: DraftConfidence) => Promise<unknown> | unknown;
  disabled?: boolean;
};

const CONFIDENCE_OPTIONS: DraftConfidence[] = ['LOW', 'MEDIUM', 'HIGH'];

function confidenceLabel(
  translate: (key: string) => string,
  draftConfidence: DraftConfidence,
): string {
  switch (draftConfidence) {
    case 'LOW':
      return translate('ai.behavior.draftConfidence.low');
    case 'HIGH':
      return translate('ai.behavior.draftConfidence.high');
    case 'MEDIUM':
    default:
      return translate('ai.behavior.draftConfidence.medium');
  }
}

export function DraftConfidenceDialog({ value, onSave, disabled }: DraftConfidenceDialogProps) {
  const t = useTranslations();
  const translate = t as unknown as (key: string) => string;
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<DraftConfidence>(value);
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
        {t('ai.actions.edit')}
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{t('ai.behavior.draftConfidence.title')}</DialogTitle>
            <DialogDescription>{t('ai.behavior.draftConfidence.description')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="draft-confidence">{t('ai.behavior.draftConfidence.title')}</Label>
            <Select
              value={draft}
              onValueChange={(nextValue) => setDraft(nextValue as DraftConfidence)}
            >
              <SelectTrigger id="draft-confidence" className="w-full" disabled={saving}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {CONFIDENCE_OPTIONS.map((confidenceOption) => (
                  <SelectItem key={confidenceOption} value={confidenceOption}>
                    {confidenceLabel(translate, confidenceOption)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
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
