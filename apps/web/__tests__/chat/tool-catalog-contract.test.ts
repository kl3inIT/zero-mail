import { describe, expect, it } from 'vitest';

import {
  BODY_SLOT_TOOL_NAMES,
  CHAT_TOOL_NAMES,
  CHAT_TOOL_PARTITIONS,
} from '@/features/chat/components/preview-card/preview-card-state';
import {
  BODY_SLOT_MAP,
  previewCardBodySlotCount,
} from '@/features/chat/components/preview-card/preview-card';

describe('chat tool catalog contract', () => {
  it('matches the Phase 7 24-tool authoritative list and partition', () => {
    expect(CHAT_TOOL_NAMES).toEqual([
      'searchInbox',
      'getMessage',
      'listLabels',
      'getThread',
      'getRule',
      'listRules',
      'getSenderSafetyEntry',
      'searchMemories',
      'applyLabel',
      'removeLabel',
      'archiveThread',
      'updateRule',
      'disableRule',
      'saveDraft',
      'addToKnowledgeBase',
      'createRule',
      'deleteRule',
      'removeSenderFromSafetyNet',
      'bulkArchive',
      'saveMemory',
      'updatePersonalInstructions',
      'sendEmail',
      'replyEmail',
      'forwardEmail',
    ]);
    expect(CHAT_TOOL_NAMES).toHaveLength(24);
    expect(CHAT_TOOL_PARTITIONS.read).toHaveLength(8);
    expect(CHAT_TOOL_PARTITIONS.writeReversible).toHaveLength(7);
    expect(CHAT_TOOL_PARTITIONS.confirmRequired).toHaveLength(6);
    expect(CHAT_TOOL_PARTITIONS.confirmedSend).toHaveLength(3);
    expect(CHAT_TOOL_PARTITIONS.confirmRequired).toContain('createRule');
  });

  it('maps exactly the nine user-confirmable tools to preview body slots', () => {
    expect(BODY_SLOT_TOOL_NAMES).toEqual([
      'createRule',
      'deleteRule',
      'removeSenderFromSafetyNet',
      'bulkArchive',
      'saveMemory',
      'updatePersonalInstructions',
      'sendEmail',
      'replyEmail',
      'forwardEmail',
    ]);
    expect(previewCardBodySlotCount).toBe(9);
    expect(Object.keys(BODY_SLOT_MAP).sort()).toEqual([...BODY_SLOT_TOOL_NAMES].sort());
  });
});
