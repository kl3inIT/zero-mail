'use client';

import { StatusAlert } from '@/components/ui/StatusAlert';

/**
 * ReconnectPrompt — refactored in Phase 01.4 Plan 06 to consume the StatusAlert
 * primitive (UI-SPEC §Component Inventory + analog precedent in PATTERNS.md
 * "StatusAlert existing in-repo precedent"). Token-aware via StatusAlert's
 * variant=warn mapping; the previous border-amber-500 / bg-amber-50 raw color
 * literals are replaced by the primitive's neutral warn surface.
 *
 * The single i18n key `connectionHealth.reconnectPrompt` resolves to a one-line
 * sentence — passed as titleKey only (no bodyKey). The CTA reuses the existing
 * `settings.gmailConnection.reconnectCta` key.
 */
export function ReconnectPrompt({ onReconnect }: { onReconnect: () => void }) {
  return (
    <StatusAlert
      variant="warn"
      titleKey="connectionHealth.reconnectPrompt"
      cta={{ labelKey: 'settings.gmailConnection.reconnectCta', onClick: onReconnect }}
    />
  );
}
