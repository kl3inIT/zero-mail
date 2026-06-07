'use client';

import { useCallback, useRef, useState, type ReactNode } from 'react';

const EMAIL_FRAME_CSS = `
  :root { color-scheme: light; }
  html, body { margin: 0; padding: 0; background: #fff; color: #202124; }
  html { width: 100%; }
  body {
    box-sizing: border-box;
    width: 100%;
    max-width: 100%;
    min-width: 0;
    padding: 16px;
    font-family: Arial, Helvetica, sans-serif;
    font-size: 14px;
    line-height: 1.55;
    overflow-wrap: anywhere;
    word-break: break-word;
    overflow-x: auto;
  }
  .zm-mail-wrapper {
    max-width: 720px;
    margin: 0 auto;
  }
  .zm-mail-wrapper > * {
    margin-left: auto !important;
    margin-right: auto !important;
  }
  img, video, iframe, embed, object {
    max-width: 100% !important;
    height: auto !important;
  }
  img {
    display: block;
    margin-left: auto !important;
    margin-right: auto !important;
  }
  table {
    max-width: 100% !important;
    margin-left: auto !important;
    margin-right: auto !important;
    border-collapse: collapse;
  }
  td, th { word-break: break-word; }
  a { color: #1a73e8; text-decoration: none; word-break: break-all; }
  a:hover { text-decoration: underline; }
  blockquote { margin: 12px 0 12px 12px; padding-left: 12px; border-left: 2px solid #dadce0; color: #5f6368; }
  pre { white-space: pre-wrap; overflow-wrap: anywhere; }
  @media (max-width: 640px) {
    body { padding: 12px; font-size: 15px; }
    h1, h2 { font-size: 1.5rem !important; line-height: 1.3; }
    h3 { font-size: 1.25rem !important; line-height: 1.3; }
    h1, h2, h3, h4, h5, h6, p, span, div, td { max-width: 100%; }
  }
`;

function emailFrameSrcDoc(renderedHtml: string, locale: string) {
  const documentLanguage = locale.replace(/[^A-Za-z-]/g, '') || 'en';
  return `<!doctype html><html lang="${documentLanguage}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=5"><base target="_blank"><style>${EMAIL_FRAME_CSS}</style></head><body><div class="zm-mail-wrapper">${renderedHtml}</div></body></html>`;
}

/**
 * Renders sanitized email HTML inside a sandboxed, auto-resizing iframe. Shared by the inbox detail
 * panel and the needs-reply in-place reader so both surfaces render mail identically. The HTML must
 * already be sanitized server-side (`RecentInboxReadService` runs `SafeHtmlSanitizer`); the iframe
 * sandbox is defence-in-depth.
 */
export function EmailHtmlFrame({
  renderedHtml,
  title,
  locale,
}: {
  renderedHtml: string;
  title: string;
  locale: string;
}) {
  const frameRef = useRef<HTMLIFrameElement | null>(null);
  const [frameHeight, setFrameHeight] = useState(520);

  const resizeFrame = useCallback(() => {
    const frameElement = frameRef.current;
    const frameDocument = frameElement?.contentDocument;
    if (!frameDocument) {
      return;
    }
    const bodyHeight = frameDocument.body?.scrollHeight ?? 0;
    const documentHeight = frameDocument.documentElement?.scrollHeight ?? 0;
    setFrameHeight(Math.max(240, bodyHeight, documentHeight));
  }, []);

  return (
    <iframe
      ref={frameRef}
      title={title}
      className="w-full border-0 bg-white"
      sandbox="allow-same-origin allow-popups allow-popups-to-escape-sandbox"
      referrerPolicy="no-referrer"
      scrolling="no"
      style={{ height: frameHeight }}
      srcDoc={emailFrameSrcDoc(renderedHtml, locale)}
      onLoad={() => {
        resizeFrame();
        window.setTimeout(resizeFrame, 250);
        window.setTimeout(resizeFrame, 1000);
      }}
    />
  );
}

/** Renders a plain-text email body with paragraphs and linkified URLs. */
export function PlainEmailContent({ text }: { text: string }) {
  const paragraphs = text.split(/\n{2,}/).filter((paragraph) => paragraph.trim().length > 0);
  return (
    <div className="mx-auto max-w-[720px] bg-white p-5 text-sm leading-6 text-neutral-900">
      {paragraphs.map((paragraph, index) => (
        <p key={index} className="mb-4 break-words whitespace-pre-wrap">
          {linkifyPlainText(paragraph)}
        </p>
      ))}
    </div>
  );
}

function linkifyPlainText(text: string) {
  const urlPattern = /<?(https?:\/\/[^\s<>]+)>?/g;
  const nodes: ReactNode[] = [];
  let lastIndex = 0;
  for (const match of text.matchAll(urlPattern)) {
    const matchedText = match[0];
    const url = match[1];
    const matchIndex = match.index ?? 0;
    if (matchIndex > lastIndex) {
      nodes.push(text.slice(lastIndex, matchIndex));
    }
    nodes.push(
      <a
        key={`${url}-${matchIndex}`}
        href={url}
        target="_blank"
        rel="noreferrer"
        className="text-primary hover:underline"
      >
        {url}
      </a>,
    );
    lastIndex = matchIndex + matchedText.length;
  }
  if (lastIndex < text.length) {
    nodes.push(text.slice(lastIndex));
  }
  return nodes.length > 0 ? nodes : text;
}
