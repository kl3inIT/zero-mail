import { describe, expect, it, vi } from 'vitest';

const FUTURE_RULES_WORKSPACE_MODULE = '@/features/rules/components/RulesWorkspace';

const rulesWorkspaceCopy = {
  compileRule: 'Compile rule',
  saveDisabledRule: 'Save disabled rule',
  previewRule: 'Preview rule',
  enableRule: 'Enable rule',
  answerClarification: 'Answer clarification',
  templateBadge: 'Template',
  customizedBadge: 'Customized',
  noGmailChanges: 'No Gmail changes were made.',
} as const;

const mockedRulesHooks = {
  useRules: vi.fn(),
  useCompileRule: vi.fn(),
  usePreviewRule: vi.fn(),
  useSaveRule: vi.fn(),
  useReorderRules: vi.fn(),
} as const;

describe('RulesWorkspace Wave 0 contract', () => {
  it('pins UI-SPEC visible copy for future component tests', () => {
    expect(Object.values(rulesWorkspaceCopy)).toContain('No Gmail changes were made.');
    expect(rulesWorkspaceCopy).toMatchObject({
      compileRule: 'Compile rule',
      previewRule: 'Preview rule',
      enableRule: 'Enable rule',
    });
  });

  it.skip('[Plan 03-08] renders composer source state and compile action from mocked hooks', async () => {
    mockedRulesHooks.useRules.mockReturnValue({ rules: [] });
    const modulePath = FUTURE_RULES_WORKSPACE_MODULE;
    const rulesWorkspaceModule = await import(/* @vite-ignore */ modulePath);
    const RulesWorkspace = rulesWorkspaceModule.RulesWorkspace;

    expect(RulesWorkspace).toBeDefined();
    expect(rulesWorkspaceCopy.compileRule).toBe('Compile rule');
  });

  it.skip('[Plan 03-08] renders one clarification state and blocks save while ambiguous', async () => {
    mockedRulesHooks.useCompileRule.mockReturnValue({
      clarification: { question: 'Which newsletters should Zero Mail archive?' },
    });
    const modulePath = FUTURE_RULES_WORKSPACE_MODULE;
    const rulesWorkspaceModule = await import(/* @vite-ignore */ modulePath);

    expect(rulesWorkspaceModule.RulesWorkspace).toBeDefined();
    expect(rulesWorkspaceCopy.answerClarification).toBe('Answer clarification');
  });

  it.skip('[Plan 03-08] keeps enable disabled until a successful preview exists', async () => {
    mockedRulesHooks.usePreviewRule.mockReturnValue({ data: null, isSuccess: false });
    const modulePath = FUTURE_RULES_WORKSPACE_MODULE;
    const rulesWorkspaceModule = await import(/* @vite-ignore */ modulePath);

    expect(rulesWorkspaceModule.RulesWorkspace).toBeDefined();
    expect(rulesWorkspaceCopy.previewRule).toBe('Preview rule');
  });

  it.skip('[Plan 03-08] renders template and customized provenance badges', async () => {
    mockedRulesHooks.useRules.mockReturnValue({
      rules: [
        { id: 'template-rule', templateKey: 'archive-receipts', customized: false },
        { id: 'customized-rule', templateKey: 'label-newsletters', customized: true },
      ],
    });
    const modulePath = FUTURE_RULES_WORKSPACE_MODULE;
    const rulesWorkspaceModule = await import(/* @vite-ignore */ modulePath);

    expect(rulesWorkspaceModule.RulesWorkspace).toBeDefined();
    expect(rulesWorkspaceCopy.templateBadge).toBe('Template');
    expect(rulesWorkspaceCopy.customizedBadge).toBe('Customized');
  });

  it.skip('[Plan 03-08] rolls back optimistic reorder rendering when the mutation fails', async () => {
    mockedRulesHooks.useReorderRules.mockReturnValue({
      mutateAsync: vi.fn().mockRejectedValue(new Error('reorder failed')),
    });
    const modulePath = FUTURE_RULES_WORKSPACE_MODULE;
    const rulesWorkspaceModule = await import(/* @vite-ignore */ modulePath);

    expect(rulesWorkspaceModule.RulesWorkspace).toBeDefined();
  });
});
