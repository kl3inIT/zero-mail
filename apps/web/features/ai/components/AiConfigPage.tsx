'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import { Plus, Send } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import {
  useRuleAutomationSettings,
  useUpdateRuleAutomationSettings,
} from '@/features/rules/hooks/use-rule-automation-settings';
import { SenderSafetyNetList } from '@/features/triage/components/SenderSafetyNetList';
import { useOptInSender } from '@/features/triage/hooks/useOptInSender';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function AiConfigPage() {
  const t = useTranslations();
  const [senderEmail, setSenderEmail] = useState('');
  const optInMutation = useOptInSender();
  const automationSettings = useRuleAutomationSettings();
  const updateAutomationSettings = useUpdateRuleAutomationSettings();
  const autoSendRulesEnabled = automationSettings.data?.autoSendRulesEnabled ?? true;

  function handleAddSender(formEvent: React.FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    const trimmed = senderEmail.trim().toLowerCase();
    if (!EMAIL_PATTERN.test(trimmed)) {
      toast.error(t('ai.senders.invalidEmail'));
      return;
    }
    optInMutation.mutate(trimmed, {
      onSuccess: () => {
        toast.success(t('ai.senders.added', { email: trimmed }));
        setSenderEmail('');
      },
      onError: () => {
        toast.error(t('ai.senders.addFailed'));
      },
    });
  }

  return (
    <div className="space-y-6">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">AI configuration</h1>
        <p className="text-muted-foreground text-sm">
          Manage auto-send rules and sender safety controls.
        </p>
      </header>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Send className="text-muted-foreground size-4" aria-hidden="true" />
            {t('rules.settings.autoSend.title')}
          </CardTitle>
          <CardDescription>
            {autoSendRulesEnabled
              ? t('rules.settings.autoSend.bodyOn')
              : t('rules.settings.autoSend.bodyOff')}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex items-center justify-between gap-4">
          <span className="text-foreground text-sm font-medium">
            {t('rules.settings.autoSend.toggleLabel')}
          </span>
          <Switch
            checked={autoSendRulesEnabled}
            aria-label={t('rules.settings.autoSend.toggleLabel')}
            disabled={automationSettings.isLoading || updateAutomationSettings.isPending}
            onCheckedChange={(enabled) => updateAutomationSettings.mutate(enabled)}
            className="data-unchecked:bg-warning/80"
            data-testid="ai-auto-send-rules-switch"
          />
        </CardContent>
        <CardFooter>
          <p className="text-muted-foreground text-xs">
            {autoSendRulesEnabled
              ? t('rules.settings.autoSend.footerOn')
              : t('rules.settings.autoSend.footerOff')}
          </p>
        </CardFooter>
      </Card>

      <form className="flex flex-col gap-2 sm:flex-row sm:items-center" onSubmit={handleAddSender}>
        <Input
          type="email"
          value={senderEmail}
          onChange={(changeEvent) => setSenderEmail(changeEvent.target.value)}
          placeholder={t('ai.senders.inputPlaceholder')}
          aria-label={t('ai.senders.inputLabel')}
          className="sm:max-w-md"
          disabled={optInMutation.isPending}
        />
        <Button
          type="submit"
          disabled={optInMutation.isPending || senderEmail.trim().length === 0}
          className="gap-1.5"
        >
          <Plus className="size-4" aria-hidden="true" />
          {optInMutation.isPending ? t('ai.senders.adding') : t('ai.senders.add')}
        </Button>
      </form>

      <SenderSafetyNetList />
    </div>
  );
}
