'use client';

import { useTranslations } from 'next-intl';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';

// The literal confirmation phrase ("delete my data") is a typed-confirmation
// token, NOT user-facing prose, per UI-SPEC §"Destructive Confirmations".
// It stays hard-coded; the user-visible label/placeholder uses translation keys
// that explain "Type X to confirm" in vi/en.
const CONFIRM_PHRASE = 'delete my data';

export function DeleteAccountDialog({
  onConfirm,
  isPending = false,
}: {
  onConfirm: () => Promise<void>;
  isPending?: boolean;
}) {
  const t = useTranslations();
  const [v, setV] = useState('');

  return (
    <Dialog>
      <DialogTrigger
        render={(props) => (
          <Button {...props} variant="destructive">
            {t('settings.deleteAccount.cta')}
          </Button>
        )}
      />

      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('deleteAccount.title')}</DialogTitle>
        </DialogHeader>
        <p>{t('deleteAccount.body')}</p>
        <Input
          value={v}
          onChange={(e) => setV(e.target.value)}
          placeholder={t('deleteAccount.confirmInputPlaceholder')}
          aria-label={t('deleteAccount.confirmInputLabel')}
        />
        <Button
          variant="destructive"
          disabled={v !== CONFIRM_PHRASE || isPending}
          onClick={onConfirm}
        >
          {isPending ? t('common.loading') : t('deleteAccount.confirmCta')}
        </Button>
      </DialogContent>
    </Dialog>
  );
}
