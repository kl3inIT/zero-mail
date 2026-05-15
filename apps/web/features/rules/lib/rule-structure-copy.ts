import type {
  ManualActionType,
  ManualConditionType,
  RuleStructureCopy,
} from '@/features/rules/lib/rule-structure';

type TranslationFunction = (key: string) => string;

const CONDITION_TYPES: ManualConditionType[] = [
  'SENDER_DOMAIN',
  'SENDER_EMAIL',
  'RECIPIENT_TO',
  'SUBJECT_CONTAINS',
  'GMAIL_LABEL_PRESENT',
  'HAS_ATTACHMENT',
  'NEWSLETTER_INDICATOR',
  'SEMANTIC_INTENT',
];

const ACTION_TYPES: ManualActionType[] = ['label', 'archive', 'save_draft'];

export function createRuleStructureCopy(t: TranslationFunction): RuleStructureCopy {
  return {
    conditionLabels: Object.fromEntries(
      CONDITION_TYPES.map((conditionType) => [
        conditionType,
        t(`rules.manual.condition.${conditionType}`),
      ]),
    ) as Record<ManualConditionType, string>,
    actionLabels: Object.fromEntries(
      ACTION_TYPES.map((actionType) => [actionType, t(`rules.manual.action.${actionType}`)]),
    ) as Record<ManualActionType, string>,
  };
}
