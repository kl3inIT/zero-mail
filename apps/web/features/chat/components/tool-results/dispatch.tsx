import { ApplyLabelResult } from './apply-label-result';
import { ArchiveThreadResult } from './archive-thread-result';
import { DisableRuleResult } from './disable-rule-result';
import { GetMessageResult } from './get-message-result';
import { GetRuleResult } from './get-rule-result';
import { GetSenderSafetyEntryResult } from './get-sender-safety-entry-result';
import { GetThreadResult } from './get-thread-result';
import { ListLabelsResult } from './list-labels-result';
import { ListRulesResult } from './list-rules-result';
import { RemoveLabelResult } from './remove-label-result';
import { SaveDraftResult } from './save-draft-result';
import { SearchInboxResult } from './search-inbox-result';
import { SearchMemoriesResult } from './search-memories-result';
import { UpdateRuleResult } from './update-rule-result';

const READ_RENDERERS: Record<
  string,
  (props: { input: unknown; output: unknown }) => React.ReactNode
> = {
  searchInbox: SearchInboxResult,
  getMessage: GetMessageResult,
  listLabels: ListLabelsResult,
  getThread: GetThreadResult,
  getRule: GetRuleResult,
  listRules: ListRulesResult,
  getSenderSafetyEntry: GetSenderSafetyEntryResult,
  searchMemories: SearchMemoriesResult,
};

const WRITE_REVERSIBLE_RENDERERS: Record<
  string,
  (props: { input: unknown; output: unknown }) => React.ReactNode
> = {
  applyLabel: ApplyLabelResult,
  removeLabel: RemoveLabelResult,
  archiveThread: ArchiveThreadResult,
  updateRule: UpdateRuleResult,
  disableRule: DisableRuleResult,
  saveDraft: SaveDraftResult,
};

export function renderToolResult({
  toolName,
  input,
  output,
}: {
  toolName: string;
  input: unknown;
  output: unknown;
}): React.ReactNode | null {
  const renderer = Object.hasOwn(READ_RENDERERS, toolName)
    ? READ_RENDERERS[toolName]
    : Object.hasOwn(WRITE_REVERSIBLE_RENDERERS, toolName)
      ? WRITE_REVERSIBLE_RENDERERS[toolName]
      : undefined;
  if (!renderer) return null;
  // Only render once we have output (otherwise let the caller show a loading state).
  // Some tools (e.g., searchInbox) still meaningfully render input-only while running.
  return renderer({ input, output });
}

export function hasToolResultRenderer(toolName: string): boolean {
  return (
    Object.hasOwn(READ_RENDERERS, toolName) || Object.hasOwn(WRITE_REVERSIBLE_RENDERERS, toolName)
  );
}
