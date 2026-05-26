'use client';

import { Pencil, Trash2 } from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { TableCell, TableRow } from '@/components/ui/table';
import { ConfirmDialog } from '@/features/ai/components/ConfirmDialog';
import type { KnowledgeSnippet } from '@/features/knowledge/api/knowledge-api';

type KnowledgeRowProps = {
  snippet: KnowledgeSnippet;
  onEdit: (snippet: KnowledgeSnippet) => void;
  onDelete: (id: string) => void | Promise<void>;
};

export function KnowledgeRow({ snippet, onEdit, onDelete }: KnowledgeRowProps) {
  const t = useTranslations();
  const locale = useLocale();
  const updatedAt = new Intl.DateTimeFormat(locale === 'vi' ? 'vi-VN' : 'en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(snippet.updatedAt));

  return (
    <TableRow>
      <TableCell className="min-w-48 font-medium whitespace-normal">{snippet.title}</TableCell>
      <TableCell className="text-muted-foreground">{updatedAt}</TableCell>
      <TableCell className="w-12 text-right">
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          aria-label={t('ai.knowledge.table.edit')}
          onClick={() => onEdit(snippet)}
        >
          <Pencil className="size-4" aria-hidden="true" />
        </Button>
      </TableCell>
      <TableCell className="w-12 text-right">
        <ConfirmDialog
          title={t('ai.knowledge.table.delete')}
          description={t('ai.confirm.deleteKnowledge', { title: snippet.title })}
          confirmLabel={t('ai.actions.delete')}
          onConfirm={() => onDelete(snippet.id)}
          trigger={
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              aria-label={t('ai.knowledge.table.delete')}
            >
              <Trash2 className="size-4" aria-hidden="true" />
            </Button>
          }
        />
      </TableCell>
    </TableRow>
  );
}
