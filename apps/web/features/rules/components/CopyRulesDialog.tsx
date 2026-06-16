'use client';

import { useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Copy, Loader2 } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import type { MailboxSummary } from '@/features/mailbox/api/mailbox-api';
import { useActiveMailbox } from '@/features/mailbox/hooks/useActiveMailbox';
import { useMailboxList } from '@/features/mailbox/hooks/useMailboxList';
import { copyRules } from '@/features/rules/api/rules-api';
import { rulesKeys } from '@/features/rules/query-keys';

function mailboxLabel(mailbox: MailboxSummary): string {
  return mailbox.displayPurpose?.trim() || mailbox.googleEmail;
}

export function CopyRulesDialog() {
  const t = useTranslations();
  const queryClient = useQueryClient();
  const mailboxList = useMailboxList();
  const activeMailbox = useActiveMailbox();
  const [open, setOpen] = useState(false);
  const activeMailboxId = activeMailbox.data?.gmailConnectionId ?? null;
  const sourceMailboxes = useMemo(
    () =>
      (mailboxList.data ?? []).filter(
        (mailbox) =>
          mailbox.gmailConnectionId !== activeMailboxId && mailbox.status === 'CONNECTED',
      ),
    [activeMailboxId, mailboxList.data],
  );
  const [sourceMailboxId, setSourceMailboxId] = useState('');
  const selectedSourceMailboxId = sourceMailboxId || sourceMailboxes[0]?.gmailConnectionId || '';
  const selectedSourceMailbox = sourceMailboxes.find(
    (mailbox) => mailbox.gmailConnectionId === selectedSourceMailboxId,
  );
  const targetLabel = activeMailbox.data?.email ?? activeMailbox.data?.displayPurpose ?? '';
  const canCopy = Boolean(activeMailboxId && selectedSourceMailboxId);

  const copyRulesMutation = useMutation({
    mutationFn: async () => {
      if (!activeMailboxId || !selectedSourceMailboxId) {
        throw new Error('copy rules requires source and target mailboxes');
      }
      return copyRules({
        sourceGmailConnectionId: selectedSourceMailboxId,
        targetGmailConnectionId: activeMailboxId,
      });
    },
    onSuccess: async () => {
      setOpen(false);
      await queryClient.invalidateQueries({ queryKey: rulesKeys.all });
    },
    meta: {
      successMessage: t('rules.copy.success'),
      errorMessage: t('errors.rules.copy.generic'),
    },
  });

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button type="button" variant="outline" size="sm" />}>
        <Copy className="size-4" aria-hidden="true" />
        <span data-testid="copy-rules-button">{t('rules.copy.button')}</span>
      </DialogTrigger>
      <DialogContent>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (canCopy) copyRulesMutation.mutate();
          }}
        >
          <DialogHeader>
            <DialogTitle>{t('rules.copy.title')}</DialogTitle>
            <DialogDescription>{t('rules.copy.body')}</DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="copy-rules-source-mailbox">
                {t('rules.copy.sourceLabel')}
              </label>
              <Select
                value={selectedSourceMailboxId}
                onValueChange={(value) => setSourceMailboxId(value ?? '')}
                disabled={sourceMailboxes.length === 0 || copyRulesMutation.isPending}
              >
                <SelectTrigger id="copy-rules-source-mailbox" className="w-full">
                  <SelectValue>
                    {selectedSourceMailbox
                      ? mailboxLabel(selectedSourceMailbox)
                      : t('rules.copy.sourcePlaceholder')}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent align="start">
                  {sourceMailboxes.map((mailbox) => (
                    <SelectItem key={mailbox.gmailConnectionId} value={mailbox.gmailConnectionId}>
                      {mailboxLabel(mailbox)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {sourceMailboxes.length === 0 && (
                <p className="text-muted-foreground text-xs">{t('rules.copy.empty')}</p>
              )}
            </div>

            <div className="bg-muted/40 rounded-lg border px-3 py-2 text-sm">
              <p className="text-muted-foreground text-xs font-medium">
                {t('rules.copy.activeMailbox')}
              </p>
              <p className="text-foreground mt-1 truncate font-medium">
                {targetLabel || t('rules.copy.sourcePlaceholder')}
              </p>
            </div>
          </div>

          <DialogFooter>
            <DialogClose render={<Button type="button" variant="outline" />}>
              {t('rules.copy.cancel')}
            </DialogClose>
            <Button type="submit" disabled={!canCopy || copyRulesMutation.isPending}>
              {copyRulesMutation.isPending && (
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              )}
              {t('rules.copy.confirm')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
