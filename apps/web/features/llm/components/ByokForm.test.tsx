import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import viMessages from '@/i18n/messages/vi.json';

const apiMocks = vi.hoisted(() => ({
  getCurrentByok: vi.fn(),
  saveByok: vi.fn(),
  validateByok: vi.fn(),
}));

vi.mock('@/features/llm/api/llm-api', async (importOriginal) => {
  const actual = (await importOriginal()) as Record<string, unknown>;
  return {
    ...actual,
    getCurrentByok: apiMocks.getCurrentByok,
    saveByok: apiMocks.saveByok,
    validateByok: apiMocks.validateByok,
  };
});

import { ByokForm } from '@/features/llm/components/ByokForm';

function renderByokForm() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <NextIntlClientProvider locale="vi" messages={viMessages}>
      <QueryClientProvider client={queryClient}>
        <ByokForm />
      </QueryClientProvider>
    </NextIntlClientProvider>,
  );
}

describe('ByokForm', () => {
  beforeEach(() => {
    apiMocks.getCurrentByok.mockReset();
    apiMocks.saveByok.mockReset();
    apiMocks.validateByok.mockReset();
    apiMocks.getCurrentByok.mockResolvedValue(null);
  });

  it('renders provider options and keeps actions disabled until required fields are present', () => {
    renderByokForm();

    expect(
      screen.getByRole('radio', { name: viMessages.llm.byok.provider.openaiCompatible }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('radio', { name: viMessages.llm.byok.provider.anthropic }),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: viMessages.llm.byok.validateCta })).toBeDisabled();
    expect(screen.getByRole('button', { name: viMessages.llm.byok.saveCta })).toBeDisabled();
  });

  it('does not mirror the raw apiKey into rendered React state', () => {
    renderByokForm();

    fireEvent.change(screen.getByLabelText(viMessages.llm.byok.apiKey.label), {
      target: { value: 'sk-secret-test-value' },
    });

    const stateSnapshot = screen.getByTestId('form-state-snapshot');
    expect(stateSnapshot).toHaveTextContent('"hasApiKey":true');
    expect(stateSnapshot).not.toHaveTextContent('sk-secret-test-value');
  });

  it('enables save after validate succeeds and disables save again after field changes', async () => {
    apiMocks.validateByok.mockResolvedValue({ ok: true, models: ['openai/gpt-4o-mini'] });
    renderByokForm();

    fireEvent.change(screen.getByLabelText(viMessages.llm.byok.apiKey.label), {
      target: { value: 'sk-or-v1-test' },
    });
    fireEvent.click(screen.getByRole('button', { name: viMessages.llm.byok.validateCta }));

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(viMessages.llm.byok.validation.success);
    });
    expect(screen.getByRole('button', { name: viMessages.llm.byok.saveCta })).toBeEnabled();

    fireEvent.change(screen.getByLabelText(viMessages.llm.byok.endpoint.label), {
      target: { value: 'https://openrouter.ai/api/v1/custom' },
    });

    expect(screen.getByRole('button', { name: viMessages.llm.byok.saveCta })).toBeDisabled();
  });

  it('resets the password input on save success', async () => {
    apiMocks.validateByok.mockResolvedValue({ ok: true, models: ['openai/gpt-4o-mini'] });
    apiMocks.saveByok.mockResolvedValue({ ok: true, savedAt: '2026-05-08T04:00:00Z' });
    renderByokForm();

    const apiKeyInput = screen.getByLabelText(viMessages.llm.byok.apiKey.label) as HTMLInputElement;
    fireEvent.change(apiKeyInput, { target: { value: 'sk-or-v1-test' } });
    fireEvent.click(screen.getByRole('button', { name: viMessages.llm.byok.validateCta }));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: viMessages.llm.byok.saveCta })).toBeEnabled();
    });
    fireEvent.click(screen.getByRole('button', { name: viMessages.llm.byok.saveCta }));

    await waitFor(() => {
      expect(apiMocks.saveByok).toHaveBeenCalled();
    });
    expect(apiMocks.saveByok.mock.calls[0]?.[0]).toEqual({
      provider: 'openai-compatible',
      endpoint: 'https://openrouter.ai/api/v1',
      apiKey: 'sk-or-v1-test',
    });
    expect(apiKeyInput.value).toBe('');
    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(viMessages.llm.byok.save.success);
    });
  });

  it('renders a destructive validation alert when validation fails', async () => {
    apiMocks.validateByok.mockResolvedValue({ ok: false, reason: 'invalid' });
    renderByokForm();

    fireEvent.change(screen.getByLabelText(viMessages.llm.byok.apiKey.label), {
      target: { value: 'sk-invalid' },
    });
    fireEvent.click(screen.getByRole('button', { name: viMessages.llm.byok.validateCta }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(viMessages.errors.llm.byokValidateFailed);
      expect(screen.getByRole('alert')).toHaveTextContent(viMessages.llm.byok.validation.invalid);
    });
  });
});
