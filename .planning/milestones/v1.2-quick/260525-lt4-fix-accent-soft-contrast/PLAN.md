---
quick_id: 260525-lt4
slug: fix-accent-soft-contrast
date: 2026-05-25
status: in-progress
---

# Quick Task: Fix bg-accent-soft text-accent contrast bug across the app

## Problem

User reported a "purple pill with no visible text" in the PreviewCard status badge. Root cause: `bg-accent-soft text-accent` produces a pill where both background AND foreground resolve to the same hex.

`apps/web/app/globals.css` light-mode tokens:
- `--accent: #EAE8F7` (light purple)
- `--accent-soft: #EAE8F7` (**SAME light purple**)
- `--accent-foreground: #5849C9` (dark purple — the readable foreground)

Dark mode is also broken: `--accent: #2F2A4A`, `--accent-soft: #1F3A37` — both very dark, low-contrast pair.

The correct pairing for `bg-accent-soft` is `text-accent-foreground` (consistent with how `bg-accent` + `text-accent-foreground` is used throughout shadcn primitives).

## Fix

Replace `bg-accent-soft text-accent` with `bg-accent-soft text-accent-foreground` in every source file. 9 source occurrences across 7 files:

- `apps/web/features/auth/components/StepIndicator.tsx:82` — completed-step indicator
- `apps/web/app/(protected)/onboarding/template-select/TemplateSelectClient.tsx:231` — action pill
- `apps/web/app/(protected)/onboarding/complete/CompleteClient.tsx:40, 82, 136` — 3 pills/icons
- `apps/web/features/chat/components/preview-card/preview-card.tsx:199` — status badge (the one user spotted)
- `apps/web/features/chat/components/conversation-pane.tsx:231, 239` — empty-state Sparkles icons
- `apps/web/features/billing/components/TopupInstructions.tsx:74` — Landmark icon background
- `apps/web/features/billing/components/BalanceCard.tsx:55` — WalletCards icon background
- `apps/web/features/billing/components/BalanceCard.tsx:86` — beta-notice banner (uses `bg-accent-soft/60`)

Per CLAUDE.md convention 11: "Never hardcode color hex values in primitives or feature components — consume tokens." The fix keeps all colors token-based; only the foreground token name changes.

`apps/admin` was grepped and has no occurrences.

## Verification

- `pnpm --filter web typecheck` — pass.
- `pnpm --filter web lint` — pass.
- Manual: PreviewCard confirmed/sent badge now shows "✓ Đã xác nhận" with dark-purple text on light-purple bg.
