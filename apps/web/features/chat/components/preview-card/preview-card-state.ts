'use client';

import { useMemo } from 'react';

export const CHAT_TOOL_PARTITIONS = {
  read: [
    'searchInbox',
    'getMessage',
    'listLabels',
    'getThread',
    'getRule',
    'listRules',
    'getSenderSafetyEntry',
    'searchMemories',
  ],
  writeReversible: [
    'applyLabel',
    'removeLabel',
    'archiveThread',
    'updateRule',
    'disableRule',
    'saveDraft',
  ],
  confirmRequired: [
    'createRule',
    'deleteRule',
    'removeSenderFromSafetyNet',
    'bulkArchive',
    'saveMemory',
    'updatePersonalInstructions',
  ],
  confirmedSend: ['sendEmail', 'replyEmail', 'forwardEmail'],
} as const;

export const CHAT_TOOL_NAMES = [
  ...CHAT_TOOL_PARTITIONS.read,
  ...CHAT_TOOL_PARTITIONS.writeReversible,
  ...CHAT_TOOL_PARTITIONS.confirmRequired,
  ...CHAT_TOOL_PARTITIONS.confirmedSend,
] as const;

export const BODY_SLOT_TOOL_NAMES = [
  ...CHAT_TOOL_PARTITIONS.confirmRequired,
  ...CHAT_TOOL_PARTITIONS.confirmedSend,
] as const;

export type ChatToolName = (typeof CHAT_TOOL_NAMES)[number];
export type BodySlotToolName = (typeof BODY_SLOT_TOOL_NAMES)[number];

export type Recipient = {
  email: string;
  outsideSourceThread?: boolean;
};

export type PreviewCardAction = {
  kind: BodySlotToolName;
  chatId: string;
  messageId: string;
  toolCallId: string;
  state?: string;
  input: Record<string, unknown>;
  output?: Record<string, unknown>;
  confirmation?: Record<string, unknown>;
  persistenceConfirmed: boolean;
  autoConfirm?: boolean;
};

export type PreviewCardStatus = 'pending' | 'confirmed' | 'sent' | 'cancelled' | 'failed';

export type PreviewCardComputedState = {
  status: PreviewCardStatus;
  sendEnabled: boolean;
  isTerminal: boolean;
  isSendTool: boolean;
  isDestructive: boolean;
  vipRequired: boolean;
  draftBody: string;
  recipients: Recipient[];
};

export function isBodySlotToolName(toolName: string): toolName is BodySlotToolName {
  return (BODY_SLOT_TOOL_NAMES as readonly string[]).includes(toolName);
}

export function isWriteReversibleToolName(toolName: string): boolean {
  return (CHAT_TOOL_PARTITIONS.writeReversible as readonly string[]).includes(toolName);
}

export function textValue(value: unknown): string {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (value && typeof value === 'object' && 'value' in value) {
    return textValue((value as { value?: unknown }).value);
  }
  return '';
}

export function optionalRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

export function parseMaybeJsonObject(value: unknown): Record<string, unknown> {
  if (typeof value === 'string') {
    try {
      return optionalRecord(JSON.parse(value) as unknown);
    } catch {
      return {};
    }
  }
  return optionalRecord(value);
}

export function recipientList(value: unknown): Recipient[] {
  if (Array.isArray(value)) {
    return value
      .map((entry) => {
        if (typeof entry === 'string') return { email: entry };
        const record = optionalRecord(entry);
        const email = textValue(record.email ?? record.address ?? record.value);
        if (!email) return null;
        return {
          email,
          outsideSourceThread: Boolean(record.outsideSourceThread ?? record.outside_source_thread),
        };
      })
      .filter((recipient): recipient is Recipient => Boolean(recipient));
  }

  return textValue(value)
    .split(',')
    .map((email) => email.trim())
    .filter(Boolean)
    .map((email) => ({ email }));
}

export function actionRecipients(action: PreviewCardAction): Recipient[] {
  const input = action.input;
  const recipients =
    recipientList(input.toRecipients).length > 0
      ? recipientList(input.toRecipients)
      : recipientList(input.to);
  const outside = new Set(
    recipientList(input.outsideSourceThreadRecipients).map((recipient) => recipient.email),
  );
  return recipients.map((recipient) => ({
    ...recipient,
    outsideSourceThread:
      recipient.outsideSourceThread ||
      outside.has(recipient.email) ||
      Boolean(input.outsideSourceThread ?? input.outside_source_thread),
  }));
}

export function actionDraftBody(action: PreviewCardAction): string {
  return (
    textValue(action.input.body) ||
    textValue(action.input.draftBody) ||
    textValue(action.input.additionalBody) ||
    textValue(action.confirmation?.draftBody) ||
    textValue(action.confirmation?.body)
  );
}

export function actionStatus(action: PreviewCardAction): PreviewCardStatus {
  const state = `${action.state ?? action.output?.state ?? action.confirmation?.state ?? ''}`
    .toLowerCase()
    .replaceAll('_', '-');
  if (state.includes('cancel')) return 'cancelled';
  if (state.includes('fail') || state.includes('error')) return 'failed';
  if (state.includes('sent')) return 'sent';
  if (
    state.includes('confirm') ||
    state.includes('complete') ||
    state.includes('output-available')
  ) {
    return action.kind === 'sendEmail' ||
      action.kind === 'replyEmail' ||
      action.kind === 'forwardEmail'
      ? 'sent'
      : 'confirmed';
  }
  if (action.output && Object.keys(action.output).length > 0) {
    return action.kind === 'sendEmail' ||
      action.kind === 'replyEmail' ||
      action.kind === 'forwardEmail'
      ? 'sent'
      : 'confirmed';
  }
  return 'pending';
}

export function usePreviewCardState({
  action,
  vipAcknowledged,
  submitting,
}: {
  action: PreviewCardAction;
  vipAcknowledged: boolean;
  submitting: boolean;
}): PreviewCardComputedState {
  return useMemo(() => {
    const status = actionStatus(action);
    const isTerminal = status !== 'pending';
    const isSendTool =
      action.kind === 'sendEmail' || action.kind === 'replyEmail' || action.kind === 'forwardEmail';
    const vipRequired = Boolean(
      action.input.vipRequired ??
      action.input.vip ??
      action.confirmation?.vipRequired ??
      action.confirmation?.vip,
    );

    return {
      status,
      isTerminal,
      isSendTool,
      isDestructive:
        action.kind === 'deleteRule' ||
        action.kind === 'removeSenderFromSafetyNet' ||
        action.kind === 'bulkArchive',
      vipRequired,
      draftBody: actionDraftBody(action),
      recipients: actionRecipients(action),
      sendEnabled:
        action.persistenceConfirmed &&
        !isTerminal &&
        !submitting &&
        (!vipRequired || vipAcknowledged),
    };
  }, [action, submitting, vipAcknowledged]);
}
