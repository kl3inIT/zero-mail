import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { RuleCatalogPage } from '@/routes/_authenticated/rule-catalog';

const mocks = vi.hoisted(() => ({
  useRuleCatalogPersonas: vi.fn(),
  useSavePersona: vi.fn(),
  useSaveExample: vi.fn(),
  useSetRuleCatalogEnabled: vi.fn(),
  useReorderRuleCatalog: vi.fn(),
}));

vi.mock('@/features/rule-catalog/use-rule-catalog', () => ({
  useRuleCatalogPersonas: mocks.useRuleCatalogPersonas,
}));

vi.mock('@/features/rule-catalog/use-save-persona', () => ({
  useSavePersona: mocks.useSavePersona,
}));

vi.mock('@/features/rule-catalog/use-save-example', () => ({
  useSaveExample: mocks.useSaveExample,
}));

vi.mock('@/features/rule-catalog/use-save-action-descriptor', () => ({
  useSetRuleCatalogEnabled: mocks.useSetRuleCatalogEnabled,
}));

vi.mock('@/features/rule-catalog/use-reorder-rule-catalog', () => ({
  useReorderRuleCatalog: mocks.useReorderRuleCatalog,
}));

const savePersonaMutateAsync = vi.fn();
const saveExampleMutateAsync = vi.fn();
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
              },
              {
                exampleId: 'example-2',
                exampleTextEn: 'Draft a reply with my calendar link',
                exampleTextVi: 'Tạo bản nháp trả lời kèm link lịch',
                displayOrder: 20,
                enabled: true,
              },
            ],
          },
          {
            personaId: 'persona-2',
            personaKey: 'student',
            displayNameEn: 'Student',
            displayNameVi: 'Sinh viên',
            icon: 'book',
            displayOrder: 20,
            enabled: true,
            examples: [
              {
                exampleId: 'example-3',
                exampleTextEn: 'Label scholarship updates as School',
                exampleTextVi: 'Gắn nhãn học bổng là Trường học',
                displayOrder: 10,
                enabled: true,
              },
            ],
          },
        ],
      },
    });
    mocks.useSavePersona.mockReturnValue({ isPending: false, mutateAsync: savePersonaMutateAsync });
    mocks.useSaveExample.mockReturnValue({ isPending: false, mutateAsync: saveExampleMutateAsync });
    mocks.useSetRuleCatalogEnabled.mockReturnValue({
      isPending: false,
      mutate: setEnabledMutate,
    });
    mocks.useReorderRuleCatalog.mockReturnValue({ isPending: false, mutate: reorderMutate });
    savePersonaMutateAsync.mockResolvedValue(undefined);
    saveExampleMutateAsync.mockResolvedValue(undefined);
  });

  it('renders persona-owned examples and saves edited prompt text through the feature hook', async () => {
    const user = userEvent.setup();

    render(<RuleCatalogPage />);

    expect(screen.getByRole('heading', { name: 'Ví dụ tạo quy tắc' })).toBeInTheDocument();
    expect(screen.getByText('Founder')).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Actions' })).not.toBeInTheDocument();

    await user.click(
      screen.getByRole('button', { name: 'Sửa ví dụ Label investor emails as @[Investor]' }),
    );

    await user.clear(screen.getByLabelText('Mẫu VI'));
    await user.type(screen.getByLabelText('Mẫu VI'), 'Gắn nhãn thư nhà đầu tư là Investor');
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
          reason: 'Admin rule catalog UI update',
        },
      }),
    );
  });

  it('switches personas before editing examples', async () => {
    const user = userEvent.setup();

    render(<RuleCatalogPage />);

    await user.click(screen.getByRole('button', { name: 'Chọn nhóm Student' }));
    expect(screen.getByText('Label scholarship updates as School')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', { name: 'Sửa ví dụ Label scholarship updates as School' }),
    );
    await user.clear(screen.getByLabelText('Mẫu EN'));
    await user.type(screen.getByLabelText('Mẫu EN'), 'Archive scholarship newsletters');
    await user.click(screen.getByRole('button', { name: 'Lưu' }));

    await waitFor(() =>
      expect(saveExampleMutateAsync).toHaveBeenCalledWith({
        personaId: 'persona-2',
        exampleId: 'example-3',
        request: {
          exampleTextEn: 'Archive scholarship newsletters',
          exampleTextVi: 'Gắn nhãn học bổng là Trường học',
          displayOrder: 10,
          enabled: true,
          reason: 'Admin rule catalog UI update',
        },
      }),
    );
  });
});
