import { existsSync, readFileSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, it, expect } from 'vitest';

const APP_WEB = resolve(__dirname, '../..');
const FEATURES_DIR = resolve(APP_WEB, 'features');
const COMPONENTS_DIR = resolve(APP_WEB, 'components');
const I18N_DIR = resolve(APP_WEB, 'i18n');
const FEATURE_ROOTS = ['account', 'auth', 'gmail', 'landing', 'onboarding', 'triage'] as const;
const STANDARD_FEATURE_ROOTS = ['account', 'auth', 'gmail', 'onboarding', 'triage'] as const;
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

describe('Feature folder architecture', () => {
  it.each(FEATURE_ROOTS)('features/%s/ exists', (feature) => {
    const dir = resolve(FEATURES_DIR, feature);
    expect(existsSync(dir)).toBe(true);
    expect(statSync(dir).isDirectory()).toBe(true);
  });

  it.each(STANDARD_FEATURE_ROOTS)('features/%s/ has api, components, hooks subdirs', (feature) => {
    for (const sub of SUBDIRS) {
      const dir = resolve(FEATURES_DIR, feature, sub);
      expect(existsSync(dir)).toBe(true);
      expect(statSync(dir).isDirectory()).toBe(true);
    }
  });

  it('features/landing is a presentation-only feature with components/lib', () => {
    expect(existsSync(resolve(FEATURES_DIR, 'landing/components'))).toBe(true);
    expect(existsSync(resolve(FEATURES_DIR, 'landing/lib'))).toBe(true);
    expect(existsSync(resolve(FEATURES_DIR, 'landing/api'))).toBe(false);
    expect(existsSync(resolve(FEATURES_DIR, 'landing/hooks'))).toBe(false);
  });

  it.each(FEATURE_ROOTS)('features/%s/ has no index.ts barrel', (feature) => {
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

  it('shadcn primitives stay in components/ui/', () => {
    expect(existsSync(resolve(COMPONENTS_DIR, 'ui'))).toBe(true);
    expect(existsSync(resolve(COMPONENTS_DIR, 'ui/button.tsx'))).toBe(true);
    expect(existsSync(resolve(COMPONENTS_DIR, 'ui/card.tsx'))).toBe(true);
  });

  it('features/gmail consolidates API calls and keeps query keys outside api/', () => {
    const apiFile = resolve(FEATURES_DIR, 'gmail/api/gmail-api.ts');
    const queryKeys = resolve(FEATURES_DIR, 'gmail/query-keys.ts');
    expect(existsSync(apiFile)).toBe(true);
    expect(existsSync(queryKeys)).toBe(true);
    expect(readFileSync(apiFile, 'utf8')).toMatch(/export\s+async\s+function\s+getTenantStatus/);
    expect(readFileSync(apiFile, 'utf8')).toMatch(/export\s+async\s+function\s+disconnectGmail/);
    expect(readFileSync(queryKeys, 'utf8')).toMatch(/export\s+const\s+gmailQueryKeys/);
    expect(existsSync(resolve(FEATURES_DIR, 'gmail/api/status.ts'))).toBe(false);
    expect(existsSync(resolve(FEATURES_DIR, 'gmail/api/disconnect.ts'))).toBe(false);
    expect(existsSync(resolve(FEATURES_DIR, 'gmail/api/keys.ts'))).toBe(false);
  });

  it('features/onboarding consolidates mutation API calls without unused query keys', () => {
    const apiFile = resolve(FEATURES_DIR, 'onboarding/api/onboarding-api.ts');
    expect(existsSync(apiFile)).toBe(true);
    expect(readFileSync(apiFile, 'utf8')).toMatch(/export\s+async\s+function\s+selectTemplate/);
    expect(readFileSync(apiFile, 'utf8')).toMatch(/export\s+async\s+function\s+completeOnboarding/);
    expect(existsSync(resolve(FEATURES_DIR, 'onboarding/query-keys.ts'))).toBe(false);
    expect(existsSync(resolve(FEATURES_DIR, 'onboarding/api/selectTemplate.ts'))).toBe(false);
    expect(existsSync(resolve(FEATURES_DIR, 'onboarding/api/complete.ts'))).toBe(false);
    expect(existsSync(resolve(FEATURES_DIR, 'onboarding/api/keys.ts'))).toBe(false);
  });

  it('features/account consolidates account API calls and keeps query keys outside api/', () => {
    const apiFile = resolve(FEATURES_DIR, 'account/api/account-api.ts');
    const queryKeys = resolve(FEATURES_DIR, 'account/query-keys.ts');
    expect(existsSync(apiFile)).toBe(true);
    expect(existsSync(queryKeys)).toBe(true);
    expect(readFileSync(apiFile, 'utf8')).toMatch(/export\s+async\s+function\s+fetchCurrentUser/);
    expect(readFileSync(apiFile, 'utf8')).toMatch(/export\s+async\s+function\s+deleteAccount/);
    expect(readFileSync(apiFile, 'utf8')).toMatch(/export\s+async\s+function\s+updateLanguage/);
    expect(readFileSync(queryKeys, 'utf8')).toMatch(/export\s+const\s+accountQueryKeys/);
    expect(existsSync(resolve(FEATURES_DIR, 'account/api/me.ts'))).toBe(false);
    expect(existsSync(resolve(FEATURES_DIR, 'account/api/deleteAccount.ts'))).toBe(false);
    expect(existsSync(resolve(FEATURES_DIR, 'account/api/keys.ts'))).toBe(false);
  });

  it('features/triage keeps the mutation API consolidated and invalidates account state', () => {
    const apiFile = resolve(FEATURES_DIR, 'triage/api/triage-api.ts');
    const hookFile = resolve(FEATURES_DIR, 'triage/hooks/useToggleTriagePause.ts');
    expect(existsSync(apiFile)).toBe(true);
    expect(readFileSync(apiFile, 'utf8')).toMatch(/export\s+async\s+function\s+setTriagePaused/);
    expect(readFileSync(hookFile, 'utf8')).toMatch(/accountQueryKeys\.me\(\)/);
    expect(existsSync(resolve(FEATURES_DIR, 'triage/query-keys.ts'))).toBe(false);
    expect(existsSync(resolve(FEATURES_DIR, 'triage/api/triagePause.ts'))).toBe(false);
    expect(existsSync(resolve(FEATURES_DIR, 'triage/api/keys.ts'))).toBe(false);
  });

  it('feature hooks for the 5 expanded endpoints exist with the right TanStack Query primitive', () => {
    const cases = [
      ['gmail/hooks/useTenantStatus.ts', /useQuery/],
      ['gmail/hooks/useDisconnectGmail.ts', /useMutation/],
      ['onboarding/hooks/useSelectTemplate.ts', /useMutation/],
      ['onboarding/hooks/useCompleteOnboarding.ts', /useMutation/],
      ['account/hooks/useDeleteAccount.ts', /useMutation/],
      ['triage/hooks/useToggleTriagePause.ts', /useMutation/],
    ] as const;
    for (const [rel, re] of cases) {
      const file = resolve(FEATURES_DIR, rel);
      expect(existsSync(file), `expected ${rel} to exist`).toBe(true);
      expect(readFileSync(file, 'utf8')).toMatch(re);
    }
  });

  it('settings page no longer calls api.GET/POST/DELETE for moved endpoints', () => {
    const src = readSettingsPage();
    expect(src).not.toMatch(/api\.GET\(\s*['"]\/tenant\/status['"]/);
    expect(src).not.toMatch(/api\.POST\(\s*['"]\/tenant\/disconnect['"]/);
    expect(src).not.toMatch(/api\.DELETE\(\s*['"]\/me\/account['"]/);
  });

  it('onboarding page no longer calls api.POST for moved endpoints', () => {
    const src = readOnboardingPage();
    expect(src).not.toMatch(/api\.POST\(\s*['"]\/onboarding\/select-template['"]/);
    expect(src).not.toMatch(/api\.POST\(\s*['"]\/onboarding\/complete['"]/);
  });
});
