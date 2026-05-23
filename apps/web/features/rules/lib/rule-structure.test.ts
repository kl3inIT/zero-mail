import { describe, expect, it } from 'vitest';

import {
  buildManualRule,
  MANUAL_ACTION_TYPES,
  manualDraftFromCompiledRule,
  summarizeActionIntents,
  type ManualAction,
  type ManualRuleDraft,
} from '@/features/rules/lib/rule-structure';

const allActions: ManualAction[] = [
  { id: 'action-1', type: 'label', value: 'Finance' },
  { id: 'action-2', type: 'archive', value: '' },
  { id: 'action-3', type: 'save_draft', value: 'Draft a polite reply' },
  { id: 'action-4', type: 'mark_read', value: '' },
  { id: 'action-5', type: 'star', value: '' },
  { id: 'action-6', type: 'add_to_digest', value: '' },
  { id: 'action-7', type: 'mark_spam', value: '' },
  { id: 'action-8', type: 'send_reply', value: 'Send a short acknowledgement' },
  {
    id: 'action-9',
    type: 'forward_email',
    value: 'ops@example.com',
    instruction: 'Forward with a short note',
  },
  {
    id: 'action-10',
    type: 'send_email',
    value: 'founder@example.com',
    cc: 'ops@example.com',
    subject: 'Investor update',
    body: 'Here is the update.',
  },
];

describe('rule-structure expanded action contract', () => {
  it('exposes every Phase 08.1 manual action type', () => {
    expect(MANUAL_ACTION_TYPES).toEqual([
      'label',
      'archive',
      'save_draft',
      'mark_read',
      'star',
      'add_to_digest',
      'mark_spam',
      'send_reply',
      'forward_email',
      'send_email',
    ]);
  });

  it('builds canonical action intent JSON for every manual action type', () => {
    const builtRule = buildManualRule(draftWithActions(allActions));

    expect(builtRule).not.toBeNull();
    const actionIntents = JSON.parse(builtRule?.compiled.actionIntents ?? '[]') as unknown[];

    expect(actionIntents).toEqual([
      { type: 'label', labelName: 'Finance' },
      { type: 'archive' },
      { type: 'save_draft', instruction: 'Draft a polite reply' },
      { type: 'mark_read' },
      { type: 'star' },
      { type: 'add_to_digest' },
      { type: 'mark_spam' },
      { type: 'send_reply', instruction: 'Send a short acknowledgement' },
      {
        type: 'forward_email',
        recipients: ['ops@example.com'],
        instruction: 'Forward with a short note',
      },
      {
        type: 'send_email',
        to: ['founder@example.com'],
        cc: ['ops@example.com'],
        subject: 'Investor update',
        body: 'Here is the update.',
      },
    ]);
  });

  it('parses compiled expanded action JSON back into manual rows', () => {
    const draft = manualDraftFromCompiledRule({
      displayName: 'Investor updates',
      matcherAst:
        '{"schemaVersion":"rules.v1","type":"GMAIL_LABEL_PRESENT","labelId":"Investor Update"}',
      actionIntents: JSON.stringify([
        { type: 'mark_read' },
        { type: 'forward_email', recipients: ['ops@example.com'], instruction: 'Forward' },
        {
          type: 'send_email',
          to: ['founder@example.com'],
          cc: ['ops@example.com'],
          subject: 'Investor update',
          body: 'Here is the update.',
        },
      ]),
    });

    expect(draft.actions).toEqual([
      { id: 'action-1', type: 'mark_read', value: '' },
      {
        id: 'action-2',
        type: 'forward_email',
        value: 'ops@example.com',
        instruction: 'Forward',
      },
      {
        id: 'action-3',
        type: 'send_email',
        value: 'founder@example.com',
        cc: 'ops@example.com',
        bcc: '',
        subject: 'Investor update',
        body: 'Here is the update.',
      },
    ]);
  });

  it('summarizes expanded action intents for review chips', () => {
    expect(
      summarizeActionIntents(
        JSON.stringify([
          { type: 'send_reply', instruction: 'Send a short acknowledgement' },
          { type: 'forward_email', recipients: ['ops@example.com'], instruction: 'Forward' },
          {
            type: 'send_email',
            to: ['founder@example.com'],
            subject: 'Investor update',
            body: 'Here is the update.',
          },
        ]),
        'fallback',
      ),
    ).toEqual([
      'send reply: Send a short acknowledgement',
      'forward to ops@example.com',
      'send email to founder@example.com: Investor update',
    ]);
  });
});

function draftWithActions(actions: ManualAction[]): ManualRuleDraft {
  return {
    displayName: 'Expanded actions',
    matchOperator: 'ALL',
    conditions: [{ id: 'condition-1', type: 'GMAIL_LABEL_PRESENT', value: 'Investor Update' }],
    actions,
  };
}
