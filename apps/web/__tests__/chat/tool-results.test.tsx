import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { hasToolResultRenderer, renderToolResult } from '@/features/chat/components/tool-results';

const TOOL_NAMES = [
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
] as const;

describe('tool-results dispatcher', () => {
  it('claims a renderer for every catalog tool', () => {
    for (const toolName of TOOL_NAMES) {
      expect(hasToolResultRenderer(toolName), `missing renderer: ${toolName}`).toBe(true);
    }
  });

  it('returns false for unknown tools', () => {
    expect(hasToolResultRenderer('definitelyNotARealTool')).toBe(false);
    expect(hasToolResultRenderer('')).toBe(false);
  });

  it('rejects prototype-pollution lookups via Object.hasOwn guard', () => {
    // Bracket lookup on a plain object would happily return Object.prototype values for these
    // names. The guard inside the dispatcher must treat them as misses.
    expect(hasToolResultRenderer('__proto__')).toBe(false);
    expect(hasToolResultRenderer('constructor')).toBe(false);
    expect(hasToolResultRenderer('toString')).toBe(false);
    expect(hasToolResultRenderer('hasOwnProperty')).toBe(false);

    expect(renderToolResult({ toolName: '__proto__', input: {}, output: {} })).toBeNull();
    expect(renderToolResult({ toolName: 'constructor', input: {}, output: {} })).toBeNull();
  });

  it('returns null for unknown tools', () => {
    expect(renderToolResult({ toolName: 'mystery', input: {}, output: {} })).toBeNull();
  });
});

describe('tool-results render smoke', () => {
  const fixtures: Record<string, { input: unknown; output: unknown }> = {
    searchInbox: {
      input: { query: 'from:(github)' },
      output: {
        messages: [
          {
            messageId: 'm1',
            threadId: 't1',
            subject: 'Re: PR #42',
            from: '"Foo Bar" <foo@example.com>',
            snippet: 'Merged into main',
            date: new Date(Date.now() - 60_000).toISOString(),
            isUnread: true,
          },
        ],
      },
    },
    getMessage: {
      input: { messageId: 'm1' },
      output: {
        messageId: 'm1',
        threadId: 't1',
        subject: 'Hello world',
        from: 'sender@example.com',
        to: ['me@example.com'],
        cc: [],
        date: new Date().toISOString(),
        bodyText: 'Body content',
      },
    },
    listLabels: {
      input: {},
      output: {
        labels: [
          { id: 'INBOX', name: 'INBOX', type: 'system' },
          { id: 'SENT', name: 'SENT', type: 'system' },
        ],
      },
    },
    getThread: {
      input: { threadId: 't1' },
      output: {
        threadId: 't1',
        participantList: ['a@example.com', 'b@example.com'],
        messageIds: ['m1', 'm2'],
        lastActivityAt: new Date().toISOString(),
      },
    },
    getRule: {
      input: { ruleId: 'r1' },
      output: {
        rule: { ruleId: 'r1', displayName: 'Archive receipts', enabled: true, sourceText: 'src' },
      },
    },
    listRules: {
      input: {},
      output: {
        rules: [
          { ruleId: 'r1', displayName: 'Archive receipts', enabled: true, sourceText: 'src' },
          { ruleId: 'r2', displayName: 'Hiring', enabled: false, sourceText: 'src' },
        ],
      },
    },
    getSenderSafetyEntry: {
      input: { senderEmail: 'x@example.com' },
      output: { recipientEmailHash: 'h', mode: 'opted_in', addedAt: new Date().toISOString() },
    },
    searchMemories: {
      input: { query: 'project' },
      output: {
        memories: [{ id: 'mem1', content: 'Note', createdAt: new Date().toISOString() }],
      },
    },
    applyLabel: {
      input: { messageId: 'm1', labelName: 'Notification' },
      output: { message_id: 'm1', label_id: 'Label_1' },
    },
    removeLabel: {
      input: { messageId: 'm1', labelName: 'Notification' },
      output: { message_id: 'm1', label_id: 'Label_1' },
    },
    archiveThread: {
      input: { threadId: 't1' },
      output: { thread_id: 't1', label_id: 'INBOX' },
    },
    updateRule: {
      input: { ruleId: 'r1', displayName: 'Renamed' },
      output: { rule_id: 'r1', enabled: true },
    },
    disableRule: {
      input: { ruleId: 'r1' },
      output: { rule_id: 'r1', enabled: false },
    },
    saveDraft: {
      input: { to: 'x@example.com', subject: 'Re: hi', body: 'Body' },
      output: { draft_id: 'd1', gmail_thread_id: 't1' },
    },
    addToKnowledgeBase: {
      input: { title: 'Deadline', content: 'Submit 15/06' },
      output: { knowledge_snippet_id: 'k1' },
    },
  };

  for (const toolName of TOOL_NAMES) {
    it(`renders ${toolName} without throwing`, () => {
      const fixture = fixtures[toolName];
      const rendered = renderToolResult({
        toolName,
        input: fixture.input,
        output: fixture.output,
      });
      expect(rendered, `renderToolResult returned null for ${toolName}`).not.toBeNull();
      const { container } = render(<>{rendered}</>);
      // Smoke test: each renderer must produce SOME DOM (not an empty fragment).
      expect(container.textContent ?? '').not.toBe('');
    });
  }
});
