import { useTranslations } from 'next-intl';

import {
  type PreviewCardAction,
  textValue,
} from '@/features/chat/components/preview-card/preview-card-state';

export function RemoveSenderFromSafetyNetBody({ action }: { action: PreviewCardAction }) {
  const t = useTranslations('chat.preview');

  return (
    <div className="grid gap-2 text-sm">
      <p className="font-semibold">{t('removeSender')}</p>
      <p>
        {t('sender')}:{' '}
        {textValue(action.input.senderEmail) || textValue(action.input.senderEmailHash)}
      </p>
    </div>
  );
}
