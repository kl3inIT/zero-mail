import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import enMessages from '@/i18n/messages/en.json';
import { RuleComposer } from '@/features/rules/components/RuleComposer';
import { RuleList } from '@/features/rules/components/RuleList';

describe('RulesWorkspace Wave 0 contract', () => {
  it('renders one inline clarification prompt under the original source textarea', () => {
    renderWithMessages(
      <RuleComposer
        sourceText="Archive receipts from Stripe"
        clarificationAnswer=""
        compileResult={{
          status: 'clarificationRequired',
          clarification: {
            language: 'en',
            question: 'Which receipts should Zero Mail archive?',
          },
          priorCompileContext: 'Which receipts should Zero Mail archive?',
        }}
        compileError={null}
        insufficientCreditError={null}
        isCompiling={false}
        isSaving={false}
        onSourceTextChange={vi.fn()}
        onClarificationAnswerChange={vi.fn()}
        onCompile={vi.fn()}
        onAnswerClarification={vi.fn()}
        onSaveDisabledRule={vi.fn()}
      />,
    );

    expect(screen.getByLabelText('Rule text')).toHaveValue('Archive receipts from Stripe');
    expect(screen.getByText('Which receipts should Zero Mail archive?')).toBeInTheDocument();
    expect(screen.getByLabelText('Clarification answer')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Answer clarification' })).toBeDisabled();
  });

  it('renders HTML-looking rule names as text instead of injected markup', () => {
    const { container } = renderWithMessages(
      <RuleList
        rules={[
          {
            ruleId: 'rule-html',
            displayName: '<img src=x onerror=alert(1)>',
            sourceText: 'Archive receipts',
            enabled: false,
            orderIndex: 1,
            entityVersion: 1,
          },
        ]}
        selectedRuleId="rule-html"
        isLoading={false}
        pendingRuleId={null}
        canEnableRule={() => false}
        onSelectRule={vi.fn()}
        onMoveRule={vi.fn()}
        onEditRule={vi.fn()}
        onToggleEnabled={vi.fn()}
        onDeleteRule={vi.fn()}
      />,
    );

    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
  });

  it('pins UI-SPEC visible copy for component tests', () => {
    expect(enMessages.rules.preview.noWriteNotice).toBe('No Gmail changes were made.');
    expect(enMessages.rules.composer.compileCta).toBe('Compile rule');
    expect(enMessages.rules.preview.previewCta).toBe('Preview rule');
    expect(enMessages.rules.preview.enableCta).toBe('Enable rule');
  });
});

function renderWithMessages(children: ReactNode) {
  return render(
    <NextIntlClientProvider locale="en" messages={enMessages}>
      {children}
    </NextIntlClientProvider>,
  );
}
