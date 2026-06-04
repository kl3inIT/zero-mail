'use client';

import { Check, Copy } from 'lucide-react';
import { useState } from 'react';

import { cn } from '@/lib/utils';

export function CopyButton({ text, className }: { text: string; className?: string }) {
  const [copied, setCopied] = useState(false);

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard can be blocked (insecure context / permissions); fail silently.
    }
  }

  return (
    <button
      type="button"
      onClick={handleCopy}
      className={cn(
        'text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-xs',
        className,
      )}
    >
      {copied ? <Check className="size-3" /> : <Copy className="size-3" />}
      {copied ? 'Đã chép' : 'Chép'}
    </button>
  );
}
