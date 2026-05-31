import type { ByokProvider } from '@/features/ai/api/byok-api';

export const BYOK_PROVIDER_OPTIONS: Array<{
  provider: ByokProvider;
  defaultBaseUrl: string;
}> = [
  { provider: 'OPENAI', defaultBaseUrl: 'https://api.openai.com/v1' },
  { provider: 'ANTHROPIC', defaultBaseUrl: 'https://api.anthropic.com/v1' },
  { provider: 'GOOGLE', defaultBaseUrl: 'https://generativelanguage.googleapis.com/v1beta' },
  { provider: 'DEEPSEEK', defaultBaseUrl: 'https://api.deepseek.com/v1' },
];

export const DEFAULT_PROVIDER = BYOK_PROVIDER_OPTIONS[0];
