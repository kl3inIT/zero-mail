'use client';

import { Trash2 } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import type { ProtectedSenderResponse } from '@/features/triage/api/triage-api';
import { useDeleteProtectedSender } from '@/features/triage/hooks/useDeleteProtectedSender';

type SenderRowProps = {
  sender: ProtectedSenderResponse;
};

export function SenderRow({ sender }: SenderRowProps) {
  const t = useTranslations();
  const deleteSender = useDeleteProtectedSender();
  const pattern = sender.pattern || sender.senderEmail || t('triage.senders.unknown');
  const canDelete = sender.createdByUser;

  const deleteButton = (
    <Button
      type="button"
      variant="ghost"
      size="icon-sm"
      aria-label={t('ai.actions.remove')}
      disabled={!canDelete || deleteSender.isPending}
      onClick={() => deleteSender.mutate(sender.id)}
    >
      <Trash2 className="size-4" aria-hidden="true" />
    </Button>
  );

  return (
    <div className="flex flex-col gap-3 p-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0 space-y-2">
        <p className="text-foreground truncate text-sm font-medium">{pattern}</p>
        <div className="flex flex-wrap gap-2">
          <Badge variant="outline">
            {sender.patternKind === 'DOMAIN'
              ? t('ai.safetyNet.kind.domain')
              : t('ai.safetyNet.kind.email')}
          </Badge>
          <Badge variant="outline">
            {sender.createdByUser
              ? t('ai.safetyNet.createdBy.user')
              : t('ai.safetyNet.createdBy.system')}
          </Badge>
        </div>
      </div>
      {canDelete ? (
        deleteButton
      ) : (
        <Tooltip>
          <TooltipTrigger render={<span className="inline-flex" />}>{deleteButton}</TooltipTrigger>
          <TooltipContent>{t('ai.safetyNet.deleteDisabled')}</TooltipContent>
        </Tooltip>
      )}
    </div>
  );
}
