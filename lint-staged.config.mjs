// Cross-platform lint-staged configuration.
//
// The backend/**/*.java entry is a FUNCTION that ignores the staged-files argument so
// lint-staged does not append the staged filenames as Gradle task names; on Windows it
// invokes gradlew.bat, elsewhere ./gradlew. lint-staged spawns commands via cross-spawn
// without a shell, so the Gradle wrapper is referenced by an absolute path resolved from
// this config file's own location (the repo root). The apps/web/** entries are plain
// arrays and DO receive the staged filenames.
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const repoRoot = path.dirname(fileURLToPath(import.meta.url));
// lint-staged parses the command with string-argv, which strips backslashes even inside
// double quotes — so the wrapper path uses forward slashes (accepted by Node/cross-spawn and
// by cmd.exe). Quoted to survive any space in the repo path on other machines.
const gradlewName = process.platform === 'win32' ? 'gradlew.bat' : 'gradlew';
const gradlew = path.join(repoRoot, gradlewName).split(path.sep).join('/');

export default {
  'backend/**/*.java': (_files) => `"${gradlew}" spotlessApply -q`,
  'apps/web/**/*.{ts,tsx,js,jsx}': [
    'pnpm --filter web exec eslint --fix',
    'pnpm --filter web exec prettier --write',
  ],
  'apps/web/messages/*.json': [
    'pnpm --filter web exec prettier --write',
    'pnpm --filter web run i18n:check',
  ],
  'apps/web/!(messages)/**/*.{md,mdx,json,css}': ['pnpm --filter web exec prettier --write'],
  'apps/web/*.{md,json,css}': ['pnpm --filter web exec prettier --write'],
};
