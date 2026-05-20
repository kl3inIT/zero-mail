#!/usr/bin/env node
// Convert every docs/ops/*.md into a self-contained docs/ops/*.html page.
//
// Usage:
//   node tools/docs/build-ops-html.mjs            # convert all
//   node tools/docs/build-ops-html.mjs <file.md>  # convert one
//
// The .md stays source of truth. The .html is a generated artifact —
// commit alongside the .md after edits, or regenerate via this script.
//
// Deps: markdown-it + gray-matter. Install one-time:
//   pnpm add -D markdown-it gray-matter        # for repo-local
// Or run via /tmp install (CI rarely needs to regenerate the HTML).

import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, basename, relative } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..');
const opsDir = join(repoRoot, 'docs', 'ops');

let MarkdownIt, matter;
try {
    MarkdownIt = (await import('markdown-it')).default;
    matter = (await import('gray-matter')).default;
} catch {
    console.error('[build-ops-html] missing dep — install once:');
    console.error('    cd /tmp && mkdir -p docs-build && cd docs-build');
    console.error('    echo \'{"dependencies":{"markdown-it":"^14.1.0","gray-matter":"^4.0.3"}}\' > package.json');
    console.error('    pnpm install');
    console.error('    NODE_PATH=$PWD/node_modules node ' + process.argv[1]);
    process.exit(2);
}

const md = new MarkdownIt({
    html: true,
    linkify: true,
    breaks: false,
    typographer: false,
});

// Anchor every heading.
const slug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
const defaultHeadingOpen = md.renderer.rules.heading_open ||
    ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options));
md.renderer.rules.heading_open = (tokens, idx, options, env, self) => {
    const inline = tokens[idx + 1];
    const text = (inline && inline.children) ? inline.children.filter((t) => t.type === 'text').map((t) => t.content).join('') : '';
    const id = slug(text);
    tokens[idx].attrSet('id', id);
    const rendered = defaultHeadingOpen(tokens, idx, options, env, self);
    return rendered + `<a class="anchor" href="#${id}" aria-label="link">¶</a> `;
};

// External links open in new tab.
const defaultLink = md.renderer.rules.link_open ||
    ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options));
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
    const href = tokens[idx].attrGet('href') || '';
    if (/^https?:/i.test(href)) {
        tokens[idx].attrSet('target', '_blank');
        tokens[idx].attrSet('rel', 'noopener');
    }
    return defaultLink(tokens, idx, options, env, self);
};

const css = `
:root {
  color-scheme: light dark;
  --fg: #1f2328;
  --bg: #ffffff;
  --muted: #656d76;
  --border: #d0d7de;
  --code-bg: #f6f8fa;
  --code-fg: #1f2328;
  --link: #0969da;
  --accent: #218bff;
  --table-stripe: #f6f8fa;
  --callout: #ddf4ff;
  --callout-border: #54aeff;
}
@media (prefers-color-scheme: dark) {
  :root {
    --fg: #e6edf3;
    --bg: #0d1117;
    --muted: #8b949e;
    --border: #30363d;
    --code-bg: #161b22;
    --code-fg: #e6edf3;
    --link: #2f81f7;
    --accent: #58a6ff;
    --table-stripe: #161b22;
    --callout: #051d4d;
    --callout-border: #1f6feb;
  }
}
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; background: var(--bg); color: var(--fg); }
body {
  font: 15px/1.6 -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui,
        "Helvetica Neue", Arial, sans-serif;
  max-width: 980px;
  margin: 0 auto;
  padding: 32px 24px 80px;
}
header.meta {
  margin: 0 0 32px;
  padding: 0 0 16px;
  border-bottom: 1px solid var(--border);
}
header.meta h1 { font-size: 28px; margin: 0 0 8px; }
header.meta .meta-row {
  color: var(--muted);
  font-size: 13px;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
header.meta .meta-row span strong { color: var(--fg); font-weight: 600; }
nav.toc {
  background: var(--callout);
  border: 1px solid var(--callout-border);
  border-radius: 6px;
  padding: 12px 16px;
  margin: 0 0 32px;
  font-size: 14px;
}
nav.toc h2 { font-size: 14px; margin: 0 0 8px; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); }
nav.toc ul { margin: 0; padding-left: 18px; }
nav.toc li { margin: 2px 0; }
nav.toc a { color: var(--link); text-decoration: none; }
nav.toc a:hover { text-decoration: underline; }
h1, h2, h3, h4, h5, h6 {
  margin: 24px 0 8px;
  font-weight: 600;
  line-height: 1.25;
  scroll-margin-top: 16px;
}
h1 { font-size: 28px; padding-bottom: 8px; border-bottom: 1px solid var(--border); }
h2 { font-size: 22px; padding-bottom: 6px; border-bottom: 1px solid var(--border); }
h3 { font-size: 18px; }
h4 { font-size: 16px; }
.anchor { color: var(--muted); margin-left: 6px; text-decoration: none; opacity: 0; font-weight: normal; }
h1:hover .anchor, h2:hover .anchor, h3:hover .anchor, h4:hover .anchor,
h5:hover .anchor, h6:hover .anchor { opacity: 1; }
a { color: var(--link); }
p, ul, ol { margin: 8px 0 16px; }
code {
  font: 13px/1.45 ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace;
  background: var(--code-bg);
  color: var(--code-fg);
  padding: 1px 6px;
  border-radius: 4px;
  border: 1px solid var(--border);
}
pre {
  background: var(--code-bg);
  color: var(--code-fg);
  padding: 14px 16px;
  border-radius: 6px;
  border: 1px solid var(--border);
  overflow-x: auto;
  margin: 12px 0 20px;
}
pre code { background: none; border: none; padding: 0; }
table {
  border-collapse: collapse;
  margin: 12px 0 20px;
  width: 100%;
  font-size: 14px;
}
th, td {
  border: 1px solid var(--border);
  padding: 6px 12px;
  text-align: left;
  vertical-align: top;
}
thead { background: var(--code-bg); }
tbody tr:nth-child(2n) { background: var(--table-stripe); }
blockquote {
  border-left: 4px solid var(--border);
  padding: 0 14px;
  color: var(--muted);
  margin: 12px 0;
}
hr { border: 0; border-top: 1px solid var(--border); margin: 24px 0; }
footer {
  margin-top: 64px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
  color: var(--muted);
  font-size: 13px;
}
`.trim();

function escapeHtml(s) {
    return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function renderMeta(data) {
    if (!data || Object.keys(data).length === 0) return '';
    const rows = Object.entries(data)
        .filter(([, v]) => v !== undefined && v !== null && v !== '')
        .map(([k, v]) => `<span><strong>${escapeHtml(k)}:</strong> ${escapeHtml(String(v))}</span>`)
        .join('');
    return `<div class="meta-row">${rows}</div>`;
}

function extractToc(body) {
    const matches = [...body.matchAll(/<h([23])\s+id="([^"]+)"[^>]*>([\s\S]*?)<\/h\1>/g)];
    if (matches.length === 0) return '';
    const items = matches.map(([, depth, id, text]) => {
        const cleanText = text.replace(/<a class="anchor"[^>]*>[\s\S]*?<\/a>/g, '').replace(/<[^>]+>/g, '').trim();
        const indent = depth === '3' ? '  ' : '';
        return `${indent}<li><a href="#${id}">${escapeHtml(cleanText)}</a></li>`;
    });
    return `<nav class="toc"><h2>Contents</h2><ul>${items.join('')}</ul></nav>`;
}

function convertOne(mdPath) {
    const raw = readFileSync(mdPath, 'utf8');
    const { data, content } = matter(raw);

    const firstH1 = content.match(/^#\s+(.+?)$/m);
    const title = data.title || (firstH1 ? firstH1[1] : basename(mdPath, '.md'));

    // Drop the first H1 if it duplicates the frontmatter title.
    const body = data.title
        ? content.replace(/^#\s+.+?\n+/m, '')
        : content;

    const html = md.render(body);
    const toc = extractToc(html);
    const generatedAt = new Date().toISOString().replace('T', ' ').slice(0, 19) + ' UTC';
    const sourceRel = relative(repoRoot, mdPath);

    const out = `<!doctype html>
<html lang="vi">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)} — Zero Mail Ops</title>
<style>${css}</style>
</head>
<body>
<header class="meta">
  <h1>${escapeHtml(title)}</h1>
  ${renderMeta(data)}
</header>
${toc}
<main>
${html}
</main>
<footer>
  Generated from <code>${escapeHtml(sourceRel)}</code> at ${generatedAt} by <code>tools/docs/build-ops-html.mjs</code>.
  Edit the markdown source, then re-run the build script.
</footer>
</body>
</html>
`;

    const htmlPath = mdPath.replace(/\.md$/, '.html');
    writeFileSync(htmlPath, out);
    return htmlPath;
}

const args = process.argv.slice(2);
const targets = args.length > 0
    ? args.map((a) => (a.startsWith('/') ? a : join(repoRoot, a)))
    : readdirSync(opsDir).filter((f) => f.endsWith('.md')).map((f) => join(opsDir, f));

let okCount = 0;
for (const t of targets) {
    try {
        const out = convertOne(t);
        console.log(`  ${relative(repoRoot, t)}  →  ${relative(repoRoot, out)}`);
        okCount += 1;
    } catch (err) {
        console.error(`  ${relative(repoRoot, t)}  FAILED: ${err.message}`);
    }
}
console.log(`\nConverted ${okCount}/${targets.length} files.`);
