'use client';

import { Plus } from 'lucide-react';
import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { EmptyState } from '@/components/states/EmptyState';
import { ErrorState } from '@/components/states/ErrorState';
import { LoadingState } from '@/components/states/LoadingState';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import type { KnowledgeSnippet } from '@/features/knowledge/api/knowledge-api';
import { KnowledgeDialog } from '@/features/knowledge/components/KnowledgeDialog';
import { KnowledgeRow } from '@/features/knowledge/components/KnowledgeRow';
import { useDeleteKnowledge } from '@/features/knowledge/hooks/useDeleteKnowledge';
import { useKnowledge } from '@/features/knowledge/hooks/useKnowledge';

export function KnowledgeTable() {
  const t = useTranslations();
  const knowledge = useKnowledge();
  const deleteKnowledge = useDeleteKnowledge();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingSnippet, setEditingSnippet] = useState<KnowledgeSnippet | null>(null);

  function openCreateDialog() {
    setEditingSnippet(null);
    setDialogOpen(true);
  }

  function openEditDialog(snippet: KnowledgeSnippet) {
    setEditingSnippet(snippet);
    setDialogOpen(true);
  }

  if (knowledge.isPending) {
    return <LoadingState variant="rows" count={3} />;
  }

  if (knowledge.isError) {
    return (
      <ErrorState
        heading={t('ai.toast.genericFailure')}
        body={t('ai.knowledge.title.description')}
        retryLabel={t('triage.audit.error.retry')}
        onRetry={() => void knowledge.refetch()}
      />
    );
  }

  const snippets = knowledge.data ?? [];

  return (
    <div className="space-y-3">
      {snippets.length > 0 ? (
        <div className="flex justify-end">
          <Button type="button" variant="outline" onClick={openCreateDialog}>
            <Plus className="size-4" aria-hidden="true" />
            {t('ai.actions.addSnippet')}
          </Button>
        </div>
      ) : null}
      {snippets.length === 0 ? (
        <EmptyState
          heading={t('ai.empty.knowledge.title')}
          body={t('ai.empty.knowledge.body')}
          cta={
            <Button
              type="button"
              onClick={() => {
                setEditingSnippet(null);
                setDialogOpen(true);
              }}
            >
              <Plus className="size-4" aria-hidden="true" />
              {t('ai.actions.addSnippet')}
            </Button>
          }
        />
      ) : (
        <div className="rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('ai.knowledge.table.title')}</TableHead>
                <TableHead>{t('ai.knowledge.table.lastUpdated')}</TableHead>
                <TableHead className="w-12 text-right">{t('ai.knowledge.table.edit')}</TableHead>
                <TableHead className="w-12 text-right">{t('ai.knowledge.table.delete')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {snippets.map((snippet) => (
                <KnowledgeRow
                  key={snippet.id}
                  snippet={snippet}
                  onEdit={openEditDialog}
                  onDelete={(id) => deleteKnowledge.mutateAsync(id)}
                />
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      {dialogOpen ? (
        <KnowledgeDialog open={dialogOpen} onOpenChange={setDialogOpen} snippet={editingSnippet} />
      ) : null}
    </div>
  );
}
