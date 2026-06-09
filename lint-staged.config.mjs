// Cross-platform lint-staged configuration.
//
// The backend/**/*.java entry is a FUNCTION that ignores the staged-files argument so
// lint-staged does not append the staged filenames as Gradle task names; on Windows it
// invokes gradlew.bat, elsewhere ./gradlew. lint-staged spawns commands via cross-spawn
// without a shell, so the Gradle wrapper is referenced by an absolute path resolved from
// this config file's own location (the repo root). The apps/web/** entries are plain
// arrays and DO receive the staged filenames.
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const repoRoot = path.dirname(fileURLToPath(import.meta.url));
// lint-staged parses the command with string-argv, which strips backslashes even inside
// double quotes — so wrapper paths use forward slashes (accepted by Node/cross-spawn and
// by cmd.exe). Quoted to survive any space in the repo path on other machines.
const toForwardSlashes = (target) => target.split(path.sep).join('/');

const gradlewName = process.platform === 'win32' ? 'gradlew.bat' : 'gradlew';
const gradlew = toForwardSlashes(path.join(repoRoot, gradlewName));

// Node 18.20+/20.12+/22+ refuse to spawn .cmd/.bat files without a shell (the
// CVE-2024-27980 mitigation), and lint-staged spawns commands via cross-spawn WITHOUT a
// shell. On Windows the `pnpm` shim is `pnpm.CMD`, so a bare `pnpm ...` command ENOENTs in
// the pre-commit hook. Invoke pnpm through the real node binary (process.execPath, a true
// .exe Node can always spawn) + the corepack-bundled `pnpm.js` instead. Non-Windows keeps
// the plain `pnpm` binary, which is directly spawnable there.
const corepackPnpmJs = path.join(
  path.dirname(process.execPath),
  'node_modules',
  'corepack',
  'dist',
  'pnpm.js',
);
const pnpm =
  process.platform === 'win32' && existsSync(corepackPnpmJs)
    ? `"${toForwardSlashes(process.execPath)}" "${toForwardSlashes(corepackPnpmJs)}"`
    : 'pnpm';

export default {
  'backend/**/*.java': (_files) => `"${gradlew}" spotlessApply -q`,
  'apps/web/**/*.{ts,tsx,js,jsx}': [
    `${pnpm} --filter web exec eslint --fix`,
    `${pnpm} --filter web exec prettier --write`,
  ],
  'apps/web/messages/*.json': [
    `${pnpm} --filter web exec prettier --write`,
    `${pnpm} --filter web run i18n:check`,
  ],
  'apps/web/!(messages)/**/*.{md,mdx,json,css}': [`${pnpm} --filter web exec prettier --write`],
  'apps/web/*.{md,json,css}': [`${pnpm} --filter web exec prettier --write`],
  'apps/admin/**/*.{ts,tsx,js,jsx}': [`${pnpm} --filter @zeromail/admin exec eslint --fix`],
};
