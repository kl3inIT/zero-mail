import { useTranslations } from 'next-intl';

import {
  type PreviewCardAction,
  textValue,
} from '@/features/chat/components/preview-card/preview-card-state';

export function DeleteRuleBody({ action }: { action: PreviewCardAction }) {
  const t = useTranslations('chat.preview');
  const ruleName = textValue(action.input.displayName) || textValue(action.input.ruleName);

  return (
    <div className="border-destructive/20 bg-red-soft/40 text-foreground grid gap-2 rounded-md border p-3 text-sm">
      <p className="font-semibold">{t('deleteRule')}</p>
      <p>
        {t('ruleName')}: {ruleName || textValue(action.input.ruleId)}
      </p>
    </div>
  );
}
