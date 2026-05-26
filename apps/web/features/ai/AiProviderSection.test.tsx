import { render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ByokResponse } from '@/features/ai/api/byok-api';
import {
  AiProviderSection,
  BYOK_PROVIDER_OPTIONS,
} from '@/features/ai/components/AiProviderSection';
import enMessages from '@/i18n/messages/en.json';

const mocks = vi.hoisted(() => ({
  activateMutateAsync: vi.fn(),
  byokState: { data: null as unknown, error: null as unknown, isError: false, isPending: false },
  costState: { data: { usd: 0 }, isPending: false },
  deleteMutateAsync: vi.fn(),
  saveMutateAsync: vi.fn(),
  selectMutateAsync: vi.fn(),
  testMutateAsync: vi.fn(),
}));

vi.mock('@/features/ai/hooks/useByok', () => ({
  useByok: () => mocks.byokState,
}));

vi.mock('@/features/ai/hooks/useAiCost', () => ({
  useAiCost: () => mocks.costState,
}));

vi.mock('@/features/ai/hooks/useSaveByok', () => ({
  useSaveByok: () => ({ mutateAsync: mocks.saveMutateAsync, isPending: false }),
}));

vi.mock('@/features/ai/hooks/useTestByokConnection', () => ({
  useTestByokConnection: () => ({ mutateAsync: mocks.testMutateAsync, isPending: false }),
}));

vi.mock('@/features/ai/hooks/useSelectByokModel', () => ({
  useSelectByokModel: () => ({ mutateAsync: mocks.selectMutateAsync, isPending: false }),
}));

vi.mock('@/features/ai/hooks/useActivateByok', () => ({
  useActivateByok: () => ({ mutateAsync: mocks.activateMutateAsync, isPending: false }),
}));

vi.mock('@/features/ai/hooks/useDeleteByok', () => ({
  useDeleteByok: () => ({ mutateAsync: mocks.deleteMutateAsync, isPending: false }),
}));

describe('AiProviderSection', () => {
  beforeEach(() => {
    mocks.activateMutateAsync.mockReset();
    mocks.byokState.data = null;
    mocks.byokState.error = null;
    mocks.byokState.isError = false;
    mocks.byokState.isPending = false;
    mocks.costState.data = { usd: 0 };
    mocks.deleteMutateAsync.mockReset();
    mocks.saveMutateAsync.mockReset();
    mocks.selectMutateAsync.mockReset();
    mocks.testMutateAsync.mockReset();
  });

  it('keeps BYOK provider options locked to the supported providers', () => {
    expect(BYOK_PROVIDER_OPTIONS.map((providerOption) => providerOption.provider)).toEqual([
      'OPENAI',
      'ANTHROPIC',
      'GOOGLE',
      'DEEPSEEK',
    ]);
    expect(BYOK_PROVIDER_OPTIONS.map((providerOption) => providerOption.provider)).not.toContain(
      'OPENROUTER',
    );
  });

  it('renders platform-key empty state and zero cost footer', () => {
    renderWithMessages(<AiProviderSection />);

    expect(screen.getByText('Using the platform key')).toBeInTheDocument();
    expect(screen.getByText('AI cost last 7 days: $0.00')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Test connection' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });

  it('renders a saved key, model, test status, and cost', () => {
    mocks.byokState.data = byokRow({ active: true, modelId: 'gpt-4o-mini', lastTestResult: 'OK' });
    mocks.costState.data = { usd: 2.43 };

    renderWithMessages(<AiProviderSection />);

    expect(screen.getByText('Saved key: ****abc1')).toBeInTheDocument();
    expect(screen.getByText('gpt-4o-mini')).toBeInTheDocument();
    expect(screen.getAllByText('OK').length).toBeGreaterThan(0);
    expect(screen.getByText('AI cost last 7 days: $2.43')).toBeInTheDocument();
  });
});

function byokRow(overrides: Partial<ByokResponse> = {}): ByokResponse {
  return {
    active: false,
    baseUrl: 'https://api.openai.com/v1',
    lastFourChars: 'abc1',
    provider: 'OPENAI',
    ...overrides,
  };
}

function renderWithMessages(children: ReactNode) {
  return render(
    <NextIntlClientProvider locale="en" messages={enMessages}>
      {children}
    </NextIntlClientProvider>,
  );
}
