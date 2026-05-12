import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { describe, it, expect } from 'vitest';

const APP_WEB = resolve(__dirname, '../..');
const REPO_ROOT = resolve(APP_WEB, '../..');

describe('Phase 1.3 — Workspace cleanup (D-E1..D-E4)', () => {
  it('apps/web/pnpm-lock.yaml is deleted (D-E4 step 1)', () => {
    expect(existsSync(resolve(APP_WEB, 'pnpm-lock.yaml'))).toBe(false);
  });

  it('apps/web/pnpm-workspace.yaml is deleted (D-E4 step 2)', () => {
    expect(existsSync(resolve(APP_WEB, 'pnpm-workspace.yaml'))).toBe(false);
  });

  it('root pnpm-workspace.yaml uses pnpm 11 allowBuilds for dependency build policy', () => {
    const src = readFileSync(resolve(REPO_ROOT, 'pnpm-workspace.yaml'), 'utf8');
    expect(src).toMatch(/allowBuilds/);
    expect(src).toMatch(/sharp/);
    expect(src).toMatch(/unrs-resolver/);
  });

  it('root package.json has prepare:"husky" and root lint-staged config exists', () => {
    const pkg = JSON.parse(readFileSync(resolve(REPO_ROOT, 'package.json'), 'utf8'));
    expect(pkg.scripts?.prepare).toBe('husky');

    const lintStagedConfigPath = resolve(REPO_ROOT, 'lint-staged.config.mjs');
    expect(existsSync(lintStagedConfigPath)).toBe(true);
    const lintStagedConfig = readFileSync(lintStagedConfigPath, 'utf8');
    expect(lintStagedConfig).toMatch(/backend\/\*\*\/\*\.java/);
    expect(lintStagedConfig).toMatch(/spotlessApply/);
    expect(lintStagedConfig).toMatch(/apps\/web\/\*\*\/\*\.\{ts,tsx,js,jsx\}/);
    expect(lintStagedConfig).toMatch(/eslint --fix/);
    expect(lintStagedConfig).toMatch(/prettier --write/);
    expect(lintStagedConfig).toMatch(/i18n:check/);
  });

  it('.husky/pre-commit exists at repo root and runs lint-staged', () => {
    const hookPath = resolve(REPO_ROOT, '.husky/pre-commit');
    expect(existsSync(hookPath)).toBe(true);
    const hook = readFileSync(hookPath, 'utf8');
    expect(hook).toMatch(/pnpm\s+exec\s+lint-staged/);
    const indexEntry = execFileSync('git', ['ls-files', '--stage', '.husky/pre-commit'], {
      cwd: REPO_ROOT,
      encoding: 'utf8',
    });
    expect(indexEntry).toMatch(/^100755\s/);
    // Husky 9: no shebang, no husky.sh source line (Pitfall 7)
    expect(hook).not.toMatch(/husky\.sh/);
  });

  it('.prettierrc.json and .prettierignore exist at repo root', () => {
    expect(existsSync(resolve(REPO_ROOT, '.prettierrc.json'))).toBe(true);
    expect(existsSync(resolve(REPO_ROOT, '.prettierignore'))).toBe(true);
  });

  it('.prettierrc.json declares prettier-plugin-tailwindcss', () => {
    const cfg = JSON.parse(readFileSync(resolve(REPO_ROOT, '.prettierrc.json'), 'utf8'));
    expect(cfg.plugins).toContain('prettier-plugin-tailwindcss');
  });
});
