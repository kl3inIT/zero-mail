import { NextRequest } from 'next/server';
import { describe, expect, it } from 'vitest';

import { GET } from '@/app/md/[[...path]]/route';
import { estimateTokens, extractMainHtml, htmlToMarkdown } from '@/lib/markdown/html-to-markdown';

const PAGE = `<!doctype html><html><head><title>x</title>
<style>.a{color:red}</style></head>
<body>
<header><nav><a href="/login">Login</a></nav></header>
<main>
<h1>Inbox Zero for Gmail</h1>
<p>AI triage you can <strong>trust</strong>.</p>
<ul><li>Auto-archive</li><li>Draft replies</li></ul>
<a href="/features">Features</a>
<script>window.analytics()</script>
</main>
<footer>© Zero Mail</footer>
</body></html>`;

describe('extractMainHtml', () => {
  it('isolates the <main> region, dropping header/nav/footer chrome', () => {
    const main = extractMainHtml(PAGE);
    expect(main).toContain('Inbox Zero for Gmail');
    expect(main).not.toContain('Login');
    expect(main).not.toContain('© Zero Mail');
  });

  it('falls back to <body> when there is no <main>', () => {
    expect(extractMainHtml('<body><p>hello</p></body>')).toContain('hello');
  });

  it('falls back to the whole string for fragment markup', () => {
    expect(extractMainHtml('<p>fragment</p>')).toBe('<p>fragment</p>');
  });
});

describe('htmlToMarkdown', () => {
  it('converts main content to markdown and never leaks scripts/styles', () => {
    const markdown = htmlToMarkdown(PAGE);
    expect(markdown).toContain('# Inbox Zero for Gmail');
    expect(markdown).toContain('**trust**');
    expect(markdown).toContain('* Auto-archive');
    expect(markdown).toContain('[Features](/features)');
    // Privacy/quality invariant: executable + presentational chrome must be gone.
    expect(markdown).not.toContain('window.analytics');
    expect(markdown).not.toContain('color:red');
  });
});

describe('estimateTokens', () => {
  it('approximates ~4 characters per token', () => {
    expect(estimateTokens('')).toBe(0);
    expect(estimateTokens('abcd')).toBe(1);
    expect(estimateTokens('abcde')).toBe(2);
  });
});

describe('GET /md security allowlist', () => {
  const ctx = (path: string[]) => ({ params: Promise.resolve({ path }) });

  it('404s a protected path before any upstream fetch (no markdown for app pages)', async () => {
    const response = await GET(new NextRequest('http://127.0.0.1/md/chat'), ctx(['chat']));
    expect(response.status).toBe(404);
  });

  it('404s an unknown public path', async () => {
    const response = await GET(new NextRequest('http://127.0.0.1/md/settings'), ctx(['settings']));
    expect(response.status).toBe(404);
  });
});
