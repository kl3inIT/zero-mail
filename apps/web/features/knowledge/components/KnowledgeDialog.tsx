'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import type { KnowledgeSnippet } from '@/features/knowledge/api/knowledge-api';
import { useCreateKnowledge } from '@/features/knowledge/hooks/useCreateKnowledge';
import { useUpdateKnowledge } from '@/features/knowledge/hooks/useUpdateKnowledge';
import { useLocalizedApiError, type ApiError } from '@/lib/api/errors';

type KnowledgeDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  snippet?: KnowledgeSnippet | null;
};

function maybeApiError(error: unknown): ApiError | undefined {
  return error &&
    typeof error === 'object' &&
    typeof (error as { code?: unknown }).code === 'string'
    ? (error as ApiError)
    : undefined;
}

export function KnowledgeDialog({ open, onOpenChange, snippet }: KnowledgeDialogProps) {
  const t = useTranslations();
  const localizeApiError = useLocalizedApiError();
  const createKnowledge = useCreateKnowledge();
  const updateKnowledge = useUpdateKnowledge();
  const [title, setTitle] = useState(snippet?.title ?? '');
  const [content, setContent] = useState(snippet?.content ?? '');
  const [formError, setFormError] = useState<string | null>(null);

  const editing = Boolean(snippet);
  const busy = createKnowledge.isPending || updateKnowledge.isPending;

  async function handleSubmit(formEvent: React.FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    setFormError(null);
    try {
      if (snippet) {
        await updateKnowledge.mutateAsync({ id: snippet.id, body: { title, content } });
      } else {
        await createKnowledge.mutateAsync({ title, content });
      }
      onOpenChange(false);
    } catch (mutationError) {
      const apiError = maybeApiError(mutationError);
      setFormError(apiError ? localizeApiError(apiError) : t('ai.toast.genericFailure'));
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={handleSubmit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>
              {editing ? t('ai.knowledge.dialog.editTitle') : t('ai.knowledge.dialog.addTitle')}
            </DialogTitle>
            <DialogDescription>{t('ai.knowledge.title.description')}</DialogDescription>
          </DialogHeader>

          <div className="space-y-3">
            <div className="space-y-2">
              <Label htmlFor="knowledge-title">{t('ai.knowledge.title.label')}</Label>
              {formError ? <p className="text-destructive text-sm">{formError}</p> : null}
              <Input
                id="knowledge-title"
                value={title}
                onChange={(changeEvent) => setTitle(changeEvent.target.value)}
                disabled={busy}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="knowledge-content">{t('ai.knowledge.content.label')}</Label>
              <Textarea
                id="knowledge-content"
                value={content}
                onChange={(changeEvent) => setContent(changeEvent.target.value)}
                disabled={busy}
                required
                className="min-h-32"
              />
            </div>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={busy}
            >
              {t('ai.actions.cancel')}
            </Button>
            <Button
              type="submit"
              disabled={busy || title.trim().length === 0 || content.trim().length === 0}
            >
              {t('ai.actions.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
