import { describe, it, expect } from 'vitest';

/**
 * Wave 0 RED stub — Plan 05 turns this GREEN.
 *
 * Locks the vi/en dictionary parity contract:
 *  - every leaf key in messages/vi.json exists in messages/en.json (and vice versa)
 *  - the "errors" namespace mirrors the backend ErrorCodes hierarchy 1:1
 *
 * Plan 05 will wire the actual deep-walk + diff against
 *   apps/web/messages/vi.json
 *   apps/web/messages/en.json
 * and feed the same parity logic to the apps/web/scripts/check-i18n.ts CI gate
 * (CONTEXT.md decision D-D1 — single source of truth so test + CI never drift).
 */
describe('vi/en key parity (Wave 0 stub — implemented by Plan 05)', () => {
  it.skip('every leaf key in messages/vi.json exists in messages/en.json and vice versa', () => {
    expect(true).toBe(true);
  });

  it.skip('errors namespace mirrors backend ErrorCodes 1:1', () => {
    expect(true).toBe(true);
  });
});
