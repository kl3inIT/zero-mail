import { useTranslations } from 'next-intl';

import { Textarea } from '@/components/ui/textarea';
import { CopyButton } from '@/features/chat/components/preview-card/copy-button';
import { OutsideSourceThreadBadge } from '@/features/chat/components/preview-card/outside-source-thread-badge';
import {
  type PreviewCardAction,
  type Recipient,
  textValue,
} from '@/features/chat/components/preview-card/preview-card-state';
import { gmailMessageUrl, gmailThreadUrl } from '@/features/chat/components/tool-results/gmail-url';
import { OpenInGmailLink } from '@/features/chat/components/tool-results/open-in-gmail-link';

function sourceThreadUrl(action: PreviewCardAction): string | undefined {
  const sourceMessageId = textValue(action.input.sourceMessageId);
  if (sourceMessageId) return gmailMessageUrl(sourceMessageId);
  const gmailThreadId = textValue(action.input.gmailThreadId);
  return gmailThreadId ? gmailThreadUrl(gmailThreadId) : undefined;
}

export function ForwardEmailBody({
  action,
  draftBody,
  recipients,
  editing,
  onOverrideChange,
}: {
  action: PreviewCardAction;
  draftBody: string;
  recipients: Recipient[];
  editing: boolean;
  onOverrideChange: (key: string, value: string) => void;
}) {
  const t = useTranslations('chat.preview');
  const sourceUrl = sourceThreadUrl(action);

  return (
    <div className="grid gap-3 text-sm">
      <div className="bg-muted text-muted-foreground flex items-center justify-between gap-2 rounded-md px-3 py-2 text-xs">
        <span>{t('sourceThread')}</span>
        {sourceUrl && <OpenInGmailLink href={sourceUrl} />}
      </div>
      <div className="grid gap-1">
        <p className="text-muted-foreground text-xs font-medium uppercase">{t('to')}</p>
        <div className="grid gap-1">
          {recipients.map((recipient) => (
            <div key={recipient.email} className="flex flex-wrap items-center gap-2">
              <span className="font-semibold">{recipient.email}</span>
              {recipient.outsideSourceThread && <OutsideSourceThreadBadge />}
            </div>
          ))}
        </div>
      </div>
      <div className="grid gap-1">
        <p className="text-muted-foreground text-xs font-medium uppercase">{t('body')}</p>
        {editing ? (
          <Textarea
            className="min-h-32"
            defaultValue={draftBody}
            onChange={(event) => onOverrideChange('additionalBody', event.currentTarget.value)}
          />
        ) : (
          <div className="space-y-1">
            <p className="leading-relaxed whitespace-pre-wrap">{draftBody}</p>
            {draftBody && <CopyButton text={draftBody} />}
          </div>
        )}
      </div>
    </div>
  );
}
