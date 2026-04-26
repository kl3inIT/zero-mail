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

  it('root pnpm-workspace.yaml has ignoredBuiltDependencies with sharp + unrs-resolver', () => {
    const src = readFileSync(resolve(REPO_ROOT, 'pnpm-workspace.yaml'), 'utf8');
    expect(src).toMatch(/ignoredBuiltDependencies/);
    expect(src).toMatch(/sharp/);
    expect(src).toMatch(/unrs-resolver/);
  });

  it('root package.json has prepare:"husky" + lint-staged config', () => {
    const pkg = JSON.parse(readFileSync(resolve(REPO_ROOT, 'package.json'), 'utf8'));
    expect(pkg.scripts?.prepare).toBe('husky');
    expect(pkg['lint-staged']).toBeDefined();
    expect(typeof pkg['lint-staged']).toBe('object');
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
