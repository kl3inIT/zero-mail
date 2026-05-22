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
    fireEvent.click(
      screen.getByRole('button', {
        name: /Archive investor updates from portfolio companies/i,
      }),
    );

    expect(onSourceTextChange).toHaveBeenCalledWith(
      'Archive investor updates from portfolio companies',
    );
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
        sourceRef: 'seed:founder:1',
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
        sourceRef: 'seed:student:1',
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
