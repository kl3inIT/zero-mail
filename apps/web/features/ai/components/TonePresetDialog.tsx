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
import type { VoiceSettings } from '@/features/ai/api/ai-settings-api';

type TonePreset = VoiceSettings['tonePreset'];

type TonePresetDialogProps = {
  value: TonePreset;
  onSave: (value: TonePreset) => Promise<unknown> | unknown;
  disabled?: boolean;
};

const TONE_OPTIONS: TonePreset[] = ['PROFESSIONAL', 'FRIENDLY', 'CASUAL', 'FORMAL', 'CUSTOM'];

function toneLabel(translate: (key: string) => string, tonePreset: TonePreset): string {
  switch (tonePreset) {
    case 'FRIENDLY':
      return translate('ai.voice.tone.friendly');
    case 'CASUAL':
      return translate('ai.voice.tone.casual');
    case 'FORMAL':
      return translate('ai.voice.tone.formal');
    case 'CUSTOM':
      return translate('ai.voice.tone.custom');
    case 'PROFESSIONAL':
    default:
      return translate('ai.voice.tone.professional');
  }
}

export function TonePresetDialog({ value, onSave, disabled }: TonePresetDialogProps) {
  const t = useTranslations();
  const translate = t as unknown as (key: string) => string;
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<TonePreset>(value);
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
            <DialogTitle>{t('ai.voice.tone.title')}</DialogTitle>
            <DialogDescription>{t('ai.voice.tone.description')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="tone-preset">{t('ai.voice.tone.title')}</Label>
            <Select value={draft} onValueChange={(nextValue) => setDraft(nextValue as TonePreset)}>
              <SelectTrigger id="tone-preset" className="w-full" disabled={saving}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {TONE_OPTIONS.map((toneOption) => (
                  <SelectItem key={toneOption} value={toneOption}>
                    {toneLabel(translate, toneOption)}
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
