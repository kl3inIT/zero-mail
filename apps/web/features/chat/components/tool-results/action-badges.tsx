import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

type ActionMeta = { label: string; className: string };

// Colors mirror the action catalog; outbound (send/reply/forward) share one
// emerald family so the "this rule sends mail" signal reads at a glance.
const ACTION_META: Record<string, ActionMeta> = {
  label: {
    label: 'Gắn nhãn',
    className: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  },
  archive: {
    label: 'Lưu trữ',
    className: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
  },
  save_draft: {
    label: 'Lưu nháp',
    className: 'bg-violet-100 text-violet-700 dark:bg-violet-900/30 dark:text-violet-300',
  },
  mark_read: {
    label: 'Đánh dấu đã đọc',
    className: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
  },
  star: {
    label: 'Gắn sao',
    className: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300',
  },
  add_to_digest: {
    label: 'Thêm vào digest',
    className: 'bg-teal-100 text-teal-700 dark:bg-teal-900/30 dark:text-teal-300',
  },
  mark_spam: {
    label: 'Đánh dấu spam',
    className: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
  },
  send_reply: {
    label: 'Trả lời',
    className: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300',
  },
  forward_email: {
    label: 'Chuyển tiếp',
    className: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300',
  },
  send_email: {
    label: 'Gửi email',
    className: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300',
  },
};

type ParsedAction = { type: string; detail?: string };

function asTrimmed(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function asRecipients(value: unknown): string | undefined {
  if (Array.isArray(value)) {
    const joined = value.filter((entry): entry is string => typeof entry === 'string').join(', ');
    return joined || undefined;
  }
  return asTrimmed(value);
}

function detailForAction(type: string, node: Record<string, unknown>): string | undefined {
  switch (type) {
    case 'label':
      return asTrimmed(node.labelName ?? node.value);
    case 'forward_email':
      return asRecipients(node.recipients ?? node.to);
    case 'send_email':
      return asRecipients(node.to ?? node.recipients);
    default:
      return undefined;
  }
}

function parseActions(actionIntents: unknown): ParsedAction[] {
  // Backend rule output carries actionIntents as a JSON string; the createRule
  // preview may pass it already parsed. Accept both.
  let parsed: unknown = actionIntents;
  if (typeof parsed === 'string') {
    try {
      parsed = JSON.parse(parsed);
    } catch {
      return [];
    }
  }
  if (!Array.isArray(parsed)) return [];
  return parsed
    .map((node): ParsedAction => {
      if (node === null || typeof node !== 'object') return { type: '' };
      const record = node as Record<string, unknown>;
      const type = String(record.type ?? record.action ?? '')
        .trim()
        .replaceAll('-', '_')
        .toLowerCase();
      return { type, detail: detailForAction(type, record) };
    })
    .filter((action) => action.type.length > 0);
}

/** Renders the rule's actions ("Then") as colored badges parsed from actionIntents JSON. */
export function ActionBadges({ actionIntents }: { actionIntents: unknown }) {
  const actions = parseActions(actionIntents);
  if (actions.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-1">
      {actions.map((action, index) => {
        const meta = ACTION_META[action.type];
        return (
          <Badge
            key={`${action.type}-${index}`}
            variant="secondary"
            className={cn('font-normal', meta?.className)}
          >
            {meta?.label ?? action.type}
            {action.detail ? `: ${action.detail}` : ''}
          </Badge>
        );
      })}
    </div>
  );
}
