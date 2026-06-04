'use client';

import { Mail } from 'lucide-react';

import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { cn } from '@/lib/utils';

/**
 * Sender avatar that mirrors Inbox Zero's approach: brand/organization senders show their
 * domain favicon (via Google's public favicon service — only the domain leaves the browser, never
 * the address or body), while personal/free-mail senders fall back to a colored letter tile. Gmail
 * does not expose arbitrary sender photos, so a domain favicon + letter fallback is the best we can
 * faithfully render. The base-ui Avatar hides `AvatarImage` automatically when the favicon 404s, so
 * the fallback is the natural resting state.
 */

const FREE_MAIL_DOMAINS = new Set([
  'gmail.com',
  'googlemail.com',
  'yahoo.com',
  'yahoo.com.vn',
  'outlook.com',
  'hotmail.com',
  'live.com',
  'icloud.com',
  'me.com',
  'proton.me',
  'protonmail.com',
  'aol.com',
]);

// Decorative letter-tile palette. These are intentionally fixed avatar colors (like the inbox
// label chips), not semantic tokens — they only ever back a contrast-safe initial.
const LETTER_TILE_COLORS = [
  'bg-rose-100 text-rose-700 dark:bg-rose-900/40 dark:text-rose-200',
  'bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-200',
  'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-200',
  'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-200',
  'bg-teal-100 text-teal-700 dark:bg-teal-900/40 dark:text-teal-200',
  'bg-sky-100 text-sky-700 dark:bg-sky-900/40 dark:text-sky-200',
  'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-200',
  'bg-violet-100 text-violet-700 dark:bg-violet-900/40 dark:text-violet-200',
  'bg-fuchsia-100 text-fuchsia-700 dark:bg-fuchsia-900/40 dark:text-fuchsia-200',
] as const;

type SenderAvatarProps = {
  /** Sender display string — either an email or `"Name <email@host>"`. */
  sender: string;
  size?: 'default' | 'sm' | 'lg';
  className?: string;
};

export function SenderAvatar({ sender, size = 'default', className }: SenderAvatarProps) {
  const email = extractEmail(sender);
  const domain = domainOf(email);
  const initials = senderInitials(sender, email);
  const showFavicon = domain.length > 0 && !FREE_MAIL_DOMAINS.has(domain);
  const tileColor = LETTER_TILE_COLORS[colorIndex(domain || sender)]!;

  return (
    <Avatar size={size} className={cn('mt-0.5', className)}>
      {showFavicon ? (
        <AvatarImage src={faviconUrl(domain)} alt="" referrerPolicy="no-referrer" />
      ) : null}
      <AvatarFallback className={cn('font-semibold', tileColor)}>
        {initials === '?' ? <Mail className="size-3.5" aria-hidden="true" /> : initials}
      </AvatarFallback>
    </Avatar>
  );
}

function faviconUrl(domain: string): string {
  // s2/favicons (NOT t1.gstatic.com/faviconV2): both serve brand favicons when one exists, but
  // faviconV2 returns HTTP 404 for unknown domains and pollutes DevTools with one 404 per row.
  // s2/favicons returns HTTP 200 with a globe placeholder for the same unknown case, keeping the
  // network log clean. The base-ui Avatar's natural-size fallback still hides the placeholder.
  return `https://www.google.com/s2/favicons?domain=${encodeURIComponent(domain)}&sz=64`;
}

function extractEmail(sender: string): string {
  if (!sender) return '';
  const angled = sender.match(/<([^<>@\s]+@[^<>@\s]+)>/);
  if (angled?.[1]) return angled[1].toLowerCase();
  const bare = sender.match(/[^\s,;<>]+@[^\s,;<>]+/);
  return (bare?.[0] ?? '').toLowerCase();
}

function domainOf(email: string): string {
  const at = email.lastIndexOf('@');
  return at === -1 ? '' : email.slice(at + 1).trim();
}

function senderInitials(sender: string, email: string): string {
  // The display name is everything before the first angle bracket in "Name <email>". Slicing at
  // the bracket (rather than regex-stripping <...> segments) avoids partial-tag leftovers that a
  // single-pass replace can miss — and keeps this off CodeQL's incomplete-sanitization radar.
  const angleStart = sender.indexOf('<');
  const displayName = (angleStart === -1 ? sender : sender.slice(0, angleStart))
    .replace(/^"+|"+$/g, '')
    .trim();
  const source = displayName || email;
  if (!source) return '?';
  const parts = source.split(/[\s.@_-]+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0]!.slice(0, 2).toUpperCase();
  return (parts[0]![0]! + parts[1]![0]!).toUpperCase();
}

function colorIndex(seed: string): number {
  let hash = 0;
  for (const character of seed) {
    hash = (hash + character.charCodeAt(0)) % LETTER_TILE_COLORS.length;
  }
  return hash;
}
