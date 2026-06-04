import { gmailThreadUrl } from './gmail-url';
import { asArray, asString, formatRelativeDate, getField } from './helpers';
import { InlineEmailCard } from './inline-email-card';
import { OpenInGmailLink } from './open-in-gmail-link';
import { SubtleToolCollapsible } from './subtle-tool-collapsible';

export function GetThreadResult({ input, output }: { input: unknown; output: unknown }) {
  const threadId = asString(getField(input, 'threadId')) ?? asString(getField(output, 'threadId'));
  const participants = asArray<string>(getField(output, 'participantList')) ?? [];
  const messageIds = asArray<string>(getField(output, 'messageIds')) ?? [];
  const lastActivityAt = asString(getField(output, 'lastActivityAt'));
  return (
    <SubtleToolCollapsible title={`Xem chuỗi · ${messageIds.length} email`} defaultOpen>
      {participants.length > 0 && (
        <p className="text-muted-foreground text-xs">Người tham gia: {participants.join(', ')}</p>
      )}
      {lastActivityAt && (
        <p className="text-muted-foreground text-xs">
          Hoạt động mới: {formatRelativeDate(lastActivityAt)}
        </p>
      )}
      {threadId && messageIds.length > 0 && (
        <div className="space-y-1.5">
          {messageIds.map((messageId, index) => (
            <InlineEmailCard
              key={messageId}
              email={{ messageId, threadId }}
              fallbackTitle={`Email ${index + 1}`}
            />
          ))}
        </div>
      )}
      {threadId && <OpenInGmailLink href={gmailThreadUrl(threadId)} label="Mở chuỗi trong Gmail" />}
    </SubtleToolCollapsible>
  );
}
