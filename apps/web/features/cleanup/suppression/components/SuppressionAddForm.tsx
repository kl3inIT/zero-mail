'use client';

import { Loader2 } from 'lucide-react';
import { FormEvent, useState } from 'react';
import { useTranslations } from 'next-intl';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAddSuppression } from '@/features/cleanup/suppression/hooks/useAddSuppression';
import { cn } from '@/lib/utils';

const EMAIL_OR_DOMAIN_PATTERN =
  /^([\w!#$%&'*+/=?`{|}~^.-]+@[\w.-]+\.[A-Za-z]{2,}|[\w.-]+\.[A-Za-z]{2,})$/;

export function SuppressionAddForm() {
  const t = useTranslations();
  const add = useAddSuppression();
  const [value, setValue] = useState('');
  const [clientError, setClientError] = useState<string | null>(null);

  function handleSubmit(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    const trimmed = value.trim();
    if (!EMAIL_OR_DOMAIN_PATTERN.test(trimmed)) {
      setClientError(t('cleanup.suppression.err.invalid'));
      return;
    }
    setClientError(null);
    add.mutate(
      { senderEmailOrDomain: trimmed },
      {
        onSuccess: () => {
          setValue('');
        },
      },
    );
  }

  return (
    <form className="flex flex-col gap-2 md:flex-row md:items-start" onSubmit={handleSubmit}>
      <div className="flex flex-1 flex-col gap-1">
        <label htmlFor="suppression-input" className="sr-only">
          {t('cleanup.suppression.input.label')}
        </label>
        <Input
          id="suppression-input"
          aria-label={t('cleanup.suppression.input.label')}
          placeholder={t('cleanup.suppression.input.placeholder')}
          value={value}
          onChange={(changeEvent) => {
            setValue(changeEvent.target.value);
            if (clientError) setClientError(null);
          }}
          className={cn(clientError && 'border-destructive')}
          aria-invalid={clientError ? true : undefined}
        />
        {clientError ? (
          <p className="text-destructive text-xs">{clientError}</p>
        ) : (
          <p className="text-muted-foreground text-xs">{t('cleanup.suppression.helper')}</p>
        )}
      </div>
      <Button type="submit" disabled={add.isPending || value.trim().length === 0}>
        {add.isPending ? <Loader2 className="animate-spin" /> : null}
        {t('cleanup.suppression.add')}
      </Button>
    </form>
  );
}
