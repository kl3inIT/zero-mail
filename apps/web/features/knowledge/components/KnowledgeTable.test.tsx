import { render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { KnowledgeSnippet } from '@/features/knowledge/api/knowledge-api';
import { KnowledgeTable } from '@/features/knowledge/components/KnowledgeTable';
import enMessages from '@/i18n/messages/en.json';

const mocks = vi.hoisted(() => ({
  deleteMutateAsync: vi.fn(),
  refetch: vi.fn(),
  useKnowledge: vi.fn(),
}));

vi.mock('@/features/knowledge/hooks/useKnowledge', () => ({
  useKnowledge: mocks.useKnowledge,
}));

vi.mock('@/features/knowledge/hooks/useDeleteKnowledge', () => ({
  useDeleteKnowledge: () => ({ mutateAsync: mocks.deleteMutateAsync }),
}));

describe('KnowledgeTable', () => {
  beforeEach(() => {
    mocks.deleteMutateAsync.mockReset();
    mocks.refetch.mockReset();
    mocks.useKnowledge.mockReset();
  });

  it('renders the empty state', () => {
    mocks.useKnowledge.mockReturnValue(knowledgeState([]));

    renderWithMessages(<KnowledgeTable />);

    expect(screen.getByText('No snippets yet')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Add snippet/ })).toBeInTheDocument();
  });

  it('renders snippet rows', () => {
    mocks.useKnowledge.mockReturnValue(
      knowledgeState([
        knowledgeSnippet('00000000-0000-0000-0000-000000000001', 'Acme preferences'),
        knowledgeSnippet('00000000-0000-0000-0000-000000000002', 'Founder bio'),
      ]),
    );

    renderWithMessages(<KnowledgeTable />);

    expect(screen.getByText('Title')).toBeInTheDocument();
    expect(screen.getByText('Last updated')).toBeInTheDocument();
    expect(screen.getByText('Acme preferences')).toBeInTheDocument();
    expect(screen.getByText('Founder bio')).toBeInTheDocument();
  });
});

function knowledgeState(data: KnowledgeSnippet[]) {
  return {
    data,
    isError: false,
    isPending: false,
    refetch: mocks.refetch,
  };
}

function knowledgeSnippet(id: string, title: string): KnowledgeSnippet {
  return {
    id,
    title,
    content: `${title} content`,
    updatedAt: '2026-05-26T00:00:00.000Z',
  };
}

function renderWithMessages(children: ReactNode) {
  return render(
    <NextIntlClientProvider locale="en" messages={enMessages}>
      {children}
    </NextIntlClientProvider>,
  );
}
