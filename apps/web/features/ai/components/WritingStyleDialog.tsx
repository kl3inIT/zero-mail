'use client';

import { useMemo, useState, type FormEvent } from 'react';
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
import { useGenerateVoiceFromSent } from '@/features/ai/hooks/useGenerateVoiceFromSent';

type WritingStyleDialogProps = {
  value: string;
  onSave: (value: string) => Promise<unknown> | unknown;
  disabled?: boolean;
};

function countWords(value: string): number {
  return value.trim().split(/\s+/).filter(Boolean).length;
}

export function WritingStyleDialog({ value, onSave, disabled }: WritingStyleDialogProps) {
  const t = useTranslations();
  const generateVoice = useGenerateVoiceFromSent();
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState(value);
  const [saving, setSaving] = useState(false);

  function handleOpenChange(nextOpen: boolean) {
    if (nextOpen) {
      setDraft(value);
    }
    setOpen(nextOpen);
  }

  const wordCount = useMemo(() => countWords(draft), [draft]);

  async function handleGenerate() {
    const result = await generateVoice.mutateAsync();
    setDraft(result.generatedStyle ?? '');
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
      <DialogContent className="max-h-[calc(100svh-2rem)] overflow-y-auto sm:max-w-2xl lg:max-w-3xl">
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>{t('ai.voice.writingStyle.title')}</DialogTitle>
            <DialogDescription>{t('ai.voice.writingStyle.description')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="writing-style">{t('ai.voice.writingStyle.fieldLabel')}</Label>
            <Textarea
              id="writing-style"
              value={draft}
              onChange={(changeEvent) => setDraft(changeEvent.target.value)}
              placeholder={t('ai.voice.writingStyle.placeholder')}
              className="max-h-[42svh] min-h-44 overflow-y-auto"
              disabled={saving}
            />
            <p className="text-muted-foreground text-xs">
              {t('ai.voice.wordCount', { count: wordCount })}
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            onClick={handleGenerate}
            disabled={generateVoice.isPending || saving}
          >
            {t('ai.actions.generateFromSent')}
          </Button>
          {generateVoice.data?.generatedStyle === '' ? (
            <p className="text-muted-foreground text-sm">{t('ai.empty.sent.body')}</p>
          ) : null}
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
