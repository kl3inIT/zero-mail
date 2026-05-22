import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { RuleCatalogPage } from '@/routes/_authenticated/rule-catalog';

const mocks = vi.hoisted(() => ({
  useRuleCatalogPersonas: vi.fn(),
  useRuleCatalogActions: vi.fn(),
  useSavePersona: vi.fn(),
  useSaveExample: vi.fn(),
  useSaveActionDescriptor: vi.fn(),
  useSetRuleCatalogEnabled: vi.fn(),
  useReorderRuleCatalog: vi.fn(),
}));

vi.mock('@/features/rule-catalog/use-rule-catalog', () => ({
  useRuleCatalogPersonas: mocks.useRuleCatalogPersonas,
  useRuleCatalogActions: mocks.useRuleCatalogActions,
}));

vi.mock('@/features/rule-catalog/use-save-persona', () => ({
  useSavePersona: mocks.useSavePersona,
}));

vi.mock('@/features/rule-catalog/use-save-example', () => ({
  useSaveExample: mocks.useSaveExample,
}));

vi.mock('@/features/rule-catalog/use-save-action-descriptor', () => ({
  useSaveActionDescriptor: mocks.useSaveActionDescriptor,
  useSetRuleCatalogEnabled: mocks.useSetRuleCatalogEnabled,
}));

vi.mock('@/features/rule-catalog/use-reorder-rule-catalog', () => ({
  useReorderRuleCatalog: mocks.useReorderRuleCatalog,
}));

const savePersonaMutateAsync = vi.fn();
const saveExampleMutateAsync = vi.fn();
const saveActionMutateAsync = vi.fn();
const setEnabledMutate = vi.fn();
const reorderMutate = vi.fn();

describe('RuleCatalogPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.useRuleCatalogPersonas.mockReturnValue({
      isLoading: false,
      data: {
        personas: [
          {
            personaId: 'persona-1',
            personaKey: 'founder',
            displayNameEn: 'Founder',
            displayNameVi: 'Nhà sáng lập',
            icon: 'rocket',
            displayOrder: 10,
            enabled: true,
            examples: [
              {
                exampleId: 'example-1',
                exampleTextEn: 'Label investor emails as @[Investor]',
                exampleTextVi: 'Gắn nhãn email nhà đầu tư là @[Investor]',
                displayOrder: 10,
                enabled: true,
                sourceRef: 'inbox-zero:founder:001',
              },
              {
                exampleId: 'example-2',
                exampleTextEn: 'Draft a reply with my calendar link',
                exampleTextVi: 'Tạo bản nháp trả lời kèm link lịch',
                displayOrder: 20,
                enabled: true,
                sourceRef: 'inbox-zero:founder:002',
              },
            ],
          },
        ],
      },
    });
    mocks.useRuleCatalogActions.mockReturnValue({
      isLoading: false,
      data: {
        actions: [
          {
            actionKey: 'archive',
            labelEn: 'Archive',
            labelVi: 'Lưu trữ',
            descriptionEn: 'Remove the message from inbox.',
            descriptionVi: 'Bỏ thư khỏi inbox.',
            riskLevel: 'LOW',
            availabilityStatus: 'AVAILABLE',
            displayOrder: 10,
            enabled: true,
          },
          {
            actionKey: 'send_reply',
            labelEn: 'Send reply',
            labelVi: 'Gửi trả lời',
            descriptionEn: 'Automatically send a reply.',
            descriptionVi: 'Tự động gửi trả lời.',
            riskLevel: 'HIGH',
            availabilityStatus: 'AVAILABLE',
            displayOrder: 20,
            enabled: true,
          },
        ],
      },
    });
    mocks.useSavePersona.mockReturnValue({ isPending: false, mutateAsync: savePersonaMutateAsync });
    mocks.useSaveExample.mockReturnValue({ isPending: false, mutateAsync: saveExampleMutateAsync });
    mocks.useSaveActionDescriptor.mockReturnValue({
      isPending: false,
      mutateAsync: saveActionMutateAsync,
    });
    mocks.useSetRuleCatalogEnabled.mockReturnValue({
      isPending: false,
      mutate: setEnabledMutate,
    });
    mocks.useReorderRuleCatalog.mockReturnValue({ isPending: false, mutate: reorderMutate });
    savePersonaMutateAsync.mockResolvedValue(undefined);
    saveExampleMutateAsync.mockResolvedValue(undefined);
    saveActionMutateAsync.mockResolvedValue(undefined);
  });

  it('renders bilingual examples and saves edited prompt text through the generated feature hook', async () => {
    const user = userEvent.setup();

    render(<RuleCatalogPage />);

    expect(screen.getByRole('heading', { name: 'Rule Catalog' })).toBeInTheDocument();
    expect(screen.getByText('Founder')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Examples' }));
    await user.click(
      screen.getByRole('button', { name: 'Edit example inbox-zero:founder:001' }),
    );

    await user.clear(screen.getByLabelText('Prompt VI'));
    await user.type(
      screen.getByLabelText('Prompt VI'),
      'Gắn nhãn thư nhà đầu tư là Investor',
    );
    await user.click(screen.getByRole('button', { name: 'Lưu' }));

    await waitFor(() =>
      expect(saveExampleMutateAsync).toHaveBeenCalledWith({
        personaId: 'persona-1',
        exampleId: 'example-1',
        request: {
          exampleTextEn: 'Label investor emails as @[Investor]',
          exampleTextVi: 'Gắn nhãn thư nhà đầu tư là Investor',
          displayOrder: 10,
          enabled: true,
          sourceRef: 'inbox-zero:founder:001',
          reason: 'Admin rule catalog UI update',
        },
      }),
    );
  });

  it('toggles and reorders action descriptors without bypassing mutation hooks', async () => {
    const user = userEvent.setup();

    render(<RuleCatalogPage />);

    await user.click(screen.getByRole('tab', { name: 'Actions' }));
    await user.click(screen.getByRole('switch', { name: 'Enable action send_reply' }));

    expect(setEnabledMutate).toHaveBeenCalledWith({
      target: 'action',
      targetId: 'send_reply',
      enabled: false,
      reason: 'Admin rule catalog UI update',
    });

    const sendReplyRow = screen.getByText('send_reply').closest('tr');
    expect(sendReplyRow).not.toBeNull();
    await user.click(within(sendReplyRow!).getByRole('button', { name: 'Move up' }));

    expect(reorderMutate).toHaveBeenCalledWith({
      target: 'actions',
      request: {
        items: [
          { actionKey: 'send_reply', displayOrder: 10 },
          { actionKey: 'archive', displayOrder: 20 },
        ],
        reason: 'Admin rule catalog UI update',
      },
    });
  });

  it('saves bilingual action descriptor edits', async () => {
    const user = userEvent.setup();

    render(<RuleCatalogPage />);

    await user.click(screen.getByRole('tab', { name: 'Actions' }));
    await user.click(screen.getByRole('button', { name: 'Edit action send_reply' }));
    await user.clear(screen.getByLabelText('Label VI'));
    await user.type(screen.getByLabelText('Label VI'), 'Gửi phản hồi');
    await user.click(screen.getByRole('button', { name: 'Lưu' }));

    await waitFor(() =>
      expect(saveActionMutateAsync).toHaveBeenCalledWith({
        actionKey: 'send_reply',
        request: {
          labelEn: 'Send reply',
          labelVi: 'Gửi phản hồi',
          descriptionEn: 'Automatically send a reply.',
          descriptionVi: 'Tự động gửi trả lời.',
          riskLevel: 'HIGH',
          availabilityStatus: 'AVAILABLE',
          displayOrder: 20,
          enabled: true,
          reason: 'Admin rule catalog UI update',
        },
      }),
    );
  });
});
