import type { ReactNode } from 'react';

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
  BODY_SLOT_TOOL_NAMES,
  type BodySlotToolName,
  type PreviewCardAction,
  type PreviewCardComputedState,
} from './preview-card-state';

export type BodySlotProps = {
  action: PreviewCardAction;
  draftBody: string;
  editing: boolean;
  onOverrideChange: (key: string, value: string) => void;
  recipients: PreviewCardComputedState['recipients'];
};

export const BODY_SLOT_MAP: Record<BodySlotToolName, (props: BodySlotProps) => ReactNode> = {
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

export const previewCardBodySlotCount = BODY_SLOT_TOOL_NAMES.length;
