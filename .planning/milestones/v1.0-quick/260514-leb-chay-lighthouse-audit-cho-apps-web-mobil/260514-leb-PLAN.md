---
quick_id: 260514-leb
slug: chay-lighthouse-audit-cho-apps-web-mobil
type: execute
wave: 1
depends_on: []
files_modified:
  - apps/web/app/layout.tsx
  - apps/web/app/(public)/layout.tsx
  - apps/web/app/(public)/page.tsx
  - apps/web/next.config.ts
  - apps/web/features/landing/components/Hero.tsx
  - apps/web/features/landing/components/TopBar.tsx
  - apps/web/features/landing/components/Footer.tsx
  - apps/web/features/landing/components/Features.tsx
  - apps/web/features/landing/components/HowItWorks.tsx
  - apps/web/features/landing/components/TrustPillars.tsx
  - apps/web/app/robots.ts
  - apps/web/app/sitemap.ts
autonomous: true
requirements:
  - LH-PERF-90
  - LH-A11Y-90
  - LH-BP-90
  - LH-SEO-90

must_haves:
  truths:
    - "Lighthouse mobile audit produces Performance ≥ 90 on the public landing page (http://localhost:3000/)"
    - "Lighthouse mobile audit produces Accessibility ≥ 90 on the same page"
    - "Lighthouse mobile audit produces Best Practices ≥ 90 on the same page"
    - "Lighthouse mobile audit produces SEO ≥ 90 on the same page"
    - "Baseline + final Lighthouse reports (JSON + HTML) are saved in the quick task directory under reports/"
    - "Audit runs against a production build (next build && next start), not next dev"
    - "If any category remains < 90 after iteration cap, remaining gaps are listed as deferred items — no infinite loop"
  artifacts:
    - path: ".planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/01-baseline.report.html"
      provides: "Baseline Lighthouse HTML report"
    - path: ".planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/01-baseline.report.json"
      provides: "Baseline Lighthouse JSON report (machine-readable scores)"
    - path: ".planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/02-after-fixes.report.html"
      provides: "Post-fix Lighthouse HTML report"
    - path: ".planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/02-after-fixes.report.json"
      provides: "Post-fix Lighthouse JSON report"
  key_links:
    - from: "production server (next start on :3000)"
      to: "Lighthouse CLI via pnpm dlx"
      via: "HTTP localhost:3000 with --preset=mobile"
      pattern: "pnpm dlx lighthouse http://localhost:3000.*--preset=mobile.*--output=json.*--output=html"
    - from: "Lighthouse JSON report"
      to: "score gate check"
      via: "node -e parse categories['performance'|'accessibility'|'best-practices'|'seo'].score * 100"
      pattern: "categories\\.(performance|accessibility|best-practices|seo)\\.score"
---

<objective>
Run a Lighthouse mobile audit against the production build of `apps/web` landing page, identify the lowest-scoring categories and top opportunities, apply 1–2 targeted fix passes, and re-audit until all 4 categories (Performance, Accessibility, Best Practices, SEO) are ≥ 90 — or up to a hard cap of 3 audit runs total, after which remaining gaps are reported as deferred.

Purpose: User wants production-grade quality signal on the public landing page before further marketing/feature work. Lighthouse is the cheapest, most universally-recognized check.

Output: Baseline + post-fix Lighthouse reports (HTML + JSON) saved in the quick task directory, plus targeted source fixes to `apps/web` landing page chain, plus a SUMMARY listing final scores and any deferred gaps.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@apps/web/CLAUDE.md
@apps/web/AGENTS.md
@apps/web/package.json
@apps/web/next.config.ts
@apps/web/app/layout.tsx
@apps/web/app/(public)/layout.tsx
@apps/web/app/(public)/page.tsx

<interfaces>
<!-- Key facts the executor needs to avoid codebase exploration. -->

Landing route composition (apps/web/app/(public)/page.tsx):
- Hero, HowItWorks, Features, TrustPillars stacked vertically under (public)/layout.tsx
- (public)/layout.tsx wraps content with TopBar + <main> + Footer
- Root layout (apps/web/app/layout.tsx) owns <html lang> + <body> + fonts (Roboto, Roboto_Mono via next/font/google, display: 'optional', preload: false)

Existing metadata wiring (apps/web/app/layout.tsx generateMetadata):
- Already provides localized title + description via next-intl
- Does NOT yet provide: openGraph, twitter, robots, viewport, themeColor, icons, metadataBase
- <html lang> is dynamically set from locale (vi|en)

Existing fonts:
- Roboto + Roboto_Mono via next/font/google
- display: 'optional', preload: false
- subsets: ['latin', 'vietnamese'] for Roboto, ['latin'] for Roboto_Mono

Next.js version: 16.2.6 (App Router) — APIs may differ from training data. Consult node_modules/next/dist/docs/ or Context7 before changing metadata / image / font APIs.

next.config.ts: Already has transpilePackages: ['next-mdx-remote']. No headers(), no images config, no compiler.removeConsole yet.

Production server command (from package.json):
- Build: `pnpm --filter web build` (runs pnpm i18n:build && next build)
- Start: `pnpm --filter web start` (next start, default port 3000)

Lighthouse runner: `pnpm dlx lighthouse@latest` — no install needed; Chrome must be available on PATH (chrome-launcher autodetects). On Windows, ensure Chrome is installed at the default Program Files location.
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Baseline Lighthouse audit against production build</name>
  <files>
    .planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/01-baseline.report.html,
    .planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/01-baseline.report.json,
    .planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/BASELINE-NOTES.md
  </files>
  <action>
    Create the `reports/` subfolder under the quick task directory. Then run a baseline mobile Lighthouse audit against a production build of `apps/web`:

    1. Create the reports directory: `mkdir -p .planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports` (PowerShell: `New-Item -ItemType Directory -Force -Path ...`).

    2. Build the production bundle from the repo root: `pnpm --filter web build`. This runs `pnpm i18n:build && next build`. Confirm build completes with zero errors before continuing — any build error blocks the audit.

    3. Start the production server in the background: `pnpm --filter web start` (binds to http://localhost:3000). Use `run_in_background: true` on the Bash tool. Capture the background process id so it can be killed at the end of the task.

    4. Wait for readiness: poll `http://localhost:3000/` with a short Node one-liner or `curl` retry loop (max 30s, 1s interval) until HTTP 200 is returned. If readiness never comes within 30s, stop the background server, report the error, and abort the task.

    5. Run Lighthouse against the landing page (root URL):

       ```
       pnpm dlx lighthouse@latest http://localhost:3000/ \
         --preset=mobile \
         --quiet \
         --chrome-flags="--headless=new --no-sandbox" \
         --output=json --output=html \
         --output-path=.planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/01-baseline.report
       ```

       Lighthouse will emit `01-baseline.report.json` and `01-baseline.report.html`. On Windows shells use single-line invocation (no backslash continuations) — keep the same args.

    6. Stop the background production server (kill the captured pid). Do not leave port 3000 occupied.

    7. Parse the JSON report and extract the four category scores (×100). Use a Node one-liner like: `node -e "const r=require('./...01-baseline.report.json'); const c=r.categories; console.log(JSON.stringify({performance:c.performance.score*100, accessibility:c.accessibility.score*100, bestPractices:c['best-practices'].score*100, seo:c.seo.score*100}, null, 2))"`.

    8. Also extract the top 5 `audits` with `score < 0.9` for each category that scored < 90 — sort by their `weight` (or by category-importance ordering) descending. These are the "opportunities" the next task will target.

    9. Write `BASELINE-NOTES.md` in the quick task root containing:
       - The four category scores (table form: Category | Score | Status (PASS/FAIL vs 90)).
       - For each FAIL category: top 3–5 failing/low-scoring audits with their `id`, short `title`, and `score`.
       - A short "Fix plan" section listing the concrete code-level changes proposed for Task 2 (e.g. "add metadata.openGraph + viewport in root layout", "add `<html lang>` skip-link", "set explicit width/height on Hero image", "enable `compiler.removeConsole` in next.config.ts", "add robots.ts + sitemap.ts").

    NEVER run against `next dev` — Lighthouse Performance will be artificially low. NEVER add Lighthouse CI infrastructure (`.lighthouserc*`, GitHub workflows) — out of scope.

    Commit at end of task: `chore(quick/260514-leb): add baseline Lighthouse mobile report for landing page`.
  </action>
  <verify>
    <automated>test -f .planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/01-baseline.report.json && test -f .planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/01-baseline.report.html && test -f .planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/BASELINE-NOTES.md && node -e "const r=require('./.planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/01-baseline.report.json'); if(!r.categories.performance||!r.categories.accessibility||!r.categories['best-practices']||!r.categories.seo){process.exit(1)}"</automated>
  </verify>
  <done>
    Both `01-baseline.report.html` and `01-baseline.report.json` exist under `reports/`. `BASELINE-NOTES.md` lists all four scores, identifies failing categories (< 90), names the top failing audits per failing category, and proposes a concrete fix plan for Task 2. Production server is stopped (port 3000 free). Commit landed.
  </done>
</task>

<task type="auto">
  <name>Task 2: Apply targeted fixes for low-scoring categories</name>
  <files>
    apps/web/app/layout.tsx,
    apps/web/app/(public)/layout.tsx,
    apps/web/app/(public)/page.tsx,
    apps/web/next.config.ts,
    apps/web/app/robots.ts,
    apps/web/app/sitemap.ts,
    apps/web/features/landing/components/Hero.tsx,
    apps/web/features/landing/components/TopBar.tsx,
    apps/web/features/landing/components/Footer.tsx,
    apps/web/features/landing/components/Features.tsx,
    apps/web/features/landing/components/HowItWorks.tsx,
    apps/web/features/landing/components/TrustPillars.tsx
  </files>
  <action>
    Read `BASELINE-NOTES.md` from Task 1 and apply the fix plan it proposed. Scope the changes to whatever the baseline identified — DO NOT make speculative changes to categories that already scored ≥ 90.

    Mandatory references before touching code (per `apps/web/AGENTS.md` — "This is NOT the Next.js you know"):
    - Consult `node_modules/next/dist/docs/` (or Context7 `resolve-library-id` → `query-docs` for `/vercel/next.js` v16) before changing the Metadata API, `next/image`, `next/font`, `headers()` in `next.config.ts`, `robots.ts`, `sitemap.ts`. Next 16 has breaking changes vs training data.
    - For any UI-visible change (skip-link, contrast tweak, alt text, semantic landmarks), invoke the `frontend-design` skill before writing JSX.
    - shadcn primitive rule: if a new UI primitive is needed (e.g. a button), install via `pnpm dlx shadcn@latest add <component>` and import from `@/components/ui/*`. Do not hand-roll primitives.

    Likely high-leverage fix candidates (apply only those the baseline flags):

    SEO (most common easy wins):
    - Extend `generateMetadata()` in `apps/web/app/layout.tsx` with `metadataBase`, `openGraph` (title/description/url/siteName/locale/type), `twitter` (card/title/description), `robots` (index/follow), `icons`, `themeColor`, `viewport: { width: 'device-width', initialScale: 1 }`. Use the next-intl `getTranslations('common.app')` already imported — add new translation keys to `common.app` if needed (don't break existing).
    - Add `apps/web/app/robots.ts` (Next 16 Route Handler form) returning `{ rules: [{ userAgent: '*', allow: '/' }], sitemap: '<absolute-url>/sitemap.xml' }`. Source the absolute URL from an env var (e.g. `NEXT_PUBLIC_SITE_URL`) with a `http://localhost:3000` fallback for local audits.
    - Add `apps/web/app/sitemap.ts` listing `/`, `/docs`, `/privacy`, `/terms` with `lastModified: new Date()`.

    Accessibility:
    - Ensure `<html lang>` resolves to a valid locale at SSR for the audit run (already done — verify no regression).
    - Add a skip-to-content link at the top of `(public)/layout.tsx` targeting `#main`, and add `id="main"` to the `<main>` element. Style via Tailwind utilities so it's visually hidden until focused (`sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 ...`).
    - Ensure landmarks: `<header>` for TopBar, `<nav>` inside TopBar for primary nav, `<main>` (already present), `<footer>` for Footer. Update the landing feature components accordingly.
    - Ensure every interactive element (icon-only button, link with only an icon) has an accessible name via `aria-label` or visually hidden text. Audit `TopBar`, `Footer`, `Hero` CTA, `HowItWorks`, `Features`, `TrustPillars`.
    - Ensure all `<img>` / `next/image` have non-empty `alt`. If decorative, use `alt=""` explicitly.
    - Verify heading order (single `<h1>` on the page — in `Hero` — then `<h2>` per section). Fix any skipped levels.

    Performance:
    - For the LCP element (likely Hero image/heading), if it's an image, switch to `next/image` with explicit `width`/`height`, `priority`, `sizes` matching the responsive breakpoints; if it's text, ensure no font swap-induced layout shift (Roboto already uses `display: 'optional'` which is fine).
    - Inspect Hero and TopBar for client components that could be server components — remove unnecessary `'use client'` directives if the component does not use state/effects/event handlers.
    - In `apps/web/next.config.ts`, enable production console stripping: `compiler: { removeConsole: { exclude: ['error', 'warn'] } }` (verify the Next 16 key name via Context7 / docs — historically `compiler.removeConsole`).

    Best Practices:
    - Verify no `console.log` in production bundles (covered by `removeConsole` above).
    - Add baseline security/Cache-Control headers in `next.config.ts` `headers()`:
      - `X-Content-Type-Options: nosniff`
      - `Referrer-Policy: strict-origin-when-cross-origin`
      - `X-Frame-Options: DENY` (or CSP `frame-ancestors 'none'`)
      - Cache-Control for `/_next/static/*` is already handled by Next defaults; do not override.
    - Do NOT introduce analytics, trackers, or third-party fonts/scripts (privacy constraint from CLAUDE.md).

    After fixes, re-run typecheck + lint locally to confirm nothing regressed: `pnpm --filter web typecheck && pnpm --filter web lint`. Fix any errors before moving on.

    Commit at end of task: `feat(quick/260514-leb): fix landing page Lighthouse gaps (<categories actually changed>)`. Use a body listing the specific changes per category.
  </action>
  <verify>
    <automated>pnpm --filter web typecheck &amp;&amp; pnpm --filter web lint &amp;&amp; pnpm --filter web build</automated>
  </verify>
  <done>
    All fixes listed in `BASELINE-NOTES.md` Fix Plan are applied. `pnpm --filter web typecheck`, `pnpm --filter web lint`, and `pnpm --filter web build` all pass. No new dependencies added except via `pnpm dlx shadcn@latest add` (if a primitive was needed). No analytics or trackers introduced. Commit landed.
  </done>
</task>

<task type="auto">
  <name>Task 3: Re-audit, gate, and report</name>
  <files>
    .planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/02-after-fixes.report.html,
    .planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/02-after-fixes.report.json,
    .planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/03-final.report.html,
    .planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/03-final.report.json,
    .planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/260514-leb-SUMMARY.md
  </files>
  <action>
    Re-run the Lighthouse audit against a fresh production build to measure the impact of Task 2's fixes, then decide whether one more focused fix pass is needed.

    HARD CAP: at most 3 total Lighthouse runs across the whole quick task (Task 1 = run #1, this task may do run #2 and optionally run #3). NEVER loop further. If after run #3 any category is still < 90, list the remaining gaps as DEFERRED items in the SUMMARY and stop.

    Procedure:

    1. Repeat the build + background-start + readiness-wait sequence from Task 1 (build → `pnpm --filter web start` in background → poll `http://localhost:3000/` until 200).

    2. Run Lighthouse, writing to `02-after-fixes`:

       ```
       pnpm dlx lighthouse@latest http://localhost:3000/ \
         --preset=mobile --quiet \
         --chrome-flags="--headless=new --no-sandbox" \
         --output=json --output=html \
         --output-path=.planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/02-after-fixes.report
       ```

    3. Stop the background server.

    4. Parse `02-after-fixes.report.json` with a Node one-liner (same pattern as Task 1). Compute the four scores.

    5. Gate:
       - If ALL four scores ≥ 90: SUCCESS path — skip step 6, jump to step 7.
       - If any score < 90 AND this is the first re-audit: proceed to step 6 (one final fix pass + final audit).
       - If we are already at run #3 (i.e. previous step was a re-audit after a second fix pass): SKIP to step 7 — do NOT do another fix pass. Document remaining gaps as DEFERRED.

    6. (Only if needed and within the cap) Final focused fix pass:
       - Read the lowest remaining category's top 3 failing audits from `02-after-fixes.report.json`.
       - Apply ONE more round of targeted fixes scoped strictly to those audits.
       - Re-run typecheck + lint + build.
       - Repeat steps 1–3 writing to `03-final.report.{html,json}`. This is run #3 — the hard cap.
       - Commit: `fix(quick/260514-leb): second-pass Lighthouse fixes for <category>`.

    7. Write `260514-leb-SUMMARY.md` in the quick task root using `$HOME/.claude/get-shit-done/templates/summary.md` as the skeleton. The summary MUST include:
       - **Outcome**: `ALL_PASS` (all four ≥ 90) or `PARTIAL` (one or more deferred).
       - **Final scores table**: Category | Baseline | After Fixes | Final | Target (90) | Status.
       - **Changes applied**: bullet list grouped by category (Performance / A11y / BP / SEO), with file paths.
       - **Files modified**: union of all files touched across Tasks 1–3.
       - **Reports**: links to all report files under `reports/`.
       - **Deferred items** (if PARTIAL): each remaining failing audit with its `id`, `title`, `score`, and a one-line "why deferred" + suggested next step. DO NOT propose looping — the cap is intentional.
       - **How to re-run later**: short copy-paste block (build → start in background → lighthouse command → stop server).

    8. Commit (squashed at end of task): `docs(quick/260514-leb): final Lighthouse audit + SUMMARY`. Include the report files in this commit if not committed already.
  </action>
  <verify>
    <automated>test -f .planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/02-after-fixes.report.json &amp;&amp; test -f .planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/260514-leb-SUMMARY.md &amp;&amp; node -e "const fs=require('fs'); const summary=fs.readFileSync('.planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/260514-leb-SUMMARY.md','utf8'); if(!/Performance/.test(summary)||!/Accessibility/.test(summary)||!/Best Practices/.test(summary)||!/SEO/.test(summary)){process.exit(1)} const last=fs.existsSync('.planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/03-final.report.json')?require('./.planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/03-final.report.json'):require('./.planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/02-after-fixes.report.json'); const c=last.categories; console.log('final scores:', JSON.stringify({performance:Math.round(c.performance.score*100), accessibility:Math.round(c.accessibility.score*100), bestPractices:Math.round(c['best-practices'].score*100), seo:Math.round(c.seo.score*100)}))"</automated>
  </verify>
  <done>
    `02-after-fixes.report.{html,json}` exist. If a third pass was needed, `03-final.report.{html,json}` also exist. `260514-leb-SUMMARY.md` exists and contains the final scores table for all four categories, lists changes applied, and (if any category < 90) lists deferred items. No more than 3 Lighthouse runs were executed total across the task. Commit landed.
  </done>
</task>

</tasks>

<verification>
After all tasks:

1. `reports/01-baseline.report.{html,json}` exists with all four category scores parseable.
2. `reports/02-after-fixes.report.{html,json}` exists with all four category scores parseable.
3. `BASELINE-NOTES.md` exists and lists the original Fix Plan.
4. `260514-leb-SUMMARY.md` exists with the final scores table and either ALL_PASS or a clear DEFERRED list.
5. `apps/web` still builds: `pnpm --filter web build` passes.
6. `apps/web` typecheck + lint pass.
7. No Lighthouse CI infrastructure was added (`.lighthouserc*`, `.github/workflows/lighthouse*` do NOT appear).
8. At most 3 Lighthouse JSON reports exist under `reports/`.
</verification>

<success_criteria>
- All four Lighthouse mobile categories (Performance, Accessibility, Best Practices, SEO) score ≥ 90 on `http://localhost:3000/` against the production build, **OR** the SUMMARY explicitly documents which categories remain < 90 with a concrete reason and a suggested follow-up — no infinite-iteration loop.
- Baseline and post-fix HTML + JSON reports are saved under the quick task `reports/` directory.
- Only files inside `apps/web/**` and the quick task directory were modified (no backend changes).
- No analytics, trackers, third-party scripts, or new external fonts were introduced.
- No Lighthouse CI infrastructure was added.
</success_criteria>

<output>
After completion, the executor MUST have produced:
- `.planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/260514-leb-SUMMARY.md`
- Lighthouse reports under `.planning/quick/260514-leb-chay-lighthouse-audit-cho-apps-web-mobil/reports/`
- Targeted source fixes in `apps/web/**`
- 2–4 atomic commits (baseline report, fixes, optional second-pass fixes, final summary)
</output>
