import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import enMessages from '@/i18n/messages/en.json';
import { RuleTemplateGallery } from '@/features/rules/components/RuleTemplateGallery';
import { RuleComposer } from '@/features/rules/components/RuleComposer';
import { RuleList } from '@/features/rules/components/RuleList';

const rulesHooks = vi.hoisted(() => ({
  useRules: vi.fn(),
  useRuleTemplates: vi.fn(),
  useCompileRule: vi.fn(),
  useCreateRule: vi.fn(),
  useUpdateRule: vi.fn(),
  useDeleteRule: vi.fn(),
  usePreviewSavedRule: vi.fn(),
  usePreviewDraftRule: vi.fn(),
  usePreviewCustomMail: vi.fn(),
  useUpdateRuleEnabled: vi.fn(),
  useMaterializeRuleTemplate: vi.fn(),
}));

vi.mock('@/features/rules/hooks/use-rules', () => rulesHooks);

describe('RulesWorkspace Wave 0 contract', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

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

    expect(
      screen.getByLabelText('Which emails should Zero Mail match, and what should it do?'),
    ).toHaveValue('Archive receipts from Stripe');
    expect(screen.getByText('Which receipts should Zero Mail archive?')).toBeInTheDocument();
    expect(screen.getByLabelText('Your answer')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Send answer' })).toBeDisabled();
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
            sourceLanguage: 'en',
            schemaVersion: 'v1',
            matcherAst: '{}',
            actionIntents: '[]',
            entityVersion: 1,
            lastPreviewedEntityVersion: null,
            lastPreviewedAt: null,
            templateKey: null,
            templateVersion: null,
            customized: false,
          },
        ]}
        selectedRuleId="rule-html"
        isLoading={false}
        pendingRuleId={null}
        onSelectRule={vi.fn()}
        onEditRule={vi.fn()}
        onToggleEnabled={vi.fn()}
        onDeleteRule={vi.fn()}
      />,
    );

    // RuleList renders rule names in both the desktop table and the mobile
    // card list, so multiple matches are expected; assert at least one and
    // that no real <img> tag was ever inserted (the XSS guard).
    expect(screen.getAllByText('<img src=x onerror=alert(1)>').length).toBeGreaterThan(0);
    expect(container.querySelector('img')).toBeNull();
  });

  it('pins UI-SPEC visible copy for component tests', () => {
    expect(enMessages.rules.preview.noWriteNotice).toBe('No Gmail changes were made.');
    expect(enMessages.rules.composer.compileCta).toBe('Convert to rule');
    expect(enMessages.rules.preview.previewCta).toBe('Preview rule');
    expect(enMessages.rules.preview.enableCta).toBe('Enable rule');
  });

  // The "preview a dirty selected rule as a draft" flow was removed when
  // /rules switched to a two-tab layout (Danh sách | Kiểm tra) and the
  // Test tab moved to previewAllEnabledRules. The composer dialog no
  // longer has an inline preview path, so this test no longer applies.
  // Keep the rest of the suite asserting on the new flow.

  it('keeps customized materialized templates disabled in the gallery', () => {
    renderWithMessages(
      <RuleTemplateGallery
        templates={[
          {
            templateKey: 'archive-receipts',
            templateVersion: 1,
            displayName: 'Archive receipts',
            localizedCopyKey: 'rules.templates.archive.receipts.sourceText',
            sourceText: 'Archive receipts',
            actionSummary: 'archive',
            status: 'materializable',
            sourcedFromOnboarding: true,
            materialized: true,
            customized: true,
          },
        ]}
        isLoading={false}
        pendingTemplateKey={null}
        onUseTemplate={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'Use starter rule' })).toBeDisabled();
  });
});

function renderWithMessages(children: ReactNode) {
  return render(
    <NextIntlClientProvider locale="en" messages={enMessages}>
      {children}
    </NextIntlClientProvider>,
  );
}
