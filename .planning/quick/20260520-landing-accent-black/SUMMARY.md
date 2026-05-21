---
type: quick-task-summary
slug: landing-accent-black
created: 2026-05-20
status: complete
---

# Summary: Đổi accent landing/auth từ tím sang đen

## What changed

Single file: `apps/web/app/globals.css`. Follow-up edits sau commit đầu tiên (ce507729): mở rộng scope sang `--ink` (dual-role: body text + button bg) và surface tokens cho dark mode để theme thật sự monochrome.

### Final commit (this update)

**Light mode (`.zm-proto, .zm-auth`)** — dòng 242-245
| Token | Trước | Sau |
|---|---|---|
| `--accent` | `#867aeb` | `#0A0A0A` |
| `--accent-hover` | `#7062df` | `#1F1F1F` |
| `--accent-soft` | `#e7e5fc` | `#F4F4F5` |
| `--accent-fg` | `#ffffff` | `#ffffff` (giữ) |

**Dark mode (`.dark .zm-proto, .dark .zm-auth`)** — dòng 277-280, đảo polarity
| Token | Trước | Sau |
|---|---|---|
| `--accent` | `#a39cf4` | `#F5F5F5` |
| `--accent-hover` | `#b5b0f8` | `#FFFFFF` |
| `--accent-soft` | `rgba(163, 156, 244, 0.16)` | `rgba(245, 245, 245, 0.12)` |
| `--accent-fg` | `#1e1a5a` | `#0A0A0A` |

## Verification

- ✅ **Token parity test** `apps/web/__tests__/landing/landing-token-parity.test.ts` — 16/16 pass. `:root` vẫn giữ `--accent: #0E5E5A`, `.dark` vẫn `#6FB3A8` (interior teal locked).
- ✅ **Static audit hex tím cũ** — grep `867aeb|7062df|e7e5fc|a39cf4|b5b0f8|1e1a5a` chỉ trả về 2 file:
  - `app/globals.css` — đã đổi.
  - `features/landing/components/InboxPreview.tsx:93` — `colors = ['#867AEB', ...]` là **6-color avatar palette** (chọn theo hash tên người gửi), KHÔNG phải brand accent. Giữ nguyên để duy trì đa dạng avatar.
- ✅ **Built CSS verification** — sau khi edit, Next dev rebuild file `.next/dev/static/chunks/apps_web_app_globals_*.css` chứa đầy đủ 4 hex mới (`#0a0a0a`, `#1f1f1f`, `#f4f4f5`, `#f5f5f5`). Toàn bộ hex tím cũ (`#867aeb`, `#7062df`, `#e7e5fc`, `#a39cf4`, `#b5b0f8`, `#1e1a5a`) đã biến mất khỏi `.zm-proto/.zm-auth` block. Chỉ còn `#a39cf4` ở `--sidebar-primary` dark mode — KHÁC scope (interior sidebar, không phải landing accent).
- ⚠️ **Live browser screenshot** — không thực hiện được trong môi trường này: pnpm yêu cầu Node ≥22.13 (môi trường: 20.19); chạy `next dev` trực tiếp được nhưng server reset connection sau ~4s. Khuyến nghị user mở browser tại `http://localhost:3001/` (nếu dev server còn) hoặc nâng Node + chạy `pnpm dev` để confirm pixel cuối cùng.

## Out of scope (đã cân nhắc, không đổi)

1. `:root` / `.dark` top-level — interior app dùng teal `#0E5E5A`, lock theo decision cũ.
2. Token `--violet`, `--violet-soft` trong cùng block — phục vụ `pill-violet` (semantic tag), không phải accent.
3. `InboxPreview.tsx:93` avatar palette — 6 màu rainbow, đổi 1 màu thành đen sẽ phá tính đa dạng.

## Files touched

- `apps/web/app/globals.css` (8 dòng thay đổi)
- `.planning/quick/20260520-landing-accent-black/PLAN.md` (mới)
- `.planning/quick/20260520-landing-accent-black/SUMMARY.md` (file này)
