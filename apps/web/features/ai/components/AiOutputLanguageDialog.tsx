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
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import type { VoiceSettings } from '@/features/ai/api/ai-settings-api';

type AiOutputLanguage = VoiceSettings['aiOutputLanguage'];

type AiOutputLanguageDialogProps = {
  value: AiOutputLanguage;
  onSave: (value: AiOutputLanguage) => Promise<unknown> | unknown;
  disabled?: boolean;
};

export function AiOutputLanguageDialog({ value, onSave, disabled }: AiOutputLanguageDialogProps) {
  const t = useTranslations();
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<AiOutputLanguage>(value);
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
            <DialogTitle>{t('ai.voice.language.title')}</DialogTitle>
            <DialogDescription>{t('ai.voice.language.description')}</DialogDescription>
          </DialogHeader>
          <RadioGroup
            value={draft}
            onValueChange={(nextValue) => setDraft(nextValue as AiOutputLanguage)}
          >
            <div className="flex items-center gap-2">
              <RadioGroupItem id="ai-language-vi" value="vi" disabled={saving} />
              <Label htmlFor="ai-language-vi">{t('ai.voice.language.vietnamese')}</Label>
            </div>
            <div className="flex items-center gap-2">
              <RadioGroupItem id="ai-language-en" value="en" disabled={saving} />
              <Label htmlFor="ai-language-en">{t('ai.voice.language.english')}</Label>
            </div>
          </RadioGroup>
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
