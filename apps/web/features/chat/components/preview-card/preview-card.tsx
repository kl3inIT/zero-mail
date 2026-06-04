'use client';

import { Check, Loader2, Pencil, X } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useEffect, useRef, useState } from 'react';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { useCancelAction, useConfirmAction } from '@/features/chat/hooks/use-confirm-action';
import { BulkArchiveBody } from './body/bulk-archive-body';
import { CreateRuleBody } from './body/create-rule-body';
import { DeleteRuleBody } from './body/delete-rule-body';
import { ForwardEmailBody } from './body/forward-email-body';
import { RemoveSenderFromSafetyNetBody } from './body/remove-sender-from-safety-net-body';
import { ReplyEmailBody } from './body/reply-email-body';
import { SaveMemoryBody } from './body/save-memory-body';
import { SendEmailBody } from './body/send-email-body';
import { UpdatePersonalInstructionsBody } from './body/update-personal-instructions-body';
import {
  type BodySlotToolName,
  BODY_SLOT_TOOL_NAMES,
  type PreviewCardAction,
  usePreviewCardState,
} from './preview-card-state';
import { VipBanner } from './vip-banner';

type BodySlotProps = {
  action: PreviewCardAction;
  draftBody: string;
  editing: boolean;
  onOverrideChange: (key: string, value: string) => void;
  recipients: ReturnType<typeof usePreviewCardState>['recipients'];
};

export const BODY_SLOT_MAP: Record<BodySlotToolName, (props: BodySlotProps) => React.ReactNode> = {
  createRule: ({ action, editing, onOverrideChange }) => (
    <CreateRuleBody action={action} editing={editing} onOverrideChange={onOverrideChange} />
  ),
  deleteRule: ({ action }) => <DeleteRuleBody action={action} />,
  removeSenderFromSafetyNet: ({ action }) => <RemoveSenderFromSafetyNetBody action={action} />,
  bulkArchive: ({ action }) => <BulkArchiveBody action={action} />,
  saveMemory: ({ action, editing, onOverrideChange }) => (
    <SaveMemoryBody action={action} editing={editing} onOverrideChange={onOverrideChange} />
  ),
  updatePersonalInstructions: ({ action, editing, onOverrideChange }) => (
    <UpdatePersonalInstructionsBody
      action={action}
      editing={editing}
      onOverrideChange={onOverrideChange}
    />
  ),
  sendEmail: ({ action, draftBody, editing, onOverrideChange }) => (
    <SendEmailBody
      action={action}
      draftBody={draftBody}
      editing={editing}
      onOverrideChange={onOverrideChange}
    />
  ),
  replyEmail: ({ action, draftBody, editing, onOverrideChange, recipients }) => (
    <ReplyEmailBody
      action={action}
      draftBody={draftBody}
      editing={editing}
      onOverrideChange={onOverrideChange}
      recipients={recipients}
    />
  ),
  forwardEmail: ({ action, draftBody, editing, onOverrideChange, recipients }) => (
    <ForwardEmailBody
      action={action}
      draftBody={draftBody}
      editing={editing}
      onOverrideChange={onOverrideChange}
      recipients={recipients}
    />
  ),
};

type PreviewCtaKey =
  | 'createRule'
  | 'deleteRule'
  | 'removeSender'
  | 'bulkArchive'
  | 'saveMemory'
  | 'updateInstructions'
  | 'send';

// Only these body slots render editable fields when `editing` is toggled. The rest
// (deleteRule, removeSenderFromSafetyNet, bulkArchive) are summary-only, so showing an Edit
// button there is a dead control. createRule lets the user edit the rule name and the "When"
// text; changing the text recompiles on confirm (backend).
const EDITABLE_KINDS = new Set<BodySlotToolName>([
  'createRule',
  'saveMemory',
  'updatePersonalInstructions',
  'sendEmail',
  'replyEmail',
  'forwardEmail',
]);

function ctaKey(kind: BodySlotToolName): PreviewCtaKey {
  switch (kind) {
    case 'createRule':
      return 'createRule';
    case 'deleteRule':
      return 'deleteRule';
    case 'removeSenderFromSafetyNet':
      return 'removeSender';
    case 'bulkArchive':
      return 'bulkArchive';
    case 'saveMemory':
      return 'saveMemory';
    case 'updatePersonalInstructions':
      return 'updateInstructions';
    default:
      return 'send';
  }
}

export function PreviewCard({ action }: { action: PreviewCardAction }) {
  const t = useTranslations('chat.preview');
  const confirmAction = useConfirmAction();
  const cancelAction = useCancelAction();
  const [editing, setEditing] = useState(false);
  const [vipAcknowledged, setVipAcknowledged] = useState(false);
  const [contentOverride, setContentOverride] = useState<Record<string, unknown>>({});
  const [localState, setLocalState] = useState<string | null>(null);
  const confirmInFlightRef = useRef(false);
  const cancelInFlightRef = useRef(false);
  const computed = usePreviewCardState({
    action: { ...action, state: localState ?? action.state },
    vipAcknowledged,
    submitting: confirmAction.isPending || cancelAction.isPending,
  });
  const titleId = `preview-card-${action.toolCallId}`;
  const bodySlot = BODY_SLOT_MAP[action.kind];
  const cta = t(ctaKey(action.kind));

  function handleOverrideChange(key: string, value: string) {
    setContentOverride((previous) => ({ ...previous, [key]: value }));
  }

  async function handleConfirm() {
    if (confirmInFlightRef.current || !computed.sendEnabled) return;
    confirmInFlightRef.current = true;
    try {
      const response = await confirmAction.mutateAsync({
        chatId: action.chatId,
        invalidatesKnowledge: action.kind === 'saveMemory',
        body: {
          toolCallId: action.toolCallId,
          contentOverride,
          vipAcknowledged,
        },
      });
      setLocalState(response.state);
      setEditing(false);
    } finally {
      confirmInFlightRef.current = false;
    }
  }

  useEffect(() => {
    if (!action.autoConfirm) return;
    if (confirmInFlightRef.current) return;
    if (computed.status !== 'pending') return;
    if (!computed.sendEnabled) return;
    void handleConfirm();
    // handleConfirm is intentionally not in the dep array — confirmInFlightRef and sendEnabled gate re-entry.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [action.autoConfirm, computed.status, computed.sendEnabled]);

  if (action.autoConfirm && computed.status === 'pending') {
    return null;
  }

  async function handleCancel() {
    if (cancelInFlightRef.current || cancelAction.isPending) return;
    cancelInFlightRef.current = true;
    try {
      const response = await cancelAction.mutateAsync({
        chatId: action.chatId,
        body: {
          toolCallId: action.toolCallId,
          contentOverride: {},
          vipAcknowledged: false,
        },
      });
      setLocalState(response.state);
      setEditing(false);
    } finally {
      cancelInFlightRef.current = false;
    }
  }

  return (
    <Card
      className="border-border bg-card max-w-[680px] overflow-hidden rounded-lg shadow-sm"
      data-testid={`preview-card-${action.kind}`}
      role="region"
      aria-labelledby={titleId}
    >
      <CardHeader className="border-border space-y-3 border-b px-4 py-3">
        <div className="flex items-start justify-between gap-3">
          <div className="space-y-1">
            <p className="text-muted-foreground font-mono text-xs font-medium uppercase">
              {t('eyebrow')}
            </p>
            <CardTitle id={titleId} className="text-[17px] font-semibold tracking-normal">
              {t('title')}
            </CardTitle>
          </div>
          {computed.status !== 'pending' && (
            <span className="bg-accent-soft text-accent-foreground inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-medium">
              <Check className="size-3" />
              {computed.status === 'sent'
                ? t('sent')
                : computed.status === 'cancelled'
                  ? t('cancelled')
                  : computed.status === 'failed'
                    ? t('failed')
                    : t('confirmed')}
            </span>
          )}
        </div>
        {computed.vipRequired && computed.status === 'pending' && (
          <VipBanner checked={vipAcknowledged} onCheckedChange={setVipAcknowledged} />
        )}
      </CardHeader>
      <CardContent className="space-y-4 p-4">
        {bodySlot({
          action,
          draftBody: computed.draftBody,
          editing,
          onOverrideChange: handleOverrideChange,
          recipients: computed.recipients,
        })}
        {confirmAction.isError && <p className="text-destructive text-sm">{t('actionError')}</p>}
      </CardContent>
      {computed.status === 'pending' && (
        <CardFooter className="border-border flex items-center justify-between gap-3 border-t px-4 py-3">
          {EDITABLE_KINDS.has(action.kind) ? (
            <Button type="button" variant="ghost" onClick={() => setEditing((current) => !current)}>
              <Pencil className="size-4" />
              {t('edit')}
            </Button>
          ) : (
            <span />
          )}
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={handleCancel}
              disabled={cancelAction.isPending}
            >
              <X className="size-4" />
              {t('cancel')}
            </Button>
            <Button
              type="button"
              onClick={handleConfirm}
              disabled={!computed.sendEnabled}
              aria-disabled={!computed.sendEnabled}
              data-testid="preview-send"
              variant={computed.isDestructive ? 'destructive' : 'default'}
            >
              {(confirmAction.isPending || !action.persistenceConfirmed) && (
                <Loader2 className="size-4 animate-spin" />
              )}
              {cta}
            </Button>
          </div>
        </CardFooter>
      )}
      {computed.status === 'pending' && !action.persistenceConfirmed && (
        <p className="text-muted-foreground px-4 pb-3 text-xs">{t('syncing')}</p>
      )}
    </Card>
  );
}

export const previewCardBodySlotCount = BODY_SLOT_TOOL_NAMES.length;
