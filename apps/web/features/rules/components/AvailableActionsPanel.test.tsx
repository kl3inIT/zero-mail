import { NextIntlClientProvider } from 'next-intl';
import type { ReactNode } from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import enMessages from '@/i18n/messages/en.json';
import { AvailableActionsPanel } from '@/features/rules/components/AvailableActionsPanel';
import type { RuleCatalogActionDescriptorResponse } from '@/features/rules/api/rule-catalog-api';

describe('AvailableActionsPanel', () => {
  it('lists the rule action catalog as a compact label-only reference', () => {
    renderWithMessages(<AvailableActionsPanel actions={actions} />);

    for (const label of [
      'Label',
      'Archive',
      'Save draft',
      'Mark read',
      'Star',
      'Add to digest',
      'Mark spam',
      'Send reply',
      'Forward',
      'Send email',
    ]) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    expect(screen.queryByText('Apply a Gmail label.')).not.toBeInTheDocument();
    expect(screen.queryByText('Will auto-send')).not.toBeInTheDocument();
  });
});

const actions: RuleCatalogActionDescriptorResponse[] = [
  action('label', 'Label', 'Apply a Gmail label.', 'LOW', 10),
  action('archive', 'Archive', 'Remove from Inbox.', 'LOW', 20),
  action('save_draft', 'Save draft', 'Create a Gmail draft.', 'MEDIUM', 30),
  action('mark_read', 'Mark read', 'Mark as read.', 'LOW', 40),
  action('star', 'Star', 'Star the message.', 'LOW', 50),
  action('add_to_digest', 'Add to digest', 'Include in a digest.', 'LOW', 60),
  action('mark_spam', 'Mark spam', 'Move to spam.', 'MEDIUM', 70),
  action('send_reply', 'Send reply', 'Send a reply.', 'HIGH', 80),
  action('forward_email', 'Forward', 'Forward the message.', 'HIGH', 90),
  action('send_email', 'Send email', 'Send a new email.', 'HIGH', 100),
];

function action(
  actionKey: string,
  label: string,
  description: string,
  riskLevel: string,
  displayOrder: number,
): RuleCatalogActionDescriptorResponse {
  return {
    actionKey,
    label,
    description,
    riskLevel,
    availabilityStatus: 'AVAILABLE',
    displayOrder,
  };
}

function renderWithMessages(children: ReactNode) {
  return render(
    <NextIntlClientProvider locale="en" messages={enMessages}>
      {children}
    </NextIntlClientProvider>,
  );
}
