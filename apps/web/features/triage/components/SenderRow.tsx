'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { ProtectedSenderResponse } from '@/features/triage/api/triage-api';
import { useOptInSender } from '@/features/triage/hooks/useOptInSender';

type SenderRowProps = {
  sender: ProtectedSenderResponse;
};

export function SenderRow({ sender }: SenderRowProps) {
  const t = useTranslations();
  const optIn = useOptInSender();
  const senderEmail = sender.senderEmail ?? t('triage.senders.unknown');
  const [locallyOptedIn, setLocallyOptedIn] = useState(false);
  const optedIn = Boolean(sender.optedIn) || locallyOptedIn;

  return (
    <div className="flex flex-col gap-3 px-3 py-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0 space-y-1">
        <p className="text-foreground truncate text-sm font-medium">{senderEmail}</p>
        {optedIn ? (
          <Badge variant="outline" className="border-accent bg-accent text-accent-foreground">
            {t('triage.senders.optedIn')}
          </Badge>
        ) : null}
      </div>
      <Button
        type="button"
        variant={optedIn ? 'outline' : 'default'}
        disabled={optedIn || optIn.isPending || !sender.senderEmail}
        onClick={() => {
          if (!sender.senderEmail) return;
          optIn.mutate(sender.senderEmail, { onSuccess: () => setLocallyOptedIn(true) });
        }}
      >
        {optedIn ? t('triage.senders.optedIn') : t('triage.senders.optIn')}
      </Button>
    </div>
  );
}
