import { defineConfig, globalIgnores } from 'eslint/config';
import nextVitals from 'eslint-config-next/core-web-vitals';
import nextTs from 'eslint-config-next/typescript';
import prettier from 'eslint-config-prettier/flat';

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    '.next/**',
    'out/**',
    'build/**',
    'next-env.d.ts',
    'lib/api/schema.d.ts',
    'components/ui/**',
    'components/ai/**',
  ]),
  {
    files: ['**/*.{ts,tsx,js,jsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: [
                '@zeromail/admin',
                '@zeromail/admin/*',
                '../../admin/**',
                '../admin/**',
                'apps/admin/**',
                '**/admin-schema*',
              ],
              message:
                'apps/web cannot import from apps/admin — admin schema types stay out of public bundle (ADMIN-06).',
            },
          ],
        },
      ],
    },
  },
  // eslint-config-prettier MUST be last to disable formatting-related rules
  // that would otherwise conflict with Prettier.
  prettier,
]);

export default eslintConfig;
