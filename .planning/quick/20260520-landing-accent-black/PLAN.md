---
type: quick-task
slug: landing-accent-black
created: 2026-05-20
status: in-progress
---

# Quick Task: Đổi accent landing/auth từ tím sang đen

## Goal

Đổi token `--accent` của landing page (`.zm-proto`) và auth screens (`.zm-auth`) trong `apps/web/app/globals.css` từ tím (`#867aeb`) sang đen near-black (`#0A0A0A`). Dark mode đảo polarity sang trắng (`#F5F5F5`).

## Scope

- **In-scope:** chỉ block `.zm-proto, .zm-auth` (dòng ~240-273) và block `.dark .zm-proto, .dark .zm-auth` (dòng ~275-295) trong `apps/web/app/globals.css`.
- **Out-of-scope:**
  - `:root` / `.dark` top-level (interior teal `#0E5E5A` / `#6FB3A8` — locked).
  - Token `--violet`, `--violet-soft` (giữ nguyên semantic cho `pill-violet`).
  - Component files — không sửa Hero, CTA, Pricing, v.v. (chúng dùng `var(--accent)` đã pickup token mới).

## Locked decisions

- **Scope:** cả `.zm-proto` và `.zm-auth` cùng đổi (đã hỏi user → "Cả landing + auth").
- **Shade:** Near-black neutral `#0A0A0A` (đã hỏi user → "Near-black").
- **Dark mode policy:** Đảo polarity — accent thành trắng/sáng (đã hỏi user → "Đảo: accent thành trắng/sáng").

## Token mapping

### Light mode (`.zm-proto, .zm-auth`)

| Token | Cũ (tím) | Mới (đen) |
|---|---|---|
| `--accent` | `#867aeb` | `#0A0A0A` |
| `--accent-hover` | `#7062df` | `#1F1F1F` |
| `--accent-soft` | `#e7e5fc` | `#F4F4F5` |
| `--accent-fg` | `#ffffff` | `#ffffff` (giữ nguyên) |

### Dark mode (`.dark .zm-proto, .dark .zm-auth`) — đảo polarity

| Token | Cũ (tím sáng) | Mới (trắng) |
|---|---|---|
| `--accent` | `#a39cf4` | `#F5F5F5` |
| `--accent-hover` | `#b5b0f8` | `#FFFFFF` |
| `--accent-soft` | `rgba(163, 156, 244, 0.16)` | `rgba(245, 245, 245, 0.12)` |
| `--accent-fg` | `#1e1a5a` | `#0A0A0A` |

## Tasks

1. Edit `apps/web/app/globals.css` light-mode block — 4 token thay đổi.
2. Edit `apps/web/app/globals.css` dark-mode block — 4 token thay đổi.
3. Run `pnpm --filter web test landing-token-parity` — xác nhận test parity vẫn pass (test chỉ check `:root` và `.dark` top-level, không check `.zm-proto`).
4. Visual check qua `pnpm --filter web dev` + Playwright/manual — xem Hero CTA, Pricing button, navbar đã đổi sang đen.

## Acceptance criteria

- `apps/web/app/globals.css` chứa `--accent: #0A0A0A` trong block `.zm-proto, .zm-auth`.
- `apps/web/app/globals.css` chứa `--accent: #F5F5F5` trong block `.dark .zm-proto, .dark .zm-auth`.
- Test `landing-token-parity.test.ts` vẫn pass.
- `:root` vẫn giữ `--accent: #0E5E5A` (interior teal — không bị ảnh hưởng).
- Token `--violet`, `--violet-soft` không đổi.
- Visual: landing hero CTA + pricing button render màu đen, không tím.

## Risks / non-risks

- **Non-risk:** Token `--accent-soft` ở dark mode chuyển từ `rgba` sang `rgba` — test parity chỉ check hex format trong `:root`/`.dark` top-level (interior), không check `.dark .zm-proto`, nên không ảnh hưởng.
- **Risk nhỏ:** Một số component có thể đã hard-code `#867aeb` thay vì dùng `var(--accent)`. Đã grep — chỉ 4 file ref token này, đều trong globals.css. An toàn.
