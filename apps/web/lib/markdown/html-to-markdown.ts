import { NodeHtmlMarkdown } from 'node-html-markdown';

/**
 * HTML → Markdown for agent content negotiation (`Accept: text/markdown`).
 *
 * The public marketing/blog/docs pages are React Server Components with no raw
 * markdown source, so the markdown variant is derived from the page's own
 * rendered HTML. We isolate the `<main>` region (the `(public)` layout wraps page
 * content in a single `<main>`) so nav/header/footer chrome and `<script>` /
 * `<style>` tags never leak into the agent-facing markdown.
 */

/**
 * Extract the primary content region from a full HTML document. Prefers `<main>`
 * (the public layout's content wrapper), falls back to `<body>`, then the whole
 * string so conversion still produces something for unexpected markup.
 */
export function extractMainHtml(html: string): string {
  const main = html.match(/<main\b[^>]*>([\s\S]*?)<\/main>/i);
  if (main) return main[1];
  const body = html.match(/<body\b[^>]*>([\s\S]*?)<\/body>/i);
  if (body) return body[1];
  return html;
}

/** Convert a rendered HTML document to clean markdown of its main content. */
export function htmlToMarkdown(html: string): string {
  return NodeHtmlMarkdown.translate(extractMainHtml(html)).trim();
}

/**
 * Rough GPT-style token estimate (~4 characters per token) for the optional
 * `x-markdown-tokens` response hint. Deliberately cheap — it is advisory only,
 * not a billing or budgeting figure.
 */
export function estimateTokens(text: string): number {
  return Math.ceil(text.length / 4);
}
