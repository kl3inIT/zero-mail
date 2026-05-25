'use client';

import { ChevronRight, Mail, MailX, Tag, TagsIcon } from 'lucide-react';
import { useState } from 'react';

import { Badge } from '@/components/ui/badge';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { cn } from '@/lib/utils';

type Unknown = Record<string, unknown>;

function getField<T>(source: unknown, field: string): T | undefined {
  if (typeof source === 'object' && source !== null && field in source) {
    return (source as Unknown)[field] as T;
  }
  return undefined;
}

function asString(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined;
}

function asArray<T = unknown>(value: unknown): T[] | undefined {
  return Array.isArray(value) ? (value as T[]) : undefined;
}

function asBool(value: unknown): boolean | undefined {
  return typeof value === 'boolean' ? value : undefined;
}

function formatRelativeDate(isoDate: string | undefined): string {
  if (!isoDate) return '';
  const parsed = new Date(isoDate);
  if (Number.isNaN(parsed.getTime())) return '';
  const now = Date.now();
  const diffMs = now - parsed.getTime();
  const diffMinutes = Math.round(diffMs / 60_000);
  if (diffMinutes < 1) return 'vừa xong';
  if (diffMinutes < 60) return `${diffMinutes} phút trước`;
  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours} giờ trước`;
  const diffDays = Math.round(diffHours / 24);
  if (diffDays < 7) return `${diffDays} ngày trước`;
  return parsed.toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

function SubtleToolCollapsible({
  title,
  defaultOpen = false,
  children,
}: {
  title: React.ReactNode;
  defaultOpen?: boolean;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger className="text-muted-foreground hover:text-foreground flex items-center gap-1.5 text-xs transition-colors">
        <ChevronRight
          className={cn('size-3 shrink-0 transition-transform duration-200', open && 'rotate-90')}
        />
        <span>{title}</span>
      </CollapsibleTrigger>
      <CollapsibleContent>
        <div className="border-border mt-2 space-y-3 rounded-md border p-3">{children}</div>
      </CollapsibleContent>
    </Collapsible>
  );
}

function ToolDetailRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex gap-4 text-sm">
      <span className="text-muted-foreground w-24 shrink-0 pt-0.5 text-xs tracking-wide uppercase">
        {label}
      </span>
      <div className="text-foreground min-w-0 flex-1">{value}</div>
    </div>
  );
}

function EmptyHint({ children }: { children: React.ReactNode }) {
  return <p className="text-muted-foreground text-sm">{children}</p>;
}

type EmailRowData = {
  messageId: string;
  threadId: string;
  subject?: string;
  from?: string;
  snippet?: string;
  date?: string;
  isUnread?: boolean;
};

function EmailRowsList({ emails }: { emails: EmailRowData[] }) {
  const seen = new Set<string>();
  const unique = emails.filter((email) => {
    if (seen.has(email.threadId)) return false;
    seen.add(email.threadId);
    return true;
  });
  if (unique.length === 0) {
    return <EmptyHint>Không có email nào.</EmptyHint>;
  }
  return (
    <ul className="divide-border divide-y">
      {unique.map((email) => (
        <li key={email.threadId} className="flex flex-col gap-1 py-2 first:pt-0 last:pb-0">
          <div className="flex items-start justify-between gap-3">
            <p className="text-foreground line-clamp-1 text-sm font-medium">
              {email.subject || '(không có chủ đề)'}
            </p>
            {email.isUnread && (
              <Badge variant="secondary" className="shrink-0 text-[10px]">
                Chưa đọc
              </Badge>
            )}
          </div>
          {email.from && <p className="text-muted-foreground line-clamp-1 text-xs">{email.from}</p>}
          {email.snippet && (
            <p className="text-muted-foreground line-clamp-2 text-xs">{email.snippet}</p>
          )}
          {email.date && (
            <p className="text-muted-foreground text-[11px]">{formatRelativeDate(email.date)}</p>
          )}
        </li>
      ))}
    </ul>
  );
}

// --- READ TOOLS --------------------------------------------------------------

function SearchInboxResult({ input, output }: { input: unknown; output: unknown }) {
  const query = asString(getField(input, 'query'));
  const messages = asArray<EmailRowData>(getField(output, 'messages')) ?? [];
  return (
    <SubtleToolCollapsible
      title={`Tìm trong inbox · ${messages.length} kết quả`}
      defaultOpen={messages.length > 0}
    >
      {query && (
        <ToolDetailRow
          label="Truy vấn"
          value={<span className="font-mono text-xs">{query}</span>}
        />
      )}
      <EmailRowsList emails={messages} />
    </SubtleToolCollapsible>
  );
}

function GetMessageResult({ input, output }: { input: unknown; output: unknown }) {
  const messageId = asString(getField(input, 'messageId'));
  const subject = asString(getField(output, 'subject'));
  const from = asString(getField(output, 'from'));
  const to = asArray<string>(getField(output, 'to'));
  const cc = asArray<string>(getField(output, 'cc'));
  const date = asString(getField(output, 'date'));
  const bodyText = asString(getField(output, 'bodyText'));
  return (
    <SubtleToolCollapsible title="Đọc email" defaultOpen>
      {subject && <ToolDetailRow label="Chủ đề" value={subject} />}
      {from && <ToolDetailRow label="Từ" value={from} />}
      {to && to.length > 0 && <ToolDetailRow label="Đến" value={to.join(', ')} />}
      {cc && cc.length > 0 && <ToolDetailRow label="CC" value={cc.join(', ')} />}
      {date && <ToolDetailRow label="Thời gian" value={formatRelativeDate(date)} />}
      {bodyText && (
        <div className="border-border bg-muted/30 max-h-64 overflow-y-auto rounded border p-2 text-xs whitespace-pre-wrap">
          {bodyText}
        </div>
      )}
      {!subject && !bodyText && messageId && (
        <ToolDetailRow label="Message ID" value={<code className="text-xs">{messageId}</code>} />
      )}
    </SubtleToolCollapsible>
  );
}

function ListLabelsResult({ output }: { output: unknown }) {
  const labels =
    asArray<{ id?: string; name?: string; type?: string }>(getField(output, 'labels')) ?? [];
  return (
    <SubtleToolCollapsible title={`Danh sách nhãn · ${labels.length}`}>
      {labels.length === 0 ? (
        <EmptyHint>Không có nhãn nào.</EmptyHint>
      ) : (
        <div className="flex flex-wrap gap-1.5">
          {labels.map((label) => (
            <Badge key={label.id ?? label.name} variant="outline" className="gap-1 font-normal">
              <Tag className="size-3" />
              {label.name ?? label.id}
            </Badge>
          ))}
        </div>
      )}
    </SubtleToolCollapsible>
  );
}

function GetThreadResult({ input, output }: { input: unknown; output: unknown }) {
  const threadId = asString(getField(input, 'threadId')) ?? asString(getField(output, 'threadId'));
  const participants = asArray<string>(getField(output, 'participantList')) ?? [];
  const messageIds = asArray<string>(getField(output, 'messageIds')) ?? [];
  const lastActivityAt = asString(getField(output, 'lastActivityAt'));
  return (
    <SubtleToolCollapsible title={`Xem chuỗi · ${messageIds.length} email`} defaultOpen>
      {threadId && (
        <ToolDetailRow label="Thread" value={<code className="text-xs">{threadId}</code>} />
      )}
      {participants.length > 0 && (
        <ToolDetailRow label="Người tham gia" value={participants.join(', ')} />
      )}
      {lastActivityAt && (
        <ToolDetailRow label="Hoạt động mới" value={formatRelativeDate(lastActivityAt)} />
      )}
    </SubtleToolCollapsible>
  );
}

type RuleRow = {
  ruleId?: string;
  displayName?: string;
  sourceText?: string;
  enabled?: boolean;
  matcher?: string;
  actions?: string;
};

function RuleCard({ rule }: { rule: RuleRow }) {
  return (
    <div className="border-border space-y-1.5 rounded-md border p-2.5">
      <div className="flex items-start justify-between gap-2">
        <p className="text-foreground text-sm font-medium">{rule.displayName ?? 'Quy tắc'}</p>
        <Badge variant={rule.enabled ? 'default' : 'secondary'} className="shrink-0 text-[10px]">
          {rule.enabled ? 'Đang bật' : 'Đang tắt'}
        </Badge>
      </div>
      {rule.sourceText && (
        <p className="text-muted-foreground line-clamp-3 text-xs">{rule.sourceText}</p>
      )}
    </div>
  );
}

function GetRuleResult({ output }: { output: unknown }) {
  const rule = getField<RuleRow>(output, 'rule');
  return (
    <SubtleToolCollapsible title="Xem quy tắc" defaultOpen>
      {rule ? <RuleCard rule={rule} /> : <EmptyHint>Không tìm thấy quy tắc.</EmptyHint>}
    </SubtleToolCollapsible>
  );
}

function ListRulesResult({ output }: { output: unknown }) {
  const rules = asArray<RuleRow>(getField(output, 'rules')) ?? [];
  return (
    <SubtleToolCollapsible
      title={`Danh sách quy tắc · ${rules.length}`}
      defaultOpen={rules.length > 0 && rules.length <= 5}
    >
      {rules.length === 0 ? (
        <EmptyHint>Chưa có quy tắc nào.</EmptyHint>
      ) : (
        <div className="space-y-2">
          {rules.map((rule, index) => (
            <RuleCard key={rule.ruleId ?? index} rule={rule} />
          ))}
        </div>
      )}
    </SubtleToolCollapsible>
  );
}

function GetSenderSafetyEntryResult({ input, output }: { input: unknown; output: unknown }) {
  const senderEmail = asString(getField(input, 'senderEmail'));
  const mode = asString(getField(output, 'mode'));
  const addedAt = asString(getField(output, 'addedAt'));
  const modeLabel =
    mode === 'opted_in'
      ? 'Đã đồng ý nhận'
      : mode === 'protected'
        ? 'Được bảo vệ (safety net)'
        : mode === 'not_found'
          ? 'Không có trong danh sách'
          : (mode ?? 'Không rõ');
  return (
    <SubtleToolCollapsible title="Trạng thái safety-net" defaultOpen>
      {senderEmail && <ToolDetailRow label="Người gửi" value={senderEmail} />}
      <ToolDetailRow label="Trạng thái" value={modeLabel} />
      {addedAt && <ToolDetailRow label="Thêm vào" value={formatRelativeDate(addedAt)} />}
    </SubtleToolCollapsible>
  );
}

function SearchMemoriesResult({ input, output }: { input: unknown; output: unknown }) {
  const query = asString(getField(input, 'query'));
  const memories =
    asArray<{ id?: string; content?: string; createdAt?: string }>(getField(output, 'memories')) ??
    [];
  return (
    <SubtleToolCollapsible
      title={`Tìm ký ức · ${memories.length}`}
      defaultOpen={memories.length > 0}
    >
      {query && (
        <ToolDetailRow
          label="Truy vấn"
          value={<span className="font-mono text-xs">{query}</span>}
        />
      )}
      {memories.length === 0 ? (
        <EmptyHint>Không tìm thấy ký ức nào khớp.</EmptyHint>
      ) : (
        <ul className="divide-border divide-y">
          {memories.map((memory, index) => (
            <li key={memory.id ?? index} className="space-y-1 py-2 first:pt-0 last:pb-0">
              <p className="text-foreground text-sm whitespace-pre-wrap">{memory.content}</p>
              {memory.createdAt && (
                <p className="text-muted-foreground text-[11px]">
                  {formatRelativeDate(memory.createdAt)}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </SubtleToolCollapsible>
  );
}

// --- WRITE-REVERSIBLE TOOLS --------------------------------------------------

function StatusLine({ icon: Icon, children }: { icon: typeof Mail; children: React.ReactNode }) {
  return (
    <div className="text-muted-foreground flex items-center gap-2 text-xs">
      <Icon className="text-foreground/70 size-3.5 shrink-0" />
      <span>{children}</span>
    </div>
  );
}

function ApplyLabelResult({ input, output }: { input: unknown; output: unknown }) {
  const labelName =
    asString(getField(input, 'labelName')) ?? asString(getField(output, 'label_id')) ?? 'nhãn';
  const messageId =
    asString(getField(input, 'messageId')) ?? asString(getField(output, 'message_id'));
  return (
    <SubtleToolCollapsible title={`Đã gắn nhãn "${labelName}"`} defaultOpen={false}>
      <StatusLine icon={Tag}>
        Gắn nhãn <strong>{labelName}</strong> cho email{' '}
        {messageId ? <code className="text-[11px]">{messageId}</code> : ''}.
      </StatusLine>
    </SubtleToolCollapsible>
  );
}

function RemoveLabelResult({ input, output }: { input: unknown; output: unknown }) {
  const labelName =
    asString(getField(input, 'labelName')) ?? asString(getField(output, 'label_id')) ?? 'nhãn';
  const messageId =
    asString(getField(input, 'messageId')) ?? asString(getField(output, 'message_id'));
  return (
    <SubtleToolCollapsible title={`Đã gỡ nhãn "${labelName}"`}>
      <StatusLine icon={TagsIcon}>
        Gỡ nhãn <strong>{labelName}</strong> khỏi email{' '}
        {messageId ? <code className="text-[11px]">{messageId}</code> : ''}.
      </StatusLine>
    </SubtleToolCollapsible>
  );
}

function ArchiveThreadResult({ input, output }: { input: unknown; output: unknown }) {
  const threadId = asString(getField(input, 'threadId')) ?? asString(getField(output, 'thread_id'));
  return (
    <SubtleToolCollapsible title="Đã lưu trữ chuỗi">
      <StatusLine icon={MailX}>
        Lưu trữ chuỗi {threadId ? <code className="text-[11px]">{threadId}</code> : ''} (đã bỏ khỏi
        Inbox).
      </StatusLine>
    </SubtleToolCollapsible>
  );
}

function UpdateRuleResult({ input, output }: { input: unknown; output: unknown }) {
  const displayName = asString(getField(input, 'displayName')) ?? 'Quy tắc';
  const enabled = asBool(getField(output, 'enabled'));
  return (
    <SubtleToolCollapsible title={`Đã cập nhật "${displayName}"`} defaultOpen>
      <ToolDetailRow label="Tên" value={displayName} />
      <ToolDetailRow
        label="Trạng thái"
        value={
          <Badge variant={enabled ? 'default' : 'secondary'} className="text-[10px]">
            {enabled ? 'Đang bật' : 'Đang tắt'}
          </Badge>
        }
      />
    </SubtleToolCollapsible>
  );
}

function DisableRuleResult({ input }: { input: unknown }) {
  const ruleId = asString(getField(input, 'ruleId'));
  return (
    <SubtleToolCollapsible title="Đã tắt quy tắc">
      <StatusLine icon={MailX}>
        Quy tắc {ruleId ? <code className="text-[11px]">{ruleId}</code> : ''} đã chuyển sang tắt.
      </StatusLine>
    </SubtleToolCollapsible>
  );
}

function SaveDraftResult({ input, output }: { input: unknown; output: unknown }) {
  const to = asString(getField(input, 'to'));
  const subject = asString(getField(input, 'subject'));
  const body = asString(getField(input, 'body'));
  const draftId = asString(getField(output, 'draft_id'));
  return (
    <SubtleToolCollapsible title="Đã lưu nháp" defaultOpen>
      {to && <ToolDetailRow label="Đến" value={to} />}
      {subject && <ToolDetailRow label="Chủ đề" value={subject} />}
      {body && (
        <div className="border-border bg-muted/30 max-h-48 overflow-y-auto rounded border p-2 text-xs whitespace-pre-wrap">
          {body}
        </div>
      )}
      {draftId && (
        <ToolDetailRow label="Draft ID" value={<code className="text-[11px]">{draftId}</code>} />
      )}
    </SubtleToolCollapsible>
  );
}

function AddToKnowledgeBaseResult({ input }: { input: unknown }) {
  const title = asString(getField(input, 'title'));
  const content = asString(getField(input, 'content'));
  return (
    <SubtleToolCollapsible title="Đã thêm vào kho kiến thức" defaultOpen>
      {title && <ToolDetailRow label="Tiêu đề" value={title} />}
      {content && <div className="text-foreground text-xs whitespace-pre-wrap">{content}</div>}
    </SubtleToolCollapsible>
  );
}

// --- DISPATCHER --------------------------------------------------------------

const READ_RENDERERS: Record<
  string,
  (props: { input: unknown; output: unknown }) => React.ReactNode
> = {
  searchInbox: SearchInboxResult,
  getMessage: GetMessageResult,
  listLabels: ({ output }) => <ListLabelsResult output={output} />,
  getThread: GetThreadResult,
  getRule: ({ output }) => <GetRuleResult output={output} />,
  listRules: ({ output }) => <ListRulesResult output={output} />,
  getSenderSafetyEntry: GetSenderSafetyEntryResult,
  searchMemories: SearchMemoriesResult,
};

const WRITE_REVERSIBLE_RENDERERS: Record<
  string,
  (props: { input: unknown; output: unknown }) => React.ReactNode
> = {
  applyLabel: ApplyLabelResult,
  removeLabel: RemoveLabelResult,
  archiveThread: ({ input, output }) => <ArchiveThreadResult input={input} output={output} />,
  updateRule: UpdateRuleResult,
  disableRule: ({ input }) => <DisableRuleResult input={input} />,
  saveDraft: SaveDraftResult,
  addToKnowledgeBase: ({ input }) => <AddToKnowledgeBaseResult input={input} />,
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
