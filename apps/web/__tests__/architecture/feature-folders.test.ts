import { existsSync, readFileSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it, expect } from 'vitest';

const APP_WEB = resolve(__dirname, '../..');
const FEATURES_DIR = resolve(APP_WEB, 'features');
const COMPONENTS_DIR = resolve(APP_WEB, 'components');
const I18N_DIR = resolve(APP_WEB, 'i18n');
const FEATURE_ROOTS = ['auth', 'account', 'onboarding', 'gmail'] as const;
const SUBDIRS = ['api', 'components', 'hooks'] as const;

// Reviews Revision 1: route page locations after Plan 05 route-group migration.
// We check both pre- and post-migration paths so the test runs across plan waves.
function readSettingsPage(): string {
  const grouped = resolve(APP_WEB, 'app/(protected)/settings/page.tsx');
  const flat = resolve(APP_WEB, 'app/settings/page.tsx');
  const path = existsSync(grouped) ? grouped : flat;
  return existsSync(path) ? readFileSync(path, 'utf8') : '';
}
function readOnboardingPage(): string {
  const grouped = resolve(APP_WEB, 'app/(protected)/onboarding/page.tsx');
  const flat = resolve(APP_WEB, 'app/onboarding/page.tsx');
  const path = existsSync(grouped) ? grouped : flat;
  return existsSync(path) ? readFileSync(path, 'utf8') : '';
}

describe('Phase 1.3 — Feature folder architecture', () => {
  it.each(FEATURE_ROOTS)('features/%s/ exists', (feature) => {
    const dir = resolve(FEATURES_DIR, feature);
    expect(existsSync(dir)).toBe(true);
    expect(statSync(dir).isDirectory()).toBe(true);
  });

  it.each(FEATURE_ROOTS)('features/%s/ has api, components, hooks subdirs', (feature) => {
    for (const sub of SUBDIRS) {
      const dir = resolve(FEATURES_DIR, feature, sub);
      expect(existsSync(dir)).toBe(true);
      expect(statSync(dir).isDirectory()).toBe(true);
    }
  });

  it.each(FEATURE_ROOTS)('features/%s/ has NO index.ts barrel (D-A5 deep imports)', (feature) => {
    expect(existsSync(resolve(FEATURES_DIR, feature, 'index.ts'))).toBe(false);
    expect(existsSync(resolve(FEATURES_DIR, feature, 'index.tsx'))).toBe(false);
  });

  it('relocated components no longer live at legacy shared roots', () => {
    expect(existsSync(resolve(COMPONENTS_DIR, 'LanguageSwitcher.tsx'))).toBe(false);
    expect(existsSync(resolve(COMPONENTS_DIR, 'ConnectionHealthBadge.tsx'))).toBe(false);
    expect(existsSync(resolve(COMPONENTS_DIR, 'ReconnectPrompt.tsx'))).toBe(false);
    expect(existsSync(resolve(COMPONENTS_DIR, 'DeleteAccountDialog.tsx'))).toBe(false);
    expect(existsSync(resolve(COMPONENTS_DIR, 'TemplateCard.tsx'))).toBe(false);
  });

  it('feature components and i18n chrome exist at their owner paths', () => {
    expect(existsSync(resolve(I18N_DIR, 'components/LanguageSwitcher.tsx'))).toBe(true);
    expect(existsSync(resolve(I18N_DIR, 'messages/vi.json'))).toBe(true);
    expect(existsSync(resolve(I18N_DIR, 'messages/en.json'))).toBe(true);
    expect(existsSync(resolve(FEATURES_DIR, 'gmail/components/ConnectionHealthBadge.tsx'))).toBe(
      true,
    );
    expect(existsSync(resolve(FEATURES_DIR, 'gmail/components/ReconnectPrompt.tsx'))).toBe(true);
    expect(existsSync(resolve(FEATURES_DIR, 'account/components/DeleteAccountDialog.tsx'))).toBe(
      true,
    );
    expect(existsSync(resolve(FEATURES_DIR, 'onboarding/components/TemplateCard.tsx'))).toBe(true);
  });

  it('shadcn primitives stay in components/ui/ (ROADMAP #3)', () => {
    expect(existsSync(resolve(COMPONENTS_DIR, 'ui'))).toBe(true);
    expect(existsSync(resolve(COMPONENTS_DIR, 'ui/button.tsx'))).toBe(true);
    expect(existsSync(resolve(COMPONENTS_DIR, 'ui/card.tsx'))).toBe(true);
  });

  // REVIEWS Revision 1 — Codex HIGH #1: per-endpoint api/ files exist with right exports.
  it('features/gmail/api/{status,disconnect,keys}.ts exist with correct exports', () => {
    const status = resolve(FEATURES_DIR, 'gmail/api/status.ts');
    const disconnect = resolve(FEATURES_DIR, 'gmail/api/disconnect.ts');
    const keys = resolve(FEATURES_DIR, 'gmail/api/keys.ts');
    expect(existsSync(status)).toBe(true);
    expect(existsSync(disconnect)).toBe(true);
    expect(existsSync(keys)).toBe(true);
    expect(readFileSync(status, 'utf8')).toMatch(/export\s+async\s+function\s+getTenantStatus/);
    expect(readFileSync(disconnect, 'utf8')).toMatch(/export\s+async\s+function\s+disconnectGmail/);
    expect(readFileSync(keys, 'utf8')).toMatch(/export\s+const\s+gmailKeys/);
  });

  it('features/onboarding/api/{selectTemplate,complete,keys}.ts exist with correct exports', () => {
    const select = resolve(FEATURES_DIR, 'onboarding/api/selectTemplate.ts');
    const complete = resolve(FEATURES_DIR, 'onboarding/api/complete.ts');
    const keys = resolve(FEATURES_DIR, 'onboarding/api/keys.ts');
    expect(existsSync(select)).toBe(true);
    expect(existsSync(complete)).toBe(true);
    expect(existsSync(keys)).toBe(true);
    expect(readFileSync(select, 'utf8')).toMatch(/export\s+async\s+function\s+selectTemplate/);
    expect(readFileSync(complete, 'utf8')).toMatch(
      /export\s+async\s+function\s+completeOnboarding/,
    );
    expect(readFileSync(keys, 'utf8')).toMatch(/export\s+const\s+onboardingKeys/);
  });

  it('features/account/api/deleteAccount.ts exists with correct export', () => {
    const file = resolve(FEATURES_DIR, 'account/api/deleteAccount.ts');
    expect(existsSync(file)).toBe(true);
    expect(readFileSync(file, 'utf8')).toMatch(/export\s+async\s+function\s+deleteAccount/);
  });

  it('feature hooks for the 5 expanded endpoints exist with the right TanStack Query primitive', () => {
    const cases = [
      ['gmail/hooks/useTenantStatus.ts', /useQuery/],
      ['gmail/hooks/useDisconnectGmail.ts', /useMutation/],
      ['onboarding/hooks/useSelectTemplate.ts', /useMutation/],
      ['onboarding/hooks/useCompleteOnboarding.ts', /useMutation/],
      ['account/hooks/useDeleteAccount.ts', /useMutation/],
    ] as const;
    for (const [rel, re] of cases) {
      const file = resolve(FEATURES_DIR, rel);
      expect(existsSync(file), `expected ${rel} to exist`).toBe(true);
      expect(readFileSync(file, 'utf8')).toMatch(re);
    }
  });

  it('settings page no longer calls api.GET/POST/DELETE for moved endpoints (REVIEWS Revision 1)', () => {
    const src = readSettingsPage();
    expect(src).not.toMatch(/api\.GET\(\s*['"]\/tenant\/status['"]/);
    expect(src).not.toMatch(/api\.POST\(\s*['"]\/tenant\/disconnect['"]/);
    expect(src).not.toMatch(/api\.DELETE\(\s*['"]\/me\/account['"]/);
  });

  it('onboarding page no longer calls api.POST for moved endpoints (REVIEWS Revision 1)', () => {
    const src = readOnboardingPage();
    expect(src).not.toMatch(/api\.POST\(\s*['"]\/onboarding\/select-template['"]/);
    expect(src).not.toMatch(/api\.POST\(\s*['"]\/onboarding\/complete['"]/);
  });
});
