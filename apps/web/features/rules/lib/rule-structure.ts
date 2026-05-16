import { z } from 'zod';

import type { RuleCompiledPayloadResponse } from '@/features/rules/api/rules-api';

export const MANUAL_MATCH_OPERATORS = ['ALL', 'ANY'] as const;

export const MANUAL_CONDITION_TYPES = [
  'SENDER_DOMAIN',
  'SENDER_EMAIL',
  'RECIPIENT_TO',
  'SUBJECT_CONTAINS',
  'GMAIL_LABEL_PRESENT',
  'HAS_ATTACHMENT',
  'NEWSLETTER_INDICATOR',
  'SEMANTIC_INTENT',
] as const;

export const MANUAL_ACTION_TYPES = ['label', 'archive', 'save_draft'] as const;

const MAX_MANUAL_CONDITIONS = 24;
const MAX_MANUAL_ACTIONS = 12;
const MAX_MANUAL_ROW_ID_LENGTH = 80;
const MAX_DISPLAY_NAME_LENGTH = 160;
const MAX_CONDITION_VALUE_LENGTH = 512;
const MAX_ACTION_VALUE_LENGTH = 500;

export const ManualConditionSchema = z.object({
  id: z.string().min(1).max(MAX_MANUAL_ROW_ID_LENGTH),
  type: z.enum(MANUAL_CONDITION_TYPES),
  value: z.string().max(MAX_CONDITION_VALUE_LENGTH),
});

export const ManualActionSchema = z.object({
  id: z.string().min(1).max(MAX_MANUAL_ROW_ID_LENGTH),
  type: z.enum(MANUAL_ACTION_TYPES),
  value: z.string().max(MAX_ACTION_VALUE_LENGTH),
});

export const ManualRuleDraftSchema = z.object({
  displayName: z.string().max(MAX_DISPLAY_NAME_LENGTH),
  matchOperator: z.enum(MANUAL_MATCH_OPERATORS),
  conditions: z.array(ManualConditionSchema).min(1).max(MAX_MANUAL_CONDITIONS),
  actions: z.array(ManualActionSchema).min(1).max(MAX_MANUAL_ACTIONS),
});

export type ManualMatchOperator = (typeof MANUAL_MATCH_OPERATORS)[number];
export type ManualConditionType = z.infer<typeof ManualConditionSchema>['type'];
export type ManualActionType = z.infer<typeof ManualActionSchema>['type'];
export type ManualCondition = z.infer<typeof ManualConditionSchema>;
export type ManualAction = z.infer<typeof ManualActionSchema>;
export type ManualRuleDraft = z.infer<typeof ManualRuleDraftSchema>;

export type BuiltManualRule = {
  displayName: string;
  sourceText: string;
  compiled: RuleCompiledPayloadResponse;
};

export type RuleStructureCopy = {
  conditionLabels: Record<ManualConditionType, string>;
  actionLabels: Record<ManualActionType, string>;
};

export function createEmptyManualDraft(): ManualRuleDraft {
  return {
    displayName: '',
    matchOperator: 'ALL',
    conditions: [emptyCondition(0)],
    actions: [emptyAction(0)],
  };
}

export function emptyCondition(index: number): ManualCondition {
  return {
    id: `condition-${index + 1}`,
    type: 'SENDER_DOMAIN',
    value: '',
  };
}

export function emptyAction(index: number): ManualAction {
  return {
    id: `action-${index + 1}`,
    type: 'label',
    value: '',
  };
}

export function conditionRequiresValue(type: ManualConditionType): boolean {
  return type !== 'HAS_ATTACHMENT' && type !== 'NEWSLETTER_INDICATOR';
}

export function actionRequiresValue(type: ManualActionType): boolean {
  return type !== 'archive';
}

export function manualDraftFromCompiledRule(input: {
  displayName?: string | null;
  matcherAst?: string | null;
  actionIntents?: string | null;
}): ManualRuleDraft {
  const matcherDraft = parseMatcherAst(input.matcherAst);
  const actionDraft = parseActionIntents(input.actionIntents);

  return {
    displayName: input.displayName ?? '',
    matchOperator: matcherDraft.matchOperator,
    conditions: matcherDraft.conditions.length > 0 ? matcherDraft.conditions : [emptyCondition(0)],
    actions: actionDraft.length > 0 ? actionDraft : [emptyAction(0)],
  };
}

export function buildManualRule(draft: ManualRuleDraft): BuiltManualRule | null {
  const draftResult = ManualRuleDraftSchema.safeParse(draft);
  if (!draftResult.success) {
    return null;
  }

  const validDraft = draftResult.data;
  const completeConditions = validDraft.conditions
    .map((condition) => ({ ...condition, value: condition.value.trim() }))
    .filter((condition) => !conditionRequiresValue(condition.type) || condition.value.length > 0);
  const completeActions = validDraft.actions
    .map((action) => ({ ...action, value: action.value.trim() }))
    .filter((action) => !actionRequiresValue(action.type) || action.value.length > 0);

  if (completeConditions.length === 0 || completeActions.length === 0) {
    return null;
  }

  const matcherAst =
    completeConditions.length === 1
      ? {
          schemaVersion: 'rules.v1',
          ...conditionToMatcherNode(completeConditions[0], 0),
        }
      : {
          schemaVersion: 'rules.v1',
          nodeId: 'manual-root',
          type: validDraft.matchOperator,
          children: completeConditions.map(conditionToMatcherNode),
        };
  const actionIntents = completeActions.map(actionToIntent);
  const conditionSummary = completeConditions
    .map((condition) => describeCondition(condition))
    .join(validDraft.matchOperator === 'ANY' ? ' or ' : ' and ');
  const actionSummary = completeActions.map((action) => describeAction(action)).join(', ');
  const fallbackName = completeActions[0]?.type === 'archive' ? 'Archive matching email' : 'Rule';
  const displayName = trimBounded(validDraft.displayName.trim() || fallbackName, 160);

  return {
    displayName,
    sourceText: trimBounded(`When ${conditionSummary}, then ${actionSummary}.`, 4000),
    compiled: {
      status: 'compiled',
      sourceLanguage: 'unknown',
      displayName,
      schemaVersion: 'rules.v1',
      matcherAst: JSON.stringify(matcherAst),
      actionIntents: JSON.stringify(actionIntents),
    },
  };
}

export function summarizeMatcherAst(
  jsonText: string | undefined,
  fallback: string,
  copy?: RuleStructureCopy,
): string[] {
  const parsed = parseJsonObject(jsonText);
  if (!parsed) return [fallback];

  const matcherDraft = parseMatcherAst(jsonText);
  if (matcherDraft.conditions.length === 0) return [fallback];
  return matcherDraft.conditions.map((condition) => describeCondition(condition, copy)).slice(0, 4);
}

export function summarizeActionIntents(
  jsonText: string | undefined,
  fallback: string,
  copy?: RuleStructureCopy,
): string[] {
  const actions = parseActionIntents(jsonText);
  if (actions.length === 0) return [fallback];
  return actions.map((action) => describeAction(action, copy)).slice(0, 4);
}

export function describeCondition(condition: ManualCondition, copy?: RuleStructureCopy): string {
  const value = condition.value.trim();
  const localizedLabel = copy?.conditionLabels[condition.type];
  if (localizedLabel) return value ? `${localizedLabel}: ${value}` : localizedLabel;

  switch (condition.type) {
    case 'SENDER_DOMAIN':
      return value ? `sender domain is ${value}` : 'sender domain';
    case 'SENDER_EMAIL':
      return value ? `sender is ${value}` : 'sender email';
    case 'RECIPIENT_TO':
      return value ? `to ${value}` : 'recipient';
    case 'SUBJECT_CONTAINS':
      return value ? `subject contains ${value}` : 'subject contains';
    case 'GMAIL_LABEL_PRESENT':
      return value ? `has label ${value}` : 'has label';
    case 'HAS_ATTACHMENT':
      return 'has attachment';
    case 'NEWSLETTER_INDICATOR':
      return 'looks like a newsletter';
    case 'SEMANTIC_INTENT':
      return value ? `email meaning: ${value}` : 'email meaning';
  }
}

export function describeAction(action: ManualAction, copy?: RuleStructureCopy): string {
  const value = action.value.trim();
  const localizedLabel = copy?.actionLabels[action.type];
  if (localizedLabel) return value ? `${localizedLabel}: ${value}` : localizedLabel;

  switch (action.type) {
    case 'label':
      return value ? `label ${value}` : 'label';
    case 'archive':
      return 'archive';
    case 'save_draft':
      return value ? `save draft: ${value}` : 'save draft';
  }
}

function conditionToMatcherNode(condition: ManualCondition, index: number) {
  const nodeId = `manual-condition-${index + 1}`;
  const value = condition.value.trim();

  switch (condition.type) {
    case 'SENDER_DOMAIN':
      return { nodeId, type: condition.type, domain: value };
    case 'SENDER_EMAIL':
      return { nodeId, type: condition.type, email: value };
    case 'RECIPIENT_TO':
      return { nodeId, type: condition.type, email: value };
    case 'SUBJECT_CONTAINS':
      return { nodeId, type: condition.type, text: value };
    case 'GMAIL_LABEL_PRESENT':
      return { nodeId, type: condition.type, labelId: value };
    case 'HAS_ATTACHMENT':
      return { nodeId, type: condition.type };
    case 'NEWSLETTER_INDICATOR':
      return { nodeId, type: condition.type };
    case 'SEMANTIC_INTENT':
      return { nodeId, type: condition.type, intent: value, deferred: true };
  }
}

function actionToIntent(action: ManualAction) {
  const value = action.value.trim();

  switch (action.type) {
    case 'label':
      return { type: action.type, labelName: value };
    case 'archive':
      return { type: action.type };
    case 'save_draft':
      return { type: action.type, instruction: value };
  }
}

function parseMatcherAst(jsonText: string | null | undefined): {
  matchOperator: ManualMatchOperator;
  conditions: ManualCondition[];
} {
  const rootNode = parseJsonObject(jsonText);
  if (!rootNode) return { matchOperator: 'ALL', conditions: [] };

  const legacyAllChildren = Array.isArray(rootNode.all) ? rootNode.all : null;
  const legacyAnyChildren = Array.isArray(rootNode.any) ? rootNode.any : null;
  const rootType = normalizeMatcherType(rootNode.type ?? rootNode.matcherType);
  const children =
    legacyAllChildren ??
    legacyAnyChildren ??
    (rootType === 'ALL' || rootType === 'ANY' ? arrayValue(rootNode.children) : null);
  const matchOperator: ManualMatchOperator =
    legacyAnyChildren || rootType === 'ANY' ? 'ANY' : 'ALL';
  const nodes = children ?? [rootNode];

  return {
    matchOperator,
    conditions: nodes
      .map((node, index) => matcherNodeToCondition(node, index))
      .filter((condition): condition is ManualCondition => condition !== null),
  };
}

function matcherNodeToCondition(node: unknown, index: number): ManualCondition | null {
  if (!isRecord(node)) return null;
  const type = normalizeMatcherType(node.type ?? node.matcherType);
  const id = `condition-${index + 1}`;

  switch (type) {
    case 'SENDER_DOMAIN':
      return { id, type, value: stringValue(node.domain ?? node.value) };
    case 'SENDER_EMAIL':
      return { id, type, value: stringValue(node.email ?? node.value) };
    case 'RECIPIENT_TO':
      return { id, type, value: stringValue(node.email ?? node.value) };
    case 'SUBJECT_CONTAINS':
      return { id, type, value: stringValue(node.text ?? node.value) };
    case 'GMAIL_LABEL_PRESENT':
      return { id, type, value: stringValue(node.labelId ?? node.label ?? node.value) };
    case 'HAS_ATTACHMENT':
      return { id, type, value: '' };
    case 'NEWSLETTER_INDICATOR':
      return { id, type, value: '' };
    case 'SEMANTIC_INTENT':
      return { id, type, value: stringValue(node.intent ?? node.description ?? node.value) };
    default:
      return null;
  }
}

function parseActionIntents(jsonText: string | null | undefined): ManualAction[] {
  const rootNode = parseJson(jsonText);
  if (!Array.isArray(rootNode)) return [];

  return rootNode
    .map((node, index) => actionNodeToDraft(node, index))
    .filter((action): action is ManualAction => action !== null);
}

function actionNodeToDraft(node: unknown, index: number): ManualAction | null {
  if (!isRecord(node)) return null;
  const id = `action-${index + 1}`;
  const type = normalizeActionType(node.type ?? node.action);

  switch (type) {
    case 'label':
      return {
        id,
        type,
        value: stripLabelPrefix(stringValue(node.labelName ?? node.value ?? node.safeLabel)),
      };
    case 'archive':
      return { id, type, value: '' };
    case 'save_draft':
      return {
        id,
        type,
        value: stringValue(node.instruction ?? node.body ?? node.value),
      };
    default:
      return null;
  }
}

function parseJsonObject(jsonText: string | null | undefined): Record<string, unknown> | null {
  const parsed = parseJson(jsonText);
  return isRecord(parsed) ? parsed : null;
}

function parseJson(jsonText: string | null | undefined): unknown {
  if (!jsonText) return null;
  try {
    return JSON.parse(jsonText) as unknown;
  } catch {
    return null;
  }
}

function normalizeMatcherType(value: unknown): string {
  return String(value ?? '')
    .trim()
    .replaceAll('-', '_')
    .toUpperCase();
}

function normalizeActionType(value: unknown): string {
  return String(value ?? '')
    .trim()
    .replaceAll('-', '_')
    .toLowerCase();
}

function arrayValue(value: unknown): unknown[] | null {
  return Array.isArray(value) ? value : null;
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function stripLabelPrefix(value: string): string {
  return value.replace(/^label\s+/i, '').trim();
}

function trimBounded(value: string, maxLength: number): string {
  return value.length > maxLength ? value.slice(0, maxLength).trim() : value;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}
