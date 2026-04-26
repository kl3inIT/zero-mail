/**
 * apps/web/scripts/check-i18n.ts
 *
 * Phase 1.1 Plan 07 — CI translation parity gate (REQ-5).
 *
 * Four checks; ANY failure -> process.exit(1):
 *   1. Parity:           vi.json and en.json have identical leaf-key sets.
 *   2. Backend coverage: every ErrorCodes.java dotted constant is reachable in
 *                        both bundles under the `errors.*` namespace.
 *                        `error.validation` is the special case that maps to the
 *                        LOCKED path `errors.validation.generic`.
 *                        Plus an additional explicit set for the field codes
 *                        the PATCH /me/language endpoint emits.
 *   3. Locked key:       `errors.validation.generic` MUST be a non-empty string
 *                        in BOTH bundles, AND no `errors.validation_` shape may
 *                        exist anywhere in either file (CONTEXT.md ISS-002 lock).
 *   4. Hard-coded EN:    regex scan of Phase 1 in-scope files for prose-shaped
 *                        English string literals (D-D3).
 *
 * Output is human-readable; the FIRST line of any failing block is the
 * machine-grep target. Exit code is the contract for CI consumers.
 */
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

// -----------------------------------------------------------------------------
// Paths
// -----------------------------------------------------------------------------

const REPO_ROOT = resolve(__dirname, '..', '..', '..');
const WEB_ROOT = resolve(__dirname, '..');
const VI_PATH = resolve(WEB_ROOT, 'messages/vi.json');
const EN_PATH = resolve(WEB_ROOT, 'messages/en.json');
const ERROR_CODES_JAVA = resolve(
  REPO_ROOT,
  'backend/api/src/main/java/com/zeromail/api/error/ErrorCodes.java',
);

// Files in scope for the EN-prose scanner. Mirrors PLAN <task1.behavior>.
// Plan 1.3-05 Task 1 — pages relocated into route groups; paths updated
// in lockstep so the scanner does not silently lose coverage (Phase 1.1 D-D3).
// Plan 1.3-05 Task 3 — landing page added under (public)/page.tsx.
const EN_SCAN_FILES = [
  'app/(auth)/login/page.tsx',
  'app/(protected)/onboarding/page.tsx',
  'app/(protected)/settings/page.tsx',
  'app/(public)/page.tsx',
  'app/(public)/layout.tsx',
  'features/onboarding/components/TemplateCard.tsx',
  'features/gmail/components/ConnectionHealthBadge.tsx',
  'features/gmail/components/ReconnectPrompt.tsx',
  'features/account/components/DeleteAccountDialog.tsx',
  'features/i18n/components/LanguageSwitcher.tsx',
];

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------

type JsonObj = Record<string, unknown>;

function readJson(path: string): JsonObj {
  return JSON.parse(readFileSync(path, 'utf8'));
}

/** Walks a JSON object and emits dotted paths to every string leaf. */
function leafKeys(obj: unknown, prefix = ''): string[] {
  if (obj === null || typeof obj !== 'object') {
    return prefix ? [prefix] : [];
  }
  return Object.entries(obj as JsonObj).flatMap(([k, v]) =>
    leafKeys(v, prefix ? `${prefix}.${k}` : k),
  );
}

function getNested(obj: unknown, dotted: string): unknown {
  let cur: unknown = obj;
  for (const part of dotted.split('.')) {
    if (cur == null || typeof cur !== 'object') return undefined;
    cur = (cur as JsonObj)[part];
  }
  return cur;
}

function isNonEmptyString(v: unknown): v is string {
  return typeof v === 'string' && v.length > 0;
}

// -----------------------------------------------------------------------------
// Check 1 — vi/en parity
// -----------------------------------------------------------------------------

function checkParity(vi: JsonObj, en: JsonObj): string[] {
  const failures: string[] = [];
  const viKeys = new Set(leafKeys(vi));
  const enKeys = new Set(leafKeys(en));
  const viOnly = [...viKeys].filter((k) => !enKeys.has(k)).sort();
  const enOnly = [...enKeys].filter((k) => !viKeys.has(k)).sort();
  if (viOnly.length || enOnly.length) {
    failures.push('[parity] vi.json and en.json leaf-key sets differ:');
    for (const k of viOnly) failures.push(`  vi-only: ${k}`);
    for (const k of enOnly) failures.push(`  en-only: ${k}`);
  }
  return failures;
}

// -----------------------------------------------------------------------------
// Check 2 — Backend ErrorCodes coverage
// -----------------------------------------------------------------------------

/**
 * Parse `public static final String FOO = "error.bar.baz";` declarations.
 * Returns the dotted code values.
 */
function extractErrorCodes(javaSource: string): string[] {
  const re = /public\s+static\s+final\s+String\s+\w+\s*=\s*"([^"]+)"/g;
  const out: string[] = [];
  let m: RegExpExecArray | null;
  while ((m = re.exec(javaSource)) !== null) {
    out.push(m[1]);
  }
  return out;
}

/**
 * Map a backend dotted error code to the FE bundle path it MUST resolve to.
 * Mirrors the convention used by `apps/web/lib/api/errors.ts#normalizeCode`:
 * strip the leading `error.` prefix and namespace under `errors.*`.
 *
 * Special case: `error.validation` (generic banner) is LOCKED to
 * `errors.validation.generic` per Plan 05 Task 2 / ISS-002.
 */
function bundlePathForErrorCode(code: string): string {
  if (code === 'error.validation') return 'errors.validation.generic';
  const stripped = code.startsWith('error.') ? code.slice('error.'.length) : code;
  return `errors.${stripped}`;
}

function checkBackendCoverage(vi: JsonObj, en: JsonObj): string[] {
  const failures: string[] = [];
  const javaSource = readFileSync(ERROR_CODES_JAVA, 'utf8');
  const codes = extractErrorCodes(javaSource);

  if (codes.length === 0) {
    failures.push(
      `[backend-coverage] no error codes extracted from ${ERROR_CODES_JAVA} ` +
        `- regex broken or file moved`,
    );
    return failures;
  }

  // Plus the additional explicit field codes the PATCH /me/language endpoint
  // emits (per Plan 02 + Plan 05 SUMMARY field-error key set).
  const explicitExtras = [
    'error.validation.field.language.Pattern',
    'error.validation.field.preferredLanguage.invalid',
  ];
  const allRequired = [...codes, ...explicitExtras];

  for (const code of allRequired) {
    const bundlePath = bundlePathForErrorCode(code);
    const viVal = getNested(vi, bundlePath);
    const enVal = getNested(en, bundlePath);
    if (!isNonEmptyString(viVal)) {
      failures.push(`[backend-coverage] missing in vi.json: code=${code} -> ${bundlePath}`);
    }
    if (!isNonEmptyString(enVal)) {
      failures.push(`[backend-coverage] missing in en.json: code=${code} -> ${bundlePath}`);
    }
  }
  return failures;
}

// -----------------------------------------------------------------------------
// Check 3 - Locked errors.validation.generic key
// -----------------------------------------------------------------------------

function checkLockedKey(vi: JsonObj, en: JsonObj): string[] {
  const failures: string[] = [];
  const LOCKED = 'errors.validation.generic';
  const HINT =
    'Locked key errors.validation.generic missing or alternative shape detected ' +
    '- see Plan 05 Task 2 (ISS-002 lock-in).';

  if (!isNonEmptyString(getNested(vi, LOCKED))) {
    failures.push(`[locked-key] vi.json missing ${LOCKED}. ${HINT}`);
  }
  if (!isNonEmptyString(getNested(en, LOCKED))) {
    failures.push(`[locked-key] en.json missing ${LOCKED}. ${HINT}`);
  }
  // Reject the rejected pre-lock alternative shape (`errors.validation_` /
  // `validation_` token anywhere in either raw file).
  const viRaw = readFileSync(VI_PATH, 'utf8');
  const enRaw = readFileSync(EN_PATH, 'utf8');
  if (/"validation_/.test(viRaw)) {
    failures.push(`[locked-key] vi.json contains rejected "validation_" shape. ${HINT}`);
  }
  if (/"validation_/.test(enRaw)) {
    failures.push(`[locked-key] en.json contains rejected "validation_" shape. ${HINT}`);
  }
  return failures;
}

// -----------------------------------------------------------------------------
// Check 4 - Hard-coded EN-prose scanner (D-D3)
// -----------------------------------------------------------------------------

/**
 * Regex MVP per RESEARCH.md Open Question #3.
 *
 * A "prose" string literal:
 *   - is single- or double-quoted
 *   - length 4..200
 *   - starts with an uppercase ASCII letter
 *   - contains at least one space
 *   - contains only ASCII letters, spaces, and a small punctuation set
 *
 * Allow-list (per-line / per-token):
 *   - `// i18n-allow:` comment on the same line  -> skip the whole line
 *   - tokens in ALLOW_TOKENS                     -> skip the match
 *   - JSX/HTML attribute values whose name is in ATTR_DENYLIST -> skip
 *     (className, aria-* except aria-label, href, src, id, key, type, name,
 *      data-testid, alt, title=, role=, placeholder=)
 *
 * Note: aria-label values are NOT auto-skipped (they SHOULD be translated);
 * the call sites in this codebase pass `t(...)` which is not a string literal,
 * so this is fine.
 */
const PROSE_RE = /(['"])([A-Z][A-Za-z][A-Za-z .,!?'"\-:]{2,200})\1/g;

const ATTR_DENYLIST = new Set([
  'classname',
  'href',
  'src',
  'id',
  'key',
  'type',
  'name',
  'data-testid',
  'alt',
  'title',
  'role',
  'placeholder',
]);

const ALLOW_TOKENS = new Set<string>([
  // Typed-confirmation token (UI-SPEC §"Destructive Confirmations" - NOT prose).
  'delete my data',
]);

/**
 * Heuristic: was this string literal sitting on the right-hand side of a JSX
 * attribute whose name we always allow (e.g. className="...")? If so, skip.
 */
function isInsideAllowedAttr(line: string, matchIdx: number): boolean {
  // Look backward for the nearest `xxx=` pattern.
  const before = line.slice(0, matchIdx);
  const m = before.match(/([A-Za-z][A-Za-z0-9_-]*)\s*=\s*$/);
  if (!m) return false;
  const attr = m[1].toLowerCase();
  // Allow aria-* except aria-label.
  if (attr.startsWith('aria-') && attr !== 'aria-label') return true;
  return ATTR_DENYLIST.has(attr);
}

/**
 * Heuristic: is this line an `import ... from "..."` line, or otherwise a
 * known non-prose context?
 */
function isNonProseLine(line: string): boolean {
  const trimmed = line.trim();
  if (trimmed.startsWith('import ')) return true;
  if (trimmed.startsWith('export {') || trimmed.startsWith('export *')) return true;
  if (trimmed.startsWith('//')) return true;
  if (trimmed.startsWith('*')) return true; // JSDoc continuation
  if (trimmed.startsWith('/*') || trimmed.startsWith('*/')) return true;
  if (trimmed.includes('// i18n-allow')) return true;
  return false;
}

function scanFileForEnglishProse(absPath: string, relPath: string): string[] {
  let src: string;
  try {
    src = readFileSync(absPath, 'utf8');
  } catch {
    return [`[en-scanner] cannot read ${relPath} (file missing)`];
  }
  const failures: string[] = [];
  const lines = src.split(/\r?\n/);
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (isNonProseLine(line)) continue;
    // Reset regex state per line.
    PROSE_RE.lastIndex = 0;
    let m: RegExpExecArray | null;
    while ((m = PROSE_RE.exec(line)) !== null) {
      const [, , value] = m;
      if (ALLOW_TOKENS.has(value)) continue;
      // Must contain at least one whitespace-delimited boundary AND >= 2 words.
      const words = value.trim().split(/\s+/);
      if (words.length < 2) continue;
      // Drop matches that are obviously code identifiers (e.g. enum-like).
      if (/^[A-Z_]+$/.test(value.replace(/\s+/g, ''))) continue;
      // Drop attribute-value matches.
      if (isInsideAllowedAttr(line, m.index)) continue;
      failures.push(
        `[en-scanner] ${relPath}:${i + 1} prose-literal "${value}" - wrap in t(...) or add // i18n-allow`,
      );
    }
  }
  return failures;
}

function checkEnglishLiterals(): string[] {
  const failures: string[] = [];
  for (const rel of EN_SCAN_FILES) {
    const abs = resolve(WEB_ROOT, rel);
    failures.push(...scanFileForEnglishProse(abs, rel));
  }
  return failures;
}

// -----------------------------------------------------------------------------
// Main
// -----------------------------------------------------------------------------

function main(): void {
  const vi = readJson(VI_PATH);
  const en = readJson(EN_PATH);

  const allFailures: string[] = [];
  allFailures.push(...checkParity(vi, en));
  allFailures.push(...checkBackendCoverage(vi, en));
  allFailures.push(...checkLockedKey(vi, en));
  allFailures.push(...checkEnglishLiterals());

  if (allFailures.length > 0) {
    console.error('i18n:check FAILED with', allFailures.length, 'issue(s):');
    for (const f of allFailures) console.error(f);
    process.exit(1);
  }

  const totalKeys = leafKeys(vi).length;
  console.log(
    `i18n:check OK - vi/en parity, ${totalKeys} leaf keys, backend ErrorCodes coverage, ` +
      `locked errors.validation.generic, no English-prose literals in ${EN_SCAN_FILES.length} Phase 1 files.`,
  );
}

main();
