---
quick_id: 260525-lt4
slug: fix-accent-soft-contrast
date: 2026-05-25
status: complete
---

# Summary: Fix bg-accent-soft text-accent contrast bug across the app

## What changed

Replaced `bg-accent-soft text-accent` (and `bg-accent-soft/60 text-accent` in one case) with `bg-accent-soft text-accent-foreground` in 9 occurrences across 7 source files. All foreground references kept token-based; only the token name changed from `text-accent` to `text-accent-foreground`.

Files:
- `apps/web/features/auth/components/StepIndicator.tsx` (completed step)
- `apps/web/app/(protected)/onboarding/template-select/TemplateSelectClient.tsx` (action pill)
- `apps/web/app/(protected)/onboarding/complete/CompleteClient.tsx` (3 occurrences: avatar circle, summary icon, status pill)
- `apps/web/features/chat/components/preview-card/preview-card.tsx` (status badge — the one user spotted)
- `apps/web/features/chat/components/conversation-pane.tsx` (2 Sparkles empty-state icons)
- `apps/web/features/billing/components/TopupInstructions.tsx` (Landmark icon)
- `apps/web/features/billing/components/BalanceCard.tsx` (WalletCards icon + beta-notice banner)

## Root cause recap

Light-mode tokens in `apps/web/app/globals.css`:
- `--accent: #EAE8F7` and `--accent-soft: #EAE8F7` resolve to the same hex
- The intended readable foreground is `--accent-foreground: #5849C9`

So `bg-accent-soft text-accent` rendered text in the same color as the background → invisible. The correct shadcn-style pairing is `bg-accent-soft text-accent-foreground`.

## Verification

- `pnpm --filter web typecheck` — pass.
- `pnpm --filter web lint` — pass (only the pre-existing `coverage/lcov-report` warning).
- `apps/admin` grep — no matches; nothing to fix there.

## Not done

- Did not touch `globals.css` token definitions. If product wants to make `--accent` and `--accent-soft` visually distinct in light mode (different tints), that's a separate design-token decision.
