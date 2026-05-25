import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import enMessages from '@/i18n/messages/en.json';
import { RuleComposer } from '@/features/rules/components/RuleComposer';
import type { RuleCatalogPersonaResponse } from '@/features/rules/api/rule-catalog-api';

describe('RuleComposer catalog examples', () => {
  it('renders DB-backed persona examples and replaces the source textarea exactly', () => {
    const onSourceTextChange = vi.fn();
    renderWithMessages(
      <RuleComposer
        sourceText=""
        clarificationAnswer=""
        compileResult={null}
        lastCompiled={null}
        compileError={null}
        insufficientCreditError={null}
        isCompiling={false}
        isSaving={false}
        examplePersonas={examplePersonas}
        isLoadingExamples={false}
        examplesError={false}
        onSourceTextChange={onSourceTextChange}
        onClarificationAnswerChange={vi.fn()}
        onCompile={vi.fn()}
        onAnswerClarification={vi.fn()}
        onSaveDisabledRule={vi.fn()}
        onSaveManualRule={vi.fn()}
        onRefineManualRule={vi.fn()}
      />,
    );

    expect(screen.getByText('Choose from examples')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Choose from examples' }));
    expect(screen.getByRole('heading', { name: 'Choose persona' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Founder 1 examples/ }));
    expect(
      screen.getByRole('button', {
        name: /Archive investor updates from portfolio companies/i,
      }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('rule-example-grid')).toHaveClass('md:grid-cols-2');
    fireEvent.click(
      screen.getByRole('button', {
        name: /Archive investor updates from portfolio companies/i,
      }),
    );

    expect(onSourceTextChange).toHaveBeenCalledWith(
      'Archive investor updates from portfolio companies',
    );
  });

  it('does not show outbound policy badges inside the manual builder', () => {
    renderWithMessages(
      <RuleComposer
        sourceText=""
        clarificationAnswer=""
        compileResult={null}
        lastCompiled={null}
        compileError={null}
        insufficientCreditError={null}
        isCompiling={false}
        isSaving={false}
        onSourceTextChange={vi.fn()}
        onClarificationAnswerChange={vi.fn()}
        onCompile={vi.fn()}
        onAnswerClarification={vi.fn()}
        onSaveDisabledRule={vi.fn()}
        onSaveManualRule={vi.fn()}
        onRefineManualRule={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole('tab', { name: 'Review & edit' }));

    expect(screen.queryByText('How AI sends email')).not.toBeInTheDocument();
    expect(screen.queryByText('Can send automatically when safe')).not.toBeInTheDocument();
    expect(screen.queryByText('If not safe enough, save a Gmail draft')).not.toBeInTheDocument();
  });
});

const examplePersonas: RuleCatalogPersonaResponse[] = [
  {
    personaId: '00000000-0000-0000-0000-000000000001',
    personaKey: 'founder',
    displayName: 'Founder',
    icon: 'sparkles',
    displayOrder: 10,
    examples: [
      {
        exampleId: '00000000-0000-0000-0000-000000000101',
        exampleText: 'Archive investor updates from portfolio companies',
        displayOrder: 10,
      },
    ],
  },
  {
    personaId: '00000000-0000-0000-0000-000000000002',
    personaKey: 'student',
    displayName: 'Student',
    icon: 'book',
    displayOrder: 120,
    examples: [
      {
        exampleId: '00000000-0000-0000-0000-000000000201',
        exampleText: 'Label scholarship updates as School',
        displayOrder: 10,
      },
    ],
  },
];

function renderWithMessages(children: ReactNode) {
  return render(
    <NextIntlClientProvider locale="en" messages={enMessages}>
      {children}
    </NextIntlClientProvider>,
  );
}
