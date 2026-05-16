import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import enMessages from '@/i18n/messages/en.json';
import { RuleTemplateGallery } from '@/features/rules/components/RuleTemplateGallery';
import { RuleComposer } from '@/features/rules/components/RuleComposer';
import { RuleList } from '@/features/rules/components/RuleList';
import { RulesWorkspace } from '@/features/rules/components/RulesWorkspace';

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
            entityVersion: 1,
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

  it('previews a dirty selected rule as a draft instead of the saved rule version', async () => {
    const previewDraftRule = vi.fn().mockResolvedValue(previewResult());
    const previewSavedRule = vi.fn().mockResolvedValue(previewResult());
    const compileRule = vi.fn().mockResolvedValue({
      status: 'compiled',
      compiled: compiledPayload(
        'Archive GitHub receipts',
        '{"schemaVersion":"rules.v1","type":"SENDER_DOMAIN","domain":"github.com"}',
      ),
    });
    mockRulesWorkspaceHooks({ compileRule, previewDraftRule, previewSavedRule });

    renderWithMessages(<RulesWorkspace />);

    // RuleList renders the rule row in both the desktop table and the mobile
    // card list, so "Rule actions" appears twice; pick the first.
    const ruleActionsButtons = await screen.findAllByRole('button', { name: 'Rule actions' });
    fireEvent.click(ruleActionsButtons[0]!);
    fireEvent.click(await screen.findByRole('menuitem', { name: 'Edit rule' }));
    // Editing an existing compiled rule opens the manual tab by default
    // (lastCompiled is non-null). Switch to the describe tab so the source
    // textarea is mounted and we can simulate the user dirtying it.
    fireEvent.click(await screen.findByRole('tab', { name: /Describe/i }));
    const sourceTextarea = await screen.findByLabelText(
      'Which emails should Zero Mail match, and what should it do?',
    );
    await waitFor(() => expect(sourceTextarea).toHaveValue('Archive Stripe receipts'));
    fireEvent.change(sourceTextarea, { target: { value: 'Archive GitHub receipts' } });
    fireEvent.click(screen.getByRole('button', { name: 'Convert to rule' }));
    await waitFor(() => expect(compileRule).toHaveBeenCalled());
    // The in-dialog "Preview rule" shortcut was removed; preview now happens
    // from RulePreviewPanel. The dialog is still open which marks the rest
    // of the page as aria-hidden, so we have to include hidden elements
    // when querying for the panel's button.
    const previewButtons = await screen.findAllByRole('button', {
      name: 'Preview rule',
      hidden: true,
    });
    fireEvent.click(previewButtons[0]!);

    await waitFor(() => expect(previewDraftRule).toHaveBeenCalled());
    expect(previewSavedRule).not.toHaveBeenCalled();
  });

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

function mockRulesWorkspaceHooks({
  compileRule,
  previewDraftRule,
  previewSavedRule,
}: {
  compileRule: ReturnType<typeof vi.fn>;
  previewDraftRule: ReturnType<typeof vi.fn>;
  previewSavedRule: ReturnType<typeof vi.fn>;
}) {
  rulesHooks.useRules.mockReturnValue({
    data: {
      rules: [savedRule()],
      templates: [],
      materialization: {
        createdCount: 0,
        skippedCount: 0,
        customizedPreservedCount: 0,
        createdRules: [],
        skippedTemplates: [],
      },
    },
    error: null,
    isLoading: false,
    isSuccess: true,
  });
  rulesHooks.useRuleTemplates.mockReturnValue({ data: [], isLoading: false });
  rulesHooks.useCompileRule.mockReturnValue({ mutateAsync: compileRule, isPending: false });
  rulesHooks.useCreateRule.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
  rulesHooks.useUpdateRule.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
  rulesHooks.useDeleteRule.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
  rulesHooks.usePreviewSavedRule.mockReturnValue({
    mutateAsync: previewSavedRule,
    isPending: false,
  });
  rulesHooks.usePreviewDraftRule.mockReturnValue({
    mutateAsync: previewDraftRule,
    isPending: false,
  });
  rulesHooks.usePreviewCustomMail.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
  rulesHooks.useUpdateRuleEnabled.mockReturnValue({ mutateAsync: vi.fn(), isPending: false });
  rulesHooks.useMaterializeRuleTemplate.mockReturnValue({
    mutateAsync: vi.fn(),
    isPending: false,
  });
}

function savedRule() {
  return {
    ruleId: '11111111-1111-1111-1111-111111111111',
    displayName: 'Archive Stripe receipts',
    sourceText: 'Archive Stripe receipts',
    enabled: false,
    orderIndex: 1,
    sourceLanguage: 'en',
    schemaVersion: 'rules.v1',
    matcherAst: '{"schemaVersion":"rules.v1","type":"SENDER_DOMAIN","domain":"stripe.com"}',
    actionIntents: '[{"type":"archive"}]',
    entityVersion: 0,
    lastPreviewedEntityVersion: null,
    lastPreviewedAt: null,
    templateKey: null,
    templateVersion: null,
    customized: false,
  };
}

function compiledPayload(displayName: string, matcherAst: string) {
  return {
    status: 'compiled',
    displayName,
    sourceLanguage: 'en',
    schemaVersion: 'rules.v1',
    matcherAst,
    actionIntents: '[{"type":"archive"}]',
  };
}

function previewResult() {
  return {
    impactSummary: {
      sampleSize: 10,
      sampledMessageCount: 0,
      matchedCount: 0,
      proposedActionCounts: {},
      deferredCount: 0,
      conflictCount: 0,
      noWriteNotice: true,
      noWriteNoticeKey: 'rules.preview.noGmailChanges',
    },
    rows: [],
    savedRuleMarkedPreviewed: false,
  };
}
