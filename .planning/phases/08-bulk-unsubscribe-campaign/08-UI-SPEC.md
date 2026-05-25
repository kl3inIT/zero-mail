---
phase: 8
slug: bulk-unsubscribe-campaign
status: draft
shadcn_initialized: true
preset: base-nova (existing — apps/web/components.json)
created: 2026-05-17
response_language: vi
---

# Phase 8 — UI Design Contract (Bulk Unsubscribe Campaign)

> Hợp đồng visual + interaction cho 4 màn chính của `/cleanup/*` namespace.
> Sinh ra bởi `gsd-ui-researcher`, verify bởi `gsd-ui-checker`, consume bởi `gsd-planner` / `gsd-executor`.
>
> **Language policy:** Giải thích tiếng Việt, mọi token / class / identifier / code snippet để nguyên tiếng Anh — match codebase. Tất cả copy hiển thị ra UI ở dưới đây là tiếng Việt; planner tự sinh bản tiếng Anh (lock-step `vi.json` / `en.json` theo `apps/web/i18n/messages/`).

---

## Design System

| Property | Value | Source |
|----------|-------|--------|
| Tool | shadcn/ui (đã init) | `apps/web/components.json` |
| Preset | `base-nova` style + `lucide` icons + `neutral` baseColor + `cssVariables: true` | `apps/web/components.json` |
| Component library | Radix primitives qua shadcn (28 primitives đã có) — **KHÔNG install thêm** | `apps/web/components/ui/*` |
| Icon library | `lucide-react` (đã dùng trong sidebar / analytics) | locked |
| Font | `--font-sans` (system stack) + `--font-mono` (UI mono) + `--font-serif` (Lora) | `apps/web/app/globals.css:9-16` |
| Tailwind | v4.2.4 với `@theme inline` mapping vào CSS variables | `globals.css:7-78` |
| Third-party registry | **NONE** — chỉ shadcn official; `registries: {}` trong components.json | locked |

**Discretion locked bởi research:** KHÔNG được introduce CSS variable mới trong `globals.css`. Mọi color sử dụng token đã có (`--primary`, `--accent`, `--muted`, `--destructive`, `--green`, `--green-soft`, `--red`, `--red-soft`, `--amber`, `--amber-soft`, `--blue`, `--blue-soft`, `--violet`, `--violet-soft`, `--chart-1..5`).

---

## Spacing Scale

Tailwind 4 mặc định = 4px base. Phase 8 tuân thủ scale dưới đây — multiples of 4 only.

| Token | px value | Tailwind class | Usage trong Phase 8 |
|-------|----------|----------------|---------------------|
| 2xs | 4px | `p-1` `gap-1` | Icon gap trong badge / button |
| xs | 8px | `p-2` `gap-2` | Khoảng cách inline giữa checkbox và text trong row |
| sm | 12px | `p-3` `gap-3` | Padding cell trong `Table`, gap giữa form field |
| md | 16px | `p-4` `gap-4` | Default card padding, gap giữa card trong stack |
| lg | 24px | `p-6` `gap-6` | `CardHeader` padding, gap giữa section trong page |
| xl | 32px | `p-8` `gap-8` | Page-level vertical rhythm, `Dialog` content padding |
| 2xl | 48px | `py-12` | Empty state vertical padding |
| 3xl | 64px | `py-16` | Page top section breathing room (chỉ dùng nếu cần) |

**Exceptions cho Phase 8 (declared):**
- Risk badge minimum height `h-6` (24px) — match Phase 7 badge convention.
- Per-sender row trong status table: `py-3 px-4` (12 / 16) — khớp `Table` shadcn defaults.
- Sidebar nav item: `h-9` (36px) — locked bởi `AppSidebar.tsx` hiện có; không override.
- Tooltip + Toast: kế thừa shadcn defaults — không spec lại.

---

## Typography

Phase 8 dùng **4 size** + **2 weight**. Line-height theo recommendation: 1.5 body, 1.2 heading.

| Role | Size (px / rem) | Tailwind class | Weight | Line height | Usage |
|------|-----------------|----------------|--------|-------------|-------|
| Page heading | 24px / `text-2xl` | `text-2xl` | 600 (semibold) | 1.2 (`leading-tight`) | `<h1>` page title — "Bulk unsubscribe", "Suppression list" |
| Section heading | 18px / `text-lg` | `text-lg` | 600 (semibold) | 1.3 (`leading-snug`) | Card titles — "Newsletter ứng viên (30 ngày)", "Tiến độ campaign" |
| Body | 14px / `text-sm` | `text-sm` | 400 (regular) | 1.5 (`leading-normal`) | Default text trong row, dialog description, helper text |
| Label / Mono / Eyebrow | 12px / `text-xs` | `text-xs` | 500 (medium — eyebrow only) hoặc 400 | 1.4 | Badge label, table column header (`uppercase tracking-wide`), counter "X / 25 sender" mono digits |

**Locked decisions:**
- KHÔNG dùng `text-base` (16px) trong Phase 8 — body chuẩn của codebase là 14px (`text-sm`), khớp `TopSendersPanel.tsx:50` (`text-sm font-medium`).
- KHÔNG dùng `text-3xl` trở lên — không có hero text trong `/cleanup/*`.
- Hard cap counter "X / 25" + "Y / 2000" hiển thị **bằng `font-mono tabular-nums`** để đếm số ổn định khi user check / uncheck — pattern y hệt `formatCompactCount` trong `TopSendersPanel.tsx:90`.
- Empty state heading dùng `text-lg font-semibold` (section heading size), không bump lên page heading.

---

## Color

Phase 8 dùng 60/30/10 contract đã locked bởi `globals.css`. **Không tạo token mới.**

| Role | Token (CSS var) | Hex (light) | Hex (dark) | Usage trong Phase 8 |
|------|-----------------|-------------|------------|---------------------|
| Dominant 60% | `--background` | `#F9F8FD` | `#191724` | Page background, table row background |
| Secondary 30% | `--card` + `--muted` + `--sidebar` | `#FFFFFF` / `#F4F2F9` / `#F9F8FD` | `#211E30` / `#2A263D` / `#191724` | Card surface, sidebar, muted helper text background |
| Accent 10% (primary) | `--primary` | `#0E5E5A` (teal) | `#6FB3A8` | **Reserved-for** list bên dưới |
| Destructive | `--destructive` + `--red` + `--red-soft` | `#B0413E` / `#B0413E` / `#F5D7D2` | `#DA8480` / `#DA8480` / `#3A1F1D` | Destructive confirm button, `SUPPRESSED_BLOCKED` badge, cap-exceeded counter |
| Warning | `--warning` + `--amber` + `--amber-soft` | `#E3A023` / `#E3A023` / `#FDE8BA` | `#F5CD7A` / `#F5CD7A` / `#3D3219` | "Sắp hết undo window" tooltip, partial-success banner |
| Success / safe | `--green` + `--green-soft` | `#2F7D5C` / `#D8EAD8` | `#6FB389` / `#1F3A28` | `SAFE` risk badge background + foreground, per-sender state `OK` |
| Muted / disabled | `--muted` + `--muted-foreground` | `#F4F2F9` / `#625C78` | `#2A263D` / `#928DAA` | `NO_HEADER_DISABLED` badge, disabled checkbox row, "Undo expired" tooltip |
| Chart neutrals | `--chart-1` (`#0E5E5A` teal) | — | — | Overall campaign progress bar fill |

### Accent reserved-for (Phase 8 — explicit list)

Token `--primary` (teal) **chỉ** áp dụng cho:

1. Primary CTA button "Preview campaign" trên page `/cleanup/unsubscribe-campaign` (variant `default`).
2. Primary CTA button "Execute campaign" trong Preview Dialog confirm action — **với caveat**: nếu count vượt cap, button đổi sang variant `destructive` để cảnh báo trước khi click.
3. Active state của sidebar nav item "Cleanup" (kế thừa `bg-[#E7F0EF]! text-[#0a3d3a]!` pattern từ `AppSidebar.tsx:107`).
4. Overall campaign progress bar fill (`Progress` component, `--chart-1`).
5. Focus ring (`--ring`) trên mọi interactive element (kế thừa shadcn defaults — không override).
6. `SAFE` risk badge — dùng `--green` / `--green-soft` (KHÔNG dùng `--primary` để tránh nhầm với CTA color).

**KHÔNG được dùng accent cho:** link inline, table header, helper text, table row hover, tooltip background, mọi non-CTA secondary action.

### Risk badge palette (LOCKED in CONTEXT D-15 mapping)

| Risk state | Badge variant | Background | Foreground | Border | Icon | Source token |
|------------|---------------|------------|------------|--------|------|--------------|
| `SAFE` | custom (compose from `Badge`) | `--green-soft` | `--green` | `transparent` | `ShieldCheck` (lucide) | locked CONTEXT D-15 |
| `NO_HEADER_DISABLED` | `secondary` (shadcn) | `--muted` | `--muted-foreground` | `transparent` | `Ban` (lucide) | locked CONTEXT D-15 |
| `SUPPRESSED_BLOCKED` | `destructive` (shadcn) | `--red-soft` | `--red` | `transparent` | `ShieldX` (lucide) | locked CONTEXT D-15 |

### Per-sender state palette (status page)

| State | Variant | Background | Foreground | Icon |
|-------|---------|------------|------------|------|
| `PENDING` | `secondary` | `--muted` | `--muted-foreground` | `Clock` |
| `RUNNING` | custom | `--blue-soft` | `--blue` | `Loader2` (animate-spin) |
| `OK` | custom | `--green-soft` | `--green` | `CheckCircle2` |
| `FAILED` | `destructive` | `--red-soft` | `--red` | `XCircle` |

### Cap-exceeded counter

`"X / 25 sender selected"` mặc định `text-muted-foreground`. Khi `X > 25` → `text-(--red) font-semibold`. Cùng quy tắc cho `"Y / 2000 mail"` trong preview dialog.

---

## Layout & Responsive

### Breakpoint contract (Tailwind 4 defaults)

| Breakpoint | min-width | Phase 8 behavior |
|------------|-----------|-------------------|
| Default (mobile) | 0 | 1 column stack, candidate list = cards (không phải table), Dialog full-screen sheet fallback |
| `sm` | 640px | Vẫn 1 column nhưng inline padding 20px |
| `md` | 768px | Candidate list chuyển từ card stack sang `Table`, sidebar mở collapsed-icon mode |
| `lg` | 1024px | Sidebar expanded, table có đủ cột |
| `xl` | 1280px | Page wraps trong `max-w-screen-xl mx-auto` (1200px content area khớp `AppShell` hiện có) |

### Page-level layouts

**`/cleanup/unsubscribe-campaign` (list):**
- Top: page heading + lead paragraph + suppression link inline (right-aligned).
- Sticky toolbar row: counter "X / 25 sender" + "Preview campaign" CTA button (disabled until X ≥ 1).
- Main: candidate table (md+) hoặc card list (mobile).
- Trống → empty state (xem Copywriting).

**Preview modal (shadcn `Dialog`):**
- `DialogContent` `max-w-2xl` (672px).
- Header: title "Xem trước campaign" + total counters (sender + mail) + cap warning nếu vượt.
- Body: scrollable `ScrollArea` (`max-h-[60vh]`) với per-sender summary row (sender email + method badge + history count + willArchive flag).
- Footer: 2 button — "Quay lại" (variant `outline`) + "Execute campaign" (variant `default`, hoặc `destructive` nếu cap warning).

**`/cleanup/unsubscribe-campaign/[jobId]` (status):**
- Top: breadcrumb "Cleanup / Unsubscribe / Campaign #{shortId}" + status badge ở mức campaign.
- Overall progress bar (`Progress` component, `--chart-1`).
- Per-sender table: sender email + state badge + archived mail count + retry button per FAILED row + undo banner ở top nếu campaign COMPLETED.

**`/cleanup/suppression`:**
- Top: page heading + lead.
- Inline form (above table): `Input` (email hoặc domain) + `Button` "Add to suppression" + helper text.
- Main: `Table` columns "Sender / Domain", "Source" (manual / replied / auto), "Added", "" (remove button).

### Sidebar nav update

Thêm 1 entry vào `AppSidebar.tsx` `MAIL_NAV` array (sau `nav.analytics`, trước `nav.needsReply`):

```ts
{ href: '/cleanup', labelKey: 'nav.cleanup', icon: Sparkles, /* TBD: Trash2 hoặc Recycle */ }
```

Icon **đề xuất**: `Recycle` (lucide) — gợi ý "dọn dẹp" tốt hơn `Sparkles` đã dùng cho AI.

**Sub-items:** Vì `AppSidebar.tsx` hiện chưa render sub-items, planner phải 1 trong 2 hướng:
- (A) Mở rộng `NavItem` type để hỗ trợ optional `children: NavItem[]` + render nested `SidebarMenuSub` (shadcn primitive).
- (B) Single entry "Cleanup" → `/cleanup` → page index list 2 links (Unsubscribe + Suppression), không nested sidebar.

**Recommend hướng (A)** vì SEED-009 sẽ thêm `/cleanup/bulk-archive`, `/cleanup/cold-email-blocker` — nested sub-menu scale tốt hơn.

`isActivePath('/cleanup/unsubscribe-campaign', '/cleanup/unsubscribe-campaign') === true` đã work với hàm `isActivePath` hiện tại (line 62-64).

---

## Component Composition Recipe

> Mọi component dưới đây compose từ 28 shadcn primitives đã có. **TUYỆT ĐỐI KHÔNG `pnpm dlx shadcn@latest add` mới.**

### `CandidateListTable` (file: `features/cleanup/unsubscribe-campaign/components/CandidateListTable.tsx`)

Compose: `Table` + `TableHeader` + `TableRow` + `Checkbox` (master + per-row) + `Badge` (risk) + `Tooltip` (NO_HEADER_DISABLED hint) + `Skeleton` (loading state).

Columns: `[checkbox]`, "Sender", "Domain", "Mail trong 30 ngày", "Phương thức unsubscribe", "Risk".

`<Checkbox disabled={method === 'NONE'} />` cho row `NO_HEADER_DISABLED`. Tooltip hiển thị khi hover: "Sender này không có header `List-Unsubscribe` — chưa thể unsubscribe tự động."

### `SelectionToolbar` (sticky row above table)

Compose: counter span (`font-mono tabular-nums`) + `Button` "Preview campaign" + ghost button "Clear selection".

```tsx
<div className="bg-card sticky top-0 z-10 flex items-center justify-between gap-4 border-b px-6 py-3">
  <span className={cn('font-mono text-xs tabular-nums', isOver && 'text-(--red) font-semibold')}>
    {count} / 25 sender đã chọn
  </span>
  <div className="flex items-center gap-2">
    <Button variant="ghost" size="sm" disabled={count === 0} onClick={onClear}>Bỏ chọn</Button>
    <Button variant="default" size="sm" disabled={count === 0 || count > 25} onClick={onPreview}>
      Xem trước campaign
    </Button>
  </div>
</div>
```

### `PreviewCampaignDialog`

Compose: `Dialog` + `DialogContent` + `DialogHeader` + `DialogTitle` + `DialogDescription` + `ScrollArea` + per-sender summary cards + 2-button footer.

Pattern cap warning trong header (`text-(--red)`): "Vượt cap 25 sender ({count}). Bỏ chọn bớt trước khi execute." Khi vượt cap, "Execute campaign" button `disabled`.

### `CampaignStatusPage`

Compose: `Card` (overall) + `Progress` + `Table` (per-sender) + `Badge` (state) + `Button` (retry per row) + top-level `Alert` (undo prompt nếu campaign COMPLETED & trong window).

TanStack Query hook (`features/cleanup/unsubscribe-campaign/hooks/useCampaignStatus.ts`):

```ts
useQuery({
  queryKey: unsubscribeCampaignKeys.byId(jobId),
  queryFn: () => fetchCampaignStatus(jobId),
  refetchInterval: (query) => {
    const status = query.state.data?.status;
    return status === 'QUEUED' || status === 'RUNNING' ? 2000 : false;
  },
});
```

### `SuppressionListPage`

Compose: `Input` + `Button` "Add" + `Table` + `Badge` (source: `manual` / `replied` / `auto`) + `AlertDialog` (confirm remove).

`replied` badge dùng variant `secondary` với icon `MessageCircleReply` (lucide).

### Empty state component

Reuse pattern: centered icon (`Inbox` size 32, `text-muted-foreground`) + heading (`text-lg font-semibold`) + body (`text-sm text-muted-foreground max-w-sm`) + optional CTA. Padding `py-16`.

### Skeleton loading

`features/cleanup/unsubscribe-campaign/components/CandidateListSkeleton.tsx`: 8 row `Skeleton` `h-12 w-full` với gap-2. Hiển thị khi `isLoading` && data chưa có.

---

## Copywriting Contract (tiếng Việt — i18n namespace `cleanup.*`)

### Page `/cleanup/unsubscribe-campaign` (candidate list)

| Element | Copy (vi) | i18n key đề xuất |
|---------|-----------|------------------|
| Page heading | "Bulk unsubscribe" | `cleanup.unsubscribe.list.title` |
| Page lead | "Chọn các newsletter bạn muốn dừng nhận. Zero Mail sẽ unsubscribe an toàn và archive lịch sử mail từ những sender đã chọn." | `cleanup.unsubscribe.list.lead` |
| Suppression link (top-right) | "Quản lý suppression list →" | `cleanup.unsubscribe.list.suppressionLink` |
| Counter | "{count} / 25 sender đã chọn" | `cleanup.unsubscribe.list.counter` |
| Counter over-cap | "{count} / 25 sender — vượt giới hạn" | `cleanup.unsubscribe.list.counterOver` |
| Primary CTA | "Xem trước campaign" | `cleanup.unsubscribe.list.preview` |
| Clear selection | "Bỏ chọn" | `cleanup.unsubscribe.list.clear` |
| Column: sender | "Sender" | `cleanup.unsubscribe.list.col.sender` |
| Column: domain | "Tên miền" | `cleanup.unsubscribe.list.col.domain` |
| Column: count | "Mail 30 ngày" | `cleanup.unsubscribe.list.col.count` |
| Column: method | "Phương thức" | `cleanup.unsubscribe.list.col.method` |
| Column: risk | "Trạng thái" | `cleanup.unsubscribe.list.col.risk` |
| Method `ONE_CLICK` label | "One-click (RFC 8058)" | `cleanup.unsubscribe.method.oneClick` |
| Method `MAILTO` label | "Mailto" | `cleanup.unsubscribe.method.mailto` |
| Risk `SAFE` | "Sẵn sàng" | `cleanup.unsubscribe.risk.safe` |
| Risk `NO_HEADER_DISABLED` | "Chưa hỗ trợ" | `cleanup.unsubscribe.risk.noHeader` |
| Risk `NO_HEADER_DISABLED` tooltip | "Sender này không có header `List-Unsubscribe` — chưa thể unsubscribe tự động." | `cleanup.unsubscribe.risk.noHeaderTooltip` |
| Risk `SUPPRESSED_BLOCKED` | "Đã chặn" | `cleanup.unsubscribe.risk.suppressed` |
| Empty state heading | "Chưa có newsletter nào trong 30 ngày qua" | `cleanup.unsubscribe.list.empty.title` |
| Empty state body | "Zero Mail phát hiện newsletter qua header `List-Unsubscribe` từ ingest gần đây. Khi có dữ liệu mới, sender ứng viên sẽ xuất hiện ở đây." | `cleanup.unsubscribe.list.empty.body` |
| Empty state link | "Tìm hiểu Zero Mail phát hiện newsletter thế nào →" | `cleanup.unsubscribe.list.empty.link` |
| Error state | "Không tải được danh sách sender. Hãy thử lại sau một chút." | `cleanup.unsubscribe.list.error` |
| Loading state | (skeleton — không text) | n/a |

### Preview dialog

| Element | Copy (vi) | i18n key |
|---------|-----------|----------|
| Dialog title | "Xem trước campaign" | `cleanup.unsubscribe.preview.title` |
| Dialog description | "Kiểm tra lại danh sách trước khi execute. Campaign sẽ chạy nền và có thể undo trong 30 ngày." | `cleanup.unsubscribe.preview.description` |
| Total sender label | "Tổng sender: {count}" | `cleanup.unsubscribe.preview.totalSender` |
| Total mail label | "Tổng mail sẽ archive: {count}" | `cleanup.unsubscribe.preview.totalMail` |
| Cap exceeded — too many senders | "Đã vượt giới hạn 25 sender. Quay lại và bỏ chọn bớt." | `cleanup.unsubscribe.preview.capSender` |
| Cap exceeded — too many messages | "Đã vượt giới hạn 2.000 mail lịch sử ({count}). Quay lại và bỏ chọn bớt." | `cleanup.unsubscribe.preview.capMessage` |
| Cancel button | "Quay lại" | `cleanup.unsubscribe.preview.cancel` |
| Confirm button | "Execute campaign" | `cleanup.unsubscribe.preview.confirm` |
| Sender row: will archive count | "{count} mail sẽ archive" | `cleanup.unsubscribe.preview.willArchive` |
| Sender row: will not archive | "Không archive (thiếu header)" | `cleanup.unsubscribe.preview.willNotArchive` |
| Toast: submit OK | "Campaign đã được tạo. Đang theo dõi tiến độ…" | `cleanup.unsubscribe.preview.submitOk` |
| Toast: cap reject (400 CAMPAIGN_TOO_MANY_SENDERS) | "Vượt giới hạn 25 sender. Hãy bỏ chọn bớt." | `cleanup.unsubscribe.preview.errCapSender` |
| Toast: cap reject (400 CAMPAIGN_TOO_MANY_MESSAGES) | "Vượt giới hạn 2.000 mail lịch sử. Hãy bỏ chọn bớt." | `cleanup.unsubscribe.preview.errCapMessage` |
| Toast: generic 4xx/5xx | "Không tạo được campaign. Hãy thử lại sau một chút." | `cleanup.unsubscribe.preview.errGeneric` |

### Status page

| Element | Copy (vi) | i18n key |
|---------|-----------|----------|
| Page heading | "Campaign #{shortId}" | `cleanup.unsubscribe.status.title` |
| Breadcrumb | "Cleanup / Unsubscribe / #{shortId}" | `cleanup.unsubscribe.status.breadcrumb` |
| Status QUEUED | "Đang chờ worker pick" | `cleanup.unsubscribe.status.queued` |
| Status RUNNING | "Đang chạy" | `cleanup.unsubscribe.status.running` |
| Status COMPLETED | "Hoàn tất" | `cleanup.unsubscribe.status.completed` |
| Status FAILED | "Lỗi — không hoàn tất" | `cleanup.unsubscribe.status.failed` |
| Progress label | "{percent}% ({okCount} OK / {failedCount} lỗi / {totalCount} sender)" | `cleanup.unsubscribe.status.progress` |
| Per-sender column: state | "Trạng thái" | `cleanup.unsubscribe.status.col.state` |
| Per-sender column: archived | "Mail đã archive" | `cleanup.unsubscribe.status.col.archived` |
| Per-sender state PENDING | "Chờ" | `cleanup.unsubscribe.status.state.pending` |
| Per-sender state RUNNING | "Đang chạy" | `cleanup.unsubscribe.status.state.running` |
| Per-sender state OK | "Thành công" | `cleanup.unsubscribe.status.state.ok` |
| Per-sender state FAILED | "Thất bại" | `cleanup.unsubscribe.status.state.failed` |
| Retry button | "Thử lại" | `cleanup.unsubscribe.status.retry` |
| Retry toast OK | "Đã enqueue thử lại cho {sender}." | `cleanup.unsubscribe.status.retryOk` |
| Retry already-OK toast (HTTP 409) | "Sender này đã unsubscribe thành công, không cần thử lại." | `cleanup.unsubscribe.retry.alreadyOk` |
| Undo banner heading (COMPLETED) | "Campaign đã hoàn tất" | `cleanup.unsubscribe.status.undo.title` |
| Undo banner body | "Bạn có thể undo trong {daysLeft} ngày tới: restore mail về INBOX và remove label `Zero Mail/Unsubscribed`." | `cleanup.unsubscribe.status.undo.body` |
| Undo button | "Undo campaign" | `cleanup.unsubscribe.status.undo.button` |
| Undo confirm dialog title | "Undo campaign?" | `cleanup.unsubscribe.undo.confirmTitle` |
| Undo confirm dialog body | "Sẽ restore {count} mail về INBOX và remove label `Zero Mail/Unsubscribed`. Hành động này không thể đảo ngược lần nữa." | `cleanup.unsubscribe.undo.confirmBody` |
| Undo confirm CTA | "Đồng ý undo" | `cleanup.unsubscribe.undo.confirmCta` |
| Undo cancel | "Quay lại" | `cleanup.unsubscribe.undo.cancel` |
| Undo expired tooltip | "Đã quá 30 ngày kể từ khi campaign chạy — không undo được nữa." | `cleanup.unsubscribe.undo.windowExpired` |
| Undo expired toast (HTTP 410) | "Quá window 30 ngày — không undo được nữa." | `cleanup.unsubscribe.undo.windowExpiredToast` |
| Undo OK toast | "Đã restore {count} mail về INBOX." | `cleanup.unsubscribe.undo.ok` |

### Suppression page

| Element | Copy (vi) | i18n key |
|---------|-----------|----------|
| Page heading | "Suppression list" | `cleanup.suppression.title` |
| Page lead | "Sender hoặc domain trong danh sách này sẽ không bao giờ hiển thị trong campaign unsubscribe." | `cleanup.suppression.lead` |
| Input placeholder | "Email hoặc domain (ví dụ: boss@example.com hoặc example.com)" | `cleanup.suppression.input.placeholder` |
| Add button | "Thêm vào suppression" | `cleanup.suppression.add` |
| Add helper | "Mỗi entry chặn 1 sender hoặc cả 1 domain." | `cleanup.suppression.helper` |
| Column: target | "Sender / Domain" | `cleanup.suppression.col.target` |
| Column: source | "Nguồn" | `cleanup.suppression.col.source` |
| Column: added | "Thêm lúc" | `cleanup.suppression.col.added` |
| Source `manual` | "Thủ công" | `cleanup.suppression.source.manual` |
| Source `replied` | "Đã reply" | `cleanup.suppression.source.replied` |
| Source `auto` | "Tự động" | `cleanup.suppression.source.auto` |
| Remove button (aria-label) | "Xóa khỏi suppression" | `cleanup.suppression.remove.aria` |
| Remove confirm dialog title | "Xóa entry này?" | `cleanup.suppression.remove.confirmTitle` |
| Remove confirm body | "Sau khi xóa, sender / domain này sẽ lại xuất hiện trong campaign ứng viên." | `cleanup.suppression.remove.confirmBody` |
| Remove confirm CTA | "Đồng ý xóa" | `cleanup.suppression.remove.confirmCta` |
| Add error: invalid format | "Email hoặc domain không hợp lệ." | `cleanup.suppression.err.invalid` |
| Add error: duplicate | "Entry này đã có trong suppression list." | `cleanup.suppression.err.duplicate` |
| Empty state title | "Chưa có sender nào trong suppression list" | `cleanup.suppression.empty.title` |
| Empty state body | "Thêm sender hoặc domain bạn không bao giờ muốn unsubscribe (ví dụ: sếp, đồng nghiệp, ngân hàng)." | `cleanup.suppression.empty.body` |

### Sidebar nav

| Element | Copy (vi) | i18n key |
|---------|-----------|----------|
| Sidebar entry | "Cleanup" | `nav.cleanup` |
| Sub-item 1 | "Unsubscribe" | `nav.cleanup.unsubscribe` |
| Sub-item 2 | "Suppression" | `nav.cleanup.suppression` |

### Privacy / sensitive copy rules

- Toast / error message **KHÔNG được chứa full `sender_email`** (Phase 1 privacy invariant). Nếu reference 1 sender cụ thể trong toast (ví dụ retry-already-ok), hiển thị **local-part-masked** (`***@example.com`) — KHÔNG hiển thị full. Trong UI **table** OK hiển thị full vì user vừa chọn từ list của họ.
- Mọi câu hiển thị thẳng email user dùng `<Tooltip>` (như `TopSendersPanel.tsx:83-88`) để ellipsis-safe khi dài.

### Tone of voice

- Tiếng Việt — câu thông tin: trung lập, không gọi user là "bạn ơi", không emoji.
- Destructive copy: ngắn gọn, nêu hậu quả (`không thể đảo ngược lần nữa`), không hù dọa.
- Error: 1 câu mô tả + 1 câu action (`Hãy thử lại sau một chút.` / `Hãy bỏ chọn bớt.`).

---

## State Coverage

Mỗi page MUST handle 6 trạng thái. Check list dưới đây bắt buộc đạt sign-off bởi `gsd-ui-checker`.

### Candidate list page

| State | Trigger | Visual |
|-------|---------|--------|
| Loading (initial) | TanStack `isLoading` | `CandidateListSkeleton` (8 rows, h-12 skeleton) trong vị trí table |
| Empty | API trả `[]` | Empty state centered, icon `Inbox`, heading + body + outbound link |
| Error | TanStack `isError` | `Alert variant="destructive"` ở top với copy `cleanup.unsubscribe.list.error` + button "Thử lại" gọi `refetch()` |
| Success | data > 0 | Normal table render |
| Disabled row | sender với `unsubscribeMethod === 'NONE'` | Checkbox `disabled`, row `opacity-60`, badge `NO_HEADER_DISABLED`, tooltip on hover |
| Selecting / over-cap | `selectedCount > 25` | Counter `text-(--red) font-semibold`, "Xem trước" button `disabled`, ghost helper text dưới counter "Vượt giới hạn 25 sender" |

### Preview dialog

| State | Trigger | Visual |
|-------|---------|--------|
| Open | user click "Xem trước" | `Dialog` mở với data đã preview-fetch |
| Loading preview | preview API in-flight | `Skeleton` per-sender row trong `ScrollArea` |
| Cap-exceeded (sender) | server reject 400 `CAMPAIGN_TOO_MANY_SENDERS` | Header alert đỏ + execute disabled |
| Cap-exceeded (mail) | total > 2000 | Header alert đỏ + execute disabled |
| Submitting | execute API in-flight | Confirm button → `Loader2` spin + label "Đang tạo…" + disabled |
| Success | 201 jobId trả | Đóng dialog + `router.push('/cleanup/unsubscribe-campaign/{jobId}')` + Sonner toast OK |
| Error | 4xx/5xx | Sonner toast destructive với copy + dialog vẫn mở để user retry |

### Status page

| State | Trigger | Visual |
|-------|---------|--------|
| Initial load | TanStack `isLoading` | Skeleton overall + skeleton table rows |
| Polling RUNNING | `status ∈ {QUEUED, RUNNING}` | `refetchInterval: 2000`, progress bar animate, per-sender RUNNING rows hiển thị Loader2 spin |
| Terminal COMPLETED | `status === 'COMPLETED'` | Polling dừng, hiển thị undo banner ở top |
| Terminal FAILED | `status === 'FAILED'` | Polling dừng, banner đỏ "Lỗi hệ thống — không hoàn tất. Liên hệ support nếu cần." (planner xác nhận copy) |
| Retry-pending | user click "Thử lại" trên row FAILED | Button → Loader2, row state quay về `PENDING` ở next poll |
| Retry-already-OK | server 409 | Sonner toast `cleanup.unsubscribe.retry.alreadyOk` |
| Undo available | COMPLETED & `now < appliedAt + 30d` | Banner amber với button "Undo campaign" |
| Undo expired | `now > appliedAt + 30d` | Button vẫn hiển thị nhưng `disabled` + tooltip `cleanup.unsubscribe.undo.windowExpired` |

### Suppression page

| State | Trigger | Visual |
|-------|---------|--------|
| Loading | initial fetch | Skeleton 5 row |
| Empty | API trả `[]` | Empty state centered |
| Adding | POST in-flight | Add button disabled + Loader2 |
| Added | 201 | Sonner toast OK, list re-fetch |
| Duplicate (409) | server reject | `Input` field viền đỏ + helper text đỏ `cleanup.suppression.err.duplicate` |
| Invalid format (400) | client-side regex fail | Tương tự duplicate, copy `cleanup.suppression.err.invalid` |
| Removing | DELETE in-flight | Row `opacity-50` + spinner replace remove icon |
| Removed | 204 | Sonner toast OK, list re-fetch |

---

## Accessibility (WCAG AA target)

| Concern | Phase 8 contract |
|---------|------------------|
| Color contrast | Mọi badge / button đạt 4.5:1 (verified bằng combo `--green-soft` / `--green`, `--red-soft` / `--red`, `--muted` / `--muted-foreground` trong cả light + dark). Disabled badge ở light: `--muted-foreground #625C78` trên `--muted #F4F2F9` = 5.4:1 ✓. Disabled dark: `#928DAA` trên `#2A263D` = 5.7:1 ✓. |
| Focus ring | Mọi interactive element thừa kế `outline-ring/50` (`globals.css:225`) — KHÔNG override. Checkbox + button shadcn defaults đã đạt. |
| Keyboard nav | Table row `<tr>` không focusable; checkbox + retry button focusable. `Tab` order: top toolbar (clear → preview) → table master checkbox → row 1 checkbox → row 1 retry (nếu có) → row 2… |
| Screen reader | `aria-label="Xóa khỏi suppression"` cho icon-only button. `<th scope="col">` trên table header. `<Progress>` shadcn primitive đã có `role="progressbar"` + `aria-valuenow`. `aria-live="polite"` trên counter span để screen reader đọc số khi user check / uncheck. |
| Touch targets | Checkbox 16×16px + row padding `py-3 px-4` → tap area row ≥ 44×44. Retry button `size="sm"` height 32px — nâng lên `size="default"` (h-9 = 36px) trên mobile, hoặc thêm `min-h-11 sm:min-h-9` modifier. |
| Disabled affordance | Disabled checkbox: opacity 50% + `cursor-not-allowed` (shadcn default) + tooltip giải thích. KHÔNG ẩn checkbox. |
| Loading announce | TanStack mutation loading toast dùng `<Sonner>` `role="status"` (default). KHÔNG hiển thị `loading...` bằng vanilla text. |
| Motion | Spinner `animate-spin` honored `prefers-reduced-motion` qua Tailwind default — không cần override. Progress bar fill là CSS width transition, không loop animation. |
| Dark mode | Mọi token có dark variant trong `:root.dark` block của `globals.css`. Risk badges đã spec cả light + dark hex above. |

---

## Implementation Guidance (cho gsd-planner / gsd-executor)

### File structure (locked CONTEXT D-13)

```
apps/web/
├── app/(authed)/cleanup/
│   ├── layout.tsx                                  # optional shared chrome
│   ├── page.tsx                                    # /cleanup index — redirect → /cleanup/unsubscribe-campaign
│   ├── unsubscribe-campaign/
│   │   ├── page.tsx                                # candidate list page
│   │   └── [jobId]/page.tsx                        # status page
│   └── suppression/
│       └── page.tsx
└── features/cleanup/
    ├── unsubscribe-campaign/
    │   ├── api/unsubscribe-campaign-api.ts          # openapi-fetch wrappers
    │   ├── hooks/
    │   │   ├── useCandidates.ts                     # useQuery
    │   │   ├── usePreviewCampaign.ts                # useMutation
    │   │   ├── useExecuteCampaign.ts                # useMutation
    │   │   ├── useCampaignStatus.ts                 # useQuery with refetchInterval
    │   │   ├── useRetrySender.ts                    # useMutation
    │   │   └── useUndoCampaign.ts                   # useMutation
    │   ├── components/
    │   │   ├── CandidateListPage.tsx                # client component
    │   │   ├── CandidateListTable.tsx
    │   │   ├── CandidateListSkeleton.tsx
    │   │   ├── SelectionToolbar.tsx
    │   │   ├── PreviewCampaignDialog.tsx
    │   │   ├── RiskBadge.tsx
    │   │   ├── MethodBadge.tsx
    │   │   ├── CampaignStatusPage.tsx
    │   │   ├── PerSenderStateTable.tsx
    │   │   ├── PerSenderStateBadge.tsx
    │   │   ├── UndoBanner.tsx
    │   │   └── UndoConfirmDialog.tsx
    │   ├── query-keys.ts                            # unsubscribeCampaignKeys
    │   └── __tests__/                               # Vitest co-located
    └── suppression/
        ├── api/suppression-api.ts
        ├── hooks/
        │   ├── useSuppressionList.ts
        │   ├── useAddSuppression.ts
        │   └── useRemoveSuppression.ts
        ├── components/
        │   ├── SuppressionListPage.tsx
        │   ├── SuppressionTable.tsx
        │   ├── SuppressionAddForm.tsx
        │   ├── SuppressionSourceBadge.tsx
        │   └── RemoveConfirmDialog.tsx
        ├── query-keys.ts                            # suppressionKeys
        └── __tests__/
```

### Query key factory (locked CONTEXT D-13, match Phase 7 pattern)

```ts
// features/cleanup/unsubscribe-campaign/query-keys.ts
export const unsubscribeCampaignKeys = {
  all: ['cleanup', 'unsubscribe-campaign'] as const,
  candidates: (window: string) => [...unsubscribeCampaignKeys.all, 'candidates', window] as const,
  byId: (jobId: string) => [...unsubscribeCampaignKeys.all, 'detail', jobId] as const,
} as const;

// features/cleanup/suppression/query-keys.ts
export const suppressionKeys = {
  all: ['cleanup', 'suppression'] as const,
  list: () => [...suppressionKeys.all, 'list'] as const,
} as const;
```

### shadcn primitive usage matrix

| Primitive | Phase 8 component | Note |
|-----------|-------------------|------|
| `button` | mọi CTA + retry + remove | variants: default, destructive, outline, ghost, secondary |
| `dialog` | `PreviewCampaignDialog` | NOT `sheet`; modal not drawer |
| `alert-dialog` | undo confirm + remove confirm | dùng cho destructive flow |
| `alert` | undo banner + status FAILED banner + cap warning | variants: default, destructive |
| `table` | candidate list + status per-sender + suppression list | default styles |
| `badge` | risk + method + per-sender state + suppression source | composable variants |
| `checkbox` | multi-select + master select all | shadcn default |
| `progress` | overall campaign progress | `value={progressPct}` |
| `skeleton` | loading states | matrix above |
| `tooltip` | NO_HEADER_DISABLED + undo expired + truncated email | shadcn default |
| `sonner` | toast notifications | success + error per matrix |
| `input` | suppression add form | type=text |
| `scroll-area` | preview dialog body | `max-h-[60vh]` |
| `separator` | section dividers | optional |
| `sidebar` | nav update | extend MAIL_NAV |

### i18n keys (lock-step `vi.json` + `en.json`)

Đã liệt kê toàn bộ trong Copywriting Contract above. Planner KHÔNG được thêm key ngoài list — nếu cần, ghi lại trong PLAN.md.

### Routing rules

- `/cleanup` → `redirect('/cleanup/unsubscribe-campaign')` (Next.js Server Component redirect).
- `/cleanup/unsubscribe-campaign/[jobId]` → validate `jobId` là UUID v4 ở server boundary; 404 nếu không match shape.
- Status page sau khi terminal: pollingdừng nhưng KHÔNG redirect (giữ URL để user share / refresh).

### Data flow

1. User vào `/cleanup/unsubscribe-campaign` → `useCandidates` fetch `GET /api/unsubscribe/candidates?window=30d&limit=25` (cache `staleTime: 60s`).
2. User check N rows → state in-memory React (`useState<Set<string>>` của senderEmail). KHÔNG persist localStorage (privacy + user expectation).
3. Click "Xem trước" → `usePreviewCampaign` mutation `POST /api/unsubscribe/campaigns/preview`. Hiển thị dialog với response.
4. Click "Execute" trong dialog → `useExecuteCampaign` mutation `POST /api/unsubscribe/campaigns/execute`. Trả jobId → `router.push('/cleanup/unsubscribe-campaign/{jobId}')`.
5. Status page → `useCampaignStatus(jobId)` với `refetchInterval` conditional.
6. Retry per FAILED → `useRetrySender` mutation, invalidate `unsubscribeCampaignKeys.byId(jobId)`.
7. Undo → `useUndoCampaign` mutation, sau success invalidate query + redirect về list (planner xác nhận redirect target).

### OpenAPI codegen

Backend `springdoc-openapi` sẽ regen `apps/web/lib/api/openapi-types.ts`. Hooks dùng `openapi-fetch` typed client — KHÔNG hand-roll fetch.

### Playwright e2e (UNS-05 golden path)

Test file: `apps/web/e2e/cleanup/unsubscribe-campaign.spec.ts`.

Golden path:
1. Login → goto `/cleanup/unsubscribe-campaign`.
2. Assert 3 fixture rows hiển thị (2 SAFE + 1 NO_HEADER_DISABLED).
3. Check 2 SAFE rows → assert counter "2 / 25 sender đã chọn".
4. Click "Xem trước campaign" → dialog mở.
5. Assert preview content (per-sender summary).
6. Click "Execute campaign" → redirect tới status page.
7. Wait for `status=COMPLETED` (polling).
8. Assert undo banner visible.
9. Suppression: goto `/cleanup/suppression` → add `boss@example.com` → assert row appears.

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | 28 primitives đã có sẵn — không add new (button, dialog, alert-dialog, alert, table, badge, checkbox, progress, skeleton, tooltip, sonner, input, scroll-area, separator, sidebar, card, popover, dropdown-menu, label, select, switch, tabs, textarea, toggle, toggle-group, command, avatar, chart, sheet, radio-group, input-group) | not required (official) |
| Third-party registries | NONE | not applicable — `components.json` `registries: {}` |

Registry vetting gate: **not executed** vì không declared third-party block. Nếu planner / executor sau này cần install primitive mới (e.g., shadcn `data-table` block) phải re-run UI-spec gate.

---

## Prototype File

`08-PROTOTYPE.html` đi kèm spec này — single-file HTML mockup theo CLAUDE.md "UI Phase Prototype Rule". 4 màn chính + 6 trạng thái + light/dark toggle. Tailwind CDN, inline CSS, không build step. Throwaway artifact để visual review trước khi planner viết PLAN.md.

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PENDING
- [ ] Dimension 2 Visuals: PENDING
- [ ] Dimension 3 Color: PENDING
- [ ] Dimension 4 Typography: PENDING
- [ ] Dimension 5 Spacing: PENDING
- [ ] Dimension 6 Registry Safety: PENDING

**Approval:** pending

---

## UI-SPEC COMPLETE

**Phase:** 8 — bulk-unsubscribe-campaign
**Design System:** shadcn (base-nova preset, existing)

### Contract Summary
- Spacing: 4/8/12/16/24/32/48/64 px scale; row exception `py-3 px-4`; sidebar `h-9` locked from existing
- Typography: 4 sizes (12/14/18/24px), 2 weights (400 + 600), line-heights 1.2 heading / 1.5 body
- Color: 60% paper-warm neutral (`--background`), 30% card+sidebar+muted, 10% teal `--primary` reserved for 6 explicit elements; risk badge palette mapped to `--green-soft/--green`, `--muted/--muted-foreground`, `--red-soft/--red`
- Copywriting: ~75 i18n keys defined under `cleanup.unsubscribe.*` + `cleanup.suppression.*` + `nav.cleanup.*` namespaces (vi only; planner mirrors en)
- Registry: shadcn official only, 28 existing primitives, zero third-party

### File Created
`D:/Semester-8/zero-mail/.planning/phases/08-bulk-unsubscribe-campaign/08-UI-SPEC.md`
`D:/Semester-8/zero-mail/.planning/phases/08-bulk-unsubscribe-campaign/08-PROTOTYPE.html`

### Pre-Populated From
| Source | Decisions Used |
|--------|---------------|
| CONTEXT.md (D-12..D-15) | 4 (route layout, feature folder, entry point, polling) |
| 08-SPEC.md (UNS-01..UNS-09) | 9 (caps, risk badge meaning, endpoint shapes) |
| 08-RESEARCH.md | 5 (shadcn matrix, query-key pattern, RestClient, throttle, polling cadence) |
| `apps/web/components.json` | preset base-nova, lucide icons, neutral, cssVariables true |
| `apps/web/app/globals.css` | all tokens (40+ CSS vars) — zero new tokens introduced |
| `apps/web/components/shell/AppSidebar.tsx` | nav pattern, `MAIL_NAV` extend point, `isActivePath`, sidebar height + collapsed-icon mode |
| `apps/web/features/analytics/*` | feature folder shape, query-key factory, `Card + Table + Badge` composition (Phase 7) |
| Orchestrator additional_context | risk palette tokens, hard caps, error i18n keys, prototype rule |
| User input (this session) | 0 — all questions pre-answered upstream |

### Ready for Verification
UI-SPEC complete. Checker can now validate 6 dimensions.
