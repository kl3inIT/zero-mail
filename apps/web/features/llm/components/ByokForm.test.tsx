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
      screen.getByRole('radio', { name: viMessages.llm.byok.provider.openrouter }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('radio', { name: viMessages.llm.byok.provider.openai }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('radio', { name: viMessages.llm.byok.provider.anthropic }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('radio', { name: viMessages.llm.byok.provider.googleGenAi }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('radio', { name: viMessages.llm.byok.provider.deepseek }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('radio', { name: viMessages.llm.byok.provider.openaiCompatible }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('radio', { name: viMessages.llm.byok.provider.anthropicCompatible }),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText(viMessages.llm.byok.endpoint.openaiCompatibleLabel)).toBeNull();
    expect(screen.getByLabelText(viMessages.llm.byok.model.label)).toHaveValue(
      'openai/gpt-4o-mini',
    );
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

  it('enables save after validate succeeds and keeps provider model inventory hidden', async () => {
    apiMocks.validateByok.mockResolvedValue({ ok: true, models: ['provider/noisy-model'] });
    renderByokForm();

    fireEvent.change(screen.getByLabelText(viMessages.llm.byok.apiKey.label), {
      target: { value: 'sk-or-v1-test' },
    });
    fireEvent.click(screen.getByRole('button', { name: viMessages.llm.byok.validateCta }));

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(viMessages.llm.byok.validation.success);
    });
    expect(screen.getByTestId('byok-validation-success-alert')).toHaveClass(
      'border-green/30',
      'bg-green-soft/60',
      'text-green',
    );
    expect(screen.queryByText('provider/noisy-model')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: viMessages.llm.byok.saveCta })).toBeEnabled();

    fireEvent.change(screen.getByLabelText(viMessages.llm.byok.model.label), {
      target: { value: 'anthropic/claude-3.5-sonnet' },
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
      preset: 'openrouter',
      endpoint: undefined,
      model: 'openai/gpt-4o-mini',
      apiKey: 'sk-or-v1-test',
    });
    expect(apiKeyInput.value).toBe('');
    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(viMessages.llm.byok.save.success);
    });
  });

  it('requires endpoint only for compatible presets', async () => {
    apiMocks.validateByok.mockResolvedValue({ ok: true, models: ['gpt-4o-mini'] });
    renderByokForm();

    fireEvent.click(
      screen.getByRole('radio', { name: viMessages.llm.byok.provider.openaiCompatible }),
    );
    expect(screen.getByLabelText(viMessages.llm.byok.endpoint.openaiCompatibleLabel)).toBeVisible();
    fireEvent.change(screen.getByLabelText(viMessages.llm.byok.apiKey.label), {
      target: { value: 'sk-compatible-test' },
    });
    expect(screen.getByRole('button', { name: viMessages.llm.byok.validateCta })).toBeDisabled();

    fireEvent.change(screen.getByLabelText(viMessages.llm.byok.endpoint.openaiCompatibleLabel), {
      target: { value: 'https://together.xyz/v1' },
    });
    fireEvent.click(screen.getByRole('button', { name: viMessages.llm.byok.validateCta }));

    await waitFor(() => {
      expect(apiMocks.validateByok.mock.calls[0]?.[0]).toEqual({
        preset: 'openai-compatible',
        endpoint: 'https://together.xyz/v1',
        model: 'openai/gpt-4o-mini',
        apiKey: 'sk-compatible-test',
      });
    });
  });

  it('sends google genai official preset without an endpoint', async () => {
    apiMocks.validateByok.mockResolvedValue({ ok: true, models: ['gemini-2.0-flash'] });
    renderByokForm();

    fireEvent.click(screen.getByRole('radio', { name: viMessages.llm.byok.provider.googleGenAi }));
    fireEvent.change(screen.getByLabelText(viMessages.llm.byok.apiKey.label), {
      target: { value: 'google-key' },
    });
    fireEvent.click(screen.getByRole('button', { name: viMessages.llm.byok.validateCta }));

    await waitFor(() => {
      expect(apiMocks.validateByok.mock.calls[0]?.[0]).toEqual({
        preset: 'google-genai',
        endpoint: undefined,
        model: 'gemini-2.0-flash',
        apiKey: 'google-key',
      });
    });
  });

  it('renders a destructive validation alert when validation fails', async () => {
    apiMocks.validateByok.mockResolvedValue({ ok: false, reason: 'model_not_found' });
    renderByokForm();

    fireEvent.change(screen.getByLabelText(viMessages.llm.byok.apiKey.label), {
      target: { value: 'sk-invalid' },
    });
    fireEvent.click(screen.getByRole('button', { name: viMessages.llm.byok.validateCta }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(viMessages.errors.llm.byokValidateFailed);
      expect(screen.getByRole('alert')).toHaveTextContent(
        viMessages.llm.byok.validation.modelNotFound,
      );
    });
  });
});
