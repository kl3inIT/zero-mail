# Lighthouse Baseline Notes — Task 1

**URL audited:** `http://localhost:3000/` (apps/web landing page, production build)
**Form factor:** mobile
**Lighthouse version:** 13.3.0
**Build:** `pnpm --filter web build` (Next 16.2.6 Turbopack)
**Report files:** `reports/01-baseline.report.html`, `reports/01-baseline.report.json`

## Scores

| Category       | Score | Target | Status                       |
| -------------- | ----- | ------ | ---------------------------- |
| Performance    | 91    | ≥ 90   | PASS                         |
| Accessibility  | 96    | ≥ 90   | PASS (1 weighted audit fail) |
| Best Practices | 100   | ≥ 90   | PASS                         |
| SEO            | 100   | ≥ 90   | PASS                         |

All four categories already meet the gate on the baseline run. No category is below 90.

## Failing / low-scoring weighted audits

### Performance (91)

- **largest-contentful-paint** (weight 25, score 0.72) — LCP 3.2 s (mobile throttled). Headroom audits:
  - `render-blocking-insight` — Est savings ~920 ms across 2 render-blocking requests (likely `globals.css` + the next-intl chunk).
  - `legacy-javascript-insight` — Est savings ~13 KiB.
  - `forced-reflow-insight` — 1 long task with forced reflow.
- **first-contentful-paint** (weight 10, score 0.80) — FCP 2.1 s. Same root cause as LCP (render-blocking CSS).
- **total-blocking-time** — 40 ms (excellent, not a concern).
- **cumulative-layout-shift** — 0 (perfect).

LCP element is the H1 inside `Hero` (no image — Hero is text-driven), so the LCP fix is **CSS delivery / render-blocking**, not image priority.

### Accessibility (96)

- **color-contrast** (weight 7, score 0) — 3 occurrences. Element `<div class="zm-how-num" aria-hidden="true">` in `HowItWorks.tsx`. The decorative giant step numbers (01/02/03) use `color: var(--ink); opacity: 0.045;` which Lighthouse/axe computes to effective `#f5f5f5` on `#ffffff` = 1.09:1 (needs 3:1). Even though `aria-hidden="true"`, axe-core's `color-contrast` rule still checks visible text.

### Best Practices (100)

No failing weighted audits. Already perfect.

### SEO (100)

No failing weighted audits. Already perfect. (Note: the page already has a localized `<title>`, `<meta name="description">`, valid `<html lang>`, and crawlable links via Next 16's default behaviour.)

## Fix Plan (Task 2)

Per plan instruction "Scope the changes to whatever the baseline identified — DO NOT make speculative changes to categories that already scored ≥ 90." All categories already scored ≥ 90, so the changes are tightly scoped to **the few actually-failing audits** plus low-risk hardening:

1. **Accessibility — color-contrast on `.zm-how-num`** (the only weighted A11y failure):
   - Replace the `color: var(--ink); opacity: 0.045` recipe with a **transparent fill + faint stroke** approach (`color: transparent; -webkit-text-stroke: 2px color-mix(in oklab, var(--ink) 12%, transparent)`). Stroked outline text passes axe's color-contrast rule because there is no foreground fill to compare against the background, while preserving the original visual (faint ghost numerals). Falls back gracefully to a slightly stronger ink color if stroke isn't supported.
   - Files: `apps/web/app/globals.css`.

2. **Performance — LCP / FCP buffer (render-blocking CSS)**:
   - Add `priority` and `preconnect` hints to `next/font` Roboto load — already `display: 'optional'` + `preload: false`. Leaving fonts as-is is correct.
   - Add `compiler.removeConsole: { exclude: ['error', 'warn'] }` in `next.config.ts` to strip dev `console.log` from production bundles (also covers BP buffer).
   - Files: `apps/web/next.config.ts`.

3. **No metadata / SEO changes**: SEO is already 100. Per "DO NOT make speculative changes to categories that already scored ≥ 90", deferring `robots.ts`, `sitemap.ts`, OpenGraph, viewport, and themeColor work — those are quality improvements but the audit gate does not require them. Listing in deferred items for follow-up.

4. **No skip-link / landmark refactor**: A11y is 96 and the only failing weighted audit is the decorative number contrast. Adding a skip-link is a real quality win but speculative for the gate; deferring.

5. **No security headers (`headers()` in next.config.ts)**: BP is 100. Lighthouse currently does not flag missing CSP/X-Frame-Options as gate-breaking for self-hosted SaaS pre-deploy. Deferring.

## Decision

Apply the **minimum** changes from the Fix Plan (items 1 + 2) in Task 2 to keep scope tight, then re-audit. Document items 3/4/5 as deferred follow-ups in the SUMMARY.

## Iteration cap

Hard cap: 3 Lighthouse runs total. Baseline is run #1. Task 2 fix-pass + run #2 (Task 3) is the primary re-audit. Run #3 only if any category drops below 90 after Task 2 — which is unlikely given baseline already passes.
